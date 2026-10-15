# 角色与创作：公开资料核验与本轮范围

核验日期：2026-10-15。依据当前 Echo 源码、SillyTavern 官方文档与公开源码、Character Card V2 规范、RisuAI 公开源码及公开 issue/PR。未读取或采用 `APP改进/`。本文保留研究基线，并单独记录本轮实现；实现和验证边界以本轮交付为准。

## 本轮实际落地状态（未发版）

- 已写入共用 `WorldBookActivation`：最近 4 条有效消息与本轮输入、回复变体折叠、常驻/关键词匹配、1500 估算 token 严格预算、超大条目跳过后继续选取，以及触发/未匹配/停用/超预算等决策结果。完整可视诊断页尚未实现。
- 已写入严格 JSON 导入器与现有页面预检确认：ST 对象、V2 数组、内嵌 `character_book`，2 MiB / 2000 条限制、JSON 关键词保留短语、事务新增独立副本。书默认关闭，不自动绑定；高级/未知语义默认停用，原始扩展不保存，必须确认兼容损失。RisuAI 自有导出、完整角色卡及无损往返未列入支持范围。
- 现有对话/故事设置已区分“全部已启用”（null）、显式空选择、指定 ID；整书停用时即使绑定也不参与。外部 order 的降级明确提示；本地优先级只影响预算保留，不提高提示权限。
- 角色上下文保留用户规则，撤下每卡重复文风铁律，增加事实/摘要/示例用途说明，并修正无记忆时当前故事时间被提前返回的问题。
- 主题预设（晴空、夜航、索引，保留经典默认）、独立创作阅读样式（沉浸叙事/简洁正文/分栏上下文）和流式动画（细光标/微声波/墨环）已接入设置。首页提供六种结构预览，尚未替换现有首页。既有 v2.8.3 APK 不含本轮源码变化，未构建 APK。
- 最终验证：`compileDebugKotlin` 退出 0；`testDebugUnitTest` 退出 0，83 套件 / 633 项通过、无失败或跳过；`lintDebug` 退出 0，0 Error / 88 Warning；`git diff --check` 退出 0。Gradle 命令均使用 `-g D:/Agent/APP-Echo/app/.gradle-home --no-daemon`。

## 1. 已核实的外部来源

以下内容通过 PowerShell `Invoke-WebRequest` / GitHub API 实际联网读取；首次普通沙箱请求因 socket 权限失败，获准后重新读取成功，成功命令退出码均为 0。本文只摘取行为结论，不复制外部实现或完整文档。

| 来源 | 核实内容 | 链接 |
| --- | --- | --- |
| SillyTavern 官方 World Info | 角色/聊天/全局绑定、关键词触发、扫描深度、预算、递归、注入位置 | https://docs.sillytavern.app/usage/core-concepts/worldinfo/ |
| SillyTavern 官方 Prompt Manager | 发送顺序、消息角色、In-Chat Depth、模板与正文的区别 | https://docs.sillytavern.app/usage/prompts/prompt-manager/ |
| SillyTavern 官方 Prompt Building | 主提示与历史后指令、角色指令继承 `{{original}}` | https://docs.sillytavern.app/usage/prompts/ |
| Character Card V2 规范 | `character_book`、`system_prompt`、未知扩展保留 | https://github.com/malfoyslastname/character-card-spec-v2/blob/main/spec_v2.md |
| SillyTavern release 源码 | 原生 `entries` 对象、枚举和 `convertCharacterBook` | https://github.com/SillyTavern/SillyTavern/blob/06bde939fb1e9c4c8d8641d810f0a916b5bce127/public/scripts/world-info.js |
| RisuAI 世界书源码 | 角色/当前聊天世界书、导入字段转换和自有导出格式 | https://github.com/kwaroran/RisuAI/blob/454e5e207747399f43a4788f5f08ecd5aa291550/src/ts/process/lorebook.svelte.ts |
| RisuAI 角色卡源码 | V2/V3 `character_book`、深度角色字段转换 | https://github.com/kwaroran/RisuAI/blob/454e5e207747399f43a4788f5f08ecd5aa291550/src/ts/characterCards.ts |

SillyTavern 文档仓库核验时提交为 `ed5423d809c6b10b8c1e9ef4a0c8ddc7f2ef7f3d`。网页及分支会变化，源码链接固定提交。

公开社区问题仅用作用户痛点与设计线索，不能视为已经合并的功能或已确认的根因：

- [PR #5921：独立角色世界书导入](https://github.com/SillyTavern/SillyTavern/pull/5921)：核验时 open；说明标准 `entries` 数组被当原生对象使用会导致字段遗漏、删除异常。Echo 必须按实际形状识别，不能只检查存在 `entries`。
- [Issue #6132：世界书依赖及同名冲突](https://github.com/SillyTavern/SillyTavern/issues/6132)：核验时 open；描述同名世界书的覆盖、版本和依赖问题。Echo 导入应先预览，默认新增副本，不按名称覆盖；关联使用稳定 ID。
- [Issue #3569：批量编辑与重复导入](https://github.com/SillyTavern/SillyTavern/issues/3569)：核验时 open；反映重复世界书和逐条编辑成本。选稿后页面可提供批量启停、去重提示。
- [Issue #5146：切换插入位置后才可见](https://github.com/SillyTavern/SillyTavern/issues/5146)：核验时 open；是用户报告，不据此判断通用缺陷。提示 Echo 需要显示触发结果与未注入原因。

## 2. 改动前的 Echo 实现基线

本轮读取的关键文件：

- `app/src/main/java/com/aiassistant/domain/model/WorldBookModels.kt`
- `app/src/main/java/com/aiassistant/data/repository/AiRepository.kt`
- `app/src/main/java/com/aiassistant/data/repository/RoleplayRepository.kt`
- `app/src/main/java/com/aiassistant/data/repository/helpers/ChatContextAssemblyHelper.kt`

改动前，世界书条目只有名称、关键词字符串、正文、启停、常驻和单一 `priority`。关键词按空格拆分会破坏外部世界书的多词关键词。普通聊天只扫描传入文本，约 1500 token 预算且首条可越界；角色创作扫描本轮文本、摘要及全局创作规范，最多取 15 条，没有相同的 token 预算。两条路径的语义不一致。本轮保持数据库字段不变，新增 JSON 关键词兼容与共用选择策略。

当前 `priority` 是数值越大越先入选、越靠前输出；这不等同于 SillyTavern 的 `order`。外部 order 数值较大者在最终输出中更靠后，但预算分配时常驻、高 order 又会先入选。不能把外部 order 无说明地写入 Echo priority 并宣称完全兼容。

当前角色上下文按全局创作规范、故事提示、角色卡、世界观、世界书、记忆等拼接为正文。文本段落的先后不等于模型 API 的指令权限，也不能保证后写的世界书一定覆盖角色定义。

## 3. 本轮核心与后续页面的边界

### 本轮核心设计及实施边界

1. 共用世界书选择引擎：普通对话、故事创作、方向选择准备上下文使用相同的范围、最近消息扫描、匹配和严格预算规则。
2. 明确区分未指定书目与显式空选择，避免取消所有绑定后意外回退为全部全局世界书。保持旧会话兼容需在会话设置含义与测试中说明。
3. 返回可解释的选择结果：匹配关键词、常驻、禁用、未命中、超预算；绑定范围目前在数据库候选查询阶段处理，尚未生成“未在绑定范围”的逐条诊断。完整诊断 UI 后续接入，底层不依赖正式页面改版。
4. 安全 JSON 解析与导入预览模型：支持 SillyTavern 原生对象、V2 `entries` 数组及角色卡内 `data.character_book`。校验结构、字段类型、大小与条目数；同名不自动覆盖。
5. 对未支持字段返回明确警告，说明“已读取基础字段”与“完整行为兼容”的区别；如果原始扩展没有持久化，不提供看似无损的再导出。
6. 精简每张角色卡重复的强制文风规则：角色卡描述人物，故事/全局提示控制叙事，避免每个角色重复注入一整套互相竞争的创作指令。

### 选定至少三个 UI 预览方案之一后再落地

- 创作入口组织为故事、角色、世界书和创作规则；预览应显示完整任务路径，而非仅换配色。
- 世界书采用“导入 → 检查兼容提示 → 保存到素材库 → 绑定当前故事/角色 → 试运行关键词 → 查看本轮注入”的路径。
- 书目页显示使用范围、启用条目、常驻/条件条目数；故事页直接显示已绑定书目。
- 提示词预览显示来源、发送位置、估算 token、被覆盖/继承的配置；修改文风时能定位真正来源。
- 批量编辑、角色卡 PNG/JSON 完整往返、脚本宏、高级递归、向量触发、群组概率、定时状态和历史深度插入另行评估，不计为本轮已经兼容。

## 4. 世界书格式映射

| 语义 | SillyTavern 原生 | Character Book V2 | 本轮建议 |
| --- | --- | --- | --- |
| 条目容器 | `entries` 对象，键为 UID | `entries` 数组 | 分开解析，转统一预览模型 |
| 主关键词 | `key: string[]` | `keys: string[]` | 保留数组及多词短语，不按空格拆 |
| 正文 | `content` | `content` | 必须字符串；空白正文提示并跳过 |
| 编辑标题 | `comment` | `name` / `comment` | 用于显示，勿无条件当成额外剧情事实 |
| 启用 | `disable` 取反 | `enabled` | 正确保留 false；缺失值按声明的兼容规则处理 |
| 常驻 | `constant` | `constant` | 常驻仍受预算限制 |
| 次关键词 | `keysecondary` | `secondary_keys` | 未实现时明确提示，不静默声称等价 |
| 条件逻辑 | `selective`, `selectiveLogic` | `selective`，扩展逻辑 | ST：0 AND_ANY、1 NOT_ALL、2 NOT_ANY、3 AND_ALL |
| 输出顺序 | `order` | `insertion_order` | 与预算保留优先级区分；暂不能保真时提示 |
| 预算保留优先级 | 与常驻、order 共同决定 | 可选 `priority`：低值先丢 | 不混同输出顺序 |
| 插入位置 | `position: number` | `before_char` / `after_char` 及扩展 | 非默认位置未实现须提示 |
| 扫描设置 | 全局或条目 `scanDepth` 等 | 书级 `scan_depth`、`token_budget`、`recursive_scanning` | 读取配置并标注当前是否应用 |
| 匹配规则 | `caseSensitive`, `matchWholeWords` | `case_sensitive` / extensions | 中文默认不依赖英文词边界 |
| 其他扩展 | 概率、递归、组、定时、角色等 | `extensions` | 保留原数据或明确不支持、不可无损导出 |

SillyTavern 位置枚举核实：0 角色前、1 角色后、2 作者注顶部、3 作者注底部、4 聊天深度、5 示例前、6 示例后、7 outlet。扩展位于不同结构，不能按字段同名推断行为。角色卡 V2 `extensions` 明确要求未知字段不得被导入/导出破坏；若只做基础内容导入，应避免宣称 V2 完整兼容。

RisuAI 自有世界书导出是 `{ "type": "risu", "ver": 1, "data": [...] }`，条目有 `key` 字符串、`secondkey`、`insertorder`、`alwaysActive`。本轮可以识别并给出格式提示；不必为了多报一个支持格式而采用其字段别名的宽松猜测。其源码接受多种别名不等于所有第三方文件可无损转换。

## 5. 触发与使用语义

- 导入文件只把资料保存到素材库。是否参与当前请求由书目绑定范围、书/条目开关和触发规则共同决定，界面应明确说明。
- 条目标题、触发关键词是控制信息；标准世界书正文应能独立描述事实。SillyTavern 官方明确标题和关键词本身不自动发送。
- 扫描深度指消息条数，不是对话轮数。最近 N 条 + 本轮输入需要约定是否重复计入本轮，并用测试锁定。
- 中文关键词适合子串匹配；默认 whole-word 会漏掉连续中文文本。外部 `/pattern/flags` 是 JavaScript 正则，不可直接当 Kotlin 正则宣称兼容；不支持时提示。
- 严格预算应包含包装文字的成本，过大单条应标记超限；不能因为它是第一条或常驻就越界。遇到过大条目后可继续尝试更小条目，避免无理由丢掉全部后续条目。
- 递归是“已激活正文可继续触发其他词条”，不是扫描更多聊天。若以后实现，应限制步数、去重、继续遵守总预算，并区分不接受递归触发和不再触发后续递归。
- 世界书插入能增加模型获知某项设定的机会，不保证模型每次都采用或正确执行。

## 6. 提示词层级的正确表达

不要在 UI 中只提供一个含混的“优先级”。至少区分：

1. **配置来源**：全局默认、故事覆盖、角色资料、世界书资料。继承/替换/追加是应用规则，可以确定且可以预览。
2. **发送顺序**：在角色设定前后、历史前后等，是组装顺序。靠后可能增加影响，但不是可靠权限或必然覆盖。
3. **协议角色**：system/user/assistant 是 API 的消息结构，模型服务兼容差异需在传输适配层处理。不能把任意历史中的 system 文本直接许诺为所有协议都支持。
4. **预算优先级**：上下文装不下时优先保留哪些资料，不等于资料冲突时自动获胜。

推荐默认：全局创作规则定义通用写作方式；故事提示明确本故事目标、视角和当前约束；人物卡负责人物事实/说话习惯；世界书负责条件触发的背景资料；历史和摘要记录实际发生事件。用户应看到本轮有效规则、继承关系和资料来源。

V2 卡片对 `system_prompt` 与 `post_history_instructions` 定义了非空覆盖及 `{{original}}` 继承语义；`creator_notes` / `creator` / `character_version` 不能当提示正文。若 Echo 后续支持角色卡导入，需要提供显式预览与选择，说明当前是否按规范应用；不能把卡片所有字段拼接进提示词后声称兼容。

SillyTavern 文档关于历史后指令影响的描述是其产品用法，并不构成跨模型的安全层级或“末尾必胜”保证。Echo 应提供可见的组装结果，而不是承诺模型一定遵守某一条。

## 7. 验证建议

- 世界书引擎：同一输入在普通聊天、故事上下文、方向准备中得出一致选择；显式空范围不扫描其他书；最近消息能触发；禁用项永不触发；严格预算、超大首条和后续小条目。
- 导入器：ST 对象、V2 数组、嵌入角色世界书、多词关键词、false/0、重复 UID、缺失/错误类型、空内容、未知扩展、非 JSON、过大文件、混合坏条目；不把错误文件导成成功空书。
- 提示词：多个角色只出现一次全局文风约束；保留人物事实、局部角色、故事覆盖和已确认记忆；书目未启用不出现在请求中。
- 页面后续验收：从文件选择到当前故事实际生效的完整路径；能从未触发原因定位是范围、开关、关键词还是预算，而不要求用户猜测。

研究阶段仅读取公开材料，未发送用户会话或凭据。未运行外部项目或真实模型接口，公开 issue 的个案报告未独立复现；本轮仓库提交与远端核验另见交付记录。

## 8. 视觉预览与检查

`comparison.html` 提供晴空、夜航、索引三套主题方向；每套含会话入口、创作与阅读页。`layout-options.html` 另提供六种首页信息架构：聚焦对话、侧栏工作台、创作书架、索引仪表盘、时间线叙事、一页任务。`option-a.png`、`option-b.png`、`option-c.png` 为主题预览；流式动效预览保留细光标、微声波、墨环三种，呼吸光带不列入实现选项。创作阅读页另设沉浸叙事、简洁正文、分栏上下文三种独立选择。全部示例内容为合成数据，未连接真实会话或模型。

浏览器检查覆盖桌面 1440 px、手机 390 px、主题、阅读样式控件、三种动画、减弱动态及键盘焦点；窄屏无横向溢出。样稿不代表 Android 视觉验收；整体布局尚待用户选定。
