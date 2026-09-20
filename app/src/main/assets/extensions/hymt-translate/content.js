// HyMT 在线翻译 - 接管翻译入口 + 常驻悬浮按钮
(function(){
  function showPanel(title, body, isError) {
    let panel = document.getElementById("hymt-panel");
    if (!panel) {
      panel = document.createElement("div");
      panel.id = "hymt-panel";
      panel.style = "position:fixed;bottom:90px;right:20px;z-index:2147483646;width:min(320px,80vw);max-height:55vh;overflow:auto;background:#fff;color:#111;border-radius:14px;box-shadow:0 8px 24px rgba(0,0,0,.35);padding:14px;font-size:14px;line-height:1.6;font-family:system-ui";
      panel.innerHTML = '<div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px"><b id="hymt-panel-title"></b><button id="hymt-panel-close" style="border:none;background:none;font-size:18px;cursor:pointer">✕</button></div><div id="hymt-panel-body" style="white-space:pre-wrap;word-break:break-word"></div>';
      document.documentElement.appendChild(panel);
      panel.querySelector("#hymt-panel-close").onclick = () => panel.style.display = "none";
    }
    panel.querySelector("#hymt-panel-title").textContent = title;
    const bodyEl = panel.querySelector("#hymt-panel-body");
    bodyEl.textContent = body;
    bodyEl.style.color = isError ? "#c62828" : "#111";
    panel.style.display = "block";
  }

  async function doTranslate(engine, from, to, text) {
    if (!text || !text.trim()) { showPanel("翻译", "没有可翻译的内容（请先选中文字）", true); return; }
    showPanel("翻译", "翻译中…", false);
    try {
      const res = await browser.runtime.sendMessage({action:"translate", engine, from, to, text});
      if (res && res.ok) {
        const engineName = res.engine === "microsoft" ? "微软" : res.engine === "google" ? "谷歌" : res.engine === "mymemory" ? "MyMemory" : res.engine === "cache" ? "缓存" : res.engine === "batch" ? "批量" : "AI";
        showPanel(engineName + " 翻译", res.translated, false);
      } else if (res && res.needConfig) {
        showPanel("AI 翻译", "未配置 AI API。请在扩展 storage 中设置 hymt_api_url / hymt_api_key（OpenAI 兼容格式，可接腾讯混元）。", true);
      } else if (res && res.error) {
        showPanel("翻译", "翻译失败: " + res.error, true);
      } else {
        showPanel("翻译", "翻译失败: 未知错误", true);
      }
    } catch(e) {
      showPanel("翻译", "翻译失败: " + e, true);
    }
  }

  // 监听 Kotlin 注入的 hymt-trigger
  window.addEventListener("hymt-trigger", async (e) => {
    const detail = e.detail || {};
    const from = detail.from || document.documentElement.lang || "auto";
    const to = detail.to || "zh";
    const text = detail.text || document.body.innerText.slice(0, 4000);
    doTranslate(detail.engine || "google", from, to, text);
  });

  // 响应 popup / 扩展消息
  browser.runtime.onMessage.addListener((msg, sender, sendResponse) => {
    if (msg && msg.action === "translate-page") {
      const selected = window.getSelection().toString().trim();
      const text = msg.text || selected || document.body.innerText.slice(0, 4000);
      doTranslate(msg.engine || "google", msg.from || "auto", msg.to || "zh", text);
    }
  });

  // 常驻悬浮翻译按钮
  function ensureBar(){
    if (document.getElementById("hymt-bar")) return;
    const bar = document.createElement("div");
    bar.id = "hymt-bar";
    bar.style = "position:fixed;bottom:20px;right:20px;z-index:2147483647;display:block";
    bar.innerHTML = '<button id="hymt-float" style="padding:12px 18px;border-radius:24px;background:#111;color:#fff;border:none;box-shadow:0 4px 12px rgba(0,0,0,.4);font-size:14px;font-weight:600">🌐 翻译</button><div id="hymt-menu" style="display:none;margin-top:8px;background:#fff;border-radius:12px;box-shadow:0 4px 12px rgba(0,0,0,.2);overflow:hidden"><button data-e="google" style="display:block;width:100%;padding:10px 16px;border:none;background:#fff;text-align:left">谷歌译</button><button data-e="hymt" style="display:block;width:100%;padding:10px 16px;border:none;background:#fff;text-align:left">AI 混元翻译</button></div>';
    document.documentElement.appendChild(bar);
    const btn = bar.querySelector("#hymt-float");
    const menu = bar.querySelector("#hymt-menu");
    btn.onclick = () => menu.style.display = menu.style.display==="none" ? "block" : "none";
    menu.onclick = (e) => {
      const engine = e.target.dataset.e;
      if (!engine) return;
      menu.style.display="none";
      const selected = window.getSelection().toString().trim();
      const text = selected || document.body.innerText.slice(0, 4000);
      const from = selected ? "auto" : (document.documentElement.lang || "auto");
      doTranslate(engine, from, "zh", text);
    };
    // 选中文字时高亮按钮
    document.addEventListener("selectionchange", () => {
      const t = window.getSelection().toString().trim();
      btn.style.background = t.length > 1 ? "#00c853" : "#111";
    });
  }
  if (document.readyState==="loading") document.addEventListener("DOMContentLoaded", ensureBar);
  else ensureBar();
  // SPA 路由变化时重建
  let lastUrl = location.href;
  setInterval(()=>{ if(location.href!==lastUrl){ lastUrl=location.href; ensureBar(); }}, 1000);
})();
