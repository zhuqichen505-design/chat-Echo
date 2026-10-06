package com.aiassistant

import com.aiassistant.data.repository.ChatGenerationManager
import com.aiassistant.ui.screens.chat.isErrorMessage
import com.aiassistant.utils.DayPhase
import com.aiassistant.utils.TimelineMemoryHelper
import org.junit.Assert.*
import org.junit.Test

class V257FeaturesTest {

    // 1. 时间线提取时段识别与防篡改测试（需求 1）
    @Test
    fun testDayPhaseInferFromTextAntiTampering() {
        // 晨间关键词识别
        assertEquals(DayPhase.EARLY_MORNING, DayPhase.inferFromText("大清早推开窗户，朝阳初升"))
        assertEquals(DayPhase.EARLY_MORNING, DayPhase.inferFromText("早上好，我们今天准备出发"))
        assertEquals(DayPhase.MORNING, DayPhase.inferFromText("上午九点，我们准备出发"))
        assertEquals(DayPhase.EARLY_MORNING, DayPhase.inferFromText("黎明破晓时分，晨曦洒在林间"))

        // 对话中含有“晚”字但非夜晚场景，严禁误判为夜晚
        val nonNightSamples = listOf(
            "我昨晚没睡好，今天一大早就醒了",
            "昨夜的雨已经停了，清晨空气很清新",
            "我们晚点再去集市，先吃个早饭",
            "现在出发还不算太晚，正好赶上早集",
            "早晚要分出胜负，但现在才刚破晓",
            "别来晚了，早上九点见"
        )
        for (sample in nonNightSamples) {
            val inferred = DayPhase.inferFromText(sample)
            assertNotEquals("包含非夜晚语境‘晚/昨晚’的文本不可被误判为夜晚: $sample", DayPhase.NIGHT, inferred)
        }

        // 真实夜晚与深夜关键词应正确识别
        assertEquals(DayPhase.NIGHT, DayPhase.inferFromText("夜幕降临，入夜后华灯初上"))
        assertEquals(DayPhase.LATE_NIGHT, DayPhase.inferFromText("此时已是深夜子时，烛光摇曳"))
        assertEquals(DayPhase.DUSK, DayPhase.inferFromText("夕阳西下，黄昏时分，晚霞染红了天空"))
    }

    // 2. 时间线步进防越级飞跃跳跃测试（需求 1）
    @Test
    fun testAutoStoryTimeAdvancementAntiLeap() {
        val baseTime = "第 1 天·清晨"
        val userMsg = "掌柜的，来两碗阳春面。"
        val assistantReply = "昨晚风雨交加，幸好今天清晨放晴了。小二立刻端上两碗热腾腾的面条。"

        val advanced = TimelineMemoryHelper.detectAutoStoryTimeAdvancement(
            currentStoryTime = baseTime,
            userMessage = userMsg,
            assistantReply = assistantReply
        )

        // 严禁从清晨直接飞跃篡改至夜晚
        if (advanced != null) {
            assertFalse("早上对话不可因提及昨晚等词汇直接篡改为夜晚: $advanced", advanced.contains("夜") || advanced.contains("晚"))
        }
    }

    // 3. 全局生成状态管理器与原子防重复保存测试（需求 4 & 5）
    @Test
    fun testChatGenerationManagerAtomicSingleSaveAndBuffering() {
        val testConvId = 99901L
        val session = ChatGenerationManager.startSession(
            conversationId = testConvId,
            modelName = "deepseek-r1",
            variantGroupId = "var-group-1",
            variantIndex = 2
        )

        assertNotNull("Session 必须成功创建", session)
        assertTrue("新建 Session 默认应处于生成状态", session.isGenerating.value)
        assertFalse("新建 Session 初始 isMessageSaved 必须为 false", session.isMessageSaved.get())

        // 流式 Token 追加与缓冲测试
        session.appendThinking("正在推理剧情走向...")
        assertEquals("正在推理剧情走向...", session.currentThinking.value)
        session.appendResponse("第一段正文。")
        assertEquals("第一段正文。", session.currentResponse.value)

        // 原子标记防重复保存验证（CAS 机制：仅允许首次保存成功）
        val firstSave = ChatGenerationManager.tryMarkMessageSaved(testConvId)
        assertTrue("首次标记消息保存必须成功返回 true", firstSave)
        assertTrue("Session 的 isMessageSaved 状态已变为 true", session.isMessageSaved.get())

        val secondSave = ChatGenerationManager.tryMarkMessageSaved(testConvId)
        assertFalse("二次调用标记保存必须返回 false，彻底杜绝多次或重复报错保存", secondSave)

        val thirdSave = session.isMessageSaved.compareAndSet(false, true)
        assertFalse("直接在 AtomicBoolean 上 CAS 亦必须返回 false", thirdSave)

        session.markFinished()
        assertFalse("调用 markFinished 后 isGenerating 必须变为 false", session.isGenerating.value)
        ChatGenerationManager.removeSession(testConvId)
        assertNull("移除后获取 Session 应为 null", ChatGenerationManager.getSession(testConvId))
    }

    // 4. 报错消息识别与中断标记测试（需求 2 & 3）
    @Test
    fun testErrorMessageRecognition() {
        // 中断与网络异常
        assertTrue(isErrorMessage("[输出已被中断: 网络连接异常断开 (Socket closed)]"))
        assertTrue(isErrorMessage("请求失败\n\n连接超时，请检查网络"))
        assertTrue(isErrorMessage("[请求失败] HTTP 500 Internal Server Error"))
        assertTrue(isErrorMessage("【请求失败】API key expired"))
        assertTrue(isErrorMessage("Error: 401 Unauthorized"))
        assertTrue(isErrorMessage("API错误: 服务暂时不可用"))
        assertTrue(isErrorMessage("连接失败: 无法解析主机地址"))
        assertTrue(isErrorMessage("所有 API Key 均已失效"))

        // 正常对话正文不可被误判为报错
        assertFalse(isErrorMessage("你好，请问有什么可以帮助你的？"))
        assertFalse(isErrorMessage("这是一个关于雨夜车站的精彩故事，两人相视一笑。"))
        assertFalse(isErrorMessage("【角色设定】：一位勇敢的剑士。"))
    }
}
