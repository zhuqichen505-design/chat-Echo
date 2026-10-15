# 维护记录

## 2026-10-15：独立外观偏好与六种首页排版预览（未发版）

- 用户确认三套主题作为预设，创作阅读样式独立选择；流式动画只保留细光标、微声波、墨环，移除呼吸光带选项。首页新增聚焦对话、侧栏工作台、创作书架、索引仪表盘、时间线叙事、一页任务六种完整手机画幅预览，仍待选择，不替换现有首页。
- `PersonalizationManager` 分别保存主题、阅读与动画，兼容旧偏好和未知值回退；`SettingsAppearanceTab` 提供即时选择与三种动态预览。浅/深色模式保持独立，主题变化即时作用于应用。
- `StoryReadingComponents` 仅调整故事正文排版，分栏模式显示已保存故事资料，宽屏并排、窄屏折叠，保留 Markdown 与消息操作；不改写提示词、世界书选择、数据库或消息内容。
- `EchoStreamingIndicator` 以 Canvas 绘制三种样式，正文及连接/思考等待共用设置；移除重复点状尾部指示。动画帧不触发正文重排，系统关闭动画时静止。`EchoMotion` 监听系统动画缩放变化并在离开组合时注销。
- `ChatScreen` 分栏不新增消息列表项，沿用稳定键与索引；滚动辅助控件避开资料栏，生成完成仍不强制回到回复开头。
- `comparison.html` 提供真正独立的主题/阅读/动画控件，`layout-options.html` 只保留生效的主题预览。两页桌面 1440 px、窄屏 390 px 无页面横向溢出，浏览器控制台无错误；减弱动态检查通过。截图与文字均使用合成资料，不代表 Android 设备验收。
- 在现有偏好测试套件新增真实 SharedPreferences 回归，覆盖 36 个合法组合、旧数据默认、未知值回退及其他偏好保持。`compileDebugKotlin testDebugUnitTest lintDebug --no-daemon` 联合命令退出 0；83 套件 / 633 项全部通过（失败、错误、跳过均为 0），Lint 0 Error / 88 Warning；`git diff --check` 退出 0。Gradle 使用 `-g D:/Agent/APP-Echo/app/.gradle-home`。
- 版本仍 2.8.3/189、Room v34/20 实体；未构建 APK，既有 APK 不含上述源码改动。

## 2026-10-15：角色创作核心、世界书导入与聊天衔接完善（未发版）

- 以当前 chat-Echo 源码和实际构建结果为事实依据，未读取或采用 `APP改进/` 的历史任务材料。参考 SillyTavern 官方文档与公开源码、Character Card V2 规范、RisuAI 源码及公开社区问题，来源与兼容边界记录在 `design/roleplay-rework/research.md`。
- `WorldBookImport.kt` 严格读取有界 JSON，识别 ST 对象、V2 数组及内嵌 `character_book`；保留多词关键词，拒绝重复字段、错误结构、过深/过大的输入。`WorldBookDao.insertImportedBook` 事务新增副本，不覆盖同名书或来源 ID；整书默认关闭。
- 导入预检明确高级/未知字段、位置/深度、正则、宏等不支持项及外部插入顺序降级；需要显式确认，受影响词条保持停用，原始扩展不保存。现有角色创作页与世界书设置接入预检、确认和后续使用说明；不是完整角色卡导入或无损格式转换。
- `WorldBookActivation` 统一普通聊天、角色创作和方向准备的选择规则：本轮输入及最近 4 条有效消息、回复变体折叠、常驻/关键词匹配、1500 估算 token 严格预算；大条目不阻挡后续小条目。选择状态区分全部已启用（null）、显式空、指定 ID；关闭世界书保留选择，导入不自动绑定。
- `RoleplayRepository` 保留用户自定义规则，移除每个人物卡重复文学铁律，说明故事要求、人物事实、世界书、时间线与摘要的用途。世界书不再受全局教学词误触发；实际请求统一注入一次，数字优先级仅用于预算保留，不改变协议角色权限。
- 附件重新生成恢复原图片/文件与 MIME，纯附件也可重试；附件记录损坏或输入为空时先报错，保留已有回复。真实 ViewModel/Room/本机 HTTP 回归覆盖两种协议及正常/错误回复。
- 故事角色、世界观和聊天设置改为串行组合保存，避免异步整行覆盖；保存后立即发送等待完成，再读取新设置。世界书扫描使用压缩前历史，避免古老置顶消息误触发及近期第三、四条漏触发。
- 修复空记忆集合提前返回导致当前故事时间/独立时间线未进入上下文的问题，覆盖 `AiRepository` 与 `TimelineMemoryHelper` 路径；已由新增真实 Room 集成测试暴露并修改代码，最终回归已通过。
- `ChatScreen` / `ChatScrollPolicy` 删除生成结束强制滚动，让流式回复与落库消息保留相同列表身份；用户新输入与助手保存分别处理，保存失败的临时回复有有界收尾。重新进入生成中的会话恢复活动分支宿主，防止旧回复遮住正在输出的正文。`RetrofitClient` 的 User-Agent 改由当前版本生成。
- 在已有领域套件补导入严格校验、真实 Room 新增与事务回滚、世界书范围/预算/短语/压缩、提示词及当前时间、附件重生成、故事组合保存和流式结束身份回归。没有以删除测试、抑制 Lint 规则掩盖失败。
- 本机已补齐且忽略 SDK/签名配置，签名证书与既有 v2.8.3 APK 一致，仍为非正式生产签名。默认缓存目录创建锁失败（退出 1），改用现有缓存后编译通过。首次联合验证 627 项中 1 项失败（退出 1），修复空记忆时间线分支后通过。Lint 首次及缓存复查各退出 1，修正本机 properties 盘符转义并强制重新分析后通过，未关闭规则。
- 最终验证：`compileDebugKotlin` 退出 0；`testDebugUnitTest` 退出 0，83 套件 / 631 项通过、无失败或跳过；`lintDebug --rerun-tasks` 退出 0，0 Error / 88 Warning；`git diff --check` 退出 0。Gradle 命令均使用 `-g D:/Agent/APP-Echo/app/.gradle-home --no-daemon`。
- 版本仍 `2.8.3 / 189`，Room v34 / 20 实体，无 schema 改动，**未构建 APK**，现有 v2.8.3 APK 不含本轮改动。主题预设三套，首页排版新增六种预览；本轮只接入必要功能入口和说明。
- 修正 PROJECT.md 旧构建路径、失效工作流小节引用及“完整版本历史”的错误说明；README、走查和工作流记录同步未发版边界。完整候选 33 文件隐私模式扫描退出 0，人工核对合成内容及预览；3 张 PNG 无文字/EXIF 元数据。本机配置、签名、原有图片与浏览器日志不提交。Git 提交/推送结果见交付；设备交互和真实模型接口未验收。
- `design/roleplay-rework/comparison.html` 与 3 张截图提供晴空、夜航、索引三个主题，每套含会话、创作、阅读页面，并提供细光标、微声波、墨环三种动画。`layout-options.html` 提供六种首页信息架构；桌面/390 px 窄屏、主题/阅读页/动画控件、减弱动态及浏览器控制台检查通过；仅为设计预览。

## 2026-10-06：v2.8.3/189 构建发布

- 用户明确要求构建 APK；更新来源为本仓库已审核提交，不复制旧项目或旧 Git 历史。仅版本递增、发布文档更新；包含此前事件覆盖、专属设定和编辑光标修复。
- compileDebugKotlin testDebugUnitTest lintDebug assembleRelease --no-daemon --console=plain 联合命令退出 0；83 套件/612 项全通过，Lint 0 Error/88 Warning。
- apksigner verify --print-certs、aapt dump badging 退出 0；com.aiassistant、2.8.3/189、API 26/34、arm64-v8a；Room v34。既有 Android Debug 证书，非正式生产签名。
- APK 16,782,609 字节，SHA256 C09A59BBA27DB1E767425DCB6EF793C3DD5F1ECFE99CF895FB24163B213C3EB0；445 个解压条目已知隐私/凭据模式扫描无匹配，复制以排他创建保护历史包，扫描与复制哈希校验退出 0。
- 完整暂存及待推送历史/元数据核查后发布，原始未跟踪图片、签名及构建产物不提交；自动扫描不保证发现全部未知私人值。图片沿用之前核查素材，无新增图片。gh 不在 PATH，改用本机 Git 认证与 API，不打印凭据。
- 发布结果以实际交付核验为准，成功前不清理旧 Release；本机历史包永久保留。无设备安装、启动、输入法或真实接口验收，已被覆盖或删除的数据无法恢复。

## 2026-10-06：输入光标与长文本点击跳顶回归修复（未发版）

- 代码风险：聊天自绘光标不共享字段内部滚动坐标，点击后异步校正会改写选区；String 同步延迟重建 TextFieldValue 默认选区为开头。长文本跟随逻辑在布局变化时也滚动到旧选区。
- ChatInputComponents.kt 恢复原生光标、撤下自绘覆盖层调用及点击校正；外部文本在渲染前同步，真正替换落到末尾，同文本保留选区与输入法组合态。软换行边界遵循 Compose 原生定位。
- EchoScrollableTextEditor.kt 只在聚焦、布局与文本匹配且文本/选区实际变化时跟随，布局或焦点变化单独不再触发滚动；保留既有 bringIntoView 钳制与右侧滑块。EditorInteractionPolicy.kt 提取可验证策略。
- UiPolishRegressionTest 新增两项回归；compileDebugKotlin testDebugUnitTest lintDebug --no-daemon --console=plain 退出 0，83 套件/612 项全部通过，Lint 0 Error/88 Warning；git diff --check 退出 0。
- 影响聊天输入与使用共享编辑器的模型回复/系统提示词编辑，不改胶囊动画、数据库或版本。未构建 APK，既有 2.8.2 Release 不含修复。无真机/输入法点击或键盘视觉验收，不能声称所有设备问题已消除。
- 上传前检查完整候选文件及元数据；未跟踪图片与构建输出不上传。设计技能辅助脚本和参考文件本机缺失，未生成额外设计文档。

## 2026-10-06：普通对话自动设定确认后清单隐藏修复（未发版）

- 用户澄清问题为普通对话自动总结设定应用后不出现在会话专属设定清单，上一轮故事角色修复并非此问题的直接修复。
- 根因：isExplicitTimelineEvent 使用允许任意括号标签的解析正则，将【角色特征】等分类误判为时间线；清单显示与请求上下文都调用该过滤器。
- TimelineMemoryHelper.kt 收紧到明确时间标签，保留剧情分类前置时间标签的兼容。无需修改数据库或迁移；仍在库中的隐藏设定可重新显示，已删除数据无法恢复。
- MemoryExtractionRepositoryTest 新增普通对话确认六类设定、重新读取、清单过滤谓词及实际下一次请求携带设定回归，验证不泄漏到其他对话；TimelineGeneralizationAndIsolationTest 补分类与时间标签断言。未进行设备清单视觉验收。
- compileDebugKotlin testDebugUnitTest lintDebug --no-daemon --console=plain 首次退出 1（旧剧情前缀时间标签兼容回归），修复代码后重跑退出 0；83 套件/610 项全部通过，Lint 0 Error/88 Warning；git diff --check 退出 0。
- 版本仍 2.8.2/188、Room v34；未构建 APK，现有 Release 不含此修复。隐私检查排除未跟踪图片及构建产物，上传状态以实际交付为准。

## 2026-10-06：主动提取局部设定应用修复（未发版）

- 已确认根因：角色上下文被全局角色 ID 非空条件阻挡，未绑定角色卡的故事即使存入局部角色也不会注入上下文；界面还在异步保存完成前显示成功。
- RoleplayRepository.kt 始终读取有效角色（包含局部角色）；ChatViewModel.kt 从数据库读取当前会话，保存完成后才清除候选并回调成功，异常保留候选并反馈；ChatScreen.kt 改为完成回调提示。
- MemoryExtractionRepositoryTest.kt 新增数据库重读及上下文回归，覆盖无绑定角色卡、多局部角色与局部世界观。未模拟界面点击或保存失败，不声称完整设备链路通过。
- 首次 compileDebugKotlin testDebugUnitTest lintDebug --no-daemon --console=plain 联合执行退出 1（测试使用不存在的 title 参数）；修正后同命令退出 0。83 套件/609 项，失败/错误/跳过 0，Lint 0 Error/88 Warning；git diff --check 退出 0。
- 版本仍为 2.8.2/188、Room v34；未构建 APK，现有 Release 不含此修复。既有局部角色可重新进入上下文，不代表恢复已丢失数据；真实接口及设备仍待验收。
- 上传检查排除未跟踪原始图片与所有构建输出；核查范围与远端结果以实际交付为准。

## 2026-10-06：增量事件跨时间点覆盖保护（未发版）

- 根因：模型 UPDATE 目标与本地相似内容 APPEND→UPDATE 匹配未限制时间点，确认保存还直接替换旧时间标签。
- AiRepository.kt 在提案匹配及事务保存两处限制同一明确时间点；跨时间或未知时间按 APPEND 处理。提示词同步约束，TimelineMemoryHelper.kt 提供保守时间标签比较（仅忽略空白和分隔符，不猜测自然语言等价）。
- TimelineMemoryTest / MemoryExtractionRepositoryTest 新增两项回归，覆盖不同日期/时段、未知/粗细时间、模型 UPDATE、本地相似 APPEND 和保存时二次保护；原同时间更新测试仍通过。
- compileDebugKotlin testDebugUnitTest lintDebug --no-daemon --console=plain 联合执行退出 0；83 套件/608 项，失败/错误/跳过 0；Lint 0 Error/88 Warning；git diff --check 退出 0。
- 影响仅增量事件提案与保存，不变更数据库、主聊天或动画。版本 2.8.2/188、Room v34；未构建 APK，现有 Release 不含此修复。已覆盖的旧内容无法从此修复自动还原；时间别名不同可能保守新增。
- 提交前核查完整候选文件及元数据，合成回归数据无真实用户内容；未跟踪原始图片不上传。设备与真实接口未验收，远端结果以交付核验为准。

## 2026-10-06：构建命令工作流固化

- 用户明确要求写入规则：“构建 APK”包含 Agent 自行审核同步、验证构建、隐私复核、推送及最新版发布，无需手动复制。
- 根 AGENTS.md 更新新仓库事实与 §2.1；本仓库 PROJECT.md / WORKFLOW_GUIDELINES.md 同步维护指向和清单。
- 不盲目覆盖双目录，不复制旧历史；冲突或隐私疑点暂停确认。仅新仓库最新版 Release，永久保留本机历史 APK。
- 本次仅文档修改，未执行 Gradle 或 APK 构建；差异检查与上传结果以实际交付核验为准。未跟踪图片未纳入。

## 2026-10-06：脱敏迁移与全新历史

- 从核查后的工作文件建立独立仓库，不携带旧 .git、提交、标签、私人配置、未跟踪报告或历史 APK。
- README 与 CHANGELOG 重新编写；release-notes/ 仅保留最新版公开资料。测试中不明归属中转域名改为 .invalid 保留示例；本机用户名路径不进入新仓库。
- 每次提交、推送及附件上传前必须核查完整内容、历史与元数据；疑点未确认禁止上传，报告不重复敏感原文。
- 原项目与本机历史安装包保持不变。旧仓库由用户设为隐藏；未代为删除旧仓库、旧标签或清理平台缓存。
- 源项目执行 compileDebugKotlin / testDebugUnitTest / lintDebug --no-daemon --console=plain 联合命令退出 0。无 APK 重构建；版本 2.8.2/188、Room v34。
- 新仓库推送与 Release 成功与否以实际交付核验为准。原有历史审计记录不复制到新仓库，以免继续公开过往发布材料。
