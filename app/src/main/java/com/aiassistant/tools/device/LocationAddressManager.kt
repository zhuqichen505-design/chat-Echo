package com.aiassistant.tools.device

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.util.Locale

class LocationAddressManager(private val context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("echo_location_prefs", Context.MODE_PRIVATE)

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun getManualCity(): String {
        return prefs.getString("manual_city", "").orEmpty()
    }

    fun setManualCity(city: String) {
        prefs.edit().putString("manual_city", city.trim()).apply()
    }

    @SuppressLint("MissingPermission")
    fun getCurrentLocation(): DeviceLocationResult {
        val manual = getManualCity()
        if (manual.isNotBlank()) {
            return DeviceLocationResult(
                city = manual,
                district = "",
                province = "",
                fullAddress = manual,
                latitude = null,
                longitude = null,
                isManual = true
            )
        }

        if (!hasLocationPermission()) {
            return DeviceLocationResult(
                city = "未授权定位",
                district = "",
                province = "",
                fullAddress = "定位权限未授予（可在设置中授权或手动输入所在城市）",
                latitude = null,
                longitude = null,
                isManual = false
            )
        }

        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val location = lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm?.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)

            if (location != null) {
                return reverseGeocode(location)
            }
        } catch (_: Exception) {
        }

        return DeviceLocationResult(
            city = "未知位置",
            district = "",
            province = "",
            fullAddress = "暂未获取到有效定位信号",
            latitude = null,
            longitude = null,
            isManual = false
        )
    }

    private fun reverseGeocode(location: Location): DeviceLocationResult {
        val lat = location.latitude
        val lon = location.longitude

        try {
            val geocoder = Geocoder(context, Locale.CHINA)
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr: Address = addresses[0]
                val province = addr.adminArea.orEmpty()
                val city = addr.locality ?: addr.subAdminArea ?: province
                val district = addr.subLocality.orEmpty()
                val feature = addr.featureName.orEmpty()

                val full = listOf(province, city, district, feature)
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString("")

                return DeviceLocationResult(
                    city = city.ifBlank { "已定位" },
                    district = district,
                    province = province,
                    fullAddress = full.ifBlank { "经纬度: " },
                    latitude = lat,
                    longitude = lon,
                    isManual = false
                )
            }
        } catch (_: Exception) {
        }

        return DeviceLocationResult(
            city = "坐标已获取",
            district = "",
            province = "",
            fullAddress = "经纬度: ",
            latitude = lat,
            longitude = lon,
            isManual = false
        )
    }

    data class DeviceLocationResult(
        val city: String,
        val district: String,
        val province: String,
        val fullAddress: String,
        val latitude: Double?,
        val longitude: Double?,
        val isManual: Boolean
    ) {
        fun toPromptBlock(): String {
            return buildString {
                append("【当前设备地理位置】")
                append("\n城市/区域：").append(city)
                if (fullAddress.isNotBlank()) {
                    append("\n详细位置：").append(fullAddress)
                }
                if (latitude != null && longitude != null) {
                    append("\n坐标：").append(String.format(Locale.US, "%.4f, %.4f", latitude, longitude))
                }
            }
        }
    }
}
