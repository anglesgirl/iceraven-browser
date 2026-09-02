// ModelScope 国内 CDN
const MODELS = {
  "1.25bit": "https://modelscope.cn/models/AngelSlim/Hy-MT1.5-1.8B-1.25bit-GGUF/resolve/master/Hy-MT1.5-1.8B-1.25bit.gguf",
  "2bit": "https://modelscope.cn/models/AngelSlim/Hy-MT1.5-1.8B-2bit-GGUF/resolve/master/Hy-MT1.5-1.8B-2bit.gguf"
};
browser.runtime.onMessage.addListener(async (msg, sender) => {
  if (msg.action === "download") {
    const url = MODELS[msg.model];
    if (!url) return {ok:false, error:"unknown model"};
    console.log("[HyMT] download", msg.model, url);
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
