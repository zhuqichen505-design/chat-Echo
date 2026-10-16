package com.aiassistant.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.aiassistant.ui.theme.EchoMotion
import com.aiassistant.ui.theme.rememberReducedMotion
import com.aiassistant.utils.EchoModelMarkStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.abs

/** 身份标识的展示状态；历史消息没有可靠完成凭据时保持中性。 */
enum class EchoModelMarkState {
    Neutral, Connecting, Thinking, Replying, Completed;

    val isActive: Boolean get() = this == Connecting || this == Thinking || this == Replying
}

/** 一次完成只消费一次；切换显示设置或重挂载历史不会自行反复点头。 */
internal class ModelMarkCompletionMotion {
    private var previous: EchoModelMarkState? = null
    private var consumedCompletionEvent: Long = 0L

    fun update(state: EchoModelMarkState, completionEvent: Long = 0L): Boolean {
        val freshEvent = completionEvent != 0L && completionEvent != consumedCompletionEvent
        if (freshEvent) consumedCompletionEvent = completionEvent
        val play = state == EchoModelMarkState.Completed && (previous?.isActive == true || freshEvent)
        previous = state
        return play
    }
}

/** 保留主题色倾向，必要时向黑/白收敛，避免自定义背景吞掉细线。 */
fun modelMarkColorFor(accent: Color, backdrop: Color): Color {
    val surface = backdrop.compositeOver(Color.White)
    fun contrast(color: Color): Float {
        val first = color.compositeOver(surface).luminance()
        val second = surface.luminance()
        return (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f)
    }
    if (contrast(accent) >= 4.5f) return accent
    val target = readableTextColorFor(surface)
    for (step in 1..20) {
        val candidate = lerp(accent, target, step / 20f)
        if (contrast(candidate) >= 4.5f) return candidate
    }
    return target
}

/**
 * 头像关闭时的模型标识。默认双描边同心圆对应阅读预览中的 mark。
 * 相位只在 Canvas 绘制时读取，不使流式正文重新组合或测量；结束后卸载动画。
 */
@Composable
internal fun EchoModelMark(
    color: Color,
    style: String = EchoModelMarkStyle.CONCENTRIC,
    animated: Boolean = false,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = rememberReducedMotion(),
    description: String? = null,
    state: EchoModelMarkState = if (animated) EchoModelMarkState.Replying else EchoModelMarkState.Neutral,
    completionEvent: Long = 0L,
    completionOwner: ModelMarkCompletionMotion? = null
) {
    val resolvedStyle = EchoModelMarkStyle.sanitize(style)
    val isBot = resolvedStyle in EchoModelMarkStyle.botStyles
    val isExpressiveBot = resolvedStyle in EchoModelMarkStyle.expressiveBotStyles
    // Retain the controller across style choices so changing a completed identity cannot replay its event.
    val expressiveMotion = remember(completionOwner) {
        ExpressiveBotMotionController(resolvedStyle, completionOwner ?: ModelMarkCompletionMotion())
    }
    val expressiveFrame = remember { mutableStateOf(expressiveMotion.pose) }
    val completionProgress = remember { Animatable(1f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var lifecycleStarted by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            lifecycleStarted = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state, completionEvent, reducedMotion, resolvedStyle, lifecycleStarted, expressiveMotion) {
        // Consume exactly once; native and legacy drawing share this result, including across remounts.
        val playCompletion = expressiveMotion.update(state, completionEvent, reducedMotion, resolvedStyle, enabled = isExpressiveBot)
        expressiveFrame.value = expressiveMotion.pose
        completionProgress.snapTo(1f)
        if (!lifecycleStarted) return@LaunchedEffect
        if (isBot && !isExpressiveBot && playCompletion && !reducedMotion) {
            completionProgress.snapTo(0f)
            completionProgress.animateTo(1f, EchoMotion.tweenSpec(EchoMotion.Duration.slow))
        }
        var lastFrame = 0L
        while (expressiveMotion.needsFrames) {
            withFrameNanos { now ->
                if (lastFrame != 0L) expressiveMotion.advance((now - lastFrame) / 1_000_000f)
                lastFrame = now
                // Only the Canvas observes this value: the streamed message never recomposes per frame.
                expressiveFrame.value = expressiveMotion.pose
            }
        }
    }
    val phase: State<Float>? = if (state.isActive && !reducedMotion && !isExpressiveBot) {
        rememberInfiniteTransition(label = "modelMark").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = EchoMotion.linearCycleSpec(EchoMotion.Cycle.pulse),
            label = "modelMarkPhase"
        )
    } else null
    Canvas(modifier.size(26.dp).semantics {
        description?.let { contentDescription = it }
    }) {
        if (isExpressiveBot) {
            drawExpressiveBot(color, expressiveFrame.value)
            return@Canvas
        }
        val progress = phase?.value ?: 0f
        val pulse = if (phase == null) 0f else sin((progress * 2f * PI).toFloat())
        if (isBot) {
            drawModelBot(color, resolvedStyle, state, progress,
                moving = phase != null, completionProgress = completionProgress.value)
            return@Canvas
        }
        val radius = size.minDimension * 0.43f
        val stroke = (size.minDimension * 0.065f).coerceAtLeast(1.dp.toPx())
        when (resolvedStyle) {
            EchoModelMarkStyle.ORBIT -> {
                drawCircle(color.copy(alpha = color.alpha * 0.48f), radius, style = Stroke(stroke * 0.8f))
                drawCircle(color, radius * 0.24f)
                val angle = progress * 2f * PI.toFloat() - PI.toFloat() / 2f
                val orbitRadius = radius - stroke * 0.3f
                drawCircle(color, stroke * 1.15f,
                    center + Offset(cos(angle) * orbitRadius, sin(angle) * orbitRadius))
                drawCircle(color.copy(alpha = color.alpha * 0.65f), stroke * 0.85f,
                    center - Offset(cos(angle) * orbitRadius, sin(angle) * orbitRadius))
            }
            EchoModelMarkStyle.PRISM -> {
                // 小幅摇转保持菱形辨识度；不是高速、连续翻转的加载图标。
                rotate(pulse * 18f) {
                    val corners = listOf(
                        center + Offset(0f, -radius), center + Offset(radius, 0f),
                        center + Offset(0f, radius), center + Offset(-radius, 0f)
                    )
                    corners.indices.forEach { index ->
                        drawLine(color, corners[index], corners[(index + 1) % corners.size], stroke, StrokeCap.Round)
                    }
                    val inner = radius * (0.44f + 0.04f * pulse)
                    drawLine(color.copy(alpha = color.alpha * 0.6f), center - Offset(inner, 0f), center - Offset(0f, inner), stroke)
                    drawLine(color.copy(alpha = color.alpha * 0.6f), center - Offset(0f, inner), center + Offset(inner, 0f), stroke)
                    drawLine(color.copy(alpha = color.alpha * 0.6f), center + Offset(inner, 0f), center + Offset(0f, inner), stroke)
                    drawLine(color.copy(alpha = color.alpha * 0.6f), center + Offset(0f, inner), center - Offset(inner, 0f), stroke)
                }
            }
            EchoModelMarkStyle.WAVEFORM -> {
                repeat(5) { index ->
                    val restingHeight = if (index == 2) 0.78f else if (index % 2 == 1) 0.5f else 0.28f
                    val variation = if (phase == null) 0f else
                        0.18f * sin((progress * 2f * PI + index * 0.8f).toFloat())
                    val halfHeight = radius * (restingHeight + variation)
                    val x = center.x + (index - 2) * radius * 0.4f
                    drawLine(color, Offset(x, center.y - halfHeight), Offset(x, center.y + halfHeight),
                        stroke * 1.15f, StrokeCap.Round)
                }
            }
            else -> {
                // 两条完整圆线保持预览的双环结构，仅轻微相向舒展，无额外加载点。
                drawCircle(color, radius * (1f + 0.025f * pulse), style = Stroke(stroke))
                drawCircle(color.copy(alpha = color.alpha * (0.86f + 0.10f * pulse)),
                    radius * (0.60f - 0.045f * pulse), style = Stroke(stroke))
            }
        }
    }
}

/** Original two-eye identity, drawn natively at any size; all limbs and decorative props are omitted. */
internal fun DrawScope.drawExpressiveBot(color: Color, pose: ExpressiveBotPose) {
    val factor = size.minDimension / 100f
    val eyeColor = readableTextColorFor(color)
    withTransform({
        translate((size.width - size.minDimension) * .5f, (size.height - size.minDimension) * .5f)
        scale(factor, factor, Offset.Zero)
        translate(pose.bodyX, pose.bodyY)
        rotate(pose.rotation, Offset(50f, 54f))
        scale(pose.scaleX, pose.scaleY, Offset(50f, 54f))
    }) {
        drawRoundRect(color, Offset(50f - pose.bodyWidth * .5f, 54f - pose.bodyHeight * .5f),
            Size(pose.bodyWidth, pose.bodyHeight), CornerRadius(pose.cornerRadius, pose.cornerRadius))
        listOf(pose.left, pose.right).forEach { eye ->
            drawRoundRect(eyeColor,
                Offset(pose.faceX + eye.x - eye.width * .5f, pose.faceY + eye.y - eye.height * .5f),
                Size(eye.width, eye.height), CornerRadius(minOf(eye.width, eye.height) * .5f))
        }
    }
}

/** 26×26 归一坐标，线宽1.45；面部在小尺寸下保留足够间距。所有动态只在绘制阶段读取。 */
private fun DrawScope.drawModelBot(
    color: Color,
    style: String,
    state: EchoModelMarkState,
    progress: Float,
    moving: Boolean,
    completionProgress: Float
) {
    val scaleFactor = size.minDimension / 26f
    val wave = if (moving) sin(progress * 2f * PI.toFloat()) else 0f
    val nod = if (state == EchoModelMarkState.Completed) sin(completionProgress * PI.toFloat()) else 0f
    val angle = when (state) {
        EchoModelMarkState.Thinking -> if (moving) -3f + wave * 2f else -4f
        EchoModelMarkState.Completed -> nod * 4f
        else -> 0f
    }
    withTransform({
        translate((size.width - size.minDimension) / 2f, (size.height - size.minDimension) / 2f)
        scale(scaleFactor, scaleFactor, Offset.Zero)
    }) {
        rotate(angle, Offset(13f, 14f)) {
            withTransform({ translate(0f, nod * 1.1f) }) {
                val stroke = Stroke(1.45f, cap = StrokeCap.Round)
                fun line(start: Offset, end: Offset, tint: Color = color) =
                    drawLine(tint, start, end, 1.45f, StrokeCap.Round)
                val earOffset = if (state == EchoModelMarkState.Replying) wave * 0.75f else 0f
                val connectionStep = if (moving) (progress * 3f).toInt().coerceIn(0, 2) else 0
                val antennaColor = if (state == EchoModelMarkState.Connecting && moving && connectionStep != 0)
                    color.copy(alpha = color.alpha * 0.4f) else color
                when (style) {
                    EchoModelMarkStyle.BOT_ROUND -> {
                        drawCircle(color, 9f, Offset(13f, 14f), style = stroke)
                        line(Offset(13f, 2.5f), Offset(13f, 5f), antennaColor)
                        drawCircle(antennaColor, 1.1f, Offset(13f, 2.5f))
                        line(Offset(2f, 13f + earOffset), Offset(4f, 13f))
                        line(Offset(22f, 13f), Offset(24f, 13f - earOffset))
                    }
                    EchoModelMarkStyle.BOT_SQUARE -> {
                        drawRoundRect(color, Offset(4f, 6f), Size(18f, 16f), CornerRadius(4f, 4f), style = stroke)
                        line(Offset(2f, 13f + earOffset), Offset(4f, 13f))
                        line(Offset(22f, 13f), Offset(24f, 13f - earOffset))
                        line(Offset(13f, 3.8f), Offset(13f, 6f), antennaColor)
                        drawCircle(antennaColor, 1f, Offset(13f, 3f))
                    }
                    EchoModelMarkStyle.BOT_CAPSULE -> {
                        drawRoundRect(color, Offset(2f, 7f), Size(22f, 15f), CornerRadius(7.5f, 7.5f), style = stroke)
                        line(Offset(4f, 5.5f + earOffset), Offset(4f, 8f), antennaColor)
                        line(Offset(22f, 5.5f - earOffset), Offset(22f, 8f), antennaColor)
                    }
                    else -> {
                        drawRoundRect(color, Offset(4f, 7f), Size(18f, 15f), CornerRadius(1.4f, 1.4f), style = stroke)
                        line(Offset(13f, 3f), Offset(13f, 7f), antennaColor)
                        line(Offset(11f, 3f), Offset(15f, 3f), antennaColor)
                        line(Offset(2f, 12f + earOffset), Offset(2f, 15f + earOffset))
                        line(Offset(24f, 12f - earOffset), Offset(24f, 15f - earOffset))
                    }
                }
                val eyeLift = if (state == EchoModelMarkState.Thinking) -1f else 0f
                listOf(9.3f, 16.7f).forEachIndexed { index, x ->
                    val eyeTint = if (state == EchoModelMarkState.Connecting && moving && connectionStep != index + 1)
                        color.copy(alpha = color.alpha * 0.4f) else color
                    when {
                        state == EchoModelMarkState.Completed -> {
                            val smileEye = Path().apply {
                                moveTo(x - 1.2f, 13.4f)
                                quadraticBezierTo(x, 11.4f, x + 1.2f, 13.4f)
                            }
                            drawPath(smileEye, color, style = stroke)
                        }
                        state == EchoModelMarkState.Connecting && !moving ->
                            line(Offset(x - 0.9f, 13.2f), Offset(x + 0.9f, 13.2f))
                        style == EchoModelMarkStyle.BOT_PIXEL ->
                            drawRoundRect(eyeTint, Offset(x - 1.1f, 12.1f + eyeLift), Size(2.2f, 2.2f), CornerRadius(0.35f, 0.35f))
                        else -> drawCircle(eyeTint, 1.15f, Offset(x, 13.2f + eyeLift))
                    }
                }
                when (state) {
                    EchoModelMarkState.Completed -> {
                        val smile = Path().apply {
                            moveTo(10.5f, 17f)
                            quadraticBezierTo(13f, 20f, 15.5f, 17f)
                        }
                        drawPath(smile, color, style = stroke)
                    }
                    EchoModelMarkState.Replying -> {
                        val mouthHeight = if (moving) 1.6f + abs(wave) * 2f else 2.7f
                        drawOval(color, Offset(11.2f, 17.3f - mouthHeight / 2f), Size(3.6f, mouthHeight), style = stroke)
                    }
                    EchoModelMarkState.Thinking -> line(Offset(12f, 17.5f), Offset(15f, 17f))
                    else -> line(Offset(10.8f, 17.3f), Offset(15.2f, 17.3f))
                }
            }
        }
    }
}
