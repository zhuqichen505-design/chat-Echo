package com.aiassistant.tools.device

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import java.util.Locale

class DeviceHardwareManager(private val context: Context) {

    fun getDeviceStatus(): DeviceHardwareStatus {
        val battery = getBatteryInfo()
        val memory = getMemoryInfo()
        val storage = getStorageInfo()
        val network = getNetworkInfo()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isPowerSave = powerManager?.isPowerSaveMode == true

        return DeviceHardwareStatus(
            batteryLevel = battery.first,
            isCharging = battery.second,
            isPowerSaveMode = isPowerSave,
            availableMemoryGb = memory.first,
            totalMemoryGb = memory.second,
            availableStorageGb = storage.first,
            totalStorageGb = storage.second,
            networkType = network,
            deviceModel = " ",
            androidVersion = "Android  (API )"
        )
    }

    private fun getBatteryInfo(): Pair<Int, Boolean> {
        return try {
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus: Intent? = context.registerReceiver(null, intentFilter)
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1

            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            val batteryPct = if (level >= 0 && scale > 0) {
                (level * 100 / scale.toFloat()).toInt()
            } else {
                -1
            }
            Pair(batteryPct, isCharging)
        } catch (_: Exception) {
            Pair(-1, false)
        }
    }

    private fun getMemoryInfo(): Pair<Double, Double> {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am?.getMemoryInfo(memInfo)

            val availGb = memInfo.availMem / (1024.0 * 1024.0 * 1024.0)
            val totalGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
            Pair(availGb, totalGb)
        } catch (_: Exception) {
            Pair(0.0, 0.0)
        }
    }

    private fun getStorageInfo(): Pair<Double, Double> {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val availGb = (availableBlocks * blockSize) / (1024.0 * 1024.0 * 1024.0)
            val totalGb = (totalBlocks * blockSize) / (1024.0 * 1024.0 * 1024.0)
            Pair(availGb, totalGb)
        } catch (_: Exception) {
            Pair(0.0, 0.0)
        }
    }

    private fun getNetworkInfo(): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = cm?.activeNetwork ?: return "未连接网络"
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return "未连接网络"

            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi 高速网络"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "蜂窝移动数据网络"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "有线以太网"
                else -> "已联网"
            }
        } catch (_: Exception) {
            "未知网络"
        }
    }

    data class DeviceHardwareStatus(
        val batteryLevel: Int,
        val isCharging: Boolean,
        val isPowerSaveMode: Boolean,
        val availableMemoryGb: Double,
        val totalMemoryGb: Double,
        val availableStorageGb: Double,
        val totalStorageGb: Double,
        val networkType: String,
        val deviceModel: String,
        val androidVersion: String
    ) {
        fun toPromptBlock(): String {
            return buildString {
                append("【手机设备与硬件状态】")
                append("\n设备型号：").append(deviceModel)
                append("\n系统版本：").append(androidVersion)
                if (batteryLevel >= 0) {
                    append("\n当前电量：").append(batteryLevel).append("%")
                    append(if (isCharging) " (正在充电中 ⚡)" else " (未充电)")
                    if (isPowerSaveMode) append(" [已开启省电模式]")
                }
                if (totalMemoryGb > 0) {
                    append("\n运行内存：可用 ").append(String.format(Locale.US, "%.1f", availableMemoryGb))
                        .append(" GB / 总计 ").append(String.format(Locale.US, "%.1f", totalMemoryGb)).append(" GB")
                }
                if (totalStorageGb > 0) {
                    append("\n内部存储：可用 ").append(String.format(Locale.US, "%.1f", availableStorageGb))
                        .append(" GB / 总计 ").append(String.format(Locale.US, "%.1f", totalStorageGb)).append(" GB")
                }
                append("\n网络连接：").append(networkType)
            }
        }
    }
}
