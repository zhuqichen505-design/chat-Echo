package com.aiassistant.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Keeps the existing controlled-value API while the native editor owns selection, IME and scroll.
 * A parent echo must never replay an older selection into an in-progress handle drag.
 */
@OptIn(ExperimentalFoundationApi::class)
internal class NativeEditorState(initialValue: TextFieldValue) {
    val field = TextFieldState(initialValue.text, initialValue.selection)
    private var previousExternal = initialValue
    private var previousPublished = currentValue()

    fun reconcileExternal(value: TextFieldValue) {
        if (value == previousExternal) return
        previousExternal = value
        val current = field.text
        if (current.toString() != value.text || current.selectionInChars != value.selection) {
            field.edit {
                if (toString() != value.text) replace(0, length, value.text)
                selectCharsIn(value.selection)
            }
        }
        previousPublished = currentValue()
    }

    fun currentValue(): TextFieldValue = field.text.asTextFieldValue()

    fun valueToPublish(): TextFieldValue? = currentValue().takeUnless { it == previousPublished }
        ?.also { previousPublished = it }
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
@OptIn(ExperimentalFoundationApi::class)
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
    decorator: TextFieldDecorator? = null
) {
    val editor = remember { NativeEditorState(value) }
    SideEffect { editor.reconcileExternal(value) }
    val onCurrentValueChange by rememberUpdatedState(onValueChange)
    LaunchedEffect(editor) {
        snapshotFlow { editor.field.text }.collect {
            editor.valueToPublish()?.let(onCurrentValueChange)
        }
    }
    BasicTextField2(
        state = editor.field,
        modifier = modifier,
        textStyle = textStyle,
        interactionSource = interactionSource,
        cursorBrush = cursorBrush,
        lineLimits = lineLimits,
        scrollState = scrollState,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        decorator = decorator
    )
}
