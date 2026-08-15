package org.mozilla.fenix

import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.security.KeyChain
import java.security.KeyStore

/**
 * echdoh 集成（2026-08-16）：自签 CA 引导安装。
 * GeckoView 无内置 CA API（enterpriseRootsEnabled 只信任 Android CA store），
 * 必须在系统里装一次 CA —— APK 内置 ca.pem，检测到未装则弹窗引导，
 * 一键跳系统证书安装器。装一次 100 年有效。
 */
object EchDohCertGuide {

    private const val CA_CN = "echdoh-local-CA"

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

    /** 未安装则弹窗引导（首次启动调一次，装过不再弹）。 */
    fun maybePrompt(activity: Activity) {
        try {
            val prefs = activity.getSharedPreferences("echdoh", Context.MODE_PRIVATE)
            if (prefs.getBoolean("ca_prompted", false)) return
            prefs.edit().putBoolean("ca_prompted", true).apply()
            if (isInstalled(activity)) return

            activity.runOnUiThread {
                AlertDialog.Builder(activity)
                    .setTitle("安装 DoH 证书")
                    .setMessage(
                        "首次使用需要安装一次证书（100 年有效，仅用于本地 DNS 加密）。\n" +
                            "点击「安装」后选择：安装证书 → CA 证书 → 名称随意 → 确定。\n" +
                            "如果打不开 x.com 等网站，重新安装一次即可。"
                    )
                    .setPositiveButton("安装") { _, _ -> launchInstaller(activity) }
                    .setNegativeButton("跳过", null)
                    .show()
            }
        } catch (_: Throwable) {}
    }

    /** 跳系统证书安装器。API 34+ 用 KeyChain 直传，旧版用 FileProvider 打开证书文件。 */
    fun launchInstaller(activity: Activity) {
        try {
            val ca = activity.assets.open("echdoh-ca.pem").readBytes()
            val f = java.io.File(activity.cacheDir, "echdoh-ca.pem")
            f.writeBytes(ca)
            if (Build.VERSION.SDK_INT >= 34) {
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
