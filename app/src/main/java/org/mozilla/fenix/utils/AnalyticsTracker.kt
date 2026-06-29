/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.utils

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Analytics tracker using Firebase Analytics SDK.
 *
 * Uses the official Firebase SDK for reliable data collection.
 * The SDK handles app_instance_id generation, offline caching, and batching automatically.
 */
object AnalyticsTracker {

    private const val TAG = "AnalyticsTracker"

    private var firebaseAnalytics: FirebaseAnalytics? = null

    /**
     * Initialize the tracker. Call this from Application.onCreate().
     */
    fun init(context: Context) {
        if (firebaseAnalytics != null) return
        try {
            firebaseAnalytics = FirebaseAnalytics.getInstance(context)
            Log.d(TAG, "Firebase Analytics initialized")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize Firebase Analytics", e)
        }
    }

    /**
     * Track an event.
     *
     * @param eventName The event name (e.g. "app_open", "ao3_search")
     * @param params Optional event parameters as key-value pairs
     */
    fun trackEvent(eventName: String, params: Map<String, String> = emptyMap()) {
        val analytics = firebaseAnalytics ?: run {
            Log.w(TAG, "Firebase Analytics not initialized - skipping event: $eventName")
            return
        }

        try {
            val bundle = Bundle()
            for ((key, value) in params) {
                bundle.putString(key, value)
            }
            analytics.logEvent(eventName, bundle)
            Log.d(TAG, "Analytics event logged: $eventName")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to log analytics event: $eventName", e)
        }
    }
}
