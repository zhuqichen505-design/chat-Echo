package com.aiassistant

import com.aiassistant.data.repository.AiRepository
import com.aiassistant.domain.model.ModelCapabilityEngine
import com.aiassistant.tools.device.HealthDataManager
import org.junit.Assert.*
import org.junit.Test

class V1920FeaturesTest {

    @Test
    fun testDeepSeekV4FlashContextWindowIs1M() {
        val cap = ModelCapabilityEngine.resolveCapabilities("deepseekv4flash")
        assertEquals("deepseekv4flash 应识别为 1M 上下文", 1_000_000, cap.contextWindowTokens)
        assertEquals("deepseekv4flash 标签应为 1M", "1M", cap.contextWindowLabel)

        // 其它 flash/long 架构测试
        val capFlash = ModelCapabilityEngine.resolveCapabilities("deepseek-v4-flash-instruct")
        assertEquals(1_000_000, capFlash.contextWindowTokens)
        assertEquals("1M", capFlash.contextWindowLabel)

        // DeepSeek 标准模型默认为 128K
        val capStandard = ModelCapabilityEngine.resolveCapabilities("deepseek-chat")
        assertEquals(128_000, capStandard.contextWindowTokens)
        assertEquals("128K", capStandard.contextWindowLabel)
    }

    @Test
    fun testUnknownModelDoesNotFabricateLabel() {
        // 未包含确证架构信息的第三方/自定义模型，严禁虚构伪造标签展示给用户
        val capUnknown = ModelCapabilityEngine.resolveCapabilities("some-unknown-private-model")
        assertEquals("未知模型标签必须为空，禁止向用户编造虚假信息", "", capUnknown.contextWindowLabel)
        assertEquals("内部安全计算预算使用默认值", 256_000, capUnknown.contextWindowTokens)

        // 显式带有规格标记的自定义模型应正确提取
        val capCustom128k = ModelCapabilityEngine.resolveCapabilities("custom-ai-128k")
        assertEquals(128_000, capCustom128k.contextWindowTokens)
        assertEquals("128K", capCustom128k.contextWindowLabel)

        val capCustom32k = ModelCapabilityEngine.resolveCapabilities("enterprise-agent-32k-preview")
        assertEquals(32_000, capCustom32k.contextWindowTokens)
        assertEquals("32K", capCustom32k.contextWindowLabel)
    }

    @Test
    fun testMultipleApiKeyParsing() {
        // 1. 换行拆分多 Key
        val multilineKeys = "sk-key1\nsk-key2\nsk-key3"
        val parsedMultiline = AiRepository.parseApiKeys(multilineKeys)
        assertEquals(3, parsedMultiline.size)
        assertEquals("sk-key1", parsedMultiline[0])
        assertEquals("sk-key2", parsedMultiline[1])
        assertEquals("sk-key3", parsedMultiline[2])

        // 2. 逗号与分号混合拆分
        val mixedKeys = "sk-alpha, sk-beta; sk-gamma,   sk-delta"
        val parsedMixed = AiRepository.parseApiKeys(mixedKeys)
        assertEquals(4, parsedMixed.size)
        assertEquals("sk-alpha", parsedMixed[0])
        assertEquals("sk-beta", parsedMixed[1])
        assertEquals("sk-gamma", parsedMixed[2])
        assertEquals("sk-delta", parsedMixed[3])

        // 3. 空白与单 Key
        val singleKey = "   sk-my-secret-key   "
        val parsedSingle = AiRepository.parseApiKeys(singleKey)
        assertEquals(1, parsedSingle.size)
        assertEquals("sk-my-secret-key", parsedSingle[0])

        val blankKey = "   \n\n  "
        val parsedBlank = AiRepository.parseApiKeys(blankKey)
        assertTrue(parsedBlank.isEmpty())
    }

    @Test
    fun testHuaweiHealthHelperMethods() {
        // 1. 时间格式化：0 时返回暂未同步
        assertEquals("暂未同步", HealthDataManager.formatUpdateTime(0L))

        // 2. 刚刚更新
        val now = System.currentTimeMillis()
        assertEquals("刚刚", HealthDataManager.formatUpdateTime(now - 10_000L))

        // 3. 5分钟前
        assertEquals("5分钟前", HealthDataManager.formatUpdateTime(now - 5 * 60 * 1000L))

        // 4. 包名列表覆盖
        assertTrue(HealthDataManager.HUAWEI_HEALTH_PACKAGES.contains("com.huawei.health"))
        assertTrue(HealthDataManager.HUAWEI_HEALTH_PACKAGES.contains("com.hihonor.health"))
        assertTrue(HealthDataManager.HUAWEI_HEALTH_PACKAGES.contains("com.huawei.bone"))
    }

    @Test
    fun testThinkingGearsAvailability() {
        // 2026-09：过时 o 系列走默认统一方案；Kimi 官方三档
        val defaultCap = ModelCapabilityEngine.resolveCapabilities("some-future-model")
        assertTrue(defaultCap.supportsThinking)
        assertEquals(emptyList<String>(), defaultCap.supportedThinkingGears)

        val kimiCap = ModelCapabilityEngine.resolveCapabilities("kimi-k3")
        assertEquals(listOf("low", "high", "max"), kimiCap.supportedThinkingGears)

        // Claude 3.7：4 档
        val claudeCap = ModelCapabilityEngine.resolveCapabilities("claude-3-7-sonnet")
        assertTrue(claudeCap.supportsThinking)
        assertEquals(emptyList<String>(), claudeCap.supportedThinkingGears)

        // DeepSeek 推理模型：支持档位调节
        val dsCap = ModelCapabilityEngine.resolveCapabilities("deepseek-reasoner")
        assertTrue(dsCap.supportsThinking)
        assertEquals(emptyList<String>(), dsCap.supportedThinkingGears)

        // QwQ 推理模型：支持档位调节
        val qwqCap = ModelCapabilityEngine.resolveCapabilities("qwq-32b-preview")
        assertTrue(qwqCap.supportsThinking)
        assertEquals(emptyList<String>(), qwqCap.supportedThinkingGears)
    }
}
