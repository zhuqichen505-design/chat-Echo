(() => {
  'use strict';
  const iconPaths = {
    chat: '<path d="M20 11a8 8 0 0 1-8 8H5l-3 3v-11a9 9 0 0 1 18 0Z"/>',
    book: '<path d="M12 5v16M3 3c4-1 7 0 9 2 2-2 5-3 9-2v15c-4-1-7 0-9 2-2-2-5-3-9-2Z"/>',
    chart: '<path d="M4 20V4m0 16h17M9 16v-5m5 5V6m5 10V9"/>',
    settings: '<path d="M4 6h16M4 12h16M4 18h16"/><circle cx="8" cy="6" r="2"/><circle cx="16" cy="12" r="2"/><circle cx="9" cy="18" r="2"/>',
    search: '<circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 5 5"/>',
    plus: '<path d="M12 4v16M4 12h16"/>',
    arrow: '<path d="M12 20V4m-7 7 7-7 7 7"/>',
    back: '<path d="m14 5-7 7 7 7"/>',
    next: '<path d="m9 5 7 7-7 7"/>',
    retry: '<path d="M20 8a9 9 0 1 0 0 8M20 3v5h-5"/>',
    copy: '<rect x="8" y="8" width="12" height="13" rx="2"/><path d="M15 8V3H3v12h5"/>',
    pin: '<path d="m9 3 6 0-1 6 4 4H6l4-4-1-6ZM12 13v8"/>',
    leaf: '<path d="M5 19C1 8 10 2 21 3c0 10-7 18-16 16Zm0 0L16 8"/>',
  };
  const icon = name => `<svg class="icon" viewBox="0 0 24 24" aria-hidden="true">${iconPaths[name] || iconPaths.chat}</svg>`;
  const scenes = ['home', 'chat', 'settings', 'stats'];
  const themes = ['porcelain', 'typefield', 'cinema', 'notebook', 'garden'];
  const sessions = [
    { title: '雾港来信', excerpt: '她终于认出了信上的那枚印记。', kind: '创作', time: '刚刚', book: true },
    { title: '雨后的散步路线', excerpt: '从河堤出发，绕过那片树林。', kind: '对话', time: '11:20', book: false },
    { title: '关于一座旧车站', excerpt: '钟停在了列车到来的前一分钟。', kind: '创作', time: '昨天', book: true },
    { title: '给未来的备忘', excerpt: '把那些还没成形的想法写下来。', kind: '对话', time: '昨天', book: false },
  ];
  const state = { scene: 'home', mode: 'auto', layout: 'bottom', background: 'theme', theme: 'porcelain', focus: false };
  const devices = [...document.querySelectorAll('.device')];
  const perDevice = new Map(devices.map(d => [d, { scene: 'home', filter: 'all', query: '', avatar: false, thinking: true, directions: true, count: 3, generation: 'replying' }]));
  const clock = '<div class="status-bar"><span>09:41</span><span aria-hidden="true">▮▮▮ &nbsp; ▰</span></div>';
  const leafDrawing = '<svg class="branch-art" viewBox="0 0 140 150" aria-hidden="true"><path d="M65 146C67 108 83 64 118 13M81 96C63 93 42 80 38 63c25-3 42 11 43 33ZM94 70c17-1 34-10 40-26-22-4-38 7-40 26ZM109 39C91 36 82 22 83 8c19 3 28 15 26 31ZM69 123C47 120 29 110 23 91c27-1 43 10 46 32Z"/><path d="M9 146c4-21 13-37 29-51M22 120c-13-1-21-9-22-20 12 1 20 7 22 20Z"/></svg>';
  const vista = '<svg class="vista-art" viewBox="0 0 420 180" aria-hidden="true"><defs><linearGradient id="sky" x2="0" y2="1"><stop stop-color="#35516a"/><stop offset="1" stop-color="#8d6d63"/></linearGradient><linearGradient id="water" x2="0" y2="1"><stop stop-color="#203447"/><stop offset="1" stop-color="#0a1928"/></linearGradient></defs><path fill="url(#sky)" d="M0 0h420v180H0z"/><circle cx="322" cy="45" r="18" fill="#e8cba2"/><path d="m0 91 80-36 63 41 65-59 84 64 61-32 67 33v78H0Z" fill="#273f50"/><path d="m0 119 87-19 77 23 102-25 77 18 77-9v73H0Z" fill="#112b3d"/><path fill="url(#water)" d="M0 133h420v47H0z"/><path d="M292 139h69m-53 9h38m-26 10h25" stroke="#9d8c78" stroke-opacity=".6" stroke-width="1"/><path d="M24 130h97v3H24zM39 97h2v36h-2zM100 91h2v42h-2zM41 98l60-6" fill="#081724" stroke="#081724"/><path d="M0 164h420" stroke="#344854" stroke-width=".5"/></svg>';
  const paperclip = '<svg class="paperclip" viewBox="0 0 34 76" aria-hidden="true"><path d="M12 53V16c0-14 18-14 18 0v43C30 79 3 79 3 59V14C3-3 24-3 24 14v42c0 10-12 10-12 0"/></svg>';
  function navigation(theme, selected) {
    return `<nav class="app-nav" aria-label="${theme}导航">${[['home', 'chat', '会话'], ['creative', 'book', '创作'], ['stats', 'chart', '统计'], ['settings', 'settings', '设置']].map(([scene, glyph, label]) => `<button data-open="${scene}" class="${selected === scene ? 'active' : ''}" ${selected === scene ? 'aria-current="page"' : ''}>${icon(glyph)}<span>${label}</span></button>`).join('')}</nav>`;
  }
  function pageHeader(title, subtitle = '雾港来信') { return `<header class="page-header"><button class="icon-button" data-open="home" aria-label="返回首页">${icon('back')}</button><div><strong>${title}</strong><small>${subtitle}</small></div><button class="icon-button" data-open="settings" aria-label="打开对话设置">${icon('settings')}</button></header>`; }
  function searchBox(placeholder = '搜索会话与故事') { return `<label class="search-box">${icon('search')}<input type="search" placeholder="${placeholder}" aria-label="搜索合成会话"></label>`; }
  function filters(theme, labels = ['全部', '对话', '创作']) { return `<div class="filters" role="group" aria-label="${theme}会话筛选">${['all', 'chat', 'creative'].map((filter, i) => `<button data-filter="${filter}" aria-pressed="${filter === 'all'}">${labels[i]}</button>`).join('')}</div>`; }
  function row(s, theme, index, extras = '') { return `<button class="session-row" data-search="${s.title} ${s.excerpt}" data-kind="${s.book ? 'creative' : 'chat'}" data-open="${s.book ? 'creative' : 'chat'}">${extras}<span class="session-copy"><strong>${s.title}</strong><span>${s.excerpt}</span></span><time>${s.time}</time>${theme === 'porcelain' ? icon('next') : ''}</button>`; }
  function modelIdentity(s) { return `<div class="model-identity"><span class="model-mark ${s.avatar ? 'avatar' : ''} ${s.generation !== 'done' ? 'working' : ''}" data-state="${s.generation}" aria-label="${s.generation === 'done' ? '回复完成' : '模型正在生成'}"><i></i><i></i></span><strong>Echo</strong><span class="model-state">${{ thinking: '正在思考', replying: '正在回复', done: '回复完成' }[s.generation]}</span></div>`; }
  const prose = '<p>信是在雨停之后送来的。</p><p>你站在门边，看着信封上那行熟悉的字。街道仍湿着，远处的路灯映在积水里，像一条还未收起的金线。</p><p>“如果明天有雾，就来旧车站。”</p><p>没有落款。只有信纸右下角，那枚你以为再也不会见到的印记。</p>';
  function thinking(s) { return `<div class="thinking-note" ${s.thinking && s.generation !== 'done' ? '' : 'hidden'}>正在梳理人物、场景与上一轮线索…</div>`; }
  function messageTools() { return `<div class="message-tools"><button data-action="copy" aria-label="复制合成样稿">${icon('copy')}</button><button data-action="replay" aria-label="重播回复动画">${icon('retry')}</button><span>合成样稿 · 不发送请求</span></div>`; }
  function composer(theme) { return `<form class="composer"><label class="sr-only" for="input-${theme}">输入本地样例消息</label><textarea id="input-${theme}" rows="1" required maxlength="500" placeholder="写下接下来的行动…"></textarea><div class="composer-tools"><button type="button" class="context-link" data-open="settings">人物 · 世界书 ${icon('next')}</button><button class="send" aria-label="发送本地样例消息">${icon('arrow')}</button></div></form>`; }
  function stateControls(s) { return `<div class="generation-controls" role="group" aria-label="模型演示状态">${[['thinking', '思考'], ['replying', '回复'], ['done', '完成']].map(([value, label]) => `<button data-generation="${value}" aria-pressed="${s.generation === value}">${label}</button>`).join('')}</div>`; }
  function switchButton(key, label, enabled) { return `<button class="switch ${enabled ? 'on' : ''}" role="switch" aria-label="${label}" aria-checked="${enabled}" data-toggle="${key}"><span></span></button>`; }
  function directionSettings(s) { return `<div class="setting-line"><div><strong>多方向回复</strong><small>先选方向，再继续故事</small></div>${switchButton('directions', '多方向回复', s.directions)}</div><div class="direction-count" ${s.directions ? '' : 'hidden'}><span>候选数量</span><div class="count-buttons" role="group" aria-label="候选数量">${[2, 3, 4].map(n => `<button data-count="${n}" aria-pressed="${n === s.count}">${n} 条</button>`).join('')}</div></div>`; }
  function memorySettings() { return `<details class="context-details"><summary><span>会话设定与时间线</span><small>2 项</small></summary><div class="details-body"><p><strong>当前地点</strong><span>旧车站外的街道</span></p><p><strong>事件线索</strong><span>一封没有署名的信</span></p><small>此处演示折叠、展开与设定阅读样式。</small></div></details><details class="context-details"><summary><span>世界书与提示词</span><small>1 本</small></summary><div class="details-body"><p><strong>雾港设定集</strong><span>按关键词匹配注入</span></p><small>此样稿不读取或导入真实文件。</small></div></details>`; }
  function readingSettings(s) { return `<div class="setting-line"><div><strong>显示模型头像</strong><small>关闭后显示动态同心圆</small></div>${switchButton('avatar', '显示模型头像', s.avatar)}</div><div class="setting-line"><div><strong>显示思考气泡</strong><small>只影响展示，不改变思考</small></div>${switchButton('thinking', '显示思考气泡', s.thinking)}</div>`; }
  function chart() { return `<div class="bar-chart" role="img" aria-label="周一至周日合成请求数分别为8、12、6、16、10、20、14次">${[8, 12, 6, 16, 10, 20, 14].map((n, i) => `<div><span style="--value:${n * 4.2}%"></span><small>${'一二三四五六日'[i]}</small></div>`).join('')}</div>`; }
  function statistics(theme) { const titles = { porcelain: '每一次对话，都有回响。', typefield: '一周的回声。', cinema: '故事之外的数字。', notebook: '这一周，写了多少？', garden: '这一周，长出了什么？' }; return `${pageHeader('用量统计', '最近七日 · 合成数据')}<main class="scroll-area stats-page"><h2>${titles[theme]}</h2><dl class="usage-summary"><div><dt>会话</dt><dd>12 <small>则</small></dd></div><div><dt>消息</dt><dd>86 <small>条</small></dd></div></dl><h3>每日请求</h3>${chart()}<dl class="token-table"><div><dt>输入 Token</dt><dd>18,420</dd></div><div><dt>输出 Token</dt><dd>24,680</dd></div></dl><p class="sample-note">演示数字，不读取真实使用数据。</p></main>${navigation(theme, 'stats')}`; }
  const template = {
    porcelain: {
      home: () => `<div class="home-shell"><main class="scroll-area porcelain-home"><header class="porcelain-mast"><span class="porcelain-logo">Echo<span>·</span></span><button class="new-orb" data-open="new" aria-label="新建会话">${icon('plus')}</button></header><div class="porcelain-heading"><h2>随时，接上想法。</h2><p>日常对话，也容得下一个新故事。</p></div>${searchBox()}${filters('瓷光')}<section class="porcelain-continuation"><div class="continuation-label"><span>${icon('pin')}继续上次</span><small>创作</small></div>${row(sessions[0], 'porcelain', 0, '<span class="porcelain-story-mark" aria-hidden="true">✧</span>')}</section><div class="list-heading">最近会话 <span>3</span></div><div class="porcelain-list">${sessions.slice(1).map((s, i) => row(s, 'porcelain', i, `<span class="porcelain-row-icon">${icon(s.book ? 'book' : 'chat')}</span>`)).join('')}</div><button class="porcelain-create" data-open="creative">${icon('book')}角色与创作<span>展开你的世界 ${icon('next')}</span></button><p class="porcelain-foot">想法会在这里，等你回来。</p></main>${navigation('porcelain', 'home')}</div>`,
      chat: s => `${pageHeader('雾港来信', '互动叙事 · 沉浸阅读')}<main class="scroll-area porcelain-reading"><div class="porcelain-chapter"><span>第二章</span><h2>雨停之后</h2></div><blockquote class="user-prompt"><p>我拆开那封信，仔细查看纸上的印记。</p><small>你 · 刚刚</small></blockquote>${modelIdentity(s)}${thinking(s)}<div class="response">${prose}</div>${messageTools()}${stateControls(s)}<div class="sent-messages" aria-live="polite"></div></main>${composer('porcelain')}`,
      settings: s => `${pageHeader('对话设置')}<main class="scroll-area porcelain-settings"><div class="porcelain-settings-intro">${icon('settings')}<div><h2>调整这一段对话</h2><p>设定、生成与阅读，各就其位。</p></div></div><section class="settings-slab"><h3>回复方式</h3>${directionSettings(s)}<h3>上下文</h3>${memorySettings()}<h3>阅读呈现</h3>${readingSettings(s)}</section><p class="sample-note">设置只影响本地样稿。</p></main>${navigation('porcelain', 'settings')}`,
    },
    typefield: {
      home: () => `<div class="home-shell"><main class="scroll-area typefield-home"><header class="typefield-mast"><span>echo<span class="red-stop">.</span></span><span>想法与对话</span></header><div class="typefield-heading"><h2>会话<span class="red-stop">。</span></h2><button data-open="new">新建 ${icon('plus')}</button></div>${searchBox('寻找一个标题或一句话')}${filters('字场')}<div class="typefield-date"><span>今天</span><small>接着聊，也接着写。</small></div><div class="typefield-list">${sessions.slice(0, 2).map((s, i) => row(s, 'typefield', i, `<span class="typefield-kind">${s.kind}</span>`)).join('')}</div><div class="typefield-date earlier"><span>更早</span><button data-open="creative">创作工作区 ↗</button></div><div class="typefield-list">${sessions.slice(2).map((s, i) => row(s, 'typefield', i, `<span class="typefield-kind">${s.kind}</span>`)).join('')}</div><p class="typefield-end">每个好故事，<br>从一句话开始。</p></main>${navigation('typefield', 'home')}</div>`,
      chat: s => `${pageHeader('雾港来信', '会话 / 第二章')}<main class="scroll-area typefield-reading"><div class="typefield-article-title"><span>互动叙事</span><h2>雨停之后<span class="red-stop">。</span></h2></div><div class="user-prompt"><strong>你的行动</strong><p>我拆开那封信，仔细查看纸上的印记。</p></div>${modelIdentity(s)}${thinking(s)}<div class="response">${prose}</div>${messageTools()}${stateControls(s)}<div class="sent-messages" aria-live="polite"></div></main>${composer('typefield')}`,
      settings: s => `${pageHeader('对话设置', '雾港来信 / 当前会话')}<main class="scroll-area typefield-settings"><h2>让回复<br>更合你意<span class="red-stop">。</span></h2><section class="typefield-setting-section"><h3>生成</h3><div>${directionSettings(s)}</div></section><section class="typefield-setting-section"><h3>记忆</h3><div>${memorySettings()}</div></section><section class="typefield-setting-section"><h3>阅读</h3><div>${readingSettings(s)}</div></section><p class="sample-note">此页只调整样稿，不改变 App。</p></main>${navigation('typefield', 'settings')}`,
    },
    cinema: {
      home: () => `<div class="home-shell"><main class="scroll-area cinema-home"><header class="cinema-mast"><strong>Echo</strong><span>对话与故事的放映室</span></header><div class="cinema-heading"><h2>此刻，继续。</h2><button class="icon-button" data-open="new" aria-label="新建会话">${icon('plus')}</button></div><button class="cinema-feature session-row" data-search="雾港来信 她终于认出了信上的那枚印记" data-kind="creative" data-open="creative">${vista}<span class="cinema-frame-top"><span>继续故事</span><span>第二章</span></span><span class="cinema-frame-caption"><strong>雾港来信</strong><small>她终于认出了信上的那枚印记。</small><span>回到现场 ↗</span></span></button><div class="cinema-toolbar">${searchBox('查找一段会话')}${filters('映夜', ['全部', '对话', '故事'])}</div><div class="cinema-list">${sessions.slice(1).map((s, i) => row(s, 'cinema', i, `<span class="cinema-thumbnail cinema-thumbnail-${i}" aria-hidden="true"><i></i><i></i><i></i></span>`)).join('')}</div><button class="cinema-workspace" data-open="creative"><span>${icon('book')}角色与创作</span><span>让下一个场景发生 ↗</span></button><p class="cinema-footer-label">每一次回到这里，故事都还在。</p></main>${navigation('cinema', 'home')}</div>`,
      chat: s => `${pageHeader('雾港来信', '第二章 · 雨停之后')}<div class="cinema-scene-strip">${vista}<span>旧车站外 · 夜</span><button data-open="settings">设定与时间线 ${icon('next')}</button></div><main class="scroll-area cinema-reading"><div class="user-prompt"><span>你的行动</span><p>我拆开那封信，仔细查看纸上的印记。</p></div>${modelIdentity(s)}${thinking(s)}<div class="response">${prose}</div>${messageTools()}${stateControls(s)}<div class="sent-messages" aria-live="polite"></div></main>${composer('cinema')}`,
      settings: s => `${pageHeader('对话设置', '雾港来信 · 当前场景')}<main class="scroll-area cinema-settings"><div class="cinema-setting-title"><span class="cinema-aperture" aria-hidden="true"></span><div><h2>故事，由你掌镜。</h2><p>规则清楚，才更容易沉浸。</p></div></div><section class="cinema-setting-section"><header><h3>生成方式</h3><small>REPLY</small></header>${directionSettings(s)}</section><section class="cinema-setting-section"><header><h3>故事记忆</h3><small>CONTEXT</small></header>${memorySettings()}</section><section class="cinema-setting-section"><header><h3>消息呈现</h3><small>READING</small></header>${readingSettings(s)}</section><p class="sample-note">设置只影响眼前的合成样稿。</p></main>${navigation('cinema', 'settings')}`,
    },
    notebook: {
      home: () => `<div class="home-shell"><main class="scroll-area notebook-home"><header class="notebook-mast"><strong>Echo</strong><span class="notebook-seal">随记</span>${paperclip}</header><div class="notebook-heading"><h2>把想法，<br>写在这里。</h2><button class="notebook-new" data-open="new" aria-label="新建会话">${icon('plus')}</button></div>${filters('织页', ['全部页', '便笺', '故事'])}${searchBox('在这本会话簿里找找')}<div class="notebook-list">${sessions.map((s, i) => row(s, 'notebook', i, `<span class="notebook-page-no">${String(i + 1).padStart(2, '0')}</span><span class="notebook-page-tab ${s.book ? 'story-tab' : ''}">${s.kind}</span>`)).join('')}</div><button class="notebook-creative" data-open="creative"><span>${icon('book')}打开创作页</span><span>角色、世界书与故事 ↗</span></button><div class="notebook-end"><span>未完待续</span><i aria-hidden="true">e</i></div></main>${navigation('notebook', 'home')}</div>`,
      chat: s => `${pageHeader('雾港来信', '会话簿 · 第二页')}<main class="scroll-area notebook-reading"><div class="notebook-chapter"><span>一段未写完的故事</span><h2>雨停之后</h2><i aria-hidden="true">续</i></div><div class="user-prompt"><span>我的下一步</span><p>我拆开那封信，仔细查看纸上的印记。</p></div>${modelIdentity(s)}${thinking(s)}<div class="response">${prose}</div>${messageTools()}${stateControls(s)}<div class="sent-messages" aria-live="polite"></div></main>${composer('notebook')}`,
      settings: s => `${pageHeader('对话设置', '雾港来信 · 本页规则')}<main class="scroll-area notebook-settings"><div class="notebook-setting-intro"><h2>这本故事，<br>由你编排。</h2><span class="notebook-seal">设置</span></div><section class="notebook-setting-section"><h3><span>生成</span></h3>${directionSettings(s)}</section><section class="notebook-setting-section"><h3><span>记忆</span></h3>${memorySettings()}</section><section class="notebook-setting-section"><h3><span>阅读</span></h3>${readingSettings(s)}</section><p class="sample-note">开关与数量仅用于此预览。</p></main>${navigation('notebook', 'settings')}`,
    },
    garden: {
      home: () => `<div class="home-shell"><main class="scroll-area garden-home"><header class="garden-mast"><strong>Echo</strong><span>想法的小径</span><button class="garden-new" data-open="new" aria-label="新建会话">${icon('plus')}</button></header><div class="garden-heading"><div><h2>让故事，<br>在这里生长。</h2><p>走慢一点，也能想得更远。</p></div>${leafDrawing}</div>${searchBox('寻找一段对话')}<section class="garden-continuation"><div class="garden-arch" aria-hidden="true">${leafDrawing}<span>雾港</span></div><div><span class="garden-section-label">接着上次的故事</span><button class="session-row" data-search="雾港来信 她终于认出了信上的那枚印记" data-kind="creative" data-open="creative"><span class="session-copy"><strong>雾港来信</strong><span>那枚印记，究竟来自哪里？</span></span><span class="garden-go">${icon('next')}</span></button></div></section>${filters('游园')}<div class="garden-list">${sessions.slice(1).map((s, i) => row(s, 'garden', i, `<span class="garden-seed" aria-hidden="true">${icon(s.book ? 'leaf' : 'chat')}</span>`)).join('')}</div><button class="garden-creative" data-open="creative"><span>${icon('book')}去创作里走走</span>${icon('next')}</button></main>${navigation('garden', 'home')}</div>`,
      chat: s => `${pageHeader('雾港来信', '第二章 · 雨停之后')}<main class="scroll-area garden-reading"><div class="garden-chat-flourish">${leafDrawing}<span>故事慢慢展开</span></div><blockquote class="user-prompt"><p>我拆开那封信，仔细查看纸上的印记。</p><small>你 · 刚刚</small></blockquote>${modelIdentity(s)}${thinking(s)}<div class="response">${prose}</div>${messageTools()}${stateControls(s)}<div class="sent-messages" aria-live="polite"></div></main>${composer('garden')}`,
      settings: s => `${pageHeader('对话设置')}<main class="scroll-area garden-settings"><div class="garden-setting-intro"><h2>照料好<br>这一个世界。</h2>${leafDrawing}</div><section class="garden-setting-section"><h3>回复的枝桠</h3>${directionSettings(s)}</section><section class="garden-setting-section"><h3>故事的根系</h3>${memorySettings()}</section><section class="garden-setting-section"><h3>阅读的呼吸</h3>${readingSettings(s)}</section><p class="sample-note">仅调整本地样稿，正式设置以 App 为准。</p></main>${navigation('garden', 'settings')}`,
    },
  };
  function render(device) {
    const theme = device.dataset.theme, s = perDevice.get(device), scene = s.scene;
    const page = scene === 'creative' || scene === 'new' ? 'chat' : scene;
    device.dataset.scene = page;
    device.innerHTML = clock + (page === 'stats' ? statistics(theme) : template[theme][page](s));
    if (page === 'home') {
      device.querySelectorAll('[data-filter]').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.filter === s.filter)));
      device.querySelector('input[type=search]').value = s.query;
      applyFilter(device);
    }
    if (scene === 'new') {
      device.querySelector('.user-prompt').hidden = true;
      device.querySelector('.thinking-note').hidden = true;
      device.querySelector('.response').innerHTML = '<p>这是一个新的本地预览会话。</p><p>写下一句话，可以查看输入与正文的排版。内容不会发送，也不会保存。</p>';
    }
    if (scene === 'creative') device.querySelector('.page-header small').textContent = '创作 · 人物与世界书已关联';
  }
  function applyFilter(device) {
    const s = perDevice.get(device), term = s.query.trim().toLocaleLowerCase();
    device.querySelectorAll('.session-row').forEach(row => { row.hidden = !(s.filter === 'all' || row.dataset.kind === s.filter) || !row.dataset.search.toLocaleLowerCase().includes(term); });
    const feature = device.querySelector('.porcelain-continuation,.garden-continuation');
    if (feature) feature.hidden = feature.querySelector('.session-row').hidden;
    const recentCount = device.querySelector('.list-heading>span');
    if (recentCount) recentCount.textContent = String([...device.querySelectorAll('.porcelain-list .session-row')].filter(row => !row.hidden).length);
    let empty = device.querySelector('.empty-search');
    if (!empty) { empty = document.createElement('p'); empty.className = 'empty-search'; empty.setAttribute('role', 'status'); empty.textContent = '没有找到匹配的会话，试试其他关键词。'; device.querySelector('.scroll-area').append(empty); }
    empty.hidden = [...device.querySelectorAll('.session-row')].some(row => !row.hidden);
  }
  function sync() {
    devices.forEach(device => {
      device.dataset.mode = state.mode === 'auto' ? device.dataset.theme === 'cinema' ? 'dark' : 'light' : state.mode;
      device.dataset.background = state.background; device.dataset.layout = state.layout;
    });
    document.querySelectorAll('.control-desk [data-scene]').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.scene === state.scene)));
    document.querySelectorAll('.control-desk [data-mode]').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.mode === state.mode)));
    document.querySelectorAll('.theme-picker [data-theme]').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.theme === state.theme)));
    document.querySelector('.gallery').dataset.focus = state.theme;
    document.body.classList.toggle('focus-mode', state.focus);
    document.querySelector('#compare').setAttribute('aria-pressed', String(state.focus));
    document.querySelector('#compare').textContent = state.focus ? '并排比较' : '单独放大';
    document.querySelector('#layout').value = state.layout; document.querySelector('#background').value = state.background;
    history.replaceState(null, '', `#scene=${state.scene}&mode=${state.mode}&layout=${state.layout}&theme=${state.theme}&bg=${state.background}&view=${state.focus ? 'single' : 'compare'}`);
  }
  document.querySelector('.control-desk').addEventListener('click', e => {
    const button = e.target.closest('button'); if (!button) return;
    if (button.dataset.scene) { state.scene = button.dataset.scene; devices.forEach(device => { perDevice.get(device).scene = state.scene; render(device); }); }
    if (button.dataset.mode) state.mode = button.dataset.mode;
    if (button.id === 'compare') state.focus = !state.focus;
    sync();
  });
  document.querySelector('#layout').addEventListener('change', e => { state.layout = e.target.value; sync(); });
  document.querySelector('#background').addEventListener('change', e => { state.background = e.target.value; sync(); });
  document.querySelector('.theme-picker').addEventListener('click', e => { const button = e.target.closest('[data-theme]'); if (button) { state.theme = button.dataset.theme; if (innerWidth > 1000) state.focus = true; sync(); } });
  document.querySelector('.gallery').addEventListener('input', e => {
    if (e.target.type === 'search') { const device = e.target.closest('.device'); perDevice.get(device).query = e.target.value; applyFilter(device); }
  });
  document.querySelector('.gallery').addEventListener('click', async e => {
    const button = e.target.closest('button'); if (!button) return;
    if (button.dataset.focus) { state.theme = button.dataset.focus; state.focus = true; sync(); document.querySelector('.control-desk').scrollIntoView({ block: 'start' }); return; }
    const device = button.closest('.device'); if (!device) return;
    const s = perDevice.get(device);
    if (button.dataset.open) { s.scene = button.dataset.open; render(device); return; }
    if (button.dataset.filter) { s.filter = button.dataset.filter; device.querySelectorAll('[data-filter]').forEach(item => item.setAttribute('aria-pressed', String(item.dataset.filter === s.filter))); applyFilter(device); }
    if (button.dataset.toggle) {
      const key = button.dataset.toggle; s[key] = !s[key]; button.setAttribute('aria-checked', String(s[key])); button.classList.toggle('on', s[key]);
      if (key === 'directions') device.querySelector('.direction-count').hidden = !s.directions;
    }
    if (button.dataset.count) { s.count = Number(button.dataset.count); device.querySelectorAll('[data-count]').forEach(item => item.setAttribute('aria-pressed', String(Number(item.dataset.count) === s.count))); }
    if (button.dataset.generation || button.dataset.action === 'replay') {
      s.generation = button.dataset.generation || 'replying';
      const mark = device.querySelector('.model-mark'); mark.classList.toggle('working', s.generation !== 'done'); mark.dataset.state = s.generation;
      device.querySelector('.model-state').textContent = { thinking: '正在思考', replying: '正在回复', done: '回复完成' }[s.generation];
      device.querySelector('.thinking-note').hidden = !s.thinking || s.generation === 'done';
      device.querySelectorAll('[data-generation]').forEach(item => item.setAttribute('aria-pressed', String(item.dataset.generation === s.generation)));
    }
    if (button.dataset.action === 'copy') {
      try { await navigator.clipboard.writeText(device.querySelector('.response').textContent); showToast('已复制合成样稿正文。'); }
      catch { showToast('浏览器未开放剪贴板，可以直接选择正文复制。'); }
    }
  });
  document.querySelector('.gallery').addEventListener('submit', e => {
    if (!e.target.classList.contains('composer')) return; e.preventDefault();
    const textarea = e.target.querySelector('textarea'), value = textarea.value.trim(); if (!value) return;
    const device = e.target.closest('.device'), output = document.createElement('div'); output.className = 'sample-sent';
    const message = document.createElement('p'); message.className = 'sample-user'; message.textContent = value;
    const reply = document.createElement('p'); reply.textContent = '这段输入只用于比较当前主题的显示效果。正式回复、设定与时间线由 App 处理。';
    output.append(message, reply); device.querySelector('.sent-messages').append(output); textarea.value = '';
    output.scrollIntoView({ block: 'nearest', behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth' });
  });
  let toastTimer;
  function showToast(text) { const toast = document.querySelector('.toast'); toast.textContent = text; toast.classList.add('visible'); clearTimeout(toastTimer); toastTimer = setTimeout(() => toast.classList.remove('visible'), 3000); }
  const params = new URLSearchParams(location.hash.slice(1));
  for (const [key, values, hashKey] of [['scene', scenes, 'scene'], ['mode', ['auto', 'light', 'dark'], 'mode'], ['layout', ['bottom', 'rail'], 'layout'], ['theme', themes, 'theme'], ['background', ['theme', 'white', 'black'], 'bg']]) { if (values.includes(params.get(hashKey))) state[key] = params.get(hashKey); }
  state.focus = params.get('view') === 'single';
  devices.forEach(device => { perDevice.get(device).scene = state.scene; render(device); }); sync();
})();
