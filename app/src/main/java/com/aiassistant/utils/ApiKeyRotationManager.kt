package com.aiassistant.utils

import android.content.Context
import android.content.SharedPreferences
import com.aiassistant.data.remote.HttpAccessDeniedException
import com.aiassistant.data.repository.ApiException
import com.aiassistant.data.repository.helpers.ApiEndpointHelper
import com.aiassistant.data.repository.helpers.NetworkExceptionClassifier
import com.aiassistant.domain.model.ApiKeyRotationSettings
import com.google.gson.JsonParser
import retrofit2.HttpException
import java.security.MessageDigest
import java.util.Locale

class ApiKeyCoolingDownException(val retryAfterSeconds: Long) : IllegalStateException(
    "所有启用的 API Key 都在冷却中，约 ${retryAfterSeconds} 秒后可重试；也可在此 API 的轮询设置中重置健康记录。"
)

/**
 * 请求之间共享的 Key 健康状态。配置、游标、成功偏好与健康记录均持久化；
 * 存储标识仅由地址、协议及 Key 的 SHA-256 摘要组成，不保存明文地址或 Key。
 * 调用方负责过滤禁用 Key，并在每次实际网络请求完成后记录一次结果。
 */
class ApiKeyRotationManager(context: Context, private val nowMillis: () -> Long = System::currentTimeMillis) {
    private val prefs = (context.applicationContext ?: context)
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun getSettings(baseUrl: String, apiType: String): ApiKeyRotationSettings = synchronized(lock) {
        readSettings(scope(baseUrl, apiType), prefs.all)
    }

    fun saveSettings(baseUrl: String, apiType: String, settings: ApiKeyRotationSettings): Boolean = synchronized(lock) {
        val prefix = settingsPrefix(scope(baseUrl, apiType))
        val safe = settings.normalized()
        prefs.edit()
            .putBoolean(prefix + "enabled", safe.enabled)
            .putString(prefix + "mode", safe.mode)
            .putBoolean(prefix + "cooldownEnabled", safe.cooldownEnabled)
            .putInt(prefix + "failuresBeforeCooldown", safe.failuresBeforeCooldown)
            .putLong(prefix + "cooldownSeconds", safe.cooldownSeconds)
            .putBoolean(prefix + "exponentialBackoff", safe.exponentialBackoff)
            .putLong(prefix + "maxCooldownSeconds", safe.maxCooldownSeconds)
            .putLong(prefix + "failureResetSeconds", safe.failureResetSeconds)
            .putBoolean(prefix + "deprioritizeFailures", safe.deprioritizeFailures)
            .putBoolean(prefix + "resetOnSuccess", safe.resetOnSuccess)
            .putBoolean(prefix + "probeWhenAllCooling", safe.probeWhenAllCooling)
            .commit()
    }

    /** 仅重置本地址/协议的健康记录与选择状态，保留轮询设置。 */
    fun resetHealth(baseUrl: String, apiType: String): Boolean = synchronized(lock) {
        val scope = scope(baseUrl, apiType)
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(healthPrefix(scope)) }.forEach(editor::remove)
        editor.remove(cursorKey(scope)).remove(preferredKey(scope)).commit()
    }

    fun orderedKeys(baseUrl: String, apiType: String, keys: List<String>, allowFallback: Boolean = true): List<String> = synchronized(lock) {
        val unique = keys.map(String::trim).filter(String::isNotEmpty).distinctBy(::keyId)
        if (unique.isEmpty()) return@synchronized emptyList()
        val scope = scope(baseUrl, apiType)
        val snapshot = prefs.all
        val settings = readSettings(scope, snapshot)
        // 关闭时始终使用列表中的首个启用 Key，不轮换、不自动切换，也不受旧冷却记录拦截。
        if (!settings.enabled) return@synchronized unique.take(1)
        val now = nowMillis().coerceAtLeast(0)
        val editor = prefs.edit()
        val candidates = unique.map { key ->
            val id = keyId(key)
            Candidate(key, id, readHealth(scope, id, snapshot, settings, now, editor))
        }
        val available = candidates.filter { !settings.cooldownEnabled || it.health.coolingUntil <= now }
        if (available.isEmpty()) {
            editor.commit()
            val earliest = candidates.minBy { it.health.coolingUntil }
            if (!settings.probeWhenAllCooling) {
                throw ApiKeyCoolingDownException(((earliest.health.coolingUntil - now - 1) / 1000 + 1).coerceAtLeast(1))
            }
            // 探测只允许一个 Key，避免一次请求遍历仍在冷却的全部 Key。
            advanceCursor(scope, snapshot, editor).commit()
            return@synchronized listOf(earliest.key)
        }
        val cursor = (snapshot[cursorKey(scope)] as? Int)?.coerceAtLeast(0) ?: 0
        val preferred = snapshot[preferredKey(scope)] as? String
        val tiers = if (settings.deprioritizeFailures) available.groupBy { it.health.failures }.toSortedMap().values
            else listOf(available)
        val ordered = tiers.flatMap { tier ->
            val rotated = if (settings.mode == ApiKeyRotationSettings.ORDERED) tier else {
                val start = cursor % tier.size
                tier.drop(start) + tier.take(start)
            }
            if (settings.mode == ApiKeyRotationSettings.PREFER_SUCCESS) rotated.sortedBy { it.id != preferred } else rotated
        }.map { it.key }
        // 每个请求推进一次游标，同一健康层内公平轮询，不受低优先级 Key 数量影响。
        advanceCursor(scope, snapshot, editor).commit()
        if (allowFallback) ordered else ordered.take(1)
    }

    fun isCoolingDown(baseUrl: String, apiType: String, key: String): Boolean = synchronized(lock) {
        if (key.isBlank()) return@synchronized false
        val scope = scope(baseUrl, apiType)
        val snapshot = prefs.all
        val settings = readSettings(scope, snapshot)
        if (!settings.enabled || !settings.cooldownEnabled) return@synchronized false
        val now = nowMillis().coerceAtLeast(0)
        val editor = prefs.edit()
        val health = readHealth(scope, keyId(key), snapshot, settings, now, editor)
        editor.commit()
        health.coolingUntil > now
    }

    fun recordSuccess(baseUrl: String, apiType: String, key: String) = synchronized(lock) {
        if (key.isBlank()) return@synchronized
        val scope = scope(baseUrl, apiType)
        val id = keyId(key)
        val snapshot = prefs.all
        val settings = readSettings(scope, snapshot)
        if (!settings.enabled) return@synchronized
        val editor = prefs.edit().putString(preferredKey(scope), id)
        if (settings.resetOnSuccess) removeHealth(editor, scope, id)
        else editor.remove(healthPrefix(scope, id) + "coolingUntil")
        editor.commit()
        Unit
    }

    /** true 表示此次异常与 Key 健康相关且已成功保存；不保存错误内容。 */
    fun recordFailure(baseUrl: String, apiType: String, key: String, error: Throwable): Boolean = synchronized(lock) {
        if (key.isBlank() || !isKeyFailure(error)) return@synchronized false
        val scope = scope(baseUrl, apiType)
        val id = keyId(key)
        val snapshot = prefs.all
        val settings = readSettings(scope, snapshot)
        if (!settings.enabled) return@synchronized false
        val now = nowMillis().coerceAtLeast(0)
        val editor = prefs.edit()
        val previous = readHealth(scope, id, snapshot, settings, now, editor)
        val failures = (previous.failures + 1).coerceAtMost(MAX_RECORDED_FAILURES)
        val cooldown = if (settings.cooldownEnabled && failures >= settings.failuresBeforeCooldown) {
            var seconds = settings.cooldownSeconds
            if (settings.exponentialBackoff) {
                repeat((failures - settings.failuresBeforeCooldown).coerceAtMost(31)) {
                    seconds = (seconds * 2).coerceAtMost(settings.maxCooldownSeconds)
                }
            }
            val duration = seconds.coerceAtMost(settings.maxCooldownSeconds) * 1000
            if (now > Long.MAX_VALUE - duration) Long.MAX_VALUE else now + duration
        } else 0L
        val prefix = healthPrefix(scope, id)
        editor.putInt(prefix + "failures", failures)
            .putLong(prefix + "lastFailure", now)
            .putLong(prefix + "coolingUntil", cooldown)
            .commit()
    }

    private fun readSettings(scope: String, snapshot: Map<String, *>): ApiKeyRotationSettings {
        val prefix = settingsPrefix(scope)
        val defaults = ApiKeyRotationSettings()
        return ApiKeyRotationSettings(
            enabled = snapshot[prefix + "enabled"] as? Boolean ?: defaults.enabled,
            mode = snapshot[prefix + "mode"] as? String ?: defaults.mode,
            cooldownEnabled = snapshot[prefix + "cooldownEnabled"] as? Boolean ?: defaults.cooldownEnabled,
            failuresBeforeCooldown = snapshot[prefix + "failuresBeforeCooldown"] as? Int ?: defaults.failuresBeforeCooldown,
            cooldownSeconds = snapshot[prefix + "cooldownSeconds"] as? Long ?: defaults.cooldownSeconds,
            exponentialBackoff = snapshot[prefix + "exponentialBackoff"] as? Boolean ?: defaults.exponentialBackoff,
            maxCooldownSeconds = snapshot[prefix + "maxCooldownSeconds"] as? Long ?: defaults.maxCooldownSeconds,
            failureResetSeconds = snapshot[prefix + "failureResetSeconds"] as? Long ?: defaults.failureResetSeconds,
            deprioritizeFailures = snapshot[prefix + "deprioritizeFailures"] as? Boolean ?: defaults.deprioritizeFailures,
            resetOnSuccess = snapshot[prefix + "resetOnSuccess"] as? Boolean ?: defaults.resetOnSuccess,
            probeWhenAllCooling = snapshot[prefix + "probeWhenAllCooling"] as? Boolean ?: defaults.probeWhenAllCooling
        ).normalized()
    }

    private fun readHealth(scope: String, id: String, snapshot: Map<String, *>, settings: ApiKeyRotationSettings, now: Long, editor: SharedPreferences.Editor): Health {
        val prefix = healthPrefix(scope, id)
        val failures = ((snapshot[prefix + "failures"] as? Int) ?: 0).coerceIn(0, MAX_RECORDED_FAILURES)
        val lastFailure = ((snapshot[prefix + "lastFailure"] as? Long) ?: 0).coerceAtLeast(0)
        if (failures > 0 && settings.failureResetSeconds > 0 && now >= lastFailure &&
            now - lastFailure >= settings.failureResetSeconds * 1000) {
            removeHealth(editor, scope, id)
            return Health()
        }
        return Health(failures, ((snapshot[prefix + "coolingUntil"] as? Long) ?: 0).coerceAtLeast(0))
    }

    private fun removeHealth(editor: SharedPreferences.Editor, scope: String, id: String) {
        val prefix = healthPrefix(scope, id)
        editor.remove(prefix + "failures").remove(prefix + "lastFailure").remove(prefix + "coolingUntil")
    }

    private fun advanceCursor(scope: String, snapshot: Map<String, *>, editor: SharedPreferences.Editor): SharedPreferences.Editor {
        val cursor = (snapshot[cursorKey(scope)] as? Int)?.coerceAtLeast(0) ?: 0
        return editor.putInt(cursorKey(scope), if (cursor == Int.MAX_VALUE) 0 else cursor + 1)
    }

    private fun scope(baseUrl: String, apiType: String): String {
        val protocol = apiType.trim().lowercase(Locale.ROOT).ifBlank { "openai" }
        return hash(protocol + "\n" + ApiEndpointHelper.normalizeBaseUrl(baseUrl, protocol))
    }

    private data class Health(val failures: Int = 0, val coolingUntil: Long = 0)
    private data class Candidate(val key: String, val id: String, val health: Health)

    companion object {
        const val PREFERENCES_NAME = "api_key_rotation"
        private const val MAX_RECORDED_FAILURES = 1000000
        private val lock = Any()
        // Android ICU 会将跨字符类的 [: ... :] 误读为 POSIX 属性；冒号不要放在类首。
        private val httpCode = Regex("(?i)(?:\\bHTTP(?:\\s+error)?\\s*[(:]?\\s*|API错误\\s*[（(:]\\s*)(\\d{3})\\b")
        private val keyFailureCodes = setOf(
            "invalid_api_key", "authentication_error", "authentication_failed", "invalid_authentication",
            "insufficient_quota", "quota_exceeded", "rate_limit_exceeded", "rate_limit_error",
            "billing_hard_limit_reached"
        )
        private val keyFailureCodePrefix = Regex("^(?:${keyFailureCodes.joinToString("|")})(?:$|[\\s:：-])")
        private val credentialFailurePhrase = Regex(
            "\\b(?:invalid|incorrect|expired|revoked)\\s+api[ _-]*key\\b|" +
                "\\bapi[ _-]*key\\s+(?:(?:is|was|has(?: been)?)\\s+)?(?:invalid|incorrect|expired|revoked)\\b|" +
                "\\bauthentication\\s+(?:failed|failure)\\b|" +
                "\\b(?:insufficient\\s+(?:quota|credit|balance)|quota\\s+(?:has\\s+been\\s+)?(?:exceeded|exhausted)|rate\\s+limit\\s+(?:reached|exceeded))\\b|" +
                "(?:api\\s*(?:key|密钥)|密钥)\\s*(?:无效|错误|已?过期|已?撤销)|" +
                "(?:无效|过期|已撤销)的?\\s*api\\s*(?:key|密钥)|" +
                "(?:额度|余额|配额)\\s*(?:不足|已?耗尽|超限|超出(?:限制|上限))|请求(?:频率超限|过于频繁)"
        )

        // 与现有请求层的 Bearer 前缀处理保持一致，仅合并身份，不改实际发送字符串。
        private fun keyId(key: String): String = hash(key.trim().removePrefix("Bearer ").trim())

        private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        private fun settingsPrefix(scope: String) = "settings.$scope."
        private fun healthPrefix(scope: String) = "health.$scope."
        private fun healthPrefix(scope: String, id: String) = "${healthPrefix(scope)}$id."
        private fun cursorKey(scope: String) = "cursor.$scope"
        private fun preferredKey(scope: String) = "preferred.$scope"

        private fun isKeyFailure(error: Throwable): Boolean {
            val chain = generateSequence(error) { it.cause }.take(16).toList()
            if (NetworkExceptionClassifier.isRequestCancellation(error) || chain.any {
                it is HttpAccessDeniedException || it is ApiKeyCoolingDownException || it is IllegalArgumentException
            }) return false
            val text = chain.joinToString(" ") { throwable ->
                throwable.message.orEmpty() + " " + ((throwable as? ApiException)?.errorBody ?: "")
            }.lowercase(Locale.ROOT)
            val code = chain.filterIsInstance<ApiException>().firstOrNull()?.statusCode
                ?: chain.filterIsInstance<HttpException>().firstOrNull()?.code()
                ?: chain.firstNotNullOfOrNull { httpCode.find(it.message.orEmpty())?.groupValues?.get(1)?.toIntOrNull() }
            val tooLarge = listOf("too long", "too large", "exceed", "maximum", "max_length", "context_length", "length limit", "过长", "超出", "超过", "太大").any(text::contains)
            val explicitContextLimit = listOf("context_length", "context length", "context window", "context_window", "上下文", "模型窗口").any(text::contains) && tooLarge
            if (explicitContextLimit) return false
            if (listOf("content_policy", "content policy", "content_filter", "content filter", "blocked_prompt", "prohibited_content", "safety filter", "refusal", "内容拒绝", "内容审核", "安全过滤", "拒绝生成").any(text::contains)) return false
            // 429 常含 token quota exceeded / maximum tokens per minute；这些是
            // Key 的额度或速率限制，不能被宽泛的 token + exceed 规则当作输入超长。
            if (code == 429) return true
            val quotaLimit = listOf("quota", "rate limit", "rate_limit", "tokens per", "token per", "per minute", "per hour", "per day", "额度", "配额", "每分钟", "每小时", "每日", "限流", "速率").any(text::contains)
            val hasContext = listOf("context", "token", "prompt", "input", "上下文", "模型窗口").any(text::contains)
            if (hasContext && tooLarge && !quotaLimit) return false
            // 流式内联错误和 HTTP 200 的 error 对象会被调用方包装为 400；
            // 仅凭结构化机器码或无歧义鉴权/额度短语补充识别，普通参数错误不降权。
            if ((code == null || code in 200..299 || code == 400) &&
                (hasKeyFailureCode(chain) || credentialFailurePhrase.containsMatchIn(text))) return true
            if (code != null) return code in listOf(401, 402, 403, 408, 429) || code in 500..599
            return NetworkExceptionClassifier.isTimeoutException(error) || NetworkExceptionClassifier.isNetworkFluctuationException(error)
        }

        private fun hasKeyFailureCode(chain: List<Throwable>): Boolean = chain.any { throwable ->
            val message = throwable.message.orEmpty().trim().lowercase(Locale.ROOT)
                .removePrefix("api流式返回错误:").removePrefix("anthropic错误:").trim()
            if (keyFailureCodePrefix.containsMatchIn(message)) return@any true
            val body = (throwable as? ApiException)?.errorBody ?: return@any false
            runCatching {
                val root = JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject ?: return@runCatching false
                val nested = root.get("error")?.takeIf { it.isJsonObject }?.asJsonObject
                listOfNotNull(root, nested).any { errorObject ->
                    listOf("code", "type").any { field ->
                        errorObject.get(field)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                            ?.asString?.trim()?.lowercase(Locale.ROOT) in keyFailureCodes
                    }
                }
            }.getOrDefault(false)
        }
    }
}
