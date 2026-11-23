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
    None, Deckle, Cotton, Folio, Celadon, Lacquer, Newsprint, ProjectorGrain
}

private data class PaperTextureKey(val material: EchoPaperMaterial, val dark: Boolean, val density: Int)
private val paperBrushCache = object : LruCache<PaperTextureKey, ShaderBrush>(12) {
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
    return drawBehind {
        brush?.let { drawRect(it) }
        if (material == EchoPaperMaterial.Folio && pageRuling) {
            var y = 28.dp.toPx()
            while (y < size.height) {
                drawLine(folioRule, Offset(0f, y), Offset(size.width, y), .5.dp.toPx())
                y += 28.dp.toPx()
            }
            val margin = 20.dp.toPx()
            drawLine(folioRule.copy(alpha = folioRule.alpha * 1.7f), Offset(margin, 0f), Offset(margin, size.height), .7.dp.toPx())
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
            // 精密制图坐标网格 + 十字准星 + 页边标尺刻度
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
            }
        }
        if (style.surfacePattern == EchoSurfacePattern.NewsRegister && pageRuling) {
            // 报纸印刷对准标记：四角细 L 标与套准圆点，只出现在整页背景
            val inset = 10.dp.toPx()
            val arm = 8.dp.toPx()
            val stroke = .7.dp.toPx()
            drawLine(registerMark, Offset(inset, inset), Offset(inset + arm, inset), stroke)
            drawLine(registerMark, Offset(inset, inset), Offset(inset, inset + arm), stroke)
            drawLine(registerMark, Offset(size.width - inset - arm, inset), Offset(size.width - inset, inset), stroke)
            drawLine(registerMark, Offset(size.width - inset, inset), Offset(size.width - inset, inset + arm), stroke)
            drawLine(registerMark, Offset(inset, size.height - inset), Offset(inset + arm, size.height - inset), stroke)
            drawLine(registerMark, Offset(inset, size.height - inset - arm), Offset(inset, size.height - inset), stroke)
            drawLine(registerMark, Offset(size.width - inset - arm, size.height - inset), Offset(size.width - inset, size.height - inset), stroke)
            drawLine(registerMark, Offset(size.width - inset, size.height - inset - arm), Offset(size.width - inset, size.height - inset), stroke)
            drawCircle(registerAccent, radius = 1.6.dp.toPx(), center = Offset(size.width - inset - 3.dp.toPx(), inset + 3.dp.toPx()))
            drawCircle(registerAccent, radius = 1.6.dp.toPx(), center = Offset(inset + 3.dp.toPx(), size.height - inset - 3.dp.toPx()))
        }
        if (style.surfacePattern == EchoSurfacePattern.FilmScanline && pageRuling) {
            // 胶片细扫描线与左右片边刻度，只覆盖整页背景
            var scanY = 0f
            while (scanY < size.height) {
                drawLine(scanline, Offset(0f, scanY), Offset(size.width, scanY), 1.dp.toPx())
                scanY += 3.dp.toPx()
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
            // 漆匣双线描金页框：左右典雅细边框，营造卷轴/大漆书匣仪式感
            val outerInset = 8.dp.toPx()
            val innerInset = 11.5.dp.toPx()
            val stroke = .65.dp.toPx()
            drawLine(lacquerRuleOuter, Offset(outerInset, 0f), Offset(outerInset, size.height), stroke)
            drawLine(lacquerRuleInner, Offset(innerInset, 0f), Offset(innerInset, size.height), stroke * 0.8f)
            drawLine(lacquerRuleOuter, Offset(size.width - outerInset, 0f), Offset(size.width - outerInset, size.height), stroke)
            drawLine(lacquerRuleInner, Offset(size.width - innerInset, 0f), Offset(size.width - innerInset, size.height), stroke * 0.8f)
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
    val outline = colors.outline
    return drawWithContent {
        drawContent()
        when (ornament) {
            EchoFrameOrnament.CeramicRim -> {
                // 青瓷玉釉内高光 + 极细内缩唇边
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
                if (size.width > inset * 4f && size.height > inset * 4f) {
                    drawRoundRect(
                        color = primary.copy(alpha = rimAlpha),
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2f, size.height - inset * 2f),
                        cornerRadius = CornerRadius(r, r),
                        style = Stroke(width = 0.6.dp.toPx())
                    )
                }
            }
            EchoFrameOrnament.BlueprintBrackets -> {
                // 蓝图四角 L 形精密对准角标 + 用户气泡左侧索引竖条
                val arm = (if (compact) 5.dp else 7.dp).toPx().coerceAtMost(minOf(size.width, size.height) * 0.32f)
                val stroke = 1.3.dp.toPx()
                val bracketColor = primary.copy(alpha = if (dark) 0.80f else 0.72f)
                val pad = stroke * 0.5f
                val w = size.width
                val h = size.height
                // 左上
                drawLine(bracketColor, Offset(pad, pad), Offset(pad + arm, pad), stroke)
                drawLine(bracketColor, Offset(pad, pad), Offset(pad, pad + arm), stroke)
                // 右上
                drawLine(bracketColor, Offset(w - pad - arm, pad), Offset(w - pad, pad), stroke)
                drawLine(bracketColor, Offset(w - pad, pad), Offset(w - pad, pad + arm), stroke)
                // 左下
                drawLine(bracketColor, Offset(pad, h - pad), Offset(pad + arm, h - pad), stroke)
                drawLine(bracketColor, Offset(pad, h - pad - arm), Offset(pad, h - pad), stroke)
                // 右下
                drawLine(bracketColor, Offset(w - pad - arm, h - pad), Offset(w - pad, h - pad), stroke)
                drawLine(bracketColor, Offset(w - pad, h - pad - arm), Offset(w - pad, h - pad), stroke)
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
                // 漆匣描金内缩双框 + 四角细金点 + 用户气泡右侧印章竖边
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
                        drawCircle(pinColor, pinR, Offset(pinOffset, pinOffset))
                        drawCircle(pinColor, pinR, Offset(size.width - pinOffset, pinOffset))
                        drawCircle(pinColor, pinR, Offset(pinOffset, size.height - pinOffset))
                        drawCircle(pinColor, pinR, Offset(size.width - pinOffset, size.height - pinOffset))
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
                // 放映室：卡片上缘齿孔描边；用户气泡叠入场记板斜纹与银幕左线
                val holeWidth = (if (compact) 5.dp else 7.dp).toPx()
                val holeHeight = (if (compact) 2.2.dp else 2.8.dp).toPx()
                val holeGap = (if (compact) 4.dp else 6.dp).toPx()
                val holeInset = 4.dp.toPx()
                val holeColor = secondary.copy(alpha = if (dark) .30f else .26f)
                if (!isUserBubble) {
                    var holeX = holeInset + 2.dp.toPx()
                    while (holeX + holeWidth <= size.width - holeInset) {
                        drawRoundRect(
                            color = holeColor,
                            topLeft = Offset(holeX, holeInset),
                            size = Size(holeWidth, holeHeight),
                            cornerRadius = CornerRadius(holeHeight / 2f, holeHeight / 2f),
                            style = Stroke(width = .65.dp.toPx())
                        )
                        holeX += holeWidth + holeGap
                    }
                }
                if (isUserBubble) {
                    val band = 5.dp.toPx()
                    val horizontalInset = 7.dp.toPx()
                    if (size.width > horizontalInset * 2f && size.height > band * 2f) {
                        val bandTop = 2.dp.toPx()
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
                                strokeWidth = 1.1.dp.toPx()
                            )
                            stripeX += band * 2f
                        }
                        drawRect(
                            color = secondary.copy(alpha = .62f),
                            topLeft = Offset(0f, band + 5.dp.toPx()),
                            size = Size(1.6.dp.toPx(), (size.height - band - 10.dp.toPx()).coerceAtLeast(0f))
                        )
                    }
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
        EchoPaperMaterial.None -> 0
    })
    val rgb = when {
        dark && material == EchoPaperMaterial.Celadon -> 0xCFE8E0
        dark && material == EchoPaperMaterial.Lacquer -> 0xE8C88A
        dark && material == EchoPaperMaterial.Newsprint -> 0xD8CDBB
        dark && material == EchoPaperMaterial.ProjectorGrain -> 0xD9CFC0
        dark -> 0xE6DEC9
        material == EchoPaperMaterial.Deckle -> 0x745E3F
        material == EchoPaperMaterial.Folio -> 0x75644F
        material == EchoPaperMaterial.Celadon -> 0x4E726B
        material == EchoPaperMaterial.Lacquer -> 0x7A5736
        material == EchoPaperMaterial.Newsprint -> 0x6E6250
        material == EchoPaperMaterial.ProjectorGrain -> 0x6B5F4C
        else -> 0x647477
    }
    val maximumAlpha = if (dark) {
        when (material) {
            EchoPaperMaterial.Celadon -> 7
            EchoPaperMaterial.Lacquer -> 8
            EchoPaperMaterial.Newsprint -> 7
            EchoPaperMaterial.ProjectorGrain -> 9
            else -> 9
        }
    } else when (material) {
        EchoPaperMaterial.Deckle -> 20
        EchoPaperMaterial.Folio -> 12
        EchoPaperMaterial.Celadon -> 10
        EchoPaperMaterial.Lacquer -> 11
        EchoPaperMaterial.Newsprint -> 16
        EchoPaperMaterial.ProjectorGrain -> 14
        else -> 15
    }
    val pixels = IntArray(side * side) { (random.nextInt(maximumAlpha + 1) shl 24) or rgb }
    bitmap.setPixels(pixels, 0, side, 0, 0, side, side)
    val canvas = Canvas(bitmap)
    when (material) {
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
