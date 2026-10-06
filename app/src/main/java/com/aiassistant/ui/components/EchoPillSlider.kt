package com.aiassistant.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.key.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.luminance
import com.aiassistant.ui.theme.EchoTokens
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.gestures.awaitEachGesture

/**
 * 严格还原参考图 media_1788845280823.jpg 的高精度胶囊圆润滑块组件：
 * 1. 26dp 高度全圆角药丸轨道，左侧高亮激活色，右侧柔和浅灰背景；
 * 2. 轨道中心按离散档位均匀排布吸附圆点（激活侧半透明白色，未激活侧柔和微深色）；
 * 3. 28dp 纯白高光圆形浮雕 Thumb（带柔和立体阴影），支持手指跟随与平滑弹簧吸附。
 */
@Composable
fun EchoPillSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..4f,
    steps: Int = 3, // 中间停靠点数量，总离散点数 = steps + 2
    activeColor: Color = Color(0xFF22C55E),
    inactiveColor: Color? = null,
    trackHeight: Dp = 26.dp,
    thumbSize: Dp = 28.dp,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val resolvedInactiveColor = inactiveColor ?: if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E7EB)

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentValue by rememberUpdatedState(value)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)

    val minVal = valueRange.start
    val maxVal = valueRange.endInclusive
    val valSpan = (maxVal - minVal).coerceAtLeast(0.001f)
    val totalDiscreteStops = (steps + 2).coerceAtLeast(2)

    val currentMinVal by rememberUpdatedState(minVal)
    val currentMaxVal by rememberUpdatedState(maxVal)
    val currentValSpan by rememberUpdatedState(valSpan)
    val currentTotalStops by rememberUpdatedState(totalDiscreteStops)

    val animatedValue = remember { Animatable(value) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(value, valueRange, steps) {
        if (!isDragging) {
            animatedValue.animateTo(
                targetValue = value.coerceIn(minVal, maxVal),
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp.coerceAtLeast(thumbSize).coerceAtLeast(trackHeight))
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(valueRange), valueRange, steps)
                setProgress { requested ->
                    val step = valSpan / (totalDiscreteStops - 1)
                    val target = (minVal + ((requested - minVal) / step).roundToInt() * step).coerceIn(valueRange)
                    if (target == value) false else {
                        currentOnValueChange(target)
                        currentOnValueChangeFinished?.invoke()
                        true
                    }
                }
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || event.key !in listOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown)) false
                else {
                    val direction = if (event.key == Key.DirectionRight || event.key == Key.DirectionUp) 1 else -1
                    currentOnValueChange((value + direction * valSpan / (totalDiscreteStops - 1)).coerceIn(valueRange))
                    currentOnValueChangeFinished?.invoke()
                    true
                }
            }
            .focusable(),
        contentAlignment = Alignment.CenterStart
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val trackHeightPx = with(density) { trackHeight.toPx() }
        val thumbSizePx = with(density) { thumbSize.toPx() }
        val thumbRadiusPx = thumbSizePx / 2f

        val minThumbX = thumbRadiusPx
        val maxThumbX = (widthPx - thumbRadiusPx).coerceAtLeast(minThumbX)
        val travelDistance = (maxThumbX - minThumbX).coerceAtLeast(1f)

        val currentMinThumbX by rememberUpdatedState(minThumbX)
        val currentTravelDistance by rememberUpdatedState(travelDistance)

        var lastTouchX by remember { mutableFloatStateOf(minThumbX) }
        var lastReportedStep by remember { mutableIntStateOf(-1) }

        val currentProgress = if (isDragging) {
            ((lastTouchX - minThumbX) / travelDistance).coerceIn(0f, 1f)
        } else {
            ((animatedValue.value - minVal) / valSpan).coerceIn(0f, 1f)
        }
        val currentThumbCenterX = minThumbX + currentProgress * travelDistance

        fun snapToNearest(rawX: Float) {
            val progress = ((rawX - currentMinThumbX) / currentTravelDistance).coerceIn(0f, 1f)
            val stepIndex = (progress * (currentTotalStops - 1)).roundToInt().coerceIn(0, currentTotalStops - 1)
            val targetVal = currentMinVal + (stepIndex.toFloat() / (currentTotalStops - 1)) * currentValSpan
            val currentTouchVal = currentMinVal + progress * currentValSpan
            lastReportedStep = stepIndex
            if (targetVal != currentValue) currentOnValueChange(targetVal)
            currentOnValueChangeFinished?.invoke()
            scope.launch {
                animatedValue.snapTo(currentTouchVal)
                isDragging = false
                animatedValue.animateTo(
                    targetValue = targetVal,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
                )
            }
        }

        fun updateContinuous(rawX: Float) {
            lastTouchX = rawX
            val progress = ((rawX - currentMinThumbX) / currentTravelDistance).coerceIn(0f, 1f)
            val stepIndex = (progress * (currentTotalStops - 1)).roundToInt().coerceIn(0, currentTotalStops - 1)
            val snappedVal = currentMinVal + (stepIndex.toFloat() / (currentTotalStops - 1)) * currentValSpan
            if (stepIndex != lastReportedStep) {
                lastReportedStep = stepIndex
                currentOnValueChange(snappedVal)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp.coerceAtLeast(thumbSize).coerceAtLeast(trackHeight))
                .pointerInput(valueRange, steps) {
                    detectTapGestures { snapToNearest(it.x) }
                }
                .pointerInput(valueRange, steps) {
                    detectHorizontalDragGestures(
                        onDragStart = { isDragging = true; lastReportedStep = -1 },
                        onDragCancel = { isDragging = false },
                        onDragEnd = { snapToNearest(lastTouchX) }
                    ) { change, _ -> updateContinuous(change.position.x) }
                }
        ) {
            // 1. 绘制药丸圆角背景轨道与离散点
            Canvas(modifier = Modifier.matchParentSize()) {
                val trackTop = (heightPx - trackHeightPx) / 2f
                val cornerRadius = CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f)

                // 完整胶囊轨道裁剪路径
                val trackPath = Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            left = 0f,
                            top = trackTop,
                            right = widthPx,
                            bottom = trackTop + trackHeightPx,
                            cornerRadius = cornerRadius
                        )
                    )
                }

                // A. 绘制未激活背景底色
                drawRoundRect(
                    color = resolvedInactiveColor,
                    topLeft = Offset(0f, trackTop),
                    size = Size(widthPx, trackHeightPx),
                    cornerRadius = cornerRadius
                )

                // B. 绘制已激活侧高亮色（严格跟随当前 Thumb 移动并与全圆角轨道裁切对齐）
                clipPath(trackPath) {
                    drawRect(
                        color = activeColor,
                        topLeft = Offset(0f, trackTop),
                        size = Size(currentThumbCenterX.coerceAtLeast(0f), trackHeightPx)
                    )
                }

                // C. 绘制离散档位圆点
                val dotRadius = 2.4.dp.toPx()
                for (i in 0 until totalDiscreteStops) {
                    val stepProgress = i.toFloat() / (totalDiscreteStops - 1)
                    val dotCenterX = minThumbX + stepProgress * travelDistance
                    val dotCenterY = heightPx / 2f

                    val isLeftOfThumb = dotCenterX <= currentThumbCenterX
                    val dotColor = if (isLeftOfThumb) {
                        Color.White.copy(alpha = 0.55f)
                    } else {
                        if (isDark) Color.White.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.22f)
                    }

                    drawCircle(
                        color = dotColor,
                        radius = dotRadius,
                        center = Offset(dotCenterX, dotCenterY)
                    )
                }
            }

            // 2. 绘制纯白带阴影浮雕圆形滑块 (Thumb)
            val thumbLeft = (currentThumbCenterX - thumbRadiusPx).roundToInt()
            val thumbTop = ((heightPx - thumbSizePx) / 2f).roundToInt()

            Box(
                modifier = Modifier
                    .offset { IntOffset(thumbLeft, thumbTop) }
                    .size(thumbSize)
                    .shadow(elevation = EchoTokens.Elevation.raised(isDark).elevation, shape = CircleShape, ambientColor = EchoTokens.Elevation.raised(isDark).ambient, spotColor = EchoTokens.Elevation.raised(isDark).spot)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}
