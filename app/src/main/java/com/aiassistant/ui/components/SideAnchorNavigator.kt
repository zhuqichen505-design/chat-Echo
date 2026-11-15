package com.aiassistant.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.aiassistant.ui.theme.EchoTokens
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch

data class SideAnchorItem(
    val title: String,
    val itemIndex: Int,
    /** 输入列表序号（从 1 开始）；其它场景为空表示不展示编号 */
    val number: Int? = null,
    /** 搜索用的完整文本；为空时退回 [title] */
    val searchText: String? = null
)

/** 右侧列表内搜索：按完整文本过滤，忽略大小写与首尾空白。 */
internal fun filterAnchorItems(items: List<SideAnchorItem>, query: String): List<SideAnchorItem> {
    val normalized = query.trim()
    if (normalized.isEmpty()) return items
    return items.filter { (it.searchText ?: it.title).contains(normalized, ignoreCase = true) }
}

@Composable
fun SideAnchorNavigator(
    items: List<SideAnchorItem>,
    listState: LazyListState,
    visible: Boolean = true,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier
) {
    if (items.size < 2) return

    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    val currentIndex by remember(items, listState) {
        derivedStateOf {
            val first = listState.firstVisibleItemIndex
            items.lastOrNull { it.itemIndex <= first }?.itemIndex ?: items.first().itemIndex
        }
    }

    AnimatedVisibility(
        visible = visible || expanded,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.CenterEnd
        ) {
            if (expanded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .echoPlainClick { expanded = false }
                )
            }

            AnimatedVisibility(
                visible = !expanded,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                CollapsedAnchorRail(
                    items = items,
                    currentIndex = currentIndex,
                    onClick = { expanded = true }
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + scaleIn(initialScale = 0.96f),
                exit = fadeOut() + scaleOut(targetScale = 0.96f),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                ExpandedAnchorPanel(
                    items = items,
                    currentIndex = currentIndex,
                    hazeState = hazeState,
                    onDismiss = { expanded = false },
                    onSelected = { item ->
                        expanded = false
                        scope.launch {
                            listState.animateScrollToItem(item.itemIndex)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun CollapsedAnchorRail(
    items: List<SideAnchorItem>,
    currentIndex: Int,
    onClick: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary.copy(alpha = 0.90f)
    val inactive = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f)
    val glass = echoGlassPalette()
    // 限制最大显示点数，保持清疏美观
    val visibleCount = items.size.coerceIn(2, 8)
    val step = 15.dp
    val railHeight = (step * (visibleCount - 1) + 24.dp).coerceIn(46.dp, 168.dp)
    val clickShape = RoundedCornerShape(14.dp)

    Box(
        modifier = Modifier
            .padding(end = 2.dp)
            .width(24.dp)
            .height(railHeight)
            .clip(clickShape)
            .background(glass.panelStrong.copy(alpha = 0.55f), clickShape)
            .border(0.8.dp, glass.outline.copy(alpha = 0.38f), clickShape)
            .echoShapeClick(clickShape, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp)
        ) {
            val activePosition = items.indexOfLast { it.itemIndex <= currentIndex }
                .coerceAtLeast(0)
            val activeMarker = if (items.size <= 1) {
                0
            } else {
                (activePosition * (visibleCount - 1) / (items.size - 1).coerceAtLeast(1))
            }
            val stepPx = step.toPx()
            val startY = (size.height - stepPx * (visibleCount - 1).coerceAtLeast(0)) / 2f

            repeat(visibleCount) { marker ->
                val y = if (visibleCount <= 1) size.height / 2f else startY + marker * stepPx
                val isActive = marker == activeMarker
                if (isActive) {
                    val pillWidth = 14.dp.toPx()
                    val pillHeight = 4.5.dp.toPx()
                    drawRoundRect(
                        color = primary,
                        topLeft = Offset((size.width - pillWidth) / 2f, y - pillHeight / 2f),
                        size = Size(pillWidth, pillHeight),
                        cornerRadius = CornerRadius(999.dp.toPx(), 999.dp.toPx())
                    )
                } else {
                    val dotRadius = 2.2.dp.toPx()
                    drawCircle(
                        color = inactive,
                        radius = dotRadius,
                        center = Offset(size.width / 2f, y)
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpandedAnchorPanel(
    items: List<SideAnchorItem>,
    currentIndex: Int,
    hazeState: HazeState?,
    onDismiss: () -> Unit,
    onSelected: (SideAnchorItem) -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val panelShape = RoundedCornerShape(24.dp)
    val panelListState = rememberLazyListState()
    val activeIndex = remember(items, currentIndex) {
        items.indexOfLast { it.itemIndex <= currentIndex }.coerceAtLeast(0)
    }
    var query by remember { mutableStateOf("") }
    val visibleItems = remember(items, query) { filterAnchorItems(items, query) }
    val showNumbers = remember(items) { items.any { it.number != null } }

    LaunchedEffect(activeIndex, query) {
        if (query.isBlank() && activeIndex >= 0 && items.isNotEmpty()) {
            val targetScroll = (activeIndex - 2).coerceIn(0, (items.size - 1).coerceAtLeast(0))
            panelListState.animateScrollToItem(targetScroll)
        }
    }

    val panelModifier = if (hazeState != null) {
        Modifier
            .padding(end = 6.dp)
            .widthIn(min = 250.dp, max = 320.dp)
            .heightIn(max = 480.dp)
            .echoHazePanel(
                hazeState = hazeState,
                shape = panelShape,
                tint = echoGlassPalette().panelStrong,
                blurRadius = 18.dp,
                highlightAlpha = 0.03f
            )
            .clip(panelShape)
    } else {
        Modifier
            .padding(end = 6.dp)
            .widthIn(min = 250.dp, max = 320.dp)
            .heightIn(max = 480.dp)
            .shadow(
                elevation = EchoTokens.Elevation.overlay(isDark).elevation,
                shape = panelShape,
                ambientColor = EchoTokens.Elevation.overlay(isDark).ambient,
                spotColor = EchoTokens.Elevation.overlay(isDark).spot
            )
            .clip(panelShape)
    }
    Surface(
        modifier = panelModifier,
        shape = panelShape,
        color = if (hazeState != null) echoGlassPalette().panelStrong else MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
                    shape = panelShape
                )
                .padding(vertical = 10.dp)
        ) {
            AnchorSearchField(
                query = query,
                onQueryChange = { query = it },
                modifier = Modifier.padding(horizontal = 10.dp)
            )
            val resultHint = when {
                query.isBlank() -> "提问 · 共 ${items.size} 条"
                visibleItems.isEmpty() -> "没有匹配的提问"
                else -> "找到 ${visibleItems.size} 条匹配"
            }
            Text(
                text = resultHint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp)
            )
            Box(modifier = Modifier.fillMaxWidth()) {
                LazyColumn(
                    state = panelListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .padding(end = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    items(items = visibleItems, key = { it.itemIndex }) { item ->
                        val selected = item.itemIndex == currentIndex
                        val itemShape = RoundedCornerShape(14.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    } else {
                                        Color.Transparent
                                    },
                                    shape = itemShape
                                )
                                .echoShapeClick(itemShape) { onSelected(item) }
                                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (showNumbers) {
                                AnchorNumberBadge(
                                    number = item.number,
                                    selected = selected,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                            Text(
                                text = item.title,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .width(if (selected) 12.dp else 6.dp)
                                    .height(4.dp)
                                    .background(
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                        } else {
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                        },
                                        shape = RoundedCornerShape(999.dp)
                                    )
                            )
                        }
                    }
                    if (visibleItems.isEmpty()) {
                        item(key = "anchor_empty") {
                            Text(
                                text = "换个关键词试试",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                    }
                    item(key = "anchor_dismiss") {
                        val dismissShape = RoundedCornerShape(999.dp)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .echoShapeClick(dismissShape, onClick = onDismiss)
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "收起",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                            )
                        }
                    }
                }
                AnchorPanelScrollbar(
                    listState = panelListState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(end = 2.dp)
                )
            }
        }
    }
}

/** 列表内独立滑块：拖动时按比例定位到目标条目，与对话主列表滚动互不影响。 */
@Composable
private fun AnchorPanelScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val total by remember(listState) { derivedStateOf { listState.layoutInfo.totalItemsCount } }
    val visibleCount by remember(listState) { derivedStateOf { listState.layoutInfo.visibleItemsInfo.size } }
    if (total <= visibleCount.coerceAtLeast(1) || total <= 1) return
    val scope = rememberCoroutineScope()
    val progress by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val first = listState.firstVisibleItemIndex
            val item = info.visibleItemsInfo.firstOrNull()
            val itemFraction = if (item == null || item.size <= 0) 0f else {
                listState.firstVisibleItemScrollOffset.toFloat() / item.size
            }
            ((first + itemFraction) / (info.totalItemsCount - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
        }
    }
    val primary = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.outline

    fun scrollTo(y: Float, height: Int) {
        if (height <= 0) return
        val fraction = (y / height).coerceIn(0f, 1f)
        val target = (fraction * (total - 1)).toInt().coerceIn(0, total - 1)
        scope.launch { listState.scrollToItem(target) }
    }

    Canvas(
        modifier = modifier
            .width(12.dp)
            .pointerInput(total) {
                detectVerticalDragGestures(
                    onDragStart = { position -> scrollTo(position.y, size.height) },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        scrollTo(change.position.y, size.height)
                    }
                )
            }
    ) {
        val trackWidth = 3.dp.toPx()
        val trackX = size.width - trackWidth / 2f - 1.dp.toPx()
        drawRoundRect(
            color = inactive.copy(alpha = 0.18f),
            topLeft = Offset(trackX - trackWidth / 2f, 0f),
            size = Size(trackWidth, size.height),
            cornerRadius = CornerRadius(999.dp.toPx(), 999.dp.toPx())
        )
        val fraction = (visibleCount.toFloat() / total.toFloat()).coerceIn(0.12f, 1f)
        val thumbHeight = (size.height * fraction).coerceAtLeast(28.dp.toPx())
        val top = (size.height - thumbHeight) * progress
        drawRoundRect(
            color = primary.copy(alpha = 0.62f),
            topLeft = Offset(trackX - 2.dp.toPx(), top),
            size = Size(4.dp.toPx(), thumbHeight),
            cornerRadius = CornerRadius(999.dp.toPx(), 999.dp.toPx())
        )
    }
}

@Composable
private fun AnchorNumberBadge(
    number: Int?,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    if (number == null) return
    Box(
        modifier = modifier
            .widthIn(min = 20.dp)
            .height(20.dp)
            .background(
                color = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
                },
                shape = RoundedCornerShape(7.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            }
        )
    }
}

@Composable
private fun AnchorSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    val tint = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(shape)
            .background(tint, shape)
            .border(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), shape)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = "搜索提问内容",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .echoPlainClick { onQueryChange("") },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "清除搜索",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
