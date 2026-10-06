package com.aiassistant.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aiassistant.ui.theme.EchoTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun echoFilterChipColors(): SelectableChipColors {
    val glass = echoGlassPalette()
    return FilterChipDefaults.filterChipColors(
        containerColor = glass.control,
        labelColor = glass.textSecondary,
        iconColor = glass.iconSecondary,
        selectedContainerColor = glass.controlSelected,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        disabledContainerColor = glass.control.copy(alpha = 0.42f),
        disabledLabelColor = glass.textMuted.copy(alpha = 0.56f),
        disabledLeadingIconColor = glass.iconSecondary.copy(alpha = 0.44f),
        disabledTrailingIconColor = glass.iconSecondary.copy(alpha = 0.44f)
    )
}

@Composable
fun echoFilterChipBorder(selected: Boolean): BorderStroke {
    val glass = echoGlassPalette()
    return BorderStroke(
        width = if (selected) EchoTokens.Glass.activeBorderWidth else EchoTokens.Glass.borderWidth,
        color = if (selected) glass.outlineSelected else glass.outline
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun echoFilterChipElevation(): SelectableChipElevation =
    FilterChipDefaults.filterChipElevation(
        elevation = EchoTokens.Elevation.none,
        pressedElevation = EchoTokens.Elevation.none,
        focusedElevation = EchoTokens.Elevation.none,
        hoveredElevation = EchoTokens.Elevation.none,
        draggedElevation = EchoTokens.Elevation.none,
        disabledElevation = EchoTokens.Elevation.none
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun echoSegmentedButtonColors(): SegmentedButtonColors {
    val glass = echoGlassPalette()
    return SegmentedButtonDefaults.colors(
        activeContainerColor = glass.controlSelected,
        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        inactiveContainerColor = glass.control,
        inactiveContentColor = glass.textSecondary,
        disabledActiveContainerColor = glass.controlSelected.copy(alpha = 0.44f),
        disabledActiveContentColor = glass.textMuted.copy(alpha = 0.52f),
        disabledInactiveContainerColor = glass.control.copy(alpha = 0.36f),
        disabledInactiveContentColor = glass.textMuted.copy(alpha = 0.46f)
    )
}

@Composable
fun echoSegmentedButtonBorder(selected: Boolean): BorderStroke {
    val glass = echoGlassPalette()
    return BorderStroke(
        width = if (selected) EchoTokens.Glass.activeBorderWidth else EchoTokens.Glass.borderWidth,
        color = if (selected) glass.outlineSelected else glass.outline
    )
}

val EchoCompactButtonPadding = PaddingValues(horizontal = EchoTokens.Spacing.screenHorizontal, vertical = EchoTokens.Spacing.sm)

@Composable
fun Color.echoReadableOn(fallbackSurface: Color = MaterialTheme.colorScheme.background): Color =
    readableTextColorFor(background = this, fallbackSurface = fallbackSurface)

/**
 * Echo 主操作按钮 (EchoPrimaryButton)
 */
@Composable
fun EchoPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape = EchoTokens.Radius.shapeMd,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        contentPadding = contentPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        content = content
    )
}

/**
 * Echo 玻璃辅助按钮 (EchoGlassButton)
 */
@Composable
fun EchoGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape = EchoTokens.Radius.shapeMd,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    val glass = echoGlassPalette()
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        contentPadding = contentPadding,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = glass.control,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(EchoTokens.Glass.borderWidth, glass.outline),
        content = content
    )
}

/**
 * Echo 复选框配色方案
 * 与 echoSwitchColors() 同源的选中色规范（§5.4）：选中走 primary/onPrimary，
 * 未选中边框用 outline，保证玻璃卡上的可见性。
 */
@Composable
fun echoCheckboxColors(): CheckboxColors {
    val colorScheme = MaterialTheme.colorScheme
    return CheckboxDefaults.colors(
        checkedColor = colorScheme.primary,
        checkmarkColor = colorScheme.onPrimary,
        uncheckedColor = colorScheme.outline,
        disabledCheckedColor = colorScheme.onSurface.copy(alpha = 0.12f),
        disabledUncheckedColor = colorScheme.outline.copy(alpha = 0.38f),
        disabledIndeterminateColor = colorScheme.onSurface.copy(alpha = 0.38f)
    )
}
