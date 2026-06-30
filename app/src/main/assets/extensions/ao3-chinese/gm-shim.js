/* gm-shim.js - GM API compatibility layer for page context
 * Provides GM_* APIs that the userscript expects from Tampermonkey.
 * Runs in the page context (injected as a <script> tag by gate.js).
 */
(function () {
  "use strict";

  const GM_STORAGE_PREFIX = "ao3_gm_";
  const xhrCallbacks = new Map();
  let xhrCounter = 0;

  // ---- Storage (synchronous, backed by localStorage) ----
  function GM_getValue(key, defaultValue) {
    try {
      const v = localStorage.getItem(GM_STORAGE_PREFIX + key);
      if (v === null) return defaultValue;
      return JSON.parse(v);
    } catch (e) {
      return defaultValue;
    }
  }

  function GM_setValue(key, value) {
    try {
      localStorage.setItem(GM_STORAGE_PREFIX + key, JSON.stringify(value));
    } catch (e) {}
  }

  function GM_deleteValue(key) {
    try {
      localStorage.removeItem(GM_STORAGE_PREFIX + key);
    } catch (e) {}
  }

  function GM_listValues() {
    const keys = [];
    try {
      for (let i = 0; i < localStorage.length; i++) {
        const k = localStorage.key(i);
        if (k && k.startsWith(GM_STORAGE_PREFIX)) {
          keys.push(k.substring(GM_STORAGE_PREFIX.length));
        }
      }
    } catch (e) {}
    return keys;
  }

  // ---- GM_xmlhttpRequest (async via postMessage bridge to gate.js) ----
  function GM_xmlhttpRequest(details) {
    const id = ++xhrCounter;
    xhrCallbacks.set(id, details);

    function onResp(event) {
      const data = event.data;
      if (!data || data.type !== "AO3_GM_XHR_RESP" || data.id !== id) return;
      window.removeEventListener("message", onResp);
      xhrCallbacks.delete(id);
      if (data.error) {
        if (details.onerror) details.onerror({ error: data.error });
      } else {
        if (details.onload) details.onload(data.response);
      }
    }
    window.addEventListener("message", onResp);

    window.postMessage({ type: "AO3_GM_XHR", id: id, details: details }, "*");
    return { abort: function () { xhrCallbacks.delete(id); window.removeEventListener("message", onResp); } };
  }

  // ---- GM_addStyle ----
  function GM_addStyle(css) {
    const style = document.createElement("style");
    style.textContent = css;
    (document.head || document.documentElement).appendChild(style);
    return style;
  }

  // ---- GM_registerMenuCommand / GM_unregisterMenuCommand ----
  const menuCommands = [];
  function GM_registerMenuCommand(name, fn, accessKey) {
    const id = menuCommands.length;
    menuCommands.push({ name: name, fn: fn });
    return id;
  }
  function GM_unregisterMenuCommand(id) {
    if (menuCommands[id]) menuCommands[id] = null;
  }

  // ---- GM_getResourceText / GM_getResourceURL ----
  function GM_getResourceText(name) {
    return "";
  }
  function GM_getResourceURL(name) {
    return "";
  }

  // ---- GM_setClipboard ----
  function GM_setClipboard(text, info) {
    try {
      navigator.clipboard.writeText(text);
    } catch (e) {}
  }

  // ---- GM_notification ----
  function GM_notification(details, ondone) {
    if (ondone) ondone();
  }

  // ---- GM_addElement ----
  function GM_addElement(parent, tag, attrs) {
    if (typeof parent === "string") { attrs = tag; tag = parent; parent = null; }
    const el = document.createElement(tag);
    if (attrs) for (const k in attrs) el.setAttribute(k, attrs[k]);
    (parent || document.head || document.documentElement).appendChild(el);
    return el;
  }

  // ---- GM_info ----
  const GM_info = {
    scriptHandler: "AO3BrowserGMShim",
    version: "1.0.0",
    script: {
      name: "AO3 Translator",
      version: "1.9.0",
      namespace: "https://github.com/V-Lipset/ao3-chinese",
      description: "AO3 Chinese translation",
      matches: [],
      includes: [],
      excludes: [],
      resources: {},
    },
  };

  // ---- Expose on window (page context) ----
  const target = window;
  target.GM_getValue = GM_getValue;
  target.GM_setValue = GM_setValue;
  target.GM_deleteValue = GM_deleteValue;
  target.GM_listValues = GM_listValues;
  target.GM_xmlhttpRequest = GM_xmlhttpRequest;
  target.GM_addStyle = GM_addStyle;
  target.GM_registerMenuCommand = GM_registerMenuCommand;
  target.GM_unregisterMenuCommand = GM_unregisterMenuCommand;
  target.GM_getResourceText = GM_getResourceText;
  target.GM_getResourceURL = GM_getResourceURL;
  target.GM_setClipboard = GM_setClipboard;
  target.GM_notification = GM_notification;
  target.GM_addElement = GM_addElement;
  target.GM_info = GM_info;
  target.GM = {
    getValue: GM_getValue,
    setValue: GM_setValue,
    deleteValue: GM_deleteValue,
    listValues: GM_listValues,
    xmlhttpRequest: GM_xmlhttpRequest,
    addStyle: GM_addStyle,
    registerMenuCommand: GM_registerMenuCommand,
    unregisterMenuCommand: GM_unregisterMenuCommand,
    getResourceText: GM_getResourceText,
    getResourceURL: GM_getResourceURL,
    setClipboard: GM_setClipboard,
    notification: GM_notification,
    addElement: GM_addElement,
    info: GM_info,
  };
  target.unsafeWindow = window;
})();
