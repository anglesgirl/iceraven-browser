/**
 * OnlyFans Static2 Redirect - MV3 WebExtension
 * Uses declarativeNetRequest (static rules) + webRequestBlocking fallback
 * Redirects static2.onlyfans.com/* to onlyfans.com/* preserving path
 */

const STATIC2_HOST = 'static2.onlyfans.com';
const TARGET_HOST = 'onlyfans.com';

// Static declarativeNetRequest rules (loaded from manifest.json "declarative_net_request" key)
// This is the primary mechanism - MV3 requires static rules for reliability

// Fallback: webRequestBlocking for older GeckoView versions that don't support DNR properly
function webRequestFallbackListener(details) {
  const url = new URL(details.url);
  
  if (url.hostname !== STATIC2_HOST) {
    return {};
  }
  
  const targetUrl = `${url.protocol}//${TARGET_HOST}${url.pathname}${url.search}${url.hash}`;
  console.log(`[OnlyFans Redirect Fallback] ${details.url} -> ${targetUrl}`);
  
  return { redirectUrl: targetUrl };
}

const filter = {
  urls: [`*://${STATIC2_HOST}/*`],
  types: [
    'main_frame',
    'sub_frame',
    'script',
    'stylesheet',
    'image',
    'font',
    'xmlhttprequest',
    'other'
  ]
};

// Register fallback listener (only used if DNR doesn't work)
chrome.webRequest.onBeforeRequest.addListener(
  webRequestFallbackListener,
  filter,
  ['blocking']
);

// Log startup
console.log('[OnlyFans Static2 Redirect] Background loaded - DNR static rules + webRequest fallback active');