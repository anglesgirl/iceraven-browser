/* gate.js - AO3 Browser content script gate
 * Checks the translation enabled flag and injects the userscript into the page context.
 * Acts as the bridge between the page-context GM shim and the extension for GM_xmlhttpRequest.
 */
(function () {
  "use strict";

  const STORAGE_KEY = "ao3_translation_enabled";
  const INJECTED_FLAG = "__ao3_chinese_injected__";

  // Listen for GM_xmlhttpRequest messages from the page context (via gm-shim.js)
  window.addEventListener("message", function (event) {
    if (event.source !== window) return;
    const data = event.data;
    if (!data || data.type !== "AO3_GM_XHR") return;

    const id = data.id;
    const details = data.details || {};
    const url = details.url;
    if (!url) {
      window.postMessage({ type: "AO3_GM_XHR_RESP", id: id, error: "no url" }, "*");
      return;
    }

    const controller = new AbortController();
    const timeoutMs = details.timeout || 30000;
    const timer = setTimeout(() => controller.abort(), timeoutMs);

    fetch(url, {
      method: details.method || "GET",
      headers: details.headers || {},
      body: details.data || null,
      signal: controller.signal,
    })
      .then(async function (resp) {
        clearTimeout(timer);
        const text = await resp.text();
        window.postMessage(
          {
            type: "AO3_GM_XHR_RESP",
            id: id,
            response: {
              status: resp.status,
              statusText: resp.statusText,
              responseText: text,
              responseHeaders: "",
              finalUrl: resp.url,
            },
          },
          "*"
        );
      })
      .catch(function (err) {
        clearTimeout(timer);
        window.postMessage(
          { type: "AO3_GM_XHR_RESP", id: id, error: String(err && err.message || err) },
          "*"
        );
      });
  });

  // Check if translation is enabled, then inject scripts
  browser.storage.local.get(STORAGE_KEY).then(function (result) {
    // Default to enabled
    const enabled = result[STORAGE_KEY] !== false;
    if (!enabled) return;
    if (window[INJECTED_FLAG]) return;
    window[INJECTED_FLAG] = true;

    const base = browser.runtime.getURL("");
    const scripts = ["gm-shim.js", "zh-cn.js", "main.user.js"];
    scripts.forEach(function (name) {
      const s = document.createElement("script");
      s.src = base + name;
      s.async = false;
      s.onload = function () { s.remove(); };
      (document.head || document.documentElement).appendChild(s);
    });
  });
})();
