package com.aiassistant.utils

import android.content.Context
import androidx.core.content.edit
import com.aiassistant.data.remote.HttpAccessPolicy
import com.aiassistant.data.remote.HttpAccessRules

/** Device-local permission. Intentionally excluded from portable chat backups. */
class HttpAccessSettings(context: Context) {
    private val prefs = context.getSharedPreferences("http_access_permissions", Context.MODE_PRIVATE)

    fun getRules() = HttpAccessRules(
        enabled = prefs.getBoolean("enabled", false),
        addresses = prefs.getStringSet("addresses", emptySet()).orEmpty().sorted()
    )

    fun setEnabled(enabled: Boolean) { prefs.edit { putBoolean("enabled", enabled) } }

    fun addAddress(address: String) {
        val normalized = HttpAccessPolicy.normalizeAddress(address)
        prefs.edit { putStringSet("addresses", getRules().addresses.toSet() + normalized) }
    }

    fun removeAddress(address: String) {
        prefs.edit { putStringSet("addresses", getRules().addresses.toSet() - address) }
    }
}
