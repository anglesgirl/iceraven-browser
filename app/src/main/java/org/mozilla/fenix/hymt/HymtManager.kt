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

    /** 从 ModelScope 国内 CDN 下载模型到 filesDir/hymt/。onProgress(0~1)。 */
    fun downloadModel(context: Context, which: String = "2bit", onProgress: (Float) -> Unit = {}) {
        io.execute {
            try {
                val urlStr = when (which) {
                    "1.25bit" -> "https://modelscope.cn/models/AngelSlim/Hy-MT1.5-1.8B-1.25bit-GGUF/resolve/master/Hy-MT1.5-1.8B-1.25bit.gguf"
                    else -> "https://modelscope.cn/models/AngelSlim/Hy-MT1.5-1.8B-2bit-GGUF/resolve/master/Hy-MT1.5-1.8B-2bit.gguf"
                }
                val fileName = if (which == "1.25bit") MODEL_125BIT else MODEL_2BIT
                val dir = File(context.filesDir, MODEL_DIR)
                dir.mkdirs()
                val dest = File(dir, fileName)
                val tmp = File(dir, fileName + ".tmp")

                val conn = (java.net.URL(urlStr).openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                }
                conn.connect()
                if (conn.responseCode !in 200..299) throw RuntimeException("HTTP ${conn.responseCode}")
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    tmp.outputStream.use { output ->
                        val buf = ByteArray(64 * 1024)
                        var read: Long = 0
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            if (total > 0) onProgress(done.toFloat() / total)
                        }
                        output.flush()
                    }
                }
                conn.disconnect()
                if (dest.exists()) dest.delete()
                tmp.renameTo(dest)
                Log.i(TAG, "model downloaded: ${dest.absolutePath} (${dest.length() / 1024 / 1024}MB)")
                // 下载完成后自动加载
                val threads = Runtime.getRuntime().availableProcessors().coerceAtMost(6)
                HymtBridge.nativeInit(dest.absolutePath, threads)
                initialized.set(true)
            } catch (e: Throwable) {
                Log.e(TAG, "download failed", e)
            }
        }
    }
}
