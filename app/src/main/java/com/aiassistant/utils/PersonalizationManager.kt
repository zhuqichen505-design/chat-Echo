package com.aiassistant.utils

import android.content.Context
import androidx.core.content.edit

data class PersonalizationSettings(
    val globalSystemPrompt: String = "",
    val globalRoleplayPrompt: String = "",
    val enabled: Boolean = true,
    val aboutUser: String = "",
    val responseStyle: String = "",
    val preferences: String = "",
    val avoid: String = "",
    val autoMemoryEnabled: Boolean = true,
    val thinkingCapsuleTemplate: String = "{model} {status} {time} {tokens}",
    val fontSizeScale: Float = 1.0f,
    val chatFontSize: Int = 16,
    val appTheme: String = "system",
    val autoNameEnabled: Boolean = true,
    val autoNameApiConfigId: Long = 0L,
    val autoNameModel: String = "",
    val autoNamePrompt: String = "",
    val enableThinkingTranslation: Boolean = true,
    val thinkingTranslationApiConfigId: Long = 0L,
    val thinkingTranslationModel: String = "",
    val connectingTextTemplate: String = "{model} 正在连接中...",
    val thinkingTextTemplate: String = "{model} 正在思考中...",
    val auxiliaryMemoryEnabled: Boolean = false,
    val auxiliaryMemoryApiConfigId: Long = 0L,
    val auxiliaryMemoryModel: String = "",
    val auxiliaryMemoryPrompt: String = "",
    val autoTimelineEnabled: Boolean = true,
    val autoTimelineNoticeEnabled: Boolean = true,
    val autoCompressionTierEnabled: Boolean = false,
    val autoCompressionThresholdL2: Float = 0.75f,
    val autoCompressionThresholdL3: Float = 0.85f,
    val autoCompressionThresholdL4: Float = 0.95f
)

class PersonalizationManager(private val context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "personalization_settings",
        Context.MODE_PRIVATE
    )
    private val legacyPrefs = context.applicationContext.getSharedPreferences(
        "app_settings",
        Context.MODE_PRIVATE
    )

    fun getRetryPolicy(): com.aiassistant.domain.model.RetryPolicy = com.aiassistant.domain.model.RetryPolicy(
        com.aiassistant.domain.model.RetryErrorType.entries.associateWith { type ->
            com.aiassistant.domain.model.RetryRule(
                prefs.getBoolean("retry_${type.name}_enabled", type.defaultEnabled),
                prefs.getInt("retry_${type.name}_count", 3).coerceIn(0, com.aiassistant.domain.model.RetryPolicy.MAX_RETRIES)
            )
        },
        enabled = isModelRetryEnabled()
    )

    fun isModelRetryEnabled(): Boolean = prefs.getBoolean("model_retry_enabled", true)

    fun setModelRetryEnabled(enabled: Boolean) {
        prefs.edit(commit = true) { putBoolean("model_retry_enabled", enabled) }
    }

    fun saveRetryRule(type: com.aiassistant.domain.model.RetryErrorType, rule: com.aiassistant.domain.model.RetryRule) {
        prefs.edit(commit = true) {
            putBoolean("retry_${type.name}_enabled", rule.enabled)
            putInt("retry_${type.name}_count", rule.maxRetries.coerceIn(0, com.aiassistant.domain.model.RetryPolicy.MAX_RETRIES))
        }
    }

    fun isBackupKeyFallbackEnabled(): Boolean = isModelRetryEnabled() && prefs.getBoolean("retry_backup_key_enabled", true)

    fun setBackupKeyFallbackEnabled(enabled: Boolean) {
        prefs.edit(commit = true) { putBoolean("retry_backup_key_enabled", enabled) }
    }

    fun getSettings(): PersonalizationSettings {
        val legacyGlobalPrompt = legacyPrefs.getString("global_system_prompt", "").orEmpty()
        val globalPrompt = prefs.getString(KEY_GLOBAL_PROMPT, null) ?: legacyGlobalPrompt
        val globalRpPrompt = prefs.getString(KEY_GLOBAL_ROLEPLAY_PROMPT, "").orEmpty()

        return PersonalizationSettings(
            globalSystemPrompt = globalPrompt,
            globalRoleplayPrompt = globalRpPrompt,
            enabled = prefs.getBoolean(KEY_ENABLED, true),
            aboutUser = prefs.getString(KEY_ABOUT_USER, "").orEmpty(),
            responseStyle = prefs.getString(KEY_RESPONSE_STYLE, "").orEmpty(),
            preferences = prefs.getString(KEY_PREFERENCES, "").orEmpty(),
            avoid = prefs.getString(KEY_AVOID, "").orEmpty(),
            autoMemoryEnabled = prefs.getBoolean(KEY_AUTO_MEMORY_ENABLED, true),
            thinkingCapsuleTemplate = prefs.getString(KEY_THINKING_TEMPLATE, "{model} {status} {time} {tokens}").orEmpty().ifBlank { "{model} {status} {time} {tokens}" },
            fontSizeScale = prefs.getFloat(KEY_FONT_SIZE_SCALE, 1.0f),
            chatFontSize = prefs.getInt(KEY_CHAT_FONT_SIZE, 16),
            appTheme = prefs.getString(KEY_APP_THEME, "system").orEmpty().ifBlank { "system" },
            autoNameEnabled = prefs.getBoolean(KEY_AUTO_NAME_ENABLED, true),
            autoNameApiConfigId = prefs.getLong(KEY_AUTO_NAME_API_CONFIG_ID, 0L),
            autoNameModel = prefs.getString(KEY_AUTO_NAME_MODEL, "").orEmpty(),
            autoNamePrompt = prefs.getString(KEY_AUTO_NAME_PROMPT, "").orEmpty(),
            enableThinkingTranslation = prefs.getBoolean(KEY_ENABLE_THINKING_TRANSLATION, true),
            thinkingTranslationApiConfigId = prefs.getLong(KEY_THINKING_TRANSLATION_API_CONFIG_ID, 0L),
            thinkingTranslationModel = prefs.getString(KEY_THINKING_TRANSLATION_MODEL, "").orEmpty(),
            connectingTextTemplate = prefs.getString(KEY_CONNECTING_TEXT_TEMPLATE, "{model} 正在连接中...").orEmpty().ifBlank { "{model} 正在连接中..." },
            thinkingTextTemplate = prefs.getString(KEY_THINKING_TEXT_TEMPLATE, "{model} 正在思考中...").orEmpty().ifBlank { "{model} 正在思考中..." },
            auxiliaryMemoryEnabled = prefs.getBoolean(KEY_AUXILIARY_MEMORY_ENABLED, false),
            auxiliaryMemoryApiConfigId = prefs.getLong(KEY_AUXILIARY_MEMORY_API_CONFIG_ID, 0L),
            auxiliaryMemoryModel = prefs.getString(KEY_AUXILIARY_MEMORY_MODEL, "").orEmpty(),
            auxiliaryMemoryPrompt = prefs.getString(KEY_AUXILIARY_MEMORY_PROMPT, DEFAULT_AUXILIARY_MEMORY_PROMPT).orEmpty().ifBlank { DEFAULT_AUXILIARY_MEMORY_PROMPT },
            autoTimelineEnabled = prefs.getBoolean(KEY_AUTO_TIMELINE_ENABLED, true),
            autoTimelineNoticeEnabled = prefs.getBoolean(KEY_AUTO_TIMELINE_NOTICE_ENABLED, true),
            autoCompressionTierEnabled = prefs.getBoolean(KEY_AUTO_COMPRESSION_TIER_ENABLED, false),
            autoCompressionThresholdL2 = prefs.getFloat(KEY_AUTO_COMPRESSION_THRESHOLD_L2, 0.75f),
            autoCompressionThresholdL3 = prefs.getFloat(KEY_AUTO_COMPRESSION_THRESHOLD_L3, 0.85f),
            autoCompressionThresholdL4 = prefs.getFloat(KEY_AUTO_COMPRESSION_THRESHOLD_L4, 0.95f)
        )
    }

    fun saveSettings(settings: PersonalizationSettings): Boolean {
        legacyPrefs.edit().putString("global_system_prompt", settings.globalSystemPrompt).apply()
        return prefs.edit()
            .putString(KEY_GLOBAL_PROMPT, settings.globalSystemPrompt)
            .putString(KEY_GLOBAL_ROLEPLAY_PROMPT, settings.globalRoleplayPrompt)
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putString(KEY_ABOUT_USER, settings.aboutUser)
            .putString(KEY_RESPONSE_STYLE, settings.responseStyle)
            .putString(KEY_PREFERENCES, settings.preferences)
            .putString(KEY_AVOID, settings.avoid)
            .putBoolean(KEY_AUTO_MEMORY_ENABLED, settings.autoMemoryEnabled)
            .putString(KEY_THINKING_TEMPLATE, settings.thinkingCapsuleTemplate)
            .putFloat(KEY_FONT_SIZE_SCALE, settings.fontSizeScale)
            .putInt(KEY_CHAT_FONT_SIZE, settings.chatFontSize)
            .putString(KEY_APP_THEME, settings.appTheme)
            .putBoolean(KEY_AUTO_NAME_ENABLED, settings.autoNameEnabled)
            .putLong(KEY_AUTO_NAME_API_CONFIG_ID, settings.autoNameApiConfigId)
            .putString(KEY_AUTO_NAME_MODEL, settings.autoNameModel)
            .putString(KEY_AUTO_NAME_PROMPT, settings.autoNamePrompt)
            .putBoolean(KEY_ENABLE_THINKING_TRANSLATION, settings.enableThinkingTranslation)
            .putLong(KEY_THINKING_TRANSLATION_API_CONFIG_ID, settings.thinkingTranslationApiConfigId)
            .putString(KEY_THINKING_TRANSLATION_MODEL, settings.thinkingTranslationModel)
            .putString(KEY_CONNECTING_TEXT_TEMPLATE, settings.connectingTextTemplate)
            .putString(KEY_THINKING_TEXT_TEMPLATE, settings.thinkingTextTemplate)
            .putBoolean(KEY_AUXILIARY_MEMORY_ENABLED, settings.auxiliaryMemoryEnabled)
            .putLong(KEY_AUXILIARY_MEMORY_API_CONFIG_ID, settings.auxiliaryMemoryApiConfigId)
            .putString(KEY_AUXILIARY_MEMORY_MODEL, settings.auxiliaryMemoryModel)
            .putString(KEY_AUXILIARY_MEMORY_PROMPT, settings.auxiliaryMemoryPrompt)
            .putBoolean(KEY_AUTO_TIMELINE_ENABLED, settings.autoTimelineEnabled)
            .putBoolean(KEY_AUTO_TIMELINE_NOTICE_ENABLED, settings.autoTimelineNoticeEnabled)
            .putBoolean(KEY_AUTO_COMPRESSION_TIER_ENABLED, settings.autoCompressionTierEnabled)
            .putFloat(KEY_AUTO_COMPRESSION_THRESHOLD_L2, settings.autoCompressionThresholdL2)
            .putFloat(KEY_AUTO_COMPRESSION_THRESHOLD_L3, settings.autoCompressionThresholdL3)
            .putFloat(KEY_AUTO_COMPRESSION_THRESHOLD_L4, settings.autoCompressionThresholdL4)
            .commit()
    }

    fun buildPrompt(): String? {
        val settings = getSettings()
        if (!settings.enabled) return null

        val sections = listOfNotNull(
            settings.aboutUser.trim().takeIf { it.isNotBlank() }?.let {
                "用户自定义偏好：\n$it"
            },
            settings.responseStyle.trim().takeIf { it.isNotBlank() }?.let {
                "回答风格偏好：\n$it"
            },
            settings.preferences.trim().takeIf { it.isNotBlank() }?.let {
                "长期偏好和习惯：\n$it"
            },
            settings.avoid.trim().takeIf { it.isNotBlank() }?.let {
                "尽量避免：\n$it"
            }
        )

        if (sections.isEmpty()) return null

        return """
            以下是用户在应用内设置的个性化偏好。它们是长期偏好，不是本轮对话的新任务；当它们与用户当前消息或当前对话系统提示词冲突时，优先遵守更具体、更新的要求。

            ${sections.joinToString("\n\n")}
        """.trimIndent()
    }

    fun getApiConfigOrder(): List<Long> {
        val raw = prefs.getString(KEY_API_CONFIG_ORDER, null) ?: return emptyList()
        return raw.split(",")
            .mapNotNull { it.trim().toLongOrNull() }
    }

    fun saveApiConfigOrder(order: List<Long>): Boolean {
        val raw = order.joinToString(",")
        return prefs.edit().putString(KEY_API_CONFIG_ORDER, raw).commit()
    }

    fun isContextFallbackPermanentlyIgnored(conversationId: Long): Boolean {
        return prefs.getBoolean(KEY_IGNORE_CONTEXT_FALLBACK_PREFIX + conversationId, false)
    }

    fun setContextFallbackPermanentlyIgnored(conversationId: Long, ignored: Boolean): Boolean {
        return prefs.edit().putBoolean(KEY_IGNORE_CONTEXT_FALLBACK_PREFIX + conversationId, ignored).commit()
    }

    companion object {
        private const val KEY_GLOBAL_PROMPT = "global_prompt"
        private const val KEY_GLOBAL_ROLEPLAY_PROMPT = "global_roleplay_prompt"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_ABOUT_USER = "about_user"
        private const val KEY_RESPONSE_STYLE = "response_style"
        private const val KEY_PREFERENCES = "preferences"
        private const val KEY_AVOID = "avoid"
        private const val KEY_AUTO_MEMORY_ENABLED = "auto_memory_enabled"
        private const val KEY_THINKING_TEMPLATE = "thinking_template"
        private const val KEY_FONT_SIZE_SCALE = "font_size_scale"
        private const val KEY_CHAT_FONT_SIZE = "chat_font_size"
        private const val KEY_APP_THEME = "app_theme"
        private const val KEY_AUTO_NAME_ENABLED = "auto_name_enabled"
        private const val KEY_AUTO_NAME_API_CONFIG_ID = "auto_name_api_config_id"
        private const val KEY_AUTO_NAME_MODEL = "auto_name_model"
        private const val KEY_AUTO_NAME_PROMPT = "auto_name_prompt"
        private const val KEY_ENABLE_THINKING_TRANSLATION = "enable_thinking_translation"
        private const val KEY_THINKING_TRANSLATION_API_CONFIG_ID = "thinking_translation_api_config_id"
        private const val KEY_THINKING_TRANSLATION_MODEL = "thinking_translation_model"
        private const val KEY_CONNECTING_TEXT_TEMPLATE = "connecting_text_template"
        private const val KEY_THINKING_TEXT_TEMPLATE = "thinking_text_template"
        private const val KEY_AUXILIARY_MEMORY_ENABLED = "auxiliary_memory_enabled"
        private const val KEY_AUXILIARY_MEMORY_API_CONFIG_ID = "auxiliary_memory_api_config_id"
        private const val KEY_AUXILIARY_MEMORY_MODEL = "auxiliary_memory_model"
        private const val KEY_AUXILIARY_MEMORY_PROMPT = "auxiliary_memory_prompt"
        private const val KEY_AUTO_TIMELINE_ENABLED = "auto_timeline_enabled"
        private const val KEY_AUTO_TIMELINE_NOTICE_ENABLED = "auto_timeline_notice_enabled"
        private const val KEY_AUTO_COMPRESSION_TIER_ENABLED = "auto_compression_tier_enabled"
        private const val KEY_AUTO_COMPRESSION_THRESHOLD_L2 = "auto_compression_threshold_l2"
        private const val KEY_AUTO_COMPRESSION_THRESHOLD_L3 = "auto_compression_threshold_l3"
        private const val KEY_AUTO_COMPRESSION_THRESHOLD_L4 = "auto_compression_threshold_l4"
        private const val KEY_API_CONFIG_ORDER = "api_config_order"
        private const val KEY_IGNORE_CONTEXT_FALLBACK_PREFIX = "ignore_context_fallback_conv_"

        const val DEFAULT_AUXILIARY_MEMORY_PROMPT = "你是一个专业的记忆与设定提炼助手。请阅读以下对话内容，判断是否包含值得长期记住的人物画像、长期偏好、重要事实、剧情设定或行为约束（如称呼要求、绝对禁忌、输出规范等）。\n" +
            "【沉浸感最高准则（铁律）】：\n" +
            "1. 严禁在提炼的事实中出现“用户”、“AI”、“模型”、“助手”、“大模型”、“系统”、“机器人”等出戏的元技术词汇！\n" +
            "2. 若涉及角色扮演、称谓关系或互动人设（如将对方当做哥哥、朋友、恋人等），必须以自然沉浸的角色关系或互动规范表述（例如提炼为“角色关系：视对方为心爱的哥哥”、“互动设定：以哥哥身份进行关怀交流”，绝对不可输出“用户把AI当成...”）！\n" +
            "3. 若判定值得记忆，请直接输出一条主谓宾结构完整、表意清晰确凿的陈述事实（30~80字，必须保留完整的约束条件与限定词，严禁中途截断，严禁输出任何'根据分析'等说明或标点废话）；若只是客套、单次任务、临时疑问或瞬态动作，请只输出'IGNORE'。"
    }
}
