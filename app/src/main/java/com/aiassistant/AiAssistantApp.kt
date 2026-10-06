package com.aiassistant

import android.app.Application
import android.util.Log
import com.aiassistant.data.local.AppDatabase
import com.aiassistant.data.repository.AiRepository
import com.aiassistant.utils.BackupManager
import com.aiassistant.utils.CryptoManager
import com.aiassistant.utils.PersonalizationManager
import com.aiassistant.utils.TavilySearchManager
import com.aiassistant.utils.ThemePreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AiAssistantApp : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val startupBackup = com.aiassistant.utils.StartupBackupScheduler(applicationScope) {
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                BackupManager.autoBackup(this@AiAssistantApp)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("AiAssistantApp", "Auto backup failed", e)
            }
        }
    }

    fun onHomeDataLoaded() {
        if (isDatabaseInitialized) startupBackup.onHomeLoaded()
    }

    lateinit var repository: AiRepository
        private set

    lateinit var roleplayRepository: com.aiassistant.data.repository.RoleplayRepository
        private set

    lateinit var database: AppDatabase
        private set

    lateinit var cryptoManager: CryptoManager
        private set

    lateinit var personalizationManager: PersonalizationManager
        private set

    lateinit var tavilySearchManager: TavilySearchManager
        private set

    lateinit var echoToolHub: com.aiassistant.tools.EchoToolHub
        private set

    lateinit var themePreferenceManager: ThemePreferenceManager
        private set

    var isDatabaseInitialized = false
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        val httpAccessSettings = com.aiassistant.utils.HttpAccessSettings(this)
        com.aiassistant.data.remote.HttpAccessPolicy.initialize { httpAccessSettings.getRules() }
        com.aiassistant.utils.ReasoningCapabilityPreferences.initialize(this)

        cryptoManager = CryptoManager(this)
        personalizationManager = PersonalizationManager(this)
        tavilySearchManager = TavilySearchManager(this, cryptoManager)
        echoToolHub = com.aiassistant.tools.EchoToolHub(this, cryptoManager, tavilySearchManager)
        themePreferenceManager = ThemePreferenceManager(this)

        // 自动备份等待首页首次查询完成；数据库构造阶段不启动全库扫描。

        // 初始化数据库
        try {
            database = AppDatabase.getDatabase(this)
            repository = AiRepository(
                folderDao = database.folderDao(),
                apiConfigDao = database.apiConfigDao(),
                conversationDao = database.conversationDao(),
                messageDao = database.messageDao(),
                usageStatDao = database.usageStatDao(),
                environmentVariableDao = database.environmentVariableDao(),
                promptTemplateDao = database.promptTemplateDao(),
                memoryDao = database.memoryDao(),
                conversationBranchDao = database.conversationBranchDao(),
                selectedModelDao = database.selectedModelDao(),
                cryptoManager = cryptoManager,
                personalizationManager = personalizationManager,
                tavilySearchManager = tavilySearchManager,
                echoToolHub = echoToolHub,
                worldBookDao = database.worldBookDao(),
                timelineNodeDao = database.timelineNodeDao()
            )
            roleplayRepository = com.aiassistant.data.repository.RoleplayRepository(
                characterProfileDao = database.characterProfileDao(),
                roleplayScenarioDao = database.roleplayScenarioDao(),
                roleplaySessionDao = database.roleplaySessionDao(),
                roleplayMemoryDao = database.roleplayMemoryDao(),
                characterTagDao = database.characterTagDao(),
                conversationDao = database.conversationDao(),
                messageDao = database.messageDao(),
                worldBookDao = database.worldBookDao(),
                memoryDao = database.memoryDao()
            )
            isDatabaseInitialized = true
        } catch (e: Exception) {
            Log.e("AiAssistantApp", "Database initialization failed", e)
            tryRestoreBackup()
        }
    }

    private fun tryRestoreBackup() {
        try {
            val backups = BackupManager.getBackupList(this)
            if (backups.isNotEmpty()) {
                val latestBackup = backups.first()
                if (BackupManager.restoreBackup(this, latestBackup.filePath)) {
                    database = AppDatabase.getDatabase(this)
                    repository = AiRepository(
                        folderDao = database.folderDao(),
                        apiConfigDao = database.apiConfigDao(),
                        conversationDao = database.conversationDao(),
                        messageDao = database.messageDao(),
                        usageStatDao = database.usageStatDao(),
                        environmentVariableDao = database.environmentVariableDao(),
                        promptTemplateDao = database.promptTemplateDao(),
                        memoryDao = database.memoryDao(),
                        conversationBranchDao = database.conversationBranchDao(),
                        selectedModelDao = database.selectedModelDao(),
                        cryptoManager = cryptoManager,
                        personalizationManager = personalizationManager,
                        tavilySearchManager = tavilySearchManager,
                        echoToolHub = echoToolHub,
                        worldBookDao = database.worldBookDao(),
                        timelineNodeDao = database.timelineNodeDao()
                    )
                    roleplayRepository = com.aiassistant.data.repository.RoleplayRepository(
                        characterProfileDao = database.characterProfileDao(),
                        roleplayScenarioDao = database.roleplayScenarioDao(),
                        roleplaySessionDao = database.roleplaySessionDao(),
                        roleplayMemoryDao = database.roleplayMemoryDao(),
                        characterTagDao = database.characterTagDao(),
                        conversationDao = database.conversationDao(),
                        messageDao = database.messageDao(),
                        worldBookDao = database.worldBookDao(),
                        memoryDao = database.memoryDao()
                    )
                    isDatabaseInitialized = true
                }
            }
        } catch (e: Exception) {
            Log.e("AiAssistantApp", "Backup restore failed", e)
        }

        // 最终兜底：如果依然未初始化成功，确保 repository 被安全创建，杜绝闪退
        if (!isDatabaseInitialized) {
            try {
                database = AppDatabase.getDatabase(this)
                repository = AiRepository(
                    folderDao = database.folderDao(),
                    apiConfigDao = database.apiConfigDao(),
                    conversationDao = database.conversationDao(),
                    messageDao = database.messageDao(),
                    usageStatDao = database.usageStatDao(),
                    environmentVariableDao = database.environmentVariableDao(),
                    promptTemplateDao = database.promptTemplateDao(),
                    memoryDao = database.memoryDao(),
                    conversationBranchDao = database.conversationBranchDao(),
                    selectedModelDao = database.selectedModelDao(),
                    cryptoManager = cryptoManager,
                    personalizationManager = personalizationManager,
                    tavilySearchManager = tavilySearchManager,
                    echoToolHub = echoToolHub,
                    worldBookDao = database.worldBookDao(),
                    timelineNodeDao = database.timelineNodeDao()
                )
                roleplayRepository = com.aiassistant.data.repository.RoleplayRepository(
                    characterProfileDao = database.characterProfileDao(),
                    roleplayScenarioDao = database.roleplayScenarioDao(),
                    roleplaySessionDao = database.roleplaySessionDao(),
                    roleplayMemoryDao = database.roleplayMemoryDao(),
                    characterTagDao = database.characterTagDao(),
                    conversationDao = database.conversationDao(),
                    messageDao = database.messageDao(),
                    worldBookDao = database.worldBookDao(),
                    memoryDao = database.memoryDao()
                )
                isDatabaseInitialized = true
            } catch (fatalEx: Exception) {
                Log.e("AiAssistantApp", "Fatal fallback init failed", fatalEx)
            }
        }
    }

    companion object {
        lateinit var instance: AiAssistantApp
            private set
    }
}
