package com.aiassistant.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.aiassistant.ui.theme.EchoTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 平滑拖拽与重排序状态控制器 (SmoothReorderState)
 *
 * 拖拽期间只做视觉位移，不修改列表数据、不触发数据库排序写入；
 * 松手时才按 [computeReorderTargetIndex] 的结果调用一次 onMove。这样列表在
 * 手势进行中不会重排，六点手柄的布局原点保持固定，不会再出现“拖动过程中
 * 选中的条目突然变成别的”或位移抖动。
 *
 * 所有身份判断均使用稳定 key（API 配置 id、Key 条目 id、模型名、排队消息 id），
 * 即使列表内容在下一次重组中变化，被拖拽的条目也不会改变。
 */
@Stable
class SmoothReorderState(
    private val coroutineScope: CoroutineScope
) {
    // 当前正在被拖动的条目 key；为空表示没有拖拽事务
    var draggingKey by mutableStateOf<Any?>(null)
        private set

    // 拖拽开始时条目在列表中的下标（仅用于本次手势的移动距离计算）
    var draggingStartIndex by mutableIntStateOf(-1)
        private set

    // 根据当前位移计算出的目标下标
    var targetIndex by mutableIntStateOf(-1)
        private set

    // 被拖拽条目的实时垂直位移
    var dragOffsetY by mutableFloatStateOf(0f)
        private set

    // 是否处于松手归位动画中
    var isReleasing by mutableStateOf(false)
        private set

    val isDragging: Boolean get() = draggingKey != null

    // 松手后的视觉补偿位移。必须同步写入，避免列表重排与归位动画之间出现一帧跳变。
    private val releaseOffset = mutableFloatStateOf(0f)
    private val liftAnim = Animatable(0f)
    private var releaseJob: Job? = null
    private var liftJob: Job? = null
    // 拖拽代次：新拖拽开始时递增，旧归位协程据此放弃写入，避免清掉新拖拽状态
    private var dragGeneration = 0

    // 非拖拽条目为让位而产生的视觉位移（按 key 记录）
    private val itemOffsets: SnapshotStateMap<Any, Float> = mutableStateMapOf()
    private val itemOffsetTargets = mutableMapOf<Any, Float>()
    private val itemOffsetJobs = mutableMapOf<Any, Job>()

    // 各条目实测高度（按 key）
    private val itemHeightByKey = mutableMapOf<Any, Float>()

    // 列表项间距（像素），由调用方通过 reorderItem 提供；跨槽阈值与松手补偿都会计入
    private var itemSpacingPx = 0f

    fun setItemHeight(key: Any, height: Float) {
        if (height > 0f) itemHeightByKey[key] = height
    }

    fun setItemSpacing(spacingPx: Float) {
        if (spacingPx >= 0f) itemSpacingPx = spacingPx
    }

    fun getItemHeight(key: Any): Float = itemHeightByKey[key] ?: DEFAULT_ITEM_HEIGHT

    /** 抬升量 0f~1f，用于平滑驱动缩放与阴影 */
    fun getItemLift(): Float = liftAnim.value

    /** 仅当前正在拖拽或落座归位中的条目获得抬升量，避免联动其他卡片。 */
    fun getItemLift(key: Any): Float = if (draggingKey == key) liftAnim.value else 0f

    /** 指定 key 的视觉垂直位移；被拖拽条目跟随手指，其余条目为让位偏移 */
    fun getItemOffsetY(key: Any): Float {
        if (draggingKey == key) {
            return if (isReleasing) releaseOffset.floatValue else dragOffsetY
        }
        return itemOffsets[key] ?: 0f
    }

    /** 指定 key 是否为当前被拖拽的条目 */
    fun isItemActive(key: Any): Boolean = draggingKey == key

    /**
     * 手势开始。keys 为最新完整列表 key，key 为按住手柄所属条目。
     */
    fun onDragStart(keys: List<Any>, key: Any) {
        val index = keys.indexOf(key)
        if (index < 0) return
        // 中止上一轮尚未结束的归位/抬升动画，避免旧协程清掉本次拖拽状态
        dragGeneration++
        releaseJob?.cancel()
        liftJob?.cancel()
        resetItemOffsets()
        draggingKey = key
        draggingStartIndex = index
        targetIndex = index
        dragOffsetY = 0f
        isReleasing = false
        releaseOffset.floatValue = 0f
        liftJob = coroutineScope.launch {
            liftAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = LIFT_DURATION_MS, easing = FastOutSlowInEasing)
            )
        }
    }

    /**
     * 手势拖拽中。只累计位移并计算目标位，不修改列表。
     */
    fun onDragDelta(deltaY: Float, keys: List<Any>) {
        val key = draggingKey ?: return
        if (isReleasing) return
        val start = draggingStartIndex
        if (start !in keys.indices) return
        // 列表若在下一次重组前被外部替换，重新按 key 锚定起点，保证拖拽项不换人。
        val currentIndex = keys.indexOf(key)
        if (currentIndex < 0) return
        if (currentIndex != start) {
            draggingStartIndex = currentIndex
            targetIndex = currentIndex
            dragOffsetY = 0f
            resetItemOffsets()
            return
        }

        dragOffsetY += deltaY
        val heights = keys.map { getItemHeight(it) }
        val newTarget = computeReorderTargetIndex(start, dragOffsetY, heights, itemSpacingPx)
        targetIndex = newTarget
        keys.forEachIndexed { index, itemKey ->
            if (itemKey == key) return@forEachIndexed
            animateItemOffset(itemKey, reorderSlotOffset(index, start, newTarget, heights, itemSpacingPx))
        }
    }

    /**
     * 手势松开或取消。列表重排只在此时发生一次。
     */
    fun onDragFinish(keys: List<Any>, onMove: (fromIndex: Int, toIndex: Int) -> Unit) {
        val key = draggingKey
        if (key == null) {
            if (!isReleasing) {
                liftJob?.cancel()
                liftJob = coroutineScope.launch {
                    liftAnim.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = LIFT_DURATION_MS, easing = FastOutSlowInEasing)
                    )
                }
            }
            return
        }
        if (isReleasing) return

        val currentIndex = keys.indexOf(key)
        if (currentIndex < 0) {
            // 条目已不在列表中：直接清理状态，不执行移动。
            finishWithoutMove()
            return
        }
        val heights = keys.map { getItemHeight(it) }
        val target = targetIndex.coerceIn(0, (keys.size - 1).coerceAtLeast(0))
        val travel = slotTravelDistance(currentIndex, target, heights, itemSpacingPx)
        val movedCrossedOffsets = if (target != currentIndex) {
            val draggedHeight = heights[currentIndex]
            buildMap {
                if (target > currentIndex) {
                    for (idx in (currentIndex + 1)..target) {
                        put(keys[idx], -(draggedHeight + itemSpacingPx))
                    }
                } else {
                    for (idx in target until currentIndex) {
                        put(keys[idx], draggedHeight + itemSpacingPx)
                    }
                }
            }
        } else {
            emptyMap()
        }
        if (target != currentIndex) {
            onMove(currentIndex, target)
        }
        // 在列表重排生效的同时抵消被跨过条目的真实布局位移，避免让位项出现双倍跳动回弹。
        movedCrossedOffsets.forEach { (itemKey, layoutDelta) ->
            itemOffsetJobs.remove(itemKey)?.cancel()
            itemOffsetTargets.remove(itemKey)
            val compensated = (itemOffsets[itemKey] ?: 0f) - layoutDelta
            if (kotlin.math.abs(compensated) < 0.5f) {
                itemOffsets.remove(itemKey)
            } else {
                itemOffsets[itemKey] = compensated
            }
        }
        isReleasing = true
        // 同步写入补偿值：列表重排后的第一帧就落在手指所在位置，随后平滑归位。
        releaseOffset.floatValue = dragOffsetY - travel
        liftJob?.cancel()
        releaseJob?.cancel()
        val generation = dragGeneration
        releaseJob = coroutineScope.launch {
            keys.forEach { itemKey ->
                if (itemKey != key && ((itemOffsets[itemKey] ?: 0f) != 0f || itemOffsetJobs[itemKey]?.isActive == true)) {
                    animateItemOffset(itemKey, 0f)
                }
            }
            val settleJob = launch {
                animate(
                    initialValue = releaseOffset.floatValue,
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = SETTLE_DURATION_MS, easing = FastOutSlowInEasing)
                ) { value, _ ->
                    releaseOffset.floatValue = value
                }
            }
            val lowerJob = launch {
                liftAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = LIFT_DURATION_MS, easing = FastOutSlowInEasing)
                )
            }
            settleJob.join()
            lowerJob.join()
            if (generation != dragGeneration) return@launch
            draggingKey = null
            draggingStartIndex = -1
            targetIndex = -1
            dragOffsetY = 0f
            releaseOffset.floatValue = 0f
            isReleasing = false
            resetItemOffsets()
        }
    }

    /**
     * 箭头按钮触发的相邻项平滑换位动画。调用方已立即交换列表数据，
     * 这里只播放一次“从旧位置滑入新位置”的短动画。
     */
    fun onAnimateSwap(
        fromKey: Any,
        toKey: Any,
        fromIndex: Int,
        toIndex: Int
    ) {
        val direction = if (toIndex > fromIndex) 1f else -1f
        val fromTravel = getItemHeight(toKey) + itemSpacingPx
        val toTravel = getItemHeight(fromKey) + itemSpacingPx
        animateTransientOffset(fromKey, -direction * fromTravel)
        animateTransientOffset(toKey, direction * toTravel)
    }

    private fun animateItemOffset(key: Any, target: Float) {
        val current = itemOffsets[key] ?: 0f
        if (current == target && itemOffsetJobs[key]?.isActive != true) {
            itemOffsetTargets[key] = target
            return
        }
        if (itemOffsetTargets[key] == target && itemOffsetJobs[key]?.isActive == true) return
        itemOffsetTargets[key] = target
        itemOffsetJobs[key]?.cancel()
        itemOffsetJobs[key] = coroutineScope.launch {
            animate(
                initialValue = current,
                targetValue = target,
                animationSpec = tween(durationMillis = SWAP_DURATION_MS, easing = FastOutSlowInEasing)
            ) { value, _ ->
                itemOffsets[key] = value
            }
        }
    }

    private fun animateTransientOffset(key: Any, startOffset: Float) {
        itemOffsetJobs[key]?.cancel()
        itemOffsetTargets[key] = 0f
        itemOffsets[key] = startOffset
        itemOffsetJobs[key] = coroutineScope.launch {
            animate(
                initialValue = startOffset,
                targetValue = 0f,
                animationSpec = tween(durationMillis = SWAP_DURATION_MS, easing = FastOutSlowInEasing)
            ) { value, _ ->
                itemOffsets[key] = value
            }
        }
    }

    private fun resetItemOffsets() {
        itemOffsetJobs.values.forEach { it.cancel() }
        itemOffsetJobs.clear()
        itemOffsetTargets.clear()
        itemOffsets.clear()
    }

    private fun finishWithoutMove() {
        isReleasing = true
        releaseOffset.floatValue = dragOffsetY
        liftJob?.cancel()
        releaseJob?.cancel()
        val generation = dragGeneration
        releaseJob = coroutineScope.launch {
            val settleJob = launch {
                animate(
                    initialValue = dragOffsetY,
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = SETTLE_DURATION_MS, easing = FastOutSlowInEasing)
                ) { value, _ ->
                    releaseOffset.floatValue = value
                }
            }
            val lowerJob = launch {
                liftAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = LIFT_DURATION_MS, easing = FastOutSlowInEasing)
                )
            }
            settleJob.join()
            lowerJob.join()
            if (generation != dragGeneration) return@launch
            draggingKey = null
            draggingStartIndex = -1
            targetIndex = -1
            dragOffsetY = 0f
            releaseOffset.floatValue = 0f
            isReleasing = false
            resetItemOffsets()
        }
    }

    companion object {
        // 尚未完成测量的条目使用的估计高度
        const val DEFAULT_ITEM_HEIGHT = 180f
        // 抬升/落座过渡：快速切入、柔和收尾
        private const val LIFT_DURATION_MS = 160
        // 让位位移：略长于抬升，保证挤开感连贯
        private const val SWAP_DURATION_MS = 220
        // 松手归位：与换位节奏一致，避免过冲弹跳
        private const val SETTLE_DURATION_MS = 200
    }
}

/**
 * 根据累计位移计算目标下标。只有被拖拽条目中心越过相邻条目中心时才前进一步，
 * 因而卡片高度不同也能稳定判定；目标始终被限制在列表范围内。
 */
internal fun computeReorderTargetIndex(
    startIndex: Int,
    dragOffsetY: Float,
    heights: List<Float>,
    itemSpacing: Float = 0f
): Int {
    if (startIndex !in heights.indices) return startIndex
    val draggedHeight = heights[startIndex]
    var target = startIndex
    if (dragOffsetY > 0f) {
        var remaining = dragOffsetY
        var index = startIndex + 1
        while (index < heights.size) {
            val threshold = (draggedHeight + heights[index]) * 0.5f + itemSpacing
            if (remaining < threshold) break
            remaining -= threshold
            target = index
            index++
        }
    } else if (dragOffsetY < 0f) {
        var remaining = -dragOffsetY
        var index = startIndex - 1
        while (index >= 0) {
            val threshold = (draggedHeight + heights[index]) * 0.5f + itemSpacing
            if (remaining < threshold) break
            remaining -= threshold
            target = index
            index--
        }
    }
    return target
}

/**
 * 非拖拽条目为了让位而产生的视觉位移：
 * - 向下移动时，起点之后到目标之间的条目整体上移被拖拽条目的高度（含间距）；
 * - 向上移动时，目标之后到起点之前的条目整体下移被拖拽条目的高度（含间距）。
 */
internal fun reorderSlotOffset(
    index: Int,
    startIndex: Int,
    targetIndex: Int,
    heights: List<Float>,
    itemSpacing: Float = 0f
): Float {
    if (index == startIndex || index !in heights.indices || startIndex !in heights.indices) return 0f
    val draggedHeight = heights[startIndex]
    return when {
        targetIndex > startIndex && index in (startIndex + 1)..targetIndex ->
            -(draggedHeight + itemSpacing)
        targetIndex < startIndex && index in targetIndex until startIndex ->
            draggedHeight + itemSpacing
        else -> 0f
    }
}

/**
 * 条目从 startIndex 槽位移动到 targetIndex 槽位时，布局顶点的位移：
 * 向下为正（跨越所有途经兄弟项高度之和），向上为负。
 */
internal fun slotTravelDistance(
    startIndex: Int,
    targetIndex: Int,
    heights: List<Float>,
    itemSpacing: Float = 0f
): Float {
    if (startIndex == targetIndex || startIndex !in heights.indices || targetIndex !in heights.indices) {
        return 0f
    }
    val steps = kotlin.math.abs(targetIndex - startIndex)
    return if (targetIndex > startIndex) {
        heights.subList(startIndex + 1, targetIndex + 1).sum() + itemSpacing * steps
    } else {
        -(heights.subList(targetIndex, startIndex).sum() + itemSpacing * steps)
    }
}

@Composable
fun rememberSmoothReorderState(): SmoothReorderState {
    val coroutineScope = rememberCoroutineScope()
    return remember { SmoothReorderState(coroutineScope) }
}

/**
 * 拖拽项的外层容器修饰符。
 * 抬升量驱动缩放与阴影平滑过渡，条目身份始终用稳定 key 判断。
 */
fun Modifier.reorderItem(
    state: SmoothReorderState,
    key: Any,
    shape: Shape = RoundedCornerShape(10.dp),
    // 列表项之间的实际间距（像素）；用于跨槽阈值与松手补偿，避免间距漏算造成回弹
    spacingPx: Float = 0f,
    // 拖拽激活态阴影统一走 Elevation.raised() 配方（§3.3），默认取浅色配方值
    activeShadowElevation: Dp = EchoTokens.Elevation.raised(false).elevation,
    activeAmbientShadow: Color = EchoTokens.Elevation.raised(false).ambient,
    activeSpotShadow: Color = EchoTokens.Elevation.raised(false).spot
): Modifier = this
    .onSizeChanged { size ->
        state.setItemSpacing(spacingPx)
        state.setItemHeight(key, size.height.toFloat())
    }
    .zIndex(if (state.isItemActive(key)) 10f else 0f)
    .graphicsLayer {
        translationY = state.getItemOffsetY(key)
        this.shape = shape
        // 仅当前拖拽/归位条目获得抬升缩放与阴影，避免连带放大其他卡片
        val lift = state.getItemLift(key)
        val scale = 1f + 0.018f * lift
        scaleX = scale
        scaleY = scale
        shadowElevation = activeShadowElevation.toPx() * lift
        ambientShadowColor = activeAmbientShadow.copy(alpha = activeAmbientShadow.alpha * lift)
        spotShadowColor = activeSpotShadow.copy(alpha = activeSpotShadow.alpha * lift)
    }

/**
 * 六点拖动手柄修饰符。
 *
 * 手势协程只以 state 为键启动一次；所有回调通过 rememberUpdatedState 读取最新值，
 * 条目身份通过稳定 key 与最新 keys 反查，不依赖可能过期的组合下标。
 */
fun Modifier.reorderDragHandle(
    state: SmoothReorderState,
    key: () -> Any,
    keys: () -> List<Any>,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit
): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    val latestKey by rememberUpdatedState(key)
    val latestKeys by rememberUpdatedState(keys)
    val latestOnMove by rememberUpdatedState(onMove)
    this.pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val itemKey = latestKey()
            val currentKeys = latestKeys()
            if (itemKey !in currentKeys) return@awaitEachGesture
            down.consume()
            state.onDragStart(currentKeys, itemKey)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            var hasConsumedMove = false

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break

                val deltaY = (change.position.y - change.previousPosition.y) * (1f + 0.018f * state.getItemLift(itemKey))
                if (kotlin.math.abs(deltaY) > 0.5f || hasConsumedMove) {
                    change.consume()
                    hasConsumedMove = true
                    state.onDragDelta(deltaY, latestKeys())
                }
            }
            state.onDragFinish(latestKeys(), latestOnMove)
        }
    }
}
