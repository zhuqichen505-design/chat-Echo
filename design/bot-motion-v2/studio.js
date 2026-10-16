'use strict';
const { BOT_CATALOG, drawBot, samplePose } = globalThis.BotRigs;

const STATES = ['connecting', 'thinking', 'replying', 'completed'];
const LABELS = { connecting:'正在连接', thinking:'正在思考', replying:'正在输出', completed:'回复完成', idle:'陪你待一会儿' };
const DURATION = { connecting:2.8, thinking:4.4, replying:4.8, completed:3.2, idle:1.8 };
const SEQUENCE = [...STATES, 'idle'];
const SAMPLE = '雨停了。你推开门，树叶还在往下滴水。路灯把细碎的水珠照得很亮，像有人在夜里悄悄撒了一把星星。';
const $ = selector => document.querySelector(selector);
const media = matchMedia('(prefers-reduced-motion: reduce)');
let selection = BOT_CATALOG[0].id;
let state = 'connecting';
let age = 0;
let time = 0;
let speed = 1;
let auto = true;
let paused = false;
let manualReduce = false;
let mono = false;
let dark = false;
let background = 'theme';
let dirty = true;
let lastStamp = null;
let interactionAt = -100;
let completionAge = 3;
let testClock = null;
let pointer = { x:0, y:0 };
let pointerTarget = { x:0, y:0 };
let weights = Object.fromEntries(SEQUENCE.map(s => [s, s === state ? 1 : 0]));
let transitionStart = { ...weights };
let transitionAge = 1;
let lastMessageLength = -1;
let heroSize = 235;
let animationFrameId = null;
const reduced = () => manualReduce || media.matches;
const selected = () => BOT_CATALOG.find(bot => bot.id === selection);
const canvases = [];
function requestFrame() {
  if (animationFrameId === null && !document.hidden) animationFrameId = requestAnimationFrame(frame);
}
function invalidate() { dirty = true; requestFrame(); }

$('.cast').innerHTML = BOT_CATALOG.map(bot => `<button class="cast-choice" data-bot="${bot.id}" aria-pressed="${bot.id === selection}" aria-label="选择${bot.name}"><canvas data-cast="${bot.id}" width="220" height="220" role="img" aria-label="${bot.name}动画"></canvas><span><strong>${bot.name}</strong><small>${bot.subtitle}</small></span><i class="selection-dot" aria-hidden="true"></i></button>`).join('');
$('.timeline').innerHTML = STATES.map(s => `<button data-step="${s}" aria-label="查看${LABELS[s]}动作" aria-current="${s === state ? 'step' : 'false'}"><i aria-hidden="true"></i><span>${LABELS[s].replace('正在','')}</span></button>`).join('');
function addCanvas(element, getBot, getSize, interactive = false) {
  canvases.push({ element, context:element.getContext('2d'), getBot, getSize, interactive, visible:true });
}
document.querySelectorAll('[data-cast]').forEach(el => addCanvas(el, () => el.dataset.cast, () => el.getBoundingClientRect().width));
addCanvas($('#hero-canvas'), () => selection, () => heroSize, true);
document.querySelectorAll('.identity-canvas').forEach(el => {
  const size = Number(el.dataset.size);
  el.style.width = `${size}px`;
  el.style.height = `${size}px`;
  addCanvas(el, () => selection, () => size);
});
const observer = new IntersectionObserver(entries => {
  entries.forEach(entry => {
    const item = canvases.find(item => item.element === entry.target);
    if (item) item.visible = entry.isIntersecting;
  });
  invalidate();
}, { rootMargin:'100px' });
canvases.forEach(item => observer.observe(item.element));
const resize = new ResizeObserver(() => { heroSize = $('#hero-canvas').getBoundingClientRect().width; invalidate(); });
resize.observe($('#hero-canvas'));

function syncState() {
  document.body.dataset.state = state;
  $('#stage-status').textContent = LABELS[state];
  $('#chat-state').textContent = LABELS[state];
  document.querySelectorAll('[data-state]').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.state === state)));
  document.querySelectorAll('[data-step]').forEach(button => button.setAttribute('aria-current', button.dataset.step === state ? 'step' : 'false'));
  $('#motion-caption').textContent = selected().motions[state] || '慢慢安静下来，留在这里陪你。';
  $('#hero-canvas').setAttribute('aria-label', `${selected().name}，${LABELS[state]}`);
  lastMessageLength = -1;
  invalidate();
}
function setState(next, automatic = false) {
  if (!SEQUENCE.includes(next)) return;
  transitionStart = { ...weights };
  transitionAge = 0;
  state = next;
  age = 0;
  if (state === 'completed') completionAge = 0;
  if (reduced() || paused) {
    weights = Object.fromEntries(SEQUENCE.map(s => [s,s === state ? 1 : 0]));
    transitionStart = {...weights};
    transitionAge = 1;
    if (state === 'completed') { age = 3; completionAge = 3; }
  }
  if (!automatic) auto = false;
  $('#auto').setAttribute('aria-pressed', String(auto));
  syncState();
}
function selectBot(id) {
  if (!BOT_CATALOG.some(bot => bot.id === id)) return;
  selection = id;
  document.querySelectorAll('[data-bot]').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.bot === id)));
  $('#selected-name').textContent = selected().name;
  $('#character-note').textContent = selected().description;
  $('#stage-touch').setAttribute('aria-label', `和${selected().name}打招呼`);
  syncState();
}
function syncOptions() {
  $('#pause').setAttribute('aria-pressed', String(paused));
  $('#pause').textContent = paused ? '继续' : '暂停';
  $('#access-note').textContent = reduced() ? '已减弱动态：保留状态姿态，停止位移、眨眼与连续演出。' : paused ? '已暂停：切换状态可查看对应静姿，点“继续”恢复动作。' : '动作只在身份标识内播放，正文保持稳定。';
  $('#auto').disabled = reduced();
  $('#pause').disabled = reduced();
  $('#speed').disabled = reduced();
  document.body.dataset.motion = reduced() ? 'reduced' : 'full';
  if (reduced()) {
    weights = Object.fromEntries(SEQUENCE.map(s => [s,s === state ? 1 : 0]));
    transitionStart = {...weights};
    transitionAge = 1;
    pointer = {x:0,y:0};
    if (state === 'completed') { age = Math.max(age,3); completionAge = 3; }
  }
  invalidate();
}
function effectiveDark() { return background === 'black' || (background === 'theme' && dark); }
function updateMessage() {
  let text;
  if (state === 'connecting') text = '正在接通信号，小伙伴已经听见你了。';
  else if (state === 'thinking') text = '想一想，雨后的街道应该从哪一束光开始……';
  else if (state === 'idle' && !auto) text = '这一轮停在这里。准备好时，再继续聊。';
  else if (state === 'replying') text = SAMPLE.slice(0, Math.min(SAMPLE.length, Math.floor(age / DURATION.replying * SAMPLE.length * 1.1)));
  else text = SAMPLE;
  if (lastMessageLength !== text.length || $('#sample-message').textContent !== text) {
    $('#sample-message').textContent = text;
    lastMessageLength = text.length;
  }
}
function optionsFor(id, size, interactive = false) {
  return { time, state, stateAge:age, completionAge, weights, reducedMotion:reduced(), mono, dark:effectiveDark(),
    pointer:interactive ? pointer : { x:0, y:0 }, interaction:Math.max(0, 1 - (time - interactionAt) / 1.2), size };
}
function paint() {
  const dpr = Math.min(devicePixelRatio || 1, 2);
  for (const item of canvases) {
    if (!item.visible && testClock === null) continue;
    const size = item.getSize();
    if (!size) continue;
    const px = Math.round(size * dpr);
    if (item.element.width !== px || item.element.height !== px) {
      item.element.width = px;
      item.element.height = px;
    }
    const ctx = item.context;
    ctx.setTransform(1,0,0,1,0,0);
    ctx.clearRect(0,0,px,px);
    ctx.setTransform(px / 100,0,0,px / 100,0,0);
    drawBot(ctx, item.getBot(), optionsFor(item.getBot(), size, item.interactive));
  }
  document.querySelectorAll('[data-step]').forEach(button => {
    let progress = state === button.dataset.step ? Math.min(age / DURATION[state],1) : 0;
    if (auto && STATES.indexOf(button.dataset.step) < SEQUENCE.indexOf(state)) progress = 1;
    button.querySelector('i').style.transform = `scaleX(${progress})`;
  });
  updateMessage();
  dirty = false;
}
function advance(delta) {
  time += delta;
  age += delta;
  if (state === 'completed' || weights.completed > 0) completionAge += delta;
  transitionAge += delta;
  const p = Math.min(transitionAge / .3,1);
  const smooth = p * p * (3 - 2 * p);
  for (const s of SEQUENCE) weights[s] = transitionStart[s] + ((s === state ? 1 : 0) - transitionStart[s]) * smooth;
  const chase = 1 - Math.exp(-delta * 8);
  pointer.x += (pointerTarget.x - pointer.x) * chase;
  pointer.y += (pointerTarget.y - pointer.y) * chase;
  if (auto && age >= DURATION[state]) setState(SEQUENCE[(SEQUENCE.indexOf(state) + 1) % SEQUENCE.length], true);
}
function frame(stamp) {
  animationFrameId = null;
  const delta = lastStamp === null ? 0 : Math.min((stamp - lastStamp) / 1000, .05);
  lastStamp = stamp;
  if (!document.hidden && testClock === null && !paused && !reduced()) advance(delta * speed);
  if (!document.hidden && (dirty || (testClock === null && !paused && !reduced()))) paint();
  if (dirty || (testClock === null && !paused && !reduced())) requestFrame();
}
$('.cast').addEventListener('click', event => {
  const button = event.target.closest('[data-bot]');
  if (button) selectBot(button.dataset.bot);
});
$('.state-picker').addEventListener('click', event => {
  const button = event.target.closest('[data-state]');
  if (button) setState(button.dataset.state);
});
$('.timeline').addEventListener('click', event => {
  const button = event.target.closest('[data-step]');
  if (button) setState(button.dataset.step);
});
$('#auto').addEventListener('click', () => { auto = true; paused = false; setState('connecting', true); syncOptions(); });
$('#pause').addEventListener('click', () => { paused = !paused; syncOptions(); });
$('#replay').addEventListener('click', () => { paused = false; setState(auto ? 'connecting' : state, auto); syncOptions(); });
$('#speed').addEventListener('change', event => { speed = Number(event.target.value); });
$('#cancel').addEventListener('click', () => { setState('idle'); });
$('#mode').addEventListener('click', () => { dark = !dark; document.body.dataset.mode = dark ? 'dark' : 'light'; $('#mode').textContent = dark ? '浅色' : '深色'; $('#mode').setAttribute('aria-pressed', String(dark)); invalidate(); });
$('#background').addEventListener('change', event => { background = event.target.value; document.body.dataset.background = background; invalidate(); });
$('#mono').addEventListener('change', event => { mono = event.target.checked; invalidate(); });
$('#reduce').addEventListener('change', event => { manualReduce = event.target.checked; syncOptions(); });
media.addEventListener('change', syncOptions);
$('#stage-touch').addEventListener('pointermove', event => {
  const rect = event.currentTarget.getBoundingClientRect();
  pointerTarget = { x:Math.max(-1,Math.min(1,(event.clientX - rect.left - rect.width / 2) / 110)), y:Math.max(-1,Math.min(1,(event.clientY - rect.top - rect.height / 2) / 110)) };
});
$('#stage-touch').addEventListener('pointerleave', () => { pointerTarget = { x:0,y:0 }; });
$('#stage-touch').addEventListener('click', () => { interactionAt = time; invalidate(); });
document.addEventListener('visibilitychange', () => {
  lastStamp = null;
  if (document.hidden && animationFrameId !== null) { cancelAnimationFrame(animationFrameId); animationFrameId = null; }
  if (!document.hidden) invalidate();
});
selectBot(selection);
syncOptions();
requestFrame();

// 本地可复现的动画帧接口，用于导出演示和检验部件的持续变化，不发送或保存数据。
window.botStudio = Object.freeze({
  catalog:BOT_CATALOG,
  select:selectBot,
  setState,
  snapshot:() => ({ selection,state,age,time,completionAge,weights:{...weights},auto,paused,reduced:reduced(),mono,dark:effectiveDark(),pointer:{...pointer} }),
  seek:(nextTime, nextState = state, nextAge = nextTime) => {
    testClock = nextTime; time = nextTime; age = nextAge; state = nextState;
    completionAge = state === 'completed' ? nextAge : 3;
    weights = Object.fromEntries(SEQUENCE.map(s => [s,s === state ? 1 : 0]));
    transitionStart = {...weights}; transitionAge = 1;
    syncState(); paint();
  },
  resume:() => { testClock = null; lastStamp = null; invalidate(); },
  pose:(id, overrides = {}) => samplePose(id, {...optionsFor(id,100),...overrides}),
  capture:(at, captureState, stateAge, width = 1000, height = 430, blend = null) => {
    const canvas = document.createElement('canvas'); canvas.width = width; canvas.height = height;
    const ctx = canvas.getContext('2d');
    ctx.fillStyle = effectiveDark() ? '#162129' : '#f5f7f8'; ctx.fillRect(0,0,width,height);
    const ink = effectiveDark() ? '#edf2f5' : '#25343c';
    const muted = effectiveDark() ? '#b2c0c8' : '#53626b';
    const scale = width / 1000;
    ctx.scale(scale,scale);
    ctx.fillStyle=ink;ctx.font='600 27px "Microsoft YaHei UI",sans-serif';ctx.fillText('Echo · 四个小伙伴的连续动作',30,43);
    ctx.fillStyle=muted;ctx.font='14px "Microsoft YaHei UI",sans-serif';ctx.fillText(`${LABELS[captureState]}  /  原创动画候选`,31,72);
    const w = blend?.weights || Object.fromEntries(SEQUENCE.map(s => [s,s === captureState ? 1 : 0]));
    BOT_CATALOG.forEach((bot,index) => {
      ctx.save();ctx.translate(28+index*245,106);ctx.scale(2.05,2.05);
      drawBot(ctx,bot.id,{time:at,state:captureState,stateAge,completionAge:blend?.completionAge ?? (captureState === 'completed' ? stateAge : 3),weights:w,reducedMotion:false,mono,dark:effectiveDark(),pointer:{x:0,y:0},interaction:0,size:205});ctx.restore();
      ctx.fillStyle=ink;ctx.font='600 21px "Microsoft YaHei UI",sans-serif';ctx.textAlign='center';ctx.fillText(bot.name,130+index*245,343);
      ctx.fillStyle=muted;ctx.font='13px "Microsoft YaHei UI",sans-serif';ctx.fillText(bot.subtitle,130+index*245,370);
    });
    ctx.textAlign='left';ctx.fillStyle=muted;ctx.font='11px "Microsoft YaHei UI",sans-serif';ctx.fillText('连接 → 思考 → 输出 → 完成    ·    每个角色有独立动作节奏    ·    尚未应用到 App',31,408);
    return canvas.toDataURL('image/png');
  }
});
