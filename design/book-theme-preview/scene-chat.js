(() => {
  'use strict';

  const escapeText = value => String(value ?? '').replace(/[&<>"']/g, character => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  }[character]));

  const action = (icon, name, label, extra = '') =>
    `<button type="button" class="icon-button" data-action="${name}" aria-label="${label}" title="${label}" ${extra}>${icon}</button>`;

  const footer = (isUser, icon, tokenCount) => `<div class="message-meta">
    <span class="message-metrics">10-25 09:41${tokenCount ? ` · ${tokenCount} tokens` : ''}</span>
    <div class="message-actions">
      ${action(icon('copy'), 'copy-message', '复制')}
      ${isUser
        ? action(icon('quote'), 'preview-notice', '引用', 'data-message="可将这条消息引用到输入框。"')
        : action(icon('branch'), 'preview-notice', '分支对话', 'data-message="实际 App 可从这条回复创建分支对话。"')}
      ${isUser ? '' : action(icon('refresh'), 'preview-notice', '重新生成', 'data-message="主题预览不会调用模型；实际 App 会重新生成这条回复。"')}
      ${action(icon('more'), 'preview-notice', '更多操作', `data-message="${isUser ? '重新编辑、仅修改内容、固定到上下文、从上下文中排除、删除本条' : '编辑回复、固定到上下文、从上下文中排除、删除本条'}"`)}
    </div>
  </div>`;

  const replyParagraphs = [
    '雨在傍晚停了。她推开窗，潮湿的风翻过桌上的书，一张褪色的车票从书页间滑了出来。',
    '出发和抵达的地名之间，只有两个小时。她用指腹抹过背面的铅笔字：“等山里的桂花开了，我们就在那里见面。”',
    '她把书翻回扉页，才看见那枚小小的藏书印。窗外最后一滴雨落进花盆，她想起了那个一直没有问出口的问题。'
  ];

  window.EchoPaperScenes = window.EchoPaperScenes || {};
  window.EchoPaperScenes.chat = (s, icon) => {
    const status = ['completed', 'thinking', 'replying', 'paused', 'failed'].includes(s.status)
      ? s.status : 'completed';
    const generating = status === 'thinking' || status === 'replying';
    const showThinking = Boolean(s.showThinking);
    const thinkingEnabled = s.thinking !== false;
    const modelName = escapeText(s.modelLabel || '示例模型 A');
    const statusLabels = {
      completed: thinkingEnabled ? `${modelName} 思考过程 3s 96 token` : `${modelName}用3秒吃掉了你138token`,
      thinking: `${modelName} 正在思考中...`,
      replying: thinkingEnabled ? `${modelName} 思考过程 3s 96 token` : `${modelName} 正在思考回复中...`,
      paused: '生成已停止',
      failed: '模型连接失败'
    };
    const botLabels = {
      completed: '模型标识', thinking: '模型标识，思考中', replying: '模型标识，回复中',
      paused: '模型标识，回复已主动暂停', failed: '模型标识，连接或回复出错'
    };
    const paragraphs = status === 'thinking' || status === 'failed' ? []
      : status === 'paused' ? replyParagraphs.slice(0, 1) : replyParagraphs;
    const extras = Array.isArray(s.extraMessages) ? s.extraMessages : [];

    return `<header class="chat-header">
      ${action(icon('back'), 'go-home', '返回')}
      <strong class="chat-title">${escapeText(s.chatTitle || '山间来信')}</strong>
      <button type="button" class="context-meter icon-button" data-action="context-info" aria-label="上下文使用情况" title="上下文使用情况">${icon('context')}</button>
      ${action(icon('tune'), 'open-settings', '对话设置')}
    </header>
    <div class="chat-scroll">
      <article class="message-user">
        <div class="user-message-content">
          <p class="user-bubble">接着写她在书里发现那张车票的片段，节奏慢一点，保留动作和环境细节。</p>
          ${footer(true, icon, 35)}
        </div>
        <span class="user-avatar" role="img" aria-label="用户头像">${icon('user')}</span>
      </article>
      <article class="message-assistant">
        <div class="assistant-status-row">
          <span class="bot-face bot-${status}" role="img" aria-label="${botLabels[status]}"><i></i><i></i><span class="bot-signal" aria-hidden="true"></span></span>
          <button type="button" class="thinking-capsule capsule-${status}" data-action="thinking-details" aria-expanded="${showThinking}" aria-label="${status === 'failed' ? (showThinking ? '收起错误提示报告' : '展开错误提示报告') : (showThinking ? '收起思考过程' : '展开思考过程')}">
            <span>${statusLabels[status]}</span>${icon('chevron')}
          </button>
        </div>
        ${showThinking && thinkingEnabled && status !== 'failed' && status !== 'paused'
          ? '<div class="thinking-detail"><p>从翻书和发现车票的动作展开，先交代雨后的环境，再让背面的字带出人物的回忆。</p></div>' : ''}
        <div class="assistant-body">${paragraphs.map(text => `<p>${text}</p>`).join('')}${status === 'thinking' ? '<span class="thinking-wait" aria-label="正在思考">•••</span>' : ''}</div>
        ${status === 'failed' && showThinking
          ? '<div class="message-error"><strong>错误提示</strong><p>连接超时，暂未收到模型回复。请检查网络或稍后重试。</p></div>' : ''}
        ${status === 'thinking' ? '' : footer(false, icon, status === 'failed' ? 0 : 138)}
      </article>
      ${extras.map(text => `<article class="message-user preview-extra-message"><div class="user-message-content"><p class="user-bubble">${escapeText(text)}</p></div><span class="user-avatar" role="img" aria-label="用户头像">${icon('user')}</span></article>`).join('')}
    </div>
    <form class="chat-composer">
      <textarea data-field="inputText" rows="2" maxlength="500" aria-label="给 Echo 发送消息" placeholder="给 Echo 发送消息">${escapeText(s.inputText)}</textarea>
      <div class="composer-tools">
        <div class="composer-pills">
          <button type="button" class="input-pill ${thinkingEnabled ? 'is-selected' : ''}" data-action="toggle-thinking" aria-pressed="${thinkingEnabled}">深度思考${icon('chevron')}</button>
          <button type="button" class="input-pill ${s.webSearch ? 'is-selected' : ''}" data-action="toggle-search" aria-pressed="${Boolean(s.webSearch)}">智能搜索</button>
        </div>
        <div class="composer-action-buttons">
          ${action(icon('plus'), 'chat-tools', '添加内容', `aria-expanded="${Boolean(s.showTools)}"`)}
          ${generating
            ? action(icon('stop'), 'stop', '停止当前生成')
            : `<button type="submit" class="icon-button send-button" aria-label="发送" ${String(s.inputText ?? '').trim() ? '' : 'disabled'}>${icon('send')}</button>`}
          ${generating && String(s.inputText ?? '').trim()
            ? `<button type="submit" class="icon-button send-button" aria-label="加入排队">${icon('send')}</button>` : ''}
        </div>
      </div>
      ${s.showTools ? `<div class="chat-tool-menu" role="menu" aria-label="添加内容">
        <button type="button" role="menuitem" data-action="preview-notice" data-message="实际 App 可选择并上传图片；此处只预览主题。">${icon('image')}上传图片</button>
        <button type="button" role="menuitem" data-action="preview-notice" data-message="实际 App 可选取文件；此处只预览主题。">${icon('file')}选取文件</button>
        <button type="button" role="menuitem" data-action="preview-notice" data-message="实际 App 可识别图片文字；此处只预览主题。">${icon('ocr')}图片 OCR 识别</button>
      </div>` : ''}
    </form>`;
  };
})();
