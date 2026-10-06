package com.aiassistant

import android.app.Application
import com.aiassistant.domain.model.*
import com.aiassistant.utils.PersonalizationManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, manifest = Config.NONE)
class RetryPreferencesTest {
    @Test fun httpPermissionsDefaultClosedPersistAndRemainDeviceLocal() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("http_access_permissions", 0)
        prefs.edit().clear().commit()
        val settings = com.aiassistant.utils.HttpAccessSettings(context)
        assertFalse(settings.getRules().enabled)
        assertTrue(settings.getRules().addresses.isEmpty())
        settings.addAddress("192.0.2.1:8080/v1/")
        settings.addAddress("http://192.0.2.1:8080/v1")
        assertEquals(listOf("http://192.0.2.1:8080/v1"), settings.getRules().addresses)
        assertFalse(settings.getRules().enabled)
        settings.setEnabled(true)
        PersonalizationManager(context).saveSettings(PersonalizationManager(context).getSettings().copy(autoNameEnabled = false))
        val recreated = com.aiassistant.utils.HttpAccessSettings(context)
        assertTrue(recreated.getRules().enabled)
        recreated.setEnabled(false)
        assertEquals(1, recreated.getRules().addresses.size)
        try { recreated.addAddress("http://*.example.test"); fail("wildcard accepted") } catch (_: IllegalArgumentException) { }
        assertEquals(1, recreated.getRules().addresses.size)
        recreated.removeAddress("http://192.0.2.1:8080/v1")
        assertTrue(recreated.getRules().addresses.isEmpty())
        // Portable backup exports only personalization_settings, not this separate permission file.
        assertFalse(context.getSharedPreferences("personalization_settings", 0).contains("addresses"))
    }
    @Test fun masterSwitchPersistsAndDoesNotDiscardCategoryOrBackupKeyChoices() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("personalization_settings", 0).edit().clear().commit()
        val manager = PersonalizationManager(context)
        manager.saveRetryRule(RetryErrorType.SERVER, RetryRule(true, 2))
        manager.setBackupKeyFallbackEnabled(true)
        manager.setModelRetryEnabled(false)
        manager.saveSettings(manager.getSettings().copy(autoNameEnabled = false))
        val restored = PersonalizationManager(context)
        assertFalse(restored.isModelRetryEnabled())
        assertFalse(restored.isBackupKeyFallbackEnabled())
        assertFalse(restored.getRetryPolicy().canRetry(com.aiassistant.data.repository.ApiException(500, "API错误 (500)"), 0))
        assertEquals(RetryRule(true, 2), restored.getRetryPolicy().rule(RetryErrorType.SERVER))
        restored.setModelRetryEnabled(true)
        assertTrue(restored.isBackupKeyFallbackEnabled())
        assertTrue(restored.getRetryPolicy().canRetry(com.aiassistant.data.repository.ApiException(500, "API错误 (500)"), 1))
        assertFalse(restored.getRetryPolicy().canRetry(com.aiassistant.data.repository.ApiException(500, "API错误 (500)"), 2))
    }
    @Test fun rulesSurviveManagerRecreationAndUnrelatedSettingsSave() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("personalization_settings", 0)
        prefs.edit().clear().commit()
        val manager = PersonalizationManager(context)
        assertEquals(RetryRule(true, 3), manager.getRetryPolicy().rule(RetryErrorType.TIMEOUT))
        assertFalse(manager.getRetryPolicy().rule(RetryErrorType.AUTHENTICATION).enabled)
        manager.saveRetryRule(RetryErrorType.TIMEOUT, RetryRule(false, 7))
        manager.saveRetryRule(RetryErrorType.RATE_LIMIT, RetryRule(true, 4))
        assertEquals(RetryRule(false, 7), manager.getRetryPolicy().rule(RetryErrorType.TIMEOUT))
        assertEquals(RetryRule(true, 4), manager.getRetryPolicy().rule(RetryErrorType.RATE_LIMIT))
        manager.setBackupKeyFallbackEnabled(false)
        manager.saveSettings(manager.getSettings().copy(autoNameEnabled = false))
        val recreated = PersonalizationManager(context)
        assertEquals(RetryRule(false, 7), recreated.getRetryPolicy().rule(RetryErrorType.TIMEOUT))
        assertEquals(RetryRule(true, 4), recreated.getRetryPolicy().rule(RetryErrorType.RATE_LIMIT))
        assertFalse(recreated.isBackupKeyFallbackEnabled())
        assertFalse(recreated.getSettings().autoNameEnabled)
    }

    @Test fun retryCountsClampWithoutChangingOtherCategories() {
        val manager = PersonalizationManager(RuntimeEnvironment.getApplication())
        manager.saveRetryRule(RetryErrorType.SERVER, RetryRule(true, 500))
        manager.saveRetryRule(RetryErrorType.BAD_REQUEST, RetryRule(true, -1))
        assertEquals(20, manager.getRetryPolicy().rule(RetryErrorType.SERVER).maxRetries)
        assertEquals(0, manager.getRetryPolicy().rule(RetryErrorType.BAD_REQUEST).maxRetries)
        assertFalse(manager.getRetryPolicy().canRetry(Exception("HTTP 400: invalid"), 0))
    }
}
