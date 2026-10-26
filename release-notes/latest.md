# Echo 2.8.19

本轮正式接入毛边文库（DECKLE）与棉纸诗笺（COTTON），版本 2.8.19/205，Room v37（20 个实体）不变，基线 `75e40c4`。

- DECKLE / COTTON 提供完整浅色与深色配色、设置选择和持久化；五个旧主题保留，另外两套新纸本方案继续作为预览。
- 新增固定种子纸纹：半透明噪点与短 / 长纤维缓存 Shader 接入首页、聊天、设置背景以及共享卡片、雾化面板和对话框；纹理绘制在文字下方，不拦截点击、滚动或文本选择。
- 使用既有 `echo_literary_regular` 字体，提供纸本行距、字距和低圆角平面。普通 Markdown 段落按 DECKLE / COTTON 分别应用 2em 首行缩进或 5dp 段距，列表、代码、表格和原文字保持原排版。
- 前轮 Bot 右上符号右移与颜色引用误判修复随本轮进入构建。

临时实施方案：上一轮纸本方案主要停留在配色和装饰，正式 App 缺少共享材质与排版层；本轮由 `EchoPaperTexture`、`Theme`、`EchoThemeStyle`、`Type`、首页 / 聊天 / 设置页面、共享卡片 / 面板 / 对话框、`MarkdownText` 和 `PersonalizationManager` 共同接入。纹理采用固定种子和缓存，视觉层不改变交互或请求链路。

## 验收与安装包

最终 verify `20261026-203246-800`：脚本/Gradle 退出 0，619.399 秒；88 套件 / 1,018 项测试 EXECUTED，失败/错误/跳过均 0，Lint 0 Error / 102 Warning，输入哈希一致，`git diff --check` 退出 0。Release 构建 `assembleRelease --no-daemon --build-cache` 退出 0，412 秒。 `Echo-v2.8.19.apk`：25,472,793 字节，SHA256 `2c7c49a120b438eaeaca89b89999c70f1aac9a11e5e58df97fa33936dc260cb1`；com.aiassistant、2.8.19/205、minSdk 26 / targetSdk 34 / compileSdk 34、arm64-v8a；aapt 与 apksigner v2 验证退出 0，证书 SHA256 `939638f6d3e9af7f8a980e62af52d275fee73381f2130cc4e20a0d349f98e21f`，Android Debug **非正式生产签名**。排他复制校验退出 0，原 215 个文件（214 个 APK）名称/大小/哈希保留。 APK 隐私扫描通过：446 个 ZIP 条目 CRC 全通过，31,534 条 DEX 实际字符串、10,624 条资源字符串、已知私人标记、签名块与图片元数据扫描未决 0；新增可读字符串已逐条关联主题/纹理/组件源码人工审阅，186 张图片与 2.8.18 基线内容和元数据一致。 版本提交 `bbf04cde5d694ac97d22031b73543c6fdb45a225` 与 `v2.8.19` 已推送且远端一致，核验时无待推提交。[Echo v2.8.19 Release](https://github.com/zhuqichen505-design/chat-Echo/releases/tag/v2.8.19) 创建并上传唯一手动 APK，GitHub 摘要与匿名下载 SHA256 一致，publish / cleanup 均退出 0；远端仅保留 v2.8.19，本机旧标签与 215 个历史文件（214 APK）保留。

## 设备验收边界

未连接真机或模拟器。自动测试、浏览器预览和构建结果不能代替设备安装启动、主题视觉、触控、滚动、键盘、文本选择和真实接口验收。

1. 切换 DECKLE / COTTON、浅深模式和五个旧主题，重启后确认主题持久化；确认另外两套纸本只在预览中出现。
2. 检查首页、聊天、设置与共享组件的纹理、字体、行距、字距、低圆角和文字对比度；确认纹理不拦截点击、滚动、选择和弹窗操作。
3. 检查普通 Markdown 段落的 2em 首行缩进 / 5dp 段距，确认列表、代码、表格和原文字保持原布局。
4. 回归方方 / 扁扁 Bot 符号右移和颜色引用误判修复，确认成功回复不误显示错误色。
