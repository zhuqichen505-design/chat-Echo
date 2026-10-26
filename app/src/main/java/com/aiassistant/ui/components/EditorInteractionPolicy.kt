package com.aiassistant.ui.components

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.math.abs
import kotlin.math.sign
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.ScrollableState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Same-text parent echoes must retain IME composition and the user's exact selection. */
internal fun reconcileEditorText(current: TextFieldValue, text: String): TextFieldValue =
    if (current.text == text) current else TextFieldValue(text, selection = TextRange(text.length))

/** Tracks String callbacks separately from the editor's latest IME value. */
internal class EditorTextAcknowledgements(initialText: String) {
    private var previousExternal = initialText
    private val pending = ArrayDeque<String>()

    fun published(text: String) {
        if (pending.lastOrNull() != text) pending.addLast(text)
    }

    fun isReplacement(text: String): Boolean {
        if (text == previousExternal) return false
        previousExternal = text
        val acknowledgement = pending.indexOf(text)
        if (acknowledgement >= 0) {
            repeat(acknowledgement + 1) { pending.removeFirst() }
            return false
        }
        pending.clear()
        return true
    }
}

/** Slow near an edge, at most 88dp/s farther out; independent of document length and frame rate. */
internal fun editorSelectionScrollStep(distancePx: Float, density: Float, seconds: Float): Float {
    if (!distancePx.isFinite() || !density.isFinite() || !seconds.isFinite() || density <= 0f || seconds <= 0f) return 0f
    val speed = (40f + abs(distancePx) / density * .6f).coerceAtMost(88f) * density
    return sign(distancePx) * minOf(abs(distancePx), speed * seconds.coerceAtMost(.05f))
}

/** A short-lived follow session. User scrolling cancels only this session, never the observer. */
internal suspend fun followEditorSelectionScroll(
    selections: Flow<TextRange>,
    scrollState: ScrollableState,
    density: Float,
    distanceAt: (Int) -> Float,
    frameNanos: suspend () -> Long
) = coroutineScope {
    var previousSelection: TextRange? = null
    var followedOffset = 0
    var movementVersion = 0L
    var followJob: Job? = null
    selections.collect { selection ->
        if (selection == previousSelection) return@collect
        followedOffset = if (previousSelection?.end != selection.end) selection.end else selection.start
        previousSelection = selection
        movementVersion++
        if (selection.collapsed) {
            followJob?.cancel()
        } else if (followJob?.isActive != true) {
            followJob = launch(start = CoroutineStart.UNDISPATCHED) {
                scrollState.scroll(MutatePriority.UserInput) {
                    var previousFrame = frameNanos()
                    var observedVersion = movementVersion
                    var idleSeconds = 0f
                    while (true) {
                        val frame = frameNanos()
                        val elapsedSeconds = ((frame - previousFrame) / 1_000_000_000f).coerceAtLeast(0f)
                        val seconds = elapsedSeconds.coerceAtMost(.05f)
                        previousFrame = frame
                        if (observedVersion != movementVersion) {
                            observedVersion = movementVersion
                            idleSeconds = 0f
                        } else idleSeconds += elapsedSeconds
                        if (idleSeconds > .1f) break
                        scrollBy(editorSelectionScrollStep(distanceAt(followedOffset), density, seconds))
                    }
                }
            }
        }
    }
}

/** Layout/focus changes alone must not move a manually browsed editor to its old cursor. */
internal fun shouldFollowEditorCursor(
    focused: Boolean,
    previous: TextFieldValue,
    current: TextFieldValue,
    layoutText: String
): Boolean = focused && layoutText == current.text &&
    (previous.text != current.text || previous.selection != current.selection)
