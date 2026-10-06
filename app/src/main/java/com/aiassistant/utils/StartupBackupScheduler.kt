package com.aiassistant.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** First successful home query starts the timer, not Application/database construction. */
internal class StartupBackupScheduler(
    private val scope: CoroutineScope,
    private val delayMillis: Long = 15_000L,
    private val backup: suspend () -> Unit
) {
    private val scheduled = AtomicBoolean(false)

    fun onHomeLoaded() {
        if (!scheduled.compareAndSet(false, true)) return
        scope.launch {
            delay(delayMillis)
            backup()
        }
    }
}
