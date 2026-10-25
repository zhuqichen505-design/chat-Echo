package com.aiassistant

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.aiassistant.domain.model.*
import com.aiassistant.ui.components.EchoModelMarkState
import com.aiassistant.ui.components.ExpressiveBotEffectKind
import com.aiassistant.ui.components.drawExpressiveBot
import com.aiassistant.ui.components.expressiveBotDurationMillis
import com.aiassistant.ui.components.modelMarkColorFor
import com.aiassistant.ui.components.readableTextColorFor
import com.aiassistant.ui.components.sampleExpressiveBotPose
import com.aiassistant.ui.screens.settings.ApiKeyRotationDraft
import com.aiassistant.utils.PersonalizationManager
import com.aiassistant.utils.EchoAppearancePreset
import com.aiassistant.utils.EchoHomeLayoutStyle
import com.aiassistant.utils.EchoStoryReadingStyle
import com.aiassistant.utils.EchoStreamingAnimationStyle
import com.aiassistant.utils.EchoModelMarkStyle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, manifest = Config.NONE)
class RetryPreferencesTest {
    @Test
    fun keyRotationDraftOnlyPersistsOnSaveAndRestoresAllEditedSettings() {
        val context = RuntimeEnvironment.getApplication()
        val baseUrl = "https://rotation-draft.test/v1"
        val manager = PersonalizationManager(context).apiKeyRotationManager
        val original = manager.getSettings(baseUrl, "openai")
        val draft = ApiKeyRotationDraft(original).copy(
            settings = original.copy(
                mode = ApiKeyRotationSettings.PREFER_SUCCESS,
                exponentialBackoff = false,
                deprioritizeFailures = false,
                resetOnSuccess = false,
                probeWhenAllCooling = true
            ),
            failures = "4", cooldown = "120", maxCooldown = "900", failureReset = "0"
        )

        assertEquals("编辑草稿不能提前修改已保存策略", original, manager.getSettings(baseUrl, "openai"))
        val saved = requireNotNull(draft.toSettingsOrNull())
        assertTrue(manager.saveSettings(baseUrl, "openai", saved))
        val reloaded = PersonalizationManager(context).apiKeyRotationManager.getSettings(baseUrl, "openai")
        assertEquals(saved, reloaded)
        assertEquals(ApiKeyRotationSettings.PREFER_SUCCESS, reloaded.mode)
        assertFalse(reloaded.exponentialBackoff)
        assertFalse(reloaded.deprioritizeFailures)
        assertFalse(reloaded.resetOnSuccess)
        assertTrue(reloaded.probeWhenAllCooling)
        val restoredDraft = ApiKeyRotationDraft(reloaded)
        assertEquals("4", restoredDraft.failures)
        assertEquals("120", restoredDraft.cooldown)
        assertEquals("900", restoredDraft.maxCooldown)
        assertEquals("0", restoredDraft.failureReset)
        assertEquals(saved, restoredDraft.toSettingsOrNull())
    }

    @Test
    fun keyRotationDraftAcceptsBoundaryDurationsAndZeroDisablesTimedReset() {
        val minimum = requireNotNull(ApiKeyRotationDraft(
            ApiKeyRotationSettings(), failures = "1", cooldown = "1", maxCooldown = "1", failureReset = "0"
        ).toSettingsOrNull())
        assertEquals(1, minimum.failuresBeforeCooldown)
        assertEquals(1L, minimum.cooldownSeconds)
        assertEquals(1L, minimum.maxCooldownSeconds)
        assertEquals(0L, minimum.failureResetSeconds)

        val maximum = requireNotNull(ApiKeyRotationDraft(
            ApiKeyRotationSettings(),
            failures = ApiKeyRotationSettings.MAX_FAILURE_THRESHOLD.toString(),
            cooldown = ApiKeyRotationSettings.MAX_BASE_COOLDOWN_SECONDS.toString(),
            maxCooldown = ApiKeyRotationSettings.MAX_COOLDOWN_SECONDS.toString(),
            failureReset = ApiKeyRotationSettings.MAX_FAILURE_RESET_SECONDS.toString()
        ).toSettingsOrNull())
        assertEquals(100, maximum.failuresBeforeCooldown)
        assertEquals(604800L, maximum.cooldownSeconds)
        assertEquals(2592000L, maximum.maxCooldownSeconds)
        assertEquals(31536000L, maximum.failureResetSeconds)
    }

    @Test fun rotationOffCanBeSavedWithUnfinishedHiddenNumbersAndRestoresDraftOnReenable() {
        val settings = ApiKeyRotationSettings(mode = ApiKeyRotationSettings.PREFER_SUCCESS)
        val unfinished = ApiKeyRotationDraft(settings, failures = "", cooldown = "", maxCooldown = "", failureReset = "")
        assertNull(unfinished.toSettingsOrNull())
        val disabled = unfinished.copy(settings = settings.copy(enabled = false))
        assertNull(disabled.validationMessage)
        assertEquals(settings.copy(enabled = false), disabled.toSettingsOrNull())
        assertEquals("", disabled.failureReset)
        assertNull(disabled.copy(settings = settings).toSettingsOrNull())
    }

    @Test fun rotationSwitchOnlyPersistsWhenDraftIsSaved() {
        val context = RuntimeEnvironment.getApplication()
        val manager = PersonalizationManager(context).apiKeyRotationManager
        val baseUrl = "https://rotation-switch-draft.test/v1"
        val original = manager.getSettings(baseUrl, "openai")
        val draft = ApiKeyRotationDraft(original.copy(enabled = false))
        assertTrue(manager.getSettings(baseUrl, "openai").enabled)
        assertTrue(manager.saveSettings(baseUrl, "openai", requireNotNull(draft.toSettingsOrNull())))
        assertFalse(PersonalizationManager(context).apiKeyRotationManager.getSettings(baseUrl, "openai").enabled)
    }

    @Test
    fun keyRotationDraftRejectsBlankOverflowAndOutOfRangeNumbersWithoutReplacingInput() {
        val draft = ApiKeyRotationDraft(ApiKeyRotationSettings())
        val invalidDrafts = listOf(
            draft.copy(failures = ""), draft.copy(failures = "0"), draft.copy(failures = "101"),
            draft.copy(failures = "2147483648"),
            draft.copy(cooldown = ""), draft.copy(cooldown = "0"), draft.copy(cooldown = "604801"),
            draft.copy(cooldown = "9223372036854775808"),
            draft.copy(maxCooldown = ""), draft.copy(maxCooldown = "2592001"),
            draft.copy(failureReset = ""), draft.copy(failureReset = " "),
            draft.copy(failureReset = "-1"), draft.copy(failureReset = "31536001")
        )
        invalidDrafts.forEach { invalid ->
            assertNotNull("非法数字应提示原因", invalid.validationMessage)
            assertNull("非法草稿不能生成可保存设置", invalid.toSettingsOrNull())
        }
        val overflow = draft.copy(cooldown = "9223372036854775808")
        assertNull(overflow.toSettingsOrNull())
        assertEquals("9223372036854775808", overflow.cooldown)
    }

    @Test
    fun keyRotationDraftRequiresMaximumAtLeastBaseAndAllowsCorrection() {
        val invalid = ApiKeyRotationDraft(ApiKeyRotationSettings(), cooldown = "600", maxCooldown = "300")
        assertNotNull(invalid.maxCooldownError)
        assertNull(invalid.toSettingsOrNull())

        val corrected = invalid.copy(maxCooldown = "600")
        assertNull(corrected.validationMessage)
        assertEquals(600L, requireNotNull(corrected.toSettingsOrNull()).maxCooldownSeconds)
    }

    @Test
    fun disablingCooldownPreservesDraftAndHiddenInvalidFieldsDoNotBlockOtherSettings() {
        val original = ApiKeyRotationSettings()
        val edited = ApiKeyRotationDraft(original, failures = "3", cooldown = "120", maxCooldown = "900")
        val disabled = edited.copy(settings = original.copy(cooldownEnabled = false))
        val saved = requireNotNull(disabled.toSettingsOrNull())
        assertFalse(saved.cooldownEnabled)
        assertEquals(3, saved.failuresBeforeCooldown)
        assertEquals(120L, saved.cooldownSeconds)
        assertEquals(900L, saved.maxCooldownSeconds)
        val restored = ApiKeyRotationDraft(saved.copy(cooldownEnabled = true))
        assertEquals(edited.failures, restored.failures)
        assertEquals(edited.cooldown, restored.cooldown)
        assertEquals(edited.maxCooldown, restored.maxCooldown)

        val unfinished = edited.copy(failures = "", cooldown = "", maxCooldown = "")
        assertNull(unfinished.toSettingsOrNull())
        val hidden = unfinished.copy(settings = original.copy(cooldownEnabled = false))
        assertNotNull(hidden.toSettingsOrNull())
        assertEquals("", hidden.cooldown)
        val reenabled = hidden.copy(settings = hidden.settings.copy(cooldownEnabled = true))
        assertEquals("", reenabled.cooldown)
        assertNull(reenabled.toSettingsOrNull())
    }

    @Test
    fun disablingBackoffKeepsUnfinishedMaximumDraftUntilReenabled() {
        val draft = ApiKeyRotationDraft(ApiKeyRotationSettings(), maxCooldown = "")
        assertNull(draft.toSettingsOrNull())
        val fixedCooldown = draft.copy(settings = draft.settings.copy(exponentialBackoff = false))
        assertNotNull(fixedCooldown.toSettingsOrNull())
        assertEquals("", fixedCooldown.maxCooldown)
        assertNull(fixedCooldown.copy(settings = fixedCooldown.settings.copy(exponentialBackoff = true)).toSettingsOrNull())
    }

    @Test fun rawStoredModelIdentityKeysRemainCompatibleWhenNewBotsAreAdded() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("personalization_settings", 0)
        val storedKeys = mapOf(
            "concentric" to EchoModelMarkStyle.CONCENTRIC,
            "orbit" to EchoModelMarkStyle.ORBIT,
            "prism" to EchoModelMarkStyle.PRISM,
            "waveform" to EchoModelMarkStyle.WAVEFORM,
            "bot_round" to EchoModelMarkStyle.BOT_ROUND,
            "bot_square" to EchoModelMarkStyle.BOT_SQUARE,
            "bot_capsule" to EchoModelMarkStyle.BOT_CAPSULE,
            "bot_pixel" to EchoModelMarkStyle.BOT_PIXEL,
            "bot_orb" to EchoModelMarkStyle.BOT_ORB,
            "bot_tile" to EchoModelMarkStyle.BOT_TILE,
            "bot_wide" to EchoModelMarkStyle.BOT_WIDE
        )
        for ((rawKey, expectedStyle) in storedKeys) {
            // 写入旧安装或备份中的真实键，而不是通过当前 sanitizer 构造输入。
            assertTrue(prefs.edit().clear().putString("model_mark_style", rawKey)
                .putBoolean("show_model_avatar", false).putBoolean("show_thinking_bubbles", false)
                .putString("about_user", "合成阅读偏好").commit())
            val restored = PersonalizationManager(context)
            assertEquals(expectedStyle, restored.getSettings().modelMarkStyle)
            assertFalse(restored.getSettings().showModelAvatar)
            assertFalse(restored.getSettings().showThinkingBubbles)
            val prompt = restored.buildPrompt()
            assertTrue(restored.saveSettings(restored.getSettings().copy(appearancePreset = EchoAppearancePreset.INDEX)))
            val afterUnrelatedSave = PersonalizationManager(context)
            assertEquals("主题切换不应重置已有Bot选择", expectedStyle, afterUnrelatedSave.getSettings().modelMarkStyle)
            assertEquals(rawKey, prefs.getString("model_mark_style", null))
            assertEquals(prompt, afterUnrelatedSave.buildPrompt())
            assertFalse(afterUnrelatedSave.getSettings().showModelAvatar)
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun expressiveBotsRenderRealDistinctUnclippedFramesAtMessageAndPreviewSizes() {
        val styles = EchoModelMarkStyle.expressiveBotStyles
        val states = listOf(EchoModelMarkState.Connecting, EchoModelMarkState.Thinking,
            EchoModelMarkState.Replying, EchoModelMarkState.Completed, EchoModelMarkState.Neutral,
            EchoModelMarkState.Paused, EchoModelMarkState.Failed)
        val sizes = listOf(26, 38, 64, 128)
        val outputDirectory = File("build/reports/visual-tests/model-bots")
        assertTrue(outputDirectory.isDirectory || outputDirectory.mkdirs())
        for ((theme, backdrop) in listOf("light" to Color.White, "dark" to Color.Black)) {
            val bodyColor = modelMarkColorFor(Color(0xFF397DE6), backdrop)
            val eyeColor = readableTextColorFor(bodyColor).toArgb()
            val gallery = Bitmap.createBitmap(8 * 128, styles.size * states.size * 128, Bitmap.Config.ARGB_8888)
            gallery.eraseColor(backdrop.toArgb())
            val galleryCanvas = android.graphics.Canvas(gallery)
            val terminalSymbols = mutableMapOf<Pair<String, Int>, MutableSet<Int>>()
            for ((styleIndex, style) in styles.withIndex()) {
                for ((stateIndex, state) in states.withIndex()) {
                    val isTerminal = state == EchoModelMarkState.Paused || state == EchoModelMarkState.Failed
                    for (size in sizes) {
                        val symbolFrames = mutableListOf<Pair<Int, Float>>()
                        val speechInteriorFrames = mutableListOf<Pair<Int, List<Int>>>()
                        val snapshots = (0 until 8).map { frame ->
                            // Cover the independent symbol cycle, including its readable hold and gap.
                            val windowMillis = when (state) {
                                EchoModelMarkState.Connecting -> 6000f
                                EchoModelMarkState.Thinking -> 6400f
                                EchoModelMarkState.Replying -> 5600f
                                EchoModelMarkState.Neutral -> 7000f
                                EchoModelMarkState.Completed -> expressiveBotDurationMillis(style, state)
                                EchoModelMarkState.Paused, EchoModelMarkState.Failed -> 1000f
                            }
                            val pose = sampleExpressiveBotPose(style, state,
                                windowMillis * frame / 8f,
                                idleEffects = state == EchoModelMarkState.Neutral)
                            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr,
                                Canvas(bitmap.asImageBitmap()), Size(size.toFloat(), size.toFloat())) {
                                drawExpressiveBot(bodyColor, pose)
                            }
                            val pixels = IntArray(size * size)
                            bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
                            if (isTerminal && frame == 0) {
                                val reducedBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                                val reducedPose = sampleExpressiveBotPose(style, state, 90_000f, reducedMotion = true)
                                CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr,
                                    Canvas(reducedBitmap.asImageBitmap()), Size(size.toFloat(), size.toFloat())) {
                                    drawExpressiveBot(bodyColor, reducedPose)
                                }
                                val reducedPixels = IntArray(size * size)
                                reducedBitmap.getPixels(reducedPixels, 0, size, 0, 0, size, size)
                                assertArrayEquals("$style $state $size dp 减弱动态仍须实际绘出相同的终止状态", pixels, reducedPixels)
                                reducedBitmap.recycle()
                            }
                            // The symbol layer occupies the clear space above every transformed body.
                            // Alpha coverage detects subpixel strokes without requiring a pure color.
                            val symbolPixels = pixels.copyOfRange(0, size * (size * .16f).toInt())
                            assertTrue("$style $state $size dp 外部符号实际墨迹必须位于右上角",
                                symbolPixels.withIndex().all { (index, pixel) ->
                                    pixel ushr 24 == 0 || index % size > size * .65f
                                })
                            if (state == EchoModelMarkState.Connecting && size == 128) {
                                val base = pose.effects.single { it.kind == ExpressiveBotEffectKind.SignalDot }
                                val leftOfBase = (base.x - base.size / 2f) * size / 100f
                                assertTrue("Wi-Fi墨迹须从基点向右上展开，不能沿用朝正上的对称扇形",
                                    symbolPixels.withIndex().all { (index, pixel) ->
                                        pixel ushr 24 == 0 || index % size + 1 >= leftOfBase
                                    })
                            }
                            symbolFrames += symbolPixels.contentHashCode() to
                                symbolPixels.sumOf { (it ushr 24).toDouble() }.toFloat() / 255f
                            if (state == EchoModelMarkState.Replying && size == 128) {
                                val speech = pose.effects.single()
                                // Compare the interior only while the group's opacity is held steady.
                                // A static bubble fading as a whole must not count as talking motion.
                                if (speech.alpha >= .89f) {
                                    val left = ((speech.x - speech.size / 3f) * size / 100f).toInt()
                                    val right = ((speech.x + speech.size / 3f) * size / 100f).toInt() + 1
                                    val top = ((speech.y - speech.size / 8f) * size / 100f).toInt()
                                    val bottom = ((speech.y + speech.size / 8f) * size / 100f).toInt() + 1
                                    val interior = (top until bottom).flatMap { y ->
                                        (left until right).map { x -> pixels[y * size + x] }
                                    }.toIntArray()
                                    val width = right - left
                                    val bandPeaks = (0 until 3).map { band ->
                                        (top until bottom).maxOf { y ->
                                            (left + width * band / 3 until left + width * (band + 1) / 3)
                                                .maxOf { x -> pixels[y * size + x] ushr 24 }
                                        }
                                    }
                                    speechInteriorFrames += interior.contentHashCode() to bandPeaks
                                }
                            }
                            // Thin capsule edges and brief blinks are antialiased and need not contain
                            // exact eyeColor pixels. Measure their real contribution on the opaque body.
                            // Error's contrasting exclamation is outside the face and must not pass as an eye.
                            val facePixels = pixels.copyOf().apply { fill(0, 0, size * (size * .2f).toInt()) }
                            val (leftEye, rightEye) = projectedEyeVisibility(facePixels, size, bodyColor.toArgb(), eyeColor)
                            val minimumArea = size * size / (26f * 26f)
                            fun visible(eye: EyeVisibility) = eye.effectivePixels >= 2 &&
                                eye.coverageArea >= minimumArea && eye.peakCoverage >= .45f
                            if (!visible(leftEye) || !visible(rightEye)) {
                                File(outputDirectory, "failure-$theme-$style-${state.name}-$size-$frame.png")
                                    .outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                            }
                            assertTrue("$style $state $size dp 必须实际绘出身体", pixels.any { it == bodyColor.toArgb() })
                            assertTrue("$style $state $size dp 左眼应有独立可见像素、面积与对比贡献: $leftEye", visible(leftEye))
                            assertTrue("$style $state $size dp 右眼应有独立可见像素、面积与对比贡献: $rightEye", visible(rightEye))
                            if (size == 128) {
                                val edges = (0 until size).flatMap { index ->
                                    listOf(pixels[index], pixels[(size - 1) * size + index],
                                        pixels[index * size], pixels[index * size + size - 1])
                                }
                                assertTrue("$style $state 动作不应裁切到画布边缘", edges.all { it ushr 24 == 0 })
                                galleryCanvas.drawBitmap(bitmap, frame * 128f,
                                    (styleIndex * states.size + stateIndex) * 128f, null)
                            }
                            bitmap.recycle()
                            pixels.contentHashCode()
                        }
                        if (isTerminal) {
                            assertEquals("$style $state $size dp 终止状态应持续显示，不闪动、不退回待机", 1, snapshots.toSet().size)
                            assertEquals("$style $state $size dp 终止符号墨迹应稳定", 1, symbolFrames.map { it.first }.toSet().size)
                            assertEquals("$style $state $size dp 每一帧必须有可见终止符号", 8,
                                symbolFrames.count { it.second > size * size / (26f * 26f) * .15f })
                            terminalSymbols.getOrPut(style to size) { mutableSetOf() }.add(symbolFrames.first().first)
                        } else {
                            assertTrue("$style $state $size dp 必须是真实多帧运动，不能只更换表情标签",
                                snapshots.toSet().size >= 5)
                            assertTrue("$style $state $size dp 外部符号本身必须实际绘制出不同像素帧",
                                symbolFrames.map { it.first }.toSet().size >= 4)
                        }
                        assertTrue("$style $state $size dp 外部符号应在多个关键帧中可见",
                            symbolFrames.count { it.second > size * size / (26f * 26f) * .15f } >= 3)
                        if (state == EchoModelMarkState.Replying && size == 128) {
                            assertTrue("说话泡内部必须有独立的多帧点亮，整组淡入淡出不足以通过",
                                speechInteriorFrames.map { it.first }.toSet().size >= 3)
                            assertTrue("说话泡完整点亮时，内部三个位置必须各有实际像素",
                                speechInteriorFrames.last().second.all { it >= 166 })
                        }
                    }
                }
            }
            for (style in styles) for (size in sizes) {
                assertEquals("$style $size dp 主动暂停和失败必须绘制出不同的实际符号", 2,
                    terminalSymbols[style to size]?.size)
            }
            File(outputDirectory, "expressive-bots-$theme.png").outputStream().use {
                assertTrue(gallery.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            gallery.recycle()
        }
    }

    private data class EyeVisibility(val effectivePixels: Int, val coverageArea: Float, val peakCoverage: Float)

    private fun projectedEyeVisibility(pixels: IntArray, size: Int, body: Int, eye: Int): Pair<EyeVisibility, EyeVisibility> {
        fun channel(color: Int, shift: Int) = ((color ushr shift) and 0xff).toFloat()
        val bodyRed = channel(body, 16)
        val bodyGreen = channel(body, 8)
        val bodyBlue = channel(body, 0)
        val redDifference = channel(eye, 16) - bodyRed
        val greenDifference = channel(eye, 8) - bodyGreen
        val blueDifference = channel(eye, 0) - bodyBlue
        val squaredDistance = redDifference * redDifference + greenDifference * greenDifference + blueDifference * blueDifference
        assertTrue("眼睛必须与身体有不同颜色", squaredDistance > 0f)
        val areas = FloatArray(2)
        val peaks = FloatArray(2)
        val counts = IntArray(2)
        pixels.forEachIndexed { index, pixel ->
            // Transparent antialiasing along the outer silhouette cannot count as an eye.
            if (pixel ushr 24 == 255) {
                val contribution = (channel(pixel, 16) - bodyRed) * redDifference +
                    (channel(pixel, 8) - bodyGreen) * greenDifference + (channel(pixel, 0) - bodyBlue) * blueDifference
                val coverage = (contribution / squaredDistance).coerceIn(0f, 1f)
                val side = if (index % size < size / 2) 0 else 1
                areas[side] += coverage
                peaks[side] = maxOf(peaks[side], coverage)
                if (coverage >= .15f) counts[side]++
            }
        }
        return EyeVisibility(counts[0], areas[0], peaks[0]) to EyeVisibility(counts[1], areas[1], peaks[1])
    }

    @Test fun modelIdentityChoicesPersistIndependentlyFromThinkingAndPromptPreferences() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("personalization_settings", 0).edit().clear().commit()
        val manager = PersonalizationManager(context)
        val initial = manager.getSettings().copy(
            aboutUser = "合成展示偏好", globalSystemPrompt = "合成模型规则",
            storyReadingStyle = EchoStoryReadingStyle.PLAIN,
            streamingAnimationStyle = EchoStreamingAnimationStyle.INK_RING,
            thinkingCapsuleTemplate = "{model} {time}"
        )
        assertTrue(initial.showModelAvatar)
        assertEquals(EchoModelMarkStyle.CONCENTRIC, initial.modelMarkStyle)
        assertTrue(manager.saveSettings(initial))
        val initialPrompt = manager.buildPrompt()
        for (style in EchoModelMarkStyle.values) {
            for (showAvatar in listOf(false, true)) {
                for (showThinking in listOf(false, true)) {
                    val expected = initial.copy(modelMarkStyle = style, showModelAvatar = showAvatar,
                        showThinkingBubbles = showThinking)
                    assertTrue(manager.saveSettings(expected))
                    val restored = PersonalizationManager(context)
                    assertEquals(expected, restored.getSettings())
                    assertEquals(initialPrompt, restored.buildPrompt())
                }
            }
        }
    }

    @Test fun oldAndUnknownModelIdentityPreferencesKeepAvatarAndDefaultConcentricMark() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("personalization_settings", 0)
        prefs.edit().clear().putBoolean("show_thinking_bubbles", false)
            .putString("appearance_preset", EchoAppearancePreset.NIGHT_VOYAGE).commit()
        val manager = PersonalizationManager(context)
        val original = manager.getSettings()
        assertTrue(original.showModelAvatar)
        assertEquals(EchoModelMarkStyle.CONCENTRIC, original.modelMarkStyle)
        assertFalse(original.showThinkingBubbles)
        for (unknown in listOf("", "future_mark", "ORBIT")) {
            prefs.edit().putString("model_mark_style", unknown).putBoolean("show_model_avatar", false).commit()
            assertEquals(original.copy(showModelAvatar = false), PersonalizationManager(context).getSettings())
            assertTrue(manager.saveSettings(original.copy(showModelAvatar = false, modelMarkStyle = unknown)))
            assertEquals(EchoModelMarkStyle.CONCENTRIC, prefs.getString("model_mark_style", null))
        }
        assertTrue(manager.saveSettings(original.copy(showModelAvatar = true, modelMarkStyle = EchoModelMarkStyle.ORBIT)))
        assertEquals(EchoModelMarkStyle.ORBIT, PersonalizationManager(context).getSettings().modelMarkStyle)
    }

    @Test fun thinkingBubbleVisibilityPersistsWithoutChangingThinkingOrReadingPreferences() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("personalization_settings", 0).edit().clear().commit()
        val manager = PersonalizationManager(context)
        assertTrue(manager.getSettings().showThinkingBubbles)
        val initial = manager.getSettings().copy(
            thinkingCapsuleTemplate = "{model} {time}",
            enableThinkingTranslation = false,
            thinkingTranslationModel = "synthetic-thinking-model",
            storyReadingStyle = EchoStoryReadingStyle.SPLIT,
            applyReadingStyleToChats = true,
            applyReadingFontToChats = true,
            aboutUser = "合成展示偏好"
        )
        assertTrue(manager.saveSettings(initial))
        val initialPrompt = manager.buildPrompt()
        assertTrue(manager.saveSettings(manager.getSettings().copy(showThinkingBubbles = false)))
        val restored = PersonalizationManager(context)
        assertEquals(initial.copy(showThinkingBubbles = false), restored.getSettings())
        assertEquals(initialPrompt, restored.buildPrompt())
        assertTrue(restored.saveSettings(restored.getSettings().copy(appearancePreset = EchoAppearancePreset.INDEX)))
        val afterThemeChange = PersonalizationManager(context)
        assertFalse(afterThemeChange.getSettings().showThinkingBubbles)
        assertTrue(afterThemeChange.saveSettings(afterThemeChange.getSettings().copy(showThinkingBubbles = true)))
        assertEquals(initial.copy(appearancePreset = EchoAppearancePreset.INDEX), PersonalizationManager(context).getSettings())
    }

    @Test fun readingScopeSwitchesPersistIndependentlyAndDefaultToStoriesOnly() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("personalization_settings", 0).edit().clear().commit()
        val manager = PersonalizationManager(context)
        assertFalse(manager.getSettings().applyReadingStyleToChats)
        assertFalse(manager.getSettings().applyReadingFontToChats)
        assertTrue(manager.saveSettings(manager.getSettings().copy(applyReadingFontToChats = true,
            storyFont = com.aiassistant.utils.EchoStoryFont.TYPEWRITER)))
        val restored = PersonalizationManager(context)
        assertTrue(restored.getSettings().applyReadingFontToChats)
        assertFalse(restored.getSettings().applyReadingStyleToChats)
        assertTrue(restored.saveSettings(restored.getSettings().copy(applyReadingStyleToChats = true,
            applyReadingFontToChats = false)))
        val actual = PersonalizationManager(context).getSettings()
        assertTrue(actual.applyReadingStyleToChats)
        assertFalse(actual.applyReadingFontToChats)
        assertEquals(com.aiassistant.utils.EchoStoryFont.TYPEWRITER, actual.storyFont)
    }

    @Test fun homeLayoutPreferencesSurviveRecreationAndStayIndependentOfVisualAndRetryChoices() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("personalization_settings", 0).edit().clear().commit()
        val manager = PersonalizationManager(context)
        manager.saveRetryRule(RetryErrorType.SERVER, RetryRule(true, 7))
        manager.saveRetryRule(RetryErrorType.TIMEOUT, RetryRule(false, 2))
        manager.setBackupKeyFallbackEnabled(false)
        manager.setModelRetryEnabled(false)
        assertTrue(manager.saveSettings(manager.getSettings().copy(
            appTheme = "dark", chatFontSize = 19, fontSizeScale = 1.15f,
            appearancePreset = EchoAppearancePreset.NIGHT_VOYAGE,
            storyReadingStyle = EchoStoryReadingStyle.SPLIT,
            streamingAnimationStyle = EchoStreamingAnimationStyle.SOUND_WAVE,
            globalSystemPrompt = "合成首页测试规则"
        )))
        for (layout in EchoHomeLayoutStyle.values) {
            for (compact in listOf(false, true)) {
                for (showStory in listOf(false, true)) {
                    assertTrue(manager.saveSettings(manager.getSettings().copy(
                        homeLayoutStyle = layout, homeCompactList = compact, homeShowRecentStory = showStory
                    )))
                    val recreated = PersonalizationManager(context)
                    val actual = recreated.getSettings()
                    assertEquals(layout, actual.homeLayoutStyle)
                    assertEquals(compact, actual.homeCompactList)
                    assertEquals(showStory, actual.homeShowRecentStory)
                    assertEquals(EchoAppearancePreset.NIGHT_VOYAGE, actual.appearancePreset)
                    assertEquals(EchoStoryReadingStyle.SPLIT, actual.storyReadingStyle)
                    assertEquals(EchoStreamingAnimationStyle.SOUND_WAVE, actual.streamingAnimationStyle)
                    assertEquals("dark", actual.appTheme)
                    assertEquals(19, actual.chatFontSize)
                    assertEquals(1.15f, actual.fontSizeScale, 0f)
                    assertEquals("合成首页测试规则", actual.globalSystemPrompt)
                    assertFalse(recreated.isModelRetryEnabled())
                    assertEquals(RetryRule(true, 7), recreated.getRetryPolicy().rule(RetryErrorType.SERVER))
                    assertEquals(RetryRule(false, 2), recreated.getRetryPolicy().rule(RetryErrorType.TIMEOUT))
                }
            }
        }
        assertTrue(manager.saveSettings(manager.getSettings().copy(
            homeLayoutStyle = EchoHomeLayoutStyle.WORKBENCH, homeCompactList = true, homeShowRecentStory = false
        )))
        assertTrue(manager.saveSettings(manager.getSettings().copy(
            appearancePreset = EchoAppearancePreset.CLEAR_SKY, storyReadingStyle = EchoStoryReadingStyle.PLAIN,
            streamingAnimationStyle = EchoStreamingAnimationStyle.INK_RING
        )))
        val recreated = PersonalizationManager(context)
        assertEquals(EchoHomeLayoutStyle.WORKBENCH, recreated.getSettings().homeLayoutStyle)
        assertTrue(recreated.getSettings().homeCompactList)
        assertFalse(recreated.getSettings().homeShowRecentStory)
        recreated.setModelRetryEnabled(true)
        assertFalse("重新开启重试后仍保留备份 Key 回退选择", recreated.isBackupKeyFallbackEnabled())
    }

    @Test fun existingPreferencesWithoutHomeKeysAndUnknownLayoutsUseFocusDefaults() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("personalization_settings", 0)
        prefs.edit().clear().putString("appearance_preset", EchoAppearancePreset.INDEX)
            .putString("story_reading_style", EchoStoryReadingStyle.PLAIN)
            .putString("streaming_animation_style", EchoStreamingAnimationStyle.INK_RING)
            .putInt("chat_font_size", 21).putBoolean("auto_name_enabled", false).commit()
        val manager = PersonalizationManager(context)
        val oldPreferences = manager.getSettings()
        assertTrue(oldPreferences.showThinkingBubbles)
        assertEquals(EchoHomeLayoutStyle.FOCUS, oldPreferences.homeLayoutStyle)
        assertFalse(oldPreferences.homeCompactList)
        assertTrue(oldPreferences.homeShowRecentStory)
        assertEquals(EchoAppearancePreset.INDEX, oldPreferences.appearancePreset)
        assertEquals(21, oldPreferences.chatFontSize)
        assertFalse(oldPreferences.autoNameEnabled)
        for (unknown in listOf("future_layout", "", "WORKBENCH")) {
            prefs.edit().putString("home_layout_style", unknown)
                .putBoolean("home_compact_list", true).putBoolean("home_show_recent_story", false).commit()
            val restored = PersonalizationManager(context).getSettings()
            assertEquals(EchoHomeLayoutStyle.FOCUS, restored.homeLayoutStyle)
            assertTrue(restored.homeCompactList)
            assertFalse(restored.homeShowRecentStory)
            assertEquals(EchoAppearancePreset.INDEX, restored.appearancePreset)
            assertEquals(EchoStoryReadingStyle.PLAIN, restored.storyReadingStyle)
            assertEquals(EchoStreamingAnimationStyle.INK_RING, restored.streamingAnimationStyle)
        }
        assertTrue(manager.saveSettings(manager.getSettings().copy(homeLayoutStyle = "unsupported_layout")))
        assertEquals(EchoHomeLayoutStyle.FOCUS, prefs.getString("home_layout_style", null))
        val recreated = PersonalizationManager(context).getSettings()
        assertTrue(recreated.homeCompactList)
        assertFalse(recreated.homeShowRecentStory)
        assertEquals(21, recreated.chatFontSize)
        assertFalse(recreated.autoNameEnabled)
    }

    @Test fun visualPreferencesPersistIndependentlyWithoutChangingOtherPersonalization() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("personalization_settings", 0).edit().clear().commit()
        val manager = PersonalizationManager(context)
        val initial = manager.getSettings()
        assertEquals(EchoAppearancePreset.EXISTING, initial.appearancePreset)
        assertEquals(EchoStoryReadingStyle.IMMERSIVE, initial.storyReadingStyle)
        assertEquals(EchoStreamingAnimationStyle.THIN_CURSOR, initial.streamingAnimationStyle)
        assertTrue(manager.saveSettings(initial.copy(
            appTheme = "dark", chatFontSize = 20, fontSizeScale = 1.15f,
            globalSystemPrompt = "合成测试指引", aboutUser = "合成偏好", autoNameEnabled = false,
            appearancePreset = EchoAppearancePreset.NIGHT_VOYAGE,
            storyReadingStyle = EchoStoryReadingStyle.PLAIN,
            streamingAnimationStyle = EchoStreamingAnimationStyle.INK_RING
        )))
        val recreated = PersonalizationManager(context)
        assertTrue(recreated.saveSettings(recreated.getSettings().copy(appearancePreset = EchoAppearancePreset.INDEX)))
        val restored = PersonalizationManager(context).getSettings()
        assertEquals(EchoAppearancePreset.INDEX, restored.appearancePreset)
        assertEquals(EchoStoryReadingStyle.PLAIN, restored.storyReadingStyle)
        assertEquals(EchoStreamingAnimationStyle.INK_RING, restored.streamingAnimationStyle)
        assertEquals("dark", restored.appTheme)
        assertEquals(20, restored.chatFontSize)
        assertEquals(1.15f, restored.fontSizeScale, 0f)
        assertEquals("合成测试指引", restored.globalSystemPrompt)
        assertEquals("合成偏好", restored.aboutUser)
        assertFalse(restored.autoNameEnabled)
    }

    @Test fun unsupportedVisualPreferencesRestoreSafeDefaultsAndValidChoicesRemainIndependent() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("personalization_settings", 0)
        prefs.edit().clear()
            .putString("appearance_preset", "future_theme")
            .putString("story_reading_style", "future_reader")
            .putString("streaming_animation_style", "future_animation")
            .commit()
        val manager = PersonalizationManager(context)
        val restored = manager.getSettings()
        assertEquals(EchoAppearancePreset.EXISTING, restored.appearancePreset)
        assertEquals(EchoStoryReadingStyle.IMMERSIVE, restored.storyReadingStyle)
        assertEquals(EchoStreamingAnimationStyle.THIN_CURSOR, restored.streamingAnimationStyle)
        for (preset in EchoAppearancePreset.values) {
            for (reader in EchoStoryReadingStyle.values) {
                for (animation in EchoStreamingAnimationStyle.values) {
                    assertTrue(manager.saveSettings(restored.copy(appearancePreset = preset,
                        storyReadingStyle = reader, streamingAnimationStyle = animation)))
                    val actual = PersonalizationManager(context).getSettings()
                    assertEquals(preset, actual.appearancePreset)
                    assertEquals(reader, actual.storyReadingStyle)
                    assertEquals(animation, actual.streamingAnimationStyle)
                }
            }
        }
        assertTrue(manager.saveSettings(restored.copy(appearancePreset = "invalid",
            storyReadingStyle = "invalid", streamingAnimationStyle = "invalid")))
        assertEquals(EchoAppearancePreset.EXISTING, prefs.getString("appearance_preset", null))
        assertEquals(EchoStoryReadingStyle.IMMERSIVE, prefs.getString("story_reading_style", null))
        assertEquals(EchoStreamingAnimationStyle.THIN_CURSOR, prefs.getString("streaming_animation_style", null))
    }

    @Test fun httpPermissionsDefaultClosedPersistAndRemainDeviceLocal() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("http_access_permissions", 0)
        prefs.edit().clear().commit()
        val settings = com.aiassistant.utils.HttpAccessSettings(context)
        assertFalse(settings.getRules().enabled)
        assertTrue(settings.getRules().addresses.isEmpty())
        settings.addAddress("192.0.2.1:8080/v1/")
        settings.addAddress("http://192.0.2.1:8080/v1")
        assertEquals(listOf("http://192.0.2.1:8080/v1"), settings.getRules().addresses)
        assertFalse(settings.getRules().enabled)
        settings.setEnabled(true)
        PersonalizationManager(context).saveSettings(PersonalizationManager(context).getSettings().copy(autoNameEnabled = false))
        val recreated = com.aiassistant.utils.HttpAccessSettings(context)
        assertTrue(recreated.getRules().enabled)
        recreated.setEnabled(false)
        assertEquals(1, recreated.getRules().addresses.size)
        try { recreated.addAddress("http://*.example.test"); fail("wildcard accepted") } catch (_: IllegalArgumentException) { }
        assertEquals(1, recreated.getRules().addresses.size)
        recreated.removeAddress("http://192.0.2.1:8080/v1")
        assertTrue(recreated.getRules().addresses.isEmpty())
        // Portable backup exports only personalization_settings, not this separate permission file.
        assertFalse(context.getSharedPreferences("personalization_settings", 0).contains("addresses"))
    }
    @Test fun masterSwitchPersistsAndDoesNotDiscardCategoryOrBackupKeyChoices() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("personalization_settings", 0).edit().clear().commit()
        val manager = PersonalizationManager(context)
        manager.saveRetryRule(RetryErrorType.SERVER, RetryRule(true, 2))
        manager.setBackupKeyFallbackEnabled(true)
        manager.setModelRetryEnabled(false)
        manager.saveSettings(manager.getSettings().copy(autoNameEnabled = false))
        val restored = PersonalizationManager(context)
        assertFalse(restored.isModelRetryEnabled())
        assertFalse(restored.isBackupKeyFallbackEnabled())
        assertFalse(restored.getRetryPolicy().canRetry(com.aiassistant.data.repository.ApiException(500, "API错误 (500)"), 0))
        assertEquals(RetryRule(true, 2), restored.getRetryPolicy().rule(RetryErrorType.SERVER))
        restored.setModelRetryEnabled(true)
        assertTrue(restored.isBackupKeyFallbackEnabled())
        assertTrue(restored.getRetryPolicy().canRetry(com.aiassistant.data.repository.ApiException(500, "API错误 (500)"), 1))
        assertFalse(restored.getRetryPolicy().canRetry(com.aiassistant.data.repository.ApiException(500, "API错误 (500)"), 2))
    }
    @Test fun rulesSurviveManagerRecreationAndUnrelatedSettingsSave() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("personalization_settings", 0)
        prefs.edit().clear().commit()
        val manager = PersonalizationManager(context)
        assertEquals(RetryRule(true, 3), manager.getRetryPolicy().rule(RetryErrorType.TIMEOUT))
        assertFalse(manager.getRetryPolicy().rule(RetryErrorType.AUTHENTICATION).enabled)
        manager.saveRetryRule(RetryErrorType.TIMEOUT, RetryRule(false, 7))
        manager.saveRetryRule(RetryErrorType.RATE_LIMIT, RetryRule(true, 4))
        assertEquals(RetryRule(false, 7), manager.getRetryPolicy().rule(RetryErrorType.TIMEOUT))
        assertEquals(RetryRule(true, 4), manager.getRetryPolicy().rule(RetryErrorType.RATE_LIMIT))
        manager.setBackupKeyFallbackEnabled(false)
        manager.saveSettings(manager.getSettings().copy(autoNameEnabled = false))
        val recreated = PersonalizationManager(context)
        assertEquals(RetryRule(false, 7), recreated.getRetryPolicy().rule(RetryErrorType.TIMEOUT))
        assertEquals(RetryRule(true, 4), recreated.getRetryPolicy().rule(RetryErrorType.RATE_LIMIT))
        assertFalse(recreated.isBackupKeyFallbackEnabled())
        assertFalse(recreated.getSettings().autoNameEnabled)
    }

    @Test fun retryCountsClampWithoutChangingOtherCategories() {
        val manager = PersonalizationManager(RuntimeEnvironment.getApplication())
        manager.saveRetryRule(RetryErrorType.SERVER, RetryRule(true, 500))
        manager.saveRetryRule(RetryErrorType.BAD_REQUEST, RetryRule(true, -1))
        assertEquals(20, manager.getRetryPolicy().rule(RetryErrorType.SERVER).maxRetries)
        assertEquals(0, manager.getRetryPolicy().rule(RetryErrorType.BAD_REQUEST).maxRetries)
        assertFalse(manager.getRetryPolicy().canRetry(Exception("HTTP 400: invalid"), 0))
    }
}
