package com.aiassistant

import com.aiassistant.ui.screens.settings.CurrentVersionUserUpdates
import com.aiassistant.ui.screens.settings.V216UserUpdates
import org.junit.Assert.*
import org.junit.Test

class V216FeaturesTest {

    @Test
    fun testV216CurrentVersionUserUpdatesCompleteness() {
        val updates = V216UserUpdates
        assertFalse("本次更新日志列表不得为空", updates.isEmpty())
        assertTrue("必须包含当前版本主要更新项 (>=7项)", updates.size >= 7)
        assertTrue("必须包含600s充足模型响应超时放宽", updates.any { it.contains("600s") && it.contains("超时") })
        assertTrue("必须包含文学叙事与自然时间跨度深度支持", updates.any { it.contains("自然时间跨度") && it.contains("两周过后") })
        assertTrue("必须包含自然叙事与单调推进智能融合", updates.any { it.contains("单调推进") && it.contains("智能融合") })
        assertTrue("必须包含5大核心剧情里程碑维度提炼", updates.any { it.contains("5 大核心剧情里程碑") || it.contains("剧情重大转折") })
        assertTrue("必须包含6维常驻与多维设定深度挖掘", updates.any { it.contains("6 维常驻") || it.contains("多维设定") })
        assertTrue("必须包含大模型输出扩充至8192Tokens", updates.any { it.contains("8192") && it.contains("Tokens") })
        assertTrue("必须包含超时异常精准语义反馈与重试支持", updates.any { it.contains("超时异常") && it.contains("重试") })
    }
}
