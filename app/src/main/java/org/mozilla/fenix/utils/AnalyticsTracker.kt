/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.utils

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Analytics tracker using Firebase Analytics SDK.
 *
 * Tracks key user interactions to provide consistent analytics data.
 * Events are automatically batched and uploaded by the Firebase SDK.
 */
object AnalyticsTracker {

    private const val TAG = "AnalyticsTracker"

    private var firebaseAnalytics: FirebaseAnalytics? = null

    /**
     * Initialize the tracker. Call this from Application.onCreate().
     * Also registers a process lifecycle observer to track app foreground/background.
     */
    fun init(context: Context) {
        if (firebaseAnalytics != null) return
        try {
            firebaseAnalytics = FirebaseAnalytics.getInstance(context).apply {
                // Explicitly enable analytics collection
                setAnalyticsCollectionEnabled(true)
            }

            // Track app foreground/background via ProcessLifecycleOwner
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    // App came to foreground
                    trackEvent("app_foreground")
                }

                override fun onStop(owner: LifecycleOwner) {
                    // App went to background — Firebase SDK flushes queued events on background
                    trackEvent("app_background")
                }
            })

            Log.d(TAG, "Firebase Analytics initialized with lifecycle tracking")
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

    /**
     * Track a screen view.
     *
     * @param screenName The name of the screen (e.g. "settings", "browser")
     * @param screenClass Optional class name of the screen
     */
    fun trackScreenView(screenName: String, screenClass: String = screenName) {
        val analytics = firebaseAnalytics ?: return
        try {
            val bundle = Bundle().apply {
                putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
                putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenClass)
            }
            analytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
            Log.d(TAG, "Screen view: $screenName")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to log screen view: $screenName", e)
        }
    }
}
