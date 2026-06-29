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
 *
 * Debug mode is always enabled so events appear in Firebase DebugView without adb.
 * To disable for production, set [DEBUG_MODE] to false.
 */
object AnalyticsTracker {

    private const val TAG = "AnalyticsTracker"

    /** When true, events stream to Firebase DebugView in real-time without needing adb. */
    private const val DEBUG_MODE = true

    private var firebaseAnalytics: FirebaseAnalytics? = null

    /**
     * Initialize the tracker. Call this from Application.onCreate().
     */
    fun init(context: Context) {
        if (firebaseAnalytics != null) return
        try {
            firebaseAnalytics = FirebaseAnalytics.getInstance(context)

            // Enable debug mode so events appear in Firebase DebugView without adb.
            // In debug mode, events are sent immediately instead of batched/delayed.
            if (DEBUG_MODE) {
                val bundle = Bundle()
                bundle.putLong("debug_mode", 1)
                firebaseAnalytics!!.setUserProperty("debug_mode", "true")
                // Force enable analytics collection (in case disabled by default)
                firebaseAnalytics!!.setAnalyticsCollectionEnabled(true)
                Log.d(TAG, "Firebase Analytics initialized (DEBUG MODE - events visible in DebugView)")
            } else {
                firebaseAnalytics!!.setAnalyticsCollectionEnabled(true)
                Log.d(TAG, "Firebase Analytics initialized")
            }
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
            Log.d(TAG, "Analytics event logged: $eventName (params: $params)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to log analytics event: $eventName", e)
        }
    }
}
