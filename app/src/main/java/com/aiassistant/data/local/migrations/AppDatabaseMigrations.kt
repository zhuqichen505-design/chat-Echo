package com.aiassistant.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * AppDatabase 历史数据库迁移与修复规则集合
 * 包含版本 1 至 29 的所有 Migration 定义与兜底 repairSchema 逻辑
 */
object AppDatabaseMigrations {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `folders` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `icon` TEXT NOT NULL DEFAULT 'folder',
                        `color` INTEGER NOT NULL DEFAULT 0,
                        `parentId` INTEGER,
                        `sortOrder` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_folders_parentId` ON `folders` (`parentId`)")
                database.execSQL("ALTER TABLE `api_configs` ADD COLUMN `topK` INTEGER NOT NULL DEFAULT 50")
                database.execSQL("ALTER TABLE `api_configs` ADD COLUMN `frequencyPenalty` REAL NOT NULL DEFAULT 0.0")
                database.execSQL("ALTER TABLE `api_configs` ADD COLUMN `presencePenalty` REAL NOT NULL DEFAULT 0.0")
                database.execSQL("ALTER TABLE `api_configs` ADD COLUMN `enableThinking` INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE `api_configs` ADD COLUMN `thinkingBudget` INTEGER NOT NULL DEFAULT 1024")
                database.execSQL("ALTER TABLE `api_configs` ADD COLUMN `thinkingEffort` TEXT NOT NULL DEFAULT 'medium'")
                database.execSQL("ALTER TABLE `api_configs` ADD COLUMN `stopSequences` TEXT")
                database.execSQL("ALTER TABLE `api_configs` ADD COLUMN `seed` INTEGER")
                database.execSQL("ALTER TABLE `api_configs` ADD COLUMN `responseFormat` TEXT")
                database.execSQL("ALTER TABLE `conversations` ADD COLUMN `folderId` INTEGER")
                database.execSQL("ALTER TABLE `conversations` ADD COLUMN `isPinned` INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE `conversations` ADD COLUMN `tags` TEXT")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_conversations_folderId` ON `conversations` (`folderId`)")
                database.execSQL("ALTER TABLE `messages` ADD COLUMN `thinkingContent` TEXT")
                database.execSQL("ALTER TABLE `messages` ADD COLUMN `thinkingTokens` INTEGER NOT NULL DEFAULT 0")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_conversationId` ON `messages` (`conversationId`)")
                database.execSQL("ALTER TABLE `api_usage_stats` ADD COLUMN `thinkingTokens` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `api_configs` ADD COLUMN `enableWebSearch` INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE `api_configs` ADD COLUMN `searchContextSize` TEXT NOT NULL DEFAULT 'medium'")
                database.execSQL("ALTER TABLE `messages` ADD COLUMN `attachments` TEXT")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `api_configs` ADD COLUMN `apiType` TEXT NOT NULL DEFAULT 'openai'")
                database.execSQL("ALTER TABLE `api_configs` ADD COLUMN `availableModels` TEXT")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `prompt_templates` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `description` TEXT,
                        `category` TEXT NOT NULL DEFAULT 'general',
                        `variables` TEXT,
                        `isBuiltIn` INTEGER NOT NULL DEFAULT 0,
                        `useCount` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_prompt_templates_category` ON `prompt_templates` (`category`)")
            }
        }

        // 从任意旧版本迁移到版本10（空迁移，只是提高版本号）
        private val MIGRATION_5_10 = object : Migration(5, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {}
        }
        private val MIGRATION_6_10 = object : Migration(6, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {}
        }
        private val MIGRATION_7_10 = object : Migration(7, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {}
        }
        private val MIGRATION_8_10 = object : Migration(8, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {}
        }
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {}
        }

        // 从版本10迁移到版本11 - 添加新表
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 创建会话分支表
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `conversation_branches` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `parentConversationId` INTEGER NOT NULL,
                        `branchMessageId` INTEGER NOT NULL,
                        `childConversationId` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_conversation_branches_parentConversationId` ON `conversation_branches` (`parentConversationId`)")

                // 创建选择的模型表
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `selected_models` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `apiConfigId` INTEGER NOT NULL,
                        `modelName` TEXT NOT NULL,
                        `displayName` TEXT,
                        `isEnabled` INTEGER NOT NULL DEFAULT 1,
                        `sortOrder` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_selected_models_apiConfigId` ON `selected_models` (`apiConfigId`)")

                // 给api_usage_stats表添加缓存token字段
                addColumnIfMissing(database, "api_usage_stats", "cachedTokens", "INTEGER NOT NULL DEFAULT 0")
            }
        }

        // 从版本11迁移到版本12 - 添加对话级别配置
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                addConversationSettingsColumns(database)
            }
        }

        // 从版本12迁移到版本13 - 修复历史版本空迁移可能留下的缺失字段。
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                addColumnIfMissing(database, "api_configs", "apiType", "TEXT NOT NULL DEFAULT 'openai'")
                addColumnIfMissing(database, "api_configs", "availableModels", "TEXT")
                addColumnIfMissing(database, "api_configs", "topK", "INTEGER NOT NULL DEFAULT 50")
                addColumnIfMissing(database, "api_configs", "frequencyPenalty", "REAL NOT NULL DEFAULT 0.0")
                addColumnIfMissing(database, "api_configs", "presencePenalty", "REAL NOT NULL DEFAULT 0.0")
                addColumnIfMissing(database, "api_configs", "enableThinking", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(database, "api_configs", "thinkingBudget", "INTEGER NOT NULL DEFAULT 1024")
                addColumnIfMissing(database, "api_configs", "thinkingEffort", "TEXT NOT NULL DEFAULT 'medium'")
                addColumnIfMissing(database, "api_configs", "enableWebSearch", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(database, "api_configs", "searchContextSize", "TEXT NOT NULL DEFAULT 'medium'")
                addColumnIfMissing(database, "api_configs", "stopSequences", "TEXT")
                addColumnIfMissing(database, "api_configs", "seed", "INTEGER")
                addColumnIfMissing(database, "api_configs", "responseFormat", "TEXT")

                addColumnIfMissing(database, "messages", "thinkingContent", "TEXT")
                addColumnIfMissing(database, "messages", "thinkingTokens", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(database, "messages", "attachments", "TEXT")

                addColumnIfMissing(database, "api_usage_stats", "inputTokens", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(database, "api_usage_stats", "outputTokens", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(database, "api_usage_stats", "thinkingTokens", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(database, "api_usage_stats", "cachedTokens", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(database, "api_usage_stats", "responseTime", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(database, "api_usage_stats", "errorMessage", "TEXT")

                addColumnIfMissing(database, "conversations", "folderId", "INTEGER")
                addColumnIfMissing(database, "conversations", "isPinned", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(database, "conversations", "tags", "TEXT")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_conversations_folderId` ON `conversations` (`folderId`)")
                addConversationSettingsColumns(database)
            }
        }

        private fun addConversationSettingsColumns(database: SupportSQLiteDatabase) {
            addColumnIfMissing(database, "conversations", "temperature", "REAL")
            addColumnIfMissing(database, "conversations", "maxTokens", "INTEGER")
            addColumnIfMissing(database, "conversations", "topP", "REAL")
            addColumnIfMissing(database, "conversations", "enableThinking", "INTEGER")
            addColumnIfMissing(database, "conversations", "thinkingEffort", "TEXT")
            addColumnIfMissing(database, "conversations", "enableWebSearch", "INTEGER")
        }

        // 从版本17迁移到版本18 - 添加角色扮演相关表
        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 创建角色卡表
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `character_profiles` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `avatarUri` TEXT,
                        `identity` TEXT NOT NULL DEFAULT '',
                        `personality` TEXT NOT NULL DEFAULT '',
                        `background` TEXT NOT NULL DEFAULT '',
                        `speakingStyle` TEXT NOT NULL DEFAULT '',
                        `goals` TEXT NOT NULL DEFAULT '',
                        `relationships` TEXT NOT NULL DEFAULT '',
                        `knowledge` TEXT NOT NULL DEFAULT '',
                        `constraints` TEXT NOT NULL DEFAULT '',
                        `behaviorRules` TEXT NOT NULL DEFAULT '',
                        `greeting` TEXT NOT NULL DEFAULT '',
                        `exampleDialogue` TEXT NOT NULL DEFAULT '',
                        `tags` TEXT,
                        `isFavorite` INTEGER NOT NULL DEFAULT 0,
                        `isDefault` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_character_profiles_isFavorite` ON `character_profiles` (`isFavorite`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_character_profiles_isDefault` ON `character_profiles` (`isDefault`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_character_profiles_createdAt` ON `character_profiles` (`createdAt`)")

                // 创建场景卡表
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `roleplay_scenarios` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `worldview` TEXT NOT NULL DEFAULT '',
                        `time` TEXT NOT NULL DEFAULT '',
                        `location` TEXT NOT NULL DEFAULT '',
                        `environment` TEXT NOT NULL DEFAULT '',
                        `premise` TEXT NOT NULL DEFAULT '',
                        `rules` TEXT NOT NULL DEFAULT '',
                        `relationshipState` TEXT NOT NULL DEFAULT '',
                        `conflict` TEXT NOT NULL DEFAULT '',
                        `plotGoal` TEXT NOT NULL DEFAULT '',
                        `atmosphere` TEXT NOT NULL DEFAULT '',
                        `narrativePerspective` TEXT NOT NULL DEFAULT '',
                        `outputFormat` TEXT NOT NULL DEFAULT '',
                        `contentRestrictions` TEXT NOT NULL DEFAULT '',
                        `openingPrompt` TEXT NOT NULL DEFAULT '',
                        `tags` TEXT,
                        `isFavorite` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_roleplay_scenarios_isFavorite` ON `roleplay_scenarios` (`isFavorite`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_roleplay_scenarios_createdAt` ON `roleplay_scenarios` (`createdAt`)")

                // 创建角色扮演会话表
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `roleplay_sessions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `characterId` INTEGER,
                        `scenarioId` INTEGER,
                        `conversationId` INTEGER NOT NULL,
                        `narrativeMode` TEXT NOT NULL DEFAULT 'character',
                        `currentPlotSummary` TEXT NOT NULL DEFAULT '',
                        `pinnedFacts` TEXT,
                        `lastVersionIndex` INTEGER NOT NULL DEFAULT 1,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`characterId`) REFERENCES `character_profiles`(`id`) ON DELETE SET NULL,
                        FOREIGN KEY(`scenarioId`) REFERENCES `roleplay_scenarios`(`id`) ON DELETE SET NULL,
                        FOREIGN KEY(`conversationId`) REFERENCES `conversations`(`id`) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_roleplay_sessions_characterId` ON `roleplay_sessions` (`characterId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_roleplay_sessions_scenarioId` ON `roleplay_sessions` (`scenarioId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_roleplay_sessions_conversationId` ON `roleplay_sessions` (`conversationId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_roleplay_sessions_createdAt` ON `roleplay_sessions` (`createdAt`)")

                // 创建角色扮演记忆表
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `roleplay_memories` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sessionId` INTEGER NOT NULL,
                        `memoryType` TEXT NOT NULL DEFAULT 'fact',
                        `content` TEXT NOT NULL,
                        `sourceMessageId` INTEGER,
                        `isPinned` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`sessionId`) REFERENCES `roleplay_sessions`(`id`) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_roleplay_memories_sessionId` ON `roleplay_memories` (`sessionId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_roleplay_memories_memoryType` ON `roleplay_memories` (`memoryType`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_roleplay_memories_createdAt` ON `roleplay_memories` (`createdAt`)")

                // 创建角色标签表
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `character_tags` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                """)
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_character_tags_name` ON `character_tags` (`name`)")

                // 创建角色-标签关联表
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `character_tag_cross_ref` (
                        `characterId` INTEGER NOT NULL,
                        `tagId` INTEGER NOT NULL,
                        PRIMARY KEY(`characterId`, `tagId`),
                        FOREIGN KEY(`characterId`) REFERENCES `character_profiles`(`id`) ON DELETE CASCADE,
                        FOREIGN KEY(`tagId`) REFERENCES `character_tags`(`id`) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_character_tag_cross_ref_tagId` ON `character_tag_cross_ref` (`tagId`)")
            }
        }

        private fun addColumnIfMissing(
            database: SupportSQLiteDatabase,
            tableName: String,
            columnName: String,
            definition: String
        ) {
            if (!hasColumn(database, tableName, columnName)) {
                database.execSQL("ALTER TABLE `$tableName` ADD COLUMN `$columnName` $definition")
            }
        }

        private fun hasColumn(
            database: SupportSQLiteDatabase,
            tableName: String,
            columnName: String
        ): Boolean {
            database.query("PRAGMA table_info(`$tableName`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == columnName) return true
                }
                return false
            }
        }

        // 从版本18迁移到版本19 - 添加多角色 characterIds 字段
        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(database: SupportSQLiteDatabase) {
                addColumnIfMissing(database, "roleplay_sessions", "characterIds", "TEXT")
            }
        }

        // 从版本19迁移到版本20 - 添加当前故事专属角色与世界观覆盖字段
        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(database: SupportSQLiteDatabase) {
                addColumnIfMissing(database, "roleplay_sessions", "customCharacterData", "TEXT")
                addColumnIfMissing(database, "roleplay_sessions", "customScenarioData", "TEXT")
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(database: SupportSQLiteDatabase) {
                addColumnIfMissing(database, "messages", "toolCalls", "TEXT")
            }
        }

        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(database: SupportSQLiteDatabase) {
                addColumnIfMissing(database, "messages", "translatedThinking", "TEXT")
            }
        }

        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(database: SupportSQLiteDatabase) {
                addColumnIfMissing(database, "conversations", "enableSessionMemory", "INTEGER")
                addColumnIfMissing(database, "selected_models", "contextWindowTokens", "INTEGER")
                addColumnIfMissing(database, "selected_models", "supportsTools", "INTEGER NOT NULL DEFAULT 1")
                addColumnIfMissing(database, "selected_models", "supportsVision", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(database, "selected_models", "supportsThinking", "INTEGER NOT NULL DEFAULT 1")
                addColumnIfMissing(database, "selected_models", "supportsWebSearch", "INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(database: SupportSQLiteDatabase) {
                addColumnIfMissing(database, "messages", "isPinned", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(database, "messages", "isExcluded", "INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `world_books` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL DEFAULT '',
                        `isEnabled` INTEGER NOT NULL DEFAULT 1,
                        `tags` TEXT,
                        `createdAt` INTEGER NOT NULL DEFAULT 0,
                        `updatedAt` INTEGER NOT NULL DEFAULT 0
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_world_books_isEnabled` ON `world_books` (`isEnabled`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_world_books_createdAt` ON `world_books` (`createdAt`)")

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `world_book_entries` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `bookId` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `keys` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `isEnabled` INTEGER NOT NULL DEFAULT 1,
                        `isConstant` INTEGER NOT NULL DEFAULT 0,
                        `priority` INTEGER NOT NULL DEFAULT 100,
                        `createdAt` INTEGER NOT NULL DEFAULT 0,
                        `updatedAt` INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(`bookId`) REFERENCES `world_books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_world_book_entries_bookId` ON `world_book_entries` (`bookId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_world_book_entries_isEnabled` ON `world_book_entries` (`isEnabled`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_world_book_entries_isConstant` ON `world_book_entries` (`isConstant`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_world_book_entries_priority` ON `world_book_entries` (`priority`)")

                addColumnIfMissing(database, "conversations", "enableExternalMemory", "INTEGER")
                addColumnIfMissing(database, "conversations", "enableWorldBook", "INTEGER")
                addColumnIfMissing(database, "conversations", "activeWorldBookIds", "TEXT")

                addColumnIfMissing(database, "roleplay_sessions", "enableExternalMemory", "INTEGER NOT NULL DEFAULT 1")
                addColumnIfMissing(database, "roleplay_sessions", "enableWorldBook", "INTEGER NOT NULL DEFAULT 1")
                addColumnIfMissing(database, "roleplay_sessions", "activeWorldBookIds", "TEXT")
            }
        }

        val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(database: SupportSQLiteDatabase) {
                addColumnIfMissing(database, "conversations", "modelAvatarUri", "TEXT")
            }
        }

        val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(database: SupportSQLiteDatabase) {
                addColumnIfMissing(database, "api_configs", "isEnabled", "INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `timeline_nodes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `conversationId` INTEGER NOT NULL,
                        `timeTag` TEXT NOT NULL,
                        `eventContent` TEXT NOT NULL,
                        `category` TEXT NOT NULL DEFAULT 'PLOT_EVENT',
                        `orderIndex` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL DEFAULT 0,
                        `updatedAt` INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(`conversationId`) REFERENCES `conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_timeline_nodes_conversationId` ON `timeline_nodes` (`conversationId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_timeline_nodes_orderIndex` ON `timeline_nodes` (`orderIndex`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_timeline_nodes_updatedAt` ON `timeline_nodes` (`updatedAt`)")

                addColumnIfMissing(database, "conversations", "currentStoryTime", "TEXT")

                // 自动迁移旧记忆表中的当前故事时间到 conversations 表
                try {
                    database.execSQL("""
                        UPDATE conversations
                        SET currentStoryTime = (
                            SELECT TRIM(SUBSTR(content, INSTR(content, '：') + 1))
                            FROM memory_items
                            WHERE conversationId = conversations.id
                              AND (content LIKE '【当前故事时间】：%' OR content LIKE '当前故事时间：%')
                            ORDER BY updatedAt DESC
                            LIMIT 1
                        )
                        WHERE currentStoryTime IS NULL
                    """)
                    // 清理 memory_items 中的时间记忆条目，保证会话记忆纯净
                    database.execSQL("DELETE FROM memory_items WHERE content LIKE '【当前故事时间】：%' OR content LIKE '当前故事时间：%'")
                } catch (_: Exception) {
                }
            }
        }

        val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(database: SupportSQLiteDatabase) {
                addColumnIfMissing(database, "conversations", "contextWindowTokens", "INTEGER")
            }
        }

        val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(database: SupportSQLiteDatabase) {
                addColumnIfMissing(database, "conversations", "compressionTier", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(database, "conversations", "compressionRecentRounds", "INTEGER NOT NULL DEFAULT 8")
            }
        }

        val MIGRATION_30_31 = object : Migration(30, 31) {
            override fun migrate(database: SupportSQLiteDatabase) {
                addColumnIfMissing(database, "conversations", "compressionCustomPercent", "INTEGER NOT NULL DEFAULT 30")
            }
        }

        val MIGRATION_31_32 = object : Migration(31, 32) {
            override fun migrate(database: SupportSQLiteDatabase) {
                addColumnIfMissing(database, "messages", "modelName", "TEXT")
            }
        }

        val MIGRATION_32_33 = object : Migration(32, 33) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `backup_identities` (`entityTable` TEXT NOT NULL, `entityId` INTEGER NOT NULL, `uuid` TEXT NOT NULL, PRIMARY KEY(`entityTable`, `entityId`))")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_backup_identities_uuid` ON `backup_identities` (`uuid`)")
            }
        }

        val MIGRATION_33_34 = object : Migration(33, 34) {
            override fun migrate(database: SupportSQLiteDatabase) {
                addColumnIfMissing(database, "conversations", "enableReplyDirections", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(database, "conversations", "replyDirectionCount", "INTEGER NOT NULL DEFAULT 2")
                addColumnIfMissing(database, "messages", "replyDirection", "TEXT")
            }
        }

        val LEGACY_REPAIR_MIGRATIONS: Array<Migration> = ((1..22)
            .map { startVersion ->
                object : Migration(startVersion, 23) {
                    override fun migrate(database: SupportSQLiteDatabase) {
                        repairSchema(database)
                    }
                }
            } + MIGRATION_17_18 + MIGRATION_18_19 + MIGRATION_19_20 + MIGRATION_20_21 + MIGRATION_21_22 + MIGRATION_22_23 + MIGRATION_23_24 + MIGRATION_24_25 + MIGRATION_25_26 + MIGRATION_26_27 + MIGRATION_27_28 + MIGRATION_28_29 + MIGRATION_29_30 + MIGRATION_30_31 + MIGRATION_31_32 + MIGRATION_32_33 + MIGRATION_33_34)
            .toTypedArray()

        private fun repairSchema(database: SupportSQLiteDatabase) {
            database.execSQL("PRAGMA foreign_keys=OFF;")
            try {
                repairTable(
                    database,
                    tableName = "folders",
                columns = listOf(
                    ColumnSpec("id", "INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL", "0"),
                    ColumnSpec("name", "TEXT NOT NULL", "'未命名'"),
                    ColumnSpec("icon", "TEXT NOT NULL", "'folder'"),
                    ColumnSpec("color", "INTEGER NOT NULL", "0"),
                    ColumnSpec("parentId", "INTEGER", "NULL", nullable = true),
                    ColumnSpec("sortOrder", "INTEGER NOT NULL", "0"),
                    ColumnSpec("createdAt", "INTEGER NOT NULL", "0"),
                    ColumnSpec("updatedAt", "INTEGER NOT NULL", "0")
                ),
                indices = listOf("CREATE INDEX IF NOT EXISTS `index_folders_parentId` ON `folders` (`parentId`)")
            )
            repairTable(
                database,
                tableName = "api_configs",
                columns = listOf(
                    ColumnSpec("id", "INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL", "0"),
                    ColumnSpec("name", "TEXT NOT NULL", "'API配置'"),
                    ColumnSpec("provider", "TEXT NOT NULL", "'OpenAI'"),
                    ColumnSpec("baseUrl", "TEXT NOT NULL", "''"),
                    ColumnSpec("apiKey", "TEXT NOT NULL", "''"),
                    ColumnSpec("apiType", "TEXT NOT NULL", "'openai'"),
                    ColumnSpec("modelName", "TEXT NOT NULL", "'gpt-4o-mini'"),
                    ColumnSpec("availableModels", "TEXT", "NULL", nullable = true),
                    ColumnSpec("maxTokens", "INTEGER NOT NULL", "8192"),
                    ColumnSpec("temperature", "REAL NOT NULL", "0.95"),
                    ColumnSpec("topP", "REAL NOT NULL", "1.0"),
                    ColumnSpec("topK", "INTEGER NOT NULL", "50"),
                    ColumnSpec("frequencyPenalty", "REAL NOT NULL", "0.0"),
                    ColumnSpec("presencePenalty", "REAL NOT NULL", "0.0"),
                    ColumnSpec("enableThinking", "INTEGER NOT NULL", "0"),
                    ColumnSpec("thinkingBudget", "INTEGER NOT NULL", "1024"),
                    ColumnSpec("thinkingEffort", "TEXT NOT NULL", "'medium'"),
                    ColumnSpec("enableWebSearch", "INTEGER NOT NULL", "0"),
                    ColumnSpec("searchContextSize", "TEXT NOT NULL", "'medium'"),
                    ColumnSpec("stopSequences", "TEXT", "NULL", nullable = true),
                    ColumnSpec("seed", "INTEGER", "NULL", nullable = true),
                    ColumnSpec("responseFormat", "TEXT", "NULL", nullable = true),
                    ColumnSpec("isDefault", "INTEGER NOT NULL", "0"),
                    ColumnSpec("isEnabled", "INTEGER NOT NULL", "1"),
                    ColumnSpec("createdAt", "INTEGER NOT NULL", "0"),
                    ColumnSpec("updatedAt", "INTEGER NOT NULL", "0")
                )
            )
            repairTable(
                database,
                tableName = "conversations",
                columns = listOf(
                    ColumnSpec("id", "INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL", "0"),
                    ColumnSpec("title", "TEXT NOT NULL", "'新对话'"),
                    ColumnSpec("folderId", "INTEGER", "NULL", nullable = true),
                    ColumnSpec("apiConfigId", "INTEGER NOT NULL", "0"),
                    ColumnSpec("modelName", "TEXT NOT NULL", "''"),
                    ColumnSpec("systemPrompt", "TEXT", "NULL", nullable = true),
                    ColumnSpec("rollingSummary", "TEXT", "NULL", nullable = true),
                    ColumnSpec("summaryUpdatedMessageId", "INTEGER", "NULL", nullable = true),
                    ColumnSpec("summaryUpdatedAt", "INTEGER", "NULL", nullable = true),
                    ColumnSpec("totalTokens", "INTEGER NOT NULL", "0"),
                    ColumnSpec("messageCount", "INTEGER NOT NULL", "0"),
                    ColumnSpec("isPinned", "INTEGER NOT NULL", "0"),
                    ColumnSpec("tags", "TEXT", "NULL", nullable = true),
                    ColumnSpec("temperature", "REAL", "NULL", nullable = true),
                    ColumnSpec("maxTokens", "INTEGER", "NULL", nullable = true),
                    ColumnSpec("topP", "REAL", "NULL", nullable = true),
                    ColumnSpec("enableThinking", "INTEGER", "NULL", nullable = true),
                    ColumnSpec("thinkingEffort", "TEXT", "NULL", nullable = true),
                    ColumnSpec("enableWebSearch", "INTEGER", "NULL", nullable = true),
                    ColumnSpec("enableSessionMemory", "INTEGER", "NULL", nullable = true),
                    ColumnSpec("enableExternalMemory", "INTEGER", "NULL", nullable = true),
                    ColumnSpec("enableWorldBook", "INTEGER", "NULL", nullable = true),
                    ColumnSpec("activeWorldBookIds", "TEXT", "NULL", nullable = true),
                    ColumnSpec("modelAvatarUri", "TEXT", "NULL", nullable = true),
                    ColumnSpec("currentStoryTime", "TEXT", "NULL", nullable = true),
                    ColumnSpec("contextWindowTokens", "INTEGER", "NULL", nullable = true),
                    ColumnSpec("compressionTier", "INTEGER NOT NULL", "0"),
                    ColumnSpec("compressionRecentRounds", "INTEGER NOT NULL", "8"),
                    ColumnSpec("createdAt", "INTEGER NOT NULL", "0"),
                    ColumnSpec("updatedAt", "INTEGER NOT NULL", "0")
                ),
                indices = listOf("CREATE INDEX IF NOT EXISTS `index_conversations_folderId` ON `conversations` (`folderId`)")
            )
            repairTable(
                database,
                tableName = "messages",
                columns = listOf(
                    ColumnSpec("id", "INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL", "0"),
                    ColumnSpec("conversationId", "INTEGER NOT NULL", "0"),
                    ColumnSpec("role", "TEXT NOT NULL", "'user'"),
                    ColumnSpec("content", "TEXT NOT NULL", "''"),
                    ColumnSpec("thinkingContent", "TEXT", "NULL", nullable = true),
                    ColumnSpec("attachments", "TEXT", "NULL", nullable = true),
                    ColumnSpec("variantGroupId", "TEXT", "NULL", nullable = true),
                    ColumnSpec("variantIndex", "INTEGER NOT NULL", "1"),
                    ColumnSpec("tokenCount", "INTEGER NOT NULL", "0"),
                    ColumnSpec("thinkingTokens", "INTEGER NOT NULL", "0"),
                    ColumnSpec("responseTime", "INTEGER NOT NULL", "0"),
                    ColumnSpec("toolCalls", "TEXT", "NULL", nullable = true),
                    ColumnSpec("translatedThinking", "TEXT", "NULL", nullable = true),
                    ColumnSpec("isPinned", "INTEGER NOT NULL", "0"),
                    ColumnSpec("isExcluded", "INTEGER NOT NULL", "0"),
                    ColumnSpec("modelName", "TEXT", "NULL", nullable = true),
                    ColumnSpec("createdAt", "INTEGER NOT NULL", "0")
                ),
                indices = listOf("CREATE INDEX IF NOT EXISTS `index_messages_conversationId` ON `messages` (`conversationId`)")
            )
            repairTable(
                database,
                tableName = "api_usage_stats",
                columns = listOf(
                    ColumnSpec("id", "INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL", "0"),
                    ColumnSpec("apiConfigId", "INTEGER NOT NULL", "0"),
                    ColumnSpec("provider", "TEXT NOT NULL", "'unknown'"),
                    ColumnSpec("modelName", "TEXT NOT NULL", "'unknown'"),
                    ColumnSpec("inputTokens", "INTEGER NOT NULL", "0"),
                    ColumnSpec("outputTokens", "INTEGER NOT NULL", "0"),
                    ColumnSpec("thinkingTokens", "INTEGER NOT NULL", "0"),
                    ColumnSpec("totalTokens", "INTEGER NOT NULL", "0"),
                    ColumnSpec("cachedTokens", "INTEGER NOT NULL", "0"),
                    ColumnSpec("responseTime", "INTEGER NOT NULL", "0"),
                    ColumnSpec("success", "INTEGER NOT NULL", "1"),
                    ColumnSpec("errorMessage", "TEXT", "NULL", nullable = true),
                    ColumnSpec("timestamp", "INTEGER NOT NULL", "0")
                )
            )
            repairTable(
                database,
                tableName = "environment_variables",
                columns = listOf(
                    ColumnSpec("id", "INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL", "0"),
                    ColumnSpec("name", "TEXT NOT NULL", "''"),
                    ColumnSpec("value", "TEXT NOT NULL", "''"),
                    ColumnSpec("description", "TEXT", "NULL", nullable = true),
                    ColumnSpec("environment", "TEXT NOT NULL", "'default'"),
                    ColumnSpec("createdAt", "INTEGER NOT NULL", "0"),
                    ColumnSpec("updatedAt", "INTEGER NOT NULL", "0")
                )
            )
            repairTable(
                database,
                tableName = "prompt_templates",
                columns = listOf(
                    ColumnSpec("id", "INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL", "0"),
                    ColumnSpec("name", "TEXT NOT NULL", "''"),
                    ColumnSpec("content", "TEXT NOT NULL", "''"),
                    ColumnSpec("description", "TEXT", "NULL", nullable = true),
                    ColumnSpec("category", "TEXT NOT NULL", "'general'"),
                    ColumnSpec("variables", "TEXT", "NULL", nullable = true),
                    ColumnSpec("isBuiltIn", "INTEGER NOT NULL", "0"),
                    ColumnSpec("useCount", "INTEGER NOT NULL", "0"),
                    ColumnSpec("createdAt", "INTEGER NOT NULL", "0"),
                    ColumnSpec("updatedAt", "INTEGER NOT NULL", "0")
                )
            )
            repairTable(
                database,
                tableName = "memory_items",
                columns = listOf(
                    ColumnSpec("id", "INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL", "0"),
                    ColumnSpec("scope", "TEXT NOT NULL", "'user'"),
                    ColumnSpec("conversationId", "INTEGER", "NULL", nullable = true),
                    ColumnSpec("content", "TEXT NOT NULL", "''"),
                    ColumnSpec("keywords", "TEXT", "NULL", nullable = true),
                    ColumnSpec("sourceMessageId", "INTEGER", "NULL", nullable = true),
                    ColumnSpec("confidence", "REAL NOT NULL", "0.6"),
                    ColumnSpec("isEnabled", "INTEGER NOT NULL", "1"),
                    ColumnSpec("createdAt", "INTEGER NOT NULL", "0"),
                    ColumnSpec("updatedAt", "INTEGER NOT NULL", "0")
                ),
                indices = listOf(
                    "CREATE INDEX IF NOT EXISTS `index_memory_items_scope_conversationId` ON `memory_items` (`scope`, `conversationId`)",
                    "CREATE INDEX IF NOT EXISTS `index_memory_items_sourceMessageId` ON `memory_items` (`sourceMessageId`)",
                    "CREATE INDEX IF NOT EXISTS `index_memory_items_updatedAt` ON `memory_items` (`updatedAt`)"
                )
            )
            repairTable(
                database,
                tableName = "conversation_branches",
                columns = listOf(
                    ColumnSpec("id", "INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL", "0"),
                    ColumnSpec("parentConversationId", "INTEGER NOT NULL", "0"),
                    ColumnSpec("branchMessageId", "INTEGER NOT NULL", "0"),
                    ColumnSpec("childConversationId", "INTEGER NOT NULL", "0"),
                    ColumnSpec("createdAt", "INTEGER NOT NULL", "0")
                ),
                indices = listOf("CREATE INDEX IF NOT EXISTS `index_conversation_branches_parentConversationId` ON `conversation_branches` (`parentConversationId`)")
            )
            repairTable(
                database,
                tableName = "selected_models",
                columns = listOf(
                    ColumnSpec("id", "INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL", "0"),
                    ColumnSpec("apiConfigId", "INTEGER NOT NULL", "0"),
                    ColumnSpec("modelName", "TEXT NOT NULL", "''"),
                    ColumnSpec("displayName", "TEXT", "NULL", nullable = true),
                    ColumnSpec("isEnabled", "INTEGER NOT NULL", "1"),
                    ColumnSpec("capability", "TEXT NOT NULL", "'auto'"),
                    ColumnSpec("sortOrder", "INTEGER NOT NULL", "0"),
                    ColumnSpec("contextWindowTokens", "INTEGER", "NULL", nullable = true),
                    ColumnSpec("supportsTools", "INTEGER NOT NULL", "1"),
                    ColumnSpec("supportsVision", "INTEGER NOT NULL", "0"),
                    ColumnSpec("supportsThinking", "INTEGER NOT NULL", "1"),
                    ColumnSpec("supportsWebSearch", "INTEGER NOT NULL", "1"),
                    ColumnSpec("createdAt", "INTEGER NOT NULL", "0")
                ),
                indices = listOf("CREATE INDEX IF NOT EXISTS `index_selected_models_apiConfigId` ON `selected_models` (`apiConfigId`)")
            )
            repairTable(
                database,
                tableName = "character_profiles",
                columns = listOf(
                    ColumnSpec("id", "INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL", "0"),
                    ColumnSpec("name", "TEXT NOT NULL", "''"),
                    ColumnSpec("avatarUri", "TEXT", "NULL", nullable = true),
                    ColumnSpec("identity", "TEXT NOT NULL", "''"),
                    ColumnSpec("personality", "TEXT NOT NULL", "''"),
                    ColumnSpec("background", "TEXT NOT NULL", "''"),
                    ColumnSpec("speakingStyle", "TEXT NOT NULL", "''"),
                    ColumnSpec("goals", "TEXT NOT NULL", "''"),
                    ColumnSpec("relationships", "TEXT NOT NULL", "''"),
                    ColumnSpec("knowledge", "TEXT NOT NULL", "''"),
                    ColumnSpec("constraints", "TEXT NOT NULL", "''"),
                    ColumnSpec("behaviorRules", "TEXT NOT NULL", "''"),
                    ColumnSpec("greeting", "TEXT NOT NULL", "''"),
                    ColumnSpec("exampleDialogue", "TEXT NOT NULL", "''"),
                    ColumnSpec("tags", "TEXT", "NULL", nullable = true),
                    ColumnSpec("isFavorite", "INTEGER NOT NULL", "0"),
                    ColumnSpec("isDefault", "INTEGER NOT NULL", "0"),
                    ColumnSpec("createdAt", "INTEGER NOT NULL", "0"),
                    ColumnSpec("updatedAt", "INTEGER NOT NULL", "0")
                ),
                indices = listOf(
                    "CREATE INDEX IF NOT EXISTS `index_character_profiles_isFavorite` ON `character_profiles` (`isFavorite`)",
                    "CREATE INDEX IF NOT EXISTS `index_character_profiles_isDefault` ON `character_profiles` (`isDefault`)",
                    "CREATE INDEX IF NOT EXISTS `index_character_profiles_createdAt` ON `character_profiles` (`createdAt`)"
                )
            )
            repairTable(
                database,
                tableName = "roleplay_scenarios",
                columns = listOf(
                    ColumnSpec("id", "INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL", "0"),
                    ColumnSpec("name", "TEXT NOT NULL", "''"),
                    ColumnSpec("worldview", "TEXT NOT NULL", "''"),
                    ColumnSpec("time", "TEXT NOT NULL", "''"),
                    ColumnSpec("location", "TEXT NOT NULL", "''"),
                    ColumnSpec("environment", "TEXT NOT NULL", "''"),
                    ColumnSpec("premise", "TEXT NOT NULL", "''"),
                    ColumnSpec("rules", "TEXT NOT NULL", "''"),
                    ColumnSpec("relationshipState", "TEXT NOT NULL", "''"),
                    ColumnSpec("conflict", "TEXT NOT NULL", "''"),
                    ColumnSpec("plotGoal", "TEXT NOT NULL", "''"),
                    ColumnSpec("atmosphere", "TEXT NOT NULL", "''"),
                    ColumnSpec("narrativePerspective", "TEXT NOT NULL", "''"),
                    ColumnSpec("outputFormat", "TEXT NOT NULL", "''"),
                    ColumnSpec("contentRestrictions", "TEXT NOT NULL", "''"),
                    ColumnSpec("openingPrompt", "TEXT NOT NULL", "''"),
                    ColumnSpec("tags", "TEXT", "NULL", nullable = true),
                    ColumnSpec("isFavorite", "INTEGER NOT NULL", "0"),
                    ColumnSpec("createdAt", "INTEGER NOT NULL", "0"),
                    ColumnSpec("updatedAt", "INTEGER NOT NULL", "0")
                ),
                indices = listOf(
                    "CREATE INDEX IF NOT EXISTS `index_roleplay_scenarios_isFavorite` ON `roleplay_scenarios` (`isFavorite`)",
                    "CREATE INDEX IF NOT EXISTS `index_roleplay_scenarios_createdAt` ON `roleplay_scenarios` (`createdAt`)"
                )
            )
            repairTable(
                database,
                tableName = "roleplay_sessions",
                columns = listOf(
                    ColumnSpec("id", "INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL", "0"),
                    ColumnSpec("characterId", "INTEGER", "NULL", nullable = true),
                    ColumnSpec("scenarioId", "INTEGER", "NULL", nullable = true),
                    ColumnSpec("conversationId", "INTEGER NOT NULL", "0"),
                    ColumnSpec("narrativeMode", "TEXT NOT NULL", "'character'"),
                    ColumnSpec("currentPlotSummary", "TEXT NOT NULL", "''"),
                    ColumnSpec("pinnedFacts", "TEXT", "NULL", nullable = true),
                    ColumnSpec("lastVersionIndex", "INTEGER NOT NULL", "1"),
                    ColumnSpec("characterIds", "TEXT", "NULL", nullable = true),
                    ColumnSpec("customCharacterData", "TEXT", "NULL", nullable = true),
                    ColumnSpec("customScenarioData", "TEXT", "NULL", nullable = true),
                    ColumnSpec("enableExternalMemory", "INTEGER NOT NULL", "1"),
                    ColumnSpec("enableWorldBook", "INTEGER NOT NULL", "1"),
                    ColumnSpec("activeWorldBookIds", "TEXT", "NULL", nullable = true),
                    ColumnSpec("createdAt", "INTEGER NOT NULL", "0"),
                    ColumnSpec("updatedAt", "INTEGER NOT NULL", "0")
                ),
                indices = listOf(
                    "CREATE INDEX IF NOT EXISTS `index_roleplay_sessions_characterId` ON `roleplay_sessions` (`characterId`)",
                    "CREATE INDEX IF NOT EXISTS `index_roleplay_sessions_scenarioId` ON `roleplay_sessions` (`scenarioId`)",
                    "CREATE INDEX IF NOT EXISTS `index_roleplay_sessions_conversationId` ON `roleplay_sessions` (`conversationId`)",
                    "CREATE INDEX IF NOT EXISTS `index_roleplay_sessions_createdAt` ON `roleplay_sessions` (`createdAt`)"
                )
            )
            repairTable(
                database,
                tableName = "roleplay_memories",
                columns = listOf(
                    ColumnSpec("id", "INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL", "0"),
                    ColumnSpec("sessionId", "INTEGER NOT NULL", "0"),
                    ColumnSpec("memoryType", "TEXT NOT NULL", "'fact'"),
                    ColumnSpec("content", "TEXT NOT NULL", "''"),
                    ColumnSpec("sourceMessageId", "INTEGER", "NULL", nullable = true),
                    ColumnSpec("isPinned", "INTEGER NOT NULL", "0"),
                    ColumnSpec("createdAt", "INTEGER NOT NULL", "0"),
                    ColumnSpec("updatedAt", "INTEGER NOT NULL", "0")
                ),
                indices = listOf(
                    "CREATE INDEX IF NOT EXISTS `index_roleplay_memories_sessionId` ON `roleplay_memories` (`sessionId`)",
                    "CREATE INDEX IF NOT EXISTS `index_roleplay_memories_memoryType` ON `roleplay_memories` (`memoryType`)",
                    "CREATE INDEX IF NOT EXISTS `index_roleplay_memories_createdAt` ON `roleplay_memories` (`createdAt`)"
                )
            )
            repairTable(
                database,
                tableName = "character_tags",
                columns = listOf(
                    ColumnSpec("id", "INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL", "0"),
                    ColumnSpec("name", "TEXT NOT NULL", "''"),
                    ColumnSpec("createdAt", "INTEGER NOT NULL", "0")
                ),
                indices = listOf("CREATE UNIQUE INDEX IF NOT EXISTS `index_character_tags_name` ON `character_tags` (`name`)")
            )
            repairTable(
                database,
                tableName = "character_tag_cross_ref",
                columns = listOf(
                    ColumnSpec("characterId", "INTEGER NOT NULL", "0"),
                    ColumnSpec("tagId", "INTEGER NOT NULL", "0")
                ),
                indices = listOf("CREATE INDEX IF NOT EXISTS `index_character_tag_cross_ref_tagId` ON `character_tag_cross_ref` (`tagId`)")
            )
            repairTable(
                database,
                tableName = "world_books",
                columns = listOf(
                    ColumnSpec("id", "INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL", "0"),
                    ColumnSpec("name", "TEXT NOT NULL", "''"),
                    ColumnSpec("description", "TEXT NOT NULL", "''"),
                    ColumnSpec("isEnabled", "INTEGER NOT NULL", "1"),
                    ColumnSpec("tags", "TEXT", "NULL", nullable = true),
                    ColumnSpec("createdAt", "INTEGER NOT NULL", "0"),
                    ColumnSpec("updatedAt", "INTEGER NOT NULL", "0")
                ),
                indices = listOf(
                    "CREATE INDEX IF NOT EXISTS `index_world_books_isEnabled` ON `world_books` (`isEnabled`)",
                    "CREATE INDEX IF NOT EXISTS `index_world_books_createdAt` ON `world_books` (`createdAt`)"
                )
            )
            repairTable(
                database,
                tableName = "world_book_entries",
                columns = listOf(
                    ColumnSpec("id", "INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL", "0"),
                    ColumnSpec("bookId", "INTEGER NOT NULL", "0"),
                    ColumnSpec("name", "TEXT NOT NULL", "''"),
                    ColumnSpec("keys", "TEXT NOT NULL", "''"),
                    ColumnSpec("content", "TEXT NOT NULL", "''"),
                    ColumnSpec("isEnabled", "INTEGER NOT NULL", "1"),
                    ColumnSpec("isConstant", "INTEGER NOT NULL", "0"),
                    ColumnSpec("priority", "INTEGER NOT NULL", "100"),
                    ColumnSpec("createdAt", "INTEGER NOT NULL", "0"),
                    ColumnSpec("updatedAt", "INTEGER NOT NULL", "0")
                ),
                indices = listOf(
                    "CREATE INDEX IF NOT EXISTS `index_world_book_entries_bookId` ON `world_book_entries` (`bookId`)",
                    "CREATE INDEX IF NOT EXISTS `index_world_book_entries_isEnabled` ON `world_book_entries` (`isEnabled`)",
                    "CREATE INDEX IF NOT EXISTS `index_world_book_entries_isConstant` ON `world_book_entries` (`isConstant`)",
                    "CREATE INDEX IF NOT EXISTS `index_world_book_entries_priority` ON `world_book_entries` (`priority`)"
                )
            )
            repairTable(
                database,
                tableName = "timeline_nodes",
                columns = listOf(
                    ColumnSpec("id", "INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL", "0"),
                    ColumnSpec("conversationId", "INTEGER NOT NULL", "0"),
                    ColumnSpec("timeTag", "TEXT NOT NULL", "''"),
                    ColumnSpec("eventContent", "TEXT NOT NULL", "''"),
                    ColumnSpec("category", "TEXT NOT NULL", "'PLOT_EVENT'"),
                    ColumnSpec("orderIndex", "INTEGER NOT NULL", "0"),
                    ColumnSpec("createdAt", "INTEGER NOT NULL", "0"),
                    ColumnSpec("updatedAt", "INTEGER NOT NULL", "0")
                ),
                indices = listOf(
                    "CREATE INDEX IF NOT EXISTS `index_timeline_nodes_conversationId` ON `timeline_nodes` (`conversationId`)",
                    "CREATE INDEX IF NOT EXISTS `index_timeline_nodes_orderIndex` ON `timeline_nodes` (`orderIndex`)",
                    "CREATE INDEX IF NOT EXISTS `index_timeline_nodes_updatedAt` ON `timeline_nodes` (`updatedAt`)"
                )
            )
        } finally {
            database.execSQL("PRAGMA foreign_keys=ON;")
        }
    }

        private fun repairTable(
            database: SupportSQLiteDatabase,
            tableName: String,
            columns: List<ColumnSpec>,
            indices: List<String> = emptyList()
        ) {
            val createSql = buildCreateTableSql(tableName, columns)
            if (!tableExists(database, tableName)) {
                database.execSQL(createSql)
                indices.forEach { database.execSQL(it) }
                return
            }

            val tempTable = "${tableName}_room_repair"
            database.execSQL("DROP TABLE IF EXISTS `$tempTable`")
            database.execSQL(buildCreateTableSql(tempTable, columns))

            val existingColumns = getColumns(database, tableName)
            val columnNames = columns.joinToString(", ") { "`${it.name}`" }
            val selectValues = columns.joinToString(", ") { column ->
                if (existingColumns.contains(column.name)) {
                    if (column.nullable) {
                        "`${column.name}`"
                    } else {
                        "COALESCE(`${column.name}`, ${column.fallbackSql})"
                    }
                } else {
                    column.fallbackSql
                }
            }
            database.execSQL("INSERT INTO `$tempTable` ($columnNames) SELECT $selectValues FROM `$tableName`")
            database.execSQL("DROP TABLE `$tableName`")
            database.execSQL("ALTER TABLE `$tempTable` RENAME TO `$tableName`")
            indices.forEach { database.execSQL(it) }
        }

        private fun buildCreateTableSql(tableName: String, columns: List<ColumnSpec>): String {
            val definitions = columns.joinToString(", ") { "`${it.name}` ${it.definition}" }
            return "CREATE TABLE IF NOT EXISTS `$tableName` ($definitions)"
        }

        private fun tableExists(database: SupportSQLiteDatabase, tableName: String): Boolean {
            database.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                arrayOf(tableName)
            ).use { cursor ->
                return cursor.moveToFirst()
            }
        }

        private fun getColumns(database: SupportSQLiteDatabase, tableName: String): Set<String> {
            val columns = mutableSetOf<String>()
            database.query("PRAGMA table_info(`$tableName`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    columns += cursor.getString(nameIndex)
                }
            }
            return columns
        }

        private data class ColumnSpec(
            val name: String,
            val definition: String,
            val fallbackSql: String,
            val nullable: Boolean = false
        )

}
