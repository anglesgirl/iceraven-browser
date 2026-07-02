/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components

import android.content.Context
import mozilla.components.concept.push.PushService
import mozilla.components.feature.push.AutoPushFeature
import mozilla.components.feature.push.PushConfig
import mozilla.components.lib.crash.CrashReporter
import mozilla.components.support.base.log.logger.Logger
import org.mozilla.fenix.perf.lazyMonitored

/**
 * Component group for push services. These components use services that strongly depend on
 * push messaging (e.g. WebPush, SendTab).
 *
 * AO3 Browser: Firebase Messaging has been removed. Push functionality
 * is disabled — feature returns null, making all push operations no-ops.
 * The class is kept for compilation compatibility with AC components
 * that reference it (BackgroundServices, Components).
 */
class Push(val context: Context, crashReporter: CrashReporter) {
    private val logger = Logger("Push")

    val feature: AutoPushFeature? by lazyMonitored {
        // Push is disabled — no Firebase Messaging service available.
        logger.info("Push feature disabled (Firebase Messaging removed)")
        null
    }
}
