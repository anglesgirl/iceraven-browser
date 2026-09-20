const $ = id => document.getElementById(id);
async function send(engine) {
  try {
    const [tab] = await browser.tabs.query({active:true, currentWindow:true});
    if (!tab) { $("status").textContent = "没有活动标签页"; return; }
    await browser.tabs.sendMessage(tab.id, {action:"translate-page", engine});
  } catch(e) {
    $("status").textContent = "失败: " + e.message;
    return;
  }
  window.close();
}
$("google").onclick = () => send("google");
$("hymt").onclick = () => send("hymt");
