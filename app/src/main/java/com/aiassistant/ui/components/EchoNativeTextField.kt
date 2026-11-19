package com.aiassistant.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** One cancellable focus/IME transaction owned by the external resize handle. */
internal class EditorKeyboardRequest {
    private var request: Job? = null

    fun cancel() {
        request?.cancel()
        request = null
    }

    fun launch(
        scope: kotlinx.coroutines.CoroutineScope,
        action: EditorTapAction? = null,
        block: suspend () -> Unit
    ) {
        // A no-op handle tap must not cancel an earlier recovery that is still crossing frames.
        if (action == EditorTapAction.Ignore) return
        cancel()
        request = scope.launch { block() }
    }
}

/**
 * 读取当前窗口报告的输入法可见性。
 *
 * 组合期的 [WindowInsets.isImeVisible] 快照会滞后于一次隐藏/显示事务，涉及焦点与键盘的判定
 * 优先读实时窗口 Insets；取不到时回退到快照值。部分设备的悬浮键盘或悬浮窗
 * 可能报告不可见，即使输入法仍在屏幕上，因此 false 不能保证键盘已收起。
 */
internal fun isImeVisibleNow(view: android.view.View, fallback: Boolean): Boolean = runCatching {
    ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime())
}.getOrNull() ?: fallback

/** State sampled by the external resize handle, which sits outside the native field's hit area. */
internal data class EditorGestureStart(
    val focused: Boolean,
    val imeVisible: Boolean
)

/** Only the external handle needs application assistance; native editor taps own focus and IME. */
internal enum class EditorTapAction {
    AssistFocus,
    RevealKeyboard,
    Ignore
}

internal fun editorTapAction(start: EditorGestureStart): EditorTapAction =
    when {
        !start.focused -> EditorTapAction.AssistFocus
        start.imeVisible -> EditorTapAction.Ignore
        else -> EditorTapAction.RevealKeyboard
    }

/**
 * Assists only taps on the external resize handle. Never clears focus or probes the global
 * InputMethodManager: BasicTextField owns its input connection and all taps inside the editor.
 */
internal suspend fun reopenEditorKeyboard(
    action: EditorTapAction,
    requestFocus: () -> Unit,
    isFocused: () -> Boolean,
    isActive: () -> Boolean,
    nextFrame: suspend () -> Unit,
    showKeyboard: () -> Unit,
    isImeVisible: () -> Boolean = { false },
    maxFocusFrames: Int = 3
) {
    if (action == EditorTapAction.Ignore || !isActive()) return
    if (!isFocused()) {
        requestFocus()
        // Do not show a keyboard before the field actually owns focus and an input session.
        if (!awaitEditorFocus(isFocused, isActive, nextFrame, expected = true, maxFrames = maxFocusFrames)) return
    }
    if (isActive() && !isImeVisible()) showKeyboard()
}

/** 最多等待 [maxFrames] 帧观察焦点是否落到 [expected]；返回 false 表示焦点事务没有生效。 */
private suspend fun awaitEditorFocus(
    isFocused: () -> Boolean,
    isActive: () -> Boolean,
    nextFrame: suspend () -> Unit,
    expected: Boolean,
    maxFrames: Int
): Boolean {
    repeat(maxFrames) {
        nextFrame()
        if (!isActive()) return false
        if (isFocused() == expected) return true
    }
    return false
}

/** Wait briefly for a user-tapped window to regain focus; never queue an indefinite show. */
internal suspend fun awaitEditorWindowFocus(
    isActive: () -> Boolean,
    isWindowFocused: () -> Boolean,
    nextFrame: suspend () -> Unit,
    timeoutMillis: Long = 500
): Boolean = kotlinx.coroutines.withTimeoutOrNull(timeoutMillis) {
    while (isActive() && !isWindowFocused()) nextFrame()
    isActive() && isWindowFocused()
} == true

/** 根据调用方提供的焦点和窗口可见性信号判断是否尝试恢复键盘。 */
internal fun shouldRevealImeOnTap(focused: Boolean, imeVisible: Boolean): Boolean =
    focused && !imeVisible

/**
 * 只观察、不消费：手势在 Initial 阶段读取，原生输入框的点击/长按/拖动全部照常处理。
 * 长按（选词）与拖动（选区、滚动）仍完整交给原生字段；这里只为同一次点击的
 * SoftwareKeyboardController.show() 建立一个不重复请求的边界。
 */
@Composable
private fun Modifier.editorTapToRevealIme(
    onDown: () -> Long,
    onUp: (Long, Boolean) -> Unit
): Modifier {
    val latestOnDown by rememberUpdatedState(onDown)
    val latestOnUp by rememberUpdatedState(onUp)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val tapId = latestOnDown()
            var dragged = false
            var released = false
            var longPressed = false
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                        dragged = true
                    }
                    if (change.uptimeMillis - down.uptimeMillis > viewConfiguration.longPressTimeoutMillis) {
                        longPressed = true
                    }
                    if (!change.pressed) {
                        released = true
                        break
                    }
                }
            } finally {
                latestOnUp(tapId, released && !dragged && !longPressed)
            }
        }
    }
}

/**
 * 协调原生字段的 [SoftwareKeyboardController.show] 与同一次点击的恢复请求。
 * 窗口报告键盘可见时跳过 show；报告不可见时根据点击前焦点决定使用原生请求或恢复路径。
 * 部分悬浮键盘场景下窗口信号可能不可靠，报告不可见不保证键盘确实收起。
 * hide 始终透传。
 */
internal class ImeAwareKeyboardController(
    private val delegate: SoftwareKeyboardController?,
    private val isImeVisible: () -> Boolean,
    private val recoverHiddenIme: () -> Unit = {}
) : SoftwareKeyboardController {
    private var tapId = 0L
    private var tapReady = false
    private var showHandledForTap = false
    private var focusedBeforeTap = false

    /** Called from PointerEventPass.Initial before BasicTextField handles the same tap. */
    fun beginTap(focused: Boolean): Long {
        tapId++
        tapReady = false
        showHandledForTap = false
        focusedBeforeTap = focused
        return tapId
    }

    fun finishGesture(id: Long, isTap: Boolean) {
        if (id == tapId) tapReady = isTap
    }

    /** An older gesture's deferred cleanup must not clear a newer gesture. */
    fun endTap(id: Long) {
        if (id != tapId) return
        tapReady = false
        showHandledForTap = false
        focusedBeforeTap = false
    }

    override fun show() {
        if (tapReady && showHandledForTap) return
        if (tapReady) showHandledForTap = true
        if (isImeVisible()) return
        if (!tapReady || !focusedBeforeTap) {
            // Initial focus must stay in Compose's input-session queue. Calling IMM here would
            // race the session start and can make the first keyboard appearance flash.
            delegate?.show()
        } else {
            // The window reports hidden; a focused editor may need the platform recovery path.
            // Do not issue an additional pointer-side show for this tap.
            recoverHiddenIme()
        }
    }

    override fun hide() {
        delegate?.hide()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun rememberEditorKeyboardRequest(
    focusRequester: FocusRequester,
    isFocused: () -> Boolean,
    active: Boolean = true
): Pair<EditorKeyboardRequest, (EditorGestureStart) -> Unit> {
    val scope = rememberCoroutineScope()
    val transaction = remember { EditorKeyboardRequest() }
    val keyboard = LocalSoftwareKeyboardController.current
    val windowInfo = LocalWindowInfo.current
    val view = LocalView.current
    val imeVisible = WindowInsets.isImeVisible
    val latestActive by rememberUpdatedState(active)
    val latestFocused by rememberUpdatedState(isFocused)
    val latestImeVisible by rememberUpdatedState(imeVisible)
    fun imeVisibleNow(): Boolean = isImeVisibleNow(view, latestImeVisible)

    val request: (EditorGestureStart) -> Unit = { start ->
        val action = editorTapAction(start)
        val hadWindowFocus = windowInfo.isWindowFocused
        transaction.launch(scope, if (hadWindowFocus) action else null) {
            if (!hadWindowFocus) {
                val gained = awaitEditorWindowFocus(
                    isActive = { latestActive },
                    isWindowFocused = { windowInfo.isWindowFocused },
                    nextFrame = { withFrameNanos { } }
                )
                if (!gained) return@launch
            }
            val currentAction = if (hadWindowFocus) action else editorTapAction(
                start.copy(focused = latestFocused(), imeVisible = imeVisibleNow())
            )
            reopenEditorKeyboard(
                action = currentAction,
                requestFocus = { focusRequester.requestFocus() },
                isFocused = { latestFocused() },
                isActive = { latestActive && windowInfo.isWindowFocused },
                nextFrame = { withFrameNanos { }; kotlinx.coroutines.yield() },
                showKeyboard = { keyboard?.show() },
                isImeVisible = { imeVisibleNow() }
            )
        }
    }
    return transaction to request
}

/**
 * Keeps the existing controlled-value API while the native editor owns selection, IME and scroll.
 * A parent echo must never replay an older selection into an in-progress handle drag.
 */
@OptIn(ExperimentalFoundationApi::class)
internal class NativeEditorState(initialValue: TextFieldValue) {
    val field = TextFieldState(initialValue.text, initialValue.selection)
    private var previousExternal = initialValue
    private var previousPublished = currentValue()
    private val published = ArrayDeque<TextFieldValue>()

    fun reconcileExternal(value: TextFieldValue) {
        if (value == previousExternal) return
        previousExternal = value
        // Acknowledging an earlier callback must not rewind a newer IME edit or handle drag.
        val acknowledgement = published.indexOfFirst {
            it.text == value.text && it.selection == value.selection
        }
        if (acknowledgement >= 0) {
            repeat(acknowledgement + 1) { published.removeFirst() }
            return
        }
        if (field.text.toString() != value.text || field.selection != value.selection) {
            field.edit {
                if (toString() != value.text) replace(0, length, value.text)
                selection = value.selection
            }
        }
        previousPublished = currentValue()
        published.clear()
    }

    fun currentValue(): TextFieldValue = TextFieldValue(
        text = field.text.toString(),
        selection = field.selection,
        composition = field.composition
    )

    fun valueToPublish(): TextFieldValue? = currentValue().takeUnless { it == previousPublished }
        ?.also {
            previousPublished = it
            published.addLast(it)
        }
}

/** Keep the core text node in the same composition when the window mode changes. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun rememberEditorDecorator(
    decorator: TextFieldDecorator?,
    onPosition: (Offset) -> Unit
): TextFieldDecorator {
    val currentDecorator = rememberUpdatedState(decorator)
    val currentOnPosition = rememberUpdatedState(onPosition)
    return remember {
        TextFieldDecorator { innerTextField ->
            val probed: @Composable () -> Unit = {
                Box(
                    modifier = Modifier.onGloballyPositioned {
                        currentOnPosition.value(it.positionInRoot())
                    },
                    propagateMinConstraints = true
                ) {
                    innerTextField()
                }
            }
            currentDecorator.value?.Decoration(probed) ?: probed()
        }
    }
}

/**
 * The state-based field keeps handle positions in the same coordinate space as its
 * exposed scroll state, including content movement during a drag. Do not add an outer vertical
 * scroll or intercept its bring-into-view requests: doing so detaches handles from that viewport.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun EchoNativeTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle.Default,
    interactionSource: MutableInteractionSource? = null,
    cursorBrush: Brush = SolidColor(Color.Black),
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.Default,
    scrollState: ScrollState = rememberScrollState(),
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    decorator: TextFieldDecorator? = null,
    focusRequester: FocusRequester = remember { FocusRequester() },
    floatingWindow: Boolean = isFloatingWindowHost()
) {
    val editor = remember { NativeEditorState(value) }
    val density = LocalDensity.current.density
    var focused by remember { mutableStateOf(false) }
    var getTextLayout by remember { mutableStateOf<(() -> TextLayoutResult?)?>(null) }
    // Floating windows lay popups out relative to the app window while Compose 1.6.8 anchors the
    // built-in handles in screen coordinates. Draw our own handles there instead.
    var textLayoutInRoot by remember { mutableStateOf(Offset.Zero) }
    val textLayout = getTextLayout?.invoke()
    val selectionColors = LocalTextSelectionColors.current
    val handleColors = remember(selectionColors, floatingWindow) {
        if (floatingWindow) {
            TextSelectionColors(
                handleColor = Color.Transparent,
                backgroundColor = selectionColors.backgroundColor
            )
        } else {
            selectionColors
        }
    }
    // Switching this wrapper while focused recreated the core node in Compose 1.6.8 and could
    // leave its cursor transparent. Keep a stable structure and the origin for floating handles.
    val decoratedField = rememberEditorDecorator(decorator) { textLayoutInRoot = it }
    SideEffect { editor.reconcileExternal(value) }
    val onCurrentValueChange by rememberUpdatedState(onValueChange)
    LaunchedEffect(editor) {
        // The 1.7 state exposes text, selection and composition separately; observing text
        // alone would silently drop cursor movement and IME composition-only changes.
        snapshotFlow { editor.currentValue() }.collect {
            editor.valueToPublish()?.let(onCurrentValueChange)
        }
    }
    // The foundation version in this project follows an out-of-view selection in one jump.
    // Own only that automatic follow; an ordinary UserInput scroll can still cancel this block.
    LaunchedEffect(editor, scrollState, focused, density, lineLimits) {
        if (!focused || lineLimits == TextFieldLineLimits.SingleLine) return@LaunchedEffect
        followEditorSelectionScroll(
            selections = snapshotFlow { editor.field.selection },
            scrollState = scrollState,
            density = density,
            distanceAt = { offset ->
                val layout = getTextLayout?.invoke()
                if (layout == null || layout.layoutInput.text.text != editor.field.text.toString()) 0f
                else {
                    val viewport = layout.size.height - scrollState.maxValue
                    val cursor = layout.getCursorRect(offset.coerceIn(0, layout.layoutInput.text.length))
                    when {
                        viewport <= 0 -> 0f
                        cursor.bottom > scrollState.value + viewport -> cursor.bottom - scrollState.value - viewport
                        cursor.top < scrollState.value -> cursor.top - scrollState.value
                        else -> 0f
                    }
                }
            },
            frameNanos = { withFrameNanos { it } }
        )
    }
    // Compose owns taps, focus and the window-scoped input session. Window transitions must not
    // clear focus; the controller below coordinates show requests using the window's IME signal.
    val view = LocalView.current
    val inputMethodManager = remember(view) {
        view.context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
    }
    // 已聚焦且输入会话存在时，Compose 点击可能调用 SoftwareKeyboardController.show()。
    // Initial pass 记录点击前焦点，由 controller 根据窗口报告的可见性选择 show 路径：
    // 报告可见时跳过；报告不可见时尝试恢复；首次聚焦仍交给 Compose 的输入会话队列。
    // 在悬浮键盘等场景下，窗口报告不可见不一定表示键盘实际已收起。
    val imeVisibleSnapshot = WindowInsets.isImeVisible
    val platformKeyboard = LocalSoftwareKeyboardController.current
    val latestImeVisible = rememberUpdatedState(imeVisibleSnapshot)
    val keyboardController = remember(platformKeyboard, view) {
        ImeAwareKeyboardController(
            delegate = platformKeyboard,
            isImeVisible = {
                isImeVisibleNow(view, latestImeVisible.value)
            },
            recoverHiddenIme = {
                view.requestFocus()
                inputMethodManager?.showSoftInput(view, 0)
            }
        )
    }
    val scope = rememberCoroutineScope()
    CompositionLocalProvider(
        LocalTextSelectionColors provides handleColors,
        LocalSoftwareKeyboardController provides keyboardController
    ) {
        BasicTextField(
            state = editor.field,
            modifier = modifier
                .editorTapToRevealIme(
                    onDown = { keyboardController.beginTap(focused) },
                    onUp = { tapId, isTap ->
                        keyboardController.finishGesture(tapId, isTap)
                        scope.launch {
                            withFrameNanos { }
                            keyboardController.endTap(tapId)
                        }
                    }
                )
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused },
            textStyle = textStyle,
            interactionSource = interactionSource,
            cursorBrush = cursorBrush,
            lineLimits = lineLimits,
            scrollState = scrollState,
            keyboardOptions = keyboardOptions,
            onTextLayout = { result -> getTextLayout = result },
            decorator = decoratedField
        )
    }
    if (floatingWindow && focused) {
        EditorSelectionHandleLayer(
            layout = textLayout,
            selection = editor.field.selection,
            textLayoutInRoot = textLayoutInRoot,
            onSelectionChange = { range ->
                editor.field.edit { selection = range }
            }
        )
    }
}
