# 维护记录

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
