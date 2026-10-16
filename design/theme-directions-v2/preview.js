'use strict';

const icons = {
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
  moon: '<path d="M20.5 13a9 9 0 0 1-9.5-9.5A9 9 0 1 0 20.5 13Z"/>',
};
const icon = name => `<svg viewBox="0 0 24 24" aria-hidden="true">${icons[name] || icons.chat}</svg>`;
const sessions = [
  { title: '雾中来信', detail: '她认出了信封上熟悉的字迹。', time: '刚刚', kind: '互动叙事', book: true },
  { title: '雨后的散步路线', detail: '从河堤出发，绕过那片树林。', time: '11:20', kind: '日常对话', book: false },
  { title: '关于一座旧车站', detail: '钟停在了列车到来的前一分钟。', time: '昨天', kind: '小说创作', book: true },
  { title: '给未来的备忘', detail: '把那些还没成形的想法写下来。', time: '昨天', kind: '日常对话', book: false },
];
const state = { scene: 'home', mode: 'light', layout: 'bottom', background: 'theme', theme: 'binding' };
const devices = [...document.querySelectorAll('.device')];
const local = new Map(devices.map(device => [device, { scene: 'home', avatar: false, thinking: true, directions: true, count: 3, working: true }]));
const status = '<div class="status-bar"><span>09:41</span><span aria-hidden="true">▮▮▮ &nbsp; ▰</span></div>';
function nav(theme, selected) {
  const labels = [['home', 'chat', '会话'], ['creative', 'book', '创作'], ['stats', 'chart', '统计'], ['settings', 'settings', '设置']];
  return `<nav class="app-nav ${theme}-nav" aria-label="${'装帧'}导航">${labels.map(([scene, symbol, label], i) => `<button data-open="${scene}" class="${selected === scene ? 'active' : ''}">${icon(symbol)}<span>${label}</span></button>`).join('')}</nav>`;
}
function pageHeader(title, theme, caption = '') { return `<header class="page-top"><button class="icon-button" data-open="home" aria-label="返回首页">${icon('back')}</button><div><strong>${title}</strong>${caption ? `<small>${caption}</small>` : ''}</div><button class="icon-button" data-open="settings" aria-label="对话设置">${icon('settings')}</button></header>`; }
function search(placeholder) { return `<label class="search-box">${icon('search')}<input type="search" placeholder="${placeholder}" aria-label="搜索样例会话"></label>`; }
function model(s) { return `<div class="model-identity"><span class="model-mark ${s.avatar ? 'avatar' : ''} ${s.working ? 'working' : ''}" aria-label="${s.working ? '模型正在回复' : '回复已完成'}"><i></i><i></i></span><span>Echo</span><small class="generation-state">${s.working ? '正在回复' : '回复完成'}</small><button class="state-toggle" data-action="state">${s.working ? '完成' : '重播'}</button></div>`; }
const paragraphs = '<p>信是在雨停之后送来的。</p><p>你站在门边，看着信封上那行熟悉的字。街道仍湿着，远处的路灯映在积水里，像一条还未收起的金线。</p><p>“如果明天有雾，就来旧车站。”</p><p>没有落款。只有信纸右下角，那枚你以为再也不会见到的印记。</p>';
function composer(theme) { return `<form class="composer ${theme}-composer"><label class="sr-only" for="input-${theme}">输入样例消息</label><textarea id="input-${theme}" rows="1" placeholder="写下你的下一步…" required maxlength="500"></textarea><div class="composer-row"><button type="button" class="context-link" data-open="settings">人物 · 世界书 ${icon('next')}</button><button class="send" aria-label="发送本地预览消息">${icon('arrow')}</button></div></form>`; }
function tools() { return `<div class="message-tools"><button data-action="copy" aria-label="复制样稿正文">${icon('copy')}</button><button data-action="replay" aria-label="重播模型标识动画">${icon('retry')}</button><small>合成样稿 · 仅供预览</small></div>`; }
function toggle(name, label, checked) { return `<button class="switch ${checked ? 'on' : ''}" role="switch" aria-checked="${checked}" data-toggle="${name}" aria-label="${label}"><span></span></button>`; }
function directionSettings(s) { return `<div class="setting-line"><div><strong>多方向回复</strong><small>先选方向，再继续故事</small></div>${toggle('directions', '多方向回复', s.directions)}</div><div class="direction-options" ${s.directions ? '' : 'hidden'}><span>候选数量</span><div class="count-buttons" role="group" aria-label="候选数量">${[2, 3, 4].map(n => `<button data-count="${n}" aria-pressed="${s.count === n}">${n} 条</button>`).join('')}</div></div>`; }
function commonSettings(s) { return `<div class="setting-line"><div><strong>显示模型头像</strong><small>关闭后显示动态同心圆</small></div>${toggle('avatar', '显示模型头像', s.avatar)}</div><div class="setting-line"><div><strong>显示思考气泡</strong><small>只影响展示，不改变思考</small></div>${toggle('thinking', '显示思考气泡', s.thinking)}</div>`; }
function settingDetails() { return `<details class="context-details"><summary><span>时间线与会话设定</span><span>2 项</span></summary><div class="detail-body"><p><strong>当前地点</strong><span>旧车站外的街道</span></p><p><strong>事件线索</strong><span>一封没有署名的信</span></p><small>此处展示设定的折叠与阅读样式。</small></div></details><details class="context-details"><summary><span>世界书与提示词</span><span>1 本</span></summary><div class="detail-body"><p><strong>雾港设定集</strong><span>按关键词匹配注入</span></p><small>样稿仅展示当前会话设置，不导入文件。</small></div></details>`; }

const templates = {
  binding: {
    home: () => `<div class="app-body home-body"><main class="binding-home scroll-area"><header class="binding-mast"><span class="binding-brand">Echo</span><span>会话录</span><i aria-hidden="true">E</i></header><div class="binding-title"><span>继续，或从头写起。</span><h2>未完的故事</h2><div class="binding-title-rule"><span>私人会话目录</span><span>四则</span></div></div>${search('在会话目录中查找')}<div class="binding-new"><button data-open="new">${icon('plus')}开启一则会话</button><button data-open="creative">进入创作 ${icon('next')}</button></div><div class="binding-list">${sessions.map((s, i) => `<button class="session-row" data-title="${s.title}" data-open="${s.book ? 'creative' : 'chat'}"><span class="binding-index">${String(i + 1).padStart(2, '0')}</span><span class="session-copy"><small>${s.kind}</small><strong>${s.title}</strong><span>${s.detail}</span></span><time>${s.time}</time></button>`).join('')}</div><div class="binding-colophon"><span>对话会留下纹理。</span><span>Echo / 本地样稿</span></div></main>${nav('binding', 'home')}</div>`,
    chat: (s, creative) => `${pageHeader('雾中来信', 'binding', creative ? '创作 · 人物与世界书已关联' : '会话 · 沉浸阅读')}<main class="scroll-area binding-reading"><div class="binding-chapter"><span>第二章</span><h2>雨，刚刚停下。</h2><span class="chapter-rule"></span></div><blockquote class="user-prompt"><small>你写下</small><p>我拆开那封信，仔细查看纸上的印记。</p></blockquote>${model(s)}<div class="thinking" ${s.thinking ? '' : 'hidden'}>正在梳理人物与上一轮线索…</div><div class="response">${paragraphs}</div>${tools()}<div class="sent-messages" aria-live="polite"></div></main>${composer('binding')}`,
    settings: s => `${pageHeader('对话设置', 'binding', '雾中来信')}<main class="scroll-area binding-settings"><div class="binding-setting-title"><h2>让故事，<br>按你的方式继续。</h2><span>本会话专属设置</span></div><h3 class="section-title">生成方式</h3>${directionSettings(s)}<h3 class="section-title">故事的来处</h3>${settingDetails()}<h3 class="section-title">阅读与展示</h3>${commonSettings(s)}<div class="setting-footnote">此页只调整样稿，App 设置不会改变。</div></main>${nav('binding', 'settings')}`,
    stats: () => `${pageHeader('用量统计', 'binding', '合成数据 · 最近七日')}<main class="scroll-area binding-statistics"><h2>写过的，<br>都算数。</h2><dl class="binding-ledger"><div><dt>会话</dt><dd>12 <small>则</small></dd></div><div><dt>消息</dt><dd>86 <small>条</small></dd></div><div><dt>输入用量</dt><dd>18,420 <small>Token</small></dd></div><div><dt>输出用量</dt><dd>24,680 <small>Token</small></dd></div></dl>${barChart('binding')}<p class="data-boundary">演示数字，不读取真实用量。</p></main>${nav('binding', 'stats')}`,
  },

};
function barChart(theme) { return `<div class="bar-chart ${theme}-chart" role="img" aria-label="七日合成请求分布：周一8次、周二12次、周三6次、周四16次、周五10次、周六20次、周日14次">${[8, 12, 6, 16, 10, 20, 14].map((n, i) => `<div><span style="--value:${n * 5}%"></span><small>${'一二三四五六日'[i]}</small></div>`).join('')}</div>`; }

function render(device) {
  const theme = 'binding';
  const s = local.get(device), scene = s.scene;
  const page = scene === 'creative' || scene === 'new' ? 'chat' : scene;
  device.dataset.scene = page;
  device.innerHTML = status + templates[theme][page](s, scene === 'creative');
  if (scene === 'new') {
    device.querySelector('.response').innerHTML = '<p>这是一个新的本地预览会话。</p><p>在下面输入一句话，可以查看输入与回复的排版。内容不会发送或保存。</p>';
    device.querySelector('.user-prompt').hidden = true;
    device.querySelector('.thinking').hidden = true;
  }
}
function updateGlobal() {
  devices.forEach(device => { device.dataset.mode = state.mode; device.dataset.layout = state.layout; device.dataset.background = state.background; });
  document.querySelectorAll('[data-mode]').forEach(button => { if (button.tagName === 'BUTTON') button.setAttribute('aria-pressed', String(button.dataset.mode === state.mode)); });
  document.querySelectorAll('.preview-controls [data-scene]').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.scene === state.scene)));
  document.querySelector('.studies').dataset.visible = state.theme;
  document.querySelector('#layout').value = state.layout;
  document.querySelector('#background').value = state.background;
  history.replaceState(null, '', `#scene=${state.scene}&mode=${state.mode}&layout=${state.layout}&theme=${state.theme}&bg=${state.background}`);
}
document.querySelector('.preview-controls').addEventListener('click', e => {
  const button = e.target.closest('button'); if (!button) return;
  if (button.dataset.scene) { state.scene = button.dataset.scene; devices.forEach(device => { local.get(device).scene = state.scene; render(device); }); }
  if (button.dataset.mode) state.mode = button.dataset.mode;
  updateGlobal();
});
document.querySelector('#layout').addEventListener('change', e => { state.layout = e.target.value; updateGlobal(); });
document.querySelector('#background').addEventListener('change', e => { state.background = e.target.value; updateGlobal(); });
document.querySelector('.studies').addEventListener('input', e => {
  if (e.target.type !== 'search') return;
  const term = e.target.value.trim().toLocaleLowerCase();
  e.target.closest('.device').querySelectorAll('.session-row').forEach(row => { row.hidden = !row.textContent.toLocaleLowerCase().includes(term); });
});
document.querySelector('.studies').addEventListener('click', async e => {
  const button = e.target.closest('button'), device = button?.closest('.device'); if (!button || !device) return;
  const s = local.get(device);
  if (button.dataset.open) { s.scene = button.dataset.open; render(device); return; }
  if (button.dataset.toggle) {
    const key = button.dataset.toggle; s[key] = !s[key];
    button.classList.toggle('on', s[key]); button.setAttribute('aria-checked', String(s[key]));
    if (key === 'directions') device.querySelector('.direction-options').hidden = !s[key];
  }
  if (button.dataset.count) { s.count = Number(button.dataset.count); button.parentElement.querySelectorAll('button').forEach(item => item.setAttribute('aria-pressed', String(Number(item.dataset.count) === s.count))); }
  if (button.dataset.action === 'state' || button.dataset.action === 'replay') {
    s.working = button.dataset.action === 'replay' ? true : !s.working;
    device.querySelector('.model-mark').classList.toggle('working', s.working);
    device.querySelector('.generation-state').textContent = s.working ? '正在回复' : '回复完成';
    device.querySelector('.state-toggle').textContent = s.working ? '完成' : '重播';
    device.querySelector('.thinking').hidden = !s.thinking || !s.working;
  }
  if (button.dataset.action === 'copy') {
    try { await navigator.clipboard.writeText(device.querySelector('.response').textContent); toast('已复制合成样稿正文。'); }
    catch { toast('浏览器未允许复制，可以直接选择正文复制。'); }
  }
});
document.querySelector('.studies').addEventListener('submit', e => {
  if (!e.target.classList.contains('composer')) return;
  e.preventDefault();
  const textarea = e.target.querySelector('textarea'), message = textarea.value.trim(); if (!message) return;
  const device = e.target.closest('.device'), container = device.querySelector('.sent-messages');
  const block = document.createElement('div'); block.className = 'sample-sent';
  const user = document.createElement('p'); user.className = 'sample-user'; user.textContent = message;
  const reply = document.createElement('p'); reply.className = 'sample-reply'; reply.textContent = '这条输入仅展示本地排版。正式回复、世界书注入与时间线提取由 App 完成。';
  block.append(user, reply); container.append(block); textarea.value = '';
  block.scrollIntoView({ block: 'nearest', behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth' });
});
let toastTimer;
function toast(message) { const box = document.querySelector('.toast'); box.textContent = message; box.classList.add('visible'); clearTimeout(toastTimer); toastTimer = setTimeout(() => box.classList.remove('visible'), 3000); }
const params = new URLSearchParams(location.hash.slice(1));
for (const [key, allowed, hashKey] of [['scene', ['home', 'chat', 'settings', 'stats'], 'scene'], ['mode', ['light', 'dark'], 'mode'], ['layout', ['bottom', 'rail'], 'layout'], ['background', ['theme', 'white', 'black'], 'bg'], ['theme', ['binding'], 'theme']]) { if (allowed.includes(params.get(hashKey))) state[key] = params.get(hashKey); }
devices.forEach(device => { local.get(device).scene = state.scene; render(device); });
updateGlobal();
