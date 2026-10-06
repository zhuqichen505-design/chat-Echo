package com.aiassistant.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import com.aiassistant.data.local.migrations.AppDatabaseMigrations
import com.aiassistant.domain.model.*

@Database(
    entities = [
        Folder::class,
        ApiConfig::class,
        Conversation::class,
        Message::class,
        ApiUsageStat::class,
        EnvironmentVariable::class,
        PromptTemplate::class,
        MemoryItem::class,
        ConversationBranch::class,
        SelectedModel::class,
        CharacterProfile::class,
        RoleplayScenario::class,
        RoleplaySession::class,
        RoleplayMemory::class,
        CharacterTag::class,
        CharacterTagCrossRef::class,
        WorldBook::class,
        WorldBookEntry::class,
        TimelineNode::class,
        BackupIdentity::class
    ],
    version = 34,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun apiConfigDao(): ApiConfigDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun usageStatDao(): UsageStatDao
    abstract fun environmentVariableDao(): EnvironmentVariableDao
    abstract fun promptTemplateDao(): PromptTemplateDao
    abstract fun memoryDao(): MemoryDao
    abstract fun conversationBranchDao(): ConversationBranchDao
    abstract fun selectedModelDao(): SelectedModelDao
    abstract fun characterProfileDao(): CharacterProfileDao
    abstract fun roleplayScenarioDao(): RoleplayScenarioDao
    abstract fun roleplaySessionDao(): RoleplaySessionDao
    abstract fun roleplayMemoryDao(): RoleplayMemoryDao
    abstract fun characterTagDao(): CharacterTagDao
    abstract fun worldBookDao(): WorldBookDao
    abstract fun timelineNodeDao(): TimelineNodeDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // 兼容已有单元测试对特定 Migration 实例的版本与行为断言
        val MIGRATION_20_21: Migration get() = AppDatabaseMigrations.MIGRATION_20_21
        val MIGRATION_21_22: Migration get() = AppDatabaseMigrations.MIGRATION_21_22
        val MIGRATION_22_23: Migration get() = AppDatabaseMigrations.MIGRATION_22_23
        val MIGRATION_23_24: Migration get() = AppDatabaseMigrations.MIGRATION_23_24
        val MIGRATION_24_25: Migration get() = AppDatabaseMigrations.MIGRATION_24_25
        val MIGRATION_25_26: Migration get() = AppDatabaseMigrations.MIGRATION_25_26
        val MIGRATION_26_27: Migration get() = AppDatabaseMigrations.MIGRATION_26_27
        val MIGRATION_27_28: Migration get() = AppDatabaseMigrations.MIGRATION_27_28
        val MIGRATION_28_29: Migration get() = AppDatabaseMigrations.MIGRATION_28_29
        val MIGRATION_29_30: Migration get() = AppDatabaseMigrations.MIGRATION_29_30
        val MIGRATION_30_31: Migration get() = AppDatabaseMigrations.MIGRATION_30_31
        val MIGRATION_31_32: Migration get() = AppDatabaseMigrations.MIGRATION_31_32

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ai_assistant_database"
                )
                .addMigrations(
                    *AppDatabaseMigrations.LEGACY_REPAIR_MIGRATIONS
                )
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
