package com.aiassistant.utils

import android.content.Context
import android.net.Uri
import com.aiassistant.domain.model.CharacterProfile
import com.aiassistant.domain.model.RoleplayScenario
import java.io.BufferedReader
import java.io.InputStreamReader

data class ParsedRoleplayPack(
    val character: CharacterProfile,
    val scenario: RoleplayScenario,
    val rawText: String
)

object RoleplaySmartParser {

    fun readTextFromUri(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            } ?: ""
        } catch (e: Exception) {
            try {
                // Fallback for GBK/GB2312 encoded Chinese files
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream, java.nio.charset.Charset.forName("GBK"))).use { reader ->
                        reader.readText()
                    }
                } ?: ""
            } catch (_: Exception) {
                ""
            }
        }
    }

    fun parseCharacter(text: String, existing: CharacterProfile? = null): CharacterProfile {
        val sections = extractSections(text)
        val keyValues = extractKeyValues(text)

        fun findValue(keys: List<String>): String {
            for (key in keys) {
                val kv = keyValues[key.lowercase()]
                if (!kv.isNullOrBlank()) return kv
            }
            for (key in keys) {
                val sec = sections[key.lowercase()]
                if (!sec.isNullOrBlank()) return sec
            }
            return ""
        }

        val name = findValue(listOf("姓名", "名字", "角色名", "角色名称", "name", "char_name", "character"))
            .ifBlank { extractFirstTitle(text) ?: existing?.name ?: "未命名角色" }

        val identity = findValue(listOf("身份", "职业", "称号", "头衔", "定位", "identity", "role", "occupation", "class", "title"))
            .ifBlank { existing?.identity ?: "" }

        val personality = findValue(listOf("性格", "人设", "性格特点", "性格特征", "个性", "外貌与性格", "personality", "traits", "character"))
            .ifBlank { existing?.personality ?: "" }

        val background = findValue(listOf("背景", "背景设定", "背景故事", "故事背景", "生平", "经历", "故事", "background", "backstory", "history", "lore"))
            .ifBlank { existing?.background ?: "" }

        val speakingStyle = findValue(listOf("语言风格", "说话风格", "说话方式", "表达方式", "文风", "语气", "口吻", "台词风格", "speaking_style", "speaking style", "voice", "tone", "style"))
            .ifBlank { existing?.speakingStyle ?: "" }

        val goals = findValue(listOf("目标", "动机", "目的", "追求", "愿望", "goals", "motivations", "objective"))
            .ifBlank { existing?.goals ?: "" }

        val relationships = findValue(listOf("关系", "人际关系", "与用户关系", "与主角关系", "relationships", "affiliation"))
            .ifBlank { existing?.relationships ?: "" }

        val knowledge = findValue(listOf("能力", "技能", "知识", "魔法", "专长", "特长", "knowledge", "skills", "abilities", "powers"))
            .ifBlank { existing?.knowledge ?: "" }

        val constraints = findValue(listOf("约束", "禁忌", "禁止违背", "限制", "弱点", "规则", "constraints", "limitations", "weaknesses"))
            .ifBlank { existing?.constraints ?: "" }

        val behaviorRules = findValue(listOf("行为准则", "行为规则", "行为习惯", "准则", "习惯", "behavior_rules", "behavior rules", "habits", "rules"))
            .ifBlank { existing?.behaviorRules ?: "" }

        val greeting = findValue(listOf("开场白", "初始台词", "初始问候", "第一句话", "打招呼", "问候", "初次见面", "首句台词", "greeting", "first_message", "first message", "initial message"))
            .ifBlank { existing?.greeting ?: "" }

        val exampleDialogue = findValue(listOf("对话示例", "示例对话", "对话样例", "常用台词", "example_dialogue", "example dialogue", "mes_example", "dialogue"))
            .ifBlank { existing?.exampleDialogue ?: "" }

        val tags = findValue(listOf("标签", "分类", "类型", "tags", "tag", "keywords", "genre"))
            .ifBlank {
                extractSmartTags(text).takeIf { it.isNotBlank() } ?: existing?.tags
            }

        // If background is empty, but we have text that wasn't parsed into keys, use raw text as background
        val finalBackground = if (background.isBlank() && personality.isBlank() && text.length > 30) {
            text.trim()
        } else {
            background
        }

        return (existing ?: CharacterProfile(name = name)).copy(
            name = name,
            identity = identity,
            personality = personality,
            background = finalBackground,
            speakingStyle = speakingStyle,
            goals = goals,
            relationships = relationships,
            knowledge = knowledge,
            constraints = constraints,
            behaviorRules = behaviorRules,
            greeting = greeting,
            exampleDialogue = exampleDialogue,
            tags = tags
        )
    }

    fun parseScenario(text: String, existing: RoleplayScenario? = null): RoleplayScenario {
        val sections = extractSections(text)
        val keyValues = extractKeyValues(text)

        fun findValue(keys: List<String>): String {
            for (key in keys) {
                val kv = keyValues[key.lowercase()]
                if (!kv.isNullOrBlank()) return kv
            }
            for (key in keys) {
                val sec = sections[key.lowercase()]
                if (!sec.isNullOrBlank()) return sec
            }
            return ""
        }

        val name = findValue(listOf("场景名称", "场景名", "剧本名", "剧本名称", "名称", "scenario_name", "scenario", "title"))
            .ifBlank { extractFirstTitle(text) ?: existing?.name ?: "未命名场景" }

        val worldview = findValue(listOf("世界观", "世界设定", "时代背景", "世界", "worldview", "world_setting", "world setting", "lore"))
            .ifBlank { existing?.worldview ?: "" }

        val time = findValue(listOf("时间", "时代", "年份", "季节", "time", "era", "period", "year"))
            .ifBlank { existing?.time ?: "" }

        val location = findValue(listOf("地点", "位置", "场所", "场景", "地点环境", "location", "place", "setting"))
            .ifBlank { existing?.location ?: "" }

        val environment = findValue(listOf("环境", "环境描写", "周围环境", "场景描写", "environment", "surroundings", "scene"))
            .ifBlank { existing?.environment ?: "" }

        val premise = findValue(listOf("前提", "剧情前提", "背景前提", "事件起因", "开端", "premise", "context", "setup"))
            .ifBlank { existing?.premise ?: "" }

        val rules = findValue(listOf("规则", "不可违背规则", "不可违背法则", "不可违背的规则", "场景规则", "世界规则", "设定规则", "rules", "world_rules", "mechanics"))
            .ifBlank { existing?.rules ?: "" }

        val relationshipState = findValue(listOf("关系状态", "双方关系", "当前关系", "人物关系", "relationship_state", "relationship state"))
            .ifBlank { existing?.relationshipState ?: "" }

        val conflict = findValue(listOf("冲突", "当前冲突", "核心矛盾", "矛盾", "危机", "当前事件", "主要冲突", "conflict", "crisis", "inciting_event"))
            .ifBlank { existing?.conflict ?: "" }

        val plotGoal = findValue(listOf("剧情目标", "主线目标", "主线任务", "目标", "任务", "plot_goal", "plot goal", "main_quest", "objective"))
            .ifBlank { existing?.plotGoal ?: "" }

        val atmosphere = findValue(listOf("氛围", "基调", "气氛", "环境基调", "atmosphere", "mood", "tone"))
            .ifBlank { existing?.atmosphere ?: "" }

        val narrativePerspective = findValue(listOf("叙事视角", "视角", "人称", "narrative_perspective", "perspective", "pov"))
            .ifBlank { existing?.narrativePerspective ?: "" }

        val outputFormat = findValue(listOf("输出格式", "排版要求", "回复格式", "生成格式", "output_format", "format", "style_rules"))
            .ifBlank { existing?.outputFormat ?: "" }

        val contentRestrictions = findValue(listOf("内容限制", "避雷", "禁忌", "限制事项", "注意事项", "content_restrictions", "restrictions", "warnings"))
            .ifBlank { existing?.contentRestrictions ?: "" }

        val openingPrompt = findValue(listOf("开场提示", "引导词", "第一幕", "开场描写", "开场", "opening_prompt", "opening prompt", "starter", "prompt"))
            .ifBlank { existing?.openingPrompt ?: "" }

        val tags = findValue(listOf("标签", "分类", "类型", "tags", "tag", "keywords", "genre"))
            .ifBlank {
                extractSmartTags(text).takeIf { it.isNotBlank() } ?: existing?.tags
            }

        // If worldview or premise is empty, but we have text, use as worldview
        val finalWorldview = if (worldview.isBlank() && premise.isBlank() && text.length > 30) {
            text.trim()
        } else {
            worldview
        }

        return (existing ?: RoleplayScenario(name = name)).copy(
            name = name,
            worldview = finalWorldview,
            time = time,
            location = location,
            environment = environment,
            premise = premise,
            rules = rules,
            relationshipState = relationshipState,
            conflict = conflict,
            plotGoal = plotGoal,
            atmosphere = atmosphere,
            narrativePerspective = narrativePerspective,
            outputFormat = outputFormat,
            contentRestrictions = contentRestrictions,
            openingPrompt = openingPrompt,
            tags = tags
        )
    }

    fun parseFullPack(text: String): ParsedRoleplayPack {
        val character = parseCharacter(text)
        val scenario = parseScenario(text)
        return ParsedRoleplayPack(character, scenario, text)
    }

    private fun extractKeyValues(text: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val lines = text.lines()
        val bracketKvRegex = Regex("""^[-*#\s]*[【\[（(]([a-zA-Z_\u4e00-\u9fa5]{2,15})[】\]）)][\s:：=—]*(.+)$""")
        val plainKvRegex = Regex("""^[-*#\s]*([a-zA-Z_\u4e00-\u9fa5]{2,15})[\s:：=—]+(.+)$""")

        var currentKey: String? = null
        val currentBuffer = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue

            val match = bracketKvRegex.find(trimmed) ?: plainKvRegex.find(trimmed)
            if (match != null) {
                if (currentKey != null && currentBuffer.isNotEmpty()) {
                    map[currentKey.lowercase()] = currentBuffer.toString().trim()
                    currentBuffer.clear()
                }
                val key = match.groupValues[1].trim()
                val value = match.groupValues[2].trim()
                currentKey = key
                currentBuffer.append(value)
            } else if (currentKey != null) {
                // Continuation line
                currentBuffer.append("\n").append(trimmed)
            }
        }
        if (currentKey != null && currentBuffer.isNotEmpty()) {
            map[currentKey.lowercase()] = currentBuffer.toString().trim()
        }
        return map
    }

    private fun extractSections(text: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val headerRegex = Regex("""^(?:[#]{1,4}|【|\[)\s*([a-zA-Z_\u4e00-\u9fa5]{2,20})\s*(?:】|\]|\:)?$""")
        val lines = text.lines()

        var currentHeader: String? = null
        val currentContent = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()
            val match = headerRegex.find(trimmed)
            if (match != null) {
                if (currentHeader != null && currentContent.isNotEmpty()) {
                    map[currentHeader.lowercase()] = currentContent.toString().trim()
                    currentContent.clear()
                }
                currentHeader = match.groupValues[1].trim()
            } else if (currentHeader != null) {
                currentContent.append(line).append("\n")
            }
        }
        if (currentHeader != null && currentContent.isNotEmpty()) {
            map[currentHeader.lowercase()] = currentContent.toString().trim()
        }
        return map
    }

    private fun extractFirstTitle(text: String): String? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val firstLine = lines.firstOrNull() ?: return null
        val titleMatch = Regex("""^[#\s*【\[]*([^\n#【\[\]】]+)[#\s*】\]]*$""").find(firstLine)
        return titleMatch?.groupValues?.get(1)?.trim()?.take(30)?.takeIf { it.length >= 2 && !it.contains(":") && !it.contains("：") }
    }

    private fun extractSmartTags(text: String): String {
        val candidateTags = listOf(
            "奇幻", "科幻", "现代", "古风", "玄幻", "仙侠", "都市", "校园", "冒险",
            "恋爱", "同人", "二次元", "推理", "悬疑", "末世", "赛博朋克", "机甲",
            "治愈", "日常", "魔法", "武侠", "修仙", "系统", "穿越", "异界",
            "女帝", "导师", "同伴", "反派", "傲娇", "病娇", "温柔", "高冷", "腹黑"
        )
        val matched = candidateTags.filter { tag -> text.contains(tag, ignoreCase = true) }
        return matched.distinct().take(6).joinToString(",")
    }
}
