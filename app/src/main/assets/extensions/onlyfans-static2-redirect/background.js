/**
 * OnlyFans Static2 Redirect - MV3 WebExtension
 * Dynamic DNR + webRequestBlocking fallback with FULL LOGGING
 */

const STATIC2_HOST = 'static2.onlyfans.com';
const TARGET_HOST = 'onlyfans.com';

const RULES = [{
  id: 1,
  priority: 1,
  action: {
    type: 'redirect',
    redirect: {
      regexSubstitution: 'https://onlyfans.com/\\1'
    }
  },
  condition: {
    regexFilter: '^https?://static2\\.onlyfans\\.com/(.*)',
    resourceTypes: [
      'main_frame', 'sub_frame', 'stylesheet', 'script',
      'image', 'font', 'xmlhttprequest', 'other'
    ]
  }
}];

async function logRules(label) {
  try {
    const dynamic = await chrome.declarativeNetRequest.getDynamicRules();
    const session = await chrome.declarativeNetRequest.getSessionRules();
    console.log(`[OnlyFans Redirect] ${label} - dynamic:`, JSON.stringify(dynamic));
    console.log(`[OnlyFans Redirect] ${label} - session:`, JSON.stringify(session));
  } catch (e) {
    console.error(`[OnlyFans Redirect] ${label} - getRules error:`, e);
  }
}

async function installRules() {
  console.log('[OnlyFans Redirect] installRules() START');
  await logRules('before-install');
  
  try {
    const existing = await chrome.declarativeNetRequest.getDynamicRules();
    const removeIds = existing.map(r => r.id);
    console.log('[OnlyFans Redirect] removing old rules:', removeIds);
    
    await chrome.declarativeNetRequest.updateDynamicRules({
      removeRuleIds: removeIds,
      addRules: RULES
    });
    
    console.log('[OnlyFans Redirect] updateDynamicRules SUCCESS');
    await logRules('after-install');
    return true;
  } catch (error) {
    console.error('[OnlyFans Redirect] installRules FAILED:', error);
    return false;
  }
}

// Fallback: webRequestBlocking
function webRequestFallbackListener(details) {
  const url = new URL(details.url);
  if (url.hostname !== STATIC2_HOST) return {};
  
  const targetUrl = `${url.protocol}//${TARGET_HOST}${url.pathname}${url.search}${url.hash}`;
  console.log('[OnlyFans Redirect Fallback] MATCH:', details.url, '->', targetUrl);
  return { redirectUrl: targetUrl };
}

const filter = {
  urls: [`*://${STATIC2_HOST}/*`],
  types: [
    'main_frame', 'sub_frame', 'script', 'stylesheet',
    'image', 'font', 'xmlhttprequest', 'other'
  ]
};

chrome.webRequest.onBeforeRequest.addListener(
  webRequestFallbackListener,
  filter,
  ['blocking']
);

chrome.runtime.onStartup.addListener(async () => {
  console.log('[OnlyFans Redirect] onStartup');
  await installRules();
});

chrome.runtime.onInstalled.addListener(async (details) => {
  console.log('[OnlyFans Redirect] onInstalled:', details.reason);
  await installRules();
});

// Also log when background starts
console.log('[OnlyFans Redirect] BACKGROUND LOADED - registering listeners');
installRules();