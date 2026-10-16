package com.aiassistant.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aiassistant.utils.EchoAppearancePreset

/**
 * A visual preset includes its material and silhouette, independently of the chosen home layout.
 * Shared containers consume these tokens so selecting a preset changes the rendered interface,
 * including controls, menus and dialogs, rather than only replacing its accent color.
 */
data class EchoThemeStyle(
    val isMonochrome: Boolean = false,
    val panelCornerRadius: Dp = 18.dp,
    val controlCornerRadius: Dp = 12.dp,
    val dialogCornerRadius: Dp = 24.dp,
    val flatSurfaces: Boolean = false
)

internal fun echoThemeStyleFor(appearancePreset: String): EchoThemeStyle =
    if (EchoAppearancePreset.sanitize(appearancePreset) == EchoAppearancePreset.MONO) {
        EchoThemeStyle(
            isMonochrome = true,
            panelCornerRadius = 8.dp,
            controlCornerRadius = 6.dp,
            dialogCornerRadius = 12.dp,
            flatSurfaces = true
        )
    } else {
        EchoThemeStyle()
    }

val LocalEchoThemeStyle = staticCompositionLocalOf { EchoThemeStyle() }
