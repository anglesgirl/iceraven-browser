/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mozilla.components.concept.fetch.Client
import mozilla.components.concept.fetch.MutableHeaders
import mozilla.components.concept.fetch.Request
import mozilla.components.concept.fetch.Response
import mozilla.components.concept.fetch.isSuccess
import org.json.JSONObject
import java.io.File

/**
 * Checks for app updates via GitHub Releases API and handles APK download/install.
 *
 * Uses the project's GitHub repository releases to detect new versions.
 * The latest release's tag_name is compared with the current app version.
 * If newer, the user can download and install the APK.
 */
object AppUpdateChecker {

    private const val TAG = "AppUpdateChecker"

    private const val GITHUB_API_URL =
        "https://api.github.com/repos/anglesgirl/iceraven-browser/releases/latest"

    /** Timeout for HTTP request in milliseconds */
    private const val REQUEST_TIMEOUT_MS = 15000L

    /** Prefix for China mirror to accelerate GitHub downloads */
    private const val CN_MIRROR_PREFIX = "https://gh-proxy.com/"

    data class UpdateInfo(
        val tagName: String,
        val releaseName: String,
        val releaseBody: String,
        val apkUrl: String,
        val apkUrlCnMirror: String,
        val apkName: String,
        val apkSize: Long,
        val htmlUrl: String,
    )

    /**
     * Check for updates by querying the GitHub Releases API.
     *
     * @param client The [Client] used for HTTP requests (from components.core.client).
     * @param currentVersion The current app version name (e.g. "ao3-browser-20260629-v3").
     * @return [UpdateInfo] if a newer version is available, null otherwise.
     */
    suspend fun checkForUpdate(
        client: Client,
        currentVersion: String,
    ): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request(
                url = GITHUB_API_URL,
                method = Request.Method.GET,
                headers = MutableHeaders().apply {
                    set("Accept", "application/vnd.github.v3+json")
                    set("User-Agent", "AO3-Browser-UpdateChecker")
                },
                connectTimeout = REQUEST_TIMEOUT_MS to java.util.concurrent.TimeUnit.MILLISECONDS,
                readTimeout = REQUEST_TIMEOUT_MS to java.util.concurrent.TimeUnit.MILLISECONDS,
            )

            val response = client.fetch(request)
            if (!response.isSuccess) {
                Log.w(TAG, "GitHub API returned status: ${response.status}")
                return@withContext null
            }

            val bodyText = response.body.string()
            response.close()

            val json = JSONObject(bodyText)
            val tagName = json.optString("tag_name", "")
            if (tagName.isEmpty()) {
                Log.w(TAG, "No tag_name in release response")
                return@withContext null
            }

            // Skip if same version
            if (tagName == currentVersion || tagName <= currentVersion) {
                Log.d(TAG, "Already up to date: $tagName")
                return@withContext null
            }

            // Find the arm64-v8a APK (preferred) or first APK asset
            val assets = json.optJSONArray("assets")
            if (assets == null || assets.length() == 0) {
                Log.w(TAG, "No assets in release")
                return@withContext null
            }

            var preferredAsset: JSONObject? = null
            var fallbackAsset: JSONObject? = null

            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                if (name.contains("arm64-v8a")) {
                    preferredAsset = asset
                    break
                }
                if (name.endsWith(".apk") && fallbackAsset == null) {
                    fallbackAsset = asset
                }
            }

            val asset = preferredAsset ?: fallbackAsset ?: run {
                Log.w(TAG, "No APK asset found in release")
                return@withContext null
            }

            val apkUrl = asset.optString("browser_download_url", "")
            val info = UpdateInfo(
                tagName = tagName,
                releaseName = json.optString("name", tagName),
                releaseBody = json.optString("body", ""),
                apkUrl = apkUrl,
                apkUrlCnMirror = CN_MIRROR_PREFIX + apkUrl,
                apkName = asset.optString("name", "app-update.apk"),
                apkSize = asset.optLong("size", 0),
                htmlUrl = json.optString("html_url", ""),
            )

            Log.d(TAG, "Update available: ${info.tagName} (${info.apkName}, ${info.apkSize / 1024 / 1024}MB)")
            info
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check for updates", e)
            null
        }
    }

    /**
     * Download the APK using Android system DownloadManager.
     *
     * Returns the download ID that can be used to track the download.
     * The caller should register a BroadcastReceiver for
     * [DownloadManager.ACTION_DOWNLOAD_COMPLETE] to handle completion.
     *
     * @param context Application context.
     * @param updateInfo The update info containing the APK download URL.
     * @param useCnMirror If true, use the China mirror URL for faster download in China.
     * @return Download ID, or -1 on failure.
     */
    fun downloadApk(context: Context, updateInfo: UpdateInfo, useCnMirror: Boolean = false): Long {
        return try {
            val url = if (useCnMirror) updateInfo.apkUrlCnMirror else updateInfo.apkUrl
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle("AO3 Browser Update ${updateInfo.tagName}")
                setDescription("Downloading ${updateInfo.apkName}")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalFilesDir(
                    context,
                    Environment.DIRECTORY_DOWNLOADS,
                    updateInfo.apkName,
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setAllowedOverMetered(true)
                }
            }

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start download", e)
            -1
        }
    }

    /**
     * Install the downloaded APK file.
     *
     * Should be called after the download completes (from BroadcastReceiver).
     *
     * @param context Application context.
     * @param apkFile The downloaded APK file.
     */
    fun installApk(context: Context, apkFile: File) {
        try {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile,
                )
            } else {
                @Suppress("DEPRECATION")
                Uri.fromFile(apkFile)
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start install intent", e)
        }
    }

    /**
     * Get the downloaded APK file path from DownloadManager.
     *
     * @param context Application context.
     * @param downloadId The download ID from [downloadApk].
     * @return The [File] if download is complete, null otherwise.
     */
    fun getDownloadedFile(context: Context, downloadId: Long): File? {
        return try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = dm.query(query) ?: return null
            cursor.use {
                if (it.moveToFirst()) {
                    val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        val uriString = it.getString(
                            it.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI),
                        )
                        Uri.parse(uriString)?.let { uri -> File(uri.path) }
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get downloaded file", e)
            null
        }
    }

    /**
     * Register a download complete receiver.
     *
     * @param context Application context.
     * @param downloadId The download ID to monitor.
     * @param onComplete Callback invoked with the downloaded [File] when complete.
     * @return The registered [BroadcastReceiver] (call [unregisterDownloadReceiver] to clean up).
     */
    fun registerDownloadReceiver(
        context: Context,
        downloadId: Long,
        onComplete: (File?) -> Unit,
    ): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    val file = getDownloadedFile(ctx, downloadId)
                    onComplete(file)
                    ctx.unregisterReceiver(this)
                }
            }
        }
        context.registerReceiver(
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
        )
        return receiver
    }

    /**
     * Unregister a download receiver (for cleanup if download is cancelled).
     */
    fun unregisterDownloadReceiver(context: Context, receiver: BroadcastReceiver) {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            // Already unregistered, ignore
        }
    }
}
