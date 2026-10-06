package com.aiassistant.ui.screens.chat

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.withFrameNanos

/** Only consume actual bottom overflow; never overshoot and rely on a later layout to undo it. */
internal fun bottomFollowDistance(itemOffset: Int, itemSize: Int, viewportEnd: Int): Int =
    (itemOffset + itemSize - viewportEnd).coerceAtLeast(0)

internal suspend fun LazyListState.followMeasuredBottom() {
    val lastIndex = layoutInfo.totalItemsCount - 1
    if (lastIndex < 0) return
    if (layoutInfo.visibleItemsInfo.none { it.index == lastIndex }) {
        scrollToItem(lastIndex)
        withFrameNanos { }
    }
    val layout = layoutInfo
    val last = layout.visibleItemsInfo.lastOrNull { it.index == layout.totalItemsCount - 1 } ?: return
    val distance = bottomFollowDistance(last.offset, last.size, layout.viewportEndOffset)
    if (distance > 0) scrollBy(distance.toFloat())
}
