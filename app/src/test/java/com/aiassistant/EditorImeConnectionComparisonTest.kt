package com.aiassistant

import android.app.Application
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.aiassistant.ui.components.EchoNativeTextField
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.shadows.ShadowInputMethodManager

/** Compare the actual Compose input connection, not just a synthetic TextFieldValue echo. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, manifest = Config.NONE,
    shadows = [SelectionRecordingInputMethodManager::class, PopupTitleResources::class, ImeInsetsViewRoot::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalFoundationApi::class)
class EditorImeConnectionComparisonTest {
    private data class Observed(val selection: List<List<Int>>, val restarts: Int)

    @Test fun compositionCommitAndSelectionTapMatchNativeField() {
        val oldPaused = ShadowChoreographer.isPaused()
        val oldFrameDelay = ShadowChoreographer.getFrameDelay()
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        ShadowChoreographer.setPaused(true)
        try {
            val native = exercise(echo = false)
            val echo = exercise(echo = true)
            assertTrue("正对照必须观察到选区更新", native.selection.isNotEmpty())
            assertEquals("输入连接重启次数应与原生字段相同", native.restarts, echo.restarts)
            assertEquals("点击后的选区通知应与原生字段相同", native.selection, echo.selection)
        } finally {
            ShadowChoreographer.setFrameDelay(oldFrameDelay)
            ShadowChoreographer.setPaused(oldPaused)
        }
    }

    private fun exercise(echo: Boolean): Observed {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val view = ComposeView(activity.get())
        val focus = FocusRequester()
        val looper = Shadows.shadowOf(Looper.getMainLooper())
        lateinit var nativeView: View
        var nativeState: TextFieldState? = null
        var value by mutableStateOf(TextFieldValue("abc", selection = TextRange(3)))
        try {
            view.setContent {
                nativeView = LocalView.current
                val modifier = Modifier.width(240.dp).height(80.dp).focusRequester(focus)
                if (echo) {
                    EchoNativeTextField(value = value, onValueChange = { value = it },
                        modifier = modifier, floatingWindow = false)
                } else {
                    val state = remember { TextFieldState("abc", TextRange(3)) }
                    nativeState = state
                    BasicTextField(state = state, modifier = modifier)
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
            focus.requestFocus()
            repeat(10) { tick() }
            val connection = nativeView.onCreateInputConnection(EditorInfo())
            assertNotNull("必须建立真实 Compose InputConnection", connection)
            assertTrue(connection!!.setComposingText("ni", 1))
            repeat(8) { tick() }
            val composingText = if (echo) value.text else checkNotNull(nativeState).text.toString()
            val composingSelection = if (echo) value.selection else checkNotNull(nativeState).selection
            val composition = if (echo) value.composition else checkNotNull(nativeState).composition
            assertEquals("输入法组合文字必须同步到编辑器", "abcni", composingText)
            assertEquals("组合文字的光标位于末尾", TextRange(5), composingSelection)
            assertEquals("组合区间须保留直到上屏", TextRange(3, 5), composition)
            assertTrue(connection.commitText("你", 1))
            repeat(8) { tick() }
            val committedText = if (echo) value.text else checkNotNull(nativeState).text.toString()
            val committedSelection = if (echo) value.selection else checkNotNull(nativeState).selection
            assertEquals("组合内容必须实际提交到编辑器", "abc你", committedText)
            assertEquals("上屏后光标位于末尾", TextRange(4), committedSelection)
            assertEquals("上屏后组合区间必须结束", null,
                if (echo) value.composition else checkNotNull(nativeState).composition)
            SelectionRecordingInputMethodManager.selection.clear()
            SelectionRecordingInputMethodManager.restarts = 0
            val now = SystemClock.uptimeMillis()
            val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER }
            val coordinates = MotionEvent.PointerCoords().apply { x = 2f; y = 8f; pressure = 1f; size = 1f }
            fun event(time: Long, action: Int) = MotionEvent.obtain(now, time, action, 1,
                arrayOf(properties), arrayOf(coordinates), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            val down = event(now, MotionEvent.ACTION_DOWN)
            val up = event(now + 20, MotionEvent.ACTION_UP)
            try { view.dispatchTouchEvent(down); looper.idleFor(Duration.ofMillis(20)); view.dispatchTouchEvent(up) }
            finally { down.recycle(); up.recycle() }
            repeat(15) { tick() }
            val tappedSelection = if (echo) value.selection else checkNotNull(nativeState).selection
            assertTrue("点击须实际移动光标", tappedSelection.collapsed && tappedSelection.start < 4)
            return Observed(SelectionRecordingInputMethodManager.selection.toList(),
                SelectionRecordingInputMethodManager.restarts)
        } finally {
            view.dispatchWindowFocusChanged(false)
            view.disposeComposition()
            activity.pause().stop().destroy()
            looper.idleFor(Duration.ofMillis(32))
        }
    }
}

@Implements(InputMethodManager::class)
class SelectionRecordingInputMethodManager : ShadowInputMethodManager() {
    companion object {
        val selection = mutableListOf<List<Int>>()
        var restarts = 0
    }
    @Implementation
    protected fun updateSelection(view: View, selStart: Int, selEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        selection.add(listOf(selStart, selEnd, candidatesStart, candidatesEnd))
    }
    @Implementation
    override fun restartInput(view: View) {
        restarts++
        super.restartInput(view)
    }
}
