package com.aiassistant

import com.aiassistant.tools.EchoToolHub
import com.aiassistant.tools.cloud.OpenMeteoWeatherEngine
import com.aiassistant.tools.device.DeviceHardwareManager
import com.aiassistant.tools.device.HealthDataManager
import com.aiassistant.tools.search.ExaSearchEngine
import com.aiassistant.tools.search.SearchEngineType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V1915FeaturesTest {

    @Test
    fun testExaSearchParser_extractsTitleUrlAndHighlights() {
        val sampleResponse = """
            Title: Android 15 Major Features Overview
            URL: https://developer.android.com/about/versions/15
            Published: 2024-10-15T00:00:00.000Z
            Author: Android Team
            Highlights:
            Android 15 enhances privacy sandbox, private space, and large-screen productivity.
            Better battery management and edge-to-edge support.
            ---
            Title: DeepSeek-V3 Technical Report
            URL: https://github.com/deepseek-ai/DeepSeek-V3
            Highlights:
            DeepSeek-V3 is a strong open-source MoE model with 671B total parameters.
        """.trimIndent()

        val docs = ExaSearchEngine.parseExaResultText(sampleResponse)
        assertEquals(2, docs.size)

        assertEquals("Android 15 Major Features Overview", docs[0].title)
        assertEquals("https://developer.android.com/about/versions/15", docs[0].url)
        assertTrue(docs[0].content.contains("Android 15 enhances privacy sandbox"))

        assertEquals("DeepSeek-V3 Technical Report", docs[1].title)
        assertEquals("https://github.com/deepseek-ai/DeepSeek-V3", docs[1].url)
        assertTrue(docs[1].content.contains("DeepSeek-V3 is a strong open-source MoE model"))
    }

    @Test
    fun testOpenMeteoWeatherCodeMapping() {
        assertEquals("晴朗无云 ☀️", OpenMeteoWeatherEngine.mapWmoWeatherCodeToChinese(0))
        assertEquals("微量细毛雨 🌧️", OpenMeteoWeatherEngine.mapWmoWeatherCodeToChinese(51))
        assertEquals("小雨 🌦️", OpenMeteoWeatherEngine.mapWmoWeatherCodeToChinese(61))
        assertEquals("雷阵雨 ⛈️", OpenMeteoWeatherEngine.mapWmoWeatherCodeToChinese(95))

        val report = OpenMeteoWeatherEngine.WeatherReport(
            cityName = "深圳市",
            condition = "晴朗无云 ☀️",
            temperature = 26.5,
            humidity = 58,
            windSpeed = 12.0,
            maxTemp = 29.0,
            minTemp = 21.0
        )
        val prompt = report.toPromptBlock()
        assertTrue(prompt.contains("深圳市"))
        assertTrue(prompt.contains("晴朗无云"))
        assertTrue(prompt.contains("26.5 ℃"))
        assertTrue(prompt.contains("Open-Meteo"))
    }

    @Test
    fun testEchoToolHub_intentDetection() {
        // Weather intent & city extraction
        assertTrue(EchoToolHub.isWeatherIntent("查一下北京明天的天气预报"))
        assertTrue(EchoToolHub.isWeatherIntent("现在气温多少度？冷不冷？"))
        assertEquals("深圳", EchoToolHub.extractCityForWeather("请问深圳天气如何"))
        assertEquals("杭州", EchoToolHub.extractCityForWeather("杭州的气温是多少"))

        // Time & Calendar intent
        assertTrue(EchoToolHub.isTimeOrCalendarIntent("现在几点了？"))
        assertTrue(EchoToolHub.isTimeOrCalendarIntent("今天星期几？几月几号？"))
        assertTrue(EchoToolHub.isTimeOrCalendarIntent("看看我今天有什么日程安排"))

        // Health intent (Steps, Heart Rate, Sleep)
        assertTrue(EchoToolHub.isHealthIntent("我今天走了多少步了？"))
        assertTrue(EchoToolHub.isHealthIntent("查看我的运动步数和睡眠"))
        assertTrue(EchoToolHub.isHealthIntent("现在心率正常吗"))

        // Device hardware intent
        assertTrue(EchoToolHub.isDeviceStatusIntent("手机还剩多少电量？"))
        assertTrue(EchoToolHub.isDeviceStatusIntent("当前手机型号和剩余存储"))

        // Location intent
        assertTrue(EchoToolHub.isLocationIntent("我当前在哪个城市？"))
        assertTrue(EchoToolHub.isLocationIntent("我在哪？"))
    }

    @Test
    fun testEchoToolHub_urlExtractionAndWebReaderTrigger() {
        val message = "请帮我阅读分析一下这篇文章的内容：https://news.ycombinator.com/item?id=40000000 看看大家在讨论什么"
        val extractedUrl = EchoToolHub.extractUrl(message)
        assertEquals("https://news.ycombinator.com/item?id=40000000", extractedUrl)
        assertTrue(EchoToolHub.shouldFetchWebpage(message))

        // Normal question without URL
        assertFalse(EchoToolHub.shouldFetchWebpage("今天有什么好看的科技新闻吗？"))
    }

    @Test
    fun testHealthAndDevicePromptBlocks() {
        val health = HealthDataManager.HealthDataSummary(
            todaySteps = 8520,
            heartRate = 74,
            sleepMinutes = 450,
            deepSleepMinutes = 110,
            sleepScore = 88,
            hasHardwareStepSensor = true
        )
        val healthBlock = health.toPromptBlock()
        assertTrue(healthBlock.contains("8520 步"))
        assertTrue(healthBlock.contains("74 bpm"))
        assertTrue(healthBlock.contains("7小时30分钟"))
        assertTrue(healthBlock.contains("华为运动健康"))

        val hardware = DeviceHardwareManager.DeviceHardwareStatus(
            batteryLevel = 82,
            isCharging = true,
            isPowerSaveMode = false,
            availableMemoryGb = 4.2,
            totalMemoryGb = 12.0,
            availableStorageGb = 128.5,
            totalStorageGb = 256.0,
            networkType = "Wi-Fi 高速网络",
            deviceModel = "HUAWEI ALN-AL00",
            androidVersion = "Android 12 (API 31)"
        )
        val hardBlock = hardware.toPromptBlock()
        assertTrue(hardBlock.contains("82%"))
        assertTrue(hardBlock.contains("充电中 ⚡"))
        assertTrue(hardBlock.contains("HUAWEI ALN-AL00"))
        assertTrue(hardBlock.contains("Wi-Fi 高速网络"))
    }

    @Test
    fun testSearchEngineType_parsingAndDefaults() {
        assertEquals(SearchEngineType.EXA, SearchEngineType.fromValue("EXA"))
        assertEquals(SearchEngineType.TAVILY, SearchEngineType.fromValue("TAVILY"))
        assertEquals(SearchEngineType.EXA, SearchEngineType.fromValue("unknown"))
        assertEquals("Exa", SearchEngineType.EXA.displayName)
        assertEquals("Tavily", SearchEngineType.TAVILY.displayName)
    }
}
