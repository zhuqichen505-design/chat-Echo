package com.aiassistant.utils

import com.aiassistant.data.repository.helpers.TokenEstimationHelper
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.sqrt

/**
 * M2 可选语义向量引擎：
 * - Tavo 式 embeddingSpaceId 空间指纹校验（严禁不同模型/维度的向量混算）；
 * - RisuAI 式 Token 感知分批器（防止超过上游单次 batch Token 限制）；
 * - IEEE-754 float32 二进制紧凑编解码与安全余弦相似度；
 * - 无量纲 RRF（k=60）倒数排名融合。
 */
object SemanticEmbeddingEngine {

    const val TARGET_MEMORY = "memory"
    const val TARGET_SUMMARY_CHUNK = "summary_chunk"
    const val TARGET_TIMELINE = "timeline"

    const val DEFAULT_RRF_K = 60
    const val MAX_BATCH_ITEMS = 16
    const val MAX_BATCH_TOKENS = 6_000

    /** 生成当前向量空间唯一指纹（同服务地址 + 同模型 + 同维度）。 */
    fun computeSpaceId(baseUrl: String, model: String, dimension: Int): String {
        val normalizedUrl = baseUrl.trim().trimEnd('/').lowercase(Locale.ROOT)
        val normalizedModel = model.trim().lowercase(Locale.ROOT)
        return sha256Hex("emb-space|$normalizedUrl|$normalizedModel|dim=$dimension").take(32)
    }

    /** 未拿到维度前的配置前缀指纹，用于快速发现模型切换。 */
    fun computeConfigFingerprint(baseUrl: String, model: String): String {
        val normalizedUrl = baseUrl.trim().trimEnd('/').lowercase(Locale.ROOT)
        val normalizedModel = model.trim().lowercase(Locale.ROOT)
        return sha256Hex("emb-cfg|$normalizedUrl|$normalizedModel").take(24)
    }

    fun computeContentHash(text: String): String = sha256Hex(text.trim())

    fun encodeVector(values: List<Float>): ByteArray {
        val buffer = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (v in values) buffer.putFloat(v)
        return buffer.array()
    }

    fun decodeVector(bytes: ByteArray): FloatArray {
        if (bytes.isEmpty() || bytes.size % 4 != 0) return FloatArray(0)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val count = bytes.size / 4
        return FloatArray(count) { buffer.float }
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || a.size != b.size) return 0f
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            val av = a[i].toDouble()
            val bv = b[i].toDouble()
            dot += av * bv
            normA += av * av
            normB += bv * bv
        }
        val denom = sqrt(normA) * sqrt(normB)
        if (denom <= 1e-12) return 0f
        return (dot / denom).toFloat().coerceIn(-1f, 1f)
    }

    /**
     * 按条数 + 累计 Token 双限分批，避免触发 OpenAI / Voyage / 硅基流动单批 Token 上限报错。
     */
    fun <T> batchByTokenBudget(
        items: List<T>,
        textSelector: (T) -> String,
        maxItems: Int = MAX_BATCH_ITEMS,
        maxTokens: Int = MAX_BATCH_TOKENS
    ): List<List<T>> {
        if (items.isEmpty()) return emptyList()
        val batches = mutableListOf<List<T>>()
        var current = mutableListOf<T>()
        var tokens = 0
        for (item in items) {
            val cost = TokenEstimationHelper.estimateTokenCount(textSelector(item)).coerceAtLeast(1)
            if (current.isNotEmpty() && (current.size >= maxItems || tokens + cost > maxTokens)) {
                batches += current
                current = mutableListOf()
                tokens = 0
            }
            current += item
            tokens += cost
        }
        if (current.isNotEmpty()) batches += current
        return batches
    }

    /**
     * 无量纲 RRF（Reciprocal Rank Fusion）融合 BM25 词面排名与向量语义排名。
     */
    fun fuseRrf(
        bm25RankedIds: List<Long>,
        vectorRankedIds: List<Long>,
        k: Int = DEFAULT_RRF_K,
        bm25Weight: Float = 0.55f,
        vectorWeight: Float = 0.45f
    ): Map<Long, Float> {
        val scores = LinkedHashMap<Long, Float>()
        bm25RankedIds.forEachIndexed { idx, id ->
            scores[id] = (scores[id] ?: 0f) + bm25Weight / (k + idx + 1).toFloat()
        }
        vectorRankedIds.forEachIndexed { idx, id ->
            scores[id] = (scores[id] ?: 0f) + vectorWeight / (k + idx + 1).toFloat()
        }
        return scores
    }

    private fun sha256Hex(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
