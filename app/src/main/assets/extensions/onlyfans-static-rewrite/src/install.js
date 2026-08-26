/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

const STATIC_REWRITE_RULE_ID = "8d69e9d0-2bf8-4dfb-8e83-1ceae0010001";

const staticRewriteRule = {
    uuid: STATIC_REWRITE_RULE_ID,
    pattern: {
        scheme: "*",
        host: "static2.onlyfans.com",
        path: "",
    },
    types: ["script", "stylesheet", "image", "font", "media", "xmlhttprequest", "other"],
    action: "redirect",
    redirectUrl: "https://onlyfans.com{pathname}{search}{hash}",
    active: true,
    title: "OnlyFans static resources",
    description: "Redirect static2.onlyfans.com resources to onlyfans.com.",
};

async function ensureBundledRules() {
    const { rules = [] } = await browser.storage.local.get("rules");
    if (rules.some((rule) => rule.uuid === STATIC_REWRITE_RULE_ID)) {
        return;
    }

    await browser.storage.local.set({ rules: [...rules, staticRewriteRule] });
}

browser.runtime.onInstalled.addListener(() => {
    ensureBundledRules().catch((error) => console.error("Failed to initialize bundled rewrite rule", error));
});

ensureBundledRules().catch((error) => console.error("Failed to initialize bundled rewrite rule", error));
