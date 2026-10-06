package com.aiassistant

import com.aiassistant.data.repository.AutoTimelineUpdateResult
import com.aiassistant.ui.screens.settings.CurrentVersionUserUpdates
import com.aiassistant.ui.screens.settings.V232UserUpdates
import com.aiassistant.utils.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.junit.Assert.*
import org.junit.Test

class V232FeaturesTest {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    @Test
    fun testV232UserUpdatesCompleteness() {
        assertEquals("V2.3.2 用户更新日志数量应为 8 项完整对齐用户需求", 8, V232UserUpdates.size)
        assertTrue("V232UserUpdates 必须包含更新条目", V232UserUpdates.isNotEmpty())
        assertTrue("必须包含时间线自动识别确认更新", V232UserUpdates.any { it.contains("自动识别") && it.contains("确认") })
        assertTrue("必须包含多轮事件修改与合并更新", V232UserUpdates.any { it.contains("UPDATE") || it.contains("修改补充过往事件") })
        assertTrue("必须包含保留连接报错信息更新", V232UserUpdates.any { it.contains("暂停") && it.contains("报错信息") })
        assertTrue("必须包含时间线实时可见与断点续梳更新", V232UserUpdates.any { it.contains("实时可见") && it.contains("断点续梳") })
        assertTrue("必须包含长文本输入连接报错与空回复修复更新", V232UserUpdates.any { it.contains("长文本输入") && it.contains("空回复") })
        assertTrue("必须包含提取设定两行式精致排版更新", V232UserUpdates.any { it.contains("两行式") })
        assertTrue("必须包含界面防挤压防错位与移除灯泡更新", V232UserUpdates.any { it.contains("防挤压") && it.contains("灯泡") })
        assertTrue("必须包含设定确认保存即刻入库生效更新", V232UserUpdates.any { it.contains("入库生效") || it.contains("会话专属记忆") })
    }

    @Test
    fun testTimelineDraftSerializationAndDeserialization() {
        val originalDraft = TimelineReconcileDraft(
            conversationId = 1001L,
            lastProcessedMessageId = 42L,
            currentStoryTime = "新纪元第 3 天清晨",
            events = listOf(
                TimelineEventItem(
                    timeTag = "新纪元第 1 天",
                    content = "主角抵达边境空间站",
                    category = TimelineCategory.PLOT_EVENT
                ),
                TimelineEventItem(
                    timeTag = "新纪元第 2 天",
                    content = "遭遇未知能量波动并展开调查",
                    category = TimelineCategory.TURNING_POINT
                )
            ),
            atemporalSettings = listOf(
                AtemporalSettingItem(
                    category = "世界规则",
                    content = "空间站人工重力由中子环维持",
                    isSelected = true,
                    targetScope = "session"
                ),
                AtemporalSettingItem(
                    category = "角色特质",
                    content = "主角性格坚毅、擅长星际机械修理",
                    isSelected = true,
                    targetScope = "global"
                )
            ),
            isCompleted = false,
            totalChunks = 4,
            processedChunks = 2,
            timestamp = 1720000000000L
        )

        val json = gson.toJson(originalDraft)
        assertTrue("JSON 序列化结果必须包含 conversationId", json.contains("\"conversationId\": 1001"))
        assertTrue("JSON 序列化结果必须包含时间事件", json.contains("主角抵达边境空间站"))
        assertTrue("JSON 序列化结果必须包含无时序设定", json.contains("空间站人工重力由中子环维持"))

        val restoredDraft = gson.fromJson(json, TimelineReconcileDraft::class.java)
        assertEquals(originalDraft.conversationId, restoredDraft.conversationId)
        assertEquals(originalDraft.lastProcessedMessageId, restoredDraft.lastProcessedMessageId)
        assertEquals(originalDraft.currentStoryTime, restoredDraft.currentStoryTime)
        assertEquals(2, restoredDraft.events.size)
        assertEquals("新纪元第 1 天", restoredDraft.events[0].timeTag)
        assertEquals(TimelineCategory.PLOT_EVENT, restoredDraft.events[0].category)
        assertEquals(2, restoredDraft.atemporalSettings.size)
        assertEquals("世界规则", restoredDraft.atemporalSettings[0].category)
        assertFalse(restoredDraft.isCompleted)
        assertEquals(4, restoredDraft.totalChunks)
        assertEquals(2, restoredDraft.processedChunks)
    }

    @Test
    fun testCorruptDraftWithExplicitNullsIsSanitizedInsteadOfCrashingReview() {
        // 回归：旧草稿/手改文件中的显式 null 经 Gson 反射写入 Kotlin 非空字段，
        // 打开草稿审核弹窗时 toMutableList()/trim/parseColor 即 NPE 闪退。
        val corruptJson = """
            {
              "conversationId": 7,
              "lastProcessedMessageId": 3,
              "currentStoryTime": null,
              "events": [null, {"id": null, "timeTag": null, "content": null, "category": null}],
              "atemporalSettings": [null, {"id": null, "category": null, "content": null, "isSelected": true, "targetScope": "weird"}],
              "isCompleted": false,
              "totalChunks": 1,
              "processedChunks": 1,
              "timestamp": 1
            }
        """.trimIndent()
        val parsed = gson.fromJson(corruptJson, TimelineReconcileDraft::class.java)
        val safe = TimelineDraftManager.sanitizeDraft(parsed)
        assertNotNull("损坏草稿必须被无害化而非返回 null 导致调用方 NPE", safe)
        assertEquals("未确定", safe!!.currentStoryTime)
        assertEquals(1, safe.events.size)
        assertTrue(safe.events[0].id.isNotBlank())
        assertEquals("", safe.events[0].timeTag)
        assertEquals("", safe.events[0].content)
        assertEquals(TimelineCategory.PLOT_EVENT, safe.events[0].category)
        assertEquals(1, safe.atemporalSettings.size)
        assertTrue(safe.atemporalSettings[0].id.isNotBlank())
        assertEquals("角色特质", safe.atemporalSettings[0].category)
        assertEquals("", safe.atemporalSettings[0].content)
        assertEquals("session", safe.atemporalSettings[0].targetScope)
        // 模拟打开审核弹窗的数据复制路径，绝不能抛 NPE
        val eventsCopy = safe.events.toMutableList()
        val settingsCopy = safe.atemporalSettings.toMutableList()
        assertEquals(1, eventsCopy.size)
        assertEquals(1, settingsCopy.size)
    }

    @Test
    fun testSanitizeDraftKeepsValidDraftAndUnknownCategoryNeverReachesUi() {
        // 未知枚举值：Gson 可能抛异常（getDraft 捕获后忽略）或置 null；
        // 两种路径最终都不能把 null 分类漏给 UI。
        val parsedUnknown = try {
            gson.fromJson(
                """{"conversationId": 8, "events": [{"timeTag": "t", "content": "c", "category": "NOT_A_REAL_CATEGORY"}]}""",
                TimelineReconcileDraft::class.java
            )
        } catch (_: Exception) {
            null
        }
        val safeUnknown = TimelineDraftManager.sanitizeDraft(parsedUnknown)
        if (safeUnknown != null) {
            assertEquals(TimelineCategory.PLOT_EVENT, safeUnknown.events.single().category)
        }
        assertNull(TimelineDraftManager.sanitizeDraft(null))

        val valid = TimelineReconcileDraft(
            conversationId = 9L,
            currentStoryTime = "第 2 天·上午",
            events = listOf(TimelineEventItem(id = "e1", timeTag = "第 1 天", content = "开场", category = TimelineCategory.TURNING_POINT)),
            atemporalSettings = listOf(AtemporalSettingItem(id = "s1", category = "世界规则", content = "规则", targetScope = "global"))
        )
        val kept = TimelineDraftManager.sanitizeDraft(valid)
        assertNotNull(kept)
        assertEquals("e1", kept!!.events.single().id)
        assertEquals(TimelineCategory.TURNING_POINT, kept.events.single().category)
        assertEquals("s1", kept.atemporalSettings.single().id)
        assertEquals("global", kept.atemporalSettings.single().targetScope)
    }

    @Test
    fun testAutoTimelineProposalModelActionUpdateVsAppend() {
        // 测试 UPDATE 操作提案结构
        val updateProposal = AutoTimelineUpdateResult(
            updatedStoryTime = "第 2 天夜晚",
            newEvent = TimelineEventItem(
                timeTag = "第 2 天夜晚",
                content = "在废弃矿井深处，两人合力解开古代星图密码并发现暗门",
                category = TimelineCategory.PLOT_EVENT
            ),
            summaryNotice = "更新了时间线",
            action = "UPDATE",
            targetNodeId = 88L,
            previousEventContent = "两人在废弃矿井寻找线索"
        )
        assertEquals("UPDATE", updateProposal.action)
        assertEquals(88L, updateProposal.targetNodeId)
        assertEquals("两人在废弃矿井寻找线索", updateProposal.previousEventContent)
        assertEquals("第 2 天夜晚", updateProposal.updatedStoryTime)

        // 测试 APPEND 操作提案结构
        val appendProposal = AutoTimelineUpdateResult(
            updatedStoryTime = "第 3 天拂晓",
            newEvent = TimelineEventItem(
                timeTag = "第 3 天拂晓",
                content = "黎明破晓，飞船穿越迷雾安全返回基地",
                category = TimelineCategory.TURNING_POINT
            ),
            summaryNotice = "推进了时间线",
            action = "APPEND",
            targetNodeId = null,
            previousEventContent = null
        )
        assertEquals("APPEND", appendProposal.action)
        assertNull(appendProposal.targetNodeId)
        assertNull(appendProposal.previousEventContent)
    }

    @Test
    fun testPausedMessageErrorPreservationFormat() {
        val reconnectStatus = "第 2 次重试中，网络波动超时"
        val errorDetail = "SocketTimeoutException: failed to connect to api.endpoint:443"
        val keyErrors = listOf(
            "Key[主力 1]: 429 Too Many Requests",
            "Key[备用 2]: 504 Gateway Timeout"
        )

        val builder = StringBuilder()
        builder.append("【回复已被暂停】\n")
        builder.append("\n【连接异常信息记录】\n")
        builder.append("· 异常状态: $reconnectStatus\n")
        builder.append("· 报错详情: $errorDetail\n")
        builder.append("· Key 尝试记录:\n")
        keyErrors.forEach { keyErr ->
            builder.append("  - $keyErr\n")
        }

        val formattedResult = builder.toString()
        assertTrue("必须保留【回复已被暂停】标记", formattedResult.contains("【回复已被暂停】"))
        assertTrue("必须包含【连接异常信息记录】专区", formattedResult.contains("【连接异常信息记录】"))
        assertTrue("必须记录当时的网络异常状态", formattedResult.contains("第 2 次重试中，网络波动超时"))
        assertTrue("必须记录详细的报错详情", formattedResult.contains("SocketTimeoutException"))
        assertTrue("必须完整保留多 Key 尝试记录", formattedResult.contains("Key[主力 1]: 429 Too Many Requests"))
        assertTrue("必须完整保留备用 Key 尝试记录", formattedResult.contains("Key[备用 2]: 504 Gateway Timeout"))
    }

    @Test
    fun testTimelineCategoryKeysAndDisplayNames() {
        assertEquals("PLOT_EVENT", TimelineCategory.PLOT_EVENT.key)
        assertEquals("剧情事件", TimelineCategory.PLOT_EVENT.displayName)

        assertEquals("TURNING_POINT", TimelineCategory.TURNING_POINT.key)
        assertEquals("转折关键", TimelineCategory.TURNING_POINT.displayName)

        assertEquals("KEY_FACT", TimelineCategory.KEY_FACT.key)
        assertEquals("重要事实", TimelineCategory.KEY_FACT.displayName)

        assertEquals("RULE_CONSTRAINT", TimelineCategory.RULE_CONSTRAINT.key)
        assertEquals("规则约束", TimelineCategory.RULE_CONSTRAINT.displayName)

        assertEquals(TimelineCategory.PLOT_EVENT, TimelineCategory.fromKey("PLOT_EVENT"))
        assertEquals(TimelineCategory.TURNING_POINT, TimelineCategory.fromKey("TURNING_POINT"))
        assertEquals(TimelineCategory.KEY_FACT, TimelineCategory.fromKey("KEY_FACT"))
        assertEquals(TimelineCategory.RULE_CONSTRAINT, TimelineCategory.fromKey("RULE_CONSTRAINT"))
        assertEquals(TimelineCategory.PLOT_EVENT, TimelineCategory.fromKey("unknown_key"))
    }
}
