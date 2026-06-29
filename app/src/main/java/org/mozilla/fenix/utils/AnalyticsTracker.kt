/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.utils

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.concurrent.thread

/**
 * Lightweight analytics tracker using Google Analytics Measurement Protocol v2.
 *
 * No SDK dependency required - sends HTTP POST requests directly to Google Analytics.
 * This avoids dependency conflicts with Mozilla android-components.
 *
 * For app data streams (Firebase), use firebase_app_id instead of measurement_id.
 * Reference: https://developers.google.cn/analytics/devguides/collection/protocol/ga4/sending-events
 */
object AnalyticsTracker {

    private const val TAG = "AnalyticsTracker"

    // Firebase App ID from google-services.json
    private const val FIREBASE_APP_ID = "1:853194114797:android:44903d99ebaff5024baee4"

    // API Secret from Google Analytics > Data Streams > Measurement Protocol API secrets
    private const val API_SECRET = "DmEqfUM1RPWmXS581HVfdA"

    private const val ENDPOINT =
        "https://www.google-analytics.com/mp/collect"

    // Persistent app instance ID - generated once per install
    private const val PREFS_NAME = "analytics_prefs"
    private const val INSTANCE_ID_KEY = "app_instance_id"

    private var appInstanceId: String? = null

    /**
     * Initialize the tracker. Call this from Application.onCreate().
     * Generates or retrieves a persistent app instance ID.
     */
    fun init(context: Context) {
        if (appInstanceId != null) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        appInstanceId = prefs.getString(INSTANCE_ID_KEY, null) ?: run {
            val id = UUID.randomUUID().toString()
            prefs.edit().putString(INSTANCE_ID_KEY, id).apply()
            id
        }
        Log.d(TAG, "AnalyticsTracker initialized with app instance ID: $appInstanceId")
    }

    /**
     * Track an event. Safe to call from any thread - network request runs in background.
     *
     * @param eventName The event name (e.g. "app_open", "ao3_search")
     * @param params Optional event parameters as key-value pairs
     */
    fun trackEvent(eventName: String, params: Map<String, String> = emptyMap()) {
        val instanceId = appInstanceId ?: run {
            Log.w(TAG, "AnalyticsTracker not initialized - skipping event: $eventName")
            return
        }

        thread(name = "analytics-$eventName", start = true) {
            try {
                sendEvent(instanceId, eventName, params)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send analytics event: $eventName", e)
            }
        }
    }

    private fun sendEvent(appInstanceId: String, eventName: String, params: Map<String, String>) {
        // For app data streams, use firebase_app_id instead of measurement_id
        val url = URL("$ENDPOINT?firebase_app_id=$FIREBASE_APP_ID&api_secret=$API_SECRET")

        val jsonParams = JSONObject()
        for ((key, value) in params) {
            jsonParams.put(key, value)
        }

        val event = JSONObject().apply {
            put("name", eventName)
            put("params", jsonParams)
        }

        // For app data streams, use app_instance_id instead of client_id
        val requestBody = JSONObject().apply {
            put("app_instance_id", appInstanceId)
            put("events", JSONArray().put(event))
        }

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10000
            readTimeout = 10000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }

        try {
            val outputStream: OutputStream = connection.outputStream
            outputStream.write(requestBody.toString().toByteArray(Charsets.UTF_8))
            outputStream.flush()
            outputStream.close()

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                Log.d(TAG, "Analytics event sent: $eventName (response: $responseCode)")
            } else {
                Log.w(TAG, "Analytics event failed: $eventName (response: $responseCode)")
            }
        } finally {
            connection.disconnect()
        }
    }
}
