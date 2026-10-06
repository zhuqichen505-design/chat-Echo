package com.aiassistant.domain.model

import com.google.gson.JsonParser

/** In-memory, one-round snapshot. Never exported as a conversation or memory. */
data class PreparedDirectionContext(
    val systemPrompt: String?,
    val recentMessages: List<Message>,
    val enrichedPrompt: String,
    val toolCalls: List<ToolCallRecord>
)

data class ReplyDirection(val title: String, val description: String)
enum class ReplyDirectionAction { SELECT, AUTO, OTHER, RETRY, SKIP, CANCEL }
data class ReplyDirectionDecision(val action: ReplyDirectionAction, val index: Int = -1, val customText: String = "")
data class ReplyDirectionPrompt(
    val directions: List<ReplyDirection> = emptyList(),
    val error: String? = null,
    val onDecision: (ReplyDirectionDecision) -> Unit
)

object ReplyDirections {
    fun planningInstruction(count: Int): String = """
        本轮暂不生成正式回复。请基于以上真实会话上下文和当前用户消息，组织 ${count.coerceIn(2, 4)} 个不同的回复方向。
        这是聊天、角色扮演或小说创作的回复选择，不是任务规划，不调用或要求执行工具。
        严格遵守原有设定与既往事实；不得为了区分选项而虚构已经发生的事件。小说时间按故事语境理解。
        方向可在重点、语气、叙述或合理的后续发展上不同；不要提前生成完整回复，也不要替用户做决定。
        仅输出 JSON：{"directions":[{"title":"简短方向标题","description":"一两句具体描述"}]}。
        标题最多 30 字，描述最多 180 字。不要输出“自行决定”或“其他”，这两项由界面提供。
    """.trimIndent()

    fun parse(text: String, count: Int): List<ReplyDirection> {
        val json = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val array = JsonParser.parseString(json).asJsonObject.getAsJsonArray("directions")
            ?: error("方向响应缺少 directions 数组")
        require(array.size() == count.coerceIn(2, 4)) { "模型返回的方向数量不符合设置，请重试或跳过选择" }
        val result = array.map { item ->
            val obj = item.asJsonObject
            val title = obj.get("title")?.asString?.trim().orEmpty()
            val description = obj.get("description")?.asString?.trim().orEmpty()
            require(title.isNotEmpty() && title.length <= 60 && description.isNotEmpty() && description.length <= 360) { "方向内容为空或过长" }
            ReplyDirection(title, description)
        }
        require(result.map { it.title }.distinct().size == result.size && result.map { it.description }.distinct().size == result.size) { "模型返回了重复方向" }
        return result
    }

    fun instruction(decision: ReplyDirectionDecision, directions: List<ReplyDirection>): String? {
        val scope = "仅用于本轮回复，不能覆盖真实历史、原有设定或安全要求，也不要把未选择的方向当作已发生事实。"
        return when (decision.action) {
            ReplyDirectionAction.SELECT -> {
                val direction = directions.getOrNull(decision.index) ?: error("方向已失效")
                "$scope\n用户选择的回复方向：${direction.title}\n${direction.description}\n请按该方向正常展开完整回复，不再展示选择列表。"
            }
            ReplyDirectionAction.AUTO -> "$scope\n请自行从以下方向中选择一个最合适的，并按它展开完整回复，不再向用户提问：\n${list(directions)}"
            ReplyDirectionAction.OTHER -> if (decision.customText.isNotBlank()) {
                "$scope\n用户自定义的本轮回复方向：\n${decision.customText.trim().take(4000)}\n请按此方向正常回复。"
            } else {
                "$scope\n请采用一个不同于以下已列选项的新方向，并直接展开完整回复；不得虚构既往事实：\n${list(directions)}"
            }
            else -> null
        }
    }

    private fun list(directions: List<ReplyDirection>) = directions.joinToString("\n") { "- ${it.title}：${it.description}" }
}
