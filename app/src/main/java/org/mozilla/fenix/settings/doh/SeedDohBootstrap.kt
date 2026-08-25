/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.settings.doh

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mozilla.components.concept.engine.Engine
import org.minidns.dnsmessage.DnsMessage
import org.minidns.dnsmessage.Question
import org.minidns.hla.DnssecResolverApi
import org.minidns.record.Record
import org.minidns.record.TXT
import org.mozilla.fenix.utils.Settings
import java.net.HttpsURLConnection
import java.net.URI
import java.time.Instant
import kotlin.random.Random

/**
 * Loads the DoH bootstrap list only when MiniDNS has authenticated the TXT RRset.
 * A failed DNSSEC lookup, malformed payload, expired seed, or failed HTTPS probe leaves Settings
 * untouched. The resolver validates the DNSSEC chain from its bundled root trust anchor.
 */
internal object SeedDohBootstrap {
    private const val seedName = "seed.xn--pn1aul.eu.org"
    private const val maxCandidates = 16
    private const val bootstrapTimeoutMs = 4_000L

    fun resolveAndApply(settings: Settings) {
        // Engine creation is synchronous, but DNS must never run on Android's main thread.
        val selected = runBlocking {
            withTimeoutOrNull(bootstrapTimeoutMs) {
                withContext(Dispatchers.IO) {
                    val seed = runCatching { authenticatedTxtRecords() }
                        .getOrNull()
                        ?.let { SeedDohPayload.parse(it, Instant.now()) }
                        ?: return@withContext null
                    seed.candidates.shuffled(Random.Default).firstOrNull(::isHealthy)
                }
            }
        } ?: return
        settings.dohProviderUrl = selected.url
        settings.setDohSettingsMode(Engine.DohSettingsMode.MAX)
    }

    internal fun selectHealthyCandidate(
        txtRecords: Collection<String>,
        now: Instant,
        healthCheck: (SeedDohCandidate) -> Boolean,
        random: Random = Random.Default,
    ): SeedDohCandidate? {
        val seed = SeedDohPayload.parse(txtRecords, now) ?: return null
        return seed.candidates.shuffled(random).firstOrNull(healthCheck)
    }

    private fun authenticatedTxtRecords(): List<String> {
        val result = DnssecResolverApi.INSTANCE.resolveDnssecReliable(seedName, TXT::class.java)
        check(result.wasSuccessful() && result.isAuthenticData) { "Seed TXT was not DNSSEC authenticated" }
        return result.answers.map { it.characterStrings.joinToString(separator = "") }
    }

    private fun isHealthy(candidate: SeedDohCandidate): Boolean = runCatching {
        val question = Question(candidate.probeName, Record.TYPE.A)
        val query = question.asQueryMessage().toArray()
        val encodedQuery = Base64.encodeToString(query, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val separator = if (candidate.url.contains('?')) '&' else '?'
        val connection = (URI("${candidate.url}${separator}dns=$encodedQuery").toURL().openConnection() as HttpsURLConnection).apply {
            connectTimeout = 1_500
            readTimeout = 1_500
            requestMethod = "GET"
            setRequestProperty("Accept", "application/dns-message")
        }
        try {
            connection.responseCode == HttpsURLConnection.HTTP_OK &&
                connection.contentType?.startsWith("application/dns-message") == true &&
                connection.inputStream.use {
                    val response = DnsMessage(it.readBytes())
                    response.qr && response.responseCode == DnsMessage.RESPONSE_CODE.NO_ERROR && response.questions == listOf(question)
                }
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(false)

    @Serializable
    private data class SeedDocument(
        val version: Int,
        @SerialName("ttl_seconds") val ttlSeconds: Long,
        @SerialName("expires_at") val expiresAt: String,
        val doh: List<SeedDohCandidate>,
    )

    @Serializable
    internal data class SeedDohCandidate(
        val url: String,
        @SerialName("probe_name") val probeName: String,
        @SerialName("probe_type") val probeType: String,
    )

    internal data class SeedDohPayload(val candidates: List<SeedDohCandidate>) {
        companion object {
            private val json = Json { ignoreUnknownKeys = true }

            fun parse(txtRecords: Collection<String>, now: Instant): SeedDohPayload? = runCatching {
                val records = txtRecords.associate { record ->
                    val separator = record.indexOf('=')
                    check(separator > 0) { "Malformed seed TXT" }
                    record.substring(0, separator) to record.substring(separator + 1)
                }
                val meta = records["doh1meta"]?.split(';')?.associate {
                    val separator = it.indexOf(':')
                    check(separator > 0) { "Malformed seed metadata" }
                    it.substring(0, separator) to it.substring(separator + 1)
                } ?: error("Missing seed metadata")
                check(meta["v"] == "1") { "Unsupported seed version" }
                val parts = meta["parts"]?.toIntOrNull() ?: error("Missing part count")
                check(parts in 1..32) { "Invalid part count" }
                check(records.size == parts + 1) { "Unexpected seed TXT records" }
                val encoded = (0 until parts).joinToString(separator = "") { index ->
                    records["doh1.$index"] ?: error("Missing seed part")
                }
                val document = json.decodeFromString<SeedDocument>(
                    Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP).decodeToString(),
                )
                check(document.version == 1 && document.ttlSeconds in 60..86_400)
                check(Instant.parse(document.expiresAt).isAfter(now)) { "Expired seed" }
                check(document.doh.size in 1..maxCandidates)
                val candidates = document.doh.onEach(::validateCandidate).distinctBy { it.url }
                check(candidates.isNotEmpty())
                SeedDohPayload(candidates)
            }.getOrNull()

            private fun validateCandidate(candidate: SeedDohCandidate) {
                val uri = URI(candidate.url)
                check(uri.scheme == "https" && uri.host != null && uri.userInfo == null)
                check(uri.fragment == null && candidate.probeType == "A")
                check(candidate.probeName.matches(Regex("[A-Za-z0-9.-]{1,253}")))
            }
        }
    }
}