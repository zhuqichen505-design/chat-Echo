Add-Type -AssemblyName System.Drawing

$ErrorActionPreference = "Stop"

$Root = if ($MyInvocation.MyCommand.Path) {
    Split-Path -Parent $MyInvocation.MyCommand.Path
} else {
    Join-Path (Get-Location) "design\ui-refresh"
}
$ScreensDir = Join-Path $Root "screens"
New-Item -ItemType Directory -Force -Path $ScreensDir | Out-Null

$W = 390
$H = 844

function C($hex) {
    [System.Drawing.ColorTranslator]::FromHtml($hex)
}

function Brush($hex) {
    New-Object System.Drawing.SolidBrush (C $hex)
}

function PenC($hex, $width = 1) {
    New-Object System.Drawing.Pen((C $hex), $width)
}

function FontC($size, $style = "Regular") {
    New-Object System.Drawing.Font("Microsoft YaHei UI", $size, [System.Drawing.FontStyle]::$style, [System.Drawing.GraphicsUnit]::Pixel)
}

function RoundPath($x, $y, $w, $h, $r) {
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $r * 2
    $path.AddArc($x, $y, $d, $d, 180, 90)
    $path.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $path.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
    $path.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $path.CloseFigure()
    $path
}

function FillRound($g, $brush, $x, $y, $w, $h, $r) {
    $path = RoundPath $x $y $w $h $r
    $g.FillPath($brush, $path)
    $path.Dispose()
}

function StrokeRound($g, $pen, $x, $y, $w, $h, $r) {
    $path = RoundPath $x $y $w $h $r
    $g.DrawPath($pen, $path)
    $path.Dispose()
}

function Text($g, $value, $font, $brush, $x, $y, $w = 300, $h = 40, $align = "Near") {
    $rect = [System.Drawing.RectangleF]::new([float]$x, [float]$y, [float]$w, [float]$h)
    $fmt = New-Object System.Drawing.StringFormat
    $fmt.Alignment = [System.Drawing.StringAlignment]::$align
    $fmt.LineAlignment = [System.Drawing.StringAlignment]::Near
    $fmt.Trimming = [System.Drawing.StringTrimming]::EllipsisCharacter
    $g.DrawString($value, $font, $brush, $rect, $fmt)
    $fmt.Dispose()
}

function Chip($g, $text, $x, $y, $w, $fill = "#EEF5FF", $fg = "#2563EB") {
    FillRound $g (Brush $fill) $x $y $w 30 15
    Text $g $text (FontC 13 "Bold") (Brush $fg) ($x + 12) ($y + 6) ($w - 24) 20
}

function IconButton($g, $x, $y, $label, $fill = "#F1F5F9", $fg = "#334155") {
    FillRound $g (Brush $fill) $x $y 40 40 20
    Text $g $label (FontC 15 "Bold") (Brush $fg) $x ($y + 9) 40 22 "Center"
}

function Card($g, $x, $y, $w, $h, $r = 18, $fill = "#FFFFFF") {
    FillRound $g (Brush $fill) $x $y $w $h $r
    StrokeRound $g (PenC "#E2E8F0" 1) $x $y $w $h $r
}

function Header($g, $title, $subtitle = "") {
    FillRound $g (Brush "#FFFFFF") 0 -18 $W 112 24
    Text $g $title (FontC 24 "Bold") (Brush "#0F172A") 22 28 230 34
    if ($subtitle -ne "") {
        Text $g $subtitle (FontC 12) (Brush "#64748B") 23 60 260 24
    }
}

function BottomHandle($g) {
    FillRound $g (Brush "#CBD5E1") 158 824 74 5 3
}

function NewScreen($file, [scriptblock]$draw) {
    $bmp = [System.Drawing.Bitmap]::new($W, $H)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit
    $g.FillRectangle((Brush "#F7F9FC"), 0, 0, $W, $H)
    & $draw $g
    BottomHandle $g
    $path = Join-Path $ScreensDir $file
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose()
    $bmp.Dispose()
    $path
}

function DrawBarsIcon($g, $x, $y) {
    FillRound $g (Brush "#E8F0FA") $x $y 40 40 20
    FillRound $g (Brush "#1E293B") ($x + 11) ($y + 10) 18 20 5
    FillRound $g (Brush "#FFFFFF") ($x + 16) ($y + 23) 3 5 2
    FillRound $g (Brush "#FFFFFF") ($x + 21) ($y + 18) 3 10 2
    FillRound $g (Brush "#FFFFFF") ($x + 26) ($y + 14) 3 14 2
}

function HomePreview {
    param($g)
    Header $g "Echo" "精简、清晰、可长时间停留"
    DrawBarsIcon $g 284 28
    IconButton $g 332 28 "设"
    FillRound $g (Brush "#FFFFFF") 18 108 354 50 25
    Text $g "搜索对话、模型或提示词" (FontC 14) (Brush "#94A3B8") 52 123 230 22
    Text $g "⌕" (FontC 18 "Bold") (Brush "#64748B") 30 121 20 22

    Chip $g "全部" 18 174 58 "#2563EB" "#FFFFFF"
    Chip $g "置顶" 86 174 58
    Chip $g "项目" 154 174 58
    Chip $g "隐藏" 222 174 58 "#F8FAFC" "#64748B"

    Text $g "今天" (FontC 14 "Bold") (Brush "#334155") 22 224 120 24
    Card $g 18 254 354 112
    FillRound $g (Brush "#2563EB") 34 276 4 64 2
    Text $g "UI 美化方向讨论" (FontC 16 "Bold") (Brush "#0F172A") 52 274 250 26
    Text $g "GPT-4o · 12 条消息 · 刚刚更新" (FontC 12) (Brush "#64748B") 52 302 250 20
    Chip $g "上下文 42%" 52 326 94 "#EEF5FF" "#2563EB"
    Chip $g "已置顶" 154 326 72 "#F8FAFC" "#64748B"
    Text $g "⋯" (FontC 24 "Bold") (Brush "#94A3B8") 330 279 24 28 "Center"

    Card $g 18 380 354 96
    Text $g "课程设计功能整理" (FontC 15 "Bold") (Brush "#0F172A") 34 398 250 24
    Text $g "DeepSeek · 8 条消息 · 昨天" (FontC 12) (Brush "#64748B") 34 424 250 20
    Chip $g "文件夹：课程" 34 448 106 "#F8FAFC" "#64748B"

    Card $g 18 490 354 96
    Text $g "API 调用兼容性测试" (FontC 15 "Bold") (Brush "#0F172A") 34 508 250 24
    Text $g "Claude · 23 条消息 · 周二" (FontC 12) (Brush "#64748B") 34 534 250 20
    Chip $g "长期记忆" 34 558 82 "#ECFDF5" "#047857"

    FillRound $g (Brush "#2563EB") 276 738 76 48 24
    Text $g "+ 新建" (FontC 15 "Bold") (Brush "#FFFFFF") 294 751 58 24
}

function ChatPreview {
    param($g)
    Header $g "UI 美化方向讨论" "GPT-4o · 本对话配置"
    Chip $g "上下文 42%" 252 56 104 "#EEF5FF" "#2563EB"
    Chip $g "生成中" 24 100 72 "#F8FAFC" "#64748B"

    Card $g 18 150 292 92 20 "#FFFFFF"
    Text $g "我想让应用整体更精致简约，先看预览。" (FontC 15) (Brush "#0F172A") 36 170 245 46
    Text $g "12:18" (FontC 11) (Brush "#94A3B8") 36 214 70 18

    FillRound $g (Brush "#2563EB") 82 266 290 112 20
    Text $g "可以。建议先统一视觉 token，再逐屏替换控件。下面先给出主页、对话、统计、设置和历史页预览。" (FontC 14) (Brush "#FFFFFF") 102 286 242 60
    Text $g "12:19" (FontC 11) (Brush "#DBEAFE") 102 352 70 18

    Card $g 18 406 320 130 20 "#FFFFFF"
    Text $g "设计原则" (FontC 14 "Bold") (Brush "#0F172A") 36 426 150 22
    Text $g "少装饰、强层级、轻边线、稳定间距。玫红只保留给提醒类状态。" (FontC 13) (Brush "#475569") 36 454 260 54
    Chip $g "Material 3" 36 502 92 "#EEF5FF" "#2563EB"
    Chip $g "低噪声" 136 502 72 "#F8FAFC" "#64748B"

    FillRound $g (Brush "#FFFFFF") 14 738 362 62 24
    StrokeRound $g (PenC "#E2E8F0" 1) 14 738 362 62 24
    Text $g "继续输入..." (FontC 14) (Brush "#94A3B8") 56 758 180 24
    IconButton $g 24 749 "+" "#F8FAFC" "#64748B"
    FillRound $g (Brush "#2563EB") 322 749 40 40 20
    Text $g "↑" (FontC 18 "Bold") (Brush "#FFFFFF") 322 756 40 24 "Center"
}

function ContextPreview {
    param($g)
    Header $g "上下文使用情况" "当前对话预算估算"
    Card $g 18 118 354 590 24 "#FFFFFF"
    Text $g "输入预算" (FontC 15 "Bold") (Brush "#0F172A") 42 146 120 24
    Text $g "5.4k / 12.8k tokens" (FontC 13) (Brush "#64748B") 42 174 160 22
    Text $g "42%" (FontC 24 "Bold") (Brush "#2563EB") 250 146 78 32 "Far"
    FillRound $g (Brush "#E2E8F0") 42 212 286 10 5
    FillRound $g (Brush "#2563EB") 42 212 120 10 5

    $rows = @(
        @("近期原文", "18 条 · 3.2k"),
        @("较早消息", "42 条"),
        @("滚动摘要", "1.1k tokens"),
        @("长期记忆", "7 条 · 420"),
        @("已压缩至", "#185"),
        @("摘要时间", "07-04 12:45")
    )
    $y = 258
    foreach ($row in $rows) {
        FillRound $g (Brush "#F8FAFC") 42 $y 286 44 14
        Text $g $row[0] (FontC 13) (Brush "#64748B") 58 ($y + 12) 120 20
        Text $g $row[1] (FontC 13 "Bold") (Brush "#0F172A") 172 ($y + 12) 132 20 "Far"
        $y += 54
    }

    FillRound $g (Brush "#FFF7ED") 42 598 286 52 16
    Text $g "有较早消息可压缩，建议在长对话前主动刷新摘要。" (FontC 12) (Brush "#9A3412") 58 612 250 30
    FillRound $g (Brush "#2563EB") 198 668 130 44 22
    Text $g "主动压缩" (FontC 15 "Bold") (Brush "#FFFFFF") 198 680 130 24 "Center"
}

function StatsPreview {
    param($g)
    Header $g "使用统计" "模型、token 与命中率"
    Chip $g "今天" 18 108 58 "#2563EB" "#FFFFFF"
    Chip $g "7 天" 86 108 58
    Chip $g "30 天" 154 108 68

    Card $g 18 158 354 122 22 "#FFFFFF"
    Text $g "总览" (FontC 15 "Bold") (Brush "#0F172A") 38 178 80 24
    Text $g "128" (FontC 28 "Bold") (Brush "#2563EB") 38 210 90 38
    Text $g "请求" (FontC 12) (Brush "#64748B") 42 248 70 18
    Text $g "86.4k" (FontC 28 "Bold") (Brush "#0F172A") 148 210 100 38
    Text $g "tokens" (FontC 12) (Brush "#64748B") 152 248 70 18
    Text $g "31%" (FontC 28 "Bold") (Brush "#E11D48") 260 210 92 38
    Text $g "缓存命中" (FontC 12) (Brush "#64748B") 272 248 80 18

    Card $g 18 302 354 170 22 "#FFFFFF"
    Text $g "Token 消耗趋势" (FontC 15 "Bold") (Brush "#0F172A") 38 322 180 24
    $baseY = 438
    $bars = @(45, 78, 52, 96, 68, 110, 86)
    for ($i = 0; $i -lt $bars.Count; $i++) {
        $bx = 46 + $i * 42
        FillRound $g (Brush "#DBEAFE") $bx ($baseY - $bars[$i]) 18 $bars[$i] 9
        if ($i -eq 5) { FillRound $g (Brush "#2563EB") $bx ($baseY - $bars[$i]) 18 $bars[$i] 9 }
    }

    Card $g 18 492 354 206 22 "#FFFFFF"
    Text $g "模型明细" (FontC 15 "Bold") (Brush "#0F172A") 38 512 120 24
    $models = @(
        @("gpt-4o", "52 次", "#2563EB"),
        @("deepseek-chat", "39 次", "#0F766E"),
        @("claude-sonnet", "22 次", "#E11D48")
    )
    $y = 552
    foreach ($m in $models) {
        FillRound $g (Brush "#F8FAFC") 38 $y 294 40 14
        FillRound $g (Brush $m[2]) 52 ($y + 13) 14 14 7
        Text $g $m[0] (FontC 13 "Bold") (Brush "#0F172A") 76 ($y + 10) 150 20
        Text $g $m[1] (FontC 13) (Brush "#64748B") 250 ($y + 10) 60 20 "Far"
        $y += 48
    }
}

function SettingsPreview {
    param($g)
    Header $g "设置" "安静、分组、少干扰"
    $items = @(
        @("API 配置", "模型服务、密钥与默认模型", "API"),
        @("联网搜索", "Tavily 与搜索开关", "网"),
        @("个性化", "头像、隐藏对话与外观", "人"),
        @("全局提示词", "默认系统提示词", "词"),
        @("环境变量", "加密保存可复用变量", "ENV"),
        @("备份恢复", "导出、恢复与自动备份", "备")
    )
    $y = 116
    foreach ($item in $items) {
        Card $g 18 $y 354 74 18 "#FFFFFF"
        FillRound $g (Brush "#EEF5FF") 34 ($y + 17) 40 40 20
        Text $g $item[2] (FontC 12 "Bold") (Brush "#2563EB") 34 ($y + 27) 40 20 "Center"
        Text $g $item[0] (FontC 15 "Bold") (Brush "#0F172A") 90 ($y + 16) 180 22
        Text $g $item[1] (FontC 12) (Brush "#64748B") 90 ($y + 40) 210 20
        Text $g "›" (FontC 24 "Bold") (Brush "#CBD5E1") 336 ($y + 22) 20 28 "Center"
        $y += 88
    }
    Card $g 18 668 354 92 20 "#FFFFFF"
    Text $g "关于 Echo" (FontC 15 "Bold") (Brush "#0F172A") 38 690 160 24
    Text $g "版本 1.7.5 · 上下文用量、主动压缩、导航优化" (FontC 12) (Brush "#64748B") 38 718 290 28
}

function HistoryPreview {
    param($g)
    Header $g "历史与文件夹" "快速定位已有内容"
    FillRound $g (Brush "#FFFFFF") 18 108 354 48 24
    Text $g "搜索历史记录" (FontC 14) (Brush "#94A3B8") 52 122 180 22
    Text $g "⌕" (FontC 18 "Bold") (Brush "#64748B") 30 120 20 22
    Chip $g "全部" 18 176 58 "#2563EB" "#FFFFFF"
    Chip $g "课程" 86 176 58
    Chip $g "代码" 154 176 58
    Chip $g "API" 222 176 56

    $items = @(
        @("UI 美化方向讨论", "今天 · 12 条消息 · GPT-4o", "上下文"),
        @("Android APK 构建记录", "昨天 · 36 条消息 · DeepSeek", "构建"),
        @("GitHub 备份流程", "周三 · 18 条消息 · Claude", "备份"),
        @("联网搜索配置排查", "周二 · 24 条消息 · GPT-4o", "网络")
    )
    $y = 230
    foreach ($item in $items) {
        Card $g 18 $y 354 96 18 "#FFFFFF"
        Text $g $item[0] (FontC 15 "Bold") (Brush "#0F172A") 36 ($y + 18) 250 24
        Text $g $item[1] (FontC 12) (Brush "#64748B") 36 ($y + 44) 250 20
        Chip $g $item[2] 36 ($y + 68) 72 "#F8FAFC" "#64748B"
        Text $g "›" (FontC 24 "Bold") (Brush "#CBD5E1") 336 ($y + 34) 20 28 "Center"
        $y += 112
    }

    FillRound $g (Brush "#FFFFFF") 18 714 354 58 22
    StrokeRound $g (PenC "#E2E8F0" 1) 18 714 354 58 22
    Text $g "文件夹管理" (FontC 15 "Bold") (Brush "#0F172A") 38 732 140 24
    Text $g "9 个文件夹" (FontC 12) (Brush "#64748B") 270 734 70 20 "Far"
}

$outputs = @()
$outputs += NewScreen "home-preview.png" ${function:HomePreview}
$outputs += NewScreen "chat-preview.png" ${function:ChatPreview}
$outputs += NewScreen "context-preview.png" ${function:ContextPreview}
$outputs += NewScreen "stats-preview.png" ${function:StatsPreview}
$outputs += NewScreen "settings-preview.png" ${function:SettingsPreview}
$outputs += NewScreen "history-preview.png" ${function:HistoryPreview}

$boardW = 1500
$boardH = 1880
$board = [System.Drawing.Bitmap]::new($boardW, $boardH)
$bg = [System.Drawing.Graphics]::FromImage($board)
$bg.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$bg.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit
$bg.FillRectangle((Brush "#F7F9FC"), 0, 0, $boardW, $boardH)
Text $bg "Echo UI Refresh Preview" (FontC 34 "Bold") (Brush "#0F172A") 56 42 600 48
Text $bg "精致简约方向：低噪声背景、轻卡片、清晰信息层级、少装饰高可读。" (FontC 16) (Brush "#64748B") 58 96 760 30

$labels = @("主页", "对话", "上下文", "统计", "设置", "历史/文件夹")
$positions = @(
    @(56, 150), @(532, 150), @(1008, 150),
    @(56, 1000), @(532, 1000), @(1008, 1000)
)
for ($i = 0; $i -lt $outputs.Count; $i++) {
    $img = [System.Drawing.Image]::FromFile($outputs[$i])
    $x = $positions[$i][0]
    $y = $positions[$i][1]
    Text $bg $labels[$i] (FontC 18 "Bold") (Brush "#0F172A") $x ($y - 34) 180 28
    FillRound $bg (Brush "#FFFFFF") $x $y 390 844 34
    StrokeRound $bg (PenC "#CBD5E1" 1.2) $x $y 390 844 34
    $bg.SetClip((RoundPath $x $y 390 844 34))
    $bg.DrawImage($img, $x, $y, 390, 844)
    $bg.ResetClip()
    $img.Dispose()
}

$boardPath = Join-Path $Root "ui-refresh-board.png"
$board.Save($boardPath, [System.Drawing.Imaging.ImageFormat]::Png)
$bg.Dispose()
$board.Dispose()

$outputs + $boardPath | ForEach-Object {
    Get-Item $_ | Select-Object FullName, Length
}
