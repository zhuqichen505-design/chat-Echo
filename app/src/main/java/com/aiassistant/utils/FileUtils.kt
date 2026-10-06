package com.aiassistant.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Base64
import android.webkit.MimeTypeMap
import com.aiassistant.domain.model.Attachment
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.coroutines.resume

object FileUtils {

    // 获取文件名
    fun getFileName(context: Context, uri: Uri): String {
        var fileName = "unknown"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                fileName = cursor.getString(nameIndex)
            }
        }
        return fileName
    }

    // 获取文件大小
    fun getFileSize(context: Context, uri: Uri): Long {
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst() && sizeIndex >= 0) {
                size = cursor.getLong(sizeIndex)
            }
        }
        return size
    }

    // 获取MIME类型
    fun getMimeType(context: Context, uri: Uri): String {
        return getMimeType(context, uri, getFileName(context, uri))
    }

    fun getMimeType(context: Context, uri: Uri, fileName: String): String {
        val resolverType = context.contentResolver.getType(uri)
        if (!resolverType.isNullOrBlank() && resolverType != "application/octet-stream") {
            return resolverType
        }

        val extension = fileName.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: resolverType
            ?: "application/octet-stream"
    }

    // 检查是否是图片
    fun isImage(mimeType: String): Boolean {
        return mimeType.startsWith("image/")
    }

    fun isImage(mimeType: String, fileName: String): Boolean {
        val imageExtensions = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".heic", ".heif")
        return isImage(mimeType) || imageExtensions.any { fileName.endsWith(it, ignoreCase = true) }
    }

    // 检查是否是文本文件
    fun isTextFile(mimeType: String, fileName: String): Boolean {
        val textMimeTypes = listOf(
            "text/",
            "application/json",
            "application/xml",
            "application/javascript",
            "application/x-yaml",
            "application/yaml",
            "application/csv"
        )
        val textExtensions = listOf(
            ".txt", ".md", ".json", ".xml", ".csv", ".yaml", ".yml",
            ".js", ".ts", ".py", ".java", ".kt", ".swift", ".c", ".cpp",
            ".h", ".hpp", ".cs", ".go", ".rs", ".rb", ".php", ".html",
            ".css", ".sql", ".sh", ".bat", ".ps1", ".toml", ".ini",
            ".cfg", ".conf", ".log", ".env", ".gitignore", ".dockerignore"
        )
        return textMimeTypes.any { mimeType.startsWith(it) } ||
               textExtensions.any { fileName.endsWith(it, ignoreCase = true) }
    }

    // 检查是否是PDF
    fun isPdf(mimeType: String): Boolean {
        return mimeType == "application/pdf"
    }

    // 检查是否是文档
    fun isDocument(mimeType: String): Boolean {
        return mimeType.startsWith("application/msword") ||
               mimeType.startsWith("application/vnd.openxmlformats") ||
               mimeType.startsWith("application/vnd.ms-")
    }

    suspend fun prepareAttachment(
        context: Context,
        uri: Uri,
        modelName: String,
        forceOcr: Boolean = false,
        supportsImageInputOverride: Boolean? = null
    ): Attachment? = withContext(Dispatchers.IO) {
        try {
            val name = getFileName(context, uri)
            val mimeType = getMimeType(context, uri, name)
            val size = getFileSize(context, uri)
            require(size <= BoundedInput.MAX_FILE_BYTES) { "单个附件不能超过 16 MB" }
            val image = isImage(mimeType, name)
            val modelSupportsImages = supportsImageInputOverride ?: supportsImageInput(modelName)

            when {
                image && (forceOcr || !modelSupportsImages) -> {
                    val ocrText = recognizeImageText(context, uri).orEmpty().trim()
                    Attachment(
                        uri = uri.toString(),
                        name = name,
                        mimeType = mimeType,
                        size = size,
                        textContent = if (ocrText.isNotBlank()) {
                            "[图片OCR识别：$name]\n$ocrText"
                        } else {
                            "[图片OCR识别：$name]\n未识别到文字。"
                        },
                        ocrText = ocrText.ifBlank { null },
                        processingNote = if (forceOcr) "已OCR" else "纯文本模型，已自动OCR"
                    )
                }
                image -> {
                    val base64 = imageToBase64(context, uri)
                    if (base64 != null) {
                        Attachment(
                            uri = uri.toString(),
                            name = name,
                            mimeType = mimeType,
                            size = size,
                            base64Data = base64,
                            processingNote = "图片已压缩",
                            payloadMimeType = "image/jpeg"
                        )
                    } else {
                        val ocrText = recognizeImageText(context, uri).orEmpty().trim()
                        Attachment(
                            uri = uri.toString(),
                            name = name,
                            mimeType = mimeType,
                            size = size,
                            textContent = if (ocrText.isNotBlank()) {
                                "[图片OCR识别：$name]\n$ocrText"
                            } else {
                                "[图片：$name]\n图片读取失败，且未识别到文字。"
                            },
                            ocrText = ocrText.ifBlank { null },
                            processingNote = "图片读取失败，已尝试OCR"
                        )
                    }
                }
                isPdf(mimeType) -> {
                    val pdfText = recognizePdfText(context, uri).orEmpty().trim()
                    Attachment(
                        uri = uri.toString(),
                        name = name,
                        mimeType = mimeType,
                        size = size,
                        base64Data = readFileAsBase64(context, uri),
                        textContent = if (pdfText.isNotBlank()) {
                            "[PDF OCR识别：$name]\n$pdfText"
                        } else {
                            getFileSummary(context, uri, mimeType, name)
                        },
                        ocrText = pdfText.ifBlank { null },
                        processingNote = if (pdfText.isNotBlank()) "PDF已OCR" else "PDF已附加"
                    )
                }
                isTextFile(mimeType, name) -> {
                    val text = readTextFile(context, uri)
                    Attachment(
                        uri = uri.toString(),
                        name = name,
                        mimeType = mimeType,
                        size = size,
                        textContent = text,
                        processingNote = if (text != null) "文本已读取" else "文本读取失败"
                    )
                }
                else -> {
                    Attachment(
                        uri = uri.toString(),
                        name = name,
                        mimeType = mimeType,
                        size = size,
                        base64Data = readFileAsBase64(context, uri),
                        textContent = getFileSummary(context, uri, mimeType, name),
                        processingNote = "已附加文件信息"
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun supportsImageInput(modelName: String): Boolean {
        val name = modelName.lowercase()
        if (name.isBlank()) return false

        // DeepSeek V4：flash 支持视觉；pro / 历史 reasoner/r1 不支持
        if (name.contains("deepseek")) {
            if (name.contains("deepseek-v4-pro") || name.contains("deepseek-reasoner") ||
                name.contains("deepseek-r1") || name == "deepseek-chat"
            ) {
                return false
            }
            return name.contains("flash") || name.contains("vision") || name.contains("vl")
        }

        val visionSignals = listOf(
            "vision",
            "vl",
            "gpt-4o",
            "gpt-4.1",
            "gpt-5",
            "gpt-6",
            "astra",
            "gemini",
            "claude-3",
            "claude-4",
            "claude-5",
            "qwen2-vl",
            "qwen-vl",
            "glm-4v",
            "llava",
            "pixtral"
        )
        return visionSignals.any { name.contains(it) }
    }

    suspend fun recognizeImageText(context: Context, uri: Uri): String? {
        return try {
            val bitmap = decodeSampled(context, uri, 2048, 2048) ?: return null
            try {
                recognizeInputImage(InputImage.fromBitmap(bitmap, 0))
            } finally {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun recognizeInputImage(image: InputImage): String? {
        return try {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            suspendCancellableCoroutine { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { text ->
                        if (continuation.isActive) {
                            continuation.resume(text.text)
                        }
                    }
                    .addOnFailureListener {
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
                    .addOnCompleteListener {
                        recognizer.close()
                    }
                continuation.invokeOnCancellation {
                    recognizer.close()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun recognizePdfText(context: Context, uri: Uri, maxPages: Int = 5): String? {
        return withContext(Dispatchers.IO) {
            var descriptor: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null
            try {
                descriptor = context.contentResolver.openFileDescriptor(uri, "r")
                    ?: return@withContext null
                renderer = PdfRenderer(descriptor)
                val pageCount = minOf(renderer.pageCount, maxPages)
                val result = StringBuilder()

                for (index in 0 until pageCount) {
                    renderer.openPage(index).use { page ->
                        val scale = minOf(2.0, 2048.0 / maxOf(page.width, page.height).coerceAtLeast(1))
                        val width = (page.width * scale).toInt().coerceAtLeast(1)
                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val pageText = recognizeInputImage(InputImage.fromBitmap(bitmap, 0)).orEmpty().trim()
                        bitmap.recycle()
                        if (pageText.isNotBlank()) {
                            result.appendLine("第${index + 1}页:")
                            result.appendLine(pageText)
                            result.appendLine()
                        }
                    }
                }

                if (result.isBlank()) null else result.toString()
            } catch (e: Exception) {
                e.printStackTrace()
                null
            } finally {
                renderer?.close()
                descriptor?.close()
            }
        }
    }

    // 将图片转换为Base64
    fun imageToBase64(context: Context, uri: Uri, maxWidth: Int = 1024, maxHeight: Int = 1024): String? {
        return try {
            val bitmap = decodeSampled(context, uri, maxWidth, maxHeight) ?: return null

            // 压缩图片
            val scaledBitmap = scaleBitmap(bitmap, maxWidth, maxHeight)

            // 转换为Base64
            val byteArrayOutputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, byteArrayOutputStream)
            val byteArray = byteArrayOutputStream.toByteArray()

            if (scaledBitmap != bitmap) {
                scaledBitmap.recycle()
            }
            bitmap.recycle()

            Base64.encodeToString(byteArray, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // 缩放图片
    private fun scaleBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxWidth && height <= maxHeight) {
            return bitmap
        }

        val ratio = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
        val newWidth = (width * ratio).toInt().coerceAtLeast(1)
        val newHeight = (height * ratio).toInt().coerceAtLeast(1)

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    // 读取文件内容（文本文件）
    fun readTextFile(context: Context, uri: Uri, maxChars: Int = 50000): String? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val content = inputStream?.bufferedReader()?.use { reader ->
                BoundedInput.text(reader, maxChars)
            }
            content
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // 读取文件为Base64（用于PDF等二进制文件）
    fun readFileAsBase64(context: Context, uri: Uri): String? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val byteArray = inputStream?.use { BoundedInput.bytes(it) }
            if (byteArray != null) {
                Base64.encodeToString(byteArray, Base64.NO_WRAP)
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun decodeSampled(context: Context, uri: Uri, maxWidth: Int, maxHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = BoundedInput.sampleSize(bounds.outWidth, bounds.outHeight, maxWidth, maxHeight)
        }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    // 格式化文件大小
    fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${String.format("%.1f", size / (1024 * 1024.0))} MB"
            else -> "${String.format("%.1f", size / (1024 * 1024 * 1024.0))} GB"
        }
    }

    // 获取文件摘要信息
    fun getFileSummary(context: Context, uri: Uri, mimeType: String, fileName: String): String {
        return when {
            isImage(mimeType) -> "图片文件: $fileName"
            isTextFile(mimeType, fileName) -> {
                val content = readTextFile(context, uri, 2000)
                if (content != null) {
                    "文本文件: $fileName\n内容预览:\n$content"
                } else {
                    "文本文件: $fileName（无法读取内容）"
                }
            }
            isPdf(mimeType) -> "PDF文件: $fileName"
            isDocument(mimeType) -> "文档文件: $fileName"
            else -> "文件: $fileName ($mimeType)"
        }
    }
}
