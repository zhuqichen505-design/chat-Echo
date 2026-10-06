package com.aiassistant.ui.components

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

private val ReadableDark = Color.Black
private val ReadableLight = Color(0xFFFFFFFF)

data class ReadableBackdropColors(
    val full: Color,
    val top: Color,
    val content: Color,
    val bottom: Color
)

fun readableTextColorFor(
    background: Color,
    fallbackSurface: Color = Color.White
): Color {
    val resolvedBackground = if (background.alpha < 1f) {
        background.compositeOver(fallbackSurface)
    } else {
        background
    }
    return if (contrastRatio(ReadableDark, resolvedBackground) >= contrastRatio(ReadableLight, resolvedBackground)) {
        ReadableDark
    } else {
        ReadableLight
    }
}

fun readableTextColorFor(
    background: Color,
    darkColor: Color,
    lightColor: Color,
    fallbackSurface: Color = Color.White
): Color {
    val resolvedBackground = if (background.alpha < 1f) {
        background.compositeOver(fallbackSurface)
    } else {
        background
    }
    return if (contrastRatio(darkColor, resolvedBackground) >= contrastRatio(lightColor, resolvedBackground)) {
        darkColor
    } else {
        lightColor
    }
}

@Composable
fun rememberReadableBackdropColor(bitmap: Bitmap?): Color {
    val fallback = MaterialTheme.colorScheme.background
    return remember(bitmap, fallback) {
        sampledBackgroundColor(bitmap, fallback)
    }
}

@Composable
fun rememberReadableBackdropColors(bitmap: Bitmap?): ReadableBackdropColors {
    val fallback = MaterialTheme.colorScheme.background
    return remember(bitmap, fallback) {
        ReadableBackdropColors(
            full = sampledBackgroundColor(bitmap, fallback),
            top = sampledBackgroundColor(bitmap, fallback, verticalStart = 0f, verticalEnd = 0.24f),
            content = sampledBackgroundColor(bitmap, fallback, verticalStart = 0.20f, verticalEnd = 0.78f),
            bottom = sampledBackgroundColor(bitmap, fallback, verticalStart = 0.72f, verticalEnd = 1f)
        )
    }
}

fun sampledBackgroundColor(
    bitmap: Bitmap?,
    fallback: Color,
    verticalStart: Float = 0f,
    verticalEnd: Float = 1f
): Color {
    if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) {
        return fallback
    }

    // v2.6.6：先钳制 verticalStart 到 0..1，避免调用方传入 >1f 时下方 coerceIn(start, 1f)
    // 构成空区间抛 IllegalArgumentException；startY 已被钳到 height-1，startY+1 <= height 恒成立
    val clampedVerticalStart = verticalStart.coerceIn(0f, 1f)
    val startY = (bitmap.height * clampedVerticalStart)
        .toInt()
        .coerceIn(0, bitmap.height - 1)
    val endY = (bitmap.height * verticalEnd.coerceIn(clampedVerticalStart, 1f))
        .toInt()
        .coerceIn(startY + 1, bitmap.height)
    val sampleHeight = (endY - startY).coerceAtLeast(1)
    val xStep = maxOf(1, bitmap.width / 24)
    val yStep = maxOf(1, sampleHeight / 16)
    var red = 0f
    var green = 0f
    var blue = 0f
    var weightTotal = 0f

    var y = startY + yStep / 2
    while (y < endY) {
        var x = xStep / 2
        while (x < bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            val alpha = AndroidColor.alpha(pixel) / 255f
            if (alpha > 0f) {
                red += AndroidColor.red(pixel) * alpha
                green += AndroidColor.green(pixel) * alpha
                blue += AndroidColor.blue(pixel) * alpha
                weightTotal += alpha
            }
            x += xStep
        }
        y += yStep
    }

    if (weightTotal <= 0f) {
        return fallback
    }

    return Color(
        red = (red / weightTotal / 255f).coerceIn(0f, 1f),
        green = (green / weightTotal / 255f).coerceIn(0f, 1f),
        blue = (blue / weightTotal / 255f).coerceIn(0f, 1f),
        alpha = 1f
    )
}

private fun contrastRatio(foreground: Color, background: Color): Float {
    val lighter = maxOf(foreground.luminance(), background.luminance())
    val darker = minOf(foreground.luminance(), background.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}
