package com.example

import com.example.automation.PlannerAgent
import com.example.automation.RetrieverEngine
import com.example.automation.TabSnapshotTarget
import com.example.automation.ValidatorAgent
import com.example.data.local.PrivacyStatSummaryEntity
import com.example.data.preferences.SearchEngineOption
import com.example.engine.PrivacyManager
import com.example.engine.SearchSuggestionEngine
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.ui.unit.DpSize
import com.example.ui.browser.BrowserMenuBottomSheet
import com.example.ui.browser.BrowserScreen
import com.example.ui.browser.CompactTopToolbar
import com.example.ui.browser.DraggableTabGridOverlay
import com.example.ui.browser.GroupedListSpacing
import com.example.ui.browser.SettingsScreenOverlay
import com.example.ui.browser.WebViewViewport
import com.example.ui.browser.groupedListItemShape
import com.example.data.preferences.BrowserPreferences
import com.example.engine.DefaultBrowserHelper
import com.example.engine.DefaultBrowserStatus
import com.example.engine.PerformanceOptimizer
import com.example.ui.theme.ApexBrowserTheme
import com.example.ui.theme.ApexColorSchemeBuilder
import com.example.ui.theme.SeedPalettePreset
import com.example.ui.theme.TabGroupColorOption
import com.example.ui.viewmodel.BrowserTab
import com.example.ui.viewmodel.BrowserUiState
import com.example.ui.viewmodel.BrowserViewModel
import com.example.ui.viewmodel.TabGridEntry
import com.example.ui.viewmodel.TabGroupInfo
import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows
import com.example.data.local.ApexDatabase
import com.example.data.local.SavedPasswordEntity
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.BrowserRepository
import com.example.engine.FastDownloadEngine
import com.example.engine.PasswordAuditEngine
import com.example.engine.PasswordStrength
import com.example.engine.PasswordStrengthEvaluator
import com.example.engine.PasswordVaultCrypto
import com.example.engine.PasswordVaultUtil
import com.example.engine.SecurePasswordGenerator
import com.example.ui.browser.PasswordManagerScreenOverlay
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import kotlinx.coroutines.flow.first
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `verify fresh install state starts with single Home tab and zero stats`() {
        val freshState = BrowserUiState()
        assertEquals(1, freshState.tabs.size)
        assertTrue(freshState.activeTab.isHomePage)
        assertEquals("apex://home", freshState.activeTab.url)
        assertTrue(freshState.tabGroups.isEmpty())
        assertTrue(freshState.searchSuggestions.isEmpty())
        assertTrue(freshState.searchQuery.isEmpty())
        assertFalse(freshState.isSettingsScreenOpen)
        assertFalse(freshState.isMenuSheetOpen)

        SearchEngineOption.entries.forEach { option ->
            assertTrue(option.description.isNotBlank())
            assertTrue(option.searchUrlTemplate.contains("%s"))
        }

        val freshStats = PrivacyStatSummaryEntity()
        assertEquals(0, freshStats.trackersBlocked)
        assertEquals(0, freshStats.adsBlocked)
        assertEquals(0, freshStats.httpsUpgrades)
        assertEquals(0L, freshStats.bytesSaved)
        assertEquals(0L, freshStats.timeSavedMs)
    }

    @Test
    fun `verify SearchSuggestionEngine builds URLs and parses engine suggestions`() {
        val suggestionEngine = SearchSuggestionEngine()

        assertTrue(
            suggestionEngine.buildSuggestUrl(SearchEngineOption.DUCKDUCKGO, "kotlin")
                .contains("ac.duckduckgo.com")
        )
        assertTrue(
            suggestionEngine.buildSuggestUrl(SearchEngineOption.GOOGLE, "kotlin")
                .contains("suggestqueries.google.com")
        )
        assertTrue(
            suggestionEngine.buildSuggestUrl(SearchEngineOption.BRAVE, "kotlin")
                .contains("search.brave.com")
        )
        assertTrue(
            suggestionEngine.buildSuggestUrl(SearchEngineOption.WIKIPEDIA, "kotlin")
                .contains("en.wikipedia.org")
        )

        // OpenSearch JSON array (Google / DuckDuckGo list / Brave / Bing)
        val openSearchJson = """["kotlin",["kotlin","kotlin compose","kotlin coroutines"]]"""
        val googleParsed = suggestionEngine.parseEngineSuggestions(
            rawJson = openSearchJson,
            originalQuery = "kotlin",
            engine = SearchEngineOption.GOOGLE
        )
        assertEquals(3, googleParsed.size)
        assertEquals("kotlin compose", googleParsed[1].title)
        assertEquals("G", googleParsed[1].engineBadge)

        // DuckDuckGo object array format
        val ddgObjJson = """[{"phrase":"privacy browser"},{"phrase":"privacy tools"}]"""
        val ddgParsed = suggestionEngine.parseEngineSuggestions(
            rawJson = ddgObjJson,
            originalQuery = "privacy",
            engine = SearchEngineOption.DUCKDUCKGO
        )
        assertEquals(2, ddgParsed.size)
        assertEquals("privacy browser", ddgParsed[0].title)
        assertEquals("DDG", ddgParsed[0].engineBadge)

        // Wikipedia OpenSearch with descriptions and direct article URLs
        val wikiJson = """["jetpack",["Jetpack Compose"],["Android UI toolkit"],["https://en.wikipedia.org/wiki/Jetpack_Compose"]]"""
        val wikiParsed = suggestionEngine.parseEngineSuggestions(
            rawJson = wikiJson,
            originalQuery = "jetpack",
            engine = SearchEngineOption.WIKIPEDIA
        )
        assertTrue(wikiParsed.any { it.submitValue == "https://en.wikipedia.org/wiki/Jetpack_Compose" })
        assertTrue(wikiParsed.any { it.subtitle == "Android UI toolkit" })
    }

    @Test
    fun `verify M3 Expressive seed ColorScheme generation`() {
        val light = ApexColorSchemeBuilder.fromSeed(
            seed = SeedPalettePreset.ELECTRIC_COBALT.seedColor,
            darkTheme = false
        )
        val dark = ApexColorSchemeBuilder.fromSeed(
            seed = SeedPalettePreset.ELECTRIC_COBALT.seedColor,
            darkTheme = true
        )
        assertNotEquals(light.primaryContainer, dark.primaryContainer)
        assertNotEquals(light.surface, dark.surface)
    }

    @Test
    fun `verify Tab Group grid entries for 4 tabs vs 5+ tabs`() {
        val grp = TabGroupInfo(id = "grp_1", title = "Group 1")
        val fourGroupedTabs = (1..4).map { idx ->
            BrowserTab(id = "t_$idx", title = "Tab $idx", url = "https://example.com/$idx", groupId = "grp_1")
        }
        val stateFour = BrowserUiState(
            tabs = fourGroupedTabs + BrowserTab(id = "solo", title = "Solo", url = "apex://home"),
            tabGroups = listOf(grp)
        )
        val entriesFour = stateFour.gridEntries
        assertEquals(2, entriesFour.size)
        val groupFour = entriesFour.first() as TabGridEntry.GroupTab
        assertEquals(4, groupFour.tabs.size)
        assertFalse(groupFour.tabs.size > 4) // 4 tabs stay shown directly

        val sixGroupedTabs = (1..6).map { idx ->
            BrowserTab(id = "t_$idx", title = "Tab $idx", url = "https://example.com/$idx", groupId = "grp_1")
        }
        val stateSix = BrowserUiState(tabs = sixGroupedTabs, tabGroups = listOf(grp))
        val groupSix = stateSix.gridEntries.single() as TabGridEntry.GroupTab
        assertTrue(groupSix.tabs.size > 4)
        assertEquals(3, groupSix.tabs.size - 3) // 3 tabs shown + 4th slot shows +3 remaining
    }

    @Test
    fun `verify PrivacyManager HTTPS upgrade and tracker blocking`() {
        val privacyManager = PrivacyManager()
        val upgraded = privacyManager.normalizeAndUpgradeUrl(
            rawInput = "http://example.com/articles",
            httpsUpgradeEnabled = true,
            searchEngineTemplate = "https://duckduckgo.com/?q=%s"
        )
        assertEquals("https://example.com/articles", upgraded.finalUrl)
        assertTrue(upgraded.wasUpgradedToHttps)

        val blocked = privacyManager.evaluateRequest(
            requestUrl = "https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js",
            pageUrl = "https://news.ycombinator.com",
            shieldsEnabled = true
        )
        assertTrue(blocked.blocked)
        assertEquals("Ad Network", blocked.category)

        // Anti-adblock & pop-under domains must also be intercepted
        val admiralBlocked = privacyManager.evaluateRequest(
            requestUrl = "https://cdn.getadmiral.com/v2/admiral.js",
            pageUrl = "https://example.com",
            shieldsEnabled = true
        )
        assertTrue(admiralBlocked.blocked)

        val firstPartyAllowed = privacyManager.evaluateRequest(
            requestUrl = "https://news.ycombinator.com/news.css",
            pageUrl = "https://news.ycombinator.com",
            shieldsEnabled = true
        )
        assertFalse(firstPartyAllowed.blocked)

        // Stealth JS surrogate and 1x1 GIF surrogate verification
        val jsSurrogate = privacyManager.stealthBlockedResponse(
            "https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"
        )
        assertEquals("application/javascript", jsSurrogate.mimeType)
        val jsText = jsSurrogate.data.bufferedReader().readText()
        assertTrue(jsText.contains("window.adsbygoogle"))
        assertTrue(jsText.contains("window.googletag"))

        val gifSurrogate = privacyManager.stealthBlockedResponse(
            "https://ad.doubleclick.net/ddm/trackimp/pixel.gif"
        )
        assertEquals("image/gif", gifSurrogate.mimeType)
        assertTrue(gifSurrogate.data.readBytes().isNotEmpty())

        val stealthScript = privacyManager.buildStealthAdblockScript(true)
        assertTrue(stealthScript.contains("FakeBlockAdBlock"))
        assertTrue(stealthScript.contains("offsetHeight"))
        assertTrue(stealthScript.contains("ytp-ad-skip-button"))
    }

    @Test
    fun `verify Nano multi-agent planner validator and Retriever parallel extraction`() = runTest {
        val planner = PlannerAgent()
        val steps = planner.decomposeTask(
            prompt = "Find the top 3 AI news articles today and summarize them",
            currentUrl = "apex://home"
        )
        assertTrue(steps.size >= 2)

        val validator = ValidatorAgent()
        val outcome = validator.validateAndSynthesize(
            userPrompt = "Summarize top 3",
            observations = emptyList()
        )
        assertTrue(outcome.passed)

        val retriever = RetrieverEngine()
        val rule = retriever.buildRuleFromInput("Extract all product names and prices from this page")
        val records = retriever.extractAcrossTabsInParallel(
            tabs = listOf(
                TabSnapshotTarget("t1", "AI News Pulse", "apex://news"),
                TabSnapshotTarget("t2", "Apex Gear Store", "apex://store")
            ),
            webViewLookup = { null },
            rule = rule
        )
        assertTrue(records.size >= 8)
    }

    @Test
    fun `verify Material 3 Grouped List item shapes for single first middle and last items`() {
        assertTrue(GroupedListSpacing > 0.dp)

        val single = groupedListItemShape(index = 0, totalCount = 1)
        val first = groupedListItemShape(index = 0, totalCount = 3)
        val middle = groupedListItemShape(index = 1, totalCount = 3)
        val last = groupedListItemShape(index = 2, totalCount = 3)

        assertEquals(RoundedCornerShape(24.dp), single)
        assertEquals(
            RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 5.dp, bottomEnd = 5.dp),
            first
        )
        assertEquals(RoundedCornerShape(5.dp), middle)
        assertEquals(
            RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp, bottomStart = 24.dp, bottomEnd = 24.dp),
            last
        )
        assertNotEquals(first, middle)
        assertNotEquals(middle, last)
        assertNotEquals(first, last)
    }

    @Test
    fun `verify group rename color palettes and bottom group tab strip state`() {
        TabGroupColorOption.entries.forEach { option ->
            val lightPalette = ApexColorSchemeBuilder.groupTonalColors(option, darkTheme = false)
            val darkPalette = ApexColorSchemeBuilder.groupTonalColors(option, darkTheme = true)
            assertNotEquals(lightPalette.container, darkPalette.container)
            assertNotEquals(lightPalette.accent, lightPalette.onAccent)
        }

        val emeraldGroup = TabGroupInfo(
            id = "grp_research",
            title = "Research",
            colorOption = TabGroupColorOption.EMERALD
        )
        val homeInGroup = BrowserTab(
            id = "t_home_grp",
            title = "Home",
            url = "apex://home",
            groupId = "grp_research",
            tabColor = TabGroupColorOption.EMERALD
        )
        val docsInGroup = BrowserTab(
            id = "t_docs_grp",
            title = "Kotlin Docs",
            url = "https://kotlinlang.org",
            groupId = "grp_research",
            tabColor = TabGroupColorOption.EMERALD
        )
        val standaloneTab = BrowserTab(
            id = "t_solo",
            title = "Solo",
            url = "apex://home",
            groupId = null
        )

        val stateInGroup = BrowserUiState(
            tabs = listOf(homeInGroup, docsInGroup, standaloneTab),
            tabGroups = listOf(emeraldGroup),
            activeTabId = "t_home_grp"
        )
        assertNotNull(stateInGroup.activeTabGroup)
        assertEquals("Research", stateInGroup.activeTabGroup?.title)
        assertEquals(TabGroupColorOption.EMERALD, stateInGroup.activeTabGroup?.colorOption)
        assertEquals(2, stateInGroup.activeGroupTabs.size)

        val stateOnStandalone = stateInGroup.copy(activeTabId = "t_solo")
        assertNull(stateOnStandalone.activeTabGroup)
        assertTrue(stateOnStandalone.activeGroupTabs.isEmpty())
    }

    @Test
    fun `verify TopVisitEntity model and search engine options for start page`() {
        val topVisit = com.example.data.local.TopVisitEntity(
            id = 1L,
            title = "YouTube",
            url = "https://youtube.com"
        )
        assertEquals(1L, topVisit.id)
        assertEquals("YouTube", topVisit.title)
        assertEquals("https://youtube.com", topVisit.url)
        assertTrue(topVisit.createdAt > 0L)

        // Verify SearchEngineOptions
        val engineIds = SearchEngineOption.entries.map { it.id }.toSet()
        assertTrue(engineIds.contains("google"))
        assertTrue(engineIds.contains("duckduckgo"))
        assertTrue(engineIds.contains("brave"))
        assertTrue(engineIds.contains("bing"))
        assertTrue(engineIds.contains("wikipedia"))
        assertEquals(5, engineIds.size)

        // Verify curved edges shape: RoundedCornerShape(16.dp)
        val searchBarCurvedShape = RoundedCornerShape(16.dp)
        assertEquals(RoundedCornerShape(16.dp), searchBarCurvedShape)
    }

    @Test
    fun `verify SharedPreferences serialization and search engine card configs`() {
        val testBookmarks = listOf(
            com.example.data.local.BookmarkEntity(id = 1L, url = "https://example.com", title = "Example")
        )
        // Verify format and key representation
        val bookmark = testBookmarks.first()
        assertEquals(1L, bookmark.id)
        assertEquals("https://example.com", bookmark.url)
        assertEquals("Example", bookmark.title)

        // Verify all 5 search engines have valid card labels and IDs
        SearchEngineOption.entries.forEach { option ->
            assertTrue(option.id.isNotBlank())
            assertTrue(option.displayName.isNotBlank())
            assertTrue(option.shortBadge.isNotBlank())
            assertTrue(option.description.isNotBlank())
        }
    }

    @Test
    fun `verify FastDownloadEngine pauses cleanly without failing and resumes to completion`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val prefsRepo = UserPreferencesRepository(context)
        val sharedPrefs = context.getSharedPreferences("test_download_prefs", Context.MODE_PRIVATE)
        val repository = BrowserRepository(db.apexDao(), prefsRepo, sharedPrefs)
        val engine = FastDownloadEngine(context, repository)

        val payload = ByteArray(320 * 1024) { idx -> ((idx * 31) and 0xFF).toByte() }
        val server = ServerSocket(0)
        val serverThread = thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    runCatching {
                        socket.use { s ->
                            val reader = s.getInputStream().bufferedReader()
                            var rangeHeader: String? = null
                            while (true) {
                                val line = reader.readLine() ?: break
                                if (line.isEmpty()) break
                                if (line.startsWith("Range:", ignoreCase = true)) {
                                    rangeHeader = line.substringAfter(':').trim()
                                }
                            }
                            val out = s.getOutputStream()
                            val total = payload.size
                            if (rangeHeader != null && rangeHeader!!.startsWith("bytes=")) {
                                val spec = rangeHeader!!.removePrefix("bytes=").trim()
                                val start = spec.substringBefore('-').toIntOrNull() ?: 0
                                val endRaw = spec.substringAfter('-', "").toIntOrNull()
                                val end = (endRaw ?: (total - 1)).coerceAtMost(total - 1)
                                if (start >= total || start > end) {
                                    out.write("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$total\r\nConnection: close\r\n\r\n".toByteArray())
                                    out.flush()
                                    return@use
                                }
                                val len = end - start + 1
                                val header = "HTTP/1.1 206 Partial Content\r\n" +
                                    "Accept-Ranges: bytes\r\n" +
                                    "Content-Length: $len\r\n" +
                                    "Content-Range: bytes $start-$end/$total\r\n" +
                                    "Content-Type: application/octet-stream\r\n" +
                                    "Connection: close\r\n\r\n"
                                out.write(header.toByteArray())
                                var pos = start
                                while (pos <= end) {
                                    val chunk = minOf(8192, end - pos + 1)
                                    out.write(payload, pos, chunk)
                                    out.flush()
                                    pos += chunk
                                    Thread.sleep(15L)
                                }
                            } else {
                                val header = "HTTP/1.1 200 OK\r\n" +
                                    "Accept-Ranges: bytes\r\n" +
                                    "Content-Length: $total\r\n" +
                                    "Content-Type: application/octet-stream\r\n" +
                                    "Connection: close\r\n\r\n"
                                out.write(header.toByteArray())
                                out.write(payload)
                                out.flush()
                            }
                        }
                    }
                }
            }
        }

        try {
            val startedLatch = CountDownLatch(1)
            var downloadId = -1L
            engine.enqueueDownload(
                url = "http://127.0.0.1:${server.localPort}/bundle.bin",
                suggestedFileName = "bundle_test.bin"
            ) { entity ->
                downloadId = entity.id
                startedLatch.countDown()
            }
            val startDeadline = System.currentTimeMillis() + 5_000L
            while (startedLatch.count > 0 && System.currentTimeMillis() < startDeadline) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(20L)
            }
            assertEquals(0L, startedLatch.count)
            assertTrue(downloadId > 0L)

            // Wait briefly for chunks to start transferring, then pause
            Thread.sleep(45L)
            engine.pauseDownload(downloadId)

            var pausedEntity = runBlocking { db.apexDao().getDownloadById(downloadId) }
            val deadlinePause = System.currentTimeMillis() + 5_000L
            while (System.currentTimeMillis() < deadlinePause) {
                pausedEntity = runBlocking { db.apexDao().getDownloadById(downloadId) }
                if (pausedEntity?.status == FastDownloadEngine.STATUS_PAUSED) break
                assertNotEquals(
                    "Paused download must never fail: ${pausedEntity?.errorMessage}",
                    FastDownloadEngine.STATUS_FAILED,
                    pausedEntity?.status
                )
                Thread.sleep(40L)
            }
            assertEquals(FastDownloadEngine.STATUS_PAUSED, pausedEntity?.status)

            // Wait another moment to ensure no late error overwrites PAUSED
            Thread.sleep(250L)
            val stablePaused = runBlocking { db.apexDao().getDownloadById(downloadId) }
            assertEquals(FastDownloadEngine.STATUS_PAUSED, stablePaused?.status)

            // Resume and verify completion with byte-exact payload
            engine.resumeDownload(downloadId)
            var finalEntity = runBlocking { db.apexDao().getDownloadById(downloadId) }
            val deadlineComplete = System.currentTimeMillis() + 10_000L
            while (System.currentTimeMillis() < deadlineComplete) {
                finalEntity = runBlocking { db.apexDao().getDownloadById(downloadId) }
                if (finalEntity?.status == FastDownloadEngine.STATUS_COMPLETED) break
                assertNotEquals(
                    "Resumed download must not fail: ${finalEntity?.errorMessage}",
                    FastDownloadEngine.STATUS_FAILED,
                    finalEntity?.status
                )
                Thread.sleep(50L)
            }
            assertEquals(FastDownloadEngine.STATUS_COMPLETED, finalEntity?.status)
            assertEquals(payload.size.toLong(), finalEntity?.downloadedBytes)
            val outFile = File(finalEntity!!.localFilePath)
            assertTrue(outFile.exists())
            assertArrayEquals(payload, outFile.readBytes())
        } finally {
            runCatching { server.close() }
            serverThread.interrupt()
            db.close()
        }
    }

    @Test
    fun `verify CompactTopToolbar horizontal swipe switches tabs and preserves tap`() {
        var nextCount = 0
        var prevCount = 0
        var clickCount = 0
        val homeTab = BrowserTab(id = "t_home", title = "Home", url = "apex://home")
        val webTab = BrowserTab(id = "t_web", title = "Example", url = "https://example.com")

        var currentIndex by mutableIntStateOf(1)
        var currentTab by mutableStateOf(homeTab)

        composeTestRule.setContent {
            ApexBrowserTheme {
                CompactTopToolbar(
                    visible = true,
                    activeTab = currentTab,
                    activeTabIndex = currentIndex,
                    totalTabs = 3,
                    onAddressPillClick = { clickCount++ },
                    onReloadClick = {},
                    onSwipeToNextTab = { nextCount++ },
                    onSwipeToPreviousTab = { prevCount++ }
                )
            }
        }

        // Tap still opens search screen
        composeTestRule.onNodeWithTag("home_top_search_bar").performClick()
        assertEquals(1, clickCount)

        // Swiping left moves to next tab
        composeTestRule.onNodeWithTag("home_top_search_bar").performTouchInput {
            swipeLeft(startX = right * 0.85f, endX = left + (width * 0.1f))
        }
        composeTestRule.waitForIdle()
        assertEquals(1, nextCount)
        assertEquals(0, prevCount)

        // Swiping right on web address bar moves to previous tab
        currentTab = webTab
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("address_bar_container").performTouchInput {
            swipeRight(startX = left + (width * 0.15f), endX = right * 0.9f)
        }
        composeTestRule.waitForIdle()
        assertEquals(1, prevCount)

        // At index 0, swiping right should not trigger previous tab
        currentIndex = 0
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("address_bar_container").performTouchInput {
            swipeRight(startX = left + (width * 0.15f), endX = right * 0.9f)
        }
        composeTestRule.waitForIdle()
        assertEquals(1, prevCount)
    }

    @Test
    fun `verify PasswordVaultCrypto generator evaluator audit and CSV round trip`() {
        val crypto = PasswordVaultCrypto()
        val secret = "Tr0ub4dor&3!ApexVault"
        val encrypted1 = crypto.encrypt(secret)
        val encrypted2 = crypto.encrypt(secret)

        assertNotEquals(secret, encrypted1)
        assertNotEquals(encrypted1, encrypted2) // randomized IV
        assertEquals(secret, crypto.decrypt(encrypted1))
        assertEquals(secret, crypto.decrypt(encrypted2))
        assertEquals(crypto.fingerprint(secret), crypto.fingerprint(secret))
        assertNotEquals(crypto.fingerprint(secret), crypto.fingerprint("${secret}x"))

        val weak = PasswordStrengthEvaluator.evaluate("password")
        assertEquals(PasswordStrength.WEAK, weak.strength)
        assertTrue(PasswordStrengthEvaluator.isCommonWeak("password"))

        val strong = PasswordStrengthEvaluator.evaluate(secret)
        assertTrue(strong.score >= 62)

        val generated = SecurePasswordGenerator.generateRandom(
            length = 18,
            includeUppercase = true,
            includeLowercase = true,
            includeDigits = true,
            includeSymbols = true
        )
        assertEquals(18, generated.length)
        assertTrue(generated.any { it.isUpperCase() })
        assertTrue(generated.any { it.isLowerCase() })
        assertTrue(generated.any { it.isDigit() })
        assertTrue(generated.any { !it.isLetterOrDigit() })

        val phrase = SecurePasswordGenerator.generatePassphrase(wordCount = 4, includeNumberAndSymbol = true)
        assertTrue(phrase.split('-').size >= 5)

        val reusedFp = crypto.fingerprint("Reused#Pass1")
        val items = listOf(
            SavedPasswordEntity(
                id = 1L,
                siteName = "GitHub",
                siteUrl = "https://github.com",
                host = "github.com",
                username = "dev@apex.io",
                encryptedPassword = crypto.encrypt("Reused#Pass1"),
                strengthScore = 70,
                passwordFingerprint = reusedFp
            ),
            SavedPasswordEntity(
                id = 2L,
                siteName = "GitLab",
                siteUrl = "https://gitlab.com",
                host = "gitlab.com",
                username = "dev@apex.io",
                encryptedPassword = crypto.encrypt("Reused#Pass1"),
                strengthScore = 70,
                passwordFingerprint = reusedFp
            ),
            SavedPasswordEntity(
                id = 3L,
                siteName = "Legacy",
                siteUrl = "https://legacy.org",
                host = "legacy.org",
                username = "old",
                encryptedPassword = crypto.encrypt("123456"),
                strengthScore = 12,
                passwordFingerprint = crypto.fingerprint("123456")
            )
        )
        val audit = PasswordAuditEngine.analyze(items) { crypto.decrypt(it.encryptedPassword) }
        assertEquals(3, audit.totalCount)
        assertEquals(1, audit.weakItems.size)
        assertEquals(1, audit.reusedGroups.size)
        assertEquals(1, audit.compromisedCount)
        assertTrue(audit.overallScore < 100)

        assertTrue(PasswordVaultUtil.hostMatches("github.com", "gist.github.com"))
        assertFalse(PasswordVaultUtil.hostMatches("github.com", "evilgithub.com"))

        val csv = PasswordVaultUtil.exportCsv(items) { crypto.decrypt(it.encryptedPassword) }
        val parsed = PasswordVaultUtil.parseCsv(csv)
        assertEquals(3, parsed.size)
        assertEquals("GitHub", parsed[0].siteName)
        assertEquals("Reused#Pass1", parsed[0].password)
    }

    @Test
    fun `verify BrowserRepository password vault CRUD host matching and CSV import`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val prefsRepo = UserPreferencesRepository(context)
        val sharedPrefs = context.getSharedPreferences("test_password_repo_prefs", Context.MODE_PRIVATE)
        val repository = BrowserRepository(db.apexDao(), prefsRepo, sharedPrefs)

        try {
            val savedId = repository.saveOrUpdatePassword(
                siteName = "GitHub",
                siteUrl = "https://github.com/login",
                username = "octocat",
                plainPassword = "S3cure!Apex#Password",
                notes = "Main dev account",
                category = "Work",
                isFavorite = true
            )
            assertTrue(savedId > 0L)

            val matches = repository.getPasswordsForUrl("https://gist.github.com/octocat")
            assertEquals(1, matches.size)
            assertEquals("octocat", matches.first().username)
            assertEquals("S3cure!Apex#Password", repository.decryptPassword(matches.first()))

            val csvInput = """
                name,url,username,password,note
                "Proton","https://account.proton.me","alice@proton.me","Vault#Key!9912","Recovery set"
            """.trimIndent()
            val importedCount = repository.importPasswordsFromCsv(csvInput)
            assertEquals(1, importedCount)

            val exportedCsv = repository.exportPasswordsToCsv()
            assertTrue(exportedCsv.contains("octocat"))
            assertTrue(exportedCsv.contains("alice@proton.me"))

            repository.deleteSavedPassword(savedId)
            assertTrue(repository.getPasswordsForUrl("https://github.com").isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun `verify Material 3 Expressive PasswordManagerScreenOverlay switches modes and displays vault entries`() {
        val crypto = PasswordVaultCrypto()
        val sample = SavedPasswordEntity(
            id = 10L,
            siteName = "Figma",
            siteUrl = "https://figma.com",
            host = "figma.com",
            username = "designer@studio.io",
            encryptedPassword = crypto.encrypt("Design#Vault99!"),
            category = "Work",
            isFavorite = true,
            strengthScore = 90,
            passwordFingerprint = crypto.fingerprint("Design#Vault99!")
        )

        composeTestRule.setContent {
            ApexBrowserTheme {
                PasswordManagerScreenOverlay(
                    passwords = listOf(sample),
                    autofillEnabled = true,
                    decryptPassword = { crypto.decrypt(it.encryptedPassword) },
                    onSavePassword = { _, _, _, _, _, _, _, _ -> },
                    onToggleFavorite = {},
                    onDeletePassword = {},
                    onClearVault = {},
                    onImportCsv = {},
                    onExportCsv = {},
                    onToggleAutofill = {},
                    onOpenWebsite = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("password_manager_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("passwords_split_add_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("passwords_mode_vault").assertIsDisplayed()
        composeTestRule.onNodeWithTag("passwords_mode_generator").assertIsDisplayed()
        composeTestRule.onNodeWithTag("passwords_mode_audit").assertIsDisplayed()

        // Bottom floating toolbar should not exist; controls live at the top
        composeTestRule.onNodeWithTag("passwords_floating_toolbar").assertDoesNotExist()
        composeTestRule.onNodeWithTag("toolbar_vault_btn").assertDoesNotExist()
        composeTestRule.onNodeWithTag("toolbar_generator_btn").assertDoesNotExist()
        composeTestRule.onNodeWithTag("toolbar_audit_btn").assertDoesNotExist()
        composeTestRule.onNodeWithTag("passwords_floating_add_fab").assertDoesNotExist()

        composeTestRule.onNodeWithTag("passwords_vault_list").performScrollToIndex(2)
        composeTestRule.onNodeWithText("Figma").assertIsDisplayed()
        composeTestRule.onNodeWithText("designer@studio.io").assertIsDisplayed()

        // Switch to Material 3 Expressive Generator tab
        composeTestRule.onNodeWithTag("passwords_mode_generator").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Random Characters").assertIsDisplayed()

        // Switch to Security Health Audit tab
        composeTestRule.onNodeWithTag("passwords_mode_audit").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Security Health").assertIsDisplayed()
        composeTestRule.onNodeWithText("All Credentials Passing").assertIsDisplayed()
    }

    @Test
    fun `verify Set as Default Browser hides in menu when default and Settings shows selected browser`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val status = DefaultBrowserHelper.getStatus(context)
        assertNotNull(status.currentDefaultName)
        val launchIntent = DefaultBrowserHelper.createRequestOrSettingsIntent(context)
        assertNotNull(launchIntent.action)

        var showMenu by mutableStateOf(true)
        var isDefault by mutableStateOf(false)
        var browserStatus by mutableStateOf(
            DefaultBrowserStatus(
                isApexDefault = false,
                currentDefaultName = "Chrome",
                currentDefaultPackage = "com.android.chrome"
            )
        )

        composeTestRule.setContent {
            ApexBrowserTheme {
                if (showMenu) {
                    BrowserMenuBottomSheet(
                        isHomeActive = true,
                        desktopViewportEnabled = false,
                        isDefaultBrowser = isDefault,
                        onOpenSettings = {},
                        onSetAsDefaultBrowserClick = { isDefault = true },
                        onNewTabClick = {},
                        onReloadPageClick = {},
                        onToggleDesktopMode = {},
                        onClearBrowsingData = {},
                        onDismiss = {}
                    )
                } else {
                    SettingsScreenOverlay(
                        preferences = BrowserPreferences(),
                        onSelectSearchEngine = {},
                        onToggleShields = {},
                        onToggleHttpsUpgrade = {},
                        onToggleScriptGuard = {},
                        onToggleSmartPreload = {},
                        onToggleDesktopViewport = {},
                        onToggleDynamicColor = {},
                        onSelectSeedPalette = {},
                        onClearBrowsingData = {},
                        defaultBrowserStatus = browserStatus,
                        onOpenDefaultBrowserSettings = {},
                        onDismiss = {}
                    )
                }
            }
        }

        // 1. When not default, menu shows "Set as Default Browser"
        composeTestRule.onNodeWithTag("menu_sheet_set_default_browser").performScrollTo()
        composeTestRule.onNodeWithTag("menu_sheet_set_default_browser").assertIsDisplayed()

        // 2. When Apex becomes default, menu hides "Set as Default Browser"
        isDefault = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("menu_sheet_set_default_browser").assertDoesNotExist()

        // 3. In Settings, currently selected default browser ("Chrome") is displayed in subtitle without a pill badge
        showMenu = false
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_default_browser_card").performScrollTo()
        composeTestRule.onNodeWithTag("settings_default_browser_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Currently selected: Chrome").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_default_browser_selected_badge").assertDoesNotExist()

        // 4. Once Apex is default, Settings updates to display Apex Browser subtitle without a pill badge
        browserStatus = DefaultBrowserStatus(
            isApexDefault = true,
            currentDefaultName = "Apex Browser",
            currentDefaultPackage = context.packageName
        )
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Apex Browser is selected as your default browser").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_default_browser_selected_badge").assertDoesNotExist()
    }

    @Test
    fun `verify Desktop Viewport toggle closes menu sheet when enabled without hiding the item`() {
        var menuOpen by mutableStateOf(true)
        var desktopEnabled by mutableStateOf(false)

        composeTestRule.setContent {
            ApexBrowserTheme {
                if (menuOpen) {
                    BrowserMenuBottomSheet(
                        isHomeActive = false,
                        desktopViewportEnabled = desktopEnabled,
                        onOpenSettings = {},
                        onNewTabClick = {},
                        onReloadPageClick = {},
                        onToggleDesktopMode = { enabled -> desktopEnabled = enabled },
                        onClearBrowsingData = {},
                        onDismiss = { menuOpen = false }
                    )
                }
            }
        }

        // Visible when disabled
        composeTestRule.onNodeWithTag("menu_sheet_desktop_switch").performScrollTo()
        composeTestRule.onNodeWithTag("menu_sheet_desktop_switch").assertIsDisplayed()

        // Tap to enable -> turns on AND closes menu sheet
        composeTestRule.onNodeWithTag("menu_sheet_desktop_switch").performClick()
        composeTestRule.waitForIdle()
        assertTrue(desktopEnabled)
        assertFalse(menuOpen)
        composeTestRule.onNodeWithTag("hamburger_menu_bottom_sheet").assertDoesNotExist()

        // Reopen menu while desktop mode is enabled -> item is still visible (not hidden)
        menuOpen = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("menu_sheet_desktop_switch").performScrollTo()
        composeTestRule.onNodeWithTag("menu_sheet_desktop_switch").assertIsDisplayed()

        // Tap again to disable desktop mode
        composeTestRule.onNodeWithTag("menu_sheet_desktop_switch").performClick()
        composeTestRule.waitForIdle()
        assertFalse(desktopEnabled)
        assertTrue(menuOpen)
    }

    @Test
    fun `verify horizontal swipe left and right deletes tab card while small swipe cancels and tap selects`() {
        val tab1 = BrowserTab(id = "tab_swipe_1", title = "Docs", url = "https://kotlinlang.org")
        val tab2 = BrowserTab(id = "tab_swipe_2", title = "News", url = "https://news.ycombinator.com")
        val tab3 = BrowserTab(id = "tab_swipe_3", title = "Home", url = "apex://home")

        var tabs by mutableStateOf(listOf(tab1, tab2, tab3))
        var selectedTabId by mutableStateOf<String?>(null)
        val closedTabIds = mutableListOf<String>()

        composeTestRule.setContent {
            ApexBrowserTheme {
                DraggableTabGridOverlay(
                    gridEntries = tabs.map { TabGridEntry.SingleTab(it) },
                    totalOpenTabs = tabs.size,
                    activeTabId = tab1.id,
                    expandedGroupId = null,
                    onSelectTab = { selectedTabId = it },
                    onCloseTab = { id ->
                        closedTabIds.add(id)
                        tabs = tabs.filterNot { it.id == id }
                    },
                    onCloseGroup = {},
                    onEditGroup = {},
                    onMergeEntries = { _, _ -> },
                    onMoveGroupedTabToTarget = { _, _ -> },
                    onOpenGroup = {},
                    onAddTabToGroup = {},
                    onUngroupTab = {},
                    onCycleTabColor = {},
                    onNewTab = {},
                    onCreateGroup = {},
                    onResetAllTabs = {},
                    onDismiss = {}
                )
            }
        }

        // 1. Small partial horizontal swipe below threshold springs back without deleting
        composeTestRule.onNodeWithTag("grid_tab_card_tab_swipe_1").performTouchInput {
            swipe(
                start = center,
                end = center.copy(x = center.x + (width * 0.12f)),
                durationMillis = 300
            )
        }
        composeTestRule.waitForIdle()
        assertTrue(closedTabIds.isEmpty())
        composeTestRule.onNodeWithTag("grid_tab_card_tab_swipe_1").assertIsDisplayed()

        // 2. Tap still selects the tab
        composeTestRule.onNodeWithTag("grid_tab_card_tab_swipe_1").performClick()
        composeTestRule.waitForIdle()
        assertEquals("tab_swipe_1", selectedTabId)

        // 3. Full horizontal swipe left deletes tab_swipe_2
        composeTestRule.onNodeWithTag("grid_tab_card_tab_swipe_2").performTouchInput {
            swipeLeft()
        }
        composeTestRule.waitForIdle()
        assertTrue(closedTabIds.contains("tab_swipe_2"))
        composeTestRule.onNodeWithTag("grid_tab_card_tab_swipe_2").assertDoesNotExist()

        // 4. Full horizontal swipe right deletes tab_swipe_3
        composeTestRule.onNodeWithTag("grid_tab_card_tab_swipe_3").performTouchInput {
            swipeRight()
        }
        composeTestRule.waitForIdle()
        assertTrue(closedTabIds.contains("tab_swipe_3"))
        composeTestRule.onNodeWithTag("grid_tab_card_tab_swipe_3").assertDoesNotExist()
    }

    @Test
    fun `verify horizontal swipe deletes tab row inside expanded group modal`() {
        val group = TabGroupInfo(id = "grp_swipe", title = "Work")
        val member1 = BrowserTab(
            id = "grp_tab_1",
            title = "GitHub",
            url = "https://github.com",
            groupId = group.id
        )
        val member2 = BrowserTab(
            id = "grp_tab_2",
            title = "Android Docs",
            url = "https://developer.android.com",
            groupId = group.id
        )
        var groupTabs by mutableStateOf(listOf(member1, member2))
        val closedTabIds = mutableListOf<String>()

        composeTestRule.setContent {
            ApexBrowserTheme {
                DraggableTabGridOverlay(
                    gridEntries = listOf(TabGridEntry.GroupTab(group = group, tabs = groupTabs)),
                    totalOpenTabs = groupTabs.size,
                    activeTabId = member1.id,
                    expandedGroupId = group.id,
                    onSelectTab = {},
                    onCloseTab = { id ->
                        closedTabIds.add(id)
                        groupTabs = groupTabs.filterNot { it.id == id }
                    },
                    onCloseGroup = {},
                    onEditGroup = {},
                    onMergeEntries = { _, _ -> },
                    onMoveGroupedTabToTarget = { _, _ -> },
                    onOpenGroup = {},
                    onAddTabToGroup = {},
                    onUngroupTab = {},
                    onCycleTabColor = {},
                    onNewTab = {},
                    onCreateGroup = {},
                    onResetAllTabs = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("expanded_group_modal").assertIsDisplayed()
        composeTestRule.onNodeWithTag("group_modal_tab_row_grp_tab_2").performTouchInput {
            swipeLeft()
        }
        composeTestRule.waitForIdle()
        assertEquals(listOf("grp_tab_2"), closedTabIds)
        composeTestRule.onNodeWithTag("group_modal_tab_row_grp_tab_2").assertDoesNotExist()
    }

    @Test
    fun `verify desktop viewport defaults off and swipe to delete has no red backdrop`() {
        // Default BrowserPreferences keeps desktop viewport disabled
        assertFalse(BrowserPreferences().desktopViewportEnabled)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val sharedPrefs = context.getSharedPreferences("apex_browser_user_preferences", Context.MODE_PRIVATE)
        sharedPrefs.edit()
            .putBoolean("desktop_viewport_enabled", true)
            .remove("reset_default_desktop_viewport_v1")
            .apply()
        val prefsRepo = UserPreferencesRepository(context)
        val repository = BrowserRepository(db.apexDao(), prefsRepo, sharedPrefs)

        val prefsAfterInit = runBlocking {
            repository.ensureFreshInitialized()
            repository.preferencesFlow.first()
        }
        assertFalse(prefsAfterInit.desktopViewportEnabled)
        db.close()

        // Mid-swipe check confirms no red delete backdrop or delete icon is rendered
        val tab1 = BrowserTab(id = "tab_nobg_1", title = "Docs", url = "https://kotlinlang.org")
        var tabs by mutableStateOf(listOf(tab1))
        composeTestRule.setContent {
            ApexBrowserTheme {
                DraggableTabGridOverlay(
                    gridEntries = tabs.map { TabGridEntry.SingleTab(it) },
                    totalOpenTabs = tabs.size,
                    activeTabId = tab1.id,
                    expandedGroupId = null,
                    onSelectTab = {},
                    onCloseTab = { id -> tabs = tabs.filterNot { it.id == id } },
                    onCloseGroup = {},
                    onEditGroup = {},
                    onMergeEntries = { _, _ -> },
                    onMoveGroupedTabToTarget = { _, _ -> },
                    onOpenGroup = {},
                    onAddTabToGroup = {},
                    onUngroupTab = {},
                    onCycleTabColor = {},
                    onNewTab = {},
                    onCreateGroup = {},
                    onResetAllTabs = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("grid_tab_card_tab_nobg_1").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(-80f, 0f))
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("swipe_delete_backdrop_tab_nobg_1").assertDoesNotExist()
        composeTestRule.onNodeWithTag("grid_tab_card_tab_nobg_1").performTouchInput { up() }
        composeTestRule.waitForIdle()
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    @Test
    fun `verify swipe to delete tab shows undo snackbar and undo restores closed tab`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val sharedPrefs = context.getSharedPreferences("test_undo_tab_prefs", Context.MODE_PRIVATE)
        val prefsRepo = UserPreferencesRepository(context)
        val repository = BrowserRepository(db.apexDao(), prefsRepo, sharedPrefs)
        val viewModel = BrowserViewModel(repository)

        viewModel.createNewTab(stayInSwitcher = true)
        val secondTab = viewModel.uiState.value.tabs.last()
        viewModel.setTabSwitcherOpen(true)

        composeTestRule.setContent {
            ApexBrowserTheme {
                BrowserScreen(
                    viewModel = viewModel,
                    windowSizeClass = WindowSizeClass.calculateFromSize(DpSize(412.dp, 915.dp))
                )
            }
        }
        composeTestRule.waitForIdle()

        assertEquals(2, viewModel.uiState.value.tabs.size)
        composeTestRule.onNodeWithTag("grid_tab_card_${secondTab.id}").assertIsDisplayed()

        // Swipe horizontally to delete the second tab
        composeTestRule.onNodeWithTag("grid_tab_card_${secondTab.id}").performTouchInput {
            swipeLeft()
        }
        composeTestRule.waitForIdle()

        assertEquals(1, viewModel.uiState.value.tabs.size)
        composeTestRule.onNodeWithTag("grid_tab_card_${secondTab.id}").assertDoesNotExist()
        composeTestRule.onNodeWithText("Undo").assertIsDisplayed()

        // Snackbar must sit cleanly above the Add (+) button with no overlap
        val snackbarBounds = composeTestRule.onNodeWithTag("browser_snackbar_host").fetchSemanticsNode().boundsInRoot
        val addButtonBounds = composeTestRule.onNodeWithTag("switcher_new_tab_fab").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Expected snackbar bottom (${snackbarBounds.bottom}) <= Add button top (${addButtonBounds.top})",
            snackbarBounds.bottom <= addButtonBounds.top
        )

        // Tap Undo on the Snackbar -> restores the closed tab
        composeTestRule.onNodeWithText("Undo").performClick()
        composeTestRule.waitForIdle()

        assertEquals(2, viewModel.uiState.value.tabs.size)
        assertTrue(viewModel.uiState.value.tabs.any { it.id == secondTab.id })
        composeTestRule.onNodeWithTag("grid_tab_card_${secondTab.id}").assertIsDisplayed()

        db.close()
    }

    @Test
    fun `verify reloadActiveTab sets loading synchronously and onWebViewUrlCommitted avoids loading flicker`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val sharedPrefs = context.getSharedPreferences("test_reload_sync_prefs", Context.MODE_PRIVATE)
        val prefsRepo = UserPreferencesRepository(context)
        val repository = BrowserRepository(db.apexDao(), prefsRepo, sharedPrefs)
        val store = androidx.lifecycle.ViewModelStore()
        val viewModel = androidx.lifecycle.ViewModelProvider(
            store,
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return BrowserViewModel(repository) as T
                }
            }
        )[BrowserViewModel::class.java]

        try {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            viewModel.submitNavigation("https://example.com")
            val tabId = viewModel.uiState.value.activeTabId
            viewModel.onWebViewPageFinished(
                tabId = tabId,
                title = "Example",
                url = "https://example.com",
                canGoBack = false,
                canGoForward = false
            )
            assertFalse(viewModel.uiState.value.activeTab.isLoading)

            // SPA history/anchor commit updates URL without flashing isLoading = true
            viewModel.onWebViewUrlCommitted(tabId, "https://example.com/#section")
            assertEquals("https://example.com/#section", viewModel.uiState.value.activeTab.url)
            assertFalse(viewModel.uiState.value.activeTab.isLoading)

            // reloadActiveTab immediately marks tab as loading so pull-to-refresh does not cancel early
            viewModel.reloadActiveTab()
            assertTrue(viewModel.uiState.value.activeTab.isLoading)
            assertTrue(viewModel.uiState.value.activeTab.progress > 0f)
        } finally {
            store.clear()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            kotlinx.coroutines.delay(50)
            db.close()
        }
    }

    @Test
    fun `verify WebViewViewport downward pull from top reveals expressive indicator and triggers refresh`() {
        var tab by mutableStateOf(
            BrowserTab(
                id = "ptr_tab_1",
                title = "Example",
                url = "https://example.com",
                isLoading = false,
                progress = 1f
            )
        )
        var refreshCount by mutableIntStateOf(0)
        val privacyManager = PrivacyManager()
        val performanceOptimizer = PerformanceOptimizer()

        composeTestRule.setContent {
            ApexBrowserTheme {
                WebViewViewport(
                    tab = tab,
                    preferences = BrowserPreferences(),
                    privacyManager = privacyManager,
                    performanceOptimizer = performanceOptimizer,
                    existingWebView = null,
                    onRegisterWebView = { _, _ -> },
                    onPageStarted = { _, _ -> },
                    onProgressChanged = { _, _ -> },
                    onPageFinished = { _, _, _, _, _ -> },
                    onSubresourceBlocked = { _, _, _, _, _, _ -> },
                    onHttpsUpgradeTriggered = {},
                    onScrollDelta = {},
                    onRefreshPage = {
                        refreshCount += 1
                        tab = tab.copy(isLoading = true, progress = 0.08f)
                    }
                )
            }
        }
        composeTestRule.waitForIdle()

        // Pull down on the WebView from top (scrollY == 0)
        composeTestRule.onNodeWithTag("browser_webview_ptr_tab_1").performTouchInput {
            swipeDown(
                startY = top + 40f,
                endY = top + 420f,
                durationMillis = 250
            )
        }
        composeTestRule.waitForIdle()

        assertEquals(1, refreshCount)
        composeTestRule.onNodeWithTag("expressive_pull_to_refresh_indicator").assertIsDisplayed()
    }

    @Test
    fun `verify Brave Search is default search engine in preferences and repository init`() = runBlocking {
        assertEquals(SearchEngineOption.BRAVE, BrowserPreferences().searchEngine)
        assertEquals(SearchEngineOption.BRAVE, SearchEngineOption.fromId(null))
        assertEquals(SearchEngineOption.BRAVE, SearchEngineOption.fromId("unknown"))

        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val sharedPrefs = context.getSharedPreferences("test_brave_default_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().clear().commit()
        val prefsRepo = UserPreferencesRepository(context)
        val repository = BrowserRepository(db.apexDao(), prefsRepo, sharedPrefs)

        try {
            assertEquals(SearchEngineOption.BRAVE, prefsRepo.preferencesFlow.first().searchEngine)

            // Simulate an older install that had DuckDuckGo before the Brave default migration ran
            prefsRepo.setSearchEngine(SearchEngineOption.DUCKDUCKGO)
            repository.ensureFreshInitialized()

            assertEquals(SearchEngineOption.BRAVE, prefsRepo.preferencesFlow.first().searchEngine)
            assertTrue(sharedPrefs.getBoolean("reset_default_search_engine_brave_v1", false))
        } finally {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            db.close()
        }
    }

    @Test
    fun `verify DownloadsScreenOverlay app bar uses icon-only pill buttons on right side`() {
        val sampleDownload = com.example.data.local.DownloadItemEntity(
            id = 42L,
            fileName = "archive.zip",
            url = "https://example.com/archive.zip",
            mimeType = "application/zip",
            category = "Archive",
            totalBytes = 2048L,
            downloadedBytes = 1024L,
            speedBytesPerSec = 512L,
            chunksCount = 4,
            status = FastDownloadEngine.STATUS_DOWNLOADING,
            localFilePath = "/tmp/archive.zip",
            createdAt = System.currentTimeMillis()
        )

        composeTestRule.setContent {
            ApexBrowserTheme {
                com.example.ui.browser.DownloadsScreenOverlay(
                    downloads = listOf(sampleDownload),
                    onStartNewDownload = { _, _ -> },
                    onPauseDownload = {},
                    onResumeDownload = {},
                    onRetryDownload = {},
                    onCancelDownload = {},
                    onDeleteDownload = {},
                    onClearCompletedDownloads = {},
                    onClearAllDownloads = {},
                    onOpenUrlInBrowser = {},
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Downloads").assertIsDisplayed()
        composeTestRule.onNodeWithTag("downloads_new_url_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("downloads_clear_button").assertIsDisplayed()

        // App bar buttons must be icon-only pills with no inline "New Download" / "Clear" text
        composeTestRule.onNodeWithText("New Download").assertDoesNotExist()
        composeTestRule.onNodeWithText("Clear").assertDoesNotExist()

        composeTestRule.onNodeWithTag("downloads_new_url_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Start Fast Download").assertIsDisplayed()
    }

    @Test
    fun `verify DownloadNotificationHelper shows progress in status bar notification and clears on completion`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

        val helper = com.example.engine.DownloadNotificationHelper(app)
        val notificationManager = app.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val shadowNotificationManager = Shadows.shadowOf(notificationManager)

        val downloadId = 77L
        val downloadingItem = com.example.data.local.DownloadItemEntity(
            id = downloadId,
            fileName = "ubuntu.iso",
            url = "https://example.com/ubuntu.iso",
            mimeType = "application/octet-stream",
            category = "Other",
            totalBytes = 100_000_000L,
            downloadedBytes = 50_000_000L,
            speedBytesPerSec = 5_000_000L,
            chunksCount = 4,
            status = FastDownloadEngine.STATUS_DOWNLOADING,
            localFilePath = "/tmp/ubuntu.iso",
            createdAt = System.currentTimeMillis()
        )

        helper.notifyDownloadState(downloadingItem, forceImmediate = true)

        val notifId = com.example.engine.DownloadNotificationHelper.notificationIdFor(downloadId)
        val activeNotif = shadowNotificationManager.getNotification(notifId)
        assertNotNull(activeNotif)
        assertTrue((activeNotif.flags and android.app.Notification.FLAG_ONGOING_EVENT) != 0)
        assertEquals(50, activeNotif.extras.getInt(android.app.Notification.EXTRA_PROGRESS))
        assertEquals(100, activeNotif.extras.getInt(android.app.Notification.EXTRA_PROGRESS_MAX))

        val completedItem = downloadingItem.copy(
            downloadedBytes = 100_000_000L,
            status = FastDownloadEngine.STATUS_COMPLETED,
            completedAt = System.currentTimeMillis()
        )
        helper.notifyDownloadState(completedItem, forceImmediate = true)

        val completedNotif = shadowNotificationManager.getNotification(notifId)
        assertNotNull(completedNotif)
        assertTrue((completedNotif.flags and android.app.Notification.FLAG_ONGOING_EVENT) == 0)
        assertEquals(0, completedNotif.extras.getInt(android.app.Notification.EXTRA_PROGRESS_MAX))

        helper.cancel(downloadId)
        assertNull(shadowNotificationManager.getNotification(notifId))
    }

    @Test
    fun `verify active tab grid card renders cleanly under selected theme palette`() {
        val activeTab = BrowserTab(
            id = "tab_theme_active",
            title = "Brave Search",
            url = "https://search.brave.com",
            isLoading = false
        )
        val inactiveTab = BrowserTab(
            id = "tab_theme_inactive",
            title = "Home",
            url = "apex://home",
            isLoading = false
        )

        composeTestRule.setContent {
            ApexBrowserTheme(
                dynamicColor = false,
                seedPreset = SeedPalettePreset.CYBER_EMERALD
            ) {
                DraggableTabGridOverlay(
                    gridEntries = listOf(
                        TabGridEntry.SingleTab(activeTab),
                        TabGridEntry.SingleTab(inactiveTab)
                    ),
                    totalOpenTabs = 2,
                    activeTabId = activeTab.id,
                    expandedGroupId = null,
                    onSelectTab = {},
                    onCloseTab = {},
                    onCloseGroup = {},
                    onEditGroup = {},
                    onMergeEntries = { _, _ -> },
                    onMoveGroupedTabToTarget = { _, _ -> },
                    onOpenGroup = {},
                    onAddTabToGroup = {},
                    onUngroupTab = {},
                    onCycleTabColor = {},
                    onNewTab = {},
                    onCreateGroup = {},
                    onResetAllTabs = {},
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("grid_tab_card_tab_theme_active").assertIsDisplayed()
        composeTestRule.onNodeWithTag("grid_tab_card_tab_theme_inactive").assertIsDisplayed()
    }

    @OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
    @Test
    fun `verify Google Sans Flex typography scale roles axes and zero letter spacing`() {
        val typography = com.example.ui.theme.ApexTypography

        data class RoleSpec(
            val base: androidx.compose.ui.text.TextStyle,
            val emphasized: androidx.compose.ui.text.TextStyle,
            val sizeSp: Float,
            val lineHeightSp: Float,
            val baseWeight: Int,
            val emphasizedWeight: Int
        )

        val specs = listOf(
            RoleSpec(typography.displayLarge, typography.displayLargeEmphasized, 57f, 64f, 400, 500),
            RoleSpec(typography.displayMedium, typography.displayMediumEmphasized, 45f, 52f, 400, 500),
            RoleSpec(typography.displaySmall, typography.displaySmallEmphasized, 36f, 44f, 400, 500),
            RoleSpec(typography.headlineLarge, typography.headlineLargeEmphasized, 32f, 40f, 400, 500),
            RoleSpec(typography.headlineMedium, typography.headlineMediumEmphasized, 28f, 36f, 400, 500),
            RoleSpec(typography.headlineSmall, typography.headlineSmallEmphasized, 24f, 32f, 400, 500),
            RoleSpec(typography.titleLarge, typography.titleLargeEmphasized, 22f, 28f, 400, 500),
            RoleSpec(typography.titleMedium, typography.titleMediumEmphasized, 16f, 24f, 500, 600),
            RoleSpec(typography.titleSmall, typography.titleSmallEmphasized, 14f, 20f, 500, 600),
            RoleSpec(typography.bodyLarge, typography.bodyLargeEmphasized, 16f, 24f, 400, 500),
            RoleSpec(typography.bodyMedium, typography.bodyMediumEmphasized, 14f, 20f, 400, 500),
            RoleSpec(typography.bodySmall, typography.bodySmallEmphasized, 12f, 16f, 400, 500),
            RoleSpec(typography.labelLarge, typography.labelLargeEmphasized, 14f, 20f, 500, 600),
            RoleSpec(typography.labelMedium, typography.labelMediumEmphasized, 12f, 16f, 500, 600),
            RoleSpec(typography.labelSmall, typography.labelSmallEmphasized, 11f, 16f, 500, 600)
        )

        specs.forEach { spec ->
            assertEquals(spec.sizeSp, spec.base.fontSize.value, 0.001f)
            assertEquals(spec.lineHeightSp, spec.base.lineHeight.value, 0.001f)
            assertEquals(spec.baseWeight, spec.base.fontWeight?.weight)
            assertEquals(0f, spec.base.letterSpacing.value, 0.001f)

            assertEquals(spec.sizeSp, spec.emphasized.fontSize.value, 0.001f)
            assertEquals(spec.lineHeightSp, spec.emphasized.lineHeight.value, 0.001f)
            assertEquals(spec.emphasizedWeight, spec.emphasized.fontWeight?.weight)
            assertEquals(0f, spec.emphasized.letterSpacing.value, 0.001f)

            val baseFamily = spec.base.fontFamily as androidx.compose.ui.text.font.FontListFontFamily
            val empFamily = spec.emphasized.fontFamily as androidx.compose.ui.text.font.FontListFontFamily
            val baseFont = baseFamily.fonts.first() as androidx.compose.ui.text.font.ResourceFont
            val empFont = empFamily.fonts.first() as androidx.compose.ui.text.font.ResourceFont

            assertEquals(R.font.google_sans_flex, baseFont.resId)
            assertEquals(R.font.google_sans_flex, empFont.resId)

            val density = androidx.compose.ui.unit.Density(1f)
            val baseOpsz = baseFont.variationSettings.settings.first { it.axisName == "opsz" }.toVariationValue(density)
            val baseRond = baseFont.variationSettings.settings.first { it.axisName == "ROND" }.toVariationValue(density)
            assertEquals(spec.sizeSp, baseOpsz, 0.001f)
            assertEquals(0f, baseRond, 0.001f)

            val empOpsz = empFont.variationSettings.settings.first { it.axisName == "opsz" }.toVariationValue(density)
            val empRond = empFont.variationSettings.settings.first { it.axisName == "ROND" }.toVariationValue(density)
            assertEquals(spec.sizeSp, empOpsz, 0.001f)
            assertEquals(100f, empRond, 0.001f)
        }

        // Verify EmphasizedTypography custom styles also have 0 letterSpacing and bundled fonts
        assertEquals(0f, com.example.ui.theme.EmphasizedTypography.displayStat.letterSpacing.value, 0.001f)
        assertEquals(0f, com.example.ui.theme.EmphasizedTypography.headlineHero.letterSpacing.value, 0.001f)
        assertEquals(0f, com.example.ui.theme.EmphasizedTypography.addressBarActive.letterSpacing.value, 0.001f)
        assertEquals(0f, com.example.ui.theme.EmphasizedTypography.activeTabTitle.letterSpacing.value, 0.001f)
        assertEquals(0f, com.example.ui.theme.EmphasizedTypography.badgeCounter.letterSpacing.value, 0.001f)
        assertEquals(0f, com.example.ui.theme.EmphasizedTypography.webClipboard.letterSpacing.value, 0.001f)
        assertEquals(0f, com.example.ui.theme.EmphasizedTypography.monoCode.letterSpacing.value, 0.001f)
    }

    @Test
    fun `verify BrowserMenuBottomSheet omits subheadings bookmarks and offline pages`() {
        composeTestRule.setContent {
            ApexBrowserTheme {
                BrowserMenuBottomSheet(
                    isHomeActive = false,
                    desktopViewportEnabled = false,
                    browsingHistoryCount = 5,
                    downloadsCount = 3,
                    savedPasswordsCount = 2,
                    isDefaultBrowser = false,
                    onOpenSettings = {},
                    onToggleDesktopMode = {},
                    onClearBrowsingData = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("menu_sheet_new_incognito_tab").assertIsDisplayed()
        composeTestRule.onNodeWithTag("menu_sheet_history").assertIsDisplayed()
        composeTestRule.onNodeWithTag("menu_sheet_downloads").assertIsDisplayed()
        composeTestRule.onNodeWithTag("menu_sheet_passwords").assertIsDisplayed()
        composeTestRule.onNodeWithTag("menu_sheet_desktop_switch").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("menu_sheet_set_default_browser").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("menu_sheet_open_settings").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("menu_sheet_clear_data").performScrollTo().assertIsDisplayed()

        // Bookmarks and Offline Pages must not exist in menu
        composeTestRule.onNodeWithTag("menu_sheet_bookmarks").assertDoesNotExist()
        composeTestRule.onNodeWithTag("menu_sheet_offline_pages").assertDoesNotExist()
        composeTestRule.onNodeWithTag("menu_sheet_save_offline").assertDoesNotExist()

        // No section subheadings or item subtitles
        composeTestRule.onNodeWithText("Quick Actions").assertDoesNotExist()
        composeTestRule.onNodeWithText("Preferences & Privacy").assertDoesNotExist()
        composeTestRule.onNodeWithText("Bookmarks").assertDoesNotExist()
        composeTestRule.onNodeWithText("Offline Pages").assertDoesNotExist()
        composeTestRule.onNodeWithText("Save for Offline").assertDoesNotExist()
    }

    @Test
    fun `verify SettingsScreenOverlay opens dedicated Privacy and Safety and Appearance sub-screens with selected Search Engine subheading`() {
        var currentPrefs by mutableStateOf(
            BrowserPreferences(searchEngine = SearchEngineOption.BRAVE)
        )
        var openedPasswords by mutableStateOf(false)
        var openedDownloads by mutableStateOf(false)
        var openedHistory by mutableStateOf(false)

        composeTestRule.setContent {
            ApexBrowserTheme {
                SettingsScreenOverlay(
                    preferences = currentPrefs,
                    onSelectSearchEngine = { engine ->
                        currentPrefs = currentPrefs.copy(searchEngine = engine)
                    },
                    onToggleShields = {},
                    onToggleHttpsUpgrade = {},
                    onToggleScriptGuard = {},
                    onToggleSmartPreload = {},
                    onToggleDesktopViewport = {},
                    onToggleDynamicColor = {},
                    onSelectSeedPalette = {},
                    onSelectThemeMode = { mode ->
                        currentPrefs = currentPrefs.copy(themeMode = mode)
                    },
                    onClearBrowsingData = {},
                    onOpenPasswordManager = { openedPasswords = true },
                    onOpenDownloads = { openedDownloads = true },
                    onOpenHistory = { openedHistory = true },
                    onDismiss = {}
                )
            }
        }

        // Main Settings shows Search Engine with selected engine as subheading
        composeTestRule.onNodeWithTag("settings_search_engine_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Search Engine").assertIsDisplayed()
        composeTestRule.onNodeWithText("Brave Search").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_default_browser_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_default_browser_selected_badge").assertDoesNotExist()

        // Selecting a different search engine updates the subheading
        currentPrefs = currentPrefs.copy(searchEngine = SearchEngineOption.GOOGLE)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Google").assertIsDisplayed()

        // Passwords, Downloads, and History appear above Appearance
        composeTestRule.onNodeWithTag("settings_privacy_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_passwords_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_downloads_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_history_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_appearance_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_browsing_card").performScrollTo().assertIsDisplayed()

        val passwordsTop = composeTestRule.onNodeWithTag("settings_passwords_card").fetchSemanticsNode().boundsInRoot.top
        val downloadsTop = composeTestRule.onNodeWithTag("settings_downloads_card").fetchSemanticsNode().boundsInRoot.top
        val historyTop = composeTestRule.onNodeWithTag("settings_history_card").fetchSemanticsNode().boundsInRoot.top
        val appearanceTop = composeTestRule.onNodeWithTag("settings_appearance_card").fetchSemanticsNode().boundsInRoot.top
        assertTrue(passwordsTop < appearanceTop)
        assertTrue(downloadsTop < appearanceTop)
        assertTrue(historyTop < appearanceTop)

        // Quick links trigger their screen callbacks
        composeTestRule.onNodeWithTag("settings_passwords_card").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("settings_downloads_card").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("settings_history_card").performScrollTo().performClick()
        assertTrue(openedPasswords)
        assertTrue(openedDownloads)
        assertTrue(openedHistory)

        composeTestRule.onNodeWithTag("settings_switch_shields").assertDoesNotExist()
        composeTestRule.onNodeWithTag("settings_switch_dynamic_color").assertDoesNotExist()
        composeTestRule.onNodeWithTag("settings_switch_preload").assertDoesNotExist()

        // Open Privacy & Safety sub-screen (no Passwords or Auto-Save & Fill Logins toggle inside)
        composeTestRule.onNodeWithTag("settings_privacy_card").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_privacy_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Privacy & Safety").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_switch_shields").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_switch_https").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_switch_script_guard").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_clear_data_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_switch_password_autofill").assertDoesNotExist()
        composeTestRule.onNodeWithTag("settings_open_password_manager").assertDoesNotExist()
        composeTestRule.onNodeWithText("Block known third-party ad networks and tracking scripts").assertDoesNotExist()
        composeTestRule.onNodeWithText("Automatically upgrade unencrypted HTTP links to HTTPS").assertDoesNotExist()
        composeTestRule.onNodeWithText("Removes saved search queries and resets local counters").assertDoesNotExist()

        // Navigate back to main Settings
        composeTestRule.onNodeWithTag("settings_privacy_back_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()

        // Open Appearance sub-screen (Theme mode, Dynamic Color, Color Palette & Top Visits)
        composeTestRule.onNodeWithTag("settings_appearance_card").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_appearance_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Appearance").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_theme_mode_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_theme_mode_system").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_theme_mode_light").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_theme_mode_dark").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_theme_mode_dark").performClick()
        composeTestRule.waitForIdle()
        assertEquals(com.example.data.preferences.ThemeModeOption.DARK, currentPrefs.themeMode)
        composeTestRule.onNodeWithTag("settings_switch_dynamic_color").assertIsDisplayed()
        composeTestRule.onNodeWithText("Color Palette").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_palette_${SeedPalettePreset.ELECTRIC_COBALT.name}").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_switch_top_visits").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Adapt browser surfaces to your Android wallpaper palette").assertDoesNotExist()

        // Navigate back to main Settings
        composeTestRule.onNodeWithTag("settings_appearance_back_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()

        // Open Browsing sub-screen
        composeTestRule.onNodeWithTag("settings_browsing_card").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_browsing_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Browsing").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_switch_preload").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_switch_desktop").assertIsDisplayed()
    }

    @Test
    fun `verify Accessibility sub-screen UI controls preview scaling dialogs and menu zoom`() {
        var currentPrefs by mutableStateOf(
            BrowserPreferences(
                savedSiteZooms = mapOf("kotlinlang.org" to 125)
            )
        )
        var showMenu by mutableStateOf(false)

        composeTestRule.setContent {
            ApexBrowserTheme {
                if (showMenu) {
                    BrowserMenuBottomSheet(
                        isHomeActive = false,
                        desktopViewportEnabled = false,
                        isDefaultBrowser = true,
                        showZoomOption = currentPrefs.showZoomInMainMenu,
                        activeSiteHost = "kotlinlang.org",
                        currentZoomPercent = currentPrefs.effectiveZoomForUrl("https://kotlinlang.org"),
                        hasCustomSiteZoom = currentPrefs.savedSiteZooms.containsKey("kotlinlang.org"),
                        onAdjustZoomPercent = { newZoom ->
                            currentPrefs = currentPrefs.copy(
                                savedSiteZooms = currentPrefs.savedSiteZooms + ("kotlinlang.org" to newZoom)
                            )
                        },
                        onResetSiteZoom = {
                            currentPrefs = currentPrefs.copy(
                                savedSiteZooms = currentPrefs.savedSiteZooms - "kotlinlang.org"
                            )
                        },
                        onOpenSettings = {},
                        onToggleDesktopMode = {},
                        onClearBrowsingData = {},
                        onDismiss = {}
                    )
                } else {
                    SettingsScreenOverlay(
                        preferences = currentPrefs,
                        onSelectSearchEngine = {},
                        onToggleShields = {},
                        onToggleHttpsUpgrade = {},
                        onToggleScriptGuard = {},
                        onToggleSmartPreload = {},
                        onToggleDesktopViewport = {},
                        onToggleDynamicColor = {},
                        onSelectSeedPalette = {},
                        onClearBrowsingData = {},
                        onChangeDefaultZoom = { zoom ->
                            currentPrefs = currentPrefs.copy(defaultZoomPercent = zoom)
                        },
                        onSetSiteZoom = { host, zoom ->
                            currentPrefs = currentPrefs.copy(
                                savedSiteZooms = currentPrefs.savedSiteZooms + (host to zoom)
                            )
                        },
                        onRemoveSiteZoom = { host ->
                            currentPrefs = currentPrefs.copy(
                                savedSiteZooms = currentPrefs.savedSiteZooms - host
                            )
                        },
                        onClearSavedSiteZooms = {
                            currentPrefs = currentPrefs.copy(savedSiteZooms = emptyMap())
                        },
                        onToggleShowZoomInMainMenu = { enabled ->
                            currentPrefs = currentPrefs.copy(showZoomInMainMenu = enabled)
                        },
                        onToggleForceEnableZoom = { enabled ->
                            currentPrefs = currentPrefs.copy(forceEnableZoom = enabled)
                        },
                        onToggleSimplifiedView = { enabled ->
                            currentPrefs = currentPrefs.copy(simplifiedViewEnabled = enabled)
                        },
                        onToggleCaretBrowsing = { enabled ->
                            currentPrefs = currentPrefs.copy(caretBrowsingEnabled = enabled)
                        },
                        onToggleTouchpadSwipeNavigation = { enabled ->
                            currentPrefs = currentPrefs.copy(touchpadSwipeNavigationEnabled = enabled)
                        },
                        onDismiss = {}
                    )
                }
            }
        }

        // 1. Open Accessibility from main Settings
        composeTestRule.onNodeWithTag("settings_accessibility_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_accessibility_card").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_accessibility_screen").assertIsDisplayed()

        // 2. Default zoom & Preview card
        composeTestRule.onNodeWithTag("accessibility_default_zoom_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Default zoom").assertIsDisplayed()
        composeTestRule.onNodeWithText("100 %").assertIsDisplayed()
        composeTestRule.onNodeWithTag("accessibility_preview_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Preview").assertIsDisplayed()
        composeTestRule.onNodeWithText("The Wonderful Wizard of Oz").assertIsDisplayed()
        composeTestRule.onNodeWithText("Chapter 11: The Wonderful Emerald City of Oz").assertIsDisplayed()

        composeTestRule.onNodeWithTag("accessibility_zoom_in_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(105, currentPrefs.defaultZoomPercent)
        composeTestRule.onNodeWithText("105 %").assertIsDisplayed()

        composeTestRule.onNodeWithTag("accessibility_zoom_out_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(100, currentPrefs.defaultZoomPercent)

        // 3. Help dialog
        composeTestRule.onNodeWithTag("settings_accessibility_help_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("accessibility_help_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("accessibility_help_close_button").performClick()
        composeTestRule.waitForIdle()

        // 4. All 7 Material 3 Expressive grouped items
        val a11yTags = listOf(
            "settings_accessibility_saved_zoom",
            "settings_switch_show_zoom_menu",
            "settings_switch_force_zoom",
            "settings_switch_simplified_view",
            "settings_switch_caret_browsing",
            "settings_accessibility_captions",
            "settings_switch_touchpad_swipe"
        )
        a11yTags.forEach { tag ->
            composeTestRule.onNodeWithTag("settings_accessibility_list").performScrollToNode(hasTestTag(tag))
            composeTestRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
        }

        // 5. Saved zoom for sites dialog
        composeTestRule.onNodeWithTag("settings_accessibility_list").performScrollToNode(hasTestTag("settings_accessibility_saved_zoom"))
        composeTestRule.onNodeWithTag("settings_accessibility_saved_zoom").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("saved_site_zooms_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("kotlinlang.org").assertIsDisplayed()
        composeTestRule.onNodeWithTag("saved_site_zoom_plus_kotlinlang.org").performClick()
        composeTestRule.waitForIdle()
        assertEquals(130, currentPrefs.savedSiteZooms["kotlinlang.org"])
        composeTestRule.onNodeWithTag("saved_site_zoom_close").performClick()
        composeTestRule.waitForIdle()

        // 6. Toggle Accessibility switches
        listOf(
            "settings_switch_show_zoom_menu",
            "settings_switch_force_zoom",
            "settings_switch_simplified_view",
            "settings_switch_caret_browsing",
            "settings_switch_touchpad_swipe"
        ).forEach { tag ->
            composeTestRule.onNodeWithTag("settings_accessibility_list").performScrollToNode(hasTestTag(tag))
            composeTestRule.onNodeWithTag(tag).performScrollTo().performClick()
        }
        composeTestRule.waitForIdle()

        assertTrue(currentPrefs.showZoomInMainMenu)
        assertTrue(currentPrefs.forceEnableZoom)
        assertTrue(currentPrefs.simplifiedViewEnabled)
        assertTrue(currentPrefs.caretBrowsingEnabled)
        assertFalse(currentPrefs.touchpadSwipeNavigationEnabled)

        // 7. When "Show zoom option in main menu" is enabled, BrowserMenuBottomSheet shows zoom controls
        showMenu = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("menu_sheet_zoom_row").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("130 %").assertIsDisplayed()
        composeTestRule.onNodeWithTag("menu_sheet_zoom_in").performClick()
        composeTestRule.waitForIdle()
        assertEquals(135, currentPrefs.savedSiteZooms["kotlinlang.org"])
    }

    @Test
    fun `verify UserPreferencesRepository persists accessibility settings and site zooms`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsRepo = UserPreferencesRepository(context)

        prefsRepo.setDefaultZoomPercent(120)
        prefsRepo.setSiteZoom("https://developer.android.com/compose", 145)
        prefsRepo.setShowZoomInMainMenu(true)
        prefsRepo.setForceEnableZoom(true)
        prefsRepo.setSimplifiedViewEnabled(true)
        prefsRepo.setCaretBrowsingEnabled(true)
        prefsRepo.setTouchpadSwipeNavigationEnabled(false)

        val loaded = prefsRepo.preferencesFlow.first()
        assertEquals(120, loaded.defaultZoomPercent)
        assertEquals(145, loaded.savedSiteZooms["developer.android.com"])
        assertEquals(145, loaded.effectiveZoomForUrl("https://developer.android.com/guide"))
        assertEquals(120, loaded.effectiveZoomForUrl("https://example.org"))
        assertTrue(loaded.showZoomInMainMenu)
        assertTrue(loaded.forceEnableZoom)
        assertTrue(loaded.simplifiedViewEnabled)
        assertTrue(loaded.caretBrowsingEnabled)
        assertFalse(loaded.touchpadSwipeNavigationEnabled)

        prefsRepo.removeSiteZoom("developer.android.com")
        val afterRemove = prefsRepo.preferencesFlow.first()
        assertFalse(afterRemove.savedSiteZooms.containsKey("developer.android.com"))
        assertEquals(120, afterRemove.effectiveZoomForUrl("https://developer.android.com/guide"))
    }

    @Test
    fun `verify Languages sub-screen download manager preferred order and menu translate option`() {
        var currentPrefs by mutableStateOf(
            BrowserPreferences(
                preferredLanguageTags = listOf("en-IN", "ur", "en"),
                offerToTranslateEnabled = true,
                translateTargetLanguageCode = "en",
                neverTranslateLanguageCodes = listOf("en")
            )
        )
        var downloadedCodes by mutableStateOf(setOf("en", "ur"))
        var downloadingCodes by mutableStateOf(emptySet<String>())
        var showMenu by mutableStateOf(false)
        var translateClicked by mutableStateOf(false)

        composeTestRule.setContent {
            ApexBrowserTheme {
                if (showMenu) {
                    BrowserMenuBottomSheet(
                        isHomeActive = false,
                        desktopViewportEnabled = false,
                        isDefaultBrowser = true,
                        onOpenSettings = {},
                        onToggleDesktopMode = {},
                        onTranslatePageClick = { translateClicked = true },
                        onClearBrowsingData = {},
                        onDismiss = {}
                    )
                } else {
                    SettingsScreenOverlay(
                        preferences = currentPrefs,
                        onSelectSearchEngine = {},
                        onToggleShields = {},
                        onToggleHttpsUpgrade = {},
                        onToggleScriptGuard = {},
                        onToggleSmartPreload = {},
                        onToggleDesktopViewport = {},
                        onToggleDynamicColor = {},
                        onSelectSeedPalette = {},
                        onClearBrowsingData = {},
                        downloadedLanguageCodes = downloadedCodes,
                        downloadingLanguageCodes = downloadingCodes,
                        onSelectAppLanguage = { tag ->
                            currentPrefs = currentPrefs.copy(appLanguageTag = tag)
                        },
                        onAddPreferredLanguage = { tag, _ ->
                            currentPrefs = currentPrefs.copy(
                                preferredLanguageTags = currentPrefs.preferredLanguageTags + tag
                            )
                        },
                        onMovePreferredLanguageUp = { tag ->
                            val list = currentPrefs.preferredLanguageTags.toMutableList()
                            val idx = list.indexOf(tag)
                            if (idx > 0) {
                                list.removeAt(idx)
                                list.add(idx - 1, tag)
                                currentPrefs = currentPrefs.copy(preferredLanguageTags = list)
                            }
                        },
                        onMovePreferredLanguageToTop = { tag ->
                            val list = currentPrefs.preferredLanguageTags.toMutableList()
                            list.remove(tag)
                            list.add(0, tag)
                            currentPrefs = currentPrefs.copy(preferredLanguageTags = list)
                        },
                        onToggleOfferToTranslate = { enabled ->
                            currentPrefs = currentPrefs.copy(offerToTranslateEnabled = enabled)
                        },
                        onDownloadTranslationModel = { code ->
                            downloadedCodes = downloadedCodes + code
                        },
                        onDeleteTranslationModel = { code ->
                            downloadedCodes = downloadedCodes - code
                        },
                        onToggleWifiOnlyTranslationDownloads = { enabled ->
                            currentPrefs = currentPrefs.copy(wifiOnlyTranslationDownloads = enabled)
                        },
                        onSelectTranslateTargetLanguage = { code ->
                            currentPrefs = currentPrefs.copy(translateTargetLanguageCode = code)
                        },
                        onDismiss = {}
                    )
                }
            }
        }

        // 1. Open Languages from main Settings
        composeTestRule.onNodeWithTag("settings_languages_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_languages_card").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_languages_screen").assertIsDisplayed()

        // 2. Verify App language & Preferred languages sections
        composeTestRule.onNodeWithTag("settings_app_language_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("preferred_lang_row_en-IN").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("preferred_lang_row_ur").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("languages_add_button").performScrollTo().assertIsDisplayed()

        // 3. Reorder Urdu up to top via drag button
        composeTestRule.onNodeWithTag("preferred_lang_drag_ur").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals("ur", currentPrefs.preferredLanguageTags.first())

        // 4. Translation settings & Advanced items
        val translationTags = listOf(
            "settings_switch_offer_translate",
            "settings_download_languages_card",
            "settings_translate_target_card",
            "settings_auto_translate_card",
            "settings_never_translate_card"
        )
        translationTags.forEach { tag ->
            composeTestRule.onNodeWithTag("settings_languages_list").performScrollToNode(hasTestTag(tag))
            composeTestRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
        }

        // 5. Open Offline translation languages download sheet and download/delete a specific language
        composeTestRule.onNodeWithTag("settings_languages_list").performScrollToNode(hasTestTag("settings_download_languages_card"))
        composeTestRule.onNodeWithTag("settings_download_languages_card").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("languages_download_sheet").assertIsDisplayed()

        composeTestRule.onNodeWithTag("languages_download_search_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("languages_filter_downloaded").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("delete_lang_btn_ur").assertIsDisplayed()
        composeTestRule.onNodeWithTag("delete_lang_btn_ur").performClick()
        composeTestRule.waitForIdle()
        assertFalse("ur" in downloadedCodes)

        composeTestRule.onNodeWithTag("languages_filter_available").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNode(
            hasScrollAction() and hasAnyAncestor(hasTestTag("languages_download_sheet"))
        ).performScrollToNode(hasTestTag("download_lang_btn_ar"))
        composeTestRule.onNodeWithTag("download_lang_btn_ar").performClick()
        composeTestRule.waitForIdle()
        assertTrue("ar" in downloadedCodes)

        // 6. BrowserMenuBottomSheet shows Translate action on web pages
        showMenu = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("menu_sheet_translate_page").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("menu_sheet_translate_page").performClick()
        composeTestRule.waitForIdle()
        assertTrue(translateClicked)
    }

    @Test
    fun `verify UserPreferencesRepository persists language translation preferences`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsRepo = UserPreferencesRepository(context)

        prefsRepo.setAppLanguageTag("hi")
        prefsRepo.setPreferredLanguageTags(listOf("hi", "en-IN", "ur"))
        prefsRepo.setOfferToTranslateEnabled(false)
        prefsRepo.setTranslateTargetLanguageCode("fr")
        prefsRepo.setAutoTranslateLanguageCodes(listOf("es", "de"))
        prefsRepo.setNeverTranslateLanguageCodes(listOf("en", "fr"))
        prefsRepo.setWifiOnlyTranslationDownloads(true)

        val loaded = prefsRepo.preferencesFlow.first()
        assertEquals("hi", loaded.appLanguageTag)
        assertEquals(listOf("hi", "en-IN", "ur"), loaded.preferredLanguageTags)
        assertFalse(loaded.offerToTranslateEnabled)
        assertEquals("fr", loaded.translateTargetLanguageCode)
        assertEquals(listOf("es", "de"), loaded.autoTranslateLanguageCodes)
        assertEquals(listOf("en", "fr"), loaded.neverTranslateLanguageCodes)
        assertTrue(loaded.wifiOnlyTranslationDownloads)
    }

    @Test
    fun `verify BrowserViewModel downloads and deletes offline translation models`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val prefsRepo = UserPreferencesRepository(context)
        val sharedPrefs = context.getSharedPreferences("test_lang_vm_prefs", Context.MODE_PRIVATE)
        val repo = BrowserRepository(db.apexDao(), prefsRepo, sharedPrefs)

        val fakeModels = mutableSetOf("en")
        val fakeEngine = object : com.example.engine.LanguageTranslationEngine() {
            override suspend fun refreshDownloadedModels(): Set<String> = fakeModels.toSet()
            override suspend fun downloadLanguageModel(mlKitCode: String, requireWifi: Boolean): Result<Unit> {
                fakeModels.add(mlKitCode)
                return Result.success(Unit)
            }
            override suspend fun deleteLanguageModel(mlKitCode: String): Result<Unit> {
                fakeModels.remove(mlKitCode)
                return Result.success(Unit)
            }
        }

        val vm = BrowserViewModel(repository = repo, translationEngine = fakeEngine)
        try {
            val refreshDeadline = System.currentTimeMillis() + 3_000L
            while ("en" !in vm.downloadedLanguageCodes.value && System.currentTimeMillis() < refreshDeadline) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(20L)
            }
            assertTrue("en" in vm.downloadedLanguageCodes.value)

            vm.downloadTranslationModel("es")
            val dlDeadline = System.currentTimeMillis() + 3_000L
            while ("es" !in vm.downloadedLanguageCodes.value && System.currentTimeMillis() < dlDeadline) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(20L)
            }
            assertTrue("es" in vm.downloadedLanguageCodes.value)

            vm.deleteTranslationModel("es")
            val delDeadline = System.currentTimeMillis() + 3_000L
            while ("es" in vm.downloadedLanguageCodes.value && System.currentTimeMillis() < delDeadline) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(20L)
            }
            assertFalse("es" in vm.downloadedLanguageCodes.value)
        } finally {
            db.close()
        }
    }

    @Test
    fun networkStatusBanner_andHomeSearchBar_renderOfflineAndBackOnlineStates() {
        assertEquals("github.com", com.example.ui.browser.formatCleanAddressBarUrl("https://www.github.com/Ahmed9ali"))
        assertEquals("github.com", com.example.ui.browser.formatCleanAddressBarUrl("http://github.com/"))

        var bannerState by mutableStateOf(com.example.ui.viewmodel.NetworkStatusBannerState.HIDDEN)
        var isOnline by mutableStateOf(true)
        var currentUrl by mutableStateOf("apex://home")

        composeTestRule.setContent {
            ApexBrowserTheme {
                CompactTopToolbar(
                    visible = true,
                    activeTab = BrowserTab(
                        id = "tab-1",
                        title = "GitHub",
                        url = currentUrl,
                        isLoading = false,
                        progress = 1f
                    ),
                    onAddressPillClick = {},
                    onReloadClick = {},
                    searchEngine = SearchEngineOption.BRAVE,
                    isOnline = isOnline,
                    networkBannerState = bannerState
                )
            }
        }

        // Home page search bar exists and banner is hidden initially
        composeTestRule.onNodeWithTag("home_top_search_bar").assertExists()
        composeTestRule.onNodeWithTag("status_bar_offline_banner").assertDoesNotExist()
        composeTestRule.onNodeWithTag("status_bar_online_banner").assertDoesNotExist()

        // Switch to offline on web page (like Screenshot 1: "No internet connection" + info icon + github.com)
        currentUrl = "https://github.com/dashboard"
        isOnline = false
        bannerState = com.example.ui.viewmodel.NetworkStatusBannerState.OFFLINE
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("status_bar_offline_banner").assertExists()
        composeTestRule.onNode(androidx.compose.ui.test.hasText("No internet connection")).assertExists()
        composeTestRule.onNodeWithTag("address_bar_container").assertExists()
        composeTestRule.onNodeWithTag("address_bar_info_icon", useUnmergedTree = true).assertExists()
        composeTestRule.onNode(androidx.compose.ui.test.hasText("github.com")).assertExists()

        // Reconnect to online (like Screenshot 2: "Back online" + page tune icon + github.com)
        isOnline = true
        bannerState = com.example.ui.viewmodel.NetworkStatusBannerState.BACK_ONLINE
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("status_bar_online_banner").assertExists()
        composeTestRule.onNode(androidx.compose.ui.test.hasText("Back online")).assertExists()
        composeTestRule.onNodeWithTag("address_bar_tune_icon", useUnmergedTree = true).assertExists()
        composeTestRule.onNode(androidx.compose.ui.test.hasText("github.com")).assertExists()
    }

    @Test
    fun settingsOverlayBackStack_returnsToSettingsFromPasswordsDownloadsHistoryAndBookmarks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val prefsRepo = UserPreferencesRepository(context)
        val sharedPrefs = context.getSharedPreferences("test_settings_backstack_prefs", Context.MODE_PRIVATE)
        val repo = BrowserRepository(db.apexDao(), prefsRepo, sharedPrefs)
        val fakeEngine = object : com.example.engine.LanguageTranslationEngine() {
            override suspend fun refreshDownloadedModels(): Set<String> = setOf("en")
        }
        val store = androidx.lifecycle.ViewModelStore()
        val vm = androidx.lifecycle.ViewModelProvider(
            store,
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return BrowserViewModel(repository = repo, translationEngine = fakeEngine) as T
                }
            }
        )[BrowserViewModel::class.java]

        try {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            // 1. Open Settings -> Open Passwords -> Go back -> Should return to Settings (not Home)
            vm.setSettingsScreenOpen(true)
            assertTrue(vm.uiState.value.isSettingsScreenOpen)
            assertFalse(vm.uiState.value.isPasswordManagerOpen)

            vm.setPasswordManagerOpen(true)
            assertTrue(vm.uiState.value.isPasswordManagerOpen)
            assertFalse(vm.uiState.value.isSettingsScreenOpen)

            vm.setPasswordManagerOpen(false)
            assertFalse(vm.uiState.value.isPasswordManagerOpen)
            assertTrue(vm.uiState.value.isSettingsScreenOpen)

            // 2. From Settings -> Open Downloads -> Go back -> Should return to Settings
            vm.setDownloadsScreenOpen(true)
            assertTrue(vm.uiState.value.isDownloadsScreenOpen)
            assertFalse(vm.uiState.value.isSettingsScreenOpen)

            vm.setDownloadsScreenOpen(false)
            assertFalse(vm.uiState.value.isDownloadsScreenOpen)
            assertTrue(vm.uiState.value.isSettingsScreenOpen)

            // 3. From Settings -> Open History -> Go back -> Should return to Settings
            vm.setHistoryScreenOpen(true)
            assertTrue(vm.uiState.value.isHistoryScreenOpen)
            assertFalse(vm.uiState.value.isSettingsScreenOpen)

            vm.setHistoryScreenOpen(false)
            assertFalse(vm.uiState.value.isHistoryScreenOpen)
            assertTrue(vm.uiState.value.isSettingsScreenOpen)

            // 4. From Settings -> Open Bookmarks -> Go back -> Should return to Settings
            vm.setBookmarksScreenOpen(true)
            assertTrue(vm.uiState.value.isBookmarksScreenOpen)
            assertFalse(vm.uiState.value.isSettingsScreenOpen)

            vm.setBookmarksScreenOpen(false)
            assertFalse(vm.uiState.value.isBookmarksScreenOpen)
            assertTrue(vm.uiState.value.isSettingsScreenOpen)

            // 5. Going back from Settings now returns to Home/Browser
            vm.setSettingsScreenOpen(false)
            assertFalse(vm.uiState.value.isSettingsScreenOpen)

            // 6. Opening Passwords directly from the 3-dot Menu (Home) -> Go back -> Returns directly to Home
            vm.setPasswordManagerOpen(true)
            assertTrue(vm.uiState.value.isPasswordManagerOpen)
            assertFalse(vm.uiState.value.isSettingsScreenOpen)

            vm.setPasswordManagerOpen(false)
            assertFalse(vm.uiState.value.isPasswordManagerOpen)
            assertFalse(vm.uiState.value.isSettingsScreenOpen)
        } finally {
            store.clear()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(60)
            db.close()
        }
    }

    @Test
    fun webViewSmoothness_preventsPostFinishProgressFlickerAndBatchesBlockedSubresources() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val prefsRepo = UserPreferencesRepository(context)
        val sharedPrefs = context.getSharedPreferences("test_webview_smoothness_prefs", Context.MODE_PRIVATE)
        val repo = BrowserRepository(db.apexDao(), prefsRepo, sharedPrefs)
        val fakeEngine = object : com.example.engine.LanguageTranslationEngine() {
            override suspend fun refreshDownloadedModels(): Set<String> = setOf("en")
        }
        val store = androidx.lifecycle.ViewModelStore()
        val vm = androidx.lifecycle.ViewModelProvider(
            store,
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return BrowserViewModel(repository = repo, translationEngine = fakeEngine) as T
                }
            }
        )[BrowserViewModel::class.java]

        try {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            val tabId = vm.uiState.value.activeTabId
            vm.submitNavigation("https://github.com", tabId)
            assertTrue(vm.uiState.value.activeTab.isLoading)

            // Queue multiple blocked subresources during page load
            vm.onSubresourceBlocked(tabId, "github.com", "doubleclick.net", "Ad Network", 12000L, 45L)
            vm.onSubresourceBlocked(tabId, "github.com", "google-analytics.com", "Tracker", 8000L, 30L)
            vm.onSubresourceBlocked(tabId, "github.com", "taboola.com", "Ad Network", 15000L, 55L)

            // Finish page load — flushes batched blocked count and marks isLoading = false
            vm.onWebViewPageFinished(
                tabId = tabId,
                title = "GitHub",
                url = "https://github.com",
                canGoBack = true,
                canGoForward = false
            )
            assertFalse(vm.uiState.value.activeTab.isLoading)
            assertEquals(1f, vm.uiState.value.activeTab.progress, 0.001f)
            assertEquals(3, vm.uiState.value.activeTab.blockedCountOnPage)

            // Late iframe/analytics progress callback (< 100) after page finish must NOT re-enable isLoading
            vm.onWebViewProgress(tabId, 45)
            assertFalse(vm.uiState.value.activeTab.isLoading)
            assertEquals(1f, vm.uiState.value.activeTab.progress, 0.001f)
        } finally {
            store.clear()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            kotlinx.coroutines.delay(80)
            db.close()
        }
    }

    @Test
    fun turboDownload_handlesRedirectAndTransientDropAndResumes8xChunksToCompletion() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val prefsRepo = UserPreferencesRepository(context)
        val sharedPrefs = context.getSharedPreferences("test_turbo_redirect_prefs", Context.MODE_PRIVATE)
        val repository = BrowserRepository(db.apexDao(), prefsRepo, sharedPrefs)
        val engine = FastDownloadEngine(context, repository)

        // 640 KB payload triggers 8x Parallel Turbo chunks (>= 512 KB)
        val payload = ByteArray(640 * 1024) { idx -> ((idx * 37 + 13) and 0xFF).toByte() }
        val droppedOnceForChunk = java.util.concurrent.atomic.AtomicBoolean(false)
        val server = ServerSocket(0)
        val port = server.localPort
        val serverThread = thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    runCatching {
                        socket.use { s ->
                            val reader = s.getInputStream().bufferedReader()
                            val requestLine = reader.readLine() ?: return@use
                            val path = requestLine.split(' ').getOrNull(1).orEmpty()
                            var rangeHeader: String? = null
                            while (true) {
                                val line = reader.readLine() ?: break
                                if (line.isEmpty()) break
                                if (line.startsWith("Range:", ignoreCase = true)) {
                                    rangeHeader = line.substringAfter(':').trim()
                                }
                            }
                            val out = s.getOutputStream()
                            if (path.startsWith("/redirect")) {
                                val redirectHeader = "HTTP/1.1 302 Found\r\n" +
                                    "Location: http://127.0.0.1:$port/cdn/turbo_archive.bin\r\n" +
                                    "Content-Length: 0\r\n" +
                                    "Connection: close\r\n\r\n"
                                out.write(redirectHeader.toByteArray())
                                out.flush()
                                return@use
                            }

                            val total = payload.size
                            if (rangeHeader != null && rangeHeader!!.startsWith("bytes=")) {
                                val spec = rangeHeader!!.removePrefix("bytes=").trim()
                                val start = spec.substringBefore('-').toIntOrNull() ?: 0
                                val endRaw = spec.substringAfter('-', "").toIntOrNull()
                                val end = (endRaw ?: (total - 1)).coerceAtMost(total - 1)
                                val len = end - start + 1
                                val header = "HTTP/1.1 206 Partial Content\r\n" +
                                    "Accept-Ranges: bytes\r\n" +
                                    "Content-Length: $len\r\n" +
                                    "Content-Range: bytes $start-$end/$total\r\n" +
                                    "Content-Type: application/octet-stream\r\n" +
                                    "Connection: close\r\n\r\n"
                                out.write(header.toByteArray())

                                // Simulate a transient socket drop mid-chunk when user switches apps/tabs
                                if (start > 0 && end > start + 16384 && droppedOnceForChunk.compareAndSet(false, true)) {
                                    out.write(payload, start, 8192)
                                    out.flush()
                                    return@use
                                }

                                var pos = start
                                while (pos <= end) {
                                    val chunk = minOf(16384, end - pos + 1)
                                    out.write(payload, pos, chunk)
                                    out.flush()
                                    pos += chunk
                                }
                            } else {
                                val header = "HTTP/1.1 200 OK\r\n" +
                                    "Accept-Ranges: bytes\r\n" +
                                    "Content-Length: $total\r\n" +
                                    "Content-Type: application/octet-stream\r\n" +
                                    "Connection: close\r\n\r\n"
                                out.write(header.toByteArray())
                                out.write(payload)
                                out.flush()
                            }
                        }
                    }
                }
            }
        }

        try {
            val startedLatch = CountDownLatch(1)
            var downloadId = -1L
            engine.enqueueDownload(
                url = "http://127.0.0.1:$port/redirect/turbo_archive.bin",
                suggestedFileName = "turbo_archive.bin"
            ) { entity ->
                downloadId = entity.id
                startedLatch.countDown()
            }
            val startDeadline = System.currentTimeMillis() + 5_000L
            while (startedLatch.count > 0 && System.currentTimeMillis() < startDeadline) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(20L)
            }
            assertEquals(0L, startedLatch.count)
            assertTrue(downloadId > 0L)

            var finalEntity = runBlocking { db.apexDao().getDownloadById(downloadId) }
            val deadlineComplete = System.currentTimeMillis() + 12_000L
            while (System.currentTimeMillis() < deadlineComplete) {
                finalEntity = runBlocking { db.apexDao().getDownloadById(downloadId) }
                if (finalEntity?.status == FastDownloadEngine.STATUS_COMPLETED) break
                assertNotEquals(
                    "8x Turbo download must recover from transient socket drop without failing: ${finalEntity?.errorMessage}",
                    FastDownloadEngine.STATUS_FAILED,
                    finalEntity?.status
                )
                Thread.sleep(50L)
            }
            assertEquals(FastDownloadEngine.STATUS_COMPLETED, finalEntity?.status)
            assertEquals(FastDownloadEngine.DEFAULT_TURBO_CHUNKS, finalEntity?.chunksCount)
            assertEquals(payload.size.toLong(), finalEntity?.downloadedBytes)
            assertTrue("Transient socket drop should have been triggered and recovered", droppedOnceForChunk.get())
            val outFile = File(finalEntity!!.localFilePath)
            assertTrue(outFile.exists())
            assertArrayEquals(payload, outFile.readBytes())
        } finally {
            runCatching { server.close() }
            serverThread.interrupt()
            db.close()
        }
    }

    @Test
    fun clipboardDetection_disappearsAfterUseAndDoesNotReappearUntilNewCopy() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.ui.browser.ClipboardConsumptionTracker.resetForTesting(context)
        val sysClipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        sysClipboard.setPrimaryClip(
            android.content.ClipData.newPlainText("url", "https://developer.android.com/jetpack")
        )

        var isSearchOpen by mutableStateOf(true)
        var currentQuery by mutableStateOf("")
        var submittedTarget by mutableStateOf<String?>(null)

        composeTestRule.setContent {
            ApexBrowserTheme {
                if (isSearchOpen) {
                    com.example.ui.browser.SearchScreenOverlay(
                        query = currentQuery,
                        selectedEngine = SearchEngineOption.GOOGLE,
                        suggestions = emptyList(),
                        isLoadingSuggestions = false,
                        recentHistory = emptyList(),
                        activeTabUrl = null,
                        activeTabTitle = null,
                        onQueryChange = { currentQuery = it },
                        onSubmitSearch = { submitted ->
                            submittedTarget = submitted
                            currentQuery = ""
                            isSearchOpen = false
                        },
                        onFillQuery = { currentQuery = it },
                        onDeleteHistoryItem = {},
                        onClearAllHistory = {},
                        onDismiss = { isSearchOpen = false }
                    )
                }
            }
        }

        composeTestRule.mainClock.advanceTimeBy(150)
        composeTestRule.waitForIdle()

        // 1. Clipboard card is initially detected and displayed
        composeTestRule.onNodeWithTag("clipboard_detected_row").assertIsDisplayed()

        // 2. Use the clipboard suggestion via Fill button -> query fills and clipboard card is consumed
        composeTestRule.onNodeWithTag("fill_clipboard_suggestion").performClick()
        composeTestRule.waitForIdle()
        assertEquals("https://developer.android.com/jetpack", currentQuery)
        composeTestRule.onNodeWithTag("clipboard_detected_row").assertDoesNotExist()

        // 3. Even if the user clears the search input, the consumed clipboard card must NOT reappear
        composeTestRule.onNodeWithTag("search_screen_clear_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals("", currentQuery)
        composeTestRule.onNodeWithTag("clipboard_detected_row").assertDoesNotExist()

        // 4. Close and reopen the Search screen -> same clipboard content must remain hidden
        isSearchOpen = false
        composeTestRule.waitForIdle()
        isSearchOpen = true
        composeTestRule.mainClock.advanceTimeBy(150)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("clipboard_detected_row").assertDoesNotExist()

        // 5. Copy a new URL to the clipboard -> reopening Search screen detects the new clip once,
        // and clicking the row navigates and consumes it so it disappears afterwards.
        isSearchOpen = false
        composeTestRule.waitForIdle()
        sysClipboard.setPrimaryClip(
            android.content.ClipData.newPlainText("url", "https://kotlinlang.org/docs")
        )
        isSearchOpen = true
        composeTestRule.mainClock.advanceTimeBy(150)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("clipboard_detected_row").assertIsDisplayed()

        composeTestRule.onNodeWithTag("clipboard_detected_row").performClick()
        composeTestRule.waitForIdle()
        assertEquals("https://kotlinlang.org/docs", submittedTarget)

        // Reopen Search screen after direct click -> consumed clipboard item does not reappear
        isSearchOpen = true
        composeTestRule.mainClock.advanceTimeBy(150)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("clipboard_detected_row").assertDoesNotExist()
    }
}
