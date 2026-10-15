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

    var avatarBase64 by remember { mutableStateOf(AvatarManager.getAvatar(context)) }
    var avatarHistoryRevision by remember { mutableIntStateOf(0) }
    val avatarHistory = remember(avatarHistoryRevision, avatarBase64) {
        AvatarManager.listAvatarHistory(context)
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
    var chatFontSize by remember(settings) { mutableIntStateOf(settings.chatFontSize) }
    var fontSizeScale by remember(settings) { mutableFloatStateOf(settings.fontSizeScale) }
    var savedMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    var backgroundRevision by remember { mutableIntStateOf(0) }

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

    val backgroundHistory = remember(backgroundRevision) {
        BackgroundImageManager.listBackgroundHistory(context)
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
                            val bitmap = remember(entry.id) { AvatarManager.getAvatarHistoryBitmap(context, entry) }
                            if (bitmap != null) {
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
                                            bitmap = bitmap.asImageBitmap(),
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
                                                Image(bitmap.asImageBitmap(), "历史头像预览", modifier = Modifier.fillMaxWidth().height(180.dp), contentScale = ContentScale.Fit)
                                                TextButton(onClick = {
                                                    val saved = AvatarManager.restoreAvatarHistory(context, entry)
                                                    if (saved) { avatarBase64 = AvatarManager.getAvatar(context); avatarHistoryRevision++ }
                                                    savedMessage = if (saved) "已恢复历史头像" else "头像恢复失败，请重试"
                                                    showActions = false
                                                }) { Text("设为用户头像") }
                                                TextButton(onClick = {
                                                    val saved = BackgroundImageManager.saveHomeBackgroundBitmap(context, bitmap)
                                                    backgroundRevision++
                                                    savedMessage = if (saved) "已设置首页壁纸" else "壁纸保存失败，请重试"
                                                    showActions = false
                                                }) { Text("设为首页壁纸") }
                                                TextButton(onClick = {
                                                    val saved = BackgroundImageManager.saveChatBackgroundBitmap(context, bitmap)
                                                    backgroundRevision++
                                                    savedMessage = if (saved) "已设置对话壁纸" else "壁纸保存失败，请重试"
                                                    showActions = false
                                                }) { Text("设为对话壁纸") }
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
                        label = "聚焦对话（底栏）",
                        selected = settings.homeLayoutStyle == EchoHomeLayoutStyle.FOCUS,
                        compact = settings.homeCompactList,
                        showRecentStory = settings.homeShowRecentStory,
                        onSelected = { saveVisualPreference("首页已切换为聚焦对话") { it.copy(homeLayoutStyle = EchoHomeLayoutStyle.FOCUS) } },
                        modifier = Modifier.weight(1f)
                    )
                    HomeLayoutChoice(
                        style = EchoHomeLayoutStyle.WORKBENCH,
                        label = "侧栏工作台（左栏）",
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
                Text("切换主题会同步首页与对话页的纯色背景。用过的图片保留在背景历史中，可随时恢复。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.fillMaxWidth().selectableGroup()) {
                    val presets = listOf(
                        AppearancePresetOption(EchoAppearancePreset.EXISTING, "经典", "保留当前 Echo 配色", listOf(0xFF3B82F6, 0xFF38BDF8, 0xFFFB7185)),
                        AppearancePresetOption(EchoAppearancePreset.CLEAR_SKY, "晴空", "蓝白层次，清晰舒展", listOf(0xFF2C73B7, 0xFF83D3F0, 0xFFEDF4FC)),
                        AppearancePresetOption(EchoAppearancePreset.NIGHT_VOYAGE, "夜航", "墨绿与暖金，安静阅读", listOf(0xFF172B29, 0xFFD6BB83, 0xFFA5C9AD)),
                        AppearancePresetOption(EchoAppearancePreset.INDEX, "索引", "纸色与赭红，温和分明", listOf(0xFFAF4D34, 0xFFF0E7DF, 0xFF706354))
                    )
                    presets.forEach { preset ->
                        val selected = settings.appearancePreset == preset.id
                        Row(
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .selectable(selected = selected, role = Role.RadioButton, onClick = {
                                    saveVisualPreference("已应用「${preset.label}」主题") { it.copy(appearancePreset = preset.id) }
                                }).padding(horizontal = 4.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selected, onClick = null)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(preset.label, style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                                Text(preset.description, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                preset.colors.forEach { color ->
                                    Box(Modifier.size(16.dp).clip(CircleShape).background(Color(color))
                                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
                                }
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
                Spacer(Modifier.height(10.dp))
                Text("创作正文字体", style = MaterialTheme.typography.titleSmall)
                Text("只改变故事正文，不影响普通对话和设置文字。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        EchoStoryFont.LITERARY to "沉浸衬线",
                        EchoStoryFont.CLEAR to "清晰无衬线",
                        EchoStoryFont.TYPEWRITER to "稿纸等宽"
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = settings.storyFont == value,
                            onClick = { saveVisualPreference("创作正文字体已切换为$label") { it.copy(storyFont = value) } },
                            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = echoFilterChipColors(),
                            border = echoFilterChipBorder(settings.storyFont == value),
                            modifier = Modifier.weight(1f)
                        )
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
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val scales = listOf(0.85f to "紧凑", 1.0f to "标准", 1.15f to "大", 1.25f to "特大")
                        scales.forEachIndexed { index, (scale, label) ->
                            val selected = kotlin.math.abs(fontSizeScale - scale) < 0.05f
                            SegmentedButton(
                                selected = selected,
                                onClick = {
                                    fontSizeScale = scale
                                    val saved = manager.saveSettings(manager.getSettings().copy(chatFontSize = chatFontSize, fontSizeScale = scale))
                                    settings = manager.getSettings()
                                    savedMessage = if (saved) "已保存界面字体缩放" else "字体缩放保存失败，请重试"
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = scales.size),
                                colors = echoSegmentedButtonColors(),
                                border = echoSegmentedButtonBorder(selected)
                            ) {
                                Text(label)
                            }
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
                                fontFamily = FontFamily.SansSerif
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
                        "选择颜色后设置到首页、对话页或两处。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    var selectedColorPreset by remember { mutableStateOf<BackgroundImageManager.SolidColorPreset?>(null) }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
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
                                            tint = if (isDark) Color.White else MaterialTheme.colorScheme.primary,
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
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val isDark = themeMode == AppThemeMode.Dark || (themeMode == AppThemeMode.System && androidx.compose.foundation.isSystemInDarkTheme())
                                    val targetColorInt = if (isDark) preset.darkColorInt else preset.lightColorInt

                                    FilledTonalButton(
                                        onClick = {
                                            BackgroundImageManager.saveHomeBackgroundSolidColor(context, targetColorInt)
                                            backgroundRevision++
                                            savedMessage = "已将「${preset.name}」应用为首页背景"
                                            selectedColorPreset = null
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                    ) {
                                        Text("设为首页", style = MaterialTheme.typography.labelSmall)
                                    }

                                    FilledTonalButton(
                                        onClick = {
                                            BackgroundImageManager.saveChatBackgroundSolidColor(context, targetColorInt)
                                            backgroundRevision++
                                            savedMessage = "已将「${preset.name}」应用为对话页背景"
                                            selectedColorPreset = null
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                    ) {
                                        Text("设为对话页", style = MaterialTheme.typography.labelSmall)
                                    }

                                    Button(
                                        onClick = {
                                            BackgroundImageManager.saveHomeBackgroundSolidColor(context, targetColorInt)
                                            BackgroundImageManager.saveChatBackgroundSolidColor(context, targetColorInt)
                                            backgroundRevision++
                                            savedMessage = "已将「${preset.name}」应用为全局背景"
                                            selectedColorPreset = null
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
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
                            val bitmap = remember(entry.id) { BackgroundImageManager.getBackgroundHistoryBitmap(context, entry) }
                            if (bitmap != null) {
                                Column(modifier = Modifier.width(110.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Image(bitmap = bitmap.asImageBitmap(), contentDescription = "历史背景",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth().height(74.dp).clip(RoundedCornerShape(10.dp))
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        TextButton(onClick = {
                                            if (BackgroundImageManager.restoreHomeBackground(context, entry)) {
                                                backgroundRevision++
                                                savedMessage = "已恢复首页背景"
                                            }
                                        }, contentPadding = PaddingValues(horizontal = 4.dp), modifier = Modifier.weight(1f)) {
                                            Text("首页", style = MaterialTheme.typography.labelSmall)
                                        }
                                        TextButton(onClick = {
                                            if (BackgroundImageManager.restoreChatBackground(context, entry)) {
                                                backgroundRevision++
                                                savedMessage = "已恢复对话页背景"
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
