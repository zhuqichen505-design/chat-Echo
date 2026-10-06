package com.aiassistant.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import com.aiassistant.ui.theme.EchoMotion
import com.aiassistant.ui.theme.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.cos

/**
 * Echo 连接脉冲环（方案 P0-1）
 * 一圈圆环以呼吸式缩放 + 透明度脉冲表达"连接中/思考中"的活动感。
 *
 * v2.7.2 需求 4：新增 animated 参数——回复完毕后的落定态以「静态圆环」呈现（animated=false，
 * 不创建 InfiniteTransition），生成全程（连接→思考→流式）保持 animated=true 的圆环动效。
 *
 * 性能约束（R-3/R-7）：单个 InfiniteTransition 驱动，动画值仅在 Canvas（draw 阶段）读取，
 * 零重组、零每帧对象分配。reduced motion / animated=false 时退化为静态圆环。
 */
@Composable
fun EchoPulseRing(
    color: Color,
    modifier: Modifier = Modifier,
    ringSize: Dp = 16.dp,
    strokeWidth: Dp = 1.8.dp,
    animated: Boolean = true
) {
    val reduced = rememberReducedMotion()
    // 检查报告 P3-2：reduced motion / 静态圆环下条件创建 InfiniteTransition，避免帧回调空转（微功耗）
    val pulse: Float = if (reduced) {
        0.8f
    } else if (!animated) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "echoPulseRing")
        val v by transition.animateFloat(
            initialValue = 0.6f,
            targetValue = 1f,
            animationSpec = EchoMotion.reverseCycleSpec<Float>(EchoMotion.Cycle.pulse),
            label = "pulseScale"
        )
        v
    }
    Canvas(modifier) {
        val radius = ringSize.toPx() / 2f - strokeWidth.toPx() / 2f
        val scale = pulse
        val alpha = if (reduced || !animated) 0.85f else pulse
        drawCircle(
            color = color.copy(alpha = alpha),
            radius = radius * scale,
            center = Offset(size.width / 2f, size.height / 2f),
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        )
    }
}

/**
 * Echo 重连双弧环（方案 P0-1 ④）
 * 两段弧以 1.2s/圈 追逐旋转，表达"重连进行中"。
 * 与脉冲环同一性能约束：仅 draw 阶段动画，reduced motion 时退化为静态单弧。
 */
@Composable
fun EchoDoubleArcRing(
    color: Color,
    modifier: Modifier = Modifier,
    ringSize: Dp = 16.dp,
    strokeWidth: Dp = 1.8.dp
) {
    val reduced = rememberReducedMotion()
    // 检查报告 P3-2：reduced motion 下条件创建（静态 0° 单弧）
    val rotation: Float = if (reduced) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "echoDoubleArc")
        val v by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = EchoMotion.linearCycleSpec(EchoMotion.Cycle.reconnectArc),
            label = "arcRotation"
        )
        v
    }
    Canvas(modifier) {
        val radius = ringSize.toPx() / 2f - strokeWidth.toPx() / 2f
        val sweep = 100f
        val baseRotation = rotation
        val center = Offset(size.width / 2f, size.height / 2f)
        // 双弧追逐：主弧 100° + 次弧 60°，相位差 180°
        drawArc(
            color = color,
            startAngle = baseRotation,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        )
        drawArc(
            color = color.copy(alpha = 0.45f),
            startAngle = baseRotation + 180f,
            sweepAngle = sweep * 0.6f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        )
    }
}

/**
 * Echo 思考等待指示器「行波点」（替代原波浪点，供 TypingIndicator 使用）
 * 三个圆点做正弦行波：相位均分 1/3 周期，任意时刻仅一个点处于峰值，
 * 形成清晰的左→右依次点亮流动感，表达"正在思考/等待首 token"。
 *
 * v2.7.9 美观性重构（纯绘制层，性能约束不变）：
 * ① 纯净行波——连续正弦（0→1→0）取代"55% 脉冲窗 + 静息"，周期接缝一阶连续零跳变；
 *    相位差由 0.24 改为 1/3，相邻点脉冲零重叠，不再是"一团同时起伏"；
 * ② 单层柔光——删除远层扩散光环与白色高光点：旧光环峰值半径 3.7r 远超半点距 1.7r，
 *    邻点光晕互相交叠糊成一片，1dp 高光点在深色强调色上像坏点；
 * ③ 三点同尺寸同基色——删除尺寸/亮度阶梯，静息相位保持 0.38 透明度清晰可辨。
 *
 * 配色约定：消费 generationAccentColor（思考模型取思考档位色，否则 primary），
 * 与流式光标、脉冲环同源（P0-1②/P0-2②）。
 *
 * 性能约束（R-3/R-7）：单 InfiniteTransition + Canvas，动画值仅在 draw 阶段读取，
 * 零重组、零每帧堆分配（Color/Offset 均为值类型）。reduced motion 时退化为静态三点。
 */
@Composable
fun EchoThinkingDots(
    color: Color,
    modifier: Modifier = Modifier,
    dotRadius: Dp = 2.8.dp
) {
    val reduced = rememberReducedMotion()
    // 检查报告 P3-2：reduced motion 下条件创建 InfiniteTransition，避免帧回调空转
    val t: Float = if (reduced) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "echoThinkingDots")
        val v by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = EchoMotion.linearCycleSpec(EchoMotion.Cycle.thinkingDots),
            label = "dotPhase"
        )
        v
    }
    Canvas(modifier) {
        val r = dotRadius.toPx()
        val gap = r * 3.4f
        val totalWidth = gap * 2
        val baseY = size.height / 2f
        repeat(3) { i ->
            val x = size.width / 2f - totalWidth / 2f + i * gap
            // 行波相位：三点均分 1/3 周期，任意时刻仅一个点接近峰值；reduced 时 t=0 全部静息
            val phase = (t - i / 3f).mod(1f)
            // 连续正弦：0→1→0 平滑往返，周期接缝一阶连续，无跳变
            val wave = if (reduced) 0f else 0.5f - 0.5f * cos((phase * 2.0 * PI).toFloat())
            // 峰值轻微上浮（小半半径），静息时落回基线
            val dotY = baseY - r * 0.55f * wave
            // 单层柔光：与脉冲同步呼吸（1.15r→1.55r < 半点距 1.7r，与邻点零交叠）
            if (!reduced && wave > 0.02f) {
                drawCircle(
                    color = color.copy(alpha = 0.14f * wave),
                    radius = r * (1.15f + 0.4f * wave),
                    center = Offset(x, dotY)
                )
            }
            // 主体点：呼吸式缩放 + 提亮，静息相位保持 0.38 透明度不消失
            drawCircle(
                color = color.copy(alpha = 0.38f + 0.62f * wave),
                radius = r * (0.82f + 0.30f * wave),
                center = Offset(x, dotY)
            )
        }
    }
}
