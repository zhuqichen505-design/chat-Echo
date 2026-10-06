package com.aiassistant

import com.aiassistant.ui.components.SmoothReorderState
import com.aiassistant.ui.screens.settings.cleanModelNames
import com.aiassistant.ui.screens.settings.parseModelList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UI 细节统一修复回归：
 * 1. 模型列表拖拽排序必须保持自定义顺序（cleanModelNames 去重但不重排）
 * 2. SmoothReorderState 抬升量与按 key 高度记录
 */
class UiPolishRegressionTest {

    private val testClock = object : androidx.compose.runtime.MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            return onFrame(System.nanoTime())
        }
    }
    private val testScope = CoroutineScope(Dispatchers.Unconfined + testClock)

    @Test
    fun cleanModelNames_preservesCustomOrder() {
        // 模拟拖拽后 availableModels 的顺序（后添加的排到前面）
        val reordered = listOf("gpt-6-astra", "claude-sonnet-4-6", "deepseek-flash", "qwen-max")
        val cleaned = cleanModelNames(reordered)
        assertEquals(reordered, cleaned)
    }

    @Test
    fun cleanModelNames_dedupesWithoutReorder() {
        val withDup = listOf("model-b", "model-a", "model-b", "model-c", "model-a")
        val cleaned = cleanModelNames(withDup)
        assertEquals(listOf("model-b", "model-a", "model-c"), cleaned)
    }

    @Test
    fun parseModelList_preservesJsonArrayOrder() {
        val raw = """["zeta-model","alpha-model","mid-model"]"""
        assertEquals(listOf("zeta-model", "alpha-model", "mid-model"), parseModelList(raw))
    }

    @Test
    fun smoothReorder_liftAndKeyHeight() {
        val state = SmoothReorderState(testScope)
        state.setItemHeight("k0", 90f)
        state.setItemHeight(0, 90f)
        assertEquals(90f, state.getItemHeight("k0", 0), 0.001f)
        assertEquals(90f, state.getItemHeight(0), 0.001f)

        assertEquals(0f, state.getItemLift(), 0.001f)
        state.onDragStart(0)
        // Unconfined 调度下 lift 动画立即完成
        assertEquals(1f, state.getItemLift(), 0.001f)

        state.onDragFinish()
        assertEquals(0f, state.getItemLift(), 0.001f)
    }

    @Test
    fun smoothReorder_swapAnimatesWithoutThrowing() {
        val state = SmoothReorderState(testScope)
        state.setItemHeight("a", 80f)
        state.setItemHeight("b", 80f)
        state.onAnimateSwap(fromKey = "a", toKey = "b", fromIndex = 0, toIndex = 1)
        assertTrue(true)
    }

    @Test
    fun contextFallbackChoice_values() {
        val choices = com.aiassistant.domain.model.ContextFallbackChoice.entries
        assertEquals(3, choices.size)
        assertTrue(choices.contains(com.aiassistant.domain.model.ContextFallbackChoice.FALLBACK))
        assertTrue(choices.contains(com.aiassistant.domain.model.ContextFallbackChoice.IGNORE))
        assertTrue(choices.contains(com.aiassistant.domain.model.ContextFallbackChoice.PERMANENTLY_IGNORE))
    }

    @Test
    fun providerOrdering_sortsAccordingToSavedOrder() {
        val savedOrder = listOf(103L, 101L, 102L)
        val orderMap = savedOrder.withIndex().associate { it.value to it.index }

        data class MockProvider(val id: Long, val name: String, val isDefault: Boolean)
        val list = listOf(
            MockProvider(101L, "Provider A", false),
            MockProvider(102L, "Provider B", true),
            MockProvider(103L, "Provider C", false),
            MockProvider(104L, "Provider D", false)
        )

        val sorted = list.sortedWith(
            compareBy<MockProvider> { orderMap[it.id] ?: Int.MAX_VALUE }
                .thenByDescending { it.isDefault }
                .thenBy { it.name }
        )

        assertEquals(listOf(103L, 101L, 102L, 104L), sorted.map { it.id })
    }
}
