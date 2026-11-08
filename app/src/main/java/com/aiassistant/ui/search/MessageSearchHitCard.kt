package com.aiassistant.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiassistant.ui.components.EchoGlassPagePanelShape
import com.aiassistant.ui.components.echoGlassPalette
import com.aiassistant.ui.components.echoHazePanel
import com.aiassistant.ui.components.echoShapeClick
import com.aiassistant.ui.components.readableTextColorFor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 消息命中卡片。
 *
 * 与旧搜索卡片的区别：
 * 1. 片段由 [buildSearchSnippet] 计算，命中词以高亮背景标出，用户不用自己找；
 * 2. 点击后由调用方携带消息 id 跳转并定位，而不是只打开对话；
 * 3. 顶部重复展示对话标题，避免同一话题的多条命中无法区分归属。
 */
@Composable
fun MessageSearchHitCard(
    hazeState: dev.chrisbanes.haze.HazeState,
    hit: MessageSearchHit,
    readableBackdrop: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }
    val cardTint = echoGlassPalette().panelStrong
    val content = readableTextColorFor(
        background = cardTint,
        fallbackSurface = readableBackdrop
    )
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.26f)
    val preview = remember(hit.snippet, highlight, content) {
        buildHighlightedPreview(hit.snippet, highlight, content)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .echoHazePanel(
                hazeState = hazeState,
                shape = EchoGlassPagePanelShape,
                tint = cardTint,
                blurRadius = 18.dp
            )
            .echoShapeClick(EchoGlassPagePanelShape, onClick = onClick),
        shape = EchoGlassPagePanelShape,
        color = cardTint,
        contentColor = content,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.ChatBubbleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = hit.conversation.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "定位到该消息",
                    modifier = Modifier.size(16.dp),
                    tint = content.copy(alpha = 0.62f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f)
            ) {
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = content,
                    modifier = Modifier.padding(8.dp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (hit.message.role == "user") "用户" else "AI",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = dateFormat.format(Date(hit.message.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = content.copy(alpha = 0.68f)
                )
            }
        }
    }
}

/** 省略号也拼进 AnnotatedString，并把高亮区间整体偏移，避免前缀导致高亮错位。 */
internal fun buildHighlightedPreview(
    snippet: SearchSnippet,
    highlight: Color,
    textColor: Color
): AnnotatedString = buildAnnotatedString {
    val prefix = if (snippet.truncatedStart) "…" else ""
    val suffix = if (snippet.truncatedEnd) "…" else ""
    append(prefix)
    val offset = prefix.length
    append(snippet.text)
    val bodyEnd = offset + snippet.text.length
    snippet.highlights.forEach { range ->
        val start = offset + range.first
        val end = offset + range.last + 1
        if (start in offset until bodyEnd && end in (start + 1)..bodyEnd) {
            addStyle(
                SpanStyle(background = highlight, color = textColor),
                start,
                end
            )
        }
    }
    append(suffix)
}
