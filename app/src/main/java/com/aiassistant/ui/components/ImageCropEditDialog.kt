package com.aiassistant.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.luminance
import com.aiassistant.ui.theme.EchoTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

enum class CropShapeMode {
    CIRCLE,
    RECTANGLE
}

@Composable
fun ImageCropEditDialog(
    imageUri: Uri,
    shapeMode: CropShapeMode = CropShapeMode.CIRCLE,
    title: String = if (shapeMode == CropShapeMode.CIRCLE) "裁剪与编辑头像" else "裁剪与编辑壁纸",
    onDismiss: () -> Unit,
    onConfirm: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var rotationDegrees by remember { mutableFloatStateOf(0f) }
    var isFlippedH by remember { mutableStateOf(false) }
    var isFlippedV by remember { mutableStateOf(false) }

    LaunchedEffect(imageUri) {
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(imageUri)?.use { stream ->
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(stream, null, options)
                    val maxDim = max(options.outWidth, options.outHeight)
                    var sampleSize = 1
                    while (maxDim / sampleSize > 2048) {
                        sampleSize *= 2
                    }
                    context.contentResolver.openInputStream(imageUri)?.use { s2 ->
                        val loadOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                        sourceBitmap = BitmapFactory.decodeStream(s2, null, loadOptions)
                    }
                }
            } catch (_: Exception) {
                sourceBitmap = null
            }
        }
        isLoading = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = EchoTokens.Radius.shapeXl,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            tonalElevation = 0.dp,
            shadowElevation = EchoTokens.Elevation.overlay(isDark).elevation
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 顶部标题与关闭
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "取消")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 中间手势画布与裁剪框
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading || sourceBitmap == null) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    } else {
                        val bmp = sourceBitmap!!
                        val imageBitmap = remember(bmp) { bmp.asImageBitmap() }

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        scale = (scale * zoom).coerceIn(0.5f, 5.0f)
                                        offsetX += pan.x
                                        offsetY += pan.y
                                    }
                                }
                        ) {
                            val canvasWidth = size.width
                            val canvasHeight = size.height
                            val cropRadius = min(canvasWidth, canvasHeight) * 0.38f
                            val cropCenter = Offset(canvasWidth / 2f, canvasHeight / 2f)

                            // 绘制受手势变换的底层图片
                            drawImage(
                                image = imageBitmap,
                                dstOffset = androidx.compose.ui.unit.IntOffset(
                                    x = ((canvasWidth - bmp.width * scale) / 2f + offsetX).toInt(),
                                    y = ((canvasHeight - bmp.height * scale) / 2f + offsetY).toInt()
                                ),
                                dstSize = androidx.compose.ui.unit.IntSize(
                                    width = (bmp.width * scale).toInt(),
                                    height = (bmp.height * scale).toInt()
                                )
                            )

                            // 蒙版阴影层与取景框
                            val cropRect = Rect(
                                left = cropCenter.x - cropRadius,
                                top = cropCenter.y - (if (shapeMode == CropShapeMode.CIRCLE) cropRadius else cropRadius * 0.75f),
                                right = cropCenter.x + cropRadius,
                                bottom = cropCenter.y + (if (shapeMode == CropShapeMode.CIRCLE) cropRadius else cropRadius * 0.75f)
                            )

                            val path = Path().apply {
                                addRect(Rect(0f, 0f, canvasWidth, canvasHeight))
                                if (shapeMode == CropShapeMode.CIRCLE) {
                                    addOval(cropRect)
                                } else {
                                    addRoundRect(androidx.compose.ui.geometry.RoundRect(cropRect, androidx.compose.ui.geometry.CornerRadius(24f, 24f)))
                                }
                                fillType = PathFillType.EvenOdd
                            }
                            drawPath(path, color = Color.Black.copy(alpha = 0.65f))

                            // 取景框边框
                            if (shapeMode == CropShapeMode.CIRCLE) {
                                drawCircle(
                                    color = Color.White,
                                    radius = cropRadius,
                                    center = cropCenter,
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            } else {
                                drawRoundRect(
                                    color = Color.White,
                                    topLeft = cropRect.topLeft,
                                    size = cropRect.size,
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(24f, 24f),
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 缩放滑块
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ZoomOut, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = scale,
                        onValueChange = { scale = it },
                        valueRange = 0.5f..5.0f,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    Icon(Icons.Default.ZoomIn, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // 变换工具栏：旋转、水平翻转、垂直翻转、重置
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { rotationDegrees = (rotationDegrees + 90f) % 360f }
                    ) {
                        Icon(Icons.Default.RotateRight, contentDescription = "顺时针旋转90°")
                    }
                    IconButton(
                        onClick = { isFlippedH = !isFlippedH }
                    ) {
                        Icon(Icons.Default.Flip, contentDescription = "水平翻转")
                    }
                    IconButton(
                        onClick = { isFlippedV = !isFlippedV }
                    ) {
                        Icon(Icons.Default.SwapVert, contentDescription = "垂直翻转")
                    }
                    IconButton(
                        onClick = {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                            rotationDegrees = 0f
                            isFlippedH = false
                            isFlippedV = false
                        }
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "重置")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val bmp = sourceBitmap ?: return@Button
                            val matrix = Matrix().apply {
                                postScale(if (isFlippedH) -1f else 1f, if (isFlippedV) -1f else 1f)
                                postRotate(rotationDegrees)
                            }
                            val transformed = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                            val targetSize = if (shapeMode == CropShapeMode.CIRCLE) 512 else 1080
                            val scaled = Bitmap.createScaledBitmap(transformed, targetSize, targetSize, true)
                            onConfirm(scaled)
                        },
                        shape = RoundedCornerShape(999.dp),
                        enabled = sourceBitmap != null
                    ) {
                        Text("确定并应用")
                    }
                }
            }
        }
    }
}
