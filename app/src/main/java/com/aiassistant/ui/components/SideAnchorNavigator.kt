package com.aiassistant.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch

data class SideAnchorItem(
    val title: String,
    val itemIndex: Int
)

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

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && items.isNotEmpty()) {
            val targetScroll = (activeIndex - 2).coerceIn(0, (items.size - 1).coerceAtLeast(0))
            panelListState.animateScrollToItem(targetScroll)
        }
    }

    val panelModifier = if (hazeState != null) {
        Modifier
            .padding(end = 6.dp)
            .widthIn(min = 230.dp, max = 300.dp)
            .heightIn(max = 440.dp)
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
            .widthIn(min = 230.dp, max = 300.dp)
            .heightIn(max = 440.dp)
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
        LazyColumn(
            state = panelListState,
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
                    shape = panelShape
                )
                .padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            items(items = items, key = { "${it.itemIndex}_${it.title}" }) { item ->
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
                        .padding(start = 16.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
            item {
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
    }
}
