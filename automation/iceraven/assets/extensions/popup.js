// ECH Redirector Popup - Status display & force update

const STATUS_KEYS = {
  CLEAN_IP: 'ech_redirector_clean_ip',
  LAST_UPDATE: 'ech_redirector_last_update',
  RULE_COUNT: 'ech_redirector_rule_count',
  UPDATING: 'ech_redirector_updating'
};

const DOMAINS_TO_REDIRECT = ['x.com', 'twitter.com', 'twimg.com'];

async function loadStatus() {
  const result = await chrome.storage.local.get([
    STATUS_KEYS.CLEAN_IP,
    STATUS_KEYS.LAST_UPDATE,
    STATUS_KEYS.RULE_COUNT,
    STATUS_KEYS.UPDATING
  ]);

  const ipMap = result[STATUS_KEYS.CLEAN_IP] || null;
  const lastUpdate = result[STATUS_KEYS.LAST_UPDATE] || null;
  const ruleCount = result[STATUS_KEYS.RULE_COUNT] || 0;
  const updating = result[STATUS_KEYS.UPDATING] || false;

  updateUI(ipMap, lastUpdate, ruleCount, updating);
}

function updateUI(ipMap, lastUpdate, ruleCount, updating) {
  const statusIcon = document.getElementById('statusIcon');
  const statusText = document.getElementById('statusText');
  const cleanIPEl = document.getElementById('cleanIP');
  const ruleCountEl = document.getElementById('ruleCount');
  const lastUpdateEl = document.getElementById('lastUpdate');
  const forceUpdateBtn = document.getElementById('forceUpdateBtn');
  const updateMsg = document.getElementById('updateMsg');

  if (updating) {
    statusIcon.className = 'status-indicator';
    statusText.textContent = '更新中...';
    statusText.className = 'value';
    forceUpdateBtn.disabled = true;
    forceUpdateBtn.textContent = '⏳ 更新中...';
    updateMsg.textContent = '正在从腾讯云 DoH 获取最新 IP...';
    return;
  }

  if (ipMap) {
    statusIcon.className = 'status-indicator running';
    statusText.textContent = '运行正常';
    statusText.className = 'value ok';
    // Show all domain IPs
    const ipLines = DOMAINS_TO_REDIRECT.map(d => `${d}: ${ipMap[d] || '—'}`).join('\n');
    cleanIPEl.textContent = ipLines;
    cleanIPEl.className = 'value ok';
  } else {
    statusIcon.className = 'status-indicator stopped';
    statusText.textContent = '未获取到 IP';
    statusText.className = 'value error';
    cleanIPEl.textContent = '—';
    cleanIPEl.className = 'value error';
  }

  ruleCountEl.textContent = `${ruleCount} 条`;
  ruleCountEl.className = 'value';

  if (lastUpdate) {
    const date = new Date(lastUpdate);
    lastUpdateEl.textContent = date.toLocaleString('zh-CN');
  } else {
    lastUpdateEl.textContent = '从未';
  }

  forceUpdateBtn.disabled = false;
  forceUpdateBtn.textContent = '🔄 强制更新 IP & 规则';
  updateMsg.textContent = '';
}

async function forceUpdate() {
  const forceUpdateBtn = document.getElementById('forceUpdateBtn');
  const updateMsg = document.getElementById('updateMsg');

  forceUpdateBtn.disabled = true;
  forceUpdateBtn.textContent = '⏳ 更新中...';
  updateMsg.textContent = '正在从腾讯云 DoH 获取最新 IP...';

  // 发送消息给 background 强制更新
  chrome.runtime.sendMessage({ type: 'force-update' }, async (response) => {
    if (chrome.runtime.lastError) {
      updateMsg.textContent = '❌ 通信失败：' + chrome.runtime.lastError.message;
      forceUpdateBtn.disabled = false;
      forceUpdateBtn.textContent = '🔄 强制更新 IP & 规则';
      return;
    }

    if (response && response.success) {
      updateMsg.textContent = '✅ 更新成功！';
      setTimeout(loadStatus, 500); // 稍等 background 存储状态
    } else {
      updateMsg.textContent = '❌ 更新失败，请查看控制台日志';
      forceUpdateBtn.disabled = false;
      forceUpdateBtn.textContent = '🔄 强制更新 IP & 规则';
    }
  });
}

function openOptions() {
  chrome.runtime.openOptionsPage();
}

document.getElementById('forceUpdateBtn').addEventListener('click', forceUpdate);
document.getElementById('openOptionsBtn').addEventListener('click', openOptions);

// 初始加载
loadStatus();

// 监听 storage 变化（background 更新后自动刷新）
chrome.storage.onChanged.addListener((changes, area) => {
  if (area === 'local') {
    loadStatus();
  }
});