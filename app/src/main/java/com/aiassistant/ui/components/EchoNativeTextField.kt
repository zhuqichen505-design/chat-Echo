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
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
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

/** A real frame between losing and regaining focus lets BasicTextField2 restart its input session. */
internal suspend fun reopenEditorKeyboard(
    restartFocus: Boolean,
    clearFocus: () -> Unit,
    requestFocus: () -> Unit,
    isFocused: () -> Boolean,
    isActive: () -> Boolean,
    nextFrame: suspend () -> Unit,
    showKeyboard: () -> Unit
) {
    if (!isActive()) return
    if (restartFocus) {
        clearFocus()
        nextFrame()
        if (!isActive()) return
    }
    requestFocus()
    nextFrame()
    if (isActive() && isFocused()) showKeyboard()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun rememberEditorKeyboardRequest(
    focusRequester: FocusRequester,
    focused: Boolean,
    active: Boolean = true
): Pair<EditorKeyboardRequest, () -> Unit> {
    val scope = rememberCoroutineScope()
    val transaction = remember { EditorKeyboardRequest() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val windowInfo = LocalWindowInfo.current
    // Visibility also covers floating IMEs, whose bottom inset can legitimately be zero.
    val imeVisible = WindowInsets.isImeVisible
    val latestFocused by rememberUpdatedState(focused)
    val latestActive by rememberUpdatedState(active)
    val latestImeVisible by rememberUpdatedState(imeVisible)
    val request = {
        transaction.launch(scope) {
            reopenEditorKeyboard(
                restartFocus = latestFocused && !latestImeVisible,
                clearFocus = { focusManager.clearFocus(force = true) },
                requestFocus = { focusRequester.requestFocus() },
                isFocused = { latestFocused },
                isActive = { latestActive && windowInfo.isWindowFocused },
                nextFrame = { withFrameNanos { }; kotlinx.coroutines.yield() },
                showKeyboard = { keyboard?.show() }
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
    onRequestKeyboard: (() -> Unit)? = null
) {
    val editor = remember { NativeEditorState(value) }
    val density = LocalDensity.current.density
    var focused by remember { mutableStateOf(false) }
    val requestKeyboard = onRequestKeyboard ?: rememberEditorKeyboardRequest(focusRequester, focused).second
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
                        latestRequestKeyboard()
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
