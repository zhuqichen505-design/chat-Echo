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
enum class EchoSurfacePattern { None, SonarGrid, CeladonCrackle, BlueprintDraft, LacquerFrame }

/** 卡片、弹窗与气泡共享的细部边框工艺，避免主题停留在单一换色。 */
enum class EchoFrameOrnament { None, CeramicRim, BlueprintBrackets, LacquerDoubleFrame }

data class EchoThemeStyle(
    val isMonochrome: Boolean = false,
    val panelCornerRadius: Dp = 18.dp,
    val controlCornerRadius: Dp = 12.dp,
    val dialogCornerRadius: Dp = 24.dp,
    val flatSurfaces: Boolean = false,
    val paperMaterial: EchoPaperMaterial = EchoPaperMaterial.None,
    val surfacePattern: EchoSurfacePattern = EchoSurfacePattern.None,
    val frameOrnament: EchoFrameOrnament = EchoFrameOrnament.None,
    val accentUserBubble: Boolean = false,
    val cutCorners: Boolean = false,
    val hardShadow: Boolean = false,
    val strongOutline: Boolean = false
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
        EchoAppearancePreset.FOLIO -> EchoThemeStyle(
            panelCornerRadius = 4.dp,
            controlCornerRadius = 4.dp,
            dialogCornerRadius = 8.dp,
            flatSurfaces = true,
            paperMaterial = EchoPaperMaterial.Folio
        )
        EchoAppearancePreset.SONAR -> EchoThemeStyle(
            panelCornerRadius = 12.dp,
            controlCornerRadius = 8.dp,
            dialogCornerRadius = 16.dp,
            flatSurfaces = true,
            surfacePattern = EchoSurfacePattern.SonarGrid,
            cutCorners = true
        )
        EchoAppearancePreset.BAUHAUS -> EchoThemeStyle(
            panelCornerRadius = 3.dp,
            controlCornerRadius = 3.dp,
            dialogCornerRadius = 4.dp,
            flatSurfaces = true,
            accentUserBubble = true,
            hardShadow = true,
            strongOutline = true
        )
        EchoAppearancePreset.CELADON -> EchoThemeStyle(
            panelCornerRadius = 20.dp,
            controlCornerRadius = 14.dp,
            dialogCornerRadius = 24.dp,
            flatSurfaces = true,
            paperMaterial = EchoPaperMaterial.Celadon,
            surfacePattern = EchoSurfacePattern.CeladonCrackle,
            frameOrnament = EchoFrameOrnament.CeramicRim,
            accentUserBubble = true
        )
        EchoAppearancePreset.BLUEPRINT -> EchoThemeStyle(
            panelCornerRadius = 2.dp,
            controlCornerRadius = 2.dp,
            dialogCornerRadius = 4.dp,
            flatSurfaces = true,
            surfacePattern = EchoSurfacePattern.BlueprintDraft,
            frameOrnament = EchoFrameOrnament.BlueprintBrackets
        )
        EchoAppearancePreset.LACQUER -> EchoThemeStyle(
            panelCornerRadius = 10.dp,
            controlCornerRadius = 8.dp,
            dialogCornerRadius = 14.dp,
            flatSurfaces = true,
            paperMaterial = EchoPaperMaterial.Lacquer,
            surfacePattern = EchoSurfacePattern.LacquerFrame,
            frameOrnament = EchoFrameOrnament.LacquerDoubleFrame,
            accentUserBubble = true
        )
        else -> EchoThemeStyle()
    }

val LocalEchoThemeStyle = staticCompositionLocalOf { EchoThemeStyle() }
