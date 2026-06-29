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
 * Setup:
 * 1. Go to Firebase Console > Analytics > Data Streams
 * 2. Select your Android app
 * 3. Copy the Measurement ID (G-XXXXXXXXXX)
 * 4. Go to Measurement Protocol API Secrets > Create a new secret
 * 5. Fill in MEASUREMENT_ID and API_SECRET below
 */
object AnalyticsTracker {

    private const val TAG = "AnalyticsTracker"

    // TODO: Fill in your Measurement ID from Firebase Console
    // Firebase Console > Analytics > Data Streams > [your app] > Measurement ID
    private const val MEASUREMENT_ID = "G-PLACEHOLDER"

    // TODO: Fill in your API Secret from Firebase Console
    // Firebase Console > Analytics > Data Streams > [your app] > Measurement Protocol API Secrets
    private const val API_SECRET = "PLACEHOLDER"

    private const val ENDPOINT =
        "https://www.google-analytics.com/mp/collect"

    // Persistent client ID - generated once per install
    private const val PREFS_NAME = "analytics_prefs"
    private const val CLIENT_ID_KEY = "client_id"

    private var clientId: String? = null

    /**
     * Initialize the tracker. Call this from Application.onCreate().
     * Generates or retrieves a persistent client ID.
     */
    fun init(context: Context) {
        if (clientId != null) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        clientId = prefs.getString(CLIENT_ID_KEY, null) ?: run {
            val id = UUID.randomUUID().toString()
            prefs.edit().putString(CLIENT_ID_KEY, id).apply()
            id
        }
        Log.d(TAG, "AnalyticsTracker initialized with client ID: $clientId")
    }

    /**
     * Track an event. Safe to call from any thread - network request runs in background.
     *
     * @param eventName The event name (e.g. "app_open", "ao3_search")
     * @param params Optional event parameters as key-value pairs
     */
    fun trackEvent(eventName: String, params: Map<String, String> = emptyMap()) {
        if (MEASUREMENT_ID == "G-PLACEHOLDER" || API_SECRET == "PLACEHOLDER") {
            Log.w(TAG, "AnalyticsTracker not configured - skipping event: $eventName")
            return
        }

        val cid = clientId ?: run {
            Log.w(TAG, "AnalyticsTracker not initialized - skipping event: $eventName")
            return
        }

        thread(name = "analytics-$eventName", start = true) {
            try {
                sendEvent(cid, eventName, params)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send analytics event: $eventName", e)
            }
        }
    }

    private fun sendEvent(clientId: String, eventName: String, params: Map<String, String>) {
        val url = URL("$ENDPOINT?measurement_id=$MEASUREMENT_ID&api_secret=$API_SECRET")

        val jsonParams = JSONObject()
        for ((key, value) in params) {
            jsonParams.put(key, value)
        }

        val event = JSONObject().apply {
            put("name", eventName)
            put("params", jsonParams)
        }

        val requestBody = JSONObject().apply {
            put("client_id", clientId)
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
