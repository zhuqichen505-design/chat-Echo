package com.aiassistant

import com.aiassistant.ui.components.SmoothReorderState
import com.aiassistant.ui.components.computeReorderTargetIndex
import com.aiassistant.ui.components.reorderSlotOffset
import com.aiassistant.ui.components.slotTravelDistance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SmoothReorderTest {

    private val testClock = object : androidx.compose.runtime.MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            return onFrame(System.nanoTime())
        }
    }
    private val testScope = CoroutineScope(Dispatchers.Unconfined + testClock)

    @Test
    fun testInitialState() {
        val state = SmoothReorderState(testScope)
        assertNull(state.draggingKey)
        assertFalse(state.isDragging)
        assertEquals(0f, state.dragOffsetY, 0.001f)
        assertFalse(state.isReleasing)
        assertFalse(state.isItemActive("k0"))
        assertEquals(-1, state.targetIndex)
    }

    @Test
    fun testOnDragStartTracksStableKey() {
        val state = SmoothReorderState(testScope)
        state.setItemHeight("a", 90f)
        state.setItemHeight("b", 120f)
        state.onDragStart(listOf("a", "b", "c"), "b")

        assertEquals("b", state.draggingKey)
        assertEquals(1, state.draggingStartIndex)
        assertEquals(1, state.targetIndex)
        assertEquals(0f, state.dragOffsetY, 0.001f)
        assertTrue(state.isItemActive("b"))
        assertFalse(state.isItemActive("a"))
        assertEquals(120f, state.getItemHeight("b"), 0.001f)
        assertEquals(1f, state.getItemLift(), 0.001f)
    }

    @Test
    fun testDragDownOnlyMovesOnRelease() {
        val state = SmoothReorderState(testScope)
        val keys = listOf("k0", "k1", "k2")
        keys.forEach { state.setItemHeight(it, 100f) }
        state.onDragStart(keys, "k0")

        // 越过邻居中心（(100+100)/2 = 100）才切换到下一槽位
        state.onDragDelta(deltaY = 80f, keys = keys)
        assertEquals(0, state.targetIndex)
        assertEquals(80f, state.dragOffsetY, 0.001f)

        state.onDragDelta(deltaY = 40f, keys = keys)
        assertEquals(1, state.targetIndex)
        assertEquals(120f, state.dragOffsetY, 0.001f)
        // 列表数据在手势期间不变，让位只是视觉位移
        assertEquals(-100f, state.getItemOffsetY("k1"), 0.001f)
        assertEquals(0f, state.getItemOffsetY("k2"), 0.001f)

        var moved: Pair<Int, Int>? = null
        state.onDragFinish(keys) { from, to -> moved = from to to }
        assertEquals(0 to 1, moved)
        assertNull(state.draggingKey)
        assertFalse(state.isDragging)
        assertEquals(0f, state.dragOffsetY, 0.001f)
        assertEquals(0f, state.getItemOffsetY("k1"), 0.001f)
        assertEquals(0f, state.getItemLift(), 0.001f)
    }

    @Test
    fun testDragUpMovesOneSlot() {
        val state = SmoothReorderState(testScope)
        val keys = listOf("k0", "k1")
        keys.forEach { state.setItemHeight(it, 100f) }
        state.onDragStart(keys, "k1")

        state.onDragDelta(deltaY = -120f, keys = keys)
        assertEquals(0, state.targetIndex)
        assertEquals(-120f, state.dragOffsetY, 0.001f)
        assertEquals(100f, state.getItemOffsetY("k0"), 0.001f)

        var moved: Pair<Int, Int>? = null
        state.onDragFinish(keys) { from, to -> moved = from to to }
        assertEquals(1 to 0, moved)
    }

    @Test
    fun testDragStaysOnSameKeyWhenListIsReplaced() {
        val state = SmoothReorderState(testScope)
        val original = listOf("a", "b", "c")
        original.forEach { state.setItemHeight(it, 100f) }
        state.onDragStart(original, "b")

        // 外部回流导致 b 的位置变化：拖拽仍必须绑定 b，不得换成别的条目
        val refreshed = listOf("b", "c", "a")
        state.onDragDelta(deltaY = 60f, keys = refreshed)

        assertEquals("b", state.draggingKey)
        assertTrue(state.isItemActive("b"))
        assertEquals(0, state.draggingStartIndex)
    }

    @Test
    fun testTapWithoutMovementDoesNotReorder() {
        val state = SmoothReorderState(testScope)
        val keys = listOf("k0", "k1")
        keys.forEach { state.setItemHeight(it, 100f) }
        state.onDragStart(keys, "k0")

        var moved = false
        state.onDragFinish(keys) { _, _ -> moved = true }
        assertFalse(moved)
        assertNull(state.draggingKey)
        assertEquals(0f, state.getItemLift(), 0.001f)
    }

    @Test
    fun testBoundaryGuards() {
        val state = SmoothReorderState(testScope)
        state.setItemHeight(0, 100f)
        state.setItemHeight(1, 100f)
        state.setItemHeight(2, 100f)
        val keys = listOf(0, 1, 2)

        // 顶部项继续向上拖动，目标保持 0
        state.onDragStart(keys, 0)
        state.onDragDelta(deltaY = -500f, keys = keys)
        assertEquals(0, state.targetIndex)
        var moved = false
        state.onDragFinish(keys) { _, _ -> moved = true }
        assertFalse(moved)

        // 末尾项继续向下拖动，目标保持末尾
        state.onDragStart(keys, 2)
        state.onDragDelta(deltaY = 500f, keys = keys)
        assertEquals(2, state.targetIndex)
        moved = false
        state.onDragFinish(keys) { _, _ -> moved = true }
        assertFalse(moved)
    }

    @Test
    fun testTargetIndexUsesVariableHeights() {
        assertEquals(0, computeReorderTargetIndex(0, 99f, listOf(100f, 100f)))
        assertEquals(1, computeReorderTargetIndex(0, 101f, listOf(100f, 100f)))
        // 相邻条目更高时，需要更大的位移才跨过其中心
        assertEquals(0, computeReorderTargetIndex(0, 150f, listOf(100f, 300f)))
        assertEquals(1, computeReorderTargetIndex(0, 201f, listOf(100f, 300f)))
        assertEquals(2, computeReorderTargetIndex(1, 201f, listOf(100f, 100f, 300f)))
        assertEquals(0, computeReorderTargetIndex(1, -201f, listOf(300f, 100f)))
        // 列表项间距也要计入跨槽阈值
        assertEquals(0, computeReorderTargetIndex(0, 149f, listOf(100f, 100f), itemSpacing = 50f))
        assertEquals(1, computeReorderTargetIndex(0, 151f, listOf(100f, 100f), itemSpacing = 50f))
    }

    @Test
    fun testSlotOffsetsAndTravel() {
        val heights = listOf(100f, 120f, 140f)
        // 向下让位：k1 与 k2 各自上移被拖拽项 k0 的高度（100f）
        assertEquals(-100f, reorderSlotOffset(1, 0, 2, heights), 0.001f)
        assertEquals(-100f, reorderSlotOffset(2, 0, 2, heights), 0.001f)
        // 向上让位：k1 下移 k2 的高度
        assertEquals(140f, reorderSlotOffset(1, 2, 1, heights), 0.001f)
        assertEquals(0f, reorderSlotOffset(0, 2, 1, heights), 0.001f)
        // 跨槽位移 = 途经兄弟槽位高度之和
        assertEquals(260f, slotTravelDistance(0, 2, heights), 0.001f)
        assertEquals(-220f, slotTravelDistance(2, 0, heights), 0.001f)
        assertEquals(0f, slotTravelDistance(1, 1, heights), 0.001f)
        // 间距参与让位与跨槽补偿
        assertEquals(-108f, reorderSlotOffset(1, 0, 1, listOf(100f, 100f), itemSpacing = 8f), 0.001f)
        assertEquals(216f, slotTravelDistance(0, 2, listOf(100f, 100f, 100f), itemSpacing = 8f), 0.001f)
    }

    @Test
    fun testOnlyActiveItemReceivesLift() {
        val state = SmoothReorderState(testScope)
        val keys = listOf("a", "b", "c")
        state.onDragStart(keys, "b")

        assertEquals(1f, state.getItemLift("b"), 0.001f)
        assertEquals(0f, state.getItemLift("a"), 0.001f)
        assertEquals(0f, state.getItemLift("c"), 0.001f)

        state.onDragFinish(keys) { _, _ -> }
        assertEquals(0f, state.getItemLift("b"), 0.001f)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testOnDragFinishSynchronouslyCompensatesDisplacedItems() = runTest {
        val deferredClock = object : androidx.compose.runtime.MonotonicFrameClock {
            override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
                delay(16)
                return onFrame(testScheduler.currentTime * 1_000_000L)
            }
        }
        val controlledScope = CoroutineScope(backgroundScope.coroutineContext + deferredClock)
        val state = SmoothReorderState(controlledScope)
        val keys = listOf("k0", "k1", "k2")
        state.setItemSpacing(10f)
        state.setItemHeight("k0", 100f)
        state.setItemHeight("k1", 140f)
        state.setItemHeight("k2", 180f)

        state.onDragStart(keys, "k0")
        state.onDragDelta(deltaY = 140f, keys = keys)
        runCurrent()
        advanceTimeBy(300)
        runCurrent()

        assertEquals(1, state.targetIndex)
        assertEquals(-110f, state.getItemOffsetY("k1"), 0.001f)

        var moved: Pair<Int, Int>? = null
        state.onDragFinish(keys) { from, to -> moved = from to to }
        // 松手瞬间已同步抵消 k1 的让位偏移，不产生双倍跳动
        assertEquals(0 to 1, moved)
        assertEquals(0f, state.getItemOffsetY("k1"), 0.001f)
        assertEquals(-10f, state.getItemOffsetY("k0"), 0.001f)

        advanceTimeBy(300)
        runCurrent()
        assertNull(state.draggingKey)
        assertEquals(0f, state.getItemOffsetY("k0"), 0.001f)
    }

    @Test
    fun testOnAnimateSwapAccountsForSpacingAndUnequalHeights() {
        val state = SmoothReorderState(testScope)
        state.setItemSpacing(10f)
        state.setItemHeight("k0", 80f)
        state.setItemHeight("k1", 120f)

        state.onAnimateSwap(fromKey = "k0", toKey = "k1", fromIndex = 0, toIndex = 1)
        assertEquals(0f, state.getItemOffsetY("k0"), 0.001f)
        assertEquals(0f, state.getItemOffsetY("k1"), 0.001f)
    }

    @Test
    fun testOnAnimateSwap_buttonTrigger() {
        val state = SmoothReorderState(testScope)
        state.setItemHeight("k0", 80f)
        state.setItemHeight("k1", 80f)

        state.onAnimateSwap(fromKey = "k0", toKey = "k1", fromIndex = 0, toIndex = 1)
        // 箭头换位动画播放后回到最终位置
        assertEquals(0f, state.getItemOffsetY("k0"), 0.001f)
        assertEquals(0f, state.getItemOffsetY("k1"), 0.001f)
    }
}
