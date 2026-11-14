package com.aiassistant

import com.aiassistant.ui.components.SmoothReorderState
import com.aiassistant.ui.components.echoContentInset
import com.aiassistant.ui.components.echoContentInsetFor
import com.aiassistant.ui.screens.settings.cleanModelNames
import com.aiassistant.ui.screens.settings.parseModelList
import com.aiassistant.ui.screens.settings.apiModelDiscoveryEntries
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.ui.text.TextRange
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.graphics.luminance
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.robolectric.Robolectric

/**
 * UI 细节统一修复回归：
 * 1. 模型列表拖拽排序必须保持自定义顺序（cleanModelNames 去重但不重排）
 * 2. SmoothReorderState 抬升量与按 key 高度记录
 */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34], application = android.app.Application::class, manifest = org.robolectric.annotation.Config.NONE)
class UiPolishRegressionTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun pendingHandleKeyboardRequestCanBeCancelledBeforeFocus() = runTest {
        val transaction = com.aiassistant.ui.components.EditorKeyboardRequest()
        var shows = 0
        transaction.launch(this) {
            com.aiassistant.ui.components.reopenEditorKeyboard(
                action = com.aiassistant.ui.components.EditorTapAction.AssistFocus,
                requestFocus = {},
                isFocused = { false },
                isActive = { true },
                nextFrame = { delay(16) },
                showKeyboard = { shows++ }
            )
        }
        runCurrent()

        transaction.cancel()
        advanceTimeBy(100)
        runCurrent()

        assertEquals("发送或隐藏输入栏后不得再执行手柄的延迟显示", 0, shows)
    }

    @Test
    fun adaptiveContentInsetKeepsPhoneGutterAndCentresWideContent() {
        assertEquals("手机宽度保持基础留白", 12f, echoContentInsetFor(411.dp, 12.dp, 720.dp).value, 0.01f)
        assertEquals("平板宽度按 720dp 内容列居中", 292f, echoContentInsetFor(1280.dp, 12.dp, 720.dp).value, 0.01f)
        assertEquals("临界宽度不产生额外留白", 12f, echoContentInsetFor(720.dp, 12.dp, 720.dp).value, 0.01f)
        assertEquals("超宽窗口仍按 720dp 居中", 652f, echoContentInsetFor(2000.dp, 12.dp, 720.dp).value, 0.01f)
    }

    @Test
    fun wideWindowAppliesSymmetricAdaptiveGutters() {
        val bounds = mutableMapOf<String, Rect>()
        var windowWidth = 0
        measureComposeViewport(1280, 800) {
            windowWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
            Box(Modifier.fillMaxSize().padding(horizontal = echoContentInset(base = 12.dp))) {
                Box(
                    Modifier.fillMaxWidth().height(40.dp)
                        .onGloballyPositioned { bounds["row"] = it.boundsInRoot() }
                )
            }
        }
        val row = bounds.getValue("row")
        assertEquals("左右留白必须对称", row.left, 1280f - row.right, 1f)
        assertEquals(
            "留白应与窗口宽度对应的内容列一致",
            echoContentInsetFor(windowWidth.dp, 12.dp, 720.dp).value,
            row.left,
            1f
        )
    }

    @Test
    fun chatReviewPanelStackShrinksShortReviewsAndKeepsInputAdjacent() {
        val bounds = measureReviewInput(statusHeight = 30) {
            Box(Modifier.fillMaxWidth().height(48.dp))
        }

        assertEquals(48f, bounds.getValue("pending").height, .01f)
        assertEquals("短审阅内容不应撑满 440dp 上限", 78f, bounds.getValue("review").height, .01f)
        assertReviewAdjacentToInput(bounds)
    }

    @Test
    fun chatReviewInputLayoutCapsLongReviewAndKeepsInputImmediatelyBelowIt() {
        val bounds = measureReviewInput(statusHeight = 30) {
            Box(Modifier.fillMaxWidth().height(900.dp))
        }

        assertEquals("长审阅内容必须使用 440dp 上限", 440f, bounds.getValue("review").height, .01f)
        assertEquals(410f, bounds.getValue("pending").height, .01f)
        assertReviewAdjacentToInput(bounds)
    }

    @Test
    fun chatReviewInputLayoutReservesConversationSpaceOnSmallScreensAndWithTallInput() {
        for ((screenHeight, inputHeight, reviewHeight) in listOf(
            Triple(400, 80, 200), Triple(640, 360, 160), Triple(300, 180, 0)
        )) {
            val bounds = measureReviewInput(height = screenHeight, inputHeight = inputHeight, statusHeight = 24) {
                Box(Modifier.fillMaxWidth().height(900.dp))
            }

            assertEquals(reviewHeight.toFloat(), bounds.getValue("review").height, .01f)
            assertEquals("输入栏始终保有请求高度", inputHeight.toFloat(), bounds.getValue("input").height, .01f)
            assertReviewAdjacentToInput(bounds)
        }
        for (statusHeight in listOf(0, 30)) {
            val bounds = measureReviewInput(statusHeight = statusHeight) {}

            assertEquals(0f, bounds.getValue("pending").height, .01f)
            assertEquals(statusHeight.toFloat(), bounds.getValue("review").height, .01f)
            assertReviewAdjacentToInput(bounds)
        }
    }

    @Test
    fun realPendingMemoryAndTimelinePanelsStayBoundedAndAdjacentWithStatus() {
        val memory = com.aiassistant.domain.model.PendingMemoryCandidate(
            id = "synthetic-review-memory", distilledContent = "合成设定候选", originalSnippet = "合成来源", conversationId = 1
        )
        val timeline = com.aiassistant.data.repository.AutoTimelineUpdateResult(
            updatedStoryTime = "合成日程", newEvent = null, summaryNotice = "合成摘要", proposalId = "synthetic-review-timeline"
        )
        val bounds = measureReviewInput(statusHeight = 32) {
            com.aiassistant.ui.screens.chat.ChatPendingReviewPanels(
                memory = memory, timeline = timeline, onDismissMemory = {}, onAcceptMemory = { _, _ -> },
                onDismissTimeline = {}, onApplyTimeline = {}
            )
        }

        assertTrue("双候选应有实际可见高度", bounds.getValue("pending").height > 100f)
        assertTrue("短候选不应耗尽 440dp 限额", bounds.getValue("review").height < 440f)
        assertEquals(bounds.getValue("pending").bottom, bounds.getValue("status").top, .01f)
        assertReviewAdjacentToInput(bounds)
    }

    @Test
    fun realLongTimelinePanelScrollsWithinSmallScreenBudget() {
        val timeline = com.aiassistant.data.repository.AutoTimelineUpdateResult(
            updatedStoryTime = null, newEvent = null, summaryNotice = "合成摘要", proposalId = "synthetic-long-review-timeline",
            atemporalSettings = (1..12).map { index ->
                com.aiassistant.utils.AtemporalSettingItem(id = "synthetic-setting-$index", content = "合成长设定条目 $index")
            }
        )
        val bounds = measureReviewInput(height = 480, inputHeight = 100, statusHeight = 24) {
            com.aiassistant.ui.screens.chat.ChatPendingReviewPanels(
                memory = null, timeline = timeline, onDismissMemory = {}, onAcceptMemory = { _, _ -> },
                onDismissTimeline = {}, onApplyTimeline = {}
            )
        }

        assertEquals("长正文受屏幕剩余空间约束", 260f, bounds.getValue("review").height, .01f)
        assertEquals(236f, bounds.getValue("pending").height, .01f)
        assertReviewAdjacentToInput(bounds)
    }

    @Test
    fun reasoningEffortPopupIsAnInputAnchoredCardWithInputImmediatelyBelow() {
        val bounds = measureReasoningPopup()

        assertTrue("思考强度卡片必须实际显示", bounds.getValue("thinking").height > 0f)
        assertTrue("思考强度卡片必须受 220dp 上限约束", bounds.getValue("thinking").height <= 220f)
        assertEquals("思考强度卡片下方应直接接输入区", bounds.getValue("thinking").bottom, bounds.getValue("input").top, .01f)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun rapidHandleTapsKeepOnlyTheLatestKeyboardRequest() = runTest {
        val transaction = com.aiassistant.ui.components.EditorKeyboardRequest()
        val shown = mutableListOf<Int>()
        var focused = false
        repeat(4) { tap ->
            transaction.launch(this) {
                com.aiassistant.ui.components.reopenEditorKeyboard(
                    action = com.aiassistant.ui.components.EditorTapAction.AssistFocus,
                    requestFocus = {},
                    isFocused = { focused },
                    isActive = { true },
                    nextFrame = { delay(16) },
                    showKeyboard = { shown += tap }
                )
            }
            runCurrent()
            advanceTimeBy(4)
        }
        focused = true
        advanceTimeBy(64)
        runCurrent()
        assertEquals(listOf(3), shown)
    }

    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    @Test     fun nativeEditorTapsAreNotInterceptedOrDuplicated() {
        fun resultFromTaps(
            wrapped: Boolean,
            preFocus: Boolean = true,
            hideAndRestoreBeforeTap: Boolean = false,
            tapCount: Int = 1
        ): Pair<Int, Boolean> {
            val activityController = Robolectric.buildActivity(ComponentActivity::class.java).setup()
            val activity = activityController.get()
            val composeView = ComposeView(activity)
            val focusRequester = androidx.compose.ui.focus.FocusRequester()
            var shows = 0
            var focused = false
            val keyboard = object : androidx.compose.ui.platform.SoftwareKeyboardController {
                override fun show() { shows++ }
                override fun hide() { }
            }
            try {
                composeView.setContent {
                    CompositionLocalProvider(
                        LocalDensity provides Density(1f, 1f),
                        androidx.compose.ui.platform.LocalSoftwareKeyboardController provides keyboard
                    ) {
                        val modifier = Modifier.fillMaxWidth().height(100.dp)
                            .then(Modifier.onFocusChanged { focused = it.isFocused })
                        if (wrapped) {
                            com.aiassistant.ui.components.EchoNativeTextField(
                                value = androidx.compose.ui.text.input.TextFieldValue("已有输入内容"),
                                onValueChange = {}, modifier = modifier, focusRequester = focusRequester
                            )
                        } else {
                            val state = androidx.compose.runtime.remember {
                                androidx.compose.foundation.text2.input.TextFieldState("已有输入内容")
                            }
                            androidx.compose.foundation.text2.BasicTextField2(
                                state = state,
                                modifier = modifier.then(Modifier.focusRequester(focusRequester))
                            )
                        }
                    }
                }
                activity.setContentView(composeView, android.widget.FrameLayout.LayoutParams(300, 100))
                val looper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
                looper.idleFor(java.time.Duration.ofMillis(32))
                composeView.measure(
                    android.view.View.MeasureSpec.makeMeasureSpec(300, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(100, android.view.View.MeasureSpec.EXACTLY)
                )
                composeView.layout(0, 0, 300, 100)
                composeView.dispatchWindowFocusChanged(true)
                if (preFocus) focusRequester.requestFocus()
                assertEquals("测试前焦点必须符合场景", preFocus, focused)
                if (hideAndRestoreBeforeTap) {
                    keyboard.hide()
                    composeView.dispatchWindowFocusChanged(false)
                    composeView.dispatchWindowFocusChanged(true)
                    looper.idleFor(java.time.Duration.ofMillis(32))
                }
                shows = 0
                repeat(tapCount) { index ->
                    // 独立单击必须超过系统双击窗口，否则 Compose 会把后一次识别为双击。
                    val now = android.os.SystemClock.uptimeMillis() + index * 400L
                    val down = android.view.MotionEvent.obtain(
                        now, now, android.view.MotionEvent.ACTION_DOWN, 60f, 30f, 0
                    )
                    val up = android.view.MotionEvent.obtain(
                        now, now + 20, android.view.MotionEvent.ACTION_UP, 60f, 30f, 0
                    )
                    try {
                        composeView.dispatchTouchEvent(down)
                        composeView.dispatchTouchEvent(up)
                        looper.idleFor(java.time.Duration.ofMillis(64))
                    } finally {
                        down.recycle()
                        up.recycle()
                    }
                }
                return shows to focused
            } finally {
                composeView.disposeComposition()
                activityController.pause().stop().destroy()
            }
        }

        assertEquals(1 to true, resultFromTaps(wrapped = false))
        assertEquals("包装后必须保留且仅保留原生的一次 show", 1 to true, resultFromTaps(wrapped = true))
        assertEquals(
            "首次聚焦由原生字段完成",
            resultFromTaps(wrapped = false, preFocus = false),
            resultFromTaps(wrapped = true, preFocus = false)
        )
        assertEquals(
            "收起键盘及窗口恢复后，下一次点击仍由原生字段唤出",
            1 to true,
            resultFromTaps(wrapped = true, hideAndRestoreBeforeTap = true)
        )
        val nativeRepeated = resultFromTaps(wrapped = false, tapCount = 3)
        val wrappedRepeated = resultFromTaps(wrapped = true, tapCount = 3)
        assertEquals(
            "已有内容时包装层不得改变原生字段的连续点击手势与显示次数",
            nativeRepeated,
            wrappedRepeated
        )
        assertTrue("已有内容时至少要完成一次原生显示", wrappedRepeated.first >= 1)
    }

    @Test fun tapWaitsOnlyBrieflyForWindowFocusAndStopsWhenInactive() = runTest {
        var focused = false
        var active = true
        var frames = 0
        val regained = com.aiassistant.ui.components.awaitEditorWindowFocus(
            isActive = { active }, isWindowFocused = { focused }, nextFrame = {
                delay(16)
                if (++frames == 2) focused = true
            }
        )
        assertTrue(regained)
        assertEquals(2, frames)
        focused = false
        val cancelled = com.aiassistant.ui.components.awaitEditorWindowFocus(
            isActive = { active }, isWindowFocused = { focused }, nextFrame = {
                delay(16)
                active = false
            }
        )
        assertFalse(cancelled)
        active = true
        val timedOut = com.aiassistant.ui.components.awaitEditorWindowFocus(
            isActive = { active }, isWindowFocused = { false },
            nextFrame = { delay(16) }, timeoutMillis = 50
        )
        assertFalse(timedOut)
    }

    @Test fun keyboardRequestCannotShowBeforeFocusArrives() = runTest {
        var shows = 0
        com.aiassistant.ui.components.reopenEditorKeyboard(
            action = com.aiassistant.ui.components.EditorTapAction.AssistFocus,
            requestFocus = {},
            isFocused = { false },
            isActive = { true },
            nextFrame = { },
            showKeyboard = { shows++ }
        )
        assertEquals(0, shows)
    }

    @Test fun keyboardReopenWaitsForFocusToSettleAcrossFrames() = runTest {
        var focused = false
        var shows = 0
        var frames = 0
        com.aiassistant.ui.components.reopenEditorKeyboard(
            action = com.aiassistant.ui.components.EditorTapAction.AssistFocus,
            requestFocus = {},
            isFocused = { focused },
            isActive = { true },
            nextFrame = {
                frames++
                if (frames >= 2) focused = true
            },
            showKeyboard = { shows++ },
            isImeVisible = { false }
        )
        assertEquals(1, shows)
        assertEquals(2, frames)
    }

    /**
     * 请求失败时正文可能一个字符都没有，气泡高度全部来自状态胶囊与实时 Key 明细：
     * 跟随信号必须覆盖这两项，否则报错信息不会像流式输出那样推动页面上滑。
     */
    @Test fun followSignalCoversStatusAndKeyErrorsWithoutBodyText() {
        val idle = com.aiassistant.ui.screens.chat.chatFollowSignal("", "", null, emptyList())
        val reconnecting = com.aiassistant.ui.screens.chat.chatFollowSignal(
            "", "", "正在尝试重新连接（1/3）…", emptyList()
        )
        val keyError = com.aiassistant.ui.screens.chat.chatFollowSignal(
            "", "", null, listOf("Key #1 (sk-***)：第 1 次请求失败：连接超时")
        )
        assertTrue("状态胶囊文案变化必须形成新的跟随信号", idle != reconnecting)
        assertTrue("实时 Key 报错明细增加必须形成新的跟随信号", idle != keyError)
        assertEquals(
            "状态与正文都不变时不得重复触发滚动",
            reconnecting,
            com.aiassistant.ui.screens.chat.chatFollowSignal("", "", "正在尝试重新连接（1/3）…", emptyList())
        )
    }

    /** 失败收尾贴底与流式跟随同一规则：用户拖动阅读位置后立即让出。 */
    @Test fun failedReplyFollowYieldsToUserDrag() {
        assertTrue(com.aiassistant.ui.screens.chat.shouldFollowFailedReply(autoFollowOutput = true, isDragging = false))
        assertFalse(com.aiassistant.ui.screens.chat.shouldFollowFailedReply(autoFollowOutput = false, isDragging = false))
        assertFalse(com.aiassistant.ui.screens.chat.shouldFollowFailedReply(autoFollowOutput = true, isDragging = true))
    }

    @Test fun externalHandleTapPolicyOnlyAssistsFocusOrRevealsHiddenKeyboard() {
        assertEquals(
            com.aiassistant.ui.components.EditorTapAction.AssistFocus,
            com.aiassistant.ui.components.editorTapAction(
                com.aiassistant.ui.components.EditorGestureStart(focused = false, imeVisible = false)
            )
        )
        assertEquals(
            com.aiassistant.ui.components.EditorTapAction.AssistFocus,
            com.aiassistant.ui.components.editorTapAction(
                com.aiassistant.ui.components.EditorGestureStart(focused = false, imeVisible = true)
            )
        )
        assertEquals(
            com.aiassistant.ui.components.EditorTapAction.RevealKeyboard,
            com.aiassistant.ui.components.editorTapAction(
                com.aiassistant.ui.components.EditorGestureStart(focused = true, imeVisible = false)
            )
        )
        assertEquals(
            com.aiassistant.ui.components.EditorTapAction.Ignore,
            com.aiassistant.ui.components.editorTapAction(
                com.aiassistant.ui.components.EditorGestureStart(focused = true, imeVisible = true)
            )
        )
    }

    @Test fun focusedHandleWithVisibleImeSkipsRedundantShow() = runTest {
        var requests = 0
        var shows = 0
        com.aiassistant.ui.components.reopenEditorKeyboard(
            action = com.aiassistant.ui.components.EditorTapAction.Ignore,
            requestFocus = { requests++ },
            isFocused = { true },
            isActive = { true },
            nextFrame = { error("输入法可见时不应等待焦点帧") },
            showKeyboard = { shows++ },
            isImeVisible = { true }
        )
        assertEquals(0, requests)
        assertEquals(0, shows)
    }

    @Test fun hiddenImeOnFocusedHandleShowsOnceWithoutRestartingFocus() = runTest {
        var requests = 0
        var shows = 0
        com.aiassistant.ui.components.reopenEditorKeyboard(
            action = com.aiassistant.ui.components.EditorTapAction.RevealKeyboard,
            requestFocus = { requests++ },
            isFocused = { true },
            isActive = { true },
            nextFrame = { error("已聚焦的字段不应等待焦点事务") },
            showKeyboard = { shows++ },
            isImeVisible = { false }
        )
        assertEquals("已聚焦时不得重复请求焦点", 0, requests)
        assertEquals("收起后只补一次 show", 1, shows)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun noOpHandleTapDoesNotCancelPendingFocus() = runTest {
        val transaction = com.aiassistant.ui.components.EditorKeyboardRequest()
        var recovered = 0
        transaction.launch(this) {
            delay(32)
            recovered++
        }
        runCurrent()

        transaction.launch(this, com.aiassistant.ui.components.EditorTapAction.Ignore) {
            error("让行点击不得启动新事务")
        }
        advanceTimeBy(64)
        runCurrent()

        assertEquals("无需处理的手柄点击不得取消正在执行的聚焦", 1, recovered)
    }

    @Test fun inactiveHandleRequestCannotRevealKeyboard() = runTest {
        var requests = 0
        var shows = 0
        com.aiassistant.ui.components.reopenEditorKeyboard(
            action = com.aiassistant.ui.components.EditorTapAction.RevealKeyboard,
            requestFocus = { requests++ },
            isFocused = { true },
            isActive = { false },
            nextFrame = { error("失活请求不得等待帧") },
            showKeyboard = { shows++ },
            isImeVisible = { false }
        )
        assertEquals(0, requests)
        assertEquals(0, shows)
    }

    @Test fun paperParagraphsUseDifferentIndentAndKeepTextOutsideTheStyle() {
        val base = androidx.compose.ui.text.TextStyle(fontSize = androidx.compose.ui.unit.TextUnit(17f, androidx.compose.ui.unit.TextUnitType.Sp))
        val deckle = com.aiassistant.ui.components.paperParagraphStyle(base, com.aiassistant.ui.theme.EchoPaperMaterial.Deckle)
        val cotton = com.aiassistant.ui.components.paperParagraphStyle(base, com.aiassistant.ui.theme.EchoPaperMaterial.Cotton)
        val folio = com.aiassistant.ui.components.paperParagraphStyle(base, com.aiassistant.ui.theme.EchoPaperMaterial.Folio)
        assertEquals(androidx.compose.ui.unit.TextUnit(2f, androidx.compose.ui.unit.TextUnitType.Em), deckle.textIndent?.firstLine)
        assertEquals(androidx.compose.ui.text.style.TextIndent.None, cotton.textIndent)
        assertEquals(androidx.compose.ui.unit.TextUnit(.8f, androidx.compose.ui.unit.TextUnitType.Em), folio.textIndent?.firstLine)
        assertEquals(androidx.compose.ui.text.style.TextAlign.Justify, folio.textAlign)
        assertEquals(base.fontSize, deckle.fontSize)
        assertEquals(base, com.aiassistant.ui.components.paperParagraphStyle(base, com.aiassistant.ui.theme.EchoPaperMaterial.None))
    }

    @Test fun paperAppearancePresetsArePersistableAndUseDistinctThemeTokens() {
        val presets = listOf(
            com.aiassistant.utils.EchoAppearancePreset.DECKLE,
            com.aiassistant.utils.EchoAppearancePreset.COTTON
        )
        assertEquals(presets, presets.map { com.aiassistant.utils.EchoAppearancePreset.sanitize(it) })
        assertTrue(com.aiassistant.utils.EchoAppearancePreset.values.containsAll(presets))
        val deckleLight = com.aiassistant.ui.theme.echoColorScheme(com.aiassistant.utils.EchoAppearancePreset.DECKLE, false)
        val cottonLight = com.aiassistant.ui.theme.echoColorScheme(com.aiassistant.utils.EchoAppearancePreset.COTTON, false)
        assertTrue("毛边文库和棉纸诗笺应有不同主色", deckleLight.primary != cottonLight.primary)
        assertTrue("毛边文库和棉纸诗笺应有不同背景", deckleLight.background != cottonLight.background)
        assertTrue("纸张主题使用较平的面板形状", com.aiassistant.ui.theme.echoThemeStyleFor(com.aiassistant.utils.EchoAppearancePreset.DECKLE).flatSurfaces)
        assertTrue(com.aiassistant.ui.theme.echoThemeStyleFor(com.aiassistant.utils.EchoAppearancePreset.COTTON).flatSurfaces)
    }

    @Test fun folioSonarAndBauhausArePersistableAndHaveCompleteDistinctSchemes() {
        val presets = listOf(
            com.aiassistant.utils.EchoAppearancePreset.FOLIO,
            com.aiassistant.utils.EchoAppearancePreset.SONAR,
            com.aiassistant.utils.EchoAppearancePreset.BAUHAUS
        )
        assertEquals(presets, presets.map { com.aiassistant.utils.EchoAppearancePreset.sanitize(it) })
        assertTrue(com.aiassistant.utils.EchoAppearancePreset.values.containsAll(presets))

        fun contrast(fg: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color): Float {
            val lighter = maxOf(fg.luminance(), bg.luminance())
            val darker = minOf(fg.luminance(), bg.luminance())
            return (lighter + .05f) / (darker + .05f)
        }

        val light = presets.associateWith { com.aiassistant.ui.theme.echoColorScheme(it, false) }
        val dark = presets.associateWith { com.aiassistant.ui.theme.echoColorScheme(it, true) }
        assertEquals("三套主题浅色主色必须不同", 3, light.values.map { it.primary }.toSet().size)
        assertEquals("三套主题深色背景必须不同", 3, dark.values.map { it.background }.toSet().size)
        for ((preset, scheme) in light + dark) {
            assertTrue("$preset 必须有独立的面板层级", scheme.surface != scheme.background || scheme.surfaceContainer != scheme.surface)
            assertTrue("$preset 正文对比度不足", contrast(scheme.onBackground, scheme.background) >= 4.5f)
            assertTrue("$preset 主操作对比度不足", contrast(scheme.onPrimary, scheme.primary) >= 4.5f)
            assertTrue("$preset 次操作对比度不足", contrast(scheme.onSecondary, scheme.secondary) >= 4.5f)
            assertTrue("$preset 主容器文字对比度不足", contrast(scheme.onPrimaryContainer, scheme.primaryContainer) >= 4.5f)
            assertTrue("$preset 次容器文字对比度不足", contrast(scheme.onSecondaryContainer, scheme.secondaryContainer) >= 4.5f)
            assertTrue("$preset 第三容器文字对比度不足", contrast(scheme.onTertiaryContainer, scheme.tertiaryContainer) >= 4.5f)
            assertTrue("$preset 错误状态对比度不足", contrast(scheme.onError, scheme.error) >= 4.5f)
            assertTrue("$preset 辅助文字对比度不足", contrast(scheme.onSurfaceVariant, scheme.surface) >= 4.5f)
        }
    }

    @Test fun newThemesCarryDifferentMaterialsShapesAndTypographyAcrossSharedComponents() {
        val folio = com.aiassistant.ui.theme.echoThemeStyleFor(com.aiassistant.utils.EchoAppearancePreset.FOLIO)
        val sonar = com.aiassistant.ui.theme.echoThemeStyleFor(com.aiassistant.utils.EchoAppearancePreset.SONAR)
        val bauhaus = com.aiassistant.ui.theme.echoThemeStyleFor(com.aiassistant.utils.EchoAppearancePreset.BAUHAUS)

        assertEquals(com.aiassistant.ui.theme.EchoPaperMaterial.Folio, folio.paperMaterial)
        assertEquals(com.aiassistant.ui.theme.EchoSurfacePattern.SonarGrid, sonar.surfacePattern)
        assertTrue(sonar.cutCorners)
        assertTrue(bauhaus.hardShadow)
        assertTrue(bauhaus.strongOutline)
        assertTrue(
            "声呐的面板必须使用切角而非普通圆角",
            com.aiassistant.ui.theme.echoShapesFor(com.aiassistant.utils.EchoAppearancePreset.SONAR).medium
                is androidx.compose.foundation.shape.CutCornerShape
        )
        assertTrue(
            "构型的面板必须使用低圆角几何轮廓",
            com.aiassistant.ui.theme.echoShapesFor(com.aiassistant.utils.EchoAppearancePreset.BAUHAUS).medium
                is androidx.compose.foundation.shape.RoundedCornerShape
        )
        assertEquals(
            androidx.compose.ui.text.font.FontFamily.Monospace,
            com.aiassistant.ui.theme.echoTypographyFor(com.aiassistant.utils.EchoAppearancePreset.SONAR).titleMedium.fontFamily
        )
        assertEquals(
            androidx.compose.ui.text.font.FontWeight.Bold,
            com.aiassistant.ui.theme.echoTypographyFor(com.aiassistant.utils.EchoAppearancePreset.BAUHAUS).titleLarge.fontWeight
        )
        assertTrue(
            "拾页正文应使用纸本字体",
            com.aiassistant.ui.theme.echoTypographyFor(com.aiassistant.utils.EchoAppearancePreset.FOLIO).bodyLarge.fontFamily
                != androidx.compose.ui.text.font.FontFamily.Default
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun frequentSelectionUpdatesKeepScrollingAndIdleReleasesTheMutationLock() = runTest {
        val selections = MutableStateFlow(TextRange.Zero)
        var position = 0f
        val scrollState = ScrollableState { delta -> position += delta; delta }
        val follower = backgroundScope.launch {
            com.aiassistant.ui.components.followEditorSelectionScroll(
                selections, scrollState, 1f, distanceAt = { 1_000f },
                frameNanos = { delay(16); testScheduler.currentTime * 1_000_000L }
            )
        }
        runCurrent()
        try {
            repeat(20) { index ->
                selections.value = TextRange(0, index + 2)
                runCurrent()
                advanceTimeBy(4)
                runCurrent()
                if (index == 9) assertTrue("选区更新快于帧率时也必须滚动", position > 0f)
            }
            assertTrue(follower.isActive)
            assertTrue(position > 0f)
            assertTrue(position <= 144f * testScheduler.currentTime / 1_000f + .001f)
            advanceTimeBy(180)
            runCurrent()
            assertTrue("空闲选区必须释放滚动锁", !scrollState.isScrollInProgress)
            val settled = position
            advanceTimeBy(80)
            runCurrent()
            assertEquals("空闲后不继续追赶远处选区", settled, position, .001f)
            scrollState.scroll(MutatePriority.Default) { scrollBy(7f) }
            assertEquals(settled + 7f, position, .001f)
        } finally { follower.cancelAndJoin() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun selectionFollowRestartsAfterUserInputWithoutCollapsingTheSelection() = runTest {
        val selections = MutableStateFlow(TextRange.Zero)
        var position = 0f
        val scrollState = ScrollableState { delta -> position += delta; delta }
        val follower = backgroundScope.launch {
            com.aiassistant.ui.components.followEditorSelectionScroll(
                selections, scrollState, 1f, distanceAt = { 1_000f },
                frameNanos = { delay(16); testScheduler.currentTime * 1_000_000L }
            )
        }
        runCurrent()
        try {
            selections.value = TextRange(0, 8)
            runCurrent()
            advanceTimeBy(64)
            runCurrent()
            assertTrue(position > 0f)
            assertTrue(scrollState.isScrollInProgress)
            scrollState.scroll(MutatePriority.UserInput) { scrollBy(-5f) }
            runCurrent()
            assertTrue("手动滚动不能取消选区观察器", follower.isActive)
            assertTrue(!scrollState.isScrollInProgress)
            val manuallyScrolled = position
            advanceTimeBy(32)
            runCurrent()
            assertEquals(manuallyScrolled, position, .001f)
            selections.value = TextRange(0, 9)
            runCurrent()
            advanceTimeBy(48)
            runCurrent()
            assertTrue("非折叠选区继续变化时须恢复限速", position > manuallyScrolled)
            assertTrue(follower.isActive)
            advanceTimeBy(180)
            runCurrent()
            assertTrue(!scrollState.isScrollInProgress)
        } finally { follower.cancelAndJoin() }
    }

    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    @Test fun delayedEditorAcknowledgementsCannotRewindAnImeEditOrHandleDrag() {
        val initial = androidx.compose.ui.text.input.TextFieldValue("合成编辑内容", androidx.compose.ui.text.TextRange(2))
        val editor = com.aiassistant.ui.components.NativeEditorState(initial)
        editor.field.edit { replace(2, 2, "新增"); selectCharsIn(androidx.compose.ui.text.TextRange(3)) }
        val published = requireNotNull(editor.valueToPublish())
        editor.field.edit { replace(3, 3, "后续"); selectCharsIn(androidx.compose.ui.text.TextRange(2, 6)) }
        val latest = editor.currentValue()
        editor.reconcileExternal(published)
        assertEquals("延迟的文本/选区回声不能覆盖新编辑", latest, editor.currentValue())
        assertEquals("未发布的新编辑不能被确认旧值时吞掉", latest, editor.valueToPublish())
        editor.field.edit { selectCharsIn(androidx.compose.ui.text.TextRange(1, 7)) }
        val dragged = editor.currentValue()
        editor.reconcileExternal(latest)
        assertEquals(dragged, editor.currentValue())
        assertEquals(dragged, editor.valueToPublish())
        editor.reconcileExternal(androidx.compose.ui.text.input.TextFieldValue("外部替换", androidx.compose.ui.text.TextRange(4)))
        assertEquals("外部替换", editor.currentValue().text)
        assertEquals(androidx.compose.ui.text.TextRange(4), editor.currentValue().selection)
    }

    @Test fun stringParentEchoOnlyAcknowledgesTextAndDoesNotReplaceNewerLocalSelection() {
        val acknowledgements = com.aiassistant.ui.components.EditorTextAcknowledgements("初始")
        acknowledgements.published("合成第一段")
        acknowledgements.published("合成第一段与后续")
        assertTrue(!acknowledgements.isReplacement("初始"))
        assertTrue(!acknowledgements.isReplacement("合成第一段"))
        assertTrue(!acknowledgements.isReplacement("合成第一段与后续"))
        assertTrue(acknowledgements.isReplacement(""))
        assertTrue(!acknowledgements.isReplacement(""))
        assertTrue(acknowledgements.isReplacement("外部插入"))
    }

    @Test fun selectionScrollSpeedIsBoundedByTimeDensityAndDistance() {
        fun step(distance: Float, density: Float = 1f, seconds: Float = 1f / 60f) =
            com.aiassistant.ui.components.editorSelectionScrollStep(distance, density, seconds)
        assertEquals("远距离选区应提速到 144dp/s，避免旧版 88dp/s 过慢", 144f / 60f, step(1000f), .001f)
        assertTrue("近边缘仍应保留精细跟随", step(24f) < 72f / 60f)
        assertEquals(step(1000f) * 3, step(3000f, 3f), .001f)
        assertEquals(-step(1000f), step(-1000f), .001f)
        assertEquals(.1f, step(.1f), .001f)
        assertEquals(0f, step(0f), 0f)
        assertEquals(0f, step(Float.NaN), 0f)
        assertTrue("卡帧后不应追赶跳滚", step(1000f, seconds = 1f) <= 7.2001f)
        assertEquals(step(1000f, seconds = 1f / 30f), 2 * step(1000f), .001f)
    }

    @Test fun dialogBoundsRespectTheCurrentViewportIncludingKeyboardSizedWindows() {
        fun bounds(width: Int, height: Int, expanded: Boolean = false) =
            com.aiassistant.ui.components.echoDialogBounds(
                androidx.compose.ui.unit.Dp(width.toFloat()),
                androidx.compose.ui.unit.Dp(height.toFloat()),
                if (expanded) com.aiassistant.ui.components.EchoDialogSize.Expanded
                else com.aiassistant.ui.components.EchoDialogSize.Standard
            )
        val phone = bounds(328, 640)
        assertEquals(328f, phone.width.value, 0f)
        assertTrue(phone.maximumHeight.value in 0f..640f)
        assertEquals(480f, bounds(1000, 900).width.value, 0f)
        assertEquals(640f, bounds(1000, 900, expanded = true).width.value, 0f)
        assertEquals(phone.width, bounds(328, 640, expanded = true).width)
        val keyboardWindow = bounds(328, 68)
        assertTrue("No forced 240dp minimum may overflow the remaining IME viewport",
            keyboardWindow.maximumHeight.value in 0f..68f)
        assertEquals(0f, bounds(0, 0).maximumHeight.value, 0f)
        assertEquals(0f, bounds(-1, -1).width.value, 0f)
    }

    @Test fun currentModelRemainsInTheMenuWithoutMergingEqualNamesAcrossProviders() {
        fun option(config: Long, model: String) = com.aiassistant.domain.model.ChatModelOption(
            apiConfigId = config, configName = "Synthetic config", provider = "Synthetic provider",
            apiType = "openai", modelName = model
        )
        val current = option(1, "synthetic-model")
        val otherProvider = option(2, "synthetic-model")
        val choices = com.aiassistant.ui.screens.chat.chatSettingsModelOptions(
            current, listOf(otherProvider, current, option(3, ""))
        )
        assertEquals(listOf(current, otherProvider), choices)
        assertEquals(listOf(current),
            com.aiassistant.ui.screens.chat.chatSettingsModelOptions(current, emptyList()))
        assertTrue(com.aiassistant.ui.screens.chat.chatSettingsModelOptions(null, emptyList()).isEmpty())
    }

    @Test fun allPresetThemeTextPairsMeetReadableContrastInBothModes() {
        fun contrast(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color): Float =
            (maxOf(a.luminance(), b.luminance()) + 0.05f) / (minOf(a.luminance(), b.luminance()) + 0.05f)
        for (preset in com.aiassistant.utils.EchoAppearancePreset.values) {
            for (dark in listOf(false, true)) {
                val colors = com.aiassistant.ui.theme.echoColorScheme(preset, dark)
                val pairs = listOf(
                    "page" to (colors.onBackground to colors.background),
                    "body" to (colors.onSurface to colors.surface),
                    "secondary text" to (colors.onSurfaceVariant to colors.surfaceVariant),
                    "primary action" to (colors.onPrimary to colors.primary),
                    "secondary action" to (colors.onSecondary to colors.secondary),
                    "tertiary action" to (colors.onTertiary to colors.tertiary),
                    "error action" to (colors.onError to colors.error),
                    "selected option" to (colors.onPrimaryContainer to colors.primaryContainer),
                    "secondary container" to (colors.onSecondaryContainer to colors.secondaryContainer),
                    "tertiary container" to (colors.onTertiaryContainer to colors.tertiaryContainer),
                    "error container" to (colors.onErrorContainer to colors.errorContainer),
                    "menu" to (colors.onSurface to colors.surfaceContainer),
                    "inverse" to (colors.inverseOnSurface to colors.inverseSurface)
                )
                pairs.forEach { (role, pair) ->
                    val ratio = contrast(pair.first, pair.second)
                    assertTrue("$preset dark=$dark $role contrast=$ratio", ratio >= 4.5f)
                }
            }
        }
    }

    @Test fun solidBackgroundSwatchesKeepTheirChosenColorInDarkMode() {
        val presets = com.aiassistant.utils.BackgroundImageManager.PRESET_SOLID_COLORS
        assertEquals(14, presets.map { it.id }.distinct().size)
        presets.forEach { assertEquals(it.lightColorInt, it.darkColorInt) }
        val darkSwatches = presets.filter { androidx.compose.ui.graphics.Color(it.lightColorInt).luminance() < 0.1f }
        assertEquals(7, darkSwatches.size)
        assertEquals(7, darkSwatches.map { it.lightColorInt }.distinct().size)
        assertEquals(0xFFFFFFFF.toInt(), presets.single { it.id == "pure_white" }.lightColorInt)
        assertEquals(0xFF000000.toInt(), presets.single { it.id == "pure_black" }.darkColorInt)
        assertTrue(darkSwatches.filter { it.id != "pure_black" }.all { (it.lightColorInt and 0xFFFFFF) != 0 })
    }


    @Test fun recentHomeStoryUsesLatestVisibleStoryRatherThanPinnedOrOrdinaryConversation() {
        fun conversation(id: Long, updated: Long, pinned: Boolean = false, tags: String? = null) =
            com.aiassistant.domain.model.Conversation(id = id, title = "Synthetic $id", apiConfigId = 1,
                modelName = "synthetic-model", updatedAt = updated, isPinned = pinned, tags = tags)
        val pinnedStory = conversation(1, 10, pinned = true)
        val newestStory = conversation(2, 20)
        val ordinary = conversation(3, 100)
        val hiddenStory = conversation(4, 200, tags = "hidden")
        val privateStory = conversation(5, 300, tags = "private")
        val sessions = listOf(1L, 2L, 4L, 5L, 99L).map {
            com.aiassistant.domain.model.RoleplaySession(id = it, conversationId = it)
        }
        val result = com.aiassistant.ui.screens.home.recentHomeStory(
            listOf(pinnedStory, hiddenStory, ordinary, newestStory, privateStory), sessions)
        assertEquals(newestStory, result?.conversation)
        assertEquals(2L, result?.session?.conversationId)
        assertEquals(null, com.aiassistant.ui.screens.home.recentHomeStory(
            listOf(ordinary, hiddenStory, privateStory), sessions))
        assertEquals(null, com.aiassistant.ui.screens.home.recentHomeStory(emptyList(), sessions))
        assertEquals(null, com.aiassistant.ui.screens.home.recentHomeStory(listOf(newestStory), emptyList()))
    }

    @Test fun homeAnchorIndicesIncludePresentationAndPinnedSectionWithoutShiftingCollapsedEntries() {
        fun conversation(id: Long, pinned: Boolean) = com.aiassistant.domain.model.Conversation(
            id = id, title = "Synthetic $id", apiConfigId = 1, modelName = "synthetic-model", isPinned = pinned)
        val pinned = listOf(conversation(1, true), conversation(2, true))
        val ordinary = listOf(conversation(3, false), conversation(4, false))
        // Presentation at 0, pinned header at 1, pinned rows 2/3, other header 4, normal rows 5/6.
        val expanded = com.aiassistant.ui.screens.home.buildHomeAnchorItems(pinned + ordinary, true, 1)
        assertEquals(listOf("Synthetic 1", "Synthetic 2", "Synthetic 3", "Synthetic 4"), expanded.map { it.title })
        assertEquals(listOf(2, 3, 5, 6), expanded.map { it.itemIndex })
        val collapsed = com.aiassistant.ui.screens.home.buildHomeAnchorItems(pinned + ordinary, false, 1)
        assertEquals(listOf("Synthetic 3", "Synthetic 4"), collapsed.map { it.title })
        assertEquals(listOf(3, 4), collapsed.map { it.itemIndex })
        // Search and folder filters remove the presentation, with no phantom offset.
        assertEquals(listOf(1, 2, 4, 5),
            com.aiassistant.ui.screens.home.buildHomeAnchorItems(pinned + ordinary, true, 0).map { it.itemIndex })
        assertEquals(listOf(0, 1),
            com.aiassistant.ui.screens.home.buildHomeAnchorItems(ordinary, true, 0).map { it.itemIndex })
        assertEquals(listOf(1, 2),
            com.aiassistant.ui.screens.home.buildHomeAnchorItems(ordinary, true, 1).map { it.itemIndex })
        assertTrue(com.aiassistant.ui.screens.home.buildHomeAnchorItems(pinned, false, 1).isEmpty())
        assertTrue(com.aiassistant.ui.screens.home.buildHomeAnchorItems(emptyList(), true, 1).isEmpty())
    }

    @Test fun editorParentEchoPreservesSelectionAndComposition() {
        val value = androidx.compose.ui.text.input.TextFieldValue("合成输入文本",
            selection = androidx.compose.ui.text.TextRange(2), composition = androidx.compose.ui.text.TextRange(1, 3))
        assertEquals(value, com.aiassistant.ui.components.reconcileEditorText(value, value.text))
        val replacement = com.aiassistant.ui.components.reconcileEditorText(value, "外部引用")
        assertEquals(androidx.compose.ui.text.TextRange(4), replacement.selection)
        assertEquals(null, replacement.composition)
        assertEquals(androidx.compose.ui.text.TextRange.Zero,
            com.aiassistant.ui.components.reconcileEditorText(value, "").selection)
    }

    @Test fun editorLayoutAndFocusChangesDoNotFollowOldCursor() {
        val old = androidx.compose.ui.text.input.TextFieldValue("合成长文本")
        val clicked = old.copy(selection = androidx.compose.ui.text.TextRange(3))
        assertTrue(!com.aiassistant.ui.components.shouldFollowEditorCursor(true, old, old, old.text))
        assertTrue(!com.aiassistant.ui.components.shouldFollowEditorCursor(false, old, clicked, clicked.text))
        assertTrue(!com.aiassistant.ui.components.shouldFollowEditorCursor(true, old, clicked, "过期布局"))
        assertTrue(com.aiassistant.ui.components.shouldFollowEditorCursor(true, old, clicked, clicked.text))
        val typed = clicked.copy(text = "合成新增长文本")
        assertTrue(com.aiassistant.ui.components.shouldFollowEditorCursor(true, clicked, typed, typed.text))
    }

    @Test fun selectionToolbarPositionUsesWindowCoordinatesAndStaysInsideBounds() {
        val position = com.aiassistant.ui.components.selectionToolbarPosition(
            selection = androidx.compose.ui.geometry.Rect(100f, 160f, 180f, 190f),
            rootInWindow = androidx.compose.ui.unit.IntOffset(20, 40),
            window = androidx.compose.ui.unit.IntSize(400, 800),
            popup = androidx.compose.ui.unit.IntSize(220, 48),
            marginPx = 16,
            gapPx = 10
        )
        assertEquals(50, position.x)
        assertEquals(142, position.y)

        val bottom = com.aiassistant.ui.components.selectionToolbarPosition(
            selection = androidx.compose.ui.geometry.Rect(150f, 760f, 200f, 790f),
            rootInWindow = androidx.compose.ui.unit.IntOffset.Zero,
            window = androidx.compose.ui.unit.IntSize(400, 800),
            popup = androidx.compose.ui.unit.IntSize(220, 48),
            marginPx = 16,
            gapPx = 10
        )
        assertTrue(bottom.x in 16..164)
        assertTrue(bottom.y in 16..736)
    }

    private val testClock = object : androidx.compose.runtime.MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            return onFrame(System.nanoTime())
        }
    }
    private val testScope = CoroutineScope(Dispatchers.Unconfined + testClock)

    @Test
    fun cleanModelNames_preservesCustomOrder() {
        // 模拟拖拽后 availableModels 的顺序（后添加的排到前面）
        val reordered = listOf("gpt-6-astra", "claude-sonnet-4-6", "deepseek-flash", "qwen-max")
        val cleaned = cleanModelNames(reordered)
        assertEquals(reordered, cleaned)
    }

    @Test
    fun cleanModelNames_dedupesWithoutReorder() {
        val withDup = listOf("model-b", "model-a", "model-b", "model-c", "model-a")
        val cleaned = cleanModelNames(withDup)
        assertEquals(listOf("model-b", "model-a", "model-c"), cleaned)
    }

    @Test
    fun parseModelList_preservesJsonArrayOrder() {
        val raw = """["zeta-model","alpha-model","mid-model"]"""
        assertEquals(listOf("zeta-model", "alpha-model", "mid-model"), parseModelList(raw))
    }

    @Test fun modelDiscoveryOnlyMarksMissingModelsAfterSuccessfulFetchAndKeepsThemFirst() {
        val configured = listOf("synthetic-kept", "synthetic-missing", "synthetic-other-missing")
        assertTrue(apiModelDiscoveryEntries(configured, null).isEmpty())
        val rows = apiModelDiscoveryEntries(configured, listOf("synthetic-new", "synthetic-kept", "synthetic-new"))
        assertEquals(listOf("synthetic-missing", "synthetic-other-missing", "synthetic-new", "synthetic-kept"), rows.map { it.model })
        assertEquals(listOf(true, true, false, false), rows.map { it.isUnavailable })
        assertEquals(listOf(true, true, false, true), rows.map { it.isAdded })
        assertTrue(apiModelDiscoveryEntries(configured, null).none { it.isUnavailable })
    }

    @Test fun modelDiscoveryRemovalAndReaddingUpdateBothMissingAndReturnedRows() {
        val fetched = listOf("synthetic-current", "synthetic-new")
        val configured = listOf("synthetic-missing", "synthetic-current")
        val afterRemoval = apiModelDiscoveryEntries(configured - "synthetic-missing" - "synthetic-current", fetched)
        assertEquals(fetched, afterRemoval.map { it.model })
        assertTrue(afterRemoval.none { it.isAdded || it.isUnavailable })
        val afterAdding = apiModelDiscoveryEntries(listOf("synthetic-current"), fetched)
        assertTrue(afterAdding.single { it.model == "synthetic-current" }.isAdded)
        assertTrue(!afterAdding.single { it.model == "synthetic-new" }.isAdded)
    }

    @Test fun modelDiscoverySearchIncludesMissingModelsAndDoesNotTruncateLargeLists() {
        val fetched = (0..124).map { "synthetic-model-$it" }
        val configured = listOf("synthetic-missing-model", "synthetic-model-124")
        val all = apiModelDiscoveryEntries(configured, fetched)
        assertEquals(126, all.size)
        assertEquals("synthetic-missing-model", all.first().model)
        assertTrue(all.last().isAdded)
        assertEquals(listOf("synthetic-missing-model"), apiModelDiscoveryEntries(configured, fetched, " MISSING ").map { it.model })
        assertTrue(apiModelDiscoveryEntries(configured, fetched, "no-match").isEmpty())
    }

    @Test
    fun smoothReorder_liftAndKeyHeight() {
        val state = SmoothReorderState(testScope)
        state.setItemHeight("k0", 90f)
        state.setItemHeight(0, 90f)
        assertEquals(90f, state.getItemHeight("k0", 0), 0.001f)
        assertEquals(90f, state.getItemHeight(0), 0.001f)

        assertEquals(0f, state.getItemLift(), 0.001f)
        state.onDragStart(0)
        // Unconfined 调度下 lift 动画立即完成
        assertEquals(1f, state.getItemLift(), 0.001f)

        state.onDragFinish()
        assertEquals(0f, state.getItemLift(), 0.001f)
    }

    @Test
    fun smoothReorder_swapAnimatesWithoutThrowing() {
        val state = SmoothReorderState(testScope)
        state.setItemHeight("a", 80f)
        state.setItemHeight("b", 80f)
        state.onAnimateSwap(fromKey = "a", toKey = "b", fromIndex = 0, toIndex = 1)
        assertTrue(true)
    }

    @Test
    fun contextFallbackChoice_values() {
        val choices = com.aiassistant.domain.model.ContextFallbackChoice.entries
        assertEquals(3, choices.size)
        assertTrue(choices.contains(com.aiassistant.domain.model.ContextFallbackChoice.FALLBACK))
        assertTrue(choices.contains(com.aiassistant.domain.model.ContextFallbackChoice.IGNORE))
        assertTrue(choices.contains(com.aiassistant.domain.model.ContextFallbackChoice.PERMANENTLY_IGNORE))
    }

    @Test
    fun providerOrdering_sortsAccordingToSavedOrder() {
        val savedOrder = listOf(103L, 101L, 102L)
        val orderMap = savedOrder.withIndex().associate { it.value to it.index }

        data class MockProvider(val id: Long, val name: String, val isDefault: Boolean)
        val list = listOf(
            MockProvider(101L, "Provider A", false),
            MockProvider(102L, "Provider B", true),
            MockProvider(103L, "Provider C", false),
            MockProvider(104L, "Provider D", false)
        )

        val sorted = list.sortedWith(
            compareBy<MockProvider> { orderMap[it.id] ?: Int.MAX_VALUE }
                .thenByDescending { it.isDefault }
                .thenBy { it.name }
        )

        assertEquals(listOf(103L, 101L, 102L, 104L), sorted.map { it.id })
    }
}

private fun assertReviewAdjacentToInput(bounds: Map<String, Rect>) {
    assertEquals("输入框必须紧贴审阅区", bounds.getValue("review").bottom, bounds.getValue("input").top, .01f)
    assertEquals("状态提示必须紧贴候选内容", bounds.getValue("pending").bottom, bounds.getValue("status").top, .01f)
}

private fun measureReviewInput(
    width: Int = 360,
    height: Int = 800,
    inputHeight: Int = 80,
    statusHeight: Int = 30,
    pendingReview: @Composable () -> Unit
): Map<String, Rect> {
    val bounds = mutableMapOf<String, Rect>()
    measureComposeViewport(width, height) {
        Box(Modifier.fillMaxSize()) {
                com.aiassistant.ui.screens.chat.ChatReviewInputLayout(
                    review = {
                        com.aiassistant.ui.screens.chat.ChatReviewPanelStack(
                            modifier = Modifier.onGloballyPositioned { bounds["review"] = it.boundsInRoot() },
                            pendingReview = {
                                Box(Modifier.fillMaxWidth().onGloballyPositioned { bounds["pending"] = it.boundsInRoot() }) {
                                    pendingReview()
                                }
                            },
                            status = {
                                Box(
                                    Modifier.fillMaxWidth().height(statusHeight.dp)
                                        .onGloballyPositioned { bounds["status"] = it.boundsInRoot() }
                                )
                            }
                        )
                    },
                    input = {
                        Box(
                            Modifier.fillMaxWidth().height(inputHeight.dp)
                                .onGloballyPositioned { bounds["input"] = it.boundsInRoot() }
                        )
                    }
                )
        }
    }
    return bounds.toMap()
}

private fun measureReasoningPopup(): Map<String, Rect> {
    val bounds = mutableMapOf<String, Rect>()
    measureComposeViewport(width = 360, height = 400) {
        androidx.compose.foundation.layout.Column {
            com.aiassistant.ui.screens.chat.ReasoningEffortPopupCard(
                enableThinking = true, thinkingEffort = "medium", modelName = "synthetic-model", apiType = "openai",
                onEffortSelected = { _, _ -> }, onClose = {},
                modifier = Modifier.weight(1f, fill = false).onGloballyPositioned { bounds["thinking"] = it.boundsInRoot() }
            )
            Box(Modifier.fillMaxWidth().height(120.dp).onGloballyPositioned { bounds["input"] = it.boundsInRoot() })
        }
    }
    return bounds.toMap()
}

private fun measureComposeViewport(width: Int, height: Int, content: @Composable () -> Unit) {
    val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    val activity = controller.get()
    val composeView = ComposeView(activity)
    val scaleKey = android.provider.Settings.Global.ANIMATOR_DURATION_SCALE
    val previousScale = android.provider.Settings.Global.getFloat(activity.contentResolver, scaleKey, 1f)
    android.provider.Settings.Global.putFloat(activity.contentResolver, scaleKey, 0f)
    try {
        composeView.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                androidx.compose.material3.MaterialTheme { content() }
            }
        }
        // Keep the test viewport explicit even when the synthetic activity decor performs a
        // follow-up layout using its default display size. One dp equals one pixel in these tests.
        activity.setContentView(composeView, android.widget.FrameLayout.LayoutParams(width, height))
        val mainLooper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        mainLooper.idleFor(java.time.Duration.ofMillis(32))
        val widthSpec = android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY)
        val heightSpec = android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY)
        repeat(2) {
            composeView.measure(widthSpec, heightSpec)
            composeView.layout(0, 0, width, height)
        }
        assertEquals("测量视口宽度必须固定", width, composeView.width)
        assertEquals("测量视口高度必须固定", height, composeView.height)
    } finally {
        composeView.disposeComposition()
        controller.pause().stop().destroy()
        android.provider.Settings.Global.putFloat(activity.contentResolver, scaleKey, previousScale)
    }
}
