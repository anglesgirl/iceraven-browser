// HyMT 混元翻译 - 在线翻译版 v0.2.1
// 三引擎免费降级链（移植自 CO3/main/web/translate）：
//   Microsoft Edge（国内最稳，天然支持批量）→ Google legacy（走浏览器网络/ECH）→ MyMemory（兜底，匿名日 5000 字符）
// AI 引擎可配置：storage 设置 hymt_api_url / hymt_api_key / hymt_api_model（OpenAI 兼容，可接腾讯混元）

const MICROSOFT_API = "https://edge.microsoft.com/translate/translatetext";
const GOOGLE_LEGACY_API = "https://translate.googleapis.com/translate_a/single";
const MYMEMORY_API = "https://api.mymemory.translated.net/get";
const FETCH_TIMEOUT = 15000;

const LANGS = {
  "zh": "zh-CN", "zh-CN": "zh-CN", "zh-TW": "zh-TW", "zh-Hans": "zh-CN", "zh-Hant": "zh-TW",
  "en": "en", "en-US": "en", "en-GB": "en",
  "ja": "ja", "ko": "ko", "fr": "fr", "de": "de", "es": "es", "ru": "ru", "it": "it",
  "pt": "pt-PT", "pt-BR": "pt-BR", "ar": "ar", "hi": "hi", "vi": "vi", "th": "th",
  "id": "id", "tr": "tr", "nl": "nl", "pl": "pl", "uk": "uk", "auto": "zh-CN"
};

function normalizeLang(lang) {
  if (!lang) return "zh-CN";
  return LANGS[lang] || LANGS[lang.split("-")[0]] || "zh-CN";
}

function fetchWithTimeout(url, init = {}) {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), FETCH_TIMEOUT);
  return fetch(url, { ...init, signal: ctrl.signal }).finally(() => clearTimeout(timer));
}

// Microsoft Edge 免费接口：POST JSON 数组
async function translateMicrosoft(text, from, to) {
  const url = `${MICROSOFT_API}?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}&isEnterpriseClient=false`;
  const res = await fetchWithTimeout(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify([text]),
  });
  if (!res.ok) throw new Error("Microsoft HTTP " + res.status);
  const arr = await res.json();
  const out = arr && arr[0] && arr[0].translations && arr[0].translations[0] && arr[0].translations[0].text;
  if (!out) throw new Error("Microsoft 返回为空");
  return out;
}

// Google legacy 接口：GET single?client=gtx
async function translateGoogle(text, from, to) {
  const url = `${GOOGLE_LEGACY_API}?client=gtx&sl=${encodeURIComponent(from)}&tl=${encodeURIComponent(to)}&dt=t&q=${encodeURIComponent(text)}`;
  const res = await fetchWithTimeout(url);
  if (!res.ok) throw new Error("Google HTTP " + res.status);
  const data = await res.json();
  const segs = data && data[0];
  if (!Array.isArray(segs)) throw new Error("Google 返回格式异常");
  const out = segs.map(s => (Array.isArray(s) && typeof s[0] === "string" ? s[0] : "")).join("");
  if (!out) throw new Error("Google 返回为空");
  return out;
}

// MyMemory 兜底
async function translateMyMemory(text, from, to) {
  const url = `${MYMEMORY_API}?q=${encodeURIComponent(text)}&langpair=${from}|${to}`;
  const res = await fetchWithTimeout(url);
  if (!res.ok) throw new Error("MyMemory HTTP " + res.status);
  const data = await res.json();
  const out = data.responseData && data.responseData.translatedText;
  if (!out) throw new Error((data.responseStatus || "") + ": " + (data.responseDetails || "no result"));
  return out;
}

// 降级链：Google gtx 走浏览器 ECH 通道（质量最好，CO3 主路径）→ Microsoft（国内直连稳）→ MyMemory 兜底
const CHAIN = [
  ["google", translateGoogle],
  ["microsoft", translateMicrosoft],
  ["mymemory", translateMyMemory],
];

const BATCH_CHARS = 1200;   // GET URL 长度限制（CO3 经验值）
const CONCURRENCY = 3;

// 翻译缓存：避免重复翻译同一段
async function cacheGet(key) {
  try {
    const r = await browser.storage.local.get("tr_cache_" + key);
    return r["tr_cache_" + key] || null;
  } catch { return null; }
}
async function cacheSet(key, val) {
  try { await browser.storage.local.set({ ["tr_cache_" + key]: val }); } catch {}
}
function djb2(s) {
  let h = 5381;
  for (let i = 0; i < s.length; i++) h = ((h << 5) + h + s.charCodeAt(i)) | 0;
  return (h >>> 0).toString(36);
}

async function mapLimit(items, limit, fn) {
  const out = new Array(items.length);
  let i = 0;
  const workers = Array.from({ length: Math.min(limit, items.length) }, async () => {
    while (i < items.length) {
      const idx = i++;
      out[idx] = await fn(items[idx], idx);
    }
  });
  await Promise.all(workers);
  return out;
}

async function translateOneEngine(text, from, to) {
  let lastError = null;
  for (const [name, fn] of CHAIN) {
    try {
      const out = await fn(text, from, to);
      if (out && out.trim()) return { text: out, engine: name };
      throw new Error(name + " 返回空译文");
    } catch (e) {
      lastError = e;
      console.log("[HyMT] 引擎", name, "失败，降级：", e.message);
    }
  }
  throw lastError || new Error("全部翻译引擎失败");
}

// 长文按段落切批，3 路并发；空译文回退原文（CO3 坑：Google 短文本可能返回空串）
async function translateFree(text, from, to) {
  const cacheKey = djb2(from + "|" + to + "|" + text);
  const cached = await cacheGet(cacheKey);
  if (cached) return { text: cached, engine: "cache" };

  if (text.length <= BATCH_CHARS) {
    const r = await translateOneEngine(text, from, to);
    await cacheSet(cacheKey, r.text);
    return r;
  }
  // 长文：按双换行切段，凑批
  const parts = [];
  let buf = "";
  for (const para of text.split(/\n{2,}/)) {
    if ((buf + para).length > BATCH_CHARS && buf) { parts.push(buf); buf = ""; }
    buf += (buf ? "\n\n" : "") + para;
  }
  if (buf) parts.push(buf);
  const done = await mapLimit(parts, CONCURRENCY, p => translateOneEngine(p, from, to).then(r => r.text));
  const out = done.join("\n\n");
  await cacheSet(cacheKey, out);
  return { text: out, engine: "batch" };
}

// AI 引擎：OpenAI 兼容（腾讯混元 / DeepSeek 等）
async function translateAI(text, from, to) {
  const cfg = await browser.storage.local.get(["hymt_api_url", "hymt_api_key", "hymt_api_model"]);
  if (!cfg.hymt_api_url) return { needConfig: true };
  const model = cfg.hymt_api_model || "hunyuan";
  const headers = { "Content-Type": "application/json" };
  if (cfg.hymt_api_key) headers["Authorization"] = "Bearer " + cfg.hymt_api_key;
  const prompt = `Translate the following text from ${from} to ${to}. Reply with the translation only.\n\n${text}`;
  const res = await fetch(cfg.hymt_api_url, {
    method: "POST",
    headers,
    body: JSON.stringify({ model, messages: [{ role: "user", content: prompt }] }),
  });
  if (!res.ok) throw new Error("AI HTTP " + res.status);
  const data = await res.json();
  const out = data.choices && data.choices[0] && data.choices[0].message && data.choices[0].message.content;
  if (!out) throw new Error("AI 返回无内容");
  return { text: out.trim(), engine: "hymt" };
}

browser.runtime.onMessage.addListener(async (msg, sender) => {
  if (msg.action === "translate") {
    const from = normalizeLang(msg.from || "auto");
    const to = normalizeLang(msg.to || "zh-CN");
    const text = (msg.text || "").slice(0, 4000);
    try {
      if (msg.engine === "hymt") {
        const r = await translateAI(text, from, to);
        if (r.needConfig) return { ok: false, needConfig: true };
        return { ok: true, translated: r.text, engine: r.engine };
      }
      const r = await translateFree(text, from, to);
      return { ok: true, translated: r.text, engine: r.engine };
    } catch (e) {
      console.error("[HyMT]", e);
      return { ok: false, error: String((e && e.message) || e) };
    }
  }
  return { ok: false, error: "unknown action" };
});
