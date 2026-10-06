package com.aiassistant.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiassistant.AiAssistantApp
import com.aiassistant.domain.model.ApiConfig
import com.aiassistant.domain.model.Conversation
import com.aiassistant.domain.model.Folder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {
    private val repository = AiAssistantApp.instance.repository
    private companion object {
        const val PINNED_FOLDER_ID = -2L
        const val UNFILED_FOLDER_ID = -1L
    }

    private val _selectedFolderId = MutableStateFlow<Long?>(null)
    val selectedFolderId: StateFlow<Long?> = _selectedFolderId.asStateFlow()

    private val _folders = MutableStateFlow<List<Folder>>(emptyList())
    val folders: StateFlow<List<Folder>> = _folders.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val conversations: StateFlow<List<Conversation>> = _selectedFolderId.flatMapLatest { folderId ->
        when (folderId) {
            null -> repository.getAllConversations()
            PINNED_FOLDER_ID -> repository.getPinnedConversations()
            UNFILED_FOLDER_ID -> repository.getUnfiledConversations()
            else -> repository.getConversationsByFolder(folderId)
        }
    }.onEach { AiAssistantApp.instance.onHomeDataLoaded() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _apiConfigs = MutableStateFlow<List<ApiConfig>>(emptyList())
    val apiConfigs: StateFlow<List<ApiConfig>> = _apiConfigs.asStateFlow()

    private val _selectedConfig = MutableStateFlow<ApiConfig?>(null)
    val selectedConfig: StateFlow<ApiConfig?> = _selectedConfig.asStateFlow()

    private val _showNewChatDialog = MutableStateFlow(false)
    val showNewChatDialog: StateFlow<Boolean> = _showNewChatDialog.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _visibleModelOptions = MutableStateFlow<List<com.aiassistant.domain.model.ChatModelOption>>(emptyList())
    val visibleModelOptions: StateFlow<List<com.aiassistant.domain.model.ChatModelOption>> = _visibleModelOptions.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            repository.getAllFolders().collect { folderList ->
                _folders.value = folderList
            }
        }

        viewModelScope.launch {
            repository.getAllApiConfigs().collect { configs ->
                _apiConfigs.value = configs
                val enabledConfigs = configs.filter { it.isEnabled }
                val defaultConfig = enabledConfigs.firstOrNull { it.isDefault } ?: enabledConfigs.firstOrNull()
                val currentConfig = _selectedConfig.value
                _selectedConfig.value = when {
                    currentConfig == null -> defaultConfig
                    enabledConfigs.none { it.id == currentConfig.id } -> defaultConfig
                    defaultConfig?.isDefault == true && currentConfig.id != defaultConfig.id -> defaultConfig
                    else -> enabledConfigs.firstOrNull { it.id == currentConfig.id } ?: defaultConfig
                }
                _visibleModelOptions.value = repository.getAllVisibleChatModelOptions()
            }
        }
    }

    fun selectFolder(folderId: Long?) {
        _selectedFolderId.value = folderId
    }

    fun selectConfig(config: ApiConfig) {
        _selectedConfig.value = config
    }

    fun showNewChatDialog() {
        _showNewChatDialog.value = true
    }

    fun hideNewChatDialog() {
        _showNewChatDialog.value = false
    }

    fun createNewConversation(
        title: String,
        systemPrompt: String?,
        folderId: Long? = null,
        apiConfig: ApiConfig? = null,
        modelName: String? = null,
        isPrivate: Boolean = false,
        onSuccess: (Long) -> Unit
    ) {
        viewModelScope.launch {
            val config = apiConfig
                ?: repository.getDefaultApiConfig()
                ?: _selectedConfig.value
                ?: _apiConfigs.value.firstOrNull()
                ?: return@launch
            _isLoading.value = true
            try {
                val targetModelName = modelName?.trim()?.ifBlank { null }
                    ?: repository.resolveDefaultModelName(config)
                val id = repository.createConversation(
                    title = title,
                    apiConfigId = config.id,
                    modelName = targetModelName,
                    folderId = normalizeFolderIdForCreation(folderId ?: _selectedFolderId.value),
                    systemPrompt = systemPrompt,
                    tags = if (isPrivate) "private" else null
                )
                _showNewChatDialog.value = false
                onSuccess(id)
            } catch (e: Exception) {
                // 处理错误
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createDefaultConversation(onSuccess: (Long) -> Unit) {
        createNewConversation(
            title = "",
            systemPrompt = null,
            folderId = normalizeFolderIdForCreation(_selectedFolderId.value),
            onSuccess = onSuccess
        )
    }

    fun createConversationInNewFolder(
        title: String,
        systemPrompt: String?,
        folderName: String,
        apiConfig: ApiConfig?,
        modelName: String? = null,
        isPrivate: Boolean = false,
        onSuccess: (Long) -> Unit
    ) {
        val trimmedFolderName = folderName.trim()
        if (trimmedFolderName.isBlank()) return

        viewModelScope.launch {
            val config = apiConfig
                ?: repository.getDefaultApiConfig()
                ?: _selectedConfig.value
                ?: _apiConfigs.value.firstOrNull()
                ?: return@launch
            _isLoading.value = true
            try {
                val folderId = repository.createFolder(trimmedFolderName)
                val targetModelName = modelName?.trim()?.ifBlank { null }
                    ?: repository.resolveDefaultModelName(config)
                val conversationId = repository.createConversation(
                    title = title,
                    apiConfigId = config.id,
                    modelName = targetModelName,
                    folderId = folderId,
                    systemPrompt = systemPrompt,
                    tags = if (isPrivate) "private" else null
                )
                _showNewChatDialog.value = false
                _selectedFolderId.value = folderId
                onSuccess(conversationId)
            } catch (e: Exception) {
                // 处理错误
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun normalizeFolderIdForCreation(folderId: Long?): Long? {
        return folderId?.takeUnless { it == PINNED_FOLDER_ID || it == UNFILED_FOLDER_ID }
    }

    fun deleteConversation(conversation: Conversation) {
        viewModelScope.launch {
            repository.deleteConversation(conversation.id)
        }
    }

    fun deleteConversations(conversationIds: Collection<Long>) {
        if (conversationIds.isEmpty()) return

        viewModelScope.launch {
            conversationIds.forEach { conversationId ->
                repository.deleteConversation(conversationId)
            }
        }
    }

    fun togglePin(conversation: Conversation) {
        viewModelScope.launch {
            repository.setPinned(conversation.id, !conversation.isPinned)
        }
    }

    fun setPinned(conversationIds: Collection<Long>, pinned: Boolean) {
        if (conversationIds.isEmpty()) return

        viewModelScope.launch {
            conversationIds.forEach { conversationId ->
                repository.setPinned(conversationId, pinned)
            }
        }
    }

    fun moveToFolder(conversationId: Long, folderId: Long?) {
        viewModelScope.launch {
            repository.moveToFolder(conversationId, folderId)
        }
    }

    fun moveConversationsToFolder(conversationIds: Collection<Long>, folderId: Long?) {
        if (conversationIds.isEmpty()) return

        viewModelScope.launch {
            conversationIds.forEach { conversationId ->
                repository.moveToFolder(conversationId, folderId)
            }
        }
    }

    fun hideConversations(conversationIds: Collection<Long>) {
        if (conversationIds.isEmpty()) return

        viewModelScope.launch {
            repository.setConversationsHidden(conversationIds, true)
        }
    }

    fun unhideConversation(conversationId: Long) {
        viewModelScope.launch {
            repository.setConversationHidden(conversationId, false)
        }
    }

    fun createFolder(name: String, icon: String = "folder", color: Int = 0) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return

        viewModelScope.launch {
            repository.createFolder(trimmedName, icon = icon, color = color)
        }
    }

    fun updateFolder(folder: Folder, name: String, icon: String = folder.icon, color: Int = folder.color) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return

        viewModelScope.launch {
            repository.updateFolder(folder.copy(name = trimmedName, icon = icon, color = color))
        }
    }

    fun renameConversation(conversationId: Long, newTitle: String) {
        viewModelScope.launch {
            val conversation = repository.getConversationById(conversationId)
            conversation?.let {
                val updated = it.copy(title = newTitle)
                AiAssistantApp.instance.database.conversationDao().updateConversation(updated)
            }
        }
    }

    fun setDefaultConfig(config: ApiConfig) {
        viewModelScope.launch {
            repository.setDefaultConfig(config.id)
        }
    }

    fun duplicateConversation(conversationId: Long, onSuccess: ((Long) -> Unit)? = null) {
        viewModelScope.launch {
            val newId = repository.duplicateConversation(conversationId)
            if (newId > 0) {
                onSuccess?.invoke(newId)
            }
        }
    }
}
