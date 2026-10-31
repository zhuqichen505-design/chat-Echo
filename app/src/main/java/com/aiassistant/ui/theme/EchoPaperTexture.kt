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
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlin.random.Random

enum class EchoPaperMaterial { None, Deckle, Cotton, Folio }

private data class PaperTextureKey(val material: EchoPaperMaterial, val dark: Boolean, val density: Int)
private val paperBrushCache = object : LruCache<PaperTextureKey, ShaderBrush>(8) {
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
    return drawBehind {
        brush?.let { drawRect(it) }
        if (material == EchoPaperMaterial.Folio && pageRuling) {
            var y = 28.dp.toPx()
            while (y < size.height) {
                drawLine(folioRule, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), .5.dp.toPx())
                y += 28.dp.toPx()
            }
            val margin = 20.dp.toPx()
            drawLine(folioRule.copy(alpha = folioRule.alpha * 1.7f), androidx.compose.ui.geometry.Offset(margin, 0f), androidx.compose.ui.geometry.Offset(margin, size.height), .7.dp.toPx())
        }
        if (style.surfacePattern == EchoSurfacePattern.SonarGrid) {
            val step = 24.dp.toPx()
            var x = 0f
            var column = 0
            while (x <= size.width) {
                drawLine(if (column % 5 == 0) majorGrid else minorGrid, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), .5.dp.toPx())
                x += step
                column++
            }
            var y = 0f
            var row = 0
            while (y <= size.height) {
                drawLine(if (row % 5 == 0) majorGrid else minorGrid, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), .5.dp.toPx())
                y += step
                row++
            }
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
        EchoPaperMaterial.None -> 0
    })
    val rgb = when {
        dark -> 0xE6DEC9
        material == EchoPaperMaterial.Deckle -> 0x745E3F
        material == EchoPaperMaterial.Folio -> 0x75644F
        else -> 0x647477
    }
    val maximumAlpha = if (dark) 9 else when (material) {
        EchoPaperMaterial.Deckle -> 20
        EchoPaperMaterial.Folio -> 12
        else -> 15
    }
    val pixels = IntArray(side * side) { (random.nextInt(maximumAlpha + 1) shl 24) or rgb }
    bitmap.setPixels(pixels, 0, side, 0, 0, side, side)
    val canvas = Canvas(bitmap)
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
    return bitmap
}
