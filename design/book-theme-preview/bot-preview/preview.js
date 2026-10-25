(() => {
  'use strict';
  const motion=window.EchoBotMotion;
  const shapes=[['round','圆圆'],['square','方方'],['flat','扁扁']];
  const labels={connecting:'连接：视线先看向信号，身体缓缓跟随。',thinking:'思考：抬眼思索，右上气泡慢慢形成。',replying:'回复：正文输出，右上说话泡慢慢点亮。',completed:'完成：只演一次收势，随后保持静止。',paused:'主动暂停：身体收回，双竖条持续保留。',failed:'连接 / 回复出错：圆底叹号持续保留，可恢复到回复。',idle:'待机：仅最新成功回复的身份可以进入 Z 待机。'};
  const stage=document.querySelector('.stage');
  const chip=(shape,size,state='replying')=>'<span class="bot-svg state-'+state+'" data-shape="'+shape+'" style="width:'+size+'px;height:'+size+'px" role="img" aria-label="'+shape+' Bot">'+motion.svg(motion.sampleBot(shape,state,0))+'</span>';
  stage.innerHTML=shapes.map(([shape,name])=>'<article class="bot-card"><div class="card-heading"><h2>'+name+'</h2><span>同一动作参数 · 不同体态</span></div><div class="large-bot">'+chip(shape,144)+'</div><div class="actual-sizes"><div class="size-example">'+chip(shape,38)+'<span>聊天 38px</span></div><div class="size-example">'+chip(shape,64)+'<span>64px</span></div></div></article>').join('');
  document.querySelectorAll('[data-terminal]').forEach(element=>{const state=element.dataset.terminal;element.innerHTML=shapes.map(([shape])=>chip(shape,58,state)).join('');element.querySelectorAll('.bot-svg').forEach(bot=>bot.innerHTML=motion.svg(motion.sampleBot(bot.dataset.shape,state,0)));});
  let state='replying',elapsed=0,transition=1,frozen=false,automatic=false,sequenceIndex=0,sequenceTime=0,last=0;
  let reduced=matchMedia('(prefers-reduced-motion: reduce)').matches;
  document.querySelector('#reduce').checked=reduced;
  const poses=Object.fromEntries(shapes.map(([shape])=>[shape,motion.sampleBot(shape,state,0)]));
  let from=structuredClone(poses);
  const sequence=[['connecting',4],['thinking',4],['replying',4],['paused',3],['replying',3],['failed',3],['replying',3],['completed',2],['idle',4]];
  function sync(){document.querySelectorAll('[data-state]').forEach(button=>button.setAttribute('aria-pressed',String(button.dataset.state===state)));document.querySelector('#status').textContent=labels[state];document.querySelector('#freeze').setAttribute('aria-pressed',String(frozen));document.querySelector('#freeze').textContent=frozen?'继续播放':'冻结画面';document.querySelector('#play').classList.toggle('is-playing',automatic);document.querySelector('#play').textContent=automatic?'完整过程播放中':'播放完整过程';}
  function paint(){stage.querySelectorAll('.bot-svg').forEach(element=>{element.className='bot-svg state-'+state;element.innerHTML=motion.svg(poses[element.dataset.shape]);});}
  function select(next,keepSequence=false){state=next;elapsed=0;transition=0;from=structuredClone(poses);if(!keepSequence)automatic=false;if(reduced){for(const [shape]of shapes)poses[shape]=motion.sampleBot(shape,state,0,true,state==='idle');transition=1;}sync();paint();}
  function tick(now){
    const delta=last?Math.min((now-last)/1000,.05):0;last=now;
    if(!document.hidden&&!frozen){
      if(automatic){sequenceTime+=delta;if(sequenceTime>=sequence[sequenceIndex][1]){sequenceTime=0;sequenceIndex=(sequenceIndex+1)%sequence.length;select(sequence[sequenceIndex][0],true);}}
      elapsed+=delta;transition=Math.min(1,transition+delta/.26);
      for(const [shape]of shapes){const target=motion.sampleBot(shape,state,elapsed,reduced,state==='idle');poses[shape]=reduced?target:motion.blendPose(from[shape],target,motion.smooth(transition));}
      paint();
    }
    requestAnimationFrame(tick);
  }
  document.querySelectorAll('[data-state]').forEach(button=>button.addEventListener('click',()=>select(button.dataset.state)));
  document.querySelector('#replay').addEventListener('click',()=>select(state));
  document.querySelector('#play').addEventListener('click',()=>{automatic=!automatic;if(automatic){sequenceIndex=0;sequenceTime=0;frozen=false;select(sequence[0][0],true);}sync();});
  document.querySelector('#freeze').addEventListener('click',()=>{frozen=!frozen;sync();});
  document.querySelector('#reduce').addEventListener('change',event=>{reduced=event.target.checked;select(state);});
  document.addEventListener('visibilitychange',()=>last=0);
  window.BotPreview={
    setState:select,
    renderAt(next,time,previous='replying',previousTime=.8){
      state=next;frozen=true;automatic=false;
      for(const [shape]of shapes){const target=motion.sampleBot(shape,next,time,false,next==='idle');const source=motion.sampleBot(shape,previous,previousTime,false,previous==='idle');poses[shape]=motion.blendPose(source,target,motion.smooth(Math.min(1,time/.26)));}
      sync();paint();
    },
    get state(){return state;},
    sample:motion.sampleBot
  };
  sync();requestAnimationFrame(tick);
})();
