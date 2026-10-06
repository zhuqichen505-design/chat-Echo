@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package com.aiassistant.ui.screens.chat

import android.net.Uri
import android.graphics.BitmapFactory
import com.aiassistant.ui.components.ImageCropEditDialog
import com.aiassistant.ui.components.CropShapeMode
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import com.aiassistant.domain.model.ToolCallRecord
import com.aiassistant.domain.model.QueuedMessage
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import com.aiassistant.domain.model.TimelineNode
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aiassistant.utils.TimelineMemoryHelper
import com.aiassistant.utils.TimelineReconcileResult
import com.aiassistant.utils.TimelineEventItem
import com.aiassistant.utils.TimelineCategory
import com.aiassistant.utils.AtemporalSettingItem
import com.aiassistant.utils.TimelineReconcileCheckpoint
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalUriHandler
import com.aiassistant.ui.components.EchoTextToolbar
import com.aiassistant.ui.components.EchoTextToolbarHost
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Brush
import kotlin.math.roundToInt
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.aiassistant.AiAssistantApp
import com.aiassistant.R
import com.aiassistant.data.repository.AiRepository
import com.aiassistant.domain.model.Attachment
import com.aiassistant.domain.model.ChatModelOption
import com.aiassistant.domain.model.ConversationContextUsage
import com.aiassistant.domain.model.Message
import com.aiassistant.domain.model.MemoryItem
import com.aiassistant.domain.model.PromptTemplate
import com.aiassistant.ui.components.MarkdownText
import com.aiassistant.ui.components.SideAnchorItem
import com.aiassistant.ui.components.SideAnchorNavigator
import com.aiassistant.ui.components.TransientLazyListScrollbar
import com.aiassistant.ui.components.EchoPillSlider
import androidx.compose.runtime.CompositionLocalProvider
import com.aiassistant.ui.components.EchoGlassDialog
import com.aiassistant.ui.components.EchoGlassDropdownMenu
import com.aiassistant.ui.components.echoFilterChipBorder
import com.aiassistant.ui.components.echoFilterChipColors
import com.aiassistant.ui.components.rememberSmoothReorderState
import com.aiassistant.ui.components.reorderItem
import com.aiassistant.ui.components.reorderDragHandle
import com.aiassistant.ui.components.echoFilterChipElevation
import com.aiassistant.ui.components.echoGlassPalette
import com.aiassistant.ui.components.echoSegmentedButtonBorder
import com.aiassistant.ui.components.echoSegmentedButtonColors
import com.aiassistant.ui.components.echoShapeClick
import com.aiassistant.ui.components.echoHazePanel
import com.aiassistant.ui.components.echoHazeSource
import com.aiassistant.ui.components.readableTextColorFor
import com.aiassistant.ui.components.rememberReadableBackdropColors
import com.aiassistant.ui.components.rememberEchoHazeState
import com.aiassistant.ui.components.rememberLazyListControlsVisible
import com.aiassistant.utils.AvatarManager
import com.aiassistant.utils.BackgroundImageManager
import com.aiassistant.utils.FileUtils
import com.aiassistant.utils.RoleplaySmartAnalyzer
import com.aiassistant.utils.RoleplaySmartParser
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import android.widget.Toast
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.aiassistant.domain.model.CharacterProfile
import com.aiassistant.domain.model.NarrativeMode
import com.aiassistant.domain.model.PlotAction
import com.aiassistant.domain.model.RoleplayScenario
import com.aiassistant.domain.model.RoleplaySession
import com.aiassistant.ui.components.EchoGlassCard
import com.aiassistant.ui.components.EchoPrimaryButton
import com.aiassistant.ui.components.EchoGlassButton
import com.aiassistant.ui.screens.roleplay.ConflictAction
import com.aiassistant.ui.theme.EchoTokens


@Composable
fun ModelSelector(
    currentModel: String,
    availableModels: List<String>,
    onModelSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        val selectorShape = RoundedCornerShape(999.dp)
        Surface(
            modifier = Modifier
                .padding(top = 2.dp)
                .echoShapeClick(selectorShape) { expanded = true },
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = selectorShape
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = com.aiassistant.domain.model.ModelDisplayName.format(currentModel).ifBlank { "未选择模型" },
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        EchoGlassDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 280.dp)
        ) {
            if (availableModels.isEmpty()) {
                DropdownMenuItem(
                    text = { Text(com.aiassistant.domain.model.ModelDisplayName.format(currentModel)) },
                    onClick = { expanded = false },
                    leadingIcon = {
                        Icon(Icons.Default.Check, contentDescription = null)
                    }
                )
            } else {
                availableModels.forEach { model ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = com.aiassistant.domain.model.ModelDisplayName.format(model),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        onClick = {
                            onModelSelected(model)
                            expanded = false
                        },
                        leadingIcon = {
                            if (model == currentModel) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ModelSelector(
    currentOption: ChatModelOption?,
    fallbackModel: String,
    availableOptions: List<ChatModelOption>,
    onModelSelected: (ChatModelOption) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val options = remember(currentOption, fallbackModel, availableOptions) {
        val fallback = currentOption ?: ChatModelOption(
            apiConfigId = 0,
            configName = "",
            provider = "",
            apiType = "",
            modelName = fallbackModel
        )
        (availableOptions + fallback)
            .filter { it.modelName.isNotBlank() }
            .distinctBy { "${it.apiConfigId}:${it.modelName}" }
    }
    val currentLabel = currentOption?.modelName ?: fallbackModel

    Box {
        val selectorShape = RoundedCornerShape(999.dp)
        Surface(
            modifier = Modifier
                .padding(top = 2.dp)
                .echoShapeClick(selectorShape) { expanded = true },
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = selectorShape
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = currentLabel.shortModelLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        EchoGlassDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 280.dp)
        ) {
            if (options.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("暂无可切换模型") },
                    onClick = { expanded = false }
                )
            } else {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { ModelOptionText(option = option) },
                        onClick = {
                            onModelSelected(option)
                            expanded = false
                        },
                        leadingIcon = {
                            if (option.sameModelOption(currentOption, currentLabel)) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

data class ThinkingEffortOption(
    val value: String,
    val label: String
)

data class ChatTuningProfile(
    val modelLabel: String,
    val temperatureMax: Float,
    val temperatureEnabled: Boolean,
    val thinkingEfforts: List<ThinkingEffortOption>,
    val noThinkingEffortReason: String? = null,
    val forcedThinking: Boolean = false,
    val thinkingToggleEnabled: Boolean = true
)

internal fun chatTuningProfile(
    currentOption: ChatModelOption?,
    fallbackModel: String,
    enableThinking: Boolean
): ChatTuningProfile {
    val modelName = currentOption?.modelName?.ifBlank { null } ?: fallbackModel
    val provider = currentOption?.provider.orEmpty()
    val cap = com.aiassistant.domain.model.ModelCapabilityEngine.resolveCapabilities(modelName, provider)

    val label = when (cap.reasoningProviderType) {
        "deepseek_fixed" -> "DeepSeek 推理"
        "openai" -> "OpenAI 推理"
        "anthropic" -> "Claude 思考"
        else -> if (provider.isNotBlank()) provider else "当前模型"
    }

    val evidence = currentOption?.reasoningCapability
    val apiType = currentOption?.apiType ?: "openai"
    val reasoningPolicy = com.aiassistant.domain.model.ReasoningControls.policy(modelName, evidence)
    val efforts = com.aiassistant.domain.model.ReasoningControls.options(modelName, apiType, evidence)
        .filter { it.enabled }.map { ThinkingEffortOption(it.value, it.label) }

    val reason: String? = null

    val isDeepSeek = cap.reasoningProviderType == "deepseek_fixed" || modelName.lowercase().contains("deepseek")
    val tempMax = if (isDeepSeek) 2f else 1f
    val tempEnabled = !(enableThinking && (cap.reasoningProviderType == "openai" || cap.reasoningProviderType == "deepseek_fixed"))

    return ChatTuningProfile(
        modelLabel = label,
        temperatureMax = tempMax,
        temperatureEnabled = tempEnabled,
        thinkingEfforts = efforts,
        noThinkingEffortReason = reason,
        forcedThinking = reasoningPolicy.alwaysThinking,
        thinkingToggleEnabled = com.aiassistant.domain.model.ReasoningControls.canToggle(modelName, apiType, evidence)
    )
}

@Composable
internal fun ChatSettingsModelSelector(
    currentOption: ChatModelOption?,
    fallbackModel: String,
    availableOptions: List<ChatModelOption>,
    contentColor: Color,
    secondaryColor: Color,
    onModelSelected: (ChatModelOption) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var selectorWidth by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val currentLabel = currentOption?.modelName ?: fallbackModel

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val cap = remember(currentLabel) {
            com.aiassistant.domain.model.ModelCapabilityEngine.evaluateModel(currentLabel)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("模型", style = MaterialTheme.typography.titleSmall, color = contentColor)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (cap.contextWindowDisplay.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = "窗口: ${cap.contextWindowDisplay}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (cap.supportsVision) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = "🖼️ 视觉",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (cap.supportsTools) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = "🛠️ 工具",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (cap.supportsReasoning) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = "🧠 思考",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { size ->
                    selectorWidth = with(density) { size.width.toDp() }
                }
        ) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = currentLabel.displayModelShortName().ifBlank { "未选择模型" },
                    modifier = Modifier.weight(1f),
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = secondaryColor)
            }

            val menuModifier = if (selectorWidth > 0.dp) {
                Modifier
                    .width(selectorWidth)
                    .heightIn(max = 280.dp)
            } else {
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
            }

            EchoGlassDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = menuModifier
            ) {
                if (availableOptions.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("暂无可切换模型") },
                        onClick = { expanded = false }
                    )
                } else {
                    availableOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { ModelOptionText(option = option) },
                            enabled = option.apiConfigId > 0,
                            onClick = {
                                onModelSelected(option)
                                expanded = false
                            },
                            leadingIcon = {
                                if (option.sameModelOption(currentOption, currentLabel)) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

