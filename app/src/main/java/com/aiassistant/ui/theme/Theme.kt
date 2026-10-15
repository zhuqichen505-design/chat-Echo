package com.aiassistant.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
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
    else -> LightColorScheme
}

private fun darkColorSchemeFor(appearancePreset: String): ColorScheme = when (EchoAppearancePreset.sanitize(appearancePreset)) {
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

@Composable
fun AiApiAssistantTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // 默认不使用动态颜色，保持设计一致性
    appearancePreset: String = EchoAppearancePreset.EXISTING,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
