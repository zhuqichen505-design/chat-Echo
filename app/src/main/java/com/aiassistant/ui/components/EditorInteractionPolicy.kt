package com.aiassistant.ui.components

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** Same-text parent echoes must retain IME composition and the user's exact selection. */
internal fun reconcileEditorText(current: TextFieldValue, text: String): TextFieldValue =
    if (current.text == text) current else TextFieldValue(text, selection = TextRange(text.length))

/** Layout/focus changes alone must not move a manually browsed editor to its old cursor. */
internal fun shouldFollowEditorCursor(
    focused: Boolean,
    previous: TextFieldValue,
    current: TextFieldValue,
    layoutText: String
): Boolean = focused && layoutText == current.text &&
    (previous.text != current.text || previous.selection != current.selection)
