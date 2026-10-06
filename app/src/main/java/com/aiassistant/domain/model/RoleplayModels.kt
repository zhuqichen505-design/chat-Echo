package com.aiassistant.domain.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 角色卡 - 定义角色的完整设定
 */
@Entity(
    tableName = "character_profiles",
    indices = [
        Index(value = ["isFavorite"]),
        Index(value = ["isDefault"]),
        Index(value = ["createdAt"])
    ]
)
data class CharacterProfile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val avatarUri: String? = null,
    val identity: String = "",           // 身份/职业
    val personality: String = "",        // 性格特征
    val background: String = "",         // 背景故事
    val speakingStyle: String = "",      // 说话方式和语言风格
    val goals: String = "",              // 目标、动机和当前欲望
    val relationships: String = "",      // 与用户或其他角色的关系
    val knowledge: String = "",          // 已知信息和知识边界
    val constraints: String = "",        // 禁止违背的设定
    val behaviorRules: String = "",      // 角色行为约束
    val greeting: String = "",           // 初始问候语
    val exampleDialogue: String = "",    // 示例对话
    val tags: String? = null,            // 标签，逗号分隔
    val isFavorite: Boolean = false,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 场景卡 - 定义剧情场景的配置
 */
@Entity(
    tableName = "roleplay_scenarios",
    indices = [
        Index(value = ["isFavorite"]),
        Index(value = ["createdAt"])
    ]
)
data class RoleplayScenario(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val worldview: String = "",          // 世界观
    val time: String = "",               // 时间
    val location: String = "",           // 地点
    val environment: String = "",        // 环境描述
    val premise: String = "",            // 当前剧情前提
    val rules: String = "",              // 世界规则
    val relationshipState: String = "",  // 角色之间的关系状态
    val conflict: String = "",           // 当前冲突
    val plotGoal: String = "",           // 剧情目标
    val atmosphere: String = "",         // 叙事氛围
    val narrativePerspective: String = "", // 叙事视角
    val outputFormat: String = "",       // 输出格式
    val contentRestrictions: String = "", // 内容限制
    val openingPrompt: String = "",      // 开场提示
    val tags: String? = null,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 角色扮演会话 - 绑定角色、场景和对话
 */
@Entity(
    tableName = "roleplay_sessions",
    indices = [
        Index(value = ["characterId"]),
        Index(value = ["scenarioId"]),
        Index(value = ["conversationId"]),
        Index(value = ["createdAt"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = CharacterProfile::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = RoleplayScenario::class,
            parentColumns = ["id"],
            childColumns = ["scenarioId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = Conversation::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class RoleplaySession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val characterId: Long? = null,
    val scenarioId: Long? = null,
    val conversationId: Long,
    val narrativeMode: String = "character", // character/author/narrator/multi
    val currentPlotSummary: String = "",     // 当前剧情摘要
    val pinnedFacts: String? = null,         // 已固定的重要事实，JSON数组
    val lastVersionIndex: Int = 1,           // 当前版本索引
    val characterIds: String? = null,        // 多角色关联，JSON数组格式 "[1,2,3]" 或逗号分隔 "1,2,3"
    val customCharacterData: String? = null, // 当前故事专属的角色设定覆盖 (JSON List<CharacterProfile>)
    val customScenarioData: String? = null,  // 当前故事专属的世界观设定覆盖 (JSON RoleplayScenario)
    val enableExternalMemory: Boolean = false,
    val enableWorldBook: Boolean = false,
    val activeWorldBookIds: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun getEffectiveCharacterIds(): List<Long> {
        if (!characterIds.isNullOrBlank()) {
            try {
                val trimmed = characterIds.trim()
                if (trimmed.startsWith("[")) {
                    val array = com.google.gson.JsonParser.parseString(trimmed).asJsonArray
                    return array.mapNotNull { kotlin.runCatching { it.asLong }.getOrNull() }
                } else {
                    return trimmed.split(",").mapNotNull { it.trim().toLongOrNull() }
                }
            } catch (e: Exception) {
                // fallback
            }
        }
        return characterId?.let { listOf(it) } ?: emptyList()
    }

    fun getCustomizedCharacters(baseCharacters: List<CharacterProfile>): List<CharacterProfile> {
        if (customCharacterData.isNullOrBlank()) return baseCharacters
        return try {
            val type = com.google.gson.reflect.TypeToken.getParameterized(
                List::class.java,
                CharacterProfile::class.java
            ).type
            val customList: List<CharacterProfile> = com.google.gson.Gson().fromJson(customCharacterData, type) ?: return baseCharacters
            val customMap = customList.associateBy { it.id }
            val merged = baseCharacters.map { base ->
                customMap[base.id] ?: base
            }.toMutableList()
            customList.forEach { custom ->
                if (merged.none { it.id == custom.id && it.name == custom.name }) {
                    merged.add(custom)
                }
            }
            merged
        } catch (e: Exception) {
            baseCharacters
        }
    }

    fun getCustomizedScenario(baseScenario: RoleplayScenario?): RoleplayScenario? {
        if (customScenarioData.isNullOrBlank()) return baseScenario
        return try {
            com.google.gson.Gson().fromJson(customScenarioData, RoleplayScenario::class.java) ?: baseScenario
        } catch (e: Exception) {
            baseScenario
        }
    }
}

/**
 * 角色扮演记忆 - 存储角色扮演相关的记忆
 */
@Entity(
    tableName = "roleplay_memories",
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["memoryType"]),
        Index(value = ["createdAt"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = RoleplaySession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class RoleplayMemory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val memoryType: String = "fact",     // fact/summary/relationship/event
    val content: String,
    val sourceMessageId: Long? = null,
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 角色标签
 */
@Entity(
    tableName = "character_tags",
    indices = [
        Index(value = ["name"], unique = true)
    ]
)
data class CharacterTag(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 角色-标签关联
 */
@Entity(
    tableName = "character_tag_cross_ref",
    primaryKeys = ["characterId", "tagId"],
    indices = [
        Index(value = ["tagId"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = CharacterProfile::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CharacterTag::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class CharacterTagCrossRef(
    val characterId: Long,
    val tagId: Long
)

/**
 * 剧情提示模式
 */
enum class NarrativeMode(val value: String, val displayName: String, val description: String) {
    CHARACTER("character", "角色内指令", "模型以登场角色身份互动，沉浸式第一人称对话"),
    AUTHOR("author", "作者/导演指令", "用户控制剧情大纲与方向，模型负责铺陈与推进故事"),
    NARRATOR("narrator", "旁白模式", "纯客观环境描写与剧情旁白叙事，不代替用户做决定"),
    MULTI("multi", "多角色群像模式", "多角色共同参与，推演交互群像戏");

    companion object {
        fun fromValue(value: String): NarrativeMode {
            return entries.find { it.value == value } ?: CHARACTER
        }
    }
}

/**
 * 剧情操作类型
 */
enum class PlotAction(val value: String, val displayName: String, val description: String) {
    CONTINUE("continue", "继续剧情", "继续当前剧情发展"),
    REGENERATE("regenerate", "重生成", "重新生成上一段内容"),
    REWRITE("rewrite", "改写上一段", "改写上一段内容"),
    EXTEND("extend", "延长内容", "延长当前内容"),
    SHORTEN("shorten", "缩短内容", "缩短当前内容"),
    CHANGE_PERSPECTIVE("change_perspective", "改变叙事视角", "改变叙事视角"),
    CHANGE_TONE("change_tone", "改变语气", "改变叙事语气"),
    BRANCH("branch", "创建剧情分支", "从当前点创建分支"),
    ROLLBACK("rollback", "回到上一个版本", "回到上一个版本"),
    SUMMARY("summary", "生成剧情摘要", "生成当前剧情摘要"),
    BRANCH_CHOICES("branch_choices", "剧情走向选择", "提供3~4个不同发展方向与节奏供选择"),
    DIALOGUE_ONLY("dialogue_only", "只生成角色对白", "只生成角色对白"),
    NARRATION_ONLY("narration_only", "只生成旁白", "只生成旁白"),
    DIALOGUE_ACTION("dialogue_action", "生成对白加动作", "生成对白加动作描述"),
    CUSTOM("custom", "自定义指令", "用户自定义指令");

    companion object {
        fun fromValue(value: String): PlotAction {
            return entries.find { it.value == value } ?: CONTINUE
        }
    }
}
