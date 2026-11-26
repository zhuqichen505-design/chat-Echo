# Echo UI 与主题设计流程及规范

> **最后同步日期**：2026-11-26
> **同步基线**：Echo v2.9.4/239（创作阅读页与字体扩展、UI/主题规范建档、API 配置页及拖拽动画优化）
> **权威状态**：本文档是 Echo 项目 UI 与主题设计的单一事实规范；源码实现与本文档发生冲突时，以仓库实际代码和用户最新指令为准，并**必须立即回写更新本文档**。

---

## 0. 自动维护与同步触发机制（Mandatory Auto-Update）

为确保规范始终准确且随产品演进自动保持同步，任何开发或维护任务只要涉及以下任一变更，**必须在同一任务内同步更新本文档对应章节并在文末「修订记录」追加条目**：

1. **设计令牌变更**：修改 `EchoDesignTokens.kt`（间距、圆角、玻璃透明度、模糊半径、阴影、图标、触控目标、组件高度、排版扩展）或 `EchoMotion.kt`（时长、周期、缓动、弹簧、合帧预算）。
2. **色彩与无障碍规范变更**：修改 `Color.kt`、`Theme.kt`、`EchoSemanticColors.kt`、`ReadableColors.kt` 或 `UiPolishRegressionTest.allPresetThemeTextPairsMeetReadableContrastInBothModes`。
3. **主题预设与材质工艺变更**：在 `PersonalizationManager.kt`（`EchoAppearancePreset`）、`EchoThemeStyle.kt`、`EchoPaperTexture.kt`、`EchoThemeChrome.kt`、`Type.kt` 中新增、调整或下线主题预设、材质、纹样、边饰、版头、分隔线、标题或元数据样式。
4. **共享容器与自适应布局变更**：修改 `EchoHaze.kt`、`EchoGlassCard.kt`、`EchoControls.kt`、`EchoSwitch.kt`、`EchoSectionHeader.kt`、`EchoBadge.kt`、`EchoAdaptiveLayout.kt`、`SmoothReorderState.kt` 或 `ChatInputComponents.kt` 中的输入拖动手柄联动策略。
5. **创作阅读页与正文字体变更**：修改 `EchoStoryReadingStyle`、`EchoStoryFont`、`StoryReadingComponents.kt`、`MarkdownText.kt` 段落策略或 `SettingsAppearanceTab.kt` 预览与选项。
6. **用户提出新的设计偏好或视觉红线**：当用户在对话中明确调整视觉风格、排版规则或组件形态准则时，将新规则持久化写入本文档对应章节。

---

## 1. 设计令牌体系（Single Source of Truth）

所有页面与组件必须消费令牌，**严禁在业务页面中硬编码裸 `dp` 间距、裸 `sp` 字号、裸 `tween(NNN)` 时长或硬编码颜色**。

### 1.1 空间与尺寸令牌（`ui/theme/EchoDesignTokens.kt` -> `EchoTokens`）

| 令牌域 | 令牌名 | 数值 / 规则 | 用途与约束 |
|---|---|---|---|
| **Spacing** | `xxs / xs / sm / md / lg / xl / xxl` | `2 / 4 / 8 / 12 / 16 / 20 / 28 dp` | 基础阶梯 |
| **Spacing 语义别名** | `pageGutter` | `16.dp` | 页面水平统一边距（替代已废弃 `screenHorizontal`） |
| | `cardPadding` / `cardPaddingCompact` | `16.dp` / `12.dp` | 标准卡片 / 弹窗内紧凑卡片内边距 |
| | `cardGap` / `sectionGap` | `12.dp` / `20.dp` | 卡片间距 / 分区组间距 |
| | `inlineGap` / `inlineGapTight` | `8.dp` / `4.dp` | 行内元素间距 / 紧凑行内间距 |
| **Radius** | `xs / sm / md / lg / xl / pill` | `6 / 8 / 12 / 18 / 24 / 999 dp` | 基础圆角阶梯及对应 `shapeXs..shapePill` |
| **内容固定形状** | `shapeInput` / `shapeEditor` / `shapeCodeBlock` | `22.dp` / `14.dp` / `10.dp` | **内容类容器豁免主题控件圆角覆盖**（`EchoGlassInputShape` 在 `flatSurfaces` 下仍随 `panelCornerRadius`，而内置编辑器与代码块保持固定形状） |
| **Glass** | `cardAlphaLight / Dark` | `0.28f / 0.38f` | 经典玻璃卡背景透明度 |
| | `panelAlphaLight / Dark` | `0.88f / 0.85f` | 面板透明底色 |
| | `strongAlphaLight / Dark` | `0.94f / 0.92f` | 强面板底色 |
| | `softAlphaLight / Dark` | `0.72f / 0.68f` | 弱面板底色 |
| | `dialogAlphaLight / Dark` | `0.85f / 0.90f` | 弹窗面板透明度 |
| | `controlAlphaLight / Dark` | `0.80f / 0.76f` | 控件底色透明度 |
| | `inputAlphaLight / Dark` | `0.93f / 0.90f` | 输入框与悬浮工具栏透明度 |
| | `borderWidth / activeBorderWidth` | `1.dp` / `1.3.dp` | 默认描边宽 / 激活描边宽 |
| | `blurRadiusStandard / Heavy / Subtle` | `20.dp / 30.dp / 12.dp` | Haze 实时高斯模糊半径 |
| **Elevation** | `card(isDark)` / `overlay(isDark)` / `raised(isDark)` | `2.dp / 4.dp / 6.dp`（中性黑，深色降低 alpha 至 `0.10f..0.14f`） | L1 静止卡片 / L2 浮层 / L3 拖拽按压态；深色靠提升 Surface 明度区分层级而非加深黑影 |
| **IconSize** | `action / inline / badge / header` | `24 / 18 / 14 / 22 dp` | 列表操作 / 行内辅助 / 徽章内 / 卡片标题图标 |
| **TouchTarget** | `minimum` / `compact` | `48.dp` / `36.dp` | 标准触控热区 48dp；`compact` 仅限高密度工具条且须配 ≥ 6dp 间距补偿 |
| **Component** | `settingRowMinHeight` / `WithSubtitle` | `56.dp` / `64.dp` | 设置行最小高度 |
| | `menuItemMinHeight` | `72.dp` | 菜单条目最小高度 |
| | `settingIconBackdrop` / `avatarSize` / `colorSwatchSize` | `42.dp` / `72.dp` / `40.dp` | 固定组件尺寸 |
| | `switchTrackWidth × switchTrackHeight` | `44.dp × 26.dp` | `EchoSwitch(Compact)` 轨道尺寸（外层仍保 `48.dp` 触控目标） |
| **Type 扩展** | `chatBody` / `badgeLabel` / `cardTitle` | `16sp(26sp LH)` / `12sp(16sp LH)` / `16sp(24sp LH)` | 长文气泡 / 徽章 / 卡片标题扩展排版 |

### 1.2 动效与流畅度红线（`ui/theme/EchoMotion.kt` -> `EchoMotion`）

- **时长阶梯 (`Duration`)**：`instant = 80ms`、`fast = 150ms`、`standard = 250ms`、`emphasized = 350ms`、`slow = 500ms`。页面内**禁止手写 `tween(NNN)` 裸值**，统一走 `EchoMotion.tweenSpec(...)`。
- **循环周期 (`Cycle`)**：`pulse = 1600ms`、`reconnectArc = 1200ms`、`thinkingDots = 1500ms`、`shimmer = 1500ms`。
- **弹簧 (`Spring`)**：`gentle()`（阻尼比 `0.8f`，刚度 `380f`）用于尺寸过渡；`snappy()`（阻尼比 `0.75f`，刚度 `600f`）用于按压回弹。
- **打字机合帧 (`Typewriter`)**：`frameBudgetMs = 33L`（≈30fps 合帧消费流式 token）、`cursorBlinkMs = 530ms`、`cursorFadeMs = 300ms`。
- **性能铁律**：循环动画**只允许驱动绘制层（`Canvas` / `graphicsLayer`）**，严禁驱动布局测量或整屏重组。
- **无障碍降级**：所有装饰性/过渡动效必须响应 `rememberReducedMotion()`（当系统 `ANIMATOR_DURATION_SCALE == 0f` 时归零或使用 `snap()`，仅保留状态切换结果）。

---

## 2. 色彩系统与无障碍对比度红线

### 2.1 双模式色板结构（`Color.kt` & `Theme.kt`）
- 浅色与深色必须分别独立调校，严禁将深色色板直接反转或机械调暗充当浅色，也不允许浅色直接套用暗灰背景冒充深色。
- `echoColorScheme(appearancePreset, darkTheme)` 负责补全 5 阶容器明度梯度（`surfaceContainerLowest` → `surfaceContainerLow` → `surfaceContainer` → `surfaceContainerHigh` → `surfaceContainerHighest`）以及 `surfaceBright` / `surfaceDim` / `inverseSurface` / `inverseOnSurface` / `inversePrimary`。
- 深色模式中 `background` 与 `surface` 必须保持清晰明度阶差，使卡片不用重阴影即可辨识层级。
- 透明 `Scaffold` 或自绘容器必须显式继承或提供 `onBackground` / `onSurface`，防止深色模式下出现默认黑字。

### 2.2 WCAG AA ≥ 4.5:1 硬性门禁
全仓任意主题预设的浅色与深色模式，必须通过 `UiPolishRegressionTest.allPresetThemeTextPairsMeetReadableContrastInBothModes` 验证。以下 **13 组角色色对**对比度必须均 **≥ 4.5:1**：
1. `onBackground` / `background`（页面正文）
2. `onSurface` / `surface`（卡片正文）
3. `onSurfaceVariant` / `surfaceVariant`（次级文本）
4. `onPrimary` / `primary`（主操作按钮）
5. `onSecondary` / `secondary`（次操作）
6. `onTertiary` / `tertiary`（第三强调）
7. `onError` / `error`（错误操作）
8. `onPrimaryContainer` / `primaryContainer`（选中态容器）
9. `onSecondaryContainer` / `secondaryContainer`（次级容器）
10. `onTertiaryContainer` / `tertiaryContainer`（第三容器）
11. `onErrorContainer` / `errorContainer`（错误容器）
12. `onSurface` / `surfaceContainer`（菜单与控件表面）
13. `inverseOnSurface` / `inverseSurface`（反色提示条/Snackbar）

### 2.3 语义色与壁纸可读性（`EchoSemanticColors.kt` & `ReadableColors.kt`）
- **语义四元组 (`SemanticColorGroup`)**：`error`、`warning`、`success`、`info` 均包含 `main / container / onContainer / border` 四角色。
- **单色主题收敛 (`isMonochrome == true`)**：如「墨白」主题，在 `rememberEchoSemanticColors()` 中将 `warning / success / info` 收敛为中性表面色，**仅保留 `error` 红色**以提示需要处理的操作。
- **自定义壁纸适配**：叠加用户壁纸时，顶部工具栏、消息气泡与文本层必须经 `readableTextColorFor(...)` 与 `rememberReadableBackdropColors(bitmap)` 对背景图采样后计算高对比前景文字色。

---

## 3. 多维主题架构与 15 套主题矩阵

Echo 主题绝非单一换色，而是由 **11 个正交设计维度**共同塑造完整视觉体验：

1. **双模式 ColorScheme**（`Theme.kt`）
2. **形态几何与轮廓策略**（`EchoThemeStyle.kt`：`panelCornerRadius`、`controlCornerRadius`、`dialogCornerRadius`、`flatSurfaces`、`cutCorners`、`hardShadow`、`strongOutline`、`accentUserBubble`）
3. **纸质/材质纹理 (`EchoPaperMaterial`)**：`None`, `Deckle`, `Cotton`, `Folio`, `Celadon`, `Lacquer`, `Newsprint`, `ProjectorGrain`, `SkyMist`, `IndexStock`, `NauticalVellum`（`EchoPaperTexture.kt` 固定随机种子生成 Tileable `ShaderBrush`，`paperBrushCache` 容量 24）
4. **页面级底纹 (`EchoSurfacePattern`)**：`None`, `SkyCurrents`, `SonarGrid`, `CeladonCrackle`, `BlueprintDraft`, `LacquerFrame`, `IndexCatalogue`, `NewsRegister`, `FilmScanline`, `NauticalChart`, `SwissGrid`, `LetterColumns`, `BauhausGeometry`, `DeckleEdge`
5. **容器边框工艺 (`EchoFrameOrnament`)**：`None`, `SkyHorizon`, `CeramicRim`, `BlueprintBrackets`, `LacquerDoubleFrame`, `IndexTab`, `CropMarks`, `ReelPerforation`, `VoyageCompass`, `SwissRule`, `DeckleSpine`, `LetterSlip`, `FolioMargin`, `SonarReticle`, `BauhausBlock`
6. **首页与分区版头 (`EchoHeaderStyle`)**：`Default`, `AiryHorizon`, `NauticalLog`, `CatalogueBar`, `SwissEditorial`, `BookPlate`, `VerseSlip`, `FolioChapter`, `TelemetryHud`, `BauhausBar`, `CeladonSeal`, `BlueprintSpec`, `LacquerPlaque`, `NewspaperMasthead`, `CinemaSlate`
7. **结构分隔线 (`EchoDividerStyle`)**：`Default`, `DoubleRule`, `DashedTick`, `HeavyEditorial`, `LiteraryOrnament`, `CatalogRule`, `FilmTrack`, `HorizonFade`, `VersePause`, `RadarPulse`, `CeramicWave`, `GiltDoubleRule`
8. **Markdown 标题饰样 (`EchoMarkdownHeadingStyle`)**：`Default`, `EditorialRule`, `TechnicalPrefix`, `LiteraryChapter`, `GeometricBlock`, `CinemaCue`, `AiryHorizon`, `NauticalHeading`, `CatalogHeading`, `VerseHeading`, `FolioChapter`, `TelemetryHud`, `CeladonSeal`, `LacquerPlaque`
9. **消息元数据与计时格式 (`EchoMessageMetaStyle`)**：`Default`, `TimecodeSubtitle`, `Marginalia`, `Telemetry`, `CatalogueStamp`, `Logbook`, `SwissStamp`, `Colophon`, `VerseNote`, `GeometricTag`, `SealMark`, `PlaqueStamp`, `PressDateline`
10. **界面排版韵律 (`Type.kt` -> `echoTypographyFor`)**：标题字体族、字重、字距与正文默认行高
11. **段落缩进与对话气泡轮廓 (`MarkdownText.kt` & `ChatMessageComponents.kt`)**：首行缩进 `em` / 两端对齐 / 段落间距 + 用户气泡专属方向圆角与边饰

### 3.1 主题差异化红线
- **唯一组合约束**：除 `EXISTING`（经典）外，其余所有主题**必须拥有互异的 `(frameOrnament, headerStyle)` 组合**，禁止两套主题共享同一套边框工艺与版头样式。
- **反干扰约束**：所有 `echoPaperTexture`、`echoThemeFrameOrnament` 与 `echoThemeHardShadow` 均运行在 `drawBehind` / `drawWithContent` 绘制层，**严禁拦截触控点击或文本划选**；纹理保持固定确定性种子与低对比度，保证长文阅读不刺眼。

### 3.2 现行 15 套主题预设对照表

| ID (`EchoAppearancePreset`) | 名称 | 圆角 (面板/控件/弹窗 dp) | 材质 (`PaperMaterial`) | 底纹 (`SurfacePattern`) | 框饰 (`FrameOrnament`) | 版头 (`HeaderStyle`) | 特殊标志 |
|---|---|---|---|---|---|---|---|
| `existing` | 经典 | 18 / 12(pill) / 24 | `None` | `None` | `None` | `Default` | 液态玻璃实时模糊 + 45° 高光 |
| `clear_sky` | 晴空 | 24 / 16 / 28 | `SkyMist` | `SkyCurrents` | `SkyHorizon` | `AiryHorizon` | 宽圆角 + 方向气泡 + 舒展排版 |
| `night_voyage` | 夜航 | 12 / 8 / 16 | `NauticalVellum` | `NauticalChart` | `VoyageCompass` | `NauticalLog` | 航海罗盘 + 铆钉 + 衬线题/等宽签 |
| `index` | 索引 | 6 / 4 / 8 | `IndexStock` | `IndexCatalogue` | `IndexTab` | `CatalogueBar` | `strongOutline` + 26dp 横格 + 挂签 |
| `mono` | 墨白 | 4 / 3 / 6 | `None` | `SwissGrid` | `SwissRule` | `SwissEditorial` | `isMonochrome` + `strongOutline` + 瑞士四栏 |
| `deckle` | 毛边文库 | 6 / 6 / 12 | `Deckle` | `DeckleEdge` | `DeckleSpine` | `BookPlate` | 2em 首行缩进 + 书脊针脚 |
| `cotton` | 棉纸诗笺 | 8 / 8 / 14 | `Cotton` | `LetterColumns` | `LetterSlip` | `VerseSlip` | 直行界栏 + 三点诗节 + 宽松段距 |
| `folio` | 拾页 | 4 / 4 / 8 | `Folio` | `None`（Folio 双栏红批注） | `FolioMargin` | `FolioChapter` | 0.8em 缩进 + 左侧边注线 |
| `sonar` | 声呐 | 12 / 8 / 16 | `None` | `SonarGrid` | `SonarReticle` | `TelemetryHud` | `cutCorners` 45° 切角 + 雷达环 |
| `bauhaus` | 构型 | 3 / 3 / 4 | `None` | `BauhausGeometry` | `BauhausBlock` | `BauhausBar` | `hardShadow` + `strongOutline` + 三原色 |
| `celadon` | 青瓷 | 20 / 14 / 24 | `Celadon` | `CeladonCrackle` | `CeramicRim` | `CeladonSeal` | 冰裂开片 + 玉釉/暖金双唇边 |
| `blueprint` | 蓝图 | 2 / 2 / 4 | `None` | `BlueprintDraft` | `BlueprintBrackets` | `BlueprintSpec` | 双轴标尺 + 十字准星 + CAD 角标 |
| `lacquer` | 漆匣 | 10 / 8 / 14 | `Lacquer` | `LacquerFrame` | `LacquerDoubleFrame` | `LacquerPlaque` | 1.2em 缩进 + 描金回纹双框 |
| `edition` | 晨刊 | 2 / 2 / 4 | `Newsprint` | `NewsRegister` | `CropMarks` | `NewspaperMasthead` | `strongOutline` + 1.6em 缩进 + 裁切角标 |
| `screening` | 放映室 | 8 / 5 / 10 | `ProjectorGrain` | `FilmScanline` | `ReelPerforation` | `CinemaSlate` | `strongOutline` + 银盐浅/暗胶片齿孔 + TC 时间码 |

---

## 4. 共享组件、自适应布局与阅读解耦规范

### 4.1 液态玻璃 vs 实体材质双轨渲染
- `EchoGlassCard` 与 `Modifier.echoHazePanel` 根据 `LocalEchoThemeStyle.current.flatSurfaces` 自动分流：
  - `flatSurfaces == false`（经典主题）：启用 Haze 实时模糊、半透明叠色、45° 折射高光与渐变微光描边；
  - `flatSurfaces == true`（14 套风格主题）：转为不透明纸质/实体表面，叠加 `echoThemeHardShadow`、`echoPaperTexture()`、自适应描边（`strongOutline` 时 `1.6.dp`）与 `echoThemeFrameOrnament()`。
- **下拉菜单防斑块规范**：`EchoGlassDropdownMenu` 必须保持不透明自绘 `Popup`（`surfaceTint` 5% 合成 `surface`），`tonalElevation = 0.dp`、`shadowElevation = 0.dp`，消除半透明菜单透字与黑边。

### 4.2 全局几何与输入框手柄联动
- 全局形状令牌：`EchoGlassDialogShape`、`EchoGlassPagePanelShape`、`EchoGlassControlShape`、`EchoGlassInputShape`、`EchoGlassSubPanelShape`、`EchoGlassCompactPanelShape`、`EchoGlassIconButtonShape`。
- **输入框右上角拖动手柄 (`ChatInputComponents.kt`)**：`inputResizeHandleStyleFor(themeStyle)` 必须随外框形态自动匹配四态之一：
  - `cutCorners` → `CutBevel`（切角斜边折线）
  - `flatSurfaces && panelCornerRadius <= 4.dp` → `RightAngle`（直角折线）
  - `flatSurfaces && panelCornerRadius < 12.dp` → `RoundedAngle`（小圆角折线）
  - 其余大圆角/玻璃主题 → `Arc`（同心圆弧）
- **核心输入框保护**：`EchoNativeTextField.kt` 的焦点、点击与 IME 会话逻辑独立于主题样式，仅做外观调整时不得改动其交互与输入会话链路。

### 4.3 宽屏自适应与布局解耦（`EchoAdaptiveLayout.kt`）
- 窗口断点：`Compact`（< 600dp）、`Medium`（600–839dp）、`Expanded`（≥ 840dp）。
- `echoContentInset(base, maxContentWidth)` 在大屏对称收拢内容列宽：设置页 / 首页 / 历史页默认 `720.dp`，对话消息列与输入栏上限为 `900.dp`。
- **首页布局与主题完全解耦**：`EchoHomeLayoutStyle`（`FOCUS` 聚焦对话 / `WORKBENCH` 侧栏工作台）独立于 15 套配色预设与阅读设置，仅控制导航与会话列表排布。

### 4.4 创作阅读页与正文字体独立规范
创作阅读排版（`EchoStoryReadingStyle`）与创作正文字体（`EchoStoryFont`）**独立于 15 套主题预设**，支持通过 `applyReadingStyleToChats` 与 `applyReadingFontToChats` 各自控制是否应用到普通对话，**设置页与操作控件永不继承正文字体层**：
- **6 种创作阅读页 (`EchoStoryReadingStyle`)**：
  - `immersive`（沉浸叙事）：1.95x 行高，用户消息去气泡改为左侧细引线
  - `plain`（简洁正文）：1.65x 行高，标准气泡
  - `split`（分栏上下文）：1.65x 行高，宽屏并排资料侧栏 / 窄屏顶部可折叠资料卡
  - `book`（书页典籍）：1.85x 行高，2.0em 首行缩进 + 两端对齐 + 书签双竖线用户区
  - `compact`（紧凑速览）：1.48x 行高，8dp 紧凑消息间距，取消首行缩进
  - `script`（剧本分镜）：1.72x 行高，上下细线分镜提示用户行动/对白
- **8 种创作正文字体 (`EchoStoryFont`)**：
  - `literary`（沉浸衬线，内置思源宋体 `echo_literary_regular.ttf`）、`clear`（清晰无衬线）、`typewriter`（稿纸等宽）、`system`（跟随系统）、`system_serif`（经典宋体 `FontFamily.Serif`）、`print_book`（精印书宋，中等字重+0.45sp 字距）、`elegant_light`（轻盈细黑，Light 字重+0.35sp 字距）、`calligraphy`（随笔手写 `FontFamily.Cursive`）。

### 4.5 拖拽重排组件规范（`SmoothReorderState.kt`）
- **稳定身份**：必须使用业务稳定 ID（如配置 ID、Key UUID、模型名、消息 ID），严禁使用列表下标作 Key；外部列表容器必须配合 `key(itemId)` 使用。
- **精确间距**：容器外围不得额外叠加未计入 `spacingPx` 的 `padding(vertical = ...)`；`reorderItem(..., spacingPx = ...)` 必须与父布局 `Arrangement.spacedBy(...)` 的真实像素值严格一致。
- **视觉反馈与落座补偿**：仅激活拖拽项获得 `getItemLift(key)` 的缩放与投影；松手瞬间先按拖拽项实测高度同步抵消受影响非拖拽项的让位偏移，再并行播放落座与抬升回落动画，杜绝松手后双倍跳动与闪回。

---

## 5. 主题与 UI 变更标准研发流程（SOP）

### 阶段 1：概念定调与差异化评审
1. 明确目标主题/界面的视觉隐喻（如纸张、器物、印刷、仪器、胶片等）与信息层级优先级。
2. 若新增或修改主题预设，确定其独有的 `(frameOrnament, headerStyle)` 组合与几何轮廓（圆角/切角/硬阴影/粗描边）。

### 阶段 2：双模式色板与对比度预检
1. 同时设计浅色（Light）与深色（Dark）两套完整色板（含 `surfaceContainerLowest..Highest` 五阶）。
2. 逐一核对 13 组角色色对，确保浅深双模式对比度均 **≥ 4.5:1**；校验 `EchoSemanticColors` 与暗色表面明度差。

### 阶段 3：材质、边饰、排版与动效设计
1. 设计 `EchoPaperMaterial` 固定种子底纹、`EchoSurfacePattern` 页面纹样、`EchoFrameOrnament` 容器工艺与 `EchoDividerStyle` / `EchoHeaderStyle`。
2. 配置 `echoTypographyFor` 标题/标签/正文韵律与 `paperParagraphStyle` 段落缩进。
3. 核查所有动效均使用 `EchoMotion` 令牌且遵循 `rememberReducedMotion()`；布局间距与圆角均来自 `EchoTokens`。

### 阶段 4：逐文件接入核对清单（以主题预设为例）
1. `app/src/main/java/com/aiassistant/utils/PersonalizationManager.kt`：登记 `EchoAppearancePreset` 常量与 `values`（或 `EchoStoryReadingStyle` / `EchoStoryFont`）。
2. `app/src/main/java/com/aiassistant/ui/theme/EchoThemeStyle.kt`：补充枚举分支并完成 `echoThemeStyleFor` 映射。
3. `app/src/main/java/com/aiassistant/ui/theme/Theme.kt`：补充 `lightColorSchemeFor` 与 `darkColorSchemeFor`。
4. `app/src/main/java/com/aiassistant/ui/theme/Type.kt`：补充 `echoTypographyFor`。
5. `app/src/main/java/com/aiassistant/ui/theme/EchoPaperTexture.kt`：实现材质纹理、页面纹样与边框工艺绘制。
6. `app/src/main/java/com/aiassistant/ui/theme/EchoThemeChrome.kt`：实现分隔线、版头字标与消息时间戳格式。
7. `app/src/main/java/com/aiassistant/ui/components/EchoSectionHeader.kt` & `MarkdownText.kt`：接入分区前导符、Markdown 标题装饰与段落缩进。
8. `app/src/main/java/com/aiassistant/ui/screens/chat/ChatMessageComponents.kt`：适配用户气泡圆角、轮廓与边饰。
9. `app/src/main/java/com/aiassistant/ui/screens/settings/SettingsAppearanceTab.kt`：补充预设条目、中文说明、三色色板与缩略预览。
10. `app/src/test/java/com/aiassistant/UiPolishRegressionTest.kt` & `RetryPreferencesTest.kt`：补全持久化、样式映射与 13 组双模式对比度断言。

### 阶段 5：自动化验证与人工验收
1. 在 `app/chat-Echo` 执行全量验证：
   ```powershell
   pwsh -File scripts/verify.ps1 -GradleUserHome D:/Agent/APP-Echo/app/.gradle-home
   git diff --check
   ```
2. 确认关键测试通过：`allPresetThemeTextPairsMeetReadableContrastInBothModes`、`RetryPreferencesTest`、`SmoothReorderTest`、`UiPolishRegressionTest`。
3. 真机人工复核清单：浅/深模式切换、自定义壁纸叠加可读性、长文/大字体（1.25x）无截断折行、输入栏右上角手柄几何贴合、拖拽与滚动手感流畅无闪烁。
4. 同步更新本规范文档及维护留痕（`UPDATE_LOG.md`、`WORKFLOW_GUIDELINES.md`、`PROJECT.md`）。

---

## 6. 规范修订记录

| 日期 | 变更摘要 | 关联文件 / 基线 |
|---|---|---|
| 2026-11-26 | 初始建立 Echo UI 与主题设计流程及规范文档；完整收录 15 套主题矩阵、设计令牌、13 组 WCAG AA 对比度红线、6 种创作阅读页排版、8 种创作正文字体、拖拽重排规范与自动更新触发机制。 | v2.9.3/238 基线 + 本轮阅读/字体/API 配置/拖拽重排优化 |
