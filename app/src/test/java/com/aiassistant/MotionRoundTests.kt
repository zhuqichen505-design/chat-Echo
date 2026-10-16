package com.aiassistant

import com.aiassistant.ui.components.MarkdownSegmentation
import com.aiassistant.ui.components.MarkdownSegmentationCache
import com.aiassistant.ui.components.computeStableSegments
import com.aiassistant.ui.components.modelMarkColorFor
import com.aiassistant.ui.components.EchoModelMarkState
import com.aiassistant.ui.components.ModelMarkCompletionMotion
import com.aiassistant.ui.components.ExpressiveBotMotionController
import com.aiassistant.ui.components.ExpressiveBotPose
import com.aiassistant.ui.components.ExpressiveBotEffectKind
import com.aiassistant.ui.components.ExpressiveBotEffectPose
import com.aiassistant.ui.components.expressiveBotDurationMillis
import com.aiassistant.ui.components.expressiveBotRestPose
import com.aiassistant.ui.components.sampleExpressiveBotPose
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.aiassistant.ui.screens.chat.GenerationUiState
import com.aiassistant.ui.screens.chat.GenerationUiStateRules
import com.aiassistant.utils.EchoModelMarkStyle
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 动效轮（UI-微交互与动效深化方案.md）核心红线测试：
 * - R-2：Markdown 增量渲染分段不变量（前缀稳定/还原/围栏/数学块/表格安全）
 * - P0-4：GenerationUiState 状态机派生行为等价
 */
class MotionRoundTests {

    @Test fun expressiveExternalSymbolsMoveThroughConnectionThinkingReplyingAndExplicitIdle() {
        val states = mapOf(EchoModelMarkState.Connecting to ExpressiveBotEffectKind.Signal,
            EchoModelMarkState.Thinking to ExpressiveBotEffectKind.Thought,
            EchoModelMarkState.Replying to ExpressiveBotEffectKind.Speech,
            EchoModelMarkState.Neutral to ExpressiveBotEffectKind.Z)
        for (style in EchoModelMarkStyle.expressiveBotStyles) {
            for ((state, kind) in states) {
                val frames = (0..100).map { sampleExpressiveBotPose(style, state, it * 80f, idleEffects = true) }
                val effects = frames.flatMap { it.effects }.filter { it.alpha > .05f }
                assertTrue("$style $state 必须呈现对应状态的符号", effects.any { it.kind == kind } && effects.all {
                    it.kind == kind || state == EchoModelMarkState.Connecting && it.kind == ExpressiveBotEffectKind.SignalDot
                })
                if (kind == ExpressiveBotEffectKind.Thought || kind == ExpressiveBotEffectKind.Z) {
                    assertTrue("$style $state 符号位置或尺寸应实际连续变化", effects.map { listOf(it.x, it.y, it.size) }.toSet().size >= 15)
                    val horizontalTravel = effects.maxOf { it.x } - effects.minOf { it.x }
                    val verticalTravel = effects.maxOf { it.y } - effects.minOf { it.y }
                    val travel = sqrt(horizontalTravel * horizontalTravel + verticalTravel * verticalTravel)
                    val growth = effects.maxOf { it.size } - effects.minOf { it.size }
                    val minimumTravel = if (kind == ExpressiveBotEffectKind.Thought) 2.5f else 5f
                    val minimumGrowth = if (kind == ExpressiveBotEffectKind.Thought) 2.5f else 3.5f
                    assertTrue("$style $state 位移应可感知，不能只做亚单位浮动: $travel", travel >= minimumTravel)
                    assertTrue("$style $state 放大应可感知，不能只依赖整组透明度: $growth", growth >= minimumGrowth)
                } else {
                    assertTrue("连接扇弧与说话泡保持稳定轮廓，通过透明度缓慢交替", effects.groupBy { it.id }.values.all { track ->
                        track.map { listOf(it.x, it.y, it.size) }.distinct().size == 1
                    })
                }
                if (kind == ExpressiveBotEffectKind.Speech) {
                    assertTrue("输出泡内三点的进度应持续变化，不能仅让整个静态图案淡入淡出",
                        effects.map { it.progress }.distinct().size >= 15)
                }
                assertTrue("$style $state 符号应渐显渐隐", effects.map { it.alpha }.toSet().size >= 15)
                assertTrue("同帧符号不能使用冲突id", frames.all { frame -> frame.effects.map { it.id }.distinct().size == frame.effects.size })
            }
            val rest = expressiveBotRestPose(style)
            for (time in listOf(0f, 1000f, 30_000f)) {
                assertEquals("历史中性消息默认不显示待机符号", rest, sampleExpressiveBotPose(style, EchoModelMarkState.Neutral, time))
            }
        }
    }

    @Test fun expressiveSymbolsUseClearUpperRightSemanticsAndUnhurriedHolds() {
        for (state in listOf(EchoModelMarkState.Connecting, EchoModelMarkState.Thinking,
            EchoModelMarkState.Replying, EchoModelMarkState.Neutral)) {
            val frames = (0..160).map { sampleExpressiveBotPose(EchoModelMarkStyle.BOT_TILE, state, it * 100f, idleEffects = true).effects }
            for (frame in frames) {
                val visible = frame.filter { it.alpha > .1f }
                for (effect in visible) {
                    val minimumSize = when (effect.kind) {
                        ExpressiveBotEffectKind.Signal -> 7f
                        ExpressiveBotEffectKind.SignalDot -> 2f
                        ExpressiveBotEffectKind.Thought -> 11f
                        ExpressiveBotEffectKind.Speech -> 16f
                        ExpressiveBotEffectKind.Z -> 8f
                        else -> 10f
                    }
                    assertTrue("$state 符号不能退回小而难辨的尺寸", effect.size >= minimumSize)
                    val bounds = expressiveEffectBounds(effect)
                    assertTrue("$state 所有状态符号统一锚定右上角", bounds[0] > 65f && bounds[3] < 18f)
                }
                if (state == EchoModelMarkState.Connecting) {
                    val dot = frame.single { it.kind == ExpressiveBotEffectKind.SignalDot }
                    val arcs = frame.filter { it.kind == ExpressiveBotEffectKind.Signal }.sortedBy { it.size }
                    assertEquals("信号图案由基点和三个等级扇弧组成", 3, arcs.size)
                    for (arc in arcs) {
                        assertEquals("信号扇弧围绕基点展开", dot.x, arc.x, 0f)
                        assertEquals(dot.y, arc.y, 0f)
                    }
                    arcs.zipWithNext().forEach { (inner, outer) ->
                        assertTrue("扇弧描边之间需有清晰间隙", (outer.size - inner.size) / 2f - 2.2f >= 1f)
                    }
                } else {
                    assertEquals("思考泡、说话泡或Z使用单个完整语义组，不能堆叠多个图案", 1, frame.size)
                }
            }
            val firstId = frames.first().first { it.kind != ExpressiveBotEffectKind.SignalDot }.id
            val alphas = frames.map { frame -> frame.first { it.id == firstId }.alpha }
            val pulses = alphas.zipWithNext().count { (before, after) -> before <= .45f && after > .45f }
            assertTrue("$state 在16秒内应缓慢重复，避免高频闪动: $pulses", pulses in 2..4)
            var hold = 0
            var longestHold = 0
            alphas.zipWithNext().forEach { (before, after) ->
                hold = if (before > .65f && abs(after - before) < .0001f) hold + 1 else 0
                longestHold = maxOf(longestHold, hold)
            }
            assertTrue("$state 符号须有可供阅读的停留，不能持续快速进出", longestHold >= 7)
        }
    }

    @Test fun expressiveSymbolsStayFiniteInsideCanvasAndSeparateFromTheTransformedBody() {
        for (style in EchoModelMarkStyle.expressiveBotStyles) {
            for (state in EchoModelMarkState.entries) {
                for (reduced in listOf(false, true)) {
                    for (time in (0..100).map { it * 80f } + listOf(-10f, Float.NaN, Float.POSITIVE_INFINITY)) {
                        val pose = sampleExpressiveBotPose(style, state, time, reducedMotion = reduced, idleEffects = true)
                        for (effect in pose.effects) {
                            assertTrue("符号参数必须为有限数", listOf(effect.x, effect.y, effect.size, effect.alpha, effect.progress).all { it.isFinite() })
                            assertTrue(effect.size > 0f && effect.alpha in 0f..1f && effect.progress in 0f..1f)
                            val bounds = expressiveEffectBounds(effect)
                            assertTrue("$style $state 符号完整描边应在100×100画板内", bounds[0] > 0f && bounds[1] > 0f && bounds[2] < 100f && bounds[3] < 100f)
                            if (effect.alpha > 0f) for (column in 0..4) for (row in 0..4) {
                                val x = bounds[0] + (bounds[2] - bounds[0]) * column / 4f
                                val y = bounds[1] + (bounds[3] - bounds[1]) * row / 4f
                                assertTrue("$style $state 外部符号不能遮住旋转后的身体",
                                    expressiveBodyClearance(x, y, pose) > .25f)
                            }
                        }
                    }
                }
            }
        }
    }

    @Test fun expressiveSymbolsFadeAcrossStatesAndClearAfterCompletionOrCancelledIdle() {
        for (style in EchoModelMarkStyle.expressiveBotStyles) {
            val controller = ExpressiveBotMotionController(style)
            controller.update(EchoModelMarkState.Connecting)
            advanceExpressiveBot(controller, 700f)
            val before = controller.pose
            controller.update(EchoModelMarkState.Thinking)
            assertEquals("切换状态不能瞬间移除外部符号", before, controller.pose)
            advanceExpressiveBot(controller, 96f)
            val fadingSignals = controller.pose.effects.filter { it.kind == ExpressiveBotEffectKind.Signal }
            assertTrue("旧连接弧在过渡中应逐渐淡出", fadingSignals.any { fading ->
                before.effects.any { it.id == fading.id && fading.alpha < it.alpha && fading.alpha > 0f }
            })
            assertTrue("新思考泡在过渡中应渐入", controller.pose.effects.any { it.kind == ExpressiveBotEffectKind.Thought && it.alpha > 0f })
            advanceExpressiveBot(controller, 300f)
            assertTrue(controller.pose.effects.all { it.kind == ExpressiveBotEffectKind.Thought })
            controller.update(EchoModelMarkState.Completed, completionEvent = 211L)
            advanceExpressiveBot(controller, 400f)
            assertTrue("完成时有有限确认符号", controller.pose.effects.any { it.kind == ExpressiveBotEffectKind.Spark && it.alpha > 0f })
            advanceExpressiveBot(controller, 2000f)
            assertTrue("默认完成后不能遗留符号", controller.pose.effects.isEmpty())
            assertFalse(controller.needsFrames)

            controller.update(EchoModelMarkState.Neutral, idleEffects = true)
            advanceExpressiveBot(controller, 700f)
            assertTrue(controller.needsFrames)
            assertTrue(controller.pose.effects.any { it.kind == ExpressiveBotEffectKind.Z && it.alpha > 0f })
            val sleeping = controller.pose
            controller.update(EchoModelMarkState.Neutral, idleEffects = false)
            assertEquals("取消待机也应从当前可见符号淡出", sleeping, controller.pose)
            advanceExpressiveBot(controller, 300f)
            assertEquals(expressiveBotRestPose(style), controller.pose)
            assertFalse("取消、失败或离开最新回复后应停止待机帧循环", controller.needsFrames)
        }
    }

    @Test fun expressiveIdleRequiresExplicitEligibilityAndHonorsReducedMotionAndFrameTime() {
        for (style in EchoModelMarkStyle.expressiveBotStyles) {
            val rest = expressiveBotRestPose(style)
            val loweringGaze = listOf(0f, 300f, 600f, 1200f).map { time ->
                sampleExpressiveBotPose(style, EchoModelMarkState.Neutral, time, idleEffects = true)
            }
            assertEquals("待机视线从原位缓缓下移", 0f, loweringGaze.first().faceY, 0f)
            assertTrue("待机下看应逐步出现，不能瞬间换表情", loweringGaze.zipWithNext().all { (before, after) ->
                after.faceY > before.faceY
            })
            assertTrue("待机下看的最终位移应可见", loweringGaze.last().faceY >= 4f)
            for (pose in loweringGaze) {
                assertEquals("待机只移动视线，身体与另一状态的运动轨迹不能混入",
                    rest.copy(faceY = pose.faceY, left = pose.left, right = pose.right, effects = pose.effects), pose)
                assertEquals("待机双眼保持同步同形", pose.left.copy(x = pose.right.x), pose.right)
                assertEquals(rest.left.width, pose.left.width, 0f)
                assertEquals(rest.right.width, pose.right.width, 0f)
                assertTrue("待机下看不能令双眼越出身体", listOf(pose.left, pose.right).all { eye ->
                    eye.height > 0f && eye.height <= rest.left.height &&
                        abs(eye.y + pose.faceY - 54f) + eye.height / 2f < pose.bodyHeight / 2f
                })
            }
            for (state in listOf(EchoModelMarkState.Neutral, EchoModelMarkState.Completed)) {
                val historical = ExpressiveBotMotionController(style)
                historical.update(state)
                assertFalse("历史标识不能启动待机ticker", historical.needsFrames)
                assertTrue(historical.pose.effects.isEmpty())
            }
            val controller = ExpressiveBotMotionController(style)
            controller.update(EchoModelMarkState.Replying)
            controller.update(EchoModelMarkState.Completed, completionEvent = 212L, idleEffects = true)
            advanceExpressiveBot(controller, 3500f)
            assertTrue("显式最新回复完成后可进入待机", controller.needsFrames)
            assertTrue(controller.pose.effects.any { it.kind == ExpressiveBotEffectKind.Z && it.alpha > 0f })
            assertTrue(controller.pose.effects.none { it.kind == ExpressiveBotEffectKind.Spark })
            assertTrue("完成确认动作结束后也应缓缓下看", controller.pose.faceY >= 4f)
            for (state in EchoModelMarkState.entries) {
                val expected = sampleExpressiveBotPose(style, state, 0f, reducedMotion = true, idleEffects = true)
                if (state == EchoModelMarkState.Neutral || state == EchoModelMarkState.Completed) {
                    assertTrue("减弱动态直接使用静态下看姿态", expected.faceY >= 4f)
                }
                assertEquals("减弱动态的外部符号不应随时钟变化", expected,
                    sampleExpressiveBotPose(style, state, 90_000f, reducedMotion = true, idleEffects = true))
                controller.update(state, reducedMotion = true, idleEffects = true)
                assertFalse("系统减弱动态下不启动外部符号ticker", controller.needsFrames)
                val fixed = controller.pose
                advanceExpressiveBot(controller, 300f)
                assertEquals(fixed, controller.pose)
            }
            val resumed = ExpressiveBotMotionController(style)
            val normal = ExpressiveBotMotionController(style)
            resumed.update(EchoModelMarkState.Neutral, idleEffects = true)
            normal.update(EchoModelMarkState.Neutral, idleEffects = true)
            advanceExpressiveBot(resumed, 700f)
            advanceExpressiveBot(normal, 700f)
            val paused = resumed.pose
            resumed.update(EchoModelMarkState.Neutral, idleEffects = true)
            assertEquals("后台无可见帧时应保持符号姿态", paused, resumed.pose)
            resumed.advance(60_000f)
            normal.advance(ExpressiveBotMotionController.MAX_FRAME_MILLIS)
            assertEquals("后台恢复不能把zzz快进一分钟", normal.pose, resumed.pose)
        }
    }

    @Test fun onlyLatestSuccessfulIdleAssistantMayShowIdleSymbols() {
        val successful = "合成正常回复"
        for (state in GenerationUiState.entries) {
            val mayIdle = state == GenerationUiState.Idle || state == GenerationUiState.Settling
            assertEquals("最新正常回复的待机资格: $state", mayIdle,
                GenerationUiStateRules.modelMarkAllowsIdle(state, true, successful))
            assertFalse("历史回复不应启动待机: $state", GenerationUiStateRules.modelMarkAllowsIdle(state, false, successful))
            for (unsuccessful in listOf("", "回复已停止 (用户已暂停)", "合成部分正文\n\n*(回复已被暂停)*",
                "合成部分正文\n\n*(输出已被中断: 合成网络错误)*", "请求失败\n\n合成错误")) {
                assertFalse("空白、停止或失败回复不能待机: $state",
                    GenerationUiStateRules.modelMarkAllowsIdle(state, true, unsuccessful))
            }
        }
        assertTrue("正常引用暂停文字不能误伤待机资格", GenerationUiStateRules.modelMarkAllowsIdle(
            GenerationUiState.Idle, true, "她问：“回复已被暂停是什么意思？”"))
    }

    private fun expressiveEffectBounds(effect: ExpressiveBotEffectPose): FloatArray {
        val radius = effect.size / 2f
        return when (effect.kind) {
            ExpressiveBotEffectKind.Signal -> {
                floatArrayOf(effect.x - 1.1f, effect.y - radius - 1.1f,
                    effect.x + radius + 1.1f, effect.y + 1.1f)
            }
            ExpressiveBotEffectKind.SignalDot -> floatArrayOf(effect.x - radius, effect.y - radius,
                effect.x + radius, effect.y + radius)
            ExpressiveBotEffectKind.Thought -> floatArrayOf(effect.x - effect.size * 12.85f / 14f,
                effect.y - effect.size * 9f / 28f - 1.1f, effect.x + radius + 1.1f,
                effect.y + effect.size * 8.95f / 14f)
            ExpressiveBotEffectKind.Speech -> {
                val stroke = 1.1f * effect.size / 18f
                floatArrayOf(effect.x - radius - stroke, effect.y - effect.size * .3f - stroke,
                    effect.x + radius + stroke, effect.y + effect.size * 6.7f / 18f + stroke)
            }
            ExpressiveBotEffectKind.Z -> floatArrayOf(effect.x - radius * .72f - 1.1f,
                effect.y - radius - 1.1f, effect.x + radius * .72f + 1.1f, effect.y + radius + 1.1f)
            ExpressiveBotEffectKind.Spark -> floatArrayOf(effect.x - radius - 1.1f,
                effect.y - radius - 1.1f, effect.x + radius + 1.1f, effect.y + radius + 1.1f)
        }
    }

    private fun expressiveBodyClearance(x: Float, y: Float, pose: ExpressiveBotPose): Float {
        val angle = Math.toRadians(pose.rotation.toDouble()).toFloat()
        val dx = x - pose.bodyX - 50f
        val dy = y - pose.bodyY - 54f
        val localX = (dx * cos(angle) + dy * sin(angle)) / pose.scaleX
        val localY = (-dx * sin(angle) + dy * cos(angle)) / pose.scaleY
        val qx = abs(localX) - (pose.bodyWidth / 2f - pose.cornerRadius)
        val qy = abs(localY) - (pose.bodyHeight / 2f - pose.cornerRadius)
        val distance = sqrt(maxOf(qx, 0f) * maxOf(qx, 0f) + maxOf(qy, 0f) * maxOf(qy, 0f)) +
            minOf(maxOf(qx, qy), 0f) - pose.cornerRadius
        return distance
    }

    @Test fun expressiveBotsAnimateCommonGazeAndBodyRhythmThroughoutEveryGenerationState() {
        for (style in EchoModelMarkStyle.expressiveBotStyles) {
            for (state in EchoModelMarkState.entries.filter { it.isActive }) {
                val duration = expressiveBotDurationMillis(style, state)
                val frames = (0 until 40).map { sampleExpressiveBotPose(style, state, duration * it / 40f) }
                val bodies = frames.map { listOf(it.bodyX, it.bodyY, it.rotation, it.scaleX, it.scaleY) }.toSet()
                assertTrue("$style $state 身体必须持续运动", bodies.size >= 12)
                assertTrue("$style $state 双眼视线必须共同连续移动", frames.map { it.faceX to it.faceY }.toSet().size >= 12)
                val eyeHeights = (0..400).map { sampleExpressiveBotPose(style, state, it * 20f).left.height }
                val openHeight = expressiveBotRestPose(style).left.height
                assertTrue("$style $state 需有短暂同步眨眼", eyeHeights.any { it < openHeight * .2f })
                assertTrue("$style $state 眨眼间应恢复自然胶囊眼", eyeHeights.count { it == openHeight } > 300)
            }
            val connecting = expressiveFrames(style, EchoModelMarkState.Connecting)
            assertTrue("连接时视线应向两侧寻找", connecting.any { it.faceX < -3f } && connecting.any { it.faceX > 3f })
            val thinking = expressiveFrames(style, EchoModelMarkState.Thinking)
            assertTrue("思考应有克制而可辨识的歪头", thinking.any { abs(it.rotation) > 2f })
            assertTrue("思考应保持向上的共同视线", thinking.any { it.faceY < -2f })
            val replying = expressiveFrames(style, EchoModelMarkState.Replying)
            assertTrue("输出应包含轻微点头及回弹", replying.any { it.bodyY < -.1f } && replying.any { it.bodyY > .5f })
            assertTrue("自然动作不应剧烈摆动", (connecting + thinking + replying).all {
                abs(it.rotation) < 8f && abs(it.bodyY) < 3f
            })
            val bodySequences = listOf(connecting, thinking, replying).map { sequence ->
                sequence.map { listOf(it.bodyX, it.bodyY, it.rotation, it.scaleX, it.scaleY) }
            }
            assertEquals("三种状态须通过体态与节奏区分", 3, bodySequences.toSet().size)
        }
    }

    @Test fun expressiveBotEyesRemainFiniteAndInsideTheirBodyAcrossMotionAndReducedPoses() {
        for (style in EchoModelMarkStyle.expressiveBotStyles) {
            val rest = expressiveBotRestPose(style)
            for (state in EchoModelMarkState.entries) {
                val duration = expressiveBotDurationMillis(style, state).coerceAtLeast(1f)
                for (reduced in listOf(false, true)) {
                    val times = (0..100).map { duration * it / 100f } +
                        listOf(-100f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)
                    for (time in times) {
                        val pose = sampleExpressiveBotPose(style, state, time, reduced)
                        assertTrue("$style $state 身体几何应为有限数", listOf(pose.bodyWidth, pose.bodyHeight,
                            pose.cornerRadius, pose.bodyX, pose.bodyY, pose.rotation, pose.scaleX,
                            pose.scaleY, pose.faceX, pose.faceY).all { it.isFinite() })
                        assertTrue(pose.bodyWidth > 0f && pose.bodyHeight > 0f && pose.cornerRadius > 0f)
                        assertTrue(pose.scaleX in .8f..1.2f && pose.scaleY in .8f..1.2f)
                        assertEquals("双眼必须同步眨眼，不能一长一短", pose.left.height, pose.right.height, 0f)
                        assertEquals("双眼保持相同的固定宽度", pose.left.width, pose.right.width, 0f)
                        assertEquals("视线移动不能使两眼高低错位", pose.left.y, pose.right.y, 0f)
                        assertEquals(rest.left.x, pose.left.x, 0f)
                        assertEquals(rest.right.x, pose.right.x, 0f)
                        assertEquals(rest.left.y, pose.left.y, 0f)
                        assertEquals(rest.right.y, pose.right.y, 0f)
                        assertEquals(rest.left.width, pose.left.width, 0f)
                        assertEquals(rest.right.width, pose.right.width, 0f)
                        for (eye in listOf(pose.left, pose.right)) {
                            assertTrue(listOf(eye.x, eye.y, eye.width, eye.height).all { it.isFinite() })
                            assertTrue(eye.width > 0f && eye.height > 0f)
                            // Production draws a filled roundRect: its full width/height are the bounds.
                            assertTrue("$style $state 眼睛不应越出身体左右边缘",
                                abs(eye.x + pose.faceX - 50f) + eye.width / 2f < pose.bodyWidth / 2f)
                            assertTrue("$style $state 眼睛不应越出身体上下边缘",
                                abs(eye.y + pose.faceY - 54f) + eye.height / 2f < pose.bodyHeight / 2f)
                        }
                    }
                }
            }
        }
    }

    @Test fun expressiveBotTransitionsContinueFromInterruptedPoseAndStopAfterNeutralSettles() {
        for (style in EchoModelMarkStyle.expressiveBotStyles) {
            val controller = ExpressiveBotMotionController(style)
            for (state in listOf(EchoModelMarkState.Connecting, EchoModelMarkState.Thinking,
                EchoModelMarkState.Replying, EchoModelMarkState.Thinking)) {
                val before = controller.pose
                controller.update(state)
                assertEquals("状态切换的第一帧不能跳到另一张姿态", before, controller.pose)
                advanceExpressiveBot(controller, 96f)
                assertTrue("过渡必须从原姿态开始移动", before != controller.pose)
            }
            val interrupted = controller.pose
            controller.update(EchoModelMarkState.Neutral)
            assertEquals(interrupted, controller.pose)
            assertTrue(controller.needsFrames)
            advanceExpressiveBot(controller, ExpressiveBotMotionController.TRANSITION_MILLIS)
            assertEquals(expressiveBotRestPose(style), controller.pose)
            assertFalse("取消、失败与待选方向转中性后应停止帧循环", controller.needsFrames)
            val resting = controller.pose
            controller.advance(5000f)
            assertEquals(resting, controller.pose)
        }
    }

    @Test fun expressiveBotCompletionIsFiniteDeduplicatedAndNeverStartsOnHistoricalMount() {
        for (style in EchoModelMarkStyle.expressiveBotStyles) {
            val controller = ExpressiveBotMotionController(style)
            controller.update(EchoModelMarkState.Completed)
            assertEquals(expressiveBotRestPose(style), controller.pose)
            assertFalse("首次挂载的历史完成消息应静止", controller.needsFrames)
            controller.update(EchoModelMarkState.Replying)
            advanceExpressiveBot(controller, 410f)
            controller.update(EchoModelMarkState.Completed, completionEvent = 41L)
            advanceExpressiveBot(controller, 400f)
            val celebration = controller.pose
            assertTrue(celebration != expressiveBotRestPose(style))
            controller.update(EchoModelMarkState.Completed, completionEvent = 41L)
            assertEquals("同一完成事件的重组不能重置动作", celebration, controller.pose)
            advanceExpressiveBot(controller, 2000f)
            assertEquals(expressiveBotRestPose(style), controller.pose)
            assertFalse(controller.needsFrames)
            controller.update(EchoModelMarkState.Completed, completionEvent = 41L)
            assertFalse("已完成的事件不能重播", controller.needsFrames)
            controller.update(EchoModelMarkState.Completed, completionEvent = 42L)
            assertTrue("设置中的明确新预览事件可以重播", controller.needsFrames)
            advanceExpressiveBot(controller, 2000f)
            controller.update(EchoModelMarkState.Connecting)
            controller.update(EchoModelMarkState.Replying)
            controller.update(EchoModelMarkState.Completed)
            assertTrue("下一次真实生成仍可播放完成动作", controller.needsFrames)
        }
    }

    @Test fun expressiveBotsRespectReducedMotionAndDoNotReplayEventsAfterRestoringAnimation() {
        for (style in EchoModelMarkStyle.expressiveBotStyles) {
            for (state in EchoModelMarkState.entries) {
                val rest = sampleExpressiveBotPose(style, state, 0f, reducedMotion = true)
                for (time in listOf(75f, 4000f, 90_000f, Float.NaN)) {
                    assertEquals("减弱动态的姿态不应随时间变化", rest,
                        sampleExpressiveBotPose(style, state, time, reducedMotion = true))
                }
            }
            val controller = ExpressiveBotMotionController(style)
            controller.update(EchoModelMarkState.Thinking, reducedMotion = true)
            val thinking = controller.pose
            assertFalse(controller.needsFrames)
            advanceExpressiveBot(controller, 300f)
            assertEquals(thinking, controller.pose)
            controller.update(EchoModelMarkState.Completed, completionEvent = 73L, reducedMotion = true)
            assertEquals(expressiveBotRestPose(style), controller.pose)
            controller.update(EchoModelMarkState.Completed, completionEvent = 73L)
            assertFalse("恢复动画不应补播减弱期间已消费的完成", controller.needsFrames)
            controller.update(EchoModelMarkState.Replying, enabled = false)
            assertFalse(controller.needsFrames)
            controller.update(EchoModelMarkState.Completed, completionEvent = 74L, enabled = false)
            controller.update(EchoModelMarkState.Completed, completionEvent = 74L, enabled = true)
            assertFalse("头像/其他标识期间的完成不能在重新显示Bot后补播", controller.needsFrames)
        }
    }

    @Test fun expressiveBotControllerRejectsInvalidDeltasAndAvoidsBackgroundTimeCatchUp() {
        for (style in EchoModelMarkStyle.expressiveBotStyles) {
            val controller = ExpressiveBotMotionController(style)
            val normalFrame = ExpressiveBotMotionController(style)
            controller.update(EchoModelMarkState.Thinking)
            normalFrame.update(EchoModelMarkState.Thinking)
            advanceExpressiveBot(controller, 400f)
            advanceExpressiveBot(normalFrame, 400f)
            val before = controller.pose
            for (invalid in listOf(-100f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
                controller.advance(invalid)
                assertEquals("异常帧时间不能改变可见姿态", before, controller.pose)
            }
            controller.advance(60_000f)
            normalFrame.advance(ExpressiveBotMotionController.MAX_FRAME_MILLIS)
            assertEquals("后台一分钟不能把运动快进一分钟", normalFrame.pose, controller.pose)
        }
    }

    @Test fun changingExpressiveBotStyleDuringCompletionSettlesWithoutReplayingCelebration() {
        val styles = EchoModelMarkStyle.expressiveBotStyles.toList()
        for (index in styles.indices) {
            val controller = ExpressiveBotMotionController(styles[index])
            controller.update(EchoModelMarkState.Replying)
            advanceExpressiveBot(controller, 400f)
            controller.update(EchoModelMarkState.Completed, completionEvent = 91L)
            advanceExpressiveBot(controller, 350f)
            val before = controller.pose
            val replacement = styles[(index + 1) % styles.size]
            controller.update(EchoModelMarkState.Completed, completionEvent = 91L, style = replacement)
            assertEquals("切换体型时保留当前可见姿态", before, controller.pose)
            advanceExpressiveBot(controller, 300f)
            assertEquals(expressiveBotRestPose(replacement), controller.pose)
            assertFalse("切换体型后不能留下完成动画或空转帧循环", controller.needsFrames)
        }
    }

    @Test fun sharedMessageCompletionOwnerSurvivesIdentityRemountAndAllowsSameIdRegeneration() {
        for (style in EchoModelMarkStyle.expressiveBotStyles) {
            val owner = ModelMarkCompletionMotion()
            val firstIdentity = ExpressiveBotMotionController(style, owner)
            firstIdentity.update(EchoModelMarkState.Replying)
            advanceExpressiveBot(firstIdentity, 400f)
            firstIdentity.update(EchoModelMarkState.Completed, completionEvent = 103L)
            assertTrue(firstIdentity.needsFrames)

            // 气泡父级仍在，但头像开关或思考气泡开关使身份子组件重挂载。
            val remountedIdentity = ExpressiveBotMotionController(style, owner)
            remountedIdentity.update(EchoModelMarkState.Completed, completionEvent = 103L)
            assertEquals(expressiveBotRestPose(style), remountedIdentity.pose)
            assertFalse("同一消息的子组件重挂载不能重播完成", remountedIdentity.needsFrames)

            // 原消息 id 保持不变的重新生成仍是新一轮，不能被事件去重吞掉。
            remountedIdentity.update(EchoModelMarkState.Connecting)
            remountedIdentity.update(EchoModelMarkState.Replying)
            remountedIdentity.update(EchoModelMarkState.Completed, completionEvent = 103L)
            assertTrue("原id重新生成成功后仍应播放一次完成", remountedIdentity.needsFrames)
            advanceExpressiveBot(remountedIdentity, 2000f)
            assertFalse(remountedIdentity.needsFrames)
            remountedIdentity.update(EchoModelMarkState.Completed, completionEvent = 103L)
            assertFalse(remountedIdentity.needsFrames)
        }
    }

    private fun expressiveFrames(style: String, state: EchoModelMarkState): List<ExpressiveBotPose> =
        (0..80).map { sampleExpressiveBotPose(style, state, expressiveBotDurationMillis(style, state) * it / 80f) }

    private fun advanceExpressiveBot(controller: ExpressiveBotMotionController, durationMillis: Float) {
        var remaining = durationMillis
        while (remaining > 0f) {
            val step = minOf(16f, remaining)
            controller.advance(step)
            remaining -= step
        }
    }

    @Test fun botCompletionPlaysOnceForObservedGenerationAndNeverForHistoricalMountOrCancelledOutput() {
        val motion = ModelMarkCompletionMotion()
        var previous: GenerationUiState? = null
        fun advance(generating: Boolean, content: String, thinking: Boolean = false, retry: String? = null,
                    error: Boolean = false): Pair<EchoModelMarkState, Boolean> {
            val next = GenerationUiStateRules.derive(generating, content, thinking, retry, error)
            val completed = GenerationUiStateRules.modelMarkCompletionObserved(previous, next, content)
            val mark = GenerationUiStateRules.modelMarkState(next, completed)
            previous = next
            return mark to motion.update(mark)
        }
        assertEquals(EchoModelMarkState.Connecting to false, advance(true, ""))
        assertEquals(EchoModelMarkState.Thinking to false, advance(true, "", thinking = true))
        assertEquals(EchoModelMarkState.Replying to false, advance(true, "合成正文", thinking = true))
        assertEquals(EchoModelMarkState.Completed to true, advance(false, "合成正文", thinking = true))
        assertFalse(motion.update(EchoModelMarkState.Completed))
        assertFalse(ModelMarkCompletionMotion().update(EchoModelMarkState.Completed))

        // 下轮连接与恢复输出会重新建立一次完成机会。
        advance(true, "")
        advance(true, "合成重试正文")
        assertEquals(EchoModelMarkState.Completed to true, advance(false, "合成重试正文"))
        advance(true, "合成部分正文")
        assertEquals(EchoModelMarkState.Neutral to false,
            advance(false, "合成部分正文\n\n*(回复已被暂停)*"))
        advance(true, "", thinking = true)
        assertEquals(EchoModelMarkState.Neutral to false, advance(false, "*(思考已停止，回复已暂停)*", thinking = true))
        assertEquals(EchoModelMarkState.Neutral to false, advance(true, "", retry = "等待选择回复方向"))
        assertEquals(EchoModelMarkState.Neutral to false, advance(false, "请求失败\n\n合成错误", error = true))

        val preview = ModelMarkCompletionMotion()
        assertTrue(preview.update(EchoModelMarkState.Completed, completionEvent = 1L))
        assertFalse(preview.update(EchoModelMarkState.Completed, completionEvent = 1L))
        assertTrue(preview.update(EchoModelMarkState.Completed, completionEvent = 2L))
        // 即使减弱动态期间已消费事件，恢复动画后也不应补播旧完成。
        assertFalse(preview.update(EchoModelMarkState.Completed, completionEvent = 2L))
    }

    @Test fun botSettlingSelectsOnlyChangedSuccessfulMessageFromCurrentRoundIncludingRegeneratedId() {
        fun message(id: Long, content: String, group: String = "synthetic-turn") =
            com.aiassistant.domain.model.Message(id = id, conversationId = 1L, role = "assistant",
                content = content, variantGroupId = group)
        val original = message(1L, "合成旧回复")
        val before = mapOf(original.id to original.content)
        fun selected(vararg messages: com.aiassistant.domain.model.Message) =
            com.aiassistant.ui.screens.chat.settledModelMarkMessageId(before, messages.toList(), "synthetic-turn")
        assertEquals(0L, selected(original))
        assertEquals(0L, selected(original, message(2L, "合成部分正文\n\n*(回复已被暂停)*")))
        assertEquals(0L, selected(original, message(2L, "回复已停止 (用户已暂停)")))
        assertEquals(0L, selected(original, message(2L, "合成部分正文\n\n*(输出已被中断: 合成网络错误)*")))
        assertEquals(0L, selected(original, message(2L, "*(思考已输出，回复已被中断: 合成网络错误)*")))
        assertEquals(0L, selected(original, message(2L, "请求失败\n\n合成错误")))
        assertEquals(0L, selected(original, message(2L, "合成其他轮次", "synthetic-other")))
        assertEquals(2L, selected(original, message(2L, "合成正常回复")))
        assertEquals(0L, selected(original, message(2L, "合成前一段"),
            message(3L, "合成后段\n\n*(回复已被暂停)*")))
        assertEquals(1L, selected(original.copy(content = "合成原位重生成回复")))
        assertTrue(GenerationUiStateRules.modelMarkHasSuccessfulBody("她问：“回复已被暂停是什么意思？”"))
        assertTrue(GenerationUiStateRules.modelMarkHasSuccessfulBody("设定中写着 *(回复已被暂停)*，但故事继续。"))
        assertFalse(GenerationUiStateRules.modelMarkCompletionObserved(null, GenerationUiState.Idle, "合成历史回复"))
        assertFalse(GenerationUiStateRules.modelMarkCompletionObserved(GenerationUiState.Connecting,
            GenerationUiState.Idle, "合成历史回复"))
        assertFalse(GenerationUiStateRules.modelMarkCompletionObserved(GenerationUiState.Reconnecting,
            GenerationUiState.Idle, "合成历史回复"))
        assertFalse(GenerationUiStateRules.modelMarkCompletionObserved(GenerationUiState.Thinking,
            GenerationUiState.Idle, ""))
        assertTrue(GenerationUiStateRules.modelMarkCompletionObserved(GenerationUiState.Thinking,
            GenerationUiState.Idle, "合成稍后到达正文"))
        assertTrue(GenerationUiStateRules.modelMarkCompletionObserved(null, GenerationUiState.Idle,
            "合成新落库回复", completionSignal = true))
    }

    @Test fun modelMarkMovesDuringThinkingAndStreamingButNotAfterCompletionOrUserChoice() {
        val activeStates = setOf(GenerationUiState.Connecting, GenerationUiState.Reconnecting,
            GenerationUiState.Thinking, GenerationUiState.Streaming)
        GenerationUiState.entries.forEach { state ->
            assertEquals("模型标识状态 $state", state in activeStates, GenerationUiStateRules.animateModelMark(state))
        }
        val flow = listOf(
            GenerationUiStateRules.derive(true, "", false, null),
            GenerationUiStateRules.derive(true, "", true, null),
            GenerationUiStateRules.derive(true, "合成回复", true, null),
            GenerationUiStateRules.derive(false, "合成回复", true, null)
        )
        assertEquals(listOf(true, true, true, false), flow.map(GenerationUiStateRules::animateModelMark))
    }

    @Test fun modelMarkRetainsReadableThemeColorOnPureAndCustomBackdrops() {
        for (surface in listOf(Color.White, Color.Black, Color(0xFF536779), Color(0xFF162C29))) {
            for (accent in listOf(Color.White, Color.Black, Color(0xFFEEE8D8), Color(0xFF324253), Color(0xFF8574AC))) {
                val chosen = modelMarkColorFor(accent, surface)
                val foregroundLuminance = chosen.luminance()
                val backgroundLuminance = surface.luminance()
                val ratio = (maxOf(foregroundLuminance, backgroundLuminance) + 0.05f) /
                    (minOf(foregroundLuminance, backgroundLuminance) + 0.05f)
                assertTrue("自定义背景上的标识对比度不足: $ratio", ratio >= 4.5f)
            }
        }
        assertEquals(Color.Black, modelMarkColorFor(Color.Black, Color.White))
        assertEquals(Color.White, modelMarkColorFor(Color.White, Color.Black))
    }

    private fun rebuild(seg: MarkdownSegmentation): String = seg.segments.joinToString("") + seg.tail

    // ---------- R-2：稳定分段 ----------

    @Test
    fun testStableSegments_emptyContent() {
        val seg = computeStableSegments("")
        assertEquals(0, seg.segments.size)
        assertEquals("", seg.tail)
        assertFalse(seg.tailInFence)
    }

    @Test
    fun testStableSegments_incrementalCache_equivalentToFullRecompute() {
        // v2.7.3 流畅度：分段增量缓存（MarkdownSegmentationCache）必须与全文重算严格等价——
        // 覆盖标题/列表/闭合围栏/数学块/表格/空行边界逐 token 追加，以及非前缀跳变（切换 variant）回退
        val full = buildString {
            append("# 标题\n\n")
            append("第一段落内容，包含 **加粗** 与 [1] 引用。\n\n")
            append("- 列表项一\n- 列表项二\n\n")
            append("```kotlin\nfun main() {\n    val x = 1\n}\n```\n\n")
            append("闭合围栏后的段落。\n\n")
            append("$$\nx^2 + y^2 = z^2\n$$\n\n")
            append("| a | b |\n|---|---|\n| 1 | 2 |\n\n")
            append("尾段落，包含 *斜体* 与收尾文字。")
        }
        val cache = MarkdownSegmentationCache()
        var step = 1
        while (step <= full.length) {
            val partial = full.substring(0, step)
            val incremental = cache.get(partial)
            val fullRecompute = computeStableSegments(partial)
            assertEquals("段列表与全文重算不一致（step=$step）", fullRecompute.segments, incremental.segments)
            assertEquals("尾部与全文重算不一致（step=$step）", fullRecompute.tail, incremental.tail)
            assertEquals("围栏态与全文重算不一致（step=$step）", fullRecompute.tailInFence, incremental.tailInFence)
            assertEquals("段拼接+尾必须还原原文（step=$step）", partial, rebuild(incremental))
            step += 1
        }
        // 非前缀跳变：必须回退全量重算，结果仍等价
        val jumped = cache.get("完全不同的开头\n\n新内容")
        val fullJump = computeStableSegments("完全不同的开头\n\n新内容")
        assertEquals(fullJump.segments, jumped.segments)
        assertEquals(fullJump.tail, jumped.tail)
        assertEquals(fullJump.tailInFence, jumped.tailInFence)
        // 末段含未闭合围栏的追加路径
        val cache2 = MarkdownSegmentationCache()
        val fenceText = "说明段落\n\n```python\nprint(1)\n\nprint(2"
        var fstep = 1
        while (fstep <= fenceText.length) {
            val partial = fenceText.substring(0, fstep)
            val incremental = cache2.get(partial)
            val fullRecompute = computeStableSegments(partial)
            assertEquals(fullRecompute.segments, incremental.segments)
            assertEquals(fullRecompute.tail, incremental.tail)
            assertTrue("未闭合围栏阶段应保持围栏态（step=$fstep）", incremental.tailInFence == fullRecompute.tailInFence)
            assertEquals(partial, rebuild(incremental))
            fstep += 1
        }
    }

    @Test
    fun testStableSegments_monotonicGrowth_overTokenAppends() {
        // 模拟流式：逐 token 追加（R-2 核心）：
        // ① 已产出段序列只能追加、永不修改（字符串前缀稳定 → remember 零重算）
        // ② 段拼接 + 尾部必须严格还原原文
        val full = buildString {
            append("# 标题\n\n")
            append("第一段落内容，包含 **加粗** 与 *斜体* 片段。\n\n")
            append("- 列表项一\n- 列表项二\n\n")
            append("第二段落收尾文字。")
        }
        var lastCount = 0
        var lastSegments: List<String> = emptyList()
        var step = 8
        while (step <= full.length) {
            val partial = full.substring(0, step)
            val seg = computeStableSegments(partial)
            assertTrue(
                "段数量在 step=$step 减少：$lastCount -> ${seg.segments.size}",
                seg.segments.size >= lastCount
            )
            for (i in lastSegments.indices) {
                assertEquals("历史段 i=$i 在 step=$step 被修改", lastSegments[i], seg.segments[i])
            }
            assertEquals("段拼接+尾必须还原原文（step=$step）", partial, rebuild(seg))
            lastCount = seg.segments.size
            lastSegments = seg.segments
            step += 8
        }
        val finalSeg = computeStableSegments(full)
        assertTrue(finalSeg.segments.size >= lastCount)
    }

    @Test
    fun testStableSegments_unclosedFence_keepsTailInFence() {
        // 围栏未闭合：不得把围栏内的空行切段
        val content = "代码开始：\n\n```kotlin\nfun main() {\n\n    val x = 1\n"
        val seg = computeStableSegments(content)
        val stableText = seg.segments.joinToString("")
        assertTrue("未闭合围栏内不应产生稳定段中的围栏内容", !stableText.contains("fun main()"))
        assertTrue(seg.tailInFence)
        // 尾部含围栏开启行，渲染侧负责剥离开启行后以等宽体预览
        assertEquals("```kotlin\nfun main() {\n\n    val x = 1\n", seg.tail)
    }

    @Test
    fun testStableSegments_closedFence_becomesStable() {
        val content = "段落一。\n\n```python\nprint('hi')\n```\n\n后续段落。"
        val seg = computeStableSegments(content)
        val stableText = seg.segments.joinToString("")
        assertTrue("闭合围栏整体应进入稳定段", stableText.contains("print('hi')"))
        assertFalse(seg.tailInFence)
        assertEquals(content, rebuild(seg))
    }

    @Test
    fun testStableSegments_unclosedMathBlock_notSplit() {
        // $$ 块未闭合期间不得在块内空行处切段（公式跨段撕裂防护）
        val content = "前置段落。\n\n\$\$\nE = mc^2\n\n\\int_0^1 x dx\n"
        val seg = computeStableSegments(content)
        val stableText = seg.segments.joinToString("")
        assertTrue(stableText.contains("前置段落。"))
        assertFalse("公式内容不得进入稳定段", stableText.contains("E = mc^2"))
        assertTrue(seg.tail.contains("E = mc^2"))
    }

    @Test
    fun testStableSegments_closedMathBlock_becomesStable() {
        val content = "公式如下：\n\n\$\$E = mc^2\$\$\n\n后续内容。"
        val seg = computeStableSegments(content)
        val stableText = seg.segments.joinToString("")
        assertTrue("闭合公式整体应可进入稳定段", stableText.contains("E = mc^2") || seg.tail.contains("E = mc^2"))
        assertEquals(content, rebuild(seg))
    }

    @Test
    fun testStableSegments_trailingBlankLine_entersStable() {
        val content = "段落一。\n\n"
        val seg = computeStableSegments(content)
        // 内容以空行结尾：空行边界成段，尾部为空（该帧光标隐藏，属预期）
        assertEquals(1, seg.segments.size)
        assertEquals("段落一。\n\n", seg.segments[0])
        assertEquals("", seg.tail)
        assertFalse(seg.tailInFence)
    }

    @Test
    fun testStableSegments_tableBlock_staysIntact() {
        val content = "| A | B |\n|---|---|\n| 1 | 2 |\n\n下一段。"
        val seg = computeStableSegments(content)
        // 表格行之间无空行 → 表格要么整体在尾部，要么整体在稳定段，不得拦腰切断
        val stableText = seg.segments.joinToString("")
        val tableInStable = stableText.contains("| A | B |")
        val tableInTail = seg.tail.contains("| A | B |")
        assertTrue("表格必须完整位于一侧", tableInStable != tableInTail)
    }

    // ---------- P0-4：状态机派生 ----------

    @Test
    fun hiddenThinkingKeepsProgressAndDropsStaleRetryAfterTextArrives() {
        fun status(generating: Boolean, content: String, thinking: Boolean, retry: String? = null): String? =
            GenerationUiStateRules.statusWithoutThinking(
                GenerationUiStateRules.derive(generating, content, thinking, retry), retry, content.isBlank())
        assertEquals("正在连接…", status(true, "", false))
        assertEquals("正在生成回复…", status(true, "", true))
        val retry = "网络波动，正在重连…"
        assertEquals(retry, status(true, "", false, retry))
        assertEquals(null, status(true, "合成正文", true, retry))
        assertEquals(null, status(false, "合成正文", true, retry))
        assertEquals("未返回回复正文", status(false, "", true))
    }

    @Test
    fun hiddenThinkingDoesNotHideDirectionDecisionsOrConnectionFailures() {
        for (notice in listOf("等待选择回复方向", "回复方向生成失败，等待处理", "连接异常，HTTP 500")) {
            val state = GenerationUiStateRules.derive(true, "", false, notice)
            assertEquals(notice, GenerationUiStateRules.statusWithoutThinking(state, notice, true))
        }
        val persistedFailure = GenerationUiStateRules.derive(false, "请求失败", true, null, contentIsError = true)
        assertEquals("模型请求异常", GenerationUiStateRules.statusWithoutThinking(persistedFailure, null, false))
        assertEquals("模型请求异常", GenerationUiStateRules.statusWithoutThinking(persistedFailure, "正在尝试重新连接…", false))
        assertEquals("生成已停止", GenerationUiStateRules.statusWithoutThinking(GenerationUiState.Cancelled, null, true))
    }

    @Test
    fun testGenerationState_idle() {
        assertEquals(
            GenerationUiState.Idle,
            GenerationUiStateRules.derive(false, "已有内容", true, null)
        )
    }

    @Test
    fun testGenerationState_connecting() {
        assertEquals(
            GenerationUiState.Connecting,
            GenerationUiStateRules.derive(true, "", false, null)
        )
    }

    @Test
    fun testGenerationState_reconnecting() {
        assertEquals(
            GenerationUiState.Reconnecting,
            GenerationUiStateRules.derive(true, "", false, "网络波动，正在重连…")
        )
    }

    @Test
    fun testGenerationState_thinking() {
        assertEquals(
            GenerationUiState.Thinking,
            GenerationUiStateRules.derive(true, "", true, null)
        )
    }

    @Test
    fun testGenerationState_streaming() {
        assertEquals(
            GenerationUiState.Streaming,
            GenerationUiStateRules.derive(true, "部分正文", true, null)
        )
        assertEquals(
            GenerationUiState.Streaming,
            GenerationUiStateRules.derive(true, "正文", false, null)
        )
    }

    @Test
    fun testGenerationState_failed_viaReconnectErrorText() {
        // 行为等价：reconnect 文案命中错误词 → Failed（原 capsule contains 规则）
        assertEquals(
            GenerationUiState.Failed,
            GenerationUiStateRules.derive(true, "", false, "连接异常，HTTP 500")
        )
        assertEquals(
            GenerationUiState.Failed,
            GenerationUiStateRules.derive(true, "", false, "请求 Error: timeout")
        )
    }

    @Test
    fun testGenerationState_failed_viaContentError() {
        // v2.5.7 错误报告全包裹：内容命中 isErrorMessage 语义 → Failed 优先
        assertEquals(
            GenerationUiState.Failed,
            GenerationUiStateRules.derive(true, "请求失败：所有 API Key 均不可用", false, null, contentIsError = true)
        )
    }

    @Test
    fun testErrorText_rulesMatchOriginalCapsuleKeywords() {
        assertTrue(GenerationUiStateRules.isErrorText("包含异常"))
        assertTrue(GenerationUiStateRules.isErrorText("包含报错"))
        assertTrue(GenerationUiStateRules.isErrorText("任务失败"))
        assertTrue(GenerationUiStateRules.isErrorText("出现错误"))
        assertTrue(GenerationUiStateRules.isErrorText("an Error occurred"))
        assertTrue(GenerationUiStateRules.isErrorText("HTTP 403"))
        assertFalse(GenerationUiStateRules.isErrorText("正常重连中，请稍候"))
    }
}
