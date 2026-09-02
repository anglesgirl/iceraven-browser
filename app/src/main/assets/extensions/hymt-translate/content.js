// HyMT 接管原生翻译入口 + 常驻悬浮按钮
(function(){
  // 监听 Kotlin 注入的 hymt-trigger
  window.addEventListener("hymt-trigger", async (e) => {
    const detail = e.detail || {};
    const from = detail.from || document.documentElement.lang || "auto";
    const to = detail.to || "zh";
    console.log("[HyMT] trigger", from, "->", to);
    try {
      const res = await browser.runtime.sendMessage({action:"translate", engine:"hymt", from, to, text: document.body.innerText.slice(0,4000)});
      if (res && res.needDownload) {
        if (confirm("HyMT 模型未下载，是否现在下载？(1.25bit ~800MB / 2bit ~1.2GB 走 ModelScope 国内CDN)")) {
          browser.runtime.sendMessage({action:"download", model:"1.25bit"});
        }
        return;
      }
      if (res && res.note) console.log("[HyMT]", res.note);
    } catch(e){ console.log("[HyMT] bg error", e); }
    document.body.style.border = "2px solid #00c853";
    setTimeout(()=>document.body.style.border="", 1500);
  });

  // 常驻悬浮翻译按钮（不依赖选中，永远可见）
  function ensureBar(){
    if (document.getElementById("hymt-bar")) return;
    const bar = document.createElement("div");
    bar.id = "hymt-bar";
    bar.style = "position:fixed;bottom:20px;right:20px;z-index:2147483647;display:block";
    bar.innerHTML = '<button id="hymt-float" style="padding:12px 18px;border-radius:24px;background:#111;color:#fff;border:none;box-shadow:0 4px 12px rgba(0,0,0,.4);font-size:14px;font-weight:600">🌐 翻译</button><div id="hymt-menu" style="display:none;margin-top:8px;background:#fff;border-radius:12px;box-shadow:0 4px 12px rgba(0,0,0,.2);overflow:hidden"><button data-e="google" style="display:block;width:100%;padding:10px 16px;border:none;background:#fff;text-align:left">谷歌译 (阿里云)</button><button data-e="hymt" style="display:block;width:100%;padding:10px 16px;border:none;background:#fff;text-align:left">AI 混元翻译</button></div>';
    document.documentElement.appendChild(bar);
    const btn = bar.querySelector("#hymt-float");
    const menu = bar.querySelector("#hymt-menu");
    btn.onclick = () => menu.style.display = menu.style.display==="none" ? "block" : "none";
    menu.onclick = (e) => {
      const engine = e.target.dataset.e;
      if (!engine) return;
      menu.style.display="none";
      browser.runtime.sendMessage({action:"translate", engine, text: window.getSelection().toString() || document.body.innerText.slice(0,4000)});
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
