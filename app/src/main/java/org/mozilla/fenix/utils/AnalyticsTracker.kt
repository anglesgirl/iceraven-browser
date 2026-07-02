/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.utils

import android.app.Activity
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.microsoft.clarity.Clarity
import com.microsoft.clarity.ClarityConfig
import com.microsoft.clarity.models.LogLevel

/**
 * Analytics tracker using Microsoft Clarity SDK.
 *
 * Provides session recordings, heatmaps, and custom event tracking.
 * Events are uploaded automatically by the Clarity SDK.
 *
 * IMPORTANT: Clarity must be initialized from an Activity (not Application)
 * context, and after WorkManager initialization. See Clarity docs:
 * "Add the following code to your startup activity only"
 * "If you use a custom WorkManager initializer, Clarity initialization
 * must take place after the WorkManager initializer."
 */
object AnalyticsTracker {

    private const val TAG = "AnalyticsTracker"
    private const val CLARITY_PROJECT_ID = "xfqtr4s6xh"

    @Volatile
    private var initialized = false

    /**
     * Initialize the tracker. Must be called from the startup Activity's onCreate(),
     * after super.onCreate() and after WorkManager is initialized.
     */
    fun init(activity: Activity) {
        if (initialized) return
        try {
            val config = ClarityConfig(
                projectId = CLARITY_PROJECT_ID,
                logLevel = LogLevel.Verbose,
            )
            Clarity.initialize(activity, config)
            initialized = true

            // Track app foreground/background via ProcessLifecycleOwner
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    trackEvent("app_foreground")
                }

                override fun onStop(owner: LifecycleOwner) {
                    trackEvent("app_background")
                }
            })

            Log.d(TAG, "Microsoft Clarity initialized with lifecycle tracking")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize Microsoft Clarity", e)
        }
    }

    /**
     * Track an event.
     *
     * Clarity custom events don't support key-value parameters directly,
     * so params are sent as custom tags before the event.
     *
     * @param eventName The event name (e.g. "app_open", "ao3_search")
     * @param params Optional event parameters as key-value pairs
     */
    fun trackEvent(eventName: String, params: Map<String, String> = emptyMap()) {
        if (!initialized) {
            Log.w(TAG, "Clarity not initialized - skipping event: $eventName")
            return
        }

        try {
            // Send params as custom tags
            for ((key, value) in params) {
                Clarity.setCustomTag(key, value)
            }
            Clarity.sendCustomEvent(eventName)
            Log.d(TAG, "Clarity event: $eventName (params: $params)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to log Clarity event: $eventName", e)
        }
    }

    /**
     * Track a screen view.
     *
     * @param screenName The name of the screen (e.g. "settings", "browser")
     * @param screenClass Optional class name (ignored by Clarity, kept for API compat)
     */
    fun trackScreenView(screenName: String, screenClass: String = screenName) {
        if (!initialized) return
        try {
            Clarity.setCurrentScreenName(screenName)
            Log.d(TAG, "Clarity screen view: $screenName")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to log screen view: $screenName", e)
        }
    }
}
