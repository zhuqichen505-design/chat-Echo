package com.aiassistant.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

/** 应用内设置提交通过同一监听刷新外观；直接恢复备份 XML 后仍需重启应用。 */
@Composable
internal fun rememberPersonalizationSettings(): PersonalizationSettings {
    val context = LocalContext.current.applicationContext
    val manager = remember(context) { PersonalizationManager(context) }
    var settings by remember(manager) { mutableStateOf(manager.getSettings()) }
    DisposableEffect(context, manager) {
        val preferences = context.getSharedPreferences("personalization_settings", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> settings = manager.getSettings() }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        settings = manager.getSettings()
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return settings
}
