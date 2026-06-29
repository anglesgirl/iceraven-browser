/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.utils

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.mozilla.fenix.R

/**
 * Dialog for showing app update information and handling the download flow.
 *
 * Shows the new version name, release notes, and two download options:
 * - China mirror (faster for users in China)
 * - GitHub direct download
 *
 * After download completes, the caller's callback handles auto-install.
 */
class AppUpdateDialog(
    context: Context,
    private val updateInfo: AppUpdateChecker.UpdateInfo,
    private val onDownloadClick: (useCnMirror: Boolean) -> Unit,
    private val onDismiss: () -> Unit,
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val message = buildString {
            append(updateInfo.releaseName)
            append("\n\n")
            // Show release notes (truncate if too long)
            val body = updateInfo.releaseBody.trim()
            if (body.isNotEmpty()) {
                append(body.take(500))
                if (body.length > 500) append("...")
            }
            append("\n\n")
            append("APK size: ${updateInfo.apkSize / 1024 / 1024} MB")
        }

        val builder = MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.update_available_title, updateInfo.tagName))
            .setMessage(message)
            .setPositiveButton(R.string.update_download_cn) { _, _ ->
                onDownloadClick(true)
            }
            .setNeutralButton(R.string.update_download_github) { _, _ ->
                onDownloadClick(false)
            }
            .setNegativeButton(R.string.update_later) { _, _ ->
                onDismiss()
            }
            .setOnCancelListener { onDismiss() }

        builder.show()
    }
}
