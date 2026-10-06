package com.aiassistant

import com.aiassistant.utils.BoundedInput
import com.aiassistant.utils.HistoryExchange
import org.junit.Assert.*
import org.junit.Test

class AttachmentAndHistoryExchangeTest {
    @Test fun unknownSizeStreamStopsAtBudgetAndExactBudgetWorks() {
        assertArrayEquals(ByteArray(8), BoundedInput.bytes(ByteArray(8).inputStream(), 8))
        try { BoundedInput.bytes(ByteArray(9).inputStream(), 8); fail("over budget accepted") } catch (_: IllegalArgumentException) { }
        assertEquals(8, BoundedInput.sampleSize(12000, 12000, 1024, 1024))
    }
    @Test fun longSingleLineIsBounded() {
        assertEquals("a".repeat(100) + "\n[内容已截断，完整内容过长]", BoundedInput.text("a".repeat(100000).reader(), 100))
        assertEquals("a\nb", BoundedInput.text("a\nb".reader(), 3))
    }
    @Test fun importPreservesTimestampAndRejectsInvalidTail() {
        val parsed = HistoryExchange.parse("""{"messages":[{"role":"user","content":"hello","timestamp":"2026-10-03T08:00:00+08:00"}]}""", "model")
        assertEquals(java.time.Instant.parse("2026-10-03T00:00:00Z").toEpochMilli(), parsed.messages.single().createdAt)
        try { HistoryExchange.parse("""{"messages":[{"role":"user","content":"hello"},"broken"]}""", "model"); fail("invalid tail accepted") }
        catch (_: IllegalStateException) { }
    }
}
