package com.aiassistant.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Compose 1.6.8 anchors the built-in selection handles through `positionInWindow()`, which on this
 * platform reports screen coordinates while the handle windows are laid out relative to the app
 * window. In a floating/free-form window every handle is therefore shifted by the window origin:
 * it drifts away from the selected text and often lands outside the app.
 *
 * [EditorSelectionHandleLayer] replaces those handles for such windows. Its handles still live in
 * popups, but their position is derived from root coordinates (exactly like [EchoTextToolbarHost]),
 * so the window manager places them relative to the app window and they always sit on the text.
 */

/** True when the app window is not the fullscreen one, where Compose's handle popups break. */
@Composable
internal fun isFloatingWindowHost(): Boolean {
    val view = LocalView.current
    // Re-evaluated whenever the window configuration changes (free-form, split screen, fullscreen).
    val configuration = LocalConfiguration.current
    return remember(view, configuration) {
        val context = view.context
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowManager = context.getSystemService(WindowManager::class.java)
            if (windowManager != null) {
                val window = windowManager.currentWindowMetrics.bounds
                val display = windowManager.maximumWindowMetrics.bounds
                return@remember window.width() < display.width() ||
                    window.height() < display.height()
            }
        }
        context.findActivity()?.isInMultiWindowMode == true
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private val HandleWidth = 28.dp
private val HandleStemHeight = 9.dp
private val HandleRadius = 8.5.dp

/**
 * Draws draggable selection handles inside the app window's own coordinate space.
 * Only used where the platform breaks Compose's handle popups; otherwise the native handles stay.
 */
@Composable
internal fun EditorSelectionHandleLayer(
    layout: TextLayoutResult?,
    selection: TextRange,
    textLayoutInRoot: Offset,
    onSelectionChange: (TextRange) -> Unit
) {
    if (layout == null || selection.collapsed) return
    val view = LocalView.current
    val windowLocation = IntArray(2)
    view.getLocationInWindow(windowLocation)
    val textLayoutInWindow = Offset(
        textLayoutInRoot.x + windowLocation[0],
        textLayoutInRoot.y + windowLocation[1]
    )
    val handleColor = MaterialTheme.colorScheme.primary
    val startAnchor = handleAnchor(layout, selection.start, isStart = true)
    val endAnchor = handleAnchor(layout, selection.end, isStart = false)

    if (startAnchor != Offset.Unspecified) {
        EditorHandlePopup(
            anchor = startAnchor,
            textLayoutInWindow = textLayoutInWindow,
            color = handleColor,
            onDragTo = { point ->
                val limit = (selection.end - 1).coerceAtLeast(0)
                val offset = layout.getOffsetForPosition(point).coerceIn(0, limit)
                if (offset != selection.start) {
                    onSelectionChange(TextRange(offset, selection.end))
                }
            }
        )
    }
    if (endAnchor != Offset.Unspecified) {
        EditorHandlePopup(
            anchor = endAnchor,
            textLayoutInWindow = textLayoutInWindow,
            color = handleColor,
            onDragTo = { point ->
                val length = layout.layoutInput.text.length
                val limit = (selection.start + 1).coerceAtMost(length)
                val offset = layout.getOffsetForPosition(point).coerceIn(limit, length)
                if (offset != selection.end) {
                    onSelectionChange(TextRange(selection.start, offset))
                }
            }
        )
    }
}

@Composable
private fun EditorHandlePopup(
    anchor: Offset,
    textLayoutInWindow: Offset,
    color: Color,
    onDragTo: (Offset) -> Unit
) {
    val currentDragTo by rememberUpdatedState(onDragTo)

    val positionProvider = remember(anchor, textLayoutInWindow) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset = IntOffset(
                (textLayoutInWindow.x + anchor.x - popupContentSize.width / 2f).roundToInt(),
                (textLayoutInWindow.y + anchor.y).roundToInt()
            )
        }
    }

    Popup(
        popupPositionProvider = positionProvider,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            excludeFromSystemGesture = true,
            clippingEnabled = false
        )
    ) {
        Canvas(
            modifier = Modifier
                .size(HandleWidth, HandleStemHeight + HandleRadius * 2)
                .pointerInput(Unit) {
                    // The handle window moves with the finger, so only the accumulated drag
                    // distance is stable; the anchor itself is captured when the drag starts.
                    var dragBegin = Offset.Zero
                    var dragDistance = Offset.Zero
                    detectDragGestures(
                        onDragStart = {
                            dragBegin = anchor
                            dragDistance = Offset.Zero
                        },
                        onDrag = { change, delta ->
                            change.consume()
                            dragDistance += delta
                            currentDragTo(dragBegin + dragDistance)
                        }
                    )
                }
        ) {
            val centerX = size.width / 2f
            val stemWidth = 2.5.dp.toPx()
            val stemHeight = HandleStemHeight.toPx()
            val radius = HandleRadius.toPx()
            drawRoundRect(
                color = color,
                topLeft = Offset(centerX - stemWidth / 2f, 0f),
                size = Size(stemWidth, stemHeight + radius),
                cornerRadius = CornerRadius(stemWidth / 2f, stemWidth / 2f)
            )
            drawCircle(
                color = color,
                radius = radius,
                center = Offset(centerX, stemHeight + radius)
            )
        }
    }
}

/** Mirrors Compose's handle anchor: horizontal position of the character, line bottom. */
private fun handleAnchor(layout: TextLayoutResult, offset: Int, isStart: Boolean): Offset {
    val length = layout.layoutInput.text.length
    if (layout.lineCount <= 0) return Offset.Unspecified
    val safeOffset = offset.coerceIn(0, length)
    val line = layout.getLineForOffset(safeOffset)
    if (line >= layout.lineCount) return Offset.Unspecified
    val offsetToCheck = if (isStart) safeOffset else max(safeOffset - 1, 0)
    val usePrimaryDirection = layout.getBidiRunDirection(offsetToCheck) ==
        layout.getParagraphDirection(safeOffset)
    return Offset(
        x = layout.getHorizontalPosition(safeOffset, usePrimaryDirection),
        y = layout.getLineBottom(line)
    )
}
