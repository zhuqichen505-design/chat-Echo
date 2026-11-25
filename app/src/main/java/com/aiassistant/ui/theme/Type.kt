package com.aiassistant.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.aiassistant.utils.EchoAppearancePreset
import com.aiassistant.R

// 使用系统默认字体，但设置更人性化的参数
val DefaultFontFamily = FontFamily.Default
internal val PaperFontFamily = FontFamily(Font(R.font.echo_literary_regular))

val Typography = Typography(
    // 大标题 - 用于页面标题
    displayLarge = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    // 中标题
    displayMedium = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    // 小标题
    displaySmall = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    // 大标题 - 用于重要区域标题
    headlineLarge = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    // 中标题
    headlineMedium = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    // 小标题
    headlineSmall = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    // 大标题 - 用于卡片标题
    titleLarge = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    // 中标题 - 用于列表项标题
    titleMedium = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    // 小标题
    titleSmall = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    // 大正文 - 用于消息内容
    bodyLarge = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    // 中正文 - 用于普通文本（§3.4：行高 20→21sp，1.5 倍，长文阅读更松）
    bodyMedium = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.25.sp
    ),
    // 小正文 - 用于辅助信息
    bodySmall = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    // 大标签 - 用于按钮文字
    labelLarge = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    // 中标签 - 用于芯片标签
    labelMedium = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    // 小标签 - 用于时间戳、token数等
    labelSmall = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

/** 墨白用更利落的标题和中性字距表达层次，正文字号和用户阅读字体不受影响。 */
internal fun echoTypographyFor(appearancePreset: String): Typography =
    when (EchoAppearancePreset.sanitize(appearancePreset)) {
        EchoAppearancePreset.CLEAR_SKY -> Typography.copy(
            displayLarge = Typography.displayLarge.copy(fontWeight = FontWeight.Light, letterSpacing = .2.sp),
            displayMedium = Typography.displayMedium.copy(fontWeight = FontWeight.Light, letterSpacing = .2.sp),
            displaySmall = Typography.displaySmall.copy(fontWeight = FontWeight.Normal, letterSpacing = .25.sp),
            headlineLarge = Typography.headlineLarge.copy(fontWeight = FontWeight.Normal, letterSpacing = .35.sp),
            headlineMedium = Typography.headlineMedium.copy(fontWeight = FontWeight.Normal, letterSpacing = .3.sp),
            headlineSmall = Typography.headlineSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = .25.sp),
            titleLarge = Typography.titleLarge.copy(fontWeight = FontWeight.Medium, letterSpacing = .2.sp),
            titleMedium = Typography.titleMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = .2.sp),
            bodyLarge = Typography.bodyLarge.copy(lineHeight = 27.sp, letterSpacing = .35.sp),
            bodyMedium = Typography.bodyMedium.copy(lineHeight = 23.sp, letterSpacing = .3.sp),
            bodySmall = Typography.bodySmall.copy(lineHeight = 18.sp, letterSpacing = .25.sp),
            labelLarge = Typography.labelLarge.copy(letterSpacing = .3.sp)
        )
        EchoAppearancePreset.NIGHT_VOYAGE -> Typography.copy(
            displayLarge = Typography.displayLarge.copy(fontFamily = PaperFontFamily, letterSpacing = .15.sp),
            displayMedium = Typography.displayMedium.copy(fontFamily = PaperFontFamily, letterSpacing = .15.sp),
            displaySmall = Typography.displaySmall.copy(fontFamily = PaperFontFamily, letterSpacing = .1.sp),
            headlineLarge = Typography.headlineLarge.copy(fontFamily = PaperFontFamily, fontWeight = FontWeight.Medium, letterSpacing = .3.sp),
            headlineMedium = Typography.headlineMedium.copy(fontFamily = PaperFontFamily, fontWeight = FontWeight.Medium, letterSpacing = .25.sp),
            headlineSmall = Typography.headlineSmall.copy(fontFamily = PaperFontFamily, fontWeight = FontWeight.Medium, letterSpacing = .2.sp),
            titleLarge = Typography.titleLarge.copy(fontFamily = PaperFontFamily, fontWeight = FontWeight.SemiBold, letterSpacing = .25.sp),
            titleMedium = Typography.titleMedium.copy(fontFamily = PaperFontFamily, fontWeight = FontWeight.Medium, letterSpacing = .2.sp),
            bodyLarge = Typography.bodyLarge.copy(lineHeight = 27.sp, letterSpacing = .25.sp),
            bodyMedium = Typography.bodyMedium.copy(lineHeight = 23.sp, letterSpacing = .2.sp),
            bodySmall = Typography.bodySmall.copy(lineHeight = 18.sp, letterSpacing = .2.sp),
            labelLarge = Typography.labelLarge.copy(fontFamily = FontFamily.Monospace, letterSpacing = .4.sp),
            labelMedium = Typography.labelMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = .5.sp),
            labelSmall = Typography.labelSmall.copy(fontFamily = FontFamily.Monospace, letterSpacing = .45.sp)
        )
        EchoAppearancePreset.INDEX -> Typography.copy(
            displayLarge = Typography.displayLarge.copy(fontFamily = PaperFontFamily, fontWeight = FontWeight.Medium, letterSpacing = .15.sp),
            displayMedium = Typography.displayMedium.copy(fontFamily = PaperFontFamily, fontWeight = FontWeight.Medium, letterSpacing = .15.sp),
            displaySmall = Typography.displaySmall.copy(fontFamily = PaperFontFamily, fontWeight = FontWeight.Medium, letterSpacing = .1.sp),
            headlineLarge = Typography.headlineLarge.copy(fontFamily = PaperFontFamily, fontWeight = FontWeight.SemiBold, letterSpacing = .25.sp),
            headlineMedium = Typography.headlineMedium.copy(fontFamily = PaperFontFamily, fontWeight = FontWeight.SemiBold, letterSpacing = .2.sp),
            headlineSmall = Typography.headlineSmall.copy(fontFamily = PaperFontFamily, fontWeight = FontWeight.SemiBold, letterSpacing = .15.sp),
            titleLarge = Typography.titleLarge.copy(fontFamily = PaperFontFamily, fontWeight = FontWeight.SemiBold, letterSpacing = .2.sp),
            titleMedium = Typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
            titleSmall = Typography.titleSmall.copy(letterSpacing = 0.sp),
            bodyLarge = Typography.bodyLarge.copy(lineHeight = 25.sp, letterSpacing = .15.sp),
            bodyMedium = Typography.bodyMedium.copy(lineHeight = 22.sp, letterSpacing = .1.sp),
            bodySmall = Typography.bodySmall.copy(lineHeight = 18.sp, letterSpacing = .2.sp),
            labelLarge = Typography.labelLarge.copy(fontFamily = FontFamily.Monospace, letterSpacing = .4.sp),
            labelMedium = Typography.labelMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = .6.sp),
            labelSmall = Typography.labelSmall.copy(fontFamily = FontFamily.Monospace, letterSpacing = .5.sp)
        )
        EchoAppearancePreset.MONO -> Typography.copy(
            displayLarge = Typography.displayLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
            displayMedium = Typography.displayMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
            displaySmall = Typography.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.15).sp),
            headlineLarge = Typography.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
            headlineMedium = Typography.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.15).sp),
            headlineSmall = Typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
            titleLarge = Typography.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.1).sp),
            titleMedium = Typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
            titleSmall = Typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
            bodyLarge = Typography.bodyLarge.copy(lineHeight = 26.sp, letterSpacing = .15.sp),
            bodyMedium = Typography.bodyMedium.copy(lineHeight = 22.sp, letterSpacing = .15.sp),
            labelLarge = Typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = .15.sp),
            labelMedium = Typography.labelMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = .4.sp),
            labelSmall = Typography.labelSmall.copy(fontFamily = FontFamily.Monospace, letterSpacing = .4.sp)
        )
        EchoAppearancePreset.DECKLE -> Typography.copy(
            displayLarge = Typography.displayLarge.copy(fontFamily = PaperFontFamily, letterSpacing = (-0.15).sp),
            displayMedium = Typography.displayMedium.copy(fontFamily = PaperFontFamily),
            displaySmall = Typography.displaySmall.copy(fontFamily = PaperFontFamily),
            headlineLarge = Typography.headlineLarge.copy(fontFamily = PaperFontFamily, letterSpacing = .15.sp),
            headlineMedium = Typography.headlineMedium.copy(fontFamily = PaperFontFamily, letterSpacing = .15.sp),
            headlineSmall = Typography.headlineSmall.copy(fontFamily = PaperFontFamily, letterSpacing = .1.sp),
            titleLarge = Typography.titleLarge.copy(fontFamily = PaperFontFamily, letterSpacing = .2.sp),
            titleMedium = Typography.titleMedium.copy(fontFamily = PaperFontFamily, letterSpacing = .15.sp),
            bodyLarge = Typography.bodyLarge.copy(fontFamily = PaperFontFamily, lineHeight = 29.sp),
            bodyMedium = Typography.bodyMedium.copy(fontFamily = PaperFontFamily, lineHeight = 25.sp),
            bodySmall = Typography.bodySmall.copy(fontFamily = PaperFontFamily, lineHeight = 20.sp),
            labelSmall = Typography.labelSmall.copy(letterSpacing = .35.sp)
        )
        EchoAppearancePreset.COTTON -> Typography.copy(
            displayLarge = Typography.displayLarge.copy(fontFamily = PaperFontFamily),
            displayMedium = Typography.displayMedium.copy(fontFamily = PaperFontFamily),
            displaySmall = Typography.displaySmall.copy(fontFamily = PaperFontFamily),
            headlineLarge = Typography.headlineLarge.copy(fontFamily = PaperFontFamily, letterSpacing = .3.sp),
            headlineMedium = Typography.headlineMedium.copy(fontFamily = PaperFontFamily, letterSpacing = .25.sp),
            headlineSmall = Typography.headlineSmall.copy(fontFamily = PaperFontFamily, letterSpacing = .2.sp),
            titleLarge = Typography.titleLarge.copy(fontFamily = PaperFontFamily, letterSpacing = .3.sp),
            titleMedium = Typography.titleMedium.copy(fontFamily = PaperFontFamily, letterSpacing = .2.sp),
            bodyLarge = Typography.bodyLarge.copy(fontFamily = PaperFontFamily, lineHeight = 30.sp, letterSpacing = .1.sp),
            bodyMedium = Typography.bodyMedium.copy(fontFamily = PaperFontFamily, lineHeight = 26.sp, letterSpacing = .1.sp),
            bodySmall = Typography.bodySmall.copy(fontFamily = PaperFontFamily, lineHeight = 21.sp),
            labelSmall = Typography.labelSmall.copy(letterSpacing = .4.sp)
        )
        EchoAppearancePreset.FOLIO -> Typography.copy(
            displayLarge = Typography.displayLarge.copy(fontFamily = PaperFontFamily),
            displayMedium = Typography.displayMedium.copy(fontFamily = PaperFontFamily),
            displaySmall = Typography.displaySmall.copy(fontFamily = PaperFontFamily),
            headlineLarge = Typography.headlineLarge.copy(fontFamily = PaperFontFamily, fontWeight = FontWeight.Normal),
            headlineMedium = Typography.headlineMedium.copy(fontFamily = PaperFontFamily, fontWeight = FontWeight.Normal),
            headlineSmall = Typography.headlineSmall.copy(fontFamily = PaperFontFamily, fontWeight = FontWeight.Normal),
            titleLarge = Typography.titleLarge.copy(fontFamily = PaperFontFamily, fontWeight = FontWeight.SemiBold, letterSpacing = .5.sp),
            titleMedium = Typography.titleMedium.copy(fontFamily = PaperFontFamily, letterSpacing = .3.sp),
            bodyLarge = Typography.bodyLarge.copy(fontFamily = PaperFontFamily, lineHeight = 30.sp, letterSpacing = .2.sp),
            bodyMedium = Typography.bodyMedium.copy(fontFamily = PaperFontFamily, lineHeight = 26.sp, letterSpacing = .15.sp),
            bodySmall = Typography.bodySmall.copy(fontFamily = PaperFontFamily, lineHeight = 21.sp),
            labelSmall = Typography.labelSmall.copy(letterSpacing = .35.sp)
        )
        EchoAppearancePreset.SONAR -> Typography.copy(
            headlineLarge = Typography.headlineLarge.copy(fontFamily = FontFamily.Monospace, letterSpacing = .5.sp),
            headlineMedium = Typography.headlineMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = .5.sp),
            headlineSmall = Typography.headlineSmall.copy(fontFamily = FontFamily.Monospace, letterSpacing = .4.sp),
            titleLarge = Typography.titleLarge.copy(fontFamily = FontFamily.Monospace, letterSpacing = .4.sp),
            titleMedium = Typography.titleMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = .3.sp),
            labelLarge = Typography.labelLarge.copy(fontFamily = FontFamily.Monospace, letterSpacing = .6.sp),
            labelMedium = Typography.labelMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = .7.sp),
            labelSmall = Typography.labelSmall.copy(fontFamily = FontFamily.Monospace, letterSpacing = .6.sp),
            bodyLarge = Typography.bodyLarge.copy(lineHeight = 26.sp),
            bodyMedium = Typography.bodyMedium.copy(lineHeight = 22.sp)
        )
        EchoAppearancePreset.BAUHAUS -> Typography.copy(
            displayLarge = Typography.displayLarge.copy(fontWeight = FontWeight.Black),
            displayMedium = Typography.displayMedium.copy(fontWeight = FontWeight.Black),
            displaySmall = Typography.displaySmall.copy(fontWeight = FontWeight.Bold),
            headlineLarge = Typography.headlineLarge.copy(fontWeight = FontWeight.Black),
            headlineMedium = Typography.headlineMedium.copy(fontWeight = FontWeight.Black),
            headlineSmall = Typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            titleLarge = Typography.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-.2).sp),
            titleMedium = Typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
            titleSmall = Typography.titleSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
            labelLarge = Typography.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = .4.sp),
            labelMedium = Typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = .5.sp),
            labelSmall = Typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = .45.sp)
        )
        EchoAppearancePreset.CELADON -> Typography.copy(
            displayLarge = Typography.displayLarge.copy(fontFamily = PaperFontFamily, letterSpacing = .2.sp),
            displayMedium = Typography.displayMedium.copy(fontFamily = PaperFontFamily, letterSpacing = .2.sp),
            displaySmall = Typography.displaySmall.copy(fontFamily = PaperFontFamily, letterSpacing = .15.sp),
            headlineLarge = Typography.headlineLarge.copy(fontFamily = PaperFontFamily, fontWeight = FontWeight.Normal, letterSpacing = .4.sp),
            headlineMedium = Typography.headlineMedium.copy(fontFamily = PaperFontFamily, fontWeight = FontWeight.Normal, letterSpacing = .35.sp),
            headlineSmall = Typography.headlineSmall.copy(fontFamily = PaperFontFamily, fontWeight = FontWeight.Normal, letterSpacing = .3.sp),
            titleLarge = Typography.titleLarge.copy(fontFamily = PaperFontFamily, fontWeight = FontWeight.Medium, letterSpacing = .4.sp),
            titleMedium = Typography.titleMedium.copy(fontFamily = PaperFontFamily, fontWeight = FontWeight.Medium, letterSpacing = .25.sp),
            bodyLarge = Typography.bodyLarge.copy(lineHeight = 27.sp, letterSpacing = .25.sp),
            bodyMedium = Typography.bodyMedium.copy(lineHeight = 23.sp, letterSpacing = .2.sp),
            bodySmall = Typography.bodySmall.copy(lineHeight = 18.sp),
            labelMedium = Typography.labelMedium.copy(letterSpacing = .4.sp),
            labelSmall = Typography.labelSmall.copy(letterSpacing = .35.sp)
        )
        EchoAppearancePreset.BLUEPRINT -> Typography.copy(
            headlineLarge = Typography.headlineLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, letterSpacing = .6.sp),
            headlineMedium = Typography.headlineMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, letterSpacing = .5.sp),
            headlineSmall = Typography.headlineSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, letterSpacing = .4.sp),
            titleLarge = Typography.titleLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, letterSpacing = .5.sp),
            titleMedium = Typography.titleMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, letterSpacing = .4.sp),
            titleSmall = Typography.titleSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, letterSpacing = .4.sp),
            labelLarge = Typography.labelLarge.copy(fontFamily = FontFamily.Monospace, letterSpacing = .6.sp),
            labelMedium = Typography.labelMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = .7.sp),
            labelSmall = Typography.labelSmall.copy(fontFamily = FontFamily.Monospace, letterSpacing = .6.sp),
            bodyLarge = Typography.bodyLarge.copy(lineHeight = 25.sp, letterSpacing = .2.sp),
            bodyMedium = Typography.bodyMedium.copy(lineHeight = 22.sp, letterSpacing = .15.sp)
        )
        EchoAppearancePreset.LACQUER -> Typography.copy(
            displayLarge = Typography.displayLarge.copy(fontFamily = PaperFontFamily, letterSpacing = .4.sp),
            displayMedium = Typography.displayMedium.copy(fontFamily = PaperFontFamily, letterSpacing = .35.sp),
            displaySmall = Typography.displaySmall.copy(fontFamily = PaperFontFamily, letterSpacing = .3.sp),
            headlineLarge = Typography.headlineLarge.copy(fontFamily = PaperFontFamily, fontWeight = FontWeight.SemiBold, letterSpacing = .5.sp),
            headlineMedium = Typography.headlineMedium.copy(fontFamily = PaperFontFamily, fontWeight = FontWeight.SemiBold, letterSpacing = .45.sp),
            headlineSmall = Typography.headlineSmall.copy(fontFamily = PaperFontFamily, fontWeight = FontWeight.Medium, letterSpacing = .4.sp),
            titleLarge = Typography.titleLarge.copy(fontFamily = PaperFontFamily, fontWeight = FontWeight.SemiBold, letterSpacing = .6.sp),
            titleMedium = Typography.titleMedium.copy(fontFamily = PaperFontFamily, fontWeight = FontWeight.Medium, letterSpacing = .4.sp),
            titleSmall = Typography.titleSmall.copy(fontFamily = PaperFontFamily, fontWeight = FontWeight.Medium, letterSpacing = .3.sp),
            bodyLarge = Typography.bodyLarge.copy(fontFamily = PaperFontFamily, lineHeight = 29.sp, letterSpacing = .2.sp),
            bodyMedium = Typography.bodyMedium.copy(fontFamily = PaperFontFamily, lineHeight = 25.sp, letterSpacing = .15.sp),
            bodySmall = Typography.bodySmall.copy(fontFamily = PaperFontFamily, lineHeight = 20.sp),
            labelLarge = Typography.labelLarge.copy(letterSpacing = .35.sp),
            labelMedium = Typography.labelMedium.copy(letterSpacing = .45.sp),
            labelSmall = Typography.labelSmall.copy(letterSpacing = .35.sp)
        )
        EchoAppearancePreset.EDITION -> Typography.copy(
            displayLarge = Typography.displayLarge.copy(fontFamily = PaperFontFamily, fontWeight = FontWeight.Medium, letterSpacing = .2.sp),
            displayMedium = Typography.displayMedium.copy(fontFamily = PaperFontFamily, fontWeight = FontWeight.Medium, letterSpacing = .2.sp),
            displaySmall = Typography.displaySmall.copy(fontFamily = PaperFontFamily, fontWeight = FontWeight.Medium, letterSpacing = .15.sp),
            headlineLarge = Typography.headlineLarge.copy(fontFamily = PaperFontFamily, fontWeight = FontWeight.Bold, letterSpacing = .3.sp),
            headlineMedium = Typography.headlineMedium.copy(fontFamily = PaperFontFamily, fontWeight = FontWeight.Bold, letterSpacing = .25.sp),
            headlineSmall = Typography.headlineSmall.copy(fontFamily = PaperFontFamily, fontWeight = FontWeight.SemiBold, letterSpacing = .2.sp),
            titleLarge = Typography.titleLarge.copy(fontFamily = PaperFontFamily, fontWeight = FontWeight.SemiBold, letterSpacing = .3.sp),
            titleMedium = Typography.titleMedium.copy(fontFamily = PaperFontFamily, fontWeight = FontWeight.SemiBold, letterSpacing = .25.sp),
            titleSmall = Typography.titleSmall.copy(fontFamily = PaperFontFamily, fontWeight = FontWeight.Medium, letterSpacing = .2.sp),
            bodyLarge = Typography.bodyLarge.copy(fontFamily = PaperFontFamily, lineHeight = 28.sp, letterSpacing = .2.sp),
            bodyMedium = Typography.bodyMedium.copy(fontFamily = PaperFontFamily, lineHeight = 24.sp, letterSpacing = .2.sp),
            bodySmall = Typography.bodySmall.copy(fontFamily = PaperFontFamily, lineHeight = 19.sp, letterSpacing = .15.sp),
            labelLarge = Typography.labelLarge.copy(letterSpacing = .2.sp),
            labelMedium = Typography.labelMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = .6.sp),
            labelSmall = Typography.labelSmall.copy(fontFamily = FontFamily.Monospace, letterSpacing = .5.sp)
        )
        EchoAppearancePreset.SCREENING -> Typography.copy(
            displayLarge = Typography.displayLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = .4.sp),
            displayMedium = Typography.displayMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = .4.sp),
            displaySmall = Typography.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = .3.sp),
            headlineLarge = Typography.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = .6.sp),
            headlineMedium = Typography.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = .5.sp),
            headlineSmall = Typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = .4.sp),
            titleLarge = Typography.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = .45.sp),
            titleMedium = Typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = .35.sp),
            titleSmall = Typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = .3.sp),
            bodyLarge = Typography.bodyLarge.copy(lineHeight = 27.sp, letterSpacing = .25.sp),
            bodyMedium = Typography.bodyMedium.copy(lineHeight = 23.sp, letterSpacing = .2.sp),
            bodySmall = Typography.bodySmall.copy(lineHeight = 18.sp, letterSpacing = .2.sp),
            labelLarge = Typography.labelLarge.copy(fontFamily = FontFamily.Monospace, letterSpacing = .5.sp),
            labelMedium = Typography.labelMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = .7.sp),
            labelSmall = Typography.labelSmall.copy(fontFamily = FontFamily.Monospace, letterSpacing = .6.sp)
        )
        else -> Typography
    }
