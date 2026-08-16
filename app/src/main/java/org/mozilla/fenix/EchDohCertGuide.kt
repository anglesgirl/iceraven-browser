package org.mozilla.fenix

import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.security.KeyChain
import java.security.KeyStore

/**
 * echdoh 集成（2026-08-16）：自动更新证书方案。
 *
 * cert9.db 预置与 GeckoView profile 初始化冲突（实测弹"需要重新启动"）→ 废弃。
 * 本方案：激活一次设备管理员，之后 DevicePolicyManager.installCaCert 静默装 CA
 * 到系统信任库（模拟自动更新，无确认框，每次启动自动检测自动装）→
 * GeckoView enterpriseRootsEnabled 信任 → 本地 DoH 自签证书直接通过校验。
 *
 * 用户流程：首次启动弹窗 → 点「激活」→ 系统设备管理页勾选一次（30 秒）→
 * 之后全自动，100 年证书无需再管。
 */
object EchDohCertGuide {

    private const val CA_CN = "echdoh-local-CA"
    private const val PREFS = "echdoh"
    private const val KEY_PROMPTED = "ca_prompted"

    private fun adminComponent(ctx: Context) =
        ComponentName(ctx, EchDohAdminReceiver::class.java)

    /** 是否已激活设备管理员。 */
    fun isAdminActive(ctx: Context): Boolean {
        return try {
            val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.isAdminActive(adminComponent(ctx))
        } catch (_: Throwable) {
            false
        }
    }

    /** 是否已安装我们的 CA（遍历 Android 系统信任库）。 */
    fun isInstalled(ctx: Context): Boolean {
        return try {
            val ks = KeyStore.getInstance("AndroidCAStore")
            ks.load(null)
            ks.aliases().asSequence().any { alias ->
                val cert = (ks.getCertificate(alias) as? java.security.cert.X509Certificate)
                cert?.subjectX500Principal?.name?.contains(CA_CN) == true
            }
        } catch (_: Throwable) {
            false
        }
    }

    /** 首次启动调用：未装 CA 时引导激活设备管理员。 */
    fun maybePrompt(activity: Activity) {
        try {
            if (isInstalled(activity)) return
            val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.getBoolean(KEY_PROMPTED, false) && !isAdminActive(activity)) {
                // 激活过但 CA 没了 → 自动重装（模拟自动更新）
                tryInstallSilently(activity)
                return
            }
            if (prefs.getBoolean(KEY_PROMPTED, false)) return
            prefs.edit().putBoolean(KEY_PROMPTED, true).apply()

            activity.runOnUiThread {
                AlertDialog.Builder(activity)
                    .setTitle("启用 DoH 证书自动管理")
                    .setMessage(
                        "需要激活一次「设备管理」权限（用于自动安装 DNS 加密证书，100 年有效）。\n\n" +
                            "激活后：系统设置 → 安全 → 设备管理应用 → 勾选「ECH DoH 证书管理」→ 激活。\n" +
                            "之后每次启动自动检查/安装证书，无需再操作。"
                    )
                    .setPositiveButton("去激活") { _, _ ->
                        activity.startActivity(
                            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent(activity))
                                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "用于自动安装本地 DNS 加密证书")
                            }
                        )
                    }
                    .setNegativeButton("跳过", null)
                    .show()
            }
        } catch (_: Throwable) {}
    }

    /** 已激活设备管理员时：静默安装 CA（每次启动自动检测，缺失即装）。 */
    fun tryInstallSilently(ctx: Context) {
        try {
            if (!isAdminActive(ctx) || isInstalled(ctx)) return
            val ca = ctx.assets.open("echdoh-ca.pem").readBytes()
            val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (dpm.installCaCert(adminComponent(ctx), ca)) {
                android.util.Log.i("EchDoh", "CA installed silently via DevicePolicyManager")
            }
        } catch (e: Throwable) {
            android.util.Log.w("EchDoh", "silent CA install failed: $e")
        }
    }

    /** 旧版手动安装入口（保留备用，API 34+ KeyChain 直传，旧版 FileProvider）。 */
    fun launchInstaller(activity: Activity) {
        try {
            val ca = activity.assets.open("echdoh-ca.pem").readBytes()
            val f = java.io.File(activity.cacheDir, "echdoh-ca.pem")
            f.writeBytes(ca)
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                val intent = KeyChain.createInstallIntent().apply {
                    putExtra(KeyChain.EXTRA_CERTIFICATE, ca)
                    putExtra(KeyChain.EXTRA_NAME, "echdoh-local-CA")
                }
                activity.startActivity(intent)
            } else {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    activity, "${activity.packageName}.fileprovider", f
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/x-x509-ca-cert")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                activity.startActivity(intent)
            }
        } catch (_: Throwable) {}
    }
}
