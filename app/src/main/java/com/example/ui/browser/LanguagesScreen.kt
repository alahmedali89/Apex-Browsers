package com.example.ui.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.preferences.BrowserPreferences
import com.example.engine.LanguageCatalog
import com.example.engine.LanguageInfo
import com.example.ui.theme.EmphasizedTypography

private val LangGroupedListSpacing = 4.dp

private fun langGroupedListItemShape(index: Int, totalCount: Int): RoundedCornerShape {
    return when {
        totalCount == 1 -> RoundedCornerShape(20.dp)
        index == 0 -> RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
        index == totalCount - 1 -> RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
        else -> RoundedCornerShape(6.dp)
    }
}

@Composable
private fun LangSettingsIconBadge(
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
private fun LangFormattedSettingsSwitchItem(
    index: Int,
    totalCount: Int,
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String,
    description: String? = null
) {
    val shape = langGroupedListItemShape(index, totalCount)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .testTag(tag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            LangSettingsIconBadge(icon = icon, highlighted = checked)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = EmphasizedTypography.activeTabTitle,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!description.isNullOrBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = null,
                modifier = if (tag == "settings_switch_wifi_only_downloads") Modifier.testTag("languages_wifi_only_switch") else Modifier
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectLanguageFullPage(
    preferredLanguageTags: List<String>,
    onSelectLanguage: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onNavigateBack)
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val allLangs = LanguageCatalog.supportedLanguages
    val filteredLangs = allLangs.filter {
        searchQuery.isBlank() ||
            it.displayName.contains(searchQuery, ignoreCase = true) ||
            it.nativeName.contains(searchQuery, ignoreCase = true) ||
            it.code.contains(searchQuery, ignoreCase = true)
    }

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
                            .testTag("select_language_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back)
                        )
                    }
                },
                title = {
                    Text(
                        text = "Select language",
                        style = EmphasizedTypography.headlineHero
                    )
                },
                actions = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            )
        },
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .testTag("select_language_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.languages_add_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("languages_select_search_input")
            )

            LazyColumn(
                contentPadding = PaddingValues(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                itemsIndexed(filteredLangs) { index, lang ->
                    val isAdded = preferredLanguageTags.contains(lang.code)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        onClick = {
                            onSelectLanguage(lang.code)
                            onNavigateBack()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("select_lang_item_${lang.code}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = lang.displayName,
                                    style = EmphasizedTypography.activeTabTitle,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = lang.nativeName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isAdded) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagesFullPage(
    preferences: BrowserPreferences,
    downloadedLanguageCodes: Set<String>,
    downloadingLanguageCodes: Set<String>,
    onSelectAppLanguage: (String) -> Unit,
    onAddPreferredLanguage: (String, Boolean) -> Unit,
    onRemovePreferredLanguage: (String) -> Unit,
    onMovePreferredLanguageUp: (String) -> Unit,
    onMovePreferredLanguageDown: (String) -> Unit,
    onMovePreferredLanguageToTop: (String) -> Unit,
    onToggleOfferToTranslate: (Boolean) -> Unit,
    onDownloadTranslationModel: (String) -> Unit,
    onDeleteTranslationModel: (String) -> Unit,
    onToggleWifiOnlyDownloads: (Boolean) -> Unit,
    onSelectTranslateTargetLanguage: (String) -> Unit,
    onToggleAutoTranslateLanguage: (String) -> Unit,
    onToggleNeverTranslateLanguage: (String) -> Unit,
    onOpenSelectLanguage: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onNavigateBack)

    var languageToManageMenu by remember { mutableStateOf<String?>(null) }
    var showOfflineDownloadDialog by rememberSaveable { mutableStateOf(false) }
    var downloadSearchQuery by rememberSaveable { mutableStateOf("") }
    var downloadFilter by rememberSaveable { mutableStateOf("all") }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val allLangs = LanguageCatalog.supportedLanguages
    val preferredTags = preferences.preferredLanguageTags.ifEmpty { LanguageCatalog.defaultPreferredLanguageTags() }

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
                            .testTag("settings_languages_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back)
                        )
                    }
                },
                title = {
                    Text(
                        text = stringResource(R.string.settings_languages_section),
                        style = EmphasizedTypography.headlineHero
                    )
                }
            )
        },
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .testTag("settings_languages_screen")
    ) { innerPadding ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .testTag("settings_languages_list")
            ) {
                // Downloading model status banner card if any model is downloading (per Screenshot 1 style)
                if (downloadingLanguageCodes.isNotEmpty()) {
                    item(key = "downloading_status_banner") {
                        val activeCode = downloadingLanguageCodes.first()
                        val activeInfo = LanguageCatalog.describeLanguage(activeCode)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("language_downloading_banner")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    LangSettingsIconBadge(icon = Icons.Filled.CloudDownload, highlighted = true)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${activeInfo.displayName}",
                                            style = EmphasizedTypography.activeTabTitle,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${activeInfo.nativeName} - Downloading...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                // App's language section
                item(key = "app_language_section") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.languages_app_language_section),
                            style = EmphasizedTypography.activeTabTitle,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val appLangInfo = LanguageCatalog.describeLanguage(preferences.appLanguageTag)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_app_language_card")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                LangSettingsIconBadge(icon = Icons.Outlined.Language, highlighted = true)
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = appLangInfo.displayName,
                                        style = EmphasizedTypography.activeTabTitle,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = appLangInfo.nativeName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(
                                    onClick = onOpenSelectLanguage,
                                    modifier = Modifier.testTag("change_app_language_btn")
                                ) {
                                    Text(stringResource(R.string.languages_add_language))
                                }
                            }
                        }
                    }
                }

                // Preferred languages section
                item(key = "preferred_languages_section") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.languages_preferred_section),
                            style = EmphasizedTypography.activeTabTitle,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.languages_preferred_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        Column(
                            verticalArrangement = Arrangement.spacedBy(LangGroupedListSpacing),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            preferredTags.forEachIndexed { index, tag ->
                                val info = LanguageCatalog.describeLanguage(tag)
                                val shape = langGroupedListItemShape(index, preferredTags.size + 1)
                                Surface(
                                    shape = shape,
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(shape)
                                        .testTag("preferred_lang_item_$tag")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp)
                                            .testTag("preferred_lang_row_$tag")
                                    ) {
                                        IconButton(
                                            onClick = { onMovePreferredLanguageToTop(tag) },
                                            modifier = Modifier
                                                .size(24.dp)
                                                .testTag("preferred_lang_drag_$tag")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.DragHandle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = info.displayName,
                                                style = EmphasizedTypography.activeTabTitle,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (info.nativeName != info.displayName) {
                                                Text(
                                                    text = info.nativeName,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Box {
                                            IconButton(
                                                onClick = { languageToManageMenu = tag },
                                                modifier = Modifier.testTag("preferred_lang_menu_$tag")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.MoreVert,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = languageToManageMenu == tag,
                                                onDismissRequest = { languageToManageMenu = null }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.languages_action_move_top)) },
                                                    onClick = {
                                                        onMovePreferredLanguageToTop(tag)
                                                        languageToManageMenu = null
                                                    }
                                                )
                                                if (index > 0) {
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(R.string.languages_action_move_up)) },
                                                        onClick = {
                                                            onMovePreferredLanguageUp(tag)
                                                            languageToManageMenu = null
                                                        }
                                                    )
                                                }
                                                if (index < preferredTags.size - 1) {
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(R.string.languages_action_move_down)) },
                                                        onClick = {
                                                            onMovePreferredLanguageDown(tag)
                                                            languageToManageMenu = null
                                                        }
                                                    )
                                                }
                                                if (preferredTags.size > 1) {
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(R.string.languages_action_remove)) },
                                                        onClick = {
                                                            onRemovePreferredLanguage(tag)
                                                            languageToManageMenu = null
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Add language action card -> navigates to SelectLanguageFullPage
                            val addShape = langGroupedListItemShape(preferredTags.size, preferredTags.size + 1)
                            Surface(
                                shape = addShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                                onClick = onOpenSelectLanguage,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(addShape)
                                    .testTag("languages_add_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Filled.Add,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = stringResource(R.string.languages_add_language),
                                        style = EmphasizedTypography.activeTabTitle,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                // Translation settings & offline models section
                item(key = "translation_settings_section") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.languages_translation_section),
                            style = EmphasizedTypography.activeTabTitle,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        Column(
                            verticalArrangement = Arrangement.spacedBy(LangGroupedListSpacing),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            LangFormattedSettingsSwitchItem(
                                index = 0,
                                totalCount = 5,
                                icon = Icons.Outlined.Translate,
                                title = stringResource(R.string.languages_offer_translate_title),
                                checked = preferences.offerToTranslateEnabled,
                                onCheckedChange = onToggleOfferToTranslate,
                                tag = "settings_switch_offer_translate"
                            )

                            // Offline models manager card
                            val offlineShape = langGroupedListItemShape(1, 5)
                            Surface(
                                shape = offlineShape,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                onClick = { showOfflineDownloadDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(offlineShape)
                                    .testTag("settings_download_languages_card")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp)
                                ) {
                                    LangSettingsIconBadge(icon = Icons.Filled.CloudDownload)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.languages_offline_models_title),
                                            style = EmphasizedTypography.activeTabTitle,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = stringResource(
                                                R.string.languages_offline_models_one,
                                                downloadedLanguageCodes.size
                                            ),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Translate target card
                            val targetShape = langGroupedListItemShape(2, 5)
                            Surface(
                                shape = targetShape,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                onClick = { },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(targetShape)
                                    .testTag("settings_translate_target_card")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp)
                                ) {
                                    LangSettingsIconBadge(icon = Icons.Outlined.Translate)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.languages_translate_into_title),
                                            style = EmphasizedTypography.activeTabTitle,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = LanguageCatalog.describeLanguage(preferences.translateTargetLanguageCode).displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Auto translate card
                            val autoShape = langGroupedListItemShape(3, 5)
                            Surface(
                                shape = autoShape,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                onClick = { },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(autoShape)
                                    .testTag("settings_auto_translate_card")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp)
                                ) {
                                    LangSettingsIconBadge(icon = Icons.Outlined.Translate)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.languages_auto_translate_title),
                                            style = EmphasizedTypography.activeTabTitle,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (preferences.autoTranslateLanguageCodes.isEmpty()) stringResource(R.string.languages_none_selected)
                                            else preferences.autoTranslateLanguageCodes.joinToString(", "),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Never translate card
                            val neverShape = langGroupedListItemShape(4, 5)
                            Surface(
                                shape = neverShape,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                onClick = { },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(neverShape)
                                    .testTag("settings_never_translate_card")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp)
                                ) {
                                    LangSettingsIconBadge(icon = Icons.Outlined.Translate)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.languages_never_translate_title),
                                            style = EmphasizedTypography.activeTabTitle,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (preferences.neverTranslateLanguageCodes.isEmpty()) stringResource(R.string.languages_none_selected)
                                            else preferences.neverTranslateLanguageCodes.joinToString(", "),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Offline Translation Models Download Dialog
    if (showOfflineDownloadDialog) {
        AlertDialog(
            onDismissRequest = { showOfflineDownloadDialog = false },
            title = { Text(stringResource(R.string.languages_offline_models_title)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("languages_download_sheet")
                ) {
                    OutlinedTextField(
                        value = downloadSearchQuery,
                        onValueChange = { downloadSearchQuery = it },
                        placeholder = { Text(stringResource(R.string.languages_add_search_hint)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("languages_download_search_input")
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = downloadFilter == "all",
                            onClick = { downloadFilter = "all" },
                            label = { Text(stringResource(R.string.languages_filter_all)) }
                        )
                        FilterChip(
                            selected = downloadFilter == "downloaded",
                            onClick = { downloadFilter = "downloaded" },
                            label = { Text(stringResource(R.string.languages_filter_downloaded)) },
                            modifier = Modifier.testTag("languages_filter_downloaded")
                        )
                        FilterChip(
                            selected = downloadFilter == "available",
                            onClick = { downloadFilter = "available" },
                            label = { Text(stringResource(R.string.languages_filter_available)) },
                            modifier = Modifier.testTag("languages_filter_available")
                        )
                    }

                    val filteredLangs = allLangs.filter { lang ->
                        val matchesSearch = downloadSearchQuery.isBlank() ||
                            lang.displayName.contains(downloadSearchQuery, ignoreCase = true) ||
                            lang.code.contains(downloadSearchQuery, ignoreCase = true)
                        val isDownloaded = downloadedLanguageCodes.contains(lang.code)
                        val matchesFilter = when (downloadFilter) {
                            "downloaded" -> isDownloaded
                            "available" -> !isDownloaded
                            else -> true
                        }
                        matchesSearch && matchesFilter
                    }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                    ) {
                        itemsIndexed(filteredLangs) { _, lang ->
                            val isDownloaded = downloadedLanguageCodes.contains(lang.code)
                            val isDownloading = downloadingLanguageCodes.contains(lang.code)

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = lang.displayName,
                                            style = EmphasizedTypography.activeTabTitle,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (isDownloaded) stringResource(R.string.languages_downloaded_badge)
                                            else if (isDownloading) stringResource(R.string.languages_action_downloading)
                                            else stringResource(R.string.languages_offline_models_subtitle_none),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isDownloaded) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (isDownloaded) {
                                        IconButton(
                                            onClick = { onDeleteTranslationModel(lang.code) },
                                            modifier = Modifier.testTag("delete_lang_btn_${lang.code}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Delete,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    } else {
                                        Button(
                                            onClick = { onDownloadTranslationModel(lang.code) },
                                            enabled = !isDownloading,
                                            modifier = Modifier.testTag("download_lang_btn_${lang.code}")
                                        ) {
                                            Text(
                                                if (isDownloading) stringResource(R.string.languages_action_downloading)
                                                else stringResource(R.string.languages_action_download)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showOfflineDownloadDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }
}
