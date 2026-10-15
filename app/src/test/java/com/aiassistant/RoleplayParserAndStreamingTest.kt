package com.aiassistant

import android.app.Application
import androidx.room.Room
import com.aiassistant.data.local.AppDatabase
import com.aiassistant.domain.model.WorldBook
import com.aiassistant.utils.RoleplaySmartParser
import com.aiassistant.utils.WorldBookImport
import com.aiassistant.utils.WorldBookImportFormat
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class RoleplayParserAndStreamingTest {

    @Test
    fun worldBookImportRecognizesTavernObjectAndPreservesKeywordPhrases() {
        val report = WorldBookImport.parse("""
            {"name":"星港指南","description":"合成测试资料","entries":{
              "70":{"uid":70,"comment":"北侧星港","key":["North Harbor","星港"],"content":"北侧星港设有维修站。","constant":false,"disable":false,"order":0}
            }}
        """.trimIndent())
        assertEquals(WorldBookImportFormat.SILLY_TAVERN, report.format)
        assertEquals("星港指南", report.name)
        assertEquals("合成测试资料", report.description)
        val entry = report.entries.single().draft
        assertEquals("北侧星港", entry.name)
        assertEquals(listOf("North Harbor", "星港"), JsonParser.parseString(entry.keys).asJsonArray.map { it.asString })
        assertEquals(0, entry.priority)
        assertTrue(entry.isEnabled)
        assertFalse(entry.isConstant)
        assertTrue(report.requiresCompatibilityConfirmation)
        assertThrows(IllegalArgumentException::class.java) { WorldBookImport.prepare(report) }
        val prepared = WorldBookImport.prepare(report, acceptCompatibilityLoss = true)
        assertFalse(prepared.book.isEnabled)
        assertEquals(0L, prepared.book.id)
        assertEquals(0L, prepared.entries.single().id)
    }

    @Test
    fun worldBookImportRecognizesStandaloneAndEmbeddedCharacterBooks() {
        val standalone = WorldBookImport.parse("""
            {"entries":[{"keys":["灯塔"],"content":"灯塔每晚亮起。","enabled":false,"constant":true}]}
        """.trimIndent(), fallbackName = "航路设定")
        assertEquals(WorldBookImportFormat.CHARACTER_BOOK, standalone.format)
        assertEquals("航路设定", standalone.name)
        assertFalse(standalone.requiresCompatibilityConfirmation)
        assertFalse(standalone.entries.single().draft.isEnabled)
        assertTrue(standalone.entries.single().draft.isConstant)

        val embedded = WorldBookImport.parse("""
            {"spec":"chara_card_v2","data":{"name":"测试角色","system_prompt":"不得导入的角色规则",
              "character_book":{"name":"灯塔群岛","entries":[{"keys":["灯塔"],"content":"群岛有三座灯塔。","enabled":true}]}}}
        """.trimIndent())
        assertEquals(WorldBookImportFormat.EMBEDDED_CHARACTER_BOOK, embedded.format)
        assertEquals("灯塔群岛", embedded.name)
        assertEquals("群岛有三座灯塔。", embedded.entries.single().draft.content)
        assertTrue(embedded.warnings.any { it.contains("仅提取角色卡") })
    }

    @Test
    fun worldBookImportDisablesUnsupportedSemanticsUntilManualReview() {
        val report = WorldBookImport.parse("""
            {"entries":[
              {"keys":["/harbor/i"],"content":"正则词条","enabled":true},
              {"keys":["星港"],"secondary_keys":["夜晚"],"selective":true,"content":"条件词条"},
              {"keys":["灯塔"],"content":"深度词条","extensions":{"position":4,"depth":2,"probability":50}},
              {"keys":["船长"],"content":"未知扩展词条","extensions":{"future_rule":{"mode":"exclusive"}}}
            ]}
        """.trimIndent())
        assertEquals(4, report.disabledEntryCount)
        assertTrue(report.unsupportedFields.contains("正则关键词"))
        assertTrue(report.unsupportedFields.contains("secondary_keys"))
        assertTrue(report.unsupportedFields.contains("extensions.position"))
        assertTrue(report.unsupportedFields.contains("extensions.future_rule"))
        assertThrows(IllegalArgumentException::class.java) { WorldBookImport.prepare(report) }
        val prepared = WorldBookImport.prepare(report, acceptCompatibilityLoss = true)
        assertTrue(prepared.entries.all { !it.isEnabled })
        assertEquals(4, prepared.entries.size)
        assertEquals("未知扩展词条", prepared.entries.last().content)
    }

    @Test
    fun worldBookImportFlagsBookSettingsMacrosAndMissingTriggers() {
        val bookSettings = WorldBookImport.parse("""
            {"scan_depth":8,"token_budget":200,"recursive_scanning":true,
              "entries":[{"keys":["船长"],"content":"船长来自群岛。","enabled":true}]}
        """.trimIndent())
        assertTrue(bookSettings.unsupportedFields.containsAll(listOf("scan_depth", "token_budget", "recursive_scanning")))
        assertFalse(bookSettings.entries.single().draft.isEnabled)

        val macros = WorldBookImport.parse("""
            {"entries":[{"keys":["{{char}}"],"content":"{{char}} 来自群岛。"},{"keys":[],"content":"无触发词"}]}
        """.trimIndent())
        assertEquals(2, macros.disabledEntryCount)
        assertTrue(macros.warnings.any { it.contains("宏或脚本语法") })
        assertTrue(macros.warnings.any { it.contains("缺少触发关键词") })
    }

    @Test
    fun worldBookImportRejectsMalformedMixedAndOversizedInputs() {
        val invalid = listOf(
            "", "[]", "{entries:[]}", "{\"entries\":[]}",
            "{\"entries\":[{\"keys\":[1],\"content\":\"测试\"}]}",
            "{\"entries\":[{\"keys\":[\"灯塔\"],\"content\":\"测试\",\"enabled\":\"false\"}]}",
            "{\"entries\":[{\"keys\":[\"灯塔\"],\"content\":\"测试\"},7]}",
            "{\"entries\":{},\"entries\":{}}",
            "{\"entries\":[{\"keys\":[\"灯塔\"],\"content\":\"测试\",\"order\":1.5}]}",
            "{\"entries\":[{\"keys\":[\"灯塔\"],\"content\":\"  \"}]}",
            "{\"entries\":[{\"keys\":[\"灯塔\"],\"content\":\"测试\"}]} trailing"
        )
        invalid.forEach { json -> assertThrows(IllegalArgumentException::class.java) { WorldBookImport.parse(json) } }
        assertThrows(IllegalArgumentException::class.java) { WorldBookImport.parse(" ".repeat(WorldBookImport.MAX_JSON_BYTES) + "x") }
        val manyEntries = "{\"entries\":[" + List(WorldBookImport.MAX_ENTRIES + 1) {
            "{\"keys\":[\"星港\"],\"content\":\"合成条目\"}"
        }.joinToString(",") + "]}"
        assertThrows(IllegalArgumentException::class.java) { WorldBookImport.parse(manyEntries) }
        val deep = "{\"unknown\":" + "[".repeat(40) + "0" + "]".repeat(40) + "}"
        assertThrows(IllegalArgumentException::class.java) { WorldBookImport.parse(deep) }
    }

    @Test
    fun worldBookImportCreatesDisabledCopyWithoutOverwritingAndRollsBackOnFailure() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val dao = database.worldBookDao()
            val existingId = dao.insertBook(WorldBook(name = "星港指南", description = "原始资料", isEnabled = true))
            val report = WorldBookImport.parse("""
                {"name":"星港指南","entries":[{"id":1,"keys":["North Harbor"],"content":"新资料"}]}
            """.trimIndent())
            val newId = dao.insertImportedBook(WorldBookImport.prepare(report))
            assertTrue(newId != existingId)
            assertEquals("原始资料", dao.getBookById(existingId)?.description)
            assertTrue(dao.getBookById(existingId)?.isEnabled == true)
            assertFalse(dao.getBookById(newId)?.isEnabled ?: true)
            assertEquals("新资料", dao.getEntriesForBookList(newId).single().content)
            assertTrue(dao.getActiveEntriesFromEnabledBooks().isEmpty())
            val countBefore = dao.getAllBooksList().size
            database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER reject_import_test BEFORE INSERT ON world_book_entries WHEN NEW.name = '拒绝词条' BEGIN SELECT RAISE(ABORT, 'synthetic import failure'); END"
            )
            val failing = WorldBookImport.prepare(WorldBookImport.parse("""
                {"name":"事务测试","entries":[{"name":"通过词条","keys":["通过"],"content":"第一条"},
                {"name":"拒绝词条","keys":["拒绝"],"content":"第二条"}]}
            """.trimIndent()))
            var failed = false
            try { dao.insertImportedBook(failing) } catch (_: android.database.sqlite.SQLiteException) { failed = true }
            assertTrue("第二条插入应被测试触发器拒绝", failed)
            assertEquals("书和先写入的词条必须一起回滚", countBefore, dao.getAllBooksList().size)
            assertEquals(1, dao.getAllEnabledEntries().size)
        } finally {
            database.close()
        }
    }

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
