/**
 * OnlyFans Static2 Redirect - MV3 WebExtension
 * Redirects static2.onlyfans.com/* to onlyfans.com/* preserving path
 * Target: onlyfans.com has ECH via Cloudflare, static2 does not
 */

const STATIC2_HOST = 'static2.onlyfans.com';
const TARGET_HOST = 'onlyfans.com';

function redirectListener(details) {
  const url = new URL(details.url);
  
  // Only handle static2.onlyfans.com
  if (url.hostname !== STATIC2_HOST) {
    return {};
  }
  
  // Build target URL: keep path, query, fragment; change hostname
  const targetUrl = `${url.protocol}//${TARGET_HOST}${url.pathname}${url.search}${url.hash}`;
  
  console.log(`[OnlyFans Redirect] ${details.url} -> ${targetUrl}`);
  
  return { redirectUrl: targetUrl };
}

// Register listener for main_frame, sub_frame, script, stylesheet, image, font, xmlhttprequest, other
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

chrome.webRequest.onBeforeRequest.addListener(
  redirectListener,
  filter,
  ['blocking']
);

console.log('[OnlyFans Static2 Redirect] Background script loaded');