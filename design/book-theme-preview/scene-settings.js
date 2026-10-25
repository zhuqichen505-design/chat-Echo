(() => {
  'use strict';

  window.EchoPaperScenes = window.EchoPaperScenes || {};

  const escapeText = value => String(value ?? '').replace(/[&<>"']/g, character => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  }[character]));

  const number = (value, fallback) => Number.isFinite(Number(value)) ? Number(value) : fallback;

  // Source: ChatSettingsDialogs.kt, ChatModelSelectors.kt and ChatMemorySection.kt.
  // This is the existing conversation-settings dialog, shown over the chat screen.
  window.EchoPaperScenes.settings = (s, icon) => {
    const settingsTab = Math.min(2, Math.max(0, Math.trunc(number(s.settingsTab, 0))));
    const modelLabel = escapeText(s.modelLabel || '示例模型');
    const field = (title, body) => `<section class="setting-field"><h4>${title}</h4>${body}</section>`;
    const notice = (label, message, className = '') => `<button type="button" class="${className}" data-action="preview-notice" data-message="${escapeText(message)}">${label}</button>`;
    const switchControl = (key, label) => `<button type="button" class="switch" data-toggle="${key}" role="switch" aria-checked="${Boolean(s[key])}" aria-label="${label}"><span></span></button>`;
    const toggle = (key, label, description = '') => `<div class="setting-row"><div><strong>${label}</strong>${description ? `<small>${description}</small>` : ''}</div>${switchControl(key, label)}</div>`;
    const tabs = ['模型与生成', '提示与记忆', '外观与模式'];

    const modelAndGeneration = () => {
      const maxTemperature = Math.max(1, number(s.temperatureMax, 1));
      const temperature = Math.min(maxTemperature, Math.max(0, number(s.temperature, 1)));
      const topP = Math.min(1, Math.max(0, number(s.topP, 1)));
      const thinkingEfforts = Array.isArray(s.thinkingEfforts) ? s.thinkingEfforts : [];

      return `${field('模型', notice(`${icon('brain')}<span>${modelLabel}</span>${icon('chevron')}`, '模型列表为合成示例，未连接接口。', 'model-select'))}
        ${toggle('thinking', '思考模式', '支持时按厂商协议传入 reasoning_effort，档位与采样参数随模型自动适配')}
        ${toggle('webSearch', '联网', '仅对支持联网的API或模型生效')}
        ${s.thinking && thinkingEfforts.length ? field('思考强度', `<div class="setting-chips">${thinkingEfforts.map(option => notice(escapeText(option.label), '思考强度仅在当前预览中展示。')).join('')}</div>`) : ''}
        ${toggle('directions', '多方向回复')}
        <p class="setting-description">发送后先选择回复方向，再展开正式回复。仅当前对话生效，会增加一次模型请求。</p>
        ${s.directions ? field('方向数量', `<div class="setting-chips">${[2, 3, 4].map(count => notice(`${count} 个方向`, '方向数量仅在当前预览中展示。')).join('')}</div><p class="setting-description">每次还可选“自行决定”或填写“其他”方向。稍后选择可从底部提示继续。</p>`) : ''}
        ${field(`温度: ${temperature.toFixed(2)}`, `<p class="setting-description">越低严谨，越高富有想象力</p><input class="setting-range" type="range" min="0" max="${maxTemperature}" step="0.05" value="${temperature}" data-field="temperature" aria-label="温度"><div class="range-labels"><span>精确</span><span>平衡</span><span>发散</span></div>`)}
        ${field('最大 Token 数', `<p class="setting-description">限制单次回复的最大生成长度</p><label class="number-field"><input type="number" min="0" max="999999" step="1" value="${Math.max(0, Math.floor(number(s.maxTokens, 4096)))}" placeholder="默认 4096" data-field="maxTokens" aria-label="最大 Token 数"><span>tokens</span></label><p class="setting-description">默认 4096 tokens。留空会使用模型或全局配置的默认值。</p>`)}
        ${field('上下文上限', `<div class="setting-chips">${['跟随模型', '32K', '64K', '128K', '200K', '1M', '2M'].map(label => notice(label, '上下文上限仅在当前预览中展示。')).join('')}</div><input type="number" class="context-limit" data-field="contextLimit" value="${escapeText(s.contextLimit || '')}" placeholder="留空表示跟随模型默认" aria-label="上下文上限"><p class="setting-description">仅对当前对话生效，不影响该模型在其他会话的限制；超限自动降级保护也仅在此对话生效。</p>`)}
        ${field(`Top P: ${topP.toFixed(2)}`, `<p class="setting-description">核采样概率阈值，控制用词发散程度</p><input class="setting-range" type="range" min="0" max="1" step="0.05" value="${topP}" data-field="topP" aria-label="Top P">`)}`;
    };

    const promptAndMemory = () => `
      ${toggle('inputInjection', '输入注入回复')}
      <p class="setting-description">输入仍显示为你的消息；后台将原文隔两个换行追加到上一条模型回复，再发送自定义触发语。</p>
      ${s.inputInjection ? field('自定义触发语', `<textarea rows="3" data-field="customTrigger" placeholder="输入实际发送给模型的文字" aria-label="自定义触发语">${escapeText(s.customTrigger || '')}</textarea>`) : ''}
      ${field('系统提示词', `<div class="setting-chips">${notice('模板', '提示词模板列表为合成示例。')}${notice('存为模板', '预览内容不会保存到 App。')}</div><textarea rows="4" data-field="systemPrompt" placeholder="例如：你是一个专业、简洁、可靠的助手。" aria-label="系统提示词">${escapeText(s.systemPrompt || '')}</textarea><p class="setting-description">【优先级说明】若设置了当前对话的系统提示词，将 100% 覆盖全局系统提示词；若留空则自动继承全局系统提示词。</p>`)}
      <section class="setting-field memory-section">
        <div class="setting-row"><div><strong>会话设定与规则</strong><small>0 条 · ${s.memory ? '发送时附带' : '已停用'}</small></div>${switchControl('memory', '会话设定与规则')}${notice(icon('chevron'), '此处使用没有已保存设定的合成会话。', 'section-expand')}</div>
      </section>
      <section class="setting-field timeline-section">
        <div class="setting-row"><div><strong>时间线</strong><small>0 个节点 · 未设置故事时间${s.timeline ? '' : ' · 已停用'}</small></div>${switchControl('timeline', '时间线')}${notice(icon('chevron'), '此处使用没有已保存时间节点的合成会话。', 'section-expand')}</div>
      </section>
      ${field('跨会话记忆与世界书 (Lorebook)', `${toggle('externalMemory', '跨会话长期记忆', '跨会话的全局偏好与用户画像，按输入意图和关键词动态检索注入，角色扮演默认严格隔离')}${toggle('worldBook', '世界书设定 (Lorebook)', '根据关键词动态唤醒世界观设定与专有名词知识，可在系统设置中管理词条')}<div class="setting-chips">${notice('全部已启用', '世界书选择仅在当前预览中展示。')}${notice('仅选中', '世界书选择仅在当前预览中展示。')}</div><p class="setting-description">暂无世界书，可先在系统设置中创建或导入。</p>`)}`;

    const appearanceAndMode = () => `${field('模型头像', `<div class="setting-row"><img class="preview-model-avatar" src="${window.EchoPaperScenes.modelAvatar}" alt="默认模型头像"><div><small>当前使用 deepseek 默认头像</small><div class="setting-chips">${notice('更换', '预览使用 App 自带默认头像，未读取私人图片。')}</div></div></div>`)}
      <button type="button" class="setting-row mode-conversion" data-action="preview-notice" data-message="此操作仅展示实际入口，预览不会修改会话模式。">${icon('book')}<span><strong>转为角色扮演 / 故事创作</strong><small>平滑升级为故事会话，解锁角色卡、世界观与剧情推进指令</small></span>${icon('chevron')}</button>`;

    return `<div class="overlay settings-overlay">
      <button type="button" class="dialog-backdrop" data-action="dismiss-settings" aria-label="关闭对话设置"></button>
      <section class="dialog conversation-settings" role="dialog" aria-modal="true" aria-label="对话设置">
        <header class="settings-heading"><h3>对话设置</h3><p>当前对话配置会直接生效</p></header>
        <div class="settings-tabs" role="tablist" aria-label="对话设置分类">${tabs.map((label, tab) => `<button type="button" role="tab" aria-selected="${settingsTab === tab}" data-action="settings-tab" data-tab="${tab}">${label}</button>`).join('')}</div>
        <div class="dialog-scroll" role="tabpanel" aria-label="${tabs[settingsTab]}">${[modelAndGeneration, promptAndMemory, appearanceAndMode][settingsTab]()}</div>
        <footer class="dialog-actions"><button type="button" data-action="dismiss-settings">取消</button><button type="button" class="primary-button" data-action="save-settings">保存</button></footer>
      </section>
    </div>`;
  };
})();
