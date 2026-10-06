package com.aiassistant.domain.model

/** Shared stop geometry: first/last label centers equal the thumb centers at the endpoints. */
object ReasoningStopLayout {
    fun center(index: Int, count: Int, width: Float, inset: Float): Float {
        if (count <= 1) return width / 2f
        return inset + index.coerceIn(0, count - 1).toFloat() / (count - 1) * (width - 2 * inset).coerceAtLeast(0f)
    }
}
