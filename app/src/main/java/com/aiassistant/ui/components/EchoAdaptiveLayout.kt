package com.aiassistant.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Width classes follow the Material 3 breakpoints so phones, foldables and tablets share one vocabulary.
 * Only the layout decision lives here; colours, shapes and typography stay with the theme.
 */
enum class EchoWindowClass { Compact, Medium, Expanded }

@Composable
fun rememberEchoWindowClass(): EchoWindowClass {
    val width = LocalConfiguration.current.screenWidthDp
    return when {
        width >= 840 -> EchoWindowClass.Expanded
        width >= 600 -> EchoWindowClass.Medium
        else -> EchoWindowClass.Compact
    }
}

/**
 * Horizontal inset that keeps a readable column centred instead of stretching rows to the screen edge.
 * Phones keep [base]; wider windows add the surplus as symmetric padding so the column stays at
 * [maxContentWidth]. Pure function so the arithmetic is unit-testable without a composition.
 */
fun echoContentInsetFor(windowWidth: Dp, base: Dp, maxContentWidth: Dp): Dp {
    if (windowWidth <= maxContentWidth) return base
    return base + (windowWidth - maxContentWidth) / 2
}

/** Window-aware variant of [echoContentInsetFor]; settings and lists use 720dp, conversation lists 840dp. */
@Composable
fun echoContentInset(base: Dp = 12.dp, maxContentWidth: Dp = 720.dp): Dp =
    echoContentInsetFor(LocalConfiguration.current.screenWidthDp.dp, base, maxContentWidth)
