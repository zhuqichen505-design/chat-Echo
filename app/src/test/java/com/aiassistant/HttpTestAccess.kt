package com.aiassistant

import com.aiassistant.data.remote.HttpAccessPolicy
import com.aiassistant.data.remote.HttpAccessRules
import java.util.concurrent.CopyOnWriteArraySet

/** Explicitly authorize only synthetic endpoints; production policy never has a test bypass. */
internal object HttpTestAccess {
    private val addresses = CopyOnWriteArraySet<String>()
    fun start() {
        addresses.clear()
        HttpAccessPolicy.initialize { HttpAccessRules(true, addresses.toList()) }
    }
    fun allow(address: String) { addresses.add(HttpAccessPolicy.normalizeAddress(address)) }
    fun stop() { HttpAccessPolicy.initialize { HttpAccessRules() }; addresses.clear() }
}
