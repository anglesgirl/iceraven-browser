const SOURCE_HOST = "static2.onlyfans.com";
const TARGET_HOST = "onlyfans.com";

function rewriteOnlyFansStaticUrl(url) {
  const parsed = new URL(url);
  if (parsed.protocol !== "https:" || parsed.hostname !== SOURCE_HOST) {
    return null;
  }

  parsed.hostname = TARGET_HOST;
  return parsed.href;
}

browser.webRequest.onBeforeRequest.addListener(
  (details) => {
    const redirectUrl = rewriteOnlyFansStaticUrl(details.url);
    return redirectUrl ? { redirectUrl } : {};
  },
  {
    urls: ["https://static2.onlyfans.com/*"],
    types: [
      "main_frame",
      "sub_frame",
      "stylesheet",
      "script",
      "image",
      "font",
      "object",
      "xmlhttprequest",
      "ping",
      "beacon",
      "media",
      "websocket",
      "csp_report",
      "imageset",
      "web_manifest",
      "speculative",
      "other"
    ]
  },
  ["blocking"]
);
