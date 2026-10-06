@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.aiassistant.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import com.aiassistant.ui.theme.rememberEchoSemanticColors
import com.aiassistant.domain.model.ChatModelOption
import com.aiassistant.ui.components.ImageCropEditDialog
import com.aiassistant.ui.components.CropShapeMode
import com.aiassistant.ui.components.ExpandableText
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiassistant.AiAssistantApp
import com.aiassistant.BuildConfig
import com.aiassistant.R
import com.aiassistant.data.repository.AiRepository
import com.aiassistant.domain.model.ApiConfig
import com.aiassistant.domain.model.NamedApiKey
import com.aiassistant.domain.model.Conversation
import com.aiassistant.domain.model.EnvironmentVariable
import com.aiassistant.domain.model.MemoryItem
import com.aiassistant.domain.model.WorldBook
import com.aiassistant.domain.model.WorldBookEntry
import com.aiassistant.domain.model.ModelCapabilityEngine
import com.aiassistant.domain.model.ModelCustomSettings
import com.aiassistant.domain.model.PromptTemplate
import com.aiassistant.ui.components.EchoSettingRow
import com.aiassistant.ui.components.EchoSwitch
import com.aiassistant.ui.components.EchoGlassCard
import com.aiassistant.ui.components.EchoGlassDialog
import com.aiassistant.ui.components.EchoGlassDropdownMenu
import com.aiassistant.ui.components.readableTextColorFor
import com.aiassistant.ui.components.rememberReadableBackdropColors
import com.aiassistant.ui.components.echoFilterChipBorder
import com.aiassistant.ui.components.echoFilterChipColors
import com.aiassistant.ui.components.echoFilterChipElevation
import com.aiassistant.ui.components.echoGlassPalette
import com.aiassistant.ui.components.echoSegmentedButtonBorder
import com.aiassistant.ui.components.echoSegmentedButtonColors
import com.aiassistant.ui.components.echoHazePanel
import com.aiassistant.ui.components.echoHazeSource
import com.aiassistant.ui.components.echoShapeClick
import com.aiassistant.ui.components.rememberEchoHazeState
import com.aiassistant.ui.components.rememberSmoothReorderState
import com.aiassistant.ui.components.reorderItem
import com.aiassistant.ui.components.reorderDragHandle
import com.aiassistant.ui.theme.EchoTokens
import com.aiassistant.utils.AvatarManager
import com.aiassistant.utils.BackgroundImageManager
import com.aiassistant.utils.BackupManager
import com.aiassistant.utils.HiddenConversationLock
import com.aiassistant.utils.TavilySearchSettings
import com.aiassistant.utils.AppThemeMode
import com.aiassistant.tools.search.SearchEngineType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.aiassistant.tools.cloud.OpenMeteoWeatherEngine
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun WebSearchTab(
    hazeState: dev.chrisbanes.haze.HazeState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp)
) {
    val toolHub = AiAssistantApp.instance.echoToolHub
    val tavilyManager = AiAssistantApp.instance.tavilySearchManager
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    var searchEngine by remember { mutableStateOf(toolHub.getSearchEngine()) }
    var searchResultCount by remember { mutableIntStateOf(toolHub.getSearchResultCount()) }
    var searxngUrl by remember { mutableStateOf(toolHub.getSearxngUrl()) }
    var exaApiKey by remember { mutableStateOf(toolHub.getExaApiKey()) }
    var jinaApiKey by remember { mutableStateOf(toolHub.getJinaApiKey()) }
    var deviceToolsEnabled by remember { mutableStateOf(toolHub.isDeviceToolsEnabled()) }
    var manualCity by remember { mutableStateOf(toolHub.locationAddressManager.getManualCity()) }

    var tavilySettings by remember { mutableStateOf(tavilyManager.getSettings()) }
    var tavilyApiKey by remember(tavilySettings) { mutableStateOf(tavilySettings.apiKey) }
    var tavilyEnabled by remember(tavilySettings) { mutableStateOf(tavilySettings.enabled) }
    var searchDepth by remember(tavilySettings) { mutableStateOf(tavilySettings.searchDepth) }
    var maxResults by remember(tavilySettings) { mutableStateOf(tavilySettings.maxResults.toString()) }
    var includeAnswer by remember(tavilySettings) { mutableStateOf(tavilySettings.includeAnswer) }

    var weatherTestResult by remember { mutableStateOf<String?>(null) }
    var isTestingWeather by remember { mutableStateOf(false) }

    var showHuaweiHealthSyncDialog by remember { mutableStateOf(false) }
    var savedMessage by remember { mutableStateOf<String?>(null) }

    fun syncAllSettings() {
        toolHub.setSearchEngine(searchEngine)
        toolHub.setSearchResultCount(searchResultCount)
        toolHub.setExaApiKey(exaApiKey)
        toolHub.setJinaApiKey(jinaApiKey)
        toolHub.setDeviceToolsEnabled(deviceToolsEnabled)
        toolHub.locationAddressManager.setManualCity(manualCity)

        val newTavily = TavilySearchSettings(
            enabled = tavilyEnabled,
            apiKey = tavilyApiKey,
            searchDepth = searchDepth,
            maxResults = maxResults.toIntOrNull()?.coerceIn(1, 20) ?: 8,
            includeAnswer = includeAnswer
        )
        tavilyManager.saveSettings(newTavily)
        tavilySettings = tavilyManager.getSettings()
    }

    val currentTime = remember { toolHub.timeCalendarManager.getCurrentTimeFormatted() }
    var currentLocation by remember { mutableStateOf(toolHub.locationAddressManager.getCurrentLocation()) }
    var currentHealthSummary by remember { mutableStateOf(toolHub.healthDataManager.getHealthDataSummary()) }
    val deviceStatus = remember { toolHub.deviceHardwareManager.getDeviceStatus() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(EchoTokens.Spacing.cardGap)
    ) {
        // 卡片 1：搜索引擎选择
        item {
            SettingsGlassCard(hazeState = hazeState) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("联网搜索引擎", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text(
                    "Exa 托管通道与 Mwmbl 无需 Key；Tavily 需要 Key；SearXNG 需要允许 JSON 搜索的实例地址。免费通道可能限流，不保证长期可用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchEngineType.entries.forEach { engine ->
                        item(key = engine.name) {
                            val selected = searchEngine == engine
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    searchEngine = engine
                                    toolHub.setSearchEngine(engine)
                                },
                                colors = echoFilterChipColors(),
                                border = echoFilterChipBorder(selected),
                                elevation = echoFilterChipElevation(),
                                label = { Text(engine.displayName) }
                            )
                        }
                    }
                }

                if (searchEngine == SearchEngineType.EXA) {
                    Surface(
                        shape = com.aiassistant.ui.theme.EchoTokens.Radius.shapeMd,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "免 Key 托管通道：无需申请密钥，可能受服务端配额与限流影响。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    SettingsInputField(
                        title = "Exa API Key (选填)",
                        value = exaApiKey,
                        onValueChange = {
                            exaApiKey = it
                            toolHub.setExaApiKey(it)
                        },
                        placeholder = "留空则使用官方免Key通道"
                    )
                } else if (searchEngine == SearchEngineType.MWMBL) {
                    Text("Mwmbl 是独立开源搜索引擎，无需账号或 Key。索引规模较小，结果可能不足设定条数；不以无关结果凑数。", style = MaterialTheme.typography.bodySmall)
                } else if (searchEngine == SearchEngineType.SEARXNG) {
                    SettingsInputField(
                        title = "SearXNG 实例地址（HTTPS）",
                        value = searxngUrl,
                        onValueChange = { searxngUrl = it; toolHub.setSearxngUrl(it) },
                        placeholder = "https://你的实例地址"
                    )
                    Text("无需 Key，但不是配置前即可用。公共实例可能禁止 JSON 或限流；查询会发送到你填写的实例。最多读取 10 页并去重。", style = MaterialTheme.typography.bodySmall)
                } else {
                    EchoSettingRow(
                        title = "启用 Tavily 搜索",
                        contentPaddingHorizontal = 0.dp
                    ) {
                        EchoSwitch(
                            checked = tavilyEnabled,
                            onCheckedChange = {
                                tavilyEnabled = it
                                val newTavily = tavilySettings.copy(enabled = it)
                                tavilyManager.saveSettings(newTavily)
                                tavilySettings = tavilyManager.getSettings()
                            }
                        )
                    }

                    SettingsInputField(
                        title = "Tavily API Key",
                        value = tavilyApiKey,
                        onValueChange = {
                            tavilyApiKey = it
                            val newTavily = tavilySettings.copy(apiKey = it)
                            tavilyManager.saveSettings(newTavily)
                            tavilySettings = tavilyManager.getSettings()
                        },
                        placeholder = "tvly-..."
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("basic", "advanced").forEach { depth ->
                            val selected = searchDepth == depth
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    searchDepth = depth
                                    val newTavily = tavilySettings.copy(searchDepth = depth)
                                    tavilyManager.saveSettings(newTavily)
                                    tavilySettings = tavilyManager.getSettings()
                                },
                                colors = echoFilterChipColors(),
                                border = echoFilterChipBorder(selected),
                                elevation = echoFilterChipElevation(),
                                label = { Text(if (depth == "basic") "基础搜索" else "深入搜索") }
                            )
                        }
                    }

                    SettingsInputField(
                        title = "最大结果数 (1-20)",
                        value = maxResults,
                        onValueChange = { v ->
                            val cleaned = v.filter { it.isDigit() }.take(2)
                            maxResults = cleaned
                            val newTavily = tavilySettings.copy(maxResults = cleaned.toIntOrNull()?.coerceIn(1, 20) ?: 8)
                            tavilyManager.saveSettings(newTavily)
                            tavilySettings = tavilyManager.getSettings()
                        },
                        placeholder = "8",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    EchoSettingRow(
                        title = "包含 Tavily 自动摘要",
                        contentPaddingHorizontal = 0.dp
                    ) {
                        EchoSwitch(
                            checked = includeAnswer,
                            onCheckedChange = {
                                includeAnswer = it
                                val newTavily = tavilySettings.copy(includeAnswer = it)
                                tavilyManager.saveSettings(newTavily)
                                tavilySettings = tavilyManager.getSettings()
                            }
                        )
                    }
                }

                // Requested count is shared; provider quotas are shown separately.
                var showCustomCountDialog by remember { mutableStateOf(false) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "搜索返回条数：当前 $searchResultCount 条",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    androidx.compose.foundation.lazy.LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(3, 5, 8, 10).forEach { count ->
                            item(key = count) {
                                val selected = searchResultCount == count
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        searchResultCount = count
                                        toolHub.setSearchResultCount(count)
                                    },
                                    colors = echoFilterChipColors(),
                                    border = echoFilterChipBorder(selected),
                                    elevation = echoFilterChipElevation(),
                                    label = { Text(if (count == 5) "5条 (推荐)" else "${count}条") }
                                )
                            }
                        }
                        item(key = "custom") {
                            val isCustom = searchResultCount !in listOf(3, 5, 8, 10)
                            FilterChip(
                                selected = isCustom,
                                onClick = { showCustomCountDialog = true },
                                colors = echoFilterChipColors(),
                                border = echoFilterChipBorder(isCustom),
                                elevation = echoFilterChipElevation(),
                                label = { Text(if (isCustom) "自定义: ${searchResultCount}条" else "自定义...") }
                            )
                        }
                    }
                    Text("可请求 1–100 条；Tavily 单次最多 20 条，Exa / Mwmbl 受服务端与索引限制，SearXNG 受实例分页限制。实际返回可能更少；条数越大，耗时和上下文占用越高。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    if (showCustomCountDialog) {
                        var customInput by remember { mutableStateOf(searchResultCount.toString()) }
                        AlertDialog(
                            onDismissRequest = { showCustomCountDialog = false },
                            title = { Text("自定义搜索结果数 (1-100)") },
                            text = {
                                BasicTextField(
                                    value = customInput,
                                    onValueChange = { customInput = it.filter { char -> char.isDigit() }.take(3) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    decorationBox = { innerTextField ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                                .border(
                                                    BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .padding(horizontal = 12.dp, vertical = 9.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            if (customInput.isEmpty()) {
                                                Text(
                                                    text = "请输入条数 (1~100)...",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                                )
                                            }
                                            innerTextField()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            confirmButton = {
                                Button(enabled = customInput.toIntOrNull() in 1..100, onClick = {
                                    val count = customInput.toIntOrNull() ?: return@Button
                                    searchResultCount = count
                                    toolHub.setSearchResultCount(count)
                                    showCustomCountDialog = false
                                }) {
                                    Text("确定")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showCustomCountDialog = false }) {
                                    Text("取消")
                                }
                            }
                        )
                    }
                }
            }
        }

        // 卡片 2：手机本地设备与健康工具
        item {
            val activityRecognitionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                toolHub.healthDataManager.forceRefreshHardwareSteps()
                currentHealthSummary = toolHub.healthDataManager.getHealthDataSummary()
            }

            SettingsGlassCard(hazeState = hazeState) {
                EchoSettingRow(
                    title = "手机设备与健康数据联动",
                    subtitle = "允许模型根据问题读取时间、定位、步数、心率、睡眠及硬件",
                    icon = Icons.Default.Smartphone,
                    contentPaddingHorizontal = 0.dp
                ) {
                    EchoSwitch(
                        checked = deviceToolsEnabled,
                        onCheckedChange = {
                            deviceToolsEnabled = it
                            toolHub.setDeviceToolsEnabled(it)
                        }
                    )
                }

                if (deviceToolsEnabled) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // 时间与日历
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("系统时间：", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Text(currentTime, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    // 定位与逆地理编码
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("当前定位：", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Text(currentLocation.city.ifBlank { "检测中" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { currentLocation = toolHub.locationAddressManager.getCurrentLocation() }) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("刷新定位", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (currentLocation.fullAddress.isNotBlank()) {
                            Text("详细位置: ${currentLocation.fullAddress}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        SettingsInputField(
                            title = "手动指定常驻城市（选填，留空自动定位）",
                            value = manualCity,
                            onValueChange = {
                                manualCity = it
                                toolHub.locationAddressManager.setManualCity(it)
                            },
                            placeholder = "例如：深圳 / 北京 / 上海"
                        )
                    }

                    // 健康与运动数据 (华为运动健康与硬件计步)
                    val isHealthInstalled = remember { toolHub.healthDataManager.isHuaweiHealthInstalled() }
                    val healthAppName = remember { toolHub.healthDataManager.getHealthAppName() }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // 标题栏与安装状态徽标
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.DirectionsWalk,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "运动健康与设备体征",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (isHealthInstalled) rememberEchoSemanticColors().success.container else MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isHealthInstalled) rememberEchoSemanticColors().success.border else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                    )
                                ) {
                                    Text(
                                        text = if (isHealthInstalled) "已安装 $healthAppName" else "未检测到健康App",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isHealthInstalled) rememberEchoSemanticColors().success.main else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // 3列体征高对比指标卡
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // 步数
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("今日步数", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            "${currentHealthSummary.todaySteps}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text("步", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                                    }
                                }

                                // 心率
                                val hrVal = currentHealthSummary.heartRate
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    color = rememberEchoSemanticColors().error.container.copy(alpha = 0.35f),
                                    border = BorderStroke(1.dp, rememberEchoSemanticColors().error.border.copy(alpha = 0.3f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("静息心率", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            if (hrVal > 0) "$hrVal" else "--",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (hrVal > 0) rememberEchoSemanticColors().error.main else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(if (hrVal > 0) "bpm" else "未录入", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

                                // 睡眠
                                val sleepMins = currentHealthSummary.sleepMinutes
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    color = rememberEchoSemanticColors().info.container.copy(alpha = 0.35f),
                                    border = BorderStroke(1.dp, rememberEchoSemanticColors().info.border.copy(alpha = 0.3f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("昨晚睡眠", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            if (sleepMins > 0) "${sleepMins / 60}h${sleepMins % 60}m" else "--",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (sleepMins > 0) rememberEchoSemanticColors().info.main else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(if (sleepMins > 0 && currentHealthSummary.sleepScore > 0) "评分 ${currentHealthSummary.sleepScore}" else if (sleepMins > 0) "已记录" else "未录入", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }

                            // 状态详情与更新时间 (使用优雅格式化时间)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "传感器: ${toolHub.healthDataManager.getSensorStatusText()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "同步: ${toolHub.healthDataManager.getFormattedLastUpdateTime()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // 操作按钮栏 (清晰分行，响应式三等分)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
                                            !toolHub.healthDataManager.hasActivityRecognitionPermission()
                                        ) {
                                            activityRecognitionLauncher.launch(android.Manifest.permission.ACTIVITY_RECOGNITION)
                                        } else {
                                            toolHub.healthDataManager.forceRefreshHardwareSteps()
                                            currentHealthSummary = toolHub.healthDataManager.getHealthDataSummary()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("刷新步数", style = MaterialTheme.typography.labelSmall)
                                }

                                OutlinedButton(
                                    onClick = { showHuaweiHealthSyncDialog = true },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("数据校准", style = MaterialTheme.typography.labelSmall)
                                }

                                OutlinedButton(
                                    onClick = {
                                        if (!toolHub.healthDataManager.openHuaweiHealthApp(context)) {
                                            savedMessage = "未检测到已安装的华为运动健康应用"
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("打开健康App", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    // 硬件状态
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Smartphone, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "电池: ${if (deviceStatus.batteryLevel >= 0) "${deviceStatus.batteryLevel}%" else "未知"}${if (deviceStatus.isCharging) " (充电中 ⚡)" else ""} · ${deviceStatus.deviceModel} · ${deviceStatus.networkType}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 卡片 3：公开免 Key 云端工具 (Open-Meteo 与 Jina Reader)
        item {
            SettingsGlassCard(hazeState = hazeState) {
                Text("开放免Key云端工具", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                // Open-Meteo
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Open-Meteo 全球气象 (免Key)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text("全球高精度天气与预报，无需任何 API Key", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(
                            onClick = {
                                isTestingWeather = true
                                coroutineScope.launch {
                                    val loc = toolHub.locationAddressManager.getCurrentLocation()
                                    val res = withContext(Dispatchers.IO) {
                                        toolHub.openMeteoWeatherEngine.getWeather(
                                            cityName = manualCity.ifBlank { null },
                                            defaultLat = loc.latitude,
                                            defaultLon = loc.longitude
                                        )
                                    }
                                    isTestingWeather = false
                                    weatherTestResult = res.fold(
                                        onSuccess = { "${it.cityName}: ${it.condition} · 气温 ${String.format(Locale.US, "%.1f", it.temperature)}℃ · 湿度 ${it.humidity}% · 风速 ${String.format(Locale.US, "%.1f", it.windSpeed)}km/h" },
                                        onFailure = { "查询失败: ${it.message}" }
                                    )
                                }
                            },
                            enabled = !isTestingWeather
                        ) {
                            Text(if (isTestingWeather) "测试中..." else "测试天气")
                        }
                    }
                    weatherTestResult?.let { result ->
                        Surface(
                            shape = com.aiassistant.ui.theme.EchoTokens.Radius.shapeSm,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Text(result, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // Jina Reader
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Jina Reader 网页长文提取", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text("输入网页网址时，自动抓取并转换为纯净 Markdown 正文，免去广告干扰。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SettingsInputField(
                        title = "Jina API Key (选填)",
                        value = jinaApiKey,
                        onValueChange = {
                            jinaApiKey = it
                            toolHub.setJinaApiKey(it)
                        },
                        placeholder = "留空使用免费通道，填入可避免数据中心限制"
                    )
                }
            }
        }

        // 自动存盘提示
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = com.aiassistant.ui.theme.EchoTokens.Radius.shapeMd,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "所有联网搜索与工具配置修改后均已实时自动存盘生效，无需手动保存",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        savedMessage?.let { message ->
            item {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    if (showHuaweiHealthSyncDialog) {
        HuaweiHealthSyncDialog(
            initialSteps = currentHealthSummary.todaySteps,
            initialHeartRate = currentHealthSummary.heartRate,
            initialSleepMinutes = currentHealthSummary.sleepMinutes,
            onDismiss = { showHuaweiHealthSyncDialog = false },
            onCalibrateSteps = { steps ->
                toolHub.healthDataManager.calibrateTodaySteps(steps)
                currentHealthSummary = toolHub.healthDataManager.getHealthDataSummary()
            },
            onSyncAll = { steps, hr, sleep, deepSleep, score ->
                toolHub.healthDataManager.syncHuaweiHealthData(steps, hr, sleep, deepSleep, score)
                currentHealthSummary = toolHub.healthDataManager.getHealthDataSummary()
            }
        )
    }
}

@Composable
fun HuaweiHealthSyncDialog(
    initialSteps: Int,
    initialHeartRate: Int,
    initialSleepMinutes: Int,
    onDismiss: () -> Unit,
    onCalibrateSteps: (Int) -> Unit,
    onSyncAll: (steps: Int, heartRate: Int, sleepMinutes: Int, deepSleepMinutes: Int, score: Int) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var stepsText by remember { mutableStateOf(if (initialSteps > 0) initialSteps.toString() else "") }
    var heartRateText by remember { mutableStateOf(if (initialHeartRate > 0) initialHeartRate.toString() else "") }
    var sleepHoursText by remember { mutableStateOf(if (initialSleepMinutes > 0) (initialSleepMinutes / 60).toString() else "") }
    var sleepMinsText by remember { mutableStateOf(if (initialSleepMinutes > 0) (initialSleepMinutes % 60).toString() else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.DirectionsWalk, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("华为运动健康数据校准与同步", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            val healthDataManager = remember { AiAssistantApp.instance.echoToolHub.healthDataManager }
            val isInstalled = remember { healthDataManager.isHuaweiHealthInstalled() }
            val appName = remember { healthDataManager.getHealthAppName() }
            val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = (screenHeight * 0.72f).dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "机制说明：受 Android 系统安全沙箱保护，三方应用无法直接跨应用暗中读取华为运动健康私有数据。当前步数由本机硬件计步传感器自动累加；若需将手环/手表记录的心率与睡眠同步给 AI，可点击下方打开健康 APP 对照填入。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                OutlinedButton(
                    onClick = {
                        healthDataManager.openHuaweiHealthApp(context)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isInstalled) "打开 $appName 查看数据" else "打开华为运动健康 APP 查看数据")
                }

                SettingsInputField(
                    title = "今日实时步数 (步)",
                    value = stepsText,
                    onValueChange = { stepsText = it.filter { c -> c.isDigit() }.take(6) },
                    placeholder = if (initialSteps > 0) initialSteps.toString() else "输入今日步数",
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )

                SettingsInputField(
                    title = "静态/静息心率 (bpm，可选)",
                    value = heartRateText,
                    onValueChange = { heartRateText = it.filter { c -> c.isDigit() }.take(3) },
                    placeholder = "留空表示未录入",
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        SettingsInputField(
                            title = "昨晚睡眠 (小时)",
                            value = sleepHoursText,
                            onValueChange = { sleepHoursText = it.filter { c -> c.isDigit() }.take(2) },
                            placeholder = "可选，如 7",
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        SettingsInputField(
                            title = "睡眠零头 (分钟)",
                            value = sleepMinsText,
                            onValueChange = { sleepMinsText = it.filter { c -> c.isDigit() }.take(2) },
                            placeholder = "可选，如 30",
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val steps = stepsText.toIntOrNull() ?: initialSteps
                    val hr = heartRateText.toIntOrNull() ?: -1
                    val totalSleep = if (sleepHoursText.isNotBlank() || sleepMinsText.isNotBlank()) {
                        (sleepHoursText.toIntOrNull() ?: 0) * 60 + (sleepMinsText.toIntOrNull() ?: 0)
                    } else {
                        -1
                    }
                    val deepSleep = if (totalSleep > 0) (totalSleep * 0.28f).toInt() else -1
                    val score = if (totalSleep in 420..540) 88 else if (totalSleep > 0) 80 else -1
                    onSyncAll(steps, hr, totalSleep, deepSleep, score)
                    onDismiss()
                }
            ) {
                Text("保存并完成校准")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

data class FullModelChoice(
    val configId: Long,
    val configName: String,
    val provider: String,
    val modelName: String,
    val isDefault: Boolean = false
)
