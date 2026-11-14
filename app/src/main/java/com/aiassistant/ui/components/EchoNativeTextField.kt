package com.aiassistant.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.ScrollState
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
import androidx.compose.ui.platform.LocalDensity
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

/** One cancellable focus/IME transaction owned by the external resize handle. */
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
        // A no-op handle tap must not cancel an earlier recovery that is still crossing frames.
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

/** State sampled by the external resize handle, which sits outside the native field's hit area. */
internal data class EditorGestureStart(
    val focused: Boolean,
    val imeVisible: Boolean
)

/** Only the external handle needs application assistance; native editor taps own focus and IME. */
internal enum class EditorTapAction {
    AssistFocus,
    RevealKeyboard,
    Ignore
}

internal fun editorTapAction(start: EditorGestureStart): EditorTapAction =
    when {
        !start.focused -> EditorTapAction.AssistFocus
        start.imeVisible -> EditorTapAction.Ignore
        else -> EditorTapAction.RevealKeyboard
    }

/**
 * Assists only taps on the external resize handle. Never clears focus or probes the global
 * InputMethodManager: BasicTextField2 owns its input connection and all taps inside the editor.
 */
internal suspend fun reopenEditorKeyboard(
    action: EditorTapAction,
    requestFocus: () -> Unit,
    isFocused: () -> Boolean,
    isActive: () -> Boolean,
    nextFrame: suspend () -> Unit,
    showKeyboard: () -> Unit,
    isImeVisible: () -> Boolean = { false },
    maxFocusFrames: Int = 3
) {
    if (action == EditorTapAction.Ignore || !isActive()) return
    if (!isFocused()) {
        requestFocus()
        // Do not show a keyboard before the field actually owns focus and an input session.
        if (!awaitEditorFocus(isFocused, isActive, nextFrame, expected = true, maxFrames = maxFocusFrames)) return
    }
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

/** Wait briefly for a user-tapped window to regain focus; never queue an indefinite show. */
internal suspend fun awaitEditorWindowFocus(
    isActive: () -> Boolean,
    isWindowFocused: () -> Boolean,
    nextFrame: suspend () -> Unit,
    timeoutMillis: Long = 500
): Boolean = kotlinx.coroutines.withTimeoutOrNull(timeoutMillis) {
    while (isActive() && !isWindowFocused()) nextFrame()
    isActive() && isWindowFocused()
} == true

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun rememberEditorKeyboardRequest(
    focusRequester: FocusRequester,
    isFocused: () -> Boolean,
    active: Boolean = true
): Pair<EditorKeyboardRequest, (EditorGestureStart) -> Unit> {
    val scope = rememberCoroutineScope()
    val transaction = remember { EditorKeyboardRequest() }
    val keyboard = LocalSoftwareKeyboardController.current
    val windowInfo = LocalWindowInfo.current
    val view = LocalView.current
    val imeVisible = WindowInsets.isImeVisible
    val latestActive by rememberUpdatedState(active)
    val latestFocused by rememberUpdatedState(isFocused)
    val latestImeVisible by rememberUpdatedState(imeVisible)
    fun imeVisibleNow(): Boolean = isImeVisibleNow(view, latestImeVisible)

    val request: (EditorGestureStart) -> Unit = { start ->
        val action = editorTapAction(start)
        val hadWindowFocus = windowInfo.isWindowFocused
        transaction.launch(scope, if (hadWindowFocus) action else null) {
            if (!hadWindowFocus) {
                val gained = awaitEditorWindowFocus(
                    isActive = { latestActive },
                    isWindowFocused = { windowInfo.isWindowFocused },
                    nextFrame = { withFrameNanos { } }
                )
                if (!gained) return@launch
            }
            val currentAction = if (hadWindowFocus) action else editorTapAction(
                start.copy(focused = latestFocused(), imeVisible = imeVisibleNow())
            )
            reopenEditorKeyboard(
                action = currentAction,
                requestFocus = { focusRequester.requestFocus() },
                isFocused = { latestFocused() },
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
    focusRequester: FocusRequester = remember { FocusRequester() }
) {
    val editor = remember { NativeEditorState(value) }
    val density = LocalDensity.current.density
    var focused by remember { mutableStateOf(false) }
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
    // Compose owns taps, focus and the window-scoped input session, including restarting that
    // session after a window focus change. Do not intercept its keyboard controller, clear focus
    // on window transitions, or infer connection health from InputMethodManager's process state.
    BasicTextField2(
        state = editor.field,
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused },
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
