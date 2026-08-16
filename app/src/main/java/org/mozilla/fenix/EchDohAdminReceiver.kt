package org.mozilla.fenix

import android.app.admin.DeviceAdminReceiver

/**
 * echdoh 集成（2026-08-16）：设备管理员 receiver。
 * 激活一次后可用 DevicePolicyManager.installCaCert 静默装 CA（模拟自动更新，
 * 无确认框）→ GeckoView enterpriseRootsEnabled 信任 → 本地 DoH 证书免手动安装。
 */
class EchDohAdminReceiver : DeviceAdminReceiver()
