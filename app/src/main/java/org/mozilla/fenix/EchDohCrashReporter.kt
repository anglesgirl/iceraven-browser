package org.mozilla.fenix

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃日志自动上报（2026-08-16）：Java 崩溃 + logcat 捕获 → 上传 R2 私有桶
 * （crash/ 前缀），我这边直接能看，用户零操作。
 * 复用证书 OTA 的 R2 通道（SigV4 PUT 在 echdoh 导出）。
 */
object EchDohCrashReporter {

    private const val R2_ENDPOINT = "https://cce6c3a3b595692f6041a278411fb20e.r2.cloudflarestorage.com"
    private const val R2_BUCKET = "echdoh-certs"
    private const val R2_KEY = "81b656e3afd8f3dc3a9a24a2864da3f2"
    private const val R2_SECRET = "922cd9103f0baa391e026a5fd4f5f5361b0da9b9ffd85b32403f2c7ca9130ae4"

    @Volatile
    private var installed = false

    /** 安装全局崩溃捕获（Application onCreate 调用一次）。 */
    fun install(ctx: Context) {
        if (installed) return
        installed = true
        val appCtx = ctx.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val report = buildReport(appCtx, thread, throwable)
                upload(report)
                // 本地也存一份
                try {
                    val dir = File(appCtx.getExternalFilesDir(null) ?: appCtx.filesDir, "crash")
                    dir.mkdirs()
                    File(dir, "crash-" + System.currentTimeMillis() + ".txt").writeText(report)
                } catch (_: Throwable) {}
            } catch (_: Throwable) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun buildReport(ctx: Context, thread: Thread, throwable: Throwable): String {
        val sb = StringBuilder()
        sb.append("=== ECH Browser Crash Report ===\n")
        sb.append("time: ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())).append("\n")
        sb.append("device: ").append(android.os.Build.MANUFACTURER).append(' ')
            .append(android.os.Build.MODEL).append("\n")
        sb.append("android: ").append(android.os.Build.VERSION.RELEASE).append(" (API ")
            .append(android.os.Build.VERSION.SDK_INT).append(")\n")
        sb.append("app: ").append(ctx.packageName).append("\n")
        sb.append("thread: ").append(thread.name).append("\n\n")

        sb.append("=== Java StackTrace ===\n")
        val sw = java.io.StringWriter()
        throwable.printStackTrace(java.io.PrintWriter(sw))
        sb.append(sw.toString()).append("\n")

        // 所有线程栈（定位卡死/ANR 有用）
        sb.append("=== All Threads ===\n")
        try {
            val tms = Thread.getAllStackTraces()
            for ((t, st) in tms) {
                sb.append(t.name).append(" (").append(t.state).append("):\n")
                for (el in st.take(20)) sb.append("    at ").append(el.toString()).append("\n")
            }
        } catch (_: Throwable) {}

        // logcat 关键 tag（EchDoh / AndroidRuntime / Gecko / MOZ）
        sb.append("\n=== Logcat (EchDoh/AndroidRuntime/Gecko/MOZ) ===\n")
        try {
            val p = Runtime.getRuntime().exec(
                arrayOf("logcat", "-d", "-t", "800",
                    "EchDoh:V", "AndroidRuntime:E", "GeckoConsole:V", "GeckoView:V",
                    "MOZ:V", "libc:F", "DEBUG:F", "*:S")
            )
            val rdr = p.inputStream.bufferedReader()
            var line: String?
            var n = 0
            while (rdr.readLine().also { line = it } != null && n < 300) {
                sb.append(line).append("\n")
                n++
            }
            p.destroy()
        } catch (_: Throwable) {}

        return sb.toString()
    }

    private fun upload(report: String) {
        try {
            val name = "crash/" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) +
                "-" + android.os.Process.myPid() + ".txt"
            val ok = com.anglesgirl.echdoh.echdoh.Echdoh.uploadToR2(
                R2_ENDPOINT, R2_BUCKET, name, R2_KEY, R2_SECRET, "text/plain", report,
            )
            android.util.Log.i("EchDoh", "crash report uploaded: $name ok=$ok")
        } catch (e: Throwable) {
            android.util.Log.e("EchDoh", "crash upload failed: $e")
        }
    }

    /** 每次启动后台上报关键日志（不崩溃的"打不开/黑屏"也能定位）。
     *  forkRelease（release 构建）无 logcat 权限 → fallback 到 echdoh 的
     *  PollLogs()（Go 侧有界日志缓冲，含 DNS 解析记录）。 */
    fun uploadStartupLogs(ctx: Context) {
        try {
            val sb = StringBuilder()
            sb.append("=== Startup Logs ").append(
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())).append(" ===\n")
            sb.append("device: ").append(android.os.Build.MANUFACTURER).append(' ')
                .append(android.os.Build.MODEL).append(" | Android ")
                .append(android.os.Build.VERSION.RELEASE).append("\n\n")
            var n = 0
            try {
                val p = Runtime.getRuntime().exec(
                    arrayOf("logcat", "-d", "-t", "600",
                        "EchDoh:V", "AndroidRuntime:E", "GeckoConsole:V", "GeckoView:V",
                        "MOZ:V", "ActivityManager:E", "libc:F", "DEBUG:F", "*:S")
                )
                val rdr = p.inputStream.bufferedReader()
                var line: String?
                while (rdr.readLine().also { line = it } != null && n < 400) {
                    sb.append(line).append("\n")
                    n++
                }
                p.destroy()
            } catch (_: Throwable) {}
            if (n == 0) {
                // release 构建无 logcat 权限 → echdoh 内部日志
                sb.append("(logcat unavailable — using echdoh PollLogs)\n")
                try {
                    val goLogs = com.anglesgirl.echdoh.echdoh.Echdoh.pollLogs()
                    sb.append(goLogs).append("\n")
                } catch (_: Throwable) {}
            }
            val name = "logs/startup-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) +
                "-" + android.os.Process.myPid() + ".txt"
            val ok = com.anglesgirl.echdoh.echdoh.Echdoh.uploadToR2(
                R2_ENDPOINT, R2_BUCKET, name, R2_KEY, R2_SECRET, "text/plain", sb.toString(),
            )
            android.util.Log.i("EchDoh", "startup logs uploaded: $name ok=$ok lines=$n")
        } catch (e: Throwable) {
            android.util.Log.w("EchDoh", "startup logs upload failed: $e")
        }
    }
}
