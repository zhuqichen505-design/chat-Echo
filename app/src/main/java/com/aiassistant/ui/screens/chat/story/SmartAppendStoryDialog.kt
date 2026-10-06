@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package com.aiassistant.ui.screens.chat.story

import com.aiassistant.ui.screens.chat.*

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
fun ActionChip(text: String, onClick: () -> Unit) {
    SuggestionChip(
        onClick = onClick,
        label = { Text(text, style = MaterialTheme.typography.labelSmall) }
    )
}

@Composable
fun SmartAppendStoryDialog(
    hazeState: dev.chrisbanes.haze.HazeState,
    onDismiss: () -> Unit,
    onAppendAndMerge: (List<CharacterProfile>, RoleplayScenario?, Map<String, ConflictAction>) -> Unit
) {
    val context = LocalContext.current
    val repository = remember { com.aiassistant.AiAssistantApp.instance.repository }
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    var isAnalyzing by remember { mutableStateOf(false) }
    var progressStatus by remember { mutableStateOf("") }
    var analyzeJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var extractedBundle by remember { mutableStateOf<com.aiassistant.utils.AnalyzedRoleplayBundle?>(null) }

    val apiConfigs by repository.getAllApiConfigs().collectAsState(initial = emptyList())
    var selectedConfig by remember { mutableStateOf<com.aiassistant.domain.model.ApiConfig?>(null) }

    LaunchedEffect(apiConfigs) {
        if (selectedConfig == null && apiConfigs.isNotEmpty()) {
            selectedConfig = apiConfigs.firstOrNull { it.isDefault } ?: apiConfigs.firstOrNull()
        }
    }

    if (extractedBundle == null) {
        EchoGlassDialog(
            hazeState = hazeState,
            onDismissRequest = {
                if (isAnalyzing) {
                    analyzeJob?.cancel()
                    isAnalyzing = false
                }
                onDismiss()
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("智能识别与实时追加")
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "在此粘贴小说新章节、新角色档案或世界观设定，AI 将自动识别提炼并智能融合到当前故事会话中。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        label = { Text("粘贴设定/正文文本") },
                        placeholder = { Text("粘贴内容...") },
                        enabled = !isAnalyzing
                    )
                    if (isAnalyzing) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = progressStatus.ifBlank { "正在分析中..." },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(
                                onClick = {
                                    analyzeJob?.cancel()
                                    isAnalyzing = false
                                    Toast.makeText(context, "已终止分析", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.defaultMinSize(minHeight = 28.dp)
                            ) {
                                Text("停止", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                EchoPrimaryButton(
                    onClick = {
                        analyzeJob = scope.launch {
                            isAnalyzing = true
                            progressStatus = "正在提取人设与世界观..."
                            try {
                                val bundle = RoleplaySmartAnalyzer.analyzeTextOrNovel(
                                    context = context,
                                    rawText = inputText,
                                    repository = repository,
                                    preferredConfig = selectedConfig,
                                    selectedModel = selectedConfig?.modelName.orEmpty(),
                                    onProgress = { progressStatus = it }
                                )
                                extractedBundle = bundle
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                Toast.makeText(context, "解析已取消", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "分析失败: ${e.message}", Toast.LENGTH_LONG).show()
                            } finally {
                                isAnalyzing = false
                            }
                        }
                    },
                    enabled = inputText.isNotBlank() && !isAnalyzing
                ) {
                    Text(if (isAnalyzing) "解析中..." else "开始解析")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (isAnalyzing) {
                            analyzeJob?.cancel()
                            isAnalyzing = false
                        }
                        onDismiss()
                    }
                ) {
                    Text(if (isAnalyzing) "终止" else "取消")
                }
            }
        )
    } else {
        val bundle = extractedBundle!!
        val resolutionMap = remember { mutableStateMapOf<String, ConflictAction>() }
        EchoGlassDialog(
            hazeState = hazeState,
            onDismissRequest = { extractedBundle = null },
            title = { Text("确认追加/融合至本故事") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("识别到 ${bundle.characters.size} 位角色与 ${if (bundle.scenario != null) 1 else 0} 个世界观设定：", style = MaterialTheme.typography.labelSmall)
                    bundle.characters.forEach { char ->
                        EchoGlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(char.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                if (char.identity.isNotBlank()) Text("身份: ${char.identity}", style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    ConflictAction.values().forEach { action ->
                                        val isSelected = (resolutionMap[char.name.trim()] ?: ConflictAction.MERGE) == action
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { resolutionMap[char.name.trim()] = action },
                                            label = { Text(action.displayName, style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    bundle.scenario?.let { sc ->
                        EchoGlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("世界观：${sc.name}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                if (sc.worldview.isNotBlank()) Text(sc.worldview, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                EchoPrimaryButton(
                    onClick = {
                        onAppendAndMerge(bundle.characters, bundle.scenario, resolutionMap.toMap())
                    }
                ) {
                    Text("确认融合并入")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onDismiss) {
                        Text("放弃")
                    }
                    TextButton(onClick = { extractedBundle = null }) {
                        Text("上一步")
                    }
                }
            }
        )
    }
}
