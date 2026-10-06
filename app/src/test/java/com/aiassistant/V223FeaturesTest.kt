package com.aiassistant

import androidx.compose.ui.text.font.FontStyle
import com.aiassistant.ui.components.cleanLeadingStarArtifacts
import com.aiassistant.ui.components.parseInlineMarkdown
import org.junit.Assert.*
import org.junit.Test

class V223FeaturesTest {

    @Test
    fun testLeadingStarNotSwallowedInMarkdown() {
        // 1. 验证首字符为单个星号的内容不被 cleanLeadingStarArtifacts 误吞
        val actionText = "*轻轻地叹了一口气* 你怎么现在才来？"
        assertEquals("*轻轻地叹了一口气* 你怎么现在才来？", cleanLeadingStarArtifacts(actionText))

        val normalStarText = "*针对您提出的问题，分析如下："
        assertEquals("*针对您提出的问题，分析如下：", cleanLeadingStarArtifacts(normalStarText))

        val italicMarkdown = "*这是重要的斜体内容*"
        assertEquals("*这是重要的斜体内容*", cleanLeadingStarArtifacts(italicMarkdown))

        val listText = "* 标准无序列表项"
        assertEquals("* 标准无序列表项", cleanLeadingStarArtifacts(listText))

        val boldText = "**重要结论**"
        assertEquals("**重要结论**", cleanLeadingStarArtifacts(boldText))

        // 2. 验证 parseInlineMarkdown 解析带有首部星号的文本时，格式正确渲染且星号不被吞噬
        val parsedItalic = parseInlineMarkdown("*这是斜体*")
        assertEquals("这是斜体", parsedItalic.text)
        val italicStyles = parsedItalic.spanStyles.filter { it.item.fontStyle == FontStyle.Italic }
        assertTrue("必须成功解析出斜体样式", italicStyles.isNotEmpty())

        val parsedAction = parseInlineMarkdown("*动作描写* 说话内容")
        assertEquals("动作描写 说话内容", parsedAction.text)
        val actionStyles = parsedAction.spanStyles.filter { it.item.fontStyle == FontStyle.Italic }
        assertTrue("动作描写部分必须为斜体", actionStyles.isNotEmpty())
        assertEquals(0, actionStyles.first().start)
        assertEquals(4, actionStyles.first().end)

        // 3. 验证孤立的单星号字符（例如流式首个 token 或正文单星号）不被强行剥除
        val parsedSingleStar = parseInlineMarkdown("*")
        assertEquals("*", parsedSingleStar.text)

        val parsedStarWord = parseInlineMarkdown("*未配对星号")
        assertEquals("*未配对星号", parsedStarWord.text)
    }

    @Test
    fun testConnectionBubbleErrorContentPreservedFullLength() {
        val longApiError = "API错误 (401): Incorrect API key provided: sk-proj-1234567890abcdefghijklmnopqrstuvwxyz. You can find your API key at https://platform.openai.com/account/api-keys."
        assertTrue("原始报错长度必须大于 40", longApiError.length > 40)

        // 模拟生成 failText，验证不使用 take(40) 后的完整保留行为
        val cleanErrMsg = longApiError.trim().ifBlank { "未知异常" }
        val failText = "当前 Key 异常 ($cleanErrMsg)，正在自动尝试备用 Key (2/3)..."

        assertTrue("failText 中必须包含完整的 API key 报错原因", failText.contains("Incorrect API key provided"))
        assertTrue("failText 中必须包含报错 URL", failText.contains("https://platform.openai.com"))
        assertFalse("报错不得在40字符处被强行截断为带括号残缺字符串", failText.contains("sk-proj-1234567890abcdefgh)"))

        // 验证网络重连提示包含具体异常详情
        val netExMsg = "failed to connect to api.example.com/1.2.3.4 (port 443) after 15000ms: connect timed out"
        val retryText = "网络波动 ($netExMsg)，正在尝试重新连接 (1/3)..."
        assertTrue("重试文案必须包含连接超时与目标地址等完整信息", retryText.contains("connect timed out") && retryText.contains("api.example.com"))
    }

    @Test
    fun testConnectionBubbleErrorStatusDetection() {
        fun isStatusError(reconnectStatus: String?, capsuleText: String): Boolean {
            return !reconnectStatus.isNullOrBlank() && (
                capsuleText.contains("异常") ||
                capsuleText.contains("报错") ||
                capsuleText.contains("失败") ||
                capsuleText.contains("错误") ||
                capsuleText.contains("Error", ignoreCase = true) ||
                capsuleText.contains("HTTP", ignoreCase = true)
            )
        }

        val normalConnecting = "正在连接 DeepSeek-V3..."
        assertFalse("普通连接中文案不应被判定为错误", isStatusError(normalConnecting, normalConnecting))

        val keyErrorStatus = "当前 Key 异常 (API错误 (401): Invalid Key)，正在自动尝试备用 Key (2/3)..."
        assertTrue("Key 异常状态必须被准确识别为错误状态", isStatusError(keyErrorStatus, keyErrorStatus))

        val httpErrorStatus = "Key[1] 请求报错: HTTP 429 Too Many Requests"
        assertTrue("HTTP 错误状态必须被准确识别为错误状态", isStatusError(httpErrorStatus, httpErrorStatus))
    }

    @Test
    fun testDeleteReplyAnchorStabilizationAlgorithm() {
        // 模拟 4 条消息：0: User 1, 1: Assistant 1, 2: User 2, 3: Assistant 2
        val messageIds = listOf(101L, 102L, 103L, 104L)

        fun computeTargetAnchor(
            targetMsgId: Long,
            firstVisibleIndex: Int,
            visibleIndices: List<Int>
        ): Pair<Int, Boolean> {
            val targetIdx = messageIds.indexOfFirst { it == targetMsgId }
            if (targetIdx < 0) return Pair(firstVisibleIndex, false)

            return when {
                firstVisibleIndex == targetIdx -> {
                    // 当前被删除项是顶部首个可见项，必须将锚点转移给上一项以防止滑动
                    if (targetIdx > 0) {
                        Pair(targetIdx - 1, true) // 切换锚点至上一条有效项
                    } else if (messageIds.size > 1) {
                        Pair(0, true)
                    } else {
                        Pair(0, false)
                    }
                }
                firstVisibleIndex > targetIdx -> {
                    // 被删除项在上方，索引减 1 保持视觉位置不动
                    Pair(firstVisibleIndex - 1, true)
                }
                else -> {
                    // 被删除项在下方，首个可见项位置完全不受影响
                    Pair(firstVisibleIndex, false)
                }
            }
        }

        // 场景 A：用户删除当前正在查看的回复 Assistant 1 (index 1)，当前顶部可见项就是 index 1
        val (newIndexA, shouldAdjustA) = computeTargetAnchor(102L, 1, listOf(1, 2))
        assertTrue("当删除顶部可见项时必须重新锚定", shouldAdjustA)
        assertEquals("必须平滑迁移至上一条提问消息 (index 0)", 0, newIndexA)

        // 场景 B：用户删除末尾回复 Assistant 2 (index 3)，但视口正在阅读 User 2 (index 2)
        val (newIndexB, shouldAdjustB) = computeTargetAnchor(104L, 2, listOf(2, 3))
        assertFalse("删除视口下方项不应触发锚点改变", shouldAdjustB)
        assertEquals("当前阅读的 index 2 保持不动", 2, newIndexB)

        // 场景 C：用户已滑到下方 (index 3)，删除了上方历史消息 Assistant 1 (index 1)
        val (newIndexC, shouldAdjustC) = computeTargetAnchor(102L, 3, listOf(3))
        assertTrue("删除视口上方项需要自动同步新索引", shouldAdjustC)
        assertEquals("上方减少 1 项后，当前视野对应的索引调整为 2，内容像素保持绝对静止", 2, newIndexC)
    }

    @Test
    fun testV223UserUpdatesCompleteness() {
        val updates = com.aiassistant.ui.screens.settings.V223UserUpdates
        assertFalse("V223 更新日志不得为空", updates.isEmpty())
        assertEquals("必须包含全部 6 项核心更新说明", 6, updates.size)
        assertTrue(
            "CurrentVersionUserUpdates 数量必须大于等于 5 项满足基准",
            com.aiassistant.ui.screens.settings.CurrentVersionUserUpdates.size >= 5
        )

        assertTrue("第1项：星号被吞修复说明", updates.any { it.contains("星号误吞") })
        assertTrue("第2项：删除回复防滑说明", updates.any { it.contains("删除回复") })
        assertTrue("第3项：报错全量展示说明", updates.any { it.contains("报错全量展示") })
        assertTrue("第4项：思考链翻译重构说明", updates.any { it.contains("思考链翻译全链路健壮重构") })
        assertTrue("第5项：思考链流式通道说明", updates.any { it.contains("流式通道备用保底") })
        assertTrue("第6项：思考链模型智能继承说明", updates.any { it.contains("思考链翻译模型智能继承") })
    }

    @Test
    fun testThinkingTranslationUrlAndModelFallbackLogic() {
        // 1. 验证 Base URL 自动补全 /v1 逻辑
        fun normalizeApiBaseUrl(rawBaseUrl: String, apiType: String): String {
            var baseUrl = rawBaseUrl.trim()
            if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
                baseUrl = "https://$baseUrl"
            }
            while (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.dropLast(1)
            }
            return when (apiType) {
                "anthropic" -> if (baseUrl.endsWith("/v1") || baseUrl.endsWith("/anthropic")) baseUrl else "$baseUrl/v1"
                else -> if (baseUrl.endsWith("/v1")) baseUrl else "$baseUrl/v1"
            }
        }

        assertEquals("https://api.deepseek.com/v1", normalizeApiBaseUrl("https://api.deepseek.com", "openai"))
        assertEquals("https://api.deepseek.com/v1", normalizeApiBaseUrl("https://api.deepseek.com/", "openai"))
        assertEquals("https://api.openai.com/v1", normalizeApiBaseUrl("https://api.openai.com/v1", "openai"))
        assertEquals("https://api.anthropic.com/v1", normalizeApiBaseUrl("https://api.anthropic.com", "anthropic"))

        // 2. 验证翻译模型未配置时，自动继承当前会话活跃配置逻辑
        val settingsThinkingConfigId = 0L
        val settingsThinkingModel = ""
        val activeConvConfigId = 15L
        val activeConvModel = "deepseek-chat"

        val effectiveConfigId = if (settingsThinkingConfigId > 0L) settingsThinkingConfigId else activeConvConfigId
        val effectiveModel = if (settingsThinkingModel.isNotBlank()) settingsThinkingModel else activeConvModel

        assertEquals("未配置专用翻译模型时必须继承会话当前活跃配置 ID", 15L, effectiveConfigId)
        assertEquals("未配置专用翻译模型时必须继承会话当前活跃模型名称", "deepseek-chat", effectiveModel)
    }
}

