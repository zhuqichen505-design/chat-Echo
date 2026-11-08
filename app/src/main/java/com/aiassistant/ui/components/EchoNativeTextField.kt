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

    fun launch(scope: kotlinx.coroutines.CoroutineScope, block: suspend () -> Unit) {
        cancel()
        request = scope.launch { block() }
    }
}

/**
 * 只有“手势开始前已聚焦、且输入法不可见”才需要重建输入会话。
 *
 * BasicTextField2 1.6.8 仅在焦点重新获得时调用 startInputSession；IME 关闭 InputConnection 后
 * 字段仍保持聚焦，旧的输入会话已经结束，此时 show() 只能弹出没有连接、打不出字的键盘。
 * 首次点击由输入框自身的 focus 请求建立会话，若此时再清焦，会把刚建立的会话拆掉并留下死连接。
 */
internal fun shouldRestartEditorFocus(wasFocusedBeforeGesture: Boolean, imeVisible: Boolean): Boolean =
    wasFocusedBeforeGesture && !imeVisible

/**
 * A real frame between losing and regaining focus lets BasicTextField2 restart its input session.
 * 清焦后必须确认字段真的失焦：焦点事务若被合并，跨帧 requestFocus 不会触发新的输入会话，
 * 键盘虽被 show() 唤起却没有可用的 InputConnection，表现为“能弹出键盘但无法输入”。
 */
internal suspend fun reopenEditorKeyboard(
    restartFocus: Boolean,
    clearFocus: () -> Unit,
    requestFocus: () -> Unit,
    isFocused: () -> Boolean,
    isActive: () -> Boolean,
    nextFrame: suspend () -> Unit,
    showKeyboard: () -> Unit,
    isImeVisible: () -> Boolean = { false },
    maxFocusFrames: Int = 3
) {
    if (!isActive()) return
    // Tapping an already active editor must not restart focus or re-show a visible IME.
    if (isFocused() && isImeVisible()) return
    if (restartFocus) {
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
): Pair<EditorKeyboardRequest, (Boolean) -> Unit> {
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
    fun imeVisibleNow(): Boolean = runCatching {
        ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime())
    }.getOrNull() ?: latestImeVisible
    // 参数是手势开始前的聚焦状态（输入框在按下时读取），不能用事件尾部的状态判断：
    // 首次点击时字段已被自身手势聚焦，误判会立刻清焦并拆掉刚建立的输入会话。
    val request: (Boolean) -> Unit = { wasFocusedBeforeGesture ->
        transaction.launch(scope) {
            reopenEditorKeyboard(
                restartFocus = shouldRestartEditorFocus(wasFocusedBeforeGesture, imeVisibleNow()),
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
    onRequestKeyboard: ((Boolean) -> Unit)? = null
) {
    val editor = remember { NativeEditorState(value) }
    val density = LocalDensity.current.density
    var focused by remember { mutableStateOf(false) }
    val requestKeyboard = onRequestKeyboard ?: rememberEditorKeyboardRequest(focusRequester, isFocused = { focused }).second
    val latestRequestKeyboard by rememberUpdatedState(requestKeyboard)
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
                    // 在输入框自身手势（Main pass）处理之前读取，得到的是“点击前”的聚焦状态。
                    val wasFocused = focused
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
                        latestRequestKeyboard(wasFocused)
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
