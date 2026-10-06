package com.aiassistant

import com.aiassistant.utils.RoleplaySmartParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleplayParserAndStreamingTest {

    @Test
    fun testSmartParserCharacterExtraction() {
        val rawText = """
            【角色名称】林凡
            【身份】落魄剑圣
            【性格】沉默寡言、重情重义
            【背景故事】曾是天下第一剑客，在十年前的一场大战中失去右手，从此归隐山林。
            【说话方式】话少、语气沉稳克制
            【目标】查明当年师门被灭的真相
            【禁忌】绝不向欺凌弱小者拔剑
            【初始台词】这把断剑，早已不问江湖事。
            【标签】武侠, 剑客, 隐士
        """.trimIndent()

        val parsed = RoleplaySmartParser.parseCharacter(rawText)
        assertEquals("林凡", parsed.name)
        assertEquals("落魄剑圣", parsed.identity)
        assertEquals("沉默寡言、重情重义", parsed.personality)
        assertTrue(parsed.background.contains("曾是天下第一剑客"))
        assertEquals("话少、语气沉稳克制", parsed.speakingStyle)
        assertEquals("查明当年师门被灭的真相", parsed.goals)
        assertEquals("绝不向欺凌弱小者拔剑", parsed.constraints)
        assertEquals("这把断剑，早已不问江湖事。", parsed.greeting)
        assertTrue(parsed.tags?.contains("武侠") == true)
    }

    @Test
    fun testSmartParserScenarioExtraction() {
        val rawText = """
            【场景名称】断魂崖夜雨
            【世界观】高武玄幻，宗门林立
            【时间】深夜暴雨
            【地点】断魂崖顶孤亭
            【环境描写】狂风夹杂着冰冷的雨水抽打在残破的石亭上，远处雷声轰鸣。
            【剧情前提】正邪两派高手在此约定决战
            【不可违背规则】决斗前不可伤及引路童子
            【当前冲突】魔教护法突然现身，撕毁停战协议
            【氛围】肃杀、压抑、雷雨交加
            【开场提示】一道闪电划破夜空，照亮了亭中静坐的身影。
        """.trimIndent()

        val parsed = RoleplaySmartParser.parseScenario(rawText)
        assertEquals("断魂崖夜雨", parsed.name)
        assertEquals("高武玄幻，宗门林立", parsed.worldview)
        assertEquals("深夜暴雨", parsed.time)
        assertEquals("断魂崖顶孤亭", parsed.location)
        assertTrue(parsed.environment.contains("狂风夹杂着冰冷的雨水"))
        assertEquals("正邪两派高手在此约定决战", parsed.premise)
        assertEquals("决斗前不可伤及引路童子", parsed.rules)
        assertEquals("魔教护法突然现身，撕毁停战协议", parsed.conflict)
        assertEquals("肃杀、压抑、雷雨交加", parsed.atmosphere)
        assertTrue(parsed.openingPrompt.contains("一道闪电划破夜空"))
    }

    @Test
    fun testJsonExtractorHandling() {
        val textWithMarkdown = """
            Here is the requested JSON:
            ```json
            {
              "plotSummary": "主角在森林中遇到了神秘老者，获得了上古剑谱。",
              "extractedFacts": [
                "老者是隐世百年的天剑宗祖师",
                "上古剑谱只有拥有纯阳体质的人才能修炼"
              ]
            }
            ```
            Hope this helps!
        """.trimIndent()

        val start = textWithMarkdown.indexOf('{')
        val end = textWithMarkdown.lastIndexOf('}')
        assertTrue(start >= 0 && end > start)

        val jsonStr = textWithMarkdown.substring(start, end + 1)
        val parsed = com.google.gson.JsonParser.parseString(jsonStr).asJsonObject
        assertEquals("主角在森林中遇到了神秘老者，获得了上古剑谱。", parsed.get("plotSummary").asString)
        val facts = parsed.get("extractedFacts").asJsonArray
        assertEquals(2, facts.size())
        assertEquals("老者是隐世百年的天剑宗祖师", facts.get(0).asString)
        assertEquals("上古剑谱只有拥有纯阳体质的人才能修炼", facts.get(1).asString)
    }

    @Test
    fun testThinkingTokenExtractionFallback() {
        val rawOpenAiChunk = """
            {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1694268190,"model":"deepseek-reasoner","choices":[{"index":0,"delta":{"reasoning_content":"正在思考剧情发展...","content":null},"finish_reason":null}]}
        """.trimIndent()

        val json = com.google.gson.JsonParser.parseString(rawOpenAiChunk).asJsonObject
        val delta = json.getAsJsonArray("choices").get(0).asJsonObject.getAsJsonObject("delta")
        val reasoning = delta.get("reasoning_content")?.asString
        assertNotNull(reasoning)
        assertEquals("正在思考剧情发展...", reasoning)
    }

    @Test
    fun testStorySettingProposalBundle() {
        val existingChar = com.aiassistant.domain.model.CharacterProfile(
            id = 1,
            name = "林凡",
            identity = "落魄剑圣",
            personality = "沉默寡言",
            background = "曾是天下第一剑客"
        )
        val update = com.aiassistant.utils.ProposedCharacterUpdate(
            character = existingChar.copy(personality = "冷静从容", background = "曾是天下第一剑客；在荒原上习得断浪剑诀"),
            isNew = false,
            summaryOfChanges = "性格调整，追加断浪剑诀经历"
        )
        val newChar = com.aiassistant.utils.ProposedCharacterUpdate(
            character = com.aiassistant.domain.model.CharacterProfile(
                id = 0,
                name = "苏璃",
                identity = "神机阁阁主",
                personality = "机智狡黠"
            ),
            isNew = true,
            summaryOfChanges = "新登场神秘阁主"
        )
        val scenarioUpdate = com.aiassistant.utils.ProposedScenarioUpdate(
            scenario = com.aiassistant.domain.model.RoleplayScenario(
                id = 10,
                name = "断魂崖",
                worldview = "高武玄幻",
                rules = "崖顶禁空飞行"
            ),
            summaryOfChanges = "追加禁空法则"
        )

        val bundle = com.aiassistant.utils.StorySettingProposalBundle(
            updatedCharacters = listOf(update),
            newCharacters = listOf(newChar),
            scenarioUpdate = scenarioUpdate,
            isAiAnalyzed = true,
            summaryReport = "识别到林凡设定更新、新角色苏璃与世界观法则扩充"
        )

        assertTrue(bundle.hasAnyUpdates)
        assertEquals(3, bundle.totalCount)
        assertEquals(1, bundle.updatedCharacters.size)
        assertEquals(1, bundle.newCharacters.size)
        assertNotNull(bundle.scenarioUpdate)
        assertEquals("林凡", bundle.updatedCharacters[0].character.name)
        assertEquals("苏璃", bundle.newCharacters[0].character.name)
        assertTrue(bundle.updatedCharacters[0].character.background.contains("断浪剑诀"))
    }
}
