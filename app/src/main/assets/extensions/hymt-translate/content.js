// 手机端注入悬浮翻译条（菜单入口已通过 browserAction，这里是页内快捷）
(function(){
  if (document.getElementById("hymt-bar")) return;
  const bar = document.createElement("div");
  bar.id = "hymt-bar";
  bar.style = "position:fixed;bottom:12px;right:12px;z-index:999999;display:none";
  bar.innerHTML = '<button id="hymt-float" style="padding:8px 12px;border-radius:20px;background:#000;color:#fff;border:none;box-shadow:0 2px 8px rgba(0,0,0,.3)">翻译</button>';
  document.documentElement.appendChild(bar);
  // 选择文本时显示
  document.addEventListener("selectionchange", () => {
    const t = window.getSelection().toString().trim();
    bar.style.display = t.length > 1 ? "block" : "none";
  });
  bar.onclick = () => browser.runtime.sendMessage({action:"translate", engine:"hymt", text: window.getSelection().toString()});
  browser.runtime.onMessage.addListener(msg => {
    if (msg.action === "translate") console.log("[HyMT] page translate", msg.engine);
  });
})();
