package com.aiassistant

import com.aiassistant.utils.StartupBackupScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StartupBackupSchedulerTest {
    @Test fun waitsForHomeLoadAndGracePeriodAndSchedulesOnlyOnce() = runTest {
        var backups = 0
        val scheduler = StartupBackupScheduler(this) { backups++ }
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(0, backups)
        repeat(5) { scheduler.onHomeLoaded() }
        runCurrent()
        advanceTimeBy(14_999)
        runCurrent()
        assertEquals(0, backups)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, backups)
        scheduler.onHomeLoaded()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, backups)
    }
}
