package com.aiassistant.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text2.BasicTextField2
import androidx.compose.foundation.text2.TextFieldDecorator
import androidx.compose.foundation.text2.input.TextFieldCharSequence
import androidx.compose.foundation.text2.input.TextFieldLineLimits
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** One cancellable focus/IME transaction shared by the editor and its overlapping handle. */
internal class EditorKeyboardRequest {
    private var request: Job? = null

    fun cancel() {
        request?.cancel()
        request = null
    }

    fun launch(
        scope: kotlinx.coroutines.CoroutineScope,
        action: EditorTapAction? = null,
        block: suspend () -> Unit
    ) {
        // A native no-op tap must not cancel an earlier recovery that is still crossing frames.
        if (action == EditorTapAction.Ignore) return
        cancel()
        request = scope.launch { block() }
    }
}

/**
 * 读取当前窗口的输入法可见性。
 *
 * 组合期的 [WindowInsets.isImeVisible] 快照会滞后于一次隐藏/显示事务，涉及焦点与键盘的判定
 * 必须读实时窗口 Insets；取不到时回退到快照值。全应用只保留这一处实现。
 */
internal fun isImeVisibleNow(view: android.view.View, fallback: Boolean): Boolean = runCatching {
    ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime())
}.getOrNull() ?: fallback

/**
 * 输入框是否仍有可用的输入连接。
 *
 * 悬浮输入法的 IME Insets 恒为 0，[isImeVisibleNow] 会一直读到“不可见”；
 * 此时若按可见性清焦重建，每次点击都会收起再弹起键盘（闪烁），正在合成的文字也会中断。
 * 连接仍在时只重新显示键盘，只有连接确实失效才走清焦重建。
 */
internal fun isInputConnectionActiveNow(context: android.content.Context): Boolean = runCatching {
    (context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
        ?.isAcceptingText == true
    // 查询失败不能等价于连接失效：未知状态一律走非破坏性路径，绝不能据此清焦。
}.getOrNull() ?: true

/**
 * 一次点击开始时的输入状态，在按下（Initial pass）时读取，早于输入框自身的点击手势。
 *
 * 必须在手势处理前采样：BasicTextField2 的点击手势会 `requestFocus()` 并 `show()`，
 * 手势之后再读只会得到“刚被它改过”的状态，据此判断会把正常输入误判成需要重建会话。
 */
internal data class EditorGestureStart(
    val focused: Boolean,
    val imeVisible: Boolean,
    /** 点击前输入连接是否仍可用；false 只触发延迟复核，不再直接清焦。 */
    val inputConnectionActive: Boolean = true,
    /** BasicTextField2 原生点击负责 requestFocus；show 只在输入法已显示时才由它自己完成。 */
    val nativeTapHandlesFocusAndKeyboard: Boolean = false
)

/** 点击输入框时该做什么。 */
internal enum class EditorTapAction {
    /** 手势前未聚焦：输入框自身的手势会请求焦点并建立输入会话，这里只补一次保险。 */
    AssistFocus,

    /** 输入连接确实失效（键盘在屏幕上也打不出字 / 旧会话已结束）：必须清焦重建。 */
    RestartSession,

    /** 已聚焦、读不到输入法但连接仍可用：只重新显示键盘，绝不清焦（清焦会收起再弹起，表现为闪烁）。 */
    RevealKeyboard,

    /** 手势前已聚焦、输入法仍在屏幕上且连接可用：不要碰焦点，也不要重复 show()，否则键盘会闪一下。 */
    Ignore
}

/**
 * BasicTextField2 1.6.8 仅在焦点重新获得时调用 startInputSession；IME 关闭 InputConnection 后
 * 字段仍保持聚焦，旧的输入会话已经结束，此时 show() 只能弹出没有连接、打不出字的键盘。
 * 首次点击由输入框自身的 focus 请求建立会话，若此时再清焦，会把刚建立的会话拆掉并留下死连接。
 *
 * 判定顺序：
 * 1. 点击前未聚焦 → [EditorTapAction.AssistFocus]。原生点击也会请求焦点，但装饰器空白区域
 *    与窗口/布局切换期间的原生事务可能没有落地，因此这里需要跨帧确认，未聚焦才补一次。
 * 2. 已聚焦且点击前连接失效 → [EditorTapAction.RestartSession]。此处只表示“需要复核”，
 *    必须先给原生 startInput/show 的跨帧队列一个恢复机会，连续确认仍失效后才能清焦。
 * 3. 已聚焦、连接可用且输入法仍在屏幕上 → [EditorTapAction.Ignore]：不改焦点、不重复 show()，
 *    避免打字过程中键盘闪一下。
 * 4. 已聚焦、连接可用但输入法不可见（返回键收起 / 关闭弹窗 / 悬浮窗放大后）→
 *    [EditorTapAction.RevealKeyboard]。原生点击对已聚焦字段不会再 show()，应用层必须补一次：
 *    v2.8.33 把这一情形也让行给原生，造成“点输入框永远唤不出键盘”的死锁。
 *    悬浮输入法的 Insets 可能恒为 0：连接仍可用，因此只补显示、绝不清焦，不会收起再弹起。
 *
 * 旧实现按按下时的单次 isAcceptingText=false 立即 clearFocus。原生点击的 startInput/show
 * 要到下一帧才执行，因此一次瞬时 false 会把正常显示事务覆盖成 stopInput/hide，造成不弹出或闪烁。
 */
internal fun editorTapAction(start: EditorGestureStart): EditorTapAction =
    when {
        !start.focused -> EditorTapAction.AssistFocus
        !start.inputConnectionActive -> EditorTapAction.RestartSession
        start.imeVisible -> EditorTapAction.Ignore
        else -> EditorTapAction.RevealKeyboard
    }

/**
 * A real frame between losing and regaining focus lets BasicTextField2 restart its input session.
 * 清焦后必须确认字段真的失焦：焦点事务若被合并，跨帧 requestFocus 不会触发新的输入会话，
 * 键盘虽被 show() 唤起却没有可用的 InputConnection，表现为“能弹出键盘但无法输入”。
 *
 * 反过来，清焦之后无论焦点事务是否被观察到，都必须继续 requestFocus 并补一次 show()：
 * 提前 return 会留下“键盘已被收起、也没有再唤起”的状态，用户看到的就是输入法被删掉。
 */
internal suspend fun reopenEditorKeyboard(
    action: EditorTapAction,
    clearFocus: () -> Unit,
    requestFocus: () -> Unit,
    isFocused: () -> Boolean,
    isActive: () -> Boolean,
    nextFrame: suspend () -> Unit,
    showKeyboard: () -> Unit,
    isImeVisible: () -> Boolean = { false },
    isInputConnectionActive: () -> Boolean = { true },
    nativeTapHandlesFocusAndKeyboard: Boolean = false,
    maxFocusFrames: Int = 3,
    /** 连接恢复复核窗口：远长于一次焦点事务，避免把输入法绑定中的瞬时 false 误判为失效。 */
    maxConnectionFrames: Int = 8
) {
    if (action == EditorTapAction.Ignore) return
    if (!isActive()) return
    val nativeFirstFocus = nativeTapHandlesFocusAndKeyboard && action == EditorTapAction.AssistFocus
    // Tapping an already active editor must not restart focus or re-show a visible IME.
    // A native first-focus tap still needs to verify that its input connection survived.
    if (action != EditorTapAction.RestartSession && !nativeFirstFocus &&
        isFocused() && isImeVisible()) return
    if (action == EditorTapAction.RestartSession || nativeFirstFocus) {
        if (nativeFirstFocus) {
            // 原生 onTap 的 requestFocus 可能被装饰器空白区、窗口切换或同一帧布局吞掉。
            // 先等它落地；确实没有聚焦时才补请求，不先清焦，避免拆掉已经建立的会话。
            val focusArrived = awaitEditorFocus(
                isFocused = isFocused,
                isActive = isActive,
                nextFrame = nextFrame,
                expected = true,
                maxFrames = maxFocusFrames
            )
            if (!isActive()) return
            if (!focusArrived) {
                requestFocus()
                if (!awaitEditorFocus(isFocused, isActive, nextFrame, expected = true, maxFrames = maxFocusFrames)) return
            }
        }
        // BasicTextField2 的 startInput/show 在下一帧才执行。先让原生点击完成恢复；
        // 只有连续数帧都没有活动连接，才允许执行会隐藏键盘的 clearFocus。
        val connectionRecovered = awaitEditorInputConnection(
            isInputConnectionActive = isInputConnectionActive,
            isActive = isActive,
            nextFrame = nextFrame,
            maxFrames = maxConnectionFrames
        )
        // RestartSession 来自“点击前已聚焦”；等待期间失焦说明用户已离开，不能再抢回焦点。
        if (!isActive() || !isFocused()) return
        if (connectionRecovered) {
            // 原生已聚焦且连接可用时，它的 show 是唯一显示请求。首次聚焦则需要确认键盘真的出现：
            // 系统显示请求是 best-effort，焦点切换时可能被窗口状态静默丢弃。
            if ((!nativeTapHandlesFocusAndKeyboard || nativeFirstFocus) && !isImeVisible()) showKeyboard()
            return
        }
        clearFocus()
        awaitEditorFocus(isFocused, isActive, nextFrame, expected = false, maxFrames = maxFocusFrames)
        if (!isActive()) return
        requestFocus()
        awaitEditorFocus(isFocused, isActive, nextFrame, expected = true, maxFrames = maxFocusFrames)
        // 会话重建是修复动作：键盘可能已被清焦收起，即使焦点确认失败也要补一次 show()。
        if (isActive() && !isImeVisible()) showKeyboard()
        return
    }
    // 已聚焦的字段完全不动焦点：重复 requestFocus 只会在部分输入法上重建输入视图。
    if (!isFocused()) {
        requestFocus()
        // 焦点没有落地就不要弹键盘：那只会得到一把没有连接的键盘。
        if (!awaitEditorFocus(isFocused, isActive, nextFrame, expected = true, maxFrames = maxFocusFrames)) return
    }
    // 焦点获得本身就会 startInput 并请求显示键盘；只有它没有生效时才补一次 show()。
    if (isActive() && !isImeVisible()) showKeyboard()
}

/** 给原生跨帧 startInput/show 最多 [maxFrames] 帧恢复机会，任一样本恢复即取消重建。 */
private suspend fun awaitEditorInputConnection(
    isInputConnectionActive: () -> Boolean,
    isActive: () -> Boolean,
    nextFrame: suspend () -> Unit,
    maxFrames: Int
): Boolean {
    if (isInputConnectionActive()) return true
    repeat(maxFrames) {
        nextFrame()
        if (!isActive()) return false
        if (isInputConnectionActive()) return true
    }
    return false
}

/** 最多等待 [maxFrames] 帧观察焦点是否落到 [expected]；返回 false 表示焦点事务没有生效。 */
private suspend fun awaitEditorFocus(
    isFocused: () -> Boolean,
    isActive: () -> Boolean,
    nextFrame: suspend () -> Unit,
    expected: Boolean,
    maxFrames: Int
): Boolean {
    repeat(maxFrames) {
        nextFrame()
        if (!isActive()) return false
        if (isFocused() == expected) return true
    }
    return false
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun rememberEditorKeyboardRequest(
    focusRequester: FocusRequester,
    isFocused: () -> Boolean,
    active: Boolean = true
): Pair<EditorKeyboardRequest, (EditorGestureStart) -> Unit> {
    val scope = rememberCoroutineScope()
    val transaction = remember { EditorKeyboardRequest() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val windowInfo = LocalWindowInfo.current
    val context = LocalContext.current
    val view = LocalView.current
    // Visibility also covers floating IMEs, whose bottom inset can legitimately be zero.
    val imeVisible = WindowInsets.isImeVisible
    val latestActive by rememberUpdatedState(active)
    val latestImeVisible by rememberUpdatedState(imeVisible)
    // Query the live window insets: composition snapshots can lag behind a hide/show transaction.
    fun imeVisibleNow(): Boolean = isImeVisibleNow(view, latestImeVisible)
    // 参数是手势开始前（按下时）的聚焦与输入法状态，不能用事件尾部的状态判断：
    // 首次点击时字段已被自身手势聚焦，误判会立刻清焦并拆掉刚建立的输入会话。
    val request: (EditorGestureStart) -> Unit = { start ->
        val action = editorTapAction(start)
        transaction.launch(scope, action) {
            reopenEditorKeyboard(
                action = action,
                clearFocus = { focusManager.clearFocus(force = true) },
                requestFocus = { focusRequester.requestFocus() },
                isFocused = isFocused,
                isActive = { latestActive && windowInfo.isWindowFocused },
                nextFrame = { withFrameNanos { }; kotlinx.coroutines.yield() },
                showKeyboard = { keyboard?.show() },
                isImeVisible = { imeVisibleNow() },
                isInputConnectionActive = { isInputConnectionActiveNow(context) },
                nativeTapHandlesFocusAndKeyboard = start.nativeTapHandlesFocusAndKeyboard
            )
        }
    }
    return transaction to request
}

/**
 * Keeps the existing controlled-value API while the native editor owns selection, IME and scroll.
 * A parent echo must never replay an older selection into an in-progress handle drag.
 */
@OptIn(ExperimentalFoundationApi::class)
internal class NativeEditorState(initialValue: TextFieldValue) {
    val field = TextFieldState(initialValue.text, initialValue.selection)
    private var previousExternal = initialValue
    private var previousPublished = currentValue()
    private val published = ArrayDeque<TextFieldValue>()

    fun reconcileExternal(value: TextFieldValue) {
        if (value == previousExternal) return
        previousExternal = value
        // Acknowledging an earlier callback must not rewind a newer IME edit or handle drag.
        val acknowledgement = published.indexOfFirst {
            it.text == value.text && it.selection == value.selection
        }
        if (acknowledgement >= 0) {
            repeat(acknowledgement + 1) { published.removeFirst() }
            return
        }
        val current = field.text
        if (current.toString() != value.text || current.selectionInChars != value.selection) {
            field.edit {
                if (toString() != value.text) replace(0, length, value.text)
                selectCharsIn(value.selection)
            }
        }
        previousPublished = currentValue()
        published.clear()
    }

    fun currentValue(): TextFieldValue = field.text.asTextFieldValue()

    fun valueToPublish(): TextFieldValue? = currentValue().takeUnless { it == previousPublished }
        ?.also {
            previousPublished = it
            published.addLast(it)
        }
}

@OptIn(ExperimentalFoundationApi::class)
private fun TextFieldCharSequence.asTextFieldValue() = TextFieldValue(
    text = toString(),
    selection = selectionInChars,
    composition = compositionInChars
)

/**
 * Compose 1.6.8's state-based field keeps handle positions in the same coordinate space as its
 * exposed scroll state, including content movement during a drag. Do not add an outer vertical
 * scroll or intercept its bring-into-view requests: doing so detaches handles from that viewport.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun EchoNativeTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle.Default,
    interactionSource: MutableInteractionSource? = null,
    cursorBrush: Brush = SolidColor(Color.Black),
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.Default,
    scrollState: ScrollState = rememberScrollState(),
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    decorator: TextFieldDecorator? = null,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onRequestKeyboard: ((EditorGestureStart) -> Unit)? = null
) {
    val editor = remember { NativeEditorState(value) }
    val density = LocalDensity.current.density
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val requestKeyboard = onRequestKeyboard ?: rememberEditorKeyboardRequest(focusRequester, isFocused = { focused }).second
    val latestRequestKeyboard by rememberUpdatedState(requestKeyboard)
    val view = LocalView.current
    var getTextLayout by remember { mutableStateOf<(() -> TextLayoutResult?)?>(null) }
    SideEffect { editor.reconcileExternal(value) }
    val onCurrentValueChange by rememberUpdatedState(onValueChange)
    LaunchedEffect(editor) {
        snapshotFlow { editor.field.text }.collect {
            editor.valueToPublish()?.let(onCurrentValueChange)
        }
    }
    // The foundation version in this project follows an out-of-view selection in one jump.
    // Own only that automatic follow; an ordinary UserInput scroll can still cancel this block.
    LaunchedEffect(editor, scrollState, focused, density, lineLimits) {
        if (!focused || lineLimits == TextFieldLineLimits.SingleLine) return@LaunchedEffect
        followEditorSelectionScroll(
            selections = snapshotFlow { editor.field.text.selectionInChars },
            scrollState = scrollState,
            density = density,
            distanceAt = { offset ->
                val layout = getTextLayout?.invoke()
                if (layout == null || layout.layoutInput.text.text != editor.field.text.toString()) 0f
                else {
                    val viewport = layout.size.height - scrollState.maxValue
                    val cursor = layout.getCursorRect(offset.coerceIn(0, layout.layoutInput.text.length))
                    when {
                        viewport <= 0 -> 0f
                        cursor.bottom > scrollState.value + viewport -> cursor.bottom - scrollState.value - viewport
                        cursor.top < scrollState.value -> cursor.top - scrollState.value
                        else -> 0f
                    }
                }
            },
            frameNanos = { withFrameNanos { it } }
        )
    }
    // 下面的观察器不消费事件：已聚焦且键盘仍在屏幕上时完全让行；键盘被收起（返回键 / 关闭弹窗 /
    // 悬浮窗放大）时应用层补一次 show；首次聚焦验证原生焦点 / 输入会话是否落地，失效会话再延迟复核。
    // 不要替换 LocalSoftwareKeyboardController，否则键盘收起动画期间残留的“可见”状态会把 show() 丢掉。
    BasicTextField2(
        state = editor.field,
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .pointerInput(Unit) {
                // Observe taps without consuming them. This also covers the decorator's empty
                // area and reopens an IME hidden with Back while the field still has focus.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    // 在输入框自身手势（Main pass）处理之前采样：聚焦、输入法可见性与连接状态
                    // 都必须取“点击前”的值，否则会把输入框刚建立的会话误判成需要重建。
                    val start = EditorGestureStart(
                        focused = focused,
                        imeVisible = isImeVisibleNow(view, fallback = false),
                        inputConnectionActive = isInputConnectionActiveNow(context),
                        nativeTapHandlesFocusAndKeyboard = true
                    )
                    var tap = true
                    var pressed: Boolean
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || (change.position - down.position).getDistance() > viewConfiguration.touchSlop ||
                            event.changes.size > 1 || change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis) tap = false
                        pressed = change?.pressed == true
                    } while (pressed)
                    if (tap) {
                        // Run after the native tap handler, so its Up event cannot undo our clear.
                        awaitPointerEvent(PointerEventPass.Final)
                        latestRequestKeyboard(start)
                    }
                }
            },
        textStyle = textStyle,
        interactionSource = interactionSource,
        cursorBrush = cursorBrush,
        lineLimits = lineLimits,
        scrollState = scrollState,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        onTextLayout = { getTextLayout = it },
        decorator = decorator
    )
}
