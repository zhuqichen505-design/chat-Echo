package com.aiassistant

import com.aiassistant.data.repository.ChatGenerationManager
import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Test

class GenerationLifecycleTest {
    @Test fun concurrentExtractionProgressBalancesAndDeletingSessionClearsReview() {
        val id = 908074L
        ChatGenerationManager.beginTimelineExtraction(id)
        ChatGenerationManager.beginTimelineExtraction(id)
        assertEquals(2, ChatGenerationManager.timelineExtractionCount(id).value)
        ChatGenerationManager.endTimelineExtraction(id)
        assertEquals(1, ChatGenerationManager.timelineExtractionCount(id).value)
        ChatGenerationManager.endTimelineExtraction(id)
        ChatGenerationManager.endTimelineExtraction(id)
        assertEquals(0, ChatGenerationManager.timelineExtractionCount(id).value)
        ChatGenerationManager.offerTimelineProposal(id, com.aiassistant.data.repository.AutoTimelineUpdateResult(null, null, "旧内容"))
        val before = ChatGenerationManager.mutationEpoch(id)
        ChatGenerationManager.cancelSession(id)
        assertTrue(ChatGenerationManager.timelineProposals(id).value.isEmpty())
        assertEquals(before + 1, ChatGenerationManager.mutationEpoch(id))
    }
    @Test fun roundProposalsSurviveGenerationRemovalAndKeepUnconfirmedRounds() {
        val id = 908073L
        val first = com.aiassistant.data.repository.AutoTimelineUpdateResult(null, null, "第一轮")
        val second = com.aiassistant.data.repository.AutoTimelineUpdateResult(null, null, "第二轮")
        try {
            val session = ChatGenerationManager.startSession(id, "model")
            ChatGenerationManager.offerTimelineProposal(id, first)
            session.markFinished()
            ChatGenerationManager.removeSession(id, session)
            ChatGenerationManager.offerTimelineProposal(id, second)
            assertEquals(listOf(first, second), ChatGenerationManager.timelineProposals(id).value)
            ChatGenerationManager.dismissTimelineProposal(id, first)
            assertSame(second, ChatGenerationManager.timelineProposals(id).value.single())
            ChatGenerationManager.dismissTimelineProposal(id, first)
            assertSame(second, ChatGenerationManager.timelineProposals(id).value.single())
        } finally {
            ChatGenerationManager.dismissTimelineProposal(id, first)
            ChatGenerationManager.dismissTimelineProposal(id, second)
        }
    }
    @org.junit.Test fun replyDirectionWaitSurvivesReattachmentAndAcceptsOnlyOneAnswer() {
        val id = 78123L
        val session = ChatGenerationManager.startSession(id, "exact/id")
        try {
            session.directionPhase = true
            session._isConnecting.value = false
            var answers = 0
            session.pendingReplyDirection.value = com.aiassistant.domain.model.ReplyDirectionPrompt(onDecision = { answers++ })
            val reattached = ChatGenerationManager.getSession(id)!!
            assertSame(session.pendingReplyDirection.value, reattached.pendingReplyDirection.value)
            assertTrue(reattached.isGenerating.value)
            assertFalse(reattached.isConnecting.value)
            val choice = com.aiassistant.domain.model.ReplyDirectionDecision(com.aiassistant.domain.model.ReplyDirectionAction.SKIP)
            reattached.answerReplyDirection(choice)
            reattached.answerReplyDirection(choice)
            assertEquals(1, answers)
            assertNull(reattached.pendingReplyDirection.value)
            session.pendingReplyDirection.value = com.aiassistant.domain.model.ReplyDirectionPrompt(onDecision = { answers++ })
            session.markFinished()
            assertNull(session.pendingReplyDirection.value)
            assertEquals(1, answers)
        } finally { ChatGenerationManager.removeSession(id, session) }
    }
    @Test fun attemptErrorRemainsVisibleAcrossNavigationWithoutEndingGeneration() {
        val id = 908072L
        val session = ChatGenerationManager.startSession(id, "model")
        try {
            session.setAttemptError("API错误 (500)")
            val restored = ChatGenerationManager.getSession(id)!!
            assertEquals("API错误 (500)", restored.error.value)
            assertTrue(restored.isGenerating.value)
            assertTrue(restored.isConnecting.value)
            session.setAttemptError(null)
            session.appendResponse("OK")
            assertNull(restored.error.value)
            assertTrue(restored.isGenerating.value)
            assertFalse(restored.isConnecting.value)
            assertEquals("OK", restored.currentResponse.value)
            session.markFinished()
            assertFalse(restored.isGenerating.value)
        } finally { ChatGenerationManager.removeSession(id, session) }
    }
    @Test fun navigationReattachesSameBuffersAndCancellationPreventsLateSave() {
        val id = 908070L
        val session = ChatGenerationManager.startSession(id, "model")
        session.generationJob = Job()
        session.appendThinking("reasoning")
        session.appendResponse("partial")
        session.anchorUserMessageId = 123
        val reattached = ChatGenerationManager.getSession(id)!!
        assertSame(session, reattached)
        session.appendResponse(" completed")
        assertEquals("partial completed", reattached.currentResponse.value)
        assertEquals("reasoning", reattached.currentThinking.value)
        assertEquals(123L, reattached.anchorUserMessageId)
        ChatGenerationManager.cancelSession(id)
        assertTrue(session.generationJob!!.isCancelled)
        assertFalse(session.isGenerating.value)
        assertFalse(session.isMessageSaved.compareAndSet(false, true))
        assertNull(ChatGenerationManager.getSession(id))
    }

    @Test fun oldCompletionCannotRemoveNewGeneration() {
        val id = 908071L
        val old = ChatGenerationManager.startSession(id, "old")
        val current = ChatGenerationManager.startSession(id, "new")
        ChatGenerationManager.removeSession(id, old)
        assertSame(current, ChatGenerationManager.getSession(id))
        assertTrue(ChatGenerationManager.tryMarkMessageSaved(id))
        assertFalse(ChatGenerationManager.tryMarkMessageSaved(id))
        ChatGenerationManager.removeSession(id, current)
    }
}
