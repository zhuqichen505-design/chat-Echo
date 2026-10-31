@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.aiassistant.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import com.aiassistant.domain.model.ChatModelOption
import com.aiassistant.ui.components.ImageCropEditDialog
import com.aiassistant.ui.components.CropShapeMode
import com.aiassistant.ui.components.ExpandableText
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiassistant.AiAssistantApp
import com.aiassistant.BuildConfig
import com.aiassistant.R
import com.aiassistant.data.repository.AiRepository
import com.aiassistant.domain.model.ApiConfig
import com.aiassistant.domain.model.NamedApiKey
import com.aiassistant.domain.model.Conversation
import com.aiassistant.domain.model.EnvironmentVariable
import com.aiassistant.domain.model.MemoryItem
import com.aiassistant.domain.model.WorldBook
import com.aiassistant.domain.model.WorldBookEntry
import com.aiassistant.domain.model.ModelCapabilityEngine
import com.aiassistant.domain.model.ModelCustomSettings
import com.aiassistant.domain.model.PromptTemplate
import com.aiassistant.ui.components.EchoGlassCard
import com.aiassistant.ui.components.EchoStreamingIndicator
import com.aiassistant.ui.components.EchoModelMark
import com.aiassistant.ui.components.EchoModelMarkState
import com.aiassistant.ui.components.modelMarkColorFor
import com.aiassistant.ui.components.echoSwitchColors
import com.aiassistant.ui.components.EchoGlassDialog
import com.aiassistant.ui.components.EchoGlassDropdownMenu
import com.aiassistant.ui.components.readableTextColorFor
import com.aiassistant.ui.components.rememberReadableBackdropColors
import com.aiassistant.ui.components.echoFilterChipBorder
import com.aiassistant.ui.components.echoFilterChipColors
import com.aiassistant.ui.components.echoFilterChipElevation
import com.aiassistant.ui.components.echoGlassPalette
import com.aiassistant.ui.components.echoSegmentedButtonBorder
import com.aiassistant.ui.components.echoSegmentedButtonColors
import com.aiassistant.ui.components.echoHazePanel
import com.aiassistant.ui.components.echoHazeSource
import com.aiassistant.ui.components.echoShapeClick
import com.aiassistant.ui.components.rememberEchoHazeState
import com.aiassistant.ui.components.rememberSmoothReorderState
import com.aiassistant.ui.components.reorderItem
import com.aiassistant.ui.components.reorderDragHandle
import com.aiassistant.ui.theme.EchoTokens
import com.aiassistant.utils.AvatarManager
import com.aiassistant.utils.BackgroundImageManager
import com.aiassistant.utils.BackupManager
import com.aiassistant.utils.HiddenConversationLock
import com.aiassistant.utils.TavilySearchSettings
import com.aiassistant.utils.AppThemeMode
import com.aiassistant.utils.EchoAppearancePreset
import com.aiassistant.utils.EchoHomeLayoutStyle
import com.aiassistant.utils.EchoStoryReadingStyle
import com.aiassistant.utils.EchoStoryFont
import com.aiassistant.utils.EchoStreamingAnimationStyle
import com.aiassistant.utils.EchoModelMarkStyle
import com.aiassistant.utils.PersonalizationSettings
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import com.aiassistant.tools.search.SearchEngineType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.aiassistant.tools.cloud.OpenMeteoWeatherEngine
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AppearanceTab(
    hazeState: dev.chrisbanes.haze.HazeState,
    modifier: Modifier = Modifier,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onAppearancePresetChange: (String) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(16.dp)
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val manager = AiAssistantApp.instance.personalizationManager
    val appearanceScope = rememberCoroutineScope()

    var avatarBase64 by remember { mutableStateOf(AvatarManager.getAvatar(context)) }
    var avatarHistoryRevision by remember { mutableIntStateOf(0) }
    var avatarHistory by remember(context) { mutableStateOf(emptyList<AvatarManager.AvatarHistoryEntry>()) }
    LaunchedEffect(context, avatarHistoryRevision, avatarBase64) {
        avatarHistory = withContext(Dispatchers.IO) { AvatarManager.listAvatarHistory(context) }
    }

    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCropTarget by remember { mutableStateOf("avatar") } // "avatar", "home_bg", "chat_bg"

    val imagePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            pendingCropTarget = "avatar"
            pendingCropUri = it
        }
    }

    var settings by remember { mutableStateOf(manager.getSettings()) }
    var modelMarkPreviewState by remember { mutableStateOf(EchoModelMarkState.Thinking) }
    var modelMarkPreviewCompletionEvent by remember { mutableLongStateOf(0L) }
    var chatFontSize by remember(settings) { mutableIntStateOf(settings.chatFontSize) }
    var fontSizeScale by remember(settings) { mutableFloatStateOf(settings.fontSizeScale) }
    var savedMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    var backgroundRevision by remember { mutableIntStateOf(0) }
    var avatarPendingDelete by remember { mutableStateOf<AvatarManager.AvatarHistoryEntry?>(null) }
    var backgroundPendingDelete by remember { mutableStateOf<BackgroundImageManager.BackgroundHistoryEntry?>(null) }

    fun performSave() {
        val saved = manager.saveSettings(
            manager.getSettings().copy(
                chatFontSize = chatFontSize,
                fontSizeScale = fontSizeScale
            )
        )
        settings = manager.getSettings()
        savedMessage = if (saved) "已保存界面与外观设定" else "外观设定保存失败，请重试"
    }

    fun saveVisualPreference(message: String, update: (PersonalizationSettings) -> PersonalizationSettings) {
        val current = manager.getSettings()
        val updated = update(current)
        if (manager.saveSettings(updated)) {
            settings = manager.getSettings()
            if (updated.appearancePreset != current.appearancePreset) {
                val backgroundSaved = BackgroundImageManager.applyThemeBackground(context)
                backgroundRevision++
                if (!backgroundSaved) {
                    savedMessage = "主题已更新，背景切换失败；原图片仍保留，请重试"
                    return
                }
            }
            onAppearancePresetChange(settings.appearancePreset)
            savedMessage = message
        } else {
            savedMessage = "外观设定保存失败，请重试"
        }
    }

    LaunchedEffect(savedMessage) {
        savedMessage?.let {
            snackbarHostState.showSnackbar(it)
            savedMessage = null
        }
    }

    var backgroundHistory by remember(context) { mutableStateOf(emptyList<BackgroundImageManager.BackgroundHistoryEntry>()) }
    LaunchedEffect(context, backgroundRevision) {
        backgroundHistory = withContext(Dispatchers.IO) { BackgroundImageManager.listBackgroundHistory(context) }
    }
    val hasHomeBackground = remember(backgroundRevision) {
        BackgroundImageManager.hasHomeBackground(context)
    }
    val hasChatBackground = remember(backgroundRevision) {
        BackgroundImageManager.hasChatBackground(context)
    }
    val homeBackgroundPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            pendingCropTarget = "home_bg"
            pendingCropUri = it
        }
    }
    val chatBackgroundPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            pendingCropTarget = "chat_bg"
            pendingCropUri = it
        }
    }

    val glass = echoGlassPalette()

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(EchoTokens.Spacing.cardGap)
        ) {
        // 0. 用户头像设置
        item {
            SettingsGlassCard(hazeState = hazeState) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("用户头像", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "自定义用户气泡头像，支持选择相册图片",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (avatarBase64 != null) {
                            val bitmap = remember(avatarBase64) { AvatarManager.base64ToBitmap(avatarBase64) }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "用户头像",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        } else {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("上传新头像")
                        }

                        if (avatarBase64 != null) {
                            OutlinedButton(
                                onClick = {
                                    AvatarManager.deleteAvatar(context)
                                    avatarBase64 = null
                                    savedMessage = "已恢复默认头像"
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("恢复默认头像")
                            }
                        }
                    }
                }
            }
        }

        if (avatarHistory.isNotEmpty()) {
            item {
                SettingsGlassCard(hazeState = hazeState) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("头像历史", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                "用户、模型与角色头像统一保留，也可以设为壁纸。横向滑动查看全部 ${avatarHistory.size} 张。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(avatarHistory, key = { it.id }) { entry ->
                            var bitmap by remember(context, entry.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
                            LaunchedEffect(context, entry.id) {
                                bitmap = withContext(Dispatchers.IO) { AvatarManager.getAvatarHistoryBitmap(context, entry) }
                            }
                            val loadedBitmap = bitmap
                            if (loadedBitmap != null) {
                                var showActions by remember { mutableStateOf(false) }
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                            .clickable { showActions = true }
                                    ) {
                                        Image(
                                            bitmap = loadedBitmap.asImageBitmap(),
                                            contentDescription = "历史头像",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    TextButton(onClick = { showActions = true }, contentPadding = PaddingValues(0.dp)) {
                                        Text("预览 / 应用", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                if (showActions) {
                                    AlertDialog(onDismissRequest = { showActions = false },
                                        title = { Text("历史头像") },
                                        text = {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Image(loadedBitmap.asImageBitmap(), "历史头像预览", modifier = Modifier.fillMaxWidth().height(180.dp), contentScale = ContentScale.Fit)
                                                TextButton(onClick = {
                                                    showActions = false
                                                    appearanceScope.launch {
                                                        val restored = withContext(Dispatchers.IO) {
                                                            if (AvatarManager.restoreAvatarHistory(context, entry)) AvatarManager.getAvatar(context) else null
                                                        }
                                                        if (restored != null) { avatarBase64 = restored; avatarHistoryRevision++ }
                                                        savedMessage = if (restored != null) "已恢复历史头像" else "头像恢复失败，请重试"
                                                    }
                                                }) { Text("设为用户头像") }
                                                TextButton(onClick = {
                                                    showActions = false
                                                    appearanceScope.launch {
                                                        val saved = withContext(Dispatchers.IO) { BackgroundImageManager.saveHomeBackgroundBitmap(context, loadedBitmap) }
                                                        backgroundRevision++
                                                        savedMessage = if (saved) "已设置首页壁纸" else "壁纸保存失败，请重试"
                                                    }
                                                }) { Text("设为首页壁纸") }
                                                TextButton(onClick = {
                                                    showActions = false
                                                    appearanceScope.launch {
                                                        val saved = withContext(Dispatchers.IO) { BackgroundImageManager.saveChatBackgroundBitmap(context, loadedBitmap) }
                                                        backgroundRevision++
                                                        savedMessage = if (saved) "已设置对话壁纸" else "壁纸保存失败，请重试"
                                                    }
                                                }) { Text("设为对话壁纸") }
                                                TextButton(onClick = {
                                                    showActions = false
                                                    avatarPendingDelete = entry
                                                }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                                                    Icon(Icons.Default.DeleteOutline, null, Modifier.size(18.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("删除此历史头像")
                                                }
                                            }
                                        }, confirmButton = { TextButton(onClick = { showActions = false }) { Text("关闭") } })
                                }
                            }
                        }
                    }
                }
            }
        }

        // 1. 应用主题模式
        item {
            ThemeModeCard(
                hazeState = hazeState,
                selected = themeMode,
                onSelected = { mode ->
                    if (mode != themeMode) {
                        onThemeModeChange(mode)
                        val saved = BackgroundImageManager.applyThemeBackground(context)
                        backgroundRevision++
                        savedMessage = if (saved) "已切换${mode.label}，背景已同步为主题底色" else "背景切换失败，原图片仍保留，请重试"
                    }
                }
            )
        }

        item {
            SettingsGlassCard(hazeState = hazeState) {
                Text("首页布局", style = MaterialTheme.typography.titleMedium)
                Text("两种布局均提供会话、创作、统计和设置入口。配色、创作阅读与流式动画可分别选择。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeLayoutChoice(
                        style = EchoHomeLayoutStyle.FOCUS,
                        label = "聚焦对话",
                        selected = settings.homeLayoutStyle == EchoHomeLayoutStyle.FOCUS,
                        compact = settings.homeCompactList,
                        showRecentStory = settings.homeShowRecentStory,
                        onSelected = { saveVisualPreference("首页已切换为聚焦对话") { it.copy(homeLayoutStyle = EchoHomeLayoutStyle.FOCUS) } },
                        modifier = Modifier.weight(1f)
                    )
                    HomeLayoutChoice(
                        style = EchoHomeLayoutStyle.WORKBENCH,
                        label = "侧栏工作台",
                        selected = settings.homeLayoutStyle == EchoHomeLayoutStyle.WORKBENCH,
                        compact = settings.homeCompactList,
                        showRecentStory = settings.homeShowRecentStory,
                        onSelected = { saveVisualPreference("首页已切换为侧栏工作台") { it.copy(homeLayoutStyle = EchoHomeLayoutStyle.WORKBENCH) } },
                        modifier = Modifier.weight(1f)
                    )
                }
                HomeLayoutSwitch(
                    title = "列表紧凑模式", description = "减少两种布局中会话列表的留白。",
                    checked = settings.homeCompactList,
                    onCheckedChange = { value -> saveVisualPreference(if (value) "已开启列表紧凑模式" else "已恢复标准列表间距") {
                        it.copy(homeCompactList = value)
                    } }
                )
                HomeLayoutSwitch(
                    title = "显示继续故事", description = "在两种布局中显示最近故事的继续入口。",
                    checked = settings.homeShowRecentStory,
                    onCheckedChange = { value -> saveVisualPreference(if (value) "首页将显示继续故事" else "已隐藏首页继续故事") {
                        it.copy(homeShowRecentStory = value)
                    } }
                )
            }
        }

        item {
            SettingsGlassCard(hazeState = hazeState) {
                Text("主题预设", style = MaterialTheme.typography.titleMedium)
                Text("主题同步首页、对话页、设置、弹窗和编辑控件的颜色、材质、形状与排版。用过的图片保留在背景历史中，可随时恢复。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.fillMaxWidth().selectableGroup()) {
                    val presets = listOf(
                        AppearancePresetOption(EchoAppearancePreset.EXISTING, "经典", "保留当前 Echo 配色", listOf(0xFF3B82F6, 0xFF38BDF8, 0xFFFB7185)),
                        AppearancePresetOption(EchoAppearancePreset.CLEAR_SKY, "晴空", "蓝白层次，清晰舒展", listOf(0xFF2C73B7, 0xFF83D3F0, 0xFFEDF4FC)),
                        AppearancePresetOption(EchoAppearancePreset.NIGHT_VOYAGE, "夜航", "墨绿与暖金，安静阅读", listOf(0xFF172B29, 0xFFD6BB83, 0xFFA5C9AD)),
                        AppearancePresetOption(EchoAppearancePreset.INDEX, "索引", "纸色与赭红，温和分明", listOf(0xFFAF4D34, 0xFFF0E7DF, 0xFF706354)),
                        AppearancePresetOption(EchoAppearancePreset.MONO, "墨白", "黑白对照、细线轮廓、平面材质", listOf(0xFF242424, 0xFFE9E9E9, 0xFFFFFFFF)),
                        AppearancePresetOption(EchoAppearancePreset.DECKLE, "毛边文库", "暖纸纤维、砖红铅墨、书页排版", listOf(0xFFF2E8D3, 0xFF8B3E2D, 0xFFEADAC0)),
                        AppearancePresetOption(EchoAppearancePreset.COTTON, "棉纸诗笺", "冷白棉纸、松绿蓝墨、舒展留白", listOf(0xFFF4F5F1, 0xFF375D70, 0xFFDDE6E6)),
                        AppearancePresetOption(EchoAppearancePreset.FOLIO, "拾页", "书页纹理、章节排版、页边批注", listOf(0xFFF3EDDF, 0xFF6F3345, 0xFFB9A78A)),
                        AppearancePresetOption(EchoAppearancePreset.SONAR, "声呐", "深空网格、切角面板、荧光信号", listOf(0xFF071411, 0xFF63F5D2, 0xFF82D7E8)),
                        AppearancePresetOption(EchoAppearancePreset.BAUHAUS, "构型", "几何色块、粗描边、硬质投影", listOf(0xFFF2EDDF, 0xFFC93F32, 0xFF1668B4))
                    )
                    presets.forEach { preset ->
                        val selected = settings.appearancePreset == preset.id
                        Row(
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .selectable(selected = selected, role = Role.RadioButton, onClick = {
                                    saveVisualPreference("已应用「${preset.label}」主题") { it.copy(appearancePreset = preset.id) }
                                }).padding(horizontal = 4.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selected, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(preset.label, modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        preset.colors.forEach { color ->
                                            Box(Modifier.size(16.dp).clip(CircleShape).background(Color(color))
                                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
                                        }
                                    }
                                }
                                Text(preset.description, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        item {
            SettingsGlassCard(hazeState = hazeState) {
                Text("创作阅读页", style = MaterialTheme.typography.titleMedium)
                Text("独立选择故事正文排版。分栏上下文在宽屏并排显示，窄屏折叠为资料面板。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(EchoStoryReadingStyle.IMMERSIVE to "沉浸叙事", EchoStoryReadingStyle.PLAIN to "简洁正文",
                        EchoStoryReadingStyle.SPLIT to "分栏上下文").forEach { (value, label) ->
                        FilterChip(selected = settings.storyReadingStyle == value,
                            onClick = { saveVisualPreference("创作阅读页已切换为$label") { it.copy(storyReadingStyle = value) } },
                            label = { Text(label) }, colors = echoFilterChipColors(),
                            border = echoFilterChipBorder(settings.storyReadingStyle == value))
                    }
                }
                HomeLayoutSwitch("阅读排版也用于普通对话", "普通对话可使用沉浸、简洁或分栏布局。", settings.applyReadingStyleToChats) { enabled ->
                    saveVisualPreference("普通对话阅读排版已更新") { it.copy(applyReadingStyleToChats = enabled) }
                }
                Spacer(Modifier.height(10.dp))
                Text("创作正文字体", style = MaterialTheme.typography.titleSmall)
                Text("可单独选择是否应用于普通对话；设置与操作按钮保持界面字体。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        EchoStoryFont.LITERARY to "沉浸衬线",
                        EchoStoryFont.CLEAR to "清晰无衬线",
                        EchoStoryFont.TYPEWRITER to "稿纸等宽"
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = settings.storyFont == value,
                            onClick = { saveVisualPreference("创作正文字体已切换为$label") { it.copy(storyFont = value) } },
                            label = { Text(label, softWrap = false) },
                            colors = echoFilterChipColors(),
                            border = echoFilterChipBorder(settings.storyFont == value)
                        )
                    }
                }
                HomeLayoutSwitch("正文字体也用于普通对话", "同时用于消息正文与展开的思考内容。", settings.applyReadingFontToChats) { enabled ->
                    saveVisualPreference("普通对话字体已更新") { it.copy(applyReadingFontToChats = enabled) }
                }
                HomeLayoutSwitch("显示思考气泡", "控制普通对话与创作消息中的思考状态与展开内容，不影响模型思考和请求参数。", settings.showThinkingBubbles) { enabled ->
                    saveVisualPreference(if (enabled) "思考气泡已显示" else "思考气泡已隐藏") { it.copy(showThinkingBubbles = enabled) }
                }
            }
        }

        item {
            SettingsGlassCard(hazeState = hazeState) {
                Text("模型标识", style = MaterialTheme.typography.titleMedium)
                HomeLayoutSwitch("显示模型头像", "用于普通对话与创作。关闭后可选极简 Bot、线条机器人或几何标识。", settings.showModelAvatar) { enabled ->
                    saveVisualPreference(if (enabled) "模型头像已显示" else "已切换为模型标识") { it.copy(showModelAvatar = enabled) }
                }
                if (!settings.showModelAvatar) {
                    Text("连接发出信号弧、思考浮起泡泡、待机冒出 Z；回复轻点，完成闪星收势。主动暂停显示双竖条，出错显示叹号。遵循系统移除动画设置。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        EchoModelMark(
                            color = modelMarkColorFor(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.surface),
                            style = settings.modelMarkStyle,
                            state = modelMarkPreviewState,
                            completionEvent = modelMarkPreviewCompletionEvent,
                            idleEffects = true,
                            modifier = Modifier.size(64.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)),
                            description = "当前模型标识预览"
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("实时预览", style = MaterialTheme.typography.titleSmall)
                            Text("选择下方状态查看动作；完成收势后回到待机，再次点击完成可重播。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text("预览状态", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            EchoModelMarkState.Neutral to "待机",
                            EchoModelMarkState.Connecting to "连接",
                            EchoModelMarkState.Thinking to "思考",
                            EchoModelMarkState.Replying to "回复",
                            EchoModelMarkState.Completed to "完成",
                            EchoModelMarkState.Paused to "主动暂停",
                            EchoModelMarkState.Failed to "连接 / 回复出错"
                        ).forEach { (value, label) ->
                            FilterChip(selected = modelMarkPreviewState == value,
                                onClick = {
                                    modelMarkPreviewState = value
                                    if (value == EchoModelMarkState.Completed) modelMarkPreviewCompletionEvent++
                                }, label = { Text(label, softWrap = false) },
                                colors = echoFilterChipColors(),
                                border = echoFilterChipBorder(modelMarkPreviewState == value)
                            )
                        }
                    }
                    listOf(
                        "极简 Bot" to listOf(EchoModelMarkStyle.BOT_ORB to "圆圆", EchoModelMarkStyle.BOT_TILE to "方方",
                            EchoModelMarkStyle.BOT_WIDE to "扁扁"),
                        "线条机器人" to listOf(EchoModelMarkStyle.BOT_ROUND to "圆豆", EchoModelMarkStyle.BOT_SQUARE to "方糖",
                            EchoModelMarkStyle.BOT_CAPSULE to "团子", EchoModelMarkStyle.BOT_PIXEL to "小方"),
                        "几何标识" to listOf(EchoModelMarkStyle.CONCENTRIC to "同心圆", EchoModelMarkStyle.ORBIT to "轨道",
                            EchoModelMarkStyle.PRISM to "棱镜", EchoModelMarkStyle.WAVEFORM to "声纹")
                    ).forEach { (group, options) ->
                        Text(group, style = MaterialTheme.typography.labelLarge)
                        FlowRow(modifier = Modifier.selectableGroup(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            options.forEach { (value, label) ->
                                FilterChip(selected = settings.modelMarkStyle == value,
                                    onClick = { saveVisualPreference("模型标识已切换为$label") { it.copy(modelMarkStyle = value) } },
                                    label = { Text(label, softWrap = false) },
                                    leadingIcon = {
                                        EchoModelMark(color = if (settings.modelMarkStyle == value) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.primary,
                                            style = value, state = EchoModelMarkState.Neutral,
                                            modifier = Modifier.size(if (value in EchoModelMarkStyle.expressiveBotStyles) 32.dp else 26.dp))
                                    }, colors = echoFilterChipColors(), border = echoFilterChipBorder(settings.modelMarkStyle == value))
                            }
                        }
                    }
                }
            }
        }

        item {
            SettingsGlassCard(hazeState = hazeState) {
                Text("流式动画", style = MaterialTheme.typography.titleMedium)
                Text("回复生成时的轻量提示，遵循系统减弱动画设置。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(EchoStreamingAnimationStyle.THIN_CURSOR to "细光标", EchoStreamingAnimationStyle.SOUND_WAVE to "微声波",
                        EchoStreamingAnimationStyle.INK_RING to "墨环").forEach { (value, label) ->
                        FilterChip(selected = settings.streamingAnimationStyle == value,
                            onClick = { saveVisualPreference("流式动画已切换为$label") { it.copy(streamingAnimationStyle = value) } },
                            label = { Text(label) },
                            leadingIcon = { EchoStreamingIndicator(color = MaterialTheme.colorScheme.primary, style = value, compact = true) },
                            colors = echoFilterChipColors(),
                            border = echoFilterChipBorder(settings.streamingAnimationStyle == value))
                    }
                }
            }
        }

        // 2. 界面与对话字体大小设置
        item {
            SettingsGlassCard(hazeState = hazeState) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.FormatSize,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("字体大小调节", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "可分别调节对话正文字号与界面缩放比例，适应不同阅读习惯。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("对话正文字号", style = MaterialTheme.typography.bodyMedium)
                        Text("${chatFontSize} sp", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = chatFontSize.toFloat(),
                        onValueChange = {
                            chatFontSize = it.toInt()
                        },
                        onValueChangeFinished = {
                            performSave()
                        },
                        valueRange = 13f..22f,
                        steps = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("界面字体缩放", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(modifier = Modifier.fillMaxWidth().selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val scales = listOf(0.85f to "紧凑", 1.0f to "标准", 1.15f to "大", 1.25f to "特大")
                        scales.forEach { (scale, label) ->
                            val selected = kotlin.math.abs(fontSizeScale - scale) < 0.05f
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    fontSizeScale = scale
                                    val saved = manager.saveSettings(manager.getSettings().copy(chatFontSize = chatFontSize, fontSizeScale = scale))
                                    settings = manager.getSettings()
                                    savedMessage = if (saved) "已保存界面字体缩放" else "字体缩放保存失败，请重试"
                                },
                                colors = echoFilterChipColors(),
                                border = echoFilterChipBorder(selected),
                                label = { Text(label, softWrap = false) }
                            )
                        }
                    }
                }

                // 字体实时预览卡片
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = SettingsInnerShape,
                    color = glass.control,
                    border = BorderStroke(1.dp, glass.outline)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "预览效果：",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "你好！我是 Echo 智能助手，这是一段用于预览对话与排版字体大小的示例文本。",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = chatFontSize.sp,
                                fontFamily = if (settings.applyReadingFontToChats) com.aiassistant.ui.screens.chat.readingFontFamily(settings.storyFont) else FontFamily.SansSerif
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // 3. 界面背景设置
        item {
            SettingsGlassCard(hazeState = hazeState) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("界面背景", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "首页与对话页可分别设置图片或纯色。切换主题后，可在历史中恢复以前的背景。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 内置低饱和度纯色背景选择区
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "纯色背景",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "含浅色、深色及纯白纯黑预设。色卡所见即所得，不随深浅模式变黑；切换主题可恢复主题背景。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    var selectedColorPreset by remember { mutableStateOf<BackgroundImageManager.SolidColorPreset?>(null) }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        maxItemsInEachRow = 6,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BackgroundImageManager.PRESET_SOLID_COLORS.forEach { preset ->
                            val isDark = themeMode == AppThemeMode.Dark || (themeMode == AppThemeMode.System && androidx.compose.foundation.isSystemInDarkTheme())
                            val displayColorInt = if (isDark) preset.darkColorInt else preset.lightColorInt
                            val isSelected = selectedColorPreset?.id == preset.id

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedColorPreset = if (isSelected) null else preset
                                    }
                                    .padding(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color(displayColorInt))
                                        .border(
                                            BorderStroke(
                                                if (isSelected) 2.5.dp else 1.dp,
                                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                            ),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = readableTextColorFor(Color(displayColorInt)),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    preset.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    selectedColorPreset?.let { preset ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = SettingsInnerShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "应用「${preset.name}」到：",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val isDark = themeMode == AppThemeMode.Dark || (themeMode == AppThemeMode.System && androidx.compose.foundation.isSystemInDarkTheme())
                                    val targetColorInt = if (isDark) preset.darkColorInt else preset.lightColorInt

                                    FilledTonalButton(
                                        onClick = {
                                            val saved = BackgroundImageManager.saveHomeBackgroundSolidColor(context, targetColorInt)
                                            backgroundRevision++
                                            savedMessage = if (saved) "已将「${preset.name}」应用为首页背景" else "背景保存失败，请重试"
                                            selectedColorPreset = null
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("设为首页", style = MaterialTheme.typography.labelSmall)
                                    }

                                    FilledTonalButton(
                                        onClick = {
                                            val saved = BackgroundImageManager.saveChatBackgroundSolidColor(context, targetColorInt)
                                            backgroundRevision++
                                            savedMessage = if (saved) "已将「${preset.name}」应用为对话页背景" else "背景保存失败，请重试"
                                            selectedColorPreset = null
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("设为对话页", style = MaterialTheme.typography.labelSmall)
                                    }

                                    Button(
                                        onClick = {
                                            val homeSaved = BackgroundImageManager.saveHomeBackgroundSolidColor(context, targetColorInt)
                                            val chatSaved = BackgroundImageManager.saveChatBackgroundSolidColor(context, targetColorInt)
                                            backgroundRevision++
                                            savedMessage = if (homeSaved && chatSaved) "已将「${preset.name}」应用为全局背景" else "部分背景保存失败，请重试"
                                            selectedColorPreset = null
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("全部应用", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // 自定义相册背景图片
                Text("自定义相册图片背景", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                BackgroundPickerRow(
                    title = "首页背景",
                    hasImage = hasHomeBackground,
                    onPick = { homeBackgroundPicker.launch("image/*") },
                    onClear = {
                        BackgroundImageManager.deleteHomeBackground(context)
                        backgroundRevision++
                        savedMessage = "已恢复首页默认背景"
                    }
                )
                BackgroundPickerRow(
                    title = "对话页背景",
                    hasImage = hasChatBackground,
                    onPick = { chatBackgroundPicker.launch("image/*") },
                    onClear = {
                        BackgroundImageManager.deleteChatBackground(context)
                        backgroundRevision++
                        savedMessage = "已恢复对话页默认背景"
                    }
                )
                if (backgroundHistory.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Text("背景历史", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("图片与纯色均保留，横向滑动查看全部 ${backgroundHistory.size} 张。", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(backgroundHistory, key = { it.id }) { entry ->
                            var bitmap by remember(context, entry.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
                            LaunchedEffect(context, entry.id) {
                                bitmap = withContext(Dispatchers.IO) { BackgroundImageManager.getBackgroundHistoryBitmap(context, entry) }
                            }
                            val loadedBitmap = bitmap
                            if (loadedBitmap != null) {
                                Column(modifier = Modifier.width(110.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Box {
                                        Image(bitmap = loadedBitmap.asImageBitmap(), contentDescription = "历史背景",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxWidth().height(74.dp).clip(RoundedCornerShape(10.dp))
                                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)))
                                        FilledIconButton(onClick = { backgroundPendingDelete = entry },
                                            modifier = Modifier.align(Alignment.TopEnd).size(40.dp),
                                            colors = IconButtonDefaults.filledIconButtonColors(
                                                containerColor = MaterialTheme.colorScheme.surface,
                                                contentColor = MaterialTheme.colorScheme.error)) {
                                            Icon(Icons.Default.DeleteOutline, "删除历史背景", Modifier.size(18.dp))
                                        }
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        TextButton(onClick = {
                                            appearanceScope.launch {
                                                val saved = withContext(Dispatchers.IO) { BackgroundImageManager.restoreHomeBackground(context, entry) }
                                                backgroundRevision++
                                                savedMessage = if (saved) "已恢复首页背景" else "背景恢复失败，请重试"
                                            }
                                        }, contentPadding = PaddingValues(horizontal = 4.dp), modifier = Modifier.weight(1f)) {
                                            Text("首页", style = MaterialTheme.typography.labelSmall)
                                        }
                                        TextButton(onClick = {
                                            appearanceScope.launch {
                                                val saved = withContext(Dispatchers.IO) { BackgroundImageManager.restoreChatBackground(context, entry) }
                                                backgroundRevision++
                                                savedMessage = if (saved) "已恢复对话页背景" else "背景恢复失败，请重试"
                                            }
                                        }, contentPadding = PaddingValues(horizontal = 4.dp), modifier = Modifier.weight(1f)) {
                                            Text("对话", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter)
    )
}

    avatarPendingDelete?.let { entry ->
        AlertDialog(onDismissRequest = { avatarPendingDelete = null },
            title = { Text("删除历史头像？") },
            text = { Text("删除后不会自动重新收录。正在使用的用户、模型或角色头像保持不变；以后重新上传该图片仍可保存。") },
            confirmButton = { TextButton(onClick = {
                avatarPendingDelete = null
                appearanceScope.launch {
                    val deleted = withContext(Dispatchers.IO) { AvatarManager.deleteAvatarHistory(context, entry) }
                    avatarHistoryRevision++
                    savedMessage = if (deleted) "已删除历史头像" else "删除失败，请重试"
                }
            }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { avatarPendingDelete = null }) { Text("取消") } })
    }
    backgroundPendingDelete?.let { entry ->
        AlertDialog(onDismissRequest = { backgroundPendingDelete = null },
            title = { Text("删除历史背景？") },
            text = { Text("删除后不会自动重新收录。当前首页和对话页背景保持不变；以后重新上传该图片仍可保存。") },
            confirmButton = { TextButton(onClick = {
                backgroundPendingDelete = null
                appearanceScope.launch {
                    val deleted = withContext(Dispatchers.IO) { BackgroundImageManager.deleteBackgroundHistory(context, entry) }
                    backgroundRevision++
                    savedMessage = if (deleted) "已删除历史背景" else "删除失败，请重试"
                }
            }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { backgroundPendingDelete = null }) { Text("取消") } })
    }

    pendingCropUri?.let { cropUri ->
        val currentTarget = pendingCropTarget
        val shapeMode = if (currentTarget == "avatar") CropShapeMode.CIRCLE else CropShapeMode.RECTANGLE
        val cropTitle = when (currentTarget) {
            "avatar" -> "裁剪与编辑用户头像"
            "home_bg" -> "裁剪与编辑首页壁纸"
            else -> "裁剪与编辑对话壁纸"
        }
        ImageCropEditDialog(
            imageUri = cropUri,
            shapeMode = shapeMode,
            title = cropTitle,
            onDismiss = { pendingCropUri = null },
            onConfirm = { croppedBitmap ->
                when (currentTarget) {
                    "avatar" -> {
                        if (AvatarManager.saveAvatarBitmap(context, croppedBitmap)) {
                            avatarBase64 = AvatarManager.getAvatar(context)
                            savedMessage = "用户头像已更新"
                        } else {
                            savedMessage = "头像保存失败，请重试"
                        }
                    }
                    "home_bg" -> {
                        val saved = BackgroundImageManager.saveHomeBackgroundBitmap(context, croppedBitmap)
                        backgroundRevision++
                        savedMessage = if (saved) "已设置首页背景" else "背景保存失败，请重试"
                    }
                    "chat_bg" -> {
                        val saved = BackgroundImageManager.saveChatBackgroundBitmap(context, croppedBitmap)
                        backgroundRevision++
                        savedMessage = if (saved) "已设置对话页背景" else "背景保存失败，请重试"
                    }
                }
                pendingCropUri = null
            }
        )
    }
}

private data class AppearancePresetOption(
    val id: String,
    val label: String,
    val description: String,
    val colors: List<Long>
)

@Composable
private fun HomeLayoutChoice(
    style: String,
    label: String,
    selected: Boolean,
    compact: Boolean,
    showRecentStory: Boolean,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.clip(RoundedCornerShape(12.dp))
        .selectable(selected = selected, role = Role.RadioButton, onClick = onSelected)
        .padding(4.dp)) {
        Canvas(Modifier.fillMaxWidth().height(106.dp).clip(RoundedCornerShape(8.dp))
            .background(colors.surface).border(if (selected) 2.dp else 1.dp,
                if (selected) colors.primary else colors.outlineVariant, RoundedCornerShape(8.dp))
            .clearAndSetSemantics { }) {
            val gap = 9.dp.toPx()
            val isWorkbench = style == EchoHomeLayoutStyle.WORKBENCH
            val navWidth = if (isWorkbench) size.width * 0.22f else 0f
            val navHeight = if (isWorkbench) 0f else 23.dp.toPx()
            val contentLeft = navWidth + gap
            val contentWidth = size.width - contentLeft - gap
            val navColor = colors.primaryContainer
            if (isWorkbench) {
                drawRect(navColor, size = Size(navWidth, size.height))
            } else {
                drawRect(navColor, topLeft = Offset(0f, size.height - navHeight), size = Size(size.width, navHeight))
            }
            repeat(4) { index ->
                val center = if (isWorkbench) Offset(navWidth / 2f, (index + 0.5f) * size.height / 4f)
                    else Offset((index + 0.5f) * size.width / 4f, size.height - navHeight / 2f)
                drawCircle(if (index == 0) colors.primary else colors.onPrimaryContainer.copy(alpha = 0.55f),
                    radius = if (index == 0) 4.dp.toPx() else 2.5.dp.toPx(), center = center)
            }
            val radius = CornerRadius(2.dp.toPx())
            drawRoundRect(colors.onSurface, topLeft = Offset(contentLeft, gap),
                size = Size(contentWidth * 0.58f, 4.dp.toPx()), cornerRadius = radius)
            var rowTop = gap + 12.dp.toPx()
            if (showRecentStory) {
                drawRoundRect(colors.secondaryContainer, topLeft = Offset(contentLeft, rowTop),
                    size = Size(contentWidth, 17.dp.toPx()), cornerRadius = radius)
                drawRoundRect(colors.onSecondaryContainer, topLeft = Offset(contentLeft + 5.dp.toPx(), rowTop + 7.dp.toPx()),
                    size = Size(contentWidth * 0.45f, 3.dp.toPx()), cornerRadius = radius)
                rowTop += 24.dp.toPx()
            }
            val rowGap = if (compact) 9.dp.toPx() else 15.dp.toPx()
            val rowCount = ((size.height - navHeight - gap - rowTop) / rowGap).toInt().coerceAtLeast(1)
            repeat(rowCount) { index ->
                drawRoundRect(colors.onSurfaceVariant.copy(alpha = 0.48f), topLeft = Offset(contentLeft, rowTop + index * rowGap),
                    size = Size(contentWidth * (if (index % 2 == 0) 0.9f else 0.68f), 3.dp.toPx()), cornerRadius = radius)
            }
        }
        Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = colors.onSurface, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun HomeLayoutSwitch(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp)
        .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null, colors = echoSwitchColors(),
            modifier = Modifier.clearAndSetSemantics { })
    }
}
