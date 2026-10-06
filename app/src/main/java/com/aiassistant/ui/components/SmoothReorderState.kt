package com.aiassistant.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.aiassistant.ui.theme.EchoTokens

/**
 * 平滑拖拽与重排序状态控制器 (SmoothReorderState)
 * 支持：
 * 1. 按住六点手柄实时位移跟随手指 (translationY 连续渲染)
 * 2. 拖拽条目放大与悬浮阴影 (平滑 lift 过渡，避免瞬间跳变)
 * 3. 跨越相邻项时平滑无缝数据交换，手势不中断并伴随触觉反馈
 * 4. 相邻被挤开项平滑让位 (tween 缓动，无过冲弹跳)
 * 5. 松手/取消时平滑吸附归位
 * 6. 箭头按钮点击时双向对流平滑换位动画
 */
@Stable
class SmoothReorderState(
    private val coroutineScope: CoroutineScope
) {
    // 当前正在被拖动的项目索引
    var draggingIndex by mutableStateOf<Int?>(null)
        private set

    // 正在拖动的项目的实时垂直位移
    var dragOffsetY by mutableFloatStateOf(0f)
        private set

    // 正在释放回弹的 Animatable
    private val releaseAnim = Animatable(0f)
    var isReleasing by mutableStateOf(false)
        private set

    // 拖拽悬浮抬升量 0f~1f（驱动缩放与阴影平滑过渡，消除瞬间跳变）
    private val liftAnim = Animatable(0f)

    // 记录各条目由于交换产生的位置平滑补间动画（由 itemKey 索引）
    private val itemAnimMap = mutableMapOf<Any, Animatable<Float, AnimationVector1D>>()
    private val itemAnimJobs = mutableMapOf<Any, Job>()

    // 记录各条目的实测高度（像素）：优先按 key，回退按 index
    private val itemHeightByKey = mutableMapOf<Any, Float>()
    private val itemHeightMap = mutableMapOf<Int, Float>()

    fun setItemHeight(index: Int, height: Float) {
        if (height > 0f) {
            itemHeightMap[index] = height
        }
    }

    fun setItemHeight(key: Any, height: Float) {
        if (height > 0f) {
            itemHeightByKey[key] = height
        }
    }

    fun getItemHeight(index: Int): Float {
        return itemHeightMap[index] ?: 180f
    }

    fun getItemHeight(key: Any, fallbackIndex: Int): Float {
        return itemHeightByKey[key] ?: itemHeightMap[fallbackIndex] ?: 180f
    }

    /**
     * 拖拽抬升量 0f~1f，用于平滑驱动缩放与阴影
     */
    fun getItemLift(): Float = liftAnim.value

    /**
     * 获取指定 key 和 index 的平滑位移动画偏移
     */
    fun getItemOffsetY(key: Any, index: Int): Float {
        if (draggingIndex == index) {
            return if (isReleasing) releaseAnim.value else dragOffsetY
        }
        return itemAnimMap[key]?.value ?: 0f
    }

    /**
     * 是否处于拖拽或释放状态
     */
    fun isItemActive(index: Int): Boolean {
        return draggingIndex == index
    }

    /**
     * 手势开始
     */
    fun onDragStart(index: Int) {
        coroutineScope.launch {
            releaseAnim.snapTo(0f)
            isReleasing = false
            dragOffsetY = 0f
            draggingIndex = index
            liftAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = LIFT_DURATION_MS,
                    easing = FastOutSlowInEasing
                )
            )
        }
    }

    /**
     * 手势拖拽中
     */
    fun onDragDelta(
        deltaY: Float,
        listSize: Int,
        keys: List<Any>,
        haptic: HapticFeedback?,
        onMove: (fromIndex: Int, toIndex: Int) -> Unit
    ) {
        val currentIndex = draggingIndex ?: return
        dragOffsetY += deltaY

        val currentKey = keys.getOrNull(currentIndex)
        val itemHeight = if (currentKey != null) {
            getItemHeight(currentKey, currentIndex)
        } else {
            getItemHeight(currentIndex)
        }
        val threshold = itemHeight * 0.42f

        // 向下拖动超过半个身位，且未到底部
        if (dragOffsetY > threshold && currentIndex < listSize - 1) {
            val targetIndex = currentIndex + 1
            val targetKey = keys.getOrNull(targetIndex)

            // 交换判定成功：关闭震动反馈，杜绝交界处手机马达反复响动
            onMove(currentIndex, targetIndex)

            // 正在拖拽项补偿位移，视觉位置无缝保持在手指下方
            dragOffsetY -= itemHeight
            draggingIndex = targetIndex

            // 被交换项启动平滑滑动动画：视觉位置瞬间保持在原处，然后平滑滑向 0f
            if (targetKey != null) {
                animateItemTransition(targetKey, itemHeight)
            }
        }
        // 向上拖动超过半个身位，且未到顶部
        else if (dragOffsetY < -threshold && currentIndex > 0) {
            val targetIndex = currentIndex - 1
            val targetKey = keys.getOrNull(targetIndex)

            // 交换判定成功：关闭震动反馈，杜绝交界处手机马达反复响动
            onMove(currentIndex, targetIndex)

            // 正在拖拽项补偿位移，视觉位置无缝保持在手指下方
            dragOffsetY += itemHeight
            draggingIndex = targetIndex

            // 被交换项启动平滑滑动动画：视觉位置瞬间保持在原处，然后平滑滑向 0f
            if (targetKey != null) {
                animateItemTransition(targetKey, -itemHeight)
            }
        }
    }

    /**
     * 手势松开或取消
     */
    fun onDragFinish() {
        if (draggingIndex == null && !isReleasing) {
            coroutineScope.launch {
                liftAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = LIFT_DURATION_MS,
                        easing = FastOutSlowInEasing
                    )
                )
            }
            return
        }
        isReleasing = true
        coroutineScope.launch {
            releaseAnim.snapTo(dragOffsetY)
            releaseAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = SETTLE_DURATION_MS,
                    easing = FastOutSlowInEasing
                )
            )
            draggingIndex = null
            dragOffsetY = 0f
            isReleasing = false
            liftAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = LIFT_DURATION_MS,
                    easing = FastOutSlowInEasing
                )
            )
        }
    }

    /**
     * 点击按钮（如箭头）触发的相邻项平滑换位动画
     */
    fun onAnimateSwap(
        fromKey: Any,
        toKey: Any,
        fromIndex: Int,
        toIndex: Int
    ) {
        val itemHeight = getItemHeight(fromKey, fromIndex)
        val direction = if (toIndex > fromIndex) 1f else -1f
        // fromKey 移向新位置
        animateItemTransition(fromKey, -direction * itemHeight)
        // toKey 移向新位置
        animateItemTransition(toKey, direction * itemHeight)
    }

    private fun animateItemTransition(key: Any, startOffset: Float) {
        itemAnimJobs[key]?.cancel()
        itemAnimJobs[key] = coroutineScope.launch {
            val anim = itemAnimMap.getOrPut(key) { Animatable(0f) }
            anim.snapTo(startOffset)
            anim.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = SWAP_DURATION_MS,
                    easing = FastOutSlowInEasing
                )
            )
        }
    }

    companion object {
        // 抬升/落座过渡：快速切入、柔和收尾
        private const val LIFT_DURATION_MS = 160
        // 相邻项换位位移：略长于抬升，保证挤开感连贯
        private const val SWAP_DURATION_MS = 220
        // 松手归位：与换位节奏一致，避免过冲弹跳
        private const val SETTLE_DURATION_MS = 200
    }
}

@Composable
fun rememberSmoothReorderState(): SmoothReorderState {
    val coroutineScope = rememberCoroutineScope()
    return remember { SmoothReorderState(coroutineScope) }
}

/**
 * 拖拽项的外层容器修饰符
 * 抬升量驱动缩放与阴影平滑过渡，消除长按拖动交换时的瞬间跳变
 */
fun Modifier.reorderItem(
    state: SmoothReorderState,
    index: Int,
    key: Any,
    shape: Shape = RoundedCornerShape(10.dp),
    // 拖拽激活态阴影统一走 Elevation.raised() 配方（§3.3），默认取浅色配方值
    activeShadowElevation: Dp = EchoTokens.Elevation.raised(false).elevation,
    activeAmbientShadow: Color = EchoTokens.Elevation.raised(false).ambient,
    activeSpotShadow: Color = EchoTokens.Elevation.raised(false).spot
): Modifier = this
    .onSizeChanged { size ->
        state.setItemHeight(index, size.height.toFloat())
        state.setItemHeight(key, size.height.toFloat())
    }
    .zIndex(if (state.isItemActive(index)) 10f else 0f)
    .graphicsLayer {
        val offsetY = state.getItemOffsetY(key, index)
        translationY = offsetY
        this.shape = shape
        // lift 0→1 平滑驱动，拖起与放下都连续过渡
        val lift = state.getItemLift()
        val scale = 1f + 0.018f * lift
        scaleX = scale
        scaleY = scale
        shadowElevation = activeShadowElevation.toPx() * lift
        ambientShadowColor = activeAmbientShadow.copy(alpha = activeAmbientShadow.alpha * lift)
        spotShadowColor = activeSpotShadow.copy(alpha = activeSpotShadow.alpha * lift)
    }

/**
 * 六点拖动手柄修饰符
 */
fun Modifier.reorderDragHandle(
    state: SmoothReorderState,
    index: () -> Int,
    key: () -> Any,
    keys: () -> List<Any>,
    listSize: () -> Int,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit
): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    this.pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val currentIndex = index()
            state.onDragStart(currentIndex)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            var hasConsumedMove = false

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break

                val deltaY = change.position.y - change.previousPosition.y
                if (kotlin.math.abs(deltaY) > 0.5f || hasConsumedMove) {
                    change.consume()
                    hasConsumedMove = true
                    state.onDragDelta(
                        deltaY = deltaY,
                        listSize = listSize(),
                        keys = keys(),
                        haptic = haptic,
                        onMove = onMove
                    )
                }
            }
            state.onDragFinish()
        }
    }
}
