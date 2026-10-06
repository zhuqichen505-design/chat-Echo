package com.aiassistant.utils

import android.content.Context
import android.util.Log
import com.aiassistant.data.remote.RetrofitClient
import com.aiassistant.data.repository.AiRepository
import com.aiassistant.domain.model.ApiConfig
import com.aiassistant.domain.model.CharacterProfile
import com.aiassistant.domain.model.RoleplayScenario
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AnalyzedRoleplayBundle(
    val characters: List<CharacterProfile>,
    val scenario: RoleplayScenario?,
    val isAiAnalyzed: Boolean,
    val summaryReport: String
)

data class ProposedCharacterUpdate(
    val character: CharacterProfile,
    val isNew: Boolean,
    val summaryOfChanges: String
)

data class ProposedScenarioUpdate(
    val scenario: RoleplayScenario,
    val summaryOfChanges: String
)

data class StorySettingProposalBundle(
    val updatedCharacters: List<ProposedCharacterUpdate>,
    val newCharacters: List<ProposedCharacterUpdate>,
    val scenarioUpdate: ProposedScenarioUpdate?,
    val isAiAnalyzed: Boolean,
    val summaryReport: String
) {
    val hasAnyUpdates: Boolean
        get() = updatedCharacters.isNotEmpty() || newCharacters.isNotEmpty() || scenarioUpdate != null

    val totalCount: Int
        get() = updatedCharacters.size + newCharacters.size + (if (scenarioUpdate != null) 1 else 0)
}

object RoleplaySmartAnalyzer {

    private const val TAG = "RoleplaySmartAnalyzer"
    private val gson = Gson()

    private suspend fun safeProgress(onProgress: (String) -> Unit, msg: String) {
        withContext(Dispatchers.Main) {
            try {
                onProgress(msg)
            } catch (e: Exception) {
                Log.w(TAG, "onProgress invocation failed", e)
            }
        }
    }

    /**
     * AI 深度解析并拆解角色设定 (支持小说正文、人物小传、设定文本)
     */
    suspend fun analyzeCharacter(
        context: Context,
        rawText: String,
        repository: AiRepository,
        apiConfig: ApiConfig?,
        selectedModel: String? = null,
        onProgress: (String) -> Unit = {}
    ): CharacterProfile = withContext(Dispatchers.IO) {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return@withContext CharacterProfile(name = "未命名角色")

        val config = apiConfig ?: repository.getDefaultApiConfig()
        if (config != null && config.apiKey.isNotBlank() && config.baseUrl.isNotBlank()) {
            try {
                val modelToUse = selectedModel?.ifBlank { null } ?: config.modelName
                safeProgress(onProgress, "正在连接模型 [${config.name.ifBlank { config.provider }} - $modelToUse] 深度拆解人设与文风...")
                val systemPrompt = """
                    你是一个顶级角色扮演设定提取与人设拆解专家。
                    用户输入的文本可能是一篇小说章节、故事片段、人物小传、角色设定或对话记录。
                    请深度通读并智能识别其中的核心角色，提炼拆解其性格、动机、背景故事、语言风格并生成开场白，严格输出合法 JSON，禁止输出任何其他文字：
                    {
                      "name": "角色名称（必填）",
                      "identity": "身份、职业、头衔或阵营",
                      "personality": "性格特质、内在心理与行为倾向",
                      "background": "背景故事与过往经历提炼（重点概括人物生平，切勿原样照搬整篇小说全文）",
                      "speakingStyle": "语言风格、口吻语气、习惯用语、口癖与文风特点",
                      "goals": "核心目标、动机与当下欲望",
                      "relationships": "与玩家（主角）或他人的关系羁绊",
                      "knowledge": "角色的已知知识边界、能力、技能与特长",
                      "constraints": "绝对不可违背的设定底线与禁忌约束",
                      "behaviorRules": "日常行为准则与习惯动作",
                      "greeting": "符合该角色性格的第一人称初次问候语/开场白台词",
                      "exampleDialogue": "经典对话范例（展现该角色口吻）",
                      "tags": "3-5个分类标签，逗号分隔，例如：傲娇, 冒险家, 二次元"
                    }
                    【重要要求】：如果输入是小说正文，请务必提炼浓缩，切勿把整篇小说原文直接丢进 background 或其他字段中！
                """.trimIndent()

                val jsonResponse = executeAiPrompt(config, systemPrompt, trimmed, modelToUse, onProgress)
                val jsonStr = extractJsonBlock(jsonResponse)
                val parsed = parseSingleCharacterJson(jsonStr)
                if (parsed != null) {
                    safeProgress(onProgress, "AI 深度拆解完成！")
                    return@withContext parsed
                }
            } catch (e: Exception) {
                Log.w(TAG, "AI 角色拆解失败，回退到本地规则解析", e)
                safeProgress(onProgress, "AI 请求异常 (${e.message})，使用本地规则解析...")
            }
        }

        // 本地降级解析
        RoleplaySmartParser.parseCharacter(trimmed)
    }

    /**
     * AI 深度解析并拆解场景与世界观设定
     */
    suspend fun analyzeScenario(
        context: Context,
        rawText: String,
        repository: AiRepository,
        apiConfig: ApiConfig?,
        selectedModel: String? = null,
        onProgress: (String) -> Unit = {}
    ): RoleplayScenario = withContext(Dispatchers.IO) {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return@withContext RoleplayScenario(name = "未命名场景")

        val config = apiConfig ?: repository.getDefaultApiConfig()
        if (config != null && config.apiKey.isNotBlank() && config.baseUrl.isNotBlank()) {
            try {
                val modelToUse = selectedModel?.ifBlank { null } ?: config.modelName
                safeProgress(onProgress, "正在连接模型 [${config.name.ifBlank { config.provider }} - $modelToUse] 深度提取世界观与场景...")
                val systemPrompt = """
                    你是一个顶级剧情世界观与场景构架专家。
                    用户输入的文本可能是一篇小说片段、世界观设定、剧本大纲或当前环境描写。
                    请深度通读并拆解提炼场景要素，严格输出合法 JSON，禁止输出任何其他文字：
                    {
                      "name": "场景/剧本名称",
                      "worldview": "世界观设定与法则背景",
                      "time": "具体时间点或时代背景",
                      "location": "核心发生地点",
                      "environment": "现场环境细节、感官细节与周围氛围",
                      "premise": "剧情起点与当前正在发生的事件前提",
                      "rules": "世界或现场必须遵守的规则/限制",
                      "relationshipState": "登场角色之间的初始关系与状态",
                      "conflict": "当前核心矛盾与危机冲突",
                      "plotGoal": "剧情互动推进的核心目标",
                      "atmosphere": "叙事氛围（如：悬疑紧迫、浪漫宁静、赛博废土）",
                      "narrativePerspective": "第二人称/第三人称",
                      "outputFormat": "动作与心理细致描写的互动小说叙事格式",
                      "contentRestrictions": "内容限制",
                      "openingPrompt": "第一幕开场情境引导语（引导玩家开始互动）",
                      "tags": "3-5个标签，逗号分隔"
                    }
                    【重要要求】：如果输入是小说正文，请务必精炼总结当前现场与世界观，切勿把整篇小说原文直接塞进 environment 或 premise 中！
                """.trimIndent()

                val jsonResponse = executeAiPrompt(config, systemPrompt, trimmed, modelToUse, onProgress)
                val jsonStr = extractJsonBlock(jsonResponse)
                val parsed = parseSingleScenarioJson(jsonStr)
                if (parsed != null) {
                    safeProgress(onProgress, "AI 场景提炼完成！")
                    return@withContext parsed
                }
            } catch (e: Exception) {
                Log.w(TAG, "AI 场景解析失败，回退到本地规则解析", e)
                safeProgress(onProgress, "AI 请求异常 (${e.message})，使用本地规则解析...")
            }
        }

        // 本地降级解析
        RoleplaySmartParser.parseScenario(trimmed)
    }

    /**
     * AI 一键全景解析：同时提取角色包 + 场景世界观
     */
    suspend fun analyzeTextOrNovel(
        context: Context,
        rawText: String,
        repository: AiRepository,
        preferredConfig: ApiConfig? = null,
        selectedModel: String? = null,
        onProgress: (String) -> Unit = {}
    ): AnalyzedRoleplayBundle = withContext(Dispatchers.IO) {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) {
            return@withContext AnalyzedRoleplayBundle(
                characters = emptyList(),
                scenario = null,
                isAiAnalyzed = false,
                summaryReport = "未提供有效文本"
            )
        }

        val config = preferredConfig ?: repository.getDefaultApiConfig()
        if (config != null && config.apiKey.isNotBlank() && config.baseUrl.isNotBlank()) {
            try {
                val modelToUse = selectedModel?.ifBlank { null } ?: config.modelName
                safeProgress(onProgress, "正在连接 AI 模型 [${config.name.ifBlank { config.provider }} - $modelToUse] 进行全景深度拆解...")
                val systemPrompt = """
                    你是一个顶级剧本分析与角色扮演世界观提炼专家。
                    请通读用户给出的文本（可能是人设设定、大纲、小说章节或故事剧本）。
                    请提炼出：
                    1. 主要角色列表（包含主角与重要配角，提取姓名、身份、性格、背景生平摘要、说话风格文风、动机目标、人际关系、能力知识、禁忌约束、第一句问候语、经典台词、标签）
                    2. 场景与世界观设定（剧本名、世界观法则、时间、地点、现场环境氛围、剧情前提、当前矛盾冲突、目标、叙事氛围、第一幕开场引导语、标签）

                    【核心要求】：如果输入是一篇小说正文，请务必精炼总结人物与剧情设定，绝对不要把整篇小说原文直接全部塞入背景或描述字段！
                    
                    请严格输出合法 JSON，不要有任何其他解释：
                    {
                      "characters": [
                        {
                          "name": "角色名",
                          "identity": "身份/职业",
                          "personality": "性格特质与内在心理",
                          "background": "背景故事与过往经历概括",
                          "speakingStyle": "语言风格、口吻用词习惯与文风",
                          "goals": "核心目标与动机欲望",
                          "relationships": "与用户或他人的关系",
                          "knowledge": "能力知识边界",
                          "constraints": "禁忌约束与底线",
                          "behaviorRules": "行为准则",
                          "greeting": "初次见面开场白或首句台词",
                          "exampleDialogue": "经典对话范例",
                          "tags": "标签1, 标签2"
                        }
                      ],
                      "scenario": {
                        "name": "场景/剧本名称",
                        "worldview": "世界观设定与法则背景",
                        "time": "时代背景或时间点",
                        "location": "主要地点",
                        "environment": "现场环境细节与氛围描写",
                        "premise": "剧情起点与当前事件",
                        "rules": "世界不可违背的法则",
                        "conflict": "当前核心矛盾冲突",
                        "plotGoal": "互动推进目标",
                        "atmosphere": "叙事氛围",
                        "narrativePerspective": "第二人称/第三人称",
                        "outputFormat": "动作与心理细致描写的互动小说格式",
                        "contentRestrictions": "限制内容",
                        "openingPrompt": "第一幕开场情境引导语",
                        "tags": "标签1, 标签2"
                      }
                    }
                """.trimIndent()

                val jsonResponse = executeAiPrompt(config, systemPrompt, trimmed, modelToUse, onProgress)
                val jsonStr = extractJsonBlock(jsonResponse)
                val bundle = parseAnalyzedBundleJson(jsonStr, isAi = true)
                if (bundle.characters.isNotEmpty() || bundle.scenario != null) {
                    safeProgress(onProgress, "AI 全景拆解完成！")
                    return@withContext bundle
                }
            } catch (e: Exception) {
                Log.w(TAG, "AI 智能全景解析失败，回退到本地规则", e)
                safeProgress(onProgress, "AI 请求受限 (${e.message})，使用本地规则解析...")
            }
        }

        fallbackLocalAnalysis(trimmed)
    }

    /**
     * AI 深度情境分析与精准设定融入：
     * 结合故事中已有角色和世界观，精准辨别“更新已有角色”、“发现新角色”、“扩展世界观”或“无设定变动”，
     * 拒绝粗暴粗放的一刀切重复创建。
     */
    suspend fun analyzeStoryInputForProposal(
        context: Context,
        rawText: String,
        existingCharacters: List<CharacterProfile>,
        existingScenario: RoleplayScenario?,
        repository: AiRepository,
        preferredConfig: ApiConfig? = null,
        selectedModel: String? = null,
        onProgress: (String) -> Unit = {}
    ): StorySettingProposalBundle = withContext(Dispatchers.IO) {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) {
            return@withContext StorySettingProposalBundle(
                updatedCharacters = emptyList(),
                newCharacters = emptyList(),
                scenarioUpdate = null,
                isAiAnalyzed = false,
                summaryReport = "未提供有效文本"
            )
        }

        val config = preferredConfig ?: repository.getDefaultApiConfig()
        if (config != null && config.apiKey.isNotBlank() && config.baseUrl.isNotBlank()) {
            try {
                val modelToUse = selectedModel?.ifBlank { null } ?: config.modelName
                safeProgress(onProgress, "正在结合故事已有设定进行精准分析 [${config.name.ifBlank { config.provider }}]...")

                val knownCharsDesc = if (existingCharacters.isNotEmpty()) {
                    existingCharacters.joinToString("\n") {
                        "- 【${it.name}】（ID: ${it.id}）身份: ${it.identity.ifBlank { "未定" }}，性格: ${it.personality.ifBlank { "未定" }}，背景: ${it.background.take(60)}"
                    }
                } else {
                    "（暂无已知角色）"
                }

                val knownScenarioDesc = if (existingScenario != null) {
                    "剧本名: 【${existingScenario.name}】，世界观: ${existingScenario.worldview.take(80)}，地点: ${existingScenario.location}，规则: ${existingScenario.rules}"
                } else {
                    "（暂无世界观设定）"
                }

                val systemPrompt = """
                    你是一个顶级故事设定与人物档案提炼专家。
                    你的任务是深度分析用户提供的【输入文本或最近剧情片段】，并结合当前故事已有的【已知角色列表】和【已有世界观设定】，精准提取并分类以下设定：

                    【当前故事已知角色列表】：
                    $knownCharsDesc

                    【当前故事已有世界观与场景】：
                    $knownScenarioDesc

                    【任务指引与分类规则】：
                    1. 严格区分“更新已有角色”与“新增角色”：
                       - 如果文本是在描述已有角色的新能力、新经历、心理转变、人际关系变化或新目标，请归类为【更新已有角色】（updatedCharacters），并给出变动摘要与合并后的完整人设；
                       - 只有当文本明确出现全新姓名或独立新人物且不属于已知角色时，才归类为【新登场角色】（newCharacters）；
                       - 如果只是普通对白或动作，没有人物属性/状态/背景的变化，不要修改已有角色。
                    2. 严格区分“世界观/场景设定更新”与“无需更新世界观”：
                       - 只有当文本涉及新的世界法则、地理环境、势力格局、环境氛围或主线危机冲突变动时，才更新世界观（scenarioUpdate）；
                       - 如果只是角色之间的日常对话或局部动作，世界观未发生任何改变，scenarioUpdate 必须为 null！切勿强行捏造新世界观或把角色对话当成世界观！
                    3. 输出严格合法 JSON，禁止输出任何其他解释说明：
                    {
                      "hasSettingUpdates": true,
                      "updatedCharacters": [
                        {
                          "name": "已有角色的姓名",
                          "summaryOfChanges": "变动摘要，如：领悟惊雷剑法，突破至金丹期",
                          "profile": {
                            "name": "已有角色姓名",
                            "identity": "身份/职业",
                            "personality": "性格特质",
                            "background": "背景故事（包含新补充经历）",
                            "speakingStyle": "语言风格",
                            "goals": "核心目标",
                            "relationships": "人际关系",
                            "knowledge": "技能能力",
                            "constraints": "禁忌约束",
                            "behaviorRules": "行为习惯",
                            "greeting": "开场白",
                            "exampleDialogue": "经典台词",
                            "tags": "标签"
                          }
                        }
                      ],
                      "newCharacters": [
                        {
                          "name": "新角色姓名",
                          "summaryOfChanges": "新角色身份说明",
                          "profile": {
                            "name": "新角色姓名",
                            "identity": "身份/职业",
                            "personality": "性格特质",
                            "background": "背景故事",
                            "speakingStyle": "语言风格",
                            "goals": "核心目标",
                            "relationships": "人际关系",
                            "knowledge": "技能能力",
                            "constraints": "禁忌约束",
                            "behaviorRules": "行为习惯",
                            "greeting": "开场白",
                            "exampleDialogue": "经典台词",
                            "tags": "标签"
                          }
                        }
                      ],
                      "scenarioUpdate": {
                        "summaryOfChanges": "世界观/场景变动说明",
                        "scenario": {
                          "name": "世界观/场景名称",
                          "worldview": "世界观设定",
                          "time": "时间",
                          "location": "地点",
                          "environment": "环境描写",
                          "premise": "剧情前提",
                          "rules": "不可违背规则",
                          "relationshipState": "关系状态",
                          "conflict": "当前矛盾冲突",
                          "plotGoal": "剧情目标",
                          "atmosphere": "氛围",
                          "narrativePerspective": "叙事视角",
                          "outputFormat": "输出格式",
                          "contentRestrictions": "内容限制",
                          "openingPrompt": "开场提示",
                          "tags": "标签"
                        }
                      }
                    }
                """.trimIndent()

                val jsonResponse = executeAiPrompt(config, systemPrompt, trimmed, modelToUse, onProgress)
                val jsonStr = extractJsonBlock(jsonResponse)
                val proposal = parseStorySettingProposalJson(jsonStr, existingCharacters, existingScenario, isAi = true)
                if (proposal.hasAnyUpdates) {
                    safeProgress(onProgress, "识别到可补充/更新的故事设定！")
                    return@withContext proposal
                }
            } catch (e: Exception) {
                Log.w(TAG, "AI 设定融合识别失败，使用本地规则解析", e)
                safeProgress(onProgress, "AI 分析异常 (${e.message})，使用本地规则解析...")
            }
        }

        fallbackLocalStoryProposal(trimmed, existingCharacters, existingScenario)
    }

    private fun parseStorySettingProposalJson(
        jsonStr: String,
        existingCharacters: List<CharacterProfile>,
        existingScenario: RoleplayScenario?,
        isAi: Boolean
    ): StorySettingProposalBundle {
        return try {
            val root = gson.fromJson(jsonStr, JsonObject::class.java) ?: return StorySettingProposalBundle(emptyList(), emptyList(), null, isAi, "解析为空")
            val updatedList = mutableListOf<ProposedCharacterUpdate>()
            val newList = mutableListOf<ProposedCharacterUpdate>()

            root.getAsJsonArray("updatedCharacters")?.forEach { elem ->
                if (elem.isJsonObject) {
                    val obj = elem.asJsonObject
                    val summary = obj.get("summaryOfChanges")?.asString.orEmpty().ifBlank { "设定发生更新" }
                    val charObj = obj.getAsJsonObject("profile") ?: obj
                    val parsedChar = parseSingleCharacterJson(gson.toJson(charObj))
                    if (parsedChar != null) {
                        val existingMatch = existingCharacters.find { it.name.trim().equals(parsedChar.name.trim(), ignoreCase = true) }
                        val finalChar = if (existingMatch != null) {
                            mergeCharacters(existingMatch, parsedChar)
                        } else {
                            parsedChar
                        }
                        updatedList.add(
                            ProposedCharacterUpdate(
                                character = finalChar,
                                isNew = false,
                                summaryOfChanges = summary
                            )
                        )
                    }
                }
            }

            root.getAsJsonArray("newCharacters")?.forEach { elem ->
                if (elem.isJsonObject) {
                    val obj = elem.asJsonObject
                    val summary = obj.get("summaryOfChanges")?.asString.orEmpty().ifBlank { "新登场角色" }
                    val charObj = obj.getAsJsonObject("profile") ?: obj
                    val parsedChar = parseSingleCharacterJson(gson.toJson(charObj))
                    if (parsedChar != null && parsedChar.name.isNotBlank() && parsedChar.name != "未命名角色") {
                        newList.add(
                            ProposedCharacterUpdate(
                                character = parsedChar,
                                isNew = true,
                                summaryOfChanges = summary
                            )
                        )
                    }
                }
            }

            var scenarioUpdate: ProposedScenarioUpdate? = null
            if (root.has("scenarioUpdate") && !root.get("scenarioUpdate").isJsonNull && root.get("scenarioUpdate").isJsonObject) {
                val scObj = root.getAsJsonObject("scenarioUpdate")
                val summary = scObj.get("summaryOfChanges")?.asString.orEmpty().ifBlank { "世界观/场景设定更新" }
                val rawSc = scObj.getAsJsonObject("scenario") ?: scObj
                val parsedSc = parseSingleScenarioJson(gson.toJson(rawSc))
                if (parsedSc != null) {
                    val finalScenario = if (existingScenario != null) {
                        mergeScenarios(existingScenario, parsedSc)
                    } else {
                        parsedSc
                    }
                    scenarioUpdate = ProposedScenarioUpdate(
                        scenario = finalScenario,
                        summaryOfChanges = summary
                    )
                }
            }

            val summaryBuilder = StringBuilder()
            if (updatedList.isNotEmpty()) {
                summaryBuilder.append("更新已有角色: ${updatedList.joinToString { it.character.name }}；")
            }
            if (newList.isNotEmpty()) {
                summaryBuilder.append("发现新角色: ${newList.joinToString { it.character.name }}；")
            }
            if (scenarioUpdate != null) {
                summaryBuilder.append("更新世界观: ${scenarioUpdate.scenario.name}；")
            }
            if (summaryBuilder.isEmpty()) {
                summaryBuilder.append("未识别到实质设定变动")
            }

            StorySettingProposalBundle(
                updatedCharacters = updatedList,
                newCharacters = newList,
                scenarioUpdate = scenarioUpdate,
                isAiAnalyzed = isAi,
                summaryReport = summaryBuilder.toString()
            )
        } catch (e: Exception) {
            Log.w(TAG, "解析 Proposal JSON 失败", e)
            StorySettingProposalBundle(emptyList(), emptyList(), null, isAi, "解析失败: ${e.message}")
        }
    }

    private fun fallbackLocalStoryProposal(
        text: String,
        existingCharacters: List<CharacterProfile>,
        existingScenario: RoleplayScenario?
    ): StorySettingProposalBundle {
        val parsedChar = RoleplaySmartParser.parseCharacter(text)
        val parsedSc = RoleplaySmartParser.parseScenario(text)

        val updatedList = mutableListOf<ProposedCharacterUpdate>()
        val newList = mutableListOf<ProposedCharacterUpdate>()

        if (parsedChar.name.isNotBlank() && parsedChar.name != "未命名角色") {
            val existing = existingCharacters.find { it.name.trim().equals(parsedChar.name.trim(), ignoreCase = true) }
            if (existing != null) {
                updatedList.add(
                    ProposedCharacterUpdate(
                        character = mergeCharacters(existing, parsedChar),
                        isNew = false,
                        summaryOfChanges = "补充【${existing.name}】的设定与经历"
                    )
                )
            } else {
                newList.add(
                    ProposedCharacterUpdate(
                        character = parsedChar,
                        isNew = true,
                        summaryOfChanges = "新提取角色【${parsedChar.name}】"
                    )
                )
            }
        }

        var scenarioUpdate: ProposedScenarioUpdate? = null
        if (parsedSc.worldview.isNotBlank() || parsedSc.rules.isNotBlank() || parsedSc.location.isNotBlank()) {
            if (existingScenario != null) {
                scenarioUpdate = ProposedScenarioUpdate(
                    scenario = mergeScenarios(existingScenario, parsedSc),
                    summaryOfChanges = "扩充世界观与场景设定"
                )
            } else if (parsedSc.name.isNotBlank() && parsedSc.name != "未命名场景") {
                scenarioUpdate = ProposedScenarioUpdate(
                    scenario = parsedSc,
                    summaryOfChanges = "提取全新世界观【${parsedSc.name}】"
                )
            }
        }

        val summary = if (updatedList.isNotEmpty() || newList.isNotEmpty() || scenarioUpdate != null) {
            "本地规则提取: 角色(${updatedList.size + newList.size}), 世界观(${if (scenarioUpdate != null) 1 else 0})"
        } else {
            "未检测到符合格式的设定变动"
        }

        return StorySettingProposalBundle(
            updatedCharacters = updatedList,
            newCharacters = newList,
            scenarioUpdate = scenarioUpdate,
            isAiAnalyzed = false,
            summaryReport = summary
        )
    }

    private suspend fun executeAiPrompt(
        config: ApiConfig,
        systemPrompt: String,
        userText: String,
        selectedModel: String? = null,
        onProgress: (String) -> Unit
    ): String {
        val safeText = if (userText.length > 20000) {
            userText.take(20000) + "\n\n[注：文本过长，已截取前20000字进行核心人设与剧情提取]"
        } else {
            userText
        }

        val modelToUse = selectedModel?.ifBlank { null } ?: config.modelName

        val requestPayload = JsonObject().apply {
            addProperty("model", modelToUse)
            addProperty("temperature", 0.3)
            addProperty("max_tokens", 8192)
            addProperty("stream", false)
            val messages = JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "system")
                    addProperty("content", systemPrompt)
                })
                add(JsonObject().apply {
                    addProperty("role", "user")
                    addProperty("content", "请分析以下文本并输出提取的 JSON：\n\n$safeText")
                })
            }
            add("messages", messages)
        }

        val auth = RetrofitClient.formatApiKey(config.apiKey)
        val response = RetrofitClient.postJson(
            baseUrl = config.baseUrl,
            path = "chat/completions",
            headers = mapOf(
                "Authorization" to auth,
                "Accept" to "application/json"
            ),
            json = gson.toJson(requestPayload)
        )

        val rawBody = response.use { okResponse ->
            if (!okResponse.isSuccessful) {
                throw Exception("HTTP ${okResponse.code}: ${okResponse.message}")
            }
            okResponse.body?.string() ?: throw Exception("响应体为空")
        }

        return extractTextFromOpenAiResponse(rawBody) ?: rawBody
    }

    private fun extractTextFromOpenAiResponse(responseBody: String): String? {
        try {
            val root = gson.fromJson(responseBody, JsonObject::class.java)
            val choices = root.getAsJsonArray("choices")
            if (choices != null && choices.size() > 0) {
                val message = choices.get(0).asJsonObject.getAsJsonObject("message")
                return message?.get("content")?.asString
            }
        } catch (_: Exception) {}
        return null
    }

    private fun extractJsonBlock(text: String): String {
        val trimmed = text.trim()
        val markdownMatch = Regex("```(?:json)?\\s*([\\s\\S]*?)\\s*```").find(trimmed)
        if (markdownMatch != null) {
            return markdownMatch.groupValues[1].trim()
        }
        val firstBrace = trimmed.indexOf('{')
        val lastBrace = trimmed.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1)
        }
        return trimmed
    }

    private fun parseSingleCharacterJson(jsonStr: String): CharacterProfile? {
        return try {
            val obj = gson.fromJson(jsonStr, JsonObject::class.java)
            val charObj = if (obj.has("characters") && obj.getAsJsonArray("characters").size() > 0) {
                obj.getAsJsonArray("characters").get(0).asJsonObject
            } else if (obj.has("character")) {
                obj.getAsJsonObject("character")
            } else {
                obj
            }

            val name = charObj.get("name")?.asString?.trim().orEmpty().ifBlank { "提取角色" }
            CharacterProfile(
                name = name,
                identity = charObj.get("identity")?.asString.orEmpty(),
                personality = charObj.get("personality")?.asString.orEmpty(),
                background = charObj.get("background")?.asString.orEmpty(),
                speakingStyle = charObj.get("speakingStyle")?.asString.orEmpty(),
                goals = charObj.get("goals")?.asString.orEmpty(),
                relationships = charObj.get("relationships")?.asString.orEmpty(),
                knowledge = charObj.get("knowledge")?.asString.orEmpty(),
                constraints = charObj.get("constraints")?.asString.orEmpty().ifBlank { charObj.get("behaviorRules")?.asString.orEmpty() },
                behaviorRules = charObj.get("behaviorRules")?.asString.orEmpty(),
                greeting = charObj.get("greeting")?.asString.orEmpty(),
                exampleDialogue = charObj.get("exampleDialogue")?.asString.orEmpty(),
                tags = charObj.get("tags")?.asString
            )
        } catch (e: Exception) {
            Log.w(TAG, "解析角色 JSON 异常", e)
            null
        }
    }

    private fun parseSingleScenarioJson(jsonStr: String): RoleplayScenario? {
        return try {
            val root = gson.fromJson(jsonStr, JsonObject::class.java)
            val scObj = if (root.has("scenario")) {
                root.getAsJsonObject("scenario")
            } else {
                root
            }

            val name = scObj.get("name")?.asString?.trim().orEmpty().ifBlank { "提取场景" }
            RoleplayScenario(
                name = name,
                worldview = scObj.get("worldview")?.asString.orEmpty(),
                time = scObj.get("time")?.asString.orEmpty(),
                location = scObj.get("location")?.asString.orEmpty(),
                environment = scObj.get("environment")?.asString.orEmpty(),
                premise = scObj.get("premise")?.asString.orEmpty(),
                rules = scObj.get("rules")?.asString.orEmpty().ifBlank { scObj.get("worldRules")?.asString.orEmpty() },
                relationshipState = scObj.get("relationshipState")?.asString.orEmpty(),
                conflict = scObj.get("conflict")?.asString.orEmpty(),
                plotGoal = scObj.get("plotGoal")?.asString.orEmpty().ifBlank { scObj.get("goal")?.asString.orEmpty() },
                atmosphere = scObj.get("atmosphere")?.asString.orEmpty(),
                narrativePerspective = scObj.get("narrativePerspective")?.asString.orEmpty().ifBlank { "第二人称" },
                outputFormat = scObj.get("outputFormat")?.asString.orEmpty(),
                contentRestrictions = scObj.get("contentRestrictions")?.asString.orEmpty(),
                openingPrompt = scObj.get("openingPrompt")?.asString.orEmpty(),
                tags = scObj.get("tags")?.asString
            )
        } catch (e: Exception) {
            Log.w(TAG, "解析场景 JSON 异常", e)
            null
        }
    }

    private fun parseAnalyzedBundleJson(jsonStr: String, isAi: Boolean): AnalyzedRoleplayBundle {
        val root = gson.fromJson(jsonStr, JsonObject::class.java)
        val characters = mutableListOf<CharacterProfile>()

        root.getAsJsonArray("characters")?.forEach { elem ->
            if (elem.isJsonObject) {
                val obj = elem.asJsonObject
                val name = obj.get("name")?.asString?.trim().orEmpty().ifBlank { "未命名角色" }
                characters.add(
                    CharacterProfile(
                        name = name,
                        identity = obj.get("identity")?.asString.orEmpty(),
                        personality = obj.get("personality")?.asString.orEmpty(),
                        background = obj.get("background")?.asString.orEmpty(),
                        speakingStyle = obj.get("speakingStyle")?.asString.orEmpty(),
                        goals = obj.get("goals")?.asString.orEmpty(),
                        relationships = obj.get("relationships")?.asString.orEmpty(),
                        knowledge = obj.get("knowledge")?.asString.orEmpty(),
                        constraints = obj.get("constraints")?.asString.orEmpty().ifBlank { obj.get("behaviorRules")?.asString.orEmpty() },
                        behaviorRules = obj.get("behaviorRules")?.asString.orEmpty(),
                        greeting = obj.get("greeting")?.asString.orEmpty(),
                        exampleDialogue = obj.get("exampleDialogue")?.asString.orEmpty(),
                        tags = obj.get("tags")?.asString
                    )
                )
            }
        }

        var scenario: RoleplayScenario? = null
        if (root.has("scenario") && root.get("scenario").isJsonObject) {
            val scObj = root.getAsJsonObject("scenario")
            val scName = scObj.get("name")?.asString?.trim().orEmpty().ifBlank { "提取场景" }
            scenario = RoleplayScenario(
                name = scName,
                worldview = scObj.get("worldview")?.asString.orEmpty(),
                time = scObj.get("time")?.asString.orEmpty(),
                location = scObj.get("location")?.asString.orEmpty(),
                environment = scObj.get("environment")?.asString.orEmpty(),
                premise = scObj.get("premise")?.asString.orEmpty(),
                rules = scObj.get("rules")?.asString.orEmpty().ifBlank { scObj.get("worldRules")?.asString.orEmpty() },
                relationshipState = scObj.get("relationshipState")?.asString.orEmpty(),
                conflict = scObj.get("conflict")?.asString.orEmpty(),
                plotGoal = scObj.get("plotGoal")?.asString.orEmpty().ifBlank { scObj.get("goal")?.asString.orEmpty() },
                atmosphere = scObj.get("atmosphere")?.asString.orEmpty(),
                narrativePerspective = scObj.get("narrativePerspective")?.asString.orEmpty().ifBlank { "第二人称" },
                outputFormat = scObj.get("outputFormat")?.asString.orEmpty(),
                contentRestrictions = scObj.get("contentRestrictions")?.asString.orEmpty(),
                openingPrompt = scObj.get("openingPrompt")?.asString.orEmpty(),
                tags = scObj.get("tags")?.asString
            )
        }

        val charSummary = if (characters.isNotEmpty()) "提取出 ${characters.size} 位角色 [${characters.joinToString { it.name }}]" else "未检测到角色"
        val scSummary = if (scenario != null) "剧本 [${scenario.name}]" else "无场景"
        return AnalyzedRoleplayBundle(
            characters = characters,
            scenario = scenario,
            isAiAnalyzed = isAi,
            summaryReport = "$charSummary · $scSummary"
        )
    }

    private fun fallbackLocalAnalysis(text: String): AnalyzedRoleplayBundle {
        val charProfile = RoleplaySmartParser.parseCharacter(text)
        val scProfile = RoleplaySmartParser.parseScenario(text)
        val charName: String = if (charProfile.name.isNotBlank()) charProfile.name else "提取角色"
        val scName: String = if (scProfile.name.isNotBlank()) scProfile.name else "提取场景"
        return AnalyzedRoleplayBundle(
            characters = listOf(charProfile.copy(name = charName)),
            scenario = scProfile.copy(name = scName),
            isAiAnalyzed = false,
            summaryReport = "本地规则识别：角色 [$charName] · 剧本 [$scName]"
        )
    }

    /**
     * 智能融合角色设定
     */
    fun mergeCharacters(existing: CharacterProfile, incoming: CharacterProfile): CharacterProfile {
        val mergedBackground = when {
            existing.background.isBlank() -> incoming.background
            incoming.background.isBlank() -> existing.background
            existing.background.contains(incoming.background) -> existing.background
            else -> "${existing.background}\n\n【补充背景】\n${incoming.background}"
        }

        val mergedPersonality = when {
            existing.personality.isBlank() -> incoming.personality
            incoming.personality.isBlank() -> existing.personality
            else -> "${existing.personality}；${incoming.personality}"
        }

        val mergedIdentity = when {
            existing.identity.isBlank() -> incoming.identity
            incoming.identity.isBlank() -> existing.identity
            existing.identity == incoming.identity -> existing.identity
            else -> "${existing.identity} / ${incoming.identity}"
        }

        val mergedSpeakingStyle = incoming.speakingStyle.ifBlank { existing.speakingStyle }
        val mergedGoals = when {
            existing.goals.isBlank() -> incoming.goals
            incoming.goals.isBlank() -> existing.goals
            else -> "${existing.goals}\n${incoming.goals}"
        }

        val mergedRelationships = when {
            existing.relationships.isBlank() -> incoming.relationships
            incoming.relationships.isBlank() -> existing.relationships
            else -> "${existing.relationships}\n${incoming.relationships}"
        }

        val mergedKnowledge = when {
            existing.knowledge.isBlank() -> incoming.knowledge
            incoming.knowledge.isBlank() -> existing.knowledge
            else -> "${existing.knowledge}\n${incoming.knowledge}"
        }

        val mergedConstraints = when {
            existing.constraints.isBlank() -> incoming.constraints
            incoming.constraints.isBlank() -> existing.constraints
            else -> "${existing.constraints}\n${incoming.constraints}"
        }

        val mergedBehaviorRules = when {
            existing.behaviorRules.isBlank() -> incoming.behaviorRules
            incoming.behaviorRules.isBlank() -> existing.behaviorRules
            else -> "${existing.behaviorRules}\n${incoming.behaviorRules}"
        }

        val mergedGreeting = incoming.greeting.ifBlank { existing.greeting }
        val mergedExampleDialogue = when {
            existing.exampleDialogue.isBlank() -> incoming.exampleDialogue
            incoming.exampleDialogue.isBlank() -> existing.exampleDialogue
            else -> "${existing.exampleDialogue}\n\n${incoming.exampleDialogue}"
        }

        val mergedTags = combineTags(existing.tags, incoming.tags)

        return existing.copy(
            identity = mergedIdentity,
            personality = mergedPersonality,
            background = mergedBackground,
            speakingStyle = mergedSpeakingStyle,
            goals = mergedGoals,
            relationships = mergedRelationships,
            knowledge = mergedKnowledge,
            constraints = mergedConstraints,
            behaviorRules = mergedBehaviorRules,
            greeting = mergedGreeting,
            exampleDialogue = mergedExampleDialogue,
            tags = mergedTags,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * 智能融合世界观设定
     */
    fun mergeScenarios(existing: RoleplayScenario, incoming: RoleplayScenario): RoleplayScenario {
        val mergedWorldview = when {
            existing.worldview.isBlank() -> incoming.worldview
            incoming.worldview.isBlank() -> existing.worldview
            else -> "${existing.worldview}\n\n【扩展世界观】\n${incoming.worldview}"
        }

        val mergedRules = when {
            existing.rules.isBlank() -> incoming.rules
            incoming.rules.isBlank() -> existing.rules
            else -> "${existing.rules}\n${incoming.rules}"
        }

        val mergedEnvironment = incoming.environment.ifBlank { existing.environment }
        val mergedPremise = incoming.premise.ifBlank { existing.premise }
        val mergedConflict = incoming.conflict.ifBlank { existing.conflict }
        val mergedPlotGoal = incoming.plotGoal.ifBlank { existing.plotGoal }
        val mergedAtmosphere = incoming.atmosphere.ifBlank { existing.atmosphere }
        val mergedTags = combineTags(existing.tags, incoming.tags)

        return existing.copy(
            worldview = mergedWorldview,
            time = incoming.time.ifBlank { existing.time },
            location = incoming.location.ifBlank { existing.location },
            environment = mergedEnvironment,
            premise = mergedPremise,
            rules = mergedRules,
            relationshipState = incoming.relationshipState.ifBlank { existing.relationshipState },
            conflict = mergedConflict,
            plotGoal = mergedPlotGoal,
            atmosphere = mergedAtmosphere,
            narrativePerspective = incoming.narrativePerspective.ifBlank { existing.narrativePerspective },
            outputFormat = incoming.outputFormat.ifBlank { existing.outputFormat },
            contentRestrictions = incoming.contentRestrictions.ifBlank { existing.contentRestrictions },
            openingPrompt = incoming.openingPrompt.ifBlank { existing.openingPrompt },
            tags = mergedTags,
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun combineTags(tags1: String?, tags2: String?): String? {
        val set1 = tags1?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        val set2 = tags2?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        val union = (set1 + set2).toList()
        return if (union.isEmpty()) null else union.joinToString(",")
    }
}
