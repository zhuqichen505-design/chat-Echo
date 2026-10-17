# Echo 2.8.10

本版完善剧情时间线、会话设定与创作流程，并修复长会话压缩确认后未实际生效的问题。普通对话与创作共用这些改进，原始聊天记录继续保存在本机。

## 安装包

- 文件：`Echo-v2.8.10.apk`
- applicationId：`com.aiassistant`
- versionName / versionCode：`2.8.10` / `196`
- Android 8.0（API 26）及以上，`arm64-v8a`；targetSdk 34 / compileSdk 34，非 debuggable。
- 文件大小：25,341,425 字节；446 个 ZIP 条目。
- APK SHA256：`f8e991533ae2199abc9e80912ce240e1e190484361726c75c7faa078217417b1`。
- 签名：v2 签名验证通过，与 v2.8.9 证书一致；Android Debug 证书，**非正式生产签名**。
- 证书 SHA256：`939638f6d3e9af7f8a980e62af52d275fee73381f2130cc4e20a0d349f98e21f`。
- Room v35，20 个实体；本版不修改数据库结构，既有聊天、时间线、设定与备份保持兼容。

## 本版内容与入口

普通聊天和创作使用同一套时间线、会话设定与上下文策略。聊天页的上下文用量面板用于选档、确认参数、压缩较早历史、审阅摘要或取消整理；创作工作区可管理时间线、会话设定、人物与世界观资料。

- **剧情过程与详略**：时间线按分段来源提取，取消严格短句限制和最终二次压缩。多轮购物等剧情保留中间经过，细写内容允许较详细概述，自动补充保留已有事件。
- **时间跨度与顺序**：周/月跳跃、回忆和不确定时间按原文证据表达，避免机械续成下一天或强制分到早晚；没有明确日期时保留不确定性。
- **设定补充与分支命名**：已有设定按记录 ID 对照维护，同义内容不重复保存，补充与冲突修订可审阅；分支创建成功后，长按弹窗中的分支名称即可重命名。
- **创作资料与保存**：导入匹配完整资料库并隔离外部 ID；人物/世界观提案按实际变化字段维护，新增人物参与故事。智能追加保存失败保留结果，转换工作台前先保存。
- **长会话压缩实际生效**：确认 L2/L3/L4/自定义比例或调整同档参数后实际整理历史并刷新占用，追加少量消息不把已摘要的上千轮重新计回。保留完整轮次和置顶内容，原始聊天记录始终保存在本机。
- **原文与摘要范围独立**：浅档按预算恢复原文，L0/L1 不主动调用摘要模型。在这两个档位点击“应用滚动摘要并压缩”，会在确有可退休历史时采用保留最近 8 轮的滚动摘要策略，界面明确说明。
- **请求与状态保护**：发送等待已确认策略保存，取消摘要不丢档位，生成中的设置在下一轮生效。摘要、预览和实际请求使用当前 API 配置与模型窗口，超限回退只收紧窗口；复制会话映射有效摘要边界，普通设置不回写旧摘要或档位。

实现参考已核对的固定公开源码：[SillyTavern memory](https://github.com/SillyTavern/SillyTavern/blob/f3521e70075af7cd1c59c22d58385fe48e7da815/public/scripts/extensions/memory/index.js)、[mem0 prompts](https://github.com/mem0ai/mem0/blob/a488e19044e4de9322a4e82be8d49ee67a322151/mem0/configs/prompts.py)、[Aider](https://github.com/Aider-AI/aider/blob/5dc9490bb35f9729ef2c95d00a19ccd30c26339c/aider/history.py)、[LangMem](https://github.com/langchain-ai/langmem/blob/48e3c11f5bb527282c7d5339c6a87a0b35abccfc/src/langmem/short_term/summarization.py)、[OpenCode](https://github.com/anomalyco/opencode/blob/bc5ebf00a4c169ece10f276e6ce02eaf18c326e6/packages/core/src/session/compaction.ts)。借鉴增量维护、完整轮次、预算与覆盖记录思路，没有新增依赖或复制源文件。

## 验证与边界

本版 2.8.10/196 冻结后的标准 verify 与 Gradle 均退出 0，总 410.969 秒。compileDebugKotlin、完整 testDebugUnitTest、lintDebug 均实际执行（EXECUTED），85 套件/797 项测试全部通过，失败/错误/跳过 0，测试任务 77.333 秒；Lint XML 为 0 Error/95 Warning。输入前后哈希一致（inputsChangedDuringRun=false），git diff --check 退出 0。本次结果来自升版后的实际验收，通过后仅进行 Release 构建和文档收尾。

`assembleRelease --no-daemon` 退出 0，耗时 274.010 秒；aapt、apksigner、ZIP 与哈希核验均退出 0，包名、版本、SDK、架构与上述元数据一致。安装包排他复制及源/副本 SHA256 一致性核验退出 0，本机原有历史文件名称、大小与哈希全部保持不变。

安装包内容隐私复核退出 0：检查全部 446 个 ZIP 条目的 CRC、30,733 个 DEX 字符串、10,617 个 AXML/ARSC 字符串，以及 186 个图片的元数据、签名块、证书与已知私人值的全字节匹配；295 个新增唯一字符串逐项全文人工复核，无发现或未解决项。图片均与上版逐字节一致；3 个候选仅为标准 Android 资源命名空间，按精确条目和字符串哈希核实。自动扫描结合人工复核，不能保证识别任意未知或加密私人内容，外部平台缓存不在核验范围。

模型生成的摘要、时间线和设定仍可能遗漏或误判，请审阅名称、数值、否定条件、时间和待办，必要时修订。单条最新输入、置顶内容或设定本身超过窗口时仍会明确失败。合成 Room/本机 HTTP 测试不等于真实厂商模型语义评测；没有真机或可用模拟器，安装、启动、触控与真实接口效果尚未验收，升级前请备份。

发布附件仅包含本版 APK，不手动上传源码压缩包、历史包、日志或私有审核材料。GitHub 自动提供的 Source code zip/tar.gz 链接无法关闭；远端按仅保留最新版的策略维护，本机历史 APK 永久保留。
