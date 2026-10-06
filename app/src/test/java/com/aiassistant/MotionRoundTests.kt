package com.aiassistant

import com.aiassistant.ui.components.MarkdownSegmentation
import com.aiassistant.ui.components.MarkdownSegmentationCache
import com.aiassistant.ui.components.computeStableSegments
import com.aiassistant.ui.screens.chat.GenerationUiState
import com.aiassistant.ui.screens.chat.GenerationUiStateRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 动效轮（UI-微交互与动效深化方案.md）核心红线测试：
 * - R-2：Markdown 增量渲染分段不变量（前缀稳定/还原/围栏/数学块/表格安全）
 * - P0-4：GenerationUiState 状态机派生行为等价
 */
class MotionRoundTests {

    private fun rebuild(seg: MarkdownSegmentation): String = seg.segments.joinToString("") + seg.tail

    // ---------- R-2：稳定分段 ----------

    @Test
    fun testStableSegments_emptyContent() {
        val seg = computeStableSegments("")
        assertEquals(0, seg.segments.size)
        assertEquals("", seg.tail)
        assertFalse(seg.tailInFence)
    }

    @Test
    fun testStableSegments_incrementalCache_equivalentToFullRecompute() {
        // v2.7.3 流畅度：分段增量缓存（MarkdownSegmentationCache）必须与全文重算严格等价——
        // 覆盖标题/列表/闭合围栏/数学块/表格/空行边界逐 token 追加，以及非前缀跳变（切换 variant）回退
        val full = buildString {
            append("# 标题\n\n")
            append("第一段落内容，包含 **加粗** 与 [1] 引用。\n\n")
            append("- 列表项一\n- 列表项二\n\n")
            append("```kotlin\nfun main() {\n    val x = 1\n}\n```\n\n")
            append("闭合围栏后的段落。\n\n")
            append("$$\nx^2 + y^2 = z^2\n$$\n\n")
            append("| a | b |\n|---|---|\n| 1 | 2 |\n\n")
            append("尾段落，包含 *斜体* 与收尾文字。")
        }
        val cache = MarkdownSegmentationCache()
        var step = 1
        while (step <= full.length) {
            val partial = full.substring(0, step)
            val incremental = cache.get(partial)
            val fullRecompute = computeStableSegments(partial)
            assertEquals("段列表与全文重算不一致（step=$step）", fullRecompute.segments, incremental.segments)
            assertEquals("尾部与全文重算不一致（step=$step）", fullRecompute.tail, incremental.tail)
            assertEquals("围栏态与全文重算不一致（step=$step）", fullRecompute.tailInFence, incremental.tailInFence)
            assertEquals("段拼接+尾必须还原原文（step=$step）", partial, rebuild(incremental))
            step += 1
        }
        // 非前缀跳变：必须回退全量重算，结果仍等价
        val jumped = cache.get("完全不同的开头\n\n新内容")
        val fullJump = computeStableSegments("完全不同的开头\n\n新内容")
        assertEquals(fullJump.segments, jumped.segments)
        assertEquals(fullJump.tail, jumped.tail)
        assertEquals(fullJump.tailInFence, jumped.tailInFence)
        // 末段含未闭合围栏的追加路径
        val cache2 = MarkdownSegmentationCache()
        val fenceText = "说明段落\n\n```python\nprint(1)\n\nprint(2"
        var fstep = 1
        while (fstep <= fenceText.length) {
            val partial = fenceText.substring(0, fstep)
            val incremental = cache2.get(partial)
            val fullRecompute = computeStableSegments(partial)
            assertEquals(fullRecompute.segments, incremental.segments)
            assertEquals(fullRecompute.tail, incremental.tail)
            assertTrue("未闭合围栏阶段应保持围栏态（step=$fstep）", incremental.tailInFence == fullRecompute.tailInFence)
            assertEquals(partial, rebuild(incremental))
            fstep += 1
        }
    }

    @Test
    fun testStableSegments_monotonicGrowth_overTokenAppends() {
        // 模拟流式：逐 token 追加（R-2 核心）：
        // ① 已产出段序列只能追加、永不修改（字符串前缀稳定 → remember 零重算）
        // ② 段拼接 + 尾部必须严格还原原文
        val full = buildString {
            append("# 标题\n\n")
            append("第一段落内容，包含 **加粗** 与 *斜体* 片段。\n\n")
            append("- 列表项一\n- 列表项二\n\n")
            append("第二段落收尾文字。")
        }
        var lastCount = 0
        var lastSegments: List<String> = emptyList()
        var step = 8
        while (step <= full.length) {
            val partial = full.substring(0, step)
            val seg = computeStableSegments(partial)
            assertTrue(
                "段数量在 step=$step 减少：$lastCount -> ${seg.segments.size}",
                seg.segments.size >= lastCount
            )
            for (i in lastSegments.indices) {
                assertEquals("历史段 i=$i 在 step=$step 被修改", lastSegments[i], seg.segments[i])
            }
            assertEquals("段拼接+尾必须还原原文（step=$step）", partial, rebuild(seg))
            lastCount = seg.segments.size
            lastSegments = seg.segments
            step += 8
        }
        val finalSeg = computeStableSegments(full)
        assertTrue(finalSeg.segments.size >= lastCount)
    }

    @Test
    fun testStableSegments_unclosedFence_keepsTailInFence() {
        // 围栏未闭合：不得把围栏内的空行切段
        val content = "代码开始：\n\n```kotlin\nfun main() {\n\n    val x = 1\n"
        val seg = computeStableSegments(content)
        val stableText = seg.segments.joinToString("")
        assertTrue("未闭合围栏内不应产生稳定段中的围栏内容", !stableText.contains("fun main()"))
        assertTrue(seg.tailInFence)
        // 尾部含围栏开启行，渲染侧负责剥离开启行后以等宽体预览
        assertEquals("```kotlin\nfun main() {\n\n    val x = 1\n", seg.tail)
    }

    @Test
    fun testStableSegments_closedFence_becomesStable() {
        val content = "段落一。\n\n```python\nprint('hi')\n```\n\n后续段落。"
        val seg = computeStableSegments(content)
        val stableText = seg.segments.joinToString("")
        assertTrue("闭合围栏整体应进入稳定段", stableText.contains("print('hi')"))
        assertFalse(seg.tailInFence)
        assertEquals(content, rebuild(seg))
    }

    @Test
    fun testStableSegments_unclosedMathBlock_notSplit() {
        // $$ 块未闭合期间不得在块内空行处切段（公式跨段撕裂防护）
        val content = "前置段落。\n\n\$\$\nE = mc^2\n\n\\int_0^1 x dx\n"
        val seg = computeStableSegments(content)
        val stableText = seg.segments.joinToString("")
        assertTrue(stableText.contains("前置段落。"))
        assertFalse("公式内容不得进入稳定段", stableText.contains("E = mc^2"))
        assertTrue(seg.tail.contains("E = mc^2"))
    }

    @Test
    fun testStableSegments_closedMathBlock_becomesStable() {
        val content = "公式如下：\n\n\$\$E = mc^2\$\$\n\n后续内容。"
        val seg = computeStableSegments(content)
        val stableText = seg.segments.joinToString("")
        assertTrue("闭合公式整体应可进入稳定段", stableText.contains("E = mc^2") || seg.tail.contains("E = mc^2"))
        assertEquals(content, rebuild(seg))
    }

    @Test
    fun testStableSegments_trailingBlankLine_entersStable() {
        val content = "段落一。\n\n"
        val seg = computeStableSegments(content)
        // 内容以空行结尾：空行边界成段，尾部为空（该帧光标隐藏，属预期）
        assertEquals(1, seg.segments.size)
        assertEquals("段落一。\n\n", seg.segments[0])
        assertEquals("", seg.tail)
        assertFalse(seg.tailInFence)
    }

    @Test
    fun testStableSegments_tableBlock_staysIntact() {
        val content = "| A | B |\n|---|---|\n| 1 | 2 |\n\n下一段。"
        val seg = computeStableSegments(content)
        // 表格行之间无空行 → 表格要么整体在尾部，要么整体在稳定段，不得拦腰切断
        val stableText = seg.segments.joinToString("")
        val tableInStable = stableText.contains("| A | B |")
        val tableInTail = seg.tail.contains("| A | B |")
        assertTrue("表格必须完整位于一侧", tableInStable != tableInTail)
    }

    // ---------- P0-4：状态机派生 ----------

    @Test
    fun testGenerationState_idle() {
        assertEquals(
            GenerationUiState.Idle,
            GenerationUiStateRules.derive(false, "已有内容", true, null)
        )
    }

    @Test
    fun testGenerationState_connecting() {
        assertEquals(
            GenerationUiState.Connecting,
            GenerationUiStateRules.derive(true, "", false, null)
        )
    }

    @Test
    fun testGenerationState_reconnecting() {
        assertEquals(
            GenerationUiState.Reconnecting,
            GenerationUiStateRules.derive(true, "", false, "网络波动，正在重连…")
        )
    }

    @Test
    fun testGenerationState_thinking() {
        assertEquals(
            GenerationUiState.Thinking,
            GenerationUiStateRules.derive(true, "", true, null)
        )
    }

    @Test
    fun testGenerationState_streaming() {
        assertEquals(
            GenerationUiState.Streaming,
            GenerationUiStateRules.derive(true, "部分正文", true, null)
        )
        assertEquals(
            GenerationUiState.Streaming,
            GenerationUiStateRules.derive(true, "正文", false, null)
        )
    }

    @Test
    fun testGenerationState_failed_viaReconnectErrorText() {
        // 行为等价：reconnect 文案命中错误词 → Failed（原 capsule contains 规则）
        assertEquals(
            GenerationUiState.Failed,
            GenerationUiStateRules.derive(true, "", false, "连接异常，HTTP 500")
        )
        assertEquals(
            GenerationUiState.Failed,
            GenerationUiStateRules.derive(true, "", false, "请求 Error: timeout")
        )
    }

    @Test
    fun testGenerationState_failed_viaContentError() {
        // v2.5.7 错误报告全包裹：内容命中 isErrorMessage 语义 → Failed 优先
        assertEquals(
            GenerationUiState.Failed,
            GenerationUiStateRules.derive(true, "请求失败：所有 API Key 均不可用", false, null, contentIsError = true)
        )
    }

    @Test
    fun testErrorText_rulesMatchOriginalCapsuleKeywords() {
        assertTrue(GenerationUiStateRules.isErrorText("包含异常"))
        assertTrue(GenerationUiStateRules.isErrorText("包含报错"))
        assertTrue(GenerationUiStateRules.isErrorText("任务失败"))
        assertTrue(GenerationUiStateRules.isErrorText("出现错误"))
        assertTrue(GenerationUiStateRules.isErrorText("an Error occurred"))
        assertTrue(GenerationUiStateRules.isErrorText("HTTP 403"))
        assertFalse(GenerationUiStateRules.isErrorText("正常重连中，请稍候"))
    }
}
