package com.example.ui.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FlutterDash
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.automation.AutomationToolbarStatus
import com.example.ui.theme.ApexMotion
import com.example.ui.theme.EmphasizedTypography
import com.example.ui.theme.LocalReducedMotion
import com.example.ui.theme.rememberMorphingCornerShape
import com.example.ui.viewmodel.BrowserTab
import com.example.ui.viewmodel.NetworkStatusBannerState
import java.net.URI

private val OfflineStatusBarBlack = Color(0xFF000000)
private val BackOnlineStatusBarBlue = Color(0xFF1967D2)

/**
 * Formats a web URL into a clean host display string (e.g. "github.com") for the address bar,
 * matching the clean address bar presentation in the user's screenshots.
 */
fun formatCleanAddressBarUrl(rawUrl: String): String {
    val trimmed = rawUrl.trim()
    if (trimmed.isEmpty() || trimmed.startsWith("apex://", ignoreCase = true)) {
        return trimmed
    }
    val parsedHost = runCatching {
        URI(trimmed).host?.removePrefix("www.")?.takeIf { it.isNotBlank() }
    }.getOrNull()
    if (parsedHost != null) {
        return parsedHost
    }
    return trimmed
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("www.")
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
        .ifBlank { trimmed }
}

/**
 * Status bar connectivity indicator modeled directly after the user's screenshots:
 * - Offline: Solid Black (#000000) status bar + slim row with CloudOff icon and "No internet connection"
 * - Back online: Blue (#1967D2) status bar + slim row with Globe icon and "Back online"
 * - Hidden: Normal surface status bar inset without the connectivity row
 */
@Composable
fun StatusBarConnectivityHeader(
    networkBannerState: NetworkStatusBannerState,
    defaultContainerColor: Color = MaterialTheme.colorScheme.surface,
    modifier: Modifier = Modifier
) {
    val reducedMotion = LocalReducedMotion.current
    val isBannerVisible = networkBannerState != NetworkStatusBannerState.HIDDEN
    val targetBgColor = when (networkBannerState) {
        NetworkStatusBannerState.OFFLINE -> OfflineStatusBarBlack
        NetworkStatusBannerState.BACK_ONLINE -> BackOnlineStatusBarBlue
        NetworkStatusBannerState.HIDDEN -> defaultContainerColor
    }
    val animatedBgColor by animateColorAsState(
        targetValue = targetBgColor,
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 220),
        label = "StatusBarConnectivityBgColor"
    )

    var lastVisibleState by remember {
        mutableStateOf(
            if (networkBannerState != NetworkStatusBannerState.HIDDEN) {
                networkBannerState
            } else {
                NetworkStatusBannerState.OFFLINE
            }
        )
    }
    if (networkBannerState != NetworkStatusBannerState.HIDDEN) {
        lastVisibleState = networkBannerState
    }

    Surface(
        color = animatedBgColor,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
            )
            AnimatedVisibility(
                visible = isBannerVisible,
                enter = if (reducedMotion) {
                    fadeIn()
                } else {
                    expandVertically(animationSpec = tween(200)) + fadeIn(animationSpec = tween(180))
                },
                exit = if (reducedMotion) {
                    fadeOut()
                } else {
                    shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(160))
                }
            ) {
                val activeBannerState = if (networkBannerState != NetworkStatusBannerState.HIDDEN) {
                    networkBannerState
                } else {
                    lastVisibleState
                }
                val isOffline = activeBannerState == NetworkStatusBannerState.OFFLINE
                val specificTag = if (isOffline) {
                    "status_bar_offline_banner"
                } else {
                    "status_bar_online_banner"
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .testTag("status_bar_connectivity_banner")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .testTag(specificTag)
                    ) {
                        Icon(
                            imageVector = if (isOffline) {
                                Icons.Outlined.CloudOff
                            } else {
                                Icons.Outlined.Public
                            },
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(
                                if (isOffline) {
                                    R.string.status_no_internet_connection
                                } else {
                                    R.string.status_back_online
                                }
                            ),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Custom vector site-settings / tune sliders icon (two horizontal lines with opposing circles)
 * matching the left address bar icon in the user's online screenshot.
 */
@Composable
fun PageTuneSettingsIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.105f
        val radius = size.minDimension * 0.145f
        val padX = size.width * 0.12f
        val topY = size.height * 0.33f
        val bottomY = size.height * 0.67f
        val topKnobX = size.width * 0.34f
        val bottomKnobX = size.width * 0.66f

        // Top line segments around top knob
        if (topKnobX - radius > padX) {
            drawLine(
                color = tint,
                start = Offset(padX, topY),
                end = Offset(topKnobX - radius, topY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
        drawLine(
            color = tint,
            start = Offset(topKnobX + radius, topY),
            end = Offset(size.width - padX, topY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = tint,
            radius = radius,
            center = Offset(topKnobX, topY),
            style = Stroke(width = strokeWidth)
        )

        // Bottom line segments around bottom knob
        drawLine(
            color = tint,
            start = Offset(padX, bottomY),
            end = Offset(bottomKnobX - radius, bottomY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        if (bottomKnobX + radius < size.width - padX) {
            drawLine(
                color = tint,
                start = Offset(bottomKnobX + radius, bottomY),
                end = Offset(size.width - padX, bottomY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
        drawCircle(
            color = tint,
            radius = radius,
            center = Offset(bottomKnobX, bottomY),
            style = Stroke(width = strokeWidth)
        )
    }
}

/**
 * Clean Material 3 Top Toolbar with integrated Status Bar Offline/Online indicator:
 * - On Home screen: Displays the clean borderless search bar with selected search engine logo.
 * - On Web pages: Displays the clean address bar pill with Info (offline/HTTP) or Page Tune (online HTTPS) icon and domain.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactTopToolbar(
    visible: Boolean,
    activeTab: BrowserTab,
    activeTabIndex: Int = 0,
    totalTabs: Int = 1,
    onAddressPillClick: () -> Unit,
    onReloadClick: () -> Unit,
    onSwipeToNextTab: () -> Unit = {},
    onSwipeToPreviousTab: () -> Unit = {},
    searchEngine: com.example.data.preferences.SearchEngineOption = com.example.data.preferences.SearchEngineOption.BRAVE,
    isOnline: Boolean = true,
    networkBannerState: NetworkStatusBannerState = NetworkStatusBannerState.HIDDEN,
    automationStatus: AutomationToolbarStatus = AutomationToolbarStatus.IDLE,
    detectedElementsCount: Int = 0,
    onAutomationClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val reducedMotion = LocalReducedMotion.current
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current

    val currentTabIndex by rememberUpdatedState(activeTabIndex)
    val currentTotalTabs by rememberUpdatedState(totalTabs)
    val currentOnNext by rememberUpdatedState(onSwipeToNextTab)
    val currentOnPrev by rememberUpdatedState(onSwipeToPreviousTab)

    val switchThresholdPx = remember(density) { with(density) { 52.dp.toPx() } }
    val flickMinDistancePx = remember(density) { with(density) { 20.dp.toPx() } }
    val maxVisualOffsetPx = remember(density) { with(density) { 76.dp.toPx() } }
    val boundaryMaxOffsetPx = remember(density) { with(density) { 18.dp.toPx() } }

    var rawDragOffsetPx by remember { mutableFloatStateOf(0f) }
    var isDraggingBar by remember { mutableStateOf(false) }

    LaunchedEffect(activeTab.id) {
        if (!isDraggingBar) {
            rawDragOffsetPx = 0f
        }
    }

    val animatedTranslationX by animateFloatAsState(
        targetValue = if (isDraggingBar) rawDragOffsetPx else 0f,
        animationSpec = if (isDraggingBar || reducedMotion) {
            snap()
        } else {
            spring(dampingRatio = 0.82f, stiffness = 700f)
        },
        label = "SearchBarSwipeTranslation"
    )

    val canGoNext = activeTabIndex < totalTabs - 1
    val canGoPrev = activeTabIndex > 0

    val swipeModifier = Modifier.pointerInput(switchThresholdPx) {
        val velocityTracker = VelocityTracker()
        var totalDrag = 0f
        var crossedHaptic = false
        detectHorizontalDragGestures(
            onDragStart = {
                velocityTracker.resetTracking()
                totalDrag = 0f
                crossedHaptic = false
                isDraggingBar = true
            },
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                totalDrag += dragAmount

                val hasNext = currentTabIndex < currentTotalTabs - 1
                val hasPrev = currentTabIndex > 0
                val canMoveInDir = (totalDrag < 0f && hasNext) || (totalDrag > 0f && hasPrev)

                rawDragOffsetPx = if (canMoveInDir) {
                    (totalDrag * 0.65f).coerceIn(-maxVisualOffsetPx, maxVisualOffsetPx)
                } else {
                    (totalDrag * 0.2f).coerceIn(-boundaryMaxOffsetPx, boundaryMaxOffsetPx)
                }

                val nowCrossed = canMoveInDir && kotlin.math.abs(totalDrag) >= switchThresholdPx
                if (nowCrossed && !crossedHaptic) {
                    crossedHaptic = true
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                } else if (!nowCrossed) {
                    crossedHaptic = false
                }
            },
            onDragCancel = {
                isDraggingBar = false
                rawDragOffsetPx = 0f
            },
            onDragEnd = {
                val velocityX = velocityTracker.calculateVelocity().x
                val hasNext = currentTabIndex < currentTotalTabs - 1
                val hasPrev = currentTabIndex > 0

                val goNext = hasNext && (
                    totalDrag <= -switchThresholdPx ||
                        (totalDrag <= -flickMinDistancePx && velocityX <= -480f)
                    )
                val goPrev = hasPrev && (
                    totalDrag >= switchThresholdPx ||
                        (totalDrag >= flickMinDistancePx && velocityX >= 480f)
                    )

                isDraggingBar = false
                rawDragOffsetPx = 0f

                when {
                    goNext -> currentOnNext()
                    goPrev -> currentOnPrev()
                }
            }
        )
    }

    val enterTransition = if (reducedMotion) {
        fadeIn()
    } else {
        expandVertically(animationSpec = ApexMotion.spatialSpring()) +
            fadeIn(animationSpec = ApexMotion.effectsSpring())
    }
    val exitTransition = if (reducedMotion) {
        fadeOut()
    } else {
        shrinkVertically(animationSpec = ApexMotion.spatialSpring()) +
            fadeOut(animationSpec = ApexMotion.effectsSpring())
    }

    AnimatedVisibility(
        visible = visible,
        enter = enterTransition,
        exit = exitTransition,
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Status bar + "No internet connection" (Black) / "Back online" (Blue) strip
            StatusBarConnectivityHeader(
                networkBannerState = networkBannerState,
                defaultContainerColor = MaterialTheme.colorScheme.surface
            )

            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("material_top_app_bar")
                ) {
                    val dragProgress = (kotlin.math.abs(animatedTranslationX) / (switchThresholdPx * 0.65f))
                        .coerceIn(0f, 1f)
                    val swipingLeft = animatedTranslationX < -4f
                    val swipingRight = animatedTranslationX > 4f
                    val readyNext = swipingLeft && canGoNext && dragProgress >= 0.95f
                    val readyPrev = swipingRight && canGoPrev && dragProgress >= 0.95f

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .then(swipeModifier)
                    ) {
                        // Subtle edge directional tab-switch indicator behind the sliding pill
                        if (swipingLeft && canGoNext) {
                            Surface(
                                shape = CircleShape,
                                color = if (readyNext) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                },
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 4.dp)
                                    .size(32.dp)
                                    .alpha(dragProgress)
                                    .testTag("search_swipe_next_indicator")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = stringResource(R.string.cd_swipe_next_tab),
                                        tint = if (readyNext) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        } else if (swipingRight && canGoPrev) {
                            Surface(
                                shape = CircleShape,
                                color = if (readyPrev) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                },
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 4.dp)
                                    .size(32.dp)
                                    .alpha(dragProgress)
                                    .testTag("search_swipe_prev_indicator")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                        contentDescription = stringResource(R.string.cd_swipe_previous_tab),
                                        tint = if (readyPrev) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                if (activeTab.isHomePage) {
                                    val homeBarShape = RoundedCornerShape(16.dp)
                                    Surface(
                                        shape = homeBarShape,
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        border = if (activeTab.isPrivate) {
                                            BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)
                                        } else {
                                            null
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                            .graphicsLayer { translationX = animatedTranslationX }
                                            .clip(homeBarShape)
                                            .clickable(onClick = onAddressPillClick)
                                            .testTag("home_top_search_bar")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 14.dp)
                                        ) {
                                            SearchEngineLogo(
                                                engine = searchEngine,
                                                size = 22.dp
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = stringResource(R.string.home_search_placeholder_clean),
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (!isOnline || networkBannerState == NetworkStatusBannerState.OFFLINE) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Icon(
                                                    imageVector = Icons.Outlined.CloudOff,
                                                    contentDescription = stringResource(R.string.status_no_internet_connection),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            if (activeTab.isPrivate) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                IncognitoToolbarBadge()
                                            }
                                        }
                                    }
                                } else {
                                    val addressBarShape = RoundedCornerShape(16.dp)
                                    val showOfflineOrInfoIcon = !isOnline ||
                                        networkBannerState == NetworkStatusBannerState.OFFLINE ||
                                        !activeTab.isHttps
                                    Surface(
                                        shape = addressBarShape,
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        border = if (activeTab.isPrivate) {
                                            BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)
                                        } else {
                                            null
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                            .graphicsLayer { translationX = animatedTranslationX }
                                            .clip(addressBarShape)
                                            .clickable(onClick = onAddressPillClick)
                                            .testTag("address_bar_container")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 14.dp)
                                        ) {
                                            if (showOfflineOrInfoIcon) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Info,
                                                    contentDescription = stringResource(R.string.cd_page_info),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .testTag("address_bar_info_icon")
                                                )
                                            } else {
                                                PageTuneSettingsIcon(
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .testTag("address_bar_tune_icon")
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(10.dp))

                                            Text(
                                                text = formatCleanAddressBarUrl(activeTab.url),
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (activeTab.isPrivate) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                IncognitoToolbarBadge()
                                            }
                                        }
                                    }
                                }
                            }

                            AutomationToolbarIconButton(
                                status = automationStatus,
                                detectedElementsCount = detectedElementsCount,
                                onClick = onAutomationClick
                            )
                        }
                    }
                }

                if (activeTab.isLoading && activeTab.progress < 1f) {
                    val animatedProgress = androidx.compose.animation.core.animateFloatAsState(
                        targetValue = activeTab.progress.coerceIn(0.04f, 1f),
                        label = "WebPageLoadingProgress"
                    )
                    LinearProgressIndicator(
                        progress = { animatedProgress.value },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp)
                            .testTag("page_loading_indicator")
                    )
                }
            }
        }
    }
}

@Composable
fun AutomationToolbarIconButton(
    status: AutomationToolbarStatus,
    detectedElementsCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reducedMotion = LocalReducedMotion.current
    val pulseTransition = rememberInfiniteTransition(label = "AutomationToolbarPulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (!reducedMotion && (status == AutomationToolbarStatus.RECORDING || status == AutomationToolbarStatus.EXECUTING)) {
            0.45f
        } else {
            1f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AutomationPulseAlpha"
    )

    val containerColor by animateColorAsState(
        targetValue = when (status) {
            AutomationToolbarStatus.IDLE -> MaterialTheme.colorScheme.surfaceContainerHigh
            AutomationToolbarStatus.ELEMENTS_DETECTED -> MaterialTheme.colorScheme.surfaceContainerHigh
            AutomationToolbarStatus.RECORDING -> MaterialTheme.colorScheme.errorContainer
            AutomationToolbarStatus.PAUSED -> MaterialTheme.colorScheme.tertiaryContainer
            AutomationToolbarStatus.EXECUTING -> MaterialTheme.colorScheme.primaryContainer
            AutomationToolbarStatus.COMPLETED -> MaterialTheme.colorScheme.primaryContainer
            AutomationToolbarStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
        },
        animationSpec = if (reducedMotion) snap() else ApexMotion.effectsSpring(),
        label = "AutomationButtonContainerColor"
    )

    val iconTint by animateColorAsState(
        targetValue = when (status) {
            AutomationToolbarStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
            AutomationToolbarStatus.ELEMENTS_DETECTED -> MaterialTheme.colorScheme.primary
            AutomationToolbarStatus.RECORDING -> MaterialTheme.colorScheme.onErrorContainer
            AutomationToolbarStatus.PAUSED -> MaterialTheme.colorScheme.onTertiaryContainer
            AutomationToolbarStatus.EXECUTING -> MaterialTheme.colorScheme.onPrimaryContainer
            AutomationToolbarStatus.COMPLETED -> MaterialTheme.colorScheme.onPrimaryContainer
            AutomationToolbarStatus.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        },
        animationSpec = if (reducedMotion) snap() else ApexMotion.effectsSpring(),
        label = "AutomationButtonIconTint"
    )

    val shapeRadius = when (status) {
        AutomationToolbarStatus.RECORDING,
        AutomationToolbarStatus.EXECUTING -> 22.dp
        else -> 16.dp
    }
    val shape = rememberMorphingCornerShape(targetRadius = shapeRadius, label = "AutomationToolbarCornerMorph")

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(width = 48.dp, height = 48.dp)
            .minimumInteractiveComponentSize()
    ) {
        Surface(
            shape = shape,
            color = containerColor,
            border = when (status) {
                AutomationToolbarStatus.RECORDING,
                AutomationToolbarStatus.ERROR -> BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.65f))
                AutomationToolbarStatus.COMPLETED,
                AutomationToolbarStatus.EXECUTING -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                else -> null
            },
            modifier = Modifier
                .size(44.dp)
                .clip(shape)
                .clickable(
                    role = Role.Button,
                    onClick = onClick
                )
                .testTag("toolbar_automation_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (status == AutomationToolbarStatus.EXECUTING) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("toolbar_automation_badge_executing")
                    )
                }

                Icon(
                    imageVector = Icons.Outlined.FlutterDash,
                    contentDescription = stringResource(R.string.cd_toolbar_automation),
                    tint = iconTint,
                    modifier = Modifier
                        .size(22.dp)
                        .alpha(pulseAlpha)
                        .testTag("toolbar_automation_icon")
                )

                when (status) {
                    AutomationToolbarStatus.ELEMENTS_DETECTED -> {
                        val badgeLabel = when {
                            detectedElementsCount > 99 -> "99+"
                            detectedElementsCount > 0 -> detectedElementsCount.toString()
                            else -> "•"
                        }
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(3.dp)
                                .testTag("toolbar_automation_badge_elements")
                        ) {
                            Text(
                                text = badgeLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    lineHeight = 10.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    AutomationToolbarStatus.RECORDING -> {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                                .alpha(pulseAlpha)
                                .testTag("toolbar_automation_badge_recording")
                        )
                    }
                    AutomationToolbarStatus.PAUSED -> {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(3.dp)
                                .size(14.dp)
                                .testTag("toolbar_automation_badge_paused")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Pause,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiary,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                    }
                    AutomationToolbarStatus.COMPLETED -> {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(3.dp)
                                .size(14.dp)
                                .testTag("toolbar_automation_badge_completed")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                    }
                    AutomationToolbarStatus.ERROR -> {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(3.dp)
                                .size(14.dp)
                                .testTag("toolbar_automation_badge_error")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.PriorityHigh,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onError,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                    }
                    AutomationToolbarStatus.IDLE,
                    AutomationToolbarStatus.EXECUTING -> Unit
                }
            }
        }
    }
}

@Composable
private fun IncognitoToolbarBadge() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
        modifier = Modifier.testTag("incognito_mode_badge")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            IncognitoGlassesIcon(
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = stringResource(R.string.incognito_badge_label),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

/**
 * Ergonomic Bottom NavigationBar featuring:
 * Home • Bookmarks • Search • Tabs • Hamburger Menu (opens the Browser Menu Bottom Sheet with Settings).
 */
@Composable
fun BraveBottomNavigationBar(
    isHomeActive: Boolean,
    isBookmarksScreenOpen: Boolean,
    isSettingsScreenOpen: Boolean,
    isMenuSheetOpen: Boolean,
    isCurrentWebPageBookmarked: Boolean,
    isTabSwitcherOpen: Boolean,
    isSearchScreenOpen: Boolean,
    openTabCount: Int,
    onHomeClick: () -> Unit,
    onBookmarksClick: () -> Unit,
    onSearchClick: () -> Unit,
    onTabsClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("bottom_navigation_bar")
    ) {
        // 1. Home
        val homeSelected = isHomeActive &&
            !isBookmarksScreenOpen &&
            !isSettingsScreenOpen &&
            !isTabSwitcherOpen &&
            !isSearchScreenOpen &&
            !isMenuSheetOpen

        NavigationBarItem(
            selected = homeSelected,
            onClick = onHomeClick,
            icon = {
                Icon(
                    imageVector = if (homeSelected) Icons.Filled.Home else Icons.Outlined.Home,
                    contentDescription = stringResource(R.string.nav_home)
                )
            },
            label = { Text(stringResource(R.string.nav_home), maxLines = 1) },
            modifier = Modifier.testTag("nav_home_button")
        )

        // 2. Bookmarks
        NavigationBarItem(
            selected = isBookmarksScreenOpen ||
                (!isHomeActive && isCurrentWebPageBookmarked && !isSearchScreenOpen && !isSettingsScreenOpen),
            onClick = onBookmarksClick,
            icon = {
                val bookmarkIcon = when {
                    isBookmarksScreenOpen -> Icons.Filled.Bookmarks
                    isHomeActive || isSettingsScreenOpen -> Icons.Outlined.Bookmarks
                    isCurrentWebPageBookmarked -> Icons.Filled.Bookmark
                    else -> Icons.Outlined.BookmarkAdd
                }
                Icon(
                    imageVector = bookmarkIcon,
                    contentDescription = stringResource(R.string.nav_bookmarks)
                )
            },
            label = { Text(stringResource(R.string.nav_bookmarks), maxLines = 1) },
            modifier = Modifier.testTag("nav_bookmarks_button")
        )

        // 3. Search
        NavigationBarItem(
            selected = isSearchScreenOpen,
            onClick = onSearchClick,
            icon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = stringResource(R.string.nav_search)
                )
            },
            label = { Text(stringResource(R.string.nav_search), maxLines = 1) },
            modifier = Modifier.testTag("nav_search_button")
        )

        // 4. Tabs with square count icon (Brave / Chrome style)
        NavigationBarItem(
            selected = isTabSwitcherOpen,
            onClick = onTabsClick,
            icon = {
                SquareTabCounterIcon(
                    count = openTabCount,
                    isSelected = isTabSwitcherOpen
                )
            },
            label = { Text(stringResource(R.string.nav_tabs), maxLines = 1) },
            modifier = Modifier.testTag("nav_tabs_button")
        )

        // 5. Hamburger Menu (opens Browser Menu ModalBottomSheet containing Settings)
        NavigationBarItem(
            selected = isMenuSheetOpen || isSettingsScreenOpen,
            onClick = onMenuClick,
            icon = {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = stringResource(R.string.cd_open_menu)
                )
            },
            label = { Text(stringResource(R.string.nav_menu), maxLines = 1) },
            modifier = Modifier.testTag("nav_menu_button")
        )
    }
}

/**
 * Brave & Chrome style square tab counter icon with rounded corners, stroke border,
 * and the open tabs count rendered inside the square.
 */
@Composable
fun SquareTabCounterIcon(
    count: Int,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val displayText = when {
        count > 99 -> ":D"
        count <= 0 -> "1"
        else -> count.toString()
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
        },
        border = BorderStroke(2.dp, borderColor),
        modifier = modifier
            .size(22.dp)
            .testTag("square_tab_counter_icon")
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = displayText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = if (displayText.length >= 2) 10.sp else 12.sp,
                    lineHeight = 12.sp,
                    textAlign = TextAlign.Center
                ),
                color = borderColor
            )
        }
    }
}

/**
 * Material 3 ModalBottomSheet opened from the bottom Hamburger Menu button.
 * Uses Material 3 Expressive Grouped Lists and places Settings cleanly inside the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BrowserMenuBottomSheet(
    isHomeActive: Boolean = true,
    desktopViewportEnabled: Boolean,
    browsingHistoryCount: Int = 0,
    downloadsCount: Int = 0,
    savedPasswordsCount: Int = 0,
    isDefaultBrowser: Boolean = false,
    showZoomOption: Boolean = false,
    activeSiteHost: String = "",
    currentZoomPercent: Int = 100,
    hasCustomSiteZoom: Boolean = false,
    onAdjustZoomPercent: (Int) -> Unit = {},
    onResetSiteZoom: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onSetAsDefaultBrowserClick: () -> Unit = {},
    onNewTabClick: () -> Unit = {},
    onNewIncognitoTabClick: () -> Unit = {},
    onOpenHistoryClick: () -> Unit = {},
    onOpenDownloadsClick: () -> Unit = {},
    onOpenPasswordsClick: () -> Unit = {},
    onReloadPageClick: () -> Unit = {},
    onToggleDesktopMode: (Boolean) -> Unit,
    onTranslatePageClick: (() -> Unit)? = null,
    onClearBrowsingData: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.testTag("hamburger_menu_bottom_sheet")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
        ) {
            // Optional Zoom Control in Main Menu (when enabled in Accessibility settings)
            if (showZoomOption) {
                val boundedZoom = currentZoomPercent.coerceIn(50, 200)
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("menu_sheet_zoom_row")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        MenuSheetIconBadge(
                            icon = Icons.Outlined.ZoomIn,
                            highlighted = hasCustomSiteZoom || boundedZoom != 100
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.menu_zoom_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (!isHomeActive && activeSiteHost.isNotBlank()) {
                                Text(
                                    text = activeSiteHost,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                        if (hasCustomSiteZoom) {
                            TextButton(
                                onClick = onResetSiteZoom,
                                modifier = Modifier.testTag("menu_sheet_zoom_reset")
                            ) {
                                Text(stringResource(R.string.menu_zoom_reset))
                            }
                        }
                        FilledTonalIconButton(
                            onClick = { onAdjustZoomPercent((boundedZoom - 5).coerceAtLeast(50)) },
                            enabled = boundedZoom > 50,
                            shapes = IconButtonDefaults.shapes(),
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("menu_sheet_zoom_out")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Remove,
                                contentDescription = stringResource(R.string.cd_accessibility_zoom_out),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.accessibility_zoom_percent_format, boundedZoom),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("menu_sheet_zoom_value")
                        )
                        FilledTonalIconButton(
                            onClick = { onAdjustZoomPercent((boundedZoom + 5).coerceAtMost(200)) },
                            enabled = boundedZoom < 200,
                            shapes = IconButtonDefaults.shapes(),
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("menu_sheet_zoom_in")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = stringResource(R.string.cd_accessibility_zoom_in),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Group 1: Quick Browser Actions (Material 3 Grouped List without section subheadings)
            val showTranslateOption = !isHomeActive && onTranslatePageClick != null
            val quickActionsCount = if (showTranslateOption) 6 else 5
            Column(
                verticalArrangement = Arrangement.spacedBy(GroupedListSpacing),
                modifier = Modifier.fillMaxWidth()
            ) {
                MaterialGroupListItem(
                    index = 0,
                    totalCount = quickActionsCount,
                    title = stringResource(R.string.menu_new_incognito_tab),
                    role = Role.Button,
                    onClick = {
                        onDismiss()
                        onNewIncognitoTabClick()
                    },
                    leadingContent = {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                IncognitoGlassesIcon(
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    modifier = Modifier.testTag("menu_sheet_new_incognito_tab")
                )

                MenuSheetActionRow(
                    index = 1,
                    totalCount = quickActionsCount,
                    icon = Icons.Outlined.History,
                    title = stringResource(R.string.menu_history),
                    onClick = {
                        onDismiss()
                        onOpenHistoryClick()
                    },
                    tag = "menu_sheet_history"
                )

                MenuSheetActionRow(
                    index = 2,
                    totalCount = quickActionsCount,
                    icon = Icons.Outlined.Download,
                    title = stringResource(R.string.menu_downloads),
                    onClick = {
                        onDismiss()
                        onOpenDownloadsClick()
                    },
                    tag = "menu_sheet_downloads"
                )

                MenuSheetActionRow(
                    index = 3,
                    totalCount = quickActionsCount,
                    icon = Icons.Outlined.Key,
                    title = stringResource(R.string.menu_passwords),
                    onClick = {
                        onDismiss()
                        onOpenPasswordsClick()
                    },
                    tag = "menu_sheet_passwords"
                )

                if (showTranslateOption) {
                    MenuSheetActionRow(
                        index = 4,
                        totalCount = quickActionsCount,
                        icon = Icons.Outlined.Translate,
                        title = stringResource(R.string.menu_translate_page),
                        onClick = {
                            onDismiss()
                            onTranslatePageClick?.invoke()
                        },
                        tag = "menu_sheet_translate_page"
                    )
                }

                MaterialGroupListItem(
                    index = if (showTranslateOption) 5 else 4,
                    totalCount = quickActionsCount,
                    title = stringResource(R.string.menu_desktop_mode),
                    role = Role.Switch,
                    onClick = {
                        val enabling = !desktopViewportEnabled
                        onToggleDesktopMode(enabling)
                        if (enabling) {
                            onDismiss()
                        }
                    },
                    leadingContent = {
                        MenuSheetIconBadge(
                            icon = Icons.Outlined.DesktopWindows,
                            highlighted = desktopViewportEnabled
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = desktopViewportEnabled,
                            onCheckedChange = null
                        )
                    },
                    modifier = Modifier.testTag("menu_sheet_desktop_switch")
                )
            }

            // Group 2: Preferences & Privacy (Default Browser when unset + Settings + Clear Browsing Data)
            val preferencesCount = if (isDefaultBrowser) 2 else 3
            Column(
                verticalArrangement = Arrangement.spacedBy(GroupedListSpacing),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!isDefaultBrowser) {
                    MaterialGroupListItem(
                        index = 0,
                        totalCount = preferencesCount,
                        title = stringResource(R.string.menu_set_default_browser),
                        role = Role.Button,
                        onClick = {
                            onDismiss()
                            onSetAsDefaultBrowserClick()
                        },
                        leadingContent = {
                            MenuSheetIconBadge(
                                icon = Icons.Outlined.Public,
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
                        modifier = Modifier.testTag("menu_sheet_set_default_browser")
                    )
                }

                val settingsIndex = if (isDefaultBrowser) 0 else 1
                MaterialGroupListItem(
                    index = settingsIndex,
                    totalCount = preferencesCount,
                    title = stringResource(R.string.menu_settings),
                    role = Role.Button,
                    onClick = {
                        onDismiss()
                        onOpenSettings()
                    },
                    leadingContent = {
                        MenuSheetIconBadge(
                            icon = Icons.Outlined.Settings,
                            highlighted = isDefaultBrowser
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.testTag("menu_sheet_open_settings")
                )

                val clearDataIndex = if (isDefaultBrowser) 1 else 2
                MaterialGroupListItem(
                    index = clearDataIndex,
                    totalCount = preferencesCount,
                    title = stringResource(R.string.menu_clear_data),
                    role = Role.Button,
                    onClick = {
                        onDismiss()
                        onClearBrowsingData()
                    },
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
                    modifier = Modifier.testTag("menu_sheet_clear_data")
                )
            }
        }
    }
}

@Composable
private fun MenuSheetActionRow(
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
        role = Role.Button,
        onClick = onClick,
        leadingContent = {
            MenuSheetIconBadge(icon = icon)
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

@Composable
private fun MenuSheetIconBadge(
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
