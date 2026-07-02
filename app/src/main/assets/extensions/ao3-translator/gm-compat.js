(function () {
  'use strict';

  var RESOURCE_URLS = {
    vIcon: 'https://cdn.jsdelivr.net/gh/V-Lipset/ao3-chinese@main/assets/icon.png',
    santaHat: 'https://cdn.jsdelivr.net/gh/V-Lipset/ao3-chinese@main/assets/santa%20hat.png',
  };
  var STORAGE_PREFIX = 'ao3_translator_';
  var listeners = {};
  var listenerId = 1;

  function encode(value) {
    return JSON.stringify(value);
  }

  function decode(value, fallback) {
    if (value === null || typeof value === 'undefined') return fallback;
    try { return JSON.parse(value); } catch (e) { return fallback; }
  }

  function storageKey(key) {
    return STORAGE_PREFIX + key;
  }

  window.GM_getValue = function (key, defaultValue) {
    return decode(window.localStorage.getItem(storageKey(key)), defaultValue);
  };

  window.GM_setValue = function (key, value) {
    var oldValue = window.GM_getValue(key);
    window.localStorage.setItem(storageKey(key), encode(value));
    Object.keys(listeners).forEach(function (id) {
      var item = listeners[id];
      if (item && item.key === key) {
        try { item.callback(key, oldValue, value, false); } catch (e) { console.error(e); }
      }
    });
  };

  window.GM_deleteValue = function (key) {
    var oldValue = window.GM_getValue(key);
    window.localStorage.removeItem(storageKey(key));
    Object.keys(listeners).forEach(function (id) {
      var item = listeners[id];
      if (item && item.key === key) {
        try { item.callback(key, oldValue, undefined, false); } catch (e) { console.error(e); }
      }
    });
  };

  window.GM_addValueChangeListener = function (key, callback) {
    var id = listenerId++;
    listeners[id] = { key: key, callback: callback };
    return id;
  };

  window.GM_removeValueChangeListener = function (id) {
    delete listeners[id];
  };

  window.GM_addStyle = function (css) {
    var style = document.createElement('style');
    style.textContent = css;
    (document.head || document.documentElement).appendChild(style);
    return style;
  };

  window.GM_getResourceURL = function (name) {
    return RESOURCE_URLS[name] || '';
  };

  window.GM_registerMenuCommand = function () {
    return listenerId++;
  };

  window.GM_unregisterMenuCommand = function () {};

  window.GM_notification = function (details, title, image, onclick) {
    var payload = typeof details === 'string' ? { text: details, title: title || 'AO3 中文翻译' } : (details || {});
    if (browser.runtime && browser.runtime.sendMessage) {
      browser.runtime.sendMessage({
        type: 'GM_NOTIFICATION',
        title: payload.title || 'AO3 中文翻译',
        text: payload.text || payload.message || '',
      }).catch(function () {});
    }
    if (typeof payload.onclick === 'function') payload.onclick();
    if (typeof onclick === 'function') onclick();
  };

  window.GM_download = function (options) {
    browser.runtime.sendMessage({ type: 'GM_DOWNLOAD', options: options || {} }).then(function () {
      if (options && typeof options.onload === 'function') options.onload();
    }).catch(function (error) {
      if (options && typeof options.onerror === 'function') options.onerror(error);
    });
  };

  // [FIX] GeckoView built-in extensions may not support browser.runtime.sendMessage
  // between content scripts and background scripts reliably. Instead of forwarding
  // GM_xmlhttpRequest to the background script, we perform the request directly in
  // the content script using XMLHttpRequest. In Firefox content scripts, XHR uses
  // the extension's host_permissions to bypass CORS (unlike fetch which uses the
  // page origin). If direct XHR fails, fall back to background messaging.
  function gmXhrDirect(options) {
    return new Promise(function (resolve, reject) {
      var xhr = new XMLHttpRequest();
      xhr.open(options.method || 'GET', options.url, true);
      if (options.headers) {
        Object.keys(options.headers).forEach(function (k) {
          try { xhr.setRequestHeader(k, options.headers[k]); } catch (e) {}
        });
      }
      if (options.responseType === 'json') {
        xhr.responseType = 'text';
      } else if (options.responseType && ['arraybuffer', 'blob', 'document'].indexOf(options.responseType) >= 0) {
        xhr.responseType = options.responseType;
      }
      if (options.timeout) xhr.timeout = options.timeout;
      xhr.onreadystatechange = function () {
        if (xhr.readyState !== 4) return;
        var text = xhr.responseType === 'arraybuffer' || xhr.responseType === 'blob' ? xhr.response : xhr.responseText;
        var parsed = null;
        if (options.responseType === 'json' && typeof text === 'string') {
          try { parsed = JSON.parse(text); } catch (e) { parsed = null; }
        }
        resolve({
          status: xhr.status,
          statusText: xhr.statusText || '',
          finalUrl: xhr.responseURL || options.url,
          responseText: typeof text === 'string' ? text : '',
          response: options.responseType === 'json' ? parsed : text,
          responseHeaders: xhr.getAllResponseHeaders(),
        });
      };
      xhr.ontimeout = function () { reject(new Error('timeout')); };
      xhr.onerror = function () { reject(new Error('network error')); };
      try {
        xhr.send(options.data && options.method !== 'GET' && options.method !== 'HEAD' ? options.data : null);
      } catch (e) {
        reject(e);
      }
    });
  }

  function gmFetchViaBackground(options) {
    return browser.runtime.sendMessage({ type: 'GM_XMLHTTPREQUEST', options: options });
  }

  window.GM_xmlhttpRequest = function (options) {
    options = options || {};
    gmXhrDirect(options).then(function (result) {
      if (typeof options.onload === 'function') options.onload(result);
    }).catch(function (directError) {
      // Direct fetch failed; try background script as fallback
      if (typeof browser !== 'undefined' && browser.runtime && browser.runtime.sendMessage) {
        gmFetchViaBackground(options).then(function (response) {
          if (options.responseType === 'json' && response && typeof response.response === 'undefined') {
            try { response.response = JSON.parse(response.responseText); } catch (e) { response.response = null; }
          }
          if (typeof options.onload === 'function') options.onload(response);
        }).catch(function (error) {
          if (error && String(error).toLowerCase().includes('abort') && typeof options.ontimeout === 'function') {
            options.ontimeout(error);
          } else if (typeof options.onerror === 'function') {
            options.onerror(error);
          }
        });
      } else if (typeof options.onerror === 'function') {
        options.onerror(directError);
      }
    });
  };

  window.GM_info = {
    script: {
      name: 'AO3 Translator',
      namespace: 'https://github.com/V-Lipset/ao3-chinese',
      version: '1.9.0-2026-05-25',
      description: '为 AO3 打造的中文阅读体验增强工具，支持 UI 界面汉化与多种翻译服务的实时内容翻译。',
    },
    scriptHandler: 'AO3 Browser Built-in Extension',
    version: '1.0.0',
  };
})();

var GM_getValue = window.GM_getValue;
var GM_setValue = window.GM_setValue;
var GM_deleteValue = window.GM_deleteValue;
var GM_addValueChangeListener = window.GM_addValueChangeListener;
var GM_removeValueChangeListener = window.GM_removeValueChangeListener;
var GM_addStyle = window.GM_addStyle;
var GM_getResourceURL = window.GM_getResourceURL;
var GM_registerMenuCommand = window.GM_registerMenuCommand;
var GM_unregisterMenuCommand = window.GM_unregisterMenuCommand;
var GM_notification = window.GM_notification;
var GM_download = window.GM_download;
var GM_xmlhttpRequest = window.GM_xmlhttpRequest;
var GM_info = window.GM_info;
