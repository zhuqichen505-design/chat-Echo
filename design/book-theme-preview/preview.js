(() => {
  'use strict';
  const themes = [
    {id:'deckle',name:'毛边文库',english:'THE PAPERBACK',description:'微黄书纸与砖红书签。页边微微叠起，像一本读到一半的随身文库。',notes:'适合长篇阅读 · 暖白纸纤维 / 砖红油墨 / 细页码',colors:['#f4ecda','#963e2d','#3f342a','#cbbda4']},
    {id:'thread',name:'素纸线装',english:'THE THREADBOUND',description:'灰白宣纸、松绿封面与线装留痕。留出宽页边，让每一行字静下来。',notes:'适合清静写作 · 竖排章节名 / 装订线 / 松绿墨色',colors:['#f1f1e9','#385d50','#263e37','#b8c5ba']},
    {id:'field',name:'牛皮手札',english:'THE FIELD NOTES',description:'带横格的牛皮手记。纸胶带压住便笺，想法和故事都可以随手写下。',notes:'适合日常记录 · 轻横格 / 胶带便笺 / 深绿铅笔',colors:['#e4c9a0','#37594e','#413525','#b39a75']},
    {id:'cloth',name:'藏书精装',english:'THE CLOTHBOUND',description:'梅棕布面包住象牙书页。细金分隔和首字下沉，为故事留一点仪式感。',notes:'适合沉浸创作 · 布面书脊 / 象牙内页 / 压金细线',colors:['#50363e','#f1e8d4','#663f46','#bca273']},
  ];
  const state = {scene:'chat',focus:'deckle',single:false};
  const gallery = document.querySelector('.gallery');
  const escapeText = value => value.replace(/[&<>"']/g, character => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[character]));
  const header = title => `<div class="statusbar"><span>9:41</span><span aria-label="信号、电量">▂▄▆ &nbsp; ▰</span></div><header class="book-header"><button data-local="home" aria-label="返回会话目录">‹</button><div class="book-title"><strong>${title}</strong><small>Echo · 纸上回声</small></div><span class="bookmark" aria-hidden="true"></span></header>`;
  const nav = active => `<nav class="book-nav" aria-label="样稿页面"><button data-local="home" aria-pressed="${active==='home'}">会话目录</button><button data-local="chat" aria-pressed="${active==='chat'}">故事正文</button><button data-local="settings" aria-pressed="${active==='settings'}">阅读设定</button></nav>`;
  const chat = () => `${header('山间来信')}<main class="page-scroll"><div class="chapter-label">第三章 &nbsp; / &nbsp; 雨停之前</div><p class="user-prompt">接着写吧。从她在书里发现那张车票开始，慢一点。</p><div class="speaker"><span class="bot" aria-hidden="true"><i>✓</i></span><span>Echo · 已完成</span></div><article class="prose"><h3>夹在书页里的远方</h3><p>雨在傍晚停了。她推开窗，潮湿的风翻过桌上的书，停在一张褪色的车票旁。</p><p>那是一段很短的旅程。出发和抵达的地名之间，只有两个小时。可她总觉得，有些路要走上一生。</p><p>车票背面有一行铅笔字，写得很轻，像是怕惊动了什么。</p><p>“等山里的桂花开了，我们就在那里见面。”</p><p>她把书翻回扉页，才看见那枚小小的藏书印。窗外最后一滴雨，落进了花盆。</p><span class="end-mark">❧</span></article><div class="message-actions"><button data-action="copy">复制正文</button><button data-local="settings">阅读设定</button></div><div class="local-feedback" role="status"></div><div class="sent-messages" aria-live="polite"></div></main><div class="folio">— &nbsp; 27 &nbsp; —</div><form class="composer"><button type="button" data-local="home" aria-label="打开会话目录">≡</button><input maxlength="500" aria-label="写下你的下一句" placeholder="写下你的下一句……"><button type="submit" aria-label="预览发送">↑</button></form>`;
  const home = () => `${header('我的书页')}<main class="page-scroll"><h2 class="home-mast">每一页，<br>都有回音。</h2><p class="home-sub">午后适合翻开一个故事。</p><button class="continue-story" data-local="chat"><small>接着读 · 上次停在第三章</small><strong>山间来信</strong><p>一张车票，从书里落了下来。</p><em>翻到这一页 &nbsp; ↗</em></button><div class="list-heading"><span>最近的会话</span><span>三篇</span></div>${[['周末的散步路线','想找一条经过旧书店的路。'],['一段散文的修改','把句子再写得轻一点。'],['留给秋天的清单','读书、做饭，以及慢慢生活。']].map((row,index)=>`<button class="contents-row" data-local="chat"><span>${String(index+1).padStart(2,'0')}</span><div><strong>${row[0]}</strong><small>${row[1]}</small></div><b>↗</b></button>`).join('')}<p class="local-note">这是一份主题样稿，目录使用合成内容。</p></main>${nav('home')}`;
  const settings = () => `${header('阅读设定')}<main class="page-scroll"><h2 class="setting-title">照顾阅读的眼睛。</h2><p class="setting-explain">安静的材质，清楚的文字。<br>所有调整仅在当前预览中生效。</p><label class="setting-row"><span>纸张纹理<small>保留轻纤维与油墨感</small></span><input type="checkbox" data-paper checked aria-label="样稿纸张纹理"></label><label class="setting-row"><span>正文字体<small>衬线字体更接近纸本</small></span><select data-font aria-label="样稿正文字体"><option value="serif">宋体 / 衬线</option><option value="sans">黑体 / 无衬线</option></select></label><label class="setting-row"><span>阅读字号<small>也可使用上方滑杆调整</small></span><select data-size aria-label="样稿正文字号"><option value="14">14</option><option value="16" selected>16</option><option value="18">18</option><option value="20">20</option></select></label><div class="setting-row"><span>Bot 状态示意<small>形状与文字一起区分状态</small></span></div><div class="state-example"><span><b>Ⅱ</b>主动暂停</span><span class="error"><b>!</b>连接 / 回复错误</span></div><p class="local-note">纸张颜色保持固定，不叠加毛玻璃。输入区与导航留在底部，正文可单独滚动。此处是视觉方向预览。</p></main>${nav('settings')}`;
  const renderScene = scene => ({home,chat,settings}[scene]());
  function render() {
    gallery.innerHTML = themes.map(theme => `<article class="study ${state.focus===theme.id?'is-focused':''}" data-theme="${theme.id}"><header class="study-header"><h2>${theme.name}</h2><button data-focus="${theme.id}" aria-label="单独查看${theme.name}">翻开这本 ↗</button></header><p class="study-description">${theme.description}</p><div class="book ${theme.id}" aria-label="${theme.name}主题样稿">${renderScene(state.scene)}</div><div class="swatches" aria-label="主题色板">${theme.colors.map(color=>`<i style="background:${color}"></i>`).join('')}<span>${theme.english}</span></div><p class="study-notes">${theme.notes}</p></article>`).join('');
    sync();
  }
  function sync() {
    gallery.dataset.focus = state.focus;
    gallery.classList.toggle('focus-mode',state.single);
    document.querySelectorAll('[data-theme]').forEach(element => {if(element.tagName==='BUTTON')element.setAttribute('aria-pressed',String(element.dataset.theme===state.focus));else element.classList.toggle('is-focused',element.dataset.theme===state.focus);});
    document.querySelectorAll('[data-scene]').forEach(button=>button.setAttribute('aria-pressed',String(button.dataset.scene===state.scene)));
    document.querySelector('#compare').setAttribute('aria-pressed',String(state.single));
    document.querySelector('#compare').textContent=state.single?'四本并排':'单独查看';
    document.querySelectorAll('[data-paper]').forEach(input=>input.checked=document.querySelector('#texture').checked);
    document.querySelectorAll('[data-size]').forEach(select=>select.value=document.querySelector('#font-size').value);
  }
  function setSize(size) {document.documentElement.style.setProperty('--body-size',`${size}px`);document.querySelector('#font-size').value=size;document.querySelector('#font-value').value=size;sync();}
  document.addEventListener('click',async event=>{
    const button=event.target.closest('button');if(!button)return;
    if(button.dataset.scene){state.scene=button.dataset.scene;render();}
    if(button.dataset.theme){state.focus=button.dataset.theme;sync();}
    if(button.dataset.focus){state.focus=button.dataset.focus;state.single=true;sync();document.querySelector('.desk').scrollIntoView({block:'start',behavior:'instant'});}
    if(button.id==='compare'){state.single=!state.single;sync();}
    if(button.dataset.local){const book=button.closest('.book');book.innerHTML=renderScene(button.dataset.local);sync();}
    if(button.dataset.action==='copy'){
      const book=button.closest('.book');const prose=book.querySelector('.prose');const feedback=book.querySelector('.local-feedback');
      try{await navigator.clipboard.writeText(prose.innerText);feedback.textContent='正文已复制';}
      catch{const range=document.createRange();range.selectNodeContents(prose);window.getSelection().removeAllRanges();window.getSelection().addRange(range);feedback.textContent='正文已选中，可手动复制';}
    }
  });
  document.querySelector('#texture').addEventListener('change',event=>{document.body.classList.toggle('no-texture',!event.target.checked);sync();});
  document.querySelector('#font-size').addEventListener('input',event=>setSize(event.target.value));
  document.addEventListener('change',event=>{
    if(event.target.matches('[data-paper]')){document.querySelector('#texture').checked=event.target.checked;document.body.classList.toggle('no-texture',!event.target.checked);sync();}
    if(event.target.matches('[data-size]'))setSize(event.target.value);
    if(event.target.matches('[data-font]'))event.target.closest('.book').style.setProperty('--serif',event.target.value==='sans'?'var(--sans)':"'Noto Serif CJK SC','Source Han Serif SC','SimSun',serif");
  });
  document.addEventListener('submit',event=>{
    if(!event.target.matches('.composer'))return;event.preventDefault();const input=event.target.querySelector('input');const value=input.value.trim();if(!value)return;
    const book=event.target.closest('.book');book.querySelector('.sent-messages').insertAdjacentHTML('beforeend',`<p class="local-message">${escapeText(value)}</p>`);book.querySelector('.local-feedback').textContent='已加入当前样稿 · 未发送到接口';input.value='';const scroll=book.querySelector('.page-scroll');scroll.scrollTop=scroll.scrollHeight;
  });
  render();
})();
