package com.aiassistant

import com.aiassistant.domain.model.*
import com.aiassistant.data.repository.helpers.TokenEstimationHelper
import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class ReasoningControlsTest {
    @Test fun gatewayWireDoesNotGuessNewExtensionsAndRetainsBudgetThinking() {
        assertNull(ReasoningControls.wireThinkingType("deepseek-v4-pro", "openai", true))
        assertNull(ReasoningControls.wireThinkingType("deepseek-v4-pro", "openai", false))
        assertNull(ReasoningControls.wireChatEffort("gpt-5.2", "openai", false, "high"))
        assertEquals("enabled", ReasoningControls.wireThinkingType("claude-sonnet-4-6", "anthropic", true))
        assertEquals("adaptive", ReasoningControls.wireThinkingType("claude-opus-4-7", "anthropic", true))
        assertEquals("adaptive", ReasoningControls.wireThinkingType("claude-opus-4-8", "anthropic", true))
        assertNull(ReasoningControls.wireThinkingType("claude-sonnet-4-6", "anthropic", false))
        assertNull(ReasoningControls.wireChatEffort("claude-sonnet-4-6", "anthropic", true, "high"))
        val evidence = ReasoningCapabilityEvidence(supportsThinking = true, efforts = listOf("none", "low", "high"), thinkingType = "adaptive")
        assertEquals("adaptive", ReasoningControls.wireThinkingType("claude-sonnet-4-6", "anthropic", true, evidence))
        assertEquals("high", ReasoningControls.wireChatEffort("claude-sonnet-4-6", "anthropic", true, "high", evidence))
        assertEquals("none", ReasoningControls.wireChatEffort("gpt-5.2", "openai", false, "high", evidence))
    }
    @Test fun modelSpecificRangesAndCanonicalValues() {
        val cases = mapOf(
            "gpt-6-astra" to listOf("low", "medium", "high", "xhigh", "max"),
            "gpt-5.2-pro" to listOf("medium", "high", "xhigh"),
            "kimi-k3" to listOf("low", "high", "max"),
            "glm-5.2" to listOf("disabled", "high", "max"),
            "MiniMax-M3.1-Flash-Preview" to listOf("low", "medium", "high", "xhigh", "max"),
            "gemini-3.1-pro" to listOf("low", "medium", "high")
        )
        cases.forEach { (model, values) ->
            assertEquals(model, values, ReasoningControls.options(model).map { it.value })
            values.forEach { value ->
                assertEquals(value, ReasoningControls.selected(model, "openai", value != "disabled", value).value)
                if (value != "disabled") assertEquals(value, TokenEstimationHelper.normalizeThinkingEffort(value, "openai", model))
            }
        }
        assertEquals("Xhigh", ReasoningControls.selected("gpt-6-astra", "openai", true, "xhigh").label)
        assertEquals("max", ReasoningControls.selected("kimi-k3", "openai", true, "ultra").value)
        assertEquals("high", ReasoningControls.selected("deepseek-v4-pro", "openai", true, "medium").value)
        assertTrue(ReasoningControls.options("kimi-k2").none { it.value == "max" })
    }

    @Test fun cannotDisableAlwaysThinkingAndOmissionIsNotOff() {
        assertTrue(ReasoningControls.selected("gpt-6-astra", "openai", false, "high").enabled)
        assertEquals("none", ReasoningControls.selected("gpt-5.6", "openai", false, "high").value)
        assertEquals("disabled", ReasoningControls.selected("deepseek-v4-pro", "openai", false, "high").value)
        assertEquals("default", ReasoningControls.selected("unknown-private-model", "openai", true, "max").value)
    }

    @Test fun nativeClaudeBudgetAndEffortAreDifferentWireContracts() {
        val old = ReasoningControls.selected("claude-3-7-sonnet", "anthropic", true, "high")
        assertEquals("Budget_tokens=8192", old.label)
        assertEquals(8192, TokenEstimationHelper.thinkingBudgetForEffort(old.value, 1024))
        val selection = ReasoningControls.selected("claude-opus-4-6", "anthropic", true, "max")
        val request = AnthropicRequest("claude-opus-4-6", emptyList(), 16000,
            thinking = AnthropicThinking(type = "adaptive"), output_config = AnthropicOutputConfig(selection.value))
        val json = JsonParser.parseString(Gson().toJson(request)).asJsonObject
        assertEquals("max", json.getAsJsonObject("output_config").get("effort").asString)
        assertFalse(json.getAsJsonObject("thinking").has("budget_tokens"))
        assertEquals("default", ReasoningControls.options("claude-opus-4-6", "openai").single().value)
    }

    @Test fun defaultIsNotForcedAndHasNoIntensityArrow() {
        for (model in listOf("private-model", "MiniMax-M3", "kimi-k2.6", "claude-opus-4-6")) {
            assertFalse(model, ReasoningControls.policy(model).alwaysThinking)
            assertTrue(model, ReasoningControls.canToggle(model, "openai"))
            assertFalse(model, ReasoningControls.hasIntensityChoice(model, "openai"))
            assertEquals("默认", ReasoningControls.options(model).single().label)
            assertNull(ReasoningControls.wireEffort(model, "openai", true, "default"))
        }
    }

    @Test fun specificModelsHaveDifferentSwitchContracts() {
        assertEquals("none", ReasoningControls.selected("gpt-6-sol", "openai", false, "high").value)
        assertEquals("none", ReasoningControls.selected("gpt-6-luna", "openai", false, "max").value)
        assertTrue(ReasoningControls.policy("gpt-6.1-sol").alwaysThinking)
        assertFalse(ReasoningControls.policy("glm-5.2").alwaysThinking)
        assertEquals("disabled", ReasoningControls.thinkingType("glm-5.2", "openai", false))
        assertEquals("disabled", ReasoningControls.thinkingType("MiniMax-M3", "openai", false))
        assertTrue(ReasoningControls.policy("MiniMax-M2.7").alwaysThinking)
        assertTrue(ReasoningControls.policy("kimi-k2.7-code").alwaysThinking)
        assertEquals("disabled", ReasoningControls.thinkingType("kimi-k2.6", "openai", false))
        assertEquals(listOf("Low", "Medium", "High"), ReasoningControls.options("gemini-3.8-flash").map { it.label })
        assertEquals(listOf("low", "medium", "high", "max"), ReasoningControls.options("claude-sonnet-4-6", "anthropic").filter { it.enabled }.map { it.value })
    }

    @Test fun endpointEvidenceOverridesFallbackWithoutChangingWireCase() {
        val evidence = ReasoningCapabilityEvidence.parse(JsonParser.parseString("""{
            "id":"glm-5.3", "capabilities": {"always_thinking":false,
            "parameters":{"reasoning_effort":{"enum":["low","medium","high"]}}}
        }""").asJsonObject)!!
        assertFalse(ReasoningControls.policy("glm-5.3", evidence).alwaysThinking)
        assertEquals(listOf("disabled", "low", "medium", "high"), ReasoningControls.options("glm-5.3", "openai", evidence).map { it.value })
        val option = ReasoningControls.selected("glm-5.3", "openai", true, "Medium", evidence)
        assertEquals("Medium", option.label)
        assertEquals("medium", ReasoningControls.wireEffort("glm-5.3", "openai", true, option.value, evidence))
        val json = JsonParser.parseString(Gson().toJson(ChatCompletionRequest("glm-5.3", emptyList(), reasoning_effort = option.value))).asJsonObject
        assertEquals("medium", json["reasoning_effort"].asString)
    }

    @Test fun metadataParsingDoesNotInferForcedFromSupportOrDescriptions() {
        val info = ReasoningCapabilityEvidence.parse(JsonParser.parseString("""{
            "supports_reasoning":true,"description":"always thinking high max"
        }""").asJsonObject)!!
        assertNull(info.alwaysThinking)
        assertNull(info.efforts)
        assertFalse(ReasoningControls.policy("unknown", info).alwaysThinking)
        val unsupported = ReasoningCapabilityEvidence(false, true, listOf("high"))
        assertFalse(ReasoningControls.policy("kimi-k3", unsupported).alwaysThinking)
        assertFalse(ReasoningControls.canToggle("kimi-k3", "openai", unsupported))
        assertFalse(ReasoningControls.hasIntensityChoice("kimi-k3", "openai", unsupported))
        assertNull(ReasoningControls.wireEffort("kimi-k3", "openai", true, "high", unsupported))
        assertNull(ReasoningControls.thinkingType("kimi-k3", "openai", true, unsupported))
    }

    @Test fun catalogueIsScopedByEndpointAndProtocolAndCanReplaceStaleEvidence() {
        val model = "catalog-test-model"
        val info = ReasoningCapabilityEvidence(efforts = listOf("low", "xhigh"))
        ReasoningCapabilityCatalog.put("https://a.invalid", "openai", model, info)
        assertEquals(info, ReasoningCapabilityCatalog.get("https://a.invalid/v1/", "openai", model))
        assertNull(ReasoningCapabilityCatalog.get("https://b.invalid", "openai", model))
        assertNull(ReasoningCapabilityCatalog.get("https://a.invalid", "anthropic", model))
        ReasoningCapabilityCatalog.put("https://a.invalid", "openai", model, null)
        assertNull(ReasoningCapabilityCatalog.get("https://a.invalid", "openai", model))
    }

    @Test fun apiDefinedGearsArePreservedIncludingUnknownModelAndLevelObjects() {
        val evidence = ReasoningCapabilityEvidence.parse(JsonParser.parseString("""{
          "supported_reasoning_levels":[{"effort":"low"},{"effort":"medium"},{"effort":"xhigh"}],
          "default_reasoning_level":"xhigh","can_disable_thinking":false
        }""").asJsonObject)!!
        assertEquals(listOf("low", "medium", "xhigh"), evidence.efforts)
        assertTrue(ReasoningControls.policy("private-qwen", evidence).alwaysThinking)
        assertEquals("xhigh", ReasoningControls.selected("private-qwen", "openai", true, "max", evidence).value)
        assertEquals("xhigh", ReasoningControls.wireEffort("private-qwen", "openai", true, "xhigh", evidence))
        assertTrue(ReasoningControls.hasIntensityChoice("claude-custom", "openai", evidence))
    }

    @Test fun stopCentersMatchSliderAtEveryGearCount() {
        for (count in 2..8) {
            val width = 321f
            val inset = (width.toInt() / count / 2).toFloat().coerceAtLeast(14f)
            val sliderLeft = inset - 14f
            val sliderWidth = width - 2 * sliderLeft
            for (index in 0 until count) {
                val thumbCenter = sliderLeft + 14f + index.toFloat() / (count - 1) * (sliderWidth - 28f)
                assertEquals(thumbCenter, ReasoningStopLayout.center(index, count, width, inset), 0.001f)
            }
        }
        assertEquals(160.5f, ReasoningStopLayout.center(0, 1, 321f, 14f), 0.001f)
    }

    @Test fun nativeModelCatalogueCapabilityObjectsControlBudgetAndAdaptiveModes() {
        val info = ReasoningCapabilityEvidence.parse(JsonParser.parseString("""{
          "capabilities": {
            "thinking":{"supported":true,"types":{"enabled":{"supported":false},"adaptive":{"supported":true}}},
            "effort":{"supported":true,"low":{"supported":true},"medium":{"supported":true},
              "high":{"supported":true},"xhigh":{"supported":false},"max":{"supported":false}}
          }
        }""").asJsonObject)!!
        assertEquals(listOf("low", "medium", "high"), info.efforts)
        assertEquals("adaptive", info.thinkingType)
        assertNull(info.alwaysThinking)
        assertEquals("adaptive", ReasoningControls.thinkingType("private-native", "anthropic", true, info))
        assertEquals("disabled", ReasoningControls.thinkingType("private-native", "anthropic", false, info))
        assertEquals("high", ReasoningControls.wireEffort("private-native", "anthropic", true, "high", info))
        assertEquals("disabled", ReasoningControls.thinkingType("claude-3-7-sonnet", "anthropic", false))
        assertNull(ReasoningControls.thinkingType("kimi-k2.7-code", "openai", true))
        assertNull(ReasoningControls.thinkingType("glm-4", "openai", true))
    }

    @Test fun rawApiValuesAndCaseSensitiveModelIdsAreNeverRewritten() {
        val info = ReasoningCapabilityEvidence.parse(JsonParser.parseString("""{
          "supported_reasoning_efforts":["LOW","MEDIUM"],"default_effort":"MEDIUM"
        }""").asJsonObject)!!
        assertEquals(listOf("LOW", "MEDIUM"), info.efforts)
        assertEquals("MEDIUM", ReasoningControls.selected("private", "openai", true, null, info).value)
        assertEquals("LOW", ReasoningControls.wireEffort("private", "openai", true, "low", info))
        assertNotEquals(ReasoningCapabilityCatalog.key("a.invalid", "openai", "Model"),
            ReasoningCapabilityCatalog.key("a.invalid", "openai", "model"))
        assertTrue(ReasoningControls.selected("glm-5.2", "openai", true, "disabled").enabled)
        assertEquals("medium", ReasoningControls.selected("gpt-6-sol", "openai", true, "none").value)
        assertEquals("none", ReasoningControls.selected("gpt-6-sol", "openai", false, "medium").value)
        assertFalse(ReasoningControls.policy("glm-5.3-flashx").alwaysThinking)
        assertFalse(ReasoningControls.policy("gpt-5.99-unknown").alwaysThinking)
        val toggle = ReasoningCapabilityEvidence(efforts = listOf("none", "high"))
        assertFalse(ReasoningControls.policy("gpt-6-astra", toggle).alwaysThinking)
        val malformed = ReasoningCapabilityEvidence(alwaysThinking = true, efforts = listOf("none"))
        assertEquals("default", ReasoningControls.selected("private", "openai", true, "none", malformed).value)
    }
}
