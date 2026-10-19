package com.aiassistant

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.aiassistant.data.remote.RetrofitClient
import com.aiassistant.data.repository.AiRepository
import com.aiassistant.data.repository.ApiException
import com.aiassistant.data.repository.KeyAttemptFailure
import com.aiassistant.domain.model.ApiKeyRotationSettings
import com.aiassistant.utils.ApiKeyCoolingDownException
import com.aiassistant.utils.ApiKeyRotationManager
import com.aiassistant.utils.PersonalizationManager
import com.aiassistant.utils.SmartMemoryExtractor
import org.junit.Assert.*
import org.junit.Test
import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.io.InterruptedIOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class NetworkAndKeyStabilityTest {
    @org.junit.Before fun allowSyntheticHttpEndpoints() { HttpTestAccess.start() }
    @org.junit.After fun restoreClosedHttpPolicy() { HttpTestAccess.stop() }

    @Test fun startupRotationPatternsAreCompatibleWithIcuCharacterSets() {
        // Desktop Pattern accepts some classes that Android ICU reads as malformed POSIX properties.
        // Parse the real startup fields with ICU's set parser before compiling their JVM equivalent.
        val patterns = ApiKeyRotationManager::class.java.declaredFields
            .filter { it.type == Regex::class.java }
            .map { field -> field.isAccessible = true; field.get(null) as Regex }
        assertTrue("必须检查实际启动时编译的正则", patterns.isNotEmpty())
        patterns.forEach { pattern ->
            java.util.regex.Pattern.compile(com.ibm.icu.impl.UnicodeRegex.fix(pattern.pattern))
        }
    }

    @Test fun textualHttpStatusVariantsKeepTheirKeyHealthClassification() {
        val fixture = RotationFixture()
        val manager = fixture.manager()
        val accepted = listOf(
            "HTTP 401", "HTTP error:429", "HTTP(500)", "http error (503)",
            "API错误（403）", "API错误(408)", "API错误:402"
        )
        accepted.forEach { message ->
            assertTrue(message, manager.recordFailure(ROTATION_URL, "openai", ROTATION_KEYS[0], Exception(message)))
        }
        assertEquals(accepted.size, fixture.preferences.all.entries.single { it.key.endsWith(".failures") }.value)
        val snapshot = fixture.preferences.all
        listOf("HTTP 400", "HTTP error:404", "API错误（422）", "HTTP 4010", "no status").forEach { message ->
            assertFalse(message, manager.recordFailure(ROTATION_URL, "openai", ROTATION_KEYS[0], Exception(message)))
        }
        assertEquals("非 Key 错误不能修改健康记录", snapshot, fixture.preferences.all)
    }

    @Test fun keyRotationBoundsRejectUnsafeTimesAndUnknownModesWithoutOverflow() {
        val normalized = ApiKeyRotationSettings(
            mode = "unknown", failuresBeforeCooldown = Int.MIN_VALUE,
            cooldownSeconds = Long.MAX_VALUE, maxCooldownSeconds = Long.MIN_VALUE,
            failureResetSeconds = Long.MAX_VALUE
        ).normalized()
        assertEquals(ApiKeyRotationSettings.ROUND_ROBIN, normalized.mode)
        assertEquals(1, normalized.failuresBeforeCooldown)
        assertEquals(ApiKeyRotationSettings.MAX_BASE_COOLDOWN_SECONDS, normalized.cooldownSeconds)
        assertEquals(normalized.cooldownSeconds, normalized.maxCooldownSeconds)
        assertEquals(ApiKeyRotationSettings.MAX_FAILURE_RESET_SECONDS, normalized.failureResetSeconds)
        val minimum = ApiKeyRotationSettings(failuresBeforeCooldown = Int.MAX_VALUE,
            cooldownSeconds = -1, maxCooldownSeconds = Long.MAX_VALUE, failureResetSeconds = -1).normalized()
        assertEquals(100, minimum.failuresBeforeCooldown)
        assertEquals(1L, minimum.cooldownSeconds)
        assertEquals(ApiKeyRotationSettings.MAX_COOLDOWN_SECONDS, minimum.maxCooldownSeconds)
        assertEquals(0L, minimum.failureResetSeconds)
    }

    @Test fun roundRobinPersistsItsCursorAndRemovesEmptyOrDuplicateKeys() {
        val fixture = RotationFixture()
        val dirty = listOf("", "  ", " synthetic-alpha ", "synthetic-alpha", "synthetic-beta", "synthetic-gamma")
        val starts = (0..5).map { fixture.manager().orderedKeys(ROTATION_URL, "openai", dirty, false).single() }
        assertEquals(ROTATION_KEYS + ROTATION_KEYS, starts)
        assertTrue(fixture.manager().orderedKeys("", "", emptyList()).isEmpty())
        assertEquals(ROTATION_KEYS.toSet(), fixture.manager().orderedKeys(ROTATION_URL, "openai", dirty).toSet())
    }

    @Test fun bearerAliasesAreDeduplicatedWhileOriginalSendingStringsAndCaseRemainUnchanged() {
        val fixture = RotationFixture()
        val manager = fixture.manager()
        manager.saveSettings(ROTATION_URL, "openai", ApiKeyRotationSettings(mode = "ordered"))
        val aliases = listOf(
            "  Bearer synthetic-alpha  ", "synthetic-alpha", "synthetic-beta", "Bearer synthetic-beta",
            "bearer synthetic-alpha"
        )
        assertEquals(
            listOf("Bearer synthetic-alpha", "synthetic-beta", "bearer synthetic-alpha"),
            manager.orderedKeys(ROTATION_URL, "openai", aliases)
        )
        assertEquals(
            listOf("synthetic-alpha", "Bearer synthetic-beta"),
            manager.orderedKeys(ROTATION_URL, "openai", listOf("synthetic-alpha", "Bearer synthetic-alpha", "Bearer synthetic-beta", "synthetic-beta"))
        )
    }

    @Test fun bearerAliasesShareFailuresCooldownAndSuccessResetAcrossManagersAndConfigurations() {
        for ((failedKey, otherSpelling) in listOf(
            "synthetic-alpha" to "Bearer synthetic-alpha",
            "Bearer synthetic-alpha" to "synthetic-alpha"
        )) {
            val fixture = RotationFixture()
            val manager = fixture.manager()
            manager.saveSettings(ROTATION_URL, "openai", ApiKeyRotationSettings(mode = "ordered", failuresBeforeCooldown = 2))
            assertTrue(manager.recordFailure(ROTATION_URL, "openai", failedKey, ApiException(401, "synthetic auth failure")))
            assertFalse(fixture.manager().isCoolingDown(ROTATION_URL, "openai", otherSpelling))
            assertTrue(fixture.manager().recordFailure(ROTATION_URL, "openai", otherSpelling, ApiException(401, "synthetic auth failure")))
            assertEquals(2, fixture.preferences.all.entries.single { it.key.endsWith(".failures") }.value)
            assertTrue(manager.isCoolingDown(ROTATION_URL, "openai", failedKey))
            assertTrue(fixture.manager().isCoolingDown(ROTATION_URL, "openai", otherSpelling))
            assertEquals(listOf("synthetic-beta"), fixture.manager().orderedKeys(
                "rotation.example.test/v1/", "OPENAI", listOf(otherSpelling, "synthetic-beta")
            ))
            fixture.manager().recordSuccess(ROTATION_URL, "openai", otherSpelling)
            assertFalse(manager.isCoolingDown(ROTATION_URL, "openai", failedKey))
            assertFalse(fixture.preferences.all.keys.any { it.endsWith(".failures") })
            assertEquals(listOf(failedKey, "synthetic-beta"), fixture.manager().orderedKeys(
                ROTATION_URL, "openai", listOf(failedKey, "synthetic-beta")
            ))
        }
    }

    @Test fun successfulBearerAliasesSharePreferredIdentityWithoutRewritingReturnedKeys() {
        val fixture = RotationFixture()
        val manager = fixture.manager()
        manager.saveSettings(ROTATION_URL, "openai", ApiKeyRotationSettings(mode = "prefer_success"))
        manager.recordSuccess(ROTATION_URL, "openai", "Bearer synthetic-gamma")
        assertEquals("synthetic-gamma", fixture.manager().orderedKeys(ROTATION_URL, "openai", ROTATION_KEYS).first())
        fixture.manager().recordSuccess(ROTATION_URL, "openai", "synthetic-beta")
        val otherConfiguration = listOf("Bearer synthetic-alpha", "Bearer synthetic-beta", "Bearer synthetic-gamma")
        assertEquals("Bearer synthetic-beta", manager.orderedKeys(ROTATION_URL, "openai", otherConfiguration).first())
    }

    @Test fun failedFirstKeyStaysCoolingAcrossInstancesAndEquivalentSavedAddresses() {
        val fixture = RotationFixture()
        val manager = fixture.manager()
        assertTrue(manager.recordFailure(ROTATION_URL, "openai", ROTATION_KEYS[0], ApiException(401, "synthetic auth failure")))
        assertEquals(ROTATION_KEYS.drop(1), fixture.manager().orderedKeys(" rotation.example.test/v1/ ", "OPENAI", ROTATION_KEYS))
        assertFalse(fixture.manager().orderedKeys(ROTATION_URL, "openai", ROTATION_KEYS).contains(ROTATION_KEYS[0]))
        assertTrue(manager.isCoolingDown(ROTATION_URL, "openai", ROTATION_KEYS[0]))
        assertFalse(manager.isCoolingDown(ROTATION_URL, "anthropic", ROTATION_KEYS[0]))
        assertFalse(manager.isCoolingDown("https://other.example.test/v1", "openai", ROTATION_KEYS[0]))
    }

    @Test fun failureCountsLowerPriorityBeforeCooldownAndRoundRobinRemainsFairWithinHealthyTier() {
        val fixture = RotationFixture()
        val manager = fixture.manager()
        manager.saveSettings(ROTATION_URL, "openai", ApiKeyRotationSettings(cooldownEnabled = false))
        manager.recordFailure(ROTATION_URL, "openai", ROTATION_KEYS[1], ApiException(429, "synthetic rate limit"))
        val starts = (0..5).map { fixture.manager().orderedKeys(ROTATION_URL, "openai", ROTATION_KEYS, false).single() }
        assertEquals(listOf(ROTATION_KEYS[0], ROTATION_KEYS[2], ROTATION_KEYS[0], ROTATION_KEYS[2], ROTATION_KEYS[0], ROTATION_KEYS[2]), starts)
        manager.saveSettings(ROTATION_URL, "openai", ApiKeyRotationSettings(mode = "ordered", failuresBeforeCooldown = 5))
        manager.recordFailure(ROTATION_URL, "openai", ROTATION_KEYS[0], ApiException(500, "synthetic server error"))
        manager.recordFailure(ROTATION_URL, "openai", ROTATION_KEYS[1], ApiException(500, "synthetic server error"))
        assertEquals(listOf(ROTATION_KEYS[2], ROTATION_KEYS[0], ROTATION_KEYS[1]), manager.orderedKeys(ROTATION_URL, "openai", ROTATION_KEYS))
        assertFalse(manager.isCoolingDown(ROTATION_URL, "openai", ROTATION_KEYS[0]))
        assertEquals(listOf(ROTATION_KEYS[2]), manager.orderedKeys(ROTATION_URL, "openai", ROTATION_KEYS, false))
    }

    @Test fun allCoolingReportsSafeWaitAndRecoversAtExactClockBoundary() {
        val fixture = RotationFixture()
        val manager = fixture.manager()
        manager.recordFailure(ROTATION_URL, "openai", ROTATION_KEYS[0], ApiException(401, "synthetic"))
        fixture.now += 1001
        manager.recordFailure(ROTATION_URL, "openai", ROTATION_KEYS[1], ApiException(403, "synthetic"))
        try {
            manager.orderedKeys(ROTATION_URL, "openai", ROTATION_KEYS.take(2))
            fail("Cooling Keys must not be sent on every request")
        } catch (error: ApiKeyCoolingDownException) {
            assertEquals(299L, error.retryAfterSeconds)
            assertFalse(error.message!!.contains("rotation.example.test"))
            assertFalse(error.message!!.contains("synthetic-alpha"))
        }
        fixture.now += 298999
        assertEquals(listOf(ROTATION_KEYS[0]), fixture.manager().orderedKeys(ROTATION_URL, "openai", ROTATION_KEYS.take(2)))
        assertTrue(manager.isCoolingDown(ROTATION_URL, "openai", ROTATION_KEYS[1]))
    }

    @Test fun configurableFailureThresholdBackoffAndMaximumControlEachCooldown() {
        val fixture = RotationFixture()
        val manager = fixture.manager()
        manager.saveSettings(ROTATION_URL, "openai", ApiKeyRotationSettings(
            failuresBeforeCooldown = 2, cooldownSeconds = 10, maxCooldownSeconds = 25, failureResetSeconds = 0
        ))
        val key = ROTATION_KEYS[0]
        manager.recordFailure(ROTATION_URL, "openai", key, ApiException(402, "synthetic quota"))
        assertFalse(manager.isCoolingDown(ROTATION_URL, "openai", key))
        for (expectedSeconds in listOf(10L, 20L, 25L, 25L)) {
            manager.recordFailure(ROTATION_URL, "openai", key, ApiException(402, "synthetic quota"))
            assertCoolingFor(manager, fixture, key, expectedSeconds)
        }
        manager.saveSettings(ROTATION_URL, "openai", manager.getSettings(ROTATION_URL, "openai").copy(exponentialBackoff = false))
        manager.recordFailure(ROTATION_URL, "openai", key, ApiException(402, "synthetic quota"))
        assertCoolingFor(manager, fixture, key, 10L)
    }

    @Test fun automaticResetExpiresFailurePriorityAndCooldownWithoutRestartingApp() {
        val fixture = RotationFixture()
        val manager = fixture.manager()
        manager.saveSettings(ROTATION_URL, "openai", ApiKeyRotationSettings(mode = "ordered", failureResetSeconds = 30))
        manager.recordFailure(ROTATION_URL, "openai", ROTATION_KEYS[0], ApiException(503, "synthetic"))
        fixture.now += 29999
        assertEquals(ROTATION_KEYS.drop(1), manager.orderedKeys(ROTATION_URL, "openai", ROTATION_KEYS))
        fixture.now += 1
        assertEquals(ROTATION_KEYS, manager.orderedKeys(ROTATION_URL, "openai", ROTATION_KEYS))
        assertFalse(fixture.preferences.all.keys.any { it.endsWith(".failures") })
        manager.saveSettings(ROTATION_URL, "openai", manager.getSettings(ROTATION_URL, "openai").copy(cooldownEnabled = false, failureResetSeconds = 0))
        manager.recordFailure(ROTATION_URL, "openai", ROTATION_KEYS[0], ApiException(503, "synthetic"))
        fixture.now += 366L * 86400 * 1000
        assertEquals(ROTATION_KEYS[1], manager.orderedKeys(ROTATION_URL, "openai", ROTATION_KEYS).first())
    }

    @Test fun successfulKeyCanBePreferredAndSuccessResetIsConfigurable() {
        val fixture = RotationFixture()
        val manager = fixture.manager()
        manager.saveSettings(ROTATION_URL, "openai", ApiKeyRotationSettings(mode = "prefer_success"))
        manager.recordSuccess(ROTATION_URL, "openai", ROTATION_KEYS[2])
        repeat(4) { assertEquals(ROTATION_KEYS[2], fixture.manager().orderedKeys(ROTATION_URL, "openai", ROTATION_KEYS).first()) }
        manager.recordFailure(ROTATION_URL, "openai", ROTATION_KEYS[2], ApiException(401, "synthetic"))
        assertFalse(manager.orderedKeys(ROTATION_URL, "openai", ROTATION_KEYS).contains(ROTATION_KEYS[2]))
        manager.recordSuccess(ROTATION_URL, "openai", ROTATION_KEYS[2])
        assertEquals(ROTATION_KEYS[2], manager.orderedKeys(ROTATION_URL, "openai", ROTATION_KEYS).first())
        manager.saveSettings(ROTATION_URL, "openai", manager.getSettings(ROTATION_URL, "openai").copy(resetOnSuccess = false))
        manager.recordFailure(ROTATION_URL, "openai", ROTATION_KEYS[2], ApiException(401, "synthetic"))
        manager.recordSuccess(ROTATION_URL, "openai", ROTATION_KEYS[2])
        assertFalse(manager.isCoolingDown(ROTATION_URL, "openai", ROTATION_KEYS[2]))
        assertNotEquals(ROTATION_KEYS[2], manager.orderedKeys(ROTATION_URL, "openai", ROTATION_KEYS).first())
    }

    @Test fun optionalAllCoolingProbeOnlySelectsEarliestRecoveryAndDisabledCooldownAppliesImmediately() {
        val fixture = RotationFixture()
        val manager = fixture.manager()
        manager.saveSettings(ROTATION_URL, "openai", ApiKeyRotationSettings(probeWhenAllCooling = true))
        manager.recordFailure(ROTATION_URL, "openai", ROTATION_KEYS[2], SocketTimeoutException("synthetic timeout"))
        fixture.now += 1000
        manager.recordFailure(ROTATION_URL, "openai", ROTATION_KEYS[0], SocketTimeoutException("synthetic timeout"))
        manager.recordFailure(ROTATION_URL, "openai", ROTATION_KEYS[1], SocketTimeoutException("synthetic timeout"))
        assertEquals(listOf(ROTATION_KEYS[2]), manager.orderedKeys(ROTATION_URL, "openai", ROTATION_KEYS))
        manager.saveSettings(ROTATION_URL, "openai", manager.getSettings(ROTATION_URL, "openai").copy(cooldownEnabled = false))
        assertEquals(3, manager.orderedKeys(ROTATION_URL, "openai", ROTATION_KEYS).size)
        assertFalse(manager.isCoolingDown(ROTATION_URL, "openai", ROTATION_KEYS[0]))
    }

    @Test fun disablingFailurePriorityRestoresConfiguredOrderAndScopeResetKeepsSettings() {
        val fixture = RotationFixture()
        val manager = fixture.manager()
        val settings = ApiKeyRotationSettings(mode = "ordered", cooldownEnabled = false, deprioritizeFailures = false)
        assertTrue(manager.saveSettings(ROTATION_URL, "openai", settings))
        manager.recordFailure(ROTATION_URL, "openai", ROTATION_KEYS[0], ApiException(500, "synthetic"))
        manager.recordFailure(ROTATION_URL, "anthropic", ROTATION_KEYS[0], ApiException(500, "synthetic"))
        assertEquals(ROTATION_KEYS, manager.orderedKeys(ROTATION_URL, "openai", ROTATION_KEYS))
        assertTrue(manager.resetHealth(ROTATION_URL, "openai"))
        assertEquals(settings, fixture.manager().getSettings(ROTATION_URL, "openai"))
        assertTrue(manager.isCoolingDown(ROTATION_URL, "anthropic", ROTATION_KEYS[0]))
        assertEquals(ApiKeyRotationSettings(), manager.getSettings(ROTATION_URL, "anthropic"))
    }

    @Test fun onlyTransportAuthenticationQuotaAndServerErrorsAffectKeyHealth() {
        val fixture = RotationFixture()
        val manager = fixture.manager()
        val accepted = listOf(401, 402, 403, 408, 429, 500, 502, 503, 504, 599).map { ApiException(it, "synthetic error") } + listOf(
            SocketTimeoutException("synthetic timeout"), java.net.ConnectException("synthetic connection refused"),
            java.io.EOFException("synthetic disconnect"), Exception("HTTP 429: synthetic rate limit"),
            InterruptedIOException("timeout").apply { initCause(java.io.IOException("Canceled")) }
        )
        for (error in accepted) {
            assertTrue(error.javaClass.simpleName, manager.recordFailure(ROTATION_URL, "openai", ROTATION_KEYS[0], error))
        }
        assertEquals(accepted.size, fixture.preferences.all.values.filterIsInstance<Int>().single())
        val snapshot = fixture.preferences.all
        val ignored = listOf(400, 404, 409, 413, 422).map { ApiException(it, "synthetic timeout in request body") } + listOf(
            ApiException(500, "synthetic failure", "context_length_exceeded: maximum input tokens exceeded"),
            ApiException(403, "synthetic content_policy_violation"),
            IllegalStateException("内容拒绝，拒绝生成"), IllegalArgumentException("synthetic timeout validation"),
            kotlinx.coroutines.CancellationException("synthetic cancellation", SocketTimeoutException("timeout")),
            java.io.IOException("Canceled"), com.aiassistant.data.remote.HttpAccessDeniedException("synthetic timeout denied"),
            ApiKeyCoolingDownException(10), IllegalStateException("synthetic local failure")
        )
        ignored.forEach { error -> assertFalse(error.javaClass.simpleName, manager.recordFailure(ROTATION_URL, "openai", ROTATION_KEYS[0], error)) }
        assertEquals("被忽略的错误不得修改任何健康字段", snapshot, fixture.preferences.all)
        assertFalse(manager.recordFailure(ROTATION_URL, "openai", " ", ApiException(401, "synthetic")))
    }

    @Test fun rotationStorageContainsOnlyHashedScopeAndKeyIdentifiersAndNumericHealth() {
        val fixture = RotationFixture()
        val manager = fixture.manager()
        assertTrue(manager.saveSettings(ROTATION_URL, "openai", ApiKeyRotationSettings(mode = "prefer_success")))
        manager.recordSuccess(ROTATION_URL, "openai", ROTATION_KEYS[1])
        manager.recordFailure(ROTATION_URL, "openai", ROTATION_KEYS[0], ApiException(401, "synthetic-secret-error-body"))
        manager.orderedKeys(ROTATION_URL, "openai", ROTATION_KEYS)
        val storedText = fixture.preferences.all.toString()
        for (privateValue in ROTATION_KEYS + listOf(ROTATION_URL, "rotation.example.test", "synthetic-secret-error-body")) {
            assertFalse("轮询存储不得含输入原文", storedText.contains(privateValue))
        }
        val hash = "[a-f0-9]{64}"
        fixture.preferences.all.keys.forEach { key ->
            assertTrue(key, key.matches(Regex("(?:settings\\.$hash\\.[A-Za-z]+|health\\.$hash\\.$hash\\.[A-Za-z]+|(?:cursor|preferred)\\.$hash)")))
        }
        val preferred = fixture.preferences.all.entries.single { it.key.startsWith("preferred.") }.value
        assertTrue(preferred is String && preferred.matches(Regex(hash)))
    }

    @Test fun tokenQuotaAndRateLimitResponsesLowerKeyPriorityInsteadOfLookingLikeContextOverflow() {
        val fixture = RotationFixture()
        val manager = fixture.manager()
        manager.saveSettings(ROTATION_URL, "openai", ApiKeyRotationSettings(mode = "ordered", cooldownEnabled = false))
        for (message in listOf(
            "token quota exceeded", "maximum tokens per minute exceeded",
            "每分钟 token 配额超过上限", "token 额度超出限制"
        )) {
            assertTrue(message, manager.recordFailure(ROTATION_URL, "openai", ROTATION_KEYS[0], ApiException(429, "HTTP 429", message)))
        }
        assertEquals(4, fixture.preferences.all.entries.single { it.key.endsWith(".failures") }.value)
        assertEquals(ROTATION_KEYS[1], manager.orderedKeys(ROTATION_URL, "openai", ROTATION_KEYS).first())
        val snapshot = fixture.preferences.all
        assertFalse(manager.recordFailure(ROTATION_URL, "openai", ROTATION_KEYS[0],
            ApiException(429, "HTTP 429", "context_length_exceeded: maximum input tokens exceeded")))
        assertFalse(manager.recordFailure(ROTATION_URL, "openai", ROTATION_KEYS[0],
            ApiException(429, "HTTP 429", "content_filter: synthetic refusal")))
        assertEquals(snapshot, fixture.preferences.all)
    }

    @Test fun explicitCredentialAndQuotaErrorsInSuccessfulOrInlineResponsesStillLowerKeyPriority() {
        val fixture = RotationFixture()
        val manager = fixture.manager()
        manager.saveSettings(ROTATION_URL, "openai", ApiKeyRotationSettings(mode = "ordered", cooldownEnabled = false))
        val failures = listOf<Throwable>(
            ApiException(400, "API流式返回错误: synthetic failure", """{"code":"invalid_api_key"}"""),
            ApiException(400, "Anthropic错误: synthetic failure", """{"type":"error","error":{"type":"authentication_error"}}"""),
            ApiException(200, "synthetic error", """{"error":{"code":"insufficient_quota","message":"synthetic failure"}}"""),
            ApiException(400, "synthetic error", """{"type":"rate_limit_error"}"""),
            Exception("authentication_failed: synthetic failure"),
            Exception("rate_limit_exceeded"),
            ApiException(400, "API流式返回错误: invalid_api_key"),
            ApiException(400, "API流式返回错误: Incorrect API key provided"),
            Exception("API key has expired"),
            Exception("Authentication failed"),
            Exception("Insufficient quota"),
            Exception("Quota has been exceeded"),
            Exception("Rate limit reached for synthetic-model"),
            Exception("API 密钥无效"),
            Exception("API key 已过期"),
            Exception("额度不足"),
            Exception("余额不足"),
            Exception("配额已耗尽"),
            Exception("请求频率超限")
        )
        failures.forEach { failure -> assertTrue(failure.message, manager.recordFailure(ROTATION_URL, "openai", ROTATION_KEYS[0], failure)) }
        assertEquals(failures.size, fixture.preferences.all.entries.single { it.key.endsWith(".failures") }.value)
        assertEquals(ROTATION_KEYS[1], manager.orderedKeys(ROTATION_URL, "openai", ROTATION_KEYS).first())
    }

    @Test fun ordinaryParameterErrorsAndRefusalsCannotMasqueradeAsCredentialFailures() {
        val fixture = RotationFixture()
        val manager = fixture.manager()
        val ignored = listOf<Throwable>(
            ApiException(400, "Missing required key 'model'"),
            ApiException(400, "Unsupported parameter 'api_key'"),
            ApiException(400, "Unsupported parameter 'invalid_api_key'"),
            ApiException(400, "synthetic parameter error", """{"param":"invalid_api_key"}"""),
            ApiException(400, "Invalid key 'model' provided"),
            ApiException(400, "Incorrect value for max_tokens"),
            ApiException(400, "synthetic error", """{"code":"invalid_api_key_suffix"}"""),
            ApiException(404, "synthetic error", """{"code":"invalid_api_key"}"""),
            ApiException(413, "synthetic error", """{"code":"insufficient_quota"}"""),
            ApiException(422, "synthetic error", """{"code":"authentication_error"}"""),
            ApiException(400, "synthetic error", """{"error":{"code":"invalid_api_key","message":"context_length_exceeded"}}"""),
            ApiException(400, "synthetic error", """{"error":{"code":"insufficient_quota","type":"content_filter"}}"""),
            IllegalArgumentException("Invalid API key"),
            kotlinx.coroutines.CancellationException("invalid_api_key"),
            Exception("API key configuration invalid"),
            Exception("API 密钥配置错误")
        )
        val snapshot = fixture.preferences.all
        ignored.forEach { failure -> assertFalse(failure.message, manager.recordFailure(ROTATION_URL, "openai", ROTATION_KEYS[0], failure)) }
        assertEquals(snapshot, fixture.preferences.all)
        assertEquals(ROTATION_KEYS[0], manager.orderedKeys(ROTATION_URL, "openai", ROTATION_KEYS).first())
    }

    @Test fun concurrentManagersAdvanceFairCursorAndKeepEveryFailureIncrement() {
        val fixture = RotationFixture()
        val manager = fixture.manager()
        manager.saveSettings(ROTATION_URL, "openai", ApiKeyRotationSettings(cooldownEnabled = false))
        val executor = Executors.newFixedThreadPool(6)
        val chosen = CopyOnWriteArrayList<String>()
        try {
            val selections = (0 until 6).map { executor.submit {
                repeat(20) { chosen.add(fixture.manager().orderedKeys(ROTATION_URL, "openai", ROTATION_KEYS, false).single()) }
            } }
            selections.forEach { it.get(10, TimeUnit.SECONDS) }
            ROTATION_KEYS.forEach { key -> assertEquals(40, chosen.count { it == key }) }
            val failures = (0 until 6).map { executor.submit {
                repeat(10) { assertTrue(fixture.manager().recordFailure(ROTATION_URL, "openai", ROTATION_KEYS[0], ApiException(401, "synthetic"))) }
            } }
            failures.forEach { it.get(10, TimeUnit.SECONDS) }
            val failureCount = fixture.preferences.all.entries.single { it.key.endsWith(".failures") }.value
            assertEquals(60, failureCount)
            assertNotEquals(ROTATION_KEYS[0], manager.orderedKeys(ROTATION_URL, "openai", ROTATION_KEYS).first())
        } finally { executor.shutdownNow() }
    }

    private fun assertCoolingFor(manager: ApiKeyRotationManager, fixture: RotationFixture, key: String, seconds: Long) {
        try {
            manager.orderedKeys(ROTATION_URL, "openai", listOf(key))
            fail("Expected cooldown")
        } catch (error: ApiKeyCoolingDownException) { assertEquals(seconds, error.retryAfterSeconds) }
        fixture.now += seconds * 1000 - 1
        assertTrue(manager.isCoolingDown(ROTATION_URL, "openai", key))
        fixture.now += 1
        assertFalse(manager.isCoolingDown(ROTATION_URL, "openai", key))
    }

    private class RotationFixture {
        var now = 1_000_000L
        val preferences: SharedPreferences = MemoryPreferences()
        private val context = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
                require(name == ApiKeyRotationManager.PREFERENCES_NAME)
                return preferences
            }
        }
        fun manager() = ApiKeyRotationManager(context) { now }
    }

    /** Android 无 UI 存储替身，多个 manager 使用同一持久层，时钟由测试推进。 */
    private class MemoryPreferences : SharedPreferences {
        private val values = mutableMapOf<String, Any>()
        @Synchronized override fun getAll(): MutableMap<String, *> = HashMap(values)
        override fun contains(key: String?) = getAll().containsKey(key)
        override fun getString(key: String?, defValue: String?): String? = getAll()[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (getAll()[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int) = getAll()[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long) = getAll()[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float) = getAll()[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean) = getAll()[key] as? Boolean ?: defValue
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clear = false
            private fun put(key: String?, value: Any?): SharedPreferences.Editor = apply { pending[requireNotNull(key)] = value }
            override fun putString(key: String?, value: String?) = put(key, value)
            override fun putStringSet(key: String?, values: MutableSet<String>?) = put(key, values?.toSet())
            override fun putInt(key: String?, value: Int) = put(key, value)
            override fun putLong(key: String?, value: Long) = put(key, value)
            override fun putFloat(key: String?, value: Float) = put(key, value)
            override fun putBoolean(key: String?, value: Boolean) = put(key, value)
            override fun remove(key: String?) = put(key, null)
            override fun clear(): SharedPreferences.Editor = apply { clear = true }
            override fun commit(): Boolean = synchronized(this@MemoryPreferences) {
                if (clear) values.clear()
                pending.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
                true
            }
            override fun apply() { commit() }
        }
    }

    private companion object {
        const val ROTATION_URL = "https://rotation.example.test/v1"
        val ROTATION_KEYS = listOf("synthetic-alpha", "synthetic-beta", "synthetic-gamma")
    }

    @Test fun apiAddressesPreserveExplicitServiceRootsAndRepairKnownExtraVersionSuffixes() {
        val helper = com.aiassistant.data.repository.helpers.ApiEndpointHelper
        val cases = listOf(
            " proxy.example.test " to "https://proxy.example.test/v1",
            "https://proxy.example.test/" to "https://proxy.example.test/v1",
            "https://proxy.example.test/v1///" to "https://proxy.example.test/v1",
            "https://proxy.example.test/v1beta/openai" to "https://proxy.example.test/v1beta/openai",
            "https://proxy.example.test/v1beta/openai/v1" to "https://proxy.example.test/v1beta/openai",
            "https://proxy.example.test/api/paas/v4" to "https://proxy.example.test/api/paas/v4",
            "https://proxy.example.test/api/paas/v4/v1" to "https://proxy.example.test/api/paas/v4",
            "https://proxy.example.test/gateway/custom" to "https://proxy.example.test/gateway/custom",
            "https://proxy.example.test/gateway/v1" to "https://proxy.example.test/gateway/v1",
            "https://proxy.example.test/v1/v1" to "https://proxy.example.test/v1",
            "https://proxy.example.test/v1/chat/completions/v1" to "https://proxy.example.test/v1/chat/completions",
            "https://proxy.example.test/chat/completions" to "https://proxy.example.test/chat/completions"
        )
        for ((input, expected) in cases) {
            val actual = helper.normalizeBaseUrl(input)
            assertEquals(input, expected, actual)
            assertEquals("重复保存或读取配置不能继续改变地址", actual, helper.normalizeBaseUrl(actual))
        }
    }

    @Test fun fullApiOperationAddressesResolveToOneTransportOperation() {
        val helper = com.aiassistant.data.repository.helpers.ApiEndpointHelper
        val cases = listOf(
            "https://proxy.example.test/chat/completions" to "https://proxy.example.test/",
            "https://proxy.example.test/v1/chat/completions" to "https://proxy.example.test/v1/",
            "https://proxy.example.test/v1/chat/completions/v1" to "https://proxy.example.test/v1/",
            "https://proxy.example.test/v1beta/openai/chat/completions" to "https://proxy.example.test/v1beta/openai/",
            "https://proxy.example.test/api/paas/v4/chat/completions" to "https://proxy.example.test/api/paas/v4/",
            "https://proxy.example.test/anthropic/messages" to "https://proxy.example.test/anthropic/",
            "https://proxy.example.test/v1/messages/v1" to "https://proxy.example.test/v1/",
            "http://[::1]:8080/v1/models" to "http://[::1]:8080/v1/"
        )
        for ((input, expected) in cases) {
            for (protocol in listOf("openai", "anthropic")) {
                assertEquals(input, expected, helper.requestBaseUrl(input, protocol))
                assertEquals(input, expected, helper.requestBaseUrl(helper.normalizeBaseUrl(input, protocol), protocol))
            }
        }
    }

    @Test fun allRetrofitTransportsUseTheConfiguredCustomRootAndStripFullOperationUrls() {
        val cases = listOf(
            "https://proxy.example.test/v1beta/openai" to "https://proxy.example.test/v1beta/openai/chat/completions",
            "https://proxy.example.test/api/paas/v4/v1" to "https://proxy.example.test/api/paas/v4/chat/completions",
            "https://proxy.example.test/v1/chat/completions" to "https://proxy.example.test/v1/chat/completions",
            "https://proxy.example.test/chat/completions" to "https://proxy.example.test/chat/completions"
        )
        for ((input, expected) in cases) {
            val configured = com.aiassistant.data.repository.helpers.ApiEndpointHelper.normalizeBaseUrl(input)
            for (service in listOf(RetrofitClient.getService(configured), RetrofitClient.getAnalysisService(configured),
                RetrofitClient.getSummaryService(configured))) {
                val request = service.chatCompletion("Bearer synthetic", com.aiassistant.domain.model.ChatCompletionRequest(
                    model = "synthetic-model", messages = emptyList()
                )).request()
                assertEquals(input, expected, request.url.toString())
            }
        }
    }

    @Test fun genericSearchPostKeepsItsRootWhileAiOperationsUseTheirOwnVersionedAddress() {
        LocalEndpoint { it.reply(body = "synthetic response") }.use { server ->
            val host = "http://127.0.0.1:${server.port}"
            val cases = listOf(
                Triple(host, "search", "/search"),
                Triple("$host/", "search", "/search"),
                Triple("$host/gateway/", "/search", "/gateway/search"),
                Triple(host, "chat/completions", "/v1/chat/completions"),
                Triple("$host/chat/completions", "chat/completions", "/chat/completions"),
                Triple("$host/v1beta/openai/v1", "chat/completions", "/v1beta/openai/chat/completions"),
                Triple("$host/v1/messages", "messages", "/v1/messages")
            )
            for ((base, operation, expectedPath) in cases) {
                RetrofitClient.postJson(base, operation, mapOf("Authorization" to "Bearer synthetic"), "{}").use {
                    assertEquals("synthetic response", it.body!!.string())
                }
                assertEquals("通用搜索不能被 AI /v1 默认规则改写", "POST $expectedPath HTTP/1.1", server.requestLines.last())
            }
            RetrofitClient.getJson(host, "models", emptyMap()).use { assertEquals(200, it.code) }
            assertEquals("GET /v1/models HTTP/1.1", server.requestLines.last())
            assertEquals(cases.size + 1, server.requests.get())
        }
    }

    @Test fun httpAddressRulesMatchExactOriginAndPathBoundaries() {
        val policy = com.aiassistant.data.remote.HttpAccessPolicy
        val rules = com.aiassistant.data.remote.HttpAccessRules(true, listOf("http://192.0.2.1:8080/v1"))
        fun permits(url: String) = policy.permits(okhttp3.HttpUrl.Companion.run { url.toHttpUrl() }, rules)
        assertTrue(permits("http://192.0.2.1:8080/v1"))
        assertTrue(permits("http://192.0.2.1:8080/v1/models?limit=10"))
        assertTrue(permits("http://192.0.2.1:8080/v1/chat/completions"))
        assertFalse(permits("http://192.0.2.1:8081/v1/models"))
        assertFalse(permits("http://192.0.2.2:8080/v1/models"))
        assertFalse(permits("http://192.0.2.1:8080/v10/models"))
        assertFalse(permits("http://192.0.2.1:8080/admin"))
        assertFalse(permits("http://192.0.2.1:8080/v1/../admin"))
        assertFalse(permits("http://192.0.2.1:8080/v1/a%2f..%2f..%2fadmin"))
        assertTrue(permits("https://unlisted.example.test/v1/models"))
        val domainRules = com.aiassistant.data.remote.HttpAccessRules(true, listOf("http://proxy.example.test:8045/v1"))
        assertFalse(policy.permits(okhttp3.HttpUrl.Companion.run { "http://proxy.example.test.evil.test:8045/v1".toHttpUrl() }, domainRules))
        assertFalse(policy.permits(okhttp3.HttpUrl.Companion.run { "http://sub.proxy.example.test:8045/v1".toHttpUrl() }, domainRules))
        assertEquals("http://proxy.example.test:8045/v1", policy.normalizeAddress(" HTTP://PROXY.EXAMPLE.TEST:8045/v1/ "))
        assertEquals("http://[::1]:8045/v1", policy.normalizeAddress("[::1]:8045/v1"))
        for (invalid in listOf("", "http://*.test", "https://proxy.test", "http://u:p@proxy.test", "http://proxy.test?key=secret", "http://proxy.test/#x", "http://proxy.test:99999", "http://a b", "http://proxy.test/v1%2fadmin", "ftp://proxy.test")) {
            try { policy.normalizeAddress(invalid); fail("Invalid allow address: $invalid") } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun dynamicHttpPolicyBlocksBeforeSendingAndClosesPreviouslyPooledClients() {
        val policy = com.aiassistant.data.remote.HttpAccessPolicy
        LocalEndpoint { it.reply(body = "ok") }.use { server ->
            var rules = com.aiassistant.data.remote.HttpAccessRules()
            policy.initialize { rules }
            for (client in listOf(RetrofitClient.restHttpClient, RetrofitClient.streamHttpClient,
                RetrofitClient.summaryHttpClient, RetrofitClient.longAnalysisHttpClient)) {
                try { client.newCall(request(server.port)).execute().close(); fail("Closed HTTP policy sent a request") }
                catch (e: com.aiassistant.data.remote.HttpAccessDeniedException) { assertTrue(e.message!!.contains("HTTP 访问已关闭")) }
            }
            assertEquals(0, server.requests.get())
            rules = com.aiassistant.data.remote.HttpAccessRules(true, listOf("http://127.0.0.1:${server.port}/v1"))
            RetrofitClient.getJson("http://127.0.0.1:${server.port}/v1", "models", mapOf("Authorization" to "Bearer synthetic")).use { assertEquals("ok", it.body!!.string()) }
            assertEquals(1, server.requests.get())
            rules = rules.copy(addresses = emptyList())
            try { RetrofitClient.restHttpClient.newCall(request(server.port)).execute().close(); fail("Removed address still allowed") }
            catch (_: com.aiassistant.data.remote.HttpAccessDeniedException) { }
            assertEquals(1, server.requests.get())
            rules = rules.copy(enabled = false)
            val https = okhttp3.HttpUrl.Companion.run { "https://example.test".toHttpUrl() }
            policy.check(https)
            try { policy.check(okhttp3.HttpUrl.Companion.run { "http://127.0.0.1:${server.port}/v1?apiKey=do-not-log".toHttpUrl() }); fail("HTTP must be denied") }
            catch (e: com.aiassistant.data.remote.HttpAccessDeniedException) { assertFalse(e.message!!.contains("do-not-log")) }
            assertFalse(com.aiassistant.domain.model.RetryPolicy(mapOf(com.aiassistant.domain.model.RetryErrorType.OTHER to com.aiassistant.domain.model.RetryRule(true, 20))).canRetry(com.aiassistant.data.remote.HttpAccessDeniedException("blocked"), 0))
        }
    }

    @Test fun httpRedirectCannotEscapeAllowedOriginOrPath() {
        val policy = com.aiassistant.data.remote.HttpAccessPolicy
        LocalEndpoint { it.reply(body = "must not receive credentials") }.use { denied ->
            LocalEndpoint { it.reply("302 Found", body = "", extraHeaders = "Location: http://127.0.0.1:${denied.port}/v1/models\r\n") }.use { allowed ->
                policy.initialize { com.aiassistant.data.remote.HttpAccessRules(true, listOf("http://127.0.0.1:${allowed.port}/v1")) }
                try { RetrofitClient.getJson("http://127.0.0.1:${allowed.port}/v1", "models", mapOf("Authorization" to "Bearer synthetic")).close(); fail("Redirect escaped whitelist") }
                catch (_: com.aiassistant.data.remote.HttpAccessDeniedException) { }
                assertEquals(1, allowed.requests.get()); assertEquals(0, denied.requests.get())
            }
        }
        LocalEndpoint { it.reply("302 Found", body = "", extraHeaders = "Location: /admin\r\n") }.use { allowed ->
            policy.initialize { com.aiassistant.data.remote.HttpAccessRules(true, listOf("http://127.0.0.1:${allowed.port}/v1")) }
            try { RetrofitClient.getJson("http://127.0.0.1:${allowed.port}/v1", "models", emptyMap()).close(); fail("Redirect escaped path scope") }
            catch (_: com.aiassistant.data.remote.HttpAccessDeniedException) { }
            assertEquals(1, allowed.requests.get())
        }
    }

    @Test fun allowedRelativeRedirectWorksButHttpsDowngradeIsAlwaysRefused() {
        val policy = com.aiassistant.data.remote.HttpAccessPolicy
        LocalEndpoint { it.reply(body = "ok") }.use { target ->
            LocalEndpoint { it.reply("302 Found", body = "", extraHeaders = "Location: http://127.0.0.1:${target.port}/v1/models\r\n") }.use { source ->
                policy.initialize { com.aiassistant.data.remote.HttpAccessRules(true, listOf("http://127.0.0.1:${source.port}/v1", "http://127.0.0.1:${target.port}/v1")) }
                RetrofitClient.getJson("http://127.0.0.1:${source.port}/v1", "models", emptyMap()).use { assertEquals("ok", it.body!!.string()) }
                assertEquals(1, source.requests.get()); assertEquals(1, target.requests.get())
                try { policy.checkRedirect(okhttp3.HttpUrl.Companion.run { "https://proxy.test/v1".toHttpUrl() }, "http://127.0.0.1:${target.port}/v1/models"); fail("HTTPS downgrade permitted") }
                catch (e: com.aiassistant.data.remote.HttpAccessDeniedException) { assertTrue(e.message!!.contains("HTTPS 自动重定向")) }
                policy.checkRedirect(okhttp3.HttpUrl.Companion.run { "https://proxy.test/v1".toHttpUrl() }, "https://other.test/models")
            }
        }
    }
    @Test fun socketCancellationInsideCallTimeoutRemainsFailureWhileExplicitUserCancellationWins() {
        val timeout = InterruptedIOException("timeout").apply { initCause(java.io.IOException("Canceled")) }
        val wrapped = IllegalStateException("summary request failed", timeout)
        for (error in listOf(timeout, wrapped)) {
            assertTrue(AiRepository.isTimeoutException(error))
            assertFalse("OkHttp内部终止socket不代表用户取消", AiRepository.isRequestCancellation(error))
            assertEquals(com.aiassistant.domain.model.RetryErrorType.TIMEOUT,
                com.aiassistant.domain.model.RetryErrorType.classify(error))
        }
        val userStop = kotlinx.coroutines.CancellationException("user stopped", timeout)
        for (error in listOf(userStop, IllegalStateException("wrapped user stop", userStop), java.io.IOException("Canceled"))) {
            assertTrue("显式取消必须继续阻止重试", AiRepository.isRequestCancellation(error))
            assertFalse(com.aiassistant.domain.model.RetryPolicy().canRetry(error, 0))
        }
    }

    @Test fun firstTokenDeadlineCannotConvertUserStopIntoTimeout() {
        LocalEndpoint { Thread.sleep(1000) }.use { server ->
            val call = RetrofitClient.streamHttpClient.newCall(request(server.port))
            com.aiassistant.data.repository.helpers.FirstTokenDeadline(100).use { deadline ->
                call.cancel()
                deadline.start(call)
                Thread.sleep(200)
                val stopped = java.io.IOException("Canceled")
                assertSame(stopped, deadline.failure(stopped))
                assertTrue(AiRepository.isRequestCancellation(deadline.failure(stopped)))
                assertFalse(com.aiassistant.domain.model.RetryPolicy().canRetry(deadline.failure(stopped), 0))
            }
            assertEquals(0, server.requests.get())
        }
    }

    @Test fun firstTokenDeadlineExpiresBlockedHeadersAndIsClassifiedAsTimeout() {
        LocalEndpoint { Thread.sleep(1000) }.use { server ->
            val call = RetrofitClient.streamHttpClient.newCall(request(server.port))
            com.aiassistant.data.repository.helpers.FirstTokenDeadline(300).use { deadline ->
                deadline.start(call)
                try { call.execute().close(); fail("Must time out") } catch (e: java.io.IOException) {
                    val failure = deadline.failure(e)
                    assertTrue(failure is SocketTimeoutException)
                    assertEquals(com.aiassistant.domain.model.RetryErrorType.TIMEOUT, com.aiassistant.domain.model.RetryErrorType.classify(failure))
                    assertFalse(AiRepository.isRequestCancellation(failure))
                }
            }
            assertEquals(1, server.requests.get())
        }
    }
    @Test fun concurrentRestAndAnalysisServicesKeepTheirOwnEndpointSnapshots() {
        val executor = Executors.newFixedThreadPool(8)
        try {
            val futures = (0 until 8).map { worker -> executor.submit {
                repeat(40) { round ->
                    val base = "http://127.0.0.1:${20000 + worker * 100 + round}/v1/"
                    for (service in listOf(RetrofitClient.getService(base), RetrofitClient.getAnalysisService(base))) {
                        val request = service.chatCompletion("Bearer test-only", com.aiassistant.domain.model.ChatCompletionRequest(
                            model = "vendor/Exact-ID", messages = emptyList(), stream = false
                        )).request()
                        assertEquals(base + "chat/completions", request.url.toString())
                    }
                }
            } }
            futures.forEach { it.get(20, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
    }
    private class LocalEndpoint(private val respond: (Socket) -> Unit) : AutoCloseable {
        private val server = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
        private val executor = Executors.newSingleThreadExecutor()
        val requests = AtomicInteger()
        val requestLines = CopyOnWriteArrayList<String>()
        val payloads = CopyOnWriteArrayList<String>()
        val port: Int get() = server.localPort
        init {
            HttpTestAccess.allow("http://127.0.0.1:$port")
            HttpTestAccess.allow("http://model.test:$port")
            executor.submit {
                while (!server.isClosed) {
                    try {
                        server.accept().use { socket ->
                            socket.soTimeout = 2_000
                            val reader = socket.getInputStream().bufferedReader()
                            requestLines.add(reader.readLine())
                            var length = 0
                            while (true) {
                                val line = reader.readLine() ?: break
                                if (line.isEmpty()) break
                                if (line.startsWith("Content-Length:", true)) length = line.substringAfter(':').trim().toInt()
                            }
                            val payload = CharArray(length)
                            var read = 0
                            while (read < length) {
                                val count = reader.read(payload, read, length - read)
                                if (count < 0) break
                                read += count
                            }
                            payloads.add(String(payload, 0, read))
                            requests.incrementAndGet()
                            respond(socket)
                        }
                    } catch (_: java.io.IOException) {
                        if (server.isClosed) break
                    }
                }
            }
        }
        override fun close() {
            server.close()
            executor.shutdownNow()
            executor.awaitTermination(3, TimeUnit.SECONDS)
        }
    }

    private fun Socket.reply(status: String = "200 OK", body: String = "data: {\"choices\":[{\"delta\":{\"content\":\"ok\"}}]}\n\ndata: [DONE]\n\n", extraHeaders: String = "") {
        val bytes = body.toByteArray(Charsets.UTF_8)
        getOutputStream().apply {
            write("HTTP/1.1 $status\r\nContent-Type: text/event-stream\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n$extraHeaders\r\n".toByteArray())
            write(bytes)
            flush()
        }
    }

    private fun request(port: Int, host: String = "127.0.0.1") = Request.Builder()
        .url("http://$host:$port/v1/chat/completions")
        .post("{\"model\":\"gpt-6.1-sol\",\"stream\":true}".toRequestBody())
        .build()

    @Test fun connectionFallsBackToSecondAddressBeforeSendingPost() {
        LocalEndpoint { it.reply() }.use { server ->
            val client = RetrofitClient.streamHttpClient.newBuilder()
                .dns(object : Dns {
                    override fun lookup(hostname: String) = listOf(InetAddress.getByName("127.0.0.2"), InetAddress.getByName("127.0.0.1"))
                })
                .connectTimeout(300, TimeUnit.MILLISECONDS).readTimeout(2, TimeUnit.SECONDS).build()
            client.newCall(request(server.port, "model.test")).execute().use { response ->
                assertEquals(200, response.code)
                assertTrue(response.body!!.string().contains("[DONE]"))
            }
            assertEquals("Only the reachable address receives the model POST", 1, server.requests.get())
        }
    }

    @Test fun allAiClientsRecoverBeforeSendingButDoNotReplaySentPosts() {
        for (base in listOf(RetrofitClient.restHttpClient, RetrofitClient.summaryHttpClient,
            RetrofitClient.longAnalysisHttpClient)) {
            LocalEndpoint { it.reply(body = "ok") }.use { server ->
                val client = base.newBuilder().dns(object : Dns {
                    override fun lookup(hostname: String) = listOf(InetAddress.getByName("127.0.0.2"), InetAddress.getByName("127.0.0.1"))
                }).connectTimeout(300, TimeUnit.MILLISECONDS).build()
                client.newCall(request(server.port, "model.test")).execute().use { response ->
                    assertEquals("ok", response.body!!.string())
                }
                assertEquals(1, server.requests.get())
            }
            LocalEndpoint { }.use { server ->
                try { base.newCall(request(server.port)).execute().close(); fail("Disconnect must fail") }
                catch (_: java.io.IOException) { }
                assertEquals(1, server.requests.get())
            }
        }
    }

    @Test fun actualPostJsonDeliversFirstDeltaBeforeStreamClosesForBothProtocols() {
        val protocols = listOf(
            "chat/completions" to "data: {\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}\n\n",
            "messages" to "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"hello\"}}\n\n"
        )
        for ((path, firstChunk) in protocols) {
            val release = CountDownLatch(1)
            val tail = if (path == "messages") "data: {\"type\":\"message_stop\"}\n\n" else "data: [DONE]\n\n"
            val worker = Executors.newSingleThreadExecutor()
            LocalEndpoint { socket ->
                val length = (firstChunk + tail).toByteArray().size
                val output = socket.getOutputStream()
                output.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Length: $length\r\nConnection: close\r\n\r\n$firstChunk".toByteArray())
                output.flush()
                if (release.await(3, TimeUnit.SECONDS)) { output.write(tail.toByteArray()); output.flush() }
            }.use { server ->
                try {
                    val json = "{\"model\":\"gpt-6.1-sol\",\"stream\":true}"
                    val result = worker.submit<String> {
                        RetrofitClient.postJson("http://127.0.0.1:${server.port}/v1", path,
                            mapOf("Accept" to "text/event-stream", "Authorization" to "Bearer test-only"), json).use { response ->
                            assertEquals(200, response.code)
                            assertEquals(TimeUnit.SECONDS.toNanos(600), response.body!!.source().timeout().timeoutNanos())
                            response.body!!.byteStream().bufferedReader().readLine()
                        }
                    }
                    val line = result.get(2, TimeUnit.SECONDS)
                    if (path == "messages") {
                        val event = com.google.gson.Gson().fromJson(line.removePrefix("data: "), com.aiassistant.domain.model.AnthropicStreamEvent::class.java)
                        assertEquals("hello", event.delta!!.text)
                    } else assertEquals("hello", AiRepository.parseOpenAiStreamLine(line)!!.contentDelta)
                    assertEquals("POST /v1/$path HTTP/1.1", server.requestLines.single())
                    assertEquals(json, server.payloads.single())
                    assertEquals(1, server.requests.get())
                } finally { release.countDown(); worker.shutdownNow() }
            }
        }
    }

    @Test fun receivedPostIsNeverSilentlyReplayedAfterDisconnect() {
        LocalEndpoint { /* Close after reading the complete POST, without response headers. */ }.use { server ->
            val client = RetrofitClient.streamHttpClient.newBuilder().readTimeout(1, TimeUnit.SECONDS).build()
            try {
                client.newCall(request(server.port)).execute().close()
                fail("A disconnected response must fail")
            } catch (_: java.io.IOException) { }
            assertEquals(1, server.requests.get())
        }
    }

    @Test fun httpRetryResponsesRemainVisibleToExplicitPolicy() {
        for (status in listOf("408 Request Timeout", "503 Service Unavailable")) {
            LocalEndpoint { it.reply(status, "error", "Retry-After: 0\r\n") }.use { server ->
                RetrofitClient.streamHttpClient.newCall(request(server.port)).execute().use { response ->
                    assertEquals(status.substringBefore(' ').toInt(), response.code)
                    assertEquals("error", response.body!!.string())
                }
                assertEquals("HTTP retry must be owned by RetryPolicy", 1, server.requests.get())
            }
        }
    }

    @Test fun responseHeaderTimeoutIsFiniteAndClassifiedForRetry() {
        LocalEndpoint { socket -> Thread.sleep(600); socket.reply() }.use { server ->
            val client = RetrofitClient.streamHttpClient.newBuilder().readTimeout(100, TimeUnit.MILLISECONDS).build()
            try {
                client.newCall(request(server.port)).execute().close()
                fail("A silent server must time out")
            } catch (e: SocketTimeoutException) {
                assertEquals(com.aiassistant.domain.model.RetryErrorType.TIMEOUT, com.aiassistant.domain.model.RetryErrorType.classify(e))
            }
            assertEquals(1, server.requests.get())
        }
    }

    @Test fun cancellingBlockedHeaderReadUnblocksCallWithoutReplay() {
        LocalEndpoint { Thread.sleep(700) }.use { server ->
            val client = RetrofitClient.streamHttpClient.newBuilder().readTimeout(2, TimeUnit.SECONDS).build()
            val call = client.newCall(request(server.port))
            val worker = Executors.newSingleThreadExecutor()
            try {
                val result = worker.submit<Boolean> {
                    try { call.execute().close(); false } catch (_: java.io.IOException) { call.isCanceled() }
                }
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
                while (server.requests.get() == 0 && System.nanoTime() < deadline) Thread.sleep(10)
                assertEquals(1, server.requests.get())
                call.cancel()
                assertTrue(result.get(2, TimeUnit.SECONDS))
                assertEquals(1, server.requests.get())
            } finally { worker.shutdownNow() }
        }
    }

    @Test
    fun retryClassifiesActualApiExceptionsBeforeBodyText() {
        val types = mapOf(400 to "BAD_REQUEST", 401 to "AUTHENTICATION", 403 to "PERMISSION", 404 to "NOT_FOUND", 408 to "REQUEST_TIMEOUT", 409 to "CONFLICT", 413 to "TOO_LARGE", 422 to "INVALID_PARAMETERS", 429 to "RATE_LIMIT", 500 to "SERVER", 502 to "BAD_GATEWAY", 503 to "UNAVAILABLE", 504 to "GATEWAY_TIMEOUT", 418 to "OTHER_HTTP")
        types.forEach { (code, name) ->
            assertEquals(name, com.aiassistant.domain.model.RetryErrorType.classify(com.aiassistant.data.repository.ApiException(code, "API错误 ($code): timeout in provider" )).name)
            assertEquals(name, com.aiassistant.domain.model.RetryErrorType.classify(Exception("HTTP $code: error")).name)
        }
        assertEquals(com.aiassistant.domain.model.RetryErrorType.EMPTY_RESPONSE, com.aiassistant.domain.model.RetryErrorType.classify(com.aiassistant.data.repository.ApiException(500, "empty response detected")))
        assertEquals(com.aiassistant.domain.model.RetryErrorType.TLS, com.aiassistant.domain.model.RetryErrorType.classify(javax.net.ssl.SSLException("certificate invalid")))
    }

    @Test
    fun retryRulesRespectCountDisabledCancellationAndPartialOutput() {
        val timeout = SocketTimeoutException("timeout")
        val policy = com.aiassistant.domain.model.RetryPolicy()
        assertTrue(policy.canRetry(timeout, 0))
        assertTrue(policy.canRetry(timeout, 2))
        assertFalse(policy.canRetry(timeout, 3))
        assertFalse(policy.canRetry(timeout, 0, hasOutput = true))
        assertFalse(policy.canRetry(kotlinx.coroutines.CancellationException("stop"), 0))
        assertFalse(policy.canRetry(Exception("HTTP 401: invalid"), 0))
        val custom = com.aiassistant.domain.model.RetryPolicy(mapOf(com.aiassistant.domain.model.RetryErrorType.AUTHENTICATION to com.aiassistant.domain.model.RetryRule(true, 2), com.aiassistant.domain.model.RetryErrorType.TIMEOUT to com.aiassistant.domain.model.RetryRule(false, 20)))
        assertTrue(custom.canRetry(Exception("HTTP 401: invalid"), 1))
        assertFalse(custom.canRetry(Exception("HTTP 401: invalid"), 2))
        assertFalse(custom.canRetry(timeout, 0))
        assertEquals(5_000L, custom.delayMillis(19))
    }

    // 1. 网络稳定性测试：验证 OkHttpClient 的 fastFallback、pingInterval、连接池配置及合规 User-Agent
    @Test
    fun testNetworkStabilityConfiguration() {
        val streamClient = RetrofitClient.streamHttpClient
        assertTrue("请求发送前必须允许连接地址回退", streamClient.retryOnConnectionFailure)
        assertTrue(RetrofitClient.longAnalysisHttpClient.retryOnConnectionFailure)
        assertEquals(120_000, streamClient.readTimeoutMillis)
        assertEquals(600L, RetrofitClient.STREAM_IDLE_TIMEOUT_SECONDS)
        assertEquals("streamHttpClient 应配置 15s 的 HTTP/2 pingInterval 保活心跳", 15_000, streamClient.pingIntervalMillis)
        assertNotNull("streamHttpClient 应配置专用连接池", streamClient.connectionPool)

        val restClient = RetrofitClient.restHttpClient
        assertTrue(restClient.retryOnConnectionFailure)
        assertNotNull("restClient 应配置专用连接池", restClient.connectionPool)

        val summaryClient = RetrofitClient.summaryHttpClient
        assertTrue("summaryHttpClient 应允许发送前地址回退", summaryClient.retryOnConnectionFailure)
        assertEquals(RetrofitClient.SUMMARY_CONNECT_TIMEOUT_SECONDS * 1_000,
            summaryClient.connectTimeoutMillis.toLong())
        assertEquals(RetrofitClient.SUMMARY_RESPONSE_TIMEOUT_SECONDS * 1_000,
            summaryClient.readTimeoutMillis.toLong())
        assertEquals(RetrofitClient.SUMMARY_CALL_TIMEOUT_SECONDS * 1_000,
            summaryClient.callTimeoutMillis.toLong())
        assertNotNull("summaryHttpClient 应配置专用连接池", summaryClient.connectionPool)

        assertEquals("默认 User-Agent 必须使用当前应用版本", "Echo-Assistant/${BuildConfig.VERSION_NAME} (Android; Mobile)", RetrofitClient.DEFAULT_USER_AGENT)
    }

    // 2. 异常判定测试：验证协议抖动与超时异常均被正确识别为网络波动以触发退避重试
    @Test
    fun testNetworkFluctuationExceptionIdentification() {
        assertTrue("SocketTimeoutException 应被判定为网络波动", AiRepository.isNetworkFluctuationException(SocketTimeoutException("Read timed out")))
        assertTrue("ProtocolException 应被判定为网络波动", AiRepository.isNetworkFluctuationException(ProtocolException("unexpected end of stream")))
        assertTrue("包含 http2 stream reset 的异常应被判定为网络波动", AiRepository.isNetworkFluctuationException(Exception("HTTP2 stream was reset by peer")))
        assertTrue("SSL 握手抖动应被判定为网络波动", AiRepository.isNetworkFluctuationException(Exception("SSL handshake failed due to timeout")))
        assertFalse("业务逻辑参数错误不应被判定为网络波动", AiRepository.isNetworkFluctuationException(IllegalArgumentException("Invalid model name")))
    }

    // 3. 沉浸式记忆与去元词汇测试：彻底消除“用户把AI当成心爱的哥哥”等出戏表述
    @Test
    fun testSanitizeMetaLanguageRemovesImmersionBreakingTerms() {
        // 典型出戏文本输入
        val rawInput1 = "用户把AI当成心爱的哥哥"
        val sanitized1 = SmartMemoryExtractor.sanitizeMetaLanguage(rawInput1)
        assertEquals("视对方为心爱的哥哥", sanitized1)
        assertFalse("净化后绝对不可包含'用户把AI当成'", sanitized1.contains("用户把AI当成"))
        assertFalse("净化后绝对不可包含'AI'", sanitized1.contains("AI"))

        val rawInput2 = "把模型当做最好的朋友"
        val sanitized2 = SmartMemoryExtractor.sanitizeMetaLanguage(rawInput2)
        assertEquals("视对方为最好的朋友", sanitized2)
        assertFalse("净化后不应出现'模型'", sanitized2.contains("模型"))

        val rawInput3 = "用户要求AI在回答时保持温柔"
        val sanitized3 = SmartMemoryExtractor.sanitizeMetaLanguage(rawInput3)
        assertEquals("要求在互动中在回答时保持温柔", sanitized3)

        // 验证提炼管线 refineMemoryContent
        val (distilled, category) = SmartMemoryExtractor.refineMemoryContent("根据上述对话提炼出如下核心事实：用户把AI当成心爱的哥哥")
        assertEquals("角色设定：视对方为心爱的哥哥", distilled)
        assertEquals("PROJECT", category)
        assertFalse(distilled.contains("用户把AI"))
    }

    // 4. 辅助模型提炼提示词模板测试：验证严格沉浸感准则
    @Test
    fun testAuxiliaryMemoryPromptImmersionRules() {
        val prompt = PersonalizationManager.DEFAULT_AUXILIARY_MEMORY_PROMPT
        assertTrue("提炼提示词必须强调沉浸感最高准则", prompt.contains("沉浸感最高准则"))
        assertTrue("提炼提示词必须明文严禁用户、AI、模型等元词汇", prompt.contains("严禁在提炼的事实中出现“用户”、“AI”、“模型”"))
        assertTrue("提炼提示词必须要求规范角色扮演关系表述", prompt.contains("视对方为心爱的哥哥"))
    }

    // 5. 多 Key 错误汇总展示测试
    @Test
    fun testMultiKeyCompositeErrorReporting() {
        val failures = listOf(
            KeyAttemptFailure(1, "...4a8b", "HTTP 401 Unauthorized - 密钥无效"),
            KeyAttemptFailure(2, "...9c12", "HTTP 429 Too Many Requests - 频率超限"),
            KeyAttemptFailure(3, "...7e3f", "网络连接超时 (SocketTimeoutException 30s)", isTimeout = true)
        )

        val compositeMessage = buildString {
            append("所有 API Key 均请求失败 (共尝试 ${failures.size} 个 Key)：\n")
            failures.forEach { failure ->
                append("• Key #${failure.keyIndex} (${failure.keyMasked})：${failure.errorMessage}\n")
            }
            append("\n建议检查 API 地址、网络连接或对应 Key 的额度与可用状态。")
        }.trim()

        assertTrue(compositeMessage.contains("共尝试 3 个 Key"))
        assertTrue(compositeMessage.contains("Key #1 (...4a8b)：HTTP 401"))
        assertTrue(compositeMessage.contains("Key #2 (...9c12)：HTTP 429"))
        assertTrue(compositeMessage.contains("Key #3 (...7e3f)：网络连接超时"))
    }

    // 6. 暂停生成时保留前序 Key 报错原因测试
    @Test
    fun testRetainKeyErrorsWhenStoppedByUser() {
        val attemptErrors = mutableListOf(
            "Key #1 (...4a8b)：HTTP 401 Unauthorized",
            "Key #2 (...9c12)：网络连接超时 (SocketTimeoutException)"
        )

        val finalContent = buildString {
            append("回复已停止 (用户已暂停)\n\n")
            append("【已尝试 Key 报错记录】：\n")
            attemptErrors.forEach { append("• $it\n") }
            append("\n*(在尝试后续 Key 期间，用户主动暂停了回复)*")
        }.trim()

        assertTrue(finalContent.contains("回复已停止 (用户已暂停)"))
        assertTrue(finalContent.contains("【已尝试 Key 报错记录】："))
        assertTrue(finalContent.contains("• Key #1 (...4a8b)：HTTP 401"))
        assertTrue(finalContent.contains("• Key #2 (...9c12)：网络连接超时"))
        assertTrue(finalContent.contains("用户主动暂停了回复"))
    }
}
