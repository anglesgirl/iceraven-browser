// ModelScope 国内 CDN（base.apk 验证过）
const MODELS = {
  "1.25bit": "https://modelscope.cn/models/AngelSlim/Hy-MT1.5-1.8B-1.25bit-GGUF/resolve/master/Hy-MT1.5-1.8B-1.25bit.gguf",
  "2bit": "https://modelscope.cn/models/AngelSlim/Hy-MT1.5-1.8B-2bit-GGUF/resolve/master/Hy-MT1.5-1.8B-2bit.gguf"
};
browser.runtime.onMessage.addListener(async (msg, sender) => {
  if (msg.action === "download") {
    const url = MODELS[msg.model];
    if (!url) return {ok:false, error:"unknown model"};
    // 提示下载（实际下一步接 wllama + IndexedDB 缓存）
    console.log("[HyMT] download", msg.model, url);
    return {ok:true, url};
  }
  if (msg.action === "translate") {
    // msg: {action:"translate", engine:"google"|"hymt", text}
    if (msg.engine === "google") {
      // 走阿里云 47.103.34.63 的 translate.googleapis
      return {ok:true, note:"google via aliyun"};
    }
    // hymt: 检查模型是否存在，不存在则提示下载
    const hasModel = await browser.storage.local.get("hymt_model");
    if (!hasModel.hymt_model) return {ok:false, needDownload:true};
    return {ok:true, note:"hymt translate"};
  }
});
