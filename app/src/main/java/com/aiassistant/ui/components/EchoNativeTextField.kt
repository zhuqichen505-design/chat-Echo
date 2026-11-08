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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.SoftwareKeyboardController
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

    fun launch(scope: kotlinx.coroutines.CoroutineScope, block: suspend () -> Unit) {
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
 * 输入法已经在屏幕上时再次 show() 会让部分输入法重建输入视图，表现为键盘闪一下。
 *
 * BasicTextField2 1.6.8 的点击手势（`TextFieldSelectionState.detectTextFieldTapGestures`）在
 * 每次点击已聚焦输入框时都会调用 `showKeyboard()`，这是应用侧无法通过自己的焦点事务规避的
 * 重复显示。这里只拦掉“输入法确实已可见”时的重复 show()；输入法已收起时照常放行，
 * 隐藏键盘与真正需要重新唤起键盘的路径不受影响。
 */
internal class ImeAwareKeyboardController(
    private val delegate: SoftwareKeyboardController?,
    private val isImeVisible: () -> Boolean
) : SoftwareKeyboardController {
    override fun show() {
        if (isImeVisible()) return
        delegate?.show()
    }

    override fun hide() {
        delegate?.hide()
    }
}

/**
 * 一次点击开始时的输入状态，在按下（Initial pass）时读取，早于输入框自身的点击手势。
 *
 * 必须在手势处理前采样：BasicTextField2 的点击手势会 `requestFocus()` 并 `show()`，
 * 手势之后再读只会得到“刚被它改过”的状态，据此判断会把正常输入误判成需要重建会话。
 */
internal data class EditorGestureStart(
    val focused: Boolean,
    val imeVisible: Boolean
)

/** 点击输入框时该做什么。 */
internal enum class EditorTapAction {
    /** 手势前未聚焦：输入框自身的手势会请求焦点并建立输入会话，这里只补一次保险。 */
    AssistFocus,

    /** 手势前已聚焦、且输入法确实已收起：旧会话已结束，必须清焦重建。 */
    RestartSession,

    /** 手势前已聚焦、输入法仍在屏幕上：不要碰焦点，也不要重复 show()，否则键盘会闪一下。 */
    Ignore
}

/**
 * BasicTextField2 1.6.8 仅在焦点重新获得时调用 startInputSession；IME 关闭 InputConnection 后
 * 字段仍保持聚焦，旧的输入会话已经结束，此时 show() 只能弹出没有连接、打不出字的键盘。
 * 首次点击由输入框自身的 focus 请求建立会话，若此时再清焦，会把刚建立的会话拆掉并留下死连接。
 *
 * 打字时点击已聚焦输入框、输入法仍在屏幕上属于 [EditorTapAction.Ignore]：
 * 旧实现只看“输入法此刻是否可见”，显示动画 / 悬浮输入法 / API<30 的近似值都可能读到不可见，
 * 于是 clearFocus → stopInput 收起键盘、重新聚焦再弹起，表现为输入法闪烁。
 */
internal fun editorTapAction(start: EditorGestureStart): EditorTapAction =
    when {
        !start.focused -> EditorTapAction.AssistFocus
        start.imeVisible -> EditorTapAction.Ignore
        else -> EditorTapAction.RestartSession
    }

/**
 * A real frame between losing and regaining focus lets BasicTextField2 restart its input session.
 * 清焦后必须确认字段真的失焦：焦点事务若被合并，跨帧 requestFocus 不会触发新的输入会话，
 * 键盘虽被 show() 唤起却没有可用的 InputConnection，表现为“能弹出键盘但无法输入”。
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
    maxFocusFrames: Int = 3
) {
    if (action == EditorTapAction.Ignore) return
    if (!isActive()) return
    // Tapping an already active editor must not restart focus or re-show a visible IME.
    if (isFocused() && isImeVisible()) return
    if (action == EditorTapAction.RestartSession) {
        clearFocus()
        // 失焦没有被观察到时不要重新聚焦：会话不会重建，只会得到“有键盘、无输入”。
        if (!awaitEditorFocus(isFocused, isActive, nextFrame, expected = false, maxFrames = maxFocusFrames)) return
    }
    requestFocus()
    if (!awaitEditorFocus(isFocused, isActive, nextFrame, expected = true, maxFrames = maxFocusFrames)) return
    // 焦点获得本身就会 startInput 并请求显示键盘；只有它没有生效时才补一次 show()。
    if (isActive() && !isImeVisible()) showKeyboard()
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
        transaction.launch(scope) {
            reopenEditorKeyboard(
                action = editorTapAction(start),
                clearFocus = { focusManager.clearFocus(force = true) },
                requestFocus = { focusRequester.requestFocus() },
                isFocused = isFocused,
                isActive = { latestActive && windowInfo.isWindowFocused },
                nextFrame = { withFrameNanos { }; kotlinx.coroutines.yield() },
                showKeyboard = { keyboard?.show() },
                isImeVisible = { imeVisibleNow() }
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
    var focused by remember { mutableStateOf(false) }
    val requestKeyboard = onRequestKeyboard ?: rememberEditorKeyboardRequest(focusRequester, isFocused = { focused }).second
    val latestRequestKeyboard by rememberUpdatedState(requestKeyboard)
    // Compose 自身的点击手势每次都会 show()；输入法已可见时重复 show() 会让它闪一下。
    // 只拦这一条路径：本组件自己的焦点事务仍用原始控制器（它已先判断可见性）。
    val keyboard = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    val imeFallback = WindowInsets.isImeVisible
    val guardedKeyboard = remember(keyboard, view, imeFallback) {
        keyboard?.let { delegate ->
            ImeAwareKeyboardController(delegate) { isImeVisibleNow(view, imeFallback) }
        }
    }
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
    // 输入法已可见时，BasicTextField2 的点击手势仍会调用 show()，部分输入法因此重建输入视图闪一下。
    // 只在输入框内部替换键盘控制器：其它组件与隐藏键盘路径不受影响。
    CompositionLocalProvider(LocalSoftwareKeyboardController provides guardedKeyboard) {
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
                        // 在输入框自身手势（Main pass）处理之前采样：聚焦与输入法可见性都必须取
                        // “点击前”的值，否则会把输入框刚建立的会话误判成需要重建。
                        val start = EditorGestureStart(
                            focused = focused,
                            imeVisible = isImeVisibleNow(view, fallback = false)
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
}
