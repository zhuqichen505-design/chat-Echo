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
import com.aiassistant.domain.model.ChatModelOption
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.alpha
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
import com.aiassistant.ui.components.EchoConfirmDialog
import com.aiassistant.ui.components.EchoBadge
import com.aiassistant.ui.components.EchoBadgeType
import com.aiassistant.ui.components.EchoGlassCard
import com.aiassistant.ui.components.EchoGlassDialog
import com.aiassistant.ui.components.EchoGlassDropdownMenu
import com.aiassistant.ui.components.EchoIconButton
import com.aiassistant.ui.components.EchoSectionHeader
import com.aiassistant.ui.components.EchoSettingRow
import com.aiassistant.ui.components.EchoSwitch
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

internal val CurrentFeatureHighlights = listOf(
    "沉浸式角色扮演工作室与剧情自由创作",
    "对话与故事长记忆严格隔离与全功能管理",
    "合并一体化个性化与全局设定管理面板",
    "多角色设定与世界观一站式管理及双向同步",
    "AI 智能识别、提炼追加与已有设定精准融入",
    "剧情动作指令库（推进、改写、分支、摘要等）",
    "多模型 API 与流式对话及深度思考推理",
    "Token 预算上下文、滚动摘要与长记忆库",
    "上下文使用情况查看与主动压缩",
    "全量数据备份与安全加密恢复",
    "全界面 Echo 液态玻璃设计与暗色主题适配"
)

internal val V205UserUpdates = listOf(
    "优化备份导入逻辑：重构为非破坏性增量合并引擎，导入备份时绝不删除本地已有但备份中未包含的对话",
    "新增复制对话功能：支持深拷贝整个会话生成全新独立对话，全量复制消息、高级参数、角色卡设定与会话专属记忆",
    "复制对话入口对齐：普通对话与隐藏对话均在「置顶」同一层级提供「复制对话」入口，体验 100% 同步对齐",
    "复制隐藏对话特性同步：复制隐藏对话自动继承隐藏标签与隐私安全属性，即刻出现在隐藏会话列表中",
    "新增单对话备份功能：支持将单个会话及其完整消息、角色扮演与专属记忆导出为独立备份，入口在「置顶」同一层级",
    "单对话备份导入隔离：导入单对话备份时仅新增或更新该对话，对其余所有会话 100% 隔离，绝无覆盖消失风险",
    "备份管理卡片增强：自动区分展示全量备份（ZIP）与单对话备份（JSON），提供针对性增量恢复安全提示",
    "分支功能原子事务、生成成功确认弹窗与隐藏会话密码维持特性完美保持"
)

internal val V225UserUpdates = listOf(
    "API 配置独立启用总开关：在设置及 API 编辑弹窗中支持为每个 API 配置设置启用开关，可一键停用不使用的供应商",
    "停用 API 自动从选择列表中移除：当某个 API 配置关闭启用时，其下所有模型绝不出现在主对话页、角色扮演等任何模型选择列表中",
    "独立 API Key 精细化启用开关：在 API 配置的多 Key 列表中，支持对每个 Key 单独开启/关闭，停用 Key 自动跳过并呈现醒目状态徽章",
    "活跃 Key 智能过滤与故障转移：发送对话与角色扮演请求时，全量过滤已停用 Key，仅在启用的活跃 Key 中轮询与故障转移，安全可靠",
    "默认 API 停用后智能回退：若当前默认 API 被关闭，系统自动平滑回退至首个处于启用状态的有效 API 配置，杜绝请求失败",
    "上下文压缩与预算超限安全保护：完善上下文预算评估与超限截断机制，避免历史对话无限拼接导致突破端点最大 Token 限制"
)

internal val V224UserUpdates = listOf(
    "全角星号排版归一化：将全角星号（＊）智能映射为半角星号，彻底解决中文输入法与特定大模型输出全角星号导致粗体渲染失效的问题",
    "首尾非对称星号容错：智能兼容 ***text** 与 **text*** 等前后星号数量不一致的非标输出，优先将核心文本高亮加粗且不留孤立星号",
    "防止跨词贪婪吞噬错配：引入非贪婪最近邻闭合策略，杜绝因行内前置标记缺失尾星而将后续段落文字全部错误吞并的格式串色问题",
    "跨行加粗与格式块连续性：在普通段落中支持未闭合标记跨换行智能合并解析，彻底解决因换行符将粗体打断导致直接显示星号的缺陷",
    "Android 中文字体合成保底：粗斜体显式声明 FontSynthesis.All 字体合成，在中文字体缺乏原生斜体字形时强制保全粗体权重，杜绝加粗回退",
    "用户消息气泡行内 Markdown 支持：用户发送的内容与引用回复全面支持粗体、斜体、删除线与行内代码富文本渲染，视觉统一精致"
)

internal val V234UserUpdates = listOf(
    "思考模型全面解耦：取消按模型名称硬编码限制，默认全系模型支持深度思考档位切换；优化思考模式下温度控制策略，避免服务端拒绝",
    "长输入流式连接防抢占：重构智能记忆提取调度时序，在回复流式完成并释放信道后异步执行，彻底根除单并发通道下长文本输入报错 500 (empty response detected) 的问题",
    "记忆机制与提取能力 100% 完整保留：完全保留智能记忆提取机制、辅助模型提炼能力及本地规则兜底机制",
    "记忆确认交互体验优化：对话流式接收完毕后自然弹出记忆确认卡片，点击即可确认保存或忽略，交互体验更丝滑稳定",
    "内部辅助任务温度安全兼容：内部自动滚动摘要、时间线分析、自动命名及快速请求统一设置安全默认温度，杜绝思考模型辅助任务报错"
)

internal val V233UserUpdates = listOf(
    "时间线梳理保留对话断点水线：梳理时间线时持久化记录上次已梳理入库的对话节点（包含最后消息 ID、序号与条数），并在时间线面板中清晰展示",
    "结合原有时间线智能增量梳理：下一次梳理自动识别断点后新增的后续对话，以已确认的原有时间线为基准，对后续新剧情（及自动提取的事件）进行整体深化融合与去重优化",
    "同一事件长对话持续补充深化：避免事件割裂，后续对话涉及前序既有事件时，模型在原有时间线节点基础上进行整合修改与补充，保持故事时序连续完整",
    "上下文「更新摘要」与「主动压缩」错位根治：彻底解耦更新摘要与主动压缩的加载状态与指示器，杜绝按钮转圈、文字状态与禁用控制互相干扰错位的缺陷",
    "上下文优化面板卡片化重构：在上下文弹窗中以独立双卡片优雅呈现「滚动摘要」与「主动压缩」，消除底部 4 按钮挤压折行溢出问题，操作一目了然",
    "滚动摘要与主动压缩功能职责明晰：滚动摘要负责提炼历史梗概置入前情提要且【不裁剪任何原文】；主动压缩负责基于核心记忆物理裁剪早期原文【强力释放 50%~80% 预算】，防止超长报错",
    "时间线清空与状态联动完善：清空时间线时同步清理草稿与水线检查点，保持状态绝对一致"
)

internal val V232UserUpdates = listOf(
    "对话时间线自动识别用户确认：对话中自动识别到的时间与时序事件转为交互式待确认提案卡片，经用户点击确认后方可入库，彻底杜绝未经确认的错误入库",
    "多轮长事件修改补充与合并去重：赋予模型 UPDATE 修改补充过往事件能力，同一事件持续多轮对话时自动充实完善原事件节点，防止重复生成冗长流水账",
    "主动暂停生成完整保留报错信息：当遇到模型连接异常或多 Key 轮询错误时，用户主动暂停回复保留完整错误堆栈、尝试记录与故障说明，告别单一暂停提示",
    "时间线梳理全程实时可见与断点续梳：梳理进度与已提炼事件/设定实时可见；无论是否完成均自动实时存盘草稿；支持随时查看草稿并接着上次进度继续向下梳理",
    "长文本输入连接报错与空回复根治：排查并修复阻断主流的同步阻塞提炼调用、推理模型参数冲突及空回复异常，保障长输入下首字迅速响应与稳定流式输出",
    "提炼设定两行式精致排版：优化时间线工作台中世界观与角色固有设定显示效果，第 1 行紧凑排布勾选框、性质徽章、应用范围与删除键，第 2 行提供宽敞文字编辑框",
    "界面防挤压防错位与纯净视觉：修复时间线全不选按钮及会话专属设定总开关的文本挤压错位问题，移除会话专属设定标题多余灯泡图案，保持液态玻璃统一精致感",
    "设定确认保存即刻入库生效：确认时间线与多维设定后，世界观与固有设定 100% 独立保存入库，并在会话专属记忆与角色扮演记忆中立即可见且在后续对话中生效"
)

internal val V236UserUpdates = listOf(
    "滚动摘要不完整截断彻底修复：解除旧版短字数限制，移除破坏性 truncated 强行截断逻辑，提炼额度全面提升至 4096~16384 Token，完整保全前序长文对话核心脉络与未决议题",
    "滚动摘要与时间线记忆协同去重：重构结构化提炼提示词，滚动摘要聚焦全局核心脉络与决策共识，时间线专注于精确时空发展与关键事件，双向协同互补、杜绝内容机械复读",
    "流式生成中删除消息视口稳定与防丢：全面重构流式分支宿主挂载与平滑降级机制，流式输出期间删除任意历史消息或关联项时绝不丢失模型输出气泡，彻底根除暂时消失与错位跳动",
    "模型能力普适化与正则狭隘判断清理：彻底移除针对 Claude 3.7 / o1 等特定过时型号的狭隘正则硬编码，现代主流模型全面默认支持思考深度与自适应参数，普适性大幅跃升",
    "辅助场景过时短截断彻底扫除：复核标题生成、记忆提炼与辅助分析等所有调用，根除过时短额度（如 64 Token）导致的 empty response (500) 报错，全链路保障充裕生成空间",
    "深度思考强度 5 档具体赋值展示：思考强度直接采用「关闭思考 - low - medium - high - max」具体赋值，自动完成跨厂商兼容转换，并移除设置栏多余文字说明，界面清爽纯净"
)

internal val V235UserUpdates = listOf(
    "思考模型长输入 empty response (500) 彻底根治：重构 safeMaxTokens 预算策略，保底提供至少 4096~16384 Token 充裕额度，杜绝上下文 Headroom 挤压导致思考链未完成即被截断为空回复",
    "双端注水消息协议合规化修复：安全合并尾部强化声明至当前用户消息头部，彻底移除消息列表中段/尾部非首位 role=system 消息，100% 兼容 DeepSeek、Claude、Gemini 等各大模型与中转网关规范",
    "主流思考模型通用适配无需按名猜测：贯彻所有主流模型均为思考模型的现代架构，全面支持思考强度档位切换与 max_completion_tokens，移除对未知名模型发送 1024 Token 截断限制",
    "记忆机制与提取能力 100% 完整保留：跨会话长期记忆、会话专属记忆、核心绝对约束准则、故事时间线注入与后台智能提炼完整运作生效，绝不受任何影响",
    "空响应与网络异常自动回退重试：底层通信增加 empty response 智能检测与上下文轻量化平稳重试机制，大幅提升弱网与第三方中转网关下的抗波动稳定性"
)

internal val V239UserUpdates = listOf(
    "滚动摘要思考模型截断彻底根治：提炼生成空间大幅提升至 16,384 Token（Anthropic 8,192），充裕容纳现代思考模型（DeepSeek-R1 / QwQ 等）3000~6000 Token 思维链与长正文，正文生成绝不腰斩",
    "尾部断句防腰斩安全闭合保护：引入 sanitizeSummaryCompletion 算法，智能检测末尾标点完整性并安全闭合或修剪残缺断句，彻底杜绝半截未完结文字存入数据库",
    "记忆已有偏好约束严格去重：动态提取记忆库与时间线已有偏好准则，下达最高去重指令严禁在摘要中重复提炼用户习惯与固定约束，篇幅全力留给近期真实推进",
    "本地抽取式兜底同步去重：本地抽取算法同步适配已知偏好约束过滤，全链路杜绝记忆系统与滚动摘要重复冗余",
    "查看编辑弹窗全屏平滑滚动：滚动摘要弹窗外层容器增加纵向滚动支持，输入框高度放宽并支持最多 30 行平滑展示，小屏与软键盘下完整浏览无遮挡"
)

internal val V238UserUpdates = listOf(
    "滚动摘要断层情节拼接彻底根除：彻底废止开篇与末尾跳跃截取机制，转录文本严格采用单一连续的近期对话切片，杜绝将最初开场相识与近期就餐等断层情节强行串联误导",
    "时间线记忆与滚动摘要职责彻底明晰：整体时间线由记忆系统（时间线节点与记忆库）独立读取与维护，滚动摘要专注于精准提炼「最近发生了什么」",
    "摘要总结内容强制锚定最新时间节点：提炼提示词深度重构，摘要总结的事件必须且只能紧密承接时间线最新时间节点，确保时序严谨、因果可信且言之有物",
    "最新时间基准动态注入提炼引擎：摘要生成时自动捕获当前会话的最新时间节点（包含具体时间标签与事件），为模型提供清晰精准的时空锚点",
    "本地抽取式兜底严格限制近期轮次：兜底算法同步收敛至最近对话轮次，全链路杜绝跨时空陈旧情节的断层干扰"
)

internal val V240UserUpdates = listOf(
    "滚动摘要末尾孤立空标题与残缺彻底根治：重构清洗闭合算法，循环检查并剔除结尾悬空的孤立板块标题与未闭合半句，绝不留下空白标题",
    "四大核心板块完整性校验与自愈回退：引入 isSummarySubstantiallyComplete 完整性判据，若模型输出短缺或残缺，自动触发本地高质量结构化提炼回退自愈，彻底杜绝残缺摘要入库",
    "提炼提示词四大板块全量输出铁律：强化提示词约束，严禁模型只写章节标题而不写实质内容，起步阶段亦规范提炼即时互动概括，杜绝半途停滞",
    "全网主流模型参数合规化（杜绝 400 报错与截断）：严格区分 OpenAI o-series 专用的 max_completion_tokens 与标准模型的 max_tokens，额度设定为通用高兼容的 8192 Token（Anthropic 4096），杜绝供应商网关报错与思考截断",
    "提炼超时进一步放宽保障深度思考：手动生成超时放宽至 150 秒，后台维护放宽至 90 秒，为 DeepSeek-R1 / QwQ 等长思考链模型留足充裕的生成时间"
)

internal val V255UserUpdates = listOf(
    "三方融合 UI 重构与终极设计规范落地 (UI-FINAL)：综合多份方案制定兼顾视觉和谐、操作逻辑与设计连贯性的统一标准，升级主题语义色彩系统与液态玻璃质感组件",
    "浅色模式色彩对比度达标与高可读性：优化浅色主题下 Primary 色彩对比度（≥ 4.5:1），消除低对比度文字，彻底解决高光与浅色模式下的眩光与辨识度不足问题",
    "角色与世界观表单交互防呆与输入保护：解决软键盘遮挡表单底栏与保存按钮问题，消除初次进入误报红框现象；保存失败保留用户未提交数据并提示原因，杜绝输入内容静默丢失",
    "API 配置弹窗异常捕获与防卡死：补齐 API 新建与编辑弹窗保存流程的全局异常捕获与友好 Toast 引导，彻底杜绝异常导致弹窗冻结与无响应",
    "角色扮演工坊实时检索与防误触优化：角色卡与世界观列表新增即时搜索框，实现名称与设定的快速检索定位；加宽生成中操作按钮物理间距，根除误触中途打断生成的体验痛点"
)

internal val V254UserUpdates = listOf(
    "根治大模型超限空回复 (500 empty response detected)：排查并根治全局默认最大 Token 数过大 (50000) 超出 Gemini、Claude、DeepSeek 等主流模型单次输出上限导致的网关断流，单次生成长度默认调整为通用的 4096 tokens，彻底消除多轮对话异常报错",
    "上下文降级保护全面放宽至 200k：请求超限或遇到服务限制时不再强制限制为 32k 上下文，全面调整为 200k (200,000 tokens) 宽裕预算，并自动为历史被误降级为 32k 的会话解禁恢复至 200k 保护",
    "对话设置高级参数优雅折叠：在对话设置窗口中，将「温度」「最大 Token 数」「上下文上限」「Top P」「转为角色扮演」统一折叠至底部「更多高级选项」，点击即可展开调节，默认界面极致精炼清爽",
    "非 o 系列思考参数智能兼容：针对 Gemini、Claude、GPT-4o、DeepSeek 等模型，自动规避仅 o1/o3 专用的 reasoning_effort 参数，彻底杜绝参数冲突导致的 400 报错与网关空回复",
    "角色扮演记忆与世界书协同注入：角色扮演模式下完整保留会话专属记忆、故事时间线与世界书背景，保障长篇剧情创作的设定连贯与深度代入"
)

internal val CurrentVersionUserUpdates = V255UserUpdates

internal val V253UserUpdates = listOf(
    "彻底根除第4次回复精准报错 (empty response detected 500)：全面废除在用户消息头部注水注入 [System Override Directive / 核心指令强化声明] 的旧机制，消除 Google Gemini 等模型将用户提问误判为越狱/指令攻击的安全拦截，多轮对话持久顺畅",
    "角色与剧情扮演模式智能识别：会话提示词包含角色定义或剧情设定时自动识别为角色扮演环境，严格隔绝通用 AI 助手模板干扰，输出人设纯正自然",
    "历史报错占位全链路阻断与自愈：历史因网络或安全拦截产生的错误记录不会渗入上下文，重新生成自动物理清理错误记录",
    "模型请求参数严格合规与分流：严格遵循 OpenAI o 系列与标准模型的参数规范，避免参数冲突与网关拒收",
    "流式请求与网络容错全链路强化：优化流式接收异常中断与超时重置机制，防止异常状态闭锁，全方位保障多轮对话流式生成的稳定可靠"
)

internal val V252UserUpdates = listOf(
    "根治会话持续报错 (empty response detected 500)：全面阻断错误占位信息注入上下文，历史中出现的请求失败与错误提示不再作为助手回复传入后续上下文，彻底自愈被错误污染的异常会话",
    "严格遵循模型与网关角色交替规范 (Role Alternation)：请求上下文自动剔除孤立助手消息、合并连续同角色消息，确保首尾符合规范，杜绝第三方网关拒收或空回复",
    "精准适配模型参数 (max_tokens 与 max_completion_tokens)：严格区分 OpenAI o-series 专用的 max_completion_tokens 与标准模型的 max_tokens，并规避不支持的温度与惩罚参数，避免请求冲突",
    "重新生成智能清理与体验优化：遇到失败提示点击重新生成时，自动清理历史残留的失败占位记录，保持多分支结构整洁，错误原因增加清晰指引",
    "流式请求与网络容错全链路强化：优化流式接收异常中断与超时重置机制，防止异常状态闭锁，全方位保障多轮对话流式生成的稳定可靠"
)

internal val V250UserUpdates = listOf(
    "滚动摘要彻底移除，根治停留在过去时间点原地踏步：全面废除系统提示词对静态滚动摘要的拼接注入与陈旧时间点锁死，模型不再受陈旧摘要束缚，恢复自然动态推进剧情",
    "最近十几次对话（16+ 条）无损保全：彻底废除按摘要标记物理切断对话的机制，强制完整保留最近活跃对话原文，模型完整掌握前序剧情与互动细节，绝不产生情节断层",
    "梳理时间线与提炼专属记忆：上下文压缩全面转向时间线沉淀，超出未压缩窗口的历史对话自动梳理为时间线节点并提炼会话专属记忆，既省 Token 又留存完整历史脉络",
    "上下文用量与压缩界面全面焕新：管理面板全面升级为时间线梳理与记忆沉淀展示，状态与指示文案精准透明，操作响应清晰直观",
    "双轨记忆与多轮对话无缝承接：历史时间线节点与会话专属记忆协同注入，既为模型提供长期世界观与关系支撑，又保障近期对话连贯推进"
)

internal val V237UserUpdates = listOf(
    "滚动摘要标点断句保护彻底根除暴力截断：引入智能标点边界闭合算法，彻底废除无视语意的字符强切逻辑，确保每一句摘要表达完整、有始有终、绝不半句残缺",
    "滚动摘要提示词去机械化与上下文深度提炼：重构提炼提示词，消除普通对话强行编造小说虚构时间线的困扰，聚焦核心主题、关键共识与未决待办，言之有物且逻辑清晰",
    "长分析服务通道全面升级保障生成韧性：将滚动摘要接入 600 秒专用的 RetrofitClient.getAnalysisService 长通道，支持多 Key 轮询容灾、思考模型 reasoning_content 提取与流式保底通道",
    "提炼超时时间大幅放宽与错误真实透明反馈：手动提炼超时放宽至 90 秒，后台自动归约放宽至 30 秒；AI 调用失败时如实反馈并保留已有高质量摘要，彻底杜绝残缺文本静默覆盖",
    "预算收缩整句保护与研发术语彻底清理：在摘要预算收敛时同样以句子边界为基准进行安全压缩，彻底移除「高保真结构化上下文状态机」等晦涩冗余的研发术语"
)

internal val V223UserUpdates = listOf(
    "模型回复首字符星号误吞彻底修复：全面移除句首单星号激进清洗规则，未配对星号作为常规字符平稳追加，彻底修复斜体语法与角色动作首字符星号被吞引发的格式异常",
    "对话页删除回复平稳防滑防跳跃：重构消息删除视口锚点定位算法，首项删除平稳重锚定至上一项相对偏移，杜绝屏幕跳动与误触发列表触底滑动",
    "模型连接气泡报错全量展示：彻底移除底层 40 字符截断，连接气泡发生报错时自动放宽并支持展开至 16 行，展示醒目警告图标并支持点击气泡一键展开查看完整多行堆栈",
    "思考链翻译全链路健壮重构：彻底解决思考链翻译失败故障，自动补全 API 路径 /v1、接入 600s 充足超时分析客户端、支持多 Key 与命名 Key 智能脱敏清洗与故障转移",
    "思考链翻译流式通道备用保底：引入流式传输保底机制，全面兼容仅支持 stream 或易超时的中转服务，兼容 DeepSeek-R1 / QwQ 等思考模型",
    "思考链翻译模型智能继承：未单独配置专用翻译模型时，自动无缝复用当前会话选中的活跃模型与可用 API 配置"
)

internal val V222UserUpdates = listOf(
    "模型连接气泡智能展开：仅在存在换行或长文本等可展开详情时才显示展开键，短文本气泡保持精炼",
    "气泡展开形变彻底修复：连接状态气泡采用统一 16dp 圆角与平滑尺寸动画，彻底杜绝展开时大小与形状异常突变跳跃",
    "全量直角矩形阴影修复：修复点击连接气泡及长按移动切换 Key 时的按压/拖拽阴影，全局严格贴合卡片与胶囊圆角，消除矩形黑影",
    "思考图标闪烁与样式统一：修复点击思考胶囊时左侧图标大小跳跃闪烁问题，删除机器人头像样式，全局统一使用优雅专业的心智脑力图标",
    "多场景输入框精简紧凑：重构添加自定义模型、上下文限制、自定义搜索数量及会话记忆编辑等输入框，消除庞大空白占用，界面紧凑精致",
    "跨会话记忆与世界书默认关闭：新会话与全局配置默认关闭跨对话记忆和世界书，避免不必要的信息污染与 Token 开销，按需自主开启",
    "跨会话记忆分类精细筛选：记忆管理界面新增「全部」「全局偏好」与「会话专属」分栏筛选 Chips，带数量实时统计，查找管理井井有条"
)

internal val V221UserUpdates = listOf(
    "OpenAI 兼容流式健壮解析：全面支持 NDJSON 格式（纯 JSON 行）、BOM 头自动剔除、SSE 注释行与 choices[0].message 非标准中转，彻底杜绝解析崩溃",
    "断流内容绝对保全与防丢弃：流式接收过程中遇到网络中断、EOF 或缺少 finish_reason/[DONE] 时，100% 完整保留并正常保存已接收内容，消除 MissingFinishReasonError 报错",
    "精准判空失败防护：仅在完全未收到任何有效 token、思考内容且无工具调用时判定为失败，彻底杜绝 empty response detected 误报",
    "智能重试保护与指数退避：已输出部分内容时坚决不自动重试，杜绝界面重复吐字；未收到内容时执行 1s / 2s / 5s 退避自动重试（最多 3 次）"
)

internal val V220UserUpdates = listOf(
    "API 设置拖拽阴影圆角统一：长按切换 Key 优先级时的投影阴影完全贴合 10dp 卡片圆角，彻底消除直角矩形割裂感",
    "API Key 命名同行紧凑排版：Key 命名直接在 Key 标识右侧同行展示，删除括号内冗余说明，界面垂直空间大幅精简",
    "思考与连接状态气泡完整查看：支持左右手势平滑滑动与点击多行展开/折叠，模型连接与复杂报错信息一览无余",
    "记忆提取完整性与废话剥离：解除字数过短截断限制，支持 30~80 字完整主谓宾陈述句，循环清洗分析型前缀与语气废话",
    "绝对行为约束与负向禁令生效：识别不允许/严禁/禁忌称谓，无条件置顶注入最高优先级系统指令，彻底根绝禁令失效顽疾",
    "多轮上下文记忆保留大幅提升：近期完整对话保留预算扩展至 16000 tokens，杜绝 2~3 轮对话后过早摘要与细节遗忘",
    "提示词输入框视口防抖与全平台思考参数透传：输入框获取焦点平稳无跳跃；OpenAI/Claude 3.7/DeepSeek/中转思考参数精准透传"
)

internal val V219UserUpdates = listOf(
    "12 项全项目专项需求深度核验与精细落地：全面复核时间跨度敏锐度、按键圆角防割裂、专属记忆排版、对话设置层级、长文本防折行、会话头像隔离、标签同排对齐、标准滚动摘要与模型回复可编辑",
    "全局图片手势裁剪与编辑统一闭环：全面接入 ImageCropEditDialog，用户头像与 API 模型头像支持圆形裁剪，首页壁纸与对话壁纸支持矩形裁剪",
    "手势微调自由操控：支持双指平滑放缩、单指平移拖拽、90° 顺时针旋转与水平/垂直镜像翻转，实时生成高保真预览",
    "记忆提炼辅助模型层级归一与自由直选：彻底剥离旧式下拉选单，统一移入「模型辅助与思考」选项卡，消除设置层级混乱",
    "跨服务商自由直选：采用 UniversalModelPickerCard 跨服务商自由直选所有模型，免去二级下拉切换",
    "即时测试连接与平滑降级验证：保留即时测试连接验证弹窗与自定义提炼提示词延迟防抖自动保存"
)

internal val V218UserUpdates = listOf(
    "记忆提取完整性与相关性重构：彻底剥离思考流（<think>标签及未闭合思维链），排除URL/Markdown语法碎片，严格校验记忆内容与原文语义相关性，并放宽提取Token上限至512，杜绝残缺断句",
    "多 API Key 自动故障转移：同一配置下填写多个 Key 时，遇到网络波动、连接超时或 4xx/5xx/内联 error 报错时透明无感尝试备用 Key，并自动剔除命名标签",
    "模型输出工具栏二级菜单收纳：新增更多操作（MoreVert）液态玻璃菜单，优雅收纳重新编辑、固定/取消固定到上下文、排除/恢复及删除，外层工具栏极致轻盈",
    "API Key 自定义命名与备注区分：新增独立 Key 卡片化录入，支持对不同供应商或额度的 Key 自定义备注命名（支持 [名称] Key 与 名称:::Key 格式），设置页一目了然",
    "滚动摘要提醒逻辑深度修复：彻底根除无上下文压力时的“有较早信息尚未进入摘要”虚假红点与提示，仅在高上下文压力或溢出阈值时提示滚动摘要",
    "核心巨型文件工程级模块化拆分：将 SettingsScreen 与 ChatScreen 近 20,000 行巨型单体文件按业务职能高内聚拆分为聚焦子模块，架构清晰稳健，零功能回归"
)

internal val V217UserUpdates = listOf(
    "时间线敏锐度与时空跳转优化：全面增强时空跳转与自然叙事敏锐度",
    "全局按键阴影与圆角几何轮廓统一：统一按键阴影与圆角几何轮廓",
    "专属记忆与时间线排版重构：优化记忆与时间线排版",
    "对话设置功能层级重整：重组对话设置功能层级",
    "全能图片裁剪与编辑系统：引入自由裁剪与旋转",
    "专属记忆列表支持折叠展开：支持长列表优雅折叠展开",
    "记忆提炼辅助模型层级归一：模型辅助与思考设置体验整合",
    "排版防折行与长文本全局展开收起：支持防折行与 ExpandableText",
    "会话级自定义模型头像隔离：支持会话级自定义模型头像隔离",
    "对话设置模型能力标签同排展示：支持能力标签同排展示",
    "标准上下文压缩体系：基于 ConversationSummaryBufferMemory 与缓冲区预警",
    "模型回复（Assistant）支持编辑：模型回复支持手动重新编辑"
)

internal val V216UserUpdates = listOf(
    "600s 充足模型响应超时放宽：针对长篇小说全文通读与深度分析耗时较长的客观规律，大幅放宽长推理 OkHttp 超时至 600 秒（10分钟），增加连接重试，彻底消除严苛过早超时导致的降级",
    "文学叙事与自然时间跨度深度支持：告别死板硬套‘具体天数’，全面拥抱‘两周过后’‘暑假开始’‘三年后·春’等文学自然时间跨度与阶段节点，保持叙事原貌",
    "自然叙事与单调推进智能融合：跨度跳跃（如两周后、一个月后）智能估算推进底层时序步进，有效防止时序倒流的同时完整保留真实叙事时间标签",
    "5 大核心剧情里程碑维度提炼：全面覆盖剧情重大转折与抉择、感情线与人际质变、秘密揭露与重要发现、状态转变与阶段成果、未决悬念与核心伏笔，脉络充实饱满",
    "6 维常驻与多维设定深度挖掘：敏锐捕捉角色特质与心结、习惯偏好与小动作、生理特征与禁忌、世界规则与法则限制、人际羁绊与誓言契约、专属信物与特殊器物",
    "大模型输出扩充至 8192 Tokens：深度分析支持高达 8192 Tokens 超长结构化输出，长篇大作 30+ 关键事件与丰富多维设定完整呈现，绝不截断",
    "超时异常精准语义反馈与重试支持：异常卡片智能识别超时错误并提供清晰指引，固有设定专属分类与工作台筛选体验持续稳定保持"
)

internal val V215UserUpdates = listOf(
    "180s 深度大模型长文推理接入：重构长推理 OkHttp 客户端，超时上限提升至 180 秒，彻底解决 30 秒超时导致大模型提炼静默降级为本地切片的严重问题",
    "固有设定独立分类与专属筛选：时间线工作台新增「💡 固有设定」顶级分类，点击可专项集中查看、独立管理与一键同步所有固有设定",
    "固有设定原子化提纯铁律：提示词严令提炼 8~25 字高度概括的原子化约束事实，严禁直接抓取小说中包含‘习惯’‘规则’的大段文学描写长句",
    "长篇编年史里程碑事件法则：拒绝零碎时间词与动词切片流水账，以完整事实（主谓宾清晰、何时何地发生何转折）梳理故事发展编年史脉络",
    "大模型提炼透明状态指示条：提炼完成后顶部显示 AI 深度提炼专属状态与所用模型，如发生降级显式展示原因与重试入口",
    "聊天窗口活动模型动态智能绑定：时间线梳理优先复用当前聊天窗口选中的活跃大模型，支持自动多级降级安全保护",
    "时间输入框、时序单调状态机与 Echo 胶囊 Dock 特性稳定保持"
)

internal val V214UserUpdates = listOf(
    "时间输入框全面可用：重构文本输入组件与 Compose 响应状态机，彻底消除预览文字被吞与无法打字故障",
    "时序单向递增状态机：解决在‘第2天’剧情后后续‘第二天早上/次日’被错误倒流识别为第2天的逻辑缺陷，单调累加至第3天、第4天",
    "放开时间轴捕捉上限：取消历史消息截断限制，全量通读百轮对话历史并扩充模型输出上限至 4096 Tokens，脉络完整连贯",
    "编剧写作指导深度脱敏：严格解耦并脱敏 [] 与 【】 中括号导演指令，深度结合正文事实生成客观陈述句，严禁照抄指令原词",
    "固有设定 6 维敏锐挖掘：深入提炼生理禁忌、习惯嗜好、身份过往、世界规则、人际羁绊与言语风格，大幅提升敏感度与精准度",
    "底栏重构为 Echo 胶囊 Dock：重构保存同步按键为立体液态玻璃渐变光泽胶囊，优化统计指示微徽章与文字排版",
    "开源前沿记忆体系与平滑拖拽重排动画特性稳定保持"
)

internal val V213UserUpdates = listOf(
    "开源前沿记忆体系升级：借鉴 Mem0 原子事实分类体系与重要度分级（不可违背约束、偏好习惯、时空经历、世界状态、客观事实）",
    "排他性属性冲突智能消解：居住地更替、称呼更替、偏好技术栈更迭等自动识别并覆盖替换，杜绝新旧矛盾冲突记忆共存",
    "三维混合动态检索：融合语义相关度 (40%)、静态重要度 (25%)、时间半衰期衰减 (20%) 与实体精准命中加成 (15%)，实现高保真记忆召回",
    "结构化多维上下文压缩：摒弃单段流式摘要，采用【核心背景固定约束】+【关键里程碑推进】+【未决待办事项】三层状态机，极大提高信息密度与信噪比",
    "智能无损信息密度提纯：自动识别并过滤纯寒暄废话轮次，杜绝无意义 Token 消耗",
    "长按拖拽平滑动画：多 Key 列表与消息排队浮窗长按移动时支持平滑重排位移动画，拖拽手感更自然直观",
    "剧情记忆与时间线提取核对：对话菜单支持一键读取完整历史，智能提炼故事时间线与重要记忆，支持用户可视化核对与二次编辑"
)

internal val V211UserUpdates = listOf(
    "分支命名单调自增：彻底修复 XX(分支1) 再次生成分支仍为 XX(分支1) 的重名问题，支持中文半角全角括号与自动编号递增",
    "记忆提取与提炼增强：支持 [] 与 【】 结构化中括号记忆提取，支持「注意」「特别注意」等关键词引导，并自动进行语义规范化加工提炼",
    "模型配置快速清空：设置中已添加的模型自定义参数支持单个一键清空与头部批量重置，快速恢复默认参数",
    "多 Key 优先级快捷切换：设置中多个 API Key 支持通过拖动手柄或上下微调箭头灵活快捷调整优先级",
    "网络波动容错重连机制：遇到 WiFi 抖动、断网重连或连接重置时自动执行退避重连（最多 3 次），并实时展示友好提示",
    "生成中消息排队与专属浮窗：模型回复过程中输入框保持可用，发送内容进入专属排队浮窗，支持拖动手柄调序、撤回回填输入框、编辑、删除与暂停控制",
    "分支创建完整保留多版本：创建分支截断历史时，完整克隆所选轮次的所有生成变体（版本 1、2、3...），保留新会话内的版本自由切换"
)

internal val V204UserUpdates = listOf(
    "分支功能完整重构：基于数据库事务与严格切片，规范严格递增时序，全链路杜绝历史记录颠倒或截断缺失",
    "分支生成弹窗确认与跳转：创建分支后弹出精致液态玻璃对话框，支持「确定」留在当前会话与「跳转到新对话」灵活选择",
    "隐藏对话解锁会话维持：解锁密码后持久维持会话解锁态，从隐藏对话返回直达已解锁会话列表，支持一键「重新锁定」",
    "隐藏属性与分支深度继承：在隐藏对话中分支严格继承隐藏标签与安全锁定，并完整继承活跃模型配置与高级参数",
    "角色扮演与会话记忆克隆：分支对话无缝克隆角色卡、场景世界观设定及会话专属长期记忆",
    "引用气泡内嵌卡片化：引用发出后在消息气泡中以精致内嵌毛玻璃卡片优雅展示，消除原始 Markdown 字符堆叠",
    "引用折叠与重新编辑联动：气泡内引文支持轻触展开/折叠，点击重新编辑时自动还原至输入框悬浮预览卡片",
    "输入框呼吸光晕与悬浮栏体验保持"
)

internal val V203UserUpdates = listOf(
    "分支功能深度修复：基于视口所见即所得切片，规范严格递增时间戳，杜绝历史颠倒与缺失，完整继承全量上下文",
    "隐藏对话分支属性继承：在隐藏对话中创建分支时，新分支自动继承隐藏标签与锁定状态",
    "角色扮演与会话记忆克隆：分支对话无缝克隆 RoleplaySession 角色、场景与会话专属记忆",
    "引用气泡内嵌卡片化：引用发出后在消息气泡中以精致内嵌毛玻璃卡片优雅展示，消除原始 Markdown 字符堆叠",
    "引用折叠与重新编辑联动：气泡内引文支持轻触展开/折叠，点击重新编辑时自动还原至输入框悬浮预览卡片",
    "输入框发送键与呼吸光晕红蓝切换与同心校准完美保持",
    "设置页真悬浮栏穿透滚动体验保持",
    "删除消息二次确认防误触机制保持"
)

internal val V202UserUpdates = listOf(
    "引用UI重构：引用文字由独立毛玻璃预览卡片呈现，不再强塞入输入框，发送时自动拼接提示词",
    "发送键边缘圆环校准：与添加文件(+)键保持完全一致的精致圆形边缘，随按键状态在蓝色与红色之间平滑切换",
    "收缩状态同心光晕重构：采用绝对同心数学绘制，彻底根除亚像素错位微小偏差",
    "收缩发送光晕红蓝切换：生成中状态光晕与边框同步变红，停止/发送状态保持纯正蔚蓝",
    "设置页真悬浮栏实现：列表支持从透明毛玻璃悬浮栏下方穿透滚动，视觉与交互完美统一",
    "模型回复新增分支对话功能：一键创建包含该回复及所有前序上下文的全新独立会话分支",
    "长期记忆与专属记忆架构升级：吸收业界前沿范式，新增技术栈/职业身份/负向约束提取，引入防鹦鹉学舌系统引导",
    "删除二次确认机制：对话页内删除输入消息或 AI 回复全面增加二次确认弹窗，防止误删"
)

internal val V201UserUpdates = listOf(
    "输入框(+)添加与发送按键直径统一为34dp（与智能搜索高度一致），间距加大至10dp，高亮边框精准贴合物理边缘",
    "输入框隐藏状态外圈呼吸脉冲光晕优化：缩至最小时与按键边缘严密重合，修复发送键脉冲错位问题",
    "输入框隐藏后支持系统级返回手势（侧滑返回）无缝退出隐藏状态并恢复面板",
    "首页壁纸全面穿透覆盖手机系统状态栏区域，带来真正全沉浸式视觉体验",
    "设置界面顶部悬浮栏完全参照对话页重构：透明毛玻璃质感，移除外圈背景填充，列表可从后方平滑穿透滚动",
    "引用功能内存级拦截：彻底杜绝系统剪切板访问弹窗提示，自动拼接'针对以上内容：'提问前缀",
    "流式输出自动滚动体验重构：生成结束平滑锚定于消息底部，根除滚动跳回回答起点的闪烁回弹问题",
    "全局排版优化：统计图表与模型配置等空间受限区域全面支持水平横向滑动浏览完整文本",
    "Markdown 渲染增强：深度清理模型首句前因颜色标签不兼容产生的孤立星号(*)伪影",
    "API模型配置支持列表展开/折叠，上下文窗口支持自定义数字输入与预设快速选择双轨模式"
)

internal val V200UserUpdates = listOf(
    "顶部控制栏与底部输入框边缘渐变高亮左侧加深，质感层次显著增强",
    "设置页面控制栏完全统一为对话页悬浮胶囊工具栏，风格一致性达到 100%",
    "输入栏支持沿手柄向下拖拽完全收缩至发送键，搭配呼吸脉冲光环与系统返回键同步退出",
    "顶部悬浮栏与输入框同步收缩至圆形返回键，支持点击恢复与系统返回拦截",
    "输入框内部按键边缘新增精致蓝色高亮微光包边，底色逻辑稳定保持",
    "会话长按与划选菜单全面新增'引用'功能，自动拼接 Markdown 引用块填入输入框",
    "修复隐藏会话重命名、删除、置顶与检索能力，与正常会话功能 100% 同步对齐",
    "使用统计图表全面重构：修复模型名称溢出截断，柱状图支持微光端帽，折线图升级为发光贝塞尔曲线",
    "对话设置页专属会话记忆保存逻辑修复，关闭后持久生效，不再被动强制开启",
    "API配置页面模型列表按'已添加'与'从Key读取'清晰分区，支持每模型独立定制上下文窗口与工具能力"
)

internal val V1929UserUpdates = listOf(
    "输入框与顶部悬浮栏透明度精确优化：略微降低透明度，提升文字清晰度与对比度，兼顾通透毛玻璃质感与极佳可读性",
    "重构组件背景着色渲染层级，解决因背景未绘制导致的文字与底层内容冲突问题",
    "保持思考胶囊文本稳定显示与异常滚动保护",
    "保持流式分支生命周期重置与幽灵气泡过滤机制",
    "输入框同心圆弧手柄尺寸与辅助滑动 4 键半透明质感持续保持"
)

internal val V1928UserUpdates = listOf(
    "输入框半透明液态毛玻璃效果完美还原，根除全黑/纯色覆盖，恢复通透背景模糊",
    "顶部悬浮工具栏与报错弹窗实现真实毛玻璃半透明透字效果，消除双层底色覆盖",
    "彻底修复模型思考胶囊文字无法显示问题，移除异常滚动裁剪，增加多级文案安全兜底",
    "根除对话页流式生成后幽灵气泡残留，完善分支生成生命周期自动回收与异常空消息过滤",
    "输入框放大同心圆弧手柄尺寸永久统一，划选复制工具栏解耦防抖彻底保持稳定",
    "辅助滑动 4 个按键保持柔和浅天蓝半透明体系与微光质感"
)

internal val V1927UserUpdates = listOf(
    "顶部悬浮工具栏与错误提示无边缘包裹且全屏穿透，文字半透明透出并实时液态毛玻璃模糊",
    "输入框放大弧线手柄大小彻底统一，拖拽前后永久固定为精致 22dp/17dp 贴角同心弧",
    "对话页辅助滑动 4 个按键重构为柔和浅天蓝半透明体系，搭配微光白边与透光质感",
    "文本划选弹窗与快照状态深度解耦，彻底修复高频闪烁死循环，复制与引用 100% 稳定响应",
    "大模型非标字体颜色与尾随星号容错解析，输入气泡呼吸感间距保留",
    "延续思考快速档纯正天蓝配色与新建会话记忆隔离规范"
)

internal val V1926UserUpdates = listOf(
    "顶部悬浮栏与错误弹窗直接复用输入框玻璃背景规范，消除悬浮栏与弹窗状态栏阴影异常穿透",
    "输入框右上角弧线控制手柄与边框精确同心贴合，拖拽微调更优雅",
    "全面兼容大模型非标颜色标签（如 <font color=\"2B7DEP\"> 与尾随星号）的精准渲染",
    "加宽用户输入气泡与上一条模型回复的纵向间距，提升长对话视觉呼吸感",
    "思考强度快速档重调为柔和纯正天蓝色，告别偏灰暗沉感",
    "对话页悬浮滚动快捷键升级为4键独立体系（到顶/上一条/下一条/到底），阶梯色彩与双线箭头",
    "文本长按选中弹窗防抖优化",
    "延续新建对话专属记忆默认关闭与长列表滚动条防断触优化"
)

internal val V1925UserUpdates = listOf(
    "对话流式响应时增加跟手跟随自动滚动",
    "修复对话顶部悬浮栏在部分机型上背景异常与玻璃穿透问题",
    "修复弹窗状态栏阴影未完整覆盖状态栏顶部边缘",
    "新建对话默认思考开启、联网关闭，专属记忆默认关闭",
    "新对话默认 API 选择入口移至「设置 - API 配置」顶部",
    "思考中与连接中状态文案支持在设置中自定义并一键重置",
    "长按文本工具栏增加剪切与粘贴，修复高频闪烁与空框问题",
    "修复点击中断后错误生成两条回复的问题",
    "对话中错误提示气泡支持折叠与双击展开/收起",
    "优化长列表滚动条滑动稳定性，避免快速拖动断触",
    "输入框展开按钮重构为优雅弧形控制手柄，支持拖拽随手调整高度"
)

internal val V1924UserUpdates = listOf(
    "修复弹窗状态栏阴影未完整覆盖状态栏顶部边缘问题",
    "修复顶部悬浮栏在部分机型上背景异常与玻璃穿透问题",
    "优化长列表滚动条滑动稳定性，避免快速拖动断触",
    "新建对话默认开启深度思考、关闭联网搜索、关闭会话专属记忆",
    "模型选择器支持按供应商分组和名称搜索"
)

internal val V1923UserUpdates = listOf(
    "修复弹窗状态栏阴影未完整覆盖状态栏顶部边缘",
    "会话专属记忆支持独立开关与范围隔离",
    "思考强度快速档位颜色调整",
    "液态玻璃组件优化"
)

val SettingsPanelShape = com.aiassistant.ui.theme.EchoTokens.Radius.shapeXl
val SettingsInnerShape = com.aiassistant.ui.theme.EchoTokens.Radius.shapeLg

@Composable
fun SettingsGlassCard(
    hazeState: dev.chrisbanes.haze.HazeState? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val glass = echoGlassPalette()
    EchoGlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = SettingsPanelShape,
        containerColor = glass.panel,
        borderColor = glass.outline,
        showBorder = true
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
fun SettingsInputField(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = if (placeholder.isNotBlank()) {
                { Text(placeholder, style = MaterialTheme.typography.bodyMedium) }
            } else null,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            shape = SettingsInnerShape,
            textStyle = MaterialTheme.typography.bodyMedium,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.28f)
            )
        )
    }
}

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToChat: (Long) -> Unit,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val glass = echoGlassPalette()
    val settingsBackgroundBitmap = remember(context) {
        BackgroundImageManager.getHomeBackgroundBitmap(context)
    }
    val hazeState = rememberEchoHazeState()
    var selectedSection by rememberSaveable { mutableStateOf<String?>(null) }

    fun executeBack() {
        if (selectedSection != null) {
            selectedSection = null
        } else {
            onNavigateBack()
        }
    }

    BackHandler(enabled = selectedSection != null) {
        executeBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.background)
                .echoHazeSource(hazeState)
        ) {
            settingsBackgroundBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        val readableBackdrops = rememberReadableBackdropColors(settingsBackgroundBitmap)
        val toolbarShape = RoundedCornerShape(22.dp)
        val toolbarTint = glass.input
        val toolbarContentColor = readableTextColorFor(
            background = toolbarTint,
            fallbackSurface = readableBackdrops.top
        )

        val topBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val topBarHeight = topBarPadding + 56.dp + 12.dp
        val tabContentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topBarHeight + 8.dp, bottom = 28.dp)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                when (selectedSection) {
                    null -> SettingsMenu(
                        hazeState = hazeState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = tabContentPadding,
                        onSectionSelected = { selectedSection = it }
                    )
                    "api_config" -> ApiConfigTab(hazeState = hazeState, modifier = Modifier.fillMaxSize(), contentPadding = tabContentPadding)
                    "http_access" -> {
                        val httpManager = remember(context) { com.aiassistant.utils.HttpAccessSettings(context) }
                        LazyColumn(modifier = Modifier.fillMaxSize().imePadding(), contentPadding = tabContentPadding) {
                            item { SettingsGlassCard(hazeState = hazeState) { SettingsHttpAccessControls(httpManager) } }
                        }
                    }
                    "appearance" -> AppearanceTab(
                        hazeState = hazeState,
                        modifier = Modifier.fillMaxSize(),
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                        contentPadding = tabContentPadding
                    )
                    "model_features" -> ModelFeaturesTab(
                        hazeState = hazeState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = tabContentPadding
                    )
                    "model_retry" -> {
                        val retryManager = AiAssistantApp.instance.personalizationManager
                        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = tabContentPadding) {
                            item { SettingsGlassCard(hazeState = hazeState) { SettingsRetryControls(retryManager) } }
                        }
                    }
                    "prompts_memory" -> PromptsMemoryTab(
                        hazeState = hazeState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = tabContentPadding
                    )
                    "web_search" -> WebSearchTab(
                        hazeState = hazeState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = tabContentPadding
                    )
                    "hidden_conversations" -> HiddenConversationsTab(
                        hazeState = hazeState,
                        modifier = Modifier.fillMaxSize(),
                        onNavigateToChat = onNavigateToChat,
                        contentPadding = tabContentPadding
                    )
                    "backup" -> BackupTab(
                        hazeState = hazeState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = tabContentPadding
                    )
                    "about" -> AboutTab(hazeState = hazeState, modifier = Modifier.fillMaxSize(), contentPadding = tabContentPadding)
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .echoHazePanel(
                            hazeState = hazeState,
                            shape = toolbarShape,
                            tint = toolbarTint,
                            blurRadius = 16.dp,
                            highlightAlpha = 0.025f
                        ),
                    shape = toolbarShape,
                    color = Color.Transparent,
                    contentColor = toolbarContentColor,
                    border = BorderStroke(1.dp, glass.outline),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { executeBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                        Text(
                            text = when (selectedSection) {
                                null -> "设置"
                                "api_config" -> "API配置"
                                "http_access" -> "HTTP 访问"
                                "appearance" -> "界面与外观"
                                "model_features" -> "模型辅助与思考"
                                "model_retry" -> "模型失败与重试"
                                "prompts_memory" -> "提示词与记忆"
                                "web_search" -> "搜索与工具"
                                "hidden_conversations" -> "其他对话"
                                "backup" -> "数据备份"
                                "about" -> "关于"
                                else -> "设置"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsMenu(
    hazeState: dev.chrisbanes.haze.HazeState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onSectionSelected: (String) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(EchoTokens.Spacing.cardGap) // T-1：主菜单与各 Tab 统一 12dp
    ) {
        item { EchoSectionHeader(title = "核心") }
        item {
            SettingsMenuItem(
                hazeState = hazeState,
                icon = Icons.Default.Key,
                title = "API配置",
                subtitle = "管理AI模型API密钥和服务商配置",
                onClick = { onSectionSelected("api_config") }
            )
        }
        item {
            SettingsMenuItem(
                hazeState = hazeState,
                icon = Icons.Default.Palette,
                title = "界面与外观",
                subtitle = "主题模式、用户头像、字体大小与壁纸设置",
                onClick = { onSectionSelected("appearance") }
            )
        }
        item {
            SettingsMenuItem(
                hazeState = hazeState,
                icon = Icons.Default.Security,
                title = "HTTP 访问",
                subtitle = "允许 HTTP 开关与自定义地址白名单",
                onClick = { onSectionSelected("http_access") }
            )
        }
        item { EchoSectionHeader(title = "智能") }
        item {
            SettingsMenuItem(
                hazeState = hazeState,
                icon = Icons.Default.Refresh,
                title = "模型失败与重试",
                subtitle = "自动重试总开关、各类错误重试次数与备用 Key",
                onClick = { onSectionSelected("model_retry") }
            )
        }
        item {
            SettingsMenuItem(
                hazeState = hazeState,
                icon = Icons.Default.Psychology,
                title = "模型辅助与思考",
                subtitle = "全模型自由直选自动命名、思考翻译与思考胶囊",
                onClick = { onSectionSelected("model_features") }
            )
        }
        item {
            SettingsMenuItem(
                hazeState = hazeState,
                icon = Icons.Default.AutoAwesome,
                title = "提示词与记忆",
                subtitle = "全局提示词、创作规范、关于我画像与长记忆",
                onClick = { onSectionSelected("prompts_memory") }
            )
        }
        item {
            SettingsMenuItem(
                hazeState = hazeState,
                icon = Icons.Default.Search,
                title = "联网搜索与智能工具箱",
                subtitle = "Exa 免Key搜索、Open-Meteo天气、健康步数与设备硬件",
                onClick = { onSectionSelected("web_search") }
            )
        }
        item { EchoSectionHeader(title = "数据") }
        item {
            SettingsMenuItem(
                hazeState = hazeState,
                icon = Icons.Default.VisibilityOff,
                title = "其他对话",
                subtitle = "输入 6 位数字密码查看隐藏对话",
                onClick = { onSectionSelected("hidden_conversations") }
            )
        }
        item {
            SettingsMenuItem(
                hazeState = hazeState,
                icon = Icons.Default.Backup,
                title = "数据备份",
                subtitle = "备份和恢复应用数据与角色设定",
                onClick = { onSectionSelected("backup") }
            )
        }
        item {
            SettingsMenuItem(
                hazeState = hazeState,
                icon = Icons.Default.Info,
                title = "关于",
                subtitle = "版本信息和功能介绍",
                onClick = { onSectionSelected("about") }
            )
        }
    }
}

@Composable
fun ThemeModeCard(
    hazeState: dev.chrisbanes.haze.HazeState,
    selected: AppThemeMode,
    onSelected: (AppThemeMode) -> Unit
) {
    val glass = echoGlassPalette()
    EchoGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = SettingsPanelShape,
        containerColor = glass.panel,
        borderColor = glass.outline,
        showBorder = true
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("应用主题", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "选择浅色、深色或跟随系统",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                AppThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = selected == mode,
                        onClick = { onSelected(mode) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = AppThemeMode.entries.size
                        ),
                        colors = echoSegmentedButtonColors(),
                        border = echoSegmentedButtonBorder(selected == mode)
                    ) {
                        Text(mode.label)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsMenuItem(
    hazeState: dev.chrisbanes.haze.HazeState,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val itemShape = SettingsPanelShape
    val glass = echoGlassPalette()
    EchoGlassCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 78.dp),
        shape = itemShape,
        containerColor = glass.panel,
        borderColor = glass.outline,
        showBorder = true
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun ApiConfigTab(
    hazeState: dev.chrisbanes.haze.HazeState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp)
) {
    val repository = AiAssistantApp.instance.repository
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val configsFromDb by repository.getAllApiConfigs().collectAsState(initial = emptyList())
    var localConfigs by remember { mutableStateOf<List<ApiConfig>>(emptyList()) }
    LaunchedEffect(configsFromDb) {
        localConfigs = configsFromDb
    }
    val providerReorderState = rememberSmoothReorderState()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingConfig by remember { mutableStateOf<ApiConfig?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    BackHandler(enabled = showAddDialog || editingConfig != null) {
        showAddDialog = false
        editingConfig = null
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            val defaultConfig = localConfigs.firstOrNull { it.isDefault } ?: localConfigs.firstOrNull()
            SettingsGlassCard(hazeState = hazeState) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Stars,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("新对话默认 API", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (defaultConfig != null) "当前默认：${defaultConfig.name} (${defaultConfig.provider})" else "尚未设置默认 API 配置",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (localConfigs.isNotEmpty()) {
                    Text(
                        "点击直接切换新对话默认生效的服务商：",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        localConfigs.forEach { cfg ->
                            FilterChip(
                                selected = cfg.isDefault,
                                onClick = {
                                    scope.launch {
                                        repository.setDefaultConfig(cfg.id)
                                    }
                                },
                                label = {
                                    Text(
                                        text = if (cfg.isDefault) "${cfg.name} (默认)" else cfg.name,
                                        fontWeight = if (cfg.isDefault) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = if (cfg.isDefault) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                colors = echoFilterChipColors(),
                                border = echoFilterChipBorder(cfg.isDefault)
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "API配置管理",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
                if (localConfigs.size > 1) {
                    Text(
                        text = "按住手柄上下拖动可调整排序",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }
            }
        }

        itemsIndexed(localConfigs, key = { _, cfg -> cfg.id }) { index, config ->
            val isDragActive = providerReorderState.isItemActive(index)
            ApiConfigCard(
                hazeState = hazeState,
                config = config,
                modifier = Modifier.reorderItem(
                    state = providerReorderState,
                    index = index,
                    key = config.id,
                    shape = SettingsPanelShape
                ),
                reorderHandle = if (localConfigs.size > 1) {
                    {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isDragActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                    else Color.Transparent
                                )
                                .reorderDragHandle(
                                    state = providerReorderState,
                                    index = { index },
                                    key = { config.id },
                                    keys = { localConfigs.map { it.id } },
                                    listSize = { localConfigs.size },
                                    onMove = { fromIdx, toIdx ->
                                        if (fromIdx in localConfigs.indices && toIdx in localConfigs.indices && fromIdx != toIdx) {
                                            val updated = localConfigs.toMutableList()
                                            val temp = updated[fromIdx]
                                            updated[fromIdx] = updated[toIdx]
                                            updated[toIdx] = temp
                                            localConfigs = updated
                                            repository.saveApiConfigOrder(updated.map { it.id })
                                        }
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DragIndicator,
                                contentDescription = "按住上下拖动调整模型供应商顺序",
                                tint = if (isDragActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else null,
                onEdit = { editingConfig = it },
                onDelete = {
                    scope.launch {
                        repository.deleteApiConfig(config)
                    }
                },
                onSetDefault = {
                    scope.launch {
                        repository.setDefaultConfig(config.id)
                    }
                },
                onToggleEnabled = { enabled ->
                    scope.launch {
                        repository.setApiConfigEnabled(config.id, enabled)
                    }
                }
            )
        }

        item {
            OutlinedButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("添加API配置")
            }
        }
    }

    if (showAddDialog || editingConfig != null) {
        ApiConfigDialog(
            hazeState = hazeState,
            config = editingConfig,
            isSaving = isSaving,
            onDismiss = {
                showAddDialog = false
                editingConfig = null
            },
            onSave = { config, modelNames, enabledModelNames, modelCapabilities, modelSettings, apiAvatarUri, clearApiAvatar ->
                if (!isSaving) {
                    isSaving = true
                    scope.launch {
                        try {
                            val configId = repository.saveApiConfig(config)
                            if (modelNames.isNotEmpty()) {
                                repository.replaceSelectedModels(
                                    apiConfigId = configId,
                                    modelNames = modelNames,
                                    enabledModelNames = enabledModelNames,
                                    modelCapabilities = modelCapabilities,
                                    modelSettings = modelSettings
                                )
                            }
                            if (clearApiAvatar) {
                                AvatarManager.deleteApiModelAvatar(context, configId)
                            }
                            apiAvatarUri?.let { uri ->
                                AvatarManager.saveApiModelAvatarFromUri(context, configId, uri)
                            }
                            showAddDialog = false
                            editingConfig = null
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "保存失败: ${e.message ?: "未知异常"}", android.widget.Toast.LENGTH_SHORT).show()
                        } finally {
                            isSaving = false
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun ApiConfigCard(
    hazeState: dev.chrisbanes.haze.HazeState,
    config: ApiConfig,
    modifier: Modifier = Modifier,
    reorderHandle: (@Composable () -> Unit)? = null,
    onEdit: (ApiConfig) -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val contentAlpha = if (config.isEnabled) 1f else 0.62f

    SettingsGlassCard(hazeState = hazeState, modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 第一行：名称 + 状态徽章 + EchoSwitch（§5.1：消除单行 4 控件挤压，S-4）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).alpha(contentAlpha)
                ) {
                    if (reorderHandle != null) {
                        reorderHandle()
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = config.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (config.isDefault) {
                        Spacer(modifier = Modifier.width(8.dp))
                        EchoBadge(text = "默认", type = EchoBadgeType.Primary)
                    }
                    if (!config.isEnabled) {
                        Spacer(modifier = Modifier.width(8.dp))
                        EchoBadge(text = "已停用", type = EchoBadgeType.Neutral)
                    }
                }

                EchoSwitch(
                    checked = config.isEnabled,
                    onCheckedChange = onToggleEnabled
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 第二行：提供商 + 模型 + API类型 + 操作按钮（EchoIconButton compact 右对齐，§5.1）
            Row(
                modifier = Modifier.fillMaxWidth().alpha(contentAlpha),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = config.provider,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text = "·",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = com.aiassistant.domain.model.ModelDisplayName.format(config.modelName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = config.apiType.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                // 操作行：编辑 / 设默认 / 删除（36dp compact，右对齐）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    EchoIconButton(
                        icon = Icons.Default.Edit,
                        contentDescription = "编辑",
                        onClick = { onEdit(config) },
                        compact = true
                    )
                    if (!config.isDefault) {
                        EchoIconButton(
                            icon = Icons.Default.StarBorder,
                            contentDescription = "设为默认",
                            onClick = onSetDefault,
                            compact = true
                        )
                    }
                    EchoIconButton(
                        icon = Icons.Default.Delete,
                        contentDescription = "删除",
                        onClick = { showDeleteDialog = true },
                        compact = true,
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    )
                }
            }

            // 第三行：密钥信息（如果有）
            val parsedKeys = remember(config.apiKey) { AiRepository.parseNamedApiKeys(config.apiKey) }
            if (parsedKeys.isNotEmpty()) {
                val enabledCount = parsedKeys.count { it.isEnabled }
                val keySummary = if (parsedKeys.size > 1) {
                    val names = parsedKeys.mapNotNull { it.name.ifBlank { null } }
                    val statusSuffix = if (enabledCount < parsedKeys.size) " ($enabledCount/${parsedKeys.size} 启用)" else ""
                    if (names.isNotEmpty()) {
                        "密钥$statusSuffix: ${names.joinToString(", ")}"
                    } else {
                        "${parsedKeys.size} 个密钥$statusSuffix (已配置自动故障转移)"
                    }
                } else {
                    val item = parsedKeys[0]
                    val statusText = if (!item.isEnabled) " [已停用]" else ""
                    if (item.name.isNotBlank()) "密钥备注: ${item.name}$statusText" else if (!item.isEnabled) "密钥已停用" else null
                }
                if (keySummary != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = keySummary,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().alpha(contentAlpha)
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        EchoConfirmDialog(
            title = "删除配置",
            text = "确定要删除这个API配置吗？",
            confirmText = "删除",
            isDestructive = true,
            onConfirm = { onDelete() },
            onDismiss = { showDeleteDialog = false }
        )
    }
}
