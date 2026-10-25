(() => {
  'use strict';

  window.EchoPaperScenes = window.EchoPaperScenes || {};
  // Exact, already tracked HomeModelAvatar fallback; no external image request.
  const modelAvatar = 'data:image/png;base64,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';
  window.EchoPaperScenes.modelAvatar = modelAvatar;
  const syntheticConversations = [
    { id: 'story', title: '山间来信', model: '示例模型 A', count: 24, pinned: true, folder: 'writing' },
    { id: 'plan', title: '周末计划', model: '示例模型 A', count: 8, pinned: false, folder: 'all' },
    { id: 'edit', title: '一段文字的修改', model: '示例模型 A', count: 12, pinned: false, folder: 'writing' },
  ];
  const escapeText = value => String(value).replace(/[&<>"']/g, character => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  }[character]));
  const notice = 'data-action="preview-notice" data-message="该入口在App中存在，本次仅预览主题外观。"';

  window.EchoPaperScenes.home = (s, icon) => {
    const query = String(s.homeQuery || '');
    const filter = ['all', 'pinned', 'writing'].includes(s.homeFolder) ? s.homeFolder : 'all';
    const hasPresentation = !query.trim() && filter === 'all';
    const conversations = syntheticConversations.filter(conversation => {
      const matchesFolder = filter === 'pinned' ? conversation.pinned : filter === 'writing' ? conversation.folder === 'writing' : true;
      const matchesQuery = `${conversation.title} ${conversation.model}`.toLocaleLowerCase().includes(query.trim().toLocaleLowerCase());
      return matchesFolder && matchesQuery;
    });
    const pinned = conversations.filter(conversation => conversation.pinned);
    const other = conversations.filter(conversation => !conversation.pinned);

    const row = conversation => `
      <div class="conversation-row">
        <button class="conversation-open" data-action="open-chat" aria-label="打开对话：${escapeText(conversation.title)}">
          <img class="model-avatar" src="${modelAvatar}" alt="模型头像" width="40" height="40">
          <span class="conversation-info">
            <span class="conversation-title"><strong>${escapeText(conversation.title)}</strong>${conversation.pinned ? `<span class="conversation-pin" aria-label="置顶">${icon('pin')}</span>` : ''}</span>
            <span class="conversation-metadata"><span class="model-pill">${escapeText(conversation.model)}</span><span class="message-count">${conversation.count} 条消息</span></span>
          </span>
        </button>
        <button class="icon-button conversation-more" ${notice} aria-label="更多对话操作：${escapeText(conversation.title)}">${icon('more')}</button>
      </div>`;

    const menu = s.homeMore ? `
      <div class="home-more-menu" role="menu" aria-label="更多会话工具">
        <button role="menuitem" data-action="new-chat">${icon('tune')}<span>配置新对话</span></button>
        <button role="menuitem" ${notice}>${icon('history')}<span>历史与隐藏会话</span></button>
        <button role="menuitem" ${notice}>${icon('folder')}<span>管理文件夹</span></button>
        <hr>
        <button role="menuitem" data-action="preview-nav" data-destination="creation">${icon('book')}<span>创作与资料</span></button>
        <button role="menuitem" data-action="preview-nav" data-destination="statistics">${icon('stats')}<span>使用统计</span></button>
        <button role="menuitem" data-action="preview-nav" data-destination="settings">${icon('settings')}<span>设置</span></button>
      </div>` : '';

    const list = conversations.length ? `
      ${pinned.length ? `<div class="home-pinned-heading"><span>${icon('pin')}<strong>置顶</strong><small>${pinned.length}</small></span><span aria-hidden="true" class="pinned-chevron">${icon('chevron')}</span></div>${pinned.map(row).join('')}` : ''}
      ${pinned.length && other.length ? '<p class="home-other-heading">其他</p>' : ''}
      ${other.map(row).join('')}` : `
      <div class="home-empty"><strong>${query.trim() ? '没有找到匹配的会话' : '这里还没有会话'}</strong><p>${query.trim() ? '试试其他关键词，或从历史记录中查找。' : '可以切回全部会话，或在这里新建对话。'}</p></div>`;

    return `
      <section class="app-home" aria-label="会话首页 · 聚焦对话布局">
        <header class="home-header">
          <div class="home-brand"><strong>ECHO</strong><span>会话</span></div>
          <div class="home-more-anchor"><button class="icon-button" data-action="home-more" aria-label="更多会话工具" aria-expanded="${Boolean(s.homeMore)}">${icon('more')}</button>${menu}</div>
        </header>
        <div class="home-search">
          <label class="home-search-input">${icon('search')}<input data-field="homeQuery" value="${escapeText(query)}" placeholder="搜索对话、模型或提示词" aria-label="搜索对话、模型或提示词">${query ? '<button class="home-clear" type="button" data-action="home-clear" aria-label="清空搜索">×</button>' : ''}</label>
          <button class="icon-button" ${notice} aria-label="历史记录">${icon('history')}</button>
        </div>
        <nav class="home-folders" aria-label="文件夹筛选">
          <button data-action="home-filter" data-filter="all" aria-pressed="${filter === 'all'}">${icon('chat')}<span>对话</span></button>
          <button data-action="home-filter" data-filter="pinned" aria-pressed="${filter === 'pinned'}">${icon('pin')}<span>置顶</span></button>
          <button data-action="home-filter" data-filter="writing" aria-pressed="${filter === 'writing'}">${icon('folder')}<span>写作</span></button>
          <button ${notice}>${icon('plus')}<span>添加</span></button>
        </nav>
        <div class="home-list app-scroll">
          ${hasPresentation ? `
            <div class="home-actions"><button class="home-new-chat" data-action="new-chat"><span>新对话</span>${icon('plus')}</button><button class="home-configure icon-button" data-action="new-chat" aria-label="选择模型与新对话设置">${icon('tune')}</button></div>
            <button class="home-story" data-action="preview-notice" data-message="实际 App 会进入关联的故事会话；本次聊天样稿展示普通聊天。">${icon('book')}<span><small>继续最近故事</small><strong>山间来信</strong></span>${icon('arrow')}</button>
            <div class="home-section"><strong>最近会话</strong><small>长按多选</small></div>` : ''}
          ${list}
        </div>
        ${!hasPresentation ? `<button class="home-floating-new" data-action="new-chat">${icon('plus')}<span>新对话</span></button>` : ''}
        <nav class="root-nav" aria-label="主导航">
          <button aria-current="page">${icon('chat')}<span>会话</span></button>
          <button data-action="preview-nav" data-destination="creation">${icon('create')}<span>创作</span></button>
          <button data-action="preview-nav" data-destination="statistics">${icon('stats')}<span>统计</span></button>
          <button data-action="preview-nav" data-destination="settings">${icon('settings')}<span>设置</span></button>
        </nav>
      </section>`;
  };
})();
