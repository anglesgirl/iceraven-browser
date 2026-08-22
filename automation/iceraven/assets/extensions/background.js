// X/Twitter ECH Redirector - Background Service Worker
// Fetches clean CF IPs from Tencent DNS and redirects x.com/twitter.com/twimg.com traffic

const DOMAINS_TO_REDIRECT = ['x.com', 'twitter.com', 'twimg.com'];
const TXT_DOMAIN = 'ech-config.anglesgirl.eu.org';
const TENCENT_DOH_URL = `https://119.29.29.29/dns-query?name=${TXT_DOMAIN}&type=TXT`;
const RULESET_ID = 'ruleset_1';
const UPDATE_INTERVAL_MINUTES = 60;

let currentCleanIP = null;
let rulesInitialized = false;

async function fetchCleanIP() {
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
      // TXT record contains the clean IP
      const txtRecord = data.Answer.find(r => r.type === 16); // TXT = 16
      if (txtRecord && txtRecord.data) {
        // TXT data format: "v=echconfig; ip=1.2.3.4"
        const match = txtRecord.data.match(/ip=([\d.]+)/);
        if (match) {
          return match[1];
        }
        // Fallback: maybe just the IP directly
        const ipMatch = txtRecord.data.match(/^[\d.]+$/);
        if (ipMatch) {
          return txtRecord.data;
        }
      }
    }
    throw new Error('No valid TXT record found');
  } catch (error) {
    console.error('[ECH Redirector] Failed to fetch clean IP:', error);
    return null;
  }
}

function buildRedirectRules(cleanIP) {
  const rules = [];
  let ruleId = 1;
  
  for (const domain of DOMAINS_TO_REDIRECT) {
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
  
  const cleanIP = await fetchCleanIP();
  if (!cleanIP) {
    console.error('[ECH Redirector] Could not fetch clean IP, keeping existing rules');
    return false;
  }
  
  if (cleanIP === currentCleanIP && rulesInitialized) {
    console.log('[ECH Redirector] IP unchanged, skipping update');
    return true;
  }
  
  currentCleanIP = cleanIP;
  const rules = buildRedirectRules(cleanIP);
  
  try {
    // Update dynamic rules
    await chrome.declarativeNetRequest.updateDynamicRules({
      removeRuleIds: rules.map(r => r.id),
      addRules: rules
    });
    
    rulesInitialized = true;
    console.log(`[ECH Redirector] Updated ${rules.length} redirect rules for IP: ${cleanIP}`);
    return true;
  } catch (error) {
    console.error('[ECH Redirector] Failed to update rules:', error);
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