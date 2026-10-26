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
        EchoAppearancePreset.MONO -> Typography.copy(
            displayLarge = Typography.displayLarge.copy(fontWeight = FontWeight.Medium),
            displayMedium = Typography.displayMedium.copy(fontWeight = FontWeight.Medium),
            displaySmall = Typography.displaySmall.copy(fontWeight = FontWeight.Medium),
            headlineLarge = Typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            headlineMedium = Typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            headlineSmall = Typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            titleLarge = Typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
            titleMedium = Typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
            titleSmall = Typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
            labelLarge = Typography.labelLarge.copy(letterSpacing = 0.sp),
            labelMedium = Typography.labelMedium.copy(letterSpacing = 0.sp),
            labelSmall = Typography.labelSmall.copy(letterSpacing = 0.sp)
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
            bodySmall = Typography.bodySmall.copy(fontFamily = PaperFontFamily, lineHeight = 20.sp)
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
            bodySmall = Typography.bodySmall.copy(fontFamily = PaperFontFamily, lineHeight = 21.sp)
        )
        else -> Typography
    }
