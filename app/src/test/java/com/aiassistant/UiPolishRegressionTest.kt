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
    fun pendingTimelineReviewShowsAndTriggersReExtractIconForSuccessAndFailure() {
        val extracted = com.aiassistant.data.repository.AutoTimelineUpdateResult(
            updatedStoryTime = "第2天·清晨",
            newEvent = com.aiassistant.utils.TimelineEventItem(timeTag = "第2天·清晨", content = "合成角色抵达古塔"),
            summaryNotice = "本轮提取结果",
            atemporalSettings = listOf(com.aiassistant.utils.AtemporalSettingItem(content = "古塔禁止火焰魔法")),
            proposalId = "synthetic-extracted-reextract"
        )
        val failed = com.aiassistant.data.repository.AutoTimelineUpdateResult(
            updatedStoryTime = null,
            newEvent = null,
            summaryNotice = "本轮提取未完成",
            extractionErrorMessage = "模型经 3 次尝试仍未返回有效的提取 JSON，请点击重新提取",
            proposalId = "synthetic-failed-reextract"
        )

        for (proposal in listOf(extracted, failed)) {
            val reExtracted = mutableListOf<String>()
            renderAndClickSemanticsNode(contentDescription = "重新提取", useDialogWindow = false) {
                com.aiassistant.ui.screens.chat.ChatPendingReviewPanels(
                    memory = null,
                    timeline = proposal,
                    onDismissMemory = {},
                    onAcceptMemory = { _, _ -> },
                    onDismissTimeline = {},
                    onApplyTimeline = {},
                    onReExtractTimeline = { reExtracted += it.proposalId }
                )
            }
            assertEquals("点击重新提取图标应触发当前提案重新读取: ${proposal.proposalId}",
                listOf(proposal.proposalId), reExtracted)
        }
    }

    @org.robolectric.annotation.Config(shadows = [MaterialStringFallbackResources::class])
    @Test
    fun timelineReconcileAndSettingProposalDialogsExposeReExtractIconForSuccessAndFailure() {
        val reconcileCases = listOf(
            com.aiassistant.utils.TimelineReconcileResult(
                currentStoryTime = "第1天·清晨",
                events = mutableListOf(com.aiassistant.utils.TimelineEventItem(timeTag = "第1天·清晨", content = "合成事件")),
                atemporalSettings = mutableListOf(com.aiassistant.utils.AtemporalSettingItem(content = "合成设定")),
                extractionSource = "AI_MODEL",
                modelUsed = "synthetic-model"
            ),
            com.aiassistant.utils.TimelineReconcileResult(
                currentStoryTime = "未确定",
                events = mutableListOf(),
                extractionSource = "EXTRACTION_FAILED",
                modelUsed = "synthetic-model",
                extractionErrorMessage = "经 3 次尝试后提取仍未通过校验"
            )
        )
        for (result in reconcileCases) {
            var reExtractClicks = 0
            renderAndClickSemanticsNode(contentDescription = "重新提取", useDialogWindow = true) {
                val haze = dev.chrisbanes.haze.HazeState()
                com.aiassistant.ui.screens.chat.TimelineReconcileDialog(
                    hazeState = haze,
                    initialResult = result,
                    onDismiss = {},
                    onApply = { _, _, _ -> },
                    onReExtract = { reExtractClicks++ }
                )
            }
            assertEquals("时间轴与多维设定工作台 (${result.extractionSource}) 必须提供可点击的重新提取图标按键", 1, reExtractClicks)
        }

        val proposalCases = listOf(
            com.aiassistant.ui.screens.chat.ProposedSettingBundle(
                newCharacters = listOf(
                    com.aiassistant.utils.ProposedCharacterUpdate(
                        character = com.aiassistant.domain.model.CharacterProfile(name = "合成角色"),
                        isNew = true,
                        summaryOfChanges = "新角色"
                    )
                ),
                summary = "发现新角色",
                sourceText = "合成剧情原文"
            ),
            com.aiassistant.ui.screens.chat.ProposedSettingBundle(
                summary = "故事设定提取失败",
                sourceText = "合成剧情原文",
                errorMessage = "经 3 次尝试返回的故事设定格式仍无效"
            )
        )
        for (bundle in proposalCases) {
            var reExtractClicks = 0
            renderAndClickSemanticsNode(contentDescription = "重新提取", useDialogWindow = true) {
                val haze = dev.chrisbanes.haze.HazeState()
                com.aiassistant.ui.screens.chat.EditableSettingProposalDialog(
                    hazeState = haze,
                    proposal = bundle,
                    onDismiss = {},
                    onApply = { _, _ -> },
                    onReExtract = { reExtractClicks++ }
                )
            }
            assertEquals("故事设定弹窗 (${bundle.summary}) 必须提供可点击的重新提取图标按键", 1, reExtractClicks)
        }
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
    @org.robolectric.annotation.Config(shadows = [CountingInputMethodManager::class, ImeInsetsViewRoot::class, PopupTitleResources::class])
    @Test fun nativeEditorTapsAreNotInterceptedOrDuplicated() {
        fun resultFromTaps(
            wrapped: Boolean,
            preFocus: Boolean = true,
            hideAndRestoreBeforeTap: Boolean = false,
            tapCount: Int = 1,
            imeVisibleBeforeTap: Boolean = true
        ): Triple<Int, Int, Boolean> {
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
                                androidx.compose.foundation.text.input.TextFieldState("已有输入内容")
                            }
                            androidx.compose.foundation.text.BasicTextField(
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
                // Set the same observable root signal that the wrapper reads; do not infer
                // actual floating-keyboard visibility from a missing inset.
                ImeInsetsViewRoot.visible = imeVisibleBeforeTap
                composeView.dispatchApplyWindowInsets(
                    android.view.WindowInsets.Builder()
                        .setInsets(android.view.WindowInsets.Type.ime(),
                            if (imeVisibleBeforeTap) android.graphics.Insets.of(0, 0, 0, 120)
                            else android.graphics.Insets.NONE)
                        .setVisible(android.view.WindowInsets.Type.ime(), imeVisibleBeforeTap)
                        .build()
                )
                looper.idleFor(java.time.Duration.ofMillis(32))
                assertTrue("测试必须取得真实根窗口 Insets", androidx.core.view.ViewCompat.getRootWindowInsets(composeView) != null)
                assertEquals("测试必须模拟指定的实时 IME 可见性", imeVisibleBeforeTap,
                    com.aiassistant.ui.components.isImeVisibleNow(composeView, !imeVisibleBeforeTap))
                shows = 0
                CountingInputMethodManager.showRequests = 0
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
                return Triple(shows, CountingInputMethodManager.showRequests, focused)
            } finally {
                ImeInsetsViewRoot.visible = false
                runCatching { composeView.disposeComposition() }
                runCatching { activityController.pause().stop().destroy() }
                runCatching {
                    val mainLooper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
                    mainLooper.idleFor(java.time.Duration.ofMillis(64))
                    mainLooper.idle()
                }
            }
        }

        val nativeOnce = resultFromTaps(wrapped = false)
        assertEquals("原生字段的点击必须实际调用键盘控制器", 1, nativeOnce.first)
        assertEquals("原生字段点击后应保持焦点", true, nativeOnce.third)
        val wrappedOnce = resultFromTaps(wrapped = true)
        assertEquals("点击后焦点必须保持", true, wrappedOnce.third)
        assertEquals("Insets 显示键盘可见时不得透传 Compose show", 0, wrappedOnce.first)
        assertEquals("Insets 显示键盘可见时不得另向 IMM 请求 show", 0, wrappedOnce.second)
        val wrappedHidden = resultFromTaps(wrapped = true, imeVisibleBeforeTap = false)
        assertEquals("Insets 显示键盘收起时点击后焦点必须保持", true, wrappedHidden.third)
        assertEquals("恢复键盘时不得另外透传 Compose show", 0, wrappedHidden.first)
        assertEquals("Insets 显示键盘收起时必须恰好向 IMM 请求一次 show", 1, wrappedHidden.second)
        assertEquals(
            "首次聚焦仍由原生字段完成",
            true,
            resultFromTaps(wrapped = true, preFocus = false, imeVisibleBeforeTap = false).third
        )
        assertEquals(
            "收起键盘及窗口恢复后点击仍需保持焦点",
            true,
            resultFromTaps(wrapped = true, hideAndRestoreBeforeTap = true,
                imeVisibleBeforeTap = false).third
        )
        val nativeRepeated = resultFromTaps(wrapped = false, tapCount = 3)
        val wrappedRepeated = resultFromTaps(wrapped = true, tapCount = 3)
        assertTrue("原生连续点击对照必须实际请求键盘", nativeRepeated.first >= 1)
        assertEquals("Insets 显示键盘可见时连续点击不得透传 Compose show", 0, wrappedRepeated.first)
        assertEquals("Insets 显示键盘可见时连续点击不得向 IMM 请求 show", 0, wrappedRepeated.second)
        assertEquals("连续点击后焦点必须保持", true, wrappedRepeated.third)
    }

    @Test fun keyboardControllerSwallowsShowWhileImeIsVisible() {
        var forwarded = 0
        var hidden = 0
        val delegate = object : androidx.compose.ui.platform.SoftwareKeyboardController {
            override fun show() { forwarded++ }
            override fun hide() { hidden++ }
        }
        var imeVisible = true
        var recovered = 0
        val controller = com.aiassistant.ui.components.ImeAwareKeyboardController(
            delegate = delegate,
            isImeVisible = { imeVisible },
            recoverHiddenIme = { recovered++ }
        )
        var tap = controller.beginTap(focused = true)
        controller.finishGesture(tap, isTap = true)
        controller.show()
        assertEquals("键盘已显示时不得再请求 show", 0, forwarded)
        assertEquals(0, recovered)
        controller.endTap(tap)
        imeVisible = false
        tap = controller.beginTap(focused = true)
        controller.finishGesture(tap, isTap = true)
        controller.show()
        controller.show()
        assertEquals("键盘已收起时必须走一次恢复路径", 1, recovered)
        assertEquals("恢复时不能同时透传 Compose show", 0, forwarded)
        controller.endTap(tap)
        tap = controller.beginTap(focused = false)
        controller.finishGesture(tap, isTap = true)
        controller.show()
        assertEquals("首次聚焦应交给 Compose 的输入会话", 1, forwarded)
        controller.endTap(tap)
        controller.hide()
        assertEquals("hide 必须始终透传", 1, hidden)
    }

    @Test fun keyboardTapGateDoesNotLetOldCleanupClearANewerGesture() {
        var forwarded = 0
        var recovered = 0
        val controller = com.aiassistant.ui.components.ImeAwareKeyboardController(
            delegate = object : androidx.compose.ui.platform.SoftwareKeyboardController {
                override fun show() { forwarded++ }
                override fun hide() = Unit
            },
            isImeVisible = { false },
            recoverHiddenIme = { recovered++ }
        )
        val oldTap = controller.beginTap(focused = true)
        controller.finishGesture(oldTap, isTap = true)
        val newTap = controller.beginTap(focused = true)
        controller.finishGesture(newTap, isTap = true)
        controller.endTap(oldTap)
        controller.show()
        assertEquals(1, recovered)
        assertEquals(0, forwarded)
        controller.endTap(newTap)

        val drag = controller.beginTap(focused = true)
        controller.finishGesture(drag, isTap = false)
        controller.show()
        assertEquals("拖动或取消不应走点击专用恢复", 1, recovered)
        assertEquals(1, forwarded)
        controller.endTap(drag)
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

    @Test fun nativeTapOnFocusedFieldRevealsHiddenKeyboardOnly() {
        // 已聚焦 + 键盘已收起：必须补一次 show()，否则点击输入框再也唤不出输入法
        assertTrue(com.aiassistant.ui.components.shouldRevealImeOnTap(focused = true, imeVisible = false))
        // 键盘已弹出：不重复 show()
        assertFalse(com.aiassistant.ui.components.shouldRevealImeOnTap(focused = true, imeVisible = true))
        // 未聚焦：交给原生点击请求焦点并唤起输入法，避免双重触发
        assertFalse(com.aiassistant.ui.components.shouldRevealImeOnTap(focused = false, imeVisible = false))
        assertFalse(com.aiassistant.ui.components.shouldRevealImeOnTap(focused = false, imeVisible = true))
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

    @Test fun celadonBlueprintAndLacquerCarryDistinctCraftDetailsTexturesAndContrast() {
        val presets = listOf(
            com.aiassistant.utils.EchoAppearancePreset.CELADON,
            com.aiassistant.utils.EchoAppearancePreset.BLUEPRINT,
            com.aiassistant.utils.EchoAppearancePreset.LACQUER
        )
        assertEquals(presets, presets.map { com.aiassistant.utils.EchoAppearancePreset.sanitize(it) })
        assertTrue(com.aiassistant.utils.EchoAppearancePreset.values.containsAll(presets))

        val celadon = com.aiassistant.ui.theme.echoThemeStyleFor(com.aiassistant.utils.EchoAppearancePreset.CELADON)
        val blueprint = com.aiassistant.ui.theme.echoThemeStyleFor(com.aiassistant.utils.EchoAppearancePreset.BLUEPRINT)
        val lacquer = com.aiassistant.ui.theme.echoThemeStyleFor(com.aiassistant.utils.EchoAppearancePreset.LACQUER)

        assertEquals(com.aiassistant.ui.theme.EchoPaperMaterial.Celadon, celadon.paperMaterial)
        assertEquals(com.aiassistant.ui.theme.EchoSurfacePattern.CeladonCrackle, celadon.surfacePattern)
        assertEquals(com.aiassistant.ui.theme.EchoFrameOrnament.CeramicRim, celadon.frameOrnament)
        assertTrue("青瓷应使用温润大圆角", celadon.panelCornerRadius >= androidx.compose.ui.unit.Dp(18f))

        assertEquals(com.aiassistant.ui.theme.EchoPaperMaterial.None, blueprint.paperMaterial)
        assertEquals(com.aiassistant.ui.theme.EchoSurfacePattern.BlueprintDraft, blueprint.surfacePattern)
        assertEquals(com.aiassistant.ui.theme.EchoFrameOrnament.BlueprintBrackets, blueprint.frameOrnament)
        assertTrue("蓝图应使用精密小圆角", blueprint.panelCornerRadius <= androidx.compose.ui.unit.Dp(4f))

        assertEquals(com.aiassistant.ui.theme.EchoPaperMaterial.Lacquer, lacquer.paperMaterial)
        assertEquals(com.aiassistant.ui.theme.EchoSurfacePattern.LacquerFrame, lacquer.surfacePattern)
        assertEquals(com.aiassistant.ui.theme.EchoFrameOrnament.LacquerDoubleFrame, lacquer.frameOrnament)
        assertTrue("漆匣应使用独立中圆角", lacquer.panelCornerRadius > blueprint.panelCornerRadius && lacquer.panelCornerRadius < celadon.panelCornerRadius)

        assertEquals(
            androidx.compose.ui.text.font.FontFamily.Monospace,
            com.aiassistant.ui.theme.echoTypographyFor(com.aiassistant.utils.EchoAppearancePreset.BLUEPRINT).labelMedium.fontFamily
        )
        assertTrue(
            "青瓷标题应使用衬线字体",
            com.aiassistant.ui.theme.echoTypographyFor(com.aiassistant.utils.EchoAppearancePreset.CELADON).titleLarge.fontFamily
                != androidx.compose.ui.text.font.FontFamily.Default
        )
        assertTrue(
            "漆匣正文应使用衬线字体",
            com.aiassistant.ui.theme.echoTypographyFor(com.aiassistant.utils.EchoAppearancePreset.LACQUER).bodyLarge.fontFamily
                != androidx.compose.ui.text.font.FontFamily.Default
        )
        val base = androidx.compose.ui.text.TextStyle(fontSize = androidx.compose.ui.unit.TextUnit(16f, androidx.compose.ui.unit.TextUnitType.Sp))
        assertEquals(androidx.compose.ui.unit.TextUnit(1.2f, androidx.compose.ui.unit.TextUnitType.Em),
            com.aiassistant.ui.components.paperParagraphStyle(base, com.aiassistant.ui.theme.EchoPaperMaterial.Lacquer).textIndent?.firstLine)
        assertEquals(androidx.compose.ui.text.style.TextIndent.None,
            com.aiassistant.ui.components.paperParagraphStyle(base, com.aiassistant.ui.theme.EchoPaperMaterial.Celadon).textIndent)

        // 纹理贴图在浅深双模式下均可正常生成且尺寸一致
        for (material in listOf(com.aiassistant.ui.theme.EchoPaperMaterial.Celadon, com.aiassistant.ui.theme.EchoPaperMaterial.Lacquer)) {
            for (dark in listOf(false, true)) {
                val bmp = com.aiassistant.ui.theme.createPaperTexture(material, dark, 1f)
                assertTrue(bmp.width >= 128 && bmp.height >= 128)
            }
        }
    }

    @Test fun editionAndScreeningThemesCarryEditorialAndCinemaDetails() {
        val presets = listOf(
            com.aiassistant.utils.EchoAppearancePreset.EDITION,
            com.aiassistant.utils.EchoAppearancePreset.SCREENING
        )
        assertEquals(presets, presets.map { com.aiassistant.utils.EchoAppearancePreset.sanitize(it) })
        assertTrue(com.aiassistant.utils.EchoAppearancePreset.values.containsAll(presets))

        val edition = com.aiassistant.ui.theme.echoThemeStyleFor(com.aiassistant.utils.EchoAppearancePreset.EDITION)
        val screening = com.aiassistant.ui.theme.echoThemeStyleFor(com.aiassistant.utils.EchoAppearancePreset.SCREENING)

        assertEquals(com.aiassistant.ui.theme.EchoPaperMaterial.Newsprint, edition.paperMaterial)
        assertEquals(com.aiassistant.ui.theme.EchoSurfacePattern.NewsRegister, edition.surfacePattern)
        assertEquals(com.aiassistant.ui.theme.EchoFrameOrnament.CropMarks, edition.frameOrnament)
        assertTrue("晨刊应使用纸片平面材质", edition.flatSurfaces)
        assertTrue("晨刊应使用硬朗油墨描边", edition.strongOutline)
        assertFalse("晨刊问句不铺强调底色", edition.accentUserBubble)
        assertTrue("晨刊面板应保持报纸式直角", edition.panelCornerRadius.value <= 2f)

        assertEquals(com.aiassistant.ui.theme.EchoPaperMaterial.ProjectorGrain, screening.paperMaterial)
        assertEquals(com.aiassistant.ui.theme.EchoSurfacePattern.FilmScanline, screening.surfacePattern)
        assertEquals(com.aiassistant.ui.theme.EchoFrameOrnament.ReelPerforation, screening.frameOrnament)
        assertTrue("放映室应使用硬质面板", screening.flatSurfaces)
        assertTrue("放映室用户消息应使用强调底色", screening.accentUserBubble)
        assertTrue("放映室面板应使用独立中圆角", screening.panelCornerRadius.value in 6f..10f)

        assertTrue(
            "晨刊正文应使用衬线字体",
            com.aiassistant.ui.theme.echoTypographyFor(com.aiassistant.utils.EchoAppearancePreset.EDITION).bodyLarge.fontFamily
                != androidx.compose.ui.text.font.FontFamily.Default
        )
        assertEquals(
            androidx.compose.ui.text.font.FontFamily.Monospace,
            com.aiassistant.ui.theme.echoTypographyFor(com.aiassistant.utils.EchoAppearancePreset.EDITION).labelSmall.fontFamily
        )
        assertEquals(
            androidx.compose.ui.text.font.FontFamily.Monospace,
            com.aiassistant.ui.theme.echoTypographyFor(com.aiassistant.utils.EchoAppearancePreset.SCREENING).labelSmall.fontFamily
        )
        assertEquals(
            androidx.compose.ui.text.font.FontWeight.Bold,
            com.aiassistant.ui.theme.echoTypographyFor(com.aiassistant.utils.EchoAppearancePreset.SCREENING).titleLarge.fontWeight
        )

        val base = androidx.compose.ui.text.TextStyle(
            fontSize = androidx.compose.ui.unit.TextUnit(16f, androidx.compose.ui.unit.TextUnitType.Sp)
        )
        assertEquals(
            androidx.compose.ui.text.style.TextAlign.Justify,
            com.aiassistant.ui.components.paperParagraphStyle(base, com.aiassistant.ui.theme.EchoPaperMaterial.Newsprint).textAlign
        )
        assertEquals(
            androidx.compose.ui.unit.TextUnit(1.6f, androidx.compose.ui.unit.TextUnitType.Em),
            com.aiassistant.ui.components.paperParagraphStyle(base, com.aiassistant.ui.theme.EchoPaperMaterial.Newsprint).textIndent?.firstLine
        )
        assertEquals(
            androidx.compose.ui.text.style.TextIndent.None,
            com.aiassistant.ui.components.paperParagraphStyle(base, com.aiassistant.ui.theme.EchoPaperMaterial.ProjectorGrain).textIndent
        )

        for (material in listOf(
            com.aiassistant.ui.theme.EchoPaperMaterial.Newsprint,
            com.aiassistant.ui.theme.EchoPaperMaterial.ProjectorGrain
        )) {
            for (dark in listOf(false, true)) {
                val bitmap = com.aiassistant.ui.theme.createPaperTexture(material, dark, 1f)
                assertTrue(bitmap.width >= 128 && bitmap.height >= 128)
            }
        }

        fun contrast(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color): Float =
            (maxOf(a.luminance(), b.luminance()) + 0.05f) / (minOf(a.luminance(), b.luminance()) + 0.05f)
        for (preset in presets) {
            for (dark in listOf(false, true)) {
                val scheme = com.aiassistant.ui.theme.echoColorScheme(preset, dark)
                assertTrue("$preset 正文对比度不足", contrast(scheme.onBackground, scheme.background) >= 4.5f)
                assertTrue("$preset 主操作对比度不足", contrast(scheme.onPrimary, scheme.primary) >= 4.5f)
                assertTrue("$preset 次操作对比度不足", contrast(scheme.onSecondary, scheme.secondary) >= 4.5f)
                assertTrue("$preset 第三操作对比度不足", contrast(scheme.onTertiary, scheme.tertiary) >= 4.5f)
                assertTrue("$preset 主容器对比度不足", contrast(scheme.onPrimaryContainer, scheme.primaryContainer) >= 4.5f)
                assertTrue("$preset 次容器对比度不足", contrast(scheme.onSecondaryContainer, scheme.secondaryContainer) >= 4.5f)
                assertTrue("$preset 第三容器对比度不足", contrast(scheme.onTertiaryContainer, scheme.tertiaryContainer) >= 4.5f)
                assertTrue("$preset 辅助文字对比度不足", contrast(scheme.onSurfaceVariant, scheme.surfaceVariant) >= 4.5f)
            }
        }
    }

    @Test fun clearSkyAndIndexThemesCarryAirAndCatalogueDetails() {
        val presets = listOf(
            com.aiassistant.utils.EchoAppearancePreset.CLEAR_SKY,
            com.aiassistant.utils.EchoAppearancePreset.INDEX
        )
        assertEquals(presets, presets.map { com.aiassistant.utils.EchoAppearancePreset.sanitize(it) })
        assertTrue(com.aiassistant.utils.EchoAppearancePreset.values.containsAll(presets))

        val clearSky = com.aiassistant.ui.theme.echoThemeStyleFor(com.aiassistant.utils.EchoAppearancePreset.CLEAR_SKY)
        val index = com.aiassistant.ui.theme.echoThemeStyleFor(com.aiassistant.utils.EchoAppearancePreset.INDEX)
        assertEquals(com.aiassistant.ui.theme.EchoPaperMaterial.SkyMist, clearSky.paperMaterial)
        assertEquals(com.aiassistant.ui.theme.EchoSurfacePattern.SkyCurrents, clearSky.surfacePattern)
        assertEquals(com.aiassistant.ui.theme.EchoFrameOrnament.SkyHorizon, clearSky.frameOrnament)
        assertTrue("晴空应使用开阔的大圆角", clearSky.panelCornerRadius.value >= 22f)
        assertTrue("晴空用户消息应使用轻蓝强调底色", clearSky.accentUserBubble)
        assertEquals(com.aiassistant.ui.theme.EchoPaperMaterial.IndexStock, index.paperMaterial)
        assertEquals(com.aiassistant.ui.theme.EchoSurfacePattern.IndexCatalogue, index.surfacePattern)
        assertEquals(com.aiassistant.ui.theme.EchoFrameOrnament.IndexTab, index.frameOrnament)
        assertTrue("索引应使用目录卡式小圆角", index.panelCornerRadius.value <= 6f)
        assertTrue("索引应使用清晰卡片描边", index.strongOutline)
        assertFalse("索引用户消息应保留卡纸底色", index.accentUserBubble)
        assertTrue(com.aiassistant.ui.theme.echoShapesFor(com.aiassistant.utils.EchoAppearancePreset.CLEAR_SKY).medium is androidx.compose.foundation.shape.RoundedCornerShape)
        assertTrue(com.aiassistant.ui.theme.echoShapesFor(com.aiassistant.utils.EchoAppearancePreset.INDEX).medium is androidx.compose.foundation.shape.RoundedCornerShape)

        val clearTypography = com.aiassistant.ui.theme.echoTypographyFor(com.aiassistant.utils.EchoAppearancePreset.CLEAR_SKY)
        val indexTypography = com.aiassistant.ui.theme.echoTypographyFor(com.aiassistant.utils.EchoAppearancePreset.INDEX)
        assertEquals(androidx.compose.ui.text.font.FontWeight.Medium, clearTypography.titleLarge.fontWeight)
        assertEquals(27f, clearTypography.bodyLarge.lineHeight.value)
        assertTrue("索引标题应使用目录衬线字体", indexTypography.titleLarge.fontFamily != androidx.compose.ui.text.font.FontFamily.Default)
        assertEquals(androidx.compose.ui.text.font.FontFamily.Monospace, indexTypography.labelMedium.fontFamily)
        assertTrue("索引正文节奏应比晴空紧凑", indexTypography.bodyLarge.lineHeight.value < clearTypography.bodyLarge.lineHeight.value)

        val base = androidx.compose.ui.text.TextStyle(
            fontSize = androidx.compose.ui.unit.TextUnit(16f, androidx.compose.ui.unit.TextUnitType.Sp)
        )
        val skyParagraph = com.aiassistant.ui.components.paperParagraphStyle(base, com.aiassistant.ui.theme.EchoPaperMaterial.SkyMist)
        val indexParagraph = com.aiassistant.ui.components.paperParagraphStyle(base, com.aiassistant.ui.theme.EchoPaperMaterial.IndexStock)
        assertEquals(androidx.compose.ui.text.style.TextAlign.Start, skyParagraph.textAlign)
        assertEquals(androidx.compose.ui.text.style.TextIndent.None, skyParagraph.textIndent)
        assertEquals(androidx.compose.ui.text.style.TextAlign.Justify, indexParagraph.textAlign)
        assertEquals(androidx.compose.ui.text.style.TextIndent.None, indexParagraph.textIndent)
        assertEquals(base.fontSize, skyParagraph.fontSize)
        assertEquals(base.fontSize, indexParagraph.fontSize)

        for (material in listOf(
            com.aiassistant.ui.theme.EchoPaperMaterial.SkyMist,
            com.aiassistant.ui.theme.EchoPaperMaterial.IndexStock
        )) {
            for (dark in listOf(false, true)) {
                val bitmap = com.aiassistant.ui.theme.createPaperTexture(material, dark, 1f)
                assertTrue(bitmap.width >= 128 && bitmap.height >= 128)
            }
        }
    }

    @Test fun everyPresetHasNonColorStructuralStyleAndTypography() {
        val nonDefaultPresets = com.aiassistant.utils.EchoAppearancePreset.values
            .filter { it != com.aiassistant.utils.EchoAppearancePreset.EXISTING }
        for (preset in nonDefaultPresets) {
            val style = com.aiassistant.ui.theme.echoThemeStyleFor(preset)
            assertTrue("$preset 必须启用独立面板形态（不得只是纯换色）", style.flatSurfaces)
            assertTrue(
                "$preset 必须具备结构/边饰/材质或版式特征",
                style.frameOrnament != com.aiassistant.ui.theme.EchoFrameOrnament.None ||
                    style.surfacePattern != com.aiassistant.ui.theme.EchoSurfacePattern.None ||
                    style.headerStyle != com.aiassistant.ui.theme.EchoHeaderStyle.Default ||
                    style.cutCorners ||
                    style.hardShadow
            )
            assertTrue(
                "$preset 必须有独立排版字体参数",
                com.aiassistant.ui.theme.echoTypographyFor(preset) != com.aiassistant.ui.theme.Typography
            )
            val lightScheme = com.aiassistant.ui.theme.echoColorScheme(preset, false)
            assertTrue(
                "$preset 浅色模式下卡片 surface 与画布 background 必须可区分",
                lightScheme.surface != lightScheme.background
            )
        }

        val voyage = com.aiassistant.ui.theme.echoThemeStyleFor(com.aiassistant.utils.EchoAppearancePreset.NIGHT_VOYAGE)
        assertEquals(com.aiassistant.ui.theme.EchoPaperMaterial.NauticalVellum, voyage.paperMaterial)
        assertEquals(com.aiassistant.ui.theme.EchoSurfacePattern.NauticalChart, voyage.surfacePattern)
        assertEquals(com.aiassistant.ui.theme.EchoFrameOrnament.VoyageCompass, voyage.frameOrnament)
        assertEquals(com.aiassistant.ui.theme.EchoMessageMetaStyle.Logbook, voyage.messageMetaStyle)
        assertEquals(com.aiassistant.ui.theme.EchoMarkdownHeadingStyle.NauticalHeading, voyage.markdownHeadingStyle)

        val mono = com.aiassistant.ui.theme.echoThemeStyleFor(com.aiassistant.utils.EchoAppearancePreset.MONO)
        assertTrue(mono.isMonochrome)
        assertEquals(com.aiassistant.ui.theme.EchoSurfacePattern.SwissGrid, mono.surfacePattern)
        assertEquals(com.aiassistant.ui.theme.EchoFrameOrnament.SwissRule, mono.frameOrnament)
        assertEquals(com.aiassistant.ui.theme.EchoHeaderStyle.SwissEditorial, mono.headerStyle)
        assertEquals(com.aiassistant.ui.theme.EchoMessageMetaStyle.SwissStamp, mono.messageMetaStyle)

        val clearSky = com.aiassistant.ui.theme.echoThemeStyleFor(com.aiassistant.utils.EchoAppearancePreset.CLEAR_SKY)
        assertEquals(com.aiassistant.ui.theme.EchoDividerStyle.HorizonFade, clearSky.dividerStyle)
        assertEquals(com.aiassistant.ui.theme.EchoMarkdownHeadingStyle.AiryHorizon, clearSky.markdownHeadingStyle)

        val index = com.aiassistant.ui.theme.echoThemeStyleFor(com.aiassistant.utils.EchoAppearancePreset.INDEX)
        assertEquals(com.aiassistant.ui.theme.EchoMarkdownHeadingStyle.CatalogHeading, index.markdownHeadingStyle)

        val deckle = com.aiassistant.ui.theme.echoThemeStyleFor(com.aiassistant.utils.EchoAppearancePreset.DECKLE)
        assertEquals(com.aiassistant.ui.theme.EchoSurfacePattern.DeckleEdge, deckle.surfacePattern)
        assertEquals(com.aiassistant.ui.theme.EchoMessageMetaStyle.Colophon, deckle.messageMetaStyle)

        val cotton = com.aiassistant.ui.theme.echoThemeStyleFor(com.aiassistant.utils.EchoAppearancePreset.COTTON)
        assertEquals(com.aiassistant.ui.theme.EchoDividerStyle.VersePause, cotton.dividerStyle)
        assertEquals(com.aiassistant.ui.theme.EchoMarkdownHeadingStyle.VerseHeading, cotton.markdownHeadingStyle)
        assertEquals(com.aiassistant.ui.theme.EchoMessageMetaStyle.VerseNote, cotton.messageMetaStyle)

        val folio = com.aiassistant.ui.theme.echoThemeStyleFor(com.aiassistant.utils.EchoAppearancePreset.FOLIO)
        assertEquals(com.aiassistant.ui.theme.EchoMarkdownHeadingStyle.FolioChapter, folio.markdownHeadingStyle)

        val sonar = com.aiassistant.ui.theme.echoThemeStyleFor(com.aiassistant.utils.EchoAppearancePreset.SONAR)
        val blueprint = com.aiassistant.ui.theme.echoThemeStyleFor(com.aiassistant.utils.EchoAppearancePreset.BLUEPRINT)
        assertEquals(com.aiassistant.ui.theme.EchoDividerStyle.RadarPulse, sonar.dividerStyle)
        assertEquals(com.aiassistant.ui.theme.EchoMarkdownHeadingStyle.TelemetryHud, sonar.markdownHeadingStyle)
        assertTrue("声呐与蓝图分隔线风格必须区分", sonar.dividerStyle != blueprint.dividerStyle)
        assertTrue("声呐与蓝图标题装饰必须区分", sonar.markdownHeadingStyle != blueprint.markdownHeadingStyle)

        val bauhaus = com.aiassistant.ui.theme.echoThemeStyleFor(com.aiassistant.utils.EchoAppearancePreset.BAUHAUS)
        assertEquals(com.aiassistant.ui.theme.EchoMessageMetaStyle.GeometricTag, bauhaus.messageMetaStyle)

        val celadon = com.aiassistant.ui.theme.echoThemeStyleFor(com.aiassistant.utils.EchoAppearancePreset.CELADON)
        val lacquer = com.aiassistant.ui.theme.echoThemeStyleFor(com.aiassistant.utils.EchoAppearancePreset.LACQUER)
        assertEquals(com.aiassistant.ui.theme.EchoDividerStyle.CeramicWave, celadon.dividerStyle)
        assertEquals(com.aiassistant.ui.theme.EchoMarkdownHeadingStyle.CeladonSeal, celadon.markdownHeadingStyle)
        assertEquals(com.aiassistant.ui.theme.EchoMessageMetaStyle.SealMark, celadon.messageMetaStyle)
        assertEquals(com.aiassistant.ui.theme.EchoDividerStyle.GiltDoubleRule, lacquer.dividerStyle)
        assertEquals(com.aiassistant.ui.theme.EchoMarkdownHeadingStyle.LacquerPlaque, lacquer.markdownHeadingStyle)
        assertEquals(com.aiassistant.ui.theme.EchoMessageMetaStyle.PlaqueStamp, lacquer.messageMetaStyle)

        val edition = com.aiassistant.ui.theme.echoThemeStyleFor(com.aiassistant.utils.EchoAppearancePreset.EDITION)
        assertEquals(com.aiassistant.ui.theme.EchoMessageMetaStyle.PressDateline, edition.messageMetaStyle)

        val identities = nonDefaultPresets.map { preset ->
            val s = com.aiassistant.ui.theme.echoThemeStyleFor(preset)
            s.frameOrnament to s.headerStyle
        }.toSet()
        assertEquals("全部 14 套非默认主题必须拥有互异的框饰与版头组合", nonDefaultPresets.size, identities.size)

        val editionLight = com.aiassistant.ui.theme.echoColorScheme(com.aiassistant.utils.EchoAppearancePreset.EDITION, false)
        val screeningLight = com.aiassistant.ui.theme.echoColorScheme(com.aiassistant.utils.EchoAppearancePreset.SCREENING, false)
        assertTrue("放映室浅色画布应与晨刊暖新闻纸明显区隔", screeningLight.background != editionLight.background)
        assertTrue("放映室浅色主色应与晨刊主色区分", screeningLight.primary != editionLight.primary)

        fun contrast(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color): Float =
            (maxOf(a.luminance(), b.luminance()) + 0.05f) / (minOf(a.luminance(), b.luminance()) + 0.05f)
        for (preset in com.aiassistant.utils.EchoAppearancePreset.values) {
            for (dark in listOf(false, true)) {
                val scheme = com.aiassistant.ui.theme.echoColorScheme(preset, dark)
                assertTrue("$preset 正文对比度不足", contrast(scheme.onBackground, scheme.background) >= 4.5f)
                assertTrue("$preset 主操作对比度不足", contrast(scheme.onPrimary, scheme.primary) >= 4.5f)
                assertTrue("$preset 次操作对比度不足", contrast(scheme.onSecondary, scheme.secondary) >= 4.5f)
                assertTrue("$preset 第三操作对比度不足", contrast(scheme.onTertiary, scheme.tertiary) >= 4.5f)
                assertTrue("$preset 主容器对比度不足", contrast(scheme.onPrimaryContainer, scheme.primaryContainer) >= 4.5f)
                assertTrue("$preset 次容器对比度不足", contrast(scheme.onSecondaryContainer, scheme.secondaryContainer) >= 4.5f)
                assertTrue("$preset 第三容器对比度不足", contrast(scheme.onTertiaryContainer, scheme.tertiaryContainer) >= 4.5f)
                assertTrue("$preset 辅助文字对比度不足", contrast(scheme.onSurfaceVariant, scheme.surfaceVariant) >= 4.5f)
            }
        }

        val base = androidx.compose.ui.text.TextStyle(
            fontSize = androidx.compose.ui.unit.TextUnit(16f, androidx.compose.ui.unit.TextUnitType.Sp)
        )
        val nauticalParagraph = com.aiassistant.ui.components.paperParagraphStyle(base, com.aiassistant.ui.theme.EchoPaperMaterial.NauticalVellum)
        assertEquals(androidx.compose.ui.text.style.TextAlign.Start, nauticalParagraph.textAlign)
        assertEquals(androidx.compose.ui.text.style.TextIndent.None, nauticalParagraph.textIndent)
        assertEquals(base.fontSize, nauticalParagraph.fontSize)
    }

    @Test fun inputResizeHandleMatchesPanelGeometryAcrossAllPresets() {
        val rightAnglePresets = listOf(
            com.aiassistant.utils.EchoAppearancePreset.BLUEPRINT,
            com.aiassistant.utils.EchoAppearancePreset.EDITION,
            com.aiassistant.utils.EchoAppearancePreset.BAUHAUS,
            com.aiassistant.utils.EchoAppearancePreset.FOLIO,
            com.aiassistant.utils.EchoAppearancePreset.MONO
        )
        rightAnglePresets.forEach { preset ->
            val style = com.aiassistant.ui.theme.echoThemeStyleFor(preset)
            assertEquals(
                "$preset 方角输入框右上角拖拽手柄应为直角",
                com.aiassistant.ui.screens.chat.InputResizeHandleStyle.RightAngle,
                com.aiassistant.ui.screens.chat.inputResizeHandleStyleFor(style)
            )
            assertEquals(style.panelCornerRadius, com.aiassistant.ui.screens.chat.inputOuterCornerRadius(style))
        }

        val sonarStyle = com.aiassistant.ui.theme.echoThemeStyleFor(com.aiassistant.utils.EchoAppearancePreset.SONAR)
        assertEquals(
            com.aiassistant.ui.screens.chat.InputResizeHandleStyle.CutBevel,
            com.aiassistant.ui.screens.chat.inputResizeHandleStyleFor(sonarStyle)
        )

        val roundedAnglePresets = listOf(
            com.aiassistant.utils.EchoAppearancePreset.INDEX,
            com.aiassistant.utils.EchoAppearancePreset.DECKLE,
            com.aiassistant.utils.EchoAppearancePreset.COTTON,
            com.aiassistant.utils.EchoAppearancePreset.SCREENING,
            com.aiassistant.utils.EchoAppearancePreset.LACQUER
        )
        roundedAnglePresets.forEach { preset ->
            val style = com.aiassistant.ui.theme.echoThemeStyleFor(preset)
            assertEquals(
                "$preset 中小圆角输入框手柄应匹配圆角拐角",
                com.aiassistant.ui.screens.chat.InputResizeHandleStyle.RoundedAngle,
                com.aiassistant.ui.screens.chat.inputResizeHandleStyleFor(style)
            )
        }

        val arcPresets = listOf(
            com.aiassistant.utils.EchoAppearancePreset.EXISTING,
            com.aiassistant.utils.EchoAppearancePreset.CLEAR_SKY,
            com.aiassistant.utils.EchoAppearancePreset.CELADON,
            com.aiassistant.utils.EchoAppearancePreset.NIGHT_VOYAGE
        )
        arcPresets.forEach { preset ->
            val style = com.aiassistant.ui.theme.echoThemeStyleFor(preset)
            assertEquals(
                "$preset 大圆角输入框手柄应为同心圆弧",
                com.aiassistant.ui.screens.chat.InputResizeHandleStyle.Arc,
                com.aiassistant.ui.screens.chat.inputResizeHandleStyleFor(style)
            )
        }
        assertEquals(
            androidx.compose.ui.unit.Dp(22f),
            com.aiassistant.ui.screens.chat.inputOuterCornerRadius(
                com.aiassistant.ui.theme.echoThemeStyleFor(com.aiassistant.utils.EchoAppearancePreset.EXISTING)
            )
        )
    }

    @Test fun themedMessageClockAndDurationReflectThemeMetaStyle() {
        val ts = 1_700_000_000_000L
        assertTrue(
            com.aiassistant.ui.theme.formatThemedMessageClock(ts, com.aiassistant.ui.theme.EchoMessageMetaStyle.TimecodeSubtitle)
                .startsWith("TC ")
        )
        assertEquals(
            "00:04.2",
            com.aiassistant.ui.theme.formatThemedDuration(4_200L, com.aiassistant.ui.theme.EchoMessageMetaStyle.TimecodeSubtitle)
        )
        assertTrue(
            com.aiassistant.ui.theme.formatThemedMessageClock(ts, com.aiassistant.ui.theme.EchoMessageMetaStyle.Marginalia)
                .startsWith("¶ ")
        )
        val telemetry = com.aiassistant.ui.theme.formatThemedMessageClock(ts, com.aiassistant.ui.theme.EchoMessageMetaStyle.Telemetry)
        assertTrue(telemetry.startsWith("[") && telemetry.endsWith("]"))
        assertTrue(
            com.aiassistant.ui.theme.formatThemedMessageClock(ts, com.aiassistant.ui.theme.EchoMessageMetaStyle.Logbook)
                .startsWith("LOG ")
        )
        assertTrue(
            com.aiassistant.ui.theme.formatThemedMessageClock(ts, com.aiassistant.ui.theme.EchoMessageMetaStyle.CatalogueStamp)
                .contains(" / ")
        )
        assertEquals(
            com.aiassistant.ui.screens.chat.formatMessageClock(ts),
            com.aiassistant.ui.theme.formatThemedMessageClock(ts, com.aiassistant.ui.theme.EchoMessageMetaStyle.Default)
        )
        assertTrue(
            com.aiassistant.ui.theme.formatThemedMessageClock(ts, com.aiassistant.ui.theme.EchoMessageMetaStyle.SwissStamp)
                .contains(" · ")
        )
        val colophon = com.aiassistant.ui.theme.formatThemedMessageClock(ts, com.aiassistant.ui.theme.EchoMessageMetaStyle.Colophon)
        assertTrue(colophon.startsWith("— ") && colophon.endsWith(" —"))
        assertTrue(
            com.aiassistant.ui.theme.formatThemedMessageClock(ts, com.aiassistant.ui.theme.EchoMessageMetaStyle.VerseNote)
                .startsWith("· ")
        )
        assertTrue(
            com.aiassistant.ui.theme.formatThemedMessageClock(ts, com.aiassistant.ui.theme.EchoMessageMetaStyle.GeometricTag)
                .startsWith("■ ")
        )
        assertTrue(
            com.aiassistant.ui.theme.formatThemedMessageClock(ts, com.aiassistant.ui.theme.EchoMessageMetaStyle.SealMark)
                .startsWith("○ ")
        )
        assertTrue(
            com.aiassistant.ui.theme.formatThemedMessageClock(ts, com.aiassistant.ui.theme.EchoMessageMetaStyle.PlaqueStamp)
                .startsWith("◆ ")
        )
        assertTrue(
            com.aiassistant.ui.theme.formatThemedMessageClock(ts, com.aiassistant.ui.theme.EchoMessageMetaStyle.PressDateline)
                .startsWith("ED. ")
        )
    }

    @Test fun redesignedPendingTimelineReviewPreservesOuterBoundsAndStructuredContent() {
        val proposal = com.aiassistant.data.repository.AutoTimelineUpdateResult(
            updatedStoryTime = "第3天·暮色",
            newEvent = com.aiassistant.utils.TimelineEventItem(
                timeTag = "第3天·暮色",
                content = "主角在钟楼顶层发现旧怀表",
                category = com.aiassistant.utils.TimelineCategory.PLOT_EVENT
            ),
            action = "UPDATE",
            previousEventContent = "主角准备前往钟楼调查",
            summaryNotice = "合成摘要",
            atemporalSettings = listOf(
                com.aiassistant.utils.AtemporalSettingItem(
                    id = "s1",
                    category = "物品",
                    content = "怀表背面刻有北境徽记",
                    action = "ADD",
                    evidence = "对话原文提到北境徽记",
                    isSelected = true
                ),
                com.aiassistant.utils.AtemporalSettingItem(
                    id = "s2",
                    category = "规则",
                    content = "钟楼在午夜后封锁",
                    action = "REVISE",
                    previousContent = "钟楼全天开放",
                    changeReason = "最新剧情修订",
                    isSelected = false
                )
            ),
            proposalId = "synthetic-structured-review"
        )
        val bounds = measureReviewInput(statusHeight = 28) {
            com.aiassistant.ui.screens.chat.ChatPendingReviewPanels(
                memory = null,
                timeline = proposal,
                onDismissMemory = {},
                onAcceptMemory = { _, _ -> },
                onDismissTimeline = {},
                onApplyTimeline = {}
            )
        }
        assertTrue("结构化排版应保持原有高度上限约束", bounds.getValue("review").height <= 440f)
        assertTrue("内容应具备清晰可见高度", bounds.getValue("pending").height > 140f)
        assertReviewAdjacentToInput(bounds)
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
        editor.field.edit { replace(2, 2, "新增"); selection = androidx.compose.ui.text.TextRange(3) }
        val published = requireNotNull(editor.valueToPublish())
        editor.field.edit { replace(3, 3, "后续"); selection = androidx.compose.ui.text.TextRange(2, 6) }
        val latest = editor.currentValue()
        editor.reconcileExternal(published)
        assertEquals("延迟的文本/选区回声不能覆盖新编辑", latest, editor.currentValue())
        assertEquals("未发布的新编辑不能被确认旧值时吞掉", latest, editor.valueToPublish())
        editor.field.edit { selection = androidx.compose.ui.text.TextRange(1, 7) }
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
        assertEquals(90f, state.getItemHeight("k0"), 0.001f)
        assertEquals(SmoothReorderState.DEFAULT_ITEM_HEIGHT, state.getItemHeight("missing"), 0.001f)

        assertEquals(0f, state.getItemLift(), 0.001f)
        state.onDragStart(listOf("k0"), "k0")
        // Unconfined 调度下 lift 动画立即完成
        assertEquals(1f, state.getItemLift(), 0.001f)

        state.onDragFinish(listOf("k0")) { _, _ -> }
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

    @org.robolectric.annotation.Config(shadows = [MaterialStringFallbackResources::class])
    @Test fun pendingTimelineReviewAllowsTogglingStoryTimeAndEventAndDisablesWhenAllUnchecked() {
        val proposal = com.aiassistant.data.repository.AutoTimelineUpdateResult(
            updatedStoryTime = "第2天·清晨",
            newEvent = com.aiassistant.utils.TimelineEventItem("第2天·清晨", "林岚进入北港灯塔", "PLOT_EVENT"),
            summaryNotice = "剧情推进至第2天·清晨",
            atemporalSettings = listOf(
                com.aiassistant.utils.AtemporalSettingItem(category = "世界规则", content = "北港灯塔每晚十点锁门", isSelected = true)
            )
        )
        var applied: com.aiassistant.data.repository.AutoTimelineUpdateResult? = null
        renderInteractiveCompose(useDialogWindow = false, width = 400, height = 960, content = {
            com.aiassistant.ui.screens.chat.ChatPendingReviewPanels(
                memory = null,
                timeline = proposal,
                onDismissMemory = {},
                onAcceptMemory = { _, _ -> },
                onDismissTimeline = {},
                onApplyTimeline = { applied = it }
            )
        }) { h ->
            val initialText = h.allText()
            assertTrue(initialText.contains("推进时空"))
            assertTrue(initialText.contains("林岚进入北港灯塔"))
            assertTrue(initialText.contains("北港灯塔每晚十点锁门"))

            // 取消勾选“推进时空”和时间线事件，只保留设定
            h.invokeSemanticsClickByText("推进时空")
            h.invokeSemanticsClickByText("林岚进入北港灯塔")
            h.invokeSemanticsClickByText("确认应用")
            org.junit.Assert.assertNotNull(applied)
            assertFalse(applied!!.applyStoryTime)
            assertFalse(applied!!.applyNewEvent)
            assertTrue(applied!!.atemporalSettings.single().isSelected)

            // 取消最后一项设定后，“确认应用”应禁用
            h.invokeSemanticsClickByText("北港灯塔每晚十点锁门")
            assertTrue("确认应用未禁用:\n${h.debugDump()}", h.isButtonDisabled("确认应用"))

            // 切到编辑态后同样受勾选控制，重新勾选时空后“应用”可用并透传状态
            applied = null
            val editSem = h.invokeSemanticsClickByText("编辑")
            assertTrue("点击编辑(sem=$editSem)后未进入编辑态:\n${h.debugDump()}", h.allText().contains("取消编辑"))
            assertTrue("编辑态应用未禁用:\n${h.debugDump()}", h.isButtonDisabled("应用"))
            h.invokeSemanticsClickByText("应用推进时空")
            assertFalse("编辑态勾选时空后应用仍禁用 toggles=${h.toggleStates()}:\n${h.debugDump()}", h.isButtonDisabled("应用"))
            h.invokeSemanticsClickByText("应用")
            org.junit.Assert.assertNotNull(applied)
            assertTrue(applied!!.applyStoryTime)
            assertFalse(applied!!.applyNewEvent)
            assertFalse(applied!!.atemporalSettings.single().isSelected)
        }
    }

    @Test fun timelineBasisChangedConfirmDialogWarnsAndSupportsConfirmOrDismissForModifiedAndDeletedNodes() {
        val baseProposal = com.aiassistant.data.repository.AutoTimelineUpdateResult(
            updatedStoryTime = "第2天·傍晚",
            newEvent = com.aiassistant.utils.TimelineEventItem("第2天·下午", "林岚在灯塔内破译密信", "PLOT_EVENT"),
            summaryNotice = "更新密信记录"
        )
        var confirms = 0
        var dismisses = 0
        renderInteractiveCompose(useDialogWindow = true, content = {
            com.aiassistant.ui.screens.chat.TimelineBasisChangedConfirmDialog(
                conflict = com.aiassistant.ui.screens.chat.PendingTimelineBasisConflict(
                    proposal = baseProposal,
                    currentEventContent = "库内已被改写的旧节点内容",
                    currentTimeTag = "第2天·正午",
                    currentCategory = "PLOT_EVENT",
                    wasDeletedOrRetimed = false
                ),
                onConfirm = { confirms++ },
                onDismiss = { dismisses++ }
            )
        }) { h ->
            val allText = h.allText()
            assertTrue(allText.contains("原时间线依据已改变"))
            assertTrue(allText.contains("当前库内节点"))
            assertTrue(allText.contains("库内已被改写的旧节点内容"))
            assertTrue(allText.contains("将要覆盖应用的内容"))
            assertTrue(allText.contains("林岚在灯塔内破译密信"))
            h.clickText("仍要应用")
            assertEquals(1, confirms)
            assertEquals(0, dismisses)
        }

        renderInteractiveCompose(useDialogWindow = true, content = {
            com.aiassistant.ui.screens.chat.TimelineBasisChangedConfirmDialog(
                conflict = com.aiassistant.ui.screens.chat.PendingTimelineBasisConflict(
                    proposal = baseProposal,
                    currentEventContent = null,
                    currentTimeTag = null,
                    currentCategory = null,
                    wasDeletedOrRetimed = true
                ),
                onConfirm = { confirms++ },
                onDismiss = { dismisses++ }
            )
        }) { h ->
            val allText = h.allText()
            assertTrue(allText.contains("已被删除或重建"))
            assertTrue(allText.contains("将要新增的内容"))
            assertFalse(allText.contains("当前库内节点"))
            h.clickText("取消")
            assertEquals(1, dismisses)
        }
    }

    @org.robolectric.annotation.Config(qualifiers = "w480dp-h1200dp", shadows = [MaterialStringFallbackResources::class])
    @Test fun timelineReconcileDialogSupportsIndependentStoryTimeAndTimelineSelection() {
        val result = com.aiassistant.utils.TimelineReconcileResult(
            currentStoryTime = "第3天·夜",
            events = mutableListOf(
                com.aiassistant.utils.TimelineEventItem("第1天·清晨", "事件甲：进入北港", "PLOT_EVENT"),
                com.aiassistant.utils.TimelineEventItem("第2天·傍晚", "事件乙：解开灯塔机关", "PLOT_EVENT")
            ),
            atemporalSettings = mutableListOf(
                com.aiassistant.utils.AtemporalSettingItem(category = "世界规则", content = "灯塔密码每日更换", isSelected = true)
            )
        )
        var captured: Triple<Boolean, Boolean, Pair<Int, Int>>? = null
        renderInteractiveCompose(useDialogWindow = true, width = 480, height = 1200, content = {
            com.aiassistant.ui.screens.chat.TimelineReconcileDialog(
                hazeState = androidx.compose.runtime.remember { dev.chrisbanes.haze.HazeState() },
                initialResult = result,
                onDismiss = {},
                onApply = { _, _, _ -> org.junit.Assert.fail("提供 onApplySelection 时应优先回调带勾选项的分发接口") },
                onApplySelection = { _, evs, sets, applyTime, applyEvs ->
                    captured = Triple(applyTime, applyEvs, evs.size to sets.size)
                }
            )
        }) { h ->
            // 初始：1个故事时间勾选框 + 1个设定勾选框 + 2个时间线事件勾选框 = 全选
            assertEquals(listOf(
                androidx.compose.ui.state.ToggleableState.On,
                androidx.compose.ui.state.ToggleableState.On,
                androidx.compose.ui.state.ToggleableState.On,
                androidx.compose.ui.state.ToggleableState.On
            ), h.toggleStates())
            assertTrue(h.allText().contains("保存时间线与独立设定"))

            // 取消勾选“当前故事停留在”，并通过时间线区域的“全不选”取消全部时间线事件
            h.invokeToggleSemanticsAt(0)
            assertTrue("点击toggle[0]后缺少保持原时间 toggles=${h.toggleStates()}:\n${h.debugDump()}", h.allText().contains("保持原时间"))
            h.invokeLastSemanticsClickByText("全不选")
            assertEquals(listOf(
                androidx.compose.ui.state.ToggleableState.Off,
                androidx.compose.ui.state.ToggleableState.On,
                androidx.compose.ui.state.ToggleableState.Off,
                androidx.compose.ui.state.ToggleableState.Off
            ), h.toggleStates())
            assertTrue("缺少仅保存独立设定:\n${h.debugDump()}", h.allText().contains("仅保存独立设定"))

            h.invokeSemanticsClickByText("仅保存独立设定")
            assertEquals(Triple(false, false, 0 to 1), captured)

            // 点击“全选”恢复时间线事件后按钮恢复联合提示
            h.invokeSemanticsClickByText("全选")
            assertTrue(h.allText().contains("保存时间线与独立设定"))

            // 再把设定和时间线都取消勾选 -> 提示“请至少选择一项”并禁用
            h.invokeSemanticsClickByText("全不选")
            h.invokeLastSemanticsClickByText("全不选")
            assertTrue(h.allText().contains("请至少选择一项"))
            assertTrue(h.isButtonDisabled("请至少选择一项"))
        }
    }

    private class InteractiveComposeHarness(
        private val activity: ComponentActivity,
        private val useDialogWindow: Boolean,
        private val advanceAndIdle: (Long) -> Unit
    ) {

        private fun rootViews(): List<Pair<androidx.compose.ui.node.RootForTest, androidx.compose.ui.semantics.SemanticsNode>> {
            val rootView = if (useDialogWindow) {
                org.robolectric.shadows.ShadowDialog.getLatestDialog()?.window?.decorView
            } else {
                activity.window.decorView
            } ?: return emptyList()
            val composeRoots = mutableListOf<androidx.compose.ui.node.RootForTest>()
            fun walk(view: android.view.View) {
                if (view is androidx.compose.ui.node.RootForTest) composeRoots += view
                if (view is android.view.ViewGroup) {
                    for (i in 0 until view.childCount) walk(view.getChildAt(i))
                }
            }
            walk(rootView)
            return composeRoots.mapNotNull { root ->
                runCatching { root to root.semanticsOwner.rootSemanticsNode }.getOrNull()
            }
        }

        private fun roots(): List<androidx.compose.ui.semantics.SemanticsNode> = rootViews().map { it.second }

        private fun allOwnedNodes(): List<Pair<androidx.compose.ui.node.RootForTest, androidx.compose.ui.semantics.SemanticsNode>> {
            val out = mutableListOf<Pair<androidx.compose.ui.node.RootForTest, androidx.compose.ui.semantics.SemanticsNode>>()
            for ((owner, root) in rootViews()) {
                fun visit(node: androidx.compose.ui.semantics.SemanticsNode) {
                    out += owner to node
                    node.children.forEach(::visit)
                }
                visit(root)
            }
            return out
        }

        private fun allNodes(): List<androidx.compose.ui.semantics.SemanticsNode> = allOwnedNodes().map { it.second }

        private fun nodeOwnText(node: androidx.compose.ui.semantics.SemanticsNode): String {
            val props = androidx.compose.ui.semantics.SemanticsProperties
            val text = if (props.Text in node.config) {
                node.config[props.Text].joinToString("") { it.text }
            } else ""
            val editable = if (props.EditableText in node.config) {
                node.config[props.EditableText].text
            } else ""
            return listOf(text, editable).filter { it.isNotEmpty() }.joinToString(" ")
        }

        private fun subtreeText(node: androidx.compose.ui.semantics.SemanticsNode): String =
            (listOf(nodeOwnText(node)) + node.children.map { subtreeText(it) })
                .filter { it.isNotEmpty() }
                .joinToString(" ")

        fun allText(): String = roots().joinToString("\n") { subtreeText(it) }

        private fun toggleOwnedNodes() =
            allOwnedNodes().filter { androidx.compose.ui.semantics.SemanticsProperties.ToggleableState in it.second.config }

        fun toggleStates(): List<androidx.compose.ui.state.ToggleableState> =
            toggleOwnedNodes().map { it.second.config[androidx.compose.ui.semantics.SemanticsProperties.ToggleableState] }

        private fun performClick(owner: androidx.compose.ui.node.RootForTest, node: androidx.compose.ui.semantics.SemanticsNode, label: String): String {
            val view = owner as? android.view.View
            val bounds = node.boundsInRoot
            if (view != null && !bounds.isEmpty && view.width > 0 && view.height > 0) {
                advanceAndIdle(500L)
                val downTime = android.os.SystemClock.uptimeMillis()
                val cx = bounds.center.x
                val cy = bounds.center.y
                val down = android.view.MotionEvent.obtain(downTime, downTime, android.view.MotionEvent.ACTION_DOWN, cx, cy, 0)
                val up = android.view.MotionEvent.obtain(downTime, downTime + 20L, android.view.MotionEvent.ACTION_UP, cx, cy, 0)
                val handledDown: Boolean
                val handledUp: Boolean
                try {
                    handledDown = view.dispatchTouchEvent(down)
                    handledUp = view.dispatchTouchEvent(up)
                    if (!handledDown && !handledUp && androidx.compose.ui.semantics.SemanticsActions.OnClick in node.config) {
                        assertTrue("$label must be clickable", node.config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action?.invoke() == true)
                    }
                } finally {
                    down.recycle()
                    up.recycle()
                }
                advanceAndIdle(0L)
                return "touch($cx,$cy down=$handledDown up=$handledUp)"
            } else {
                val action = if (androidx.compose.ui.semantics.SemanticsActions.OnClick in node.config) {
                    node.config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action
                } else null
                assertTrue("$label must be clickable (empty bounds=$bounds)", action?.invoke() == true)
                advanceAndIdle(0L)
                return "sem(empty=$bounds)"
            }
        }

        fun invokeToggleSemanticsAt(index: Int): Boolean {
            val pair = toggleOwnedNodes().getOrNull(index)
                ?: error("Missing toggle node at $index; count=${toggleOwnedNodes().size}")
            val action = if (androidx.compose.ui.semantics.SemanticsActions.OnClick in pair.second.config) {
                pair.second.config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action
            } else null
            val ok = action?.invoke() == true
            assertTrue("Toggle[$index] must be clickable", ok)
            advanceAndIdle(0L)
            return ok
        }

        private fun clickableMatches(text: String): List<Pair<androidx.compose.ui.node.RootForTest, androidx.compose.ui.semantics.SemanticsNode>> {
            val clickable = allOwnedNodes().filter {
                androidx.compose.ui.semantics.SemanticsActions.OnClick in it.second.config
            }
            val exact = clickable.filter { subtreeText(it.second).trim() == text }
            if (exact.isNotEmpty()) return exact
            return clickable.filter { subtreeText(it.second).contains(text) }
                .sortedBy { subtreeText(it.second).length }
        }

        fun clickText(text: String) {
            val pair = clickableMatches(text).firstOrNull()
                ?: error("Missing clickable text '$text' in:\n${allText()}")
            performClick(pair.first, pair.second, "Click '$text'")
        }

        fun invokeSemanticsClickByText(text: String): Boolean {
            val pair = clickableMatches(text).firstOrNull()
                ?: error("Missing clickable text '$text'")
            val ok = pair.second.config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action?.invoke() == true
            advanceAndIdle(0L)
            return ok
        }

        fun invokeLastSemanticsClickByText(text: String): Boolean {
            val pair = clickableMatches(text).lastOrNull()
                ?: error("Missing clickable text '$text'")
            val ok = pair.second.config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action?.invoke() == true
            advanceAndIdle(0L)
            return ok
        }

        fun isButtonDisabled(text: String): Boolean {
            val matching = clickableMatches(text).map { it.second }.ifEmpty {
                allNodes().filter { nodeOwnText(it).trim() == text || subtreeText(it).trim() == text }
            }
            val node = matching.firstOrNull()
                ?: error("Missing button '$text' in:\n${allText()}")
            return androidx.compose.ui.semantics.SemanticsProperties.Disabled in node.config ||
                generateSequence(node) { it.parent }.any {
                    androidx.compose.ui.semantics.SemanticsProperties.Disabled in it.config
                }
        }

        fun debugDump(): String = allOwnedNodes().joinToString("\n") { (owner, n) ->
            val v = owner as? android.view.View
            val keys = n.config.map { it.key.name }
            "id=${n.id} bounds=${n.boundsInRoot} view=${v?.width}x${v?.height} own='${nodeOwnText(n)}' sub='${subtreeText(n).take(40)}' keys=$keys"
        }
    }

    private fun renderInteractiveCompose(
        useDialogWindow: Boolean,
        width: Int = 400,
        height: Int = 800,
        content: @Composable () -> Unit,
        block: (InteractiveComposeHarness) -> Unit
    ) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val composeView = ComposeView(activity)
        val scaleKey = android.provider.Settings.Global.ANIMATOR_DURATION_SCALE
        val previousScale = android.provider.Settings.Global.getFloat(activity.contentResolver, scaleKey, 1f)
        android.provider.Settings.Global.putFloat(activity.contentResolver, scaleKey, 0f)
        val mainLooper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val frameClock = androidx.compose.runtime.BroadcastFrameClock()
        val dispatcher = object : kotlinx.coroutines.CoroutineDispatcher() {
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
                mainHandler.post(block)
            }
        }
        val recomposerJob = kotlinx.coroutines.Job()
        val recomposer = androidx.compose.runtime.Recomposer(dispatcher + frameClock + recomposerJob)
        val recomposerScope = kotlinx.coroutines.CoroutineScope(dispatcher + frameClock + recomposerJob)
        recomposerScope.launch {
            recomposer.runRecomposeAndApplyChanges()
        }
        var frameTimeNanos = System.nanoTime()
        try {
            composeView.setParentCompositionContext(recomposer)
            composeView.setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                    androidx.compose.material3.MaterialTheme { content() }
                }
            }
            activity.setContentView(composeView, android.widget.FrameLayout.LayoutParams(width, height))
            val widthSpec = android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY)
            val heightSpec = android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY)
            fun pumpFrames(extraDelayMs: Long = 0L) {
                if (extraDelayMs > 0L) mainLooper.idleFor(java.time.Duration.ofMillis(extraDelayMs))
                androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
                repeat(8) {
                    mainLooper.idle()
                    frameTimeNanos += 16_000_000L
                    frameClock.sendFrame(frameTimeNanos)
                    mainLooper.idle()
                    mainLooper.idleFor(java.time.Duration.ofMillis(16))
                    androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
                }
                if (useDialogWindow) {
                    org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView?.let { decor ->
                        decor.measure(widthSpec, heightSpec)
                        decor.layout(0, 0, width, height)
                    }
                }
                mainLooper.idle()
            }
            pumpFrames()
            composeView.measure(widthSpec, heightSpec)
            composeView.layout(0, 0, width, height)
            composeView.dispatchWindowFocusChanged(true)
            pumpFrames()
            val harness = InteractiveComposeHarness(activity, useDialogWindow) { delayMs ->
                pumpFrames(delayMs)
            }
            block(harness)
        } finally {
            android.provider.Settings.Global.putFloat(activity.contentResolver, scaleKey, previousScale)
            if (useDialogWindow) {
                runCatching { org.robolectric.shadows.ShadowDialog.getLatestDialog()?.dismiss() }
            }
            runCatching { composeView.disposeComposition() }
            runCatching { recomposer.cancel() }
            runCatching { recomposerJob.cancel() }
            runCatching { controller.pause().stop().destroy() }
            runCatching {
                mainLooper.idleFor(java.time.Duration.ofMillis(64))
                mainLooper.idle()
            }
        }
    }

    @Test fun storyReadingMetricsAndFontSpecsDistinguishAllVariants() {
        val metrics = com.aiassistant.utils.EchoStoryReadingStyle.values.associateWith {
            com.aiassistant.ui.screens.chat.storyReadingMetricsFor(it)
        }
        assertEquals(6, metrics.size)
        assertEquals(1.95f, metrics.getValue(com.aiassistant.utils.EchoStoryReadingStyle.IMMERSIVE).lineMultiplier, 0.001f)
        assertEquals(com.aiassistant.ui.screens.chat.StoryUserMessageStyle.QuoteLine,
            metrics.getValue(com.aiassistant.utils.EchoStoryReadingStyle.IMMERSIVE).userStyle)
        assertEquals(com.aiassistant.ui.screens.chat.StoryUserMessageStyle.BookIndent,
            metrics.getValue(com.aiassistant.utils.EchoStoryReadingStyle.BOOK).userStyle)
        assertEquals(2.0f, metrics.getValue(com.aiassistant.utils.EchoStoryReadingStyle.BOOK).paragraphIndentEm, 0.001f)
        assertTrue(metrics.getValue(com.aiassistant.utils.EchoStoryReadingStyle.BOOK).justifyParagraphs)
        assertEquals(8f, metrics.getValue(com.aiassistant.utils.EchoStoryReadingStyle.COMPACT).messageSpacingDp, 0.001f)
        assertEquals(com.aiassistant.ui.screens.chat.StoryUserMessageStyle.ScriptCue,
            metrics.getValue(com.aiassistant.utils.EchoStoryReadingStyle.SCRIPT).userStyle)

        val specs = com.aiassistant.utils.EchoStoryFont.values.associateWith {
            com.aiassistant.ui.screens.chat.readingFontSpec(it)
        }
        assertEquals(8, specs.size)
        assertEquals(androidx.compose.ui.text.font.FontFamily.Default,
            specs.getValue(com.aiassistant.utils.EchoStoryFont.SYSTEM).family)
        assertEquals(androidx.compose.ui.text.font.FontFamily.Serif,
            specs.getValue(com.aiassistant.utils.EchoStoryFont.SYSTEM_SERIF).family)
        assertEquals(androidx.compose.ui.text.font.FontWeight.Medium,
            specs.getValue(com.aiassistant.utils.EchoStoryFont.PRINT_BOOK).weight)
        assertEquals(androidx.compose.ui.text.font.FontWeight.Light,
            specs.getValue(com.aiassistant.utils.EchoStoryFont.ELEGANT_LIGHT).weight)
        assertEquals(androidx.compose.ui.text.font.FontFamily.Cursive,
            specs.getValue(com.aiassistant.utils.EchoStoryFont.CALLIGRAPHY).family)
    }

    @Test fun defaultApiSelectionActionPromptsOnlyWhenDisabled() {
        fun cfg(enabled: Boolean, default: Boolean) = com.aiassistant.domain.model.ApiConfig(
            id = 1L, name = "Synthetic", provider = "Synthetic", baseUrl = "https://api.example.test/v1",
            apiKey = "sk-test", modelName = "synthetic-model", isEnabled = enabled, isDefault = default
        )
        assertEquals(com.aiassistant.ui.screens.settings.DefaultApiSelectionAction.NoOp,
            com.aiassistant.ui.screens.settings.resolveDefaultApiSelectionAction(cfg(enabled = true, default = true)))
        assertEquals(com.aiassistant.ui.screens.settings.DefaultApiSelectionAction.SetDefaultDirectly,
            com.aiassistant.ui.screens.settings.resolveDefaultApiSelectionAction(cfg(enabled = true, default = false)))
        assertEquals(com.aiassistant.ui.screens.settings.DefaultApiSelectionAction.PromptEnableAndSetDefault,
            com.aiassistant.ui.screens.settings.resolveDefaultApiSelectionAction(cfg(enabled = false, default = false)))
        assertEquals(com.aiassistant.ui.screens.settings.DefaultApiSelectionAction.PromptEnableAndSetDefault,
            com.aiassistant.ui.screens.settings.resolveDefaultApiSelectionAction(cfg(enabled = false, default = true)))
    }

    @Test fun apiConfigSectionIndicesAndScrollHighlightTrackAllFourSections() {
        val emptyIndices = com.aiassistant.ui.screens.settings.apiConfigSectionIndices(
            discoveredCount = 0, showDiscoveryEmpty = false
        )
        assertEquals(0, emptyIndices.getValue(com.aiassistant.ui.screens.settings.ApiConfigSection.CONNECTION))
        assertEquals(2, emptyIndices.getValue(com.aiassistant.ui.screens.settings.ApiConfigSection.KEYS))
        assertEquals(4, emptyIndices.getValue(com.aiassistant.ui.screens.settings.ApiConfigSection.MODELS))
        assertEquals(7, emptyIndices.getValue(com.aiassistant.ui.screens.settings.ApiConfigSection.OTHER))

        val loadedIndices = com.aiassistant.ui.screens.settings.apiConfigSectionIndices(
            discoveredCount = 5, showDiscoveryEmpty = false
        )
        assertEquals(12, loadedIndices.getValue(com.aiassistant.ui.screens.settings.ApiConfigSection.OTHER))

        assertEquals(com.aiassistant.ui.screens.settings.ApiConfigSection.CONNECTION,
            com.aiassistant.ui.screens.settings.resolveActiveApiConfigSection(0, canScrollForward = false, loadedIndices))
        assertEquals(com.aiassistant.ui.screens.settings.ApiConfigSection.KEYS,
            com.aiassistant.ui.screens.settings.resolveActiveApiConfigSection(3, canScrollForward = true, loadedIndices))
        assertEquals(com.aiassistant.ui.screens.settings.ApiConfigSection.MODELS,
            com.aiassistant.ui.screens.settings.resolveActiveApiConfigSection(6, canScrollForward = true, loadedIndices))
        assertEquals(com.aiassistant.ui.screens.settings.ApiConfigSection.OTHER,
            com.aiassistant.ui.screens.settings.resolveActiveApiConfigSection(5, canScrollForward = false, loadedIndices))
    }

    @Test fun editableApiKeyItemsMoveSwapAndRemovePreserveOrderAndIds() {
        val k1 = com.aiassistant.ui.screens.settings.EditableApiKeyItem("id-1", com.aiassistant.domain.model.NamedApiKey("主用", "sk-1", true))
        val k2 = com.aiassistant.ui.screens.settings.EditableApiKeyItem("id-2", com.aiassistant.domain.model.NamedApiKey("备用", "sk-2", false))
        val k3 = com.aiassistant.ui.screens.settings.EditableApiKeyItem("id-3", com.aiassistant.domain.model.NamedApiKey("三号", "sk-3", true))
        val list = listOf(k1, k2, k3)

        val moved = com.aiassistant.ui.screens.settings.moveApiKeyItem(list, 0, 2)
        assertEquals(listOf("id-2", "id-3", "id-1"), moved.map { it.id })

        val swapped = com.aiassistant.ui.screens.settings.swapApiKeyItems(moved, 1, 2)
        assertEquals(listOf("id-2", "id-1", "id-3"), swapped.map { it.id })

        val removed = com.aiassistant.ui.screens.settings.removeApiKeyItem(swapped, 0)
        assertEquals(listOf("id-1", "id-3"), removed.map { it.id })

        val emptied = com.aiassistant.ui.screens.settings.removeApiKeyItem(listOf(k1), 0)
        assertEquals(1, emptied.size)
        assertEquals("", emptied.first().namedKey.key)
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
        runCatching { composeView.disposeComposition() }
        runCatching { controller.pause().stop().destroy() }
        android.provider.Settings.Global.putFloat(activity.contentResolver, scaleKey, previousScale)
        runCatching {
            val mainLooper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            mainLooper.idleFor(java.time.Duration.ofMillis(64))
            mainLooper.idle()
        }
    }
}

private fun renderAndClickSemanticsNode(
    contentDescription: String,
    useDialogWindow: Boolean,
    content: @Composable () -> Unit
) {
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
        activity.setContentView(composeView, android.widget.FrameLayout.LayoutParams(400, 720))
        val mainLooper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        mainLooper.idleFor(java.time.Duration.ofMillis(64))
        val widthSpec = android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY)
        val heightSpec = android.view.View.MeasureSpec.makeMeasureSpec(720, android.view.View.MeasureSpec.EXACTLY)
        repeat(2) {
            composeView.measure(widthSpec, heightSpec)
            composeView.layout(0, 0, 400, 720)
            mainLooper.idleFor(java.time.Duration.ofMillis(32))
        }

        val searchRoot: android.view.View = if (useDialogWindow) {
            val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
            assertTrue("应当已弹出目标对话框", dialog != null && dialog.isShowing)
            val decor = dialog!!.window!!.decorView
            decor.measure(widthSpec, heightSpec)
            decor.layout(0, 0, 400, 720)
            mainLooper.idleFor(java.time.Duration.ofMillis(32))
            decor
        } else {
            composeView
        }

        fun collectRoots(view: android.view.View, out: MutableList<androidx.compose.ui.node.RootForTest>) {
            if (view is androidx.compose.ui.node.RootForTest) out += view
            if (view is android.view.ViewGroup) {
                for (i in 0 until view.childCount) collectRoots(view.getChildAt(i), out)
            }
        }
        val roots = mutableListOf<androidx.compose.ui.node.RootForTest>()
        collectRoots(searchRoot, roots)
        assertTrue("未找到 Compose 语义根节点", roots.isNotEmpty())

        fun findClickableTarget(node: androidx.compose.ui.semantics.SemanticsNode): androidx.compose.ui.semantics.SemanticsNode? {
            val descriptions = node.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription) { null }
            val hasAction = node.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsActions.OnClick) { null } != null
            if (descriptions?.contains(contentDescription) == true && hasAction) return node
            for (child in node.children) {
                findClickableTarget(child)?.let { return it }
            }
            return null
        }

        val target = roots.firstNotNullOfOrNull { findClickableTarget(it.semanticsOwner.rootSemanticsNode) }
        assertTrue("未找到可点击的「$contentDescription」图标按键", target != null)
        assertFalse("「$contentDescription」图标按键不应处于禁用状态",
            target!!.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled))
        val onClick = target.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsActions.OnClick) { null }
        assertTrue("「$contentDescription」点击动作应当生效", onClick?.action?.invoke() == true)
        mainLooper.idleFor(java.time.Duration.ofMillis(32))
    } finally {
        if (useDialogWindow) {
            runCatching { org.robolectric.shadows.ShadowDialog.getLatestDialog()?.dismiss() }
        }
        runCatching { composeView.disposeComposition() }
        runCatching { controller.pause().stop().destroy() }
        android.provider.Settings.Global.putFloat(activity.contentResolver, scaleKey, previousScale)
        runCatching {
            val mainLooper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            mainLooper.idleFor(java.time.Duration.ofMillis(64))
            mainLooper.idle()
        }
    }
}

@org.robolectric.annotation.Implements(android.content.res.Resources::class)
class MaterialStringFallbackResources : org.robolectric.shadows.ShadowResources() {
    @org.robolectric.annotation.RealObject
    private lateinit var realResources: android.content.res.Resources

    @Suppress("DEPRECATION")
    @org.robolectric.annotation.Implementation
    protected fun getString(id: Int): String = try {
        org.robolectric.shadow.api.Shadow.directlyOn(realResources, android.content.res.Resources::class.java).getString(id)
    } catch (_: android.content.res.Resources.NotFoundException) {
        ""
    }
}
