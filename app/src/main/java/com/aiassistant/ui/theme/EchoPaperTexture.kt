package com.aiassistant.ui.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.LruCache
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlin.random.Random

enum class EchoPaperMaterial {
    None, Deckle, Cotton, Folio, Celadon, Lacquer, Newsprint, ProjectorGrain, SkyMist, IndexStock, NauticalVellum
}

private data class PaperTextureKey(val material: EchoPaperMaterial, val dark: Boolean, val density: Int)
private val paperBrushCache = object : LruCache<PaperTextureKey, ShaderBrush>(24) {
    override fun create(key: PaperTextureKey): ShaderBrush = ShaderBrush(ImageShader(
        createPaperTexture(key.material, key.dark, key.density / 100f).asImageBitmap(),
        TileMode.Repeated, TileMode.Repeated
    ))
}

/** Fixed-seed, low-contrast material drawn below content; it never intercepts touch or text selection. */
@Composable
fun Modifier.echoPaperTexture(enabled: Boolean = true, pageRuling: Boolean = false): Modifier {
    if (!enabled) return this
    val style = LocalEchoThemeStyle.current
    val material = style.paperMaterial
    if (material == EchoPaperMaterial.None && style.surfacePattern == EchoSurfacePattern.None) return this
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < .5f
    val density = (LocalDensity.current.density * 100).roundToInt().coerceIn(50, 400)
    val brush = if (material == EchoPaperMaterial.None) null else remember(material, dark, density) {
        requireNotNull(paperBrushCache.get(PaperTextureKey(material, dark, density)))
    }
    val minorGrid = colors.outlineVariant.copy(alpha = if (dark) .22f else .16f)
    val majorGrid = colors.primary.copy(alpha = if (dark) .14f else .09f)
    val folioRule = colors.onSurface.copy(alpha = if (dark) .055f else .045f)
    val crackleLine = colors.primary.copy(alpha = if (dark) .075f else .065f)
    val crackleGilt = colors.secondary.copy(alpha = if (dark) .065f else .055f)
    val draftMinor = colors.outlineVariant.copy(alpha = if (dark) .17f else .22f)
    val draftMajor = colors.primary.copy(alpha = if (dark) .16f else .12f)
    val draftCross = colors.primary.copy(alpha = if (dark) .32f else .24f)
    val lacquerRuleOuter = colors.secondary.copy(alpha = if (dark) .16f else .14f)
    val lacquerRuleInner = colors.primary.copy(alpha = if (dark) .11f else .09f)
    val registerMark = colors.primary.copy(alpha = if (dark) .14f else .11f)
    val registerAccent = colors.secondary.copy(alpha = if (dark) .16f else .12f)
    val scanline = colors.onSurface.copy(alpha = if (dark) .022f else .018f)
    val sprocketTick = colors.secondary.copy(alpha = if (dark) .12f else .09f)
    val skyCurrent = colors.primary.copy(alpha = if (dark) .12f else .09f)
    val skyCurrentSoft = colors.secondary.copy(alpha = if (dark) .09f else .07f)
    val indexTabRule = colors.primary.copy(alpha = if (dark) .22f else .16f)
    val chartLine = colors.primary.copy(alpha = if (dark) .10f else .075f)
    val chartTick = colors.secondary.copy(alpha = if (dark) .18f else .14f)
    val swissGuide = colors.onSurface.copy(alpha = if (dark) .065f else .055f)
    val letterRule = colors.primary.copy(alpha = if (dark) .12f else .095f)
    val bauhausPrimary = colors.primary.copy(alpha = if (dark) .16f else .12f)
    val bauhausSecondary = colors.secondary.copy(alpha = if (dark) .16f else .12f)
    val bauhausTertiary = colors.tertiary.copy(alpha = if (dark) .18f else .14f)
    val deckleEdge = colors.outlineVariant.copy(alpha = if (dark) .16f else .14f)
    val indexRule = colors.onSurface.copy(alpha = if (dark) .042f else .038f)
    return drawBehind {
        brush?.let { drawRect(it) }
        if (style.surfacePattern == EchoSurfacePattern.SkyCurrents && pageRuling) {
            val upper = Path().apply {
                moveTo(-size.width * .05f, size.height * .18f)
                cubicTo(size.width * .24f, size.height * .12f, size.width * .55f, size.height * .26f, size.width * 1.05f, size.height * .17f)
            }
            val middle = Path().apply {
                moveTo(-size.width * .08f, size.height * .52f)
                cubicTo(size.width * .25f, size.height * .59f, size.width * .62f, size.height * .43f, size.width * 1.08f, size.height * .51f)
            }
            val lower = Path().apply {
                moveTo(-size.width * .04f, size.height * .79f)
                cubicTo(size.width * .33f, size.height * .73f, size.width * .66f, size.height * .86f, size.width * 1.04f, size.height * .76f)
            }
            val stroke = Stroke(width = .75.dp.toPx())
            drawPath(upper, skyCurrent, style = stroke)
            drawPath(middle, skyCurrentSoft, style = stroke)
            drawPath(lower, skyCurrent, style = stroke)
        }
        if (style.surfacePattern == EchoSurfacePattern.IndexCatalogue && pageRuling) {
            // 目录索引卡：极淡横线 + 顶部朱红题栏线 + 右侧四枚阶梯挂签
            var rowY = 54.dp.toPx()
            drawLine(indexTabRule.copy(alpha = indexTabRule.alpha * .75f), Offset(0f, rowY), Offset(size.width, rowY), .8.dp.toPx())
            val rowStep = 26.dp.toPx()
            rowY += rowStep
            while (rowY < size.height) {
                drawLine(indexRule, Offset(0f, rowY), Offset(size.width, rowY), .5.dp.toPx())
                rowY += rowStep
            }
            val tabWidth = 10.dp.toPx()
            val tabHeight = 24.dp.toPx()
            val right = size.width - 3.dp.toPx()
            repeat(4) { index ->
                val top = size.height * (.12f + index * .16f)
                if (top + tabHeight < size.height) {
                    drawRoundRect(
                        color = indexTabRule,
                        topLeft = Offset(right - tabWidth, top),
                        size = Size(tabWidth, tabHeight),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                        style = Stroke(width = .65.dp.toPx())
                    )
                    drawLine(
                        color = indexTabRule.copy(alpha = indexTabRule.alpha * .7f),
                        start = Offset(right - tabWidth + 2.5.dp.toPx(), top + tabHeight * .5f),
                        end = Offset(right - 2.5.dp.toPx(), top + tabHeight * .5f),
                        strokeWidth = .55.dp.toPx()
                    )
                }
            }
        }
        if (material == EchoPaperMaterial.Folio && pageRuling) {
            var y = 28.dp.toPx()
            while (y < size.height) {
                drawLine(folioRule, Offset(0f, y), Offset(size.width, y), .5.dp.toPx())
                y += 28.dp.toPx()
            }
            // 拾页双栏红批注竖线
            val margin = 20.dp.toPx()
            drawLine(colors.primary.copy(alpha = if (dark) .14f else .11f), Offset(margin, 0f), Offset(margin, size.height), .75.dp.toPx())
            drawLine(colors.primary.copy(alpha = if (dark) .08f else .06f), Offset(margin + 3.dp.toPx(), 0f), Offset(margin + 3.dp.toPx(), size.height), .5.dp.toPx())
        }
        if (style.surfacePattern == EchoSurfacePattern.SonarGrid) {
            val step = 24.dp.toPx()
            var x = 0f
            var column = 0
            while (x <= size.width) {
                drawLine(if (column % 5 == 0) majorGrid else minorGrid, Offset(x, 0f), Offset(x, size.height), .5.dp.toPx())
                x += step
                column++
            }
            var y = 0f
            var row = 0
            while (y <= size.height) {
                drawLine(if (row % 5 == 0) majorGrid else minorGrid, Offset(0f, y), Offset(size.width, y), .5.dp.toPx())
                y += step
                row++
            }
            if (pageRuling) {
                val center = Offset((size.width - 28.dp.toPx()).coerceAtLeast(40.dp.toPx()), 96.dp.toPx())
                val arcColor = majorGrid.copy(alpha = majorGrid.alpha * .82f)
                val ringStroke = Stroke(width = .65.dp.toPx())
                listOf(28.dp.toPx(), 54.dp.toPx(), 82.dp.toPx()).forEach { radius ->
                    drawCircle(arcColor, radius, center, style = ringStroke)
                }
                drawLine(arcColor.copy(alpha = arcColor.alpha * .7f), center, Offset(center.x - 68.dp.toPx(), center.y + 42.dp.toPx()), .6.dp.toPx())
                drawLine(arcColor.copy(alpha = arcColor.alpha * .7f), center, Offset(center.x - 42.dp.toPx(), center.y + 72.dp.toPx()), .6.dp.toPx())
            }
        }
        if (style.surfacePattern == EchoSurfacePattern.CeladonCrackle && pageRuling) {
            // 宋瓷冰裂开片网纹：基于固定晶胞偏移生成低对比度多边形细裂纹，轻柔点缀而不干扰正文
            val cell = 96.dp.toPx()
            val cols = (size.width / cell).toInt() + 2
            val rows = (size.height / cell).toInt() + 2
            val strokeThin = .65.dp.toPx()
            val strokeHair = .45.dp.toPx()
            fun cellNode(cx: Int, cy: Int): Offset {
                val seed = (cx * 73856093) xor (cy * 19349663) xor 0x5A3C
                val jx = (((seed and 0xFF) / 255f) - 0.5f) * cell * 0.52f
                val jy = ((((seed ushr 8) and 0xFF) / 255f) - 0.5f) * cell * 0.52f
                return Offset(cx * cell + cell * 0.5f + jx, cy * cell + cell * 0.5f + jy)
            }
            for (r in -1..rows) {
                for (c in -1..cols) {
                    val p = cellNode(c, r)
                    val right = cellNode(c + 1, r)
                    val down = cellNode(c, r + 1)
                    val isGiltEdge = ((c * 3 + r * 5) and 7) == 0
                    drawLine(if (isGiltEdge) crackleGilt else crackleLine, p, right, if (isGiltEdge) strokeThin else strokeHair)
                    drawLine(crackleLine, p, down, strokeHair)
                    if (((c + r) and 1) == 0) {
                        val diag = cellNode(c + 1, r + 1)
                        val mid = Offset((p.x + diag.x) * 0.5f, (p.y + diag.y) * 0.5f)
                        drawLine(crackleLine.copy(alpha = crackleLine.alpha * 0.72f), p, mid, strokeHair)
                    }
                }
            }
        }
        if (style.surfacePattern == EchoSurfacePattern.BlueprintDraft) {
            // 精密制图坐标网格 + 十字准星 + 左缘/顶缘 L 形标尺刻度
            val step = 16.dp.toPx()
            val thin = .45.dp.toPx()
            val majorStroke = .7.dp.toPx()
            var x = 0f
            var col = 0
            while (x <= size.width) {
                val isMajor = col % 5 == 0
                drawLine(if (isMajor) draftMajor else draftMinor, Offset(x, 0f), Offset(x, size.height), if (isMajor) majorStroke else thin)
                x += step
                col++
            }
            var y = 0f
            var row = 0
            while (y <= size.height) {
                val isMajor = row % 5 == 0
                drawLine(if (isMajor) draftMajor else draftMinor, Offset(0f, y), Offset(size.width, y), if (isMajor) majorStroke else thin)
                y += step
                row++
            }
            if (pageRuling) {
                val majorStep = step * 5f
                val crossArm = 3.5.dp.toPx()
                var cy = majorStep
                while (cy < size.height) {
                    var cx = majorStep
                    while (cx < size.width) {
                        drawLine(draftCross, Offset(cx - crossArm, cy), Offset(cx + crossArm, cy), majorStroke)
                        drawLine(draftCross, Offset(cx, cy - crossArm), Offset(cx, cy + crossArm), majorStroke)
                        cx += majorStep
                    }
                    cy += majorStep
                }
                // 左缘精密工程标尺刻度
                var tickY = step
                var tickIdx = 1
                while (tickY < size.height) {
                    val tickLen = when {
                        tickIdx % 5 == 0 -> 9.dp.toPx()
                        tickIdx % 2 == 0 -> 5.5.dp.toPx()
                        else -> 3.dp.toPx()
                    }
                    drawLine(
                        if (tickIdx % 5 == 0) draftCross else draftMajor,
                        Offset(0f, tickY),
                        Offset(tickLen, tickY),
                        if (tickIdx % 5 == 0) majorStroke else thin
                    )
                    tickY += step
                    tickIdx++
                }
                // 顶缘水平工程标尺刻度
                var tickX = step
                var hTickIdx = 1
                while (tickX < size.width) {
                    val tickLen = when {
                        hTickIdx % 5 == 0 -> 8.dp.toPx()
                        hTickIdx % 2 == 0 -> 5.dp.toPx()
                        else -> 2.8.dp.toPx()
                    }
                    drawLine(
                        if (hTickIdx % 5 == 0) draftCross else draftMajor,
                        Offset(tickX, 0f),
                        Offset(tickX, tickLen),
                        if (hTickIdx % 5 == 0) majorStroke else thin
                    )
                    tickX += step
                    hTickIdx++
                }
            }
        }
        if (style.surfacePattern == EchoSurfacePattern.NewsRegister && pageRuling) {
            // 报纸印刷对准标记 + 淡三栏报刊分栏线 + 右上小十字套准标
            val inset = 10.dp.toPx()
            val arm = 8.dp.toPx()
            val stroke = .7.dp.toPx()
            val colRule = colors.onSurface.copy(alpha = if (dark) .045f else .04f)
            drawLine(colRule, Offset(size.width * .34f, inset + 18.dp.toPx()), Offset(size.width * .34f, size.height - inset - 18.dp.toPx()), .55.dp.toPx())
            drawLine(colRule, Offset(size.width * .67f, inset + 18.dp.toPx()), Offset(size.width * .67f, size.height - inset - 18.dp.toPx()), .55.dp.toPx())
            drawLine(registerMark, Offset(inset, inset), Offset(inset + arm, inset), stroke)
            drawLine(registerMark, Offset(inset, inset), Offset(inset, inset + arm), stroke)
            drawLine(registerMark, Offset(size.width - inset - arm, inset), Offset(size.width - inset, inset), stroke)
            drawLine(registerMark, Offset(size.width - inset, inset), Offset(size.width - inset, inset + arm), stroke)
            drawLine(registerMark, Offset(inset, size.height - inset), Offset(inset + arm, size.height - inset), stroke)
            drawLine(registerMark, Offset(inset, size.height - inset - arm), Offset(inset, size.height - inset), stroke)
            drawLine(registerMark, Offset(size.width - inset - arm, size.height - inset), Offset(size.width - inset, size.height - inset), stroke)
            drawLine(registerMark, Offset(size.width - inset, size.height - inset - arm), Offset(size.width - inset, size.height - inset), stroke)
            val rx = size.width - inset - 14.dp.toPx()
            val ry = inset + 3.dp.toPx()
            drawCircle(registerAccent, radius = 1.6.dp.toPx(), center = Offset(size.width - inset - 3.dp.toPx(), ry))
            drawLine(registerMark, Offset(rx - 3.dp.toPx(), ry), Offset(rx + 3.dp.toPx(), ry), .6.dp.toPx())
            drawLine(registerMark, Offset(rx, ry - 3.dp.toPx()), Offset(rx, ry + 3.dp.toPx()), .6.dp.toPx())
            drawCircle(registerAccent, radius = 1.6.dp.toPx(), center = Offset(inset + 3.dp.toPx(), size.height - inset - 3.dp.toPx()))
        }
        if (style.surfacePattern == EchoSurfacePattern.FilmScanline && pageRuling) {
            // 胶片细扫描线、上下银幕画幅线与左右片边刻度，只覆盖整页背景
            var scanY = 0f
            while (scanY < size.height) {
                drawLine(scanline, Offset(0f, scanY), Offset(size.width, scanY), 1.dp.toPx())
                scanY += 3.dp.toPx()
            }
            val frameInset = 14.dp.toPx()
            if (size.height > frameInset * 4f) {
                drawLine(sprocketTick.copy(alpha = sprocketTick.alpha * .7f), Offset(frameInset, frameInset), Offset(size.width - frameInset, frameInset), .55.dp.toPx())
                drawLine(sprocketTick.copy(alpha = sprocketTick.alpha * .7f), Offset(frameInset, size.height - frameInset), Offset(size.width - frameInset, size.height - frameInset), .55.dp.toPx())
            }
            val step = 26.dp.toPx()
            val tickLength = 4.dp.toPx()
            var tickY = 12.dp.toPx()
            while (tickY < size.height) {
                drawLine(sprocketTick, Offset(0f, tickY), Offset(tickLength, tickY), .6.dp.toPx())
                drawLine(sprocketTick, Offset(size.width - tickLength, tickY), Offset(size.width, tickY), .6.dp.toPx())
                tickY += step
            }
        }
        if (style.surfacePattern == EchoSurfacePattern.LacquerFrame && pageRuling) {
            // 漆匣双线描金页框 + 上下回纹折角，营造卷轴/大漆书匣仪式感
            val outerInset = 8.dp.toPx()
            val innerInset = 11.5.dp.toPx()
            val stroke = .65.dp.toPx()
            drawLine(lacquerRuleOuter, Offset(outerInset, 0f), Offset(outerInset, size.height), stroke)
            drawLine(lacquerRuleInner, Offset(innerInset, 0f), Offset(innerInset, size.height), stroke * 0.8f)
            drawLine(lacquerRuleOuter, Offset(size.width - outerInset, 0f), Offset(size.width - outerInset, size.height), stroke)
            drawLine(lacquerRuleInner, Offset(size.width - innerInset, 0f), Offset(size.width - innerInset, size.height), stroke * 0.8f)
            val cornerY = 14.dp.toPx()
            val returnLen = 18.dp.toPx()
            if (size.height > cornerY * 4f && size.width > outerInset * 6f) {
                drawLine(lacquerRuleOuter, Offset(outerInset, cornerY), Offset(outerInset + returnLen, cornerY), stroke)
                drawLine(lacquerRuleOuter, Offset(size.width - outerInset - returnLen, cornerY), Offset(size.width - outerInset, cornerY), stroke)
                drawLine(lacquerRuleOuter, Offset(outerInset, size.height - cornerY), Offset(outerInset + returnLen, size.height - cornerY), stroke)
                drawLine(lacquerRuleOuter, Offset(size.width - outerInset - returnLen, size.height - cornerY), Offset(size.width - outerInset, size.height - cornerY), stroke)
            }
        }
        if (style.surfacePattern == EchoSurfacePattern.NauticalChart && pageRuling) {
            // 航海图经纬线与右侧航程刻度、右下双环八向罗盘
            val stroke = .55.dp.toPx()
            val step = 64.dp.toPx()
            var x = step
            while (x < size.width) {
                drawLine(chartLine, Offset(x, 0f), Offset(x, size.height), stroke)
                x += step
            }
            var y = step
            while (y < size.height) {
                drawLine(chartLine, Offset(0f, y), Offset(size.width, y), stroke)
                y += step
            }
            var tickY = 16.dp.toPx()
            var idx = 0
            while (tickY < size.height) {
                val len = if (idx % 4 == 0) 7.dp.toPx() else 3.5.dp.toPx()
                drawLine(chartTick, Offset(size.width - len, tickY), Offset(size.width, tickY), stroke)
                tickY += 16.dp.toPx()
                idx++
            }
            val cx = (size.width - 36.dp.toPx()).coerceAtLeast(24.dp.toPx())
            val cy = (size.height - 56.dp.toPx()).coerceAtLeast(24.dp.toPx())
            val r = 15.dp.toPx()
            drawCircle(chartTick.copy(alpha = chartTick.alpha * .7f), r, Offset(cx, cy), style = Stroke(width = stroke))
            drawCircle(chartTick.copy(alpha = chartTick.alpha * .45f), r * .62f, Offset(cx, cy), style = Stroke(width = stroke * .8f))
            drawLine(chartTick, Offset(cx - r * 1.25f, cy), Offset(cx + r * 1.25f, cy), stroke)
            drawLine(chartTick, Offset(cx, cy - r * 1.25f), Offset(cx, cy + r * 1.25f), stroke)
            val diag = r * .78f
            val diagColor = chartTick.copy(alpha = chartTick.alpha * .55f)
            drawLine(diagColor, Offset(cx - diag, cy - diag), Offset(cx + diag, cy + diag), stroke * .8f)
            drawLine(diagColor, Offset(cx - diag, cy + diag), Offset(cx + diag, cy - diag), stroke * .8f)
            val northTri = Path().apply {
                moveTo(cx, cy - r * 1.38f)
                lineTo(cx - 2.3.dp.toPx(), cy - r * 1.02f)
                lineTo(cx + 2.3.dp.toPx(), cy - r * 1.02f)
                close()
            }
            drawPath(northTri, color = chartTick)
        }
        if (style.surfacePattern == EchoSurfacePattern.SwissGrid && pageRuling) {
            // 瑞士国际主义四栏版式基准线、上下页边刻度与模块交点准星
            val stroke = .5.dp.toPx()
            val margin = 12.dp.toPx()
            val usable = (size.width - margin * 2f).coerceAtLeast(1f)
            val moduleY = 96.dp.toPx()
            for (i in 0..4) {
                val gx = margin + usable * (i / 4f)
                drawLine(swissGuide, Offset(gx, 0f), Offset(gx, size.height), stroke)
                drawLine(swissGuide.copy(alpha = swissGuide.alpha * 1.8f), Offset(gx, 0f), Offset(gx, 8.dp.toPx()), stroke * 1.4f)
                drawLine(swissGuide.copy(alpha = swissGuide.alpha * 1.8f), Offset(gx, size.height - 8.dp.toPx()), Offset(gx, size.height), stroke * 1.4f)
                var my = moduleY
                while (my < size.height) {
                    drawLine(swissGuide.copy(alpha = swissGuide.alpha * 1.5f), Offset(gx - 3.dp.toPx(), my), Offset(gx + 3.dp.toPx(), my), stroke)
                    my += moduleY
                }
            }
        }
        if (style.surfacePattern == EchoSurfacePattern.LetterColumns && pageRuling) {
            // 棉纸诗笺：左右清雅直行界栏 + 天地眉线与上下短折端头
            val inset = 11.dp.toPx()
            val stroke = .6.dp.toPx()
            val cap = 5.dp.toPx()
            val vPad = 14.dp.toPx()
            if (size.width > inset * 3f && size.height > vPad * 3f) {
                drawLine(letterRule.copy(alpha = letterRule.alpha * .45f), Offset(inset, vPad), Offset(size.width - inset, vPad), stroke * .8f)
                drawLine(letterRule.copy(alpha = letterRule.alpha * .45f), Offset(inset, size.height - vPad), Offset(size.width - inset, size.height - vPad), stroke * .8f)
                drawLine(letterRule, Offset(inset, vPad), Offset(inset, size.height - vPad), stroke)
                drawLine(letterRule, Offset(size.width - inset, vPad), Offset(size.width - inset, size.height - vPad), stroke)
                drawLine(letterRule, Offset(inset - cap * .4f, vPad), Offset(inset + cap * .6f, vPad), stroke)
                drawLine(letterRule, Offset(size.width - inset - cap * .6f, vPad), Offset(size.width - inset + cap * .4f, vPad), stroke)
                drawLine(letterRule, Offset(inset - cap * .4f, size.height - vPad), Offset(inset + cap * .6f, size.height - vPad), stroke)
                drawLine(letterRule, Offset(size.width - inset - cap * .6f, size.height - vPad), Offset(size.width - inset + cap * .4f, size.height - vPad), stroke)
            }
        }
        if (style.surfacePattern == EchoSurfacePattern.BauhausGeometry && pageRuling) {
            // 构型：左边栏几何轴线、红蓝原色页边块及左下包豪斯黄空心圆环
            val axisX = 10.dp.toPx()
            drawLine(bauhausPrimary, Offset(axisX, 0f), Offset(axisX, size.height), 1.dp.toPx())
            drawRect(bauhausPrimary, Offset(0f, size.height * .22f), Size(axisX, 14.dp.toPx()))
            drawRect(bauhausSecondary, Offset(size.width - 6.dp.toPx(), size.height * .64f), Size(6.dp.toPx(), 18.dp.toPx()))
            drawCircle(
                color = bauhausTertiary,
                radius = 12.dp.toPx(),
                center = Offset(26.dp.toPx(), size.height * .78f),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
        if (style.surfacePattern == EchoSurfacePattern.DeckleEdge && pageRuling) {
            // 毛边文库：左侧书脊折痕线 + 右缘固定确定性毛边折线
            val spineX = 10.dp.toPx()
            drawLine(folioRule.copy(alpha = folioRule.alpha * 1.4f), Offset(spineX, 0f), Offset(spineX, size.height), .7.dp.toPx())
            val step = 12.dp.toPx()
            val edgePath = Path()
            var y = 0f
            var idx = 0
            edgePath.moveTo(size.width - 1.5.dp.toPx(), 0f)
            while (y < size.height) {
                y = (y + step).coerceAtMost(size.height)
                val seed = (idx * 2654435761L) and 0xFFL
                val inset = (0.8f + (seed / 255f) * 2.4f).dp.toPx()
                edgePath.lineTo(size.width - inset, y)
                idx++
            }
            drawPath(edgePath, color = deckleEdge, style = Stroke(width = .8.dp.toPx()))
        }
    }
}

/**
 * 主题边框细部工艺：在容器前景叠绘不拦截触控的材质装饰（瓷釉双唇边 / 蓝图注册角标 / 漆匣描金双框）。
 */
@Composable
fun Modifier.echoThemeFrameOrnament(
    cornerRadius: Dp = LocalEchoThemeStyle.current.panelCornerRadius,
    compact: Boolean = false,
    isUserBubble: Boolean = false
): Modifier {
    val ornament = LocalEchoThemeStyle.current.frameOrnament
    if (ornament == EchoFrameOrnament.None) return this
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < .5f
    val primary = colors.primary
    val secondary = colors.secondary
    val tertiary = colors.tertiary
    val outline = colors.outline
    return drawWithContent {
        drawContent()
        when (ornament) {
            EchoFrameOrnament.SkyHorizon -> {
                val y = (if (compact) 2.5.dp else 3.5.dp).toPx()
                val inset = (cornerRadius.toPx() * .65f).coerceAtMost(size.width * .18f)
                val end = size.width - inset
                if (end > inset && size.height > y * 2f) {
                    drawLine(
                        color = primary.copy(alpha = if (dark) .38f else .24f),
                        start = Offset(inset, y),
                        end = Offset(end, y),
                        strokeWidth = .65.dp.toPx()
                    )
                    drawCircle(
                        color = secondary.copy(alpha = if (dark) .70f else .55f),
                        radius = (if (compact) 1.1.dp else 1.4.dp).toPx(),
                        center = Offset(inset + (end - inset) * .72f, y)
                    )
                }
            }
            EchoFrameOrnament.CeramicRim -> {
                // 青瓷玉釉内高光 + 极细内缩唇边 + 底缘暖金釉反射短线
                val inset = if (compact) 2.dp.toPx() else 2.5.dp.toPx()
                val r = (cornerRadius.toPx() - inset).coerceAtLeast(2f)
                val sheenAlpha = if (dark) 0.14f else 0.55f
                val rimAlpha = if (dark) 0.22f else 0.14f
                val topSpanStart = (cornerRadius.toPx() * 0.7f).coerceAtMost(size.width * 0.22f)
                drawLine(
                    color = Color.White.copy(alpha = sheenAlpha),
                    start = Offset(topSpanStart, 1.dp.toPx()),
                    end = Offset(size.width - topSpanStart, 1.dp.toPx()),
                    strokeWidth = 1.dp.toPx()
                )
                if (size.width > topSpanStart * 2.6f && size.height > 12.dp.toPx()) {
                    drawLine(
                        color = secondary.copy(alpha = if (dark) 0.34f else 0.28f),
                        start = Offset(topSpanStart * 1.1f, size.height - 1.2.dp.toPx()),
                        end = Offset(size.width - topSpanStart * 1.1f, size.height - 1.2.dp.toPx()),
                        strokeWidth = 0.7.dp.toPx()
                    )
                }
                if (size.width > inset * 4f && size.height > inset * 4f) {
                    drawRoundRect(
                        color = primary.copy(alpha = rimAlpha),
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2f, size.height - inset * 2f),
                        cornerRadius = CornerRadius(r, r),
                        style = Stroke(width = 0.6.dp.toPx())
                    )
                }
                if (isUserBubble && size.height > 20.dp.toPx()) {
                    val vSpan = 7.dp.toPx().coerceAtMost(size.height * 0.28f)
                    drawLine(
                        color = secondary.copy(alpha = 0.42f),
                        start = Offset(size.width - 1.6.dp.toPx(), vSpan),
                        end = Offset(size.width - 1.6.dp.toPx(), size.height - vSpan),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
            EchoFrameOrnament.BlueprintBrackets -> {
                // 蓝图四角 L 形精密对准角标 + 迷你 CAD 图签角格 + 用户气泡左索引竖条
                val arm = (if (compact) 5.dp else 7.dp).toPx().coerceAtMost(minOf(size.width, size.height) * 0.32f)
                val stroke = 1.3.dp.toPx()
                val bracketColor = primary.copy(alpha = if (dark) 0.80f else 0.72f)
                val pad = stroke * 0.5f
                val w = size.width
                val h = size.height
                drawLine(bracketColor, Offset(pad, pad), Offset(pad + arm, pad), stroke)
                drawLine(bracketColor, Offset(pad, pad), Offset(pad, pad + arm), stroke)
                drawLine(bracketColor, Offset(w - pad - arm, pad), Offset(w - pad, pad), stroke)
                drawLine(bracketColor, Offset(w - pad, pad), Offset(w - pad, pad + arm), stroke)
                drawLine(bracketColor, Offset(pad, h - pad), Offset(pad + arm, h - pad), stroke)
                drawLine(bracketColor, Offset(pad, h - pad - arm), Offset(pad, h - pad), stroke)
                drawLine(bracketColor, Offset(w - pad - arm, h - pad), Offset(w - pad, h - pad), stroke)
                drawLine(bracketColor, Offset(w - pad, h - pad - arm), Offset(w - pad, h - pad), stroke)
                if (!compact && w > 96.dp.toPx() && h > 44.dp.toPx()) {
                    val boxW = 18.dp.toPx()
                    val boxH = 8.dp.toPx()
                    val cadColor = primary.copy(alpha = if (dark) 0.30f else 0.24f)
                    drawLine(cadColor, Offset(w - pad - boxW, h - pad - boxH), Offset(w - pad, h - pad - boxH), 0.6.dp.toPx())
                    drawLine(cadColor, Offset(w - pad - boxW, h - pad - boxH), Offset(w - pad - boxW, h - pad), 0.6.dp.toPx())
                }
                if (isUserBubble) {
                    val stripeWidth = 2.5.dp.toPx()
                    drawRect(
                        color = primary.copy(alpha = if (dark) 0.85f else 0.78f),
                        topLeft = Offset(0f, arm + 2.dp.toPx()),
                        size = Size(stripeWidth, (h - (arm + 2.dp.toPx()) * 2f).coerceAtLeast(0f))
                    )
                }
            }
            EchoFrameOrnament.LacquerDoubleFrame -> {
                // 漆匣描金内缩双框 + 四角金点与回纹折角 + 用户气泡右侧印章竖边
                val inset = (if (compact) 2.5.dp else 3.5.dp).toPx()
                val r = (cornerRadius.toPx() - inset).coerceAtLeast(2f)
                val giltColor = (if (dark) primary else secondary).copy(alpha = if (dark) 0.44f else 0.36f)
                val pinColor = (if (dark) secondary else primary).copy(alpha = if (dark) 0.62f else 0.52f)
                if (size.width > inset * 4f && size.height > inset * 4f) {
                    drawRoundRect(
                        color = giltColor,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2f, size.height - inset * 2f),
                        cornerRadius = CornerRadius(r, r),
                        style = Stroke(width = 0.75.dp.toPx())
                    )
                    if (!compact) {
                        val pinR = 1.2.dp.toPx()
                        val pinOffset = inset + 3.dp.toPx()
                        val fret = 3.dp.toPx()
                        drawCircle(pinColor, pinR, Offset(pinOffset, pinOffset))
                        drawCircle(pinColor, pinR, Offset(size.width - pinOffset, pinOffset))
                        drawCircle(pinColor, pinR, Offset(pinOffset, size.height - pinOffset))
                        drawCircle(pinColor, pinR, Offset(size.width - pinOffset, size.height - pinOffset))
                        if (size.width > pinOffset * 4f && size.height > pinOffset * 4f) {
                            val fretColor = giltColor.copy(alpha = giltColor.alpha * .85f)
                            drawLine(fretColor, Offset(pinOffset, pinOffset + 2.dp.toPx()), Offset(pinOffset, pinOffset + 2.dp.toPx() + fret), .65.dp.toPx())
                            drawLine(fretColor, Offset(size.width - pinOffset, pinOffset + 2.dp.toPx()), Offset(size.width - pinOffset, pinOffset + 2.dp.toPx() + fret), .65.dp.toPx())
                        }
                    }
                }
                if (isUserBubble) {
                    val stripeWidth = 2.5.dp.toPx()
                    val vPad = 6.dp.toPx().coerceAtMost(size.height * 0.25f)
                    drawRect(
                        color = (if (dark) primary else outline).copy(alpha = 0.72f),
                        topLeft = Offset(size.width - stripeWidth, vPad),
                        size = Size(stripeWidth, (size.height - vPad * 2f).coerceAtLeast(0f))
                    )
                }
            }
            EchoFrameOrnament.IndexTab -> {
                val tabWidth = (if (compact) 20.dp else 30.dp).toPx().coerceAtMost(size.width * .32f)
                val tabHeight = (if (compact) 4.dp else 6.dp).toPx()
                val top = (if (compact) 1.5.dp else 2.dp).toPx()
                val right = size.width - (if (compact) 8.dp else 12.dp).toPx()
                val left = right - tabWidth
                val bottom = top + tabHeight
                val tabColor = primary.copy(alpha = if (dark) .68f else .56f)
                val stroke = .8.dp.toPx()
                if (left > 0f && bottom < size.height) {
                    drawLine(tabColor, Offset(left, top), Offset(left, bottom), stroke)
                    drawLine(tabColor, Offset(left, bottom), Offset(right, bottom), stroke)
                    drawLine(tabColor, Offset(right, bottom), Offset(right, top), stroke)
                    if (!compact && tabWidth > 14.dp.toPx()) {
                        val codeY = top + tabHeight * .55f
                        drawLine(
                            color = secondary.copy(alpha = if (dark) .56f else .46f),
                            start = Offset(left + 5.dp.toPx(), codeY),
                            end = Offset(right - 5.dp.toPx(), codeY),
                            strokeWidth = .65.dp.toPx()
                        )
                    }
                }
                if (isUserBubble && size.width > 32.dp.toPx() && size.height > 20.dp.toPx()) {
                    val ruleY = 8.dp.toPx()
                    drawLine(
                        color = primary.copy(alpha = if (dark) .52f else .44f),
                        start = Offset(8.dp.toPx(), ruleY),
                        end = Offset(left - 6.dp.toPx(), ruleY),
                        strokeWidth = .75.dp.toPx()
                    )
                    drawCircle(
                        color = secondary.copy(alpha = if (dark) .60f else .50f),
                        radius = 1.8.dp.toPx(),
                        center = Offset(7.dp.toPx(), 4.5.dp.toPx()),
                        style = Stroke(width = .75.dp.toPx())
                    )
                }
            }
            EchoFrameOrnament.CropMarks -> {
                // 晨刊：印刷裁切角标 + 用户气泡朱红索引竖线
                val arm = (if (compact) 4.dp else 7.dp).toPx()
                    .coerceAtMost(minOf(size.width, size.height) * 0.30f)
                val stroke = .9.dp.toPx()
                val mark = primary.copy(alpha = if (dark) .74f else .62f)
                val pad = (if (compact) 2.dp else 3.dp).toPx()
                val w = size.width
                val h = size.height
                drawLine(mark, Offset(pad, pad), Offset(pad + arm, pad), stroke)
                drawLine(mark, Offset(pad, pad), Offset(pad, pad + arm), stroke)
                drawLine(mark, Offset(w - pad - arm, pad), Offset(w - pad, pad), stroke)
                drawLine(mark, Offset(w - pad, pad), Offset(w - pad, pad + arm), stroke)
                drawLine(mark, Offset(pad, h - pad), Offset(pad + arm, h - pad), stroke)
                drawLine(mark, Offset(pad, h - pad - arm), Offset(pad, h - pad), stroke)
                drawLine(mark, Offset(w - pad - arm, h - pad), Offset(w - pad, h - pad), stroke)
                drawLine(mark, Offset(w - pad, h - pad - arm), Offset(w - pad, h - pad), stroke)
                if (isUserBubble) {
                    val ruleInset = 4.dp.toPx()
                    drawRect(
                        color = primary.copy(alpha = if (dark) .82f else .72f),
                        topLeft = Offset(0f, ruleInset),
                        size = Size(2.dp.toPx(), (h - ruleInset * 2f).coerceAtLeast(0f))
                    )
                }
            }
            EchoFrameOrnament.ReelPerforation -> {
                // 放映室：卡片顶/底缘齿孔描边；用户气泡叠入场记板斜纹与银幕左线
                val holeWidth = (if (compact) 4.5.dp else 6.5.dp).toPx()
                val holeHeight = (if (compact) 1.8.dp else 2.2.dp).toPx()
                val holeGap = (if (compact) 4.dp else 6.dp).toPx()
                val holeInset = 2.5.dp.toPx()
                val holeColor = secondary.copy(alpha = if (dark) .32f else .28f)
                if (!isUserBubble) {
                    var holeX = holeInset + 3.dp.toPx()
                    while (holeX + holeWidth <= size.width - holeInset) {
                        drawRoundRect(
                            color = holeColor,
                            topLeft = Offset(holeX, holeInset),
                            size = Size(holeWidth, holeHeight),
                            cornerRadius = CornerRadius(holeHeight / 2f, holeHeight / 2f),
                            style = Stroke(width = .65.dp.toPx())
                        )
                        if (!compact && size.height > 64.dp.toPx()) {
                            drawRoundRect(
                                color = holeColor,
                                topLeft = Offset(holeX, size.height - holeInset - holeHeight),
                                size = Size(holeWidth, holeHeight),
                                cornerRadius = CornerRadius(holeHeight / 2f, holeHeight / 2f),
                                style = Stroke(width = .65.dp.toPx())
                            )
                        }
                        holeX += holeWidth + holeGap
                    }
                }
                if (isUserBubble) {
                    val band = 3.5.dp.toPx()
                    val horizontalInset = 8.dp.toPx()
                    if (size.width > horizontalInset * 2f && size.height > band * 3f) {
                        val bandTop = 1.5.dp.toPx()
                        drawRect(
                            color = primary.copy(alpha = if (dark) .24f else .18f),
                            topLeft = Offset(horizontalInset, bandTop),
                            size = Size(size.width - horizontalInset * 2f, band)
                        )
                        var stripeX = horizontalInset - band
                        while (stripeX < size.width - horizontalInset) {
                            drawLine(
                                color = secondary.copy(alpha = if (dark) .48f else .42f),
                                start = Offset(stripeX, bandTop + band),
                                end = Offset(stripeX + band, bandTop),
                                strokeWidth = 1.dp.toPx()
                            )
                            stripeX += band * 2f
                        }
                        drawRect(
                            color = secondary.copy(alpha = .62f),
                            topLeft = Offset(0f, band + 4.dp.toPx()),
                            size = Size(1.6.dp.toPx(), (size.height - band - 8.dp.toPx()).coerceAtLeast(0f))
                        )
                    }
                }
            }
            EchoFrameOrnament.VoyageCompass -> {
                // 夜航：顶缘黄铜航向线与中心菱标 + 四角黄铜铆钉 + 用户气泡左日志竖条
                val y = (if (compact) 2.2.dp else 3.dp).toPx()
                val inset = (cornerRadius.toPx() * .8f).coerceAtMost(size.width * .2f)
                val end = size.width - inset
                val brass = (if (dark) primary else secondary).copy(alpha = if (dark) .46f else .42f)
                if (end > inset + 12.dp.toPx() && size.height > y * 3f) {
                    val mid = (inset + end) * .5f
                    drawLine(brass, Offset(inset, y), Offset(mid - 4.dp.toPx(), y), .7.dp.toPx())
                    drawLine(brass, Offset(mid + 4.dp.toPx(), y), Offset(end, y), .7.dp.toPx())
                    val d = 2.dp.toPx()
                    val diamond = Path().apply {
                        moveTo(mid, y - d); lineTo(mid + d, y); lineTo(mid, y + d); lineTo(mid - d, y); close()
                    }
                    drawPath(diamond, color = primary.copy(alpha = .7f))
                }
                if (!compact && size.width > 36.dp.toPx() && size.height > 36.dp.toPx()) {
                    val rivetOff = 5.5.dp.toPx()
                    val rivetR = 1.dp.toPx()
                    drawCircle(brass, rivetR, Offset(rivetOff, rivetOff))
                    drawCircle(brass, rivetR, Offset(size.width - rivetOff, rivetOff))
                    drawCircle(brass, rivetR, Offset(rivetOff, size.height - rivetOff))
                    drawCircle(brass, rivetR, Offset(size.width - rivetOff, size.height - rivetOff))
                }
                if (isUserBubble) {
                    val vPad = 6.dp.toPx().coerceAtMost(size.height * .25f)
                    drawRect(
                        color = secondary.copy(alpha = .72f),
                        topLeft = Offset(0f, vPad),
                        size = Size(2.dp.toPx(), (size.height - vPad * 2f).coerceAtLeast(0f))
                    )
                }
            }
            EchoFrameOrnament.SwissRule -> {
                // 墨白（瑞士版式）：顶部厚重版式线 + 右上角版号细刻 + 气泡锐利左线
                val y = (if (compact) 1.5.dp else 2.dp).toPx()
                val inset = (cornerRadius.toPx() * .6f).coerceAtLeast(3.dp.toPx())
                if (size.width > inset * 3f && size.height > y * 4f) {
                    drawRect(
                        color = primary.copy(alpha = if (dark) .62f else .70f),
                        topLeft = Offset(inset, 0f),
                        size = Size((size.width * .24f).coerceAtMost(48.dp.toPx()), if (compact) 1.6.dp.toPx() else 2.2.dp.toPx())
                    )
                    drawLine(
                        color = outline.copy(alpha = .5f),
                        start = Offset(size.width - inset - 8.dp.toPx(), y + 1.5.dp.toPx()),
                        end = Offset(size.width - inset, y + 1.5.dp.toPx()),
                        strokeWidth = .7.dp.toPx()
                    )
                }
                if (isUserBubble) {
                    drawRect(
                        color = primary.copy(alpha = .85f),
                        topLeft = Offset(0f, 3.dp.toPx()),
                        size = Size(2.dp.toPx(), (size.height - 6.dp.toPx()).coerceAtLeast(0f))
                    )
                }
            }
            EchoFrameOrnament.DeckleSpine -> {
                // 毛边文库：左侧书脊折痕双线 + 线装针脚横纹
                val spineX = (if (compact) 4.dp else 5.5.dp).toPx()
                val vPad = (if (compact) 4.dp else 6.dp).toPx().coerceAtMost(size.height * .25f)
                if (size.width > spineX * 4f && size.height > vPad * 2.5f) {
                    drawLine(
                        color = primary.copy(alpha = if (dark) .36f else .30f),
                        start = Offset(spineX, vPad),
                        end = Offset(spineX, size.height - vPad),
                        strokeWidth = .8.dp.toPx()
                    )
                    if (!compact) {
                        drawLine(
                            color = secondary.copy(alpha = if (dark) .24f else .20f),
                            start = Offset(spineX + 2.5.dp.toPx(), vPad),
                            end = Offset(spineX + 2.5.dp.toPx(), size.height - vPad),
                            strokeWidth = .5.dp.toPx()
                        )
                        val span = size.height - vPad * 2f
                        if (span > 24.dp.toPx()) {
                            listOf(.22f, .5f, .78f).forEach { frac ->
                                val sy = vPad + span * frac
                                drawLine(
                                    color = primary.copy(alpha = if (dark) .42f else .34f),
                                    start = Offset(spineX - 1.5.dp.toPx(), sy),
                                    end = Offset(spineX + 1.5.dp.toPx(), sy),
                                    strokeWidth = .7.dp.toPx()
                                )
                            }
                        }
                    }
                }
            }
            EchoFrameOrnament.LetterSlip -> {
                // 棉纸诗笺：左右对称短诗笺竖框线与上下折饰 + 用户气泡左墨线
                val inset = (if (compact) 3.dp else 4.5.dp).toPx()
                val vPad = (if (compact) 6.dp else 9.dp).toPx().coerceAtMost(size.height * .30f)
                val barColor = primary.copy(alpha = if (dark) .36f else .28f)
                val cap = 2.5.dp.toPx()
                if (size.width > inset * 6f && size.height > vPad * 2.4f) {
                    drawLine(barColor, Offset(inset, vPad), Offset(inset, size.height - vPad), .75.dp.toPx())
                    drawLine(barColor, Offset(size.width - inset, vPad), Offset(size.width - inset, size.height - vPad), .75.dp.toPx())
                    drawLine(barColor, Offset(inset, vPad), Offset(inset + cap, vPad), .65.dp.toPx())
                    drawLine(barColor, Offset(size.width - inset - cap, size.height - vPad), Offset(size.width - inset, size.height - vPad), .65.dp.toPx())
                }
                if (isUserBubble && size.height > vPad * 2.4f) {
                    drawRect(
                        color = primary.copy(alpha = if (dark) .55f else .46f),
                        topLeft = Offset(0f, vPad),
                        size = Size(1.6.dp.toPx(), (size.height - vPad * 2f).coerceAtLeast(0f))
                    )
                }
            }
            EchoFrameOrnament.FolioMargin -> {
                // 拾页：左侧朱红/主色边注竖线 + 节首小菱结
                val marginX = (if (compact) 4.5.dp else 7.dp).toPx()
                val vPad = (if (compact) 4.dp else 6.dp).toPx().coerceAtMost(size.height * .25f)
                if (size.width > marginX * 4f && size.height > vPad * 2.5f) {
                    drawLine(
                        color = primary.copy(alpha = if (dark) .46f else .38f),
                        start = Offset(marginX, vPad),
                        end = Offset(marginX, size.height - vPad),
                        strokeWidth = .85.dp.toPx()
                    )
                    if (!compact) {
                        val ky = vPad + 4.dp.toPx()
                        drawCircle(primary.copy(alpha = .65f), 1.4.dp.toPx(), Offset(marginX, ky))
                    }
                }
            }
            EchoFrameOrnament.SonarReticle -> {
                // 声呐：切角对准刻度 + 右上遥测光点 + 用户气泡切角亮线
                val cut = (cornerRadius.toPx() * .85f).coerceAtMost(minOf(size.width, size.height) * .30f)
                val arm = (if (compact) 4.dp else 6.dp).toPx()
                val tickColor = primary.copy(alpha = if (dark) .75f else .65f)
                val pad = 1.8.dp.toPx()
                if (size.width > (cut + arm) * 2f && size.height > (cut + arm) * 2f) {
                    drawLine(tickColor, Offset(cut, pad), Offset(cut + arm, pad), 1.1.dp.toPx())
                    drawLine(tickColor, Offset(size.width - cut - arm, size.height - pad), Offset(size.width - cut, size.height - pad), 1.1.dp.toPx())
                    drawCircle(secondary.copy(alpha = .8f), 1.3.dp.toPx(), Offset(size.width - cut - 2.dp.toPx(), pad + 2.dp.toPx()))
                    if (isUserBubble && cut > 2.dp.toPx()) {
                        drawLine(tickColor, Offset(pad, cut), Offset(cut, pad), 1.2.dp.toPx())
                        drawCircle(primary.copy(alpha = .75f), 1.2.dp.toPx(), Offset(size.width - cut + 1.dp.toPx(), size.height - pad - 2.dp.toPx()))
                    }
                }
            }
            EchoFrameOrnament.BauhausBlock -> {
                // 构型：左上红/蓝/黄三原色比例条块
                val blockW = (if (compact) 12.dp else 18.dp).toPx().coerceAtMost(size.width * .22f)
                val blockH = (if (compact) 2.2.dp else 2.8.dp).toPx()
                if (size.width > blockW * 2.2f && size.height > blockH * 4f) {
                    drawRect(primary.copy(alpha = .92f), Offset(0f, 0f), Size(blockW, blockH))
                    drawRect(secondary.copy(alpha = .88f), Offset(blockW, 0f), Size(blockW * .45f, blockH))
                    drawRect(tertiary.copy(alpha = .90f), Offset(blockW * 1.45f, 0f), Size(blockW * .30f, blockH))
                }
            }
            EchoFrameOrnament.None -> Unit
        }
    }
}

internal fun createPaperTexture(material: EchoPaperMaterial, dark: Boolean, density: Float): Bitmap {
    val scale = density.coerceIn(.5f, 4f)
    val side = (160 * scale).roundToInt().coerceIn(128, 640)
    val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    if (material == EchoPaperMaterial.None) return bitmap
    val random = Random(when (material) {
        EchoPaperMaterial.Deckle -> 41
        EchoPaperMaterial.Cotton -> 71
        EchoPaperMaterial.Folio -> 103
        EchoPaperMaterial.Celadon -> 149
        EchoPaperMaterial.Lacquer -> 197
        EchoPaperMaterial.Newsprint -> 233
        EchoPaperMaterial.ProjectorGrain -> 263
        EchoPaperMaterial.SkyMist -> 29
        EchoPaperMaterial.IndexStock -> 37
        EchoPaperMaterial.NauticalVellum -> 173
        EchoPaperMaterial.None -> 0
    })
    val rgb = when {
        dark && material == EchoPaperMaterial.Celadon -> 0xCFE8E0
        dark && material == EchoPaperMaterial.Lacquer -> 0xE8C88A
        dark && material == EchoPaperMaterial.Newsprint -> 0xD8CDBB
        dark && material == EchoPaperMaterial.ProjectorGrain -> 0xD9CFC0
        dark && material == EchoPaperMaterial.SkyMist -> 0xD7E8F2
        dark && material == EchoPaperMaterial.IndexStock -> 0xE2D2BE
        dark && material == EchoPaperMaterial.NauticalVellum -> 0xD8DEC9
        dark -> 0xE6DEC9
        material == EchoPaperMaterial.Deckle -> 0x745E3F
        material == EchoPaperMaterial.Folio -> 0x75644F
        material == EchoPaperMaterial.Celadon -> 0x4E726B
        material == EchoPaperMaterial.Lacquer -> 0x7A5736
        material == EchoPaperMaterial.Newsprint -> 0x6E6250
        material == EchoPaperMaterial.ProjectorGrain -> 0x505660
        material == EchoPaperMaterial.SkyMist -> 0x66869C
        material == EchoPaperMaterial.IndexStock -> 0x80644D
        material == EchoPaperMaterial.NauticalVellum -> 0x526A58
        else -> 0x647477
    }
    val maximumAlpha = if (dark) {
        when (material) {
            EchoPaperMaterial.Celadon -> 7
            EchoPaperMaterial.Lacquer -> 8
            EchoPaperMaterial.Newsprint -> 7
            EchoPaperMaterial.ProjectorGrain -> 9
            EchoPaperMaterial.SkyMist -> 12
            EchoPaperMaterial.IndexStock -> 7
            EchoPaperMaterial.NauticalVellum -> 9
            else -> 9
        }
    } else when (material) {
        EchoPaperMaterial.Deckle -> 20
        EchoPaperMaterial.Folio -> 12
        EchoPaperMaterial.Celadon -> 10
        EchoPaperMaterial.Lacquer -> 11
        EchoPaperMaterial.Newsprint -> 16
        EchoPaperMaterial.ProjectorGrain -> 14
        EchoPaperMaterial.SkyMist -> 12
        EchoPaperMaterial.IndexStock -> 12
        EchoPaperMaterial.NauticalVellum -> 13
        else -> 15
    }
    val pixels = IntArray(side * side) { (random.nextInt(maximumAlpha + 1) shl 24) or rgb }
    bitmap.setPixels(pixels, 0, side, 0, 0, side, side)
    val canvas = Canvas(bitmap)
    when (material) {
        EchoPaperMaterial.SkyMist -> {
            val mistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = rgb or ((if (dark) 6 else 8) shl 24)
                strokeWidth = (.3f * scale).coerceAtLeast(.42f)
                strokeCap = Paint.Cap.ROUND
            }
            repeat(22) {
                val x = random.nextFloat() * side
                val y = random.nextFloat() * side
                val length = (16f + random.nextFloat() * 44f) * scale
                canvas.drawLine(x, y, x + length, y + (random.nextFloat() - .5f) * .35f * scale, mistPaint)
            }
        }
        EchoPaperMaterial.IndexStock -> {
            val laidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = rgb or ((if (dark) 9 else 13) shl 24)
                strokeWidth = (.3f * scale).coerceAtLeast(.42f)
            }
            var y = 4f * scale
            while (y < side) {
                canvas.drawLine(0f, y, side.toFloat(), y, laidPaint)
                y += 5f * scale
            }
        }
        EchoPaperMaterial.Newsprint -> {
            // 新闻纸：细密短纤维与轻微油墨颗粒
            val fiberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = rgb or ((if (dark) 11 else 18) shl 24)
                strokeWidth = (.3f * scale).coerceAtLeast(.42f)
                strokeCap = Paint.Cap.ROUND
            }
            repeat(190) {
                val x = random.nextFloat() * side
                val y = random.nextFloat() * side
                val length = (1f + random.nextFloat() * 3.2f) * scale
                canvas.drawLine(x, y, x + length, y + (random.nextFloat() - .5f) * .8f * scale, fiberPaint)
            }
            val fleckPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = rgb or ((if (dark) 9 else 15) shl 24)
                style = Paint.Style.FILL
            }
            repeat(24) {
                canvas.drawCircle(random.nextFloat() * side, random.nextFloat() * side, (.4f + random.nextFloat() * .7f) * scale, fleckPaint)
            }
        }
        EchoPaperMaterial.ProjectorGrain -> {
            // 放映室：细胶片颗粒 + 极少量竖向片痕
            val grainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = rgb or ((if (dark) 12 else 16) shl 24)
                style = Paint.Style.FILL
            }
            repeat(170) {
                canvas.drawCircle(
                    random.nextFloat() * side,
                    random.nextFloat() * side,
                    (.35f + random.nextFloat() * .65f) * scale,
                    grainPaint
                )
            }
            val scratchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = rgb or ((if (dark) 8 else 11) shl 24)
                strokeWidth = (.35f * scale).coerceAtLeast(.45f)
                strokeCap = Paint.Cap.ROUND
            }
            repeat(7) {
                val x = random.nextFloat() * side
                val y = random.nextFloat() * side
                val length = (10f + random.nextFloat() * 26f) * scale
                canvas.drawLine(x, y, x + (random.nextFloat() - .5f) * 1.2f * scale, y + length, scratchPaint)
            }
        }
        EchoPaperMaterial.Celadon -> {
            // 瓷釉微矿粒与极淡窑变气孔点
            val speckPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = rgb or ((if (dark) 12 else 18) shl 24)
                style = Paint.Style.FILL
            }
            repeat(55) {
                val x = random.nextFloat() * side
                val y = random.nextFloat() * side
                val r = (.45f + random.nextFloat() * 1.1f) * scale
                canvas.drawCircle(x, y, r, speckPaint)
            }
        }
        EchoPaperMaterial.Lacquer -> {
            // 丝缎大漆细密横刷漆纹
            val brushPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = rgb or ((if (dark) 10 else 14) shl 24)
                strokeWidth = (.35f * scale).coerceAtLeast(.45f)
                strokeCap = Paint.Cap.ROUND
            }
            repeat(110) {
                val x = random.nextFloat() * side
                val y = random.nextFloat() * side
                val length = (8f + random.nextFloat() * 22f) * scale
                canvas.drawLine(x, y, x + length, y + (random.nextFloat() - .5f) * .45f * scale, brushPaint)
            }
        }
        EchoPaperMaterial.NauticalVellum -> {
            // 航海羊皮纸：交叉细织经纬纹与微弱罗盘纸颗粒
            val weavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = rgb or ((if (dark) 9 else 14) shl 24)
                strokeWidth = (.32f * scale).coerceAtLeast(.45f)
                strokeCap = Paint.Cap.ROUND
            }
            repeat(90) { index ->
                val x = random.nextFloat() * side
                val y = random.nextFloat() * side
                val length = (5f + random.nextFloat() * 14f) * scale
                if (index % 2 == 0) {
                    canvas.drawLine(x, y, x + length, y + (random.nextFloat() - .5f) * .6f * scale, weavePaint)
                } else {
                    canvas.drawLine(x, y, x + (random.nextFloat() - .5f) * .6f * scale, y + length, weavePaint)
                }
            }
        }
        else -> {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = rgb or ((if (dark) 13 else 23) shl 24)
                strokeWidth = (.32f * scale).coerceAtLeast(.45f)
                strokeCap = Paint.Cap.ROUND
            }
            val cotton = material == EchoPaperMaterial.Cotton
            repeat(if (cotton) 65 else if (material == EchoPaperMaterial.Folio) 85 else 135) {
                val x = random.nextFloat() * side
                val y = random.nextFloat() * side
                val length = (if (cotton || material == EchoPaperMaterial.Folio) 3f + random.nextFloat() * 8f else 1f + random.nextFloat() * 3f) * scale
                canvas.drawLine(x, y, x + length, y + (random.nextFloat() - .5f) * 2 * scale, paint)
            }
        }
    }
    return bitmap
}
