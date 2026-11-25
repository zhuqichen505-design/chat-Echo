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
enum class EchoSurfacePattern {
    None, SkyCurrents, SonarGrid, CeladonCrackle, BlueprintDraft, LacquerFrame, IndexCatalogue, NewsRegister, FilmScanline,
    NauticalChart, SwissGrid, LetterColumns, BauhausGeometry, DeckleEdge
}

/** 卡片、弹窗与气泡共享的细部边框工艺，避免主题停留在单一换色。 */
enum class EchoFrameOrnament {
    None, SkyHorizon, CeramicRim, BlueprintBrackets, LacquerDoubleFrame, IndexTab, CropMarks, ReelPerforation,
    VoyageCompass, SwissRule, DeckleSpine, LetterSlip, FolioMargin, SonarReticle, BauhausBlock
}

enum class EchoHeaderStyle {
    Default, AiryHorizon, NauticalLog, CatalogueBar, SwissEditorial, BookPlate, VerseSlip, FolioChapter,
    TelemetryHud, BauhausBar, CeladonSeal, BlueprintSpec, LacquerPlaque, NewspaperMasthead, CinemaSlate
}

enum class EchoDividerStyle {
    Default, DoubleRule, DashedTick, HeavyEditorial, LiteraryOrnament, CatalogRule, FilmTrack,
    HorizonFade, VersePause, RadarPulse, CeramicWave, GiltDoubleRule
}

enum class EchoMarkdownHeadingStyle {
    Default, EditorialRule, TechnicalPrefix, LiteraryChapter, GeometricBlock, CinemaCue,
    AiryHorizon, NauticalHeading, CatalogHeading, VerseHeading, FolioChapter, TelemetryHud, CeladonSeal, LacquerPlaque
}

enum class EchoMessageMetaStyle {
    Default, TimecodeSubtitle, Marginalia, Telemetry, CatalogueStamp, Logbook,
    SwissStamp, Colophon, VerseNote, GeometricTag, SealMark, PlaqueStamp, PressDateline
}

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
    val strongOutline: Boolean = false,
    val headerStyle: EchoHeaderStyle = EchoHeaderStyle.Default,
    val dividerStyle: EchoDividerStyle = EchoDividerStyle.Default,
    val markdownHeadingStyle: EchoMarkdownHeadingStyle = EchoMarkdownHeadingStyle.Default,
    val messageMetaStyle: EchoMessageMetaStyle = EchoMessageMetaStyle.Default
)

internal fun echoThemeStyleFor(appearancePreset: String): EchoThemeStyle =
    when (EchoAppearancePreset.sanitize(appearancePreset)) {
        EchoAppearancePreset.CLEAR_SKY -> EchoThemeStyle(
            panelCornerRadius = 24.dp,
            controlCornerRadius = 16.dp,
            dialogCornerRadius = 28.dp,
            flatSurfaces = true,
            paperMaterial = EchoPaperMaterial.SkyMist,
            surfacePattern = EchoSurfacePattern.SkyCurrents,
            frameOrnament = EchoFrameOrnament.SkyHorizon,
            accentUserBubble = true,
            headerStyle = EchoHeaderStyle.AiryHorizon,
            dividerStyle = EchoDividerStyle.HorizonFade,
            markdownHeadingStyle = EchoMarkdownHeadingStyle.AiryHorizon,
            messageMetaStyle = EchoMessageMetaStyle.Default
        )
        EchoAppearancePreset.NIGHT_VOYAGE -> EchoThemeStyle(
            panelCornerRadius = 12.dp,
            controlCornerRadius = 8.dp,
            dialogCornerRadius = 16.dp,
            flatSurfaces = true,
            paperMaterial = EchoPaperMaterial.NauticalVellum,
            surfacePattern = EchoSurfacePattern.NauticalChart,
            frameOrnament = EchoFrameOrnament.VoyageCompass,
            accentUserBubble = true,
            headerStyle = EchoHeaderStyle.NauticalLog,
            dividerStyle = EchoDividerStyle.DashedTick,
            markdownHeadingStyle = EchoMarkdownHeadingStyle.NauticalHeading,
            messageMetaStyle = EchoMessageMetaStyle.Logbook
        )
        EchoAppearancePreset.INDEX -> EchoThemeStyle(
            panelCornerRadius = 6.dp,
            controlCornerRadius = 4.dp,
            dialogCornerRadius = 8.dp,
            flatSurfaces = true,
            paperMaterial = EchoPaperMaterial.IndexStock,
            surfacePattern = EchoSurfacePattern.IndexCatalogue,
            frameOrnament = EchoFrameOrnament.IndexTab,
            strongOutline = true,
            headerStyle = EchoHeaderStyle.CatalogueBar,
            dividerStyle = EchoDividerStyle.CatalogRule,
            markdownHeadingStyle = EchoMarkdownHeadingStyle.CatalogHeading,
            messageMetaStyle = EchoMessageMetaStyle.CatalogueStamp
        )
        EchoAppearancePreset.MONO -> EchoThemeStyle(
            isMonochrome = true,
            panelCornerRadius = 4.dp,
            controlCornerRadius = 3.dp,
            dialogCornerRadius = 6.dp,
            flatSurfaces = true,
            surfacePattern = EchoSurfacePattern.SwissGrid,
            frameOrnament = EchoFrameOrnament.SwissRule,
            strongOutline = true,
            headerStyle = EchoHeaderStyle.SwissEditorial,
            dividerStyle = EchoDividerStyle.HeavyEditorial,
            markdownHeadingStyle = EchoMarkdownHeadingStyle.EditorialRule,
            messageMetaStyle = EchoMessageMetaStyle.SwissStamp
        )
        EchoAppearancePreset.DECKLE -> EchoThemeStyle(
            panelCornerRadius = 6.dp,
            controlCornerRadius = 6.dp,
            dialogCornerRadius = 12.dp,
            flatSurfaces = true,
            paperMaterial = EchoPaperMaterial.Deckle,
            surfacePattern = EchoSurfacePattern.DeckleEdge,
            frameOrnament = EchoFrameOrnament.DeckleSpine,
            headerStyle = EchoHeaderStyle.BookPlate,
            dividerStyle = EchoDividerStyle.LiteraryOrnament,
            markdownHeadingStyle = EchoMarkdownHeadingStyle.LiteraryChapter,
            messageMetaStyle = EchoMessageMetaStyle.Colophon
        )
        EchoAppearancePreset.COTTON -> EchoThemeStyle(
            panelCornerRadius = 8.dp,
            controlCornerRadius = 8.dp,
            dialogCornerRadius = 14.dp,
            flatSurfaces = true,
            paperMaterial = EchoPaperMaterial.Cotton,
            surfacePattern = EchoSurfacePattern.LetterColumns,
            frameOrnament = EchoFrameOrnament.LetterSlip,
            headerStyle = EchoHeaderStyle.VerseSlip,
            dividerStyle = EchoDividerStyle.VersePause,
            markdownHeadingStyle = EchoMarkdownHeadingStyle.VerseHeading,
            messageMetaStyle = EchoMessageMetaStyle.VerseNote
        )
        EchoAppearancePreset.FOLIO -> EchoThemeStyle(
            panelCornerRadius = 4.dp,
            controlCornerRadius = 4.dp,
            dialogCornerRadius = 8.dp,
            flatSurfaces = true,
            paperMaterial = EchoPaperMaterial.Folio,
            frameOrnament = EchoFrameOrnament.FolioMargin,
            headerStyle = EchoHeaderStyle.FolioChapter,
            dividerStyle = EchoDividerStyle.LiteraryOrnament,
            markdownHeadingStyle = EchoMarkdownHeadingStyle.FolioChapter,
            messageMetaStyle = EchoMessageMetaStyle.Marginalia
        )
        EchoAppearancePreset.SONAR -> EchoThemeStyle(
            panelCornerRadius = 12.dp,
            controlCornerRadius = 8.dp,
            dialogCornerRadius = 16.dp,
            flatSurfaces = true,
            surfacePattern = EchoSurfacePattern.SonarGrid,
            frameOrnament = EchoFrameOrnament.SonarReticle,
            cutCorners = true,
            headerStyle = EchoHeaderStyle.TelemetryHud,
            dividerStyle = EchoDividerStyle.RadarPulse,
            markdownHeadingStyle = EchoMarkdownHeadingStyle.TelemetryHud,
            messageMetaStyle = EchoMessageMetaStyle.Telemetry
        )
        EchoAppearancePreset.BAUHAUS -> EchoThemeStyle(
            panelCornerRadius = 3.dp,
            controlCornerRadius = 3.dp,
            dialogCornerRadius = 4.dp,
            flatSurfaces = true,
            surfacePattern = EchoSurfacePattern.BauhausGeometry,
            frameOrnament = EchoFrameOrnament.BauhausBlock,
            accentUserBubble = true,
            hardShadow = true,
            strongOutline = true,
            headerStyle = EchoHeaderStyle.BauhausBar,
            dividerStyle = EchoDividerStyle.HeavyEditorial,
            markdownHeadingStyle = EchoMarkdownHeadingStyle.GeometricBlock,
            messageMetaStyle = EchoMessageMetaStyle.GeometricTag
        )
        EchoAppearancePreset.CELADON -> EchoThemeStyle(
            panelCornerRadius = 20.dp,
            controlCornerRadius = 14.dp,
            dialogCornerRadius = 24.dp,
            flatSurfaces = true,
            paperMaterial = EchoPaperMaterial.Celadon,
            surfacePattern = EchoSurfacePattern.CeladonCrackle,
            frameOrnament = EchoFrameOrnament.CeramicRim,
            accentUserBubble = true,
            headerStyle = EchoHeaderStyle.CeladonSeal,
            dividerStyle = EchoDividerStyle.CeramicWave,
            markdownHeadingStyle = EchoMarkdownHeadingStyle.CeladonSeal,
            messageMetaStyle = EchoMessageMetaStyle.SealMark
        )
        EchoAppearancePreset.BLUEPRINT -> EchoThemeStyle(
            panelCornerRadius = 2.dp,
            controlCornerRadius = 2.dp,
            dialogCornerRadius = 4.dp,
            flatSurfaces = true,
            surfacePattern = EchoSurfacePattern.BlueprintDraft,
            frameOrnament = EchoFrameOrnament.BlueprintBrackets,
            headerStyle = EchoHeaderStyle.BlueprintSpec,
            dividerStyle = EchoDividerStyle.DashedTick,
            markdownHeadingStyle = EchoMarkdownHeadingStyle.TechnicalPrefix,
            messageMetaStyle = EchoMessageMetaStyle.Telemetry
        )
        EchoAppearancePreset.LACQUER -> EchoThemeStyle(
            panelCornerRadius = 10.dp,
            controlCornerRadius = 8.dp,
            dialogCornerRadius = 14.dp,
            flatSurfaces = true,
            paperMaterial = EchoPaperMaterial.Lacquer,
            surfacePattern = EchoSurfacePattern.LacquerFrame,
            frameOrnament = EchoFrameOrnament.LacquerDoubleFrame,
            accentUserBubble = true,
            headerStyle = EchoHeaderStyle.LacquerPlaque,
            dividerStyle = EchoDividerStyle.GiltDoubleRule,
            markdownHeadingStyle = EchoMarkdownHeadingStyle.LacquerPlaque,
            messageMetaStyle = EchoMessageMetaStyle.PlaqueStamp
        )
        EchoAppearancePreset.EDITION -> EchoThemeStyle(
            panelCornerRadius = 2.dp,
            controlCornerRadius = 2.dp,
            dialogCornerRadius = 4.dp,
            flatSurfaces = true,
            paperMaterial = EchoPaperMaterial.Newsprint,
            surfacePattern = EchoSurfacePattern.NewsRegister,
            frameOrnament = EchoFrameOrnament.CropMarks,
            accentUserBubble = false,
            strongOutline = true,
            headerStyle = EchoHeaderStyle.NewspaperMasthead,
            dividerStyle = EchoDividerStyle.DoubleRule,
            markdownHeadingStyle = EchoMarkdownHeadingStyle.EditorialRule,
            messageMetaStyle = EchoMessageMetaStyle.PressDateline
        )
        EchoAppearancePreset.SCREENING -> EchoThemeStyle(
            panelCornerRadius = 8.dp,
            controlCornerRadius = 5.dp,
            dialogCornerRadius = 10.dp,
            flatSurfaces = true,
            paperMaterial = EchoPaperMaterial.ProjectorGrain,
            surfacePattern = EchoSurfacePattern.FilmScanline,
            frameOrnament = EchoFrameOrnament.ReelPerforation,
            accentUserBubble = true,
            strongOutline = true,
            headerStyle = EchoHeaderStyle.CinemaSlate,
            dividerStyle = EchoDividerStyle.FilmTrack,
            markdownHeadingStyle = EchoMarkdownHeadingStyle.CinemaCue,
            messageMetaStyle = EchoMessageMetaStyle.TimecodeSubtitle
        )
        else -> EchoThemeStyle()
    }

val LocalEchoThemeStyle = staticCompositionLocalOf { EchoThemeStyle() }
