// background.js
const cache = new Map();
const CACHE_MAX = 3000;

// [新增] 速率限制：两次翻译请求之间最少间隔 120ms（约 8 次/秒），
// 避免一次翻译整章时连发几十个请求触发 Google 的非官方接口限流。
let lastFetchAt = 0;
const RATE_LIMIT_MS = 120;

function cacheGet(key) {
  // [FIX] 原来 cache.get(key) 在 key 不存在时返回 undefined，在存在但值为
  // 空字符串时也返回 ""（falsy）。调用方用 `if (hit) return hit` 会把空字符串
  // 误判为 cache miss，导致空译文被反复重新请求。
  // 改成：用 has() 判断是否命中，命中就返回值（哪怕是 ""），未命中返回 undefined。
  return cache.has(key) ? cache.get(key) : undefined;
}

function cacheSet(key, value) {
  cache.set(key, value);
  if (cache.size > CACHE_MAX) {
    // 超出上限时删除最早插入的一条（简易 FIFO 淘汰）
    const firstKey = cache.keys().next().value;
    cache.delete(firstKey);
  }
}

async function translateWithGoogle(text, sourceLang = "auto", targetLang = "zh-CN") {
  // [新增] 超长文本保护：Google 非官方接口通过 URL 传参，单次请求上限约 5000 字符。
  // 超过时直接返回空字符串，而不是发出注定失败的请求。
  const MAX_TEXT_LEN = 4500;
  if (!text || text.length > MAX_TEXT_LEN) return "";

  const key = `${sourceLang}|${targetLang}|${text}`;
  const hit = cacheGet(key);
  // [FIX] 原来 `if (hit) return hit`，现在改为 !== undefined，
  // 这样空字符串的缓存命中也能被正确识别并返回，不会重新请求。
  if (hit !== undefined) return hit;

  // [新增] 速率限制：距离上次请求不足 RATE_LIMIT_MS 时等待差值
  const now = Date.now();
  const waitMs = RATE_LIMIT_MS - (now - lastFetchAt);
  if (waitMs > 0) await new Promise(r => setTimeout(r, waitMs));
  lastFetchAt = Date.now();

  const url = new URL("https://translate.googleapis.com/translate_a/single");
  url.searchParams.set("client", "gtx");
  url.searchParams.set("sl", sourceLang);
  url.searchParams.set("tl", targetLang);
  url.searchParams.set("dt", "t");
  url.searchParams.set("q", text);

  const res = await fetch(url.toString(), { method: "GET" });
  if (!res.ok) throw new Error(`Translate request failed: ${res.status}`);

  const data = await res.json();
  const translated = Array.isArray(data?.[0])
    ? data[0].map(part => part?.[0] || "").join("")
    : "";

  cacheSet(key, translated);
  return translated;
}

chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  (async () => {
    try {
      if (msg?.type === "AO3_TRANSLATE_TEXT") {
        const { text, sourceLang, targetLang } = msg.payload || {};
        if (!text || typeof text !== "string") {
          throw new Error("No text to translate");
        }

        const result = await translateWithGoogle(
          text,
          sourceLang || "auto",
          targetLang || "zh-CN"
        );

        sendResponse({ ok: true, data: result });
        return;
      }

      sendResponse({ ok: false, error: "Unknown message type" });
    } catch (err) {
      sendResponse({ ok: false, error: String(err?.message || err) });
    }
  })();

  return true;
});
