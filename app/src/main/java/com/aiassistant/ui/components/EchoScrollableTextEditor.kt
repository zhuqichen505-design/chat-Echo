package com.aiassistant.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text2.TextFieldDecorator
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
 * A bounded, native-scrolling editor shared by message editing and system prompts.
 * Text, selection handles, IME and the scrollbar now use the same native viewport.
 * There is deliberately no outer verticalScroll, bring-into-view clamp or cursor overlay.
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
    /** Shared with existing jump-to-end controls. */
    scrollState: ScrollState = rememberScrollState()
) {
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    var fieldHeightPx by remember { mutableIntStateOf(0) }
    val shape = com.aiassistant.ui.components.EchoGlassControlShape
    val borderColor = if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.62f)
        else secondaryColor.copy(alpha = 0.28f)
    val viewport = scrollState.viewportSize
    val content = if (viewport > 0) (viewport.toLong() + scrollState.maxValue)
        .coerceAtMost(Int.MAX_VALUE.toLong()).toInt() else 0

    Row(modifier = modifier) {
        EchoNativeTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = minHeight, max = maxHeight.coerceAtLeast(minHeight))
                .onSizeChanged { fieldHeightPx = it.height }
                .clip(shape)
                .background(containerColor)
                .border(BorderStroke(0.8.dp, borderColor), shape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = contentColor),
            interactionSource = interactionSource,
            cursorBrush = SolidColor(contentColor),
            scrollState = scrollState,
            decorator = TextFieldDecorator { innerTextField ->
                Box(Modifier.fillMaxWidth()) {
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
                    innerTextField()
                }
            }
        )
        // Keep the scrollbar slot stable while native scrolling controls its exact range.
        Box(Modifier.width(16.dp).height(with(density) { fieldHeightPx.toDp() })) {
            EchoVerticalScrollSlider(
                scrollState = scrollState,
                viewportPx = viewport,
                contentPx = content,
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
            currentScrollState.scroll(androidx.compose.foundation.MutatePriority.UserInput) {
                scrollBy((fraction.coerceIn(0f, 1f) * max).roundToInt() - currentScrollState.value.toFloat())
            }
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
