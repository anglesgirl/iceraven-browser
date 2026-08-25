/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.settings.doh

import android.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import kotlin.random.Random

class SeedDohBootstrapTest {
    private val now = Instant.parse("2026-08-25T00:00:00Z")

    @Test
    fun `selects healthy HTTPS candidate from a complete current seed`() {
        val selected = SeedDohBootstrap.selectHealthyCandidate(
            txtRecords = records(document(expiresAt = "2099-01-01T00:00:00Z")),
            now = now,
            healthCheck = { it.url == "https://healthy.example/dns-query" },
            random = Random(1),
        )

        assertEquals("https://healthy.example/dns-query", selected?.url)
    }

    @Test
    fun `rejects expired seed before probing`() {
        var probed = false
        val selected = SeedDohBootstrap.selectHealthyCandidate(
            txtRecords = records(document(expiresAt = "2020-01-01T00:00:00Z")),
            now = now,
            healthCheck = { probed = true; true },
        )

        assertNull(selected)
        assertEquals(false, probed)
    }

    @Test
    fun `rejects incomplete seed before probing`() {
        var probed = false
        val seed = records(document(expiresAt = "2099-01-01T00:00:00Z")).filterNot { it.startsWith("doh1.1=") }
        val selected = SeedDohBootstrap.selectHealthyCandidate(seed, now, { probed = true; true })

        assertNull(selected)
        assertEquals(false, probed)
    }

    @Test
    fun `does not select when no candidate is healthy`() {
        val selected = SeedDohBootstrap.selectHealthyCandidate(
            records(document(expiresAt = "2099-01-01T00:00:00Z")),
            now,
            healthCheck = { false },
        )

        assertNull(selected)
    }

    private fun document(expiresAt: String) = """{"version":1,"ttl_seconds":900,"expires_at":"$expiresAt","doh":[{"url":"https://healthy.example/dns-query","probe_name":"example.com","probe_type":"A"},{"url":"https://other.example/dns-query","probe_name":"example.com","probe_type":"A"}]}"""

    private fun records(document: String): List<String> {
        val encoded = Base64.encodeToString(document.encodeToByteArray(), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val midpoint = encoded.length / 2
        return listOf(
            "doh1.0=${encoded.substring(0, midpoint)}",
            "doh1.1=${encoded.substring(midpoint)}",
            "doh1meta=parts:2;v:1",
        )
    }
}