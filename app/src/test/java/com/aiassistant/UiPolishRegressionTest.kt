package com.aiassistant

import com.aiassistant.ui.components.SmoothReorderState
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
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.graphics.luminance

/**
 * UI 细节统一修复回归：
 * 1. 模型列表拖拽排序必须保持自定义顺序（cleanModelNames 去重但不重排）
 * 2. SmoothReorderState 抬升量与按 key 高度记录
 */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34], application = android.app.Application::class, manifest = org.robolectric.annotation.Config.NONE)
class UiPolishRegressionTest {
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
            assertTrue(position <= 88f * testScheduler.currentTime / 1_000f + .001f)
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
        assertTrue(step(1000f) <= 88f / 60f)
        assertEquals(step(1000f) * 3, step(3000f, 3f), .001f)
        assertEquals(-step(1000f), step(-1000f), .001f)
        assertEquals(.1f, step(.1f), .001f)
        assertEquals(0f, step(0f), 0f)
        assertEquals(0f, step(Float.NaN), 0f)
        assertTrue("卡帧后不应追赶跳滚", step(1000f, seconds = 1f) <= 4.4f)
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
