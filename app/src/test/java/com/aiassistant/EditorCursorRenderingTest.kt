package com.aiassistant

import android.app.Application
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Insets
import android.graphics.Color as AndroidColor
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets as ComposeWindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import com.aiassistant.ui.components.EchoNativeTextField
import com.aiassistant.ui.components.isImeVisibleNow
import java.time.Duration
import java.util.Collections
import java.util.IdentityHashMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.shadows.ShadowInputMethodManager
import org.robolectric.shadows.ShadowResources
import org.robolectric.shadows.ShadowViewRootImpl
import org.robolectric.shadow.api.Shadow

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
class EditorCursorRenderingTest {
    @Test fun cursorSurvivesDecoratorRecompositionAndWindowModeChanges() {
        val oldPaused = ShadowChoreographer.isPaused()
        val oldFrameDelay = ShadowChoreographer.getFrameDelay()
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        ShadowChoreographer.setPaused(true)
        try {
            checkCursorRendering()
        } finally {
            ShadowChoreographer.setFrameDelay(oldFrameDelay)
            ShadowChoreographer.setPaused(oldPaused)
        }
    }

    /** Exercises an actual editor in one composition, with an Android IME-insets signal. */
    @Test
    @Config(shadows = [CountingInputMethodManager::class, PopupTitleResources::class, ImeInsetsViewRoot::class])
    fun programmaticFocusTypingAndTapsRespectImeVisibilityAndKeepCursor() {
        val oldPaused = ShadowChoreographer.isPaused()
        val oldFrameDelay = ShadowChoreographer.getFrameDelay()
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        ShadowChoreographer.setPaused(true)
        val activityController = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val view = ComposeView(activityController.get())
        val focusRequester = FocusRequester()
        val looper = Shadows.shadowOf(Looper.getMainLooper())
        var value by mutableStateOf(TextFieldValue(""))
        var focused = false
        var visibleByInsets = false
        var windowFocused = false
        var controllerShows = 0
        lateinit var nativeView: View
        val keyboard = object : SoftwareKeyboardController {
            override fun show() { controllerShows++ }
            override fun hide() = Unit
        }
        try {
            view.setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(1f, 1f),
                    LocalSoftwareKeyboardController provides keyboard
                ) {
                    visibleByInsets = ComposeWindowInsets.isImeVisible
                    windowFocused = LocalWindowInfo.current.isWindowFocused
                    nativeView = LocalView.current
                    EchoNativeTextField(
                        value = value,
                        onValueChange = { value = it },
                        modifier = Modifier.width(240.dp).height(80.dp)
                            .background(Color.White).onFocusChanged { focused = it.isFocused },
                        cursorBrush = SolidColor(Color.Red),
                        focusRequester = focusRequester,
                        floatingWindow = false
                    )
                }
            }
            activityController.get().setContentView(view, FrameLayout.LayoutParams(240, 80))
            fun tick() = looper.idleFor(Duration.ofMillis(16))
            repeat(5) { tick() }
            view.measure(
                View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, 240, 80)
            view.dispatchWindowFocusChanged(true)
            repeat(5) { tick() }
            fun tap(x: Float): Long {
                val now = SystemClock.uptimeMillis()
                val properties = MotionEvent.PointerProperties().apply {
                    id = 0
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }
                val coordinates = MotionEvent.PointerCoords().apply {
                    this.x = x
                    y = 8f
                    pressure = 1f
                    size = 1f
                }
                fun event(time: Long, action: Int) = MotionEvent.obtain(
                    now, time, action, 1, arrayOf(properties), arrayOf(coordinates),
                    0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0
                )
                val down = event(now, MotionEvent.ACTION_DOWN)
                val up = event(now + 20, MotionEvent.ACTION_UP)
                try {
                    view.dispatchTouchEvent(down)
                    looper.idleFor(Duration.ofMillis(20))
                    view.dispatchTouchEvent(up)
                } finally {
                    down.recycle()
                    up.recycle()
                }
                repeat(5) { tick() }
                return now
            }
            fun cursorVisibleWithinBlink(): Boolean {
                // The blink coroutine may use real-time delay in Robolectric. Advance both
                // wall time and the paused main looper across more than a full blink cycle.
                repeat(70) {
                    Thread.sleep(16)
                    tick()
                    val bitmap = Bitmap.createBitmap(240, 80, Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(bitmap))
                    for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                        val pixel = bitmap.getPixel(x, y)
                        if (AndroidColor.red(pixel) > 180 && AndroidColor.green(pixel) < 100 &&
                            AndroidColor.blue(pixel) < 100 && AndroidColor.alpha(pixel) > 128
                        ) return true
                    }
                }
                return false
            }
            focusRequester.requestFocus()
            repeat(5) { tick() }
            assertTrue("首次聚焦必须成功", focused)
            assertTrue("首次聚焦必须能看到竖线光标", cursorVisibleWithinBlink())

            // The root snapshot and Compose snapshot are independent; a stale Compose snapshot
            // must never override a current, available window-insets reading.
            assertTrue("实时根窗口 Insets 必须可用", ViewCompat.getRootWindowInsets(nativeView) != null)
            ImeInsetsViewRoot.visible = false
            assertFalse("实时不可见应优先于旧的可见快照", isImeVisibleNow(nativeView, true))
            ImeInsetsViewRoot.visible = true
            assertTrue("实时可见应优先于旧的不可见快照", isImeVisibleNow(nativeView, false))

            // Robolectric does not display a real keyboard. Feed the same public WindowInsets
            // visibility signal and verify that it actually reached Compose before testing a tap.
            view.dispatchApplyWindowInsets(
                WindowInsets.Builder()
                    .setInsets(WindowInsets.Type.ime(), Insets.of(0, 0, 0, 120))
                    .setVisible(WindowInsets.Type.ime(), true)
                    .build()
            )
            repeat(5) { tick() }
            assertTrue("测试前 Compose 必须观察到键盘可见", visibleByInsets)
            assertTrue("测试前实时窗口判断也必须为可见", isImeVisibleNow(nativeView, visibleByInsets))

            // A key event goes through the focused native editor instead of replacing its value.
            val now = SystemClock.uptimeMillis()
            view.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, 0))
            view.dispatchKeyEvent(KeyEvent(now, now + 1, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A, 0))
            repeat(8) { tick() }
            assertEquals("输入事件必须由同一个编辑器提交", "a", value.text)
            assertEquals(TextRange(1), value.selection)
            assertTrue("输入后焦点必须保留", focused)
            assertTrue("输入后竖线仍需可见", cursorVisibleWithinBlink())

            // Clear both counters only after first focus and the input event have settled.
            controllerShows = 0
            CountingInputMethodManager.showRequests = 0
            val firstTapDown = tap(8f)
            assertTrue("点击后焦点必须保留", focused)
            assertEquals("点击不得更改文字", "a", value.text)
            assertTrue("点击后的选区应折叠且在文字范围内: ${value.selection}", value.selection.collapsed &&
                value.selection.start in 0..value.text.length)
            assertTrue("点击后竖线仍需可见", cursorVisibleWithinBlink())
            assertEquals("键盘已显示时不得透传 Compose show", 0, controllerShows)
            assertEquals("键盘已显示时不得再向 IMM 请求 show", 0, CountingInputMethodManager.showRequests)
            val selectionAfterVisibleTap = value.selection

            // Simulate dismissal while keeping the same editor and its focus. A separate event
            // must report hidden before the next tap is allowed to request keyboard recovery.
            ImeInsetsViewRoot.visible = false
            view.dispatchApplyWindowInsets(
                WindowInsets.Builder()
                    .setInsets(WindowInsets.Type.ime(), Insets.NONE)
                    .setVisible(WindowInsets.Type.ime(), false)
                    .build()
            )
            repeat(5) { tick() }
            assertFalse("收起后 Compose 必须观察到键盘不可见", visibleByInsets)
            assertFalse("收起后实时窗口判断也必须为不可见", isImeVisibleNow(nativeView, visibleByInsets))
            assertTrue("收起键盘不能丢失输入框焦点", focused)
            assertTrue("恢复点击前窗口必须获焦", windowFocused)

            // Compose's pointer timeout uses two coroutine delays; in this Robolectric
            // setup they run on real time, so advance the main looper after each delay.
            // Advancing the paused looper alone would leave the second tap as a double tap.
            looper.idleFor(Duration.ofMillis(1000))
            Thread.sleep(500)
            looper.idle()
            Thread.sleep(100)
            looper.idle()
            assertEquals("第二次点击前选区必须保持", selectionAfterVisibleTap, value.selection)
            controllerShows = 0
            CountingInputMethodManager.showRequests = 0
            // Both positions resolve to the end of the one-character text.
            val secondTapDown = tap(100f)
            assertTrue("两次点击必须超过系统双击时间", secondTapDown - firstTapDown >
                ViewConfiguration.getDoubleTapTimeout())
            assertTrue("恢复点击后焦点必须保留", focused)
            assertTrue("恢复点击后窗口必须保持焦点", windowFocused)
            assertEquals("恢复点击不能更改文字", "a", value.text)
            assertEquals("恢复点击不得另外透传 Compose show", 0, controllerShows)
            assertEquals("键盘已收起时必须向 IMM 请求恰好一次 show", 1, CountingInputMethodManager.showRequests)
            assertEquals("点击文本末尾应保留折叠光标位置", selectionAfterVisibleTap, value.selection)
            assertTrue("恢复点击后选区必须折叠", value.selection.collapsed)
            assertTrue("恢复点击后竖线仍需可见", cursorVisibleWithinBlink())
            assertEquals("恢复点击不得另外透传 Compose show", 0, controllerShows)
            assertEquals("键盘已收起时必须向 IMM 请求恰好一次 show", 1,
                CountingInputMethodManager.showRequests)
        } finally {
            ImeInsetsViewRoot.visible = false
            view.dispatchWindowFocusChanged(false)
            view.disposeComposition()
            activityController.pause().stop().destroy()
            looper.idleFor(Duration.ofMillis(32))
            ShadowChoreographer.setFrameDelay(oldFrameDelay)
            ShadowChoreographer.setPaused(oldPaused)
        }
    }

    /** Independent native-field control for the same hidden-IME and repeated-tap sequence. */
    @Test
    @Config(shadows = [CountingInputMethodManager::class, PopupTitleResources::class, ImeInsetsViewRoot::class])
    fun nativeFieldCursorSurvivesHiddenImeAndRepeatedTap() {
        val oldPaused = ShadowChoreographer.isPaused()
        val oldFrameDelay = ShadowChoreographer.getFrameDelay()
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        ShadowChoreographer.setPaused(true)
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val view = ComposeView(activity.get())
        val focus = FocusRequester()
        val state = TextFieldState("")
        val looper = Shadows.shadowOf(Looper.getMainLooper())
        var focused = false
        var imeVisible = false
        var windowFocused = false
        var recoverOnShow = false
        var recoveryRequests = 0
        lateinit var nativeView: View
        val keyboard = object : SoftwareKeyboardController {
            override fun show() {
                if (recoverOnShow) {
                    recoveryRequests++
                    nativeView.requestFocus()
                    checkNotNull(nativeView.context.getSystemService(InputMethodManager::class.java))
                        .showSoftInput(nativeView, 0)
                }
            }
            override fun hide() = Unit
        }
        try {
            view.setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(1f, 1f),
                    LocalSoftwareKeyboardController provides keyboard
                ) {
                    imeVisible = ComposeWindowInsets.isImeVisible
                    windowFocused = LocalWindowInfo.current.isWindowFocused
                    nativeView = LocalView.current
                    BasicTextField(
                        state = state,
                        modifier = Modifier.width(240.dp).height(80.dp)
                            .background(Color.White).onFocusChanged { focused = it.isFocused }
                            .focusRequester(focus),
                        cursorBrush = SolidColor(Color.Red)
                    )
                }
            }
            activity.get().setContentView(view, FrameLayout.LayoutParams(240, 80))
            fun tick() = looper.idleFor(Duration.ofMillis(16))
            repeat(5) { tick() }
            view.measure(View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 240, 80)
            view.dispatchWindowFocusChanged(true)
            repeat(5) { tick() }
            fun cursorVisibleWithinBlink(): Boolean {
                // The blink coroutine may use real-time delay in Robolectric. Advance both
                // wall time and the paused main looper across more than a full blink cycle.
                repeat(70) {
                    Thread.sleep(16)
                    tick()
                    val bitmap = Bitmap.createBitmap(240, 80, Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(bitmap))
                    for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                        val pixel = bitmap.getPixel(x, y)
                        if (AndroidColor.red(pixel) > 180 && AndroidColor.green(pixel) < 100 &&
                            AndroidColor.blue(pixel) < 100 && AndroidColor.alpha(pixel) > 128
                        ) return true
                    }
                }
                return false
            }
            fun tap(x: Float): Long {
                val now = SystemClock.uptimeMillis()
                val properties = MotionEvent.PointerProperties().apply {
                    id = 0
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }
                val coordinates = MotionEvent.PointerCoords().apply {
                    this.x = x; y = 8f; pressure = 1f; size = 1f
                }
                fun event(time: Long, action: Int) = MotionEvent.obtain(now, time, action, 1,
                    arrayOf(properties), arrayOf(coordinates), 0, 0, 1f, 1f, 0, 0,
                    InputDevice.SOURCE_TOUCHSCREEN, 0)
                val down = event(now, MotionEvent.ACTION_DOWN)
                val up = event(now + 20, MotionEvent.ACTION_UP)
                try {
                    view.dispatchTouchEvent(down)
                    looper.idleFor(Duration.ofMillis(20))
                    view.dispatchTouchEvent(up)
                } finally {
                    down.recycle(); up.recycle()
                }
                repeat(5) { tick() }
                return now
            }
            focus.requestFocus()
            repeat(5) { tick() }
            assertTrue("原生字段首次聚焦必须成功", focused)
            assertTrue("原生字段首次聚焦时光标必须可见", cursorVisibleWithinBlink())
            ImeInsetsViewRoot.visible = true
            view.dispatchApplyWindowInsets(WindowInsets.Builder()
                .setInsets(WindowInsets.Type.ime(), Insets.of(0, 0, 0, 120))
                .setVisible(WindowInsets.Type.ime(), true).build())
            repeat(5) { tick() }
            assertTrue("原生对照必须观察到键盘可见", imeVisible)
            val now = SystemClock.uptimeMillis()
            view.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, 0))
            view.dispatchKeyEvent(KeyEvent(now, now + 1, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A, 0))
            repeat(8) { tick() }
            assertEquals("原生字段必须实际输入", "a", state.text.toString())
            assertEquals(TextRange(1), state.selection)
            assertTrue("原生字段输入后光标必须可见", cursorVisibleWithinBlink())
            val firstTap = tap(8f)
            assertTrue("原生字段第一次点击后光标必须可见", cursorVisibleWithinBlink())
            val selectionAfterFirstTap = state.selection
            ImeInsetsViewRoot.visible = false
            view.dispatchApplyWindowInsets(WindowInsets.Builder()
                .setInsets(WindowInsets.Type.ime(), Insets.NONE)
                .setVisible(WindowInsets.Type.ime(), false).build())
            repeat(5) { tick() }
            assertFalse("原生对照必须观察到键盘收起", imeVisible)
            assertTrue("原生字段收起键盘后必须保持焦点", focused)
            assertTrue("原生对照恢复点击前窗口必须获焦", windowFocused)
            looper.idleFor(Duration.ofMillis(1000))
            Thread.sleep(500)
            looper.idle()
            Thread.sleep(100)
            looper.idle()
            assertEquals("原生字段再次点击前选区保持", selectionAfterFirstTap, state.selection)
            CountingInputMethodManager.showRequests = 0
            recoverOnShow = true
            val secondTap = tap(100f)
            assertEquals("原生对照必须执行一次相同的恢复路径", 1, recoveryRequests)
            assertEquals("原生对照必须向 IMM 请求恰好一次 show", 1,
                CountingInputMethodManager.showRequests)
            assertTrue("原生对照的两次点击必须超过双击时间", secondTap - firstTap >
                ViewConfiguration.getDoubleTapTimeout())
            assertTrue("原生字段再次点击后焦点必须保留", focused)
            assertTrue("原生对照恢复点击后窗口必须保持焦点", windowFocused)
            assertEquals("原生字段再次点击不能更改文字", "a", state.text.toString())
            assertEquals("原生字段再次点击应保持选区", selectionAfterFirstTap, state.selection)
            assertTrue("原生字段再次点击后选区必须折叠", state.selection.collapsed)
            assertTrue("原生字段恢复点击后竖线仍需可见", cursorVisibleWithinBlink())
        } finally {
            ImeInsetsViewRoot.visible = false
            view.dispatchWindowFocusChanged(false)
            view.disposeComposition()
            activity.pause().stop().destroy()
            looper.idleFor(Duration.ofMillis(32))
            ShadowChoreographer.setFrameDelay(oldFrameDelay)
            ShadowChoreographer.setPaused(oldPaused)
        }
    }

    private fun checkCursorRendering() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val view = ComposeView(controller.get())
        val focus = FocusRequester()
        val siblingFocus = FocusRequester()
        val interactions = MutableInteractionSource()
        val looper = Shadows.shadowOf(Looper.getMainLooper())
        val identities = Collections.newSetFromMap(IdentityHashMap<TextFieldDecorator, Boolean>())
        var revision by mutableIntStateOf(0)
        var simulatedFloating by mutableStateOf(false)
        var value by mutableStateOf(TextFieldValue("AB", selection = TextRange(1)))
        var focused = false
        var windowFocused = false
        var siblingFocused = false
        var mounts = 0
        var disposals = 0
        var sideEffects = 0
        var committedFloating = false
        val keyboard = object : SoftwareKeyboardController {
            override fun show() = Unit
            override fun hide() = Unit
        }
        try {
            view.setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(1f, 1f),
                    LocalSoftwareKeyboardController provides keyboard
                ) {
                    windowFocused = LocalWindowInfo.current.isWindowFocused
                    val currentRevision = revision
                    val floating = simulatedFloating
                    val decorator = TextFieldDecorator { inner ->
                        if (currentRevision >= 0) {
                            DisposableEffect(Unit) {
                                mounts++
                                onDispose { disposals++ }
                            }
                            Box(Modifier.fillMaxSize()) { inner() }
                        }
                    }
                    SideEffect { identities.add(decorator); sideEffects++; committedFloating = floating }
                    Box {
                        Box(Modifier.width(1.dp).height(1.dp)
                            .focusRequester(siblingFocus)
                            .onFocusChanged { siblingFocused = it.isFocused }
                            .focusable())
                        EchoNativeTextField(
                            value = value,
                            onValueChange = { value = it },
                            modifier = Modifier.width(240.dp).height(80.dp)
                                .background(Color.White).onFocusChanged { focused = it.isFocused },
                            focusRequester = focus,
                            interactionSource = interactions,
                            cursorBrush = SolidColor(Color.Red),
                            decorator = decorator,
                            floatingWindow = floating
                        )
                    }
                }
            }
            controller.get().setContentView(view, FrameLayout.LayoutParams(240, 80))
            fun tick() = looper.idleFor(Duration.ofMillis(16))
            repeat(5) { tick() }
            val w = View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY)
            val h = View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.EXACTLY)
            view.measure(w, h)
            view.layout(0, 0, 240, 80)
            view.dispatchWindowFocusChanged(true)
            repeat(5) { tick() }

            fun hasRedPixel(): Boolean {
                val bitmap = Bitmap.createBitmap(240, 80, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                    val p = bitmap.getPixel(x, y)
                    if (AndroidColor.red(p) > 180 && AndroidColor.green(p) < 100 &&
                        AndroidColor.blue(p) < 100 && AndroidColor.alpha(p) > 128
                    ) return true
                }
                return false
            }
            fun visibleWithinFrames(): Boolean {
                repeat(70) {
                    Thread.sleep(16)
                    tick()
                    if (hasRedPixel()) return true
                }
                return false
            }
            siblingFocus.requestFocus()
            repeat(5) { tick() }
            assertTrue("阴性对照前兄弟控件必须获焦", siblingFocused)
            assertFalse("阴性对照前字段必须确实失焦", focused)
            assertTrue("阴性对照前窗口仍须获焦", windowFocused)
            assertFalse("失焦红色像素阴性对照", hasRedPixel())
            focus.requestFocus()
            assertTrue("测试前字段应获焦", focused)
            assertFalse("字段获焦后兄弟控件必须失焦", siblingFocused)
            assertTrue("测试前窗口应获焦", windowFocused)
            assertTrue("首次聚焦的光标像素正对照", visibleWithinFrames())

            val oldSideEffects = sideEffects
            repeat(3) {
                revision++
                repeat(4) { tick() }
                assertTrue("装饰器更新后字段焦点应保留", focused)
                assertTrue("装饰器更新后光标仍需可见", visibleWithinFrames())
            }
            assertTrue("应实际完成多次重组", sideEffects > oldSideEffects)
            assertTrue("应实际构建不同装饰器实例", identities.size >= 2)
            assertEquals("正常重组不应卸载内部子树", 0, disposals)

            for (floatingMode in listOf(true, false)) {
                val previousMounts = mounts
                val previousDisposals = disposals
                simulatedFloating = floatingMode
                repeat(4) { tick() }
                assertEquals("必须实际完成窗口模式对应的重组", floatingMode, committedFloating)
                assertTrue("切换窗口分支时字段焦点应保留", focused)
                assertEquals("窗口分支切换不得改变文本", "AB", value.text)
                assertEquals("窗口分支切换不得改变折叠选区", TextRange(1), value.selection)
                assertTrue("切换窗口分支后光标仍需可见", visibleWithinFrames())
                assertEquals("窗口分支切换不得卸载内部子树", previousDisposals, disposals)
                assertEquals("窗口分支切换不得重建内部子树", previousMounts, mounts)
            }
        } finally {
            view.dispatchWindowFocusChanged(false)
            view.disposeComposition()
            controller.pause().stop().destroy()
            looper.idleFor(Duration.ofMillis(32))
        }
    }
}

/** Counts repeated requests, including requests that do not change fake IME visibility. */
@Implements(InputMethodManager::class)
class CountingInputMethodManager : ShadowInputMethodManager() {
    companion object { @JvmField var showRequests = 0 }

    @Implementation
    protected override fun showSoftInput(view: View, flags: Int): Boolean {
        showRequests++
        return super.showSoftInput(view, flags)
    }
}

/** The unit test has no merged Android resources, while Compose popups need this one title. */
@Implements(Resources::class)
class PopupTitleResources : ShadowResources() {
    @RealObject private lateinit var realResources: Resources

    @Implementation
    protected fun getString(id: Int): String =
        if (id == androidx.compose.ui.R.string.default_popup_window_title) "Popup"
        else Shadow.directlyOn(realResources, Resources::class.java).getString(id)
}


/** Gives the attached Android root its own IME visibility independent of dispatched insets. */
@Implements(className = "android.view.ViewRootImpl", isInAndroidSdk = false)
class ImeInsetsViewRoot : ShadowViewRootImpl() {
    companion object { @JvmField var visible = false }

    @Implementation(minSdk = 30)
    protected override fun getWindowInsets(forceConstruct: Boolean): WindowInsets =
        WindowInsets.Builder(super.getWindowInsets(forceConstruct))
            .setInsets(WindowInsets.Type.ime(), if (visible) Insets.of(0, 0, 0, 120) else Insets.NONE)
            .setVisible(WindowInsets.Type.ime(), visible)
            .build()
}
