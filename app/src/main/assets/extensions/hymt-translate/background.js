// ModelScope 国内 CDN + 菜单注册
const MODELS = {
  "1.25bit": "https://modelscope.cn/models/AngelSlim/Hy-MT1.5-1.8B-1.25bit-GGUF/resolve/master/Hy-MT1.5-1.8B-1.25bit.gguf",
  "2bit": "https://modelscope.cn/models/AngelSlim/Hy-MT1.5-1.8B-2bit-GGUF/resolve/master/Hy-MT1.5-1.8B-2bit.gguf"
};
// 注册右键/菜单项（安卓上会进三点菜单→扩展）
try {
  browser.menus.create({id:"hymt-google", title:"翻译 - 谷歌译", contexts:["page","selection"]});
  browser.menus.create({id:"hymt-ai", title:"翻译 - AI混元", contexts:["page","selection"]});
  browser.menus.onClicked.addListener((info, tab) => {
    const engine = info.menuItemId==="hymt-google" ? "google" : "hymt";
    browser.tabs.sendMessage(tab.id, {action:"translate", engine, text: info.selectionText || ""});
  });
} catch(e){ console.log("[HyMT] menus fail", e); }

browser.runtime.onMessage.addListener(async (msg, sender) => {
  if (msg.action === "download") {
    const url = MODELS[msg.model];
    if (!url) return {ok:false, error:"unknown model"};
    console.log("[HyMT] download", msg.model, url);
    // 用 downloads API 走 ModelScope 国内CDN
    try { await browser.downloads.download({url}); } catch(e){ console.log(e); }
    return {ok:true, url};
  }
  if (msg.action === "translate") {
    if (msg.engine === "google") {
      return {ok:true, note:"google via aliyun"};
    }
    const hasModel = await browser.storage.local.get("hymt_model");
    if (!hasModel.hymt_model) return {ok:false, needDownload:true};
    return {ok:true, note:"hymt translate"};
  }
});
