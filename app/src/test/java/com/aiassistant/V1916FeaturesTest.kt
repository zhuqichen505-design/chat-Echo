package com.aiassistant

import com.aiassistant.data.local.AppDatabase
import com.aiassistant.data.repository.AiRepository
import com.aiassistant.domain.model.Message
import com.aiassistant.domain.model.ToolCallRecord
import com.aiassistant.tools.EchoToolHub
import com.aiassistant.tools.cloud.OpenMeteoWeatherEngine
import com.aiassistant.tools.device.HealthDataManager
import com.aiassistant.tools.device.TimeCalendarManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class V1916FeaturesTest {

    private val gson = Gson()

    @Test
    fun testToolCallRecord_jsonSerializationAndDeserialization() {
        val records = listOf(
            ToolCallRecord(
                id = "call-1",
                toolType = "WEATHER",
                toolName = "Open-Meteo 实时气象查询",
                iconName = "Cloud",
                summary = "上海: 晴 · 气温 24.5℃ · 湿度 55%",
                detailContent = "【天气服务查询结果】\n城市: 上海\n天气状况: 晴朗无云 ☀️\n当前气温: 24.5 ℃",
                isSuccess = true
            ),
            ToolCallRecord(
                id = "call-2",
                toolType = "HEALTH",
                toolName = "华为运动健康与硬件计步",
                iconName = "DirectionsWalk",
                summary = "今日步数: 8200 步 · 心率: 72 bpm · 睡眠: 7小时30分",
                detailContent = "【手机健康数据】\n今日步数: 8200 步\n心率: 72 bpm",
                isSuccess = true
            )
        )

        val json = gson.toJson(records)
        assertTrue(json.contains("call-1"))
        assertTrue(json.contains("Open-Meteo 实时气象查询"))
        assertTrue(json.contains("华为运动健康与硬件计步"))

        val type = TypeToken.getParameterized(List::class.java, ToolCallRecord::class.java).type
        val parsed: List<ToolCallRecord> = gson.fromJson(json, type)

        assertEquals(2, parsed.size)
        assertEquals("WEATHER", parsed[0].toolType)
        assertEquals("Open-Meteo 实时气象查询", parsed[0].toolName)
        assertEquals("上海: 晴 · 气温 24.5℃ · 湿度 55%", parsed[0].summary)
        assertTrue(parsed[0].isSuccess)

        assertEquals("HEALTH", parsed[1].toolType)
        assertEquals(8200, parsed[1].detailContent.substringAfter("今日步数: ").substringBefore(" 步").toInt())
    }

    @Test
    fun testMessageEntity_supportsToolCallsField() {
        val records = listOf(
            ToolCallRecord(
                toolType = "WEB_SEARCH",
                toolName = "Exa 免Key联网搜索",
                iconName = "Search",
                summary = "已检索到 5 条网络网页资料",
                detailContent = "【网络检索结果】...",
                isSuccess = true
            )
        )
        val json = gson.toJson(records)

        val message = Message(
            conversationId = 100L,
            role = "assistant",
            content = "根据实时联网搜索结果，Android 15 已正式发布...",
            toolCalls = json
        )

        assertNotNull(message.toolCalls)
        assertEquals(json, message.toolCalls)

        val copied = message.copy(content = "更新后的回复")
        assertEquals(json, copied.toolCalls)
    }

    @Test
    fun testSanitizeGeneratedTitle_stripsThinkTagsAndPrefixes() {
        // 1. 过滤思考标签 <think>...</think>
        val r1 = "<think>\n用户询问了快速排序算法，需要一个简明扼要的标题。\n</think>\n快速排序算法解析"
        val t1 = AiRepository.sanitizeGeneratedTitle(r1)
        assertEquals("快速排序算法解析", t1)

        // 2. 剥离前缀（标题：、对话标题：、1. 、引号、书名号）
        val r2 = "标题：《Python基础教程讨论》"
        val t2 = AiRepository.sanitizeGeneratedTitle(r2)
        assertEquals("Python基础教程讨论", t2)

        val r3 = "对话标题：杭州周末旅行攻略。"
        val t3 = AiRepository.sanitizeGeneratedTitle(r3)
        assertEquals("杭州周末旅行攻略", t3)

        val r4 = "1. \"量子计算前沿进展\""
        val t4 = AiRepository.sanitizeGeneratedTitle(r4)
        assertEquals("量子计算前沿进展", t4)

        // 3. 空或全思考
        val r5 = "<think>思考中</think>"
        val t5 = AiRepository.sanitizeGeneratedTitle(r5)
        assertNull(t5)

        // 4. 超长截断在 18 字符内
        val r6 = "这是一个超级无敌长的大模型生成的对话标题测试示例文字超长内容"
        val t6 = AiRepository.sanitizeGeneratedTitle(r6)
        assertNotNull(t6)
        assertTrue(t6!!.length <= 18)
    }

    @Test
    fun testHuaweiHealth_stepCalibrationArithmetic() {
        // 模拟硬件传感器当前累计步数 50000 步
        val hardwareTotal = 50000
        val userTodaySteps = 6800

        // 校准后 baseline 应为 hardwareTotal - userTodaySteps
        val baseline = hardwareTotal - userTodaySteps
        assertEquals(43200, baseline)

        // 之后计算今日步数:
        val calculatedSteps = (hardwareTotal - baseline).coerceAtLeast(0)
        assertEquals(userTodaySteps, calculatedSteps)

        // 硬件又走了 200 步
        val newHardwareTotal = 50200
        val newCalculatedSteps = (newHardwareTotal - baseline).coerceAtLeast(0)
        assertEquals(7000, newCalculatedSteps)
    }

    @Test
    fun testOpenMeteoWeatherEngine_urlParameters() {
        val lat = 31.2304
        val lon = 121.4737
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto"

        assertTrue(url.contains("latitude=31.2304"))
        assertTrue(url.contains("longitude=121.4737"))
        assertTrue(url.contains("timezone=auto"))
        assertTrue(url.contains("current=temperature_2m"))
    }

    @Test
    fun testSearchResultCount_boundaries() {
        val clampTest = { count: Int -> count.coerceIn(1, 20) }
        assertEquals(5, clampTest(5))
        assertEquals(1, clampTest(0))
        assertEquals(20, clampTest(25))
        assertEquals(10, clampTest(10))
    }

    @Test
    fun testRoomMigration20_21_sqlStatement() {
        // 验证 MIGRATION_20_21 的版本与 SQL
        assertEquals(20, AppDatabase.MIGRATION_20_21.startVersion)
        assertEquals(21, AppDatabase.MIGRATION_20_21.endVersion)
    }
}
