package com.aiassistant.ui.screens.stats

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiassistant.ui.theme.EchoChartPalette
import com.aiassistant.ui.theme.rememberEchoChartColors
import com.aiassistant.ui.components.EchoGlassPagePanelShape
import com.aiassistant.ui.components.EchoGlassDropdownMenu
import com.aiassistant.ui.components.EchoWallpaperBackground
import com.aiassistant.ui.components.echoGlassPalette
import com.aiassistant.ui.components.echoHazePanel
import com.aiassistant.ui.components.echoShapeClick
import com.aiassistant.ui.components.readableTextColorFor
import com.aiassistant.ui.components.rememberEchoHazeState
import com.aiassistant.ui.components.rememberReadableBackdropColor
import com.aiassistant.utils.BackgroundImageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.pow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 使用统计 · 数据看板（v2.6.1 全面改版）
 *
 * 在原有「概览 + Token 趋势 + 成功率走势 + 模型明细」基础上新增：
 * 1. 核心概览升级：环比上一周期（Token/请求量增减）、失败次数、缓存命中率、平均响应、峰值单段；
 * 2. Token 构成环形图（输入/输出/思考/其他占比）；
 * 3. 24 小时调用分布直方图（定位最活跃时段）；
 * 4. 供应商 Token 占比横条；
 * 5. 失败原因 Top 归纳（仅在有失败记录时出现）。
 *
 * 纯统计逻辑（聚合/分桶/占比/格式化）保持 internal 纯函数，供 StatsDashboardTest 覆盖；
 * SQLite 只读查询层保留 v2.5.x 的容错设计（表结构不满足时优雅降级）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onNavigateBack: () -> Unit
) {
    val localContext = LocalContext.current
    val context = localContext.applicationContext
    val statsBackgroundBitmap = remember(localContext) {
        BackgroundImageManager.getHomeBackgroundBitmap(localContext)
    }
    val hazeState = rememberEchoHazeState()
    val readableBackdrop = rememberReadableBackdropColor(statsBackgroundBitmap)
    var selectedPeriod by remember { mutableStateOf(StatsPeriod.Day) }
    // 模型多选（v2.6.7 需求 5d）：记录被取消选择的模型集合，空集合 = 全部模型选中（默认）；
    // 点击「全部模型」在全选/全不选间切换，点击单个模型行切换其选中态
    var deselectedModels by remember { mutableStateOf(setOf<String>()) }
    var refreshKey by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var stats by remember { mutableStateOf<List<UsageRow>>(emptyList()) }
    var previousStats by remember { mutableStateOf<List<UsageRow>>(emptyList()) }
    var statusText by remember { mutableStateOf("正在读取统计") }
    // 悬浮筛选栏的下拉展开状态：0=均收起 1=时间范围 2=模型
    var expandedDropdown by remember { mutableStateOf(0) }

    // 一次读取覆盖「当前周期 + 上一周期」两个窗口，用于环比对比
    LaunchedEffect(selectedPeriod, refreshKey) {
        val endTime = System.currentTimeMillis()
        val startTime = endTime - selectedPeriod.durationMillis
        val result = readUsageRows(context, startTime - selectedPeriod.durationMillis, endTime)
        stats = result.rows.filter { it.timestamp >= startTime }
        previousStats = result.rows.filter { it.timestamp < startTime }
        statusText = result.message
    }

    val availableModels = remember(stats) {
        stats.map { it.modelName }.distinct().sorted()
    }

    val filteredStats = remember(stats, deselectedModels) {
        if (deselectedModels.isEmpty()) stats else stats.filter { it.modelName !in deselectedModels }
    }
    val filteredPrevious = remember(previousStats, deselectedModels) {
        if (deselectedModels.isEmpty()) previousStats else previousStats.filter { it.modelName !in deselectedModels }
    }

    // 筛选摘要标签：全部选中时为 null（标题不带筛选后缀）；单选显示模型名；多选/未选显示计数
    val modelFilterLabel: String? = remember(availableModels, deselectedModels) {
        when {
            deselectedModels.isEmpty() -> null
            else -> {
                val selectedCount = availableModels.count { it !in deselectedModels }
                when {
                    selectedCount == 0 -> "未选择模型"
                    selectedCount == 1 -> availableModels.first { it !in deselectedModels }
                    else -> "已选 $selectedCount 个模型"
                }
            }
        }
    }

    val summary = remember(filteredStats) { filteredStats.toSummary() }
    val previousSummary = remember(filteredPrevious) { filteredPrevious.toSummary() }
    val hasPreviousData = previousSummary.requestCount > 0
    val tokensDelta = remember(summary.totalTokens, previousSummary.totalTokens) {
        computeDeltaPct(summary.totalTokens.toLong(), previousSummary.totalTokens.toLong())
    }
    val requestsDelta = remember(summary.requestCount, previousSummary.requestCount) {
        computeDeltaPct(summary.requestCount.toLong(), previousSummary.requestCount.toLong())
    }

    val buckets = remember(filteredStats, selectedPeriod, refreshKey) {
        buildBuckets(filteredStats, selectedPeriod, System.currentTimeMillis())
    }
    val modelRows = remember(filteredStats) { filteredStats.toModelRows() }
    val hourSlices = remember(filteredStats) { filteredStats.toHourSlices() }
    val providerShares = remember(filteredStats) { filteredStats.toProviderShares() }
    val failureSlices = remember(filteredStats) { filteredStats.toFailureSlices() }
    val modelDonutSlices = remember(filteredStats) { filteredStats.toModelDonutSlices() }
    val modelTokenSeries = remember(filteredStats, selectedPeriod, refreshKey) {
        buildModelTokenSeries(filteredStats, selectedPeriod, System.currentTimeMillis())
    }
    val healthCells = remember(filteredStats, selectedPeriod, refreshKey) {
        buildHealthCells(filteredStats, selectedPeriod, System.currentTimeMillis())
    }

    EchoWallpaperBackground(
        backgroundBitmap = statsBackgroundBitmap,
        hazeState = hazeState
    ) {
        // v2.6.7 需求 5a：与设置页同款真悬浮栏——列表从透明毛玻璃栏下方穿透滚动；
        // 顶栏（返回/标题/刷新）下方同一行放「时间范围」「模型」两个悬浮下拉胶囊
        val glass = echoGlassPalette()
        val toolbarShape = RoundedCornerShape(22.dp)
        val toolbarTint = glass.input
        val toolbarContentColor = readableTextColorFor(
            background = toolbarTint,
            fallbackSurface = readableBackdrop
        )
        val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        // 顶栏 56 + 外边距 6 + 选择器行约 40 + 间距 8 + 呼吸余量 16
        val topBarsHeight = topInset + 56.dp + 6.dp + 40.dp + 8.dp + 16.dp

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topBarsHeight, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. 核心概览：总量 + 环比 + 九宫格指标
                item {
                    HeroSummaryCard(
                        hazeState = hazeState,
                        summary = summary,
                        period = selectedPeriod,
                        selectedModel = modelFilterLabel,
                        statusText = statusText,
                        hasPreviousData = hasPreviousData,
                        tokensDelta = tokensDelta,
                        requestsDelta = requestsDelta,
                        readableBackdrop = readableBackdrop
                    )
                }

                if (filteredStats.isEmpty()) {
                    item {
                        EmptyCard(
                            hazeState = hazeState,
                            text = "当前筛选条件下暂无统计记录",
                            readableBackdrop = readableBackdrop
                        )
                    }
                } else {
                    // 4. Token 构成环形图（Token 类型 / 模型占比双视图）
                    item {
                        TokenDonutCard(
                            hazeState = hazeState,
                            summary = summary,
                            modelSlices = modelDonutSlices,
                            readableBackdrop = readableBackdrop
                        )
                    }

                    // 5. 每模型 Token 消耗趋势（平滑折线）
                    item {
                        ChartCard(
                            hazeState = hazeState,
                            title = if (modelFilterLabel != null) "$modelFilterLabel · Token 消耗趋势" else "Token 消耗趋势",
                            subtitle = "按模型分色的平滑曲线，展示各时间分段的 Token 消耗走势",
                            readableBackdrop = readableBackdrop
                        ) { chartContentColor ->
                            ModelTokenTrendChart(
                                series = modelTokenSeries,
                                buckets = buckets,
                                maxToken = niceAxisMax(
                                    modelTokenSeries.flatMap { it.values }.maxOrNull() ?: 0
                                ),
                                labelColor = chartContentColor.copy(alpha = 0.72f)
                            )
                        }
                    }

                    // 6. 成功率走势曲线图
                    item {
                        ChartCard(
                            hazeState = hazeState,
                            title = "调用成功率走势",
                            subtitle = "按时间分段统计 API 调用的成功率变化曲线",
                            readableBackdrop = readableBackdrop,
                            legend = { chartColors ->
                                listOf("成功率" to chartColors.secondary)
                            }
                        ) { chartContentColor ->
                            ModernTrendChart(
                                buckets = buckets,
                                labelColor = chartContentColor.copy(alpha = 0.72f)
                            )
                        }
                    }

                    // 7. 请求健康时间线（热力矩形看板，点击方格查看局部时段明细）
                    item {
                        HealthTimelineCard(
                            hazeState = hazeState,
                            cells = healthCells,
                            period = selectedPeriod,
                            readableBackdrop = readableBackdrop
                        )
                    }

                    // 8. 24 小时调用分布
                    item {
                        HourActivityCard(
                            hazeState = hazeState,
                            slices = hourSlices,
                            readableBackdrop = readableBackdrop
                        )
                    }

                    // 9. 供应商 Token 占比
                    item {
                        ProviderShareCard(
                            hazeState = hazeState,
                            shares = providerShares,
                            readableBackdrop = readableBackdrop
                        )
                    }

                    // 10. 失败原因归纳（仅存在失败记录时展示）
                    if (failureSlices.isNotEmpty()) {
                        item {
                            FailureAnalysisCard(
                                hazeState = hazeState,
                                failures = failureSlices,
                                readableBackdrop = readableBackdrop
                            )
                        }
                    }
                }

                // 11. 模型明细表格（v2.6.7 需求 5g：卡片内已自带「模型统计明细」标题，删去表格外重复标题）
                if (modelRows.isEmpty()) {
                    item {
                        EmptyCard(
                            hazeState = hazeState,
                            text = "当前筛选条件下暂无统计记录",
                            readableBackdrop = readableBackdrop
                        )
                    }
                } else {
                    item {
                        ModernModelStatsTable(
                            hazeState = hazeState,
                            rows = modelRows,
                            readableBackdrop = readableBackdrop
                        )
                    }
                }
            }

            // 下拉展开时的点击遮罩：点击空白处收起下拉列表
            if (expandedDropdown != 0) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(expandedDropdown) {
                            detectTapGestures {
                                expandedDropdown = 0
                            }
                        }
                )
            }

            // 顶部悬浮区：玻璃顶栏 + 同行悬浮下拉（时间范围 / 模型），列表从下方穿透滚动
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .echoHazePanel(
                            hazeState = hazeState,
                            shape = toolbarShape,
                            tint = toolbarTint,
                            blurRadius = 16.dp,
                            highlightAlpha = 0.025f
                        ),
                    shape = toolbarShape,
                    color = Color.Transparent,
                    contentColor = toolbarContentColor,
                    border = BorderStroke(1.dp, glass.outline),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                        StatsHeaderIcon()
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "使用统计",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { refreshKey = System.currentTimeMillis() }) {
                            Text("刷新")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 时间范围 + 模型：同一行两个悬浮下拉胶囊（可向下展开为列表）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatsFilterDropdown(
                        modifier = Modifier.weight(1f),
                        label = "时间范围",
                        value = selectedPeriod.label,
                        expanded = expandedDropdown == 1,
                        onToggle = { expandedDropdown = if (expandedDropdown == 1) 0 else 1 },
                        hazeState = hazeState,
                        readableBackdrop = readableBackdrop
                    ) {
                        StatsPeriod.entries.forEach { period ->
                            DropdownOptionRow(
                                text = period.label,
                                selected = period == selectedPeriod,
                                contentColor = toolbarContentColor,
                                onClick = {
                                    selectedPeriod = period
                                    refreshKey = System.currentTimeMillis()
                                    expandedDropdown = 0
                                }
                            )
                        }
                    }

                    StatsFilterDropdown(
                        modifier = Modifier.weight(1f),
                        label = "模型",
                        value = modelFilterLabel ?: "全部模型",
                        expanded = expandedDropdown == 2,
                        onToggle = {
                            if (availableModels.isNotEmpty()) {
                                expandedDropdown = if (expandedDropdown == 2) 0 else 2
                            }
                        },
                        hazeState = hazeState,
                        readableBackdrop = readableBackdrop
                    ) {
                        DropdownOptionRow(
                            text = "全部模型",
                            selected = deselectedModels.isEmpty(),
                            contentColor = toolbarContentColor,
                            onClick = {
                                // 全部选中时点击 = 取消全选；否则恢复全选
                                deselectedModels = if (deselectedModels.isEmpty()) availableModels.toSet() else emptySet()
                            }
                        )
                        availableModels.forEach { model ->
                            DropdownOptionRow(
                                text = com.aiassistant.domain.model.ModelDisplayName.format(model),
                                selected = model !in deselectedModels,
                                contentColor = toolbarContentColor,
                                onClick = {
                                    deselectedModels = if (model in deselectedModels) {
                                        deselectedModels - model
                                    } else {
                                        deselectedModels + model
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsHeaderIcon() {
    val glass = echoGlassPalette()
    val glassBlue = MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier.size(36.dp),
        shape = CircleShape,
        color = glass.control,
        contentColor = glassBlue,
        border = BorderStroke(0.8.dp, glass.outline),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(glassBlue.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .width(14.dp)
                        .height(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.5.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    listOf(4.5.dp, 8.5.dp, 12.5.dp).forEach { barHeight ->
                        Box(
                            modifier = Modifier
                                .width(2.8.dp)
                                .height(barHeight)
                                .background(glassBlue, RoundedCornerShape(1.dp))
                        )
                    }
                }
            }
        }
    }
}

/**
 * 统计页悬浮筛选下拉（v2.6.7 需求 5a）：玻璃胶囊 + 向下展开的玻璃选项列表。
 * 展开列表面浮在滚动内容之上，同一行可并排放置时间范围与模型两个下拉。
 */
@Composable
private fun StatsFilterDropdown(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    hazeState: dev.chrisbanes.haze.HazeState,
    readableBackdrop: Color,
    dropdownContent: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val glass = echoGlassPalette()
    val capsuleShape = RoundedCornerShape(14.dp)
    val panelShape = RoundedCornerShape(12.dp)
    val capsuleTint = glass.control
    val contentColor = readableTextColorFor(
        background = capsuleTint,
        fallbackSurface = readableBackdrop
    )
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .echoHazePanel(
                    hazeState = hazeState,
                    shape = capsuleShape,
                    tint = capsuleTint,
                    blurRadius = 16.dp
                )
                .background(capsuleTint, capsuleShape)
                .border(
                    BorderStroke(
                        if (expanded) 1.2.dp else 0.8.dp,
                        if (expanded) glass.outlineSelected else glass.outline
                    ),
                    capsuleShape
                )
                .echoShapeClick(capsuleShape) { onToggle() }
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.65f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                // v2.6.8 需求 2：筛选值（单选时即完整模型名）横向可滑动，右对齐排版保持不变
                ScrollableSingleLineText(
                    text = value,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    modifier = Modifier.weight(1f),
                    align = Alignment.CenterEnd
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = contentColor.copy(alpha = 0.75f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .echoHazePanel(
                        hazeState = hazeState,
                        shape = panelShape,
                        tint = glass.panelStrong,
                        blurRadius = 18.dp
                    ),
                shape = panelShape,
                color = Color.Transparent,
                contentColor = contentColor,
                border = BorderStroke(0.8.dp, glass.outline),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(6.dp)
                ) {
                    dropdownContent()
                }
            }
        }
    }
}

@Composable
private fun DropdownOptionRow(
    text: String,
    selected: Boolean,
    contentColor: Color,
    onClick: () -> Unit
) {
    val rowShape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
            .echoShapeClick(rowShape) { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // v2.6.8 需求 2：模型名等长选项改为横向可滑动，完整名称可左右拖动查看（不再省略号截断）
        ScrollableSingleLineText(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else contentColor,
            modifier = Modifier.weight(1f),
            align = Alignment.CenterStart
        )
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "已选择",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/**
 * 可左右滑动的单行文本（v2.6.8 需求 2）：
 * 模型名称在统计页多处因单行省略号而显示不全。改为横向可滑动——文本超出可用宽度时可左右拖动
 * 查看完整名称；未超出时按 [align] 在可用宽度（调用方以 weight 指定）内对齐，排版与原设计一致。
 */
@Composable
private fun ScrollableSingleLineText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    align: Alignment = Alignment.CenterStart,
    fontWeight: FontWeight? = null
) {
    Box(modifier = modifier, contentAlignment = align) {
        Text(
            text = text,
            style = if (fontWeight != null) style.copy(fontWeight = fontWeight) else style,
            color = color,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.horizontalScroll(rememberScrollState())
        )
    }
}

@Composable
private fun HeroSummaryCard(
    hazeState: dev.chrisbanes.haze.HazeState,
    summary: UsageSummary,
    period: StatsPeriod,
    selectedModel: String?,
    statusText: String,
    hasPreviousData: Boolean,
    tokensDelta: Float?,
    requestsDelta: Float?,
    readableBackdrop: Color
) {
    val glass = echoGlassPalette()
    val tint = glass.panelStrong
    val content = readableTextColorFor(
        background = tint,
        fallbackSurface = readableBackdrop
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .echoHazePanel(
                hazeState = hazeState,
                shape = EchoGlassPagePanelShape,
                tint = tint,
                blurRadius = 20.dp
            )
            .background(tint, EchoGlassPagePanelShape)
            .border(BorderStroke(1.dp, glass.outline), EchoGlassPagePanelShape)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 标题行：周期 + 筛选
            Text(
                text = "${period.label}统计${if (selectedModel != null) " · $selectedModel" else ""}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = content
            )

            // 总量主数字
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = formatNumber(summary.totalTokens),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 32.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold
                    ),
                    color = content
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Tokens 总消耗",
                    modifier = Modifier.padding(bottom = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.68f)
                )
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = content.copy(alpha = 0.60f)
            )

            // 环比对比行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasPreviousData) {
                    if (tokensDelta != null) {
                        DeltaChip(
                            label = "Token 消耗",
                            deltaPct = tokensDelta,
                            riseColor = MaterialTheme.colorScheme.tertiary,
                            fallColor = MaterialTheme.colorScheme.secondary,
                            textColor = content
                        )
                    }
                    if (requestsDelta != null) {
                        DeltaChip(
                            label = "调用量",
                            deltaPct = requestsDelta,
                            riseColor = MaterialTheme.colorScheme.tertiary,
                            fallColor = MaterialTheme.colorScheme.secondary,
                            textColor = content
                        )
                    }
                } else {
                    DeltaHintChip(text = "上一周期暂无调用，暂无环比对比", textColor = content)
                }
            }

            // 核心指标九宫格
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricPill(
                        icon = Icons.AutoMirrored.Filled.Send,
                        label = "总请求数",
                        value = "${summary.requestCount} 次",
                        contentColor = content,
                        iconTint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricPill(
                        icon = Icons.Default.CheckCircle,
                        label = "调用成功率",
                        value = formatPercent(summary.successRate),
                        contentColor = content,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricPill(
                        icon = Icons.Default.ErrorOutline,
                        label = "失败次数",
                        value = "${summary.failedCount} 次",
                        contentColor = content,
                        iconTint = if (summary.failedCount > 0) MaterialTheme.colorScheme.tertiary else content.copy(alpha = 0.55f),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricPill(
                        icon = Icons.Default.Download,
                        label = "输入 Token",
                        value = formatNumber(summary.inputTokens),
                        contentColor = content,
                        iconTint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricPill(
                        icon = Icons.Default.Upload,
                        label = "输出 Token",
                        value = formatNumber(summary.outputTokens),
                        contentColor = content,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricPill(
                        icon = Icons.Default.Psychology,
                        label = "思考 Token",
                        value = formatNumber(summary.thinkingTokens),
                        contentColor = content,
                        iconTint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricPill(
                        icon = Icons.Default.Cached,
                        label = "缓存命中率",
                        value = formatPercent(summary.cacheHitRate),
                        contentColor = content,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricPill(
                        icon = Icons.Default.Speed,
                        label = "平均响应",
                        value = formatMillis(summary.avgResponseTime),
                        contentColor = content,
                        iconTint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricPill(
                        icon = Icons.Default.Bolt,
                        label = "平均生成速度",
                        value = formatTps(summary.avgTps),
                        contentColor = content,
                        iconTint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DeltaChip(
    label: String,
    deltaPct: Float,
    riseColor: Color,
    fallColor: Color,
    textColor: Color
) {
    val isRising = deltaPct > 0.01f
    val isFalling = deltaPct < -0.01f
    val arrow = when {
        isRising -> "▲"
        isFalling -> "▼"
        else -> "—"
    }
    val tint = when {
        isRising -> riseColor
        isFalling -> fallColor
        else -> textColor.copy(alpha = 0.6f)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tint.copy(alpha = 0.14f))
            .border(BorderStroke(0.6.dp, tint.copy(alpha = 0.35f)), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 3.5.dp)
    ) {
        Text(
            text = arrow,
            style = MaterialTheme.typography.labelSmall,
            color = tint
        )
        Text(
            text = "$label ${formatDeltaPct(deltaPct)}",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.SansSerif),
            color = textColor.copy(alpha = 0.85f),
            maxLines = 1
        )
    }
}

@Composable
private fun DeltaHintChip(text: String, textColor: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = textColor.copy(alpha = 0.55f)
    )
}

@Composable
private fun TokenDonutCard(
    hazeState: dev.chrisbanes.haze.HazeState,
    summary: UsageSummary,
    modelSlices: List<DonutSlice>,
    readableBackdrop: Color
) {
    val glass = echoGlassPalette()
    val tint = glass.panelStrong
    val content = readableTextColorFor(
        background = tint,
        fallbackSurface = readableBackdrop
    )
    val chartColors = rememberEchoChartColors()
    val paletteColors = listOf(chartColors.primary, chartColors.secondary, chartColors.tertiary, chartColors.quaternary)
    var donutMode by remember { mutableIntStateOf(0) } // 0: Token 类型构成 1: 模型占比
    val typeSlices = remember(summary) { buildDonutSlices(summary) }
    val donutSlices = if (donutMode == 0) typeSlices else modelSlices
    val sweeps = remember(donutSlices) { donutSweepDegrees(donutSlices.map { it.value }) }
    val total = donutSlices.sumOf { it.value }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .echoHazePanel(
                hazeState = hazeState,
                shape = EchoGlassPagePanelShape,
                tint = tint,
                blurRadius = 18.dp
            )
            .background(tint, EchoGlassPagePanelShape)
            .border(BorderStroke(1.dp, glass.outline), EchoGlassPagePanelShape)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        imageVector = Icons.Default.PieChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Column {
                        Text(
                            text = if (donutMode == 0) "Token 构成" else "Token 构成 · 模型占比",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = content
                        )
                        Text(
                            text = if (donutMode == 0) "输入、输出、思考与其他 Token 的占比分布"
                            else "各模型消耗的 Token 占比（Top 4 + 其他）",
                            style = MaterialTheme.typography.bodySmall,
                            color = content.copy(alpha = 0.70f)
                        )
                    }
                }

                // 维度切换：Token 类型 / 模型占比（v2.6.7 需求 5c：纵向排列，标题不再跨行）
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Token 类型", "模型占比").forEachIndexed { idx, title ->
                        val isSelected = donutMode == idx
                        val chipShape = RoundedCornerShape(8.dp)
                        Box(
                            modifier = Modifier
                                .clip(chipShape)
                                .background(if (isSelected) glass.controlSelected else glass.control.copy(alpha = 0.6f))
                                .border(
                                    BorderStroke(
                                        if (isSelected) 1.dp else 0.6.dp,
                                        if (isSelected) glass.outlineSelected else glass.outline.copy(alpha = 0.6f)
                                    ),
                                    chipShape
                                )
                                .echoShapeClick(chipShape) { donutMode = idx }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else content.copy(alpha = 0.72f)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(150.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val stroke = 20.dp.toPx()
                        val inset = stroke / 2f + 2.dp.toPx()
                        val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
                        // 仅在无任何数据时绘制中性底环；有数据时切片连续无缝，杜绝灰色间隔
                        if (total <= 0) {
                            drawArc(
                                color = content.copy(alpha = 0.06f),
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = arcSize,
                                style = Stroke(width = stroke, cap = StrokeCap.Butt)
                            )
                        }
                        var startAngle = -90f
                        donutSlices.forEachIndexed { index, slice ->
                            val sweep = sweeps.getOrNull(index) ?: 0f
                            if (slice.value > 0 && sweep > 0f) {
                                drawArc(
                                    color = paletteColors[index % paletteColors.size],
                                    startAngle = startAngle,
                                    sweepAngle = sweep,
                                    useCenter = false,
                                    topLeft = Offset(inset, inset),
                                    size = arcSize,
                                    style = Stroke(width = stroke, cap = StrokeCap.Butt)
                                )
                            }
                            startAngle += sweep
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = formatNumber(total),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold
                            ),
                            color = content
                        )
                        Text(
                            text = "Tokens",
                            style = MaterialTheme.typography.labelSmall,
                            color = content.copy(alpha = 0.65f)
                        )
                    }
                }

                // 图例（值 + 占比）
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    donutSlices.forEachIndexed { index, slice ->
                        if (slice.value <= 0) return@forEachIndexed
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(paletteColors[index % paletteColors.size])
                            )
                            // v2.6.8 需求 2：模型占比模式的图例标签即模型名，改为横向可滑动查看完整名称，
                            // 数值列仍固定在行尾
                            ScrollableSingleLineText(
                                text = slice.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = content.copy(alpha = 0.72f),
                                modifier = Modifier.weight(1f),
                                align = Alignment.CenterStart
                            )
                            Text(
                                text = "${formatNumber(slice.value)} · ${formatPercent(if (total > 0) slice.value.toFloat() / total else 0f)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.SansSerif),
                                color = content,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    contentColor: Color,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    val glass = echoGlassPalette()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(glass.control.copy(alpha = 0.65f))
            .border(BorderStroke(0.8.dp, glass.outline.copy(alpha = 0.5f)), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChartCard(
    hazeState: dev.chrisbanes.haze.HazeState,
    title: String,
    subtitle: String,
    readableBackdrop: Color,
    legend: ((EchoChartPalette) -> List<Pair<String, Color>>)? = null,
    content: @Composable (Color) -> Unit
) {
    val glass = echoGlassPalette()
    val tint = glass.panelStrong
    val contentColor = readableTextColorFor(
        background = tint,
        fallbackSurface = readableBackdrop
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .echoHazePanel(
                hazeState = hazeState,
                shape = EchoGlassPagePanelShape,
                tint = tint,
                blurRadius = 18.dp
            )
            .background(tint, EchoGlassPagePanelShape)
            .border(BorderStroke(1.dp, glass.outline), EchoGlassPagePanelShape)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.70f)
                )
            }

            content(contentColor)

            val chartColors = rememberEchoChartColors()
            val legendItems = legend?.invoke(chartColors) ?: emptyList()
            if (legendItems.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    legendItems.forEach { (label, color) ->
                        LegendDot(label, color, contentColor.copy(alpha = 0.85f))
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(text: String, color: Color, textColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = textColor)
    }
}

/** 每模型 Token 消耗趋势：平滑折线（v2.7.0 取代混合柱状样式，参照每日 Token 趋势图设计） */
@Composable
private fun ModelTokenTrendChart(
    series: List<ModelTokenSeries>,
    buckets: List<Bucket>,
    maxToken: Int,
    labelColor: Color
) {
    val chartColors = rememberEchoChartColors()
    val palette = listOf(chartColors.primary, chartColors.secondary, chartColors.tertiary, chartColors.quaternary)
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val plotBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val otherColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.9f)

    Column {
        // 顶部图例：模型名 + 颜色点（与每日 Token 趋势图一致）
        if (series.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                series.forEachIndexed { index, s ->
                    val color = if (s.modelName == "其他") otherColor else palette[index % palette.size]
                    LegendDot(com.aiassistant.domain.model.ModelDisplayName.format(s.modelName), color, labelColor)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            AxisLabels(
                labels = listOf(formatNumber(maxToken), formatNumber(maxToken / 2), "0"),
                height = 180.dp,
                color = labelColor
            )
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(180.dp)
            ) {
                drawRoundRect(
                    color = plotBackground,
                    topLeft = Offset.Zero,
                    size = size,
                    cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                )
                // 横向虚线网格
                repeat(4) { line ->
                    val y = size.height * line / 3f
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 0.8.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                            floatArrayOf(8.dp.toPx(), 6.dp.toPx())
                        )
                    )
                }

                series.forEachIndexed { index, s ->
                    val color = if (s.modelName == "其他") otherColor else palette[index % palette.size]
                    drawSmoothTokenCurve(s.values, color, maxToken)
                }
            }
        }
        XAxisLabels(buckets = buckets)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSmoothTokenCurve(
    values: List<Int>,
    color: Color,
    maxToken: Int
) {
    if (values.isEmpty() || maxToken <= 0) return
    val allZero = values.all { it <= 0 }
    val points = values.mapIndexed { index, value ->
        val x = if (values.size == 1) size.width / 2f else size.width * index / (values.size - 1)
        // 全零序列贴底绘制基准线；否则按比例映射
        val fraction = if (allZero) 0f else (value.toFloat() / maxToken).coerceIn(0f, 1f)
        val y = size.height * (1f - fraction)
        Offset(x, y.coerceIn(1.dp.toPx(), size.height - 1.dp.toPx()))
    }

    val path = Path()
    points.forEachIndexed { index, point ->
        if (index == 0) {
            path.moveTo(point.x, point.y)
        } else {
            val prev = points[index - 1]
            val midX = (prev.x + point.x) / 2f
            path.cubicTo(midX, prev.y, midX, point.y, point.x, point.y)
        }
    }

    // 发光底层 + 主线条（与成功率曲线同一平滑手法）
    drawPath(
        path = path,
        color = color.copy(alpha = 0.20f),
        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
    )
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
    )
}

@Composable
private fun ModernTrendChart(
    buckets: List<Bucket>,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val successColor = rememberEchoChartColors().secondary // 淡青蓝（深色自动切 *Dark 变体）
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val plotBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val haloColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)

    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            AxisLabels(labels = listOf("100%", "50%", "0%"), height = 180.dp, color = labelColor)
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(180.dp)
            ) {
                drawRoundRect(
                    color = plotBackground,
                    topLeft = Offset.Zero,
                    size = size,
                    cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                )
                repeat(5) { line ->
                    val y = size.height * line / 4f
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }

                fun drawSmoothCurve(values: List<Float>, color: Color) {
                    if (values.isEmpty()) return
                    val path = Path()
                    val areaPath = Path()
                    val points = values.mapIndexed { index, value ->
                        val x = if (values.size == 1) size.width / 2f else size.width * index / (values.size - 1)
                        val y = size.height * (1f - value.coerceIn(0f, 1f))
                        Offset(x, y)
                    }

                    points.forEachIndexed { index, point ->
                        if (index == 0) {
                            path.moveTo(point.x, point.y)
                            areaPath.moveTo(point.x, size.height)
                            areaPath.lineTo(point.x, point.y)
                        } else {
                            val prev = points[index - 1]
                            val midX = (prev.x + point.x) / 2f
                            path.cubicTo(midX, prev.y, midX, point.y, point.x, point.y)
                            areaPath.cubicTo(midX, prev.y, midX, point.y, point.x, point.y)
                        }
                    }

                    if (points.isNotEmpty()) {
                        areaPath.lineTo(points.last().x, size.height)
                        areaPath.close()
                        drawPath(
                            path = areaPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(color.copy(alpha = 0.32f), color.copy(alpha = 0.08f), Color.Transparent),
                                startY = 0f,
                                endY = size.height
                            )
                        )
                    }

                    // 曲线发光底层
                    drawPath(
                        path = path,
                        color = color.copy(alpha = 0.22f),
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // 曲线主线条
                    drawPath(
                        path = path,
                        color = color,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // 三层高亮光晕数据点
                    points.forEach { point ->
                        drawCircle(color = color.copy(alpha = 0.28f), radius = 7.dp.toPx(), center = point)
                        drawCircle(color = haloColor, radius = 4.5.dp.toPx(), center = point)
                        drawCircle(color = color, radius = 2.8.dp.toPx(), center = point)
                    }
                }

                drawSmoothCurve(buckets.map { it.successRate }, successColor)
            }
        }
        XAxisLabels(buckets = buckets)
    }
}

/** 24 小时调用分布直方图：定位一天中最活跃的调用时段 */
@Composable
private fun HourActivityCard(
    hazeState: dev.chrisbanes.haze.HazeState,
    slices: List<HourSlice>,
    readableBackdrop: Color
) {
    val glass = echoGlassPalette()
    val tint = glass.panelStrong
    val content = readableTextColorFor(
        background = tint,
        fallbackSurface = readableBackdrop
    )
    val chartColors = rememberEchoChartColors()
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val plotBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val peak = remember(slices) { slices.maxByOrNull { it.requestCount }?.takeIf { it.requestCount > 0 } }
    val maxCount = remember(slices) { slices.maxOfOrNull { it.requestCount } ?: 0 }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .echoHazePanel(
                hazeState = hazeState,
                shape = EchoGlassPagePanelShape,
                tint = tint,
                blurRadius = 18.dp
            )
            .background(tint, EchoGlassPagePanelShape)
            .border(BorderStroke(1.dp, glass.outline), EchoGlassPagePanelShape)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "24 小时调用分布",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = content
                    )
                    Text(
                        text = if (peak != null) "最活跃时段 ${peak.hour} 时 · ${peak.requestCount} 次调用" else "统计周期内的按小时调用次数分布",
                        style = MaterialTheme.typography.bodySmall,
                        color = content.copy(alpha = 0.70f)
                    )
                }
            }

            Column {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.width(44.dp))
                    Canvas(
                        modifier = Modifier
                            .weight(1f)
                            .height(130.dp)
                    ) {
                        drawRoundRect(
                            color = plotBackground,
                            topLeft = Offset.Zero,
                            size = size,
                            cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                        )
                        repeat(3) { line ->
                            val y = size.height * line / 2f
                            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.8.dp.toPx())
                        }

                        if (slices.size == 24) {
                            val slot = size.width / 24f
                            val barWidth = (slot * 0.58f).coerceIn(2.dp.toPx(), 18.dp.toPx())
                            slices.forEach { slice ->
                                val left = slot * slice.hour + (slot - barWidth) / 2f
                                if (slice.requestCount <= 0 || maxCount <= 0) {
                                    drawRoundRect(
                                        color = chartColors.primary.copy(alpha = 0.14f),
                                        topLeft = Offset(left, size.height - 2.5.dp.toPx()),
                                        size = Size(barWidth, 2.5.dp.toPx()),
                                        cornerRadius = CornerRadius(1.25.dp.toPx(), 1.25.dp.toPx())
                                    )
                                } else {
                                    val barHeight = (size.height * slice.requestCount / maxCount.toFloat())
                                        .coerceIn(3.dp.toPx(), size.height - 4.dp.toPx())
                                    val alpha = 0.35f + 0.65f * (slice.requestCount.toFloat() / maxCount)
                                    drawRoundRect(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                chartColors.primary.copy(alpha = alpha),
                                                chartColors.primary.copy(alpha = alpha * 0.6f)
                                            ),
                                            startY = size.height - barHeight,
                                            endY = size.height
                                        ),
                                        topLeft = Offset(left, size.height - barHeight),
                                        size = Size(barWidth, barHeight),
                                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                                    )
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 44.dp, top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("0时", "6时", "12时", "18时", "23时").forEach { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        }
    }
}

/** 供应商 Token 占比横条（Top 6） */
@Composable
private fun ProviderShareCard(
    hazeState: dev.chrisbanes.haze.HazeState,
    shares: List<ProviderShare>,
    readableBackdrop: Color
) {
    val glass = echoGlassPalette()
    val tint = glass.panelStrong
    val content = readableTextColorFor(
        background = tint,
        fallbackSurface = readableBackdrop
    )
    val chartColors = rememberEchoChartColors()
    val palette = listOf(chartColors.primary, chartColors.secondary, chartColors.tertiary, chartColors.quaternary)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .echoHazePanel(
                hazeState = hazeState,
                shape = EchoGlassPagePanelShape,
                tint = tint,
                blurRadius = 18.dp
            )
            .background(tint, EchoGlassPagePanelShape)
            .border(BorderStroke(1.dp, glass.outline), EchoGlassPagePanelShape)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Dns,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "供应商消耗占比",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = content
                    )
                    Text(
                        text = "按供应商统计 Token 消耗分布",
                        style = MaterialTheme.typography.bodySmall,
                        color = content.copy(alpha = 0.70f)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                shares.forEachIndexed { index, share ->
                    val color = palette[index % palette.size]
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                                Text(
                                    text = share.provider,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = content,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "${formatNumber(share.totalTokens)} · ${formatPercent(share.share)} · ${share.requestCount}次",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.SansSerif),
                                color = content.copy(alpha = 0.75f),
                                maxLines = 1
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(glass.control.copy(alpha = 0.8f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(share.share.coerceIn(0.02f, 1f))
                                    .fillMaxHeight()
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(color.copy(alpha = 0.9f), color.copy(alpha = 0.6f))
                                        )
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 失败原因 Top 归纳 */
@Composable
private fun FailureAnalysisCard(
    hazeState: dev.chrisbanes.haze.HazeState,
    failures: List<FailureSlice>,
    readableBackdrop: Color
) {
    val glass = echoGlassPalette()
    val tint = glass.panelStrong
    val content = readableTextColorFor(
        background = tint,
        fallbackSurface = readableBackdrop
    )
    val chartColors = rememberEchoChartColors()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .echoHazePanel(
                hazeState = hazeState,
                shape = EchoGlassPagePanelShape,
                tint = tint,
                blurRadius = 18.dp
            )
            .background(tint, EchoGlassPagePanelShape)
            .border(BorderStroke(1.dp, glass.outline), EchoGlassPagePanelShape)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = chartColors.tertiary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "失败原因归纳",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = content
                    )
                    Text(
                        text = "统计周期内调用失败的高频原因",
                        style = MaterialTheme.typography.bodySmall,
                        color = content.copy(alpha = 0.70f)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                failures.forEach { failure ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(glass.control.copy(alpha = 0.55f))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(chartColors.tertiary.copy(alpha = 0.16f))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${failure.count} 次",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = chartColors.tertiary
                            )
                        }
                        Text(
                            text = failure.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = content.copy(alpha = 0.85f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/** 请求健康时间线：热力矩形看板（v2.7.0 需求 4e），点选方格查看局部时段的请求数、成功率与 Token 消耗 */
@Composable
private fun HealthTimelineCard(
    hazeState: dev.chrisbanes.haze.HazeState,
    cells: List<HealthCell>,
    period: StatsPeriod,
    readableBackdrop: Color
) {
    val glass = echoGlassPalette()
    val tint = glass.panelStrong
    val content = readableTextColorFor(
        background = tint,
        fallbackSurface = readableBackdrop
    )
    val chartColors = rememberEchoChartColors()

    // 健康状态配色（GitHub 贡献图式，深浅主题通用）
    val healthyColor = Color(0xFF22C55E)
    val goodColor = Color(0xFFA3E635)
    val warnColor = Color(0xFFFBBF24)
    val badColor = Color(0xFFEF4444)
    val emptyColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)

    var heatMode by remember { mutableIntStateOf(0) } // 0: 健康状态 1: Token 热度
    var selectedCellIndex by remember(cells) { mutableIntStateOf(-1) }
    val selectedBorderColor = MaterialTheme.colorScheme.primary

    val columns = 14
    val rowCount = (cells.size + columns - 1) / columns
    val cellGap = 3.dp
    val maxTokens = remember(cells) { cells.maxOfOrNull { it.totalTokens } ?: 0 }

    fun cellColor(cell: HealthCell): Color {
        if (cell.requestCount <= 0) return emptyColor
        return if (heatMode == 1) {
            val t = if (maxTokens > 0) (cell.totalTokens.toFloat() / maxTokens).coerceIn(0f, 1f) else 0f
            chartColors.primary.copy(alpha = 0.15f + 0.85f * t.pow(0.6f))
        } else {
            val rate = 1f - cell.failedCount.toFloat() / cell.requestCount
            when {
                rate >= 0.99f -> healthyColor
                rate >= 0.9f -> goodColor
                rate >= 0.75f -> warnColor
                else -> badColor
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .echoHazePanel(
                hazeState = hazeState,
                shape = EchoGlassPagePanelShape,
                tint = tint,
                blurRadius = 18.dp
            )
            .background(tint, EchoGlassPagePanelShape)
            .border(BorderStroke(1.dp, glass.outline), EchoGlassPagePanelShape)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        imageVector = Icons.Default.GridView,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Column {
                        Text(
                            text = "请求健康时间线",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = content
                        )
                        Text(
                            text = "${period.label} · 点选方格查看局部时段明细",
                            style = MaterialTheme.typography.bodySmall,
                            color = content.copy(alpha = 0.70f)
                        )
                    }
                }

                // v2.7.7 需求 9：两个模式切换按钮改为竖向排列——标题与副标题不再被挤压跨行
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("健康状态", "Token 热度").forEachIndexed { idx, title ->
                        val isSelected = heatMode == idx
                        val chipShape = RoundedCornerShape(8.dp)
                        Box(
                            modifier = Modifier
                                .clip(chipShape)
                                .background(if (isSelected) glass.controlSelected else glass.control.copy(alpha = 0.6f))
                                .border(
                                    BorderStroke(
                                        if (isSelected) 1.dp else 0.6.dp,
                                        if (isSelected) glass.outlineSelected else glass.outline.copy(alpha = 0.6f)
                                    ),
                                    chipShape
                                )
                                .echoShapeClick(chipShape) { heatMode = idx }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else content.copy(alpha = 0.72f)
                            )
                        }
                    }
                }
            }

            if (cells.isEmpty()) {
                Text(
                    text = "当前筛选条件下暂无统计记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.6f)
                )
            } else {
                // 热力方格画布（v2.7.7 需求 7：纵向优先填充——时间顺序先自上而下、再自左向右，
                // 第一格的下一格在它正下方，最后一格的上一格在它正上方）
                androidx.compose.foundation.layout.BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val cellSize = ((maxWidth - cellGap * (columns - 1)) / columns).coerceAtLeast(6.dp)
                    val gridHeight = cellSize * rowCount + cellGap * (rowCount - 1)
                    val cellSizePx = with(androidx.compose.ui.platform.LocalDensity.current) { cellSize.toPx() }
                    val gapPx = with(androidx.compose.ui.platform.LocalDensity.current) { cellGap.toPx() }
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(gridHeight)
                                .pointerInput(cells, heatMode) {
                                    detectTapGestures { offset ->
                                        val col = (offset.x / (cellSizePx + gapPx)).toInt()
                                        val row = (offset.y / (cellSizePx + gapPx)).toInt()
                                        // 纵向优先填充：index = 列 × 每行列数 + 行
                                        val index = if (col in 0 until columns && row in 0 until rowCount) {
                                            col * rowCount + row
                                        } else -1
                                        selectedCellIndex = if (index in cells.indices) {
                                            if (selectedCellIndex == index) -1 else index
                                        } else -1
                                    }
                                }
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                cells.forEachIndexed { index, cell ->
                                    val col = index / rowCount
                                    val row = index % rowCount
                                    val left = col * (cellSizePx + gapPx)
                                    val top = row * (cellSizePx + gapPx)
                                    val color = cellColor(cell)
                                    drawRoundRect(
                                        color = color,
                                        topLeft = Offset(left, top),
                                        size = Size(cellSizePx, cellSizePx),
                                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                                    )
                                    if (index == selectedCellIndex) {
                                        drawRoundRect(
                                            color = selectedBorderColor,
                                            topLeft = Offset(left, top),
                                            size = Size(cellSizePx, cellSizePx),
                                            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                                            style = Stroke(width = 2.dp.toPx())
                                        )
                                    }
                                }
                            }
                        }

                        // v2.7.7 需求 8：底部横轴时间标注——在 0、4、9、13 列中心标注该列起始时间点
                        val axisTextMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
                        val axisSpan = (cells.lastOrNull()?.endTs ?: 0L) - (cells.firstOrNull()?.startTs ?: 0L)
                        val axisFormat = SimpleDateFormat(if (axisSpan > 36L * 60 * 60 * 1000) "MM-dd" else "HH:mm", Locale.getDefault())
                        val axisColumns = listOf(0, 4, 9, 13)
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(16.dp)
                        ) {
                            val axisStyle = TextStyle(
                                fontSize = 9.sp,
                                color = content.copy(alpha = 0.55f)
                            )
                            axisColumns.forEach { col ->
                                val cell = cells.getOrNull(col * rowCount) ?: return@forEach
                                val label = axisFormat.format(java.util.Date(cell.startTs))
                                val measured = axisTextMeasurer.measure(label, axisStyle)
                                val colCenter = col * (cellSizePx + gapPx) + cellSizePx / 2f
                                val left = (colCenter - measured.size.width / 2f)
                                    .coerceIn(0f, (size.width - measured.size.width).coerceAtLeast(0f))
                                drawText(
                                    textLayoutResult = measured,
                                    topLeft = Offset(left, 0f)
                                )
                            }
                        }
                    }
                }

                // 图例
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (heatMode == 1) {
                        LegendDot("无请求", emptyColor, content.copy(alpha = 0.7f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "消耗少",
                                style = MaterialTheme.typography.labelSmall,
                                color = content.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(width = 42.dp, height = 9.dp)
                                    .clip(RoundedCornerShape(4.5.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                chartColors.primary.copy(alpha = 0.15f),
                                                chartColors.primary
                                            )
                                        )
                                    )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "消耗多",
                                style = MaterialTheme.typography.labelSmall,
                                color = content.copy(alpha = 0.7f)
                            )
                        }
                    } else {
                        LegendDot("成功率 ≥99%", healthyColor, content.copy(alpha = 0.7f))
                        LegendDot("≥90%", goodColor, content.copy(alpha = 0.7f))
                        LegendDot("≥75%", warnColor, content.copy(alpha = 0.7f))
                        LegendDot("<75%", badColor, content.copy(alpha = 0.7f))
                        LegendDot("无请求", emptyColor, content.copy(alpha = 0.7f))
                    }
                }

                // 选中方格的局部时段明细
                androidx.compose.animation.AnimatedVisibility(visible = selectedCellIndex in cells.indices) {
                    val cell = cells.getOrNull(selectedCellIndex) ?: return@AnimatedVisibility
                    val rate = if (cell.requestCount > 0) {
                        formatPercent(1f - cell.failedCount.toFloat() / cell.requestCount)
                    } else "—"
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = formatHealthCellRange(cell),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = content
                            )
                            if (cell.requestCount <= 0) {
                                Text(
                                    text = "该时段暂无请求",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = content.copy(alpha = 0.7f)
                                )
                            } else {
                                Text(
                                    text = "请求数 ${cell.requestCount} 次 · 成功率 $rate · Token 消耗 ${formatNumber(cell.totalTokens)}" +
                                        if (cell.failedCount > 0) " · 失败 ${cell.failedCount} 次" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = content.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AxisLabels(labels: List<String>, height: androidx.compose.ui.unit.Dp, color: Color) {    Column(
        modifier = Modifier
            .width(44.dp)
            .height(height)
            .padding(end = 6.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.End
    ) {
        labels.forEach { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun XAxisLabels(buckets: List<Bucket>) {
    val labels = remember(buckets) { bucketAxisLabels(buckets) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 44.dp, top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        labels.forEach { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModernModelStatsTable(
    hazeState: dev.chrisbanes.haze.HazeState,
    rows: List<ModelRow>,
    readableBackdrop: Color
) {
    val glass = echoGlassPalette()
    val tint = glass.panelStrong
    val content = readableTextColorFor(
        background = tint,
        fallbackSurface = readableBackdrop
    )
    var sortMode by remember { mutableIntStateOf(0) } // 0: Tokens, 1: 请求数, 2: 成功率, 3: 平均耗时, 4: 生成速度

    val sortedRows = remember(rows, sortMode) {
        when (sortMode) {
            0 -> rows.sortedByDescending { it.totalTokens }
            1 -> rows.sortedByDescending { it.requestCount }
            2 -> rows.sortedByDescending { it.successRate }
            3 -> rows.sortedBy { if (it.avgResponseTime <= 0) Long.MAX_VALUE else it.avgResponseTime }
            4 -> rows.sortedByDescending { it.tps }
            else -> rows
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .echoHazePanel(
                hazeState = hazeState,
                shape = EchoGlassPagePanelShape,
                tint = tint,
                blurRadius = 18.dp
            )
            .background(tint, EchoGlassPagePanelShape)
            .border(BorderStroke(1.dp, glass.outline), EchoGlassPagePanelShape)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 表头与排序切换栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "模型统计明细",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = content
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${rows.size}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // v2.7.7 需求 10：排序方式改为下拉列表选择——原先 5 枚选项胶囊与标题同行，
                // 窄屏下被挤压显示不全
                var sortMenuExpanded by remember { mutableStateOf(false) }
                val sortOptions = listOf("Tokens", "请求数", "成功率", "耗时", "平均速度")
                Box {
                    val triggerShape = RoundedCornerShape(8.dp)
                    Box(
                        modifier = Modifier
                            .clip(triggerShape)
                            .background(glass.control.copy(alpha = 0.6f))
                            .border(BorderStroke(0.6.dp, glass.outline.copy(alpha = 0.6f)), triggerShape)
                            .echoShapeClick(triggerShape) { sortMenuExpanded = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "排序：${sortOptions.getOrElse(sortMode) { "Tokens" }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = content.copy(alpha = 0.85f)
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "选择排序方式",
                                tint = content.copy(alpha = 0.72f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    EchoGlassDropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        sortOptions.forEachIndexed { idx, title ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (sortMode == idx) MaterialTheme.colorScheme.primary else content
                                    )
                                },
                                leadingIcon = {
                                    if (sortMode == idx) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                },
                                onClick = {
                                    sortMode = idx
                                    sortMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // 极简精致模型列表卡片
            val chartColors = rememberEchoChartColors() // C-7：深浅双套
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sortedRows.forEach { row ->
                    val rowShape = RoundedCornerShape(14.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(rowShape)
                            .background(glass.control.copy(alpha = 0.52f))
                            .border(BorderStroke(0.8.dp, glass.outline.copy(alpha = 0.45f)), rowShape)
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // 第一行：模型名称 + 供应商标签 + 总 Token
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // v2.6.8 需求 2：模型名 + 供应商标签整体横向可滑动，长模型名可拖动查看完整名称，
                                // 右侧 Token 总量保持贴右对齐
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = com.aiassistant.domain.model.ModelDisplayName.format(row.modelName),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.SansSerif,
                                                fontWeight = FontWeight.Bold),
                                            color = content,
                                            maxLines = 1
                                        )
                                        if (row.provider.isNotBlank() && row.provider != "unknown") {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(glass.control.copy(alpha = 0.8f))
                                                    .border(BorderStroke(0.5.dp, glass.outline.copy(alpha = 0.5f)), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = row.provider,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = content.copy(alpha = 0.65f),
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }

                                Text(
                                    text = "${formatNumber(row.totalTokens)} Tokens",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = if (sortMode == 0) {
                                        Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                                            .border(
                                                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                                                RoundedCornerShape(6.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    } else {
                                        Modifier
                                    }
                                )
                            }

                            // 第二行：比例横条（输入/输出/思考可视化分布）
                            if (row.totalTokens > 0) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(glass.control.copy(alpha = 0.8f))
                                ) {
                                    val inputRatio = (row.inputTokens.toFloat() / row.totalTokens).coerceIn(0f, 1f)
                                    val outputRatio = (row.outputTokens.toFloat() / row.totalTokens).coerceIn(0f, 1f)
                                    val thinkingRatio = (row.thinkingTokens.toFloat() / row.totalTokens).coerceIn(0f, 1f)

                                    if (inputRatio > 0f) {
                                        Box(
                                            modifier = Modifier
                                                .weight(inputRatio)
                                                .fillMaxHeight()
                                                .background(chartColors.primary)
                                        )
                                    }
                                    if (outputRatio > 0f) {
                                        Box(
                                            modifier = Modifier
                                                .weight(outputRatio)
                                                .fillMaxHeight()
                                                .background(chartColors.secondary)
                                        )
                                    }
                                    if (thinkingRatio > 0f) {
                                        Box(
                                            modifier = Modifier
                                                .weight(thinkingRatio)
                                                .fillMaxHeight()
                                                .background(chartColors.tertiary)
                                        )
                                    }
                                }
                            }

                            // 第三行：多维度紧凑数据标签
                            // v2.7.7 需求 11：改为可换行 FlowRow——此前单行 Row 在出现「失败 N」
                            // 「缓存」「思考」等追加标签时整体超宽，排在其后的 TPS 标签被挤出
                            // 可视区裁掉，表现为"有失败数据时显示不出 TPS"
                            androidx.compose.foundation.layout.FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                MiniStatsChip(
                                    icon = Icons.AutoMirrored.Filled.Send,
                                    label = "${row.requestCount}次请求",
                                    tint = content.copy(alpha = 0.78f),
                                    isHighlighted = sortMode == 1
                                )
                                MiniStatsChip(
                                    icon = Icons.Default.CheckCircle,
                                    label = "成功率 ${formatPercent(row.successRate)}",
                                    tint = chartColors.secondary,
                                    isHighlighted = sortMode == 2
                                )
                                if (row.failedCount > 0) {
                                    MiniStatsChip(
                                        icon = Icons.Default.ErrorOutline,
                                        label = "失败 ${row.failedCount}",
                                        tint = chartColors.tertiary
                                    )
                                }
                                if (row.avgResponseTime > 0) {
                                    MiniStatsChip(
                                        icon = Icons.Default.Timer,
                                        label = formatMillis(row.avgResponseTime),
                                        tint = content.copy(alpha = 0.78f),
                                        isHighlighted = sortMode == 3
                                    )
                                }
                                if (row.tps > 0f) {
                                    MiniStatsChip(
                                        icon = Icons.Default.Bolt,
                                        label = "平均 ${formatTps(row.tps)}",
                                        tint = chartColors.tertiary,
                                        isHighlighted = sortMode == 4
                                    )
                                }
                                if (row.cachedTokens > 0) {
                                    MiniStatsChip(
                                        icon = Icons.Default.Cached,
                                        label = "缓存 ${formatPercent(row.cacheHitRate)}",
                                        tint = chartColors.secondary
                                    )
                                }
                                if (row.thinkingTokens > 0) {
                                    MiniStatsChip(
                                        icon = Icons.Default.Psychology,
                                        label = "思考 ${formatNumber(row.thinkingTokens)}",
                                        tint = chartColors.tertiary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStatsChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    isHighlighted: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isHighlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
            )
            .border(
                BorderStroke(
                    if (isHighlighted) 1.dp else 0.dp,
                    if (isHighlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f) else Color.Transparent
                ),
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isHighlighted) MaterialTheme.colorScheme.primary else tint,
            modifier = Modifier.size(11.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.SansSerif),
            color = if (isHighlighted) MaterialTheme.colorScheme.primary else tint,
            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

@Composable
private fun EmptyCard(
    hazeState: dev.chrisbanes.haze.HazeState,
    text: String,
    readableBackdrop: Color
) {
    val tint = echoGlassPalette().panelStrong
    val content = readableTextColorFor(
        background = tint,
        fallbackSurface = readableBackdrop
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .echoHazePanel(
                hazeState = hazeState,
                shape = EchoGlassPagePanelShape,
                tint = tint,
                blurRadius = 16.dp
            )
            .background(tint, EchoGlassPagePanelShape),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = content.copy(alpha = 0.70f), textAlign = TextAlign.Center)
    }
}

private suspend fun readUsageRows(
    context: Context,
    startTime: Long,
    endTime: Long
): StatsReadResult = withContext(Dispatchers.IO) {
    val dbFile = context.getDatabasePath("ai_assistant_database")
    if (!dbFile.exists()) {
        return@withContext StatsReadResult(emptyList(), "暂无统计数据库")
    }

    try {
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            if (!hasUsableStatsTable(db)) {
                return@withContext StatsReadResult(emptyList(), "统计表不可读，之后会重新记录")
            }
            StatsReadResult(queryUsageRows(db, startTime, endTime), "读取完成")
        }
    } catch (_: Throwable) {
        StatsReadResult(emptyList(), "统计数据不可读，已自动忽略旧统计")
    }
}

private fun hasUsableStatsTable(db: SQLiteDatabase): Boolean {
    val required = setOf(
        "id", "apiConfigId", "provider", "modelName", "inputTokens", "outputTokens",
        "thinkingTokens", "totalTokens", "cachedTokens", "responseTime", "success",
        "errorMessage", "timestamp"
    )
    val columns = mutableSetOf<String>()
    return try {
        db.rawQuery("PRAGMA table_info(`api_usage_stats`)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                columns += cursor.getString(nameIndex)
            }
        }
        columns.containsAll(required)
    } catch (_: Throwable) {
        false
    }
}

private fun queryUsageRows(db: SQLiteDatabase, startTime: Long, endTime: Long): List<UsageRow> {
    val rows = mutableListOf<UsageRow>()
    db.rawQuery(
        """
        SELECT provider, modelName, inputTokens, outputTokens, thinkingTokens,
               totalTokens, cachedTokens, responseTime, success, timestamp, errorMessage
        FROM api_usage_stats
        WHERE timestamp >= ? AND timestamp <= ?
        ORDER BY timestamp ASC
        """.trimIndent(),
        arrayOf(startTime.toString(), endTime.toString())
    ).use { cursor ->
        while (cursor.moveToNext()) {
            val inputTokens = cursor.getInt(2).coerceAtLeast(0)
            val outputTokens = cursor.getInt(3).coerceAtLeast(0)
            val thinkingTokens = cursor.getInt(4).coerceAtLeast(0)
            val recordedTotalTokens = cursor.getInt(5).coerceAtLeast(0)
            val knownTokens = inputTokens + outputTokens + thinkingTokens
            val totalTokens = maxOf(recordedTotalTokens, knownTokens)
            val otherTokens = (totalTokens - knownTokens).coerceAtLeast(0)
            rows += UsageRow(
                provider = cursor.getString(0) ?: "unknown",
                modelName = cursor.getString(1) ?: "unknown",
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                thinkingTokens = thinkingTokens,
                otherTokens = otherTokens,
                totalTokens = totalTokens,
                cachedTokens = cursor.getInt(6).coerceIn(0, inputTokens),
                responseTime = cursor.getLong(7).coerceAtLeast(0L),
                success = cursor.getInt(8) == 1,
                timestamp = cursor.getLong(9),
                errorMessage = cursor.getString(10)
            )
        }
    }
    return rows
}

internal fun List<UsageRow>.toSummary(): UsageSummary {
    val input = sumOf { it.inputTokens }
    val output = sumOf { it.outputTokens }
    val thinking = sumOf { it.thinkingTokens }
    val cached = sumOf { it.cachedTokens }
    val successCount = count { it.success }
    val timedRows = filter { it.responseTime > 0 }
    // v2.6.4 TPS：生成速度 = 输出 Token 总量 / 有耗时记录请求的总耗时（秒）
    val timedOutput = timedRows.sumOf { it.outputTokens }
    val timedSeconds = timedRows.sumOf { it.responseTime } / 1000.0
    return UsageSummary(
        totalTokens = sumOf { it.totalTokens },
        inputTokens = input,
        outputTokens = output,
        thinkingTokens = thinking,
        cachedTokens = cached,
        requestCount = size,
        failedCount = count { !it.success },
        cacheHitRate = if (input > 0) cached.toFloat() / input else 0f,
        successRate = if (isNotEmpty()) successCount.toFloat() / size else 0f,
        avgResponseTime = if (timedRows.isNotEmpty()) timedRows.map { it.responseTime }.average().toLong() else 0L,
        avgTps = if (timedSeconds > 0) timedOutput / timedSeconds.toFloat() else 0f
    )
}

internal fun List<UsageRow>.toModelRows(): List<ModelRow> {
    return groupBy { it.provider to it.modelName }
        .map { (key, rows) ->
            val input = rows.sumOf { it.inputTokens }
            val cached = rows.sumOf { it.cachedTokens }
            val timedRows = rows.filter { it.responseTime > 0 }
            val timedOutput = timedRows.sumOf { it.outputTokens }
            val timedSeconds = timedRows.sumOf { it.responseTime } / 1000.0
            ModelRow(
                provider = key.first,
                modelName = key.second,
                inputTokens = input,
                outputTokens = rows.sumOf { it.outputTokens },
                thinkingTokens = rows.sumOf { it.thinkingTokens },
                cachedTokens = cached,
                totalTokens = rows.sumOf { it.totalTokens },
                requestCount = rows.size,
                failedCount = rows.count { !it.success },
                avgResponseTime = if (rows.isNotEmpty()) rows.map { it.responseTime }.average().toLong() else 0L,
                cacheHitRate = if (input > 0) cached.toFloat() / input else 0f,
                successRate = if (rows.isNotEmpty()) rows.count { it.success }.toFloat() / rows.size else 0f,
                tps = if (timedSeconds > 0) timedOutput / timedSeconds.toFloat() else 0f
            )
        }
        .sortedByDescending { it.totalTokens }
}

internal fun buildBuckets(rows: List<UsageRow>, period: StatsPeriod, endTime: Long): List<Bucket> {
    val bucketSize = period.durationMillis / period.bucketCount
    val startTime = endTime - period.durationMillis
    val formatter = SimpleDateFormat(period.labelPattern, Locale.getDefault())
    return List(period.bucketCount) { index ->
        val bucketStart = startTime + bucketSize * index
        val bucketEnd = if (index == period.bucketCount - 1) endTime else bucketStart + bucketSize
        val bucketRows = rows.filter { it.timestamp >= bucketStart && it.timestamp < bucketEnd }
        val input = bucketRows.sumOf { it.inputTokens }
        val cached = bucketRows.sumOf { it.cachedTokens }
        Bucket(
            label = formatter.format(Date(bucketStart)),
            inputTokens = input,
            outputTokens = bucketRows.sumOf { it.outputTokens },
            thinkingTokens = bucketRows.sumOf { it.thinkingTokens },
            otherTokens = bucketRows.sumOf { it.otherTokens },
            totalTokens = bucketRows.sumOf { it.totalTokens },
            requestCount = bucketRows.size,
            cacheHitRate = if (input > 0) cached.toFloat() / input else 0f,
            successRate = if (bucketRows.isNotEmpty()) bucketRows.count { it.success }.toFloat() / bucketRows.size else 0f
        )
    }
}

/** 按 0-23 小时聚合调用次数与 Token 消耗（使用设备本地时区） */
internal fun List<UsageRow>.toHourSlices(): List<HourSlice> {
    val counts = IntArray(24)
    val tokens = IntArray(24)
    val calendar = Calendar.getInstance()
    forEach { row ->
        calendar.timeInMillis = row.timestamp
        val hour = calendar.get(Calendar.HOUR_OF_DAY).coerceIn(0, 23)
        counts[hour] += 1
        tokens[hour] += row.totalTokens
    }
    return (0 until 24).map { hour -> HourSlice(hour = hour, requestCount = counts[hour], totalTokens = tokens[hour]) }
}

/** 按供应商聚合 Token 占比（降序，含占比 0~1） */
internal fun List<UsageRow>.toProviderShares(): List<ProviderShare> {
    val total = sumOf { it.totalTokens }
    if (total <= 0) return emptyList()
    return groupBy { it.provider.ifBlank { "unknown" } }
        .map { (provider, rows) ->
            ProviderShare(
                provider = provider,
                totalTokens = rows.sumOf { it.totalTokens },
                requestCount = rows.size,
                share = rows.sumOf { it.totalTokens }.toFloat() / total
            )
        }
        .sortedByDescending { it.totalTokens }
        .take(6)
}

/** 失败原因 Top 归纳：按首行文案分组（截断 48 字），取出现频次前 4 */
internal fun List<UsageRow>.toFailureSlices(): List<FailureSlice> {
    return filter { !it.success }
        .map { it.errorMessage?.trim().orEmpty() }
        .filter { it.isNotEmpty() }
        .groupBy { it.lineSequence().firstOrNull().orEmpty().take(48) }
        .map { (reason, messages) -> FailureSlice(reason = reason, count = messages.size) }
        .sortedWith(compareByDescending<FailureSlice> { it.count }.thenBy { it.reason })
        .take(4)
}

/** 环比增减百分比（%）；上一周期无数据时返回 null */
internal fun computeDeltaPct(current: Long, previous: Long): Float? {
    if (previous <= 0L) return null
    return ((current - previous).toFloat() / previous) * 100f
}

/** 环比百分比格式化：带符号、保留 1 位小数，例如 +12.3% / -8.0% / +0.0% */
internal fun formatDeltaPct(deltaPct: Float): String {
    return String.format(Locale.US, "%+.1f%%", deltaPct)
}

/** Token 构成切片（不含配色，配色由 UI 按序号映射） */
internal data class DonutSlice(val label: String, val value: Int)

internal fun buildDonutSlices(summary: UsageSummary): List<DonutSlice> {
    val other = (summary.totalTokens - summary.inputTokens - summary.outputTokens - summary.thinkingTokens)
        .coerceAtLeast(0)
    return listOf(
        DonutSlice("输入", summary.inputTokens),
        DonutSlice("输出", summary.outputTokens),
        DonutSlice("思考", summary.thinkingTokens),
        DonutSlice("其他", other)
    )
}

/** Token 构成 · 模型占比视图：Top N 模型 + 其余合并为「其他」 */
internal fun List<UsageRow>.toModelDonutSlices(topN: Int = 4): List<DonutSlice> {
    if (isEmpty() || sumOf { it.totalTokens } <= 0) return emptyList()
    val byModel = groupBy { it.modelName.ifBlank { "unknown" } }
        .map { (name, rows) -> name to rows.sumOf { it.totalTokens } }
        .sortedByDescending { it.second }
    if (byModel.size <= topN) {
        return byModel.map { DonutSlice(it.first, it.second) }
    }
    val top = byModel.take(topN)
    val restTokens = byModel.drop(topN).sumOf { it.second }
    return top.map { DonutSlice(it.first, it.second) } + DonutSlice("其他", restTokens)
}

/** 每模型 Token 趋势序列：Top N 模型 + 其余合并为「其他」，values 与分桶一一对应 */
internal data class ModelTokenSeries(
    val modelName: String,
    val values: List<Int>,
    val totalTokens: Int
)

internal fun buildModelTokenSeries(
    rows: List<UsageRow>,
    period: StatsPeriod,
    endTime: Long,
    topN: Int = 4
): List<ModelTokenSeries> {
    val bucketSize = period.durationMillis / period.bucketCount
    val startTime = endTime - period.durationMillis

    fun bucketIndexOf(timestamp: Long): Int {
        if (timestamp < startTime) return 0
        val idx = ((timestamp - startTime) / bucketSize).toInt()
        return idx.coerceIn(0, period.bucketCount - 1)
    }

    val byModel = rows.groupBy { it.modelName.ifBlank { "unknown" } }
        .mapValues { (_, modelRows) ->
            val values = IntArray(period.bucketCount)
            modelRows.forEach { values[bucketIndexOf(it.timestamp)] += it.totalTokens }
            values
        }
        .toList()
        .sortedByDescending { (_, values) -> values.sum() }

    val topEntries = byModel.take(topN)
    val rest = byModel.drop(topN)
    val series = mutableListOf<ModelTokenSeries>()
    series.addAll(topEntries.map { (name, values) ->
        ModelTokenSeries(modelName = name, values = values.toList(), totalTokens = values.sum())
    })
    if (rest.isNotEmpty()) {
        val restValues = IntArray(period.bucketCount)
        rest.forEach { (_, values) ->
            values.forEachIndexed { idx, v -> restValues[idx] += v }
        }
        series.add(ModelTokenSeries(modelName = "其他", values = restValues.toList(), totalTokens = restValues.sum()))
    }
    return series
}

/** 请求健康时间线单元格：一段连续时间的请求健康与消耗聚合 */
internal data class HealthCell(
    val startTs: Long,
    val endTs: Long,
    val requestCount: Int,
    val failedCount: Int,
    val totalTokens: Int
)

/** 按周期定制的热力格数量聚合请求结果（成功/失败/Token 消耗） */
internal fun buildHealthCells(rows: List<UsageRow>, period: StatsPeriod, endTime: Long): List<HealthCell> {
    val cellCount = period.heatmapCells
    val cellSize = period.durationMillis / cellCount
    val startTime = endTime - period.durationMillis
    val cells = Array(cellCount) { idx ->
        val cellStart = startTime + cellSize * idx
        val cellEnd = if (idx == cellCount - 1) endTime else cellStart + cellSize
        HealthCell(startTs = cellStart, endTs = cellEnd, requestCount = 0, failedCount = 0, totalTokens = 0)
    }
    rows.forEach { row ->
        if (row.timestamp < startTime || row.timestamp > endTime) return@forEach
        val idx = (((row.timestamp - startTime) / cellSize).toInt()).coerceIn(0, cellCount - 1)
        val old = cells[idx]
        cells[idx] = old.copy(
            requestCount = old.requestCount + 1,
            failedCount = old.failedCount + if (row.success) 0 else 1,
            totalTokens = old.totalTokens + row.totalTokens
        )
    }
    return cells.toList()
}

internal fun formatHealthCellRange(cell: HealthCell): String {
    val formatter = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return "${formatter.format(Date(cell.startTs))} ~ ${formatter.format(Date(cell.endTs))}"
}

/**
 * 环形图各切片扫过角度：切片连续无缝（v2.7.0 修复切片间灰色间隔），总扫角恒为 360°。
 * 全零输入返回空列表；零值切片扫角为 0（绘制层跳过）。
 */
internal fun donutSweepDegrees(values: List<Int>): List<Float> {
    val total = values.sumOf { it.coerceAtLeast(0) }
    if (total <= 0) return emptyList()
    return values.map { value ->
        if (value <= 0) 0f else 360f * value / total
    }
}

private fun niceAxisMax(value: Int): Int {
    if (value <= 0) return 1
    val magnitude = 10.0.pow((value.toString().length - 1).toDouble()).toInt()
    val normalized = value.toFloat() / magnitude
    val nice = when {
        normalized <= 1f -> 1
        normalized <= 2f -> 2
        normalized <= 5f -> 5
        else -> 10
    }
    return nice * magnitude
}

internal fun formatNumber(value: Int): String {
    return when {
        value >= 1_000_000 -> String.format(Locale.getDefault(), "%.1fM", value / 1_000_000.0)
        value >= 1_000 -> String.format(Locale.getDefault(), "%.1fK", value / 1_000.0)
        else -> value.toString()
    }
}

/** 响应耗时格式化：毫秒 / 秒 / 分钟自适应，0 或缺失显示 — */
internal fun formatMillis(ms: Long): String {
    return when {
        ms <= 0L -> "—"
        ms < 1_000L -> "${ms}ms"
        ms < 60_000L -> String.format(Locale.US, "%.1fs", ms / 1_000.0)
        else -> String.format(Locale.US, "%.1fmin", ms / 60_000.0)
    }
}

/** TPS（tokens per second）格式化：无数据显示 — */
internal fun formatTps(tps: Float): String {
    return if (tps <= 0f) "—" else String.format(Locale.US, "%.1f t/s", tps)
}

internal fun formatPercent(value: Float): String {
    return "${(value.coerceIn(0f, 1f) * 100).toInt()}%"
}

private fun bucketAxisLabels(buckets: List<Bucket>): List<String> {
    if (buckets.isEmpty()) return emptyList()
    val n = buckets.size
    val indices = listOf(0, (n - 1) / 4, (n - 1) / 2, (3 * (n - 1)) / 4, n - 1).distinct()
    return indices.map { buckets[it].label }
}

internal enum class StatsPeriod(
    val label: String,
    val durationMillis: Long,
    val bucketCount: Int,
    val labelPattern: String,
    /**
     * 请求健康时间线热力格数量：全部时间范围统一为 14 列 × 6 行 = 84 格（v2.6.8 需求 3），
     * 看板形状与大小在任意时间范围下完全一致，不再随时长改变行列数。
     */
    val heatmapCells: Int
) {
    Hour("1小时", 60L * 60L * 1000L, 12, "HH:mm", 84),
    Hour4("4小时", 4L * 60L * 60L * 1000L, 16, "HH:mm", 84),
    Hour8("8小时", 8L * 60L * 60L * 1000L, 24, "HH:mm", 84),
    Day("1天", 24L * 60L * 60L * 1000L, 24, "HH:mm", 84),
    Day3("3天", 3L * 24L * 60L * 60L * 1000L, 36, "MM-dd", 84),
    Week("7天", 7L * 24L * 60L * 60L * 1000L, 7, "MM-dd", 84),
    Month("30天", 30L * 24L * 60L * 60L * 1000L, 30, "MM-dd", 84),
    Quarter("90天", 90L * 24L * 60L * 60L * 1000L, 30, "MM-dd", 84)
}

private data class StatsReadResult(
    val rows: List<UsageRow>,
    val message: String
)

internal data class UsageRow(
    val provider: String,
    val modelName: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val thinkingTokens: Int,
    val otherTokens: Int,
    val totalTokens: Int,
    val cachedTokens: Int,
    val responseTime: Long,
    val success: Boolean,
    val timestamp: Long,
    val errorMessage: String? = null
)

internal data class UsageSummary(
    val totalTokens: Int,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val thinkingTokens: Int = 0,
    val cachedTokens: Int = 0,
    val requestCount: Int,
    val failedCount: Int = 0,
    val cacheHitRate: Float,
    val successRate: Float,
    val avgResponseTime: Long = 0L,
    val avgTps: Float = 0f
)

internal data class Bucket(
    val label: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val thinkingTokens: Int,
    val otherTokens: Int,
    val totalTokens: Int,
    val requestCount: Int = 0,
    val cacheHitRate: Float,
    val successRate: Float
)

internal data class ModelRow(
    val provider: String,
    val modelName: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val thinkingTokens: Int,
    val cachedTokens: Int,
    val totalTokens: Int,
    val requestCount: Int,
    val failedCount: Int = 0,
    val avgResponseTime: Long,
    val cacheHitRate: Float,
    val successRate: Float,
    val tps: Float = 0f
)

internal data class HourSlice(
    val hour: Int,
    val requestCount: Int,
    val totalTokens: Int
)

internal data class ProviderShare(
    val provider: String,
    val totalTokens: Int,
    val requestCount: Int,
    val share: Float
)

internal data class FailureSlice(
    val reason: String,
    val count: Int
)
