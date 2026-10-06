package com.aiassistant

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.aiassistant.ui.components.cleanLeadingStarArtifacts
import com.aiassistant.ui.components.parseInlineMarkdown
import com.aiassistant.ui.screens.settings.CurrentVersionUserUpdates
import com.aiassistant.ui.screens.settings.V224UserUpdates
import org.junit.Assert.*
import org.junit.Test

class V224FeaturesTest {

    @Test
    fun testV224UserUpdatesCompleteness() {
        assertEquals("V2.2.4 用户更新日志数量应为 6 项", 6, V224UserUpdates.size)
        assertTrue("CurrentVersionUserUpdates 数量必须大于等于 5 项满足基准", CurrentVersionUserUpdates.size >= 5)
        assertTrue("必须包含全角星号更新", V224UserUpdates.any { it.contains("全角星号") })
        assertTrue("必须包含首尾非对称星号更新", V224UserUpdates.any { it.contains("非对称星号") })
        assertTrue("必须包含防跨词贪婪错配更新", V224UserUpdates.any { it.contains("防跨词贪婪") || it.contains("贪婪吞噬") })
        assertTrue("必须包含跨行加粗更新", V224UserUpdates.any { it.contains("跨行加粗") })
        assertTrue("必须包含字体合成保底更新", V224UserUpdates.any { it.contains("字体合成") })
        assertTrue("必须包含用户消息气泡 Markdown 更新", V224UserUpdates.any { it.contains("用户消息气泡") })
    }

    @Test
    fun testFullWidthAsteriskNormalizationAndRendering() {
        // 1. 验证全角星号（＊）在 cleanLeadingStarArtifacts 阶段被彻底归一化为半角星号（*）
        val rawFullWidth = "这是＊＊＊全角粗斜体＊＊＊，那是＊＊全角粗体＊＊"
        val cleaned = cleanLeadingStarArtifacts(rawFullWidth)
        assertEquals("这是***全角粗斜体***，那是**全角粗体**", cleaned)

        // 2. 验证 parseInlineMarkdown 渲染全角粗斜体
        val parsedBoldItalic = parseInlineMarkdown("这是＊＊＊全角粗斜体＊＊＊测试")
        assertEquals("这是全角粗斜体测试", parsedBoldItalic.text)
        val hasBold = parsedBoldItalic.spanStyles.any { it.item.fontWeight == FontWeight.Bold }
        val hasItalic = parsedBoldItalic.spanStyles.any { it.item.fontStyle == FontStyle.Italic }
        assertTrue("全角三星号必须被成功渲染为加粗样式", hasBold)
        assertTrue("全角三星号必须被成功渲染为斜体样式", hasItalic)

        // 3. 验证 parseInlineMarkdown 渲染全角粗体
        val parsedBold = parseInlineMarkdown("这是＊＊全角粗体＊＊测试")
        assertEquals("这是全角粗体测试", parsedBold.text)
        val boldStyles = parsedBold.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        assertTrue("全角双星号必须被成功渲染为加粗样式", boldStyles.isNotEmpty())
    }

    @Test
    fun testAsymmetricalAsterisksParsing() {
        // 1. 开头 3 个星号，结尾 2 个星号 (***text**)
        val parsed3to2 = parseInlineMarkdown("这是***非对称加粗一**测试")
        assertEquals("这是非对称加粗一测试", parsed3to2.text)
        val bold3to2 = parsed3to2.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        assertTrue("开头 3 星结尾 2 星必须成功提取并渲染加粗", bold3to2.isNotEmpty())
        assertFalse("不应残留任何孤立星号", parsed3to2.text.contains("*"))

        // 2. 开头 2 个星号，结尾 3 个星号 (**text***)
        val parsed2to3 = parseInlineMarkdown("这是**非对称加粗二***测试")
        assertEquals("这是非对称加粗二测试", parsed2to3.text)
        val bold2to3 = parsed2to3.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        assertTrue("开头 2 星结尾 3 星必须成功提取并渲染加粗", bold2to3.isNotEmpty())
        assertFalse("不应残留任何孤立星号", parsed2to3.text.contains("*"))

        // 3. 4 个星号强化粗体 (****text****)
        val parsed4 = parseInlineMarkdown("这是****四星加粗****测试")
        assertEquals("这是四星加粗测试", parsed4.text)
        val bold4 = parsed4.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        assertTrue("四星号加粗必须成功提取并渲染加粗", bold4.isNotEmpty())
        assertFalse("不应残留任何孤立星号", parsed4.text.contains("*"))
    }

    @Test
    fun testNonGreedyMatchingPreventsCrossWordSwallowing() {
        // 当一行内同时存在非对称星号与后置完整星号时，不得跳过近邻闭合符发生贪婪错配吞噬
        val input = "***重点一** 说明文本 ***重点二***"
        val parsed = parseInlineMarkdown(input)
        assertEquals("重点一 说明文本 重点二", parsed.text)

        val boldSpans = parsed.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        assertTrue("重点一与重点二均应被正确加粗", boldSpans.size >= 2)
        assertFalse("不应残留未闭合星号符号", parsed.text.contains("*"))
    }

    @Test
    fun testEscapedAsterisksNormalization() {
        // 大模型经常输出带有反斜杠转义的星号，如 \***text\*** 或 \*\*text\*\*
        val inputTriple = "提示：\\***重点关注事项\\***请知悉"
        val parsedTriple = parseInlineMarkdown(inputTriple)
        assertEquals("提示：重点关注事项请知悉", parsedTriple.text)
        assertTrue("转义三星号应被正确渲染为粗体", parsedTriple.spanStyles.any { it.item.fontWeight == FontWeight.Bold })

        val inputDouble = "提示：\\**次要关注事项\\**请知悉"
        val parsedDouble = parseInlineMarkdown(inputDouble)
        assertEquals("提示：次要关注事项请知悉", parsedDouble.text)
        assertTrue("转义双星号应被正确渲染为粗体", parsedDouble.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
    }

    @Test
    fun testStandardMarkdownFormattingPreserved() {
        // 常规 ***text*** 粗斜体
        val standard = parseInlineMarkdown("***标准粗斜体***")
        assertEquals("标准粗斜体", standard.text)
        assertTrue("必须具备加粗", standard.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertTrue("必须具备斜体", standard.spanStyles.any { it.item.fontStyle == FontStyle.Italic })

        // 常规 **text** 粗体
        val boldOnly = parseInlineMarkdown("**标准粗体**")
        assertEquals("标准粗体", boldOnly.text)
        assertTrue("必须具备加粗", boldOnly.spanStyles.any { it.item.fontWeight == FontWeight.Bold })

        // 孤立单星号
        val single = parseInlineMarkdown("*")
        assertEquals("*", single.text)
    }
}
