package com.aiassistant.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Echo 设计令牌体系 (Design Tokens)
 * 集中管理全应用的间距、圆角、模糊规格、透明度与排版尺寸，确保全应用视觉语言的高度一致性。
 */
object EchoTokens {

    /**
     * 间距规范 (Spacing)
     */
    object Spacing {
        val xxs: Dp = 2.dp
        val xs: Dp = 4.dp
        val sm: Dp = 8.dp
        val md: Dp = 12.dp
        val lg: Dp = 16.dp
        val xl: Dp = 20.dp
        val xxl: Dp = 28.dp

        // —— 语义化别名（页面只允许用语义别名，禁用裸 dp）——
        val pageGutter: Dp = 16.dp          // 页面水平统一边距（替代 screenHorizontal=14）
        val cardPadding: Dp = 16.dp         // 卡片内边距（统一）
        val cardPaddingCompact: Dp = 12.dp  // 紧凑卡片内边距（弹窗内卡片）
        val cardGap: Dp = 12.dp             // 卡片间距（统一，替代 10/12/14/16 混用）
        val sectionGap: Dp = 20.dp          // 分区间距（卡片组之间）
        val inlineGap: Dp = 8.dp            // 行内元素间距（图标-文字）
        val inlineGapTight: Dp = 4.dp       // 行内紧凑间距

        // —— 兼容保留（标记为旧版，新代码禁用）——
        @Deprecated("Use pageGutter instead", ReplaceWith("pageGutter"))
        val screenHorizontal: Dp = 14.dp
        val screenVertical: Dp = 8.dp
        @Deprecated("Use cardGap instead", ReplaceWith("cardGap"))
        val itemSpacing: Dp = 10.dp
    }

    /**
     * 圆角规范 (Radius)
     */
    object Radius {
        val xs: Dp = 6.dp
        val sm: Dp = 8.dp       // 标签、小徽章、紧凑组件
        val md: Dp = 12.dp      // 普通按钮、输入框、辅助卡片
        val lg: Dp = 18.dp      // 核心业务卡片、对话气泡、分块容器
        val xl: Dp = 24.dp      // 模态弹窗、主悬浮栏、底部面板
        val pill: Dp = 999.dp   // 全胶囊组件 (如状态胶囊、浮动导航条)

        val shapeXs: Shape = RoundedCornerShape(xs)
        val shapeSm: Shape = RoundedCornerShape(sm)
        val shapeMd: Shape = RoundedCornerShape(md)
        val shapeLg: Shape = RoundedCornerShape(lg)
        val shapeXl: Shape = RoundedCornerShape(xl)
        val shapePill: Shape = RoundedCornerShape(pill)
    }

    /**
     * 毛玻璃与液态流体渲染规范 (Glass Specification)
     */
    object Glass {
        // 卡片透明度 (清透晶莹，告别沉重死板)
        const val cardAlphaLight: Float = 0.28f
        const val cardAlphaDark: Float = 0.38f

        // 面板与对话框透明度（以 echoGlassPalette 实际验证值为准，消灭双轨）
        const val panelAlphaLight: Float = 0.88f
        const val panelAlphaDark: Float = 0.85f
        const val strongAlphaLight: Float = 0.94f
        const val strongAlphaDark: Float = 0.92f
        const val softAlphaLight: Float = 0.72f
        const val softAlphaDark: Float = 0.68f
        const val dialogAlphaLight: Float = 0.85f
        const val dialogAlphaDark: Float = 0.90f

        // 控件透明度（以 echoGlassPalette 实际验证值为准）
        const val controlAlphaLight: Float = 0.80f
        const val controlAlphaDark: Float = 0.76f
        const val inputAlphaLight: Float = 0.93f
        const val inputAlphaDark: Float = 0.90f

        // 边框与高光规范
        val borderWidth: Dp = 1.dp
        val activeBorderWidth: Dp = 1.3.dp
        const val borderAlphaLight: Float = 0.22f
        const val borderAlphaDark: Float = 0.14f
        const val highlightAlphaLight: Float = 0.08f
        const val highlightAlphaDark: Float = 0.05f

        // 模糊半径
        val blurRadiusStandard: Dp = 20.dp
        val blurRadiusHeavy: Dp = 30.dp
        val blurRadiusSubtle: Dp = 12.dp
    }

    /**
     * 阴影配方 (Elevation) —— 三级阴影配方，不只是高度，而是高度+环境色+光斑色的完整配方
     *
     * 【uicraft 复核补充】深色模式层级策略：
     *   深色下阴影对比度天然不足，应通过"提高表面亮度"表达层级，而非加深阴影。
     *   因此深色主题 Elevation.card() 的 ambient/spot alpha 应降至 0.10f/0.08f，
     *   同时 DarkSurface 与 DarkBackground 明度差需扩大至 ≥ 12%（见 Color.kt 修正）。
     */
    object Elevation {
        val none: Dp = 0.dp
        val subtle: Dp = 2.dp    // 轻托举（兼容旧代码使用）

        /**
         * L1 静止卡片：极轻托举
         */
        data class ShadowRecipe(
            val elevation: Dp,
            val ambient: Color,
            val spot: Color
        )

        fun card(isDark: Boolean): ShadowRecipe = ShadowRecipe(
            elevation = 2.dp,
            ambient = Color.Black.copy(alpha = if (isDark) 0.10f else 0.06f),
            spot = Color.Black.copy(alpha = if (isDark) 0.08f else 0.05f)  // 统一中性黑，弃用 primary 染色
        )

        /**
         * L2 浮层（顶栏/工具条/菜单）：中度悬浮
         */
        fun overlay(isDark: Boolean): ShadowRecipe = ShadowRecipe(
            elevation = 4.dp,
            ambient = Color.Black.copy(alpha = if (isDark) 0.12f else 0.08f),
            spot = Color.Black.copy(alpha = if (isDark) 0.10f else 0.07f)
        )

        /**
         * L3 交互态（按压/拖拽/滑块拇指）：强调浮起
         */
        fun raised(isDark: Boolean): ShadowRecipe = ShadowRecipe(
            elevation = 6.dp,
            ambient = Color.Black.copy(alpha = if (isDark) 0.14f else 0.10f),
            spot = Color.Black.copy(alpha = if (isDark) 0.12f else 0.10f)
        )
    }

    /**
     * 图标尺寸规范 (IconSize)
     */
    object IconSize {
        val action: Dp = 24.dp        // 列表/卡片内操作图标
        val inline: Dp = 18.dp        // 行内辅助图标
        val badge: Dp = 14.dp         // 徽章/标签内图标
        val header: Dp = 22.dp        // 卡片头部图标（统一设置页 20/22/24/28 混用）
    }

    /**
     * 触控目标规范 (TouchTarget)
     */
    object TouchTarget {
        val minimum: Dp = 48.dp       // 标准触控目标
        val compact: Dp = 36.dp       // 仅允许在工具条/气泡尾部等高密度区使用，
                                      // 且必须配 6dp 以上外间距补偿
    }

    /**
     * 组件级固定规格 (Component)
     */
    object Component {
        val settingRowMinHeight: Dp = 56.dp
        val settingRowMinHeightWithSubtitle: Dp = 64.dp
        val menuItemMinHeight: Dp = 72.dp
        val settingIconBackdrop: Dp = 42.dp // 设置行图标底色块（§4.2）
        val switchTrackWidth: Dp = 44.dp    // 紧凑开关轨道（见 EchoSwitch）
        val switchTrackHeight: Dp = 26.dp
        val avatarSize: Dp = 72.dp
        val colorSwatchSize: Dp = 40.dp
    }

    /**
     * 排版令牌扩展 (Type) —— 消灭裸 sp
     */
    object Type {
        /**
         * 聊天消息气泡正文：长文本场景放宽行高，降低阅读疲劳
         */
        val chatBody: TextStyle = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 26.sp,      // 1.625 倍，高于全局 bodyLarge 的 1.5 倍
            letterSpacing = 0.5.sp
        )

        /**
         * 状态徽章文字：12sp，配合 EchoBadge 使用
         */
        val badgeLabel: TextStyle = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp
        )

        /**
         * 卡片标题：统一设置页 16/16.5/17 混用
         */
        val cardTitle: TextStyle = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.15.sp
        )
    }
}
