package com.aiassistant

import com.aiassistant.ui.screens.settings.V213UserUpdates
import com.aiassistant.utils.AdvancedMemoryEngine
import org.junit.Assert.*
import org.junit.Test

class V213FeaturesTest {

    @Test
    fun testV213CurrentVersionUserUpdatesCompleteness() {
        val updates = V213UserUpdates
        assertFalse("本次更新日志列表不得为空", updates.isEmpty())
        assertTrue("必须包含当前版本主要更新项 (>=7项)", updates.size >= 7)
        assertTrue("必须包含开源记忆体系升级", updates.any { it.contains("Mem0") && it.contains("原子事实") })
        assertTrue("必须包含排他性冲突消解", updates.any { it.contains("冲突") && it.contains("消解") })
        assertTrue("必须包含三维混合检索", updates.any { it.contains("三维混合") && it.contains("检索") })
        assertTrue("必须包含结构化多维上下文压缩", updates.any { it.contains("结构化") && it.contains("上下文压缩") })
        assertTrue("必须包含智能信息密度提纯", updates.any { it.contains("信息密度提纯") })
        assertTrue("必须包含平滑重排位移动画", updates.any { it.contains("平滑") && it.contains("动画") })
        assertTrue("必须包含剧情时间线与记忆提取核对", updates.any { it.contains("时间线") && it.contains("核对") })
    }

    @Test
    fun testCategoryClassificationAndBaseWeights() {
        assertEquals(5, AdvancedMemoryEngine.MemoryCategory.CONSTRAINT.baseImportance)
        assertEquals(4, AdvancedMemoryEngine.MemoryCategory.PREFERENCE.baseImportance)
        assertEquals(4, AdvancedMemoryEngine.MemoryCategory.TIMELINE.baseImportance)
        assertEquals(3, AdvancedMemoryEngine.MemoryCategory.WORLD_STATE.baseImportance)
        assertEquals(3, AdvancedMemoryEngine.MemoryCategory.FACT.baseImportance)
    }
}
