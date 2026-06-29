/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.utils

import android.net.Uri
import android.util.Log

/**
 * Redirects AO3 mirror site URLs to the official archiveofourown.org domain.
 *
 * Maintains a comprehensive list of known AO3 mirror domains and rewrites
 * any mirror URL to use the official domain while preserving the path and query.
 */
object Ao3UrlRedirector {

    private const val TAG = "Ao3UrlRedirector"
    private const val OFFICIAL_HOST = "archiveofourown.org"

    /**
     * All known AO3 official domains. These are NOT redirected.
     * Source: https://archiveofourown.gay/faq/accessing-fanworks
     */
    private val officialHosts = setOf(
        "archiveofourown.org",
        "www.archiveofourown.org",
        "archiveofourown.com",
        "www.archiveofourown.com",
        "archiveofourown.net",
        "www.archiveofourown.net",
        "archiveofourown.gay",
        "www.archiveofourown.gay",
        "ao3.org",
        "www.ao3.org",
        "archive.transformativeworks.org",
        "insecure.archiveofourown.org",
    )

    /**
     * All known AO3 mirror/proxy domains. These ARE redirected to archiveofourown.org.
     * Sources: community-maintained mirror lists, search results (2026).
     */
    private val mirrorHosts = setOf(
        "ao3.cubeart.club",
        "www.ao3.cubeart.club",
        "xiaozhan.icu",
        "www.xiaozhan.icu",
        "nightalk.xyz",
        "www.nightalk.xyz",
        "nightalk.top",
        "www.nightalk.top",
        "nightalk.cc",
        "www.nightalk.cc",
        "1.ao3-cn.top",
        "ao3-cn.top",
        "www.ao3-cn.top",
        "ao3.pw",
        "www.ao3.pw",
        "www.aothree.club",
        "aothree.club",
        "ao3mirror.com",
        "www.ao3mirror.com",
        "ao3mirror.site",
        "www.ao3mirror.site",
        "archiveofourown.mirror.net",
        "www.archiveofourown.mirror.net",
        "s.ao3l.live",
        "l.ao3l.live",
        "www.ao3l.live",
        "ao3l.live",
        "l.ao4.live",
        "www.ao4.live",
        "ao4.live",
        "ao3.akiba.ga",
        "www.ao3.akiba.ga",
        "ao3-v.akiba.ga",
        "www.ao3-v.akiba.ga",
        "ao3.site",
        "www.ao3.site",
        "ao3go.ao3.site",
        "login-no.ao3.site",
        "lyzw.xyz",
        "www.lyzw.xyz",
        "siterate.org",
        "www.siterate.org",
        "archiveofourown.org.siterate.org",
    )

    /**
     * All AO3-related hosts (official + mirrors).
     * Used for intent-filter matching.
     */
    val allAo3Hosts: Set<String> = officialHosts + mirrorHosts

    /**
     * Checks if a URL belongs to any AO3 domain (official or mirror).
     */
    fun isAo3Url(url: String): Boolean {
        return try {
            val host = Uri.parse(url).host?.lowercase() ?: return false
            host in officialHosts || host in mirrorHosts
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Redirects a mirror URL to the official archiveofourown.org domain.
     *
     * If the URL is already on an official domain, returns it unchanged.
     * If the URL is on a mirror domain, rewrites the host to archiveofourown.org
     * while preserving the path, query, and fragment.
     * If the URL is not AO3-related, returns it unchanged.
     *
     * @return The redirected URL, or the original if no redirect is needed.
     */
    fun redirect(url: String): String {
        return try {
            val uri = Uri.parse(url)
            val host = uri.host?.lowercase() ?: return url

            when {
                host in officialHosts -> {
                    // Already official, but normalize to archiveofourown.org
                    // (e.g. archiveofourown.com -> archiveofourown.org)
                    if (host == OFFICIAL_HOST || host == "www.$OFFICIAL_HOST") {
                        url
                    } else {
                        rewriteHost(uri, OFFICIAL_HOST)
                    }
                }
                host in mirrorHosts -> {
                    val redirected = rewriteHost(uri, OFFICIAL_HOST)
                    Log.d(TAG, "Redirected: $url -> $redirected")
                    redirected
                }
                else -> url
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to process URL: $url", e)
            url
        }
    }

    private fun rewriteHost(uri: Uri, newHost: String): String {
        val builder = uri.buildUpon()
        // Uri doesn't have a direct setHost, so we reconstruct
        val scheme = uri.scheme ?: "https"
        val port = if (uri.port != -1) ":${uri.port}" else ""
        val path = uri.path ?: ""
        val query = if (uri.query != null) "?${uri.query}" else ""
        val fragment = if (uri.fragment != null) "#${uri.fragment}" else ""

        return "$scheme://$newHost$port$path$query$fragment"
    }
}
