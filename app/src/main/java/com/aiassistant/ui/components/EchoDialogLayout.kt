package com.aiassistant.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Normal forms share one width; multi-column review tools may explicitly request more room. */
enum class EchoDialogSize(val maximumWidth: Dp) {
    Standard(480.dp),
    Expanded(640.dp)
}

/** Insets are applied before these bounds, so the keyboard never gets counted twice. */
object EchoDialogLayout {
    val horizontalMargin = 16.dp
    val verticalMargin = 24.dp
    val contentPadding = 20.dp
    val titleGap = 8.dp
    val actionGap = 16.dp
    const val maximumHeightFraction = 0.90f
}

internal data class EchoDialogBounds(val width: Dp, val maximumHeight: Dp)

internal fun echoDialogBounds(
    availableWidth: Dp,
    availableHeight: Dp,
    size: EchoDialogSize = EchoDialogSize.Standard
): EchoDialogBounds = EchoDialogBounds(
    width = availableWidth.coerceAtLeast(0.dp).coerceAtMost(size.maximumWidth),
    maximumHeight = availableHeight.coerceAtLeast(0.dp) * EchoDialogLayout.maximumHeightFraction
)
