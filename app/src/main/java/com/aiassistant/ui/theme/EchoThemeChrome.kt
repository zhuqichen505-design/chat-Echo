package com.aiassistant.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 主题结构分隔线：将各主题的版式语言落到列表、Markdown 分割线与卡片分区。
 */
@Composable
fun EchoThemedDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outlineVariant
) {
    val style = LocalEchoThemeStyle.current
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    when (style.dividerStyle) {
        EchoDividerStyle.Default -> {
            Canvas(modifier = modifier.fillMaxWidth().height(1.dp)) {
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, color.copy(alpha = (color.alpha * .75f).coerceIn(.12f, 1f)), color.copy(alpha = (color.alpha * .75f).coerceIn(.12f, 1f)), Color.Transparent)
                    ),
                    start = Offset(0f, size.height * .5f),
                    end = Offset(size.width, size.height * .5f),
                    strokeWidth = 0.85.dp.toPx()
                )
            }
        }
        EchoDividerStyle.HorizonFade -> {
            Canvas(modifier = modifier.fillMaxWidth().height(6.dp)) {
                val cy = size.height * .5f
                val inset = 12.dp.toPx().coerceAtMost(size.width * .15f)
                val end = size.width - inset
                if (end > inset) {
                    drawLine(
                        brush = Brush.horizontalGradient(
                            listOf(Color.Transparent, primary.copy(alpha = .35f), primary.copy(alpha = .35f), Color.Transparent),
                            startX = inset,
                            endX = end
                        ),
                        start = Offset(inset, cy),
                        end = Offset(end, cy),
                        strokeWidth = 0.8.dp.toPx()
                    )
                    drawCircle(
                        color = secondary.copy(alpha = .62f),
                        radius = 1.6.dp.toPx(),
                        center = Offset(inset + (end - inset) * .72f, cy)
                    )
                }
            }
        }
        EchoDividerStyle.VersePause -> {
            Canvas(modifier = modifier.fillMaxWidth().height(7.dp)) {
                val cy = size.height * .5f
                val cx = size.width * .5f
                val gap = 12.dp.toPx()
                if (cx > gap) {
                    drawLine(color.copy(alpha = (color.alpha * .75f).coerceIn(.15f, 1f)), Offset(0f, cy), Offset(cx - gap, cy), .65.dp.toPx())
                    drawLine(color.copy(alpha = (color.alpha * .75f).coerceIn(.15f, 1f)), Offset(cx + gap, cy), Offset(size.width, cy), .65.dp.toPx())
                } else {
                    drawLine(color, Offset(0f, cy), Offset(size.width, cy), .65.dp.toPx())
                }
                val dotR = 1.15.dp.toPx()
                val dotGap = 5.dp.toPx()
                drawCircle(primary.copy(alpha = .62f), dotR, Offset(cx - dotGap, cy))
                drawCircle(primary.copy(alpha = .80f), dotR, Offset(cx, cy))
                drawCircle(primary.copy(alpha = .62f), dotR, Offset(cx + dotGap, cy))
            }
        }
        EchoDividerStyle.RadarPulse -> {
            Canvas(modifier = modifier.fillMaxWidth().height(8.dp)) {
                val cy = size.height * .5f
                val cx = size.width * .5f
                val pulseHalf = 7.dp.toPx()
                val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()), 0f)
                if (cx > pulseHalf + 4.dp.toPx()) {
                    drawLine(color, Offset(0f, cy), Offset(cx - pulseHalf, cy), .8.dp.toPx(), pathEffect = dash)
                    drawLine(color, Offset(cx + pulseHalf, cy), Offset(size.width, cy), .8.dp.toPx(), pathEffect = dash)
                }
                val p = Path().apply {
                    moveTo(cx - pulseHalf, cy)
                    lineTo(cx - 3.dp.toPx(), cy)
                    lineTo(cx - 1.2.dp.toPx(), cy - 3.dp.toPx())
                    lineTo(cx + 1.2.dp.toPx(), cy + 3.dp.toPx())
                    lineTo(cx + 3.dp.toPx(), cy)
                    lineTo(cx + pulseHalf, cy)
                }
                drawPath(p, color = primary.copy(alpha = .78f), style = Stroke(width = 1.dp.toPx()))
            }
        }
        EchoDividerStyle.CeramicWave -> {
            Canvas(modifier = modifier.fillMaxWidth().height(8.dp)) {
                val cy = size.height * .5f
                val cx = size.width * .5f
                val gap = 7.dp.toPx()
                if (cx > gap) {
                    drawLine(color, Offset(0f, cy), Offset(cx - gap, cy), .7.dp.toPx())
                    drawLine(color, Offset(cx + gap, cy), Offset(size.width, cy), .7.dp.toPx())
                }
                drawCircle(primary.copy(alpha = .62f), 2.6.dp.toPx(), Offset(cx, cy), style = Stroke(width = .85.dp.toPx()))
                drawCircle(secondary.copy(alpha = .70f), 1.dp.toPx(), Offset(cx, cy))
            }
        }
        EchoDividerStyle.GiltDoubleRule -> {
            Canvas(modifier = modifier.fillMaxWidth().height(7.dp)) {
                val topY = 1.6.dp.toPx()
                val bottomY = 5.4.dp.toPx()
                val cx = size.width * .5f
                val cy = (topY + bottomY) * .5f
                drawLine(secondary.copy(alpha = .58f), Offset(0f, topY), Offset(size.width, topY), .9.dp.toPx())
                drawLine(primary.copy(alpha = .48f), Offset(0f, bottomY), Offset(size.width, bottomY), .65.dp.toPx())
                val d = 2.4.dp.toPx()
                val knot = Path().apply {
                    moveTo(cx, cy - d); lineTo(cx + d, cy); lineTo(cx, cy + d); lineTo(cx - d, cy); close()
                }
                drawPath(knot, color = secondary.copy(alpha = .75f))
            }
        }
        EchoDividerStyle.DoubleRule -> {
            Canvas(modifier = modifier.fillMaxWidth().height(5.dp)) {
                val topY = 1.dp.toPx()
                val bottomY = 4.dp.toPx()
                drawLine(color, Offset(0f, topY), Offset(size.width, topY), 1.4.dp.toPx())
                drawLine(
                    color.copy(alpha = (color.alpha * 0.8f).coerceIn(0.15f, 1f)),
                    Offset(0f, bottomY),
                    Offset(size.width, bottomY),
                    0.65.dp.toPx()
                )
            }
        }
        EchoDividerStyle.DashedTick -> {
            Canvas(modifier = modifier.fillMaxWidth().height(7.dp)) {
                val cy = size.height * 0.5f
                val cx = size.width * 0.5f
                val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()), 0f)
                val gap = 9.dp.toPx()
                if (cx > gap) {
                    drawLine(color, Offset(0f, cy), Offset(cx - gap, cy), 0.8.dp.toPx(), pathEffect = dash)
                    drawLine(color, Offset(cx + gap, cy), Offset(size.width, cy), 0.8.dp.toPx(), pathEffect = dash)
                } else {
                    drawLine(color, Offset(0f, cy), Offset(size.width, cy), 0.8.dp.toPx(), pathEffect = dash)
                }
                val endTick = 2.5.dp.toPx()
                drawLine(primary.copy(alpha = 0.55f), Offset(0.5.dp.toPx(), cy - endTick), Offset(0.5.dp.toPx(), cy + endTick), 0.8.dp.toPx())
                drawLine(primary.copy(alpha = 0.55f), Offset(size.width - 0.5.dp.toPx(), cy - endTick), Offset(size.width - 0.5.dp.toPx(), cy + endTick), 0.8.dp.toPx())
                val r = 2.6.dp.toPx()
                drawLine(primary.copy(alpha = 0.65f), Offset(cx - r, cy), Offset(cx + r, cy), 1.dp.toPx())
                drawLine(primary.copy(alpha = 0.65f), Offset(cx, cy - r), Offset(cx, cy + r), 1.dp.toPx())
            }
        }
        EchoDividerStyle.HeavyEditorial -> {
            Canvas(modifier = modifier.fillMaxWidth().height(4.dp)) {
                val cy = size.height * 0.5f
                drawLine(color, Offset(0f, cy), Offset(size.width, cy), 1.8.dp.toPx())
                if (!style.isMonochrome) {
                    drawRect(
                        color = primary.copy(alpha = 0.88f),
                        topLeft = Offset(0f, cy - 1.5.dp.toPx()),
                        size = Size(18.dp.toPx(), 3.dp.toPx())
                    )
                }
            }
        }
        EchoDividerStyle.LiteraryOrnament -> {
            Canvas(modifier = modifier.fillMaxWidth().height(9.dp)) {
                val cy = size.height * 0.5f
                val cx = size.width * 0.5f
                val gap = 8.dp.toPx()
                if (cx > gap) {
                    drawLine(color, Offset(0f, cy), Offset(cx - gap, cy), 0.75.dp.toPx())
                    drawLine(color, Offset(cx + gap, cy), Offset(size.width, cy), 0.75.dp.toPx())
                } else {
                    drawLine(color, Offset(0f, cy), Offset(size.width, cy), 0.75.dp.toPx())
                }
                val d = 2.6.dp.toPx()
                val fleuron = Path().apply {
                    moveTo(cx, cy - d); lineTo(cx + d, cy); lineTo(cx, cy + d); lineTo(cx - d, cy); close()
                }
                drawPath(fleuron, color = primary.copy(alpha = 0.62f))
            }
        }
        EchoDividerStyle.CatalogRule -> {
            Canvas(modifier = modifier.fillMaxWidth().height(6.dp)) {
                val bottomY = size.height - 1.dp.toPx()
                drawLine(color, Offset(0f, bottomY), Offset(size.width, bottomY), 0.9.dp.toPx())
                drawRect(
                    color = primary.copy(alpha = 0.45f),
                    topLeft = Offset(0f, bottomY - 3.dp.toPx()),
                    size = Size(16.dp.toPx(), 3.dp.toPx())
                )
            }
        }
        EchoDividerStyle.FilmTrack -> {
            Canvas(modifier = modifier.fillMaxWidth().height(6.dp)) {
                val cy = size.height * 0.5f
                drawLine(color, Offset(0f, cy), Offset(size.width, cy), 0.8.dp.toPx())
                val step = 14.dp.toPx()
                val tickHalf = 2.dp.toPx()
                var x = 6.dp.toPx()
                while (x < size.width - 4.dp.toPx()) {
                    drawLine(secondary.copy(alpha = 0.52f), Offset(x, cy - tickHalf), Offset(x, cy + tickHalf), 0.9.dp.toPx())
                    x += step
                }
            }
        }
    }
}

/**
 * 首页顶部品牌字标与主题版头：根据主题风格呈现双线报头、胶片场记、蓝图图签等版式细节。
 */
@Composable
fun EchoThemedWordmark(
    workbench: Boolean,
    modifier: Modifier = Modifier
) {
    val style = LocalEchoThemeStyle.current
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val onBg = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val sectionLabel = if (workbench) "会话工作台" else "会话"

    when (style.headerStyle) {
        EchoHeaderStyle.NewspaperMasthead -> {
            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(Modifier.fillMaxWidth(0.78f).height(0.6.dp).background(onBg.copy(alpha = 0.45f)))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "ECHO",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 3.5.sp),
                        color = onBg
                    )
                    Text(
                        text = "晨刊 · EDITION · $sectionLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = primary,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                Box(Modifier.fillMaxWidth(0.78f).height(1.5.dp).background(onBg.copy(alpha = 0.72f)))
                Box(Modifier.fillMaxWidth(0.78f).height(0.6.dp).background(primary.copy(alpha = 0.65f)))
            }
        }
        EchoHeaderStyle.CinemaSlate -> {
            Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Box(Modifier.size(width = 6.dp, height = 3.dp).background(primary))
                        Box(Modifier.size(width = 6.dp, height = 3.dp).background(secondary))
                        Box(Modifier.size(width = 6.dp, height = 3.dp).background(primary))
                    }
                    Text(
                        text = "ECHO",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 3.sp),
                        color = onBg
                    )
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(0.8.dp, secondary.copy(alpha = 0.55f))
                ) {
                    Text(
                        text = "REEL · $sectionLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = secondary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
        EchoHeaderStyle.BlueprintSpec -> {
            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "ECHO // DWG-01",
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp),
                        color = onBg
                    )
                    Text(
                        text = "[$sectionLabel]",
                        style = MaterialTheme.typography.labelSmall,
                        color = primary
                    )
                }
                Box(Modifier.fillMaxWidth(0.72f).height(0.8.dp).background(primary.copy(alpha = 0.55f)))
            }
        }
        EchoHeaderStyle.TelemetryHud -> {
            Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CutCornerShape(3.dp))
                        .background(primary)
                )
                Text(
                    text = "ECHO :: RADAR",
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp),
                    color = onBg
                )
                Text(
                    text = sectionLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = secondary
                )
            }
        }
        EchoHeaderStyle.BauhausBar -> {
            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(primary))
                        Box(Modifier.size(7.dp).background(secondary))
                        Canvas(Modifier.size(8.dp)) {
                            val tri = Path().apply {
                                moveTo(size.width * 0.5f, 0f)
                                lineTo(size.width, size.height)
                                lineTo(0f, size.height)
                                close()
                            }
                            drawPath(tri, color = tertiary)
                        }
                    }
                    Text(
                        text = "ECHO",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 2.5.sp),
                        color = onBg
                    )
                    Text(
                        text = sectionLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = muted
                    )
                }
                Box(Modifier.width(74.dp).height(2.dp).background(onBg))
            }
        }
        EchoHeaderStyle.SwissEditorial -> {
            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "ECHO",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 2.2.sp),
                        color = onBg
                    )
                    Text(
                        text = "00 / $sectionLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = muted
                    )
                }
                Box(Modifier.width(84.dp).height(2.dp).background(onBg))
            }
        }
        EchoHeaderStyle.NauticalLog -> {
            Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    text = "ECHO",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 3.2.sp),
                    color = onBg
                )
                Text("◇", style = MaterialTheme.typography.labelMedium, color = secondary)
                Text(
                    text = "LOG · $sectionLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = muted
                )
            }
        }
        EchoHeaderStyle.FolioChapter -> {
            Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("§", style = MaterialTheme.typography.titleMedium, color = primary)
                Text(
                    text = "ECHO",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp),
                    color = onBg
                )
                Text(
                    text = sectionLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = muted
                )
            }
        }
        EchoHeaderStyle.CeladonSeal -> {
            Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ECHO",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium, letterSpacing = 3.6.sp),
                    color = onBg
                )
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .border(1.dp, primary.copy(alpha = 0.72f), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("瓷", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = primary)
                }
                Text(sectionLabel, style = MaterialTheme.typography.labelSmall, color = muted)
            }
        }
        EchoHeaderStyle.LacquerPlaque -> {
            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(Modifier.width(68.dp).height(0.8.dp).background(secondary.copy(alpha = 0.75f)))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "ECHO",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 3.5.sp),
                        color = onBg
                    )
                    Text(sectionLabel, style = MaterialTheme.typography.labelSmall, color = secondary)
                }
                Box(Modifier.width(68.dp).height(0.8.dp).background(primary.copy(alpha = 0.75f)))
            }
        }
        EchoHeaderStyle.CatalogueBar -> {
            Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ECHO",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 2.8.sp),
                    color = onBg
                )
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = primary.copy(alpha = 0.12f),
                    border = BorderStroke(0.8.dp, primary.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = "CAT · $sectionLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = primary,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
        }
        EchoHeaderStyle.BookPlate -> {
            Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "— ECHO —",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium, letterSpacing = 2.2.sp),
                    color = onBg
                )
                Text(sectionLabel, style = MaterialTheme.typography.labelSmall, color = muted)
            }
        }
        EchoHeaderStyle.VerseSlip -> {
            Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.width(2.dp).height(18.dp).background(primary.copy(alpha = 0.7f)))
                Text(
                    text = "E C H O",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = 2.sp),
                    color = onBg
                )
                Text(sectionLabel, style = MaterialTheme.typography.labelSmall, color = muted)
            }
        }
        EchoHeaderStyle.AiryHorizon -> {
            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "ECHO",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal, letterSpacing = 4.2.sp),
                        color = onBg
                    )
                    Text(sectionLabel, style = MaterialTheme.typography.labelSmall, color = primary)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.width(52.dp).height(0.8.dp).background(primary.copy(alpha = 0.5f)))
                    Box(Modifier.size(4.dp).clip(CircleShape).background(secondary))
                }
            }
        }
        EchoHeaderStyle.Default -> {
            Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "ECHO",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 4.sp),
                        color = onBg
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(primary)
                    )
                }
                Text(
                    text = sectionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = muted
                )
            }
        }
    }
}

internal fun formatThemedMessageClock(timestamp: Long, metaStyle: EchoMessageMetaStyle): String = when (metaStyle) {
    EchoMessageMetaStyle.TimecodeSubtitle ->
        "TC " + SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
    EchoMessageMetaStyle.Marginalia ->
        "¶ " + SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    EchoMessageMetaStyle.Telemetry ->
        "[" + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp)) + "]"
    EchoMessageMetaStyle.CatalogueStamp ->
        SimpleDateFormat("MM.dd / HH:mm", Locale.getDefault()).format(Date(timestamp))
    EchoMessageMetaStyle.Logbook ->
        "LOG " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    EchoMessageMetaStyle.SwissStamp ->
        SimpleDateFormat("MM.dd · HH:mm", Locale.getDefault()).format(Date(timestamp))
    EchoMessageMetaStyle.Colophon ->
        "— " + SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp)) + " —"
    EchoMessageMetaStyle.VerseNote ->
        "· " + SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    EchoMessageMetaStyle.GeometricTag ->
        "■ " + SimpleDateFormat("MM.dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    EchoMessageMetaStyle.SealMark ->
        "○ " + SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    EchoMessageMetaStyle.PlaqueStamp ->
        "◆ " + SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    EchoMessageMetaStyle.PressDateline ->
        "ED. " + SimpleDateFormat("MM.dd · HH:mm", Locale.getDefault()).format(Date(timestamp))
    EchoMessageMetaStyle.Default ->
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}

internal fun formatThemedDuration(ms: Long, metaStyle: EchoMessageMetaStyle): String {
    if (metaStyle == EchoMessageMetaStyle.TimecodeSubtitle) {
        val totalTenths = (ms.coerceAtLeast(0L) / 100L)
        val mins = (totalTenths / 600L).coerceAtMost(99L)
        val secs = (totalTenths % 600L) / 10L
        val tenths = totalTenths % 10L
        return String.format(Locale.US, "%02d:%02d.%d", mins, secs, tenths)
    }
    return when {
        ms < 1000 -> "${ms}ms"
        ms < 60000 -> "${ms / 1000}s"
        else -> "${ms / 60000}m${(ms % 60000) / 1000}s"
    }
}
