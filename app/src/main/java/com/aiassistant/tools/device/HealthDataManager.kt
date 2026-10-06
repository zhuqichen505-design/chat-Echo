package com.aiassistant.tools.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HealthDataManager(private val context: Context) : SensorEventListener {

    private val prefs = context.applicationContext.getSharedPreferences("echo_health_prefs", Context.MODE_PRIVATE)
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private var stepSensor: Sensor? = null
    private var isListening = false

    init {
        stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        startListening()
    }

    fun hasActivityRecognitionPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasStepSensor(): Boolean = stepSensor != null

    fun getSensorStatusText(): String {
        return when {
            stepSensor == null -> "设备无硬件计步传感器"
            !hasActivityRecognitionPermission() -> "未授权活动识别权限"
            isListening -> "硬件计步传感器运行中"
            else -> "传感器待命就绪"
        }
    }

    fun startListening() {
        if (!hasActivityRecognitionPermission()) return
        if (!isListening && stepSensor != null && sensorManager != null) {
            isListening = sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    sensorManager.flush(this)
                }
            } catch (_: Exception) {}
        }
    }

    fun stopListening() {
        if (isListening && sensorManager != null) {
            sensorManager.unregisterListener(this)
            isListening = false
        }
    }

    fun forceRefreshHardwareSteps(): HealthDataSummary {
        stopListening()
        startListening()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                sensorManager?.flush(this)
            }
        } catch (_: Exception) {}
        prefs.edit().putLong(KEY_HEALTH_UPDATE_TIME, System.currentTimeMillis()).apply()
        return getHealthDataSummary()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            val totalSteps = event.values.getOrNull(0)?.toInt() ?: return
            handleStepUpdate(totalSteps)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    @Volatile
    private var lastHardwareTotal: Int = 0

    @Synchronized
    private fun handleStepUpdate(currentTotal: Int) {
        lastHardwareTotal = currentTotal
        val today = getTodayDateKey()
        val savedDate = prefs.getString(KEY_STEP_DATE, "").orEmpty()
        var baseline = prefs.getInt(KEY_STEP_BASELINE, -1)

        if (savedDate != today) {
            // 新的一天
            baseline = currentTotal
            prefs.edit()
                .putString(KEY_STEP_DATE, today)
                .putInt(KEY_STEP_BASELINE, baseline)
                .putInt(KEY_TODAY_STEPS, 0)
                .putInt(KEY_LAST_HARDWARE_TOTAL, currentTotal)
                .apply()
        } else {
            if (baseline < 0) {
                baseline = currentTotal
                prefs.edit().putInt(KEY_STEP_BASELINE, baseline).apply()
            }
            if (currentTotal < baseline) {
                baseline = 0
                prefs.edit().putInt(KEY_STEP_BASELINE, 0).apply()
            }
            val todaySteps = (currentTotal - baseline).coerceAtLeast(0)
            prefs.edit()
                .putInt(KEY_TODAY_STEPS, todaySteps)
                .putInt(KEY_LAST_HARDWARE_TOTAL, currentTotal)
                .apply()
        }
    }

    fun getTodaySteps(): Int {
        val today = getTodayDateKey()
        val savedDate = prefs.getString(KEY_STEP_DATE, "").orEmpty()
        return if (savedDate == today) {
            prefs.getInt(KEY_TODAY_STEPS, 0)
        } else {
            0
        }
    }

    fun setManualSteps(steps: Int) {
        calibrateTodaySteps(steps)
    }

    /**
     * 用户手动校准华为运动健康步数：
     * 将今日步数与硬件传感器基准线对齐，后续步数实时自增
     */
    fun calibrateTodaySteps(steps: Int) {
        val today = getTodayDateKey()
        val validSteps = steps.coerceAtLeast(0)
        val currentHw = if (lastHardwareTotal > 0) lastHardwareTotal else prefs.getInt(KEY_LAST_HARDWARE_TOTAL, validSteps)
        val newBaseline = (currentHw - validSteps).coerceAtLeast(0)

        prefs.edit()
            .putString(KEY_STEP_DATE, today)
            .putInt(KEY_STEP_BASELINE, newBaseline)
            .putInt(KEY_TODAY_STEPS, validSteps)
            .putInt(KEY_LAST_HARDWARE_TOTAL, currentHw)
            .putLong(KEY_HEALTH_UPDATE_TIME, System.currentTimeMillis())
            .apply()
    }

    fun syncHuaweiHealthData(
        steps: Int,
        heartRate: Int,
        sleepHours: Int,
        sleepMinutes: Int,
        deepSleepMinutes: Int,
        sleepScore: Int
    ) {
        val totalSleepMins = sleepHours * 60 + sleepMinutes
        calibrateTodaySteps(steps)
        setHeartRate(heartRate)
        saveSleepRecord(totalSleepMins, deepSleepMinutes, sleepScore)
    }

    fun syncHuaweiHealthData(
        steps: Int,
        heartRate: Int,
        totalSleepMinutes: Int,
        deepSleepMinutes: Int,
        sleepScore: Int
    ) {
        calibrateTodaySteps(steps)
        setHeartRate(heartRate)
        saveSleepRecord(totalSleepMinutes, deepSleepMinutes, sleepScore)
    }

    // 心率 (bpm) -1 表示未录入
    fun getHeartRate(): Int = prefs.getInt(KEY_HEART_RATE, -1)

    fun setHeartRate(bpm: Int) {
        prefs.edit().putInt(KEY_HEART_RATE, bpm.coerceIn(40, 220)).apply()
    }

    // 昨晚睡眠 (分钟) -1 表示未录入
    fun getSleepDurationMinutes(): Int = prefs.getInt(KEY_SLEEP_MINUTES, -1)

    fun getDeepSleepMinutes(): Int = prefs.getInt(KEY_DEEP_SLEEP_MINUTES, -1)

    fun getSleepScore(): Int = prefs.getInt(KEY_SLEEP_SCORE, -1)

    fun saveSleepRecord(durationMinutes: Int, deepMinutes: Int, score: Int) {
        prefs.edit()
            .putInt(KEY_SLEEP_MINUTES, durationMinutes.coerceAtLeast(0))
            .putInt(KEY_DEEP_SLEEP_MINUTES, deepMinutes.coerceAtLeast(0))
            .putInt(KEY_SLEEP_SCORE, score.coerceIn(0, 100))
            .putLong(KEY_HEALTH_UPDATE_TIME, System.currentTimeMillis())
            .apply()
    }

    fun getLastUpdateTime(): Long = prefs.getLong(KEY_HEALTH_UPDATE_TIME, 0L)

    fun getFormattedLastUpdateTime(): String = formatUpdateTime(getLastUpdateTime())

    /**
     * 探测系统是否已安装华为或荣耀运动健康官方应用
     */
    fun getInstalledHealthPackage(): String? {
        val pm = context.packageManager
        for (pkg in HUAWEI_HEALTH_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0)
                return pkg
            } catch (_: Exception) {}
            try {
                if (pm.getLaunchIntentForPackage(pkg) != null) {
                    return pkg
                }
            } catch (_: Exception) {}
        }
        return null
    }

    fun isHuaweiHealthInstalled(): Boolean = getInstalledHealthPackage() != null

    fun getHealthAppName(): String {
        val pkg = getInstalledHealthPackage() ?: return "华为运动健康"
        return when (pkg) {
            "com.hihonor.health" -> "荣耀运动健康"
            "com.huawei.bone" -> "华为穿戴"
            else -> "华为运动健康"
        }
    }

    /**
     * 尝试拉起系统已安装的华为/荣耀运动健康官方应用
     */
    fun openHuaweiHealthApp(context: Context): Boolean {
        val targetPkg = getInstalledHealthPackage() ?: "com.huawei.health"
        return try {
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage(targetPkg)
            if (launchIntent != null) {
                launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun getHealthDataSummary(): HealthDataSummary {
        return HealthDataSummary(
            todaySteps = getTodaySteps(),
            heartRate = getHeartRate(),
            sleepMinutes = getSleepDurationMinutes(),
            deepSleepMinutes = getDeepSleepMinutes(),
            sleepScore = getSleepScore(),
            hasHardwareStepSensor = stepSensor != null
        )
    }

    private fun getTodayDateKey(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    data class HealthDataSummary(
        val todaySteps: Int,
        val heartRate: Int, // -1 表示未手动录入
        val sleepMinutes: Int, // -1 表示未手动录入
        val deepSleepMinutes: Int, // -1 表示未手动录入
        val sleepScore: Int, // -1 表示未手动录入
        val hasHardwareStepSensor: Boolean
    ) {
        fun toPromptBlock(): String {
            val estimatedKm = String.format(Locale.US, "%.2f", todaySteps * 0.0007)
            val estimatedKcal = (todaySteps * 0.035).toInt()

            return buildString {
                append("【用户手机运动与健康数据】")
                append("\n今日步数：").append(todaySteps).append(" 步")
                append(" (约 ").append(estimatedKm).append(" 公里, 消耗约 ").append(estimatedKcal).append(" 千卡)")
                if (hasHardwareStepSensor) {
                    append(" [来自手机硬件传感器]")
                }
                if (todaySteps >= 10000) {
                    append(" - 已达成万步目标 🎉")
                } else if (todaySteps > 0) {
                    append(" - 距一万步还差 ").append(10000 - todaySteps).append(" 步")
                }

                if (heartRate > 0) {
                    append("\n静态心率：").append(heartRate).append(" bpm (用户对照校准)")
                } else {
                    append("\n心率监测：暂无录入数据")
                }

                if (sleepMinutes > 0) {
                    val sleepHours = sleepMinutes / 60
                    val sleepMins = sleepMinutes % 60
                    append("\n昨晚睡眠时长：").append(sleepHours).append("小时").append(sleepMins).append("分钟")
                    if (deepSleepMinutes > 0) {
                        val deepHours = deepSleepMinutes / 60
                        val deepMins = deepSleepMinutes % 60
                        append(" (深睡 ").append(deepHours).append("小时").append(deepMins).append("分)")
                    }
                    if (sleepScore > 0) {
                        append(" · 质量评分: ").append(sleepScore).append(" 分")
                    }
                } else {
                    append("\n昨晚睡眠：暂无录入数据")
                }

                append("\n数据说明：步数由本地手机硬件计步传感器实时统计；心率与睡眠受 Android 系统隐私沙箱机制保护，三方应用无法直接跨应用暗中读取华为运动健康私有数据，支持在设置中对照手动校准同步。")
            }
        }
    }

    companion object {
        val HUAWEI_HEALTH_PACKAGES = listOf(
            "com.huawei.health",
            "com.huawei.bone",
            "com.hihonor.health"
        )
        private const val KEY_STEP_DATE = "health_step_date"
        private const val KEY_STEP_BASELINE = "health_step_baseline"
        private const val KEY_LAST_HARDWARE_TOTAL = "health_last_hardware_total"
        private const val KEY_TODAY_STEPS = "health_today_steps"
        private const val KEY_HEART_RATE = "health_heart_rate"
        private const val KEY_SLEEP_MINUTES = "health_sleep_minutes"
        private const val KEY_DEEP_SLEEP_MINUTES = "health_deep_sleep_minutes"
        private const val KEY_SLEEP_SCORE = "health_sleep_score"
        private const val KEY_HEALTH_UPDATE_TIME = "health_update_time"

        fun formatUpdateTime(time: Long): String {
            if (time <= 0L) return "暂未同步"
            val diff = (System.currentTimeMillis() - time).coerceAtLeast(0L)
            return when {
                diff < 60_000L -> "刚刚"
                diff < 3_600_000L -> "${diff / 60_000L}分钟前"
                diff < 86_400_000L -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time))
                else -> SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(time))
            }
        }
    }
}
