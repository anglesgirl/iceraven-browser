(function () {
  'use strict';

  function headersToObject(headers) {
    var result = {};
    headers.forEach(function (value, key) { result[key] = value; });
    return result;
  }

  function headersToString(headers) {
    var lines = [];
    headers.forEach(function (value, key) { lines.push(key + ': ' + value); });
    return lines.join('\r\n');
  }

  async function handleRequest(options) {
    var controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
    var timeoutId = null;
    if (controller && options.timeout) {
      timeoutId = setTimeout(function () { controller.abort(); }, options.timeout);
    }

    try {
      var response = await fetch(options.url, {
        method: options.method || 'GET',
        headers: options.headers || {},
        body: options.data,
        credentials: options.anonymous ? 'omit' : 'include',
        signal: controller ? controller.signal : undefined,
      });
      var text = await response.text();
      var parsed = null;
      if (options.responseType === 'json') {
        try { parsed = JSON.parse(text); } catch (e) { parsed = null; }
      }
      return {
        ok: true,
        status: response.status,
        statusText: response.statusText,
        finalUrl: response.url,
        responseText: text,
        response: options.responseType === 'json' ? parsed : text,
        responseHeaders: headersToString(response.headers),
        headers: headersToObject(response.headers),
      };
    } finally {
      if (timeoutId) clearTimeout(timeoutId);
    }
  }

  async function handleDownload(options) {
    if (!browser.downloads || !browser.downloads.download) {
      throw new Error('downloads API is not available');
    }
    return browser.downloads.download({
      url: options.url,
      filename: options.name,
      saveAs: options.saveAs !== false,
    });
  }

  browser.runtime.onMessage.addListener(function (message) {
    if (!message || !message.type) return undefined;
    if (message.type === 'GM_XMLHTTPREQUEST') return handleRequest(message.options || {});
    if (message.type === 'GM_DOWNLOAD') return handleDownload(message.options || {});
    if (message.type === 'GM_NOTIFICATION') {
      if (browser.notifications && browser.notifications.create) {
        return browser.notifications.create('', {
          type: 'basic',
          title: message.title || 'AO3 中文翻译',
          message: message.text || '',
        });
      }
    }
    return undefined;
  });
})();
