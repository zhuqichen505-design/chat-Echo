package com.aiassistant

import com.aiassistant.domain.model.CharacterProfile
import com.aiassistant.domain.model.RoleplayScenario
import com.aiassistant.domain.model.RoleplaySession
import com.aiassistant.domain.model.RoleplayMemory
import com.aiassistant.domain.model.CharacterTag
import com.aiassistant.domain.model.CharacterTagCrossRef
import com.aiassistant.domain.model.NarrativeMode
import com.aiassistant.domain.model.PlotAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleplayModelsTest {

    @Test
    fun testCharacterProfileDefaultValues() {
        val character = CharacterProfile(
            id = 0L,
            name = "测试角色"
        )

        assertEquals("测试角色", character.name)
        assertEquals("", character.identity)
        assertEquals("", character.personality)
        assertEquals("", character.background)
        assertEquals("", character.speakingStyle)
        assertEquals("", character.goals)
        assertEquals("", character.relationships)
        assertEquals("", character.knowledge)
        assertEquals("", character.constraints)
        assertEquals("", character.behaviorRules)
        assertEquals("", character.greeting)
        assertEquals("", character.exampleDialogue)
        assertNull(character.tags)
        assertFalse(character.isFavorite)
        assertFalse(character.isDefault)
    }

    @Test
    fun testCharacterProfileWithAllFields() {
        val character = CharacterProfile(
            id = 1L,
            name = "艾莉丝",
            avatarUri = "content://avatar/1",
            identity = "魔法师",
            personality = "温柔、坚强",
            background = "来自古老的魔法家族",
            speakingStyle = "文雅、带有古语",
            goals = "寻找失落的魔法",
            relationships = "与主角是师徒关系",
            knowledge = "精通元素魔法",
            constraints = "不能使用黑暗魔法",
            behaviorRules = "保护弱者",
            greeting = "你好，年轻的冒险者。",
            exampleDialogue = "用户：你是谁？\n艾莉丝：我是艾莉丝，一名元素魔法师。",
            tags = "奇幻,主角,女性",
            isFavorite = true,
            isDefault = true,
            createdAt = 1000L,
            updatedAt = 2000L
        )

        assertEquals(1L, character.id)
        assertEquals("艾莉丝", character.name)
        assertEquals("魔法师", character.identity)
        assertEquals("温柔、坚强", character.personality)
        assertEquals("来自古老的魔法家族", character.background)
        assertEquals("文雅、带有古语", character.speakingStyle)
        assertEquals("寻找失落的魔法", character.goals)
        assertEquals("与主角是师徒关系", character.relationships)
        assertEquals("精通元素魔法", character.knowledge)
        assertEquals("不能使用黑暗魔法", character.constraints)
        assertEquals("保护弱者", character.behaviorRules)
        assertEquals("你好，年轻的冒险者。", character.greeting)
        assertEquals("奇幻,主角,女性", character.tags)
        assertTrue(character.isFavorite)
        assertTrue(character.isDefault)
    }

    @Test
    fun testRoleplayScenarioDefaultValues() {
        val scenario = RoleplayScenario(
            id = 0L,
            name = "测试场景"
        )

        assertEquals("测试场景", scenario.name)
        assertEquals("", scenario.worldview)
        assertEquals("", scenario.time)
        assertEquals("", scenario.location)
        assertEquals("", scenario.environment)
        assertEquals("", scenario.premise)
        assertEquals("", scenario.rules)
        assertEquals("", scenario.relationshipState)
        assertEquals("", scenario.conflict)
        assertEquals("", scenario.plotGoal)
        assertEquals("", scenario.atmosphere)
        assertEquals("", scenario.narrativePerspective)
        assertEquals("", scenario.outputFormat)
        assertEquals("", scenario.contentRestrictions)
        assertEquals("", scenario.openingPrompt)
        assertNull(scenario.tags)
        assertFalse(scenario.isFavorite)
    }

    @Test
    fun testRoleplayScenarioWithAllFields() {
        val scenario = RoleplayScenario(
            id = 1L,
            name = "魔法学院",
            worldview = "一个充满魔法的世界",
            time = "中世纪",
            location = "艾尔文魔法学院",
            environment = "古老的城堡，周围环绕着魔法森林",
            premise = "新生入学的第一天",
            rules = "魔法分为元素、治愈、召唤三大系",
            relationshipState = "同学关系",
            conflict = "学院中出现了神秘的魔法事件",
            plotGoal = "查明真相，保护学院",
            atmosphere = "神秘、紧张",
            narrativePerspective = "第三人称有限",
            outputFormat = "包含对话和动作描写",
            contentRestrictions = "无暴力血腥内容",
            openingPrompt = "你站在魔法学院的大门前...",
            tags = "奇幻,校园,冒险",
            isFavorite = true,
            createdAt = 1000L,
            updatedAt = 2000L
        )

        assertEquals(1L, scenario.id)
        assertEquals("魔法学院", scenario.name)
        assertEquals("一个充满魔法的世界", scenario.worldview)
        assertEquals("中世纪", scenario.time)
        assertEquals("艾尔文魔法学院", scenario.location)
        assertEquals("奇幻,校园,冒险", scenario.tags)
        assertTrue(scenario.isFavorite)
    }

    @Test
    fun testRoleplaySessionDefaultValues() {
        val session = RoleplaySession(
            id = 0L,
            conversationId = 1L
        )

        assertEquals(0L, session.id)
        assertNull(session.characterId)
        assertNull(session.scenarioId)
        assertEquals(1L, session.conversationId)
        assertEquals("character", session.narrativeMode)
        assertEquals("", session.currentPlotSummary)
        assertNull(session.pinnedFacts)
        assertEquals(1, session.lastVersionIndex)
    }

    @Test
    fun testRoleplaySessionWithAllFields() {
        val session = RoleplaySession(
            id = 1L,
            characterId = 10L,
            scenarioId = 20L,
            conversationId = 30L,
            narrativeMode = "author",
            currentPlotSummary = "故事发展到一半",
            pinnedFacts = "[\"角色A是好人\",\"角色B是坏人\"]",
            lastVersionIndex = 3,
            createdAt = 1000L,
            updatedAt = 2000L
        )

        assertEquals(1L, session.id)
        assertEquals(10L, session.characterId)
        assertEquals(20L, session.scenarioId)
        assertEquals(30L, session.conversationId)
        assertEquals("author", session.narrativeMode)
        assertEquals("故事发展到一半", session.currentPlotSummary)
        assertEquals("[\"角色A是好人\",\"角色B是坏人\"]", session.pinnedFacts)
        assertEquals(3, session.lastVersionIndex)
    }

    @Test
    fun testRoleplayMemoryDefaultValues() {
        val memory = RoleplayMemory(
            id = 0L,
            sessionId = 1L,
            memoryType = "fact",
            content = "测试记忆"
        )

        assertEquals(0L, memory.id)
        assertEquals(1L, memory.sessionId)
        assertEquals("fact", memory.memoryType)
        assertEquals("测试记忆", memory.content)
        assertNull(memory.sourceMessageId)
        assertFalse(memory.isPinned)
    }

    @Test
    fun testCharacterTagDefaultValues() {
        val tag = CharacterTag(id = 0L, name = "奇幻", createdAt = 100L)

        assertEquals(0L, tag.id)
        assertEquals("奇幻", tag.name)
    }

    @Test
    fun testCharacterTagCrossRefCreation() {
        val crossRef = CharacterTagCrossRef(characterId = 1L, tagId = 2L)

        assertEquals(1L, crossRef.characterId)
        assertEquals(2L, crossRef.tagId)
    }

    @Test
    fun testNarrativeModeFromValue() {
        assertEquals(NarrativeMode.CHARACTER, NarrativeMode.fromValue("character"))
        assertEquals(NarrativeMode.AUTHOR, NarrativeMode.fromValue("author"))
        assertEquals(NarrativeMode.NARRATOR, NarrativeMode.fromValue("narrator"))
        assertEquals(NarrativeMode.CHARACTER, NarrativeMode.fromValue("unknown"))
    }

    @Test
    fun testNarrativeModeDisplayNames() {
        assertEquals("角色内指令", NarrativeMode.CHARACTER.displayName)
        assertEquals("作者/导演指令", NarrativeMode.AUTHOR.displayName)
        assertEquals("旁白模式", NarrativeMode.NARRATOR.displayName)
    }

    @Test
    fun testPlotActionFromValue() {
        assertEquals(PlotAction.CONTINUE, PlotAction.fromValue("continue"))
        assertEquals(PlotAction.REGENERATE, PlotAction.fromValue("regenerate"))
        assertEquals(PlotAction.REWRITE, PlotAction.fromValue("rewrite"))
        assertEquals(PlotAction.EXTEND, PlotAction.fromValue("extend"))
        assertEquals(PlotAction.SHORTEN, PlotAction.fromValue("shorten"))
        assertEquals(PlotAction.CHANGE_PERSPECTIVE, PlotAction.fromValue("change_perspective"))
        assertEquals(PlotAction.CHANGE_TONE, PlotAction.fromValue("change_tone"))
        assertEquals(PlotAction.BRANCH, PlotAction.fromValue("branch"))
        assertEquals(PlotAction.ROLLBACK, PlotAction.fromValue("rollback"))
        assertEquals(PlotAction.SUMMARY, PlotAction.fromValue("summary"))
        assertEquals(PlotAction.BRANCH_CHOICES, PlotAction.fromValue("branch_choices"))
        assertEquals(PlotAction.DIALOGUE_ONLY, PlotAction.fromValue("dialogue_only"))
        assertEquals(PlotAction.NARRATION_ONLY, PlotAction.fromValue("narration_only"))
        assertEquals(PlotAction.DIALOGUE_ACTION, PlotAction.fromValue("dialogue_action"))
        assertEquals(PlotAction.CUSTOM, PlotAction.fromValue("custom"))
        assertEquals(PlotAction.CONTINUE, PlotAction.fromValue("unknown"))
    }

    @Test
    fun testPlotActionDisplayNames() {
        assertEquals("继续剧情", PlotAction.CONTINUE.displayName)
        assertEquals("重生成", PlotAction.REGENERATE.displayName)
        assertEquals("改写上一段", PlotAction.REWRITE.displayName)
        assertEquals("延长内容", PlotAction.EXTEND.displayName)
        assertEquals("缩短内容", PlotAction.SHORTEN.displayName)
        assertEquals("改变叙事视角", PlotAction.CHANGE_PERSPECTIVE.displayName)
        assertEquals("改变语气", PlotAction.CHANGE_TONE.displayName)
        assertEquals("创建剧情分支", PlotAction.BRANCH.displayName)
        assertEquals("回到上一个版本", PlotAction.ROLLBACK.displayName)
        assertEquals("生成剧情摘要", PlotAction.SUMMARY.displayName)
        assertEquals("剧情走向选择", PlotAction.BRANCH_CHOICES.displayName)
        assertEquals("只生成角色对白", PlotAction.DIALOGUE_ONLY.displayName)
        assertEquals("只生成旁白", PlotAction.NARRATION_ONLY.displayName)
        assertEquals("生成对白加动作", PlotAction.DIALOGUE_ACTION.displayName)
        assertEquals("自定义指令", PlotAction.CUSTOM.displayName)
    }

    @Test
    fun testRoleplaySessionEffectiveCharacterIds() {
        // Case 1: single characterId fallback
        val session1 = RoleplaySession(
            id = 1L,
            conversationId = 100L,
            characterId = 5L,
            characterIds = null
        )
        assertEquals(listOf(5L), session1.getEffectiveCharacterIds())

        // Case 2: characterIds JSON array
        val session2 = RoleplaySession(
            id = 2L,
            conversationId = 101L,
            characterId = null,
            characterIds = "[10, 20, 30]"
        )
        assertEquals(listOf(10L, 20L, 30L), session2.getEffectiveCharacterIds())

        // Case 3: characterIds takes precedence
        val session3 = RoleplaySession(
            id = 3L,
            conversationId = 102L,
            characterId = 5L,
            characterIds = "[100, 200]"
        )
        assertEquals(listOf(100L, 200L), session3.getEffectiveCharacterIds())

        // Case 4: empty
        val session4 = RoleplaySession(
            id = 4L,
            conversationId = 103L
        )
        assertTrue(session4.getEffectiveCharacterIds().isEmpty())
    }

    @Test
    fun testRoleplaySessionCustomOverrides() {
        val baseChar = CharacterProfile(id = 1L, name = "艾莉丝", personality = "温柔")
        val customChar = baseChar.copy(personality = "黑化且冷酷")
        val customJson = com.google.gson.Gson().toJson(listOf(customChar))

        val session = RoleplaySession(
            id = 10L,
            conversationId = 100L,
            characterIds = "[1]",
            customCharacterData = customJson
        )

        val mergedChars = session.getCustomizedCharacters(listOf(baseChar))
        assertEquals(1, mergedChars.size)
        assertEquals("黑化且冷酷", mergedChars.first().personality)

        val baseScenario = RoleplayScenario(id = 2L, name = "魔法学院", rules = "禁止黑魔法")
        val customScenario = baseScenario.copy(rules = "允许任何禁忌魔法")
        val customScenarioJson = com.google.gson.Gson().toJson(customScenario)

        val sessionWithScenario = session.copy(customScenarioData = customScenarioJson)
        val mergedScenario = sessionWithScenario.getCustomizedScenario(baseScenario)
        assertEquals("允许任何禁忌魔法", mergedScenario?.rules)
    }

    @Test
    fun testMemoryItemModel() {
        val item = com.aiassistant.domain.model.MemoryItem(
            id = 1L,
            scope = "user",
            content = "用户喜欢简洁的回答",
            keywords = "简洁,回答",
            confidence = 0.9f,
            isEnabled = true
        )
        assertEquals(1L, item.id)
        assertEquals("user", item.scope)
        assertEquals("用户喜欢简洁的回答", item.content)
        assertEquals("简洁,回答", item.keywords)
        assertEquals(0.9f, item.confidence, 0.001f)
        assertTrue(item.isEnabled)
    }

    @Test
    fun testPersonalizationSettingsAndMemoryIsolation() {
        val settings = com.aiassistant.utils.PersonalizationSettings(
            globalSystemPrompt = "你是一个全能助手",
            enabled = true,
            aboutUser = "资深工程师",
            responseStyle = "简明扼要",
            preferences = "优先使用 Kotlin",
            avoid = "避免冗长废话",
            autoMemoryEnabled = true
        )
        assertEquals("你是一个全能助手", settings.globalSystemPrompt)
        assertTrue(settings.enabled)
        assertTrue(settings.autoMemoryEnabled)
        assertEquals("资深工程师", settings.aboutUser)
        assertEquals("优先使用 Kotlin", settings.preferences)
    }

    @Test
    fun localStoryCharactersHaveStableDistinctIdsWithoutDatabaseForeignKeys() {
        val gson = com.google.gson.Gson()
        val legacy = RoleplaySession(conversationId = 1, customCharacterData = gson.toJson(listOf(
            CharacterProfile(name = "Synthetic A"), CharacterProfile(name = "Synthetic B"))))
        val characters = legacy.getCustomizedCharacters(emptyList())
        assertEquals(2, characters.map { it.id }.distinct().size)
        assertTrue(characters.all { it.id < 0 })
        assertEquals(characters, legacy.getCustomizedCharacters(emptyList()))
        val changed = com.aiassistant.domain.model.StorySessionBindings.upsertCharacter(legacy, characters,
            characters.first().copy(personality = "Revised"))
        assertEquals(null, changed.characterId)
        assertEquals(2, changed.getCustomizedCharacters(emptyList()).size)
        assertEquals("Revised", changed.getCustomizedCharacters(emptyList()).first().personality)
    }

    @Test
    fun deselectingCharacterRemovesItsSnapshotFromTheGeneratedStoryContext() {
        val a = CharacterProfile(id = 1, name = "Synthetic A")
        val b = CharacterProfile(id = -1, name = "Synthetic B")
        val original = RoleplaySession(conversationId = 1, characterId = 1, characterIds = "[1,-1]",
            customCharacterData = com.google.gson.Gson().toJson(listOf(a, b)))
        val selected = com.aiassistant.domain.model.StorySessionBindings.select(original, listOf(a, b), listOf(-1),
            null, NarrativeMode.AUTHOR, "Summary")
        assertEquals(listOf(-1L), selected.getEffectiveCharacterIds())
        assertEquals(null, selected.characterId)
        assertEquals(listOf("Synthetic B"), selected.getParticipatingCharacters(emptyList()).map { it.name })
        val cleared = com.aiassistant.domain.model.StorySessionBindings.select(selected, listOf(b), emptyList(),
            null, NarrativeMode.AUTHOR, "Summary")
        assertTrue(cleared.getParticipatingCharacters(emptyList()).isEmpty())
        assertEquals(1, cleared.getCustomizedCharacters(emptyList()).size)
        assertTrue(cleared.getEffectiveCharacterIds().isEmpty())
    }

    @Test
    fun changingScenarioDoesNotKeepPreviousWorldOverride() {
        val world = RoleplayScenario(id = 3, name = "Synthetic world")
        val original = RoleplaySession(conversationId = 1, scenarioId = 3,
            customScenarioData = com.google.gson.Gson().toJson(world))
        val changed = com.aiassistant.domain.model.StorySessionBindings.select(original, emptyList(), emptyList(),
            4, NarrativeMode.AUTHOR, "")
        assertEquals(4L, changed.scenarioId)
        assertEquals(null, changed.customScenarioData)
        val kept = com.aiassistant.domain.model.StorySessionBindings.select(original, emptyList(), emptyList(),
            3, NarrativeMode.AUTHOR, "")
        assertEquals(original.customScenarioData, kept.customScenarioData)
    }

    @Test
    fun localWorldCanBeSelectedAndClearedWithoutRoomForeignKey() {
        val original = RoleplaySession(conversationId = 1, customScenarioData =
            com.google.gson.Gson().toJson(RoleplayScenario(name = "Synthetic local world")))
        val localId = original.getCustomizedScenario(null)!!.id
        assertTrue(localId < 0)
        val kept = com.aiassistant.domain.model.StorySessionBindings.select(original, emptyList(), emptyList(),
            localId, NarrativeMode.AUTHOR, "")
        assertEquals(null, kept.scenarioId)
        assertEquals("Synthetic local world", kept.getCustomizedScenario(null)?.name)
        val cleared = com.aiassistant.domain.model.StorySessionBindings.select(kept, emptyList(), emptyList(),
            null, NarrativeMode.AUTHOR, "")
        assertEquals(null, cleared.getCustomizedScenario(null))
    }

    @Test
    fun workspaceRefreshDoesNotOverwriteWritingDraftAndMergesNewCharacters() {
        val session = RoleplaySession(conversationId = 1, characterIds = "[1]")
        val initial = com.aiassistant.ui.screens.chat.story.StoryWorkspaceDraft.from(session, "Original",
            com.aiassistant.ui.screens.chat.TempChatSettings())
        val local = initial.copy(instructions = "Unsaved writing goal", characterIds = emptySet(),
            settings = initial.settings.copy(enableWorldBook = true))
        val incoming = initial.copy(characterIds = setOf(1, -1), summary = "New extraction",
            settings = initial.settings.copy(maxTokens = 8192))
        val merged = com.aiassistant.ui.screens.chat.story.mergeStoryWorkspaceDraft(local, initial, incoming)
        assertEquals("Unsaved writing goal", merged.instructions)
        assertEquals(setOf(-1L), merged.characterIds)
        assertEquals("New extraction", merged.summary)
        assertTrue(merged.settings.enableWorldBook)
        assertEquals(8192, merged.settings.maxTokens)
    }

    @Test
    fun inactiveCharacterCanBeReenabledWithoutLosingStoryEdits() {
        val profile = CharacterProfile(id = -1L, name = "Synthetic local", personality = "Story-specific")
        val session = RoleplaySession(conversationId = 1, characterIds = "[]", customCharacterData = com.google.gson.Gson().toJson(listOf(profile)))
        assertTrue(session.getParticipatingCharacters(emptyList()).isEmpty())
        val selected = com.aiassistant.domain.model.StorySessionBindings.select(session,
            session.getCustomizedCharacters(emptyList()), listOf(-1L), null, NarrativeMode.AUTHOR, "")
        assertEquals(listOf(profile), selected.getParticipatingCharacters(emptyList()))
        val newCharacter = com.aiassistant.domain.model.StorySessionBindings.upsertCharacter(session,
            session.getCustomizedCharacters(emptyList()), CharacterProfile(name = "Synthetic new"))
        assertEquals(listOf("Synthetic new"), newCharacter.getParticipatingCharacters(emptyList()).map { it.name })
        assertEquals(2, newCharacter.getCustomizedCharacters(emptyList()).size)
    }

    @Test
    fun storyRevisionUsesStableLocalIdAndReplacesOnlyChangedFields() {
        val original = CharacterProfile(id = -7, name = "合成人物甲", goals = "寻找失踪的队友",
            background = "出身港口，擅长维修。", constraints = "不伤害平民", avatarUri = "content://synthetic/avatar",
            isFavorite = true, createdAt = 11, updatedAt = 12)
        val result = com.aiassistant.utils.RoleplaySmartAnalyzer.parseStorySettingProposalJson("""
            {"hasSettingUpdates":true,"updatedCharacters":[{"characterId":-7,"name":"合成人物甲",
              "profile":{"name":"合成人物新名","goals":"队友已经获救，下一步返回港口","id":99,"isFavorite":false}}],
              "newCharacters":[],"scenarioUpdate":null}
        """.trimIndent(), listOf(original), null, true)
        val revised = result.updatedCharacters.single().character
        assertEquals(-7L, revised.id)
        assertEquals("合成人物新名", revised.name)
        assertEquals("队友已经获救，下一步返回港口", revised.goals)
        assertEquals(original.background, revised.background)
        assertEquals(original.constraints, revised.constraints)
        assertEquals(original.avatarUri, revised.avatarUri)
        assertEquals(original.createdAt, revised.createdAt)
        assertTrue(revised.isFavorite)
        assertTrue(result.newCharacters.isEmpty())
    }

    @Test
    fun storyRevisionSupportsExplicitlyClearingAnObsoleteState() {
        val original = CharacterProfile(id = 4, name = "合成人物", goals = "临时调查任务", knowledge = "会修船")
        val result = com.aiassistant.utils.RoleplaySmartAnalyzer.parseStorySettingProposalJson("""
            {"updatedCharacters":[{"characterId":4,"profile":{"goals":""}}]}
        """.trimIndent(), listOf(original), null, true)
        assertEquals("", result.updatedCharacters.single().character.goals)
        assertEquals("会修船", result.updatedCharacters.single().character.knowledge)
    }

    @Test
    fun storyRevisionClassifiesAnExistingNameAsAnUpdateEvenIfModelCallsItNew() {
        val original = CharacterProfile(id = 8, name = "Synthetic Character", knowledge = "航海")
        val result = com.aiassistant.utils.RoleplaySmartAnalyzer.parseStorySettingProposalJson("""
            {"newCharacters":[{"profile":{"name":"synthetic character","knowledge":"航海；新增修船技能"}}]}
        """.trimIndent(), listOf(original), null, true)
        assertEquals(8L, result.updatedCharacters.single().character.id)
        assertTrue(result.newCharacters.isEmpty())
    }

    @Test
    fun storyRevisionCombinesSeveralPatchesForTheSameCharacterWithoutDuplicatingCards() {
        val original = CharacterProfile(id = 8, name = "合成人物", personality = "冷静")
        val result = com.aiassistant.utils.RoleplaySmartAnalyzer.parseStorySettingProposalJson("""
            {"updatedCharacters":[{"characterId":8,"profile":{"goals":"返回港口"}},
              {"characterId":8,"profile":{"knowledge":"修船"}}]}
        """.trimIndent(), listOf(original), null, true)
        assertEquals(1, result.updatedCharacters.size)
        val revised = result.updatedCharacters.single().character
        assertEquals("返回港口", revised.goals)
        assertEquals("修船", revised.knowledge)
        assertEquals("冷静", revised.personality)
    }

    @Test
    fun unchangedStoryFieldsDoNotCreateAReviewProposal() {
        val original = CharacterProfile(id = 2, name = "合成人物", personality = "冷静")
        val scenario = RoleplayScenario(id = 3, name = "合成港口", worldview = "群岛世界", narrativePerspective = "第三人称")
        val result = com.aiassistant.utils.RoleplaySmartAnalyzer.parseStorySettingProposalJson("""
            {"hasSettingUpdates":true,"updatedCharacters":[{"characterId":2,"profile":{"personality":"冷静"}}],
             "scenarioUpdate":{"scenario":{"worldview":"群岛世界"}}}
        """.trimIndent(), listOf(original), scenario, true)
        assertFalse(result.hasAnyUpdates)
        assertEquals(0, result.totalCount)
    }

    @Test
    fun explicitNoStoryUpdateIsAuthoritativeAndUnknownIdsCannotReplaceKnownCharacters() {
        val original = CharacterProfile(id = 2, name = "合成人物", personality = "冷静")
        val noUpdate = com.aiassistant.utils.RoleplaySmartAnalyzer.parseStorySettingProposalJson("""
            {"hasSettingUpdates":false,"newCharacters":[{"profile":{"name":"不应出现的人物"}}]}
        """.trimIndent(), listOf(original), null, true)
        assertFalse(noUpdate.hasAnyUpdates)
        val invalidId = com.aiassistant.utils.RoleplaySmartAnalyzer.parseStorySettingProposalJson("""
            {"updatedCharacters":[{"characterId":999,"profile":{"name":"合成人物","personality":"冲动"}}]}
        """.trimIndent(), listOf(original), null, true)
        assertFalse(invalidId.hasAnyUpdates)
    }

    @Test
    fun worldRevisionPreservesUnmentionedRulesAndPerspectiveWithoutConcatenatingOldState() {
        val world = RoleplayScenario(id = -3, name = "合成世界", worldview = "群岛与深海",
            time = "冬季", location = "旧港", rules = "禁用明火", narrativePerspective = "第三人称",
            createdAt = 22, updatedAt = 23)
        val result = com.aiassistant.utils.RoleplaySmartAnalyzer.parseStorySettingProposalJson("""
            {"scenarioUpdate":{"scenario":{"time":"翌年春季","location":"新港"}}}
        """.trimIndent(), emptyList(), world, true)
        val revised = result.scenarioUpdate!!.scenario
        assertEquals(-3L, revised.id)
        assertEquals("翌年春季", revised.time)
        assertEquals("新港", revised.location)
        assertEquals("禁用明火", revised.rules)
        assertEquals("第三人称", revised.narrativePerspective)
        assertEquals(world.worldview, revised.worldview)
        assertEquals(world.createdAt, revised.createdAt)
    }

    @Test
    fun repeatedCharacterAndWorldImportsAreIdempotentAndKeepComplementaryNegation() {
        val original = CharacterProfile(id = 4, name = "合成人物", identity = "修船师", personality = "冷静；谨慎",
            background = "生于港口。曾经出海。", goals = "寻找航图", relationships = "信任船长",
            knowledge = "修船；航海", constraints = "喜欢甜食", behaviorRules = "天亮起床",
            greeting = "你好", exampleDialogue = "船已修好。", tags = "航海,群岛", createdAt = 11, updatedAt = 12)
        val same = com.aiassistant.utils.RoleplaySmartAnalyzer.mergeCharacters(original, original.copy(id = 0))
        assertEquals(original, same)
        val supplemented = com.aiassistant.utils.RoleplaySmartAnalyzer.mergeCharacters(original,
            CharacterProfile(name = "合成人物", knowledge = "航海；辨认星图", constraints = "不喜欢甜食"))
        assertEquals("修船；航海\n辨认星图", supplemented.knowledge)
        assertEquals("喜欢甜食\n不喜欢甜食", supplemented.constraints)
        val repeated = com.aiassistant.utils.RoleplaySmartAnalyzer.mergeCharacters(supplemented,
            CharacterProfile(name = "合成人物", knowledge = "航海；辨认星图", constraints = "不喜欢甜食"))
        assertEquals(supplemented, repeated)
        val world = RoleplayScenario(id = 3, name = "合成世界", worldview = "海上有群岛。岛间乘船通行。",
            rules = "禁用明火；夜间停航", narrativePerspective = "第三人称", updatedAt = 12)
        assertEquals(world, com.aiassistant.utils.RoleplaySmartAnalyzer.mergeScenarios(world, world.copy(id = 0)))
    }

    @Test
    fun creativeAnalysisKeepsInstructionsAndEveryPartOfLongSourceMaterial() {
        val instructions = "比较完整人物档案；只返回确实变化的字段；保持否定与例外。"
        val source = "开篇人物：合成甲。\n" + "长篇正文，保留旅程经过。".repeat(2500) +
            "\n结尾关键设定：合成甲不能登船，只有合成乙可以登船。\n引号\"与反斜线\\及新行。"
        val prompt = com.aiassistant.utils.RoleplaySmartAnalyzer.buildCreativeAnalysisPrompt(instructions, source)
        assertTrue(prompt.startsWith(instructions))
        val encodedSource = prompt.substringAfter("JSON：\n")
        val restored = com.google.gson.JsonParser.parseString(encodedSource).asString
        assertEquals(source, restored)
        assertTrue(restored.length > 20000)
        assertTrue(restored.endsWith("引号\"与反斜线\\及新行。"))
    }

    @Test
    fun malformedStoryAnalysisCannotBecomeAnAuthoritativeNoUpdate() {
        listOf("{\"updatedCharacters\":[", "null", "{}", "{\"updatedCharacters\":\"broken\"}").forEach { invalid ->
            org.junit.Assert.assertThrows(com.google.gson.JsonParseException::class.java) {
                com.aiassistant.utils.RoleplaySmartAnalyzer.parseStorySettingProposalJson(invalid, emptyList(), null, true)
            }
        }
        val noUpdate = com.aiassistant.utils.RoleplaySmartAnalyzer.parseStorySettingProposalJson(
            "{\"hasSettingUpdates\":false}", emptyList(), null, true)
        assertTrue(noUpdate.isAiAnalyzed)
        assertFalse(noUpdate.hasAnyUpdates)
    }

    @Test
    fun creativeContextKeepsHabitsAndLegacyEvidenceWithoutTurningThemIntoCurrentDaytime() = kotlinx.coroutines.runBlocking {
        val habit = "【习惯偏好】合成角色习惯早餐后整理航图"
        val legacyEvent = "[第6天·上午]合成角色在旧港吃完早餐后买好了备用船帆"
        val session = RoleplaySession(id = 3, conversationId = 4)
        for (span in listOf("承接【第6天·上午】·过了一两个月", "两周后")) {
            val repository = creativeContextRepository(session, span, listOf(habit), listOf(legacyEvent))
            val standalone = repository.assembleRoleplayContext(session.id, null, null, includeHistory = false)
            assertTrue(standalone.contains("【本故事长期事实与规则】"))
            assertTrue(standalone.contains(habit))
            assertTrue(standalone.contains("【兼容旧版剧情记录】"))
            assertTrue(standalone.contains(legacyEvent))
            assertTrue(standalone.contains("【故事当前时间节点】：$span"))
            assertFalse(standalone.contains("当前是白天"))
            assertFalse(standalone.contains("现在是白天"))
            assertFalse(standalone.contains("当前仍处于白天"))
            assertFalse(standalone.contains("剧情推进时间线与日常演进明细"))
            val chatContext = repository.assembleRoleplayContext(session.id, null, null, includeHistory = false,
                includeTimelineContext = false)
            assertTrue(chatContext.contains(habit))
            assertTrue(chatContext.contains(legacyEvent))
            assertFalse(chatContext.contains("【故事当前时间节点】"))
            assertFalse(chatContext.contains("<session_timeline>"))
        }
        val legacyOnly = creativeContextRepository(session, null, emptyList(), listOf("【当前故事时间】：两个月后", legacyEvent))
            .assembleRoleplayContext(session.id, null, null, includeHistory = false)
        assertTrue(legacyOnly.contains(legacyEvent))
        assertTrue(legacyOnly.contains("【故事当前时间节点】：两个月后"))
        assertFalse(legacyOnly.contains("当前是白天"))
    }

    private fun creativeContextRepository(
        session: RoleplaySession,
        currentTime: String?,
        facts: List<String>,
        pinned: List<String>
    ): com.aiassistant.data.repository.RoleplayRepository {
        val conversation = com.aiassistant.domain.model.Conversation(id = session.conversationId,
            title = "合成故事", apiConfigId = 1, modelName = "synthetic-model", currentStoryTime = currentTime)
        return com.aiassistant.data.repository.RoleplayRepository(
            stubDao(com.aiassistant.data.local.CharacterProfileDao::class.java, emptyMap()),
            stubDao(com.aiassistant.data.local.RoleplayScenarioDao::class.java, emptyMap()),
            stubDao(com.aiassistant.data.local.RoleplaySessionDao::class.java, mapOf("getSessionById" to session)),
            stubDao(com.aiassistant.data.local.RoleplayMemoryDao::class.java, mapOf("getPinnedFacts" to pinned.map {
                RoleplayMemory(sessionId = session.id, memoryType = "fact", content = it, isPinned = true)
            })),
            stubDao(com.aiassistant.data.local.CharacterTagDao::class.java, emptyMap()),
            conversationDao = stubDao(com.aiassistant.data.local.ConversationDao::class.java, mapOf("getConversationById" to conversation)),
            memoryDao = stubDao(com.aiassistant.data.local.MemoryDao::class.java, mapOf("getCandidateMemories" to facts.map {
                com.aiassistant.domain.model.MemoryItem(scope = "conversation", conversationId = session.conversationId, content = it)
            }))
        )
    }

    private fun <T> stubDao(type: Class<T>, values: Map<String, Any>): T = type.cast(
        java.lang.reflect.Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
            values[method.name] ?: error("Unexpected DAO call: ${type.simpleName}.${method.name}")
        }
    )
}
