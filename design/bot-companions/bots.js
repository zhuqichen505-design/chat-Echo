'use strict';

const styles = [
  { id:'bot_round', name:'圆豆', shape:'圆形', note:'小天线，圆脑袋' },
  { id:'bot_square', name:'方糖', shape:'圆角矩形', note:'短耳朵，软方角' },
  { id:'bot_capsule', name:'团子', shape:'横向胶囊', note:'扁一点，更悠闲' },
  { id:'bot_pixel', name:'小方', shape:'直角矩形', note:'小横线，利落可爱' },
];
const states = {
  connecting:{label:'正在连接',detail:'左右眼交替亮起，等待连接；没有快速旋转或大幅跳动。',text:'正在接通模型，等它准备好。'},
  thinking:{label:'正在思考',detail:'抬眼想一想，脑袋轻轻偏转。减弱动态时保留抬眼表情。',text:'正在想象雨后的街道、路灯和开场的第一句话。'},
  streaming:{label:'正在回复',detail:'小嘴开合，短耳线轻微摆动。只有标识运动，不推动正文和页面布局。',text:'雨停了。你推开门，街边的树叶还在往下滴水。路灯把细碎的水珠照得很亮，像有人在夜里悄悄撒了一把星星。'},
  completed:{label:'回复完成',detail:'微笑，轻轻点头一次，然后静止。历史已完成消息不会反复重播。',text:'雨停了。你推开门，街边的树叶还在往下滴水。路灯把细碎的水珠照得很亮，像有人在夜里悄悄撒了一把星星。'},
};
let current={style:'bot_round',state:'connecting'};
let timers=[];

function bot(style, state, celebrate=false) {
  const shape = style==='bot_round' ? '<circle cx="13" cy="14" r="9"/><path class="antenna-tip" d="M13 2.5V5"/><circle class="antenna-tip" cx="13" cy="2.5" r="1.1" fill="currentColor" stroke="none"/><path class="ear-l" d="M2 13h2"/><path class="ear-r" d="M22 13h2"/>'
    : style==='bot_square' ? '<rect x="4" y="6" width="18" height="16" rx="4"/><path class="antenna-tip" d="M13 3.8V6"/><circle class="antenna-tip" cx="13" cy="3" r="1" fill="currentColor" stroke="none"/><path class="ear-l" d="M2 13h2"/><path class="ear-r" d="M22 13h2"/>'
    : style==='bot_capsule' ? '<rect x="2" y="7" width="22" height="15" rx="7.5"/><path class="ear-l antenna-tip" d="M4 5.5V8"/><path class="ear-r antenna-tip" d="M22 5.5V8"/>'
    : '<rect x="4" y="7" width="18" height="15" rx="1.4"/><path class="antenna-tip" d="M13 3V7M11 3h4"/><path class="ear-l" d="M2 12v3"/><path class="ear-r" d="M24 12v3"/>';
  const y=state==='thinking'?12.2:13.2;
  const eyes=state==='completed' ? '<path d="M8.1 13.4q1.2-2 2.4 0M15.5 13.4q1.2-2 2.4 0"/>'
    : style==='bot_pixel'?`<rect class="eye-l" x="8.2" y="${y-1.1}" width="2.2" height="2.2" rx=".35" fill="currentColor" stroke="none"/><rect class="eye-r" x="15.6" y="${y-1.1}" width="2.2" height="2.2" rx=".35" fill="currentColor" stroke="none"/>`
    : `<circle class="eye-l" cx="9.3" cy="${y}" r="1.15" fill="currentColor" stroke="none"/><circle class="eye-r" cx="16.7" cy="${y}" r="1.15" fill="currentColor" stroke="none"/>`;
  const mouth=state==='completed'?'<path class="mouth" d="M10.5 17q2.5 3 5 0"/>'
    :state==='streaming'?'<ellipse class="mouth" cx="13" cy="17.3" rx="1.8" ry="1.8"/>'
    :state==='thinking'?'<path class="mouth" d="M12 17.5l3-.5"/>'
    :'<path class="mouth" d="M10.8 17.3h4.4"/>';
  const name=styles.find(x=>x.id===style)?.name||'圆豆';
  return `<svg class="bot-svg ${celebrate?'celebrate':''}" data-style="${style}" data-state="${state}" viewBox="0 0 26 26" role="img" aria-label="${name} · ${states[state]?.label||'静止'}" fill="none" stroke="currentColor" stroke-width="1.45" stroke-linecap="round" stroke-linejoin="round"><g class="bot-head">${shape}${eyes}${mouth}</g></svg>`;
}
function renderPicker(){
  document.querySelector('.shape-picker').innerHTML=styles.map(s=>`<button class="shape-choice" data-style="${s.id}" aria-pressed="${current.style===s.id}">${bot(s.id,'completed')}<span><strong>${s.name}</strong><small>${s.note}</small></span></button>`).join('');
}
function renderMatrix(){
  document.querySelector('.state-matrix').innerHTML=`<div class="matrix-row matrix-heading"><div>造型</div><div>连接</div><div>思考</div><div>输出</div><div>完成</div></div>`+styles.map(s=>`<div class="matrix-row"><div class="shape-name">${s.name}<small>${s.shape}</small></div>${Object.keys(states).map(state=>`<div>${bot(s.id,state)}</div>`).join('')}</div>`).join('');
}
function stopSequence(){timers.forEach(clearTimeout);timers=[];document.querySelector('#play').textContent='演示完整过程 ↗';}
function renderLive(celebrate=false){
  const s=states[current.state];
  document.querySelector('#live-bot').innerHTML=bot(current.style,current.state,celebrate);
  document.querySelector('#large-bot').innerHTML=bot(current.style,current.state,celebrate);
  document.querySelector('#live-state').textContent=s.label;
  document.querySelector('#state-detail').textContent=s.detail;
  const text=document.querySelector('#sample-text');text.textContent=s.text;text.classList.toggle('muted',['connecting','thinking'].includes(current.state));
  document.querySelectorAll('.state-picker button').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.state===current.state)));
  document.querySelectorAll('.shape-choice').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.style===current.style)));
}
document.querySelector('.shape-picker').addEventListener('click',e=>{const button=e.target.closest('[data-style]');if(!button)return;stopSequence();current.style=button.dataset.style;renderLive(false);});
document.querySelector('.state-picker').addEventListener('click',e=>{const button=e.target.closest('[data-state]');if(!button)return;stopSequence();current.state=button.dataset.state;renderLive(current.state==='completed');});
document.querySelector('#replay').addEventListener('click',()=>{stopSequence();renderLive(current.state==='completed');});
document.querySelector('#play').addEventListener('click',()=>{stopSequence();current.state='connecting';renderLive();document.querySelector('#play').textContent='演示中…';['thinking','streaming','completed'].forEach((state,i)=>timers.push(setTimeout(()=>{current.state=state;renderLive(state==='completed');if(state==='completed')stopSequence();},(i+1)*2200)));});
document.querySelector('#mode').addEventListener('click',e=>{const dark=document.body.dataset.mode!=='dark';document.body.dataset.mode=dark?'dark':'light';e.currentTarget.setAttribute('aria-pressed',String(dark));e.currentTarget.textContent=dark?'切换浅色':'切换深色';});
document.querySelector('#background').addEventListener('change',e=>{document.body.dataset.background=e.target.value;});
document.querySelector('#reduce').addEventListener('change',e=>{document.body.dataset.motion=e.target.checked?'reduced':'system';});
document.querySelector('#actual-size').addEventListener('change',e=>{document.querySelector('.state-matrix').classList.toggle('actual',e.target.checked);});
renderPicker();renderMatrix();renderLive();
