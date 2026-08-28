/**
 * OnlyFans Static2 Redirect - MV3 WebExtension (declarativeNetRequest)
 * Redirects static2.onlyfans.com/* to onlyfans.com/* preserving path
 * Target: onlyfans.com has ECH via Cloudflare, static2 does not
 */

const STATIC2_HOST = 'static2.onlyfans.com';
const TARGET_HOST = 'onlyfans.com';
const RULESET_ID = 'onlyfans_static2_redirect';

async function buildRedirectRules() {
  const rules = [];
  let ruleId = 1;

  // Match all resource types: main_frame, sub_frame, script, stylesheet, image, font, xmlhttprequest, other
  const resourceTypes = [
    'main_frame',
    'sub_frame',
    'stylesheet',
    'script',
    'image',
    'font',
    'xmlhttprequest',
    'other'
  ];

  // Redirect all paths on static2.onlyfans.com to onlyfans.com
  // Using regexFilter for flexible matching
  rules.push({
    id: ruleId++,
    priority: 1,
    action: {
      type: 'redirect',
      redirect: {
        regexSubstitution: `https://${TARGET_HOST}/\\1`
      }
    },
    condition: {
      regexFilter: `^https?://${STATIC2_HOST}/(.*)`,
      resourceTypes: resourceTypes
    }
  });

  return rules;
}

async function updateRules() {
  try {
    const rules = await buildRedirectRules();
    
    // Get existing dynamic rules to remove them
    const existingRules = await chrome.declarativeNetRequest.getDynamicRules();
    const removeRuleIds = existingRules.map(r => r.id);
    
    await chrome.declarativeNetRequest.updateDynamicRules({
      removeRuleIds: removeRuleIds,
      addRules: rules
    });
    
    console.log(`[OnlyFans Redirect] Updated ${rules.length} declarativeNetRequest rules`);
  } catch (error) {
    console.error('[OnlyFans Redirect] Failed to update rules:', error);
  }
}

// Initialize on startup
chrome.runtime.onStartup.addListener(updateRules);

// Initialize on install/update
chrome.runtime.onInstalled.addListener(async (details) => {
  console.log('[OnlyFans Redirect] Installed/updated:', details.reason);
  await updateRules();
});

console.log('[OnlyFans Redirect] Background script loaded');