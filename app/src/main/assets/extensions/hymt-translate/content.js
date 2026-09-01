// HyMT 接管原生翻译入口 + 页内悬浮条
(function(){
  // 1. 监听 Kotlin 注入的劫持事件（原生菜单点翻译时触发）
  window.addEventListener("hymt-trigger", async (e) => {
    const detail = e.detail || {};
    const from = detail.from || document.documentElement.lang || "auto";
    const to = detail.to || "zh";
    console.log("[HyMT] trigger", from, "->", to);
    // 询问 background 走哪个引擎，是否需下载
    try {
      const res = await browser.runtime.sendMessage({action:"translate", engine:"hymt", from, to, text: document.body.innerText.slice(0,4000)});
      if (res && res.needDownload) {
        if (confirm("HyMT 模型未下载，是否现在下载？(1.25bit ~800MB / 2bit ~1.2GB 走 ModelScope 国内CDN)")) {
          browser.runtime.sendMessage({action:"download", model:"1.25bit"});
        }
        return;
      }
      // 真翻译由 background 接 wllama，这里先用 Google 兜底演示
      if (res && res.note) console.log("[HyMT]", res.note);
    } catch(e){ console.log("[HyMT] bg error", e); }
    // 简易页内替换（后续接 wllama 全量）
    document.body.style.border = "2px solid #00c853";
    setTimeout(()=>document.body.style.border="", 1500);
  });

  // 2. 悬浮条（选中文字快捷）
  if (!document.getElementById("hymt-bar")) {
    const bar = document.createElement("div");
    bar.id = "hymt-bar";
    bar.style = "position:fixed;bottom:12px;right:12px;z-index:999999;display:none";
    bar.innerHTML = '<button id="hymt-float" style="padding:8px 12px;border-radius:20px;background:#000;color:#fff;border:none;box-shadow:0 2px 8px rgba(0,0,0,.3)">翻译</button>';
    document.documentElement.appendChild(bar);
    document.addEventListener("selectionchange", () => {
      const t = window.getSelection().toString().trim();
      bar.style.display = t.length > 1 ? "block" : "none";
    });
    bar.onclick = () => browser.runtime.sendMessage({action:"translate", engine:"hymt", text: window.getSelection().toString()});
  }
})();
