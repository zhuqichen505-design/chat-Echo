'use strict';
// All conversation and profile content in this preview is synthetic.
const themes = {
  mono: {
    name: '墨白', english: 'MONO', kind: '无彩 · 轻排版', title: '把注意力还给文字。',
    description: '纯白与墨色建立层次，没有彩色干扰。短对话清楚，长文章也舒展。', note: '黑白之间，让文字呼吸。', radius: '4px',
    light: {bg:'#ffffff',surface:'#f5f5f5',ink:'#202020',muted:'#606060',accent:'#242424',on:'#ffffff',line:'#d9d9d9',tint:'#e9e9e9'},
    dark: {bg:'#121212',surface:'#1d1d1d',ink:'#eeeeee',muted:'#b0b0b0',accent:'#e0e0e0',on:'#181818',line:'#393939',tint:'#303030'}
  },
  obsidian: {
    name: '玄曜', english: 'OBSIDIAN', kind: '石墨 · 琥珀', title: '夜色里，一点温度。',
    description: '石墨黑与琥珀黄搭配。正文保持中性，选中状态与操作入口用明亮的小面积色彩标记。', note: '石墨底色，琥珀标记。', radius: '9px',
    light: {bg:'#f4f4f3',surface:'#e8e8e6',ink:'#23231f',muted:'#626259',accent:'#825400',on:'#ffffff',line:'#ccccc5',tint:'#efe0bf'},
    dark: {bg:'#151513',surface:'#20201c',ink:'#f0efe9',muted:'#b7b5aa',accent:'#e4bb66',on:'#25200f',line:'#3d3c34',tint:'#3a311e'}
  },
  terracotta: {
    name: '陶朱', english: 'VERMILION', kind: '赤陶 · 素红', title: '像落款，也像开篇。',
    description: '朱红与矿物灰构成安静的暖色。浅色像一页干净的手稿，深色转为沉稳的赤褐，适合有温度的叙事。', note: '朱色落款，灰白留白。', radius: '3px',
    light: {bg:'#f6f3f3',surface:'#eee6e5',ink:'#382727',muted:'#765b59',accent:'#a33b31',on:'#ffffff',line:'#d9c8c6',tint:'#f0d7d2'},
    dark: {bg:'#2b1718',surface:'#382021',ink:'#f5e5df',muted:'#cba7a0',accent:'#f0a291',on:'#351816',line:'#65403d',tint:'#542e2b'}
  },
  fern: {
    name: '蕨野', english: 'FERN', kind: '苔绿 · 青柠', title: '让想法慢慢长出来。',
    description: '清浅的植物绿配深橄榄字色，深色则像林荫。没有夜航的金色，重点以更清新的青柠绿提示。', note: '叶片的轻盈，林荫的安静。', radius: '13px',
    light: {bg:'#f2f5ef',surface:'#e5ecdf',ink:'#273322',muted:'#596b4e',accent:'#526d26',on:'#ffffff',line:'#c7d2bd',tint:'#dbe8c8'},
    dark: {bg:'#192016',surface:'#252e20',ink:'#e7eddf',muted:'#aab99a',accent:'#b7d77f',on:'#202b14',line:'#424e37',tint:'#38482a'}
  },
  berry: {
    name: '莓紫', english: 'MULBERRY', kind: '雾粉 · 桑紫', title: '给日常一点柔软。',
    description: '雾粉背景、桑紫文字与柔和的弧度。保留清楚的分隔和足够的对比，轻盈但不甜腻。', note: '柔和颜色，清楚层次。', radius: '15px',
    light: {bg:'#faf3f8',surface:'#f0e3ee',ink:'#3a2940',muted:'#775c7c',accent:'#8b3975',on:'#ffffff',line:'#dec9db',tint:'#eed7e7'},
    dark: {bg:'#271b2a',surface:'#35243a',ink:'#f2e5f1',muted:'#c2a5c4',accent:'#e6acd2',on:'#361d32',line:'#5c405f',tint:'#51304a'}
  },
  cobalt: {
    name: '钴蓝', english: 'COBALT', kind: '瓷白 · 深钴蓝', title: '清醒，直接，有分寸。',
    description: '更鲜明的钴蓝和干净的冷白，区别于晴空的淡蓝。紧实的字重与利落边角，让频繁操作有明确落点。', note: '钴蓝作标记，冷白作底。', radius: '5px',
    light: {bg:'#f3f5fc',surface:'#e6ebf8',ink:'#1e2c4c',muted:'#526386',accent:'#284dc2',on:'#ffffff',line:'#c5cfe5',tint:'#dbe3fb'},
    dark: {bg:'#131e38',surface:'#1d2b48',ink:'#e7edff',muted:'#a9badd',accent:'#a8bfff',on:'#13285c',line:'#3b4d72',tint:'#2a3c66'}
  }
};
const state = {theme:'mono',mode:'light',background:'theme',mark:'rings',activity:'replying',avatar:false,thinking:true};
const markNames = {rings:'同心圆',orbit:'轨道',prism:'棱镜',wave:'声纹'};
const activityNames = {thinking:'正在思考',replying:'正在回复',done:'已完成'};
const themeList = document.getElementById('theme-list');
for (const [key,theme] of Object.entries(themes)) {
  const button = document.createElement('button');
  button.className = 'theme-choice';
  button.dataset.themeChoice = key;
  button.setAttribute('aria-pressed',String(key===state.theme));
  button.innerHTML = `<span class="theme-colors" aria-hidden="true"><i style="background:${theme.light.accent}"></i><i style="background:${theme.light.tint}"></i><i style="background:${theme.light.bg}"></i></span><span><span class="theme-name">${theme.name}</span><span class="theme-kind">${theme.kind}</span></span><span class="theme-check" aria-hidden="true">✓</span>`;
  themeList.append(button);
}

function effectiveMode() { return state.background==='white'?'light':state.background==='black'?'dark':state.mode; }
function applyTheme() {
  const theme = themes[state.theme];
  const mode = effectiveMode();
  const colors = {...theme[mode]};
  if (state.background==='white') colors.bg='#ffffff';
  if (state.background==='black') colors.bg='#000000';
  for (const [key,value] of Object.entries(colors)) document.documentElement.style.setProperty(`--${key}`,value);
  document.documentElement.style.setProperty('--radius',theme.radius);
  document.documentElement.style.setProperty('--theme-bg',theme[state.mode].bg);
  document.documentElement.style.setProperty('--effective-mode',mode);
  document.body.dataset.theme = state.theme;
  document.body.dataset.mode = state.mode;
  document.body.dataset.background = state.background;
  document.getElementById('theme-subtitle').textContent = `${theme.english} / ${theme.name}`;
  document.getElementById('theme-title').textContent = theme.title;
  document.getElementById('theme-description').textContent = theme.description;
  document.getElementById('setting-theme-name').textContent = theme.name;
  document.getElementById('setting-theme-description').textContent = theme.note;
  document.getElementById('palette-note').textContent = theme.note;
  document.getElementById('palette-strip').innerHTML = ['bg','surface','tint','accent','ink'].map(key=>`<span style="background:${colors[key]}" title="${key} ${colors[key]}" aria-label="${key} ${colors[key]}"></span>`).join('');
  document.querySelectorAll('[data-theme-choice]').forEach(button=>button.setAttribute('aria-pressed',String(button.dataset.themeChoice===state.theme)));
  document.querySelectorAll('[data-mode]').forEach(button=>{if(button.tagName==='BUTTON')button.setAttribute('aria-pressed',String(button.dataset.mode===state.mode));});
  document.querySelectorAll('[data-background]').forEach(button=>{if(button.tagName==='BUTTON')button.setAttribute('aria-pressed',String(button.dataset.background===state.background));});
  const hash = new URLSearchParams({theme:state.theme,mode:state.mode,bg:state.background});
  history.replaceState(null,'',`#${hash}`);
}
function applyMotion() {
  document.body.dataset.state = state.activity;
  document.querySelectorAll('.model-mark').forEach(mark=>{mark.dataset.mark=state.mark;mark.dataset.state=state.activity;});
  const mainMark = document.querySelector('.model-header .model-mark');
  mainMark.classList.toggle('avatar',state.avatar);
  mainMark.setAttribute('aria-label',`模型${activityNames[state.activity]}`);
  document.querySelector('.model-state').textContent = activityNames[state.activity];
  document.body.classList.toggle('hide-thinking',!state.thinking);
  document.querySelectorAll('[data-state]').forEach(button=>{if(button.tagName==='BUTTON')button.setAttribute('aria-pressed',String(button.dataset.state===state.activity));});
  document.getElementById('setting-mark-name').textContent = `${markNames[state.mark]} · 生成时动态`;
  document.getElementById('mark-select').value = state.mark;
}
function setScene(scene) {
  document.querySelector('.screens').dataset.scene = scene;
  document.querySelectorAll('[data-scene]').forEach(button=>{if(button.tagName==='BUTTON')button.setAttribute('aria-pressed',String(button.dataset.scene===scene));});
  if (window.matchMedia('(min-width:1001px)').matches && !window.matchMedia('(prefers-reduced-motion:reduce)').matches && !document.body.classList.contains('reduced-motion')) {
    const target = document.querySelector(`[data-page="${scene}"] .phone`);
    target.animate([{outline:'2px solid transparent'},{outline:'2px solid var(--accent)'},{outline:'2px solid transparent'}],{duration:700});
  }
}
let toastTimer;
function toast(message) {
  const element = document.querySelector('.toast');
  element.textContent=message;
  element.classList.add('visible');
  clearTimeout(toastTimer);
  toastTimer=setTimeout(()=>element.classList.remove('visible'),2600);
}
document.querySelectorAll('[data-theme-choice]').forEach(button=>button.addEventListener('click',()=>{state.theme=button.dataset.themeChoice;applyTheme();}));
document.querySelectorAll('button[data-mode]').forEach(button=>button.addEventListener('click',()=>{state.mode=button.dataset.mode;applyTheme();}));
document.querySelectorAll('button[data-background]').forEach(button=>button.addEventListener('click',()=>{state.background=button.dataset.background;applyTheme();}));
document.querySelectorAll('button[data-state]').forEach(button=>button.addEventListener('click',()=>{state.activity=button.dataset.state;applyMotion();}));
document.querySelectorAll('button[data-scene]').forEach(button=>button.addEventListener('click',()=>setScene(button.dataset.scene)));
document.querySelectorAll('[data-open-scene]').forEach(button=>button.addEventListener('click',()=>setScene(button.dataset.openScene)));
document.querySelectorAll('[data-demo-note]').forEach(button=>button.addEventListener('click',()=>toast(button.dataset.demoNote)));
document.querySelectorAll('[data-replay]').forEach(button=>button.addEventListener('click',()=>{state.activity='replying';applyMotion();toast('已切换为回复动态；预览输入不会发送到网络。');}));
document.getElementById('mark-select').addEventListener('change',event=>{state.mark=event.target.value;applyMotion();});
document.getElementById('reduce-motion').addEventListener('change',event=>document.body.classList.toggle('reduced-motion',event.target.checked));
document.getElementById('avatar-toggle').addEventListener('click',event=>{state.avatar=!state.avatar;event.currentTarget.setAttribute('aria-checked',String(state.avatar));event.currentTarget.classList.toggle('on',state.avatar);applyMotion();});
document.getElementById('thinking-toggle').addEventListener('click',event=>{state.thinking=!state.thinking;event.currentTarget.setAttribute('aria-checked',String(state.thinking));event.currentTarget.classList.toggle('on',state.thinking);applyMotion();});
document.querySelector('.font-range').addEventListener('input',event=>{document.documentElement.style.setProperty('--reading-size',`${Number(event.target.value)}px`);document.querySelector('.compact .setting-value').textContent=event.target.value;});
const hash = new URLSearchParams(location.hash.slice(1));
if (Object.hasOwn(themes,hash.get('theme'))) state.theme=hash.get('theme');
if (['light','dark'].includes(hash.get('mode'))) state.mode=hash.get('mode');
if (['theme','white','black'].includes(hash.get('bg'))) state.background=hash.get('bg');
applyTheme();
applyMotion();
