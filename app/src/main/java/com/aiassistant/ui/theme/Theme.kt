package com.aiassistant.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.unit.dp
import com.aiassistant.utils.EchoAppearancePreset

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    outline = Outline,
    outlineVariant = OutlineVariant,
    surfaceTint = Primary,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFF1F5F9),
    surfaceContainerHigh = Color(0xFFE8EEF5),
    surfaceContainerHighest = Color(0xFFE2E8F0)
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    surfaceTint = DarkPrimary,
    surfaceContainerLowest = Color(0xFF0A0F1E),
    surfaceContainerLow = Color(0xFF0E1526),
    surfaceContainer = Color(0xFF11192C),
    surfaceContainerHigh = Color(0xFF18233C),
    surfaceContainerHighest = Color(0xFF1E293B)
)

private fun lightColorSchemeFor(appearancePreset: String): ColorScheme = when (EchoAppearancePreset.sanitize(appearancePreset)) {
    EchoAppearancePreset.MONO -> LightColorScheme.copy(
        primary = Color(0xFF242424),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE9E9E9),
        onPrimaryContainer = Color(0xFF202020),
        secondary = Color(0xFF454545),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFEDEDED),
        onSecondaryContainer = Color(0xFF242424),
        tertiary = Color(0xFF5A5A5A),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFF0F0F0),
        onTertiaryContainer = Color(0xFF303030),
        background = Color.White,
        onBackground = Color(0xFF202020),
        surface = Color.White,
        onSurface = Color(0xFF202020),
        surfaceVariant = Color(0xFFF5F5F5),
        onSurfaceVariant = Color(0xFF606060),
        outline = Color(0xFF777777),
        outlineVariant = Color(0xFFD9D9D9),
        surfaceTint = Color.Transparent,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFFAFAFA),
        surfaceContainer = Color(0xFFF5F5F5),
        surfaceContainerHigh = Color(0xFFEFEFEF),
        surfaceContainerHighest = Color(0xFFE9E9E9)
    )
    EchoAppearancePreset.CLEAR_SKY -> LightColorScheme.copy(
        primary = Color(0xFF2C73B7),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFDCEBFF),
        onPrimaryContainer = Color(0xFF12345D),
        secondary = Color(0xFF197C9E),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFC5EDFF),
        onSecondaryContainer = Color(0xFF073A4C),
        tertiary = Color(0xFF536DB3),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE0E7FF),
        onTertiaryContainer = Color(0xFF1C2B5A),
        background = Color(0xFFFBFDFF),
        onBackground = Color(0xFF21364B),
        surface = Color.White,
        onSurface = Color(0xFF21364B),
        surfaceVariant = Color(0xFFEDF4FC),
        onSurfaceVariant = Color(0xFF526779),
        outline = Color(0xFF718496),
        outlineVariant = Color(0xFFE0E8F0),
        surfaceTint = Color(0xFF2C73B7),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF5F9FE),
        surfaceContainer = Color(0xFFEDF4FC),
        surfaceContainerHigh = Color(0xFFE6EFF8),
        surfaceContainerHighest = Color(0xFFDEE8F3)
    )
    EchoAppearancePreset.NIGHT_VOYAGE -> LightColorScheme.copy(
        primary = Color(0xFF486657),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD7E9DD),
        onPrimaryContainer = Color(0xFF172B29),
        secondary = Color(0xFF795C23),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFF2E2BB),
        onSecondaryContainer = Color(0xFF3A2B0D),
        tertiary = Color(0xFF526A6B),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFD9E9E7),
        onTertiaryContainer = Color(0xFF1B3635),
        background = Color(0xFFF3F6F2),
        onBackground = Color(0xFF20372F),
        surface = Color(0xFFFAFCF8),
        onSurface = Color(0xFF20372F),
        surfaceVariant = Color(0xFFE3EBE3),
        onSurfaceVariant = Color(0xFF536657),
        outline = Color(0xFF758577),
        outlineVariant = Color(0xFFCDD8CC),
        surfaceTint = Color(0xFF486657),
        surfaceContainerLowest = Color(0xFFFEFFFB),
        surfaceContainerLow = Color(0xFFF3F6EF),
        surfaceContainer = Color(0xFFEDF2E9),
        surfaceContainerHigh = Color(0xFFE6ECE2),
        surfaceContainerHighest = Color(0xFFDFE6DB)
    )
    EchoAppearancePreset.INDEX -> LightColorScheme.copy(
        primary = Color(0xFFAF4D34),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFF2DED3),
        onPrimaryContainer = Color(0xFF582314),
        secondary = Color(0xFF706354),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFEFE3D4),
        onSecondaryContainer = Color(0xFF3F3325),
        tertiary = Color(0xFF6B704E),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE7E9D2),
        onTertiaryContainer = Color(0xFF30351B),
        background = Color(0xFFFAF8F3),
        onBackground = Color(0xFF312D29),
        surface = Color(0xFFFFFCF7),
        onSurface = Color(0xFF312D29),
        surfaceVariant = Color(0xFFF0E7DF),
        onSurfaceVariant = Color(0xFF695F54),
        outline = Color(0xFF877B6D),
        outlineVariant = Color(0xFFDDD6CD),
        surfaceTint = Color(0xFFAF4D34),
        surfaceContainerLowest = Color(0xFFFFFDFA),
        surfaceContainerLow = Color(0xFFF9F3EB),
        surfaceContainer = Color(0xFFF3EDE4),
        surfaceContainerHigh = Color(0xFFEDE5DC),
        surfaceContainerHighest = Color(0xFFE7DED4)
    )
    EchoAppearancePreset.DECKLE -> LightColorScheme.copy(
        primary = Color(0xFF8B3E2D),
        onPrimary = Color(0xFFFFF9ED),
        primaryContainer = Color(0xFFE8CDBD),
        onPrimaryContainer = Color(0xFF4D1F17),
        secondary = Color(0xFF6A5542),
        onSecondary = Color(0xFFFFF8EE),
        secondaryContainer = Color(0xFFE9DCC7),
        onSecondaryContainer = Color(0xFF2F241B),
        tertiary = Color(0xFF5C6A46),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFDEE6C9),
        onTertiaryContainer = Color(0xFF202B17),
        background = Color(0xFFF2E8D3),
        onBackground = Color(0xFF382E24),
        surface = Color(0xFFF8EFDF),
        onSurface = Color(0xFF382E24),
        surfaceVariant = Color(0xFFEADDC7),
        onSurfaceVariant = Color(0xFF5A4B3A),
        outline = Color(0xFF7B6A56),
        outlineVariant = Color(0xFFC9B9A0),
        surfaceTint = Color(0xFF8B3E2D),
        surfaceContainerLowest = Color(0xFFFFFBF2),
        surfaceContainerLow = Color(0xFFFBF2E3),
        surfaceContainer = Color(0xFFF4E8D4),
        surfaceContainerHigh = Color(0xFFEEDFC8),
        surfaceContainerHighest = Color(0xFFE6D5BC)
    )
    EchoAppearancePreset.COTTON -> LightColorScheme.copy(
        primary = Color(0xFF375D70),
        onPrimary = Color(0xFFF8FCFF),
        primaryContainer = Color(0xFFD5E5EA),
        onPrimaryContainer = Color(0xFF183643),
        secondary = Color(0xFF57696A),
        onSecondary = Color(0xFFF8FCFB),
        secondaryContainer = Color(0xFFDDE7E5),
        onSecondaryContainer = Color(0xFF263A3B),
        tertiary = Color(0xFF5E7565),
        onTertiary = Color(0xFFF8FCF8),
        tertiaryContainer = Color(0xFFDCE8DE),
        onTertiaryContainer = Color(0xFF273A2B),
        background = Color(0xFFF4F5F1),
        onBackground = Color(0xFF2A373A),
        surface = Color(0xFFFAFBF7),
        onSurface = Color(0xFF2A373A),
        surfaceVariant = Color(0xFFE7ECE8),
        onSurfaceVariant = Color(0xFF536568),
        outline = Color(0xFF6F8181),
        outlineVariant = Color(0xFFC6CFCC),
        surfaceTint = Color(0xFF375D70),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFAFCF9),
        surfaceContainer = Color(0xFFF2F5F1),
        surfaceContainerHigh = Color(0xFFEAEFEC),
        surfaceContainerHighest = Color(0xFFE1E9E5)
    )
    EchoAppearancePreset.FOLIO -> LightColorScheme.copy(
        primary = Color(0xFF6F3345),
        onPrimary = Color(0xFFFFF9F2),
        primaryContainer = Color(0xFFE8D4D5),
        onPrimaryContainer = Color(0xFF401B28),
        secondary = Color(0xFF765B38),
        onSecondary = Color(0xFFFFFAF0),
        secondaryContainer = Color(0xFFE7DAC4),
        onSecondaryContainer = Color(0xFF3D2C17),
        tertiary = Color(0xFF385D5A),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFD4E1DB),
        onTertiaryContainer = Color(0xFF193531),
        background = Color(0xFFF3EDDF),
        onBackground = Color(0xFF2D2A26),
        surface = Color(0xFFFAF5E9),
        onSurface = Color(0xFF2D2A26),
        surfaceVariant = Color(0xFFE9E0CE),
        onSurfaceVariant = Color(0xFF625A4D),
        outline = Color(0xFF817665),
        outlineVariant = Color(0xFFCFC2AC),
        surfaceTint = Color(0xFF6F3345),
        surfaceContainerLowest = Color(0xFFFFFDF7),
        surfaceContainerLow = Color(0xFFFCF7EC),
        surfaceContainer = Color(0xFFF5EEDF),
        surfaceContainerHigh = Color(0xFFEDE3D1),
        surfaceContainerHighest = Color(0xFFE4D8C3)
    )
    EchoAppearancePreset.SONAR -> LightColorScheme.copy(
        primary = Color(0xFF007C69),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFC5E8DF),
        onPrimaryContainer = Color(0xFF073C33),
        secondary = Color(0xFF236477),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFC8E0E6),
        onSecondaryContainer = Color(0xFF113944),
        tertiary = Color(0xFF596B45),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFDCE5D2),
        onTertiaryContainer = Color(0xFF2D3B20),
        background = Color(0xFFEAF1EE),
        onBackground = Color(0xFF132824),
        surface = Color(0xFFF4F8F5),
        onSurface = Color(0xFF132824),
        surfaceVariant = Color(0xFFD9E5E0),
        onSurfaceVariant = Color(0xFF48625B),
        outline = Color(0xFF5E7870),
        outlineVariant = Color(0xFFB3C8C0),
        surfaceTint = Color(0xFF007C69),
        surfaceContainerLowest = Color(0xFFFBFDFC),
        surfaceContainerLow = Color(0xFFF1F7F4),
        surfaceContainer = Color(0xFFE5EFEB),
        surfaceContainerHigh = Color(0xFFDAE7E2),
        surfaceContainerHighest = Color(0xFFCFDFD8)
    )
    EchoAppearancePreset.BAUHAUS -> LightColorScheme.copy(
        primary = Color(0xFFC93F32),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFF2C2B8),
        onPrimaryContainer = Color(0xFF531710),
        secondary = Color(0xFF1668B4),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFC4DCF2),
        onSecondaryContainer = Color(0xFF0B355E),
        tertiary = Color(0xFFE2AC18),
        onTertiary = Color(0xFF211B08),
        tertiaryContainer = Color(0xFFF3DF9A),
        onTertiaryContainer = Color(0xFF423208),
        background = Color(0xFFF2EDDF),
        onBackground = Color(0xFF22221E),
        surface = Color(0xFFFFFBEF),
        onSurface = Color(0xFF22221E),
        surfaceVariant = Color(0xFFE7DFCE),
        onSurfaceVariant = Color(0xFF565249),
        outline = Color(0xFF292924),
        outlineVariant = Color(0xFF777166),
        surfaceTint = Color(0xFFC93F32),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFFFBEF),
        surfaceContainer = Color(0xFFF3EBD9),
        surfaceContainerHigh = Color(0xFFE9DFC9),
        surfaceContainerHighest = Color(0xFFDCD1BA)
    )
    else -> LightColorScheme
}

private fun darkColorSchemeFor(appearancePreset: String): ColorScheme = when (EchoAppearancePreset.sanitize(appearancePreset)) {
    EchoAppearancePreset.MONO -> DarkColorScheme.copy(
        primary = Color(0xFFE0E0E0),
        onPrimary = Color(0xFF181818),
        primaryContainer = Color(0xFF303030),
        onPrimaryContainer = Color(0xFFEEEEEE),
        secondary = Color(0xFFD0D0D0),
        onSecondary = Color(0xFF181818),
        secondaryContainer = Color(0xFF2A2A2A),
        onSecondaryContainer = Color(0xFFE5E5E5),
        tertiary = Color(0xFFBBBBBB),
        onTertiary = Color(0xFF181818),
        tertiaryContainer = Color(0xFF262626),
        onTertiaryContainer = Color(0xFFE0E0E0),
        background = Color(0xFF121212),
        onBackground = Color(0xFFEEEEEE),
        surface = Color(0xFF1D1D1D),
        onSurface = Color(0xFFEEEEEE),
        surfaceVariant = Color(0xFF262626),
        onSurfaceVariant = Color(0xFFB0B0B0),
        outline = Color(0xFF929292),
        outlineVariant = Color(0xFF454545),
        surfaceTint = Color.Transparent,
        surfaceContainerLowest = Color(0xFF0D0D0D),
        surfaceContainerLow = Color(0xFF171717),
        surfaceContainer = Color(0xFF1D1D1D),
        surfaceContainerHigh = Color(0xFF262626),
        surfaceContainerHighest = Color(0xFF303030)
    )
    EchoAppearancePreset.CLEAR_SKY -> DarkColorScheme.copy(
        primary = Color(0xFF9CC4FF),
        onPrimary = Color(0xFF08284F),
        primaryContainer = Color(0xFF1A4A82),
        onPrimaryContainer = Color(0xFFD8E9FF),
        secondary = Color(0xFF83D3F0),
        onSecondary = Color(0xFF003546),
        secondaryContainer = Color(0xFF075A73),
        onSecondaryContainer = Color(0xFFC5EDFF),
        tertiary = Color(0xFFB8C6FF),
        onTertiary = Color(0xFF202F63),
        tertiaryContainer = Color(0xFF394A82),
        onTertiaryContainer = Color(0xFFE0E7FF),
        background = Color(0xFF0E1925),
        onBackground = Color(0xFFE4EDF6),
        surface = Color(0xFF172838),
        onSurface = Color(0xFFE4EDF6),
        surfaceVariant = Color(0xFF243B4E),
        onSurfaceVariant = Color(0xFFB8CADB),
        outline = Color(0xFF8298AB),
        outlineVariant = Color(0xFF3B5367),
        surfaceTint = Color(0xFF9CC4FF),
        surfaceContainerLowest = Color(0xFF09131D),
        surfaceContainerLow = Color(0xFF132331),
        surfaceContainer = Color(0xFF192C3B),
        surfaceContainerHigh = Color(0xFF213747),
        surfaceContainerHighest = Color(0xFF2A4254)
    )
    EchoAppearancePreset.NIGHT_VOYAGE -> DarkColorScheme.copy(
        primary = Color(0xFFD6BB83),
        onPrimary = Color(0xFF172B29),
        primaryContainer = Color(0xFF55482D),
        onPrimaryContainer = Color(0xFFF1DEB5),
        secondary = Color(0xFFA5C9AD),
        onSecondary = Color(0xFF20372A),
        secondaryContainer = Color(0xFF354F3D),
        onSecondaryContainer = Color(0xFFD7E9DD),
        tertiary = Color(0xFFAECBD0),
        onTertiary = Color(0xFF183539),
        tertiaryContainer = Color(0xFF365358),
        onTertiaryContainer = Color(0xFFD9E9E7),
        background = Color(0xFF172B29),
        onBackground = Color(0xFFECEDE2),
        surface = Color(0xFF203630),
        onSurface = Color(0xFFECEDE2),
        surfaceVariant = Color(0xFF2A443A),
        onSurfaceVariant = Color(0xFFB9C8BB),
        outline = Color(0xFF829388),
        outlineVariant = Color(0xFF3F5548),
        surfaceTint = Color(0xFFD6BB83),
        surfaceContainerLowest = Color(0xFF10211D),
        surfaceContainerLow = Color(0xFF1A2D27),
        surfaceContainer = Color(0xFF20362E),
        surfaceContainerHigh = Color(0xFF294137),
        surfaceContainerHighest = Color(0xFF324B40)
    )
    EchoAppearancePreset.INDEX -> DarkColorScheme.copy(
        primary = Color(0xFFECA48B),
        onPrimary = Color(0xFF522415),
        primaryContainer = Color(0xFF783C29),
        onPrimaryContainer = Color(0xFFF2DED3),
        secondary = Color(0xFFD2C2AD),
        onSecondary = Color(0xFF3F3325),
        secondaryContainer = Color(0xFF574B3B),
        onSecondaryContainer = Color(0xFFEFE3D4),
        tertiary = Color(0xFFC4C99E),
        onTertiary = Color(0xFF30351B),
        tertiaryContainer = Color(0xFF4D5234),
        onTertiaryContainer = Color(0xFFE7E9D2),
        background = Color(0xFF221E1B),
        onBackground = Color(0xFFF1E8DE),
        surface = Color(0xFF2E2823),
        onSurface = Color(0xFFF1E8DE),
        surfaceVariant = Color(0xFF42382F),
        onSurfaceVariant = Color(0xFFD5C7B9),
        outline = Color(0xFFA19282),
        outlineVariant = Color(0xFF584D41),
        surfaceTint = Color(0xFFECA48B),
        surfaceContainerLowest = Color(0xFF1C1815),
        surfaceContainerLow = Color(0xFF28221D),
        surfaceContainer = Color(0xFF302922),
        surfaceContainerHigh = Color(0xFF3B322A),
        surfaceContainerHighest = Color(0xFF463B32)
    )
    EchoAppearancePreset.DECKLE -> DarkColorScheme.copy(
        primary = Color(0xFFEAA48E),
        onPrimary = Color(0xFF4D1F17),
        primaryContainer = Color(0xFF713A2D),
        onPrimaryContainer = Color(0xFFFFE2D7),
        secondary = Color(0xFFD8B58D),
        onSecondary = Color(0xFF332417),
        secondaryContainer = Color(0xFF5C4533),
        onSecondaryContainer = Color(0xFFF7E6CF),
        tertiary = Color(0xFFC4D09B),
        onTertiary = Color(0xFF29331C),
        tertiaryContainer = Color(0xFF465337),
        onTertiaryContainer = Color(0xFFE5EFCB),
        background = Color(0xFF2A211B),
        onBackground = Color(0xFFF7EAD4),
        surface = Color(0xFF35281F),
        onSurface = Color(0xFFF7EAD4),
        surfaceVariant = Color(0xFF49392D),
        onSurfaceVariant = Color(0xFFD3BFA4),
        outline = Color(0xFFA99477),
        outlineVariant = Color(0xFF5A4939),
        surfaceTint = Color(0xFFEAA48E),
        surfaceContainerLowest = Color(0xFF211914),
        surfaceContainerLow = Color(0xFF2E241C),
        surfaceContainer = Color(0xFF382A20),
        surfaceContainerHigh = Color(0xFF423126),
        surfaceContainerHighest = Color(0xFF4B382B)
    )
    EchoAppearancePreset.COTTON -> DarkColorScheme.copy(
        primary = Color(0xFF9CC9DD),
        onPrimary = Color(0xFF0D2D3B),
        primaryContainer = Color(0xFF2F586A),
        onPrimaryContainer = Color(0xFFD5EBF3),
        secondary = Color(0xFFB7CDCB),
        onSecondary = Color(0xFF1B3032),
        secondaryContainer = Color(0xFF3B5153),
        onSecondaryContainer = Color(0xFFDDEBEA),
        tertiary = Color(0xFFB7D0BD),
        onTertiary = Color(0xFF193124),
        tertiaryContainer = Color(0xFF3B5442),
        onTertiaryContainer = Color(0xFFDAEBDD),
        background = Color(0xFF1F292B),
        onBackground = Color(0xFFE8F1EF),
        surface = Color(0xFF293538),
        onSurface = Color(0xFFE8F1EF),
        surfaceVariant = Color(0xFF344347),
        onSurfaceVariant = Color(0xFFB5C8C8),
        outline = Color(0xFF91A6A5),
        outlineVariant = Color(0xFF4C6262),
        surfaceTint = Color(0xFF9CC9DD),
        surfaceContainerLowest = Color(0xFF172022),
        surfaceContainerLow = Color(0xFF222D30),
        surfaceContainer = Color(0xFF2A373A),
        surfaceContainerHigh = Color(0xFF334246),
        surfaceContainerHighest = Color(0xFF3B4D51)
    )
    EchoAppearancePreset.FOLIO -> DarkColorScheme.copy(
        primary = Color(0xFFE6B3BD),
        onPrimary = Color(0xFF401B28),
        primaryContainer = Color(0xFF61313F),
        onPrimaryContainer = Color(0xFFF4DDE0),
        secondary = Color(0xFFD6BB8F),
        onSecondary = Color(0xFF382912),
        secondaryContainer = Color(0xFF52432C),
        onSecondaryContainer = Color(0xFFEEE0C5),
        tertiary = Color(0xFFA8C8BD),
        onTertiary = Color(0xFF193531),
        tertiaryContainer = Color(0xFF354E48),
        onTertiaryContainer = Color(0xFFD8E8E0),
        background = Color(0xFF26221E),
        onBackground = Color(0xFFF0E6D4),
        surface = Color(0xFF322C26),
        onSurface = Color(0xFFF0E6D4),
        surfaceVariant = Color(0xFF453B31),
        onSurfaceVariant = Color(0xFFD0C1AA),
        outline = Color(0xFFAA9980),
        outlineVariant = Color(0xFF5B4D3E),
        surfaceTint = Color(0xFFE6B3BD),
        surfaceContainerLowest = Color(0xFF1D1A17),
        surfaceContainerLow = Color(0xFF2B2621),
        surfaceContainer = Color(0xFF352E27),
        surfaceContainerHigh = Color(0xFF40372D),
        surfaceContainerHighest = Color(0xFF4B4034)
    )
    EchoAppearancePreset.SONAR -> DarkColorScheme.copy(
        primary = Color(0xFF63F5D2),
        onPrimary = Color(0xFF04352C),
        primaryContainer = Color(0xFF125648),
        onPrimaryContainer = Color(0xFFC9F9E9),
        secondary = Color(0xFF82D7E8),
        onSecondary = Color(0xFF103943),
        secondaryContainer = Color(0xFF1C4B56),
        onSecondaryContainer = Color(0xFFD1EEF3),
        tertiary = Color(0xFFC0D798),
        onTertiary = Color(0xFF2D3B20),
        tertiaryContainer = Color(0xFF3E5031),
        onTertiaryContainer = Color(0xFFE0EBCF),
        background = Color(0xFF071411),
        onBackground = Color(0xFFD8EAE3),
        surface = Color(0xFF10211C),
        onSurface = Color(0xFFD8EAE3),
        surfaceVariant = Color(0xFF1B342C),
        onSurfaceVariant = Color(0xFFA8C4B9),
        outline = Color(0xFF71958A),
        outlineVariant = Color(0xFF315248),
        surfaceTint = Color(0xFF63F5D2),
        surfaceContainerLowest = Color(0xFF040D0B),
        surfaceContainerLow = Color(0xFF0C1B17),
        surfaceContainer = Color(0xFF122720),
        surfaceContainerHigh = Color(0xFF19332B),
        surfaceContainerHighest = Color(0xFF213F35)
    )
    EchoAppearancePreset.BAUHAUS -> DarkColorScheme.copy(
        primary = Color(0xFFFF796B),
        onPrimary = Color(0xFF46130D),
        primaryContainer = Color(0xFF793027),
        onPrimaryContainer = Color(0xFFFFDAD3),
        secondary = Color(0xFF79BBFF),
        onSecondary = Color(0xFF0B355E),
        secondaryContainer = Color(0xFF254F78),
        onSecondaryContainer = Color(0xFFD6E8FF),
        tertiary = Color(0xFFFFD552),
        onTertiary = Color(0xFF423208),
        tertiaryContainer = Color(0xFF6C571E),
        onTertiaryContainer = Color(0xFFFFEDB2),
        background = Color(0xFF191915),
        onBackground = Color(0xFFF2EBDD),
        surface = Color(0xFF27271F),
        onSurface = Color(0xFFF2EBDD),
        surfaceVariant = Color(0xFF3B392E),
        onSurfaceVariant = Color(0xFFD0C7B5),
        outline = Color(0xFFF0E7D5),
        outlineVariant = Color(0xFF918876),
        surfaceTint = Color(0xFFFF796B),
        surfaceContainerLowest = Color(0xFF10100D),
        surfaceContainerLow = Color(0xFF20201A),
        surfaceContainer = Color(0xFF2B2A22),
        surfaceContainerHigh = Color(0xFF38362B),
        surfaceContainerHighest = Color(0xFF454135)
    )
    else -> DarkColorScheme
}

/** Complete the semantic surface roles so menus, sheets, snackbars and dialogs use the same preset. */
internal fun echoColorScheme(appearancePreset: String, darkTheme: Boolean): ColorScheme {
    val colors = if (darkTheme) darkColorSchemeFor(appearancePreset) else lightColorSchemeFor(appearancePreset)
    val inverse = if (darkTheme) lightColorSchemeFor(appearancePreset) else darkColorSchemeFor(appearancePreset)
    return colors.copy(
        surfaceBright = if (darkTheme) colors.surfaceContainerHighest else colors.surface,
        surfaceDim = if (darkTheme) colors.background else colors.surfaceContainerHigh,
        inverseSurface = inverse.surface,
        inverseOnSurface = inverse.onSurface,
        inversePrimary = inverse.primary,
        scrim = Color.Black
    )
}

internal fun echoShapesFor(appearancePreset: String): Shapes {
    val style = echoThemeStyleFor(appearancePreset)
    return when {
        style.cutCorners -> Shapes(
            extraSmall = CutCornerShape(4.dp),
            small = CutCornerShape(6.dp),
            medium = CutCornerShape(8.dp),
            large = CutCornerShape(12.dp),
            extraLarge = CutCornerShape(16.dp)
        )
        style.flatSurfaces -> Shapes(
            extraSmall = RoundedCornerShape(2.dp),
            small = RoundedCornerShape(style.controlCornerRadius),
            medium = RoundedCornerShape(style.panelCornerRadius),
            large = RoundedCornerShape(style.dialogCornerRadius),
            extraLarge = RoundedCornerShape(style.dialogCornerRadius)
        )
        else -> Shapes()
    }
}

@Composable
fun AiApiAssistantTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // 默认不使用动态颜色，保持设计一致性
    appearancePreset: String = EchoAppearancePreset.EXISTING,
    content: @Composable () -> Unit
) {
    val themeStyle = echoThemeStyleFor(appearancePreset)
    val colorScheme = when {
        dynamicColor && EchoAppearancePreset.sanitize(appearancePreset) == EchoAppearancePreset.EXISTING &&
            !themeStyle.isMonochrome && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        else -> echoColorScheme(appearancePreset, darkTheme)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalEchoThemeStyle provides themeStyle) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = echoTypographyFor(appearancePreset),
            shapes = echoShapesFor(appearancePreset),
            content = content
        )
    }
}
