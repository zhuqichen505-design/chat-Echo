# Echo - AI API 助手 Android 应用

## 项目概述

Echo 是一个 Android 原生 AI API 客户端应用，用于调用 mimo、deepseek、OpenAI、Anthropic 等 AI 模型的 API。

**当前版本**: v2.8.2（versionCode 188）
**数据库版本**: 34（20 个实体）
**当前源码（未发版）**: 增量事件跨时间保护、故事局部角色上下文、普通对话自动设定清单过滤修复；聊天恢复原生光标并保留同文本选区，长文本编辑避免布局变化跟随旧光标跳顶。83 套件/612 项通过，Lint 0 Error/88 Warning；已发布 2.8.2 APK 尚不包含这些修复，设备交互待验收。
**本轮发布**: 用户要求构建 APK（默认末位 +1），无功能改动，打包 main 最新源码（含增量提取复核修复）。83 套件/606 项通过，Lint 0 Error/88 Warning；Release/签名/哈希/历史校验通过，APK 元数据见 walkthrough.md；设备安装/视觉待验收。
**发布维护**: 对外仅保留最新版说明及 APK 附件；旧标签删除须经用户授权。本机历史 APK 永久保留，历史审计记录不冒充当前发布。普通提交不能清除旧对象或平台缓存。
**技术栈**: Kotlin 2.2.21 + Jetpack Compose (BOM 2024.06.00) + Room 2.8.4 + Retrofit 2.9.0

> 工作流、最高准则与 APK 发布铁律的唯一权威版本在工作区根目录 `D:\Agent\APP-Echo\AGENTS.md`；本文件是项目事实卡，与其冲突时以 AGENTS.md 与代码为准。

---

## 维护规则（摘要，细则见 AGENTS.md）

当前开发/发布仓库为 chat-Echo，旧项目仅作本机历史或待审核来源。“构建 APK”命令包含 Agent 执行的隐私审核、逐项同步、验证构建、新仓库推送与最新版 APK 发布；不是后台自动同步。冲突或疑点未确认停止相关操作，严禁带入旧 Git 历史。完整规则见工作区 AGENTS.md §2.1，仓库执行清单见 WORKFLOW_GUIDELINES.md。

1. 每次代码变动后都必须提交到 Git。
2. 每次版本更新后都必须创建对应版本标签，例如 `v1.7.2`。
3. 每次代码变动或版本更新后都必须推送到远程仓库：
   `https://github.com/zhuqichen505-design/chat-Echo.git`
4. 每次交付时必须给出详细更新内容说明，包括：
   - 修改了哪些功能
   - 修复了哪些问题
   - 是否构建 APK
   - APK 路径
   - Git 提交号
   - 版本标签
   - 是否已完成网络备份
5. **APK 发布输出路径与历史版本安装包永久保留准则（最高铁律）**：
   - 以后构建和发布 APK 时，**统一只发布在 `D:\Agent\APP-Echo\app\releases` 这个路径**（严禁发布至其他路径）；
   - 绝对严禁删除、覆盖或清理 `D:\Agent\APP-Echo\app\releases` 目录下的任何历史版本安装包；
   - 每次发布新版本时只在 `D:\Agent\APP-Echo\app\releases` 目录下增量输出对应版本的唯一定名安装包（`Echo-v<version>.apk`），所有历史安装包必须永久保留。
6. 不允许提交本机敏感文件或构建产物，包括 `local.properties`、keystore、`.env`、`app/build/`、`.gradle/` 等。
7. **每次上传前必须进行隐私核查**：完整暂存文件、待推送提交/标签/元数据及所有附件均须检查；不包含用户任何个人信息或隐私，疑点未确认即禁止上传。自动扫描结合人工复核，报告只写脱敏结果；详见工作区 AGENTS.md §6 与 WORKFLOW_GUIDELINES.md §F。

---

## 数据库结构（v34，实体清单以 `data/local/AppDatabase.kt` 为准）

| 实体（表） | 说明 |
|------|------|
| Folder | 文件夹 |
| ApiConfig | API 配置 |
| Conversation / Message | 对话与消息（会话 enableReplyDirections/replyDirectionCount；消息 modelName/replyDirection 本轮元数据） |
| ApiUsageStat | 使用统计 |
| EnvironmentVariable | 环境变量（加密存储） |
| PromptTemplate | 提示词模板 |
| MemoryItem | 全局与会话专属记忆设定 |
| ConversationBranch / SelectedModel | 会话分支 / 模型选择与排序 |
| CharacterProfile / CharacterTag / CharacterTagCrossRef | 角色卡 / 角色标签及关联 |
| RoleplayScenario / RoleplaySession / RoleplayMemory | 角色扮演场景 / 会话 / 记忆 |
| WorldBook / WorldBookEntry | 世界书 |
| TimelineNode | 剧情时间线 |
| BackupIdentity | 跨设备实体稳定身份（backup_identities） |

迁移链至 `MIGRATION_33_34`（`data/local/migrations/AppDatabaseMigrations.kt`）；已开启 Room schema 导出并验证真实 v33 schema→v34 打开迁移、v32→v34 迁移链。新增列不破坏已有行；旧会话默认关闭。修改实体必须 version+1 并新增 Migration，禁止 destructive migration。

---

## 核心功能

1. **API配置管理** - 支持OpenAI兼容格式 + Anthropic格式
   - **HTTP 访问权限**：设置独立入口；默认关闭、名单空，添加不自动开启、关闭保留。按精确主机/IP/端口/路径段匹配，可省略 http://，拒绝通配符/账号/查询参数。network_security_config 静态开启传输，HttpAccessPolicy 动态默认拒绝，新增自有客户端必须 guard；不关闭 TLS 校验。权限保存在 http_access_permissions，不随便携聊天备份迁移。HTTP 不加密消息/Key，仅可信网络使用；组网仍需手机可达。
2. **对话功能** - 流式响应、思考模式、文件上传、Markdown渲染；思考能力以接口显式字段优先、具体型号资料补充，按接口/协议/模型隔离缓存，设置/胶囊/请求共享原始档位。模型名仅展示层格式化；普通/角色设置的记忆与世界书开关位于 Top P 下方高级区；连接生成保持稳定挂载、按实际底部溢出距离跟随。
   - **后台记忆提炼**：消息保存不调用模型，主聊天结束后一次提炼；辅助模型优先、不同会话活动模型备用、本地规则兜底，保存与候选复用。两类记忆关闭不请求，取消不兜底写入，返回后校验源消息/开关/恢复 epoch；原有思考内容清理、分类、冲突处理和角色隔离保留。
   - **思考 wire 兼容**：没有接口证据时不猜测 OpenAI-compatible thinking 扩展和显式 none；Anthropic 4.6 兼容回退保留 enabled/budget_tokens，有明确证据或仅支持 adaptive 的型号使用 adaptive/output_config，不给后者发送已移除的预算字段。显示、强度和原始模型 ID 保留。
    - **连接重试**：设置 → 模型失败与重试，总开关与默认展开的 19 类规则、0–20 次额外尝试、备用 Key 开关；每次失败立即提示原因，恢复输出后清除。无输出才重试、取消不重发；普通/角色回复与辅助模型共用分类规则，主请求首有效正文/思考等待 120 秒，正常输出后沿用 600 秒空闲读取。
   - **联网搜索**：Exa / Tavily / Mwmbl / SearXNG，选项只显示名字；请求结果数 1–100，Tavily 单次最多 20。Exa 托管/Mwmbl 免 Key；SearXNG 需要 HTTPS、允许 JSON 的用户实例，公共服务覆盖与配额不保证。
3. **会话分支** - 从任意消息点创建分支对话
   - **回复前选择方向**：对话设置独立开关与 2–4 个方向数量（默认关闭/2 个）；当前会话模型按真实上下文组织方向，再由用户选择后正常回复。「自行决定」从列表选一个；「其他」可填方向，留空采用列表外的新方向。等待可稍后继续、离开页面重进恢复（仅进程内），失败可重试/跳过/取消。仅最终回复进入历史与提炼，选定方向保存为本轮消息元数据，不自动成为长期设定；普通重生成重新选择，失败重试复用已选方向。开启增加耗时与费用，模型不编造历史依靠提示约束、非真实性保证。
4. **模型列表选择** - 可选择显示哪些模型
5. **使用统计** - Token使用量、缓存命中率
6. **数据备份** - 自动备份、手动备份、事务逻辑快照；新格式稳定身份支持单会话往返覆盖（编辑、删除同步），旧格式首次独立导入。
7. **提示词系统** - 预设模板、全局提示词
8. **环境变量** - 加密存储、变量引用

---

## 版本历史

| 版本 | 数据库 | 主要更新 |
|------|--------|----------|
| v1.3.0 | v5 | 基础稳定版本 |
| v1.3.6 | v10 | 修复闪退、数据备份 |
| **v1.4.0** | **v11** | **会话分支、模型选择、缓存命中率** |
| **v1.7.2** | **v16** | **联网搜索、隐私对话、隐藏对话、模型默认选择、UI与流式输出优化** |
| **v1.7.18** | **v17** | **液态玻璃 token 统一、深色可读性、对话气泡/输入栏、统计表格与设置页 UI 修复** |
| **v1.8.0** | **v18** | **角色扮演系统架构与功能实现** |
| **v1.8.1** | **v18** | **规范文档与限制解除、角色扮演全链路闭环、图标与UI显示优化、支持原版本覆盖更新** |
| **v2.0.4** | **v23** | **分支功能完整重构与事务原子落库、分支生成弹窗确认与跳转、隐藏对话解锁会话维持** |
| **v2.0.5** | **v23** | **非破坏性增量备份导入、复制整个对话（普通与隐藏同步）、备份单对话（普通与隐藏同步）** |

> 完整版本历史见 `CHANGELOG.md` 与 `UPDATE_LOG.md`（v2.0.5 之后含角色扮演全链路、时间线、世界书、阶梯式上下文压缩等）。

---

## 构建命令（Git Bash，已核验）

```bash
cd /d/Agent/APP-Echo/app/AiApiAssistant
./gradlew.bat compileDebugKotlin --no-daemon   # 编译
./gradlew.bat testDebugUnitTest --no-daemon    # 单元测试
./gradlew.bat lintDebug --no-daemon            # Lint
./gradlew.bat assembleRelease --no-daemon      # 发布 APK
```

- JDK 17 由 `gradle.properties` 的 `org.gradle.java.home=D:/Java/jdk-17.0.2` 固定，无需手动 export。
- Android SDK 由 `local.properties` 的 `sdk.dir` 指定（本机 `D:\Agent\app\.android-sdk`，该文件不入库）。
- 发布产物须复制为 `D:\Agent\APP-Echo\app\releases\Echo-v<version>.apk`（详见 AGENTS.md §4 铁律）。
