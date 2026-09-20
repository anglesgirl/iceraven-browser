// HyMT 混元翻译 - 在线翻译版
// 默认走 MyMemory（免费、无 key、国内可达）
// AI 引擎可配置：在 storage 里设置 hymt_api_url / hymt_api_key / hymt_api_model（OpenAI 兼容格式）
//   例如腾讯混元: https://api.hunyuan.cloud.tencent.com/v1/chat/completions

const MYMEMORY_API = "https://api.mymemory.translated.net/get";

const LANGS = {
  "zh": "zh-CN", "zh-CN": "zh-CN", "zh-TW": "zh-TW", "zh-Hans": "zh-CN", "zh-Hant": "zh-TW",
  "en": "en-GB", "en-US": "en-GB", "en-GB": "en-GB",
  "ja": "ja", "ko": "ko", "fr": "fr", "de": "de", "es": "es", "ru": "ru", "it": "it",
  "pt": "pt-PT", "pt-BR": "pt-BR", "ar": "ar", "hi": "hi", "vi": "vi", "th": "th",
  "id": "id", "tr": "tr", "nl": "nl", "pl": "pl", "uk": "uk", "auto": "zh-CN"
};

function normalizeLang(lang) {
  if (!lang) return "zh-CN";
  return LANGS[lang] || LANGS[lang.split("-")[0]] || "zh-CN";
}

async function translateMyMemory(text, from, to) {
  const url = `${MYMEMORY_API}?q=${encodeURIComponent(text.slice(0, 4000))}&langpair=${from}|${to}`;
  const res = await fetch(url);
  if (!res.ok) throw new Error("MyMemory HTTP " + res.status);
  const data = await res.json();
  const translated = data.responseData && data.responseData.translatedText;
  if (!translated) throw new Error(data.responseStatus + ": " + (data.responseDetails || "no result"));
  return translated;
}

async function translateAI(text, from, to) {
  const cfg = await browser.storage.local.get(["hymt_api_url", "hymt_api_key", "hymt_api_model"]);
  if (!cfg.hymt_api_url) return { ok: false, needConfig: true };
  const model = cfg.hymt_api_model || "hunyuan";
  const headers = { "Content-Type": "application/json" };
  if (cfg.hymt_api_key) headers["Authorization"] = "Bearer " + cfg.hymt_api_key;
  const prompt = `Translate the following text from ${from} to ${to}. Reply with the translation only.\n\n${text.slice(0, 4000)}`;
  const res = await fetch(cfg.hymt_api_url, {
    method: "POST",
    headers,
    body: JSON.stringify({ model, messages: [{ role: "user", content: prompt }] }),
  });
  if (!res.ok) throw new Error("AI API HTTP " + res.status);
  const data = await res.json();
  const translated = data.choices && data.choices[0] && data.choices[0].message && data.choices[0].message.content;
  if (!translated) throw new Error("AI API: no content in response");
  return translated.trim();
}

browser.runtime.onMessage.addListener(async (msg, sender) => {
  if (msg.action === "translate") {
    const from = normalizeLang(msg.from || "auto");
    const to = normalizeLang(msg.to || "zh-CN");
    try {
      if (msg.engine === "hymt") {
        const r = await translateAI(msg.text, from, to);
        if (r && r.needConfig) return { ok: false, needConfig: true };
        return { ok: true, translated: r, engine: "hymt" };
      }
      // google / 默认
      const translated = await translateMyMemory(msg.text, from, to);
      return { ok: true, translated, engine: "google" };
    } catch (e) {
      console.error("[HyMT]", e);
      return { ok: false, error: String(e && e.message || e) };
    }
  }
  return { ok: false, error: "unknown action" };
});
