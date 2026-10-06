package com.aiassistant.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import com.aiassistant.ui.theme.EchoTokens
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.foundation.focusable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn

val EchoGlassDialogShape = EchoTokens.Radius.shapeXl
val EchoGlassPagePanelShape = EchoTokens.Radius.shapeLg
val EchoGlassControlShape = EchoTokens.Radius.shapePill

data class EchoGlassPalette(
    val panel: Color,
    val panelStrong: Color,
    val panelSoft: Color,
    val control: Color,
    val controlSelected: Color,
    val input: Color,
    val userBubble: Color,
    val assistantBubble: Color,
    val outline: Color,
    val outlineSelected: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val iconPrimary: Color,
    val iconSecondary: Color
)

@Composable
fun echoGlassPalette(): EchoGlassPalette {
    val colors = MaterialTheme.colorScheme
    val isDark = colors.background.luminance() < 0.5f

    // 统一消费 EchoTokens.Glass 令牌，消灭双轨（原硬编码值已回写令牌）
    val panelAlpha = if (isDark) EchoTokens.Glass.panelAlphaDark else EchoTokens.Glass.panelAlphaLight
    val strongAlpha = if (isDark) EchoTokens.Glass.strongAlphaDark else EchoTokens.Glass.strongAlphaLight
    val softAlpha = if (isDark) EchoTokens.Glass.softAlphaDark else EchoTokens.Glass.softAlphaLight
    val controlAlpha = if (isDark) EchoTokens.Glass.controlAlphaDark else EchoTokens.Glass.controlAlphaLight
    val inputAlpha = if (isDark) EchoTokens.Glass.inputAlphaDark else EchoTokens.Glass.inputAlphaLight
    val selectedAlpha = if (isDark) 0.88f else 0.90f

    return EchoGlassPalette(
        panel = colors.surface.copy(alpha = panelAlpha),
        panelStrong = colors.surface.copy(alpha = strongAlpha),
        panelSoft = colors.surface.copy(alpha = softAlpha),
        control = colors.surface.copy(alpha = controlAlpha),
        controlSelected = colors.primaryContainer.copy(alpha = selectedAlpha),
        input = colors.surface.copy(alpha = inputAlpha),
        userBubble = if (isDark) {
            colors.primaryContainer.copy(alpha = 0.90f)
        } else {
            Color(0xFFE0F2FE).copy(alpha = 0.94f)
        },
        assistantBubble = Color.Transparent, // 模型回复不使用气泡背景
        outline = if (isDark) Color.White.copy(alpha = 0.12f) else colors.outlineVariant.copy(alpha = 0.18f),
        outlineSelected = colors.primary.copy(alpha = if (isDark) 0.65f else 0.55f),
        textPrimary = colors.onSurface,
        textSecondary = colors.onSurfaceVariant,
        // §3.4：浅色 alpha 0.65→0.75（对比度 2.9:1→约 3.6:1）；仅限装饰性辅助信息使用，功能性文字禁用
        textMuted = colors.onSurfaceVariant.copy(alpha = 0.75f),
        iconPrimary = colors.primary,
        iconSecondary = colors.onSurfaceVariant.copy(alpha = if (isDark) 0.82f else 0.72f)
    )
}

@Composable
fun rememberEchoHazeState(): HazeState = remember { HazeState() }

@Composable
fun Modifier.echoHazeSource(
    hazeState: HazeState
): Modifier = haze(
    state = hazeState,
    style = HazeDefaults.style(
        backgroundColor = MaterialTheme.colorScheme.surface,
        tint = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.08f),
        blurRadius = EchoTokens.Glass.blurRadiusStandard,
        noiseFactor = 0f
    )
)

@Composable
fun Modifier.echoHazePanel(
    hazeState: HazeState? = null,
    shape: Shape = EchoTokens.Radius.shapeLg,
    tint: Color = Color.Unspecified,
    blurRadius: Dp = EchoTokens.Glass.blurRadiusStandard,
    highlightAlpha: Float = 0.08f,
    showBorder: Boolean = true
): Modifier {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.luminance() < 0.5f
    val resolvedTint = if (tint != Color.Unspecified) {
        tint
    } else {
        colorScheme.surface.copy(alpha = if (isDark) 0.85f else 0.88f)
    }

    val shadowRecipe = EchoTokens.Elevation.card(isDark)
    var mod = this
        .shadow(
            elevation = EchoTokens.Elevation.subtle,
            shape = shape,
            ambientColor = shadowRecipe.ambient,
            spotColor = shadowRecipe.spot
        )

    if (hazeState != null) {
        val hazeTint = if (resolvedTint.alpha > 0f) {
            resolvedTint.copy(alpha = (resolvedTint.alpha * 0.22f).coerceIn(0.05f, 0.25f))
        } else {
            Color.Transparent
        }
        mod = mod.hazeChild(
            state = hazeState,
            shape = shape,
            style = HazeStyle(
                tint = hazeTint,
                blurRadius = blurRadius,
                noiseFactor = 0f
            )
        )
    }

    // 绘制面板半透明底色，确保文字无论在何种背景下均具有清晰的可读性，消除完全透明
    mod = mod
        .background(resolvedTint, shape)
        .clip(shape)
        .drawBehind {
            if (highlightAlpha > 0f) {
                // 在背景层绘制微光折射高光线，绝不覆盖子组件文本与图标
                val highlightColor = Color.White.copy(alpha = if (isDark) highlightAlpha * 0.7f else highlightAlpha)
                drawRoundRect(
                    color = highlightColor,
                    topLeft = Offset(size.width * 0.06f, 1.dp.toPx()),
                    size = Size(size.width * 0.60f, 1.2.dp.toPx()),
                    cornerRadius = CornerRadius(999.dp.toPx(), 999.dp.toPx())
                )
            }
        }

    if (showBorder) {
        mod = mod.border(
            BorderStroke(
                EchoTokens.Glass.borderWidth,
                Brush.linearGradient(
                    colorStops = arrayOf(
                        0.00f to (if (isDark) colorScheme.outline.copy(alpha = 0.50f) else colorScheme.outline.copy(alpha = 0.48f)),
                        0.40f to colorScheme.outlineVariant.copy(alpha = if (isDark) 0.16f else 0.22f),
                        1.00f to colorScheme.primary.copy(alpha = if (isDark) 0.16f else 0.20f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset.Infinite
                )
            ),
            shape
        )
    }

    return mod
}

@Composable
fun EchoLiquidGlassPanel(
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier,
    shape: Shape = EchoTokens.Radius.shapeLg,
    tint: Color = Color.Unspecified,
    blurRadius: Dp = EchoTokens.Glass.blurRadiusStandard,
    showBorder: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.echoHazePanel(
            hazeState = hazeState,
            shape = shape,
            tint = tint,
            blurRadius = blurRadius,
            showBorder = showBorder
        ),
        content = content
    )
}

@Composable
fun EchoWallpaperBackground(
    backgroundBitmap: Bitmap?,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.background)
                .echoHazeSource(hazeState)
        ) {
            backgroundBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        content()
    }
}

@Composable
fun EchoGlassDialog(
    hazeState: HazeState? = null,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = EchoTokens.Radius.shapeXl,
    tint: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    containerColor: Color = Color.Unspecified,
    title: (@Composable ColumnScope.() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
    buttons: (@Composable ColumnScope.() -> Unit)? = null,
    /** §6.0 无障碍：屏幕阅读器进入弹窗时朗读的面板标题 */
    paneTitleText: String = "对话框"
) {
    val glass = echoGlassPalette()
    val resolvedTint = if (tint != Color.Unspecified) tint else glass.panel
    val resolvedContainerColor = if (containerColor != Color.Unspecified) containerColor else glass.panelStrong
    val resolvedContentColor = if (contentColor != Color.Unspecified) contentColor else glass.textPrimary

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val view = androidx.compose.ui.platform.LocalView.current
        androidx.compose.runtime.SideEffect {
            var parent = view.parent
            while (parent != null) {
                if (parent is androidx.compose.ui.window.DialogWindowProvider) {
                    parent.window.apply {
                        setLayout(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(this, false)
                        setBackgroundDrawableResource(android.R.color.transparent)
                        statusBarColor = android.graphics.Color.TRANSPARENT
                        navigationBarColor = android.graphics.Color.TRANSPARENT
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            isNavigationBarContrastEnforced = false
                        }
                        addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                        addFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
                        addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                        setDimAmount(0.42f)
                    }
                    break
                }
                parent = parent.parent
            }
        }

        val resolvedPaneTitle = paneTitleText
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val maxResponsiveHeight = (configuration.screenHeightDp * 0.88f).dp.coerceAtLeast(240.dp)
        val maxResponsiveWidth = if (configuration.screenWidthDp > 600) 480.dp else (configuration.screenWidthDp * 0.92f).dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                ),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = maxResponsiveWidth)
                    .heightIn(max = maxResponsiveHeight)
                    .padding(horizontal = 8.dp)
                    .focusable() // §6.0：弹窗自身可聚焦，配合 Compose 弹窗窗口焦点陷阱，防止键盘焦点逃逸到背景层
                    .semantics { paneTitle = resolvedPaneTitle }
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = {} // 阻止点击弹窗内容冒泡触发关闭
                    )
                    .echoHazePanel(
                        hazeState = null, // 弹窗处于独立的 Window 中，跨窗口注册会导致宿主背景留存幽灵白窗残影，此处传入 null
                        shape = shape,
                        tint = resolvedTint,
                        blurRadius = EchoTokens.Glass.blurRadiusHeavy
                    ),
                shape = shape,
                color = resolvedContainerColor,
                contentColor = resolvedContentColor,
                tonalElevation = EchoTokens.Elevation.none,
                shadowElevation = EchoTokens.Elevation.none
            ) {
                Column(
                    modifier = Modifier.padding(EchoTokens.Spacing.lg)
                ) {
                    if (title != null) {
                        title()
                        Spacer(modifier = Modifier.height(EchoTokens.Spacing.sm))
                    }
                    if (content != null) {
                        Box(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                content()
                            }
                        }
                    }
                    if (buttons != null) {
                        Spacer(modifier = Modifier.height(EchoTokens.Spacing.md))
                        buttons()
                    }
                }
            }
        }
    }
}

@Composable
fun EchoGlassDialog(
    hazeState: HazeState? = null,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = EchoTokens.Radius.shapeXl,
    tint: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    containerColor: Color = Color.Unspecified,
    icon: (@Composable ColumnScope.() -> Unit)? = null,
    title: (@Composable ColumnScope.() -> Unit)? = null,
    text: (@Composable ColumnScope.() -> Unit)? = null,
    confirmButton: @Composable RowScope.() -> Unit,
    dismissButton: (@Composable RowScope.() -> Unit)? = null
) {
    EchoGlassDialog(
        hazeState = hazeState,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = shape,
        tint = tint,
        contentColor = contentColor,
        containerColor = containerColor,
        title = if (icon != null || title != null) {
            {
                icon?.invoke(this)
                title?.invoke(this)
            }
        } else null,
        content = text,
        buttons = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                dismissButton?.invoke(this)
                if (dismissButton != null) {
                    // §5.7：弹窗按钮间距统一 12dp（调用方不再各自叠加间距）
                    Spacer(modifier = Modifier.width(EchoTokens.Spacing.md))
                }
                confirmButton()
            }
        }
    )
}

/**
 * 玻璃风格下拉菜单（v2.6.8 需求 5：自绘不透明容器，彻底消除颜色不均与边缘黑影）。
 *
 * 此前直接使用 Material3 `DropdownMenu`。该组件内部 Surface 同时施加 **3dp 色调高度**(tonal elevation)
 * 与 **3dp 阴影高度**(shadow elevation)，而本项目的玻璃底色取自 `glass.panelStrong`
 * （`surface` + 0.92/0.94 alpha 的半透明色），于是产生两个可见缺陷：
 * 1. 半透明底色会透出背后聊天正文，再叠加色调高度的 primary 着色 —— 表现为「颜色不均匀」；
 * 2. 18dp 圆角外缘投出一圈黑色阴影 —— 表现为「边缘黑影」。
 *
 * 现改为自绘 Popup：容器底色完全不透明（surface 手工叠 5% surfaceTint，等价 3dp 色调高度的观感，
 * 但无任何透明度）、色调高度与阴影高度均为 0，仅保留 1dp 描边与 18dp 圆角 ——
 * 菜单颜色在任意背景上恒定均匀、无任何外缘黑影。定位沿用 Material3 的锚点避让策略
 * （见 [EchoMenuPositionProvider]），展开/收起行为与调用方用法保持完全兼容。
 */
@Composable
fun EchoGlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: androidx.compose.ui.unit.DpOffset = androidx.compose.ui.unit.DpOffset(0.dp, 0.dp),
    properties: androidx.compose.ui.window.PopupProperties = androidx.compose.ui.window.PopupProperties(focusable = true),
    content: @Composable ColumnScope.() -> Unit
) {
    val glass = echoGlassPalette()
    val colors = MaterialTheme.colorScheme
    val menuShape = RoundedCornerShape(18.dp)
    // 不透明菜单底色：以 surface 为基色手工叠 5% surfaceTint（等价 M3 3dp 色调高度的观感），
    // 但合成结果 alpha = 1，背后内容不会透出，也就不会出现深浅不均的斑块
    val containerColor = colors.surfaceTint.copy(alpha = 0.05f).compositeOver(colors.surface)

    val expandedState = remember { MutableTransitionState(false) }
    expandedState.targetState = expanded

    if (expandedState.currentState || expandedState.targetState || !expandedState.isIdle) {
        val density = LocalDensity.current
        val positionProvider = remember(offset, density) { EchoMenuPositionProvider(offset, density) }
        Popup(
            popupPositionProvider = positionProvider,
            onDismissRequest = onDismissRequest,
            properties = properties
        ) {
            // P1-1 菜单锚点生长动效：内容以右上锚点缩放 0.85→1.0（emphasizedDecelerate）
            // + 快速淡入展开；scaleIn 为绘制层变换，不改变弹窗定位测量（transformOrigin 对齐锚点）
            val reducedMotion = com.aiassistant.ui.theme.rememberReducedMotion()
            AnimatedVisibility(
                visibleState = expandedState,
                enter = if (reducedMotion) {
                    EnterTransition.None
                } else {
                    fadeIn(com.aiassistant.ui.theme.EchoMotion.tweenSpec(com.aiassistant.ui.theme.EchoMotion.Duration.fast)) +
                        scaleIn(
                            initialScale = 0.85f,
                            animationSpec = com.aiassistant.ui.theme.EchoMotion.tweenSpec(com.aiassistant.ui.theme.EchoMotion.Duration.standard, com.aiassistant.ui.theme.EchoMotion.Easing.emphasizedDecelerate),
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f) // A6：右上锚点（三点按钮在右侧，菜单自其右下角生长）
                        )
                },
                exit = fadeOut(com.aiassistant.ui.theme.EchoMotion.tweenSpec(com.aiassistant.ui.theme.EchoMotion.Duration.fast))
            ) {
                Surface(
                    modifier = modifier,
                    shape = menuShape,
                    color = containerColor,
                    contentColor = colors.onSurface,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    border = BorderStroke(1.dp, glass.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(min = 160.dp, max = 280.dp)
                            .width(IntrinsicSize.Max)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 8.dp),
                        content = content
                    )
                }
            }
        }
    }
}

/**
 * 下拉菜单定位策略（移植 Material3 DropdownMenuPositionProvider 的锚点避让规则，保证自绘容器定位不退化）：
 * 1. 水平依次尝试「与锚点左缘对齐 → 与锚点右缘对齐 → 贴窗口左/右边缘」（贴哪一边由锚点位于窗口左半/右半决定）；
 * 2. 垂直依次尝试「贴锚点下方 → 翻到锚点上方 → 贴窗口底部 → 贴窗口顶部」，上下各保留 [MenuVerticalMargin] 边距；
 * 3. 取第一个完整落在窗口内的候选位置，全部不满足时钳制进窗口，菜单永不跑到屏幕外。
 */
private class EchoMenuPositionProvider(
    private val contentOffset: androidx.compose.ui.unit.DpOffset,
    private val density: Density
) : PopupPositionProvider {

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val offsetX = with(density) { contentOffset.x.roundToPx() }
        val offsetY = with(density) { contentOffset.y.roundToPx() }
        val verticalMargin = with(density) { MenuVerticalMargin.roundToPx() }

        val menuWidth = popupContentSize.width
        val menuHeight = popupContentSize.height
        val maxX = (windowSize.width - menuWidth).coerceAtLeast(0)
        val maxY = (windowSize.height - menuHeight).coerceAtLeast(0)

        val horizontalCandidates = if (layoutDirection == LayoutDirection.Ltr) {
            listOf(
                anchorBounds.left + offsetX,
                anchorBounds.right - menuWidth + offsetX,
                if (anchorBounds.center.x < windowSize.width / 2) 0 else maxX
            )
        } else {
            listOf(
                anchorBounds.right - menuWidth + offsetX,
                anchorBounds.left + offsetX,
                if (anchorBounds.center.x < windowSize.width / 2) maxX else 0
            )
        }
        val x = horizontalCandidates.firstOrNull { it in 0..maxX }
            ?: horizontalCandidates.last().coerceIn(0, maxX)

        val verticalCandidates = listOf(
            anchorBounds.bottom + offsetY,
            anchorBounds.top - menuHeight + offsetY,
            (maxY - verticalMargin).coerceAtLeast(0),
            verticalMargin.coerceAtMost(maxY)
        )
        val y = verticalCandidates.firstOrNull { it in 0..maxY }
            ?: verticalCandidates.last().coerceIn(0, maxY)

        return IntOffset(x, y)
    }

    private companion object {
        /** Material3 MenuVerticalMargin 令牌值：菜单与窗口上下缘的最小边距 */
        val MenuVerticalMargin = 48.dp
    }
}
