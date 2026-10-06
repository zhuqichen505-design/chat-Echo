package com.aiassistant

import com.aiassistant.data.repository.AiRepository
import com.aiassistant.domain.model.ApiConfig
import com.aiassistant.domain.model.NamedApiKey
import com.aiassistant.ui.screens.settings.CurrentVersionUserUpdates
import com.aiassistant.ui.screens.settings.V225UserUpdates
import org.junit.Assert.*
import org.junit.Test

class V225FeaturesTest {

    @Test
    fun testV225UserUpdatesCompleteness() {
        assertEquals("V2.2.5 用户更新日志数量应为 6 项", 6, V225UserUpdates.size)
        assertTrue("V225UserUpdates 必须不为空", V225UserUpdates.isNotEmpty())
        assertTrue("必须包含 API 配置独立启用总开关更新", V225UserUpdates.any { it.contains("API 配置独立启用") })
        assertTrue("必须包含停用 API 自动从选择列表移除更新", V225UserUpdates.any { it.contains("从选择列表中移除") || it.contains("从对话选择列表中移除") })
        assertTrue("必须包含独立 API Key 精细化启用开关更新", V225UserUpdates.any { it.contains("独立 API Key") && it.contains("启用开关") })
        assertTrue("必须包含活跃 Key 智能过滤与故障转移更新", V225UserUpdates.any { it.contains("活跃 Key") })
        assertTrue("必须包含默认 API 停用后智能回退更新", V225UserUpdates.any { it.contains("默认 API 停用") || it.contains("智能回退") })
        assertTrue("必须包含上下文压缩与预算超限安全保护更新", V225UserUpdates.any { it.contains("上下文压缩") || it.contains("超限") })
    }

    @Test
    fun testNamedApiKeyEnableDisableSerialization() {
        val keys = listOf(
            NamedApiKey(name = "主力 Key", key = "sk-main-12345", isEnabled = true),
            NamedApiKey(name = "备用 Key", key = "sk-backup-67890", isEnabled = false),
            NamedApiKey(name = "", key = "sk-raw-active", isEnabled = true),
            NamedApiKey(name = "", key = "sk-raw-disabled", isEnabled = false)
        )

        // 格式化为字符串
        val formatted = AiRepository.formatNamedApiKeys(keys)
        assertTrue("启用状态的 NamedKey 不应带有 [已禁用] 标签", formatted.contains("[主力 Key] sk-main-12345"))
        assertTrue("停用状态的 NamedKey 必须带有 [已禁用] 标签", formatted.contains("[已禁用] [备用 Key] sk-backup-67890"))
        assertTrue("无备注启用的 Key 应保持原样", formatted.contains("sk-raw-active"))
        assertTrue("无备注停用的 Key 应带有 [已禁用] 标签", formatted.contains("[已禁用] sk-raw-disabled"))

        // 反序列化解析
        val parsed = AiRepository.parseNamedApiKeys(formatted)
        assertEquals(4, parsed.size)
        assertEquals("主力 Key", parsed[0].name)
        assertEquals("sk-main-12345", parsed[0].key)
        assertTrue("第一个 Key 应该处于启用状态", parsed[0].isEnabled)

        assertEquals("备用 Key", parsed[1].name)
        assertEquals("sk-backup-67890", parsed[1].key)
        assertFalse("第二个 Key 应该处于停用状态", parsed[1].isEnabled)

        assertEquals("", parsed[2].name)
        assertEquals("sk-raw-active", parsed[2].key)
        assertTrue("第三个 Key 应该处于启用状态", parsed[2].isEnabled)

        assertEquals("", parsed[3].name)
        assertEquals("sk-raw-disabled", parsed[3].key)
        assertFalse("第四个 Key 应该处于停用状态", parsed[3].isEnabled)
    }

    @Test
    fun testParseNamedApiKeyPrefixVariations() {
        val multiFormat = """
            [off] [测试1] sk-disabled-off
            [disabled] [测试2] sk-disabled-en
            [禁用] sk-disabled-cn
            [已禁用] [测试3] sk-disabled-cn2
            [正常] sk-active-normal
        """.trimIndent()

        val parsed = AiRepository.parseNamedApiKeys(multiFormat)
        assertEquals(5, parsed.size)
        assertFalse("off 前缀应识别为停用", parsed[0].isEnabled)
        assertEquals("测试1", parsed[0].name)
        assertEquals("sk-disabled-off", parsed[0].key)

        assertFalse("disabled 前缀应识别为停用", parsed[1].isEnabled)
        assertEquals("测试2", parsed[1].name)

        assertFalse("禁用 前缀应识别为停用", parsed[2].isEnabled)
        assertEquals("sk-disabled-cn", parsed[2].key)

        assertFalse("已禁用 前缀应识别为停用", parsed[3].isEnabled)
        assertEquals("测试3", parsed[3].name)

        assertTrue("常规前缀应保持启用", parsed[4].isEnabled)
        assertEquals("sk-active-normal", parsed[4].key)
    }

    @Test
    fun testParseApiKeysExcludesDisabledKeys() {
        val mixedKeyString = """
            [主力] sk-enabled-1
            [已禁用] [备用] sk-disabled-2
            sk-enabled-3
            [已禁用] sk-disabled-4
        """.trimIndent()

        val activeKeys = AiRepository.parseApiKeys(mixedKeyString)
        assertEquals("仅应返回处于启用状态的 2 个 Key", 2, activeKeys.size)
        assertEquals("sk-enabled-1", activeKeys[0])
        assertEquals("sk-enabled-3", activeKeys[1])
        assertFalse("停用的 Key 绝对不能进入活跃请求列表", activeKeys.contains("sk-disabled-2"))
        assertFalse("停用的 Key 绝对不能进入活跃请求列表", activeKeys.contains("sk-disabled-4"))
    }

    @Test
    fun testApiConfigDefaultIsEnabled() {
        val defaultConfig = ApiConfig(
            id = 1,
            name = "Test Provider",
            provider = "OpenAI",
            baseUrl = "https://api.openai.com",
            apiKey = "sk-123456",
            modelName = "gpt-4o"
        )
        assertTrue("新创建或默认加载的 ApiConfig isEnabled 应默认为 true", defaultConfig.isEnabled)

        val disabledConfig = defaultConfig.copy(isEnabled = false)
        assertFalse("复制后修改 isEnabled 应能正确反映停用状态", disabledConfig.isEnabled)
    }
}
