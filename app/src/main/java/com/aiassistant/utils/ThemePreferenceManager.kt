package com.aiassistant.utils

import android.content.Context

enum class AppThemeMode(val value: String, val label: String) {
    System("system", "跟随系统"),
    Light("light", "浅色"),
    Dark("dark", "深色");

    companion object {
        fun fromValue(value: String?): AppThemeMode {
            return entries.firstOrNull { it.value == value } ?: System
        }
    }
}

class ThemePreferenceManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "theme_preferences",
        Context.MODE_PRIVATE
    )

    fun getThemeMode(): AppThemeMode {
        return AppThemeMode.fromValue(prefs.getString(KEY_THEME_MODE, AppThemeMode.System.value))
    }

    fun saveThemeMode(mode: AppThemeMode): Boolean {
        return prefs.edit()
            .putString(KEY_THEME_MODE, mode.value)
            .commit()
    }

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
    }
}
