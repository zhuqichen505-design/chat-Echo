package com.aiassistant.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Echo 动效令牌 (EchoMotion)
 * 微交互与动效深化方案（第二轮 Polish）的单一事实来源：
 * 所有时长、缓动、弹簧参数先入令牌，组件消费令牌，页面禁止手写 `tween(NNN)` 裸值。
 *
 * 铁律：流式输出流畅度优先于一切动效——循环动画只允许驱动渲染层（Canvas/graphicsLayer），
 * 禁止驱动布局或重组（见方案 §六 R-3/R-7）。
 */
object EchoMotion {

    /** 时长阶梯（ms） */
    object Duration {
        const val instant = 80      // 按压下沉、hover 变色
        const val fast = 150        // 图标切换、徽章出现、文案交叉淡换
        const val standard = 250    // 菜单展开、横幅滑入
        const val emphasized = 350  // 弹窗进出、删除滑出
        const val slow = 500        // shimmer 一个周期、落定动画
    }

    /** 循环动画周期（ms），不属于一次性时长阶梯 */
    object Cycle {
        const val pulse = 1600        // 连接脉冲环呼吸周期
        const val reconnectArc = 1200 // 重连双弧旋转一圈
        const val thinkingDots = 1500 // 思考等待「呼吸光环点」单点脉冲周期
        const val shimmer = 1500      // 骨架屏高光扫描周期
    }

    /** 打字机节奏（见方案 P0-2） */
    object Typewriter {
        const val frameBudgetMs = 33L  // 合帧窗口：≈30fps 消费流式 token
        const val cursorBlinkMs = 530  // 光标呼吸周期
        const val cursorFadeMs = 300   // 落定后光标淡出时长
    }

    /** 缓动曲线（对齐 M3 Motion 规范） */
    object Easing {
        val standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)          // 通用
        val decelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)   // 入场（快出慢落）
        val accelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)   // 出场（慢起快收）
        val emphasizedDecelerate = CubicBezierEasing(0f, 0f, 0f, 1f)// 菜单锚点展开
        val linear = LinearEasing
    }

    /** 弹簧（替代 animateContentSize 默认值等） */
    object Spring {
        /** 尺寸变化：柔和弹簧 */
        fun <T> gentle(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)
        /** 按压回弹：轻快弹簧 */
        fun <T> snappy(): SpringSpec<T> = spring(dampingRatio = 0.75f, stiffness = 600f)
    }

    /** 一次性时长动画：唯一允许出现 `tween(` 字样的构造入口（EchoMotion.kt 定义处除外） */
    fun <T> tweenSpec(
        durationMillis: Int = Duration.standard,
        easing: CubicBezierEasing = Easing.standard,
        delayMillis: Int = 0
    ): TweenSpec<T> = tween(durationMillis, delayMillis, easing)

    /** 循环动画：正向播放一次时长后反向回放 */
    fun <T> reverseCycleSpec(
        durationMillis: Int,
        easing: CubicBezierEasing = Easing.standard
    ): InfiniteRepeatableSpec<T> = infiniteRepeatable(tween(durationMillis, easing = easing), RepeatMode.Reverse)

    /** 循环动画：单向往复（0→1 线性循环） */
    fun <T> linearCycleSpec(durationMillis: Int): InfiniteRepeatableSpec<T> =
        infiniteRepeatable(tween(durationMillis, easing = LinearEasing), RepeatMode.Restart)
}

/**
 * 全局降级开关：系统"移除动画"无障碍设置开启（动画时长缩放 = 0）时，
 * 一切装饰性动效归零（保留状态变化本身）。方案 §2.2 三档降级之"关闭"档。
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f
            ) == 0f
        }.getOrDefault(false)
    }
}
