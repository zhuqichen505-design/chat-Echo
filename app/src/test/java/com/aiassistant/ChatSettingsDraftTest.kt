package com.aiassistant

import com.aiassistant.ui.screens.chat.TempChatSettings
import com.aiassistant.ui.screens.chat.mergeChatSettingsDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSettingsDraftTest {
    @Test
    fun untouchedInjectionFieldsFollowExternalUpdates() {
        val previous = TempChatSettings()
        val incoming = previous.copy(enableInputInjection = true, inputInjectionTrigger = "继续本段故事")

        val merged = mergeChatSettingsDraft(previous, previous, incoming)

        assertTrue(merged.enableInputInjection)
        assertEquals("继续本段故事", merged.inputInjectionTrigger)
    }

    @Test
    fun externalModelUpdatesKeepLocallyEditedInjectionDraft() {
        val previous = TempChatSettings()
        val local = previous.copy(enableInputInjection = true, inputInjectionTrigger = "  自定义第一行\n第二行  ")
        val incoming = previous.copy(temperature = 0.4f, inputInjectionTrigger = "其他更新")

        val merged = mergeChatSettingsDraft(local, previous, incoming)

        assertTrue(merged.enableInputInjection)
        assertEquals("  自定义第一行\n第二行  ", merged.inputInjectionTrigger)
        assertEquals(0.4f, merged.temperature)
    }

    @Test
    fun locallyDisablingInjectionRetainsTheCustomTriggerDuringRefresh() {
        val previous = TempChatSettings(enableInputInjection = true, inputInjectionTrigger = "保存的触发语")
        val local = previous.copy(enableInputInjection = false, inputInjectionTrigger = "用户保留的触发语")
        val incoming = previous.copy(maxTokens = 8192)

        val merged = mergeChatSettingsDraft(local, previous, incoming)

        assertFalse(merged.enableInputInjection)
        assertEquals("用户保留的触发语", merged.inputInjectionTrigger)
        assertEquals(8192, merged.maxTokens)
    }

    @Test
    fun blankTriggerDraftIsNotReplacedByExternalSettingsWhileBeingEdited() {
        val previous = TempChatSettings(enableInputInjection = true, inputInjectionTrigger = "已有触发语")
        val local = previous.copy(inputInjectionTrigger = "\n  ")
        val incoming = previous.copy(topP = 0.7f, inputInjectionTrigger = "外部触发语")

        val merged = mergeChatSettingsDraft(local, previous, incoming)

        assertEquals("\n  ", merged.inputInjectionTrigger)
        assertEquals(0.7f, merged.topP)
    }
}
