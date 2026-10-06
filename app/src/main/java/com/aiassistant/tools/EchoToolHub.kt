package com.aiassistant.tools

import android.content.Context
import androidx.core.content.edit
import com.aiassistant.domain.model.ChatRequestOptions
import com.aiassistant.domain.model.ToolCallRecord
import com.aiassistant.tools.cloud.JinaReaderEngine
import com.aiassistant.tools.cloud.OpenMeteoWeatherEngine
import com.aiassistant.tools.device.DeviceHardwareManager
import com.aiassistant.tools.device.HealthDataManager
import com.aiassistant.tools.device.LocationAddressManager
import com.aiassistant.tools.device.TimeCalendarManager
import com.aiassistant.tools.search.ExaSearchEngine
import com.aiassistant.tools.search.SearchEngineType
import com.aiassistant.utils.CryptoManager
import com.aiassistant.utils.TavilySearchManager
import com.aiassistant.utils.WebSearchBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

data class EnrichedPromptResult(
    val enrichedPrompt: String,
    val toolCalls: List<ToolCallRecord>
)

class EchoToolHub(
    private val context: Context,
    private val cryptoManager: CryptoManager,
    val tavilySearchManager: TavilySearchManager
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val timeCalendarManager = TimeCalendarManager(context)
    val locationAddressManager = LocationAddressManager(context)
    val healthDataManager = HealthDataManager(context)
    val deviceHardwareManager = DeviceHardwareManager(context)
    val openMeteoWeatherEngine = OpenMeteoWeatherEngine()
    val jinaReaderEngine = JinaReaderEngine(getJinaApiKey())
    val exaSearchEngine = ExaSearchEngine(getExaApiKey())
    val mwmblSearchEngine = com.aiassistant.tools.search.MwmblSearchEngine()
    val searxngSearchEngine = com.aiassistant.tools.search.SearxngSearchEngine(::getSearxngUrl)

    fun getSearxngUrl(): String = prefs.getString("searxng_instance_url", "").orEmpty()

    fun setSearxngUrl(url: String) {
        prefs.edit { putString("searxng_instance_url", url.trim()) }
    }

    fun getSearchEngine(): SearchEngineType {
        val name = prefs.getString(KEY_SEARCH_ENGINE, SearchEngineType.EXA.name) ?: SearchEngineType.EXA.name
        return SearchEngineType.fromValue(name)
    }

    fun setSearchEngine(type: SearchEngineType) {
        prefs.edit().putString(KEY_SEARCH_ENGINE, type.name).apply()
    }

    fun getSearchResultCount(): Int {
        return prefs.getInt(KEY_SEARCH_RESULT_COUNT, 5).coerceIn(1, com.aiassistant.tools.search.SearchLimits.MAX_REQUESTED)
    }

    fun setSearchResultCount(count: Int) {
        prefs.edit().putInt(KEY_SEARCH_RESULT_COUNT, count.coerceIn(1, com.aiassistant.tools.search.SearchLimits.MAX_REQUESTED)).apply()
    }

    fun getExaApiKey(): String {
        val encrypted = prefs.getString(KEY_EXA_KEY, "").orEmpty()
        return cryptoManager.decrypt(encrypted)
    }

    fun setExaApiKey(key: String) {
        val clean = key.trim()
        exaSearchEngine.updateApiKey(clean)
        prefs.edit().putString(KEY_EXA_KEY, cryptoManager.encrypt(clean)).apply()
    }

    fun getJinaApiKey(): String {
        val encrypted = prefs.getString(KEY_JINA_KEY, "").orEmpty()
        return cryptoManager.decrypt(encrypted)
    }

    fun setJinaApiKey(key: String) {
        val clean = key.trim()
        jinaReaderEngine.updateApiKey(clean)
        prefs.edit().putString(KEY_JINA_KEY, cryptoManager.encrypt(clean)).apply()
    }

    fun isDeviceToolsEnabled(): Boolean = prefs.getBoolean(KEY_DEVICE_TOOLS_ENABLED, true)

    fun setDeviceToolsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEVICE_TOOLS_ENABLED, enabled).apply()
    }

    suspend fun performWebSearch(query: String, maxResults: Int = getSearchResultCount()): Result<WebSearchBundle> = withContext(Dispatchers.IO) {
        when (getSearchEngine()) {
            SearchEngineType.EXA -> exaSearchEngine.search(query, maxResults)
            SearchEngineType.TAVILY -> tavilySearchManager.search(query, maxResults)
            SearchEngineType.MWMBL -> mwmblSearchEngine.search(query, maxResults)
            SearchEngineType.SEARXNG -> searxngSearchEngine.search(query, maxResults)
        }
    }

    suspend fun enrichUserPrompt(
        userMessage: String,
        options: ChatRequestOptions,
        isRoleplay: Boolean = false
    ): EnrichedPromptResult = withContext(Dispatchers.IO) {
        val blocks = mutableListOf<String>()
        val toolRecords = mutableListOf<ToolCallRecord>()
        val trimmed = userMessage.trim()
        val searchCount = getSearchResultCount()

        // 1. 联网搜索处理 (主动意图检测或显式开启)
        val shouldSearch = options.enableWebSearch == true || isSearchIntent(trimmed)
        if (shouldSearch) {
            val engine = getSearchEngine()
            if (engine == SearchEngineType.TAVILY && !tavilySearchManager.isReady()) {
                if (options.enableWebSearch == true) {
                    blocks.add("[联网搜索状态]\n用户开启了 Tavily 搜索，但未配置有效的 Tavily API Key。建议在设置中切换为「Exa (免Key即用)」搜索引擎。")
                }
            } else {
                val searchResult = performWebSearch(trimmed, searchCount)
                searchResult.fold(
                    onSuccess = { bundle ->
                        blocks.add(bundle.toPromptBlock())
                        toolRecords.add(
                            ToolCallRecord(
                                toolType = "WEB_SEARCH",
                                toolName = "${engine.displayName} 联网搜索",
                                iconName = "Search",
                                 summary = "已检索到 ${bundle.results.size} 条参考网页",
                                detailContent = bundle.toPromptBlock(),
                                isSuccess = true
                            )
                        )
                    },
                    onFailure = { err ->
                        if (options.enableWebSearch == true) {
                            blocks.add("[联网搜索状态]\n搜索调度未能成功获取结果: ${err.message}\n请明确说明本轮未能成功联网。")
                        }
                    }
                )
            }
        }

        // 若未启用智能设备工具箱，或在角色扮演创作对话中且未显式开启设备工具，则直接返回基础处理结果
        if (!isDeviceToolsEnabled() || isRoleplay) {
            return@withContext EnrichedPromptResult(formatEnrichedMessage(userMessage, blocks), toolRecords)
        }

        // 2. 网页 URL 抓取意图（Jina Reader）
        val urlMatch = extractUrl(trimmed)
        if (urlMatch != null && shouldFetchWebpage(trimmed)) {
            val readResult = jinaReaderEngine.readUrl(urlMatch)
            readResult.fold(
                onSuccess = { page ->
                    blocks.add(page.toPromptBlock())
                    toolRecords.add(
                        ToolCallRecord(
                            toolType = "JINA_READER",
                            toolName = "Jina Reader 网页深度提取",
                            iconName = "Link",
                            summary = "成功提取网页正文: ${page.title.take(30)}",
                            detailContent = page.toPromptBlock(),
                            isSuccess = true
                        )
                    )
                },
                onFailure = { err ->
                    blocks.add("【网页提取状态】\n抓取该网页时出现异常: ${err.message}")
                }
            )
        }

        // 3. 天气意图（Open-Meteo）
        if (isWeatherIntent(trimmed)) {
            val city = extractCityForWeather(trimmed)
            val loc = locationAddressManager.getCurrentLocation()
            val weatherResult = openMeteoWeatherEngine.getWeather(
                cityName = city,
                defaultLat = loc.latitude,
                defaultLon = loc.longitude
            )
            weatherResult.fold(
                onSuccess = { report ->
                    blocks.add(report.toPromptBlock())
                    toolRecords.add(
                        ToolCallRecord(
                            toolType = "WEATHER",
                            toolName = "Open-Meteo 实时气象查询",
                            iconName = "Cloud",
                            summary = "${report.cityName}: ${report.condition} · 气温 ${report.temperature}℃ · 湿度 ${report.humidity}%",
                            detailContent = report.toPromptBlock(),
                            isSuccess = true
                        )
                    )
                },
                onFailure = { err ->
                    blocks.add("【天气服务状态】\n实时气象查询未成功: ${err.message}")
                }
            )
        }

        // 4. 时间与日历意图
        if (isTimeOrCalendarIntent(trimmed)) {
            val summary = timeCalendarManager.getTodayScheduleSummary()
            blocks.add(summary)
            toolRecords.add(
                ToolCallRecord(
                    toolType = "TIME_CALENDAR",
                    toolName = "系统时间与日历日程",
                    iconName = "Schedule",
                    summary = "当前设备时间: ${timeCalendarManager.getCurrentTimeFormatted()}",
                    detailContent = summary,
                    isSuccess = true
                )
            )
        }

        // 5. 手机健康与步数意图
        if (isHealthIntent(trimmed)) {
            val healthSummary = healthDataManager.getHealthDataSummary()
            blocks.add(healthSummary.toPromptBlock())
            val healthBrief = buildString {
                append("今日步数: ").append(healthSummary.todaySteps).append(" 步")
                if (healthSummary.heartRate > 0) append(" · 心率: ").append(healthSummary.heartRate).append(" bpm")
                if (healthSummary.sleepMinutes > 0) append(" · 睡眠: ").append(healthSummary.sleepMinutes / 60).append("小时").append(healthSummary.sleepMinutes % 60).append("分")
            }
            toolRecords.add(
                ToolCallRecord(
                    toolType = "HEALTH",
                    toolName = "华为运动健康与硬件计步",
                    iconName = "DirectionsWalk",
                    summary = healthBrief,
                    detailContent = healthSummary.toPromptBlock(),
                    isSuccess = true
                )
            )
        }

        // 6. 手机设备与硬件状态意图
        if (isDeviceStatusIntent(trimmed)) {
            val deviceStatus = deviceHardwareManager.getDeviceStatus()
            blocks.add(deviceStatus.toPromptBlock())
            toolRecords.add(
                ToolCallRecord(
                    toolType = "DEVICE_HARDWARE",
                    toolName = "手机设备与硬件状态",
                    iconName = "Smartphone",
                    summary = "电池: ${if (deviceStatus.batteryLevel >= 0) "${deviceStatus.batteryLevel}%" else "未知"}${if (deviceStatus.isCharging) " (充电中 ⚡)" else ""} · ${deviceStatus.deviceModel}",
                    detailContent = deviceStatus.toPromptBlock(),
                    isSuccess = true
                )
            )
        }

        // 7. 定位与所在位置意图
        if (isLocationIntent(trimmed)) {
            val location = locationAddressManager.getCurrentLocation()
            blocks.add(location.toPromptBlock())
            toolRecords.add(
                ToolCallRecord(
                    toolType = "LOCATION",
                    toolName = "本地定位与逆地理编码",
                    iconName = "LocationOn",
                    summary = "当前位置: ${location.city.ifBlank { "未知城市" }} ${location.district} (${location.fullAddress.take(30)})",
                    detailContent = location.toPromptBlock(),
                    isSuccess = true
                )
            )
        }

        EnrichedPromptResult(formatEnrichedMessage(userMessage, blocks), toolRecords)
    }

    private fun formatEnrichedMessage(original: String, blocks: List<String>): String {
        if (blocks.isEmpty()) return original
        return buildString {
            blocks.forEach { block ->
                append(block)
                append("\n\n")
            }
            append("【用户输入的问题/指令】\n")
            append(original)
        }
    }

    companion object {
        private const val PREFS_NAME = "echo_tool_hub_prefs"
        private const val KEY_SEARCH_ENGINE = "tool_search_engine"
        private const val KEY_SEARCH_RESULT_COUNT = "tool_search_result_count"
        private const val KEY_EXA_KEY = "tool_exa_api_key"
        private const val KEY_JINA_KEY = "tool_jina_api_key"
        private const val KEY_DEVICE_TOOLS_ENABLED = "tool_device_tools_enabled"

        private val URL_REGEX = Pattern.compile("https?://[\\w\\d:#@%/;~_?\\+-=\\\\\\.&]+")

        fun extractUrl(text: String): String? {
            val matcher = URL_REGEX.matcher(text)
            return if (matcher.find()) matcher.group(0) else null
        }

        fun shouldFetchWebpage(text: String): Boolean {
            val keywords = listOf("总结", "读一下", "看下", "提取", "网页", "文章", "链接", "分析这个", "什么内容", "分析链接", "读链接")
            if (keywords.any { text.contains(it) }) return true
            val pure = text.trim()
            return pure.startsWith("http://") || pure.startsWith("https://")
        }

        fun isSearchIntent(text: String): Boolean {
            val keywords = listOf("搜索", "搜一下", "搜一搜", "查一下", "检索", "最新动态", "今日新闻", "实时新闻", "最新消息", "当前汇率", "最新价格", "最近发生了什么", "网上的说法", "查查网上")
            return keywords.any { text.contains(it) }
        }

        fun isWeatherIntent(text: String): Boolean {
            val keywords = listOf(
                "天气", "气温", "下雨", "降雨", "温度", "穿什么", "冷不冷", "热不热",
                "刮风", "气候", "有雨吗", "预报", "下雪", "阴天", "晴天", "多云",
                "外面热吗", "外面冷吗", "带伞", "带不带伞", "降温", "升温", "穿短袖", "穿外套", "空气质量"
            )
            return keywords.any { text.contains(it) }
        }

        fun extractCityForWeather(text: String): String? {
            var cleaned = text
            val leadings = listOf("请问", "查一下", "查询", "帮我查", "帮我看一下", "帮我看看", "看一下", "看下", "查下", "告诉我", "想知道", "问一下", "我想知道")
            for (lead in leadings) {
                if (cleaned.startsWith(lead)) {
                    cleaned = cleaned.removePrefix(lead)
                }
            }

            val regex = Regex("([\\u4e00-\\u9fa5]{2,6})(?:的)?(?:天气|气温|温度|预报|下雨|降雨|晴天|多云)")
            val match = regex.find(cleaned)
            var candidate = match?.groupValues?.getOrNull(1) ?: return null

            for (lead in leadings) {
                if (candidate.startsWith(lead)) {
                    candidate = candidate.removePrefix(lead)
                }
            }

            candidate = candidate.removeSuffix("的")
            val timeWords = listOf("今天", "明天", "后天", "大后天", "这几天", "近几天", "实时", "现在", "目前", "本地", "当前")
            for (tw in timeWords) {
                if (candidate.endsWith(tw)) {
                    candidate = candidate.removeSuffix(tw)
                }
                if (candidate.startsWith(tw)) {
                    candidate = candidate.removePrefix(tw)
                }
            }
            candidate = candidate.removeSuffix("的")

            val exclusions = listOf("今天", "明天", "后天", "现在", "目前", "本地", "当前", "查一下", "看看", "天气", "气温", "预报")
            return if (candidate.length in 2..10 && candidate !in exclusions) candidate else null
        }

        fun isTimeOrCalendarIntent(text: String): Boolean {
            val keywords = listOf(
                "几点", "几月几号", "今天几号", "今天星期几", "周几", "礼拜几", "现在时间",
                "当前时间", "系统时间", "日程", "日历", "待办", "有什么安排", "今天安排",
                "今天的日程", "今天是什么日子", "农历", "节气", "什么时候了", "今天日期", "现在几点钟"
            )
            return keywords.any { text.contains(it) }
        }

        fun isHealthIntent(text: String): Boolean {
            val keywords = listOf(
                "步数", "走了多少步", "走了几步", "运动步数", "今日步数", "华为运动健康",
                "华为健康", "运动健康", "心率", "脉搏", "心跳", "睡眠", "睡得怎么样",
                "健康数据", "深睡", "浅睡", "运动量", "卡路里", "手环", "手表"
            )
            return keywords.any { text.contains(it) }
        }

        fun isDeviceStatusIntent(text: String): Boolean {
            val keywords = listOf(
                "电量", "电池", "充电", "还剩多少电", "手机型号", "系统版本", "内存剩余",
                "存储空间", "网络状态", "可用内存", "手机配置", "设备信息", "剩多少电"
            )
            return keywords.any { text.contains(it) }
        }

        fun isLocationIntent(text: String): Boolean {
            val keywords = listOf(
                "我在哪", "我的位置", "当前位置", "所在城市", "哪个城市", "什么城市",
                "当前城市", "所在地", "经纬度", "哪个区", "什么地方", "在什么地方",
                "定位", "所在位置", "在哪个省", "在哪个市"
            )
            return keywords.any { text.contains(it) }
        }
    }
}
