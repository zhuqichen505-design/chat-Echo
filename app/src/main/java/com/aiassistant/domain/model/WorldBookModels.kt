package com.aiassistant.domain.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 世界书 (Lorebook) - 组织与管理特定世界观、体系或主题的词条集合
 */
@Entity(
    tableName = "world_books",
    indices = [
        Index(value = ["isEnabled"]),
        Index(value = ["createdAt"])
    ]
)
data class WorldBook(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val isEnabled: Boolean = true,
    val tags: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 世界书词条 - 定义具体的名词、设定、地理、人物或规则
 */
@Entity(
    tableName = "world_book_entries",
    indices = [
        Index(value = ["bookId"]),
        Index(value = ["isEnabled"]),
        Index(value = ["isConstant"]),
        Index(value = ["priority"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = WorldBook::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class WorldBookEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: Long,
    val name: String,                    // 词条名称（如：黄金树、反物质引擎）
    val keys: String,                    // 新条目保存 JSON 数组以保留短语，兼容旧分隔格式
    val content: String,                 // 词条设定内容
    val isEnabled: Boolean = true,       // 是否启用此词条
    val isConstant: Boolean = false,     // 是否常驻激活（即使未匹配到关键词也会注入）
    val priority: Int = 100,             // 同类条目的预算保留顺序，数值越高越优先；不改变消息权限
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * 获取解析后的关键词列表（小写去重）
     */
    fun getKeyList(): List<String> {
        if (keys.isBlank()) return emptyList()
        // Imported keys are stored as a JSON array so phrases and punctuation stay intact.
        // Legacy manually entered keys retain their original delimiter semantics.
        val encodedKeys = keys.trimStart()
        val arrayStart = encodedKeys.removePrefix("[").trimStart()
        val importedValues = if (encodedKeys.startsWith("[") && (arrayStart.startsWith('"') || arrayStart == "]")) {
            runCatching {
                com.google.gson.JsonParser.parseString(keys).asJsonArray.map {
                    require(it.isJsonPrimitive && it.asJsonPrimitive.isString)
                    it.asString
                }
            }.getOrNull()
        } else null
        val values = importedValues ?: keys.split(',', '，', '、', ';', '；', '\n', '\r', ' ')
        return values.map { it.trim().lowercase(java.util.Locale.ROOT) }
            .filter { it.isNotBlank() }
            .distinct()
    }

    /**
     * 检查给定文本是否命中了此词条的任意关键词
     */
    fun matchesText(text: String): Boolean {
        if (!isEnabled) return false
        if (isConstant) return true
        val lowerText = text.lowercase(java.util.Locale.ROOT)
        return getKeyList().any { key -> lowerText.contains(key) }
    }
}
