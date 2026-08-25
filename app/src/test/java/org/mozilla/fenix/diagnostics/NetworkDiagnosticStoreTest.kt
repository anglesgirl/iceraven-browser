/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NetworkDiagnosticStoreTest {

    @Test
    fun `records a rewritten request without query parameters`() {
        val store = NetworkDiagnosticStore(capacity = 2)

        store.recordRewrite(
            sourceUrl = "https://abs-0.twimg.com/assets/app.js?token=secret#fragment",
            targetUrl = "https://abs.twimg.com/assets/app.js?session=private",
            resourceType = "script",
        )

        assertEquals(
            listOf(
                NetworkDiagnosticEvent.Rewrite(
                    sourceUrl = "https://abs-0.twimg.com/assets/app.js",
                    targetUrl = "https://abs.twimg.com/assets/app.js",
                    resourceType = "script",
                ),
            ),
            store.events(),
        )
    }

    @Test
    fun `keeps only the newest events within capacity`() {
        val store = NetworkDiagnosticStore(capacity = 2)

        store.recordNavigationError("https://one.example/path?secret=1", "connection refused")
        store.recordNavigationError("https://two.example/path?secret=2", "timeout")
        store.recordNavigationError("https://three.example/path?secret=3", "tls failure")

        assertEquals(
            listOf(
                NetworkDiagnosticEvent.NavigationError("https://two.example/path", "timeout"),
                NetworkDiagnosticEvent.NavigationError("https://three.example/path", "tls failure"),
            ),
            store.events(),
        )
    }

    @Test
    fun `rejects a non-positive capacity`() {
        assertThrows(IllegalArgumentException::class.java) {
            NetworkDiagnosticStore(capacity = 0)
        }
    }
}