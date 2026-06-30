/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.usecases

import android.content.Context
import android.widget.Toast
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import mozilla.components.browser.state.search.SearchEngine
import mozilla.components.concept.base.profiler.Profiler
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.storage.HistoryMetadataKey
import mozilla.components.feature.search.SearchUseCases
import mozilla.components.feature.session.SessionUseCases
import mozilla.components.feature.tabs.TabsUseCases
import mozilla.components.support.ktx.kotlin.isUrl
import mozilla.components.support.ktx.kotlin.toNormalizedUrl
import org.mozilla.fenix.components.AppStore
import org.mozilla.fenix.utils.Ao3UrlRedirector

/**
 * Use cases for handling loading a URL and performing a search.
 *
 * @param appStore [AppStore] used to fetch the appstore
 * @param tabsUseCases [TabsUseCases] used for adding new tabs.
 * @param loadUrlUseCase [SessionUseCases.DefaultLoadUrlUseCase] used for loading a URL.
 * @param searchUseCases [SearchUseCases] retained for compatibility with existing callers.
 * @param homepageTitle The title of the new homepage tab.
 * @param profiler [Profiler] used to add profiler markers.
 */
class FenixBrowserUseCases(
    private val appStore: AppStore,
    private val tabsUseCases: TabsUseCases,
    private val loadUrlUseCase: SessionUseCases.DefaultLoadUrlUseCase,
    @Suppress("UnusedPrivateProperty")
    private val searchUseCases: SearchUseCases,
    private val homepageTitle: String,
    private val profiler: Profiler?,
    private val context: Context,
) {
    private val ao3HomeUrl = "https://archiveofourown.org/"
    private val ao3SearchUrlPrefix = "https://archiveofourown.org/works/search?work_search%5Bquery%5D="

    /**
     * Loads a URL or performs a search depending on the value of [searchTermOrURL].
     *
     * @param searchTermOrURL The entered search term to search or URL to be loaded.
     * @param newTab Whether or not to load the URL in a new tab.
     * @param private Whether or not the tab should be private.
     * @param forceSearch Whether or not to force performing a search.
     * @param searchEngine Ignored. AO3 Browser always sends search terms to AO3 search.
     * @param flags Flags that will be used when loading the URL (not applied to searches).
     * @param historyMetadata The [HistoryMetadataKey] of the new tab in case this tab
     * was opened from history.
     * @param additionalHeaders Ignored for AO3 search.
     */
    @Suppress("CognitiveComplexMethod", "UNUSED_PARAMETER")
    fun loadUrlOrSearch(
        searchTermOrURL: String,
        newTab: Boolean,
        private: Boolean = appStore.state.mode.isPrivate,
        forceSearch: Boolean = false,
        searchEngine: SearchEngine? = null,
        flags: EngineSession.LoadUrlFlags = EngineSession.LoadUrlFlags.none(),
        historyMetadata: HistoryMetadataKey? = null,
        additionalHeaders: Map<String, String>? = null,
    ) {
        val startTime = profiler?.getProfilerTime()

        if (!forceSearch && searchTermOrURL.isUrl()) {
            // AO3 Browser: redirect AO3 mirror domains to the official domain.
            // Non-AO3 URLs are allowed through without redirection (address bar
            // has already been customized to limit user input).
            val finalUrl = Ao3UrlRedirector.redirect(searchTermOrURL).toNormalizedUrl()
            if (newTab) {
                tabsUseCases.addTab.invoke(
                    url = finalUrl,
                    flags = flags,
                    private = private,
                    historyMetadata = historyMetadata,
                    originalInput = searchTermOrURL,
                )
            } else {
                loadUrlUseCase.invoke(
                    url = finalUrl,
                    flags = flags,
                    originalInput = searchTermOrURL,
                )
            }
        } else {
            val ao3SearchUrl = ao3SearchUrlPrefix + URLEncoder.encode(
                searchTermOrURL,
                StandardCharsets.UTF_8.toString(),
            )

            if (newTab) {
                tabsUseCases.addTab.invoke(
                    url = ao3SearchUrl,
                    flags = flags,
                    private = private,
                    historyMetadata = historyMetadata,
                    originalInput = searchTermOrURL,
                )
            } else {
                loadUrlUseCase.invoke(
                    url = ao3SearchUrl,
                    flags = flags,
                    originalInput = searchTermOrURL,
                )
            }
        }

        if (profiler?.isProfilerActive() == true) {
            // Wrapping the `addMarker` method with `isProfilerActive` even though it's no-op when
            // profiler is not active. That way, `text` argument will not create a string builder
            // all the time.
            profiler.addMarker(
                markerName = "FenixBrowserUseCases.loadUrlOrSearch",
                startTime = startTime,
                text = "newTab: $newTab, private: $private",
            )
        }
    }

    /**
     * Adds a new AO3 homepage tab.
     *
     * @param private Whether or not the new homepage tab should be private.
     * @return The ID of the created tab.
     */
    fun addNewHomepageTab(private: Boolean = appStore.state.mode.isPrivate): String {
        return tabsUseCases.addTab.invoke(
            url = ao3HomeUrl,
            title = homepageTitle,
            private = private,
        )
    }

    /**
     * Adds a new AO3 homepage tab to the provided tab group.
     *
     * @param group The ID of the group.
     */
    fun addNewHomepageTabInGroup(
        group: String,
    ) {
        val tabId = addNewHomepageTab()
        tabsUseCases.addTabsInGroup(
            group = group,
            tabId = tabId,
        )
    }

    /**
     * Loads the AO3 homepage.
     */
    fun navigateToHomepage() {
        loadUrlUseCase.invoke(url = ao3HomeUrl)
    }
}
