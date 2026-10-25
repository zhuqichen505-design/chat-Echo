(() => {
  'use strict';
  const themes = [
    {id:'deckle', name:'毛边文库', english:'PAPERBACK', description:'未经裁齐的页边、洇开的砖红印记，消息像叠放的薄书纸。', notes:'毛边与叠页 · 铅印书眉 · 砖红印记', colors:['#f6efdf','#91402d','#3e342a','#eadfca']},
    {id:'thread', name:'素纸线装', english:'THREADBOUND', description:'可见的线装针脚、方正的双线框，灰白宣纸承接松绿墨色。', notes:'装订针脚 · 双线边框 · 宣纸留白', colors:['#eef1e8','#345a45','#263e34','#d5e2d0']},
    {id:'field', name:'牛皮手札', english:'FIELD NOTES', description:'活页装订、铅笔页线与胶带便笺，把对话放进随身手札。', notes:'活页装订 · 铅笔横格 · 胶带便笺', colors:['#e6cea9','#345746','#403222','#d7bd94']},
    {id:'cloth', name:'藏书精装', english:'CLOTHBOUND', description:'梅棕布面包住象牙内页，压金细线与书脊阴影勾出层次。', notes:'布面封边 · 细金压框 · 象牙书纸', colors:['#f3ebd9','#553a45','#66404b','#e9dcc8']}
  ];
  const paths = {
    back:'<path d="m15 18-6-6 6-6M9 12h12"/>',
    arrow:'<path d="M4 12h16m-6-6 6 6-6 6"/>',
    send:'<path d="M12 20V4m-6 6 6-6 6 6"/>',
    stop:'<rect x="6" y="6" width="12" height="12" rx="2" fill="currentColor" stroke="none"/>',
    search:'<circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 4.5 4.5"/>',
    more:'<circle cx="5" cy="12" r="1.4" fill="currentColor"/><circle cx="12" cy="12" r="1.4" fill="currentColor"/><circle cx="19" cy="12" r="1.4" fill="currentColor"/>',
    plus:'<path d="M12 5v14M5 12h14"/>',
    tune:'<path d="M4 6h16M4 12h16M4 18h16"/><circle cx="8" cy="6" r="2" fill="var(--surface)"/><circle cx="16" cy="12" r="2" fill="var(--surface)"/><circle cx="10" cy="18" r="2" fill="var(--surface)"/>',
    history:'<path d="M3 11a9 9 0 1 1 2 7M3 5v6h6m3-5v6l4 2"/>',
    book:'<path d="M12 6C9 3 5 3 2 4v15c4-1 7 0 10 2 3-2 6-3 10-2V4c-3-1-7-1-10 2Zm0 0v15"/>',
    create:'<path d="M12 6C9 3 5 3 2 4v15c4-1 7 0 10 2 3-2 6-3 10-2V4c-3-1-7-1-10 2Zm0 0v15"/>',
    pin:'<path d="m8 3 8 0-1 6 3 3v2H6v-2l3-3-1-6Zm4 11v7"/>',
    chevron:'<path d="m6 9 6 6 6-6"/>',
    chat:'<path d="M4 3h16a1 1 0 0 1 1 1v13H8l-5 4V4a1 1 0 0 1 1-1Z"/><path d="M7 8h10M7 12h7"/>',
    stats:'<path d="M4 20V10h4v10m2 0V4h4v16m2 0V8h4v12"/>',
    settings:'<path d="m9 3-1 3-3 1-2 4 2 2 0 3 4 3 3-1 3 1 4-3v-3l2-2-2-4-3-1-1-3Z"/><circle cx="12" cy="11" r="3"/>',
    folder:'<path d="M3 5h7l2 3h9v12H3Z"/>',
    brain:'<path d="M12 5c-4-5-9 0-7 3-4 1-3 7 0 7-1 4 5 7 7 3 2 4 8 1 7-3 3 0 4-6 0-7 2-3-3-8-7-3Zm0 0v13M6 8l3 2m6 1 3-3M6 15l3-2"/>',
    copy:'<rect x="8" y="7" width="12" height="14" rx="2"/><path d="M16 7V3H4v14h4"/>',
    quote:'<path d="M4 6h6v7H7c0 3-2 4-3 5m10-12h6v7h-3c0 3-2 4-3 5"/>',
    refresh:'<path d="M20 10a8 8 0 1 0-2 8M20 4v6h-6"/>',
    branch:'<circle cx="6" cy="5" r="2"/><circle cx="18" cy="5" r="2"/><circle cx="6" cy="19" r="2"/><path d="M6 7v10m12-10c0 6-12 3-12 9"/>',
    context:'<circle cx="12" cy="12" r="8.5" opacity=".35"/><path d="M12 3.5a8.5 8.5 0 0 1 8 11.5" stroke-width="3"/>',
    image:'<rect x="3" y="4" width="18" height="16" rx="2"/><circle cx="8" cy="9" r="2"/><path d="m3 17 5-4 4 3 4-6 5 7"/>',
    file:'<path d="M5 2h9l5 5v15H5Zm9 0v6h5M8 13h8M8 17h6"/>',
    ocr:'<path d="M3 8V3h5m8 0h5v5m0 8v5h-5M8 21H3v-5M8 9h8m-4 0v8"/>',
    info:'<circle cx="12" cy="12" r="9"/><path d="M12 11v6m0-11v1"/>',
    user:'<circle cx="12" cy="8" r="4"/><path d="M4 21c0-8 16-8 16 0"/>',
    palette:'<path d="M12 3a9 9 0 1 0 0 18h2c3 0 3-4 0-4-2 0-2-3 0-3h3c6 0 3-11-5-11Z"/><circle cx="7" cy="10" r="1"/><circle cx="11" cy="6" r="1"/><circle cx="16" cy="8" r="1"/>'
  };
  const icon = name => '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">'+(paths[name]||paths.info)+'</svg>';
  const fresh = () => ({
    scene:'chat', overlay:null, status:'completed', showThinking:false,
    thinking:true, webSearch:false, showTools:false, inputText:'', extraMessages:[],
    homeQuery:'', homeFolder:'all', homeMore:false, settingsTab:0,
    directions:false, timeline:true, memory:true, worldBook:false, externalMemory:false,
    inputInjection:false, temperature:1, temperatureMax:1, topP:1, maxTokens:4096,
    modelLabel:'示例模型 A', chatTitle:'山间来信', systemPrompt:'',customTrigger:'',contextLimit:''
  });
  const states = Object.fromEntries(themes.map(theme=>[theme.id,fresh()]));
  const view = {scene:'chat', focus:'deckle', single:false};
  const gallery = document.querySelector('.gallery');
  const feedback = document.querySelector('.preview-feedback');
  let toastTimer;
  const notify = message => {
    feedback.textContent = message;
    clearTimeout(toastTimer);
    toastTimer=setTimeout(()=>feedback.textContent='',6500);
  };
  function contents(s) {
    const scene=s.scene==='home' ? window.EchoPaperScenes.home(s,icon) : window.EchoPaperScenes.chat(s,icon);
    return scene+(s.overlay==='settings' ? window.EchoPaperScenes.settings(s,icon) : '');
  }
  function renderAll() {
    gallery.innerHTML=themes.map(theme=>'<article class="study" data-theme="'+theme.id+'"><header class="study-header"><h2>'+theme.name+'</h2><button data-focus="'+theme.id+'" aria-label="单独查看'+theme.name+'">放大 ↗</button></header><p class="study-description">'+theme.description+'</p><div class="device '+theme.id+'" aria-label="'+theme.name+' · App页面样稿"><div class="system-bar"><span>9:41</span><span class="system-icons">▂▄▆ &nbsp; ◔ <i></i></span></div><div class="app-surface">'+contents(states[theme.id])+'</div><div class="system-gesture"></div></div><div class="swatches" aria-label="主题色板">'+theme.colors.map(color=>'<i style="background:'+color+'"></i>').join('')+'<span>'+theme.english+'</span></div><p class="study-notes">'+theme.notes+'</p></article>').join('');
    sync();
  }
  function renderOne(themeId) {
    const article=gallery.querySelector('.study[data-theme="'+themeId+'"]');
    const surface=article.querySelector('.app-surface');
    const active=surface.contains(document.activeElement) ? document.activeElement : null;
    const field=active?.dataset.field;
    const selection=active && 'selectionStart' in active ? [active.selectionStart, active.selectionEnd] : null;
    const scrolls=['.chat-scroll','.home-list','.dialog-scroll'].map(selector=>[selector,surface.querySelector(selector)?.scrollTop||0]);
    surface.innerHTML=contents(states[themeId]);
    for(const [selector,top] of scrolls){const element=surface.querySelector(selector);if(element)element.scrollTop=top;}
    if(field){
      const element=surface.querySelector('[data-field="'+field+'"]');
      if(element){element.focus({preventScroll:true});if(selection && (element.tagName==='TEXTAREA'||['text','search'].includes(element.type)))try{element.setSelectionRange(...selection);}catch{}}
    }
  }
  function sync() {
    gallery.dataset.focus=view.focus;
    gallery.classList.toggle('focus-mode',view.single);
    document.querySelectorAll('.study').forEach(article=>article.classList.toggle('is-focused',article.dataset.theme===view.focus));
    document.querySelectorAll('.theme-switch [data-theme]').forEach(button=>button.setAttribute('aria-pressed',String(button.dataset.theme===view.focus)));
    document.querySelectorAll('.scene-switch [data-scene]').forEach(button=>button.setAttribute('aria-pressed',String(button.dataset.scene===view.scene)));
    const compare=document.querySelector('#compare');
    compare.setAttribute('aria-pressed',String(view.single));
    compare.textContent=view.single?'四套并排':'单独查看';
    const descriptions={
      home:'会话首页 · 聚焦对话布局；展示合成的已置顶会话与最近故事，卡片仅保留实际存在的模型名、消息数和操作。',
      chat:'普通聊天 · 示例设置：隐藏模型头像、圆圆 Bot、显示思考胶囊，已开启“普通对话应用阅读字体”；输入栏和上下文入口按实际位置排列。',
      settings:'从聊天右上角打开的“对话设置”弹窗 · 三页签、可滚动正文与固定操作区；不是独立的阅读页面。'
    };
    document.querySelector('#scene-description').textContent=descriptions[view.scene];
  }
  function changeScene(scene) {
    view.scene=scene;
    for(const s of Object.values(states)){
      s.scene=scene==='home'?'home':'chat';
      s.overlay=scene==='settings'?'settings':null;
      s.showTools=false;
      s.homeMore=false;
      if(s.overlay)s.settingsSnapshot=JSON.parse(JSON.stringify({...s,settingsSnapshot:undefined,overlay:null}));
    }
    renderAll();
  }
  document.addEventListener('click',async event=>{
    const button=event.target.closest('button');
    if(!button)return;
    if(button.dataset.scene){changeScene(button.dataset.scene);return;}
    if(button.dataset.theme){view.focus=button.dataset.theme;sync();return;}
    if(button.dataset.focus){view.focus=button.dataset.focus;view.single=true;sync();document.querySelector('.desk').scrollIntoView({block:'start'});return;}
    if(button.id==='compare'){view.single=!view.single;sync();return;}
    const article=button.closest('.study');
    if(!article)return;
    const themeId=article.dataset.theme;
    const s=states[themeId];
    if(button.dataset.toggle){s[button.dataset.toggle]=!s[button.dataset.toggle];renderOne(themeId);return;}
    const action=button.dataset.action;
    if(!action)return;
    if(action==='preview-notice'){notify(button.dataset.message||'此处仅预览实际控件外观。');return;}
    if(action==='preview-nav'){notify(({creation:'创作',statistics:'统计',settings:'全局设置'}[button.dataset.destination]||'该页面')+'是 App 中的真实入口；本次样稿展示会话首页、聊天和对话设置弹窗。');return;}
    if(action==='context-info'){notify('这是 App 的上下文使用情况入口。本次只预览入口外观，不伪造用量详情。');return;}
    if(action==='new-chat'){notify('实际 App 会创建新对话或打开新对话配置；主题样稿不会新增真实会话。');return;}
    if(action==='go-home'){s.scene='home';s.overlay=null;}
    if(action==='open-chat'){
      s.scene='chat';s.overlay=null;
      const label=button.getAttribute('aria-label')||'';
      s.chatTitle=label.startsWith('打开对话：') ? label.slice(5) : '山间来信';
    }
    if(action==='home-more')s.homeMore=!s.homeMore;
    if(action==='home-filter')s.homeFolder=button.dataset.filter;
    if(action==='home-clear')s.homeQuery='';
    if(action==='open-settings'){s.settingsSnapshot=JSON.parse(JSON.stringify({...s,settingsSnapshot:undefined}));s.overlay='settings';s.settingsTab=0;}
    if(action==='dismiss-settings'){if(s.settingsSnapshot)Object.assign(s,s.settingsSnapshot);s.overlay=null;s.settingsSnapshot=null;}
    if(action==='save-settings'){s.overlay=null;s.settingsSnapshot=null;notify('设定仅保留在本次浏览器样稿中，未写入 App。');}
    if(action==='settings-tab')s.settingsTab=Number(button.dataset.tab);
    if(action==='thinking-details')s.showThinking=!s.showThinking;
    if(action==='toggle-thinking')s.thinking=!s.thinking;
    if(action==='toggle-search')s.webSearch=!s.webSearch;
    if(action==='chat-tools')s.showTools=!s.showTools;
    if(action==='stop'){s.status='paused';document.querySelector('#chat-status').value='paused';}
    if(action==='copy-message'){
      const text=button.closest('article')?.querySelector('.assistant-body,.user-bubble')?.textContent||'';
      try{await navigator.clipboard.writeText(text);notify('消息已复制。');}catch{notify('当前浏览器不允许自动复制，可在正文中手动选择文字。');}
      return;
    }
    renderOne(themeId);
  });
  document.addEventListener('input',event=>{
    const element=event.target;
    const article=element.closest('.study');
    if(!article || !element.dataset.field)return;
    const s=states[article.dataset.theme];
    const key=element.dataset.field;
    s[key]=['temperature','topP','maxTokens'].includes(key)?Number(element.value):element.value;
    if(key==='homeQuery' || key==='inputText')renderOne(article.dataset.theme);
  });
  document.addEventListener('change',event=>{
    const element=event.target;
    if(element.id==='texture'){document.body.classList.toggle('no-texture',!element.checked);return;}
    if(element.id==='chat-status'){
      for(const s of Object.values(states)){s.status=element.value;s.showThinking=false;}
      renderAll();return;
    }
    const article=element.closest('.study');
    if(article && ['temperature','topP','maxTokens'].includes(element.dataset.field))renderOne(article.dataset.theme);
  });
  document.addEventListener('submit',event=>{
    if(!event.target.matches('.chat-composer'))return;
    event.preventDefault();
    const article=event.target.closest('.study');
    const s=states[article.dataset.theme];
    const text=s.inputText.trim();
    if(!text)return;
    if(s.status==='thinking'||s.status==='replying'){notify('实际 App 会把这条消息加入排队。主题样稿未发出请求。');s.inputText='';}
    else{s.extraMessages.push(text);s.inputText='';notify('输入已加入当前样稿，未发送到接口。');}
    renderOne(article.dataset.theme);
    const scroll=article.querySelector('.chat-scroll');
    if(scroll)scroll.scrollTop=scroll.scrollHeight;
  });
  renderAll();
})();
