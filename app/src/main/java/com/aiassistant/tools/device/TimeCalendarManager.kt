package com.aiassistant.tools.device

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class TimeCalendarManager(private val context: Context) {

    fun getCurrentTimeFormatted(): String {
        val now = Date()
        val dateFormat = SimpleDateFormat("yyyy年MM月dd日 EEEE HH:mm:ss", Locale.CHINESE)
        val tz = TimeZone.getDefault()
        return "${dateFormat.format(now)} (${tz.id}, ${tz.displayName})"
    }

    fun getTodayScheduleSummary(): String {
        val timeStr = getCurrentTimeFormatted()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return "【当前设备时间】\n$timeStr\n(系统日历读取权限未授予，如需查询日程安排请在系统设置中授予日历权限)"
        }

        val events = queryTodayCalendarEvents()
        return buildString {
            append("【当前设备时间】\n").append(timeStr)
            append("\n\n【今日日程安排】")
            if (events.isEmpty()) {
                append("\n今日暂无日程或未检索到已同步的待办事项。")
            } else {
                events.forEachIndexed { index, event ->
                    append("\n${index + 1}. [${event.time}] ${event.title}")
                    if (event.description.isNotBlank()) {
                        append(" (备注: ${event.description})")
                    }
                }
            }
        }
    }

    private fun queryTodayCalendarEvents(): List<CalendarEventItem> {
        val list = mutableListOf<CalendarEventItem>()
        try {
            val startOfDay = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val endOfDay = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.timeInMillis

            val builder: Uri.Builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(builder, startOfDay)
            ContentUris.appendId(builder, endOfDay)

            val projection = arrayOf(
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.DESCRIPTION,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY
            )

            context.contentResolver.query(
                builder.build(),
                projection,
                null,
                null,
                CalendarContract.Instances.BEGIN + " ASC"
            )?.use { cursor ->
                val titleIdx = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
                val descIdx = cursor.getColumnIndex(CalendarContract.Instances.DESCRIPTION)
                val beginIdx = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endIdx = cursor.getColumnIndex(CalendarContract.Instances.END)
                val allDayIdx = cursor.getColumnIndex(CalendarContract.Instances.ALL_DAY)

                val timeFmt = SimpleDateFormat("HH:mm", Locale.CHINESE)

                while (cursor.moveToNext()) {
                    val title = if (titleIdx >= 0) cursor.getString(titleIdx).orEmpty() else "未命名事项"
                    val desc = if (descIdx >= 0) cursor.getString(descIdx).orEmpty() else ""
                    val begin = if (beginIdx >= 0) cursor.getLong(beginIdx) else 0L
                    val end = if (endIdx >= 0) cursor.getLong(endIdx) else 0L
                    val isAllDay = if (allDayIdx >= 0) cursor.getInt(allDayIdx) == 1 else false

                    val timeLabel = if (isAllDay) {
                        "全天"
                    } else {
                        "${timeFmt.format(Date(begin))} - ${timeFmt.format(Date(end))}"
                    }

                    list.add(CalendarEventItem(title, desc, timeLabel))
                }
            }
        } catch (_: Exception) {
            // 优雅捕获查询异常
        }
        return list
    }

    data class CalendarEventItem(
        val title: String,
        val description: String,
        val time: String
    )
}
