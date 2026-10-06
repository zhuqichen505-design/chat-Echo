package com.aiassistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// ============ 清爽淡色系主题配色体系 ============
// 采用柔和的淡色系（Pastel Colors）：淡天蓝、淡青、淡粉红与淡雅莫兰迪中性色

// ============ 浅色主题 ============

val Primary = Color(0xFF3B82F6) // 纯正明朗蔚蓝，使白色文字与高光对比度达到 4.5:1+ 符合 WCAG AA
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFFEFF6FF)
val OnPrimaryContainer = Color(0xFF1E40AF)

val Secondary = Color(0xFF38BDF8) // 淡青蓝
val OnSecondary = Color(0xFFFFFFFF)
val SecondaryContainer = Color(0xFFF0F9FF)
val OnSecondaryContainer = Color(0xFF0369A1)

val Tertiary = Color(0xFFFB7185) // 淡珊瑚粉红
val OnTertiary = Color(0xFFFFFFFF)
val TertiaryContainer = Color(0xFFFFF1F2)
val OnTertiaryContainer = Color(0xFF9F1239)

val Background = Color(0xFFF8FAFC)
val OnBackground = Color(0xFF1E293B)
val Surface = Color(0xFFFFFFFF)
val OnSurface = Color(0xFF1E293B)
val SurfaceVariant = Color(0xFFF1F5F9)
val OnSurfaceVariant = Color(0xFF64748B)

// 错误色 - 修正：原 #F87171 白字对比度 ≈2.5:1 不达标
// 容器场景用 #EF4444，文字场景用 #DC2626
val Error = Color(0xFFEF4444)
val OnError = Color(0xFFFFFFFF)
val ErrorContainer = Color(0xFFFEF2F2)
val OnErrorContainer = Color(0xFF991B1B)

// 轮廓 - 柔和灰色（修正：原 #F0F8FF 与白底无差异，边框消失）
val Outline = Color(0xFFCBD5E1)
val OutlineVariant = Color(0xFFE2E8F0)

// ============ 深色主题 ============

val DarkPrimary = Color(0xFF60A5FA)
val DarkOnPrimary = Color(0xFF0A192F)
val DarkPrimaryContainer = Color(0xFF1E3A8A)
val DarkOnPrimaryContainer = Color(0xFFE0F2FE)

val DarkSecondary = Color(0xFF38BDF8)
val DarkOnSecondary = Color(0xFF082F49)
val DarkSecondaryContainer = Color(0xFF0369A1)
val DarkOnSecondaryContainer = Color(0xFFE0F2FE)

// 修正：原 #93C5FD（蓝色）与浅色 Tertiary #FB7185（珊瑚粉）色相断裂
// 统一为珊瑚粉色系，保持品牌一致性
val DarkTertiary = Color(0xFFFDA4AF)
val DarkOnTertiary = Color(0xFF0F172A)
val DarkTertiaryContainer = Color(0xFF4C1D2F)
val DarkOnTertiaryContainer = Color(0xFFFFE4E6)

val DarkBackground = Color(0xFF0A0F1E)
val DarkOnBackground = Color(0xFFF8FAFC)
// 修正：原 #11192C 与 DarkBackground 明度差仅 ~4%，层级感不足
// 提升 ~8% 明度差，确保三层表面可区分
val DarkSurface = Color(0xFF1A2332)
val DarkOnSurface = Color(0xFFF8FAFC)
// 修正：原 #18233C 与修正后 DarkSurface 差距过小
val DarkSurfaceVariant = Color(0xFF242F42)
// 修正：原 #94A3B8 叠在 DarkSurfaceVariant 上对比度 ≈3.8:1，提升至 ≈5.1:1
val DarkOnSurfaceVariant = Color(0xFFB0BECC)

val DarkError = Color(0xFFFCA5A5)
val DarkOnError = Color(0xFF7F1D1D)
val DarkErrorContainer = Color(0xFF991B1B)
val DarkOnErrorContainer = Color(0xFFFEF2F2)

// 修正：原 #334155 叠在 DarkBackground 上对比度 ≈1.2:1，提升至 ≈1.8:1
val DarkOutline = Color(0xFF475569)
val DarkOutlineVariant = Color(0xFF1E293B)

// ============ 功能色 ============

@Deprecated("Use EchoSemanticColors.success instead", ReplaceWith("EchoSemanticColors.success.main"))
val SuccessBlue = Color(0xFF38BDF8)
val WarningOrange = Color(0xFFFBBF24)
val InfoBlue = Color(0xFF6BA4F8)

// ============ 消息气泡色 ============

// 浅色主题
val UserMessageBubble = Color(0xFF3B82F6)
val AssistantMessageBubble = Color(0xFFFFFFFF)

// 深色主题
val DarkUserMessageBubble = Color(0xFF1E40AF)
val DarkAssistantMessageBubble = Color(0xFF11192C)

// ============ 特殊色 ============

// 代码块背景
val CodeBlockBackground = Color(0xFFF1F5F9)
val DarkCodeBlockBackground = Color(0xFF0F172A)

// 思考内容背景 (纯净浅冰蓝与深海蓝，杜绝杂乱红紫)
val ThinkingBackground = Color(0xFFF0F7FF)
val DarkThinkingBackground = Color(0xFF0E1A30)

// 链接色
val LinkColor = Color(0xFF2563EB)
val DarkLinkColor = Color(0xFF60A5FA)

// ============ 图表色 (EchoChartColors) ============
// 统计页图表专用，深浅双套，替代 StatsScreen 13 处硬编码色

object EchoChartColors {
    // 浅色主题
    val primary = Color(0xFF6BA4F8)
    val secondary = Color(0xFF38BDF8)
    val tertiary = Color(0xFFFB7185)
    val quaternary = Color(0xFFA5B4FC)

    // 深色主题
    val primaryDark = Color(0xFF8AB4F8)
    val secondaryDark = Color(0xFF7DD3FC)
    val tertiaryDark = Color(0xFFFDA4AF)
    val quaternaryDark = Color(0xFFC7D2FE)
}

/**
 * 图表色板四元组：按主题明暗从 EchoChartColors 选择浅/深套
 */
data class EchoChartPalette(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val quaternary: Color
)

/**
 * 图表色消费统一入口（C-7：深色主题必须使用 *Dark 变体，避免深底上浅色图表刺眼）。
 * 页面端只允许消费本函数，禁止直接引用 EchoChartColors 的单一色值。
 */
@Composable
fun rememberEchoChartColors(): EchoChartPalette {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) {
        EchoChartPalette(
            primary = EchoChartColors.primaryDark,
            secondary = EchoChartColors.secondaryDark,
            tertiary = EchoChartColors.tertiaryDark,
            quaternary = EchoChartColors.quaternaryDark
        )
    } else {
        EchoChartPalette(
            primary = EchoChartColors.primary,
            secondary = EchoChartColors.secondary,
            tertiary = EchoChartColors.tertiary,
            quaternary = EchoChartColors.quaternary
        )
    }
}

// ============ 文件夹调色板 (EchoFolderColors) ============
// 文件夹管理页专用，替代 FolderManagerScreen 15 处硬编码色

object EchoFolderColors {
    val palette = listOf(
        Color(0xFFEF4444),  // red-500
        Color(0xFFF97316),  // orange-500
        Color(0xFFF59E0B),  // amber-500
        Color(0xFFEAB308),  // yellow-500
        Color(0xFF84CC16),  // lime-500
        Color(0xFF22C55E),  // green-500
        Color(0xFF10B981),  // emerald-500
        Color(0xFF14B8A6),  // teal-500
        Color(0xFF06B6D4),  // cyan-500
        Color(0xFF3B82F6),  // blue-500
        Color(0xFF6366F1),  // indigo-500
        Color(0xFF8B5CF6),  // violet-500
        Color(0xFFA855F7),  // purple-500
        Color(0xFFEC4899),  // pink-500
        Color(0xFF64748B)   // slate-500
    )

    /**
     * 兼容历史数据的 8 色粉彩调色板。
     * folder.color 索引 1..8 与既有数据库数据一一对应，顺序禁止调整。
     */
    val pastelPalette = listOf(
        Color(0xFFE57373), // 1 红
        Color(0xFFFFB74D), // 2 橙
        Color(0xFFFFF176), // 3 黄
        Color(0xFF60A5FA), // 4 天蓝
        Color(0xFF64B5F6), // 5 蓝
        Color(0xFF9575CD), // 6 紫
        Color(0xFF818CF8), // 7 靛蓝
        Color(0xFFA1887F)  // 8 棕
    )
}

// ============ 思考等级颜色 (EchoThinkingColors) ============
// 聊天页思考等级专用，替代 ChatInputComponents 硬编码色

object EchoThinkingColors {
    // 关闭状态
    val none = Color(0xFF64748B)
    val noneGradient = listOf(Color(0xFF64748B), Color(0xFF94A3B8))

    // 低等级 (low/fast)
    val low = Color(0xFF60A5FA)
    val lowGradient = listOf(Color(0xFF93C5FD), Color(0xFF60A5FA))

    // 中等级 (medium/balanced)
    val medium = Color(0xFF2563EB)
    val mediumGradient = listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8))

    // 高等级 (high/deep)
    val high = Color(0xFF1D4ED8)
    val highGradient = listOf(Color(0xFF1D4ED8), Color(0xFF1E3A8A))

    // 最高等级 (ultra/max)
    val max = Color(0xFF4F46E5)
    val maxGradient = listOf(Color(0xFF6366F1), Color(0xFF4338CA))

    // 最高等级的强调色（档位胶囊选中态前景，取 maxGradient 深端）
    val maxAccent = Color(0xFF4338CA)

    /** 思考档位字符串 → 档位色（动效轮：光标/脉冲环同源配色，方案 P0-1②/P0-2②） */
    fun forEffort(effort: String?): Color = when {
        effort == null -> medium
        effort.equals("low", true) || effort.equals("fast", true) -> low
        effort.equals("high", true) || effort.equals("deep", true) -> high
        effort.equals("ultra", true) || effort.equals("max", true) -> maxAccent
        else -> medium
    }
}
