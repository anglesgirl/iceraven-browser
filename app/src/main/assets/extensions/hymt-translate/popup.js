const $ = id => document.getElementById(id);
$("google").onclick = async () => {
  const [tab] = await browser.tabs.query({active:true, currentWindow:true});
  browser.tabs.sendMessage(tab.id, {action:"translate", engine:"google"});
  window.close();
};
$("hymt").onclick = async () => {
  const r = await browser.runtime.sendMessage({action:"translate", engine:"hymt", text:"test"});
  if (r && r.needDownload) {
    $("status").textContent = "需先下载模型";
    $("dl").style.display = "block";
  } else {
    const [tab] = await browser.tabs.query({active:true, currentWindow:true});
    browser.tabs.sendMessage(tab.id, {action:"translate", engine:"hymt"});
    window.close();
  }
};
$("dl125").onclick = () => browser.runtime.sendMessage({action:"download", model:"1.25bit"}).then(()=>{$("status").textContent="开始下载 1.25bit...";});
$("dl2").onclick = () => browser.runtime.sendMessage({action:"download", model:"2bit"}).then(()=>{$("status").textContent="开始下载 2bit...";});
