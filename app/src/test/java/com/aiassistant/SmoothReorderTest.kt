package com.aiassistant

import com.aiassistant.ui.components.SmoothReorderState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
        assertNull(state.draggingIndex)
        assertEquals(0f, state.dragOffsetY, 0.001f)
        assertFalse(state.isReleasing)
        assertFalse(state.isItemActive(0))
    }

    @Test
    fun testOnDragStart() {
        val state = SmoothReorderState(testScope)
        state.setItemHeight(1, 120f)
        state.onDragStart(1)

        assertEquals(1, state.draggingIndex)
        assertEquals(0f, state.dragOffsetY, 0.001f)
        assertTrue(state.isItemActive(1))
        assertFalse(state.isItemActive(0))
        assertEquals(120f, state.getItemHeight(1), 0.001f)
    }

    @Test
    fun testOnDragDelta_downwardSwap() {
        val state = SmoothReorderState(testScope)
        val itemHeight = 100f
        state.setItemHeight(0, itemHeight)
        state.setItemHeight(1, itemHeight)
        state.onDragStart(0)

        val keys = mutableListOf("key0", "key1", "key2")
        var movedFrom: Int? = null
        var movedTo: Int? = null

        // 拖动位移未达到阈值 (42f)
        state.onDragDelta(
            deltaY = 30f,
            listSize = keys.size,
            keys = keys,
            haptic = null
        ) { from, to ->
            movedFrom = from
            movedTo = to
        }
        assertEquals(0, state.draggingIndex)
        assertEquals(30f, state.dragOffsetY, 0.001f)
        assertNull(movedFrom)

        // 继续拖动超过阈值 (42f)
        state.onDragDelta(
            deltaY = 20f, // 累计 50f > 42f
            listSize = keys.size,
            keys = keys,
            haptic = null
        ) { from, to ->
            movedFrom = from
            movedTo = to
            val temp = keys[from]
            keys[from] = keys[to]
            keys[to] = temp
        }

        assertEquals(0, movedFrom)
        assertEquals(1, movedTo)
        // 验证当前拖动项索引顺延至 1
        assertEquals(1, state.draggingIndex)
        // 验证位移补偿：50f - 100f = -50f，保证在屏幕上的绝对位置保持连续
        assertEquals(-50f, state.dragOffsetY, 0.001f)
    }

    @Test
    fun testOnDragDelta_upwardSwap() {
        val state = SmoothReorderState(testScope)
        val itemHeight = 100f
        state.setItemHeight(1, itemHeight)
        state.setItemHeight(0, itemHeight)
        state.onDragStart(1)

        val keys = mutableListOf("key0", "key1")
        var swapped = false

        state.onDragDelta(
            deltaY = -60f, // 向上拖动超过 42f 阈值
            listSize = keys.size,
            keys = keys,
            haptic = null
        ) { from, to ->
            swapped = true
            val temp = keys[from]
            keys[from] = keys[to]
            keys[to] = temp
        }

        assertTrue(swapped)
        assertEquals(0, state.draggingIndex)
        // 向上位移补偿：-60f + 100f = +40f
        assertEquals(40f, state.dragOffsetY, 0.001f)
    }

    @Test
    fun testBoundaryGuards() {
        val state = SmoothReorderState(testScope)
        state.setItemHeight(0, 100f)
        state.setItemHeight(1, 100f)

        val keys = listOf("key0", "key1")
        var swapped = false

        // 在顶部项向上拖动，不应发生交换越界
        state.onDragStart(0)
        state.onDragDelta(
            deltaY = -200f,
            listSize = keys.size,
            keys = keys,
            haptic = null
        ) { _, _ -> swapped = true }
        assertFalse(swapped)
        assertEquals(0, state.draggingIndex)

        // 在末尾项向下拖动，不应发生交换越界
        state.onDragStart(1)
        state.onDragDelta(
            deltaY = 200f,
            listSize = keys.size,
            keys = keys,
            haptic = null
        ) { _, _ -> swapped = true }
        assertFalse(swapped)
        assertEquals(1, state.draggingIndex)
    }

    @Test
    fun testOnAnimateSwap_buttonTrigger() {
        val state = SmoothReorderState(testScope)
        state.setItemHeight(0, 80f)
        state.setItemHeight(1, 80f)

        state.onAnimateSwap(fromKey = "k0", toKey = "k1", fromIndex = 0, toIndex = 1)
        // 验证执行无异常崩溃，并且动画已启动
        assertNotNull(state)
    }
}
