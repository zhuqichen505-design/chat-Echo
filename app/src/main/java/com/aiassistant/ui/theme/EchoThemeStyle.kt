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
    val flatSurfaces: Boolean = false,
    val paperMaterial: EchoPaperMaterial = EchoPaperMaterial.None
)

internal fun echoThemeStyleFor(appearancePreset: String): EchoThemeStyle =
    when (EchoAppearancePreset.sanitize(appearancePreset)) {
        EchoAppearancePreset.MONO -> EchoThemeStyle(
            isMonochrome = true,
            panelCornerRadius = 8.dp,
            controlCornerRadius = 6.dp,
            dialogCornerRadius = 12.dp,
            flatSurfaces = true
        )
        EchoAppearancePreset.DECKLE -> EchoThemeStyle(
            panelCornerRadius = 6.dp,
            controlCornerRadius = 6.dp,
            dialogCornerRadius = 12.dp,
            flatSurfaces = true,
            paperMaterial = EchoPaperMaterial.Deckle
        )
        EchoAppearancePreset.COTTON -> EchoThemeStyle(
            panelCornerRadius = 8.dp,
            controlCornerRadius = 8.dp,
            dialogCornerRadius = 14.dp,
            flatSurfaces = true,
            paperMaterial = EchoPaperMaterial.Cotton
        )
        else -> EchoThemeStyle()
    }

val LocalEchoThemeStyle = staticCompositionLocalOf { EchoThemeStyle() }
