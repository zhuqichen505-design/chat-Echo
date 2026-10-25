package com.aiassistant.domain.model

/** 每个 API 地址和协议分别保存的多 Key 调度设置，时间单位为秒。 */
data class ApiKeyRotationSettings(
    val mode: String = ROUND_ROBIN,
    val cooldownEnabled: Boolean = true,
    val failuresBeforeCooldown: Int = 1,
    val cooldownSeconds: Long = 300,
    val exponentialBackoff: Boolean = true,
    val maxCooldownSeconds: Long = 3600,
    val failureResetSeconds: Long = 86400,
    val deprioritizeFailures: Boolean = true,
    val resetOnSuccess: Boolean = true,
    val probeWhenAllCooling: Boolean = false,
    val enabled: Boolean = true
) {
    /** 0 秒重置间隔表示不按时间自动清除失败记录。 */
    fun normalized(): ApiKeyRotationSettings {
        val safeCooldown = cooldownSeconds.coerceIn(1, MAX_BASE_COOLDOWN_SECONDS)
        return copy(
            mode = mode.takeIf { it in MODES } ?: ROUND_ROBIN,
            failuresBeforeCooldown = failuresBeforeCooldown.coerceIn(1, MAX_FAILURE_THRESHOLD),
            cooldownSeconds = safeCooldown,
            maxCooldownSeconds = maxCooldownSeconds.coerceIn(safeCooldown, MAX_COOLDOWN_SECONDS),
            failureResetSeconds = failureResetSeconds.coerceIn(0, MAX_FAILURE_RESET_SECONDS)
        )
    }

    companion object {
        const val ROUND_ROBIN = "round_robin"
        const val PREFER_SUCCESS = "prefer_success"
        const val ORDERED = "ordered"
        val MODES = setOf(ROUND_ROBIN, PREFER_SUCCESS, ORDERED)
        const val MAX_FAILURE_THRESHOLD = 100
        const val MAX_BASE_COOLDOWN_SECONDS = 604800L
        const val MAX_COOLDOWN_SECONDS = 2592000L
        const val MAX_FAILURE_RESET_SECONDS = 31536000L
    }
}
