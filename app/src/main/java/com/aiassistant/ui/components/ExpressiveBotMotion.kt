package com.aiassistant.ui.components

import com.aiassistant.utils.EchoModelMarkStyle
import kotlin.math.abs

/** 100 × 100 画板上的两眼角色骨架；数值也供网页动作对照与原生绘制验收使用。 */
internal data class ExpressiveBotEyePose(
    val x: Float,
    val y: Float,
    val width: Float = 9.5f,
    val height: Float = 24f
)

internal enum class ExpressiveBotEffectKind { Signal, SignalDot, Thought, Speech, Spark, Z, Pause, Error }

/** 100×100 画板上的独立符号层；size 为全宽（圆为直径），不继承身体旋转和压缩。 */
internal data class ExpressiveBotEffectPose(
    val id: Int,
    val kind: ExpressiveBotEffectKind,
    val x: Float,
    val y: Float,
    val size: Float,
    val alpha: Float,
    val progress: Float = 0f
)

internal data class ExpressiveBotPose(
    val bodyWidth: Float,
    val bodyHeight: Float,
    val cornerRadius: Float,
    val bodyX: Float = 0f,
    val bodyY: Float = 0f,
    val rotation: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val faceX: Float = 0f,
    val faceY: Float = 0f,
    val left: ExpressiveBotEyePose,
    val right: ExpressiveBotEyePose,
    val effects: List<ExpressiveBotEffectPose> = emptyList()
)

internal fun expressiveBotRestPose(style: String): ExpressiveBotPose = when (style) {
    EchoModelMarkStyle.BOT_TILE -> ExpressiveBotPose(74f, 72f, 17f,
        left = ExpressiveBotEyePose(40.7f, 50.6f, width = 8.8f, height = 24.3f),
        right = ExpressiveBotEyePose(59.3f, 50.6f, width = 8.8f, height = 24.3f))
    EchoModelMarkStyle.BOT_WIDE -> ExpressiveBotPose(86f, 64f, 17f,
        left = ExpressiveBotEyePose(38f, 53f, width = 8.5f, height = 22f),
        right = ExpressiveBotEyePose(62f, 53f, width = 8.5f, height = 22f))
    else -> ExpressiveBotPose(72f, 72f, 36f,
        left = ExpressiveBotEyePose(41f, 50.7f, width = 8.5f, height = 23.6f),
        right = ExpressiveBotEyePose(59f, 50.7f, width = 8.5f, height = 23.6f))
}

internal fun expressiveBotDurationMillis(style: String, state: EchoModelMarkState): Float {
    val index = when (style) {
        EchoModelMarkStyle.BOT_TILE -> 1
        EchoModelMarkStyle.BOT_WIDE -> 2
        else -> 0
    }
    return when (state) {
        EchoModelMarkState.Connecting -> floatArrayOf(3800f, 4600f, 3400f)[index]
        EchoModelMarkState.Thinking -> floatArrayOf(6200f, 7000f, 5800f)[index]
        EchoModelMarkState.Replying -> floatArrayOf(2900f, 3200f, 2600f)[index]
        EchoModelMarkState.Completed -> floatArrayOf(1500f, 1600f, 1400f)[index]
        EchoModelMarkState.Neutral, EchoModelMarkState.Paused, EchoModelMarkState.Failed -> 0f
    }
}

/** 非等拍的“看见—跟随—停顿”轨迹；只有显式待机身份会缓缓下看并播放 Z。 */
internal fun sampleExpressiveBotPose(
    style: String,
    state: EchoModelMarkState,
    elapsedMillis: Float,
    reducedMotion: Boolean = false,
    idleEffects: Boolean = false
): ExpressiveBotPose {
    val rest = expressiveBotRestPose(style)
    val finiteElapsed = if (elapsedMillis.isFinite()) elapsedMillis.coerceAtLeast(0f) else 0f
    fun idlePose(elapsed: Float): ExpressiveBotPose = rest.copy(
        // Keep both natural eyes together; only the latest successful identity may become drowsy.
        faceY = if (!idleEffects) 0f else if (reducedMotion) 4.2f else 4.2f * smooth(elapsed / 1200f),
        effects = sampleExpressiveBotEffects(EchoModelMarkState.Neutral, elapsed, reducedMotion, idleEffects, style))
    if (state == EchoModelMarkState.Paused || state == EchoModelMarkState.Failed) {
        // A user pause and a failed request remain visibly distinct without running a historical ticker.
        val terminalProgress = if (reducedMotion || finiteElapsed <= 0f || finiteElapsed >= TERMINAL_MOTION_MILLIS) 1f
            else smooth(finiteElapsed / TERMINAL_MOTION_MILLIS)
        return rest.copy(effects = sampleExpressiveBotEffects(state, finiteElapsed, reducedMotion, idleEffects, style))
    }
    if (state == EchoModelMarkState.Neutral || (reducedMotion && state == EchoModelMarkState.Completed)) {
        return idlePose(finiteElapsed)
    }
    val duration = expressiveBotDurationMillis(style, state)
    if (state == EchoModelMarkState.Completed && finiteElapsed >= duration) return idlePose(finiteElapsed - duration)
    val position = if (reducedMotion) when (state) {
        EchoModelMarkState.Connecting -> 0.23f
        EchoModelMarkState.Thinking -> 0.24f
        EchoModelMarkState.Replying -> 0.43f
        EchoModelMarkState.Completed -> 0.39f
        else -> 0f
    } else if (state == EchoModelMarkState.Completed) finiteElapsed / duration
        else finiteElapsed % duration / duration
    val tile = style == EchoModelMarkStyle.BOT_TILE
    val wide = style == EchoModelMarkStyle.BOT_WIDE
    val direction = if (tile) -1f else 1f
    val travel = if (wide) 0.78f else 1f
    fun track(vararg points: Pair<Float, Float>): Float = botTrack(position, points)
    var pose = when (state) {
        EchoModelMarkState.Connecting -> {
            // The eyes notice a direction first; the body follows later and settles before looking back.
            val look = track(0f to 0f, .09f to -1f, .27f to -1f, .38f to 0f,
                .49f to 0f, .58f to 1f, .76f to 1f, .90f to 0f, 1f to 0f) * direction
            val follow = track(0f to 0f, .16f to -1f, .28f to -1f, .44f to 0f,
                .65f to 1f, .77f to 1f, .96f to 0f, 1f to 0f) * direction
            rest.copy(bodyX = 1.6f * follow * travel, bodyY = -.35f * abs(follow),
                rotation = (if (wide) 2.7f else 3.8f) * follow,
                faceX = 4.6f * look, faceY = -.6f * abs(look))
        }
        EchoModelMarkState.Thinking -> {
            // Sustained upward side-glance, one slow change of mind, then a quiet pause.
            val look = track(0f to .15f, .10f to 1f, .34f to 1f, .45f to .1f,
                .53f to .1f, .64f to -.8f, .83f to -.8f, .96f to .15f, 1f to .15f) * direction
            val tilt = track(0f to .1f, .19f to 1f, .35f to 1f, .49f to .15f,
                .70f to -.7f, .84f to -.7f, .98f to .1f, 1f to .1f) * direction
            val lift = track(0f to .35f, .12f to 1f, .36f to 1f, .48f to .65f,
                .62f to .9f, .85f to .9f, 1f to .35f)
            rest.copy(bodyX = .7f * tilt * travel, bodyY = .3f * abs(tilt),
                rotation = (if (wide) 3.4f else 4.8f) * tilt,
                faceX = 2.7f * look, faceY = -3.2f * lift)
        }
        EchoModelMarkState.Replying -> {
            // Two small phrase beats and one softer afterthought; most of the cycle is at rest.
            val beat = track(0f to 0f, .10f to .7f, .18f to -.25f, .28f to 0f,
                .38f to 0f, .47f to 1f, .55f to -.32f, .65f to 0f,
                .79f to 0f, .84f to .32f, .91f to 0f, 1f to 0f)
            val lean = track(0f to 0f, .12f to -.55f, .26f to 0f, .43f to 0f,
                .51f to .7f, .65f to 0f, .82f to -.2f, .92f to 0f, 1f to 0f) * direction
            rest.copy(bodyY = (if (wide) .9f else 1.5f) * beat,
                rotation = lean * (if (tile) 2.4f else if (wide) 1.5f else 2f),
                scaleX = 1f + .012f * beat, scaleY = 1f - .016f * beat,
                faceX = .75f * lean, faceY = .7f * beat)
        }
        EchoModelMarkState.Completed -> {
            // A single preparation, acknowledgement nod, and settle. The eyes keep their natural shape.
            val nod = track(0f to 0f, .16f to -.45f, .35f to 1f, .47f to 1f,
                .67f to -.16f, .85f to 0f, 1f to 0f)
            val settle = track(0f to 0f, .16f to -.3f, .36f to 1f,
                .49f to .8f, .68f to -.18f, .86f to 0f, 1f to 0f)
            rest.copy(bodyY = (if (wide) 1.2f else 1.9f) * nod,
                scaleX = 1f + .025f * settle, scaleY = 1f - .032f * settle,
                faceY = 1.8f * nod)
        }
        else -> rest
    }
    if (!reducedMotion && state.isActive) {
        // Short, synchronous eyelid closure; never a long asymmetric or curved expression.
        val blink = botBlink(finiteElapsed % 6100f, 3060f, 160f)
        if (blink > 0f) pose = pose.copy(
            left = pose.left.copy(height = mix(rest.left.height, 2.2f, blink)),
            right = pose.right.copy(height = mix(rest.right.height, 2.2f, blink)))
    }
    return pose.copy(effects = sampleExpressiveBotEffects(state, finiteElapsed, reducedMotion, idleEffects, style))
}

/** Separate, repeatable symbol motion. A settled/historical identity opts out unless explicitly marked latest. */
internal fun sampleExpressiveBotEffects(
    state: EchoModelMarkState,
    elapsedMillis: Float,
    reducedMotion: Boolean = false,
    idleEffects: Boolean = false,
    style: String = EchoModelMarkStyle.BOT_ORB
): List<ExpressiveBotEffectPose> {
    if (state == EchoModelMarkState.Neutral && !idleEffects) return emptyList()
    val time = if (elapsedMillis.isFinite()) elapsedMillis.coerceAtLeast(0f) else 0f
    fun effect(id: Int, kind: ExpressiveBotEffectKind, x: Float, y: Float, size: Float, alpha: Float,
               progress: Float = 0f) =
        ExpressiveBotEffectPose(id, kind, x, y, size, alpha.coerceIn(0f, 1f), progress)
    val wide = style == EchoModelMarkStyle.BOT_WIDE
    val terminalAmount = if (reducedMotion || time <= 0f || time >= TERMINAL_MOTION_MILLIS) 1f
        else smooth(time / TERMINAL_MOTION_MILLIS)
    if (state == EchoModelMarkState.Paused) {
        val x = if (wide) 85f + 5f * terminalAmount else 72f + 12f * terminalAmount
        val y = 9.5f - 1f * terminalAmount
        return listOf(effect(50, ExpressiveBotEffectKind.Pause,
            x, y, 5.5f + 10.5f * terminalAmount, .45f + .55f * terminalAmount, terminalAmount))
    }
    if (state == EchoModelMarkState.Failed) {
        val x = if (wide) 86f + 5f * terminalAmount else 73f + 11f * terminalAmount
        val y = 10f - 1f * terminalAmount
        return listOf(effect(60, ExpressiveBotEffectKind.Error,
            x, y, 5.5f + 10.5f * terminalAmount, .45f + .55f * terminalAmount, terminalAmount))
    }
    val signalX = if (wide) 85.8f else 83f
    val signalSizes = if (wide) listOf(10f, 18f, 24f) else listOf(10f, 19f, 28f)
    val thoughtX = if (wide) 87.5f else 87f
    val speechX = if (wide) 86.5f else 87f
    val speechSize = if (wide) 19f else 20f
    val sparkX = if (wide) 88f else 85f
    if (reducedMotion) return when (state) {
        EchoModelMarkState.Connecting -> listOf(
            effect(3, ExpressiveBotEffectKind.SignalDot, signalX, 16.3f, 3.2f, .88f)) + List(3) { index ->
            effect(index, ExpressiveBotEffectKind.Signal, signalX, 16.3f, signalSizes[index], .88f)
        }
        EchoModelMarkState.Thinking -> listOf(effect(10, ExpressiveBotEffectKind.Thought, thoughtX + 2f, 7.4f, 16f, .9f, .4f))
        EchoModelMarkState.Replying -> listOf(effect(20, ExpressiveBotEffectKind.Speech, speechX, 8.9f, speechSize, .9f, .4f))
        EchoModelMarkState.Neutral -> listOf(effect(40, ExpressiveBotEffectKind.Z, 87f, 9.3f, 14f, .9f))
        EchoModelMarkState.Completed, EchoModelMarkState.Paused, EchoModelMarkState.Failed -> emptyList()
    }
    // A long readable hold separates the entrance and exit; the rest of each cycle stays quiet.
    fun opacity(position: Float): Float = smooth(position / .14f) *
        (1f - smooth((position - .58f) / .2f))
    return when (state) {
        EchoModelMarkState.Connecting -> {
            val position = time % 6000f / 6000f
            listOf(effect(3, ExpressiveBotEffectKind.SignalDot, signalX, 16.3f, 3.2f, .78f)) + List(3) { index ->
                val start = .05f + index * .13f
                val alpha = smooth((position - start) / .12f) *
                    (1f - smooth((position - .70f) / .16f))
                effect(index, ExpressiveBotEffectKind.Signal, signalX, 16.3f, signalSizes[index], alpha * .9f)
            }
        }
        EchoModelMarkState.Thinking -> {
            val position = time % 6400f / 6400f
            val rise = smooth((position - .08f) / .5f)
            // The tail appears first, then the thought grows and travels visibly into the upper-right space.
            listOf(effect(10, ExpressiveBotEffectKind.Thought, thoughtX + 2f * rise, 9.5f - 2.1f * rise,
                13f + 3f * rise, opacity(position) * .9f, position))
        }
        EchoModelMarkState.Replying -> {
            val position = time % 5600f / 5600f
            listOf(effect(20, ExpressiveBotEffectKind.Speech, speechX, 8.9f, speechSize,
                opacity(position) * .9f, position))
        }
        EchoModelMarkState.Completed -> {
            val position = (time / 1300f).coerceIn(0f, 1f)
            listOf(effect(30, ExpressiveBotEffectKind.Spark, sparkX, 9.4f,
                12f + 2f * smooth(position / .25f), opacity(position) * .9f))
        }
        EchoModelMarkState.Neutral -> {
            val position = time % 7000f / 7000f
            val rise = smooth((position - .08f) / .5f)
            // A single Z drifts diagonally and grows; a sub-unit bob vanishes at the actual chat size.
            listOf(effect(40, ExpressiveBotEffectKind.Z, 82f + 5f * rise, 11.8f - 2.5f * rise,
                10f + 4f * rise,
                opacity(position) * .9f * smooth(time / ExpressiveBotMotionController.TRANSITION_MILLIS)))
        }
        EchoModelMarkState.Paused, EchoModelMarkState.Failed -> emptyList()
    }
}

private const val TERMINAL_MOTION_MILLIS = 520f

private fun botBlink(time: Float, start: Float, duration: Float): Float {
    val position = (time - start) / duration
    return when {
        position < 0f || position > 1f -> 0f
        position < .42f -> smooth(position / .42f)
        position < .6f -> 1f
        else -> 1f - smooth((position - .6f) / .4f)
    }
}

private fun botTrack(position: Float, points: Array<out Pair<Float, Float>>): Float {
    for (index in 1 until points.size) {
        val before = points[index - 1]
        val after = points[index]
        if (position <= after.first) return mix(before.second, after.second,
            smooth((position - before.first) / (after.first - before.first)))
    }
    return points.last().second
}

private fun smooth(value: Float): Float = value.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
private fun mix(first: Float, second: Float, amount: Float): Float = first + (second - first) * amount

internal fun blendExpressiveBotPose(first: ExpressiveBotPose, second: ExpressiveBotPose, amount: Float): ExpressiveBotPose {
    val weight = amount.coerceIn(0f, 1f)
    if (weight == 0f) return first
    if (weight == 1f) return second
    fun eye(a: ExpressiveBotEyePose, b: ExpressiveBotEyePose) = ExpressiveBotEyePose(
        mix(a.x, b.x, weight), mix(a.y, b.y, weight), mix(a.width, b.width, weight),
        mix(a.height, b.height, weight))
    return ExpressiveBotPose(
        mix(first.bodyWidth, second.bodyWidth, weight), mix(first.bodyHeight, second.bodyHeight, weight),
        mix(first.cornerRadius, second.cornerRadius, weight), mix(first.bodyX, second.bodyX, weight),
        mix(first.bodyY, second.bodyY, weight), mix(first.rotation, second.rotation, weight),
        mix(first.scaleX, second.scaleX, weight), mix(first.scaleY, second.scaleY, weight),
        mix(first.faceX, second.faceX, weight), mix(first.faceY, second.faceY, weight),
        eye(first.left, second.left), eye(first.right, second.right),
        (first.effects.map { it.id } + second.effects.map { it.id }).distinct().map { id ->
            val before = first.effects.firstOrNull { it.id == id }
            val after = second.effects.firstOrNull { it.id == id }
            val a = before ?: requireNotNull(after).copy(alpha = 0f)
            val b = after ?: requireNotNull(before).copy(alpha = 0f)
            ExpressiveBotEffectPose(id, b.kind, mix(a.x, b.x, weight), mix(a.y, b.y, weight),
                mix(a.size, b.size, weight), mix(a.alpha, b.alpha, weight), mix(a.progress, b.progress, weight))
        })
}

/**
 * Keeps one visible-time clock and blends from the exact interrupted pose.
 * No wall-clock catch-up or historical ticker; the latest successful identity explicitly opts into idle.
 */
internal class ExpressiveBotMotionController(
    initialStyle: String,
    private val completionMotion: ModelMarkCompletionMotion = ModelMarkCompletionMotion()
) {
    private var style = initialStyle
    private var state = EchoModelMarkState.Neutral
    private var reducedMotion = false
    private var enabled = true
    private var idleEffects = false
    private var elapsedMillis = 0f
    private var transitionMillis = TRANSITION_MILLIS
    private var terminalTransitionActive = false
    private var from = expressiveBotRestPose(style)
    private var completionPlaying = false
    private var initialized = false

    var pose: ExpressiveBotPose = from
        private set

    val needsFrames: Boolean get() = enabled && !reducedMotion &&
        (state.isActive || completionPlaying || transitionMillis < TRANSITION_MILLIS ||
            terminalTransitionActive && elapsedMillis < TERMINAL_MOTION_MILLIS ||
            idleEffects && (state == EchoModelMarkState.Neutral || state == EchoModelMarkState.Completed))

    fun update(
        state: EchoModelMarkState,
        completionEvent: Long = 0L,
        reducedMotion: Boolean = false,
        style: String = this.style,
        enabled: Boolean = true,
        idleEffects: Boolean = false
    ): Boolean {
        val playCompletion = completionMotion.update(state, completionEvent)
        val changed = !initialized || this.state != state || this.style != style ||
            this.reducedMotion != reducedMotion || this.enabled != enabled || this.idleEffects != idleEffects || playCompletion
        if (!changed) return false
        val previousPose = pose
        val styleChanged = this.style != style
        val wasEnabled = this.enabled
        val stateChanged = this.state != state
        terminalTransitionActive = initialized && stateChanged &&
            (state == EchoModelMarkState.Paused || state == EchoModelMarkState.Failed) &&
            this.state.isActive
        this.style = style
        this.state = state
        this.enabled = enabled
        this.reducedMotion = reducedMotion
        this.idleEffects = idleEffects
        if (stateChanged || playCompletion) elapsedMillis = 0f
        completionPlaying = state == EchoModelMarkState.Completed && !reducedMotion && enabled &&
            !styleChanged && wasEnabled && (playCompletion || completionPlaying)
        if (!enabled || reducedMotion) {
            completionPlaying = false
            transitionMillis = TRANSITION_MILLIS
            pose = if (!enabled) expressiveBotRestPose(style)
                else sampleExpressiveBotPose(style, state, 0f, reducedMotion = true, idleEffects = idleEffects)
        } else {
            from = previousPose
            transitionMillis = if (terminalTransitionActive) 0f
                else if (!initialized && !state.isActive && !completionPlaying && !idleEffects) TRANSITION_MILLIS else 0f
            val resting = sampleExpressiveBotPose(style,
                if (state == EchoModelMarkState.Paused || state == EchoModelMarkState.Failed) state else EchoModelMarkState.Neutral,
                elapsedMillis, idleEffects = idleEffects)
            if (!state.isActive && !completionPlaying && previousPose == resting) {
                transitionMillis = TRANSITION_MILLIS
            }
            if (transitionMillis >= TRANSITION_MILLIS) pose = resting
        }
        initialized = true
        return playCompletion
    }

    fun advance(deltaMillis: Float) {
        if (!needsFrames || !deltaMillis.isFinite()) return
        val step = deltaMillis.coerceIn(0f, MAX_FRAME_MILLIS)
        elapsedMillis += step
        transitionMillis = (transitionMillis + step).coerceAtMost(TRANSITION_MILLIS)
        if (completionPlaying && elapsedMillis >= expressiveBotDurationMillis(style, EchoModelMarkState.Completed)) {
            completionPlaying = false
        }
        val target = if (state == EchoModelMarkState.Completed && !completionPlaying)
            sampleExpressiveBotPose(style, EchoModelMarkState.Neutral,
                (elapsedMillis - expressiveBotDurationMillis(style, EchoModelMarkState.Completed)).coerceAtLeast(0f),
                idleEffects = idleEffects)
        else sampleExpressiveBotPose(style, state, elapsedMillis, idleEffects = idleEffects)
        pose = blendExpressiveBotPose(from, target, smooth(transitionMillis / TRANSITION_MILLIS))
    }

    companion object {
        const val TRANSITION_MILLIS = 260f
        // A suspended app resumes at its last visible pose rather than jumping to elapsed wall time.
        const val MAX_FRAME_MILLIS = 50f
    }
}
