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
    contentPadding: PaddingValues = PaddingValues(16.dp)
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val manager = AiAssistantApp.instance.personalizationManager

    var avatarBase64 by remember { mutableStateOf(AvatarManager.getAvatar(context)) }

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

    fun performSave() {
        manager.saveSettings(
            settings.copy(
                chatFontSize = chatFontSize,
                fontSizeScale = fontSizeScale
            )
        )
        settings = manager.getSettings()
        savedMessage = "已保存界面与外观设定"
    }

    LaunchedEffect(savedMessage) {
        savedMessage?.let {
            snackbarHostState.showSnackbar(it)
            savedMessage = null
        }
    }

    var backgroundRevision by remember { mutableIntStateOf(0) }
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

        // 1. 应用主题模式
        item {
            ThemeModeCard(
                hazeState = hazeState,
                selected = themeMode,
                onSelected = onThemeModeChange
            )
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
                                    manager.saveSettings(settings.copy(chatFontSize = chatFontSize, fontSizeScale = scale))
                                    settings = manager.getSettings()
                                    savedMessage = "已保存界面字体缩放"
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
                            "可为首页和对话页选择内置低饱和护眼纯色或自定义相册图片背景。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 内置低饱和度纯色背景选择区
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "内置极低饱和纯色背景（护眼高可读性）",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "精选极低饱和度（<5%）淡雅纯色，完全不影响文字清晰度与对比度（符合 WCAG AAA）。轻触选择并一键应用：",
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
