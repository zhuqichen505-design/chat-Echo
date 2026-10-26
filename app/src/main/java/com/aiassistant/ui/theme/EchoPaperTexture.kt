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
import kotlin.math.roundToInt
import kotlin.random.Random

enum class EchoPaperMaterial { None, Deckle, Cotton }

private data class PaperTextureKey(val material: EchoPaperMaterial, val dark: Boolean, val density: Int)
private val paperBrushCache = object : LruCache<PaperTextureKey, ShaderBrush>(8) {
    override fun create(key: PaperTextureKey): ShaderBrush = ShaderBrush(ImageShader(
        createPaperTexture(key.material, key.dark, key.density / 100f).asImageBitmap(),
        TileMode.Repeated, TileMode.Repeated
    ))
}

/** Fixed-seed translucent paper grain. Cached shaders tile behind content without touching input. */
@Composable
fun Modifier.echoPaperTexture(enabled: Boolean = true): Modifier {
    val material = LocalEchoThemeStyle.current.paperMaterial
    if (!enabled || material == EchoPaperMaterial.None) return this
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val density = (LocalDensity.current.density * 100).roundToInt().coerceIn(50, 400)
    val brush = remember(material, dark, density) { requireNotNull(paperBrushCache.get(PaperTextureKey(material, dark, density))) }
    return drawBehind { drawRect(brush) }
}

internal fun createPaperTexture(material: EchoPaperMaterial, dark: Boolean, density: Float): Bitmap {
    val scale = density.coerceIn(.5f, 4f)
    val side = (160 * scale).roundToInt().coerceIn(128, 640)
    val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    if (material == EchoPaperMaterial.None) return bitmap
    val random = Random(if (material == EchoPaperMaterial.Deckle) 41 else 71)
    val rgb = when {
        dark -> 0xE6DEC9
        material == EchoPaperMaterial.Deckle -> 0x745E3F
        else -> 0x647477
    }
    val maximumAlpha = if (dark) 9 else if (material == EchoPaperMaterial.Deckle) 20 else 15
    val pixels = IntArray(side * side) { (random.nextInt(maximumAlpha + 1) shl 24) or rgb }
    bitmap.setPixels(pixels, 0, side, 0, 0, side, side)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = rgb or ((if (dark) 13 else 23) shl 24)
        strokeWidth = (.32f * scale).coerceAtLeast(.45f)
        strokeCap = Paint.Cap.ROUND
    }
    val cotton = material == EchoPaperMaterial.Cotton
    repeat(if (cotton) 65 else 135) {
        val x = random.nextFloat() * side
        val y = random.nextFloat() * side
        val length = (if (cotton) 3f + random.nextFloat() * 8f else 1f + random.nextFloat() * 3f) * scale
        canvas.drawLine(x, y, x + length, y + (random.nextFloat() - .5f) * 2 * scale, paint)
    }
    return bitmap
}
