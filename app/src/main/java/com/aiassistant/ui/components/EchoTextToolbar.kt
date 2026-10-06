package com.aiassistant.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import com.aiassistant.ui.theme.EchoTokens
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

class EchoTextToolbarState(
    rect: Rect,
    val canCopy: Boolean = false,
    val canPaste: Boolean = false,
    val canCut: Boolean = false,
    val canSelectAll: Boolean = false,
    var onCopy: (() -> Unit)? = null,
    var onPaste: (() -> Unit)? = null,
    var onCut: (() -> Unit)? = null,
    var onSelectAll: (() -> Unit)? = null
) {
    var rect by mutableStateOf(rect)

    constructor(
        rect: Rect,
        onCopy: (() -> Unit)? = null,
        onPaste: (() -> Unit)? = null,
        onCut: (() -> Unit)? = null,
        onSelectAll: (() -> Unit)? = null
    ) : this(
        rect = rect,
        canCopy = onCopy != null,
        canPaste = onPaste != null,
        canCut = onCut != null,
        canSelectAll = onSelectAll != null,
        onCopy = onCopy,
        onPaste = onPaste,
        onCut = onCut,
        onSelectAll = onSelectAll
    )

    fun isEquivalent(
        newRect: Rect,
        hasCopy: Boolean,
        hasPaste: Boolean,
        hasCut: Boolean,
        hasSelectAll: Boolean
    ): Boolean {
        return abs(rect.left - newRect.left) < 16f &&
            abs(rect.top - newRect.top) < 16f &&
            abs(rect.right - newRect.right) < 16f &&
            abs(rect.bottom - newRect.bottom) < 16f &&
            canCopy == hasCopy &&
            canPaste == hasPaste &&
            canCut == hasCut &&
            canSelectAll == hasSelectAll
    }
}

class EchoTextToolbar : TextToolbar {
    private var _status: TextToolbarStatus = TextToolbarStatus.Hidden

    // status 不直接触发 Compose 快照状态依赖读取，彻底杜绝 SelectionContainer 产生死循环重组与闪烁
    override val status: TextToolbarStatus
        get() = _status

    var activeMenu by mutableStateOf<EchoTextToolbarState?>(null)

    override fun hide() {
        _status = TextToolbarStatus.Hidden
        activeMenu = null
    }

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        val hasCopy = onCopyRequested != null
        val hasPaste = onPasteRequested != null
        val hasCut = onCutRequested != null
        val hasSelectAll = onSelectAllRequested != null

        // Avoid showing empty floating box if no actions are available
        if (!hasCopy && !hasPaste && !hasCut && !hasSelectAll) {
            hide()
            return
        }

        val current = activeMenu
        if (current != null) {
            // 已存在菜单时就地更新回调与位置，严禁创建新对象触发弹窗重建与重绘闪烁
            current.onCopy = onCopyRequested
            current.onPaste = onPasteRequested
            current.onCut = onCutRequested
            current.onSelectAll = onSelectAllRequested
            if (abs(current.rect.left - rect.left) > 16f ||
                abs(current.rect.top - rect.top) > 16f ||
                abs(current.rect.right - rect.right) > 16f ||
                abs(current.rect.bottom - rect.bottom) > 16f
            ) {
                current.rect = rect
            }
            _status = TextToolbarStatus.Shown
            return
        }

        _status = TextToolbarStatus.Shown
        activeMenu = EchoTextToolbarState(
            rect = rect,
            canCopy = hasCopy,
            canPaste = hasPaste,
            canCut = hasCut,
            canSelectAll = hasSelectAll,
            onCopy = onCopyRequested,
            onPaste = onPasteRequested,
            onCut = onCutRequested,
            onSelectAll = onSelectAllRequested
        )
    }
}

class InAppSelectionClipboardManager(
    private val delegate: androidx.compose.ui.platform.ClipboardManager
) : androidx.compose.ui.platform.ClipboardManager {
    var lastCapturedText: AnnotatedString? = null
    var isQuoteCapturing: Boolean = false

    override fun getText(): AnnotatedString? {
        return lastCapturedText ?: delegate.getText()
    }

    override fun setText(annotatedString: AnnotatedString) {
        lastCapturedText = annotatedString
        if (!isQuoteCapturing) {
            delegate.setText(annotatedString)
        }
    }

    override fun hasText(): Boolean {
        return lastCapturedText != null || delegate.hasText()
    }
}

@Composable
fun EchoTextToolbarHost(
    toolbar: EchoTextToolbar,
    onQuoteSelected: (String) -> Unit
) {
    val menu = toolbar.activeMenu ?: return
    val clipboardManager = LocalClipboardManager.current
    val inAppClipboard = clipboardManager as? InAppSelectionClipboardManager
    val density = LocalDensity.current
    val glass = echoGlassPalette()
    val coroutineScope = rememberCoroutineScope()

    val popupPositionProvider = remember(density) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val menuRect = toolbar.activeMenu?.rect ?: Rect.Zero
                val x = ((menuRect.left + menuRect.right) / 2f - popupContentSize.width / 2f)
                    .roundToInt()
                    .coerceIn(16, (windowSize.width - popupContentSize.width - 16).coerceAtLeast(16))

                val yAbove = (menuRect.top - popupContentSize.height - with(density) { 10.dp.toPx() }).roundToInt()
                val yBelow = (menuRect.bottom + with(density) { 10.dp.toPx() }).roundToInt()
                val y = if (yAbove >= 70) yAbove else yBelow
                return IntOffset(x, y.coerceIn(16, (windowSize.height - popupContentSize.height - 16).coerceAtLeast(16)))
            }
        }
    }

    Popup(
        popupPositionProvider = popupPositionProvider,
        onDismissRequest = { toolbar.hide() },
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        val pillShape = RoundedCornerShape(999.dp)
        Surface(
            shape = pillShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            border = BorderStroke(1.dp, glass.outline),
            shadowElevation = EchoTokens.Elevation.overlay(isDark).elevation
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (menu.canCut) {
                    TextToolbarActionItem(
                        icon = Icons.Default.ContentCut,
                        text = "剪切",
                        onClick = {
                            menu.onCut?.invoke()
                            toolbar.hide()
                        }
                    )
                }
                if (menu.canCopy) {
                    TextToolbarActionItem(
                        icon = Icons.Default.ContentCopy,
                        text = "复制",
                        onClick = {
                            menu.onCopy?.invoke()
                            toolbar.hide()
                        }
                    )
                }
                if (menu.canPaste) {
                    TextToolbarActionItem(
                        icon = Icons.Default.ContentPaste,
                        text = "粘贴",
                        onClick = {
                            menu.onPaste?.invoke()
                            toolbar.hide()
                        }
                    )
                }
                if (menu.canCopy) {
                    TextToolbarActionItem(
                        icon = Icons.Default.FormatQuote,
                        text = "引用",
                        onClick = {
                            if (inAppClipboard != null) {
                                inAppClipboard.isQuoteCapturing = true
                                menu.onCopy?.invoke()
                                inAppClipboard.isQuoteCapturing = false
                                val captured = inAppClipboard.lastCapturedText?.text.orEmpty()
                                if (captured.isNotBlank()) {
                                    onQuoteSelected(captured)
                                }
                                toolbar.hide()
                            } else {
                                menu.onCopy?.invoke()
                                coroutineScope.launch {
                                    var retries = 8
                                    var text = ""
                                    while (retries > 0) {
                                        delay(35)
                                        text = clipboardManager.getText()?.text.orEmpty()
                                        if (text.isNotBlank()) break
                                        retries--
                                    }
                                    if (text.isNotBlank()) {
                                        onQuoteSelected(text)
                                    }
                                    toolbar.hide()
                                }
                            }
                        }
                    )
                }
                if (menu.canSelectAll) {
                    TextToolbarActionItem(
                        icon = Icons.Default.SelectAll,
                        text = "全选",
                        onClick = {
                            menu.onSelectAll?.invoke()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TextToolbarActionItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            modifier = Modifier.size(15.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
