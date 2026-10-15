package com.aiassistant.ui.screens.stats

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

internal data class UsageQuality(
    val exactTokens: Long,
    val reported: Int,
    val estimated: Int,
    val legacy: Int,
    val interrupted: Int,
    val cancelled: Int,
    val medianMs: Long,
    val p95Ms: Long,
    val firstTokenMs: Long,
    val firstTokenSamples: Int,
    val kinds: Map<String, Int>
)

internal fun List<UsageRow>.quality(): UsageQuality {
    val successfulTimes = filter { it.success && it.responseTime > 0 }.map { it.responseTime }.sorted()
    val firstTokens = filter { it.firstTokenTimeMs > 0 }.map { it.firstTokenTimeMs }
    fun percentile(p: Double): Long = if (successfulTimes.isEmpty()) 0 else
        successfulTimes[(ceil(successfulTimes.size * p).toInt() - 1).coerceIn(successfulTimes.indices)]
    return UsageQuality(
        exactTokens = sumOf { it.totalTokens.toLong() },
        reported = count { it.tokenSource == "reported" },
        estimated = count { it.tokenSource == "estimated" || it.tokenSource == "mixed" },
        legacy = count { it.tokenSource == "legacy" },
        interrupted = count { it.completionStatus == "interrupted" },
        cancelled = count { it.completionStatus == "cancelled" },
        medianMs = percentile(0.5), p95Ms = percentile(0.95),
        firstTokenMs = if (firstTokens.isEmpty()) 0 else firstTokens.average().toLong(),
        firstTokenSamples = firstTokens.size,
        kinds = groupingBy { it.requestKind }.eachCount()
    )
}

@Composable
internal fun UsageQualityCard(rows: List<UsageRow>) {
    val data = remember(rows) { rows.quality() }
    var expanded by rememberSaveable { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("统计口径与性能", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起明细" else "查看明细") }
            }
            Text("已记录 ${data.exactTokens} Token · 服务端上报 ${data.reported} 次 · 含估算 ${data.estimated} 次",
                style = MaterialTheme.typography.bodyMedium)
            Text("完成耗时 P50 ${formatMillis(data.medianMs)} / P95 ${formatMillis(data.p95Ms)} · 首段等待 ${formatMillis(data.firstTokenMs)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (expanded) {
                HorizontalDivider()
                val names = mapOf("chat" to "正式回复", "reply_direction" to "方向选择", "summary" to "上下文摘要",
                    "timeline" to "时间线提取", "memory" to "设定提取", "title" to "自动命名", "translation" to "思考翻译", "auxiliary" to "素材分析等辅助调用", "legacy" to "历史未分类")
                data.kinds.forEach { (kind, count) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(names[kind] ?: kind, style = MaterialTheme.typography.bodySmall)
                        Text("$count 次", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text("中断 ${data.interrupted} 次 · 取消 ${data.cancelled} 次 · 首段等待样本 ${data.firstTokenSamples} 次",
                    style = MaterialTheme.typography.bodySmall)
                Text("每次 API 尝试单独计数，重试会增加次数；缓存属于输入，思考属于生成，不重复叠加。耗时分位数只使用成功且有计时的请求。端到端吞吐包含等待时间，不等同于模型解码速度。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("历史 ${data.legacy} 次记录未区分上报与估算；缺失数据不反推。Token 为本机观测，不是服务商账单。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
