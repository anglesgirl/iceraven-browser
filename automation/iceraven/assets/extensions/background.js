// X/Twitter ECH Redirector - Background Service Worker
// Fetches clean CF IPs from Tencent DNS and redirects x.com/twitter.com/twimg.com traffic

const DOMAINS_TO_REDIRECT = ['x.com', 'twitter.com', 'twimg.com'];
const TXT_DOMAIN = 'ech-config.anglesgirl.eu.org';
const TENCENT_DOH_URL = `https://119.29.29.29/dns-query?name=${TXT_DOMAIN}&type=TXT`;
const RULESET_ID = 'ruleset_1';
const UPDATE_INTERVAL_MINUTES = 60;

// Storage keys
const STORAGE_KEYS = {
  CLEAN_IP: 'ech_redirector_clean_ip',
  LAST_UPDATE: 'ech_redirector_last_update',
  RULE_COUNT: 'ech_redirector_rule_count',
  UPDATING: 'ech_redirector_updating'
};

let currentCleanIP = null;
let rulesInitialized = false;

async function saveStatus(cleanIP, ruleCount, updating) {
  const data = {
    [STORAGE_KEYS.CLEAN_IP]: cleanIP,
    [STORAGE_KEYS.RULE_COUNT]: ruleCount,
    [STORAGE_KEYS.UPDATING]: updating
  };
  if (!updating) {
    data[STORAGE_KEYS.LAST_UPDATE] = Date.now();
  }
  await chrome.storage.local.set(data);
}

async function fetchCleanIPs() {
  try {
    const response = await fetch(TENCENT_DOH_URL, {
      method: 'GET',
      headers: {
        'Accept': 'application/dns-json'
      }
    });

    if (!response.ok) {
      throw new Error(`DNS query failed: ${response.status}`);
    }

    const data = await response.json();

    if (data.Answer && data.Answer.length > 0) {
      const txtRecord = data.Answer.find(r => r.type === 16); // TXT = 16
      if (txtRecord && txtRecord.data) {
        // TXT data format: "ip=x.com=1.2.3.4; ip=twitter.com=1.2.3.4; ip=twimg.com=1.2.3.4;"
        const ipMap = {};
        const ipRegex = /ip=([^=;]+)=([\d.]+)/g;
        let match;
        while ((match = ipRegex.exec(txtRecord.data)) !== null) {
          ipMap[match[1]] = match[2];
        }
        return ipMap;
      }
    }
    throw new Error('No valid TXT record found');
  } catch (error) {
    console.error('[ECH Redirector] Failed to fetch clean IPs:', error);
    return null;
  }
}

function buildRedirectRules(ipMap) {
  const rules = [];
  let ruleId = 1;

  for (const domain of DOMAINS_TO_REDIRECT) {
    const cleanIP = ipMap[domain];
    if (!cleanIP) {
      console.warn(`[ECH Redirector] No IP found for ${domain}, skipping`);
      continue;
    }
    // Match all subdomains and paths
    rules.push({
      id: ruleId++,
      priority: 1,
      action: {
        type: 'redirect',
        redirect: {
          transform: {
            host: cleanIP
          }
        }
      },
      condition: {
        urlFilter: `||${domain}/*`,
        resourceTypes: ['main_frame', 'sub_frame', 'xmlhttprequest', 'fetch', 'image', 'script', 'stylesheet', 'font', 'media', 'other']
      }
    });

    // Also match the bare domain
    rules.push({
      id: ruleId++,
      priority: 1,
      action: {
        type: 'redirect',
        redirect: {
          transform: {
            host: cleanIP
          }
        }
      },
      condition: {
        urlFilter: `||${domain}^`,
        resourceTypes: ['main_frame', 'sub_frame', 'xmlhttprequest', 'fetch', 'image', 'script', 'stylesheet', 'font', 'media', 'other']
      }
    });
  }

  return rules;
}

async function updateRedirectRules() {
  console.log('[ECH Redirector] Updating redirect rules...');
  await saveStatus(currentCleanIP, 0, true);

  const ipMap = await fetchCleanIPs();
  if (!ipMap) {
    console.error('[ECH Redirector] Could not fetch clean IPs, keeping existing rules');
    await saveStatus(currentCleanIP, rulesInitialized ? DOMAINS_TO_REDIRECT.length * 2 : 0, false);
    return false;
  }

  // Check if any IP changed
  let changed = false;
  for (const domain of DOMAINS_TO_REDIRECT) {
    if (ipMap[domain] !== currentCleanIP?.[domain]) {
      changed = true;
      break;
    }
  }

  if (!changed && rulesInitialized) {
    console.log('[ECH Redirector] IPs unchanged, skipping update');
    await saveStatus(currentCleanIP, DOMAINS_TO_REDIRECT.length * 2, false);
    return true;
  }

  currentCleanIP = ipMap;
  const rules = buildRedirectRules(ipMap);

  try {
    // Update dynamic rules
    await chrome.declarativeNetRequest.updateDynamicRules({
      removeRuleIds: rules.map(r => r.id),
      addRules: rules
    });

    rulesInitialized = true;
    console.log(`[ECH Redirector] Updated ${rules.length} redirect rules`);
    await saveStatus(ipMap, rules.length, false);
    return true;
  } catch (error) {
    console.error('[ECH Redirector] Failed to update rules:', error);
    await saveStatus(currentCleanIP, rulesInitialized ? DOMAINS_TO_REDIRECT.length * 2 : 0, false);
    return false;
  }
}

// Initialize on startup
chrome.runtime.onStartup.addListener(async () => {
  console.log('[ECH Redirector] Browser startup, initializing...');
  await updateRedirectRules();
});

// Initialize on install/update
chrome.runtime.onInstalled.addListener(async (details) => {
  console.log('[ECH Redirector] Installed/updated:', details.reason);
  await updateRedirectRules();

  // Create alarm for periodic updates
  chrome.alarms.create('update-redirect-rules', {
    periodInMinutes: UPDATE_INTERVAL_MINUTES
  });
});

// Handle alarm
chrome.alarms.onAlarm.addListener(async (alarm) => {
  if (alarm.name === 'update-redirect-rules') {
    console.log('[ECH Redirector] Alarm triggered, updating rules...');
    await updateRedirectRules();
  }
});

// Also handle extension startup (when browser restarts)
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message.type === 'force-update') {
    updateRedirectRules().then(success => {
      sendResponse({ success });
    });
    return true; // async response
  }
});

console.log('[ECH Redirector] Background script loaded');