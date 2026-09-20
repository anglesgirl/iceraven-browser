package org.mozilla.fenix.hymt

import android.content.Context
import android.util.Log
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 本机 AI 翻译管理器（混元 Hy-MT GGUF + llama.cpp）。
 * .so 或模型缺失时 isReady=false，扩展自动降级走在线引擎。
 * 所有 native 调用都在单线程 executor 上串行，避免 llama context 竞争。
 */
object HymtManager {
    private const val TAG = "HyMT"
    private const val MODEL_DIR = "hymt"
    private const val MODEL_2BIT = "Hy-MT1.5-1.8B-2bit.gguf"
    private const val MODEL_125BIT = "Hy-MT1.5-1.8B-1.25bit.gguf"

    private val io = Executors.newSingleThreadExecutor()
    private val initialized = AtomicBoolean(false)

    /** 模型文件优先级：2bit 优先（质量好），没有就用 1.25bit */
    fun modelFile(context: Context): File {
        val dir = File(context.filesDir, MODEL_DIR)
        File(dir, MODEL_2BIT).takeIf { it.exists() }?.let { return it }
        File(dir, MODEL_125BIT).takeIf { it.exists() }?.let { return it }
        return File(dir, MODEL_2BIT)
    }

    fun modelExists(context: Context): Boolean = modelFile(context).exists()

    fun isReady(context: Context): Boolean {
        if (!HymtBridge.isLoaded) return false
        if (!modelExists(context)) return false
        return try { HymtBridge.nativeIsReady() } catch (e: Throwable) { false }
    }

    /** 异步初始化模型（加载到内存）。完成后回调 onResult(success, message)。 */
    fun init(context: Context, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        io.execute {
            try {
                if (!HymtBridge.isLoaded) {
                    onResult(false, "native library missing")
                    return@execute
                }
                val f = modelFile(context)
                if (!f.exists()) {
                    onResult(false, "model file missing: ${f.absolutePath}")
                    return@execute
                }
                if (initialized.get()) {
                    onResult(true, "already loaded")
                    return@execute
                }
                val threads = Runtime.getRuntime().availableProcessors().coerceAtMost(6)
                Log.i(TAG, "loading model ${f.name} with $threads threads...")
                val ok = HymtBridge.nativeInit(f.absolutePath, threads)
                if (ok) {
                    initialized.set(true)
                    Log.i(TAG, "model loaded OK")
                    onResult(true, "model loaded")
                } else {
                    onResult(false, "nativeInit returned false")
                }
            } catch (e: Throwable) {
                Log.e(TAG, "init failed", e)
                onResult(false, e.message ?: "init failed")
            }
        }
    }

    /** 同步翻译（必须在后台线程调用）。返回译文，失败返回 null。 */
    fun translate(text: String, maxTokens: Int = 512): String? {
        if (!HymtBridge.isLoaded || !initialized.get()) return null
        return try {
            HymtBridge.nativeTranslate(text, maxTokens)
        } catch (e: Throwable) {
            Log.e(TAG, "translate failed", e)
            null
        }
    }

    fun release() {
        io.execute {
            try {
                if (HymtBridge.isLoaded) HymtBridge.nativeFree()
                initialized.set(false)
            } catch (_: Throwable) {}
        }
    }

    /**
     * 用系统下载器（DownloadManager）下载模型到公共 Download 目录，
     * 完成后自动移动到 filesDir/hymt/ 并加载。有通知栏进度、断点续传。
     */
    fun downloadModel(context: Context, which: String = "2bit", onProgress: (Float) -> Unit = {}) {
        val urlStr = when (which) {
            "1.25bit" -> "https://modelscope.cn/models/AngelSlim/Hy-MT1.5-1.8B-1.25bit-GGUF/resolve/master/Hy-MT1.5-1.8B-1.25bit.gguf"
            else -> "https://modelscope.cn/models/AngelSlim/Hy-MT1.5-1.8B-2bit-GGUF/resolve/master/Hy-MT1.5-1.8B-2bit.gguf"
        }
        val fileName = if (which == "1.25bit") MODEL_125BIT else MODEL_2BIT

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        val req = android.app.DownloadManager.Request(android.net.Uri.parse(urlStr))
            .setTitle("HyMT 模型 $which")
            .setDescription("混元翻译模型，约${if (which == "1.25bit") "440" else "574"}MB")
            .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        val downloadId = dm.enqueue(req)
        Log.i(TAG, "download queued id=$downloadId -> $fileName")

        // 监听完成
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: android.content.Intent?) {
                val id = intent?.getLongExtra(android.app.DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: -1
                if (id != downloadId) return
                ctx?.unregisterReceiver(this)
                io.execute {
                    try {
                        val src = File(android.os.Environment.getExternalStoragePublicDirectory(
                            android.os.Environment.DIRECTORY_DOWNLOADS), fileName)
                        val dir = File(context.filesDir, MODEL_DIR)
                        dir.mkdirs()
                        val dest = File(dir, fileName)
                        if (src.exists()) {
                            if (dest.exists()) dest.delete()
                            src.copyTo(dest, overwrite = true)
                            src.delete()
                            Log.i(TAG, "model moved to ${dest.absolutePath}")
                        }
                        onProgress(1f)
                        val threads = Runtime.getRuntime().availableProcessors().coerceAtMost(6)
                        HymtBridge.nativeInit(dest.absolutePath, threads)
                        initialized.set(true)
                        Log.i(TAG, "model auto-loaded")
                    } catch (e: Throwable) {
                        Log.e(TAG, "post-download failed", e)
                    }
                }
            }
        }
        context.registerReceiver(receiver, android.content.IntentFilter(android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }
}
