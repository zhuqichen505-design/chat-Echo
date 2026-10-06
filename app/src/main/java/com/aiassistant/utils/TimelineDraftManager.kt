package com.aiassistant.utils

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.util.UUID

/**
 * 时间线梳理草稿模型（支持断点续梳与实时持久化）
 */
data class TimelineReconcileDraft(
    val conversationId: Long,
    val lastProcessedMessageId: Long = 0L,
    val currentStoryTime: String = "未确定",
    val events: List<TimelineEventItem> = emptyList(),
    val atemporalSettings: List<AtemporalSettingItem> = emptyList(),
    val isCompleted: Boolean = false,
    val totalChunks: Int = 1,
    val processedChunks: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 时间线梳理水线与检查点模型（记录上一次已梳理入库的对话节点与状态）
 */
data class TimelineReconcileCheckpoint(
    val conversationId: Long,
    val lastReconciledMessageId: Long,
    val lastReconciledMessageIndex: Int,
    val totalMessageCountAtReconciliation: Int,
    val storyTimeAtReconciliation: String? = null,
    val nodeCountAtReconciliation: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 时间线梳理草稿管理器（单例，负责磁盘文件的读写与缓存管理）
 */
object TimelineDraftManager {
    private const val TAG = "TimelineDraftManager"
    private const val DRAFT_DIR_NAME = "timeline_drafts"

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    private fun getDraftDir(context: Context): File {
        val dir = File(context.filesDir, DRAFT_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun getDraftFile(context: Context, conversationId: Long): File {
        return File(getDraftDir(context), "draft_${conversationId}.json")
    }

    /**
     * 保存梳理草稿
     */
    fun saveDraft(context: Context, draft: TimelineReconcileDraft) {
        try {
            val file = getDraftFile(context, draft.conversationId)
            val json = gson.toJson(draft)
            file.writeText(json, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "保存时间线梳理草稿失败: ${e.message}", e)
        }
    }

    /**
     * 读取指定会话的梳理草稿
     *
     * 历史草稿文件可能包含显式 null（旧版本写入中断、手改文件）：
     * Gson 会经反射把 null 写进 Kotlin 非空字段，调用方再访问即 NPE 闪退。
     * 此处统一做无害化，损坏条目用默认值替代，绝不把 null 漏给 UI。
     */
    fun getDraft(context: Context, conversationId: Long): TimelineReconcileDraft? {
        try {
            val file = getDraftFile(context, conversationId)
            if (!file.exists()) return null
            val json = file.readText(Charsets.UTF_8)
            if (json.isBlank()) return null
            return sanitizeDraft(gson.fromJson(json, TimelineReconcileDraft::class.java))
        } catch (e: Exception) {
            Log.w(TAG, "读取时间线梳理草稿失败: ${e.message}")
            return null
        }
    }

    /**
     * 草稿无害化（纯函数，不碰磁盘/Context，可单测）：
     * null 列表视为空，null 字符串视为空串，null/空白 id 重发，null 分类回落默认，
     * 非法 targetScope 回落 session。输入 null 返回 null。
     */
    @Suppress("UNNECESSARY_SAFE_CALL")
    fun sanitizeDraft(draft: TimelineReconcileDraft?): TimelineReconcileDraft? {
        if (draft == null) return null
        val safeEvents = draft.events?.orEmpty()?.mapNotNull { ev ->
            if (ev == null) return@mapNotNull null
            ev.copy(
                id = ev.id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
                timeTag = ev.timeTag ?: "",
                content = ev.content ?: "",
                category = ev.category ?: TimelineCategory.PLOT_EVENT
            )
        } ?: emptyList()
        val safeSettings = draft.atemporalSettings?.orEmpty()?.mapNotNull { setting ->
            if (setting == null) return@mapNotNull null
            setting.copy(
                id = setting.id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
                category = setting.category?.takeIf { it.isNotBlank() } ?: "角色特质",
                content = setting.content ?: "",
                targetScope = setting.targetScope?.takeIf { it == "global" || it == "session" } ?: "session"
            )
        } ?: emptyList()
        return draft.copy(
            currentStoryTime = draft.currentStoryTime?.takeIf { it.isNotBlank() } ?: "未确定",
            events = safeEvents,
            atemporalSettings = safeSettings
        )
    }

    /**
     * 清理指定会话的草稿
     */
    fun clearDraft(context: Context, conversationId: Long) {
        try {
            val file = getDraftFile(context, conversationId)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "删除时间线梳理草稿失败: ${e.message}")
        }
    }

    /**
     * 是否存在可用草稿
     */
    fun hasDraft(context: Context, conversationId: Long): Boolean {
        val file = getDraftFile(context, conversationId)
        return file.exists() && file.length() > 0
    }

    private fun getCheckpointFile(context: Context, conversationId: Long): File {
        return File(getDraftDir(context), "checkpoint_${conversationId}.json")
    }

    /**
     * 保存时间线梳理水线/检查点
     */
    fun saveCheckpoint(context: Context, checkpoint: TimelineReconcileCheckpoint) {
        try {
            val file = getCheckpointFile(context, checkpoint.conversationId)
            val json = gson.toJson(checkpoint)
            file.writeText(json, Charsets.UTF_8)
            Log.d(TAG, "已记录会话 ${checkpoint.conversationId} 时间线梳理检查点: 消息 #${checkpoint.lastReconciledMessageId} (第 ${checkpoint.lastReconciledMessageIndex} 条)")
        } catch (e: Exception) {
            Log.e(TAG, "保存时间线梳理检查点失败: ${e.message}", e)
        }
    }

    /**
     * 读取指定会话的时间线梳理水线/检查点
     */
    fun getCheckpoint(context: Context, conversationId: Long): TimelineReconcileCheckpoint? {
        try {
            val file = getCheckpointFile(context, conversationId)
            if (!file.exists()) return null
            val json = file.readText(Charsets.UTF_8)
            if (json.isBlank()) return null
            return gson.fromJson(json, TimelineReconcileCheckpoint::class.java)
        } catch (e: Exception) {
            Log.w(TAG, "读取时间线梳理检查点失败: ${e.message}")
            return null
        }
    }

    /**
     * 清理指定会话的检查点
     */
    fun clearCheckpoint(context: Context, conversationId: Long) {
        try {
            val file = getCheckpointFile(context, conversationId)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "删除时间线梳理检查点失败: ${e.message}")
        }
    }

    /**
     * 是否存在可用检查点
     */
    fun hasCheckpoint(context: Context, conversationId: Long): Boolean {
        val file = getCheckpointFile(context, conversationId)
        return file.exists() && file.length() > 0
    }
}
