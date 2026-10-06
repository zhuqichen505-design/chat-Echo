# 维护记录

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
