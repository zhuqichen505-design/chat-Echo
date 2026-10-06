package com.aiassistant.utils

import android.content.Context
import androidx.core.content.edit
import com.aiassistant.domain.model.ReasoningCapabilityCatalog
import com.aiassistant.domain.model.ReasoningCapabilityEvidence
import com.google.gson.Gson

/** Non-secret endpoint metadata, persisted separately; no Room schema changes or startup requests. */
object ReasoningCapabilityPreferences {
    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences("reasoning_capabilities", Context.MODE_PRIVATE)
        val gson = Gson()
        val saved = prefs.all.mapNotNull { (key, value) ->
            (value as? String)?.let { json ->
                runCatching { gson.fromJson(json, ReasoningCapabilityEvidence::class.java) }.getOrNull()?.let { key to it }
            }
        }.toMap()
        ReasoningCapabilityCatalog.initialize(saved) { key, evidence ->
            prefs.edit {
                if (evidence == null) remove(key) else putString(key, gson.toJson(evidence))
            }
        }
    }
}
