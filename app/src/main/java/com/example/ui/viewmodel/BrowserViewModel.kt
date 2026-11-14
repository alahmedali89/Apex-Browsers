package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.webkit.WebView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.automation.ActiveRecordingSession
import com.example.automation.AutomationActionType
import com.example.automation.AutomationEngine
import com.example.automation.AutomationErrorCode
import com.example.automation.AutomationHudMode
import com.example.automation.AutomationStep
import com.example.automation.AutomationStepCodec
import com.example.automation.AutomationStepDecodeResult
import com.example.automation.AutomationToolbarStatus
import com.example.automation.AutomationType
import com.example.automation.ExtractedRecord
import com.example.automation.NanoChatMessage
import com.example.automation.NanoMultiAgentEngine
import com.example.automation.PageInspectionState
import com.example.automation.RecordedDomEvent
import com.example.automation.RecorderLifecycleResult
import com.example.automation.RecordingErrorInfo
import com.example.automation.RecordingOriginResult
import com.example.automation.RecordingStatus
import com.example.automation.RetrieverEngine
import com.example.automation.TabNavigationSynchronizer
import com.example.automation.TabSnapshotTarget
import com.example.automation.WorkflowExecutionState
import com.example.data.local.ApexDatabase
import com.example.data.local.AutomationExecutionEntity
import com.example.data.local.AutomationWorkflowEntity
import com.example.data.local.BlockedDomainLogEntity
import com.example.data.local.BookmarkEntity
import com.example.data.local.BrowsingHistoryEntity
import com.example.data.local.DownloadItemEntity
import com.example.data.local.ExtractionRuleEntity
import com.example.data.local.PrivacyStatSummaryEntity
import com.example.data.local.SavedPasswordEntity
import com.example.data.local.SearchHistoryEntity
import com.example.data.local.TopVisitEntity
import com.example.data.preferences.BrowserPreferences
import com.example.data.preferences.SearchEngineOption
import com.example.data.preferences.ThemeModeOption
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.BrowserRepository
import com.example.engine.FastDownloadEngine
import com.example.engine.LanguageCatalog
import com.example.engine.LanguageTranslationEngine
import com.example.engine.PasswordVaultUtil
import com.example.engine.PerformanceOptimizer
import com.example.engine.PreloadCandidate
import com.example.engine.PrivacyManager
import com.example.engine.SearchSuggestionEngine
import com.example.engine.SearchSuggestionItem
import org.json.JSONArray
import org.json.JSONObject
import com.example.ui.theme.SeedPalettePreset
import com.example.ui.theme.TabGroupColorOption
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TabGroupInfo(
    val id: String = "grp_${UUID.randomUUID()}",
    val title: String,
    val colorOption: TabGroupColorOption = TabGroupColorOption.COBALT
)

data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Home",
    val url: String = "apex://home",
    val requestedUrl: String = url,
    val loadRequestId: Long = 1L,
    val isPrivate: Boolean = false,
    val groupId: String? = null,
    val tabColor: TabGroupColorOption? = null,
    val isLoading: Boolean = false,
    val progress: Float = 0f,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val blockedCountOnPage: Int = 0,
    val isHttps: Boolean = true,
    val wasUpgradedToHttps: Boolean = false,
    val previewThumbnail: Bitmap? = null,
    val lastActiveTimeMillis: Long = System.currentTimeMillis()
) {
    val isHomePage: Boolean
        get() = url.isBlank() || url.equals("apex://home", ignoreCase = true)
}

sealed interface TabGridEntry {
    val gridKey: String

    data class SingleTab(val tab: BrowserTab) : TabGridEntry {
        override val gridKey: String = "single_${tab.id}"
    }

    data class GroupTab(
        val group: TabGroupInfo,
        val tabs: List<BrowserTab>
    ) : TabGridEntry {
        override val gridKey: String = "group_${group.id}"
    }
}

enum class ExtensionModule {
    NANO,
    RETRIEVER
}

enum class RetrieverDisplayMode {
    TABLE,
    CARDS
}

data class PendingCredentialPrompt(
    val siteName: String,
    val siteUrl: String,
    val username: String,
    val password: String,
    val existingId: Long? = null
)

data class PendingClosedTabUndo(
    val token: Long,
    val tabTitle: String
)

enum class NetworkStatusBannerState {
    HIDDEN,
    OFFLINE,
    BACK_ONLINE
}

enum class BrowserOverlayDestination {
    SETTINGS,
    PASSWORDS,
    DOWNLOADS,
    HISTORY,
    BOOKMARKS,
    TAB_SWITCHER,
    AUTOMATIONS
}

data class TabTranslationState(
    val tabId: String,
    val pageUrl: String = "",
    val detectedSourceCode: String? = null,
    val targetCode: String = "en",
    val isTranslated: Boolean = false,
    val isTranslating: Boolean = false,
    val isBannerVisible: Boolean = false,
    val bannerDismissed: Boolean = false,
    val errorMessage: String? = null
)

data class BrowserUiState(
    // Fresh launch: strictly 1 clean Home tab — no pre-opened tabs
    val tabs: List<BrowserTab> = listOf(
        BrowserTab(id = "tab_home", title = "Home", url = "apex://home")
    ),
    val tabGroups: List<TabGroupInfo> = emptyList(),
    val expandedGroupId: String? = null,
    val editingGroupId: String? = null,
    val activeTabId: String = "tab_home",
    val previousTabIndex: Int = 0,
    val isToolbarVisible: Boolean = true,
    val isDeviceOnline: Boolean = true,
    val networkBannerState: NetworkStatusBannerState = NetworkStatusBannerState.HIDDEN,
    val isSearchScreenOpen: Boolean = false,
    val searchQuery: String = "",
    val searchSuggestions: List<SearchSuggestionItem> = emptyList(),
    val isLoadingSuggestions: Boolean = false,
    val isTabSwitcherOpen: Boolean = false,
    val isBookmarksScreenOpen: Boolean = false,
    val isHistoryScreenOpen: Boolean = false,
    val isDownloadsScreenOpen: Boolean = false,
    val isPasswordManagerOpen: Boolean = false,
    val isSettingsScreenOpen: Boolean = false,
    val isAutomationWorkspaceOpen: Boolean = false,
    val selectedAutomationWorkflowId: Long? = null,
    val overlayBackStack: List<BrowserOverlayDestination> = emptyList(),
    val isMenuSheetOpen: Boolean = false,
    val isIncognitoUnlocked: Boolean = false,
    val isAutomationOpen: Boolean = false,
    val isAutomationHudOpen: Boolean = false,
    val automationHudMode: AutomationHudMode = AutomationHudMode.OVERVIEW,
    val automationToolbarStatus: AutomationToolbarStatus = AutomationToolbarStatus.IDLE,
    val pageInspection: PageInspectionState = PageInspectionState(),
    val recordingSession: ActiveRecordingSession = ActiveRecordingSession(),
    val instructionDraftText: String = "",
    val instructionDraftSteps: List<AutomationStep> = emptyList(),
    val executionState: WorkflowExecutionState? = null,
    val pendingCredentialPrompt: PendingCredentialPrompt? = null,
    val matchedCredentialsForActiveTab: List<SavedPasswordEntity> = emptyList(),
    val activeExtension: ExtensionModule = ExtensionModule.NANO,
    val snackbarNotice: String? = null,
    val pendingClosedTabUndo: PendingClosedTabUndo? = null,
    val preloadCandidates: List<PreloadCandidate> = emptyList(),
    val nanoMessages: List<NanoChatMessage> = listOf(
        NanoChatMessage(
            id = "welcome_nano",
            isUser = false,
            text = "Nano Multi-Agent ready. PlannerAgent, NavigatorAgent, and ValidatorAgent run locally over the active WebView DOM."
        )
    ),
    val isNanoRunning: Boolean = false,
    val selectedRuleId: Long? = null,
    val retrieverPromptInput: String = "",
    val extractedRecords: List<ExtractedRecord> = emptyList(),
    val isRetrieverRunning: Boolean = false,
    val retrieverStatusBanner: String = "Ready for single-page or multi-tab parallel capture",
    val retrieverDisplayMode: RetrieverDisplayMode = RetrieverDisplayMode.TABLE
) {
    val activeTab: BrowserTab
        get() = tabs.firstOrNull { it.id == activeTabId } ?: tabs.first()

    val activeTabIndex: Int
        get() = tabs.indexOfFirst { it.id == activeTabId }.coerceAtLeast(0)

    val activeTabGroup: TabGroupInfo?
        get() {
            val gId = activeTab.groupId ?: return null
            return tabGroups.firstOrNull { it.id == gId }
        }

    val activeGroupTabs: List<BrowserTab>
        get() {
            val gId = activeTabGroup?.id ?: return emptyList()
            return tabs.filter { it.groupId == gId }
        }

    val editingGroup: TabGroupInfo?
        get() {
            val editId = editingGroupId ?: return null
            return tabGroups.firstOrNull { it.id == editId }
        }

    val incognitoTabCount: Int
        get() = tabs.count { it.isPrivate }

    val gridEntries: List<TabGridEntry>
        get() {
            val emittedGroups = mutableSetOf<String>()
            val result = mutableListOf<TabGridEntry>()
            val groupLookup = tabGroups.associateBy { it.id }

            for (tab in tabs) {
                val gId = tab.groupId
                if (gId == null) {
                    result.add(TabGridEntry.SingleTab(tab))
                } else if (emittedGroups.add(gId)) {
                    val memberTabs = tabs.filter { it.groupId == gId }
                    val info = groupLookup[gId]
                    if (info != null && memberTabs.isNotEmpty()) {
                        result.add(TabGridEntry.GroupTab(group = info, tabs = memberTabs))
                    } else if (memberTabs.size >= 2) {
                        result.add(
                            TabGridEntry.GroupTab(
                                group = TabGroupInfo(id = gId, title = "Tab Group"),
                                tabs = memberTabs
                            )
                        )
                    } else {
                        result.add(TabGridEntry.SingleTab(tab))
                    }
                }
            }
            return result
        }
}

class BrowserViewModel(
    private val repository: BrowserRepository,
    val privacyManager: PrivacyManager = PrivacyManager(),
    val performanceOptimizer: PerformanceOptimizer = PerformanceOptimizer(),
    private val suggestionEngine: SearchSuggestionEngine = SearchSuggestionEngine(),
    private val nanoEngine: NanoMultiAgentEngine = NanoMultiAgentEngine(),
    private val retrieverEngine: RetrieverEngine = RetrieverEngine(),
    private val fastDownloadEngine: FastDownloadEngine? = null,
    private val translationEngine: LanguageTranslationEngine = LanguageTranslationEngine(),
    private val automationEngine: AutomationEngine = AutomationEngine()
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    private val _downloadedLanguageCodes = MutableStateFlow<Set<String>>(emptySet())
    val downloadedLanguageCodes: StateFlow<Set<String>> = _downloadedLanguageCodes.asStateFlow()

    private val _downloadingLanguageCodes = MutableStateFlow<Set<String>>(emptySet())
    val downloadingLanguageCodes: StateFlow<Set<String>> = _downloadingLanguageCodes.asStateFlow()

    private val _failedLanguageDownloads = MutableStateFlow<Set<String>>(emptySet())
    val failedLanguageDownloads: StateFlow<Set<String>> = _failedLanguageDownloads.asStateFlow()

    private val _tabTranslationStates = MutableStateFlow<Map<String, TabTranslationState>>(emptyMap())
    val tabTranslationStates: StateFlow<Map<String, TabTranslationState>> = _tabTranslationStates.asStateFlow()

    val preferencesState: StateFlow<BrowserPreferences> = repository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BrowserPreferences()
        )

    val privacyStatsState: StateFlow<PrivacyStatSummaryEntity> = repository.privacyStatsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PrivacyStatSummaryEntity()
        )

    val blockedLogsState: StateFlow<List<BlockedDomainLogEntity>> = repository.recentBlockedLogsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val extractionRulesState: StateFlow<List<ExtractionRuleEntity>> = repository.extractionRulesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val bookmarksState: StateFlow<List<BookmarkEntity>> = repository.bookmarksFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val searchHistoryState: StateFlow<List<SearchHistoryEntity>> = repository.searchHistoryFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val topVisitsState: StateFlow<List<TopVisitEntity>> = repository.topVisitsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val browsingHistoryState: StateFlow<List<BrowsingHistoryEntity>> = repository.browsingHistoryFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val browsingHistoryCountState: StateFlow<Int> = repository.browsingHistoryCountFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0
        )

    val downloadsState: StateFlow<List<DownloadItemEntity>> = repository.downloadsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val downloadsCountState: StateFlow<Int> = repository.downloadsCountFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0
        )

    val savedPasswordsState: StateFlow<List<SavedPasswordEntity>> = repository.savedPasswordsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val savedPasswordsCountState: StateFlow<Int> = repository.savedPasswordsCountFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0
        )

    val automationWorkflowsState: StateFlow<List<AutomationWorkflowEntity>> = repository.automationWorkflowsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val automationExecutionsState: StateFlow<List<AutomationExecutionEntity>> = repository.automationExecutionsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun observeExecutionsForWorkflow(workflowId: Long): Flow<List<AutomationExecutionEntity>> {
        return repository.observeAutomationExecutionsForWorkflow(workflowId)
    }

    internal val navigationSynchronizer = TabNavigationSynchronizer()

    private data class RecordedTabVisit(
        val loadRequestId: Long,
        val canonicalUrl: String,
        val host: String,
        val recordedAtMillis: Long
    )

    private data class ClosedTabSnapshot(
        val token: Long,
        val tab: BrowserTab,
        val originalIndex: Int,
        val previousActiveTabId: String,
        val previousExpandedGroupId: String?,
        val groupInfo: TabGroupInfo?,
        val autoCreatedReplacementTabId: String?
    )

    private data class PendingBlockedBatch(
        val pageHost: String,
        val totalCount: Int,
        val trackersDelta: Int,
        val adsDelta: Int,
        val bytesDelta: Long,
        val timeMsDelta: Long,
        val sampleDomain: String,
        val sampleCategory: String
    )

    private val webViewRegistry = mutableMapOf<String, WebView>()
    private val lastRecordedVisitByTab = mutableMapOf<String, RecordedTabVisit>()
    private val lastProgressUpdateAtByTab = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val pendingBlockedByTab = java.util.concurrent.ConcurrentHashMap<String, PendingBlockedBatch>()
    private val closedTabHistory = mutableListOf<ClosedTabSnapshot>()
    private val offlineFailedTabIds = mutableSetOf<String>()
    private var nextClosedTabUndoToken = 1L
    private var suggestionJob: Job? = null
    private var backOnlineBannerJob: Job? = null
    private var blockedFlushJob: Job? = null
    private var toolbarCompletedResetJob: Job? = null
    private var automationExecutionJob: Job? = null
    private var recordingLifecycleJob: Job? = null
    internal var lastRecordingRejectionCode: AutomationErrorCode = AutomationErrorCode.NONE
        private set
    private val pageInspectionByTab = mutableMapOf<String, PageInspectionState>()
    private var pendingDestructiveInstructionConfirm: Boolean = false
    private var pendingDestructiveWorkflowId: Long? = null
    private val blockedBatchLock = Any()

    init {
        viewModelScope.launch {
            repository.ensureFreshInitialized()
        }
        viewModelScope.launch {
            refreshDownloadedLanguageModels()
        }
        viewModelScope.launch {
            repository.savedPasswordsFlow.collect {
                refreshMatchedCredentialsForActiveTab()
            }
        }
    }

    fun registerWebView(tabId: String, webView: WebView) {
        val previous = webViewRegistry.put(tabId, webView)
        if (previous != null && previous !== webView) {
            val session = _uiState.value.recordingSession
            if (session.initiatingTabId == tabId) {
                automationEngine.detachRecorderBestEffort(previous, session.sessionId.ifBlank { null })
            }
        }
    }

    fun getWebView(tabId: String): WebView? = webViewRegistry[tabId]

    fun onWebViewPageLoadFailed(tabId: String, failingUrl: String) {
        onWebViewPageLoadFailedWithReason(tabId, failingUrl, "Page failed to load")
    }

    fun onWebViewPageLoadFailedWithReason(tabId: String, failingUrl: String, reason: String) {
        navigationSynchronizer.onPageFailed(tabId, failingUrl, reason)
        handleDocumentUnavailableForRecording(tabId, reason)
        if (!_uiState.value.isDeviceOnline) {
            offlineFailedTabIds.add(tabId)
        }
    }

    fun onNetworkConnectivityChanged(isOnline: Boolean, autoHideDelayMs: Long = 2_800L) {
        val prevState = _uiState.value
        if (!isOnline) {
            backOnlineBannerJob?.cancel()
            backOnlineBannerJob = null
            if (prevState.isDeviceOnline || prevState.networkBannerState != NetworkStatusBannerState.OFFLINE) {
                _uiState.update {
                    it.copy(
                        isDeviceOnline = false,
                        networkBannerState = NetworkStatusBannerState.OFFLINE
                    )
                }
            }
        } else {
            fastDownloadEngine?.onNetworkAvailable()
            val wasOffline = !prevState.isDeviceOnline || prevState.networkBannerState == NetworkStatusBannerState.OFFLINE
            if (wasOffline) {
                backOnlineBannerJob?.cancel()
                _uiState.update {
                    it.copy(
                        isDeviceOnline = true,
                        networkBannerState = NetworkStatusBannerState.BACK_ONLINE
                    )
                }
                val activeTab = _uiState.value.activeTab
                if (!activeTab.isHomePage && (activeTab.id in offlineFailedTabIds || !prevState.isDeviceOnline)) {
                    offlineFailedTabIds.remove(activeTab.id)
                    reloadActiveTab()
                }
                if (autoHideDelayMs > 0L) {
                    backOnlineBannerJob = viewModelScope.launch {
                        delay(autoHideDelayMs)
                        _uiState.update { current ->
                            if (current.isDeviceOnline && current.networkBannerState == NetworkStatusBannerState.BACK_ONLINE) {
                                current.copy(networkBannerState = NetworkStatusBannerState.HIDDEN)
                            } else {
                                current
                            }
                        }
                    }
                }
            }
        }
    }

    fun consumeSnackbarNotice() {
        _uiState.update { it.copy(snackbarNotice = null) }
    }

    fun consumeClosedTabUndo(token: Long) {
        _uiState.update { state ->
            if (state.pendingClosedTabUndo?.token == token) {
                state.copy(pendingClosedTabUndo = null)
            } else {
                state
            }
        }
    }

    // --- Dedicated Search Screen & Search-Engine Suggestions ---

    fun openSearchScreen(prefillCurrentUrl: Boolean = false) {
        _uiState.update {
            it.copy(
                isSearchScreenOpen = true,
                isBookmarksScreenOpen = false,
                isHistoryScreenOpen = false,
                isDownloadsScreenOpen = false,
                isPasswordManagerOpen = false,
                isSettingsScreenOpen = false,
                isAutomationWorkspaceOpen = false,
                selectedAutomationWorkflowId = null,
                isAutomationHudOpen = false,
                overlayBackStack = emptyList(),
                isMenuSheetOpen = false,
                isTabSwitcherOpen = false,
                isAutomationOpen = false,
                isToolbarVisible = true,
                searchQuery = "",
                searchSuggestions = emptyList(),
                isLoadingSuggestions = false
            )
        }
    }

    fun closeSearchScreen() {
        suggestionJob?.cancel()
        _uiState.update {
            it.copy(
                isSearchScreenOpen = false,
                isLoadingSuggestions = false
            )
        }
    }

    fun onSearchQueryChanged(newQuery: String) {
        _uiState.update {
            it.copy(
                searchQuery = newQuery,
                searchSuggestions = if (newQuery.isBlank()) emptyList() else it.searchSuggestions,
                isLoadingSuggestions = newQuery.isNotBlank()
            )
        }
        triggerSuggestionsFetch(newQuery, preferencesState.value.searchEngine)
    }

    fun selectSearchEngine(engine: SearchEngineOption) {
        viewModelScope.launch {
            repository.setSearchEngine(engine)
            val currentQ = _uiState.value.searchQuery
            if (currentQ.isNotBlank()) {
                triggerSuggestionsFetch(currentQ, engine)
            }
        }
    }

    private fun triggerSuggestionsFetch(query: String, engine: SearchEngineOption) {
        suggestionJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _uiState.update {
                it.copy(searchSuggestions = emptyList(), isLoadingSuggestions = false)
            }
            return
        }
        suggestionJob = viewModelScope.launch {
            delay(160)
            val results = suggestionEngine.fetchSuggestions(trimmed, engine)
            _uiState.update { state ->
                if (state.searchQuery.trim() == trimmed) {
                    state.copy(
                        searchSuggestions = results,
                        isLoadingSuggestions = false
                    )
                } else {
                    state
                }
            }
        }
    }

    fun deleteSearchHistoryItem(id: Long) {
        viewModelScope.launch { repository.deleteSearchHistoryItem(id) }
    }

    fun clearAllSearchHistory() {
        viewModelScope.launch { repository.clearAllSearchHistory() }
    }

    fun submitNavigation(rawInput: String, targetTabId: String = _uiState.value.activeTabId) {
        val trimmedInput = rawInput.trim()
        if (trimmedInput.isEmpty()) return

        val prefs = preferencesState.value
        val normalized = privacyManager.normalizeAndUpgradeUrl(
            rawInput = trimmedInput,
            httpsUpgradeEnabled = prefs.httpsUpgradeEnabled,
            searchEngineTemplate = prefs.searchEngineTemplate
        )

        val targetTab = _uiState.value.tabs.firstOrNull { it.id == targetTabId }
        if (!normalized.isBuiltInScheme && targetTab?.isPrivate != true) {
            viewModelScope.launch {
                repository.recordSearchQuery(
                    query = trimmedInput,
                    engineName = prefs.searchEngine.displayName
                )
            }
        }

        if (normalized.wasUpgradedToHttps) {
            viewModelScope.launch { repository.recordHttpsUpgrade() }
        }

        suggestionJob?.cancel()
        lastRecordedVisitByTab.remove(targetTabId)
        _tabTranslationStates.update { it - targetTabId }
        handleOriginNavigationForRecording(targetTabId, normalized.finalUrl)

        navigationSynchronizer.beginNavigation(targetTabId, normalized.finalUrl)
        val isHomeDest = normalized.finalUrl.equals("apex://home", ignoreCase = true)
        _uiState.update { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == targetTabId) {
                    tab.copy(
                        url = normalized.finalUrl,
                        requestedUrl = normalized.finalUrl,
                        loadRequestId = tab.loadRequestId + 1L,
                        title = if (isHomeDest) {
                            "Home"
                        } else {
                            deriveShortTitle(normalized.finalUrl)
                        },
                        blockedCountOnPage = 0,
                        isHttps = normalized.finalUrl.startsWith("https://") || normalized.isBuiltInScheme,
                        wasUpgradedToHttps = normalized.wasUpgradedToHttps,
                        isLoading = !isHomeDest,
                        progress = if (isHomeDest) 0f else 0.06f
                    )
                } else {
                    tab
                }
            }
            state.copy(
                tabs = updatedTabs,
                isSearchScreenOpen = false,
                isLoadingSuggestions = false,
                isBookmarksScreenOpen = false,
                isHistoryScreenOpen = false,
                isDownloadsScreenOpen = false,
                isPasswordManagerOpen = false,
                isSettingsScreenOpen = false,
                isAutomationWorkspaceOpen = false,
                selectedAutomationWorkflowId = null,
                isTabSwitcherOpen = false,
                overlayBackStack = emptyList(),
                isToolbarVisible = true
            )
        }
        if (targetTabId == _uiState.value.activeTabId) {
            if (isHomeDest) {
                pageInspectionByTab[targetTabId] = PageInspectionState()
                refreshAutomationToolbarStatus(clearTransientResult = true)
            } else {
                _uiState.update {
                    it.copy(
                        pageInspection = PageInspectionState(
                            domain = automationEngine.extractDomain(normalized.finalUrl),
                            url = normalized.finalUrl
                        )
                    )
                }
                refreshAutomationToolbarStatus(clearTransientResult = true)
            }
        }
    }

    fun recordHttpsUpgradeStat() {
        viewModelScope.launch { repository.recordHttpsUpgrade() }
    }

    fun openLinkInNewTab(
        rawUrl: String,
        inGroup: Boolean = false,
        isPrivate: Boolean = false,
        switchToTab: Boolean = true
    ) {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return

        val prefs = preferencesState.value
        val normalized = privacyManager.normalizeAndUpgradeUrl(
            rawInput = trimmed,
            httpsUpgradeEnabled = prefs.httpsUpgradeEnabled,
            searchEngineTemplate = prefs.searchEngineTemplate
        )

        _uiState.update { state ->
            val active = state.activeTab
            var updatedGroups = state.tabGroups
            var targetGroupId: String? = null
            var chosenColor: TabGroupColorOption? = null

            if (inGroup) {
                if (active.groupId != null) {
                    targetGroupId = active.groupId
                    chosenColor = state.tabGroups.firstOrNull { it.id == targetGroupId }?.colorOption ?: active.tabColor
                } else {
                    val newGrpId = "grp_${UUID.randomUUID()}"
                    val grpNumber = state.tabGroups.size + 1
                    chosenColor = active.tabColor ?: TabGroupColorOption.entries[
                        state.tabGroups.size % TabGroupColorOption.entries.size
                    ]
                    val newGrp = TabGroupInfo(
                        id = newGrpId,
                        title = "Group $grpNumber",
                        colorOption = chosenColor
                    )
                    updatedGroups = state.tabGroups + newGrp
                    targetGroupId = newGrpId
                }
            }

            val newTab = BrowserTab(
                title = deriveShortTitle(normalized.finalUrl),
                url = normalized.finalUrl,
                requestedUrl = normalized.finalUrl,
                loadRequestId = 1L,
                isPrivate = isPrivate,
                groupId = targetGroupId,
                tabColor = chosenColor,
                isLoading = true,
                progress = 0.06f,
                isHttps = normalized.finalUrl.startsWith("https://") || normalized.isBuiltInScheme,
                wasUpgradedToHttps = normalized.wasUpgradedToHttps
            )

            val baseTabs = if (inGroup && active.groupId == null && targetGroupId != null) {
                state.tabs.map { tab ->
                    if (tab.id == active.id) {
                        tab.copy(groupId = targetGroupId, tabColor = tab.tabColor ?: chosenColor)
                    } else {
                        tab
                    }
                }
            } else {
                state.tabs
            }

            val noticeText = when {
                isPrivate -> "Opened in Private tab"
                inGroup -> "Opened in new tab in group"
                else -> "Opened in new tab"
            }

            state.copy(
                previousTabIndex = state.activeTabIndex,
                tabs = baseTabs + newTab,
                tabGroups = updatedGroups,
                activeTabId = if (switchToTab) newTab.id else state.activeTabId,
                isHistoryScreenOpen = if (switchToTab) false else state.isHistoryScreenOpen,
                isDownloadsScreenOpen = if (switchToTab) false else state.isDownloadsScreenOpen,
                isPasswordManagerOpen = if (switchToTab) false else state.isPasswordManagerOpen,
                isBookmarksScreenOpen = if (switchToTab) false else state.isBookmarksScreenOpen,
                isSettingsScreenOpen = if (switchToTab) false else state.isSettingsScreenOpen,
                isAutomationWorkspaceOpen = if (switchToTab) false else state.isAutomationWorkspaceOpen,
                selectedAutomationWorkflowId = if (switchToTab) null else state.selectedAutomationWorkflowId,
                isAutomationHudOpen = if (switchToTab) false else state.isAutomationHudOpen,
                isTabSwitcherOpen = if (switchToTab) false else state.isTabSwitcherOpen,
                overlayBackStack = if (switchToTab) emptyList() else state.overlayBackStack,
                isIncognitoUnlocked = state.isIncognitoUnlocked || isPrivate,
                isToolbarVisible = true,
                snackbarNotice = noticeText
            )
        }
    }

    fun addLinkToReadingList(title: String, url: String) {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty()) return
        val cleanTitle = title.trim().ifEmpty { deriveShortTitle(cleanUrl) }
        viewModelScope.launch {
            repository.saveBookmark(url = cleanUrl, title = cleanTitle)
            _uiState.update {
                it.copy(snackbarNotice = "Added '$cleanTitle' to reading list")
            }
        }
    }

    fun onWebViewPageStarted(tabId: String, url: String) {
        if (url.startsWith("data:", ignoreCase = true)) {
            handleOriginNavigationForRecording(tabId, url)
            return
        }
        if (url.isBlank()) return
        lastProgressUpdateAtByTab[tabId] = System.currentTimeMillis()
        val currentTab = _uiState.value.tabs.firstOrNull { it.id == tabId }
        val resolvedStartedUrl = if (currentTab != null) mapBaseUrlBackToScheme(url, currentTab.url) else url
        navigationSynchronizer.onPageStarted(tabId, resolvedStartedUrl)
        handleOriginNavigationForRecording(tabId, resolvedStartedUrl)
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.map { tab ->
                    if (tab.id == tabId) {
                        val effectiveUrl = mapBaseUrlBackToScheme(url, tab.url)
                        tab.copy(
                            url = effectiveUrl,
                            isLoading = true,
                            progress = if (tab.isLoading) tab.progress.coerceAtLeast(0.08f) else 0.08f,
                            isHttps = effectiveUrl.startsWith("https://") || effectiveUrl.startsWith("apex://")
                        )
                    } else {
                        tab
                    }
                }
            )
        }
    }

    fun onWebViewUrlCommitted(tabId: String, url: String) {
        if (url.startsWith("data:", ignoreCase = true)) {
            handleOriginNavigationForRecording(tabId, url)
            return
        }
        if (url.isBlank()) return
        val currentTab = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
        val effectiveUrl = mapBaseUrlBackToScheme(url, currentTab.url)
        navigationSynchronizer.onUrlCommitted(tabId, effectiveUrl, isReload = false)
        handleOriginNavigationForRecording(tabId, effectiveUrl)
        val isHttps = effectiveUrl.startsWith("https://") || effectiveUrl.startsWith("apex://")
        if (currentTab.url == effectiveUrl && currentTab.isHttps == isHttps) return
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.map { tab ->
                    if (tab.id == tabId) {
                        tab.copy(
                            url = effectiveUrl,
                            isHttps = isHttps
                        )
                    } else {
                        tab
                    }
                }
            )
        }
    }

    fun onWebViewProgress(tabId: String, progress: Int) {
        val wv = webViewRegistry[tabId]
        val existingTab = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
        val isDone = progress >= 100

        // Prevent late subframe/iframe progress callbacks (< 100) from flipping a finished page
        // back into isLoading = true and causing the top loading bar to flicker on and off.
        if (!isDone && !existingTab.isLoading) {
            return
        }

        val rawTargetProgress = (progress / 100f).coerceIn(0f, 1f)
        val monotonicProgress = if (isDone) {
            1f
        } else {
            rawTargetProgress.coerceAtLeast(existingTab.progress)
        }
        val liveUrl = wv?.url?.takeIf { it.isNotBlank() && !it.startsWith("data:") }
            ?.let { mapBaseUrlBackToScheme(it, existingTab.url) }
            ?: existingTab.url
        val liveTitle = wv?.title?.takeIf { it.isNotBlank() } ?: existingTab.title
        val canGoBack = wv?.canGoBack() ?: existingTab.canGoBack
        val canGoForward = wv?.canGoForward() ?: existingTab.canGoForward

        val now = System.currentTimeMillis()
        val lastUpdateAt = lastProgressUpdateAtByTab[tabId] ?: 0L
        val navStateChanged = existingTab.url != liveUrl ||
            existingTab.canGoBack != canGoBack ||
            existingTab.canGoForward != canGoForward

        if (!isDone && !navStateChanged) {
            val progressDelta = monotonicProgress - existingTab.progress
            if (progressDelta < 0.08f || (now - lastUpdateAt) < 80L) {
                return
            }
        }
        lastProgressUpdateAtByTab[tabId] = now

        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.map { tab ->
                    if (tab.id == tabId) {
                        tab.copy(
                            url = liveUrl,
                            title = if (isDone) liveTitle else tab.title,
                            progress = monotonicProgress,
                            isLoading = !isDone,
                            canGoBack = canGoBack,
                            canGoForward = canGoForward
                        )
                    } else {
                        tab
                    }
                }
            )
        }
    }

    fun onWebViewPageFinished(
        tabId: String,
        title: String?,
        url: String?,
        canGoBack: Boolean,
        canGoForward: Boolean
    ) {
        flushPendingBlockedBatches()
        lastProgressUpdateAtByTab[tabId] = System.currentTimeMillis()
        var finishedUrl = ""
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.map { tab ->
                    if (tab.id == tabId) {
                        val effectiveUrl = if (!url.isNullOrBlank()) {
                            mapBaseUrlBackToScheme(url, tab.url)
                        } else {
                            tab.url
                        }
                        finishedUrl = effectiveUrl
                        tab.copy(
                            title = title?.takeIf { it.isNotBlank() } ?: deriveShortTitle(effectiveUrl),
                            url = effectiveUrl,
                            isLoading = false,
                            progress = 1f,
                            canGoBack = canGoBack,
                            canGoForward = canGoForward,
                            lastActiveTimeMillis = System.currentTimeMillis()
                        )
                    } else {
                        tab
                    }
                }
            )
        }

        val finishedTab = _uiState.value.tabs.firstOrNull { it.id == tabId }
        val resolvedFinishedUrl = finishedUrl.ifBlank { url.orEmpty() }
        navigationSynchronizer.onPageFinished(
            tabId = tabId,
            url = resolvedFinishedUrl
        )
        handleOriginNavigationForRecording(tabId, resolvedFinishedUrl)
        if (tabId == _uiState.value.activeTabId) {
            refreshMatchedCredentialsForActiveTab()
        }
        val wv = webViewRegistry[tabId]
        val activeSession = _uiState.value.recordingSession
        if (wv != null &&
            activeSession.status == RecordingStatus.RECORDING &&
            activeSession.initiatingTabId == tabId &&
            activeSession.sessionId.isNotBlank() &&
            automationEngine.areSameOrigin(resolvedFinishedUrl, activeSession.currentOrigin)
        ) {
            val expectedSessionId = activeSession.sessionId
            val expectedOrigin = activeSession.currentOrigin
            val expectedSeq = activeSession.lastSequenceNumber
            recordingLifecycleJob?.cancel()
            recordingLifecycleJob = viewModelScope.launch {
                val reinstall = automationEngine.installRecorderScript(
                    webView = wv,
                    sessionId = expectedSessionId,
                    tabId = tabId,
                    expectedOrigin = expectedOrigin,
                    initialSequenceNumber = expectedSeq
                )
                if (!reinstall.success) {
                    val current = _uiState.value.recordingSession
                    if (current.sessionId == expectedSessionId && current.status == RecordingStatus.RECORDING) {
                        _uiState.update { state ->
                            state.copy(
                                recordingSession = state.recordingSession.copy(
                                    status = RecordingStatus.ERROR,
                                    sessionId = "",
                                    recordingError = RecordingErrorInfo(
                                        code = reinstall.errorCode,
                                        message = reinstall.message.ifBlank { "Recorder failed to reattach after page load." }
                                    )
                                )
                            )
                        }
                        refreshAutomationToolbarStatus(clearTransientResult = false)
                    }
                }
            }
        }
        if (wv != null && tabId == _uiState.value.activeTabId) {
            if (finishedTab != null && !finishedTab.isHomePage) {
                scanActivePageElements(silent = true)
            } else {
                pageInspectionByTab[tabId] = PageInspectionState()
                refreshAutomationToolbarStatus(clearTransientResult = false)
            }
        }
        if (wv != null && tabId == _uiState.value.activeTabId && finishedTab?.isPrivate != true) {
            performanceOptimizer.preloadLikelyNextPages(
                webView = wv,
                enabled = preferencesState.value.aggressivePreloadEnabled
            ) { candidates ->
                _uiState.update { it.copy(preloadCandidates = candidates) }
            }
        }

        // Record browsing history for non-private tabs (deduplicated across multiple WebView onPageFinished callbacks)
        if (finishedTab != null &&
            !finishedTab.isPrivate &&
            !finishedTab.isHomePage &&
            finishedUrl.isNotBlank() &&
            !finishedUrl.equals("about:blank", ignoreCase = true) &&
            !finishedUrl.startsWith("data:", ignoreCase = true)
        ) {
            val canonicalUrl = repository.normalizeHistoryUrl(finishedUrl)
            if (canonicalUrl.isNotBlank()) {
                val now = System.currentTimeMillis()
                val host = canonicalUrl.substringAfter("://", canonicalUrl)
                    .substringBefore('/')
                    .removePrefix("www.")
                    .lowercase()
                val prev = lastRecordedVisitByTab[tabId]

                val isSameNavigationVisit = prev != null && (
                    prev.canonicalUrl.equals(canonicalUrl, ignoreCase = true) ||
                        (prev.loadRequestId == finishedTab.loadRequestId &&
                            (now - prev.recordedAtMillis) < 12_000L) ||
                        ((now - prev.recordedAtMillis) < 4_500L)
                )

                val previousUrlInSameLoad = if (isSameNavigationVisit) prev?.canonicalUrl else null
                lastRecordedVisitByTab[tabId] = RecordedTabVisit(
                    loadRequestId = finishedTab.loadRequestId,
                    canonicalUrl = canonicalUrl,
                    host = host,
                    recordedAtMillis = if (isSameNavigationVisit && prev != null) prev.recordedAtMillis else now
                )

                val shouldIncrementVisit = !isSameNavigationVisit
                val pageTitle = finishedTab.title
                val pageIsHttps = finishedTab.isHttps
                val pageBlockedCount = finishedTab.blockedCountOnPage

                viewModelScope.launch {
                    repository.recordPageVisit(
                        url = canonicalUrl,
                        title = pageTitle,
                        isHttps = pageIsHttps,
                        trackersBlockedOnVisit = pageBlockedCount,
                        incrementVisitCount = shouldIncrementVisit,
                        previousUrlInSameLoad = previousUrlInSameLoad
                    )
                }
            }
        }
    }

    fun onSubresourceBlocked(
        tabId: String,
        pageHost: String,
        blockedDomain: String,
        category: String,
        bytesSaved: Long,
        timeSavedMs: Long
    ) {
        val trackersAdd = if (category == "Tracker") 1 else 0
        val adsAdd = if (category == "Ad Network") 1 else 0
        synchronized(blockedBatchLock) {
            val current = pendingBlockedByTab[tabId]
            pendingBlockedByTab[tabId] = if (current == null) {
                PendingBlockedBatch(
                    pageHost = pageHost,
                    totalCount = 1,
                    trackersDelta = trackersAdd,
                    adsDelta = adsAdd,
                    bytesDelta = bytesSaved,
                    timeMsDelta = timeSavedMs,
                    sampleDomain = blockedDomain,
                    sampleCategory = category
                )
            } else {
                current.copy(
                    pageHost = pageHost.ifBlank { current.pageHost },
                    totalCount = current.totalCount + 1,
                    trackersDelta = current.trackersDelta + trackersAdd,
                    adsDelta = current.adsDelta + adsAdd,
                    bytesDelta = current.bytesDelta + bytesSaved,
                    timeMsDelta = current.timeMsDelta + timeSavedMs,
                    sampleDomain = blockedDomain.ifBlank { current.sampleDomain },
                    sampleCategory = category.ifBlank { current.sampleCategory }
                )
            }
            if (blockedFlushJob?.isActive != true) {
                blockedFlushJob = viewModelScope.launch {
                    delay(380L)
                    flushPendingBlockedBatches()
                }
            }
        }
    }

    private fun flushPendingBlockedBatches() {
        val drained: Map<String, PendingBlockedBatch>
        synchronized(blockedBatchLock) {
            blockedFlushJob?.cancel()
            blockedFlushJob = null
            if (pendingBlockedByTab.isEmpty()) return
            drained = HashMap(pendingBlockedByTab)
            pendingBlockedByTab.clear()
        }

        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.map { tab ->
                    val batch = drained[tab.id]
                    if (batch != null) {
                        tab.copy(blockedCountOnPage = tab.blockedCountOnPage + batch.totalCount)
                    } else {
                        tab
                    }
                }
            )
        }

        viewModelScope.launch {
            drained.values.forEach { batch ->
                repository.recordBlockedItems(
                    pageHost = batch.pageHost,
                    trackersDelta = batch.trackersDelta,
                    adsDelta = batch.adsDelta,
                    bytesDelta = batch.bytesDelta,
                    timeMsDelta = batch.timeMsDelta,
                    sampleDomain = batch.sampleDomain,
                    sampleCategory = batch.sampleCategory
                )
            }
        }
    }

    fun onWebViewScrollDelta(dy: Int) {
        // Keep the top toolbar stably visible during WebView scrolling so Scaffold
        // never animates innerPadding.top and resizes the WebView mid-gesture.
        if (!_uiState.value.isToolbarVisible) {
            _uiState.update { it.copy(isToolbarVisible = true) }
        }
    }

    // --- Tab & Draggable Tab Group Management ---

    fun selectTab(tabId: String) {
        _uiState.update { state ->
            val prevIdx = state.activeTabIndex
            val targetTab = state.tabs.firstOrNull { it.id == tabId } ?: return@update state
            state.copy(
                previousTabIndex = prevIdx,
                activeTabId = tabId,
                expandedGroupId = null,
                isTabSwitcherOpen = false,
                isBookmarksScreenOpen = false,
                isHistoryScreenOpen = false,
                isDownloadsScreenOpen = false,
                isPasswordManagerOpen = false,
                isSettingsScreenOpen = false,
                isAutomationWorkspaceOpen = false,
                selectedAutomationWorkflowId = null,
                overlayBackStack = emptyList(),
                isSearchScreenOpen = false,
                isToolbarVisible = true,
                pageInspection = pageInspectionByTab[tabId] ?: if (targetTab.isHomePage) {
                    PageInspectionState()
                } else {
                    PageInspectionState(
                        domain = automationEngine.extractDomain(targetTab.url),
                        url = targetTab.url
                    )
                }
            )
        }
        refreshMatchedCredentialsForActiveTab()
        val selectedTab = _uiState.value.activeTab
        val wv = webViewRegistry[tabId]
        if (wv != null) {
            if (!selectedTab.isHomePage && (pageInspectionByTab[tabId]?.elements.isNullOrEmpty())) {
                scanActivePageElements(silent = true)
            }
        }
        val activeSession = _uiState.value.recordingSession
        if ((activeSession.status == RecordingStatus.RECORDING || activeSession.status == RecordingStatus.PAUSED) &&
            activeSession.initiatingTabId.isNotBlank() &&
            tabId != activeSession.initiatingTabId
        ) {
            _uiState.update { state ->
                state.copy(
                    snackbarNotice = "Recording continues in ${activeSession.targetDomain.ifBlank { "original" }} tab"
                )
            }
        }
        refreshAutomationToolbarStatus(clearTransientResult = true)
    }

    fun selectNextTab() {
        val state = _uiState.value
        val nextIndex = state.activeTabIndex + 1
        if (nextIndex in state.tabs.indices) {
            selectTab(state.tabs[nextIndex].id)
        }
    }

    fun selectPreviousTab() {
        val state = _uiState.value
        val prevIndex = state.activeTabIndex - 1
        if (prevIndex in state.tabs.indices) {
            selectTab(state.tabs[prevIndex].id)
        }
    }

    fun createNewTab(
        initialUrl: String = "apex://home",
        groupId: String? = null,
        stayInSwitcher: Boolean = groupId != null
    ) {
        val inheritedColor = groupId?.let { gId ->
            _uiState.value.tabGroups.firstOrNull { it.id == gId }?.colorOption
        }
        val newTab = BrowserTab(
            title = if (initialUrl == "apex://home") "Home" else deriveShortTitle(initialUrl),
            url = initialUrl,
            groupId = groupId,
            tabColor = inheritedColor
        )
        _uiState.update { state ->
            state.copy(
                previousTabIndex = state.activeTabIndex,
                tabs = state.tabs + newTab,
                activeTabId = if (!stayInSwitcher) newTab.id else state.activeTabId,
                isTabSwitcherOpen = stayInSwitcher,
                isBookmarksScreenOpen = false,
                isHistoryScreenOpen = false,
                isDownloadsScreenOpen = false,
                isPasswordManagerOpen = false,
                isSettingsScreenOpen = false,
                isAutomationWorkspaceOpen = false,
                selectedAutomationWorkflowId = null,
                isAutomationHudOpen = false,
                overlayBackStack = if (stayInSwitcher) state.overlayBackStack else emptyList(),
                isSearchScreenOpen = false,
                isToolbarVisible = true
            )
        }
        if (initialUrl != "apex://home") {
            submitNavigation(initialUrl, newTab.id)
        }
    }

    fun createNewIncognitoTab(
        initialUrl: String = "apex://home",
        stayInSwitcher: Boolean = false
    ) {
        val newTab = BrowserTab(
            title = if (initialUrl == "apex://home") "Incognito" else deriveShortTitle(initialUrl),
            url = initialUrl,
            isPrivate = true
        )
        _uiState.update { state ->
            state.copy(
                previousTabIndex = state.activeTabIndex,
                tabs = state.tabs + newTab,
                activeTabId = if (!stayInSwitcher) newTab.id else state.activeTabId,
                isTabSwitcherOpen = stayInSwitcher,
                isBookmarksScreenOpen = false,
                isHistoryScreenOpen = false,
                isDownloadsScreenOpen = false,
                isPasswordManagerOpen = false,
                isSettingsScreenOpen = false,
                isAutomationWorkspaceOpen = false,
                selectedAutomationWorkflowId = null,
                isAutomationHudOpen = false,
                overlayBackStack = if (stayInSwitcher) state.overlayBackStack else emptyList(),
                isMenuSheetOpen = false,
                isSearchScreenOpen = false,
                isIncognitoUnlocked = true,
                isToolbarVisible = true,
                snackbarNotice = "Incognito mode active • History, cookies & site data will not be saved"
            )
        }
        if (initialUrl != "apex://home") {
            submitNavigation(initialUrl, newTab.id)
        }
    }

    fun closeAllIncognitoTabs() {
        val currentState = _uiState.value
        val incognitoIds = currentState.tabs.filter { it.isPrivate }.map { it.id }.toSet()
        if (incognitoIds.isEmpty()) {
            _uiState.update { it.copy(isIncognitoUnlocked = false) }
            return
        }

        handleTabsClosedForRecording(incognitoIds)
        incognitoIds.forEach { id ->
            lastRecordedVisitByTab.remove(id)
            navigationSynchronizer.onTabClosed(id)
            webViewRegistry.remove(id)?.let { wv ->
                destroyWebViewSafely(wv, isIncognito = true)
            }
        }
        performanceOptimizer.wipeIncognitoSessionStorage()

        _uiState.update { state ->
            val remaining = state.tabs.filterNot { it.isPrivate }
            if (remaining.isEmpty()) {
                val fresh = BrowserTab(title = "Home", url = "apex://home")
                state.copy(
                    tabs = listOf(fresh),
                    tabGroups = emptyList(),
                    expandedGroupId = null,
                    editingGroupId = null,
                    activeTabId = fresh.id,
                    isIncognitoUnlocked = false,
                    snackbarNotice = "Incognito session ended • Cookies & site data wiped"
                )
            } else {
                val (cleanTabs, cleanGroups) = normalizeGroups(remaining, state.tabGroups)
                val nextActiveId = if (state.activeTabId in incognitoIds) {
                    cleanTabs.last().id
                } else {
                    state.activeTabId
                }
                state.copy(
                    tabs = cleanTabs,
                    tabGroups = cleanGroups,
                    activeTabId = nextActiveId,
                    isIncognitoUnlocked = false,
                    snackbarNotice = "Incognito session ended • Cookies & site data wiped"
                )
            }
        }
    }

    fun closeSelectedTabs(tabIds: Set<String>) {
        if (tabIds.isEmpty()) return
        val currentTabs = _uiState.value.tabs
        val closingTabs = currentTabs.filter { it.id in tabIds }
        val closedAnyIncognito = closingTabs.any { it.isPrivate }

        handleTabsClosedForRecording(closingTabs.map { it.id }.toSet())
        closingTabs.forEach { tab ->
            lastRecordedVisitByTab.remove(tab.id)
            navigationSynchronizer.onTabClosed(tab.id)
            webViewRegistry.remove(tab.id)?.let { wv ->
                destroyWebViewSafely(wv, isIncognito = tab.isPrivate)
            }
        }

        val remainingAfterClose = currentTabs.filterNot { it.id in tabIds }
        val anyIncognitoLeft = remainingAfterClose.any { it.isPrivate }
        if (closedAnyIncognito && !anyIncognitoLeft) {
            performanceOptimizer.wipeIncognitoSessionStorage()
        }

        _uiState.update { state ->
            val remaining = state.tabs.filterNot { it.id in tabIds }
            if (remaining.isEmpty()) {
                val fresh = BrowserTab(title = "Home", url = "apex://home")
                state.copy(
                    tabs = listOf(fresh),
                    tabGroups = emptyList(),
                    expandedGroupId = null,
                    editingGroupId = null,
                    activeTabId = fresh.id,
                    isIncognitoUnlocked = false,
                    snackbarNotice = "Closed ${closingTabs.size} tab${if (closingTabs.size == 1) "" else "s"}"
                )
            } else {
                val (cleanTabs, cleanGroups) = normalizeGroups(remaining, state.tabGroups)
                val nextActiveId = if (state.activeTabId in tabIds) {
                    cleanTabs.last().id
                } else {
                    state.activeTabId
                }
                state.copy(
                    tabs = cleanTabs,
                    tabGroups = cleanGroups,
                    activeTabId = nextActiveId,
                    isIncognitoUnlocked = anyIncognitoLeft,
                    snackbarNotice = "Closed ${closingTabs.size} tab${if (closingTabs.size == 1) "" else "s"}"
                )
            }
        }
    }

    fun groupSelectedTabs(tabIds: Set<String>) {
        if (tabIds.isEmpty()) return
        _uiState.update { state ->
            val newGroupId = "grp_${UUID.randomUUID()}"
            val groupNumber = state.tabGroups.size + 1
            val defaultColor = TabGroupColorOption.entries[
                state.tabGroups.size % TabGroupColorOption.entries.size
            ]
            val newGroup = TabGroupInfo(
                id = newGroupId,
                title = "Group $groupNumber",
                colorOption = defaultColor
            )
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id in tabIds) {
                    tab.copy(groupId = newGroupId, tabColor = tab.tabColor ?: defaultColor)
                } else {
                    tab
                }
            }
            val (cleanTabs, cleanGroups) = normalizeGroups(updatedTabs, state.tabGroups + newGroup)
            state.copy(
                tabs = cleanTabs,
                tabGroups = cleanGroups,
                snackbarNotice = "Grouped ${tabIds.size} tab${if (tabIds.size == 1) "" else "s"} into ${newGroup.title}"
            )
        }
    }

    fun createNewTabGroupFromSwitcher() {
        _uiState.update { state ->
            val newGroupId = "grp_${UUID.randomUUID()}"
            val groupNumber = state.tabGroups.size + 1
            val defaultColor = TabGroupColorOption.entries[
                state.tabGroups.size % TabGroupColorOption.entries.size
            ]
            val newGroup = TabGroupInfo(
                id = newGroupId,
                title = "Group $groupNumber",
                colorOption = defaultColor
            )

            val ungroupedTabs = state.tabs.filter { it.groupId == null }
            val updatedTabs = if (ungroupedTabs.size >= 2) {
                val idsToGroup = ungroupedTabs.takeLast(2).map { it.id }.toSet()
                state.tabs.map { tab ->
                    if (tab.id in idsToGroup) {
                        tab.copy(groupId = newGroupId, tabColor = tab.tabColor ?: defaultColor)
                    } else {
                        tab
                    }
                }
            } else if (ungroupedTabs.size == 1) {
                val existingUngroupedId = ungroupedTabs.single().id
                val companionTab = BrowserTab(
                    title = "Home",
                    url = "apex://home",
                    groupId = newGroupId,
                    tabColor = defaultColor
                )
                state.tabs.map { tab ->
                    if (tab.id == existingUngroupedId) {
                        tab.copy(groupId = newGroupId, tabColor = tab.tabColor ?: defaultColor)
                    } else {
                        tab
                    }
                } + companionTab
            } else {
                val t1 = BrowserTab(title = "Home", url = "apex://home", groupId = newGroupId, tabColor = defaultColor)
                val t2 = BrowserTab(title = "Home", url = "apex://home", groupId = newGroupId, tabColor = defaultColor)
                state.tabs + t1 + t2
            }

            val (cleanTabs, cleanGroups) = normalizeGroups(updatedTabs, state.tabGroups + newGroup)
            state.copy(
                tabs = cleanTabs,
                tabGroups = cleanGroups,
                editingGroupId = newGroupId,
                isTabSwitcherOpen = true
            )
        }
    }

    fun mergeGridEntriesIntoGroup(draggedKey: String, targetKey: String) {
        if (draggedKey == targetKey) return

        _uiState.update { state ->
            val draggedIsGroup = draggedKey.startsWith("group_")
            val targetIsGroup = targetKey.startsWith("group_")

            val draggedId = draggedKey.removePrefix("single_").removePrefix("group_")
            val targetId = targetKey.removePrefix("single_").removePrefix("group_")

            when {
                !draggedIsGroup && !targetIsGroup -> {
                    val newGroupId = "grp_${UUID.randomUUID()}"
                    val groupNumber = state.tabGroups.size + 1
                    val targetTab = state.tabs.firstOrNull { it.id == targetId }
                    val draggedTab = state.tabs.firstOrNull { it.id == draggedId }
                    val chosenColor = targetTab?.tabColor
                        ?: draggedTab?.tabColor
                        ?: TabGroupColorOption.entries[
                            state.tabGroups.size % TabGroupColorOption.entries.size
                        ]
                    val newGroup = TabGroupInfo(
                        id = newGroupId,
                        title = "Group $groupNumber",
                        colorOption = chosenColor
                    )

                    val updatedTabs = state.tabs.map { tab ->
                        if (tab.id == targetId || tab.id == draggedId) {
                            tab.copy(groupId = newGroupId, tabColor = tab.tabColor ?: chosenColor)
                        } else {
                            tab
                        }
                    }
                    normalizeGroups(
                        tabs = updatedTabs,
                        groups = state.tabGroups + newGroup
                    ).let { (cleanTabs, cleanGroups) ->
                        state.copy(tabs = cleanTabs, tabGroups = cleanGroups)
                    }
                }

                !draggedIsGroup && targetIsGroup -> {
                    val targetGroupColor = state.tabGroups.firstOrNull { it.id == targetId }?.colorOption
                    val updatedTabs = state.tabs.map { tab ->
                        if (tab.id == draggedId) {
                            tab.copy(groupId = targetId, tabColor = tab.tabColor ?: targetGroupColor)
                        } else {
                            tab
                        }
                    }
                    normalizeGroups(updatedTabs, state.tabGroups).let { (cleanTabs, cleanGroups) ->
                        state.copy(tabs = cleanTabs, tabGroups = cleanGroups)
                    }
                }

                draggedIsGroup && !targetIsGroup -> {
                    val draggedGroupColor = state.tabGroups.firstOrNull { it.id == draggedId }?.colorOption
                    val updatedTabs = state.tabs.map { tab ->
                        if (tab.id == targetId) {
                            tab.copy(groupId = draggedId, tabColor = tab.tabColor ?: draggedGroupColor)
                        } else {
                            tab
                        }
                    }
                    normalizeGroups(updatedTabs, state.tabGroups).let { (cleanTabs, cleanGroups) ->
                        state.copy(tabs = cleanTabs, tabGroups = cleanGroups)
                    }
                }

                else -> {
                    val targetGroupColor = state.tabGroups.firstOrNull { it.id == targetId }?.colorOption
                    val updatedTabs = state.tabs.map { tab ->
                        if (tab.groupId == draggedId) {
                            tab.copy(groupId = targetId, tabColor = targetGroupColor ?: tab.tabColor)
                        } else {
                            tab
                        }
                    }
                    normalizeGroups(updatedTabs, state.tabGroups).let { (cleanTabs, cleanGroups) ->
                        state.copy(
                            tabs = cleanTabs,
                            tabGroups = cleanGroups,
                            expandedGroupId = if (state.expandedGroupId == draggedId) targetId else state.expandedGroupId
                        )
                    }
                }
            }
        }
    }

    fun moveGroupedTabToGridTarget(tabId: String, targetGridKey: String?) {
        if (targetGridKey == null) {
            ungroupTab(tabId)
            return
        }
        val currentTab = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
        val sourceGroupKey = currentTab.groupId?.let { "group_$it" }
        if (targetGridKey == sourceGroupKey || targetGridKey == "single_$tabId") {
            return
        }

        _uiState.update { state ->
            if (targetGridKey.startsWith("group_")) {
                val destGroupId = targetGridKey.removePrefix("group_")
                val destColor = state.tabGroups.firstOrNull { it.id == destGroupId }?.colorOption
                val updatedTabs = state.tabs.map { tab ->
                    if (tab.id == tabId) {
                        tab.copy(groupId = destGroupId, tabColor = destColor ?: tab.tabColor)
                    } else {
                        tab
                    }
                }
                val (cleanTabs, cleanGroups) = normalizeGroups(updatedTabs, state.tabGroups)
                state.copy(tabs = cleanTabs, tabGroups = cleanGroups)
            } else {
                val otherTabId = targetGridKey.removePrefix("single_")
                val newGroupId = "grp_${UUID.randomUUID()}"
                val groupNumber = state.tabGroups.size + 1
                val chosenColor = TabGroupColorOption.entries[
                    state.tabGroups.size % TabGroupColorOption.entries.size
                ]
                val newGroup = TabGroupInfo(
                    id = newGroupId,
                    title = "Group $groupNumber",
                    colorOption = chosenColor
                )
                val updatedTabs = state.tabs.map { tab ->
                    if (tab.id == tabId || tab.id == otherTabId) {
                        tab.copy(groupId = newGroupId, tabColor = chosenColor)
                    } else {
                        tab
                    }
                }
                val (cleanTabs, cleanGroups) = normalizeGroups(updatedTabs, state.tabGroups + newGroup)
                state.copy(tabs = cleanTabs, tabGroups = cleanGroups)
            }
        }
    }

    fun openGroupEditor(groupId: String) {
        _uiState.update { it.copy(editingGroupId = groupId) }
    }

    fun dismissGroupEditor() {
        _uiState.update { it.copy(editingGroupId = null) }
    }

    fun updateGroupTitleAndColor(
        groupId: String,
        newTitle: String,
        colorOption: TabGroupColorOption
    ) {
        _uiState.update { state ->
            val trimmed = newTitle.trim()
            val updatedGroups = state.tabGroups.map { grp ->
                if (grp.id == groupId) {
                    grp.copy(
                        title = trimmed.ifEmpty { grp.title },
                        colorOption = colorOption
                    )
                } else {
                    grp
                }
            }
            val updatedTabs = state.tabs.map { tab ->
                if (tab.groupId == groupId) {
                    tab.copy(tabColor = colorOption)
                } else {
                    tab
                }
            }
            state.copy(
                tabs = updatedTabs,
                tabGroups = updatedGroups,
                editingGroupId = null
            )
        }
    }

    fun cycleTabColor(tabId: String) {
        _uiState.update { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == tabId) {
                    val nextColor = tab.tabColor?.next() ?: TabGroupColorOption.COBALT
                    tab.copy(tabColor = nextColor)
                } else {
                    tab
                }
            }
            state.copy(tabs = updatedTabs)
        }
    }

    fun ungroupTab(tabId: String) {
        _uiState.update { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == tabId) tab.copy(groupId = null) else tab
            }
            val (cleanTabs, cleanGroups) = normalizeGroups(updatedTabs, state.tabGroups)
            val validExpanded = state.expandedGroupId?.takeIf { expId ->
                cleanGroups.any { it.id == expId }
            }
            val validEditing = state.editingGroupId?.takeIf { editId ->
                cleanGroups.any { it.id == editId }
            }
            state.copy(
                tabs = cleanTabs,
                tabGroups = cleanGroups,
                expandedGroupId = validExpanded,
                editingGroupId = validEditing
            )
        }
    }

    fun ungroupAllInGroup(groupId: String) {
        _uiState.update { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.groupId == groupId) tab.copy(groupId = null) else tab
            }
            val remainingGroups = state.tabGroups.filterNot { it.id == groupId }
            state.copy(
                tabs = updatedTabs,
                tabGroups = remainingGroups,
                expandedGroupId = if (state.expandedGroupId == groupId) null else state.expandedGroupId,
                editingGroupId = if (state.editingGroupId == groupId) null else state.editingGroupId
            )
        }
    }

    fun setExpandedGroup(groupId: String?) {
        _uiState.update { it.copy(expandedGroupId = groupId) }
    }

    fun closeGroup(groupId: String) {
        val currentTabs = _uiState.value.tabs
        val groupTabs = currentTabs.filter { it.groupId == groupId }
        val groupTabIds = groupTabs.map { it.id }.toSet()
        val closedAnyIncognito = groupTabs.any { it.isPrivate }

        handleTabsClosedForRecording(groupTabIds)
        groupTabs.forEach { tab ->
            lastRecordedVisitByTab.remove(tab.id)
            navigationSynchronizer.onTabClosed(tab.id)
            webViewRegistry.remove(tab.id)?.let { wv ->
                destroyWebViewSafely(wv, isIncognito = tab.isPrivate)
            }
        }

        val remainingTabsAfterClose = currentTabs.filterNot { it.id in groupTabIds }
        val incognitoSessionEnded = closedAnyIncognito && remainingTabsAfterClose.none { it.isPrivate }
        if (incognitoSessionEnded) {
            performanceOptimizer.wipeIncognitoSessionStorage()
        }

        _uiState.update { state ->
            val remaining = state.tabs.filterNot { it.id in groupTabIds }
            val sessionNotice = if (incognitoSessionEnded) {
                "Incognito session ended • Cookies & site data wiped"
            } else {
                state.snackbarNotice
            }
            if (remaining.isEmpty()) {
                val fresh = BrowserTab(title = "Home", url = "apex://home")
                state.copy(
                    tabs = listOf(fresh),
                    tabGroups = emptyList(),
                    expandedGroupId = null,
                    editingGroupId = null,
                    activeTabId = fresh.id,
                    snackbarNotice = sessionNotice
                )
            } else {
                val (cleanTabs, cleanGroups) = normalizeGroups(remaining, state.tabGroups)
                val nextActive = if (state.activeTabId in groupTabIds) {
                    cleanTabs.last().id
                } else {
                    state.activeTabId
                }
                state.copy(
                    tabs = cleanTabs,
                    tabGroups = cleanGroups,
                    expandedGroupId = if (state.expandedGroupId == groupId) null else state.expandedGroupId,
                    editingGroupId = if (state.editingGroupId == groupId) null else state.editingGroupId,
                    activeTabId = nextActive,
                    snackbarNotice = sessionNotice
                )
            }
        }
    }

    fun closeTab(tabId: String) {
        val snapshotBeforeClose = _uiState.value
        val closingIndex = snapshotBeforeClose.tabs.indexOfFirst { it.id == tabId }
        val closingTab = if (closingIndex >= 0) snapshotBeforeClose.tabs[closingIndex] else null
        if (closingTab == null) return

        val closingGroupInfo = closingTab.groupId?.let { gId ->
            snapshotBeforeClose.tabGroups.firstOrNull { it.id == gId }
        }
        val undoToken = nextClosedTabUndoToken++

        handleTabsClosedForRecording(setOf(tabId))
        lastRecordedVisitByTab.remove(tabId)
        navigationSynchronizer.onTabClosed(tabId)
        webViewRegistry.remove(tabId)?.let { wv ->
            destroyWebViewSafely(wv, isIncognito = closingTab.isPrivate)
        }

        val remainingAfterClose = snapshotBeforeClose.tabs.filterNot { it.id == tabId }
        val incognitoSessionEnded = closingTab.isPrivate && remainingAfterClose.none { it.isPrivate }
        if (incognitoSessionEnded) {
            performanceOptimizer.wipeIncognitoSessionStorage()
        }

        var autoReplacementTabId: String? = null
        _uiState.update { state ->
            val closedTab = state.tabs.firstOrNull { it.id == tabId }
            val remaining = state.tabs.filterNot { it.id == tabId }
            val sessionNotice = if (incognitoSessionEnded) {
                "Incognito session ended • Cookies & site data wiped"
            } else {
                state.snackbarNotice
            }
            if (remaining.isEmpty()) {
                val fresh = BrowserTab(title = "Home", url = "apex://home")
                autoReplacementTabId = fresh.id
                state.copy(
                    tabs = listOf(fresh),
                    tabGroups = emptyList(),
                    expandedGroupId = null,
                    editingGroupId = null,
                    activeTabId = fresh.id,
                    isIncognitoUnlocked = false,
                    snackbarNotice = sessionNotice,
                    pendingClosedTabUndo = PendingClosedTabUndo(
                        token = undoToken,
                        tabTitle = closedTab?.title ?: closingTab.title
                    )
                )
            } else {
                val (cleanTabs, cleanGroups) = normalizeGroups(remaining, state.tabGroups)
                val nextActiveId = if (state.activeTabId == tabId) {
                    val closedGroupId = closedTab?.groupId
                    val siblingInGroup = closedGroupId?.let { gId ->
                        cleanTabs.lastOrNull { it.groupId == gId }
                    }
                    siblingInGroup?.id ?: cleanTabs.last().id
                } else {
                    state.activeTabId
                }
                val validExpanded = state.expandedGroupId?.takeIf { expId ->
                    cleanGroups.any { it.id == expId }
                }
                val validEditing = state.editingGroupId?.takeIf { editId ->
                    cleanGroups.any { it.id == editId }
                }
                state.copy(
                    tabs = cleanTabs,
                    tabGroups = cleanGroups,
                    expandedGroupId = validExpanded,
                    editingGroupId = validEditing,
                    activeTabId = nextActiveId,
                    isIncognitoUnlocked = cleanTabs.any { it.isPrivate },
                    snackbarNotice = sessionNotice,
                    pendingClosedTabUndo = PendingClosedTabUndo(
                        token = undoToken,
                        tabTitle = closedTab?.title ?: closingTab.title
                    )
                )
            }
        }

        closedTabHistory.add(
            ClosedTabSnapshot(
                token = undoToken,
                tab = closingTab,
                originalIndex = closingIndex,
                previousActiveTabId = snapshotBeforeClose.activeTabId,
                previousExpandedGroupId = snapshotBeforeClose.expandedGroupId,
                groupInfo = closingGroupInfo,
                autoCreatedReplacementTabId = autoReplacementTabId
            )
        )
        if (closedTabHistory.size > 10) {
            closedTabHistory.removeAt(0)
        }
    }

    fun undoLastClosedTab() {
        val snapshot = closedTabHistory.removeLastOrNull() ?: return
        _uiState.update { state ->
            val baseTabs = if (
                snapshot.autoCreatedReplacementTabId != null &&
                state.tabs.size == 1 &&
                state.tabs.first().id == snapshot.autoCreatedReplacementTabId
            ) {
                emptyList()
            } else {
                state.tabs
            }

            if (baseTabs.any { it.id == snapshot.tab.id }) {
                return@update state.copy(pendingClosedTabUndo = null)
            }

            val restoredTabs = baseTabs.toMutableList().apply {
                add(snapshot.originalIndex.coerceIn(0, size), snapshot.tab)
            }
            val restoredGroups = if (
                snapshot.groupInfo != null &&
                state.tabGroups.none { it.id == snapshot.groupInfo.id }
            ) {
                state.tabGroups + snapshot.groupInfo
            } else {
                state.tabGroups
            }
            val nextActiveId = when {
                snapshot.autoCreatedReplacementTabId != null -> snapshot.tab.id
                snapshot.previousActiveTabId == snapshot.tab.id -> snapshot.tab.id
                restoredTabs.any { it.id == state.activeTabId } -> state.activeTabId
                else -> snapshot.tab.id
            }
            val nextExpandedGroupId = when {
                state.expandedGroupId != null && restoredGroups.any { it.id == state.expandedGroupId } -> {
                    state.expandedGroupId
                }
                snapshot.previousExpandedGroupId != null &&
                    restoredGroups.any { it.id == snapshot.previousExpandedGroupId } -> {
                    snapshot.previousExpandedGroupId
                }
                else -> null
            }

            state.copy(
                tabs = restoredTabs,
                tabGroups = restoredGroups,
                activeTabId = nextActiveId,
                expandedGroupId = nextExpandedGroupId,
                isIncognitoUnlocked = restoredTabs.any { it.isPrivate },
                pendingClosedTabUndo = null
            )
        }
    }

    private fun normalizeGroups(
        tabs: List<BrowserTab>,
        groups: List<TabGroupInfo>
    ): Pair<List<BrowserTab>, List<TabGroupInfo>> {
        val countsByGroup = tabs.mapNotNull { it.groupId }.groupingBy { it }.eachCount()
        val knownGroupIds = groups.map { it.id }.toSet()
        val validGroupIds = countsByGroup.filter { (gId, count) ->
            if (gId in knownGroupIds) count >= 1 else count >= 2
        }.keys
        val cleanedTabs = tabs.map { tab ->
            if (tab.groupId != null && tab.groupId !in validGroupIds) {
                tab.copy(groupId = null)
            } else {
                tab
            }
        }
        val cleanedGroups = groups.filter { it.id in validGroupIds }
        return cleanedTabs to cleanedGroups
    }

    fun resetAllTabs() {
        val currentTabs = _uiState.value.tabs
        val hadIncognito = currentTabs.any { it.isPrivate }
        handleTabsClosedForRecording(currentTabs.map { it.id }.toSet())
        currentTabs.forEach { tab ->
            navigationSynchronizer.onTabClosed(tab.id)
            val wv = webViewRegistry.remove(tab.id)
            if (wv != null) {
                destroyWebViewSafely(wv, isIncognito = tab.isPrivate)
            }
        }
        webViewRegistry.values.forEach { destroyWebViewSafely(it, isIncognito = false) }
        webViewRegistry.clear()
        lastRecordedVisitByTab.clear()
        closedTabHistory.clear()
        _tabTranslationStates.value = emptyMap()
        if (hadIncognito) {
            performanceOptimizer.wipeIncognitoSessionStorage()
        }
        val fresh = BrowserTab(title = "Home", url = "apex://home")
        _uiState.update {
            it.copy(
                tabs = listOf(fresh),
                tabGroups = emptyList(),
                expandedGroupId = null,
                editingGroupId = null,
                activeTabId = fresh.id,
                isIncognitoUnlocked = false,
                isTabSwitcherOpen = false,
                pendingClosedTabUndo = null,
                snackbarNotice = if (hadIncognito) {
                    "All tabs closed • Incognito session data wiped"
                } else {
                    it.snackbarNotice
                }
            )
        }
    }

    // --- Context-Aware Bottom Bar Controls (Home, Bookmarks, Search, Tabs) ---

    fun navigateBack() {
        val active = _uiState.value.activeTab
        val wv = webViewRegistry[active.id]
        if (!active.isHomePage && wv != null && wv.canGoBack()) {
            wv.goBack()
        } else if (!active.isHomePage) {
            wv?.setTag(-19048102, null)
            lastRecordedVisitByTab.remove(active.id)
            _tabTranslationStates.update { it - active.id }
            _uiState.update { state ->
                state.copy(
                    tabs = state.tabs.map { tab ->
                        if (tab.id == active.id) {
                            tab.copy(url = "apex://home", title = "Home", isLoading = false, blockedCountOnPage = 0)
                        } else {
                            tab
                        }
                    },
                    isToolbarVisible = true
                )
            }
        }
    }

    private fun BrowserUiState.activeOverlayDestination(): BrowserOverlayDestination? = when {
        isAutomationWorkspaceOpen -> BrowserOverlayDestination.AUTOMATIONS
        isPasswordManagerOpen -> BrowserOverlayDestination.PASSWORDS
        isDownloadsScreenOpen -> BrowserOverlayDestination.DOWNLOADS
        isHistoryScreenOpen -> BrowserOverlayDestination.HISTORY
        isBookmarksScreenOpen -> BrowserOverlayDestination.BOOKMARKS
        isSettingsScreenOpen -> BrowserOverlayDestination.SETTINGS
        isTabSwitcherOpen -> BrowserOverlayDestination.TAB_SWITCHER
        else -> null
    }

    private fun BrowserUiState.withActiveOverlay(
        destination: BrowserOverlayDestination?,
        backStack: List<BrowserOverlayDestination>
    ): BrowserUiState = copy(
        isSettingsScreenOpen = destination == BrowserOverlayDestination.SETTINGS,
        isPasswordManagerOpen = destination == BrowserOverlayDestination.PASSWORDS,
        isDownloadsScreenOpen = destination == BrowserOverlayDestination.DOWNLOADS,
        isHistoryScreenOpen = destination == BrowserOverlayDestination.HISTORY,
        isBookmarksScreenOpen = destination == BrowserOverlayDestination.BOOKMARKS,
        isTabSwitcherOpen = destination == BrowserOverlayDestination.TAB_SWITCHER,
        isAutomationWorkspaceOpen = destination == BrowserOverlayDestination.AUTOMATIONS,
        selectedAutomationWorkflowId = if (destination == BrowserOverlayDestination.AUTOMATIONS) {
            selectedAutomationWorkflowId
        } else {
            null
        },
        expandedGroupId = if (destination != BrowserOverlayDestination.TAB_SWITCHER) null else expandedGroupId,
        isMenuSheetOpen = if (destination != null) false else isMenuSheetOpen,
        isSearchScreenOpen = if (destination != null) false else isSearchScreenOpen,
        isAutomationOpen = if (destination != null) false else isAutomationOpen,
        isAutomationHudOpen = if (destination != null) false else isAutomationHudOpen,
        isToolbarVisible = true,
        overlayBackStack = backStack
    )

    private fun BrowserUiState.transitionOverlay(
        target: BrowserOverlayDestination,
        open: Boolean
    ): BrowserUiState {
        val current = activeOverlayDestination()
        return if (open) {
            if (current == target) {
                copy(
                    isMenuSheetOpen = false,
                    isSearchScreenOpen = false,
                    isAutomationOpen = false,
                    isAutomationHudOpen = false,
                    isToolbarVisible = true
                )
            } else {
                val updatedStack = if (current != null) {
                    (overlayBackStack.filterNot { it == target || it == current }) + current
                } else {
                    overlayBackStack.filterNot { it == target }
                }
                withActiveOverlay(destination = target, backStack = updatedStack)
            }
        } else {
            if (current == target) {
                val previous = overlayBackStack.lastOrNull()
                val remainingStack = if (previous != null) {
                    overlayBackStack.dropLast(1)
                } else {
                    emptyList()
                }
                withActiveOverlay(destination = previous, backStack = remainingStack)
            } else {
                val filteredStack = overlayBackStack.filterNot { it == target }
                when (target) {
                    BrowserOverlayDestination.SETTINGS -> copy(isSettingsScreenOpen = false, overlayBackStack = filteredStack)
                    BrowserOverlayDestination.PASSWORDS -> copy(isPasswordManagerOpen = false, overlayBackStack = filteredStack)
                    BrowserOverlayDestination.DOWNLOADS -> copy(isDownloadsScreenOpen = false, overlayBackStack = filteredStack)
                    BrowserOverlayDestination.HISTORY -> copy(isHistoryScreenOpen = false, overlayBackStack = filteredStack)
                    BrowserOverlayDestination.BOOKMARKS -> copy(isBookmarksScreenOpen = false, overlayBackStack = filteredStack)
                    BrowserOverlayDestination.TAB_SWITCHER -> copy(
                        isTabSwitcherOpen = false,
                        expandedGroupId = null,
                        overlayBackStack = filteredStack
                    )
                    BrowserOverlayDestination.AUTOMATIONS -> copy(
                        isAutomationWorkspaceOpen = false,
                        selectedAutomationWorkflowId = null,
                        overlayBackStack = filteredStack
                    )
                }
            }
        }
    }

    fun onBottomHomePressed() {
        val currentState = _uiState.value
        webViewRegistry[currentState.activeTabId]?.setTag(-19048102, null)
        lastRecordedVisitByTab.remove(currentState.activeTabId)
        _tabTranslationStates.update { it - currentState.activeTabId }
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.map { tab ->
                    if (tab.id == currentState.activeTabId && !tab.isHomePage) {
                        tab.copy(
                            url = "apex://home",
                            title = "Home",
                            isLoading = false,
                            blockedCountOnPage = 0
                        )
                    } else {
                        tab
                    }
                },
                isBookmarksScreenOpen = false,
                isHistoryScreenOpen = false,
                isDownloadsScreenOpen = false,
                isPasswordManagerOpen = false,
                isSettingsScreenOpen = false,
                isAutomationWorkspaceOpen = false,
                selectedAutomationWorkflowId = null,
                isAutomationHudOpen = false,
                overlayBackStack = emptyList(),
                isMenuSheetOpen = false,
                isSearchScreenOpen = false,
                isTabSwitcherOpen = false,
                isAutomationOpen = false,
                isToolbarVisible = true,
                matchedCredentialsForActiveTab = emptyList(),
                pendingCredentialPrompt = null,
                pageInspection = PageInspectionState()
            )
        }
        pageInspectionByTab[currentState.activeTabId] = PageInspectionState()
        refreshAutomationToolbarStatus(clearTransientResult = true)
    }

    fun onBottomBookmarksPressed() {
        val state = _uiState.value
        if (state.isBookmarksScreenOpen) {
            setBookmarksScreenOpen(false)
            return
        }
        if (state.activeTab.isHomePage ||
            state.isSettingsScreenOpen ||
            state.isHistoryScreenOpen ||
            state.isDownloadsScreenOpen ||
            state.isPasswordManagerOpen
        ) {
            setBookmarksScreenOpen(true)
        } else {
            val active = state.activeTab
            viewModelScope.launch {
                val existing = repository.getBookmarkByUrl(active.url)
                if (existing != null) {
                    repository.deleteBookmark(existing.id)
                    _uiState.update {
                        it.copy(
                            isTabSwitcherOpen = false,
                            isHistoryScreenOpen = false,
                            isDownloadsScreenOpen = false,
                            isPasswordManagerOpen = false,
                            isSettingsScreenOpen = false,
                            overlayBackStack = emptyList(),
                            isMenuSheetOpen = false,
                            isSearchScreenOpen = false,
                            snackbarNotice = "Removed bookmark '${active.title}'"
                        )
                    }
                } else {
                    repository.saveBookmark(url = active.url, title = active.title)
                    _uiState.update {
                        it.copy(
                            isTabSwitcherOpen = false,
                            isHistoryScreenOpen = false,
                            isDownloadsScreenOpen = false,
                            isPasswordManagerOpen = false,
                            isSettingsScreenOpen = false,
                            overlayBackStack = emptyList(),
                            isMenuSheetOpen = false,
                            isSearchScreenOpen = false,
                            snackbarNotice = "Bookmarked '${active.title}'"
                        )
                    }
                }
            }
        }
    }

    fun setBookmarksScreenOpen(open: Boolean) {
        _uiState.update { it.transitionOverlay(BrowserOverlayDestination.BOOKMARKS, open) }
    }

    fun setHistoryScreenOpen(open: Boolean) {
        _uiState.update { it.transitionOverlay(BrowserOverlayDestination.HISTORY, open) }
    }

    fun setDownloadsScreenOpen(open: Boolean) {
        _uiState.update { it.transitionOverlay(BrowserOverlayDestination.DOWNLOADS, open) }
    }

    fun setPasswordManagerOpen(open: Boolean) {
        _uiState.update { it.transitionOverlay(BrowserOverlayDestination.PASSWORDS, open) }
    }

    fun decryptPassword(entity: SavedPasswordEntity): String {
        return repository.decryptPassword(entity)
    }

    fun saveOrUpdatePassword(
        id: Long = 0L,
        siteName: String,
        siteUrl: String,
        username: String,
        plainPassword: String,
        notes: String = "",
        category: String = "Personal",
        isFavorite: Boolean = false
    ) {
        if (username.isBlank() || plainPassword.isEmpty()) return
        viewModelScope.launch {
            repository.saveOrUpdatePassword(
                id = id,
                siteName = siteName,
                siteUrl = siteUrl,
                username = username,
                plainPassword = plainPassword,
                notes = notes,
                category = category,
                isFavorite = isFavorite
            )
            refreshMatchedCredentialsForActiveTab()
            _uiState.update {
                it.copy(
                    snackbarNotice = if (id > 0L) "Password updated in vault" else "Password saved to encrypted vault"
                )
            }
        }
    }

    fun togglePasswordFavorite(id: Long) {
        viewModelScope.launch { repository.togglePasswordFavorite(id) }
    }

    fun deleteSavedPassword(id: Long) {
        viewModelScope.launch {
            repository.deleteSavedPassword(id)
            refreshMatchedCredentialsForActiveTab()
            _uiState.update { it.copy(snackbarNotice = "Password removed from vault") }
        }
    }

    fun clearAllSavedPasswords() {
        viewModelScope.launch {
            repository.clearAllSavedPasswords()
            _uiState.update {
                it.copy(
                    matchedCredentialsForActiveTab = emptyList(),
                    snackbarNotice = "Password vault cleared"
                )
            }
        }
    }

    fun importPasswordsFromCsv(rawCsv: String) {
        viewModelScope.launch {
            val count = repository.importPasswordsFromCsv(rawCsv)
            refreshMatchedCredentialsForActiveTab()
            _uiState.update {
                it.copy(
                    snackbarNotice = if (count > 0) {
                        "Imported $count password${if (count == 1) "" else "s"}"
                    } else {
                        "No valid credentials found in CSV"
                    }
                )
            }
        }
    }

    fun exportPasswordsToCsv(onExportReady: (String) -> Unit) {
        viewModelScope.launch {
            val csv = repository.exportPasswordsToCsv()
            onExportReady(csv)
        }
    }

    fun togglePasswordAutofill(enabled: Boolean) {
        viewModelScope.launch {
            repository.setPasswordAutofillEnabled(enabled)
            refreshMatchedCredentialsForActiveTab()
        }
    }

    fun refreshMatchedCredentialsForActiveTab() {
        val state = _uiState.value
        val active = state.activeTab
        if (active.isHomePage || !preferencesState.value.passwordAutofillEnabled) {
            _uiState.update { it.copy(matchedCredentialsForActiveTab = emptyList()) }
            return
        }
        viewModelScope.launch {
            val matches = repository.getPasswordsForUrl(active.url)
            if (_uiState.value.activeTabId == active.id) {
                _uiState.update { it.copy(matchedCredentialsForActiveTab = matches) }
            }
        }
    }

    fun onWebCredentialSubmitted(
        pageUrl: String,
        pageTitle: String,
        username: String,
        password: String
    ) {
        val state = _uiState.value
        val active = state.activeTab
        if (active.isPrivate || !preferencesState.value.passwordAutofillEnabled) return
        val cleanUser = username.trim()
        if (cleanUser.isBlank() || password.isBlank()) return
        val host = PasswordVaultUtil.extractHost(pageUrl)
        if (host.isBlank()) return

        viewModelScope.launch {
            val existing = repository.getPasswordsForUrl(pageUrl)
                .firstOrNull { it.username.equals(cleanUser, ignoreCase = true) }
            if (existing != null && repository.decryptPassword(existing) == password) {
                return@launch
            }
            val siteName = PasswordVaultUtil.deriveSiteName(pageUrl, pageTitle)
            _uiState.update {
                it.copy(
                    pendingCredentialPrompt = PendingCredentialPrompt(
                        siteName = siteName,
                        siteUrl = pageUrl,
                        username = cleanUser,
                        password = password,
                        existingId = existing?.id
                    )
                )
            }
        }
    }

    fun confirmSavePendingCredential() {
        val pending = _uiState.value.pendingCredentialPrompt ?: return
        _uiState.update { it.copy(pendingCredentialPrompt = null) }
        saveOrUpdatePassword(
            id = pending.existingId ?: 0L,
            siteName = pending.siteName,
            siteUrl = pending.siteUrl,
            username = pending.username,
            plainPassword = pending.password
        )
    }

    fun dismissPendingCredentialPrompt() {
        _uiState.update { it.copy(pendingCredentialPrompt = null) }
    }

    fun fillCredentialIntoActiveWebView(entity: SavedPasswordEntity) {
        val active = _uiState.value.activeTab
        val wv = webViewRegistry[active.id] ?: return
        val plainPassword = repository.decryptPassword(entity)
        val userLiteral = JSONObject.quote(entity.username)
        val passLiteral = JSONObject.quote(plainPassword)
        val js = """
            (function() {
                var userVal = $userLiteral;
                var passVal = $passLiteral;
                function setNativeValue(el, value) {
                    if (!el) return;
                    var proto = Object.getPrototypeOf(el);
                    var desc = Object.getOwnPropertyDescriptor(proto, 'value') ||
                        Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value');
                    if (desc && desc.set) {
                        desc.set.call(el, value);
                    } else {
                        el.value = value;
                    }
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                    el.dispatchEvent(new Event('change', { bubbles: true }));
                }
                var passInput = document.querySelector('input[type="password"]:not([disabled])');
                if (passInput) {
                    setNativeValue(passInput, passVal);
                    var form = passInput.form || document;
                    var candidates = Array.prototype.slice.call(
                        form.querySelectorAll('input[type="email"], input[type="text"], input[name*="user" i], input[name*="mail" i], input[name*="login" i], input:not([type])')
                    );
                    var userInput = candidates.find(function(inp) {
                        return inp !== passInput && !inp.disabled && inp.type !== 'hidden' && inp.offsetParent !== null;
                    }) || candidates[0];
                    if (userInput) {
                        setNativeValue(userInput, userVal);
                    }
                }
            })();
        """.trimIndent()
        wv.evaluateJavascript(js, null)
        viewModelScope.launch { repository.markPasswordUsed(entity.id) }
        _uiState.update {
            it.copy(snackbarNotice = "Filled credentials for ${entity.siteName}")
        }
    }

    fun startFastDownload(
        url: String,
        suggestedFileName: String? = null,
        contentDisposition: String? = null,
        mimeType: String? = null,
        contentLength: Long = -1L,
        userAgent: String? = null
    ) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return
        val engine = fastDownloadEngine
        if (engine != null) {
            engine.enqueueDownload(
                url = cleanUrl,
                suggestedFileName = suggestedFileName,
                contentDisposition = contentDisposition,
                mimeType = mimeType,
                contentLength = contentLength,
                userAgent = userAgent
            ) { entity ->
                val turboSuffix = if (entity.chunksCount > 1) " (${entity.chunksCount}× Turbo)" else ""
                _uiState.update {
                    it.copy(snackbarNotice = "Downloading ${entity.fileName}$turboSuffix…")
                }
            }
        } else {
            val rawName = suggestedFileName?.takeIf { it.isNotBlank() }
                ?: cleanUrl.substringAfterLast('/').substringBefore('?').ifBlank { "download.bin" }
            val resolvedName = FastDownloadEngine.sanitizeFileName(rawName)
            val resolvedMime = FastDownloadEngine.resolveMimeType(cleanUrl, mimeType)
            val category = FastDownloadEngine.inferCategory(resolvedName, resolvedMime)
            viewModelScope.launch {
                val now = System.currentTimeMillis()
                repository.upsertDownload(
                    DownloadItemEntity(
                        fileName = resolvedName,
                        url = cleanUrl,
                        mimeType = resolvedMime,
                        category = category,
                        totalBytes = if (contentLength > 0) contentLength else 128_000L,
                        downloadedBytes = if (contentLength > 0) contentLength else 128_000L,
                        speedBytesPerSec = 8_400_000L,
                        chunksCount = FastDownloadEngine.DEFAULT_TURBO_CHUNKS,
                        status = FastDownloadEngine.STATUS_COMPLETED,
                        createdAt = now,
                        completedAt = now
                    )
                )
                _uiState.update {
                    it.copy(snackbarNotice = "Downloaded $resolvedName")
                }
            }
        }
    }

    fun pauseDownload(id: Long) {
        fastDownloadEngine?.pauseDownload(id)
    }

    fun resumeDownload(id: Long) {
        fastDownloadEngine?.resumeDownload(id)
    }

    fun retryDownload(id: Long) {
        fastDownloadEngine?.retryDownload(id)
    }

    fun cancelDownload(id: Long) {
        fastDownloadEngine?.cancelDownload(id)
    }

    fun deleteDownload(id: Long) {
        val engine = fastDownloadEngine
        if (engine != null) {
            engine.deleteDownload(id)
        } else {
            viewModelScope.launch { repository.deleteDownloadById(id) }
        }
        _uiState.update { it.copy(snackbarNotice = "Removed download item") }
    }

    fun clearCompletedDownloads() {
        viewModelScope.launch {
            repository.clearCompletedDownloads()
            _uiState.update { it.copy(snackbarNotice = "Cleared completed downloads") }
        }
    }

    fun clearAllDownloads() {
        val engine = fastDownloadEngine
        if (engine != null) {
            engine.clearAllDownloads()
            _uiState.update { it.copy(snackbarNotice = "Cleared all downloads") }
        } else {
            viewModelScope.launch {
                repository.clearAllDownloads()
                _uiState.update { it.copy(snackbarNotice = "Cleared all downloads") }
            }
        }
    }

    fun deleteBrowsingHistoryItem(id: Long) {
        viewModelScope.launch {
            repository.deleteBrowsingHistoryItem(id)
            _uiState.update { it.copy(snackbarNotice = "Removed visit from history") }
        }
    }

    fun clearBrowsingHistoryRange(sinceMillis: Long?) {
        viewModelScope.launch {
            if (sinceMillis == null) {
                repository.clearAllBrowsingHistory()
                _uiState.update { it.copy(snackbarNotice = "Cleared all browsing history") }
            } else {
                repository.clearBrowsingHistorySince(sinceMillis)
                _uiState.update { it.copy(snackbarNotice = "Cleared selected browsing history range") }
            }
        }
    }

    fun setSettingsScreenOpen(open: Boolean) {
        _uiState.update { it.transitionOverlay(BrowserOverlayDestination.SETTINGS, open) }
    }

    fun setMenuSheetOpen(open: Boolean) {
        _uiState.update { it.copy(isMenuSheetOpen = open) }
    }

    fun toggleMenuSheet() {
        _uiState.update { it.copy(isMenuSheetOpen = !it.isMenuSheetOpen) }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch { repository.deleteBookmark(id) }
    }

    fun importBookmarksFromText(rawContent: String) {
        viewModelScope.launch {
            val importedCount = repository.importBookmarksFromRawContent(rawContent)
            val message = if (importedCount > 0) {
                "Imported $importedCount bookmark${if (importedCount == 1) "" else "s"}"
            } else {
                "No valid bookmarks found to import"
            }
            _uiState.update { it.copy(snackbarNotice = message) }
        }
    }

    fun notifyBookmarksExported(count: Int, formatLabel: String) {
        _uiState.update {
            it.copy(
                snackbarNotice = "Exported $count bookmark${if (count == 1) "" else "s"} ($formatLabel)"
            )
        }
    }

    fun addTopVisit(title: String, url: String) {
        viewModelScope.launch {
            repository.addTopVisit(title, url)
            _uiState.update { it.copy(snackbarNotice = "Added to Top Visits") }
        }
    }

    fun deleteTopVisit(id: Long) {
        viewModelScope.launch {
            repository.deleteTopVisit(id)
            _uiState.update { it.copy(snackbarNotice = "Removed from Top Visits") }
        }
    }

    fun setShowTopVisitsOnHome(enabled: Boolean) {
        viewModelScope.launch {
            repository.setShowTopVisitsOnHome(enabled)
        }
    }

    fun reloadActiveTab() {
        val active = _uiState.value.activeTab
        if (active.isHomePage) return
        val builtIn = com.example.engine.BuiltInShowcasePages.resolveHtml(active.url)
        val wv = webViewRegistry[active.id]
        navigationSynchronizer.beginNavigation(active.id, active.url)
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.map { tab ->
                    if (tab.id == active.id) {
                        tab.copy(
                            isLoading = true,
                            progress = 0.08f,
                            loadRequestId = if (wv == null) tab.loadRequestId + 1L else tab.loadRequestId
                        )
                    } else {
                        tab
                    }
                }
            )
        }
        if (wv != null) {
            if (builtIn != null) {
                wv.loadDataWithBaseURL(builtIn.first, builtIn.second, "text/html", "UTF-8", null)
            } else {
                wv.reload()
            }
        }
    }

    fun captureTabThumbnail(tabId: String): Bitmap? {
        val tab = _uiState.value.tabs.firstOrNull { it.id == tabId }
        if (tab?.isPrivate == true) return null
        val wv = webViewRegistry[tabId] ?: return null
        return runCatching {
            if (wv.width > 0 && wv.height > 0 && wv.isAttachedToWindow && wv.windowToken != null) {
                val thumbWidth = 320
                val thumbHeight = (wv.height.toFloat() / wv.width.toFloat() * thumbWidth).toInt().coerceIn(200, 480)
                val thumbBitmap = Bitmap.createBitmap(thumbWidth, thumbHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(thumbBitmap)
                val scale = thumbWidth.toFloat() / wv.width.toFloat()
                canvas.scale(scale, scale)
                wv.draw(canvas)
                thumbBitmap
            } else null
        }.getOrNull()
    }

    fun setTabSwitcherOpen(open: Boolean) {
        if (open) {
            val currentTabId = _uiState.value.activeTabId
            val thumb = captureTabThumbnail(currentTabId)
            if (thumb != null) {
                _uiState.update { state ->
                    state.copy(
                        tabs = state.tabs.map { tab ->
                            if (tab.id == currentTabId) {
                                tab.copy(
                                    previewThumbnail = thumb,
                                    lastActiveTimeMillis = System.currentTimeMillis()
                                )
                            } else tab
                        }
                    )
                }
            }
        }
        _uiState.update { it.transitionOverlay(BrowserOverlayDestination.TAB_SWITCHER, open) }
    }

    fun setAutomationOpen(open: Boolean, module: ExtensionModule = _uiState.value.activeExtension) {
        _uiState.update {
            it.copy(
                isAutomationOpen = open,
                isAutomationHudOpen = if (open) false else it.isAutomationHudOpen,
                activeExtension = module,
                isSettingsScreenOpen = if (open) false else it.isSettingsScreenOpen,
                isSearchScreenOpen = if (open) false else it.isSearchScreenOpen,
                isToolbarVisible = true
            )
        }
    }

    fun selectExtensionModule(module: ExtensionModule) {
        _uiState.update { it.copy(activeExtension = module) }
    }

    // --- Apex Automation HUD, Recording, Instructions, Workspace & Details ---

    fun openAutomationHud(initialMode: AutomationHudMode? = null) {
        val state = _uiState.value
        val activeTab = state.activeTab
        val resolvedMode = initialMode ?: when {
            state.recordingSession.status == RecordingStatus.RECORDING ||
                state.recordingSession.status == RecordingStatus.PAUSED ||
                state.recordingSession.status == RecordingStatus.STOPPED -> AutomationHudMode.RECORDING
            else -> AutomationHudMode.OVERVIEW
        }
        val currentInspection = state.pageInspection.copy(
            domain = automationEngine.extractDomain(activeTab.url),
            url = activeTab.url
        )
        _uiState.update {
            it.copy(
                isAutomationHudOpen = true,
                automationHudMode = resolvedMode,
                isMenuSheetOpen = false,
                pageInspection = currentInspection
            )
        }
        if (!activeTab.isHomePage && currentInspection.elements.isEmpty()) {
            scanActivePageElements(silent = true)
        }
    }

    fun dismissAutomationHud() {
        _uiState.update { it.copy(isAutomationHudOpen = false) }
    }

    fun setAutomationHudMode(mode: AutomationHudMode) {
        _uiState.update { it.copy(automationHudMode = mode) }
        if (mode == AutomationHudMode.ELEMENTS &&
            !_uiState.value.activeTab.isHomePage &&
            _uiState.value.pageInspection.elements.isEmpty()
        ) {
            scanActivePageElements(silent = false)
        }
    }

    fun setAutomationWorkspaceOpen(open: Boolean) {
        _uiState.update {
            val transitioned = it.transitionOverlay(BrowserOverlayDestination.AUTOMATIONS, open)
            if (open) {
                transitioned.copy(isAutomationHudOpen = false)
            } else {
                transitioned.copy(selectedAutomationWorkflowId = null)
            }
        }
    }

    fun openAutomationDetails(workflowId: Long) {
        _uiState.update { state ->
            val withWorkspace = if (state.isAutomationWorkspaceOpen) {
                state
            } else {
                state.transitionOverlay(BrowserOverlayDestination.AUTOMATIONS, true)
            }
            withWorkspace.copy(
                isAutomationHudOpen = false,
                selectedAutomationWorkflowId = workflowId
            )
        }
    }

    fun closeAutomationDetails() {
        _uiState.update { it.copy(selectedAutomationWorkflowId = null) }
    }

    fun scanActivePageElements(silent: Boolean = false) {
        val activeTab = _uiState.value.activeTab
        val tabId = activeTab.id
        val domain = automationEngine.extractDomain(activeTab.url)
        if (!silent) {
            _uiState.update {
                it.copy(
                    pageInspection = it.pageInspection.copy(
                        domain = domain,
                        url = activeTab.url,
                        isScanning = true,
                        errorMessage = null
                    )
                )
            }
        }
        viewModelScope.launch {
            val result = automationEngine.scanPageElements(
                webView = webViewRegistry[tabId],
                currentUrl = activeTab.url
            )
            pageInspectionByTab[tabId] = result
            if (_uiState.value.activeTabId == tabId) {
                _uiState.update { it.copy(pageInspection = result) }
                refreshAutomationToolbarStatus(clearTransientResult = false)
            }
        }
    }

    // --- Recording Mode (Isolated to Initiating Tab, Origin & Session) ---

    fun startRecording() {
        recordingLifecycleJob?.cancel()
        val stateSnapshot = _uiState.value
        val activeTab = stateSnapshot.activeTab
        val tabId = activeTab.id
        val tabUrl = activeTab.url
        val domain = automationEngine.extractDomain(tabUrl)
        val parsedOrigin = automationEngine.parseOrigin(tabUrl)
        val wv = webViewRegistry[tabId]

        if (parsedOrigin !is RecordingOriginResult.Valid) {
            setRecordingErrorState(
                initiatingTabId = tabId,
                targetDomain = domain,
                targetUrl = tabUrl,
                code = AutomationErrorCode.RECORDING_DOCUMENT_UNAVAILABLE,
                message = "Open an HTTP or HTTPS website before starting recording.",
                preserveSteps = false
            )
            return
        }

        if (wv == null) {
            setRecordingErrorState(
                initiatingTabId = tabId,
                targetDomain = domain,
                targetUrl = tabUrl,
                code = AutomationErrorCode.RECORDING_DOCUMENT_UNAVAILABLE,
                message = "Active webpage is not ready for recording yet.",
                preserveSteps = false
            )
            return
        }

        val liveWebViewUrl = wv.url?.takeIf { it.isNotBlank() && !it.startsWith("data:") }
            ?.let { mapBaseUrlBackToScheme(it, tabUrl) }
        if (liveWebViewUrl != null) {
            val liveOrigin = automationEngine.parseOrigin(liveWebViewUrl)
            if (liveOrigin !is RecordingOriginResult.Valid || liveOrigin.canonicalOrigin != parsedOrigin.canonicalOrigin) {
                setRecordingErrorState(
                    initiatingTabId = tabId,
                    targetDomain = domain,
                    targetUrl = tabUrl,
                    code = AutomationErrorCode.RECORDING_ORIGIN_CHANGED,
                    message = "WebView origin does not match the active tab URL.",
                    preserveSteps = false
                )
                return
            }
        }

        val existing = stateSnapshot.recordingSession
        if (existing.initiatingTabId.isNotBlank() && existing.sessionId.isNotBlank()) {
            automationEngine.detachRecorderBestEffort(webViewRegistry[existing.initiatingTabId], existing.sessionId)
        }

        val newSessionId = "rec_sess_${UUID.randomUUID()}"
        val expectedOrigin = parsedOrigin.canonicalOrigin

        recordingLifecycleJob = viewModelScope.launch {
            val result = automationEngine.installRecorderScript(
                webView = wv,
                sessionId = newSessionId,
                tabId = tabId,
                expectedOrigin = expectedOrigin,
                initialSequenceNumber = 0L
            )
            val latestTab = _uiState.value.tabs.firstOrNull { it.id == tabId }
            val latestOrigin = latestTab?.let { automationEngine.parseOrigin(it.url) }
            val stillValidDocument = latestTab != null &&
                webViewRegistry[tabId] === wv &&
                latestOrigin is RecordingOriginResult.Valid &&
                latestOrigin.canonicalOrigin == expectedOrigin

            if (!result.success || !stillValidDocument) {
                if (result.success && !stillValidDocument) {
                    automationEngine.detachRecorderBestEffort(wv, newSessionId)
                }
                val errCode = if (!stillValidDocument) {
                    if (latestTab == null) AutomationErrorCode.RECORDING_TAB_CLOSED
                    else AutomationErrorCode.RECORDING_ORIGIN_CHANGED
                } else {
                    result.errorCode
                }
                val errMsg = if (!stillValidDocument) {
                    "Recording target changed before recorder could attach."
                } else {
                    result.message.ifBlank { "Failed to start recording on $domain." }
                }
                setRecordingErrorState(
                    initiatingTabId = tabId,
                    targetDomain = domain,
                    targetUrl = tabUrl,
                    code = errCode,
                    message = errMsg,
                    preserveSteps = false
                )
                return@launch
            }

            _uiState.update {
                it.copy(
                    automationHudMode = AutomationHudMode.RECORDING,
                    recordingSession = ActiveRecordingSession(
                        sessionId = newSessionId,
                        initiatingTabId = tabId,
                        initialOrigin = expectedOrigin,
                        currentOrigin = expectedOrigin,
                        status = RecordingStatus.RECORDING,
                        steps = emptyList(),
                        targetDomain = domain,
                        targetUrl = tabUrl,
                        suggestedName = if (domain == "Start Page") "Recorded Workflow" else "$domain Workflow",
                        startedAt = System.currentTimeMillis(),
                        lastSequenceNumber = 0L,
                        recordingError = null,
                        pendingOriginChangeUrl = null
                    )
                )
            }
            refreshAutomationToolbarStatus(clearTransientResult = true)
        }
    }

    fun pauseRecording() {
        val session = _uiState.value.recordingSession
        if (session.status != RecordingStatus.RECORDING) return
        recordingLifecycleJob?.cancel()
        val targetTabId = session.initiatingTabId
        val targetSessionId = session.sessionId
        _uiState.update {
            it.copy(
                recordingSession = it.recordingSession.copy(status = RecordingStatus.PAUSED)
            )
        }
        refreshAutomationToolbarStatus(clearTransientResult = false)
        val wv = webViewRegistry[targetTabId]
        if (wv != null) {
            recordingLifecycleJob = viewModelScope.launch {
                automationEngine.uninstallRecorderScript(wv, targetSessionId)
            }
        }
    }

    fun resumeRecording() {
        val session = _uiState.value.recordingSession
        if (session.status != RecordingStatus.PAUSED) return
        recordingLifecycleJob?.cancel()

        if (session.sessionId.isBlank() || session.initiatingTabId.isBlank()) {
            setRecordingErrorState(
                initiatingTabId = session.initiatingTabId,
                targetDomain = session.targetDomain,
                targetUrl = session.targetUrl,
                code = AutomationErrorCode.RECORDING_SESSION_INVALID,
                message = "Recording session is no longer valid.",
                preserveSteps = true
            )
            return
        }

        val initiatingTab = _uiState.value.tabs.firstOrNull { it.id == session.initiatingTabId }
        if (initiatingTab == null) {
            _uiState.update { state ->
                state.copy(
                    recordingSession = state.recordingSession.copy(
                        status = RecordingStatus.CANCELLED,
                        sessionId = "",
                        recordingError = RecordingErrorInfo(
                            code = AutomationErrorCode.RECORDING_TAB_CLOSED,
                            message = "Recording cancelled because the recording tab was closed."
                        )
                    )
                )
            }
            refreshAutomationToolbarStatus(clearTransientResult = true)
            return
        }

        val tabOrigin = automationEngine.parseOrigin(initiatingTab.url)
        val expectedOrigin = session.currentOrigin
        if (tabOrigin !is RecordingOriginResult.Valid || tabOrigin.canonicalOrigin != expectedOrigin) {
            _uiState.update { state ->
                state.copy(
                    recordingSession = state.recordingSession.copy(
                        status = RecordingStatus.STOPPED,
                        sessionId = "",
                        pendingOriginChangeUrl = initiatingTab.url,
                        recordingError = RecordingErrorInfo(
                            code = AutomationErrorCode.RECORDING_ORIGIN_CHANGED,
                            message = "Recording stopped: tab navigated away from ${session.targetDomain}."
                        )
                    ),
                    snackbarNotice = "Recording stopped — tab origin changed"
                )
            }
            refreshAutomationToolbarStatus(clearTransientResult = false)
            return
        }

        val wv = webViewRegistry[session.initiatingTabId]
        if (wv == null) {
            setRecordingErrorState(
                initiatingTabId = session.initiatingTabId,
                targetDomain = session.targetDomain,
                targetUrl = session.targetUrl,
                code = AutomationErrorCode.RECORDING_DOCUMENT_UNAVAILABLE,
                message = "Recording tab WebView is unavailable to resume.",
                preserveSteps = true
            )
            return
        }

        val resumeSessionId = session.sessionId
        val resumeTabId = session.initiatingTabId
        val resumeSeq = session.lastSequenceNumber

        recordingLifecycleJob = viewModelScope.launch {
            val result = automationEngine.installRecorderScript(
                webView = wv,
                sessionId = resumeSessionId,
                tabId = resumeTabId,
                expectedOrigin = expectedOrigin,
                initialSequenceNumber = resumeSeq
            )
            val currentSession = _uiState.value.recordingSession
            val latestTab = _uiState.value.tabs.firstOrNull { it.id == resumeTabId }
            val latestOrigin = latestTab?.let { automationEngine.parseOrigin(it.url) }
            val stillConsistent = currentSession.status == RecordingStatus.PAUSED &&
                currentSession.sessionId == resumeSessionId &&
                latestTab != null &&
                webViewRegistry[resumeTabId] === wv &&
                latestOrigin is RecordingOriginResult.Valid &&
                latestOrigin.canonicalOrigin == expectedOrigin

            if (!result.success || !stillConsistent) {
                if (result.success && !stillConsistent) {
                    automationEngine.detachRecorderBestEffort(wv, resumeSessionId)
                }
                val errCode = if (!stillConsistent) {
                    if (latestTab == null) AutomationErrorCode.RECORDING_TAB_CLOSED
                    else AutomationErrorCode.RECORDING_ORIGIN_CHANGED
                } else {
                    result.errorCode
                }
                setRecordingErrorState(
                    initiatingTabId = resumeTabId,
                    targetDomain = session.targetDomain,
                    targetUrl = session.targetUrl,
                    code = errCode,
                    message = result.message.ifBlank { "Unable to resume recording on ${session.targetDomain}." },
                    preserveSteps = true
                )
                return@launch
            }

            _uiState.update { state ->
                state.copy(
                    recordingSession = state.recordingSession.copy(
                        status = RecordingStatus.RECORDING,
                        recordingError = null
                    )
                )
            }
            refreshAutomationToolbarStatus(clearTransientResult = false)
        }
    }

    fun stopRecording() {
        val session = _uiState.value.recordingSession
        if (session.status != RecordingStatus.RECORDING && session.status != RecordingStatus.PAUSED) return
        recordingLifecycleJob?.cancel()
        val oldSessionId = session.sessionId
        val targetTabId = session.initiatingTabId
        _uiState.update {
            it.copy(
                recordingSession = it.recordingSession.copy(
                    status = RecordingStatus.STOPPED,
                    sessionId = ""
                )
            )
        }
        refreshAutomationToolbarStatus(clearTransientResult = false)
        val wv = webViewRegistry[targetTabId]
        if (wv != null) {
            recordingLifecycleJob = viewModelScope.launch {
                automationEngine.uninstallRecorderScript(wv, oldSessionId.ifBlank { null })
            }
        }
    }

    fun minimizeRecordingHud() {
        _uiState.update {
            it.copy(
                isAutomationHudOpen = false,
                snackbarNotice = "Recording active — interact with page, tap icon to return"
            )
        }
    }

    fun onRecordedDomEvent(
        event: RecordedDomEvent,
        sourceTabId: String,
        sourceWebView: WebView?,
        sourceWebViewUrl: String
    ) {
        processRecordedDomEvent(
            event = event,
            sourceTabId = sourceTabId,
            sourceWebView = sourceWebView,
            sourceWebViewUrl = sourceWebViewUrl
        )
    }

    internal fun processRecordedDomEvent(
        event: RecordedDomEvent,
        sourceTabId: String,
        sourceWebView: WebView?,
        sourceWebViewUrl: String
    ): RecorderLifecycleResult {
        val stateSnapshot = _uiState.value
        val session = stateSnapshot.recordingSession

        fun reject(code: AutomationErrorCode, reason: String): RecorderLifecycleResult {
            lastRecordingRejectionCode = code
            return RecorderLifecycleResult(success = false, errorCode = code, message = reason)
        }

        // A. Session validation
        if (session.status != RecordingStatus.RECORDING || session.sessionId.isBlank()) {
            return reject(
                AutomationErrorCode.RECORDING_SESSION_INVALID,
                "No active recording session."
            )
        }
        if (event.sessionId.isBlank()) {
            return reject(
                AutomationErrorCode.RECORDING_SESSION_INVALID,
                "Recorded event missing sessionId."
            )
        }
        if (event.sessionId != session.sessionId) {
            return reject(
                AutomationErrorCode.RECORDING_SESSION_MISMATCH,
                "Recorded event came from a stale or mismatched session."
            )
        }

        // B. Tab validation
        if (session.initiatingTabId.isBlank()) {
            return reject(
                AutomationErrorCode.RECORDING_SESSION_INVALID,
                "Recording session has no initiating tab."
            )
        }
        if (event.tabId.isBlank() ||
            event.tabId != session.initiatingTabId ||
            sourceTabId.isBlank() ||
            sourceTabId != session.initiatingTabId
        ) {
            return reject(
                AutomationErrorCode.RECORDING_TAB_MISMATCH,
                "Recorded event did not originate from the initiating tab."
            )
        }
        val initiatingTab = stateSnapshot.tabs.firstOrNull { it.id == session.initiatingTabId }
            ?: return reject(
                AutomationErrorCode.RECORDING_TAB_CLOSED,
                "Initiating tab is no longer open."
            )

        // C. Native WebView instance validation
        val expectedWebView = webViewRegistry[session.initiatingTabId]
            ?: return reject(
                AutomationErrorCode.RECORDING_DOCUMENT_UNAVAILABLE,
                "Initiating tab WebView is unavailable."
            )
        if (sourceWebView != null && sourceWebView !== expectedWebView) {
            return reject(
                AutomationErrorCode.RECORDING_TAB_MISMATCH,
                "Recorded event came from a non-initiating WebView instance."
            )
        }

        // D. Origin validation (event origin + tab URL + live WebView URL)
        val expectedOrigin = automationEngine.parseOrigin(session.currentOrigin)
        if (expectedOrigin !is RecordingOriginResult.Valid) {
            return reject(
                AutomationErrorCode.RECORDING_SESSION_INVALID,
                "Session origin is invalid."
            )
        }
        val eventOrigin = automationEngine.parseOrigin(event.origin)
        if (eventOrigin !is RecordingOriginResult.Valid ||
            eventOrigin.canonicalOrigin != expectedOrigin.canonicalOrigin
        ) {
            return reject(
                AutomationErrorCode.RECORDING_ORIGIN_CHANGED,
                "Recorded event origin '${event.origin}' does not match '${expectedOrigin.canonicalOrigin}'."
            )
        }
        val tabOrigin = automationEngine.parseOrigin(initiatingTab.url)
        if (tabOrigin !is RecordingOriginResult.Valid ||
            tabOrigin.canonicalOrigin != expectedOrigin.canonicalOrigin
        ) {
            return reject(
                AutomationErrorCode.RECORDING_ORIGIN_CHANGED,
                "Initiating tab URL origin changed away from '${expectedOrigin.canonicalOrigin}'."
            )
        }
        val candidateWebViewUrls = listOf(sourceWebViewUrl, expectedWebView.url.orEmpty())
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("data:") }
        for (rawWvUrl in candidateWebViewUrls) {
            val mappedWvUrl = mapBaseUrlBackToScheme(rawWvUrl, initiatingTab.url)
            val wvOrigin = automationEngine.parseOrigin(mappedWvUrl)
            if (wvOrigin !is RecordingOriginResult.Valid ||
                wvOrigin.canonicalOrigin != expectedOrigin.canonicalOrigin
            ) {
                return reject(
                    AutomationErrorCode.RECORDING_ORIGIN_CHANGED,
                    "WebView URL origin does not match recording origin."
                )
            }
        }

        // E. Monotonic sequence check
        if (event.sequenceNumber <= 0L || event.sequenceNumber <= session.lastSequenceNumber) {
            return reject(
                AutomationErrorCode.RECORDING_SESSION_INVALID,
                "Out-of-order or duplicate event sequence ${event.sequenceNumber}."
            )
        }

        // F. Event type & security sanitization
        val matchedActionType = AutomationActionType.entries.firstOrNull {
            it.id.equals(event.eventType.trim(), ignoreCase = true)
        } ?: return reject(
            AutomationErrorCode.RECORDING_SESSION_INVALID,
            "Unsupported recorded event type '${event.eventType}'."
        )

        val cleanLabel = event.targetLabel.trim().ifBlank { "Page element" }
        val rawValue = event.value.trim()
        val isPasswordField = cleanLabel.contains("password", ignoreCase = true) ||
            event.selector.contains("password", ignoreCase = true)
        val cleanValue = if (isPasswordField || Regex("^•{2,}$").matches(rawValue)) {
            ""
        } else {
            rawValue
        }

        var accepted = false
        _uiState.update { state ->
            val current = state.recordingSession
            if (current.status != RecordingStatus.RECORDING ||
                current.sessionId != event.sessionId ||
                current.initiatingTabId != event.tabId ||
                event.sequenceNumber <= current.lastSequenceNumber
            ) {
                state
            } else {
                accepted = true
                val last = current.steps.lastOrNull()
                val isDuplicateFocusStep = last != null &&
                    last.actionType == matchedActionType &&
                    last.targetLabel.equals(cleanLabel, ignoreCase = true) &&
                    last.value == cleanValue

                val updatedSteps = if (isDuplicateFocusStep) {
                    current.steps
                } else {
                    val summary = AutomationStepCodec.buildStepSummary(
                        matchedActionType,
                        cleanLabel,
                        cleanValue
                    )
                    val newStep = AutomationStep(
                        id = "rec_${UUID.randomUUID()}",
                        order = current.steps.size + 1,
                        actionType = matchedActionType,
                        targetLabel = cleanLabel,
                        selector = event.selector,
                        value = cleanValue,
                        description = summary,
                        isDestructive = event.isDestructive
                    )
                    AutomationStepCodec.reindex(current.steps + newStep)
                }

                state.copy(
                    recordingSession = current.copy(
                        steps = updatedSteps,
                        lastSequenceNumber = event.sequenceNumber
                    )
                )
            }
        }
        return if (accepted) {
            RecorderLifecycleResult(success = true)
        } else {
            reject(
                AutomationErrorCode.RECORDING_SESSION_INVALID,
                "Recording session changed while applying recorded event."
            )
        }
    }

    private fun handleOriginNavigationForRecording(tabId: String, destinationUrl: String) {
        val session = _uiState.value.recordingSession
        if (session.initiatingTabId != tabId) return
        if (session.status != RecordingStatus.RECORDING && session.status != RecordingStatus.PAUSED) return

        val destOrigin = automationEngine.parseOrigin(destinationUrl)
        val isSameOrigin = destOrigin is RecordingOriginResult.Valid &&
            destOrigin.canonicalOrigin == session.currentOrigin
        if (isSameOrigin) return

        recordingLifecycleJob?.cancel()
        val oldSessionId = session.sessionId
        automationEngine.detachRecorderBestEffort(
            webViewRegistry[tabId],
            oldSessionId.ifBlank { null }
        )

        _uiState.update { state ->
            val current = state.recordingSession
            if (current.initiatingTabId != tabId ||
                (current.status != RecordingStatus.RECORDING && current.status != RecordingStatus.PAUSED)
            ) {
                state
            } else {
                state.copy(
                    recordingSession = current.copy(
                        status = RecordingStatus.STOPPED,
                        sessionId = "",
                        pendingOriginChangeUrl = destinationUrl,
                        recordingError = RecordingErrorInfo(
                            code = AutomationErrorCode.RECORDING_ORIGIN_CHANGED,
                            message = "Recording stopped because navigation left ${current.targetDomain}."
                        )
                    ),
                    snackbarNotice = "Recording stopped — left ${current.targetDomain}"
                )
            }
        }
        refreshAutomationToolbarStatus(clearTransientResult = false)
    }

    private fun handleDocumentUnavailableForRecording(tabId: String, reason: String) {
        val session = _uiState.value.recordingSession
        if (session.initiatingTabId != tabId) return
        if (session.status != RecordingStatus.RECORDING && session.status != RecordingStatus.PAUSED) return

        recordingLifecycleJob?.cancel()
        val oldSessionId = session.sessionId
        automationEngine.detachRecorderBestEffort(
            webViewRegistry[tabId],
            oldSessionId.ifBlank { null }
        )

        _uiState.update { state ->
            val current = state.recordingSession
            if (current.initiatingTabId != tabId ||
                (current.status != RecordingStatus.RECORDING && current.status != RecordingStatus.PAUSED)
            ) {
                state
            } else {
                state.copy(
                    recordingSession = current.copy(
                        status = RecordingStatus.ERROR,
                        sessionId = "",
                        recordingError = RecordingErrorInfo(
                            code = AutomationErrorCode.RECORDING_DOCUMENT_UNAVAILABLE,
                            message = "Recording stopped: document failed to load ($reason)."
                        )
                    ),
                    snackbarNotice = "Recording stopped — page failed to load"
                )
            }
        }
        refreshAutomationToolbarStatus(clearTransientResult = false)
    }

    private fun handleTabsClosedForRecording(closedTabIds: Set<String>) {
        val session = _uiState.value.recordingSession
        if (session.initiatingTabId.isBlank() || session.initiatingTabId !in closedTabIds) return
        if (session.status != RecordingStatus.RECORDING && session.status != RecordingStatus.PAUSED) return

        recordingLifecycleJob?.cancel()
        automationEngine.detachRecorderBestEffort(
            webViewRegistry[session.initiatingTabId],
            session.sessionId.ifBlank { null }
        )

        _uiState.update { state ->
            val current = state.recordingSession
            if (current.initiatingTabId !in closedTabIds) {
                state
            } else {
                state.copy(
                    recordingSession = current.copy(
                        status = RecordingStatus.CANCELLED,
                        sessionId = "",
                        recordingError = RecordingErrorInfo(
                            code = AutomationErrorCode.RECORDING_TAB_CLOSED,
                            message = "Recording cancelled because the recording tab was closed."
                        )
                    ),
                    snackbarNotice = "Recording cancelled — recording tab closed"
                )
            }
        }
        refreshAutomationToolbarStatus(clearTransientResult = true)
    }

    private fun setRecordingErrorState(
        initiatingTabId: String,
        targetDomain: String,
        targetUrl: String,
        code: AutomationErrorCode,
        message: String,
        preserveSteps: Boolean
    ) {
        _uiState.update { state ->
            val prev = state.recordingSession
            val preservedSteps = if (preserveSteps) prev.steps else emptyList()
            state.copy(
                automationHudMode = AutomationHudMode.RECORDING,
                recordingSession = ActiveRecordingSession(
                    sessionId = "",
                    initiatingTabId = initiatingTabId,
                    initialOrigin = if (preserveSteps) prev.initialOrigin else "",
                    currentOrigin = if (preserveSteps) prev.currentOrigin else "",
                    status = RecordingStatus.ERROR,
                    steps = preservedSteps,
                    targetDomain = targetDomain,
                    targetUrl = targetUrl,
                    suggestedName = if (preserveSteps) prev.suggestedName else "",
                    startedAt = if (preserveSteps) prev.startedAt else 0L,
                    lastSequenceNumber = if (preserveSteps) prev.lastSequenceNumber else 0L,
                    recordingError = RecordingErrorInfo(code = code, message = message),
                    pendingOriginChangeUrl = prev.pendingOriginChangeUrl
                ),
                snackbarNotice = message
            )
        }
        refreshAutomationToolbarStatus(clearTransientResult = false)
    }

    fun renameRecordingDraft(name: String) {
        _uiState.update {
            it.copy(recordingSession = it.recordingSession.copy(suggestedName = name))
        }
    }

    fun editRecordingStep(
        stepId: String,
        description: String,
        value: String,
        targetLabel: String? = null,
        selector: String? = null
    ) {
        _uiState.update { state ->
            val updated = state.recordingSession.steps.map { step ->
                if (step.id == stepId) {
                    AutomationStepCodec.applyStepEdit(
                        existing = step,
                        description = description,
                        actionType = step.actionType,
                        value = value,
                        targetLabel = targetLabel ?: step.targetLabel,
                        selector = selector ?: step.selector,
                        destructiveClassifier = automationEngine::isClauseDestructive
                    )
                } else {
                    step
                }
            }
            state.copy(
                recordingSession = state.recordingSession.copy(
                    steps = AutomationStepCodec.reindex(updated)
                )
            )
        }
    }

    fun deleteRecordingStep(stepId: String) {
        _uiState.update { state ->
            val updated = state.recordingSession.steps.filterNot { it.id == stepId }
            state.copy(
                recordingSession = state.recordingSession.copy(
                    steps = AutomationStepCodec.reindex(updated)
                )
            )
        }
    }

    fun saveRecordingWorkflow(customName: String? = null) {
        val session = _uiState.value.recordingSession
        if (session.steps.isEmpty()) return
        recordingLifecycleJob?.cancel()
        val name = (customName ?: session.suggestedName).trim().ifBlank {
            if (session.targetDomain.isNotBlank() && session.targetDomain != "Start Page") {
                "${session.targetDomain} Recording"
            } else {
                "Recorded Automation"
            }
        }
        val activeTab = _uiState.value.activeTab
        val domain = session.targetDomain.ifBlank { automationEngine.extractDomain(activeTab.url) }
        val sourceUrl = session.targetUrl.ifBlank { activeTab.url }
        val targetTabId = session.initiatingTabId.ifBlank { activeTab.id }
        val oldSessionId = session.sessionId.ifBlank { null }
        val targetWebView = webViewRegistry[targetTabId]
        viewModelScope.launch {
            val savedId = repository.saveAutomationWorkflow(
                name = name,
                domain = domain,
                sourceUrl = sourceUrl,
                type = AutomationType.RECORDED,
                steps = session.steps
            )
            if (targetWebView != null) {
                automationEngine.uninstallRecorderScript(targetWebView, oldSessionId)
            }
            _uiState.update {
                it.copy(
                    recordingSession = ActiveRecordingSession(),
                    automationHudMode = AutomationHudMode.SAVED,
                    snackbarNotice = "Saved '$name' (${session.steps.size} steps)"
                )
            }
            refreshAutomationToolbarStatus(clearTransientResult = false)
            if (savedId > 0L) {
                refreshAutomationToolbarStatus(clearTransientResult = false)
            }
        }
    }

    fun discardRecording() {
        recordingLifecycleJob?.cancel()
        val session = _uiState.value.recordingSession
        val targetTabId = session.initiatingTabId.ifBlank { _uiState.value.activeTabId }
        val oldSessionId = session.sessionId.ifBlank { null }
        automationEngine.detachRecorderBestEffort(webViewRegistry[targetTabId], oldSessionId)
        _uiState.update {
            it.copy(
                recordingSession = ActiveRecordingSession(),
                automationHudMode = AutomationHudMode.OVERVIEW,
                snackbarNotice = "Recording discarded"
            )
        }
        refreshAutomationToolbarStatus(clearTransientResult = true)
    }

    // --- Manual Instructions Mode ---

    fun onInstructionDraftChanged(text: String) {
        _uiState.update { it.copy(instructionDraftText = text) }
    }

    fun addInstructionsToDraft() {
        val raw = _uiState.value.instructionDraftText.trim()
        if (raw.isEmpty()) return
        val activeTab = _uiState.value.activeTab
        val parsed = automationEngine.parseInstructionsToSteps(raw, activeTab.url)
        if (parsed.isEmpty()) return
        _uiState.update { state ->
            state.copy(
                instructionDraftSteps = AutomationStepCodec.reindex(state.instructionDraftSteps + parsed),
                instructionDraftText = ""
            )
        }
    }

    fun clearInstructionDraft() {
        _uiState.update {
            it.copy(
                instructionDraftText = "",
                instructionDraftSteps = emptyList()
            )
        }
    }

    fun reviewInstructionSteps(): Boolean {
        val text = _uiState.value.instructionDraftText.trim()
        if (text.isNotEmpty() && _uiState.value.instructionDraftSteps.isEmpty()) {
            addInstructionsToDraft()
        }
        return _uiState.value.instructionDraftSteps.isNotEmpty()
    }

    fun editInstructionStep(
        stepId: String,
        description: String,
        actionType: AutomationActionType,
        value: String,
        targetLabel: String? = null,
        selector: String? = null
    ) {
        _uiState.update { state ->
            val updated = state.instructionDraftSteps.map { step ->
                if (step.id == stepId) {
                    AutomationStepCodec.applyStepEdit(
                        existing = step,
                        description = description,
                        actionType = actionType,
                        value = value,
                        targetLabel = targetLabel ?: step.targetLabel,
                        selector = selector ?: step.selector,
                        destructiveClassifier = automationEngine::isClauseDestructive
                    )
                } else {
                    step
                }
            }
            state.copy(instructionDraftSteps = AutomationStepCodec.reindex(updated))
        }
    }

    fun deleteInstructionStep(stepId: String) {
        _uiState.update { state ->
            val updated = state.instructionDraftSteps.filterNot { it.id == stepId }
            state.copy(instructionDraftSteps = AutomationStepCodec.reindex(updated))
        }
    }

    fun moveInstructionStepUp(stepId: String) {
        _uiState.update { state ->
            val list = state.instructionDraftSteps.toMutableList()
            val idx = list.indexOfFirst { it.id == stepId }
            if (idx > 0) {
                val item = list.removeAt(idx)
                list.add(idx - 1, item)
            }
            state.copy(instructionDraftSteps = AutomationStepCodec.reindex(list))
        }
    }

    fun moveInstructionStepDown(stepId: String) {
        _uiState.update { state ->
            val list = state.instructionDraftSteps.toMutableList()
            val idx = list.indexOfFirst { it.id == stepId }
            if (idx in 0 until list.lastIndex) {
                val item = list.removeAt(idx)
                list.add(idx + 1, item)
            }
            state.copy(instructionDraftSteps = AutomationStepCodec.reindex(list))
        }
    }

    fun saveInstructionDraftAsWorkflow(name: String) {
        if (_uiState.value.instructionDraftText.isNotBlank() && _uiState.value.instructionDraftSteps.isEmpty()) {
            addInstructionsToDraft()
        }
        val state = _uiState.value
        val steps = state.instructionDraftSteps
        if (steps.isEmpty()) return
        val activeTab = state.activeTab
        val domain = automationEngine.extractDomain(activeTab.url)
        val cleanName = name.trim().ifBlank {
            if (domain == "Start Page") "Manual Automation" else "$domain Instructions"
        }
        val summaryText = steps.joinToString("\n") { it.description }
        viewModelScope.launch {
            repository.saveAutomationWorkflow(
                name = cleanName,
                domain = domain,
                sourceUrl = activeTab.url,
                type = AutomationType.MANUAL,
                steps = steps,
                instructionsText = summaryText
            )
            _uiState.update {
                it.copy(
                    snackbarNotice = "Saved '$cleanName' (${steps.size} steps)"
                )
            }
        }
    }

    fun executeInstructionDraft(confirmDestructive: Boolean = false) {
        if (automationExecutionJob?.isActive == true || _uiState.value.executionState?.status == "RUNNING") {
            return
        }
        if (_uiState.value.instructionDraftText.isNotBlank() && _uiState.value.instructionDraftSteps.isEmpty()) {
            addInstructionsToDraft()
        }
        val steps = _uiState.value.instructionDraftSteps
        if (steps.isEmpty()) return
        val destructiveSteps = steps.filter { it.isDestructive }
        if (destructiveSteps.isNotEmpty() && !confirmDestructive) {
            pendingDestructiveInstructionConfirm = true
            pendingDestructiveWorkflowId = null
            _uiState.update {
                it.copy(
                    executionState = WorkflowExecutionState(
                        workflowId = 0L,
                        workflowName = "Manual Instructions",
                        status = "AWAITING_CONFIRMATION",
                        currentStepIndex = 0,
                        totalSteps = steps.size,
                        stepLogs = emptyList(),
                        verificationMessage = "This workflow includes potentially destructive actions.",
                        isAwaitingConfirmation = true,
                        pendingDestructiveSteps = destructiveSteps
                    )
                )
            }
            return
        }

        val activeTab = _uiState.value.activeTab
        val domain = automationEngine.extractDomain(activeTab.url)
        val summaryText = steps.joinToString("\n") { it.description }
        viewModelScope.launch {
            if (automationExecutionJob?.isActive == true || _uiState.value.executionState?.status == "RUNNING") {
                return@launch
            }
            val savedId = repository.saveAutomationWorkflow(
                name = if (domain == "Start Page") "Manual Run" else "$domain Task",
                domain = domain,
                sourceUrl = activeTab.url,
                type = AutomationType.MANUAL,
                steps = steps,
                instructionsText = summaryText
            )
            runWorkflow(savedId, confirmDestructive = true)
        }
    }

    // --- Unified Workspace, Details & Execution Engine ---

    fun createManualWorkflow(
        name: String,
        domain: String,
        sourceUrl: String,
        instructionsText: String,
        steps: List<AutomationStep>
    ) {
        val resolvedSteps = if (steps.isNotEmpty()) {
            steps
        } else {
            automationEngine.parseInstructionsToSteps(instructionsText, sourceUrl)
        }
        if (resolvedSteps.isEmpty()) return
        val activeUrl = _uiState.value.activeTab.url
        val resolvedUrl = sourceUrl.trim().ifBlank { activeUrl }
        val resolvedDomain = domain.trim().ifBlank { automationEngine.extractDomain(resolvedUrl) }
        val cleanName = name.trim().ifBlank { "$resolvedDomain Workflow" }
        viewModelScope.launch {
            val newId = repository.saveAutomationWorkflow(
                name = cleanName,
                domain = resolvedDomain,
                sourceUrl = resolvedUrl,
                type = AutomationType.MANUAL,
                steps = resolvedSteps,
                instructionsText = instructionsText
            )
            _uiState.update {
                it.copy(
                    selectedAutomationWorkflowId = newId,
                    snackbarNotice = "Created automation '$cleanName'"
                )
            }
        }
    }

    fun renameWorkflow(workflowId: Long, newName: String) {
        val clean = newName.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            repository.renameAutomationWorkflow(workflowId, clean)
            _uiState.update { it.copy(snackbarNotice = "Renamed automation to '$clean'") }
        }
    }

    fun duplicateWorkflow(workflowId: Long) {
        viewModelScope.launch {
            val copyId = repository.duplicateAutomationWorkflow(workflowId)
            if (copyId != null) {
                _uiState.update { it.copy(snackbarNotice = "Duplicated automation") }
            }
        }
    }

    fun deleteWorkflow(workflowId: Long) {
        viewModelScope.launch {
            repository.deleteAutomationWorkflow(workflowId)
            _uiState.update {
                it.copy(
                    selectedAutomationWorkflowId = if (it.selectedAutomationWorkflowId == workflowId) {
                        null
                    } else {
                        it.selectedAutomationWorkflowId
                    },
                    snackbarNotice = "Automation deleted"
                )
            }
        }
    }

    fun updateWorkflowSteps(workflowId: Long, steps: List<AutomationStep>) {
        viewModelScope.launch {
            repository.updateAutomationWorkflowSteps(workflowId, steps)
        }
    }

    fun addStepToWorkflow(
        workflowId: Long,
        actionType: AutomationActionType,
        targetLabel: String,
        value: String,
        description: String
    ) {
        val wf = automationWorkflowsState.value.firstOrNull { it.id == workflowId } ?: return
        val existing = AutomationStepCodec.decode(wf.stepsJson)
        val cleanDesc = description.trim().ifBlank {
            AutomationStepCodec.buildStepSummary(actionType, targetLabel, value)
        }
        val cleanLabel = targetLabel.trim().ifBlank { cleanDesc }
        val newStep = AutomationStep(
            id = "step_${UUID.randomUUID()}",
            order = existing.size + 1,
            actionType = actionType,
            targetLabel = cleanLabel,
            selector = "",
            value = value,
            description = cleanDesc,
            isDestructive = actionType == AutomationActionType.SUBMIT ||
                automationEngine.isClauseDestructive(cleanDesc.lowercase()) ||
                automationEngine.isClauseDestructive(cleanLabel.lowercase())
        )
        updateWorkflowSteps(workflowId, existing + newStep)
    }

    fun runWorkflow(workflowId: Long, confirmDestructive: Boolean = false) {
        if (automationExecutionJob?.isActive == true || _uiState.value.executionState?.status == "RUNNING") {
            return
        }
        toolbarCompletedResetJob?.cancel()

        automationExecutionJob = viewModelScope.launch {
            val currentJob = coroutineContext[Job]
            try {
                val workflow = repository.getAutomationWorkflowById(workflowId)
                    ?: automationWorkflowsState.value.firstOrNull { it.id == workflowId }
                    ?: return@launch

                val decodeResult = AutomationStepCodec.decodeSafely(workflow.stepsJson)
                val steps = when (decodeResult) {
                    is AutomationStepDecodeResult.Success -> decodeResult.steps
                    is AutomationStepDecodeResult.UnsupportedVersion -> {
                        _uiState.update {
                            it.copy(
                                executionState = WorkflowExecutionState(
                                    workflowId = workflow.id,
                                    workflowName = workflow.name,
                                    status = "FAILED",
                                    currentStepIndex = 0,
                                    totalSteps = 0,
                                    verificationMessage = decodeResult.message,
                                    errorCode = AutomationErrorCode.UNSUPPORTED_SCHEMA
                                ),
                                automationToolbarStatus = AutomationToolbarStatus.ERROR
                            )
                        }
                        return@launch
                    }
                    is AutomationStepDecodeResult.MalformedJson -> {
                        _uiState.update {
                            it.copy(
                                executionState = WorkflowExecutionState(
                                    workflowId = workflow.id,
                                    workflowName = workflow.name,
                                    status = "FAILED",
                                    currentStepIndex = 0,
                                    totalSteps = 0,
                                    verificationMessage = decodeResult.message,
                                    errorCode = AutomationErrorCode.UNSUPPORTED_SCHEMA
                                ),
                                automationToolbarStatus = AutomationToolbarStatus.ERROR
                            )
                        }
                        return@launch
                    }
                }

                if (steps.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            executionState = WorkflowExecutionState(
                                workflowId = workflow.id,
                                workflowName = workflow.name,
                                status = "FAILED",
                                currentStepIndex = 0,
                                totalSteps = 0,
                                verificationMessage = "This workflow has no steps to run.",
                                errorCode = AutomationErrorCode.EMPTY_WORKFLOW
                            ),
                            automationToolbarStatus = AutomationToolbarStatus.ERROR
                        )
                    }
                    return@launch
                }

                val destructiveSteps = steps.filter { it.isDestructive }
                if (destructiveSteps.isNotEmpty() && !confirmDestructive) {
                    pendingDestructiveWorkflowId = workflow.id
                    pendingDestructiveInstructionConfirm = false
                    _uiState.update {
                        it.copy(
                            executionState = WorkflowExecutionState(
                                workflowId = workflow.id,
                                workflowName = workflow.name,
                                status = "AWAITING_CONFIRMATION",
                                currentStepIndex = 0,
                                totalSteps = steps.size,
                                stepLogs = emptyList(),
                                verificationMessage = "Confirmation required before executing destructive steps.",
                                isAwaitingConfirmation = true,
                                pendingDestructiveSteps = destructiveSteps
                            )
                        )
                    }
                    return@launch
                }

                val targetTab = _uiState.value.activeTab
                val targetTabId = targetTab.id
                val execId = repository.recordAutomationExecutionStart(workflow, steps.size)
                var completedStepsCount = 0

                _uiState.update {
                    it.copy(
                        automationToolbarStatus = AutomationToolbarStatus.EXECUTING,
                        executionState = WorkflowExecutionState(
                            workflowId = workflow.id,
                            workflowName = workflow.name,
                            status = "RUNNING",
                            currentStepIndex = 0,
                            totalSteps = steps.size,
                            stepLogs = listOf("Started '${workflow.name}' (${steps.size} steps)"),
                            verificationMessage = "Executing on ${workflow.domain}…"
                        )
                    )
                }

                try {
                    val outcome = automationEngine.executeWorkflow(
                        workflow = workflow,
                        steps = steps,
                        currentTabUrl = targetTab.url,
                        webViewProvider = { webViewRegistry[targetTabId] },
                        onNavigateUrl = { dest ->
                            navigateTabUrlForAutomation(targetTabId, dest)
                        },
                        onStepProgress = { stepIndex, msg ->
                            _uiState.update { state ->
                                val currentExec = state.executionState
                                if (currentExec != null && currentExec.workflowId == workflow.id) {
                                    state.copy(
                                        executionState = currentExec.copy(
                                            currentStepIndex = stepIndex,
                                            stepLogs = currentExec.stepLogs + msg
                                        )
                                    )
                                } else {
                                    state
                                }
                            }
                        },
                        tabId = targetTabId,
                        navigationSynchronizer = navigationSynchronizer,
                        onStepCompleted = { doneCount ->
                            completedStepsCount = doneCount
                        }
                    )

                    repository.finishAutomationExecution(
                        workflowId = workflow.id,
                        executionId = execId,
                        status = outcome.status,
                        completedSteps = outcome.completedSteps,
                        totalSteps = outcome.totalSteps,
                        verificationSummary = outcome.verificationSummary
                    )

                    val succeeded = outcome.status == "COMPLETED"
                    _uiState.update { state ->
                        val currentExec = state.executionState
                        state.copy(
                            automationToolbarStatus = if (succeeded) {
                                AutomationToolbarStatus.COMPLETED
                            } else {
                                AutomationToolbarStatus.ERROR
                            },
                            executionState = currentExec?.copy(
                                status = outcome.status,
                                currentStepIndex = outcome.completedSteps,
                                stepLogs = currentExec.stepLogs + outcome.verificationSummary,
                                verificationMessage = outcome.verificationSummary,
                                isAwaitingConfirmation = false,
                                errorCode = outcome.errorCode
                            ),
                            snackbarNotice = if (succeeded) {
                                "Completed '${workflow.name}'"
                            } else {
                                "Automation stopped: ${outcome.verificationSummary}"
                            }
                        )
                    }

                    if (succeeded) {
                        toolbarCompletedResetJob?.cancel()
                        toolbarCompletedResetJob = viewModelScope.launch {
                            delay(2_800L)
                            refreshAutomationToolbarStatus(clearTransientResult = true)
                        }
                    }
                } catch (ce: CancellationException) {
                    withContext(NonCancellable) {
                        val cancelSummary = "Cancelled after $completedStepsCount/${steps.size} steps."
                        repository.finishAutomationExecution(
                            workflowId = workflow.id,
                            executionId = execId,
                            status = "CANCELLED",
                            completedSteps = completedStepsCount,
                            totalSteps = steps.size,
                            verificationSummary = cancelSummary
                        )
                        _uiState.update { state ->
                            val currentExec = state.executionState
                            state.copy(
                                executionState = currentExec?.copy(
                                    status = "CANCELLED",
                                    currentStepIndex = completedStepsCount,
                                    stepLogs = currentExec.stepLogs + cancelSummary,
                                    verificationMessage = cancelSummary,
                                    isAwaitingConfirmation = false,
                                    errorCode = AutomationErrorCode.CANCELLED
                                ),
                                snackbarNotice = "Cancelled '${workflow.name}'"
                            )
                        }
                        refreshAutomationToolbarStatus(clearTransientResult = true)
                    }
                    throw ce
                }
            } finally {
                if (automationExecutionJob === currentJob) {
                    automationExecutionJob = null
                }
            }
        }
    }

    fun cancelWorkflowExecution() {
        val activeJob = automationExecutionJob
        if (activeJob != null && activeJob.isActive) {
            activeJob.cancel()
            return
        }
        val currentExec = _uiState.value.executionState
        if (currentExec?.status == "RUNNING") {
            _uiState.update { state ->
                state.copy(
                    executionState = currentExec.copy(
                        status = "CANCELLED",
                        verificationMessage = "Execution cancelled.",
                        isAwaitingConfirmation = false,
                        errorCode = AutomationErrorCode.CANCELLED
                    )
                )
            }
            refreshAutomationToolbarStatus(clearTransientResult = true)
        }
    }

    fun confirmPendingDestructiveExecution() {
        val exec = _uiState.value.executionState ?: return
        if (!exec.isAwaitingConfirmation) return
        val targetId = pendingDestructiveWorkflowId
        val wasDraft = pendingDestructiveInstructionConfirm
        pendingDestructiveWorkflowId = null
        pendingDestructiveInstructionConfirm = false
        _uiState.update { it.copy(executionState = null) }
        if (wasDraft) {
            executeInstructionDraft(confirmDestructive = true)
        } else if (targetId != null) {
            runWorkflow(targetId, confirmDestructive = true)
        }
    }

    fun cancelPendingDestructiveExecution() {
        pendingDestructiveWorkflowId = null
        pendingDestructiveInstructionConfirm = false
        _uiState.update { it.copy(executionState = null) }
        refreshAutomationToolbarStatus(clearTransientResult = true)
    }

    fun clearExecutionBanner() {
        _uiState.update { it.copy(executionState = null) }
        refreshAutomationToolbarStatus(clearTransientResult = true)
    }

    fun exportAutomationsJson(onExportReady: (String) -> Unit) {
        viewModelScope.launch {
            val json = repository.exportAutomationsAsJson()
            onExportReady(json)
        }
    }

    fun importAutomationsJson(rawJson: String) {
        viewModelScope.launch {
            val imported = repository.importAutomationsFromJson(rawJson)
            _uiState.update {
                it.copy(
                    snackbarNotice = if (imported > 0) {
                        "Imported $imported automation${if (imported == 1) "" else "s"}"
                    } else {
                        "No valid automations found in JSON"
                    }
                )
            }
        }
    }

    private fun navigateTabUrlForAutomation(tabId: String, rawInput: String) {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return
        val prefs = preferencesState.value
        val normalized = privacyManager.normalizeAndUpgradeUrl(
            rawInput = trimmed,
            httpsUpgradeEnabled = prefs.httpsUpgradeEnabled,
            searchEngineTemplate = prefs.searchEngineTemplate
        )
        navigationSynchronizer.beginNavigation(tabId, normalized.finalUrl)
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.map { tab ->
                    if (tab.id == tabId) {
                        tab.copy(
                            url = normalized.finalUrl,
                            requestedUrl = normalized.finalUrl,
                            loadRequestId = tab.loadRequestId + 1L,
                            title = deriveShortTitle(normalized.finalUrl),
                            isHttps = normalized.finalUrl.startsWith("https://") || normalized.isBuiltInScheme,
                            isLoading = !normalized.finalUrl.equals("apex://home", ignoreCase = true),
                            progress = if (normalized.finalUrl.equals("apex://home", ignoreCase = true)) 1f else 0.08f
                        )
                    } else {
                        tab
                    }
                }
            )
        }
    }

    private fun refreshAutomationToolbarStatus(clearTransientResult: Boolean) {
        _uiState.update { state ->
            val currentToolbar = state.automationToolbarStatus
            val newStatus = when {
                state.recordingSession.status == RecordingStatus.RECORDING ->
                    AutomationToolbarStatus.RECORDING
                state.recordingSession.status == RecordingStatus.PAUSED ->
                    AutomationToolbarStatus.PAUSED
                state.executionState?.status == "RUNNING" ->
                    AutomationToolbarStatus.EXECUTING
                !clearTransientResult && currentToolbar == AutomationToolbarStatus.COMPLETED ->
                    AutomationToolbarStatus.COMPLETED
                !clearTransientResult &&
                    (state.executionState?.status == "FAILED" ||
                        state.pageInspection.errorMessage != null ||
                        state.recordingSession.status == RecordingStatus.ERROR) ->
                    AutomationToolbarStatus.ERROR
                state.pageInspection.elements.isNotEmpty() ->
                    AutomationToolbarStatus.ELEMENTS_DETECTED
                else -> AutomationToolbarStatus.IDLE
            }
            state.copy(automationToolbarStatus = newStatus)
        }
    }

    // --- Preferences & Settings Controls ---

    fun toggleShields(enabled: Boolean) {
        viewModelScope.launch { repository.setShieldsEnabled(enabled) }
    }

    fun toggleHttpsUpgrade(enabled: Boolean) {
        viewModelScope.launch { repository.setHttpsUpgradeEnabled(enabled) }
    }

    fun toggleAggressivePreload(enabled: Boolean) {
        viewModelScope.launch { repository.setAggressivePreloadEnabled(enabled) }
    }

    fun toggleScriptGuard(enabled: Boolean) {
        viewModelScope.launch { repository.setScriptGuardEnabled(enabled) }
    }

    fun toggleDesktopViewport(enabled: Boolean) {
        viewModelScope.launch {
            repository.setDesktopViewportEnabled(enabled)
            val currentPrefs = preferencesState.value
            val tabsById = _uiState.value.tabs.associateBy { it.id }
            webViewRegistry.forEach { (tabId, wv) ->
                val isPrivate = tabsById[tabId]?.isPrivate == true
                performanceOptimizer.configureFastWebView(
                    webView = wv,
                    scriptGuardEnabled = currentPrefs.scriptGuardEnabled,
                    desktopMode = enabled,
                    aggressiveCaching = currentPrefs.aggressivePreloadEnabled,
                    isIncognito = isPrivate
                )
                performanceOptimizer.applyDesktopViewportMode(wv, enabled)
            }
            _uiState.update {
                it.copy(
                    snackbarNotice = if (enabled) "Desktop site enabled" else "Mobile site enabled"
                )
            }
        }
    }

    fun toggleDynamicColor(enabled: Boolean) {
        viewModelScope.launch { repository.setDynamicColorEnabled(enabled) }
    }

    fun selectSeedPalette(preset: SeedPalettePreset) {
        viewModelScope.launch { repository.setSeedPaletteOrdinal(preset.ordinal) }
    }

    fun selectThemeMode(mode: ThemeModeOption) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun cycleSeedTheme() {
        viewModelScope.launch { repository.cycleSeedPalette() }
    }

    fun setDefaultZoomPercent(percent: Int) {
        val clamped = BrowserPreferences.clampZoomPercent(percent)
        viewModelScope.launch {
            repository.setDefaultZoomPercent(clamped)
            syncWebViewAccessibilitySettings()
        }
    }

    fun setSiteZoom(hostOrUrl: String, percent: Int) {
        val clamped = BrowserPreferences.clampZoomPercent(percent)
        viewModelScope.launch {
            repository.setSiteZoom(hostOrUrl, clamped)
            syncWebViewAccessibilitySettings()
        }
    }

    fun removeSiteZoom(hostOrUrl: String) {
        viewModelScope.launch {
            repository.removeSiteZoom(hostOrUrl)
            syncWebViewAccessibilitySettings()
        }
    }

    fun clearSavedSiteZooms() {
        viewModelScope.launch {
            repository.clearSavedSiteZooms()
            syncWebViewAccessibilitySettings()
        }
    }

    fun adjustActiveZoomPercent(newPercent: Int) {
        val clamped = BrowserPreferences.clampZoomPercent(newPercent)
        val active = _uiState.value.activeTab
        val host = if (active.isHomePage) "" else BrowserPreferences.normalizeZoomHost(active.url)
        if (host.isNotEmpty()) {
            setSiteZoom(host, clamped)
        } else {
            setDefaultZoomPercent(clamped)
        }
    }

    fun resetActiveSiteZoom() {
        val active = _uiState.value.activeTab
        val host = if (active.isHomePage) "" else BrowserPreferences.normalizeZoomHost(active.url)
        if (host.isNotEmpty()) {
            removeSiteZoom(host)
        } else {
            setDefaultZoomPercent(100)
        }
    }

    fun toggleShowZoomInMainMenu(enabled: Boolean) {
        viewModelScope.launch { repository.setShowZoomInMainMenu(enabled) }
    }

    fun toggleForceEnableZoom(enabled: Boolean) {
        viewModelScope.launch {
            repository.setForceEnableZoom(enabled)
            webViewRegistry.values.forEach { wv ->
                performanceOptimizer.applyForceEnableZoom(wv, enabled)
            }
        }
    }

    fun toggleSimplifiedView(enabled: Boolean) {
        viewModelScope.launch { repository.setSimplifiedViewEnabled(enabled) }
    }

    fun toggleCaretBrowsing(enabled: Boolean, announceShortcut: Boolean = false) {
        viewModelScope.launch {
            repository.setCaretBrowsingEnabled(enabled)
            webViewRegistry.values.forEach { wv ->
                performanceOptimizer.applyCaretBrowsing(wv, enabled)
            }
            if (announceShortcut) {
                _uiState.update {
                    it.copy(
                        snackbarNotice = if (enabled) {
                            "Caret browsing enabled (F7)"
                        } else {
                            "Caret browsing disabled (F7)"
                        }
                    )
                }
            }
        }
    }

    fun toggleTouchpadSwipeNavigation(enabled: Boolean) {
        viewModelScope.launch { repository.setTouchpadSwipeNavigationEnabled(enabled) }
    }

    private fun syncWebViewAccessibilitySettings() {
        val prefs = preferencesState.value
        val tabsById = _uiState.value.tabs.associateBy { it.id }
        webViewRegistry.forEach { (tabId, wv) ->
            val tabUrl = tabsById[tabId]?.url
            wv.settings.textZoom = prefs.effectiveZoomForUrl(tabUrl)
        }
    }

    // --- Languages & Offline Translation Models (ML Kit On-Device) ---

    fun refreshDownloadedLanguageModels() {
        viewModelScope.launch {
            val models = translationEngine.refreshDownloadedModels()
            _downloadedLanguageCodes.value = models
        }
    }

    fun downloadTranslationModel(mlKitCode: String) {
        val normalized = LanguageCatalog.normalizeMlKitCode(mlKitCode) ?: return
        if (normalized in _downloadingLanguageCodes.value) return
        _failedLanguageDownloads.update { it - normalized }
        _downloadingLanguageCodes.update { it + normalized }
        val info = LanguageCatalog.describeLanguage(normalized)
        viewModelScope.launch {
            val result = translationEngine.downloadLanguageModel(
                mlKitCode = normalized,
                requireWifi = preferencesState.value.wifiOnlyTranslationDownloads
            )
            _downloadingLanguageCodes.update { it - normalized }
            if (result.isSuccess) {
                val refreshed = translationEngine.refreshDownloadedModels()
                _downloadedLanguageCodes.value = refreshed + normalized
                _uiState.update {
                    it.copy(snackbarNotice = "${info.displayName} offline translation model ready")
                }
            } else {
                _failedLanguageDownloads.update { it + normalized }
                _uiState.update {
                    it.copy(snackbarNotice = "Failed to download ${info.displayName} model")
                }
            }
        }
    }

    fun deleteTranslationModel(mlKitCode: String) {
        val normalized = LanguageCatalog.normalizeMlKitCode(mlKitCode) ?: return
        val info = LanguageCatalog.describeLanguage(normalized)
        viewModelScope.launch {
            val result = translationEngine.deleteLanguageModel(normalized)
            if (result.isSuccess) {
                val refreshed = translationEngine.refreshDownloadedModels()
                _downloadedLanguageCodes.value = refreshed - normalized
                _uiState.update {
                    it.copy(snackbarNotice = "Removed ${info.displayName} offline model")
                }
            } else {
                _uiState.update {
                    it.copy(snackbarNotice = "Unable to remove ${info.displayName} model")
                }
            }
        }
    }

    fun setAppLanguageTag(tag: String) {
        viewModelScope.launch { repository.setAppLanguageTag(tag) }
    }

    fun addPreferredLanguage(tag: String, alsoDownloadModel: Boolean = false) {
        val clean = tag.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val current = preferencesState.value.preferredLanguageTags
            if (current.none { it.equals(clean, ignoreCase = true) }) {
                repository.setPreferredLanguageTags(current + clean)
            }
            if (alsoDownloadModel) {
                LanguageCatalog.normalizeMlKitCode(clean)?.let { code ->
                    if (code !in _downloadedLanguageCodes.value) {
                        downloadTranslationModel(code)
                    }
                }
            }
        }
    }

    fun removePreferredLanguage(tag: String) {
        viewModelScope.launch {
            val current = preferencesState.value.preferredLanguageTags
            val updated = current.filterNot { it.equals(tag, ignoreCase = true) }
            if (updated.isNotEmpty()) {
                repository.setPreferredLanguageTags(updated)
            }
        }
    }

    fun movePreferredLanguageUp(tag: String) {
        viewModelScope.launch {
            val current = preferencesState.value.preferredLanguageTags.toMutableList()
            val idx = current.indexOfFirst { it.equals(tag, ignoreCase = true) }
            if (idx > 0) {
                val item = current.removeAt(idx)
                current.add(idx - 1, item)
                repository.setPreferredLanguageTags(current)
            }
        }
    }

    fun movePreferredLanguageDown(tag: String) {
        viewModelScope.launch {
            val current = preferencesState.value.preferredLanguageTags.toMutableList()
            val idx = current.indexOfFirst { it.equals(tag, ignoreCase = true) }
            if (idx in 0 until current.lastIndex) {
                val item = current.removeAt(idx)
                current.add(idx + 1, item)
                repository.setPreferredLanguageTags(current)
            }
        }
    }

    fun movePreferredLanguageToTop(tag: String) {
        viewModelScope.launch {
            val current = preferencesState.value.preferredLanguageTags.toMutableList()
            val idx = current.indexOfFirst { it.equals(tag, ignoreCase = true) }
            if (idx > 0) {
                val item = current.removeAt(idx)
                current.add(0, item)
                repository.setPreferredLanguageTags(current)
            }
        }
    }

    fun toggleOfferToTranslate(enabled: Boolean) {
        viewModelScope.launch { repository.setOfferToTranslateEnabled(enabled) }
    }

    fun setTranslateTargetLanguage(code: String) {
        val normalized = LanguageCatalog.normalizeMlKitCode(code) ?: return
        viewModelScope.launch { repository.setTranslateTargetLanguageCode(normalized) }
    }

    fun toggleAutoTranslateLanguage(code: String) {
        val normalized = LanguageCatalog.normalizeMlKitCode(code) ?: return
        viewModelScope.launch {
            val current = preferencesState.value.autoTranslateLanguageCodes
            val updated = if (normalized in current) current - normalized else current + normalized
            repository.setAutoTranslateLanguageCodes(updated)
            if (normalized in updated) {
                val never = preferencesState.value.neverTranslateLanguageCodes - normalized
                repository.setNeverTranslateLanguageCodes(never)
            }
        }
    }

    fun toggleNeverTranslateLanguage(code: String) {
        val normalized = LanguageCatalog.normalizeMlKitCode(code) ?: return
        viewModelScope.launch {
            val current = preferencesState.value.neverTranslateLanguageCodes
            val updated = if (normalized in current) current - normalized else current + normalized
            repository.setNeverTranslateLanguageCodes(updated)
            if (normalized in updated) {
                val auto = preferencesState.value.autoTranslateLanguageCodes - normalized
                repository.setAutoTranslateLanguageCodes(auto)
            }
        }
    }

    fun toggleWifiOnlyTranslationDownloads(enabled: Boolean) {
        viewModelScope.launch { repository.setWifiOnlyTranslationDownloads(enabled) }
    }

    fun detectPageLanguageAndMaybeAutoTranslate(
        tabId: String,
        pageUrl: String,
        htmlLangHint: String?,
        textSample: String
    ) {
        val cleanUrl = pageUrl.trim()
        if (cleanUrl.isEmpty() || cleanUrl.equals("apex://home", ignoreCase = true)) return

        viewModelScope.launch {
            val prefs = preferencesState.value
            val targetCode = LanguageCatalog.normalizeMlKitCode(prefs.translateTargetLanguageCode) ?: "en"
            val detected = translationEngine.detectLanguage(textSample)
                ?: LanguageCatalog.normalizeMlKitCode(htmlLangHint)
                ?: return@launch

            val currentState = _tabTranslationStates.value[tabId]
            if (currentState != null &&
                currentState.pageUrl == cleanUrl &&
                (currentState.isTranslated || currentState.bannerDismissed)
            ) {
                return@launch
            }

            if (detected == targetCode) {
                _tabTranslationStates.update {
                    it + (
                        tabId to TabTranslationState(
                            tabId = tabId,
                            pageUrl = cleanUrl,
                            detectedSourceCode = detected,
                            targetCode = targetCode,
                            isBannerVisible = false
                        )
                    )
                }
                return@launch
            }

            val shouldAutoTranslate = detected in prefs.autoTranslateLanguageCodes
            val isNeverTranslate = detected in prefs.neverTranslateLanguageCodes
            val showBanner = prefs.offerToTranslateEnabled && !isNeverTranslate

            _tabTranslationStates.update {
                it + (
                    tabId to TabTranslationState(
                        tabId = tabId,
                        pageUrl = cleanUrl,
                        detectedSourceCode = detected,
                        targetCode = targetCode,
                        isBannerVisible = showBanner || shouldAutoTranslate
                    )
                )
            }

            if (shouldAutoTranslate) {
                translateWebPage(tabId = tabId, targetCodeOverride = targetCode)
            }
        }
    }

    fun translateActiveWebPage(targetCodeOverride: String? = null) {
        val active = _uiState.value.activeTab
        if (active.isHomePage) return
        translateWebPage(tabId = active.id, targetCodeOverride = targetCodeOverride)
    }

    fun translateWebPage(tabId: String, targetCodeOverride: String? = null) {
        val tab = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
        if (tab.isHomePage) return
        val wv = webViewRegistry[tabId] ?: return

        val prefs = preferencesState.value
        val targetCode = LanguageCatalog.normalizeMlKitCode(targetCodeOverride)
            ?: LanguageCatalog.normalizeMlKitCode(prefs.translateTargetLanguageCode)
            ?: "en"
        val existingState = _tabTranslationStates.value[tabId]

        _tabTranslationStates.update { map ->
            map + (
                tabId to (existingState ?: TabTranslationState(tabId = tabId, pageUrl = tab.url)).copy(
                    pageUrl = tab.url,
                    targetCode = targetCode,
                    isTranslating = true,
                    isBannerVisible = true,
                    bannerDismissed = false,
                    errorMessage = null
                )
            )
        }

        val collectNodesJs = """
            (function() {
                try {
                    if (!window.__apexOrigTextNodes) {
                        window.__apexOrigTextNodes = [];
                        var skipTags = { SCRIPT:1, STYLE:1, NOSCRIPT:1, CODE:1, PRE:1, SVG:1, TEXTAREA:1 };
                        var walker = document.createTreeWalker(document.body || document.documentElement, NodeFilter.SHOW_TEXT, null, false);
                        var node;
                        while ((node = walker.nextNode())) {
                            var parent = node.parentElement;
                            if (!parent || skipTags[parent.tagName]) continue;
                            var val = node.nodeValue || "";
                            if (val.trim().length >= 2) {
                                window.__apexOrigTextNodes.push({ node: node, orig: val });
                            }
                        }
                    }
                    var items = window.__apexOrigTextNodes.slice(0, 120).map(function(entry) { return entry.orig; });
                    var sample = items.slice(0, 16).join(" ").substring(0, 1200);
                    return JSON.stringify({
                        htmlLang: (document.documentElement.getAttribute("lang") || "").trim(),
                        sample: sample,
                        items: items
                    });
                } catch (e) {
                    return JSON.stringify({ htmlLang: "", sample: "", items: [] });
                }
            })();
        """.trimIndent()

        wv.evaluateJavascript(collectNodesJs) { rawResult ->
            viewModelScope.launch {
                val parsed = parseJsStringResult(rawResult)?.let { jsonStr ->
                    runCatching { JSONObject(jsonStr) }.getOrNull()
                }
                val htmlLang = parsed?.optString("htmlLang", "").orEmpty()
                val sample = parsed?.optString("sample", "").orEmpty()
                val itemsArr = parsed?.optJSONArray("items")
                val segments = buildList {
                    if (itemsArr != null) {
                        for (i in 0 until itemsArr.length()) {
                            add(itemsArr.optString(i))
                        }
                    }
                }

                if (segments.isEmpty()) {
                    _tabTranslationStates.update { map ->
                        map + (
                            tabId to (map[tabId] ?: TabTranslationState(tabId = tabId)).copy(
                                isTranslating = false,
                                errorMessage = "No translatable text found"
                            )
                        )
                    }
                    return@launch
                }

                val sourceCode = existingState?.detectedSourceCode
                    ?: translationEngine.detectLanguage(sample)
                    ?: LanguageCatalog.normalizeMlKitCode(htmlLang)
                    ?: "es"

                if (sourceCode == targetCode) {
                    val targetName = LanguageCatalog.describeLanguage(targetCode).displayName
                    _tabTranslationStates.update { map ->
                        map + (
                            tabId to (map[tabId] ?: TabTranslationState(tabId = tabId)).copy(
                                detectedSourceCode = sourceCode,
                                targetCode = targetCode,
                                isTranslating = false,
                                isTranslated = false,
                                isBannerVisible = false
                            )
                        )
                    }
                    _uiState.update {
                        it.copy(snackbarNotice = "Page is already in $targetName")
                    }
                    return@launch
                }

                val translatedResult = translationEngine.translateSegments(
                    segments = segments,
                    sourceCode = sourceCode,
                    targetCode = targetCode,
                    requireWifiForDownload = preferencesState.value.wifiOnlyTranslationDownloads
                )

                translatedResult.onSuccess { translated ->
                    val refreshed = translationEngine.refreshDownloadedModels()
                    if (refreshed.isNotEmpty()) {
                        _downloadedLanguageCodes.value = refreshed + sourceCode + targetCode
                    }
                    val payloadArray = JSONArray()
                    translated.forEach { payloadArray.put(it) }
                    val applyJs = """
                        (function(list) {
                            try {
                                if (!window.__apexOrigTextNodes) return;
                                for (var i = 0; i < list.length && i < window.__apexOrigTextNodes.length; i++) {
                                    var orig = window.__apexOrigTextNodes[i].orig;
                                    var lead = orig.match(/^\s*/)[0] || "";
                                    var trail = orig.match(/\s*$/)[0] || "";
                                    window.__apexOrigTextNodes[i].node.nodeValue = lead + list[i] + trail;
                                }
                            } catch (e) {}
                        })(${payloadArray.toString()});
                    """.trimIndent()
                    webViewRegistry[tabId]?.evaluateJavascript(applyJs, null)

                    _tabTranslationStates.update { map ->
                        map + (
                            tabId to TabTranslationState(
                                tabId = tabId,
                                pageUrl = tab.url,
                                detectedSourceCode = sourceCode,
                                targetCode = targetCode,
                                isTranslated = true,
                                isTranslating = false,
                                isBannerVisible = true,
                                bannerDismissed = false
                            )
                        )
                    }
                }.onFailure { err ->
                    _tabTranslationStates.update { map ->
                        map + (
                            tabId to (map[tabId] ?: TabTranslationState(tabId = tabId, pageUrl = tab.url)).copy(
                                detectedSourceCode = sourceCode,
                                targetCode = targetCode,
                                isTranslating = false,
                                isBannerVisible = true,
                                errorMessage = err.message ?: "Translation failed"
                            )
                        )
                    }
                    _uiState.update {
                        it.copy(snackbarNotice = "Translation model download or translation failed")
                    }
                }
            }
        }
    }

    fun restoreOriginalWebPage(tabId: String) {
        val wv = webViewRegistry[tabId]
        val restoreJs = """
            (function() {
                try {
                    if (!window.__apexOrigTextNodes) return;
                    for (var i = 0; i < window.__apexOrigTextNodes.length; i++) {
                        window.__apexOrigTextNodes[i].node.nodeValue = window.__apexOrigTextNodes[i].orig;
                    }
                } catch (e) {}
            })();
        """.trimIndent()
        wv?.evaluateJavascript(restoreJs, null)
        _tabTranslationStates.update { map ->
            val current = map[tabId] ?: return@update map
            map + (
                tabId to current.copy(
                    isTranslated = false,
                    isTranslating = false,
                    isBannerVisible = true
                )
            )
        }
    }

    fun dismissTranslationBanner(tabId: String) {
        _tabTranslationStates.update { map ->
            val current = map[tabId] ?: return@update map
            map + (
                tabId to current.copy(
                    isBannerVisible = false,
                    bannerDismissed = true
                )
            )
        }
    }

    private fun parseJsStringResult(raw: String?): String? {
        if (raw.isNullOrBlank() || raw == "null") return null
        return runCatching {
            if (raw.startsWith("\"") && raw.endsWith("\"")) {
                org.json.JSONTokener(raw).nextValue().toString()
            } else {
                raw
            }
        }.getOrNull()
    }

    fun resetPrivacyStats() {
        viewModelScope.launch {
            repository.resetTelemetryStats()
            performanceOptimizer.wipeIncognitoSessionStorage()
            _uiState.update {
                it.copy(snackbarNotice = "Search history and browsing data cleared")
            }
        }
    }

    // --- Extension 1: Nano Multi-Agent Automation ---

    fun runNanoTask(userPrompt: String) {
        val cleanPrompt = userPrompt.trim()
        if (cleanPrompt.isEmpty() || _uiState.value.isNanoRunning) return

        val userMsg = NanoChatMessage(
            id = "u_${System.currentTimeMillis()}",
            isUser = true,
            text = cleanPrompt
        )
        val agentMsgId = "a_${System.currentTimeMillis()}"
        val placeholderAgentMsg = NanoChatMessage(
            id = agentMsgId,
            isUser = false,
            text = "Executing multi-agent DOM workflow…",
            steps = emptyList(),
            isRunning = true
        )

        _uiState.update {
            it.copy(
                nanoMessages = it.nanoMessages + userMsg + placeholderAgentMsg,
                isNanoRunning = true
            )
        }

        viewModelScope.launch {
            val startTabUrl = _uiState.value.activeTab.url
            val outcome = nanoEngine.runTask(
                prompt = cleanPrompt,
                currentUrl = startTabUrl,
                webViewProvider = { webViewRegistry[_uiState.value.activeTabId] },
                onNavigateUrl = { destUrl ->
                    submitNavigation(destUrl, _uiState.value.activeTabId)
                },
                onStepUpdate = { liveSteps ->
                    _uiState.update { state ->
                        state.copy(
                            nanoMessages = state.nanoMessages.map { msg ->
                                if (msg.id == agentMsgId) msg.copy(steps = liveSteps) else msg
                            }
                        )
                    }
                }
            )

            _uiState.update { state ->
                state.copy(
                    isNanoRunning = false,
                    nanoMessages = state.nanoMessages.map { msg ->
                        if (msg.id == agentMsgId) {
                            msg.copy(
                                text = outcome.finalSummary,
                                isRunning = false
                            )
                        } else {
                            msg
                        }
                    }
                )
            }
        }
    }

    // --- Extension 2: Retriever Local-First Web Scraper ---

    fun onRetrieverPromptChanged(value: String) {
        _uiState.update { it.copy(retrieverPromptInput = value) }
    }

    fun selectExtractionRule(ruleId: Long) {
        val rule = extractionRulesState.value.firstOrNull { it.id == ruleId }
        _uiState.update {
            it.copy(
                selectedRuleId = ruleId,
                retrieverPromptInput = rule?.promptDescription ?: it.retrieverPromptInput
            )
        }
    }

    fun setRetrieverDisplayMode(mode: RetrieverDisplayMode) {
        _uiState.update { it.copy(retrieverDisplayMode = mode) }
    }

    fun saveCurrentRetrieverRule() {
        val input = _uiState.value.retrieverPromptInput.trim()
        if (input.isEmpty()) return
        viewModelScope.launch {
            val built = retrieverEngine.buildRuleFromInput(input)
            val newId = repository.saveExtractionRule(built)
            _uiState.update {
                it.copy(
                    selectedRuleId = newId,
                    retrieverStatusBanner = "Saved rule '${built.name}' locally"
                )
            }
        }
    }

    fun deleteRetrieverRule(ruleId: Long) {
        viewModelScope.launch {
            repository.deleteExtractionRule(ruleId)
            if (_uiState.value.selectedRuleId == ruleId) {
                _uiState.update { it.copy(selectedRuleId = null) }
            }
        }
    }

    fun captureCurrentPageWithRetriever() {
        val state = _uiState.value
        if (state.isRetrieverRunning) return
        val rule = resolveActiveRule(state)
        val activeTab = state.activeTab

        _uiState.update {
            it.copy(
                isRetrieverRunning = true,
                retrieverStatusBanner = "Capturing DOM from '${activeTab.title}'…"
            )
        }

        viewModelScope.launch {
            val target = TabSnapshotTarget(
                tabId = activeTab.id,
                title = activeTab.title,
                url = activeTab.url
            )
            val records = retrieverEngine.extractFromSingleTab(
                target = target,
                webView = webViewRegistry[activeTab.id],
                rule = rule
            )
            if (rule.id != 0L) {
                repository.updateRuleRunCount(rule.id, records.size)
            }
            _uiState.update {
                it.copy(
                    isRetrieverRunning = false,
                    extractedRecords = records,
                    retrieverStatusBanner = "Captured ${records.size} structured rows from '${activeTab.title}'"
                )
            }
        }
    }

    fun runParallelMultiTabExtraction() {
        val state = _uiState.value
        if (state.isRetrieverRunning) return
        val rule = resolveActiveRule(state)
        val targets = state.tabs.map {
            TabSnapshotTarget(tabId = it.id, title = it.title, url = it.url)
        }

        _uiState.update {
            it.copy(
                isRetrieverRunning = true,
                retrieverStatusBanner = "Running parallel DOM extraction across ${targets.size} open tabs…"
            )
        }

        viewModelScope.launch {
            val records = retrieverEngine.extractAcrossTabsInParallel(
                tabs = targets,
                webViewLookup = { id -> webViewRegistry[id] },
                rule = rule
            )
            if (rule.id != 0L) {
                repository.updateRuleRunCount(rule.id, records.size)
            }
            _uiState.update {
                it.copy(
                    isRetrieverRunning = false,
                    extractedRecords = records,
                    retrieverStatusBanner = "Parallel run complete: ${records.size} rows across ${targets.size} tabs"
                )
            }
        }
    }

    private fun resolveActiveRule(state: BrowserUiState): ExtractionRuleEntity {
        val rules = extractionRulesState.value
        val byId = state.selectedRuleId?.let { id -> rules.firstOrNull { it.id == id } }
        if (byId != null && (state.retrieverPromptInput.isBlank() || state.retrieverPromptInput == byId.promptDescription)) {
            return byId
        }
        if (state.retrieverPromptInput.isNotBlank()) {
            return retrieverEngine.buildRuleFromInput(state.retrieverPromptInput)
        }
        return rules.firstOrNull() ?: retrieverEngine.buildRuleFromInput("Extract headlines, prices, and links")
    }

    private fun mapBaseUrlBackToScheme(loadedUrl: String, currentTabUrl: String): String {
        return when {
            loadedUrl.startsWith("https://news.apex.browser") -> "apex://news"
            loadedUrl.startsWith("https://store.apex.browser") -> "apex://store"
            currentTabUrl.startsWith("apex://") && loadedUrl == "about:blank" -> currentTabUrl
            else -> loadedUrl
        }
    }

    private fun deriveShortTitle(url: String): String {
        return when (url.lowercase()) {
            "apex://home" -> "Home"
            "apex://news" -> "AI News Pulse"
            "apex://store" -> "Apex Gear Store"
            else -> url.removePrefix("https://")
                .removePrefix("http://")
                .removePrefix("www.")
                .substringBefore('/')
                .ifBlank { "Web Tab" }
        }
    }

    private fun destroyWebViewSafely(wv: WebView, isIncognito: Boolean) {
        runCatching {
            if (isIncognito) {
                performanceOptimizer.wipeIncognitoWebView(wv)
            } else {
                wv.stopLoading()
            }
            wv.onPause()
            (wv.parent as? android.view.ViewGroup)?.removeView(wv)
            wv.removeAllViews()
            wv.destroy()
        }
    }

    override fun onCleared() {
        super.onCleared()
        suggestionJob?.cancel()
        recordingLifecycleJob?.cancel()
        val hadIncognito = _uiState.value.tabs.any { it.isPrivate }
        handleTabsClosedForRecording(_uiState.value.tabs.map { it.id }.toSet())
        _uiState.value.tabs.forEach { tab ->
            val wv = webViewRegistry.remove(tab.id)
            if (wv != null) {
                destroyWebViewSafely(wv, isIncognito = tab.isPrivate)
            }
        }
        webViewRegistry.values.forEach { destroyWebViewSafely(it, isIncognito = false) }
        webViewRegistry.clear()
        if (hadIncognito) {
            performanceOptimizer.wipeIncognitoSessionStorage()
        }
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val database = ApexDatabase.getInstance(appContext)
                    val prefsRepo = UserPreferencesRepository(appContext)
                    val sharedPrefs = appContext.getSharedPreferences(
                        "apex_browser_storage",
                        Context.MODE_PRIVATE
                    )
                    val repo = BrowserRepository(database.apexDao(), prefsRepo, sharedPrefs)
                    val downloadEngine = FastDownloadEngine.getInstance(appContext, repo)
                    return BrowserViewModel(
                        repository = repo,
                        fastDownloadEngine = downloadEngine
                    ) as T
                }
            }
        }
    }
}
