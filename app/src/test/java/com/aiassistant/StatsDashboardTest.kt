package com.aiassistant

import com.aiassistant.ui.screens.stats.StatsPeriod
import com.aiassistant.ui.screens.stats.UsageRow
import com.aiassistant.ui.screens.stats.UsageSummary
import com.aiassistant.ui.screens.stats.buildBuckets
import com.aiassistant.ui.screens.stats.buildDonutSlices
import com.aiassistant.ui.screens.stats.buildHealthCells
import com.aiassistant.ui.screens.stats.buildModelTokenSeries
import com.aiassistant.ui.screens.stats.computeDeltaPct
import com.aiassistant.ui.screens.stats.donutSweepDegrees
import com.aiassistant.ui.screens.stats.formatDeltaPct
import com.aiassistant.ui.screens.stats.formatMillis
import com.aiassistant.ui.screens.stats.formatNumber
import com.aiassistant.ui.screens.stats.formatTps
import com.aiassistant.ui.screens.stats.toFailureSlices
import com.aiassistant.ui.screens.stats.toHourSlices
import com.aiassistant.ui.screens.stats.toModelDonutSlices
import com.aiassistant.ui.screens.stats.toModelRows
import com.aiassistant.ui.screens.stats.toProviderShares
import com.aiassistant.ui.screens.stats.toSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * v2.6.1 使用统计看板：纯统计逻辑回归
 * （概览聚合、时段分布、供应商占比、失败归纳、环比、环形图角度与格式化）
 */
class StatsDashboardTest {

    private fun row(
        timestamp: Long,
        provider: String = "openai",
        modelName: String = "gpt-test",
        inputTokens: Int = 100,
        outputTokens: Int = 200,
        thinkingTokens: Int = 0,
        cachedTokens: Int = 0,
        responseTime: Long = 500L,
        success: Boolean = true,
        errorMessage: String? = null
    ) = UsageRow(
        provider = provider,
        modelName = modelName,
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        thinkingTokens = thinkingTokens,
        otherTokens = 0,
        totalTokens = inputTokens + outputTokens + thinkingTokens,
        cachedTokens = cachedTokens,
        responseTime = responseTime,
        success = success,
        timestamp = timestamp,
        errorMessage = errorMessage
    )

    // ==================== 概览聚合 ====================

    @Test
    fun testToSummary_extendedMetrics() {
        val rows = listOf(
            row(timestamp = 1L, inputTokens = 100, outputTokens = 200, cachedTokens = 50, responseTime = 400L, success = true),
            row(timestamp = 2L, inputTokens = 100, outputTokens = 200, cachedTokens = 0, responseTime = 800L, success = false, errorMessage = "HTTP 429"),
            row(timestamp = 3L, inputTokens = 100, outputTokens = 200, cachedTokens = 50, responseTime = 600L, success = true)
        )

        val summary = rows.toSummary()

        assertEquals("总请求数应为 3", 3, summary.requestCount)
        assertEquals("失败次数应为 1", 1, summary.failedCount)
        assertEquals("成功率应为 2/3", 2f / 3f, summary.successRate, 0.0001f)
        assertEquals("总 Token 应为 900", 900, summary.totalTokens)
        assertEquals("缓存命中应为 100/300", 100f / 300f, summary.cacheHitRate, 0.0001f)
        assertEquals("平均响应应为 600ms", 600L, summary.avgResponseTime)
        assertEquals("TPS 应为 600 输出 / 1.8s", 600f / 1.8f, summary.avgTps, 0.01f)
    }

    @Test
    fun testToSummary_emptyRows() {
        val summary = emptyList<UsageRow>().toSummary()
        assertEquals(0, summary.requestCount)
        assertEquals(0, summary.failedCount)
        assertEquals(0f, summary.successRate, 0.0001f)
        assertEquals(0L, summary.avgResponseTime)
        assertEquals(0f, summary.cacheHitRate, 0.0001f)
        assertEquals("无耗时报数据时 TPS 应为 0（显示 —）", 0f, summary.avgTps, 0.0001f)
    }

    // ==================== 生成速度 TPS（v2.6.4 需求 4） ====================

    @Test
    fun testFormatTps() {
        assertEquals("12.3 t/s", formatTps(12.34f))
        assertEquals("—", formatTps(0f))
        assertEquals("—", formatTps(-1f))
    }

    @Test
    fun testToModelRows_tpsPerModel() {
        val rows = listOf(
            row(timestamp = 1L, modelName = "fast", outputTokens = 300, responseTime = 1000L),
            row(timestamp = 2L, modelName = "fast", outputTokens = 300, responseTime = 1000L),
            row(timestamp = 3L, modelName = "slow", outputTokens = 100, responseTime = 4000L),
            row(timestamp = 4L, modelName = "slow", outputTokens = 100, responseTime = 0L)
        )
        val modelRows = rows.toModelRows()
        val fast = modelRows.first { it.modelName == "fast" }
        val slow = modelRows.first { it.modelName == "slow" }
        assertEquals("fast 模型 TPS 应为 600/2s = 300", 300f, fast.tps, 0.01f)
        assertEquals(
            "slow 模型 TPS 应为 100/4s = 25（无耗时的请求不参与分子与分母）", 25f, slow.tps, 0.01f
        )
    }

    // ==================== 时间范围与热力格铺满（v2.6.4 需求 2/3） ====================

    @Test
    fun testStatsPeriod_newRangesAvailable() {
        val labels = StatsPeriod.entries.map { it.label }
        assertTrue("应提供 4 小时范围", labels.contains("4小时"))
        assertTrue("应提供 8 小时范围", labels.contains("8小时"))
        assertTrue("应提供 3 天范围", labels.contains("3天"))
        assertEquals("4 小时周期时长", 4L * 60 * 60 * 1000, StatsPeriod.entries.first { it.label == "4小时" }.durationMillis)
    }

    @Test
    fun testStatsPeriod_heatmapCellsFillGrid() {
        val columns = 14
        val rows = 6
        // v2.6.8 需求 3：所有时间范围的热力看板统一 14 × 6，形状与大小恒定不随时长变化
        StatsPeriod.entries.forEach { period ->
            assertEquals(
                "${period.label} 的请求健康时间线必须为 14 × 6 = 84 格",
                columns * rows,
                period.heatmapCells
            )
            assertEquals(
                "${period.label} 的热力看板行数必须固定为 6",
                rows,
                period.heatmapCells / columns
            )
        }
    }

    // ==================== 24 小时分布 ====================

    @Test
    fun testToHourSlices_bucketsRequestsByLocalHour() {
        fun hourTimestamp(hour: Int, dayOffset: Int = 0): Long {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, dayOffset)
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

        val slices = listOf(
            row(timestamp = hourTimestamp(8), inputTokens = 10, outputTokens = 20),
            row(timestamp = hourTimestamp(8, dayOffset = -1), inputTokens = 1, outputTokens = 2),
            row(timestamp = hourTimestamp(23), inputTokens = 5, outputTokens = 5)
        ).toHourSlices()

        assertEquals("必须固定返回 24 个切片", 24, slices.size)
        assertEquals("8 时应聚合 2 次调用", 2, slices[8].requestCount)
        assertEquals("8 时应聚合 33 Token", 33, slices[8].totalTokens)
        assertEquals("23 时应聚合 1 次调用", 1, slices[23].requestCount)
        assertEquals("0 时应无调用", 0, slices[0].requestCount)
        assertEquals("总调用次数应守恒", 3, slices.sumOf { it.requestCount })
    }

    // ==================== 供应商占比 ====================

    @Test
    fun testToProviderShares_sortedAndNormalized() {
        val shares = listOf(
            row(timestamp = 1L, provider = "openai", inputTokens = 300, outputTokens = 0),
            row(timestamp = 2L, provider = "anthropic", inputTokens = 100, outputTokens = 0),
            row(timestamp = 3L, provider = "openai", inputTokens = 100, outputTokens = 0)
        ).toProviderShares()

        assertEquals("去重后应只有两个供应商", 2, shares.size)
        assertEquals("占比应按消耗降序", "openai", shares[0].provider)
        assertEquals("openai 占比应为 80%", 0.8f, shares[0].share, 0.0001f)
        assertEquals("anthropic 占比应为 20%", 0.2f, shares[1].share, 0.0001f)
        assertEquals("openai 调用次数应为 2", 2, shares[0].requestCount)
    }

    @Test
    fun testToProviderShares_blankProviderAndEmptyInput() {
        val blank = listOf(row(timestamp = 1L, provider = "", inputTokens = 10)).toProviderShares()
        assertEquals("空白供应商应归入 unknown", "unknown", blank[0].provider)
        assertTrue("全部消耗在同一供应商时占比应为 1", blank[0].share > 0.99f)
        assertTrue("零消耗时应返回空列表", emptyList<UsageRow>().toProviderShares().isEmpty())
    }

    // ==================== 失败原因归纳 ====================

    @Test
    fun testToFailureSlices_groupsByFirstLineAndCounts() {
        val failures = listOf(
            row(timestamp = 1L, success = false, errorMessage = "HTTP 429: rate limited\n请稍后重试"),
            row(timestamp = 2L, success = false, errorMessage = "HTTP 429: rate limited\n其他提示"),
            row(timestamp = 3L, success = false, errorMessage = "连接超时"),
            row(timestamp = 4L, success = true, errorMessage = null)
        ).toFailureSlices()

        assertEquals("仅统计失败记录，成功不计入", 2, failures.size)
        assertEquals("高频原因应排第一", "HTTP 429: rate limited", failures[0].reason)
        assertEquals("HTTP 429 应计数 2 次", 2, failures[0].count)
        assertEquals("首行后的内容不参与分组", "连接超时", failures[1].reason)
    }

    @Test
    fun testToFailureSlices_emptyAndReasonCappedAtFour() {
        assertTrue("无失败记录时应返回空", emptyList<UsageRow>().toFailureSlices().isEmpty())
        val many = (1..6).map { idx ->
            row(timestamp = idx.toLong(), success = false, errorMessage = "错误 $idx")
        }.toFailureSlices()
        assertEquals("最多返回 4 条归纳", 4, many.size)
        assertTrue("全部等频时按文案稳定排序", many[0].count == 1)
    }

    // ==================== 环比 ====================

    @Test
    fun testComputeDeltaPct() {
        assertEquals("+25% 场景", 25f, computeDeltaPct(125L, 100L)!!, 0.0001f)
        assertEquals("-50% 场景", -50f, computeDeltaPct(50L, 100L)!!, 0.0001f)
        assertEquals("持平为 0", 0f, computeDeltaPct(100L, 100L)!!, 0.0001f)
        assertNull("上一周期无数据时返回 null", computeDeltaPct(100L, 0L))
    }

    @Test
    fun testFormatDeltaPct() {
        assertEquals("+12.3%", formatDeltaPct(12.34f))
        assertEquals("-8.0%", formatDeltaPct(-8.0f))
        assertEquals("+0.0%", formatDeltaPct(0f))
    }

    // ==================== 环形图 ====================

    @Test
    fun testDonutSweepDegrees_contiguousWithoutGaps() {
        val sweeps = donutSweepDegrees(listOf(50, 25, 25, 0))
        assertEquals("4 个输入对应 4 个扫角", 4, sweeps.size)
        assertEquals("零值切片扫角为 0", 0f, sweeps[3], 0.0001f)
        assertEquals("50% 切片应占半个圆", 180f, sweeps[0], 0.01f)
        assertEquals("切片连续无缝：总扫角应恰好 360°", 360f, sweeps.sum(), 0.01f)
    }

    @Test
    fun testDonutSweepDegrees_emptyAndSingle() {
        assertTrue("全零输入返回空", donutSweepDegrees(listOf(0, 0, 0, 0)).isEmpty())
        val single = donutSweepDegrees(listOf(0, 100, 0, 0))
        assertEquals("单一非零切片应铺满整圆（无灰色间隔）", 360f, single[1], 0.0001f)
    }

    @Test
    fun testBuildDonutSlices_otherIsResidual() {
        val summary = UsageSummary(
            totalTokens = 1000,
            inputTokens = 400,
            outputTokens = 300,
            thinkingTokens = 200,
            requestCount = 5,
            cacheHitRate = 0f,
            successRate = 1f
        )
        val slices = buildDonutSlices(summary)
        assertEquals("其他 Token 应为残差 100", 100, slices[3].value)
        assertEquals("四个切片总和应等于总量", 1000, slices.sumOf { it.value })
    }

    // ==================== Token 构成 · 模型占比视图（v2.7.0 需求 4b） ====================

    @Test
    fun testToModelDonutSlices_topNPlusOthers() {
        val rows = listOf(
            row(timestamp = 1L, modelName = "glm-a", inputTokens = 500, outputTokens = 0),
            row(timestamp = 2L, modelName = "glm-b", inputTokens = 300, outputTokens = 0),
            row(timestamp = 3L, modelName = "glm-c", inputTokens = 150, outputTokens = 0),
            row(timestamp = 4L, modelName = "glm-d", inputTokens = 30, outputTokens = 0),
            row(timestamp = 5L, modelName = "glm-e", inputTokens = 20, outputTokens = 0)
        )
        val slices = rows.toModelDonutSlices(topN = 4)
        assertEquals("Top4 + 其他", 5, slices.size)
        assertEquals("切片应按消耗降序", "glm-a", slices[0].label)
        assertEquals("尾部应合并为其他", "其他", slices[4].label)
        assertEquals("其他应聚合未进 Top4 的 glm-e（20 Token）", 20, slices[4].value)
        assertEquals("切片总和应守恒", 1000, slices.sumOf { it.value })
    }

    @Test
    fun testToModelDonutSlices_emptyAndWithinTopN() {
        assertTrue("空数据返回空", emptyList<UsageRow>().toModelDonutSlices().isEmpty())
        val two = listOf(
            row(timestamp = 1L, modelName = "m1", inputTokens = 10),
            row(timestamp = 2L, modelName = "m2", inputTokens = 5)
        ).toModelDonutSlices(topN = 4)
        assertEquals("不足 TopN 时不生成其他", 2, two.size)
    }

    // ==================== 每模型 Token 趋势序列（v2.7.0 需求 4c） ====================

    @Test
    fun testBuildModelTokenSeries_bucketsAndOthers() {
        val endTime = 1_700_000_000_000L
        val period = StatsPeriod.Day
        val bucketSize = period.durationMillis / period.bucketCount
        val startTime = endTime - period.durationMillis

        val rows = listOf(
            row(timestamp = startTime + 100L, modelName = "glm-a", inputTokens = 100, outputTokens = 0),
            row(timestamp = startTime + bucketSize * 3 + 10L, modelName = "glm-a", inputTokens = 50, outputTokens = 0),
            row(timestamp = startTime + bucketSize + 10L, modelName = "glm-b", inputTokens = 80, outputTokens = 0),
            row(timestamp = startTime + bucketSize * 2 + 10L, modelName = "glm-c", inputTokens = 10, outputTokens = 0),
            row(timestamp = startTime + bucketSize * 2 + 20L, modelName = "glm-d", inputTokens = 10, outputTokens = 0),
            row(timestamp = startTime + bucketSize * 2 + 30L, modelName = "glm-e", inputTokens = 10, outputTokens = 0)
        )

        val series = buildModelTokenSeries(rows, period, endTime, topN = 2)
        assertEquals("Top2 + 其他", 3, series.size)
        assertEquals("按消耗降序：glm-a 第一", "glm-a", series[0].modelName)
        assertEquals("glm-a 总量 150", 150, series[0].totalTokens)
        assertEquals("glm-a 第 0 桶 100", 100, series[0].values[0])
        assertEquals("glm-a 第 3 桶 50", 50, series[0].values[3])
        assertEquals("glm-b 第 1 桶 80", 80, series[1].values[1])
        assertEquals("其他合并为一条序列", "其他", series[2].modelName)
        assertEquals("其他第 2 桶聚合 c/d/e 共 30", 30, series[2].values[2])
        assertEquals("序列总量守恒", 260, series.sumOf { it.totalTokens })
        assertEquals("每条序列必须与分桶等长", period.bucketCount, series[0].values.size)
    }

    // ==================== 请求健康时间线（v2.7.0 需求 4e） ====================

    @Test
    fun testBuildHealthCells_countsFailuresAndTokens() {
        val endTime = 1_700_000_000_000L
        val period = StatsPeriod.Day // 48 格 × 30 分钟
        val cellSize = period.durationMillis / period.heatmapCells
        val startTime = endTime - period.durationMillis

        val rows = listOf(
            row(timestamp = startTime + 100L, inputTokens = 100, outputTokens = 50, success = true),
            row(timestamp = startTime + 200L, inputTokens = 10, outputTokens = 0, success = false, errorMessage = "HTTP 429"),
            row(timestamp = startTime + cellSize + 100L, inputTokens = 7, outputTokens = 3, success = true)
        )

        val cells = buildHealthCells(rows, period, endTime)
        assertEquals("必须返回固定数量的格子", period.heatmapCells, cells.size)
        assertEquals("第 0 格应聚合 2 次请求", 2, cells[0].requestCount)
        assertEquals("第 0 格应含 1 次失败", 1, cells[0].failedCount)
        assertEquals("第 0 格 Token 应为 160", 160, cells[0].totalTokens)
        assertEquals("第 1 格应聚合 1 次请求", 1, cells[1].requestCount)
        assertEquals("第 2 格应无请求", 0, cells[2].requestCount)
        assertEquals("总请求数应守恒", 3, cells.sumOf { it.requestCount })
        assertEquals("总 Token 应守恒", 170, cells.sumOf { it.totalTokens })
    }

    @Test
    fun testBuildHealthCells_outOfWindowIgnored() {
        val endTime = 1_700_000_000_000L
        val period = StatsPeriod.Hour
        val cells = buildHealthCells(
            listOf(row(timestamp = endTime - period.durationMillis - 5_000L, inputTokens = 100)),
            period,
            endTime
        )
        assertEquals("窗口外记录不得计入任何格子", 0, cells.sumOf { it.requestCount })
    }

    // ==================== 分桶与格式化 ====================

    @Test
    fun testBuildBuckets_includesRequestCount() {
        val endTime = System.currentTimeMillis()
        val rows = listOf(
            row(timestamp = endTime - 1000L),
            row(timestamp = endTime - 2000L)
        )
        val buckets = buildBuckets(rows, StatsPeriod.Hour, endTime)
        assertEquals("1 小时周期应有 12 个分桶", 12, buckets.size)
        assertEquals("最近一桶应包含 2 次调用", 2, buckets.last().requestCount)
    }

    @Test
    fun testFormatMillis() {
        assertEquals("850ms", formatMillis(850L))
        assertEquals("1.5s", formatMillis(1500L))
        assertEquals("2.0min", formatMillis(120_000L))
        assertEquals("缺失耗时显示 —", "—", formatMillis(0L))
    }

    @Test
    fun testFormatNumber() {
        assertEquals("999", formatNumber(999))
        assertEquals("1.5K", formatNumber(1_500))
        assertEquals("2.0M", formatNumber(2_000_000))
    }
}
