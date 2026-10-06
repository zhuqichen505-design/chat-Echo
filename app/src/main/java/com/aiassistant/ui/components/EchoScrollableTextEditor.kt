package com.aiassistant.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * v2.7.4：bringIntoView 门禁——拦截 BasicTextField 内建的光标可见性请求外传。
 *
 * v2.7.6 修复：1.6.8 的 [BringIntoViewResponderNode] 在调用 responder 后会**无条件**继续向
 * 父级转发请求（`launch { parent.bringChildIntoView(...) }`），no-op 门禁拦不住传播；
 * 获得焦点时 focusable 会请求"整个字段矩形"入视口，该矩形被原样转发给外层滚动容器后，
 * 视口被强制对到内容顶端——表现为点击/聚焦即跳顶。
 *
 * 本门禁改为把请求矩形**钳制到编辑器当前可视窗口**内：已可见的矩形使滚动容器判定
 * 无需滚动，请求就地终结（焦点整字段请求、光标矩形请求均不再引发跳顶）；
 * 真正的光标可见性由 [EchoScrollableTextEditor] 内部的 LaunchedEffect 以最小距离滚动自行接管。
 */
@OptIn(ExperimentalFoundationApi::class)
private class EditorBringIntoViewClampGate(
    /** 返回编辑器可视窗口在字段内容坐标系中的范围（px）；视口未就绪时返回 null（不钳制） */
    private val visibleRange: () -> ClosedFloatingPointRange<Float>?
) : BringIntoViewResponder {
    override fun calculateRectForParent(rect: androidx.compose.ui.geometry.Rect): androidx.compose.ui.geometry.Rect {
        val range = visibleRange() ?: return rect
        val top = rect.top.coerceIn(range.start, range.endInclusive)
        val bottom = rect.bottom.coerceIn(range.start, range.endInclusive)
        return rect.copy(top = top, bottom = bottom)
    }

    override suspend fun bringChildIntoView(localRect: () -> androidx.compose.ui.geometry.Rect?) {
        // 有意留空：本层不产生滚动，仅修正向上传播的矩形
    }
}

/**
 * Echo 大文本编辑器（v2.7.2 需求 1/2/3）：
 * 供「编辑模型回复 / 仅修改消息内容 / 系统提示词」这类包含大量文本的内容框统一使用。
 *
 * - 需求 3（滚动跳顶修复）：此前 OutlinedTextField 高度被 heightIn 截断后由其内部滚动接管，
 *   文本变化时内部滚动位置会被重置，表现为「在中间输入时页面跳回文字顶端」。
 *   本组件改为 BasicTextField 在无高度约束环境整体布局，滚动由外层 verticalScroll 承担，
 *   光标可见性经 bringIntoView 沿外层滚动解析——只在光标移出可视区时最小距离滚动，不再跳顶。
 * - 需求 2（右侧滑块）：内容溢出时右缘出现细轨道 + 圆角拇指滑块，支持拖动/点按直接定位。
 * - 高度随内容自适应（minHeight~maxHeight），溢出后固定在 maxHeight 内部滚动。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EchoScrollableTextEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    minHeight: Dp = 140.dp,
    maxHeight: Dp = 340.dp,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    secondaryColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    containerColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
    /** 外部可注入 ScrollState（如「跳转末尾」按钮需要滚动到底） */
    scrollState: ScrollState = rememberScrollState()
) {
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    // 内容真实高度（BasicTextField 在无限高约束内整体布局，onSizeChanged 即全文高度）
    var contentHeightPx by remember { mutableIntStateOf(0) }
    val minPx = with(density) { minHeight.roundToPx() }
    val maxPx = with(density) { maxHeight.roundToPx() }
    val boxHeightPx = contentHeightPx.coerceIn(minPx, maxPx)
    val boxHeight = with(density) { boxHeightPx.toDp() }

    var textLayout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }

    // v2.7.6：钳制型门禁（见类注释）——可视窗口 = [scrollValue, scrollValue + viewport]，
    // 即字段内容坐标系（门禁节点 = 字段根节点，二者同坐标系）
    val bringIntoViewGate = remember(scrollState) {
        EditorBringIntoViewClampGate {
            val viewport = scrollState.viewportSize
            if (viewport <= 0) {
                null
            } else {
                scrollState.value.toFloat()..(scrollState.value + viewport).toFloat()
            }
        }
    }

    // v2.7.4 需求 3：光标可见性由编辑器自行接管——
    // ① BasicTextField 内建的 bringIntoView 在本结构下实测会把视口强制带到内容顶端、
    //    且无法跟随光标，用 no-op 门禁（editorBringIntoViewGate）拦截其外传；
    // ② 选区/布局变化时，仅在光标行移出可视区时以最小距离滚动跟随——点击可视区内位置
    //    绝不滚动（修复「点击即强制滑到内容顶端」），输入时内容始终跟随光标。
    LaunchedEffect(value.selection, textLayout, contentHeightPx) {
        val layout = textLayout ?: return@LaunchedEffect
        val viewport = scrollState.viewportSize
        if (viewport <= 0) return@LaunchedEffect
        val offset = value.selection.end.coerceIn(0, layout.layoutInput.text.length)
        val line = layout.getLineForOffset(offset)
        val cursorTop = layout.getLineTop(line)
        val cursorBottom = layout.getLineBottom(line)
        val current = scrollState.value
        val margin = with(density) { 4.dp.roundToPx() }
        when {
            cursorTop < current -> scrollState.scrollTo((cursorTop - margin).roundToInt().coerceAtLeast(0))
            cursorBottom > current + viewport ->
                scrollState.scrollTo((cursorBottom - viewport + margin).roundToInt().coerceAtLeast(0))
        }
    }

    val shape = RoundedCornerShape(14.dp)
    val borderColor = if (focused) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.62f)
    } else {
        secondaryColor.copy(alpha = 0.28f)
    }

    Row(modifier = modifier.height(boxHeight)) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(shape)
                .background(containerColor)
                .border(BorderStroke(0.8.dp, borderColor), shape)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .verticalScroll(scrollState)
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = contentColor),
                    cursorBrush = SolidColor(contentColor),
                    interactionSource = interactionSource,
                    onTextLayout = { textLayout = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewResponder(bringIntoViewGate)
                        .onSizeChanged { contentHeightPx = it.height }
                )
            }
            if (value.text.isEmpty() && placeholder.isNotEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = secondaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.TopStart)
                )
            }
        }

        // 固定宽度槽位：滑块隐藏时布局不跳动
        Box(
            modifier = Modifier
                .width(16.dp)
                .fillMaxHeight()
        ) {
            EchoVerticalScrollSlider(
                scrollState = scrollState,
                viewportPx = boxHeightPx,
                contentPx = contentHeightPx,
                modifier = Modifier.fillMaxHeight(),
                accentColor = contentColor
            )
        }
    }
}

/**
 * 竖向滚动滑块（需求 2）：内容溢出视口时淡入，拇指位置/长度按滚动比例实时绘制；
 * 支持拇指/轨道拖动与点按定位。拖动期间以手势进度为准，避免滚动值回写滞后造成拇指抖动。
 */
@Composable
fun EchoVerticalScrollSlider(
    scrollState: ScrollState,
    viewportPx: Int,
    contentPx: Int,
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val scope = rememberCoroutineScope()
    val scrollable = viewportPx > 0 && contentPx > viewportPx
    var dragProgress by remember { mutableStateOf<Float?>(null) }
    var trackHeightPx by remember { mutableIntStateOf(0) }
    val currentScrollState by rememberUpdatedState(scrollState)
    val currentTrackHeightPx by rememberUpdatedState(trackHeightPx)

    fun scrollToFraction(fraction: Float) {
        val h = currentTrackHeightPx
        if (h <= 0) return
        val max = currentScrollState.maxValue
        if (max <= 0) return
        dragProgress = fraction.coerceIn(0f, 1f)
        scope.launch {
            currentScrollState.scrollTo((fraction.coerceIn(0f, 1f) * max).roundToInt())
        }
    }

    AnimatedVisibility(
        visible = scrollable,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxHeight()
                .onSizeChanged { trackHeightPx = it.height }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            scrollToFraction(offset.y / size.height.toFloat())
                        },
                        onDragEnd = { dragProgress = null },
                        onDragCancel = { dragProgress = null },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            scrollToFraction(change.position.y / size.height.toFloat())
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            scrollToFraction(offset.y / size.height.toFloat())
                        }
                    )
                }
        ) {
            val max = scrollState.maxValue.toFloat()
            if (max <= 0f || contentPx <= 0) return@Canvas
            val progress = dragProgress
                ?: (scrollState.value / max).coerceIn(0f, 1f)
            val thumbHeight = (size.height * (viewportPx.toFloat() / contentPx))
                .coerceIn(36.dp.toPx(), size.height)
            val thumbTop = (size.height - thumbHeight) * progress
            val trackWidth = 3.dp.toPx()
            val thumbWidth = 5.dp.toPx()
            val x = size.width - thumbWidth

            drawRoundRect(
                color = trackColor,
                topLeft = Offset(x + (thumbWidth - trackWidth) / 2f, 0f),
                size = Size(trackWidth, size.height),
                cornerRadius = CornerRadius(trackWidth, trackWidth)
            )
            drawRoundRect(
                color = accentColor.copy(alpha = 0.55f),
                topLeft = Offset(x, thumbTop),
                size = Size(thumbWidth, thumbHeight),
                cornerRadius = CornerRadius(thumbWidth / 2f, thumbWidth / 2f)
            )
        }
    }
}
