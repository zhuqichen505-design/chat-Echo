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
fun EditableSettingProposalDialog(
    hazeState: dev.chrisbanes.haze.HazeState,
    proposal: ProposedSettingBundle,
    onDismiss: () -> Unit,
    onApply: (List<CharacterProfile>, RoleplayScenario?) -> Unit
) {
    var updatedChars by remember(proposal) {
        mutableStateOf(proposal.updatedCharacters.map { it.character })
    }
    var newChars by remember(proposal) {
        mutableStateOf(proposal.newCharacters.map { it.character })
    }
    var scenario by remember(proposal) {
        mutableStateOf(proposal.scenario)
    }

    EchoGlassDialog(
        hazeState = hazeState,
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("AI 精准识别到设定融入与更新", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(proposal.summary.ifBlank { "已结合已知设定智能分类更新与新角色" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        content = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. 更新已有角色
                if (updatedChars.isNotEmpty()) {
                    item {
                        Text("🔄 更新已有角色设定 (${updatedChars.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    itemsIndexed(updatedChars) { index, char ->
                        val summaryText = proposal.updatedCharacters.getOrNull(index)?.summaryOfChanges ?: "设定变动"
                        EchoGlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = EchoTokens.Radius.shapeMd
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("【${char.name}】 $summaryText", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                                    IconButton(
                                        onClick = { updatedChars = updatedChars.filterIndexed { i, _ -> i != index } },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "忽略此更新", modifier = Modifier.size(16.dp))
                                    }
                                }
                                OutlinedTextField(
                                    value = char.identity,
                                    onValueChange = { newId -> updatedChars = updatedChars.toMutableList().also { it[index] = char.copy(identity = newId) } },
                                    label = { Text("身份 / 职业") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = char.personality,
                                    onValueChange = { newP -> updatedChars = updatedChars.toMutableList().also { it[index] = char.copy(personality = newP) } },
                                    label = { Text("性格特质") },
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 2
                                )
                                OutlinedTextField(
                                    value = char.background,
                                    onValueChange = { newBg -> updatedChars = updatedChars.toMutableList().also { it[index] = char.copy(background = newBg) } },
                                    label = { Text("背景与补充经历") },
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 3
                                )
                            }
                        }
                    }
                }

                // 2. 发现新登场角色
                if (newChars.isNotEmpty()) {
                    item {
                        Text("➕ 发现新登场角色 (${newChars.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                    itemsIndexed(newChars) { index, char ->
                        val summaryText = proposal.newCharacters.getOrNull(index)?.summaryOfChanges ?: "新角色"
                        EchoGlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = EchoTokens.Radius.shapeMd
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("新角色：【${char.name}】", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodyMedium)
                                    IconButton(
                                        onClick = { newChars = newChars.filterIndexed { i, _ -> i != index } },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "不添加此角色", modifier = Modifier.size(16.dp))
                                    }
                                }
                                OutlinedTextField(
                                    value = char.name,
                                    onValueChange = { newName -> newChars = newChars.toMutableList().also { it[index] = char.copy(name = newName) } },
                                    label = { Text("姓名") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = char.identity,
                                    onValueChange = { newId -> newChars = newChars.toMutableList().also { it[index] = char.copy(identity = newId) } },
                                    label = { Text("身份 / 职业") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = char.personality,
                                    onValueChange = { newP -> newChars = newChars.toMutableList().also { it[index] = char.copy(personality = newP) } },
                                    label = { Text("性格特质") },
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 2
                                )
                                OutlinedTextField(
                                    value = char.background,
                                    onValueChange = { newBg -> newChars = newChars.toMutableList().also { it[index] = char.copy(background = newBg) } },
                                    label = { Text("背景经历") },
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 3
                                )
                            }
                        }
                    }
                }

                // 3. 世界观更新
                scenario?.let { sc ->
                    item {
                        Text("🌍 世界观与规则设定更新", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    }
                    item {
                        val summaryText = proposal.scenarioUpdate?.summaryOfChanges ?: "世界观完善"
                        EchoGlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = EchoTokens.Radius.shapeMd
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(summaryText, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                                    IconButton(
                                        onClick = { scenario = null },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "忽略世界观更新", modifier = Modifier.size(16.dp))
                                    }
                                }
                                OutlinedTextField(
                                    value = sc.name,
                                    onValueChange = { newName -> scenario = sc.copy(name = newName) },
                                    label = { Text("世界观名称") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = sc.worldview,
                                    onValueChange = { newWv -> scenario = sc.copy(worldview = newWv) },
                                    label = { Text("世界观法则与背景") },
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 3
                                )
                                OutlinedTextField(
                                    value = sc.rules,
                                    onValueChange = { newRules -> scenario = sc.copy(rules = newRules) },
                                    label = { Text("不可违背的法则") },
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 2
                                )
                                OutlinedTextField(
                                    value = sc.premise,
                                    onValueChange = { newPremise -> scenario = sc.copy(premise = newPremise) },
                                    label = { Text("当前剧情前提") },
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }
        },
        buttons = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("放弃")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val finalChars = updatedChars + newChars
                        onApply(finalChars, scenario)
                    }
                ) {
                    Text("决定添加并融合")
                }
            }
        }
    )
}
