package com.example.ui.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FlutterDash
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Https
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.WebAssetOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.preferences.BrowserPreferences
import com.example.data.preferences.SearchEngineOption
import com.example.data.preferences.ThemeModeOption
import com.example.engine.DefaultBrowserStatus
import com.example.ui.theme.EmphasizedTypography
import com.example.ui.theme.SeedPalettePreset

private enum class SettingsSubPage {
    MAIN,
    SEARCH_ENGINES,
    PRIVACY_AND_SAFETY,
    APPEARANCE,
    BROWSING,
    ACCESSIBILITY,
    LANGUAGES,
    SELECT_LANGUAGE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenOverlay(
    preferences: BrowserPreferences,
    onSelectSearchEngine: (SearchEngineOption) -> Unit,
    onToggleShields: (Boolean) -> Unit,
    onToggleHttpsUpgrade: (Boolean) -> Unit,
    onToggleScriptGuard: (Boolean) -> Unit,
    onToggleSmartPreload: (Boolean) -> Unit,
    onToggleDesktopViewport: (Boolean) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onSelectSeedPalette: (SeedPalettePreset) -> Unit,
    onClearBrowsingData: () -> Unit,
    onSelectThemeMode: (ThemeModeOption) -> Unit = {},
    onToggleShowTopVisitsOnHome: (Boolean) -> Unit = {},
    onChangeDefaultZoom: (Int) -> Unit = {},
    onSetSiteZoom: (String, Int) -> Unit = { _, _ -> },
    onRemoveSiteZoom: (String) -> Unit = {},
    onClearSavedSiteZooms: () -> Unit = {},
    onToggleShowZoomInMainMenu: (Boolean) -> Unit = {},
    onToggleForceEnableZoom: (Boolean) -> Unit = {},
    onToggleSimplifiedView: (Boolean) -> Unit = {},
    onToggleCaretBrowsing: (Boolean) -> Unit = {},
    onToggleTouchpadSwipeNavigation: (Boolean) -> Unit = {},
    downloadedLanguageCodes: Set<String> = emptySet(),
    downloadingLanguageCodes: Set<String> = emptySet(),
    onSelectAppLanguage: (String) -> Unit = {},
    onAddPreferredLanguage: (String, Boolean) -> Unit = { _, _ -> },
    onRemovePreferredLanguage: (String) -> Unit = {},
    onMovePreferredLanguageUp: (String) -> Unit = {},
    onMovePreferredLanguageDown: (String) -> Unit = {},
    onMovePreferredLanguageToTop: (String) -> Unit = {},
    onToggleOfferToTranslate: (Boolean) -> Unit = {},
    onDownloadTranslationModel: (String) -> Unit = {},
    onDeleteTranslationModel: (String) -> Unit = {},
    onToggleWifiOnlyTranslationDownloads: (Boolean) -> Unit = {},
    onSelectTranslateTargetLanguage: (String) -> Unit = {},
    onToggleAutoTranslateLanguage: (String) -> Unit = {},
    onToggleNeverTranslateLanguage: (String) -> Unit = {},
    onOpenPasswordManager: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenAutomations: () -> Unit = {},
    defaultBrowserStatus: DefaultBrowserStatus = DefaultBrowserStatus(),
    onOpenDefaultBrowserSettings: () -> Unit = {},
    mainListState: LazyListState = rememberLazyListState(),
    backHandlerEnabled: Boolean = true,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentPage by rememberSaveable { mutableStateOf(SettingsSubPage.MAIN) }

    when (currentPage) {
        SettingsSubPage.SEARCH_ENGINES -> {
            SearchEnginesFullPage(
                selectedEngine = preferences.searchEngine,
                onSelectSearchEngine = onSelectSearchEngine,
                onNavigateBack = { currentPage = SettingsSubPage.MAIN },
                modifier = modifier
            )
            return
        }
        SettingsSubPage.PRIVACY_AND_SAFETY -> {
            PrivacySafetyFullPage(
                preferences = preferences,
                onToggleShields = onToggleShields,
                onToggleHttpsUpgrade = onToggleHttpsUpgrade,
                onToggleScriptGuard = onToggleScriptGuard,
                onClearBrowsingData = onClearBrowsingData,
                onNavigateBack = { currentPage = SettingsSubPage.MAIN },
                modifier = modifier
            )
            return
        }
        SettingsSubPage.APPEARANCE -> {
            AppearanceFullPage(
                preferences = preferences,
                onSelectThemeMode = onSelectThemeMode,
                onToggleDynamicColor = onToggleDynamicColor,
                onSelectSeedPalette = onSelectSeedPalette,
                onToggleShowTopVisitsOnHome = onToggleShowTopVisitsOnHome,
                onNavigateBack = { currentPage = SettingsSubPage.MAIN },
                modifier = modifier
            )
            return
        }
        SettingsSubPage.BROWSING -> {
            BrowsingFullPage(
                preferences = preferences,
                onToggleSmartPreload = onToggleSmartPreload,
                onToggleDesktopViewport = onToggleDesktopViewport,
                onNavigateBack = { currentPage = SettingsSubPage.MAIN },
                modifier = modifier
            )
            return
        }
        SettingsSubPage.ACCESSIBILITY -> {
            AccessibilityFullPage(
                preferences = preferences,
                onChangeDefaultZoom = onChangeDefaultZoom,
                onSetSiteZoom = onSetSiteZoom,
                onRemoveSiteZoom = onRemoveSiteZoom,
                onClearSavedSiteZooms = onClearSavedSiteZooms,
                onToggleShowZoomInMainMenu = onToggleShowZoomInMainMenu,
                onToggleForceEnableZoom = onToggleForceEnableZoom,
                onToggleSimplifiedView = onToggleSimplifiedView,
                onToggleCaretBrowsing = onToggleCaretBrowsing,
                onToggleTouchpadSwipeNavigation = onToggleTouchpadSwipeNavigation,
                onNavigateBack = { currentPage = SettingsSubPage.MAIN },
                modifier = modifier
            )
            return
        }
        SettingsSubPage.LANGUAGES -> {
            LanguagesFullPage(
                preferences = preferences,
                downloadedLanguageCodes = downloadedLanguageCodes,
                downloadingLanguageCodes = downloadingLanguageCodes,
                onSelectAppLanguage = onSelectAppLanguage,
                onAddPreferredLanguage = onAddPreferredLanguage,
                onRemovePreferredLanguage = onRemovePreferredLanguage,
                onMovePreferredLanguageUp = onMovePreferredLanguageUp,
                onMovePreferredLanguageDown = onMovePreferredLanguageDown,
                onMovePreferredLanguageToTop = onMovePreferredLanguageToTop,
                onToggleOfferToTranslate = onToggleOfferToTranslate,
                onDownloadTranslationModel = onDownloadTranslationModel,
                onDeleteTranslationModel = onDeleteTranslationModel,
                onToggleWifiOnlyDownloads = onToggleWifiOnlyTranslationDownloads,
                onSelectTranslateTargetLanguage = onSelectTranslateTargetLanguage,
                onToggleAutoTranslateLanguage = onToggleAutoTranslateLanguage,
                onToggleNeverTranslateLanguage = onToggleNeverTranslateLanguage,
                onOpenSelectLanguage = { currentPage = SettingsSubPage.SELECT_LANGUAGE },
                onNavigateBack = { currentPage = SettingsSubPage.MAIN },
                modifier = modifier
            )
            return
        }
        SettingsSubPage.SELECT_LANGUAGE -> {
            SelectLanguageFullPage(
                preferredLanguageTags = preferences.preferredLanguageTags,
                onSelectLanguage = { tag ->
                    onAddPreferredLanguage(tag, true)
                    onDownloadTranslationModel(tag)
                },
                onNavigateBack = { currentPage = SettingsSubPage.LANGUAGES },
                modifier = modifier
            )
            return
        }
        SettingsSubPage.MAIN -> Unit
    }

    BackHandler(enabled = backHandlerEnabled, onBack = onDismiss)

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back)
                        )
                    }
                },
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = EmphasizedTypography.headlineHero
                    )
                }
            )
        },
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .testTag("settings_screen")
    ) { innerPadding ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                state = mainListState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .testTag("settings_scroll_list")
            ) {
                // Group 1: Search Engine (selected engine as subheading) & Default Browser
                item(key = "group_defaults") {
                    val subtitleText = when {
                        defaultBrowserStatus.isApexDefault ->
                            stringResource(R.string.settings_default_browser_apex_selected)
                        defaultBrowserStatus.currentDefaultPackage != null ->
                            stringResource(
                                R.string.settings_default_browser_other_selected,
                                defaultBrowserStatus.currentDefaultName
                            )
                        else ->
                            stringResource(R.string.settings_default_browser_none_selected)
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(GroupedListSpacing),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        MaterialGroupListItem(
                            index = 0,
                            totalCount = 2,
                            title = stringResource(R.string.settings_search_engines_title),
                            subtitle = preferences.searchEngine.displayName,
                            role = Role.Button,
                            onClick = { currentPage = SettingsSubPage.SEARCH_ENGINES },
                            leadingContent = {
                                SearchEngineLogoBadge(
                                    engine = preferences.searchEngine,
                                    highlighted = true
                                )
                            },
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier.testTag("settings_search_engine_card")
                        )

                        MaterialGroupListItem(
                            index = 1,
                            totalCount = 2,
                            title = stringResource(R.string.settings_default_browser_title),
                            subtitle = subtitleText,
                            containerColor = if (defaultBrowserStatus.isApexDefault) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            border = if (defaultBrowserStatus.isApexDefault) {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                            } else {
                                null
                            },
                            role = Role.Button,
                            onClick = onOpenDefaultBrowserSettings,
                            leadingContent = {
                                SettingsIconBadge(
                                    icon = Icons.Outlined.Public,
                                    highlighted = defaultBrowserStatus.isApexDefault
                                )
                            },
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier.testTag("settings_default_browser_card")
                        )
                    }
                }

                // Group 2: Category items opening dedicated sub-screens & tools
                item(key = "group_categories") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(GroupedListSpacing),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SettingsCategoryItem(
                            index = 0,
                            totalCount = 9,
                            icon = Icons.Outlined.Security,
                            title = stringResource(R.string.settings_privacy_section),
                            onClick = { currentPage = SettingsSubPage.PRIVACY_AND_SAFETY },
                            tag = "settings_privacy_card"
                        )
                        SettingsCategoryItem(
                            index = 1,
                            totalCount = 9,
                            icon = Icons.Outlined.Key,
                            title = stringResource(R.string.menu_passwords),
                            onClick = onOpenPasswordManager,
                            tag = "settings_passwords_card"
                        )
                        SettingsCategoryItem(
                            index = 2,
                            totalCount = 9,
                            icon = Icons.Outlined.Download,
                            title = stringResource(R.string.menu_downloads),
                            onClick = onOpenDownloads,
                            tag = "settings_downloads_card"
                        )
                        SettingsCategoryItem(
                            index = 3,
                            totalCount = 9,
                            icon = Icons.Outlined.History,
                            title = stringResource(R.string.menu_history),
                            onClick = onOpenHistory,
                            tag = "settings_history_card"
                        )
                        SettingsCategoryItem(
                            index = 4,
                            totalCount = 9,
                            icon = Icons.Outlined.Palette,
                            title = stringResource(R.string.settings_appearance_section),
                            onClick = { currentPage = SettingsSubPage.APPEARANCE },
                            tag = "settings_appearance_card"
                        )
                        SettingsCategoryItem(
                            index = 5,
                            totalCount = 9,
                            icon = Icons.Outlined.AccessibilityNew,
                            title = stringResource(R.string.settings_accessibility_section),
                            onClick = { currentPage = SettingsSubPage.ACCESSIBILITY },
                            tag = "settings_accessibility_card"
                        )
                        SettingsCategoryItem(
                            index = 6,
                            totalCount = 9,
                            icon = Icons.Outlined.Translate,
                            title = stringResource(R.string.settings_languages_section),
                            onClick = { currentPage = SettingsSubPage.LANGUAGES },
                            tag = "settings_languages_card"
                        )
                        SettingsCategoryItem(
                            index = 7,
                            totalCount = 9,
                            icon = Icons.Outlined.FlutterDash,
                            title = stringResource(R.string.settings_automations_section),
                            onClick = onOpenAutomations,
                            tag = "settings_automations_card"
                        )
                        SettingsCategoryItem(
                            index = 8,
                            totalCount = 9,
                            icon = Icons.Outlined.Speed,
                            title = stringResource(R.string.settings_browsing_section),
                            onClick = { currentPage = SettingsSubPage.BROWSING },
                            tag = "settings_browsing_card"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCategoryItem(
    index: Int,
    totalCount: Int,
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    tag: String
) {
    MaterialGroupListItem(
        index = index,
        totalCount = totalCount,
        title = title,
        subtitle = null,
        role = Role.Button,
        onClick = onClick,
        leadingContent = {
            SettingsIconBadge(icon = icon)
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = Modifier.testTag(tag)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSubPageScaffold(
    title: String,
    screenTag: String,
    backButtonTag: String,
    listTag: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    BackHandler(onBack = onNavigateBack)

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag(backButtonTag)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back)
                        )
                    }
                },
                title = {
                    Text(
                        text = title,
                        style = EmphasizedTypography.headlineHero
                    )
                }
            )
        },
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .testTag(screenTag)
    ) { innerPadding ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .testTag(listTag),
                content = content
            )
        }
    }
}

@Composable
private fun PrivacySafetyFullPage(
    preferences: BrowserPreferences,
    onToggleShields: (Boolean) -> Unit,
    onToggleHttpsUpgrade: (Boolean) -> Unit,
    onToggleScriptGuard: (Boolean) -> Unit,
    onClearBrowsingData: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsSubPageScaffold(
        title = stringResource(R.string.settings_privacy_section),
        screenTag = "settings_privacy_screen",
        backButtonTag = "settings_privacy_back_button",
        listTag = "settings_privacy_list",
        onNavigateBack = onNavigateBack,
        modifier = modifier
    ) {
        item(key = "privacy_safety_items") {
            Column(
                verticalArrangement = Arrangement.spacedBy(GroupedListSpacing),
                modifier = Modifier.fillMaxWidth()
            ) {
                FormattedSettingsSwitchItem(
                    index = 0,
                    totalCount = 4,
                    icon = Icons.Outlined.Security,
                    title = stringResource(R.string.settings_shields_title),
                    checked = preferences.shieldsEnabled,
                    onCheckedChange = onToggleShields,
                    tag = "settings_switch_shields"
                )
                FormattedSettingsSwitchItem(
                    index = 1,
                    totalCount = 4,
                    icon = Icons.Outlined.Https,
                    title = stringResource(R.string.settings_https_title),
                    checked = preferences.httpsUpgradeEnabled,
                    onCheckedChange = onToggleHttpsUpgrade,
                    tag = "settings_switch_https"
                )
                FormattedSettingsSwitchItem(
                    index = 2,
                    totalCount = 4,
                    icon = Icons.Outlined.WebAssetOff,
                    title = stringResource(R.string.settings_script_guard_title),
                    checked = preferences.scriptGuardEnabled,
                    onCheckedChange = onToggleScriptGuard,
                    tag = "settings_switch_script_guard"
                )
                MaterialGroupListItem(
                    index = 3,
                    totalCount = 4,
                    title = stringResource(R.string.settings_clear_data_button),
                    subtitle = null,
                    role = Role.Button,
                    onClick = onClearBrowsingData,
                    leadingContent = {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.CleaningServices,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.testTag("settings_clear_data_button")
                )
            }
        }
    }
}

@Composable
private fun AppearanceFullPage(
    preferences: BrowserPreferences,
    onSelectThemeMode: (ThemeModeOption) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onSelectSeedPalette: (SeedPalettePreset) -> Unit,
    onToggleShowTopVisitsOnHome: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsSubPageScaffold(
        title = stringResource(R.string.settings_appearance_section),
        screenTag = "settings_appearance_screen",
        backButtonTag = "settings_appearance_back_button",
        listTag = "settings_appearance_list",
        onNavigateBack = onNavigateBack,
        modifier = modifier
    ) {
        item(key = "appearance_items") {
            Column(
                verticalArrangement = Arrangement.spacedBy(GroupedListSpacing),
                modifier = Modifier.fillMaxWidth()
            ) {
                val themeModeShape = groupedListItemShape(index = 0, totalCount = 4)
                Surface(
                    shape = themeModeShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(themeModeShape)
                        .testTag("settings_theme_mode_card")
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            val headerIcon = when (preferences.themeMode) {
                                ThemeModeOption.SYSTEM -> Icons.Outlined.BrightnessAuto
                                ThemeModeOption.LIGHT -> Icons.Outlined.LightMode
                                ThemeModeOption.DARK -> Icons.Outlined.DarkMode
                            }
                            SettingsIconBadge(icon = headerIcon, highlighted = true)
                            Text(
                                text = stringResource(R.string.settings_theme_mode_label),
                                style = EmphasizedTypography.activeTabTitle,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            ThemeModeOption.entries.forEach { mode ->
                                val isSelected = preferences.themeMode == mode
                                val modeIcon = when (mode) {
                                    ThemeModeOption.SYSTEM -> Icons.Outlined.BrightnessAuto
                                    ThemeModeOption.LIGHT -> Icons.Outlined.LightMode
                                    ThemeModeOption.DARK -> Icons.Outlined.DarkMode
                                }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onSelectThemeMode(mode) },
                                    label = { Text(mode.label) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = modeIcon,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.testTag("settings_theme_mode_${mode.id}")
                                )
                            }
                        }
                    }
                }

                FormattedSettingsSwitchItem(
                    index = 1,
                    totalCount = 4,
                    icon = Icons.Outlined.Palette,
                    title = stringResource(R.string.settings_dynamic_color_title),
                    checked = preferences.dynamicColorEnabled,
                    onCheckedChange = onToggleDynamicColor,
                    tag = "settings_switch_dynamic_color"
                )

                val paletteItemShape = groupedListItemShape(index = 2, totalCount = 4)
                Surface(
                    shape = paletteItemShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(paletteItemShape)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            SettingsIconBadge(icon = Icons.Outlined.ColorLens)
                            Text(
                                text = stringResource(R.string.settings_seed_palette_label),
                                style = EmphasizedTypography.activeTabTitle,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            SeedPalettePreset.entries.forEach { preset ->
                                val isSelected = !preferences.dynamicColorEnabled &&
                                    preferences.seedPreset == preset
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onSelectSeedPalette(preset) },
                                    label = { Text(preset.label) },
                                    leadingIcon = {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .background(preset.seedColor)
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Filled.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.surface,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.testTag("settings_palette_${preset.name}")
                                )
                            }
                        }
                    }
                }

                FormattedSettingsSwitchItem(
                    index = 3,
                    totalCount = 4,
                    icon = Icons.Outlined.Bookmarks,
                    title = stringResource(R.string.settings_top_visits_title),
                    checked = preferences.showTopVisitsOnHome,
                    onCheckedChange = onToggleShowTopVisitsOnHome,
                    tag = "settings_switch_top_visits"
                )
            }
        }
    }
}

@Composable
private fun BrowsingFullPage(
    preferences: BrowserPreferences,
    onToggleSmartPreload: (Boolean) -> Unit,
    onToggleDesktopViewport: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsSubPageScaffold(
        title = stringResource(R.string.settings_browsing_section),
        screenTag = "settings_browsing_screen",
        backButtonTag = "settings_browsing_back_button",
        listTag = "settings_browsing_list",
        onNavigateBack = onNavigateBack,
        modifier = modifier
    ) {
        item(key = "browsing_items") {
            Column(
                verticalArrangement = Arrangement.spacedBy(GroupedListSpacing),
                modifier = Modifier.fillMaxWidth()
            ) {
                FormattedSettingsSwitchItem(
                    index = 0,
                    totalCount = 2,
                    icon = Icons.Outlined.Speed,
                    title = stringResource(R.string.settings_preload_title),
                    checked = preferences.aggressivePreloadEnabled,
                    onCheckedChange = onToggleSmartPreload,
                    tag = "settings_switch_preload"
                )
                FormattedSettingsSwitchItem(
                    index = 1,
                    totalCount = 2,
                    icon = Icons.Outlined.DesktopWindows,
                    title = stringResource(R.string.settings_desktop_title),
                    checked = preferences.desktopViewportEnabled,
                    onCheckedChange = onToggleDesktopViewport,
                    tag = "settings_switch_desktop"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchEnginesFullPage(
    selectedEngine: SearchEngineOption,
    onSelectSearchEngine: (SearchEngineOption) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onNavigateBack)

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val engines = SearchEngineOption.entries

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("settings_search_engines_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back)
                        )
                    }
                },
                title = {
                    Text(
                        text = stringResource(R.string.settings_search_engines_title),
                        style = EmphasizedTypography.headlineHero
                    )
                }
            )
        },
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .testTag("settings_search_engines_screen")
    ) { innerPadding ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .testTag("settings_search_engines_list")
            ) {
                item(key = "search_engines_grouped_items") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(GroupedListSpacing),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        engines.forEachIndexed { index, engine ->
                            val selected = engine == selectedEngine
                            MaterialGroupListItem(
                                index = index,
                                totalCount = engines.size,
                                title = engine.displayName,
                                subtitle = null,
                                containerColor = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                },
                                border = if (selected) {
                                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                } else {
                                    null
                                },
                                role = Role.RadioButton,
                                onClick = { onSelectSearchEngine(engine) },
                                leadingContent = {
                                    SearchEngineLogoBadge(
                                        engine = engine,
                                        highlighted = selected
                                    )
                                },
                                trailingContent = {
                                    RadioButton(
                                        selected = selected,
                                        onClick = null
                                    )
                                },
                                modifier = Modifier.testTag("settings_engine_option_${engine.id}")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchEngineLogoBadge(
    engine: SearchEngineOption,
    highlighted: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            SearchEngineLogo(
                engine = engine,
                size = 24.dp
            )
        }
    }
}

@Composable
internal fun SettingsIconBadge(
    icon: ImageVector,
    highlighted: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (highlighted) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun FormattedSettingsSwitchItem(
    index: Int,
    totalCount: Int,
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String,
    description: String? = null
) {
    MaterialGroupListItem(
        index = index,
        totalCount = totalCount,
        title = title,
        subtitle = description,
        role = Role.Switch,
        onClick = { onCheckedChange(!checked) },
        leadingContent = {
            SettingsIconBadge(icon = icon, highlighted = checked)
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null
            )
        },
        modifier = Modifier.testTag(tag)
    )
}
