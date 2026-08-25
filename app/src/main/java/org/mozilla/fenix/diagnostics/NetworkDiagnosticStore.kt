/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.diagnostics

import android.net.Uri

sealed class NetworkDiagnosticEvent {
    data class Rewrite(
        val sourceUrl: String,
        val targetUrl: String,
        val resourceType: String,
    ) : NetworkDiagnosticEvent()

    data class NavigationError(
        val url: String,
        val error: String,
    ) : NetworkDiagnosticEvent()
}

/** In-memory developer diagnostics. URLs never retain queries or fragments. */
class NetworkDiagnosticStore(private val capacity: Int = DEFAULT_CAPACITY) {
    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    private val entries = ArrayDeque<NetworkDiagnosticEvent>()

    @Synchronized
    fun recordRewrite(sourceUrl: String, targetUrl: String, resourceType: String) {
        add(
            NetworkDiagnosticEvent.Rewrite(
                sourceUrl = sanitizeUrl(sourceUrl),
                targetUrl = sanitizeUrl(targetUrl),
                resourceType = resourceType,
            ),
        )
    }

    @Synchronized
    fun recordNavigationError(url: String, error: String) {
        add(NetworkDiagnosticEvent.NavigationError(sanitizeUrl(url), error))
    }

    @Synchronized
    fun events(): List<NetworkDiagnosticEvent> = entries.toList()

    @Synchronized
    fun clear() = entries.clear()

    private fun add(event: NetworkDiagnosticEvent) {
        while (entries.size >= capacity) entries.removeFirst()
        entries.addLast(event)
    }

    private fun sanitizeUrl(value: String): String = runCatching {
        Uri.parse(value).buildUpon().clearQuery().fragment(null).build().toString()
    }.getOrDefault("")

    private companion object {
        const val DEFAULT_CAPACITY = 100
    }
}

object NetworkDiagnostics {
    val store = NetworkDiagnosticStore()
}