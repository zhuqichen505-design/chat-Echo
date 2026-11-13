# Echo - AI API 助手 Android 应用

## 项目概述

Echo 是 Android 原生多模型 API 客户端，支持对话、角色扮演、长上下文和本地备份。

**当前源码版本**：v2.8.35（versionCode 221）

**数据库版本**：37（20 个实体），本版无迁移。

**当前版本（2026-11-13，2.8.35/221，已发布）**：窗口失焦与应用停止时取消待处理键盘请求，仅在本编辑器持焦时结束焦点，恢复窗口时不主动弹出键盘；用户点击发生在窗口焦点恢复前时，短暂等待后重新采样；输入连接查询结合宿主视图并将未知状态按非破坏路径处理。涉及共享原生编辑器、聊天手柄与领域回归测试，Room v37 / 20 个实体、请求协议、消息及备份格式不变。窗口失焦后可能需要再次点击，悬浮窗及不同输入法的实际表现尚需真机验收。验证：冻结验收脚本及 Gradle 退出 0；92 套件 / 1,103 项测试，失败、错误及跳过均为 0；Lint 0 Error / 105 Warning；输入哈希一致，差异检查退出 0；安装包：Release 构建退出 0；Echo-v2.8.35.apk，25,505,561 字节，SHA-256 8fe327b3da6df8e7a7b8da3f6720a560a2aa546613a7cf658bf5a2f2790510a1；签名 v2 通过，证书与上一版一致，Android Debug 非正式生产签名；446 条目 CRC 全部通过、条目名与上一版一致，资源表与 186 张位图一致。未进行设备安装验证。

版本提交 `ca048f9a1ea603427ba18770924088b2f5039b38` 与 annotated 标签 `v2.8.35` 已推送并核验；GitHub Release 唯一 APK 附件的平台摘要及独立下载 SHA-256 均与本机一致。远端仅保留 v2.8.35 Release 和版本标签，本机历史 APK 保留。原有本地领先提交未推送，本次从远端基线在独立工作树发布。

**历史版本（2026-11-12，2.8.34/220）**：输入法收起后无法唤出修复、首次聚焦补验与实时报错卡片样式统一。根因：v2.8.33 的 `editorTapAction` 对“原生点击 + 已聚焦 + 连接可用”一律 `Ignore`，而 `BasicTextField2 1.6.8` 的原生点击对已聚焦字段不会再 `show()`，连接可用让应用层也沉默，形成死锁（按返回键收起键盘 / 关闭弹窗 / 悬浮窗放大后点击输入框无反应）。现判定改为未聚焦 → `AssistFocus`、连接失效 → `RestartSession`、已聚焦且键盘可见 → `Ignore`、已聚焦 + 连接可用 + 键盘不可见 → `RevealKeyboard`（只补一次 `show()`，绝不清焦；悬浮输入法 Insets=0 同样不重建）。首次聚焦沿用跨帧确认原生焦点、未落地才补 `requestFocus`，连接就绪且键盘仍不可见才补一次 `show()`；普通 `Ignore` 点击不取消进行中的恢复，发送 / 隐藏的显式取消不变。实时 Key 明细卡片对齐思考 / 状态胶囊：16dp 圆角、`errorContainer` 28% 底、1dp / 50% 描边、12/7 内边距、16dp `WarningAmber` 与折展箭头、`labelMedium + SansSerif + SemiBold`、最小高度 34dp，宽度随内容收缩并 `animateContentSize`；思考胶囊未改。涉及 `ui/components/EchoNativeTextField.kt`、`ui/screens/chat/ChatMessageComponents.kt`、`UiPolishRegressionTest.kt` 与版本配置；不新增依赖，不改 Room schema（37 / 20 个实体）、API / 消息 / 备份格式。新增 1 项回归并改写 1 项判定测试，套件 92 不变、总数 1,097 → 1,098。冻结验收 `20261112-220245-483` 脚本 / Gradle 退出 0，456.782 秒；92 套件 / 1,098 项测试全部执行，失败 / 错误 / 跳过均 0，Lint 0 Error / 105 Warning，输入哈希一致，`git diff --check` 退出 0。Release 构建 `assembleRelease --no-daemon` 退出 0，4 分 28 秒；`Echo-v2.8.34.apk` 25,505,557 字节，SHA256 `613278429ad43b5ffdc547ff9b8393ea1b9189631fd3aaebe103846beb2e9b97`；v2 签名，证书 SHA256 `939638f6d3e9af7f8a980e62af52d275fee73381f2130cc4e20a0d349f98e21f`，与上一版一致，Android Debug **非正式生产签名**。APK 446 条目、条目名集合与 2.8.33 一致，`resources.arsc` 逐字节不变、186 张位图按内容一致，DEX 字符串表 31,656 条与上版相同，隐私新增命中 0；发布目录 229 文件（228 APK）→ 230 文件（229 APK），历史包保留。本版未做真机安装验证（平板锁屏休眠），三条复现路径与报错卡片样式待人工验收。

**历史版本（2026-11-11，2.8.33/219）**：输入框点击重复显示与同帧清焦。`BasicTextField2 1.6.8` 原生点击已负责聚焦与显示，应用观察器在未聚焦 / 连接可用时改为让行，不再叠加 `requestFocus` / `show()`；仅“已聚焦 + 连接失效”进入延迟复核，最多 8 帧实时采样，恢复即取消，连续失效才清焦，等待期间失焦即让出。删除 `ImeAwareKeyboardController` 与 `LocalSoftwareKeyboardController` 替换，避免键盘收起动画期间吞掉原生唯一显示请求；连接查询异常按非破坏路径处理。涉及 `ui/components/EchoNativeTextField.kt` 与 `UiPolishRegressionTest.kt`；不新增依赖，不改 Room schema（37 / 20 个实体）、API / 消息 / 备份格式。当前源码测试总数 1,093。冻结验收 `20261111-225850-101` 脚本 / Gradle 退出 0，845.979 秒；92 套件 / 1,093 项测试全部执行，失败 / 错误 / 跳过均 0，Lint 0 Error / 105 Warning，输入哈希一致，`git diff --check` 退出 0。Release 构建 `assembleRelease --no-daemon --build-cache` 退出 0，638 秒；`Echo-v2.8.33.apk` 25,505,561 字节，SHA256 `9fdbf3cf8543af793b271d9e9db2f49d784f81fb769decbc1d3e98961bb513a5`；v2 签名，证书 SHA256 `939638f6d3e9af7f8a980e62af52d275fee73381f2130cc4e20a0d349f98e21f`，与上一版一致，Android Debug **非正式生产签名**。APK 446 条目、CRC 全通过，5 项变化，`resources.arsc` 与 186 张位图不变，隐私新增命中 0。版本提交 `4ea5a42702ffd6acde4ba112a4394a3f4a6d49da` 与 annotated 标签 `v2.8.33` 已推送核验；[Echo 2.8.33 Release](https://github.com/zhuqichen505-design/chat-Echo/releases/tag/v2.8.33) 为 Latest，唯一附件的平台 digest 与独立下载 SHA256 / 大小均与本机一致，正文与 `release-notes/latest.md` 一致；远端仅保留 v2.8.33，本机历史标签与 APK 保留。

**历史版本（2026-11-10）**：2.8.32/218 报错贴底、输入框点击稳定性与 API 配置页三段式；基线 `main/3692eca`。滚动侧新增 `ChatFollowSignal`（正文 / 思考长度 + 状态胶囊文案 + 实时 Key 明细条数）替代裸长度采样，等待与报错阶段同样触发贴底；`ChatViewModel` 新增 `errorScrollRequest`，`saveErrorReply` 在非用户停止时递增，界面按发送贴底的同一套采样（0/90/180/360/540/720ms）跟随，拖动即让出。输入侧删除 `rememberImeHiddenAfterVisible`（组合期 Insets 单帧误报会让每次点击都清焦重建），`editorTapAction` 改以 `inputConnectionActive` 优先判定：未聚焦→`AssistFocus`、连接失效→`RestartSession`、输入法可见→`Ignore`、其余→`RevealKeyboard`；`reopenEditorKeyboard` 的清焦分支不再提前返回，必定重新聚焦并补 `show()`，已聚焦字段不重复 `requestFocus`。API 配置页去掉 `ScrollableTabRow` 与三个独立滚动状态，改为单 `LazyColumn` 的连接 → 密钥 → 模型三段，新增 `ApiConfigSectionHeader`（序号 + 标题 + 摘要）；新增 `ApiKeyRotationManager.hasSettings`（按策略前缀任意键判断，兼容无开关键的旧策略）与 `initialRotationSettings(loaded, isNewConfig)`，新建配置且该地址未保存过策略时多 Key 轮询默认关闭。涉及 `ChatScrollPolicy.kt`、`ChatScreen.kt`、`ChatViewModel.kt`、`EchoNativeTextField.kt`、`ChatInputComponents.kt`、`SettingsApiConfigDialog.kt`、`SettingsApiKeyRotation.kt`、`ApiKeyRotationManager.kt`、`UiPolishRegressionTest.kt`、`NetworkAndKeyStabilityTest.kt` 与版本配置；不新增依赖，不改 Room schema（37 / 20 个实体）、API 地址、请求协议、消息格式或备份格式。新增 3 项测试，套件 92 不变、总数 1,089 → 1,092。最终冻结验收 `20261110-214606-202` 脚本 / Gradle 退出 0，197.803 秒；92 套件 / 1,092 项测试实际执行，失败 / 错误 / 跳过均 0，Lint 0 Error / 105 Warning，输入哈希一致，`git diff --check` 退出 0。Release 构建退出 0，421.3 秒；`Echo-v2.8.32.apk` 25,505,561 字节，SHA256 `1497f7c90770ecc7cbffd5510dae0c5b6c43425367aebc6a0f65b84361a82e40`，证书 SHA256 `939638f6d3e9af7f8a980e62af52d275fee73381f2130cc4e20a0d349f98e21f`，Android Debug **非正式生产签名**；APK 446 条目且条目名集合与 2.8.31 一致，清单、版本控制信息、两个 baseline profile、DEX 共 5 项变化，DEX 31,657 条字符串（唯一 +33 / -21），新增可读中文 7 条均为本版提示，`resources.arsc` 逐字节不变，186 张位图资源一致，隐私扫描新增命中 0。发布目录由 227 文件（226 APK）排他复制为 228 文件（227 APK），历史包保留。版本提交 `99a36f997f8772a8a8d41998f12d9051c1bcd4c8` 与 annotated 标签 `v2.8.32` 已推送并核验远端 main / peeled 标签一致；[Echo 2.8.32 Release](https://github.com/zhuqichen505-design/chat-Echo/releases/tag/v2.8.32) 为 Latest，唯一 APK 附件的 GitHub digest 与 `gh release download` 资产通道下载 SHA256 / 大小均与本机一致，Release 正文与 `release-notes/latest.md` 逐字一致。按仅保留最新版策略已删除远端旧 v2.8.31 Release / 标签；本机 v2.8.31 标签与全部历史 APK 保留。

**历史已发布 2.8.31/217（2026-11-10）**：2.8.31/217 输入会话稳定性、发送贴底设置与 API 配置页排版；基线 `main/c77b6f1`。输入侧新增 `isInputConnectionActiveNow`（`InputMethodManager.isAcceptingText`），`EditorGestureStart` 增加 `inputConnectionActive` / `imeHiddenAfterVisible` 并在 Initial pass 采样；新增 `EditorTapAction.RevealKeyboard`，已聚焦且输入连接可用时只 `requestFocus + show()`、绝不清焦，只有连接失效或观察到“输入法显示后被收起”才清焦重建；草稿恢复光标置于末尾。滚动侧新增 `autoScrollToBottomOnSend`（默认开启），入口“设置 → 界面与外观 → 对话滚动”；开启时按 0/90/180/270/450/630/810ms 采样贴底并覆盖键盘收起动画，关闭时发送保持阅读位置并关闭本轮自动跟随。API 配置页改用 `EchoScaffold` 壁纸与玻璃顶栏，三页签分组为 `SettingsGlassCard`，密钥卡片改两行布局并保留竖排上下移，内层圆角统一 12dp。涉及 `EchoNativeTextField.kt`、`ChatInputComponents.kt`、`ChatScreen.kt`、`SettingsAppearanceTab.kt`、`PersonalizationManager.kt`、`SettingsApiConfigDialog.kt`、`UiPolishRegressionTest.kt`、`RetryPreferencesTest.kt` 与版本配置；不新增依赖，不改 Room schema（37 / 20 个实体）、API 地址、请求协议、消息格式或备份格式。新增 4 项测试，套件 92 不变、总数 1,085 → 1,089。最终冻结验收 `20261110-121922-014` 脚本 / Gradle 退出 0，680.846 秒；92 套件 / 1,089 项测试实际执行，失败 / 错误 / 跳过均 0，Lint 0 Error / 105 Warning，输入哈希一致，`git diff --check` 退出 0。Release 构建退出 0，5 分 54 秒；`Echo-v2.8.31.apk` 25,505,561 字节，SHA256 `A0E0DB41B736699F8FA2F95BE185761D81DC3CF240097EA4FA0431B634DC82C9`，证书 SHA256 `939638f6d3e9af7f8a980e62af52d275fee73381f2130cc4e20a0d349f98e21f`，Android Debug **非正式生产签名**；APK 446 条目且条目名集合与 2.8.30 一致，清单、版本控制信息、两个 baseline profile、DEX 共 5 项变化，DEX 31,645 条字符串（+57 / -32），新增可读中文 12 条均为本版提示，`resources.arsc` 逐字节不变，位图资源无变化，隐私扫描新增命中 0。发布目录由 226 文件（225 APK）排他复制为 227 文件（226 APK），历史包保留。版本提交 `4b187245be19d639b3dae67a772c680e7ce7e746` 与 annotated 标签 `v2.8.31` 已推送并核验远端 main / peeled 标签一致；[Echo 2.8.31 Release](https://github.com/zhuqichen505-design/chat-Echo/releases/tag/v2.8.31) 为 Latest，唯一 APK 附件的 GitHub digest 与 `gh release download` 资产通道下载 SHA256 / 大小均与本机一致，Release 正文与 `release-notes/latest.md` 逐字一致。按仅保留最新版策略已删除远端旧 v2.8.30 Release / 标签；本机 v2.8.30 标签与全部历史 APK 保留。

**历史已发布 2.8.30/216（2026-11-09）**：2.8.30/216 搜索对话重做、发送贴底、API 配置页面化、停用配置过滤与输入法闪烁修复；基线 `main/a01f0dc`。搜索改为标题与消息内容并集，查询词去空白、折叠空格、忽略大小写、最多 4 词 AND；关键词高亮，消息片段围绕首个命中截取；点击消息结果经 `chat/{id}?focus=` 进入对话并滚动定位，命中未选中的变体时先切换变体组，并先占住初始滚底标志避免“先滚到底再跳回”；搜索中显示进度，超过 200 条提示截断；首页标题无命中时回退检索消息内容，并可带关键词跳转历史。数据层新增四词 AND 查询（空词跳过、排除 hidden/private、`LIMIT 201` 判断截断），未改 Room 版本与表结构。发送后在 0/90/180/270ms 四个时间点贴底，用 `collectIsDraggedAsState` 判断用户接管并立即让出。`ApiConfigDialog` 改为 `ApiConfigEditorScreen` 全屏页（路由 `api_config?configId=`，0 为新增），三页签占满高度，保存流程内聚并在保存中禁用返回，重试沿用已落库 ID。停用配置与模型：辅助选择器改读 `getEnabledApiConfigs()` 并二次过滤；自动命名 / 思考链翻译 / 记忆提炼按 `AuxiliaryModelPolicy` 跳过停用配置、停用模型回退到配置默认或首个启用模型；删除 `getAllApiConfigs().first()` 兜底，首页只暴露启用配置。代码整理：IME 可见性三处合并为 `isImeVisibleNow`，合并两个 `LaunchedEffect(isGenerating)`，`isInputExpanded` 改为 `customInputHeightDp` 派生，删除未被生产路径使用的 `hasNewUserMessage`；输入法闪烁修复保留 `EditorGestureStart` 采样、`EditorTapAction` 三态与 `ImeAwareKeyboardController`。涉及 `MainActivity.kt`、`Daos.kt`、`AiRepository.kt`、`EchoNativeTextField.kt`、`ChatInputComponents.kt`、`ChatScreen.kt`、`ChatScrollPolicy.kt`、`ChatViewModel.kt`、`HistoryScreen.kt`、`HomeScreen.kt`、`HomeViewModel.kt`、角色 / 场景编辑与工作室导入、`SettingsScreen.kt`、`SettingsApiConfigDialog.kt`、`SettingsModelFeaturesTab.kt`、`SettingsUniversalModelPicker.kt`、新增 `helpers/AuxiliaryModelPolicy.kt`、`ui/search/*` 与版本配置；不新增依赖，不改 Room schema（37 / 20 个实体）、API 地址、请求协议、消息格式或备份格式。新增 26 项测试（搜索策略 13、路由编码 3、真实 SQLite DAO 3、辅助模型策略 7），套件 88 → 92、总数 1,059 → 1,085。最终冻结验收 `20261109-022755-694` 脚本 / Gradle 退出 0，453.334 秒；92 套件 / 1,085 项测试实际执行，失败 / 错误 / 跳过均 0，Lint 0 Error / 105 Warning，输入哈希一致，`git diff --check` 退出 0。Release 构建退出 0，6 分 16 秒；`Echo-v2.8.30.apk` 25,489,173 字节，SHA256 `fa12df35df51ce5612465ae23bd38bab09c27bda5e7bc1a0be8df16f13f5772b`，证书 SHA256 `939638f6d3e9af7f8a980e62af52d275fee73381f2130cc4e20a0d349f98e21f`，Android Debug **非正式生产签名**；APK 446 条目且条目名集合与 2.8.29 一致、CRC 全通过，清单、版本控制信息、两个 baseline profile、DEX 与两条 R8 重命名的 coroutines 服务描述符共 7 项变化，DEX 31,614 条字符串（+1,620 / -1,573），新增可读中文 18 条均为本版提示，`resources.arsc` 逐字节不变，186 张图片一致，隐私扫描新增命中 0。发布目录由 225 文件（224 APK）排他复制为 226 文件（225 APK）。版本提交 `526924dd5d6842601c4f714d4b976410bf72e765` 与 `v2.8.30` 已推送并核验远端 main / peeled 标签一致；[Echo 2.8.30 Release](https://github.com/zhuqichen505-design/chat-Echo/releases/tag/v2.8.30) 为 Latest，唯一 APK 附件的 GitHub digest（`sha256:fa12df35df51ce5612465ae23bd38bab09c27bda5e7bc1a0be8df16f13f5772b`）与大小 25,489,173 字节均与本机一致；直链与资产通道经本机代理下载返回 502 / Bad Gateway，未能完成独立下载复核（平台侧 digest 为服务端计算）。按仅保留最新版策略已删除远端旧 v2.8.29 Release / 标签；本机全部历史 APK 保留，本次新建并保留 v2.8.30 标签。核对发现 v2.8.25、v2.8.26、v2.8.27、v2.8.29 标签此前仅推送远端、本地从未创建，本次未补造。

**历史已发布 2.8.29/215（2026-11-08）**：2.8.29/215 输入框“能唤出输入法但无法输入文字”修复；基线 `main/81b23c1`，源码修复提交 `2852f2c`。根因：Compose 1.6.8 `BasicTextField2` 只在焦点重新获得时调用 `startInputSession()`；输入法关闭 InputConnection 后字段仍保持聚焦，旧会话已结束且不会重建，`show()` 只能唤起没有活动连接的键盘。2.8.28 的清焦重建条件取事件尾部状态——点击观察者在 Final pass 请求时字段已被自身手势聚焦，首次点击就清焦并拆掉刚建立的会话；清焦后只等一帧，焦点事务被合并时观察不到失焦，会话不会重建。临时方案：新增 `shouldRestartEditorFocus`，只有“手势开始前已聚焦且输入法不可见”才重建；`EchoNativeTextField` 在 `awaitFirstDown`（Initial pass）读取点击前聚焦状态，`rememberEditorKeyboardRequest` 请求签名改为 `(Boolean) -> Unit`（拖拽手柄传实时 `inputFocused`）；`reopenEditorKeyboard` 清焦后最多 3 帧确认失焦（未观察即放弃、不弹键盘），重新聚焦后最多 3 帧确认聚焦，再按需补 `show()`。2.8.27 的编辑器 14dp、输入框 / 首页搜索 22dp、代码块 10dp 固定内容形状保留。新增 2 项回归，测试总数 1,057。

涉及 `ui/components/EchoNativeTextField.kt`、`ui/screens/chat/ChatInputComponents.kt`、`UiPolishRegressionTest.kt` 与版本配置；不新增依赖，不改 Room schema（37 / 20 个实体）、API 地址、请求协议、消息格式或备份格式。最终冻结验收 `20261108-213912-107` 脚本 / Gradle 退出 0，574.434 秒；88 套件 / 1,057 项测试实际执行，失败 / 错误 / 跳过均 0，Lint 0 Error / 105 Warning，输入哈希一致，`git diff --check` 退出 0。Release 构建退出 0，5 分 28 秒；`Echo-v2.8.29.apk` 25,489,177 字节，SHA256 `dd2c2bfbf06f4c93c938f798667262342d805306c24c1dc1651d7a2b814c6e9e`，证书 SHA256 `939638f6d3e9af7f8a980e62af52d275fee73381f2130cc4e20a0d349f98e21f`，Android Debug **非正式生产签名**；APK 446 条目结构不变，仅清单、版本控制信息、两个 baseline profile 与 DEX 5 项变化，DEX 可读差异仅版本号与 UA，`resources.arsc` 与 186 张图片不变，隐私扫描新增命中 0。发布目录由 224 文件（223 APK）排他复制为 225 文件（224 APK）。版本提交 `097775d73cd835bd687586ee21a3a263ad8cff9a`（含源码修复 `2852f2c`）与 `v2.8.29` 已推送并核验远端 main / peeled 标签一致；[Echo 2.8.29 Release](https://github.com/zhuqichen505-design/chat-Echo/releases/tag/v2.8.29) 为 Latest，唯一 APK 附件的 GitHub digest 与资产通道下载 SHA256 / 大小均与本机一致，正文与已提交的 `release-notes/latest.md` 一致；按仅保留最新版策略已删除远端旧 v2.8.28 Release / 标签，本机历史标签和 APK 保留。

设备边界：无连接设备，未验证安装启动与以下交互；需人工复验发送后贴底与拖动让出、历史搜索（标题 + 内容、多词、高亮、点击定位、变体切换）、首页搜索回退与跳转、API 配置页滚动 / 键盘 / 保存 / 返回拦截、停用配置与模型在辅助选择器与执行路径中不出现，以及打字中点击输入框、连续快速点击、拖拽手柄、返回后再点、切换输入法与表情面板的闪烁与可输入性。若出现问题，请记录操作序列、输入法名称 / 版本与 Android 版本。

**历史已发布 2.8.28/214（2026-11-07）**：2.8.28/214 输入框偶发无法输入修复；基线 `main/f79d291`。根因为键盘重开事务用组合快照判断 `focused` / IME 可见性，跨帧 `clearFocus → requestFocus` 后快照可能仍为旧值，导致跳过 `showKeyboard()`，或 2.8.27 的“已显示则跳过”误判，表现为输入框看似聚焦但键盘不再弹出。现 `rememberEditorKeyboardRequest` 读取实时焦点 lambda（聊天输入栏 `{ inputFocused }`、通用编辑器 `{ focused }`），IME 可见性用 `ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime())` 查询（失败回退组合状态），`requestFocus` 后最多再等两帧确认焦点落地；保留“已聚焦且输入法确实可见时不重复重开”的防闪烁保护。2.8.27 的编辑窗口形状修复（编辑器 14dp、输入框 / 首页搜索 22dp、代码块 10dp 固定内容形状令牌）仍包含在本版。新增回归 `keyboardReopenWaitsForFocusToSettleAcrossFrames`，测试总数 1,055。

涉及 `ui/components/EchoNativeTextField.kt`、`ui/screens/chat/ChatInputComponents.kt`、`UiPolishRegressionTest.kt`、版本配置，以及 2.8.27 已发布的主题令牌、`EchoScrollableTextEditor`、`SyntaxHighlighter`、`HomeScreen`；不新增依赖，不改 Room schema（37 / 20 个实体）、API 地址、请求协议、消息格式或备份格式。最终冻结验收 `20261107-001738-718` 脚本 / Gradle 退出 0，517.513 秒；88 套件 / 1,055 项测试实际执行，失败 / 错误 / 跳过均 0，Lint 0 Error / 105 Warning，输入哈希一致，`git diff --check` 退出 0。Release 构建退出 0，4 分 12 秒；`Echo-v2.8.28.apk` 25,489,177 字节，SHA256 `a56839e2f4f7de9a5ae4cbe91d82c6b81f4c6e8da7daddb85c5054ed4c22b5d8`，证书 SHA256 `939638f6d3e9af7f8a980e62af52d275fee73381f2130cc4e20a0d349f98e21f`，Android Debug **非正式生产签名**；APK 446 条目结构不变，仅清单、版本控制信息、两个 baseline profile 与 DEX 5 项变化，DEX 可读差异仅版本号与 UA，`resources.arsc` 与 186 张图片不变，隐私扫描命中 0。发布目录由 223 文件（222 APK）排他复制为 224 文件（223 APK）。版本提交 `5c428fab21989c5377343542b8919d6e4cb7fb08` 与 `v2.8.28` 已推送并核验远端 main / peeled 标签一致；[Echo 2.8.28 Release](https://github.com/zhuqichen505-design/chat-Echo/releases/tag/v2.8.28) 为 Latest，唯一 APK 附件的 GitHub digest 与资产通道下载 SHA256 / 大小均与本机一致，正文与已提交的 `release-notes/latest.md` 一致；按仅保留最新版策略已删除远端旧 v2.8.27 Release / 标签，本机历史标签和 APK 保留。

设备边界：无连接设备，未验证输入法行为与编辑器形状；需人工复验正常点击输入、返回隐藏键盘后再点、发送后立即点击、快速连续点击、切换会话后返回、切换输入法 / 表情面板；并确认编辑模型回复 / 用户消息 / 系统提示词编辑器 14dp、首页搜索框 22dp、代码块 10dp。若仍偶现无法输入或闪烁，请记录输入法名称 / 版本与操作序列。

**历史已发布 2.8.27/213（2026-11-06）**：2.8.27/213 编辑窗口形状还原、内容形状统一与输入法防重复唤起；基线 `main/7186073`。v2.8.19 主题提交 `9febfaf` 将 `EchoScrollableTextEditor` 的固定 14dp 圆角替换为 `EchoGlassControlShape`，影响“编辑模型回复”、编辑用户消息与系统提示词窗口；同一批误改还包括首页搜索框（22dp）与 Markdown 代码块（10dp）。现于 `EchoTokens.Radius` 新增固定内容形状 `shapeInput = 22dp`、`shapeEditor = 14dp`、`shapeCodeBlock = 10dp`，分别用于对话输入框、首页搜索框、文本编辑器与代码块；通用按钮 / FAB / 首页主行动按钮仍跟随主题控件形状。输入法侧静态复核确认文字变更路径不调用焦点或键盘显示 / 隐藏；仅点击已聚焦且已显示输入法的输入区会重复 `requestFocus + show`，已在 `reopenEditorKeyboard` 中跳过该冗余重开并补回归测试；纯打字闪烁未找到应用侧触发点，需真机与输入法日志确认。

涉及 `ui/theme/EchoDesignTokens.kt`、`ui/components/EchoScrollableTextEditor.kt`、`ui/components/markdown/SyntaxHighlighter.kt`、`ui/components/EchoNativeTextField.kt`、`ui/screens/chat/ChatInputComponents.kt`、`ui/screens/home/HomeScreen.kt`、`UiPolishRegressionTest.kt` 与版本配置；不新增依赖，不改 Room schema（仍为 37 / 20 个实体）、API 地址、请求协议、消息格式或备份格式。最终冻结验收 `20261106-231321-141` 脚本 / Gradle 退出 0，435.416 秒；88 套件 / 1,054 项测试实际执行，失败 / 错误 / 跳过均 0，Lint 0 Error / 105 Warning，输入哈希一致，`git diff --check` 退出 0。Release 构建退出 0，5 分 10 秒；`Echo-v2.8.27.apk` 25,489,177 字节，SHA256 `9abdc71f5a8424a5b45aa3fd05ab1095edb3933b04af9b0b92432d4e0fd0e950`，证书 SHA256 `939638f6d3e9af7f8a980e62af52d275fee73381f2130cc4e20a0d349f98e21f`，Android Debug **非正式生产签名**；APK 446 条目结构不变，仅 DEX / 清单 / 基线配置 4 项变化，DEX 可读差异仅版本号与 UA，`resources.arsc` 与 186 张图片不变，隐私扫描命中 0。发布目录由 222 文件（221 APK）排他复制为 223 文件（222 APK）。版本提交 `cdeff252dc65161443cca3777c328e732d66aa53` 与 `v2.8.27` 已推送并核验远端 main / peeled 标签一致；[Echo 2.8.27 Release](https://github.com/zhuqichen505-design/chat-Echo/releases/tag/v2.8.27) 为 Latest，唯一 APK 附件的 GitHub digest 与资产通道下载 SHA256 / 大小均与本机一致，正文与已提交的 `release-notes/latest.md` 一致；按仅保留最新版策略已删除远端旧 v2.8.26 Release / 标签，本机历史标签和 APK 保留。

**历史已发布 2.8.26/212（2026-11-06）**：2.8.26/212 深色模式文字颜色修复与输入框形状还原；基线 `main/72a2c39`。深色黑字根因为 v2.8.19 主题提交 `9febfaf` 将根 `Surface` 从主题背景色改为透明，Compose `LocalContentColor` 退回黑色；同时首页、历史、文件夹、创作工作室、角色 / 场景编辑、记忆管理及故事工作台等透明 `Scaffold` 未显式指定 `contentColor`，未单独设定颜色的文字（如首页会话标题）按黑色绘制。现根 `Surface` 与上述所有透明 `Scaffold` 显式提供 `onBackground`，聊天页原有设置不变；输入框 `inputShape` 从主题化 `EchoGlassControlShape` 恢复为原始 `RoundedCornerShape(22.dp)`，与右上拖拽手柄 22dp 同心圆角重新吻合，其他控件继续使用主题形状。

涉及 `MainActivity.kt`、`HomeScreen.kt`、`HistoryScreen.kt`、`FolderManagerScreen.kt`、`CharacterEditorScreen.kt`、`NewRoleplaySessionScreen.kt`、`RoleplayMemoryScreen.kt`、`RoleplayStudioScreen.kt`、`ScenarioEditorScreen.kt`、`StoryUnifiedSettingsDialog.kt`、`ChatInputComponents.kt` 与版本配置；不新增依赖，不改 Room schema（仍为 37 / 20 个实体）、API 地址、请求协议、消息格式或备份格式，本轮无新增测试、既有 1,053 项全部保留。最终冻结验收 `20261106-172737-879` 脚本 / Gradle 退出 0，481.276 秒；88 套件 / 1,053 项测试实际执行，失败 / 错误 / 跳过均 0，Lint 0 Error / 105 Warning，输入哈希一致，`git diff --check` 退出 0。Release 构建退出 0（首次 8 分 10 秒，复核增量 23 秒）；`Echo-v2.8.26.apk` 25,489,177 字节，SHA256 `154aa64a8d1c7b774b66630d4e4b082a9d80d423ff9879544977561f5ea6c9dc`，证书 SHA256 `939638f6d3e9af7f8a980e62af52d275fee73381f2130cc4e20a0d349f98e21f`，Android Debug **非正式生产签名**；APK 446 条目结构不变，仅 DEX / 清单 / 基线配置 4 项变化，DEX 可读差异仅版本号与 UA，`resources.arsc` 与 186 张图片不变，隐私扫描命中 0。发布目录由 221 文件（220 APK）排他复制为 222 文件（221 APK）。版本提交 `1aa0dda682fd65fcb6cf8f931d23b2221adb3fc6` 与 `v2.8.26` 已推送并核验远端 main / peeled 标签一致；[Echo 2.8.26 Release](https://github.com/zhuqichen505-design/chat-Echo/releases/tag/v2.8.26) 为 Latest，唯一 APK 附件的 GitHub digest 与资产通道下载 SHA256 / 大小均与本机一致，正文与已提交的 `release-notes/latest.md` 一致；按仅保留最新版策略已删除远端旧 v2.8.25 Release / 标签，本机历史标签和 APK 保留。

设备边界：无连接设备，未验证安装启动与深色视觉；需人工确认深色下首页会话标题 / 说明、历史、文件夹、创作工作室、角色与场景编辑、记忆管理及故事工作台文字均为浅色可读，对话输入框恢复 22dp 圆角矩形，发送 / 搜索 / 思考等胶囊按钮形状不变。

**历史已发布 2.8.25/211（2026-11-01）**：2.8.25/211 备份导入容错与宽屏内容列适配；基线 `main/33f6cbd`。备份侧：删除 API 配置只删配置行，`api_usage_stats.apiConfigId`、`selected_models.apiConfigId`、`conversations.folderId` 等无外键列会保留悬空 ID，导出后被严格引用校验拒绝（2.8.24 才能走到该步）。现改为严格优先、命中“备份关联缺失”才回滚重试容错：可空列置空、NOT NULL 列的派生行跳过并计数，成功提示列出清理项；结构性引用缺失仍明确失败。宽屏侧：新增 `EchoAdaptiveLayout`，按窗口宽度分级并以对称留白形成居中内容列，设置 / 首页 / 历史 / 文件夹 ≤720dp，聊天消息列与输入栏共用 ≤900dp，手机布局不变。

涉及 `utils/BackupGraphStore.kt`、`utils/BackupManager.kt`、`ui/screens/settings/SettingsSecurityAndBackupTab.kt`、`ui/components/EchoAdaptiveLayout.kt`、`SettingsScreen.kt`、`HomeScreen.kt`、`FolderManagerScreen.kt`、`HistoryScreen.kt`、`ChatScreen.kt`、`ChatInputComponents.kt`、版本配置与 `BackupGraphStoreTest.kt`、`UiPolishRegressionTest.kt`。不新增依赖，不改 Room schema（仍为 37 / 20 个实体）、API 地址、消息格式或备份格式。新增 4 项回归：悬空可空引用严格回滚 / 容错置空、悬空非空引用容错跳过、留白分级与对称性、Compose 实际测量。最终冻结验收 `20261101-104659-583` 脚本 / Gradle 退出 0，509.388 秒；88 套件 / 1,053 项测试实际执行，失败 / 错误 / 跳过均 0，Lint 0 Error / 105 Warning，输入哈希一致，`git diff --check` 退出 0。Release 构建退出 0，339 秒；`Echo-v2.8.25.apk` 25,489,177 字节，SHA256 `730079c6aa1c159c08d3b3535524189db84fb210be3c87c79b2bd764f8426aad`，证书 SHA256 `939638f6d3e9af7f8a980e62af52d275fee73381f2130cc4e20a0d349f98e21f`，Android Debug **非正式生产签名**；APK 446 条目 CRC、31,571 DEX 字符串（新增 33 / 移除 16，新增可读中文 8 条均为容错提示与版本号），`resources.arsc` 逐字节不变，186 张图片一致。发布目录由 220 文件（219 APK）排他复制为 221 文件（220 APK）。

设备边界：无连接设备，未验证安装启动、真实备份导入与平板视觉；需人工复验容错导入提示、普通与单对话备份行为，以及平板 / 模拟器下各页面内容列居中。版本提交 `a8b83a3`（含 `9f615d0`、`b83178b`）与 `v2.8.25` 已推送，标签始终指向该提交，远端 main 以最新发布记录提交为准（`git ls-remote` 可复核）；[Echo v2.8.25 Release](https://github.com/zhuqichen505-design/chat-Echo/releases/tag/v2.8.25) 唯一 APK 附件的 GitHub digest 与 API 通道下载 SHA256 均与本机一致（匿名浏览器直链经本地代理返回 502，属代理侧问题）。按最新版策略已删除远端旧 v2.8.24 Release / 标签，本机历史标签和 APK 保留。

**历史已发布 2.8.24/210（2026-11-01）**：修复大备份导入 OOM，恢复改为 `restoreStream` 双遍流式读取（第一遍仅取身份与注入字段，第二遍逐行写入），新增 `guardSnapshot` 预检与 `RestoreResult` 真实失败原因；基线 `main/6e9db7e`。现象为选中备份文件后进程直接退出，宿主日志三次 `OutOfMemoryError`（`StringWriter` 一次申请 134,250,504 字节，堆上限 192 MiB），定位到 `BackupManager.restoreBackup` 第 120 行 `snapshot.readText`。

临时实施方案：A 新增 `BackupGraphStore.restoreStream`，`JsonReader` 两遍顺序读取同一文件——第一遍只取 `id`、`_uuid`、注入关系与自然键，其余 `skipValue`；第二遍逐行写入。ID 分配先于写入，会话取消仍在事务外；逐行默认值、引用映射、`variantGroupId` 与内嵌角色/场景改写、`apiKey` 处理、陈余行清理和外键检查抽为共享函数，整树入口与流式入口语义一致；身份映射按表释放，内存只与行数线性相关，未知表按前向兼容跳过。B 用 `guardSnapshot` 在解压前按 ZIP 中央目录大小与可用堆判定，扫描期记录单行峰值并在写入前拦截超大行；`restoreBackup` / `restoreBackupFromUri` 返回 `RestoreResult.Success / Failure(message)`，界面显示真实原因。

涉及 `utils/BackupGraphStore.kt`、`utils/BackupManager.kt`、`ui/screens/settings/SettingsSecurityAndBackupTab.kt`、`AiAssistantApp.kt`、版本配置与 `BackupGraphStoreTest.kt`。不新增依赖，不改 Room schema（仍为 37 / 20 个实体）、API 地址、消息格式或备份文件格式。新增 3 项回归：约 3.3 MB 快照连续导入两次的数据与行数一致、`complete=false` 在写库前被拒绝、预检纯函数边界。最终冻结验收 `20261101-025055-996` 脚本 / Gradle 退出 0，631.915 秒；88 套件 / 1,049 项测试实际执行，失败 / 错误 / 跳过均 0，Lint 0 Error / 105 Warning，输入哈希一致，`git diff --check` 退出 0。Release 构建退出 0，386 秒；`Echo-v2.8.24.apk` 25,489,173 字节，SHA256 `70d92de1794243970cb654246dbdebfd4d0cd1fe5c50254a215ebd17c388ad86`，证书 SHA256 `939638f6d3e9af7f8a980e62af52d275fee73381f2130cc4e20a0d349f98e21f`，Android Debug **非正式生产签名**；APK 446 条目 CRC、31,554 DEX 字符串（新增 60 / 移除 28，新增可读中文 16 条均为预检与失败原因），`resources.arsc` 逐字节不变，186 张图片一致。发布目录由 220 文件（219 APK）排他复制为 221 文件（220 APK）。

设备边界：无连接设备，未验证安装启动与真实导入；需用此前崩溃的大备份复验导入完整性与重启加载，并回归单对话、旧版与普通小备份。版本提交 `fad54d49576d0900fa6bcfa455e52cc89937f6b4` 与 `v2.8.24` 已推送并核验远端一致；[Echo v2.8.24 Release](https://github.com/zhuqichen505-design/chat-Echo/releases/tag/v2.8.24) 唯一 APK 附件的 GitHub digest 和匿名下载 SHA256 均与本机一致。按最新版策略已删除远端旧 v2.8.23 Release / 标签，本机历史标签和 APK 保留。

**历史已发布 2.8.23/209（2026-10-31）**：2.8.23/209 正式加入「拾页」「声呐」「构型」三套全场景主题，并进一步优化文字选择拖动的自动滚动；基线 `main/90df051`。主题拥有独立浅深语义色、字体、形状和材质：拾页纸纤维 / 页边线 / 文学排版，声呐网格 / 切角 / 等宽标题，构型粗描边 / 硬投影 / 几何色块。全局 Material 主题及共享卡片、弹窗、菜单、按钮、筛选、输入编辑器、Markdown、代码块、首页、对话、设置、历史、统计、文件夹和创作页面统一消费。滚动远距离上限由 88dp/s 调整为 144dp/s，近边缘精度、density、卡帧和松手停止保留；不改数据库、请求协议或消息格式。

临时实施方案与影响面：扩展 `EchoThemeStyle` 的纸材 / 网格 / 切角 / 硬投影 / 强描边语义，由 `Theme` / `Type` 生成全局 Shapes 与字体，`EchoPaperTexture` 以低 alpha 固定图案绘制在内容下方，不拦截触摸和选区；声呐在未自绘背景的创作等页面由根背景提供材质，Scaffold 改为透明容器；拾页仅对 Markdown 普通段落应用首行缩进，列表、代码、表格与原文字不改结构。选区滚动只改共享 `editorSelectionScrollStep`，四个编辑入口同步受益。

涉及 `PersonalizationManager.kt`、`ui/theme/*`、`MainActivity.kt`、共享 `EchoControls / EchoGlassCard / EchoHaze / EchoScrollableTextEditor / EditorInteractionPolicy / MarkdownText / SyntaxHighlighter`、首页 / 对话 / 设置 / 文件夹 / 创作页面、`RetryPreferencesTest.kt`、`UiPolishRegressionTest.kt` 及版本配置。最终冻结验收 `20261031-230059-368` 脚本 / Gradle 退出 0，583.941 秒；88 套件 / 1,046 项测试实际执行，失败 / 错误 / 跳过均 0，Lint 0 Error / 105 Warning，输入哈希一致，`git diff --check` 退出 0。Release 构建退出 0，`Echo-v2.8.23.apk` 25,472,793 字节，SHA256 `0c192a5543948a34a91390c1aa4bb037b7622d45f5cd6212b8daf5b6eefa257d`，证书 SHA256 `939638f6d3e9af7f8a980e62af52d275fee73381f2130cc4e20a0d349f98e21f`，Android Debug **非正式生产签名**；APK 446 条目 CRC、31,516 DEX 字符串 / 8,072 资源值与新增 24 条可读 DEX 字符串人工复核未决 0，186 张图片与 2.8.22 一致，新增资源字符串 0。发布目录由 219 文件（218 APK）排他复制为 220 文件（219 APK）。

设备边界：无连接设备，未验证安装启动、主题字体 / 材质视觉、输入法拖选、触控和真实服务商；人工需检查三主题明暗切换与重启持久化、主要页面 / 弹窗 / 编辑器对比和长文本拖选。版本提交 `9febfaf3eef17059c9d3a745aa242b0fffa99985` 与 `v2.8.23` 已推送并核验远端一致；[Echo v2.8.23 Release](https://github.com/zhuqichen505-design/chat-Echo/releases/tag/v2.8.23) 唯一 APK 附件的 GitHub digest 和匿名资产下载 SHA256 均与本机一致。按最新版策略已删除远端旧 v2.8.22 Release / 标签，本机历史标签和 APK 保留。

**历史已发布 2.8.22/208（2026-10-28）**：新增默认开启的全局“流式输出”开关，入口“设置 → 模型失败与重试”；基线 `main/f886e43`。开关用独立偏好键保存，旧设置草稿不会覆盖；对新请求生效，同一请求的重试和备用 Key 使用固定的输出方式。关闭后等待完整回复返回，不修改服务商地址、密钥或模型配置。

临时实施方案与影响面：用户提供的上游错误为 `streamGenerateContent is not supported`，并确认其他客户端也受影响；本轮提供可切换的非流式通道，不把服务商能力变化当作本地配置错误。OpenAI 兼容请求使用 `stream=false`、`Accept: application/json` 并移除 `stream_options`；Anthropic 完整 JSON 响应体解析正文 / 思考块、停止原因与用量。关闭时禁止摘要、命名、翻译及辅助任务进入流式保底；复用消息保存、变体、取消和统计。首个有效回复等待仍为 120 秒，非流式长推理可能触及边界；Room v37 / 20 个实体和消息格式不变。

涉及 `PersonalizationManager.kt`、`SettingsRetryControls.kt`、`SettingsScreen.kt`、`AiRepository.kt`、`Models.kt`、`RetryPreferencesTest.kt`、`ChatRetryRepositoryTest.kt` 及版本配置。第二轮完整 verify `20261028-113832-743` 退出 0，454.714 秒；88 套件 / 1,044 项测试全部执行，失败 / 错误 / 跳过均 0，Lint 0 Error / 105 Warning，输入哈希一致，`git diff --check` 退出 0。Release 构建退出 0，约 8 分 20 秒；`Echo-v2.8.22.apk` 25,472,793 字节，SHA256 `645e3019869f99e6a81963c70e82fb93c4ae351f67288f7a557f7710a9311410`；v2 签名退出 0，证书 SHA256 `939638f6d3e9af7f8a980e62af52d275fee73381f2130cc4e20a0d349f98e21f`，Android Debug **非正式生产签名**。APK 审计退出 0，新增 13 条记录 / 12 个唯一字符串人工复核未决 0，基线 20 条候选人工复核未决 0；发布目录原 218 个文件（217 个 APK）全部保留。版本提交 `4487fbf14ad82a1d026643fb49626add0d28d257` 与 `v2.8.22` 已推送，push 退出 0，远端版本标签已核验指向该版本提交。GitHub Release 已发布唯一附件 `Echo-v2.8.22.apk`，上传摘要及匿名下载 SHA256 均为 `645e3019869f99e6a81963c70e82fb93c4ae351f67288f7a557f7710a9311410`，publish 脚本退出 0，Release 正文与 `release-notes/latest.md` 一致。cleanup 退出 0，远端仅保留 v2.8.22，已撤下旧 v2.8.21 Release / 远端标签；本机历史标签和全部 APK 保留，原 218 个文件（217 个 APK）的名称 / 大小 / SHA256 均未改变。

上传前已核查完整 15 份暂存文件及提交 / 标签元数据：5 个候选均为公开链接、协议文案或 TEST-NET-1 合成测试夹具，人工复核全部通过，元数据未决 0；私有核查报告未上传。本轮发布后文档另行提交，提交与推送前须按变化后的完整候选再次核查。

设备与接口边界：无连接设备，未验证安装启动、触控及真实服务商是否允许非流式请求。

首轮 verify `20261028-112627-799` 退出 1，609.167 秒；88 套件 / 1,044 项测试，仅新增非流式取消回归失败 1，其余 1,043 项通过，错误 / 跳过 0；最终 Lint 任务未完成，不记为通过。回归发现主动 `Call.cancel()` 后 OkHttp 可返回 `Socket closed`，旧识别会误当网络错误重试；双协议异常出口已结合本次 `Call.isCanceled()` 规范化主动取消，保持传输及 `FirstTokenDeadline` 超时语义，未放宽测试。修复后重新冻结，最终结果见本轮验证记录。

**历史已发布版本（2026-10-27）**：2.8.21/207 修复对话内设定与时间线捕捉窗口被统一布局错误撑高、对话文本被抬升和页面空白问题；候选区仍显示在输入框上方，短内容自然收缩，长内容正文内部滚动；思考强度改为输入框上方紧凑卡片，减少说明并使用动态思考态 Bot 图标。影响限于 UI 布局和展示，不改请求协议、模型参数语义、消息数据格式或数据库，Room v37 / 20 个实体不变。

临时实施方案：根因是父级 `weight(1f)` 默认填满审阅区，底部栏高度被计入消息列表底部间距；本轮以 `ChatReviewPanelStack` 固定 `weight(1f, fill=false)` 并让状态提示紧跟候选区，再由 `ChatReviewInputLayout` 统一锚定到输入框上方。思考窗口移除独立居中 Dialog，改为输入栏同一布局中的 220dp 上限卡片，内部滚动，删除不必要说明，标题左侧使用 `BOT_ORB` 思考态动画。

涉及 `ChatPendingReviewPanels.kt`、`ChatScreen.kt`、`ChatInputComponents.kt` 与 `UiPolishRegressionTest.kt`；新增 6 项 Compose/Robolectric 几何回归，覆盖短 / 长候选、小屏和高输入栏、无候选、真实双候选、长时间线和思考卡片与输入框相邻。

最终冻结验收 `20261027-150815-700`：标准 verify / Gradle 退出 0，415.541 秒；88 套件 / 1,034 项测试失败、错误、跳过均 0，Lint 0 Error / 103 Warning，输入哈希一致，`git diff --check` 退出 0。Release 构建退出 0，约 381 秒。候选 APK 2.8.21/207、25,472,793 字节、SHA256 `4a726ef1ee506a5292d50f92b4b38eea07933b177d05f905565f47730c2616b0`，com.aiassistant、arm64-v8a、minSdk 26 / targetSdk 34 / compileSdk 34、不可 debug，证书 SHA256 `939638f6d3e9af7f8a980e62af52d275fee73381f2130cc4e20a0d349f98e21f`，Android Debug **非正式生产签名**。

完整 APK 隐私与完整性扫描退出 0：446 个 ZIP 条目 CRC 全通过，31,480 条 DEX 实际字符串 / 10,624 条资源字符串；16 个唯一新增字符串（17 条记录）逐项关联源码、DEX 类型 / 原型和 R8 构建结构复核，未决 0；186 张图片与已审 2.8.20 基线内容及元数据一致。排他复制退出 0，指定本机发布目录原 217 文件（216 APK）的名称 / 大小 / SHA256 保留，无覆盖或删除。 Git 提交 `0b6eeb12e2172101492c1e24b742949e27cf3647` 与 `v2.8.21` 已推送，远端 main / 标签指向一致；GitHub Release 已创建并发布唯一 APK，上传摘要与匿名下载 SHA256 均与本机一致：[Echo v2.8.21 Release](https://github.com/zhuqichen505-design/chat-Echo/releases/tag/v2.8.21)。按仅保留最新版策略已撤下新仓库旧 v2.8.20 Release 与远端标签；本机标签和全部历史 APK 保留。未连接真机或模拟器，自动测试和构建不能替代设备安装启动、输入法 / 触控、窗口位置和动态视觉验收。

**历史已发布版本（2026-10-27）**：2.8.20/206 为 Bot 状态颜色加入渐变，保留主动暂停与连接错误的区分，收紧正常正文的错误误判，并纳入此前已提交的输入法重复唤起修复。最终冻结验收 `20261027-132556-501`：标准 `pwsh -File scripts/verify.ps1 -GradleUserHome <本机可写缓存>` 脚本 / Gradle 退出 0，457.111 秒；88 套件 / 1,028 项完整单测 EXECUTED，失败 / 错误 / 跳过均 0，Lint 0 Error / 103 Warning，输入前后哈希一致，`git diff --check` 退出 0。 Release `assembleRelease --no-daemon --build-cache` 使用本机可写缓存，退出 0，262.502 秒。`Echo-v2.8.20.apk`：25,472,793 字节，SHA256 `4c4a0ed4346a4441de0ab8d2412d0ec441b973c5c1377cd6dcc675b12d2b1173`；com.aiassistant、2.8.20/206、minSdk 26 / targetSdk 34 / compileSdk 34、arm64-v8a、不可 debug。aapt / apksigner v2 校验退出 0，证书 SHA256 `939638f6d3e9af7f8a980e62af52d275fee73381f2130cc4e20a0d349f98e21f` 与上一版及项目 JKS 一致，Android Debug **非正式生产签名**。 完整 APK 隐私与完整性扫描退出 0：446 个 ZIP 条目 CRC 通过，31,550 条 DEX 实际字符串 / 10,624 条资源字符串、已知私人标记、签名块和图片元数据无未决项；45 个唯一新增字符串（46 条记录）逐条关联源码、DEX / R8 结构及公开构建元数据复核。186 张图片与已审 2.8.19 基线内容 / 元数据一致，changedImage 为 0。排他复制退出 0，指定本机发布目录原 216 个文件（215 APK）的名称 / 大小 / SHA256 均保留，无覆盖或删除。 Git 提交 `4062d2fd35b8a0dd18dc55c9f81db0f1b55ce319` 与 `v2.8.20` 已推送，远端 main / 标签指向一致；GitHub Release 已创建并发布唯一 APK，上传摘要与匿名下载 SHA256 均与本机一致：[https://github.com/zhuqichen505-design/chat-Echo/releases/tag/v2.8.20](https://github.com/zhuqichen505-design/chat-Echo/releases/tag/v2.8.20)。按仅保留最新版策略已撤下新仓库旧 v2.8.19 Release 与远端标签；本机标签和全部历史 APK 保留。Room v37 / 20 个实体不变。

**2.8.19 已发布主题（2026-10-26）**：基线 `main/75e40c4`。本轮正式接入 DECKLE（毛边文库）和 COTTON（棉纸诗笺）两套纸本主题，保留五个旧主题；另外两套纸本方案继续仅供预览。源码冻结验收、构建和 APK 隐私核查已通过。最终 verify `20261026-203246-800`：脚本/Gradle 退出 0，619.399 秒；88 套件 / 1,018 项测试 EXECUTED，失败/错误/跳过均 0，Lint 0 Error / 102 Warning，输入哈希一致，`git diff --check` 退出 0。Release 构建 `assembleRelease --no-daemon --build-cache` 退出 0，412 秒。 `Echo-v2.8.19.apk`：25,472,793 字节，SHA256 `2c7c49a120b438eaeaca89b89999c70f1aac9a11e5e58df97fa33936dc260cb1`；com.aiassistant、2.8.19/205、minSdk 26 / targetSdk 34 / compileSdk 34、arm64-v8a；aapt 与 apksigner v2 验证退出 0，证书 SHA256 `939638f6d3e9af7f8a980e62af52d275fee73381f2130cc4e20a0d349f98e21f`，Android Debug **非正式生产签名**。排他复制校验退出 0，原 215 个文件（214 个 APK）名称/大小/哈希保留。 APK 隐私扫描通过：446 个 ZIP 条目 CRC 全通过，31,534 条 DEX 实际字符串、10,624 条资源字符串、已知私人标记、签名块与图片元数据扫描未决 0；新增可读字符串已逐条关联主题/纹理/组件源码人工审阅，186 张图片与 2.8.18 基线内容和元数据一致。 版本提交 `bbf04cde5d694ac97d22031b73543c6fdb45a225` 与 `v2.8.19` 已推送且远端一致，核验时无待推提交。[Echo v2.8.19 Release](https://github.com/zhuqichen505-design/chat-Echo/releases/tag/v2.8.19) 创建并上传唯一手动 APK，GitHub 摘要与匿名下载 SHA256 一致，publish / cleanup 均退出 0；远端仅保留 v2.8.19，本机旧标签与 215 个历史文件（214 APK）保留。

- DECKLE / COTTON 的浅深配色、主题选择和持久化已接入设置；Room v37 / 20 个实体、请求协议和消息数据不变。
- `EchoPaperTexture` 以固定种子生成半透明噪点与短 / 长纤维缓存 Shader，纹理绘制在文字下方且不拦截触摸；首页、聊天、设置背景以及 `EchoGlassCard`、`EchoHazePanel`、`EchoGlassDialog` 共享组件统一挂载。
- `Type` 使用既有 `echo_literary_regular` 字体，`EchoThemeStyle` 提供纸本行距、字距和低圆角平面；`MarkdownText` 仅调整普通段落（毛边 2em 首行缩进，棉纸 5dp 段距），列表 / 代码 / 表格 / 原文字不变。
- 前轮 Bot 状态符号右移与颜色引用误判修复随本轮进入包。

临时实施方案与影响面：原主题系统主要提供颜色和控件样式，缺少纸张材质层，且 Markdown 排版无法区分普通段落与结构化内容。本轮新增 `EchoPaperTexture.kt`，在 `Theme.kt` / `EchoThemeStyle.kt` / `Type.kt` 统一主题语义；更新 `HomeScreen.kt`、`ChatScreen.kt`、`SettingsScreen.kt`、`SettingsAppearanceTab.kt`、`EchoGlassCard.kt`、`EchoHaze.kt`、`MarkdownText.kt`、`PersonalizationManager.kt`，并扩展 `RetryPreferencesTest.kt`、`UiPolishRegressionTest.kt` 等回归覆盖。纹理只负责视觉层，不改变点击、滚动、选择或请求链路。

未连接真机或模拟器；自动测试、浏览器预览和构建成功不能作为安装启动、键盘 / 触控、设备动画或真实接口验收。

**2.8.18 已发布修复（2026-10-26）**：基线 `main/ba0d4e4`。源码和测试冻结后的完整验收、Release 构建、产物 / 签名及 APK 隐私核查通过；版本提交与标签已推送，Release 唯一手动 APK 附件及匿名下载核验通过。

- 输入栏思考按钮匹配原智能搜索的窄尺寸；智能搜索按文字自然撑开并居中。思考窗口恢复 `EchoPillSlider`，无外框档位文字与吸附点对齐；大字体空间不足时可横向查看，`Default` 仅显示英文名称，参数省略语义不变。
- 时间线 / 设定候选使用自然内容高度及可用高度上限；只在内容超限时滚动正文，不强制撑满整个窗口，保留固定标题、操作和草稿。
- `EchoNativeTextField` 区分较早回传值与真实外部替换，保留选区 / IME 组合输入；点击内容空白或键盘关闭后的再次点击可重新请求焦点和输入法。共享编辑器拖选自动滚动限速，覆盖输入、回复编辑与提示词等入口。
- 暂停时约 900ms 闭眼收势，错误时约 1500ms 摇头并变为 `><` 眼睛；历史状态和减弱动态使用落定表情。Bot 本体对齐整个胶囊中心，右上状态符号不影响该锚点；折叠胶囊按当前可见单行测宽，消除可选 Token 隐去后的尾部空白。
- Bot 浏览器预览同步动作参数；四套纸本主题保持仅预览、未应用，不改变主题偏好。用户截图未复制入仓库。本版无数据库迁移、API 请求或用户数据格式变更。

未连接设备，自动测试和浏览器预览不能作为安装启动、键盘 / 触控、设备动画或真实接口验收；走查步骤及本轮源码 / 测试文件清单见 `walkthrough.md`。

最终 verify `20261026-141333-381`：脚本 / Gradle 退出 0，290.776 秒，88 套件 / 1,013 项完整单测 EXECUTED，失败 / 错误 / 跳过均 0，Lint 0 Error / 102 Warning，输入哈希一致；`git diff --check` 退出 0。浏览器参数重建预览的 4 种宽度和 37 帧过渡检查通过，错误 0。

Release `assembleRelease --no-daemon --build-cache` 退出 0，391.35 秒；`Echo-v2.8.18.apk` 为 25,472,501 字节，SHA256 `1ace8d21fe20874f46e941d39a531e18838bbbe5bf3f67879b91beaed5d0e491`，`com.aiassistant`、2.8.18/204、minSdk 26 / targetSdk 34 / compileSdk 34、arm64-v8a、不可 debug。aapt 与 apksigner v2 验证均退出 0；证书 SHA256 `939638f6d3e9af7f8a980e62af52d275fee73381f2130cc4e20a0d349f98e21f` 与项目 JKS / 旧包一致，Android Debug **非正式生产签名**。指定目录排他复制退出 0，原 214 文件（213 APK）名称、大小及 SHA256 均未变，没有覆盖或删除。

APK 隐私核查通过：446 个 ZIP 条目 CRC、DEX / 资源与已知私人标记、签名块 / 图片元数据均已扫描，新增可读项逐条人工审阅并关联源码，未决 0；186 张图片与已审基线内容 / 元数据一致，未发现用户数据或凭据。未知混淆私人值、设备数据和平台缓存不在保证范围；提交 / 上传候选仍需独立核查。

发布结果：版本提交 `1e16b10028c412bc01912c374b83034451f4b268` 与 `v2.8.18` 已推送并和远端一致，核验时无待推提交。[v2.8.18 Release](https://github.com/zhuqichen505-design/chat-Echo/releases/tag/v2.8.18) 创建和唯一手动 APK 附件上传完成，发布退出 0，GitHub 摘要、匿名下载 SHA256 与本机 `1ace8d21fe20874f46e941d39a531e18838bbbe5bf3f67879b91beaed5d0e491` 一致。旧版处理退出 0，仅撤下新仓库旧 v2.8.16 Release、远端 v2.8.16 / v2.8.17 版本标签，远端仅 v2.8.18；本机旧标签与全部历史 APK 保留。此后六份维护文档单独收尾并核查提交，公开说明冻结，不新建 / 移动版本标签，不重复编译。

以下为历史版本与阶段记录，行为和验收数字不代表 2.8.18；当前行为以本节顶部为准。

**历史 2.8.17 发版补充（2026-10-26）**：

- Bot 暂停 / 错误状态加入 520ms 活动态进入与稳定落定；扁扁体态的右上符号使用独立锚点并通过画布边界测试。
- 四种纸本主题预览重做材质结构；Bot 预览加入暂停 / 错误 / 恢复动图。预览不应用到 App。
- Release `Echo-v2.8.17.apk`：25,456,117 字节，SHA256 `72bd79b22e3e6016b36b89eb83a64c3b85eb02b2be3049fa6daf89bf6dee45e9`，versionCode 203 / versionName 2.8.17，arm64-v8a，签名为 Android Debug **非正式生产签名**；证书摘要与 v2.8.16 及项目 JKS 一致。
- 最终 verify `20261025-235531-637` 退出 0，321.196 秒；88 套件 / 1,007 项测试，失败 / 错误 / 跳过均 0，Lint 0 Error / 102 Warning；Release 构建退出 0。

**历史源码完善（2026-10-25，当时未发版）**：

- 候选审阅区固定可用窗口，内部正文独立滚动；输入栏与 IME 空间按实际测量，长文可全屏编辑。
- Bot 动画另有浏览器预览，覆盖七种状态和暂停 / 错误 / 恢复过程；不改变 Android 当前主题设置。
- `ReasoningControls` 支持 Default（默认）省略强度语义；普通协议省略 effort，原生预算式 Anthropic 契约保持明确预算档位，不能用 Default 伪造省略预算。
- 思考与智能搜索按钮统一控件尺寸；强度弹窗改为统一弹窗、独立 FilterChip、8dp 间距和最小点击区域。
- `EchoNativeTextField` / `EchoScrollableTextEditor` 统一输入、回复编辑和系统提示词的原生选区 / 滚动坐标；`EchoTextToolbar` 修复窗口坐标与窄屏操作。
- `EchoDialogLayout` 统一弹窗安全区、16dp 横向 / 24dp 纵向边距、Standard 480dp / Expanded 640dp 宽度和 90% 最大高度；模型选择统一当前项圆点。
- 纸本预览保留真实页面结构并强化四种材质装帧差异，新增 Bot 动画预览与动图；均未应用到 App。
- 版本、Room v37 / 20 个实体不变；本轮未构建 APK。

最终冻结验收 `20261025-213354-276`：标准 verify 退出 0，340.69 秒；88 套件 / 1,006 项单测实际执行，失败 / 错误 / 跳过均 0；Lint 0 Error / 102 Warning，输入哈希一致，`git diff --check` 退出 0。

**纸本主题预览校正（2026-10-25）**：上一版概念样稿偏离实际页面，现按 HomeScreen、ChatScreen、ChatInputComponents、ChatSettingsDialogs 与 EchoAppShell 重建：真实首页 / 普通聊天 / 三页签对话设置弹窗；四种纸本材质保持同一组件结构，源码对应与示例设置在预览中列明。只改预览和维护记录，App 源码及偏好不变。浏览器 15 组布局、17 组交互通过，错误 0；标准 verify `20261025-203849-905` 退出 0，13.885 秒，Android 输入未变，编译 / 1000 项单测 / Lint 分析 UP-TO-DATE，沿用既有完整验收。

**历史源码更新（2026-10-25，当时未发版）**：

- `design/book-theme-preview/` 提供毛边文库、素纸线装、牛皮手札、藏书精装四套纸本交互样稿；真实会话首页、聊天与对话设置弹窗可切换，尚未接入或应用为 App 主题。
- Bot 标识及右上角符号放大，新增主动暂停和连接 / 回复失败状态，包含历史中断恢复、减弱动态和七状态预览。
- API 密钥页新增多 Key 轮询开关，关闭固定首个启用 Key；按地址及协议保存，旧设置默认开启，沿用现有独立策略备份边界。
- 模型发现以当前来源成功清单为准，将已添加但未返回的模型置顶；提供已添加 / 移除、完整懒加载列表和搜索。明确空数组为成功空清单，失败或畸形响应不标失效。
- 候选审阅面板按实际输入栏及 IME 分配高度，固定标题与操作、正文独立滚动；两类候选互斥展开、保留草稿及位置，长文可全屏查看编辑。
- 应用版本、Room v37 / 20 个实体不变；本次未构建、复制或发布 APK，最新公开安装包仍为 v2.8.16。

最终冻结验收 `20261025-182554-240`：`pwsh -File scripts/verify.ps1 -GradleUserHome <本机可写缓存>` 脚本 / Gradle 退出 0，294.672 秒；编译通过，88 套件 / 1000 项完整单测实际执行，失败 / 错误 / 跳过均 0；Lint 0 Error / 102 Warning，输入前后哈希一致，`git diff --check` 退出 0。

本次功能提交 `86614d9f63fd9e0f94a7c6dff6eb734c87d740ad` 已推送至 main；`git push --no-follow-tags origin HEAD:main` 与 `git ls-remote origin refs/heads/main` 均退出 0，远端与本机提交一致、无待推提交。提交前完整 32 个候选文件（含七张合成预览图）及待推提交 / 元数据核查通过，未决 0；原未跟踪素材保留。本条为功能提交后的纯文档收尾，不改验收源码、不新建版本标签或 Release。

**已发布更新（2026-10-23，v2.8.16）**：

- 捕捉到的设定、时间线和提取失败提示共用限高滚动区域，长内容可滑动查看；输入栏优先保留空间，标题为关闭按钮留位。
- 每次提取使用独立候选 ID，修复连续相同的总结失败或候选内容经状态合并后点击 × 无法关闭；旧窗口回调不会误关下一条候选。
- 主动暂停前尚未恢复的连接错误与已生成正文、思考一同保存，可展开查看和复制；已恢复错误继续清除，诊断内容不会进入后续模型上下文。
- 创建分支立即显示进度并阻止并发重复创建；隐藏输入关联改为一次消息快照重映射，避免长历史逐条重读。分支仍保留消息版本、设置及有效注入关联。
- 修复数字链接、脚注和分组引用的点击及网址解析，支持编号资料、Markdown 链接、裸网址与本轮搜索来源回退；缺失真实网址时明确提示。链接详情使用纵向操作区，避免“打开网页”按钮受长网址挤压反复换行。

**已发布 v2.8.16 验证**：最终冻结记录 `20261023-153343-364`：标准 `pwsh -File scripts/verify.ps1 -GradleUserHome <本机可写缓存>` 脚本/Gradle 退出 0，296.515 秒；88 个套件/988 项完整单测，失败/错误/跳过 0，编译及 Lint 通过；Lint 0 Error/102 Warning，输入哈希一致，`git diff --check` 退出 0。Release `assembleRelease --no-daemon --build-cache` 使用本机可写缓存，退出 0，376.18 秒。

**安装包**：`Echo-v2.8.16.apk`：25,406,965 字节，446 个 ZIP 条目且 CRC 全通过；com.aiassistant、2.8.16/202、minSdk 26 / targetSdk 34 / compileSdk 34、arm64-v8a、不可 debug。SHA256 `77774547a604d2228db74b0a3443228f9517b9fb4582315105a36c2c0aac88de`。v2 签名证书 SHA256 `939638f6d3e9af7f8a980e62af52d275fee73381f2130cc4e20a0d349f98e21f` 与 v2.8.15 及项目 JKS 一致，Android Debug **非正式生产签名**。

**隐私与历史保留**：完整 APK 内容扫描及人工差异检查通过，未决 0；186 张图片内容与元数据和上版一致。完整候选文件、暂存区、待推提交/标签与公共维护者元数据在每次上传前复核；原未跟踪内容、本机配置/凭据、日志、缓存和产物均排除。仅以排他创建新增唯一版本包，原有 212 个文件（211 个 APK）的名称、大小与 SHA256 均保留。未知私人值或专有二进制语义不能由通用模式完全保证。

**Git 与发布状态**：版本提交 `16b735b09ec57d0441160e07c1fcc65e1685407f` 与 `v2.8.16` 已推送并核验；最新版 Release 唯一 APK 附件、GitHub 摘要及匿名下载 SHA256 一致。仅撤下新仓库远端 v2.8.15 Release/版本标签，本机旧标签与全部历史 APK 保留。旧版撤下曾遇网络/TLS 中断，每次先只读复核状态；改用关闭连接复用的请求后完成，最终撤下与核验退出 0。

**升级与验收边界**：Room v37（20 个实体）不变；同 applicationId、同签名及更高 versionCode 满足保留数据覆盖升级条件，无需卸载或清除应用数据。未连接真机或模拟器，安装、启动、键盘/触控、动态布局与用户真实接口仍须设备验收。多次独立搜索对同一局部编号给出不同网址时，正文明确网址优先；没有明确映射的歧义或缺失来源不猜链接。

**技术栈**: Kotlin 2.2.21 + Jetpack Compose (BOM 2024.06.00) + Room 2.8.4 + Retrofit 2.9.0

> 工作流、最高准则与 APK 发布铁律的唯一权威版本在工作区根目录 `AGENTS.md`；本文件是项目事实卡，与其冲突时以 AGENTS.md 与代码为准。

---

## 维护规则（摘要，细则见 AGENTS.md）

当前开发/发布仓库为 chat-Echo，旧项目仅作本机历史或待审核来源。`APP改进/` 含大量错误或过时内容，默认不作为项目事实或实施依据；事实以当前源码、配置和可复核结果为准，完整约束指向工作区 AGENTS.md。“构建 APK”命令包含 Agent 执行的隐私审核、逐项同步、验证构建、新仓库推送与最新版 APK 发布；不是后台自动同步。冲突或疑点未确认停止相关操作，严禁带入旧 Git 历史。完整规则见工作区 AGENTS.md §2.1，仓库执行清单见 WORKFLOW_GUIDELINES.md。

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
   - 本机输出位置只使用工作区 AGENTS.md §4 指定的发布目录，不在公开文档复制机器路径；
   - 绝对严禁删除、覆盖或清理其中的任何历史版本安装包；
   - 每次发布仅增量复制唯一定名安装包 `Echo-v<version>.apk`，所有本机历史安装包必须永久保留。
6. 不允许提交本机敏感文件或构建产物，包括 `local.properties`、keystore、`.env`、`app/build/`、`.gradle/` 等。
7. **每次上传前必须进行隐私核查**：完整暂存文件、待推送提交/标签/元数据及所有附件均须检查；不包含用户任何个人信息或隐私，疑点未确认即禁止上传。自动扫描结合人工复核，报告只写脱敏结果；详见工作区 AGENTS.md §6 与 [WORKFLOW_GUIDELINES.md 的强制隐私核查](WORKFLOW_GUIDELINES.md#强制隐私核查)。

---

## 数据库结构（v37，实体清单以 `data/local/AppDatabase.kt` 为准）

| 实体（表） | 说明 |
|------|------|
| Folder | 文件夹 |
| ApiConfig | API 配置 |
| Conversation / Message | 对话与消息（会话 enableSessionMemory/enableTimeline/enableReplyDirections/replyDirectionCount，以及 enableInputInjection/inputInjectionTrigger；消息 modelName/replyDirection、模型正文 modelContent 和 injectionTargetMessageId） |
| ApiUsageStat | 使用统计；请求用途、Token 来源、完成状态与首段等待 |
| EnvironmentVariable | 环境变量（加密存储） |
| PromptTemplate | 提示词模板 |
| MemoryItem | 全局与会话专属记忆设定 |
| ConversationBranch / SelectedModel | 会话分支 / 模型选择与排序 |
| CharacterProfile / CharacterTag / CharacterTagCrossRef | 角色卡 / 角色标签及关联 |
| RoleplayScenario / RoleplaySession / RoleplayMemory | 角色扮演场景 / 会话 / 记忆 |
| WorldBook / WorldBookEntry | 世界书 |
| TimelineNode | 剧情时间线 |
| BackupIdentity | 跨设备实体稳定身份（backup_identities） |

迁移链至 `MIGRATION_36_37`（`data/local/migrations/AppDatabaseMigrations.kt`）；Room schema 导出至 37。v35→v36 增加 `conversations.enableTimeline` nullable 列，旧会话按默认开启处理；v36→v37 增加会话 `enableInputInjection`（默认 0）/`inputInjectionTrigger` 与消息 `modelContent`/`injectionTargetMessageId` nullable 列。迁移保留旧数据，新功能默认关闭，迁移与备份回归列入本轮验收。修改实体必须 version+1 并新增 Migration，禁止 destructive migration。

---

## 核心功能

1. **API配置管理** - 支持OpenAI兼容格式 + Anthropic格式
   - **HTTP 访问权限**：设置独立入口；默认关闭、名单空，添加不自动开启、关闭保留。按精确主机/IP/端口/路径段匹配，可省略 http://，拒绝通配符/账号/查询参数。network_security_config 静态开启传输，HttpAccessPolicy 动态默认拒绝，新增自有客户端必须 guard；不关闭 TLS 校验。权限保存在 http_access_permissions，不随便携聊天备份迁移。HTTP 不加密消息/Key，仅可信网络使用；组网仍需手机可达。
2. **对话功能** - 流式响应、思考模式、文件上传、Markdown渲染；思考能力以接口显式字段优先、具体型号资料补充，按接口/协议/模型隔离缓存，设置/胶囊/请求共享原始档位。模型名仅展示层格式化；普通对话的记忆与世界书位于提示与记忆分区，故事工作区可直接切换会话设定、时间线与世界书；连接生成保持稳定挂载、按实际底部溢出距离跟随。
   - **输入注入回复**：对话设置 → 提示与记忆，默认关闭。界面显示原输入 P 与原模型回复 A，后台保存 A + 两个换行 + P，再以用户自定义触发语 T 发出请求；不添加标题、标签或固定模板。触发语不能仅为空白，失败重试不重复注入，分支/备份按本地消息 ID 映射关联，关闭后新消息恢复普通流程。
   - **编辑与滚动摘要**：手动编辑正文、自动追加输入均保留摘要正文、覆盖水位和时间戳；主动重新压缩/生成摘要才依据编辑后的原文重建，失败和取消保留上次摘要，迟到结果不能覆盖并发修改。
   - **摘要与直接截断**：拒绝字段、拒绝结束原因、安全说明及纯标题/空占位结构不作为有效摘要；既有异常摘要不覆盖原文，成功重建后替换。LT/6 按当前会话 10%～90% 截断比例从最早完整轮次计算，置顶和最新一轮保留，不调用/注入摘要、不删除聊天，超预算不自动扩大截断范围；LC/5 仍表示保留比例。候选预览不改已确认用量，独立记忆/时间线/世界书沿用原设置。
   - **后台记忆提炼**：消息保存不调用模型，主聊天结束后一次提炼；辅助模型优先、不同会话活动模型备用、本地规则兜底，保存与候选复用。两类记忆关闭不请求，取消不兜底写入，返回后校验源消息/开关/恢复 epoch；原有思考内容清理、分类、冲突处理和角色隔离保留。
   - **思考 wire 兼容**：没有接口证据时不猜测 OpenAI-compatible thinking 扩展和显式 none；Anthropic 4.6 兼容回退保留 enabled/budget_tokens，有明确证据或仅支持 adaptive 的型号使用 adaptive/output_config，不给后者发送已移除的预算字段。显示、强度和原始模型 ID 保留。
    - **连接重试**：设置 → 模型失败与重试，总开关与默认展开的 19 类规则、0–20 次额外尝试、备用 Key 开关；每次失败立即提示原因，恢复输出后清除。无输出才重试、取消不重发；普通/角色回复与辅助模型共用分类规则，主请求首有效正文/思考等待 120 秒，正常输出后沿用 600 秒空闲读取。
   - **联网搜索**：Exa / Tavily / Mwmbl / SearXNG，选项只显示名字；请求结果数 1–100，Tavily 单次最多 20。Exa 托管/Mwmbl 免 Key；SearXNG 需要 HTTPS、允许 JSON 的用户实例，公共服务覆盖与配额不保证。
3. **会话分支** - 从任意消息点创建分支对话
   - **回复前选择方向**：对话设置 → 模型与生成提供开关与 2–4 个方向数量（默认关闭/2 个）；当前会话模型按真实上下文组织方向，再由用户选择后正常回复。「自行决定」从列表选一个；「其他」可填方向，留空采用列表外的新方向。等待可稍后继续、离开页面重进恢复（仅进程内），失败可重试/跳过/取消。仅最终回复进入历史与提炼，选定方向保存为本轮消息元数据，不自动成为长期设定；普通重生成重新选择，失败重试复用已选方向。开启增加耗时与费用，模型不编造历史依靠提示约束、非真实性保证。
4. **模型列表选择** - 可选择显示哪些模型
5. **使用统计** - 实际正式/辅助请求用途、上报/估算来源、重试/失败/中断、Token与缓存、首段等待、P50/P95完成耗时和端到端吞吐；供应商＋模型共同筛选，Long汇总。旧数据保持未分类，不反推缺失字段。
6. **数据备份** - 自动备份、手动备份、事务逻辑快照；新格式稳定身份支持单会话往返覆盖（编辑、删除同步），旧格式首次独立导入。
7. **提示词系统** - 预设模板、全局提示词
8. **环境变量** - 加密存储、变量引用
9. **首页布局与统一导航** - 设置可选聚焦对话（底栏）与侧栏工作台（左栏）；二者统一提供会话、创作、统计、设置四入口，选中状态与返回路径共用目的地定义。布局切换即时应用，导航保留页面状态；聊天和编辑等详情页保留原有完整空间。紧凑列表与“显示继续故事”独立开关对两种布局生效，继续故事只从可见会话关联的真实故事中选取，排除隐藏/私密会话。
10. **外观与阅读** - 墨白、晴空、夜航、索引四种配色及经典兼容默认，明暗模式保持独立；创作正文支持沉浸叙事、简洁正文、分栏上下文，资料栏读取已保存资料、宽屏并排/窄屏折叠。细光标、微声波、墨环用于流式与等待指示，系统关闭动画时静止。阅读排版与字体可分别应用到普通对话；历史头像/背景可删除，14款预设色（含纯白/纯黑）保持固定颜色。模型头像默认显示，关闭后有同心圆、轨道、棱镜、声纹，圆圆、方方、扁扁三款极简Bot及圆豆、方糖、团子、小方四款线条Bot；三款极简Bot保持自然等比例双眼，靠视线、体态与节奏区分连接、思考、输出和一次完成，历史消息保持静止，最新成功回复完成收势后可进入符号待机，设置可单独预览七状态（含主动暂停与连接 / 回复出错），思考气泡显示独立。首页、主题、阅读、动画独立持久化，应用内设置经偏好监听即时刷新；直接恢复备份 XML 后仍需重启应用。
   - 本版展示：沉浸用户消息使用透明底细线引用，保留附件与操作；仅字体作用域不改变气泡。外观阅读设置可隐藏普通/创作思考胶囊与正文，模型身份、必要请求状态和错误继续显示；请求和数据不受展示开关影响。

### 世界书与聊天行为

- **世界书导入**：角色与创作页、设置中的世界书入口可读取 SillyTavern `entries` 对象、Character Book `entries` 数组或角色卡内嵌 `character_book`；2 MiB / 2000 条上限，严格检查结构。预检后事务新增同名独立副本，整书默认关闭，导入不会自动绑定。
- **兼容边界**：这是基础内容导入。正则、宏、次关键词逻辑、位置/深度、概率等高级语义及未知字段会提示；受影响条目默认停用，原始扩展不保存，必须显式接受兼容损失。外部 `order` 只转为预算保留优先级，不保证原插入顺序，不提供无损往返承诺。
- **范围与触发**：世界书总开关外，书目选择有三种状态：未指定（`null`）使用全部已启用书、显式空选择不使用任何书、指定 ID 仅使用选中的已启用书。普通对话、角色创作及方向准备共用选择策略；压缩前最近 4 条有效原文与本轮输入参与匹配，折叠旧回复变体并排除不参与上下文的消息，不用全局教学文案触发词条，旧置顶不冒充近期消息。
- **预算与优先级**：统一默认预算为 1500 个估算 token，包含条目与标题包装成本；常驻、同类高优先级先入选，过大条目跳过后继续尝试较小条目。数字仅影响预算内保留顺序，不改变 API 指令权限。后台返回匹配/未命中/禁用/超预算等原因；完整可视诊断页仍待后续设计。
- **提示词与时间**：保留用户全局创作规则和故事要求，移除每张角色卡重复注入的固定文学铁律；人物卡描述人物、世界书提供背景、已确认时间线记录事件，摘要作为参考。空记忆时仍携带已明确的当前故事时间。
- **附件与保存**：重新生成恢复原图片/文件与 MIME，纯附件输入也可重试；损坏附件先报错并保留既有回复。故事角色、世界观及会话设置串行组合保存，保存后发送先等待完成，再读取新值。
- **滚动衔接**：流式回复及落库消息保持列表身份，结束生成不强制滚到回复开头；重新进入生成中的会话恢复活动回复宿主，保留手动阅读位置。
- **页面边界**：世界书导入、书目选择和使用说明已接入。首页方案 01 聚焦对话与 02 侧栏工作台已落地；03 创作书架、04 索引仪表盘、05 时间线叙事、06 一页任务仅为预览，完整世界书可视诊断页仍未实现。公开研究与后续范围见 [research.md](design/roleplay-rework/research.md)。

---

## 版本历史

| 版本 | 数据库 | 主要更新 |
|------|--------|----------|
| 2.8.25（已发布） | 37 | 备份导入容错（清理失效关联 / 跳过派生行并报告）；平板与宽屏内容列居中适配 |
| 2.8.24（已发布） | 37 | 大备份导入改为流式恢复，内存与备份体积解耦；新增导入预检与真实失败原因，消除 OOM 闪退 |
| 2.8.23（已发布） | 37 | 拾页 / 声呐 / 构型三套全场景主题；文字拖选远距离自动滚动由 88dp/s 优化至 144dp/s |
| 2.8.15（已发布） | 37 | 修复启动 HTTP 状态正则的 Android ICU 字符类兼容问题，补实际正则及状态分类回归；同签名覆盖升级 |
| 2.8.14（已发布） | 37 | 自动时间线 96 字准入、多 Key 健康调度与自定义冷却；删除上下文及来源记忆保护、备用 Key 恢复、停止状态与横幅退出修复 |
| 2.8.13（已发布） | 37 | 摘要拒绝/纯标题保护、按比例直接截断与分支压缩策略继承 |
| 2.8.12（已发布） | 37 | 输入注入回复、编辑保留摘要与主动重建；纳入摘要请求参数/推理预算/紧凑二次缩短、API 路径与正文解析，以及 bot/状态放大、时间线开关、凝练提取和紧凑展示 |
| 2.8.11（已发布） | 35 | 同参数压缩重试、摘要短请求和流式兼容、失败后正常聊天与事件提炼 |
| 2.8.10 | 35 | 剧情过程与自然时间跨度、设定增量维护、分支改名、创作流程完善及长会话压缩实际应用 |
| 2.8.6 | 35 | 作品工作区重写、历史删除与主题可见性、连续压缩/提取、实际请求计量 |
| v1.3.0 | v5 | 基础稳定版本 |
| v1.3.6 | v10 | 修复闪退、数据备份 |
| **v1.4.0** | **v11** | **会话分支、模型选择、缓存命中率** |
| **v1.7.2** | **v16** | **联网搜索、隐私对话、隐藏对话、模型默认选择、UI与流式输出优化** |
| **v1.7.18** | **v17** | **液态玻璃 token 统一、深色可读性、对话气泡/输入栏、统计表格与设置页 UI 修复** |
| **v1.8.0** | **v18** | **角色扮演系统架构与功能实现** |
| **v1.8.1** | **v18** | **规范文档与限制解除、角色扮演全链路闭环、图标与UI显示优化、支持原版本覆盖更新** |
| **v2.0.4** | **v23** | **分支功能完整重构与事务原子落库、分支生成弹窗确认与跳转、隐藏对话解锁会话维持** |
| **v2.0.5** | **v23** | **非破坏性增量备份导入、复制整个对话（普通与隐藏同步）、备份单对话（普通与隐藏同步）** |

> 上表为保留的历史摘要；`CHANGELOG.md` 仅说明当前发布版本，`UPDATE_LOG.md` 记录新仓库维护事项，两者不包含完整旧版本历史。

---

## 验证命令（PowerShell 7）

```powershell
# 在仓库根目录执行
pwsh -File scripts/verify.ps1                 # 一次启动：编译、全量单测、Lint
git diff --check
```

- 仅在用户明确要求构建 APK 时运行 `./gradlew.bat assembleRelease --no-daemon`，并执行 AGENTS.md 的审核与发布流程。
- 使用 JDK 17；若 `gradle.properties` 包含其他环境的 `org.gradle.java.home`，请在本机配置有效路径。
- Android SDK 由未跟踪的 `local.properties` 中 `sdk.dir` 指定；配置与签名文件不入库。
- Gradle 默认缓存不可写时，脚本用 `-GradleUserHome` 指向自己的可写缓存目录；公共命令不绑定本机路径。完整规则见工作区 AGENTS.md §2：冻结代码后统一验证、正常增量与本地缓存、日志只留本机，不因文档收尾重复全量检查。`-RerunTests` 只重跑测试；`-VerboseTests` 恢复详细日志；`-TestForks 1/2` 为测量覆盖，默认单进程。旧 `runUnitTestsDirectly` 现为标准全量单测别名，不再只运行四个类。
- 发布产物按 AGENTS.md §4 复制到指定本机发布目录，命名 `Echo-v<version>.apk`，禁止覆盖历史包。
