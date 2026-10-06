package com.aiassistant.utils

import java.io.InputStream
import java.io.Reader

object BoundedInput {
    const val MAX_FILE_BYTES = 16 * 1024 * 1024
    const val MAX_TOTAL_CHARS = 32 * 1024 * 1024

    fun bytes(input: InputStream, limit: Int = MAX_FILE_BYTES): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer, 0, minOf(buffer.size, limit - total + 1))
            if (count < 0) break
            total += count
            require(total <= limit) { "附件超过 ${limit / 1024 / 1024} MB 限制" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    fun text(reader: Reader, limit: Int): String {
        require(limit >= 0)
        val result = StringBuilder()
        val buffer = CharArray(4096)
        while (result.length <= limit) {
            val count = reader.read(buffer, 0, minOf(buffer.size, limit - result.length + 1))
            if (count < 0) break
            result.append(buffer, 0, count)
        }
        return if (result.length > limit) result.substring(0, limit) + "\n[内容已截断，完整内容过长]" else result.toString()
    }

    fun sampleSize(width: Int, height: Int, maxWidth: Int, maxHeight: Int): Int {
        require(width > 0 && height > 0 && maxWidth > 0 && maxHeight > 0)
        var sample = 1
        while (width / sample > maxWidth * 2L || height / sample > maxHeight * 2L) sample *= 2
        return sample
    }
}
