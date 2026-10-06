package com.aiassistant.tools.cloud

import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

class OpenMeteoWeatherEngine {

    private val httpClient = com.aiassistant.data.remote.HttpAccessPolicy.guard(OkHttpClient.Builder())
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    fun getWeather(cityName: String? = null, defaultLat: Double? = null, defaultLon: Double? = null): Result<WeatherReport> {
        return try {
            var targetCity = cityName?.trim().orEmpty()
            var lat = defaultLat
            var lon = defaultLon

            // 如果指定了城市名，先通过免费 Geocoding API 解析坐标
            if (targetCity.isNotBlank()) {
                val geoResult = resolveCityCoordinates(targetCity)
                if (geoResult != null) {
                    lat = geoResult.first
                    lon = geoResult.second
                    targetCity = geoResult.third
                }
            }

            // 如果依然没有坐标，回退为北京
            if (lat == null || lon == null) {
                lat = 39.9042
                lon = 116.4074
                if (targetCity.isBlank()) targetCity = "北京 (默认参考)"
            } else if (targetCity.isBlank()) {
                targetCity = "本地定位区域"
            }

            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto"

            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return Result.failure(Exception("Open-Meteo 天气请求失败 (${response.code})"))
                }

                val root = gson.fromJson(body, JsonObject::class.java)
                val current = root.getAsJsonObject("current")
                val daily = root.getAsJsonObject("daily")

                val temp = current?.get("temperature_2m")?.asDouble ?: 0.0
                val humidity = current?.get("relative_humidity_2m")?.asInt ?: 0
                val weatherCode = current?.get("weather_code")?.asInt ?: 0
                val windSpeed = current?.get("wind_speed_10m")?.asDouble ?: 0.0

                val maxTemp = daily?.getAsJsonArray("temperature_2m_max")?.get(0)?.asDouble
                val minTemp = daily?.getAsJsonArray("temperature_2m_min")?.get(0)?.asDouble

                val condition = mapWmoWeatherCodeToChinese(weatherCode)

                Result.success(
                    WeatherReport(
                        cityName = targetCity,
                        condition = condition,
                        temperature = temp,
                        humidity = humidity,
                        windSpeed = windSpeed,
                        maxTemp = maxTemp,
                        minTemp = minTemp
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(Exception("天气服务查询失败: ${e.message}", e))
        }
    }

    private fun resolveCityCoordinates(city: String): Triple<Double, Double, String>? {
        return try {
            val encodedCity = URLEncoder.encode(city, "UTF-8")
            val url = "https://geocoding-api.open-meteo.com/v1/search?name=$encodedCity&count=1&language=zh&format=json"
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val root = gson.fromJson(resp.body?.string().orEmpty(), JsonObject::class.java)
                val results = root.getAsJsonArray("results")
                if (results != null && results.size() > 0) {
                    val item = results.get(0).asJsonObject
                    val lat = item.get("latitude").asDouble
                    val lon = item.get("longitude").asDouble
                    val name = item.get("name").asString
                    val admin = item.get("admin1")?.asString
                    val resolvedName = if (!admin.isNullOrBlank() && admin != name) "$admin $name" else name
                    Triple(lat, lon, resolvedName)
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        fun mapWmoWeatherCodeToChinese(code: Int): String {
            return when (code) {
                0 -> "晴朗无云 ☀️"
                1 -> "大致晴朗 🌤️"
                2 -> "局部多云 ⛅"
                3 -> "阴天多云 ☁️"
                45 -> "有雾 🌫️"
                48 -> "沉积雾/白霜雾 🌫️"
                51 -> "微量细毛雨 🌧️"
                53 -> "中度毛毛雨 🌧️"
                55 -> "密集细雨 🌧️"
                61 -> "小雨 🌦️"
                63 -> "中雨 🌧️"
                65 -> "大雨/暴雨 🌧️"
                71 -> "小雪 🌨️"
                73 -> "中雪 ❄️"
                75 -> "大雪 ❄️"
                77 -> "雪粒/米雪 ❄️"
                80 -> "阵雨 🌦️"
                81 -> "中等阵雨 🌧️"
                82 -> "强阵雨 ⛈️"
                85 -> "小阵雪 🌨️"
                86 -> "强阵雪 ❄️"
                95 -> "雷阵雨 ⛈️"
                96, 99 -> "雷暴伴有冰雹 ⛈️"
                else -> "多云"
            }
        }
    }

    data class WeatherReport(
        val cityName: String,
        val condition: String,
        val temperature: Double,
        val humidity: Int,
        val windSpeed: Double,
        val maxTemp: Double?,
        val minTemp: Double?
    ) {
        fun toPromptBlock(): String {
            return buildString {
                append("【实时气象报告 (由 Open-Meteo 提供)】")
                append("\n城市区域：").append(cityName)
                append("\n天气状况：").append(condition)
                append("\n实时气温：").append(String.format(Locale.US, "%.1f", temperature)).append(" ℃")
                append("\n相对湿度：").append(humidity).append("%")
                append("\n当前风速：").append(String.format(Locale.US, "%.1f", windSpeed)).append(" km/h")
                if (maxTemp != null && minTemp != null) {
                    append("\n今日温差：最低 ").append(String.format(Locale.US, "%.1f", minTemp))
                        .append(" ℃ / 最高 ").append(String.format(Locale.US, "%.1f", maxTemp)).append(" ℃")
                }
                append("\n数据特点：全球高分辨率开源气象网实时测报，免API Key调用")
            }
        }
    }
}
