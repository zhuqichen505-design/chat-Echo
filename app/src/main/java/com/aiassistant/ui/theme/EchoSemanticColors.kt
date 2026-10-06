package com.aiassistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * 语义颜色元组：包含主色、容器背景色、容器上前景色、微光边框色
 */
data class SemanticColorGroup(
    val main: Color,
    val container: Color,
    val onContainer: Color,
    val border: Color
)

/**
 * Echo 全局语义色彩系统 (EchoSemanticColors)
 * 统一管理 Error / Warning / Success / Info 四大语义状态在深浅主题下的表现，
 * 杜绝散落全工程的硬编码颜色与对比度失衡问题。
 */
data class EchoSemanticPalette(
    val error: SemanticColorGroup,
    val warning: SemanticColorGroup,
    val success: SemanticColorGroup,
    val info: SemanticColorGroup
)

object EchoSemanticColors {
    val Dark = EchoSemanticPalette(
        error = SemanticColorGroup(
            main = Color(0xFFF87171),
            container = Color(0xFF450A0A).copy(alpha = 0.65f),
            onContainer = Color(0xFFFECACA),
            border = Color(0xFFEF4444).copy(alpha = 0.45f)
        ),
        warning = SemanticColorGroup(
            main = Color(0xFFFBBF24),
            container = Color(0xFF451A03).copy(alpha = 0.65f),
            onContainer = Color(0xFFFEF3C7),
            border = Color(0xFFF59E0B).copy(alpha = 0.45f)
        ),
        success = SemanticColorGroup(
            main = Color(0xFF34D399),
            container = Color(0xFF064E3B).copy(alpha = 0.65f),
            onContainer = Color(0xFFA7F3D0),
            border = Color(0xFF10B981).copy(alpha = 0.45f)
        ),
        info = SemanticColorGroup(
            main = Color(0xFF60A5FA),
            container = Color(0xFF172554).copy(alpha = 0.65f),
            onContainer = Color(0xFFBFDBFE),
            border = Color(0xFF3B82F6).copy(alpha = 0.45f)
        )
    )

    val Light = EchoSemanticPalette(
        error = SemanticColorGroup(
            main = Color(0xFFDC2626),
            container = Color(0xFFFEF2F2),
            onContainer = Color(0xFF991B1B),
            border = Color(0xFFFCA5A5)
        ),
        warning = SemanticColorGroup(
            main = Color(0xFFD97706),
            container = Color(0xFFFFFBEB),
            onContainer = Color(0xFF92400E),
            border = Color(0xFFFCD34D)
        ),
        success = SemanticColorGroup(
            main = Color(0xFF059669),
            container = Color(0xFFECFDF5),
            onContainer = Color(0xFF065F46),
            border = Color(0xFF6EE7B7)
        ),
        info = SemanticColorGroup(
            main = Color(0xFF2563EB),
            container = Color(0xFFEFF6FF),
            onContainer = Color(0xFF1E40AF),
            border = Color(0xFF93C5FD)
        )
    )
}

@Composable
fun rememberEchoSemanticColors(): EchoSemanticPalette {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) EchoSemanticColors.Dark else EchoSemanticColors.Light
}
