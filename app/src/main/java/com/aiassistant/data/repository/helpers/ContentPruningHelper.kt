package com.aiassistant.data.repository.helpers

import java.util.regex.Pattern

/**
 * L1 档位与分档压缩请求组装层轻量修剪助手
 * 核心准则：仅在向模型组装请求报文时内存修剪，绝不修改或物理删除数据库消息！
 */
object ContentPruningHelper {

    private val OCR_BLOCK_REGEX = Regex("""\[(?:图片|PDF)\s*OCR识别[：:][^\]]+\]\n[\s\S]*?(?=(\n\n|\Z))""")

    private val TOOL_BLOCK_REGEX = Regex(
        """(【(?:实时联网参考信息|网页正文内容|实时气象报告|手机设备与硬件状态|手机健康与运动数据|本地定位信息|系统时间与日历日程|联网搜索状态|网页提取状态|天气服务状态)】[\s\S]*?)(?=(【用户输入的问题/指令】|\Z))"""
    )

    private val CODE_BLOCK_PATTERN = Pattern.compile("```([\\w+-]*)\\n([\\s\\S]*?)\\n```")

    const val CODE_BLOCK_FOLD_LINE_THRESHOLD = 8

    /**
     * 对消息正文执行轻量修剪：
     * 1. 剔除附件 OCR 识别全文 -> [附件OCR已省略]
     * 2. 剔除工具/联网搜索大块结果 -> [联网与工具调用信息已省略]
     * 3. 折叠多于 8 行的长代码块 -> 占位说明
     */
    fun pruneMessageContent(content: String): String {
        if (content.isBlank()) return content

        var result = content

        // 1. 修剪 OCR 块
        if (result.contains("OCR识别")) {
            result = OCR_BLOCK_REGEX.replace(result) {
                "[附件OCR内容已省略]\n"
            }
        }

        // 2. 修剪各类联网与工具结果块
        if (result.contains("【") && (result.contains("实时联网") || result.contains("网页正文") || result.contains("设备状态") || result.contains("健康数据") || result.contains("实时气象"))) {
            result = TOOL_BLOCK_REGEX.replace(result) {
                "[联网与工具调用信息已省略]\n\n"
            }
        }

        // 3. 折叠超长代码块（行数 > 8）
        if (result.contains("```")) {
            val matcher = CODE_BLOCK_PATTERN.matcher(result)
            val sb = StringBuffer()
            while (matcher.find()) {
                val lang = matcher.group(1).orEmpty()
                val code = matcher.group(2).orEmpty()
                val lineCount = code.lines().size
                if (lineCount > CODE_BLOCK_FOLD_LINE_THRESHOLD) {
                    val replacement = "```$lang\n// [长代码块已精简折叠: 共 $lineCount 行]\n```"
                    matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement))
                } else {
                    matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(matcher.group(0).orEmpty()))
                }
            }
            matcher.appendTail(sb)
            result = sb.toString()
        }

        return result.trim()
    }
}
