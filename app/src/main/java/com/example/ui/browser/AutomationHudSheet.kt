package com.example.ui.browser

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FlutterDash
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.automation.ActiveRecordingSession
import com.example.automation.AutomationActionType
import com.example.automation.AutomationHudMode
import com.example.automation.AutomationStep
import com.example.automation.DetectedPageElement
import com.example.automation.PageInspectionState
import com.example.automation.RecordingStatus
import com.example.automation.WorkflowExecutionState
import com.example.data.local.AutomationExecutionEntity
import com.example.data.local.AutomationWorkflowEntity
import com.example.ui.theme.EmphasizedTypography
import com.example.ui.theme.LocalReducedMotion
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationHudSheet(
    hudMode: AutomationHudMode,
    pageInspection: PageInspectionState,
    recordingSession: ActiveRecordingSession,
    instructionDraftText: String,
    instructionDraftSteps: List<AutomationStep>,
    executionState: WorkflowExecutionState?,
    workflows: List<AutomationWorkflowEntity>,
    executions: List<AutomationExecutionEntity>,
    onSelectMode: (AutomationHudMode) -> Unit,
    onScanPage: () -> Unit,
    onStartRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onMinimizeRecording: () -> Unit,
    onRenameRecordingDraft: (String) -> Unit,
    onEditRecordingStep: (String, String, String) -> Unit,
    onDeleteRecordingStep: (String) -> Unit,
    onSaveRecording: (String?) -> Unit,
    onDiscardRecording: () -> Unit,
    onInstructionTextChange: (String) -> Unit,
    onAddInstructions: () -> Unit,
    onClearInstructions: () -> Unit,
    onReviewInstructionSteps: () -> Unit,
    onEditInstructionStep: (String, String, AutomationActionType, String) -> Unit,
    onDeleteInstructionStep: (String) -> Unit,
    onMoveInstructionStepUp: (String) -> Unit,
    onMoveInstructionStepDown: (String) -> Unit,
    onSaveInstructionsAsWorkflow: (String) -> Unit,
    onExecuteInstructions: () -> Unit,
    onRunWorkflow: (Long) -> Unit,
    onOpenWorkflowDetails: (Long) -> Unit,
    onOpenWorkspace: () -> Unit,
    onOpenNanoRetrieverPanel: () -> Unit,
    onConfirmDestructiveExecution: () -> Unit,
    onCancelDestructiveExecution: () -> Unit,
    onClearExecutionBanner: () -> Unit,
    onDismiss: () -> Unit,
    onCancelExecution: () -> Unit = {},
    onEditRecordingStepDetailed: ((String, String, String, String, String) -> Unit)? = null,
    onEditInstructionStepDetailed: ((String, String, AutomationActionType, String, String, String) -> Unit)? = null,
    activeTabId: String = ""
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val coroutineScope = rememberCoroutineScope()
    val isExpanded = sheetState.currentValue == SheetValue.Expanded

    if (executionState?.isAwaitingConfirmation == true) {
        DestructiveAutomationConfirmDialog(
            workflowName = executionState.workflowName,
            destructiveSteps = executionState.pendingDestructiveSteps,
            onConfirm = onConfirmDestructiveExecution,
            onDismiss = onCancelDestructiveExecution
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.testTag("automation_hud_sheet")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
        ) {
            // HUD Header
            AutomationHudHeader(
                hudMode = hudMode,
                domain = pageInspection.domain,
                detectedCount = pageInspection.elements.size,
                isExpanded = isExpanded,
                onBackToOverview = { onSelectMode(AutomationHudMode.OVERVIEW) },
                onToggleExpand = {
                    coroutineScope.launch {
                        if (sheetState.currentValue == SheetValue.Expanded) {
                            sheetState.partialExpand()
                        } else {
                            sheetState.expand()
                        }
                    }
                },
                onClose = onDismiss
            )

            // Live Execution Feedback Card (when running or finished inside HUD)
            val isExecutionRunning = executionState?.status == "RUNNING"
            if (executionState != null && !executionState.isAwaitingConfirmation) {
                AutomationExecutionStatusBanner(
                    executionState = executionState,
                    onCancel = onCancelExecution,
                    onDismiss = onClearExecutionBanner
                )
            }

            // Active Mode Content
            AnimatedContent(
                targetState = hudMode,
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(120)) },
                label = "AutomationHudModeContent"
            ) { mode ->
                when (mode) {
                    AutomationHudMode.OVERVIEW -> {
                        AutomationHudOverviewContent(
                            pageInspection = pageInspection,
                            recordingStatus = recordingSession.status,
                            savedCount = workflows.size,
                            recentCount = executions.size,
                            onSelectRecord = {
                                if (recordingSession.status == RecordingStatus.IDLE ||
                                    recordingSession.status == RecordingStatus.CANCELLED ||
                                    recordingSession.status == RecordingStatus.ERROR
                                ) {
                                    onStartRecording()
                                } else {
                                    onSelectMode(AutomationHudMode.RECORDING)
                                }
                            },
                            onSelectInstructions = { onSelectMode(AutomationHudMode.INSTRUCTIONS) },
                            onScanPage = onScanPage,
                            onViewElements = { onSelectMode(AutomationHudMode.ELEMENTS) },
                            onOpenSaved = { onSelectMode(AutomationHudMode.SAVED) },
                            onCreateAutomation = { onSelectMode(AutomationHudMode.INSTRUCTIONS) },
                            onOpenRecent = { onSelectMode(AutomationHudMode.RECENT) },
                            onOpenWorkspace = onOpenWorkspace,
                            onOpenNanoRetriever = onOpenNanoRetrieverPanel
                        )
                    }

                    AutomationHudMode.RECORDING -> {
                        AutomationRecordingContent(
                            session = recordingSession,
                            activeTabId = activeTabId,
                            onStartRecording = onStartRecording,
                            onPauseRecording = onPauseRecording,
                            onResumeRecording = onResumeRecording,
                            onStopRecording = onStopRecording,
                            onMinimizeHud = onMinimizeRecording,
                            onRenameDraft = onRenameRecordingDraft,
                            onEditStep = onEditRecordingStep,
                            onEditStepDetailed = onEditRecordingStepDetailed,
                            onDeleteStep = onDeleteRecordingStep,
                            onSaveRecording = onSaveRecording,
                            onDiscardRecording = onDiscardRecording
                        )
                    }

                    AutomationHudMode.INSTRUCTIONS -> {
                        AutomationInstructionsContent(
                            draftText = instructionDraftText,
                            steps = instructionDraftSteps,
                            currentDomain = pageInspection.domain,
                            isExecutionRunning = isExecutionRunning,
                            onTextChange = onInstructionTextChange,
                            onAddInstructions = onAddInstructions,
                            onClear = onClearInstructions,
                            onReviewSteps = onReviewInstructionSteps,
                            onEditStep = onEditInstructionStep,
                            onEditStepDetailed = onEditInstructionStepDetailed,
                            onDeleteStep = onDeleteInstructionStep,
                            onMoveStepUp = onMoveInstructionStepUp,
                            onMoveStepDown = onMoveInstructionStepDown,
                            onSaveWorkflow = onSaveInstructionsAsWorkflow,
                            onExecute = onExecuteInstructions
                        )
                    }

                    AutomationHudMode.ELEMENTS -> {
                        AutomationElementsContent(
                            inspection = pageInspection,
                            onRescan = onScanPage,
                            onUseElementInInstructions = { element ->
                                val cmd = if (element.inputType in listOf("text", "email", "password", "search") ||
                                    element.tagName == "textarea"
                                ) {
                                    "Enter \"\" in ${element.label}"
                                } else {
                                    "Click ${element.label}"
                                }
                                onInstructionTextChange(
                                    if (instructionDraftText.isBlank()) cmd else "$instructionDraftText\n$cmd"
                                )
                                onSelectMode(AutomationHudMode.INSTRUCTIONS)
                            }
                        )
                    }

                    AutomationHudMode.SAVED -> {
                        AutomationSavedQuickListContent(
                            workflows = workflows,
                            isExecutionRunning = isExecutionRunning,
                            onRunWorkflow = onRunWorkflow,
                            onOpenDetails = onOpenWorkflowDetails,
                            onOpenWorkspace = onOpenWorkspace
                        )
                    }

                    AutomationHudMode.RECENT -> {
                        AutomationRecentExecutionsContent(
                            executions = executions,
                            onOpenDetails = onOpenWorkflowDetails,
                            onOpenWorkspace = onOpenWorkspace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AutomationHudHeader(
    hudMode: AutomationHudMode,
    domain: String,
    detectedCount: Int,
    isExpanded: Boolean,
    onBackToOverview: () -> Unit,
    onToggleExpand: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("automation_hud_header")
    ) {
        if (hudMode != AutomationHudMode.OVERVIEW) {
            IconButton(
                onClick = onBackToOverview,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("automation_hud_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_navigate_back)
                )
            }
        } else {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.FlutterDash,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.automation_hud_title),
                style = EmphasizedTypography.activeTabTitle,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = domain.ifBlank { "Start Page" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (detectedCount > 0) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                ) {
                    Text(
                        text = stringResource(R.string.automation_detected_count, detectedCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (detectedCount > 0) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        IconButton(
            onClick = onToggleExpand,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("automation_hud_expand_button")
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                contentDescription = stringResource(
                    if (isExpanded) R.string.automation_hud_collapse else R.string.automation_hud_expand
                )
            )
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("automation_hud_close_button")
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.automation_hud_close)
            )
        }
    }
}

@Composable
private fun AutomationHudOverviewContent(
    pageInspection: PageInspectionState,
    recordingStatus: RecordingStatus,
    savedCount: Int,
    recentCount: Int,
    onSelectRecord: () -> Unit,
    onSelectInstructions: () -> Unit,
    onScanPage: () -> Unit,
    onViewElements: () -> Unit,
    onOpenSaved: () -> Unit,
    onCreateAutomation: () -> Unit,
    onOpenRecent: () -> Unit,
    onOpenWorkspace: () -> Unit,
    onOpenNanoRetriever: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        // A. Automation Modes
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MaterialGroupSectionHeader(title = "Automation Modes")
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val isRecActive = recordingStatus == RecordingStatus.RECORDING ||
                    recordingStatus == RecordingStatus.PAUSED
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = if (isRecActive) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.large)
                        .clickable(role = Role.Button, onClick = onSelectRecord)
                        .testTag("hud_mode_record_button")
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.RadioButtonChecked,
                            contentDescription = null,
                            tint = if (isRecActive) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        )
                        Text(
                            text = if (isRecActive) "Recording…" else stringResource(R.string.automation_mode_record),
                            style = EmphasizedTypography.activeTabTitle,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isRecActive) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        )
                        Text(
                            text = "Capture clicks & inputs",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isRecActive) {
                                MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            }
                        )
                    }
                }

                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.large)
                        .clickable(role = Role.Button, onClick = onSelectInstructions)
                        .testTag("hud_mode_instructions_button")
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.EditNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = stringResource(R.string.automation_mode_instructions),
                            style = EmphasizedTypography.activeTabTitle,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Write steps in plain text",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        // B. Page Inspection
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MaterialGroupSectionHeader(title = "Page Inspection")
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DocumentScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when {
                                    pageInspection.isScanning -> stringResource(R.string.automation_scanning)
                                    pageInspection.elements.isNotEmpty() ->
                                        "${pageInspection.elements.size} interactive elements ready"
                                    pageInspection.domain == "Start Page" ->
                                        "Open a webpage to detect inputs and buttons"
                                    else -> "No interactive elements scanned yet"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (pageInspection.errorMessage != null) {
                                Text(
                                    text = pageInspection.errorMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        if (pageInspection.isScanning) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilledTonalButton(
                            onClick = onScanPage,
                            enabled = !pageInspection.isScanning,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("hud_scan_page_button")
                        ) {
                            Text(stringResource(R.string.automation_scan_page), maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = onViewElements,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("hud_view_elements_button")
                        ) {
                            Text(
                                text = stringResource(R.string.automation_view_elements),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // C. Quick Actions
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MaterialGroupSectionHeader(title = "Quick Actions")
            Column(verticalArrangement = Arrangement.spacedBy(GroupedListSpacing)) {
                MaterialGroupListItem(
                    index = 0,
                    totalCount = 4,
                    title = stringResource(R.string.automation_quick_saved),
                    subtitle = "$savedCount workflow${if (savedCount == 1) "" else "s"} in library",
                    role = Role.Button,
                    onClick = onOpenSaved,
                    leadingContent = {
                        SettingsIconBadge(icon = Icons.Outlined.Bookmarks, highlighted = savedCount > 0)
                    },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    },
                    modifier = Modifier.testTag("hud_quick_saved")
                )
                MaterialGroupListItem(
                    index = 1,
                    totalCount = 4,
                    title = stringResource(R.string.automation_quick_create),
                    subtitle = "Compose step-by-step instructions",
                    role = Role.Button,
                    onClick = onCreateAutomation,
                    leadingContent = {
                        SettingsIconBadge(icon = Icons.Filled.Add)
                    },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    },
                    modifier = Modifier.testTag("hud_quick_create")
                )
                MaterialGroupListItem(
                    index = 2,
                    totalCount = 4,
                    title = stringResource(R.string.automation_quick_recent),
                    subtitle = "$recentCount recent run${if (recentCount == 1) "" else "s"}",
                    role = Role.Button,
                    onClick = onOpenRecent,
                    leadingContent = {
                        SettingsIconBadge(icon = Icons.Outlined.History)
                    },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    },
                    modifier = Modifier.testTag("hud_quick_recent")
                )
                MaterialGroupListItem(
                    index = 3,
                    totalCount = 4,
                    title = "Nano & Retriever Agents",
                    subtitle = "Multi-agent DOM planner & web table extractor",
                    role = Role.Button,
                    onClick = onOpenNanoRetriever,
                    leadingContent = {
                        SettingsIconBadge(icon = Icons.Outlined.AutoAwesome)
                    },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    },
                    modifier = Modifier.testTag("hud_quick_nano_retriever")
                )
            }
        }

        // D. Main Action
        Button(
            onClick = onOpenWorkspace,
            contentPadding = PaddingValues(vertical = 14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("hud_open_workspace_button")
        ) {
            Icon(
                imageVector = Icons.Outlined.OpenInFull,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.automation_open_workspace))
        }
    }
}

@Composable
private fun AutomationRecordingContent(
    session: ActiveRecordingSession,
    onStartRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onMinimizeHud: () -> Unit,
    onRenameDraft: (String) -> Unit,
    onEditStep: (String, String, String) -> Unit,
    onEditStepDetailed: ((String, String, String, String, String) -> Unit)? = null,
    onDeleteStep: (String) -> Unit,
    onSaveRecording: (String?) -> Unit,
    onDiscardRecording: () -> Unit,
    activeTabId: String = ""
) {
    val reducedMotion = LocalReducedMotion.current
    val pulseTransition = rememberInfiniteTransition(label = "RecordingDotPulse")
    val dotAlpha by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (!reducedMotion && session.status == RecordingStatus.RECORDING) 0.3f else 1f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "RecordingDotAlpha"
    )
    var editingStep by remember { mutableStateOf<AutomationStep?>(null) }

    editingStep?.let { target ->
        EditAutomationStepDialog(
            step = target,
            allowActionTypeChange = false,
            onSave = { desc, _, valText ->
                onEditStep(target.id, desc, valText)
                editingStep = null
            },
            onSaveDetailed = { desc, _, valText, targetLabel, selector ->
                if (onEditStepDetailed != null) {
                    onEditStepDetailed(target.id, desc, valText, targetLabel, selector)
                } else {
                    onEditStep(target.id, desc, valText)
                }
                editingStep = null
            },
            onDismiss = { editingStep = null }
        )
    }

    val statusCardColor by animateColorAsState(
        targetValue = when (session.status) {
            RecordingStatus.RECORDING, RecordingStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
            RecordingStatus.PAUSED -> MaterialTheme.colorScheme.tertiaryContainer
            RecordingStatus.STOPPED -> MaterialTheme.colorScheme.primaryContainer
            RecordingStatus.CANCELLED -> MaterialTheme.colorScheme.surfaceVariant
            RecordingStatus.IDLE -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        label = "RecordingStatusCardColor"
    )
    val statusContentColor = when (session.status) {
        RecordingStatus.RECORDING, RecordingStatus.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        RecordingStatus.PAUSED -> MaterialTheme.colorScheme.onTertiaryContainer
        RecordingStatus.STOPPED -> MaterialTheme.colorScheme.onPrimaryContainer
        RecordingStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
        RecordingStatus.IDLE -> MaterialTheme.colorScheme.onSurface
    }
    val isInAnotherTab = (session.status == RecordingStatus.RECORDING || session.status == RecordingStatus.PAUSED) &&
        activeTabId.isNotBlank() &&
        session.initiatingTabId.isNotBlank() &&
        activeTabId != session.initiatingTabId

    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
        // Distinct Recording / Paused / Stopped / Cancelled / Error Status Banner
        Surface(
            shape = MaterialTheme.shapes.large,
            color = statusCardColor,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("recording_status_card")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .alpha(dotAlpha)
                        .background(
                            when (session.status) {
                                RecordingStatus.RECORDING, RecordingStatus.ERROR -> MaterialTheme.colorScheme.error
                                RecordingStatus.PAUSED -> MaterialTheme.colorScheme.tertiary
                                RecordingStatus.STOPPED -> MaterialTheme.colorScheme.primary
                                RecordingStatus.CANCELLED, RecordingStatus.IDLE -> MaterialTheme.colorScheme.outline
                            }
                        )
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (session.status) {
                            RecordingStatus.RECORDING -> "Recording actions…"
                            RecordingStatus.PAUSED -> "Recording paused"
                            RecordingStatus.STOPPED -> "Recording stopped — review & save"
                            RecordingStatus.CANCELLED -> "Recording cancelled"
                            RecordingStatus.ERROR -> "Recording unavailable"
                            RecordingStatus.IDLE -> "Ready to record"
                        },
                        style = EmphasizedTypography.activeTabTitle,
                        fontWeight = FontWeight.SemiBold,
                        color = statusContentColor
                    )
                    val subtitleText = when {
                        session.recordingError != null -> session.recordingError.message
                        isInAnotherTab -> "Recording continues in ${session.targetDomain.ifBlank { "another" }} tab (${session.steps.size} captured)"
                        else -> "${session.steps.size} action${if (session.steps.size == 1) "" else "s"} captured" +
                            if (session.targetDomain.isNotBlank()) " on ${session.targetDomain}" else ""
                    }
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusContentColor.copy(alpha = 0.85f)
                    )
                }
            }
        }

        // Control Buttons based on State
        when (session.status) {
            RecordingStatus.IDLE, RecordingStatus.CANCELLED, RecordingStatus.ERROR -> {
                if (session.steps.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = onDiscardRecording,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("recording_discard_button")
                        ) {
                            Text(stringResource(R.string.automation_discard))
                        }
                        Button(
                            onClick = onStartRecording,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("recording_start_button")
                        ) {
                            Icon(Icons.Filled.FiberManualRecord, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Start Recording")
                        }
                    }
                } else {
                    Button(
                        onClick = onStartRecording,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("recording_start_button")
                    ) {
                        Icon(Icons.Filled.FiberManualRecord, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Recording")
                    }
                }
            }
            RecordingStatus.RECORDING -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledTonalButton(
                        onClick = onPauseRecording,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("recording_pause_button")
                    ) {
                        Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.automation_pause))
                    }
                    Button(
                        onClick = onStopRecording,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("recording_stop_button")
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.automation_stop))
                    }
                }
                OutlinedButton(
                    onClick = onMinimizeHud,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("recording_minimize_button")
                ) {
                    Text(stringResource(R.string.automation_hud_minimize) + " to interact with website")
                }
            }
            RecordingStatus.PAUSED -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onResumeRecording,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("recording_resume_button")
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.automation_resume))
                    }
                    FilledTonalButton(
                        onClick = onStopRecording,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("recording_stop_button")
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.automation_stop))
                    }
                }
                OutlinedButton(
                    onClick = onMinimizeHud,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("recording_minimize_button")
                ) {
                    Text(stringResource(R.string.automation_hud_minimize))
                }
            }
            RecordingStatus.STOPPED -> {
                OutlinedTextField(
                    value = session.suggestedName,
                    onValueChange = onRenameDraft,
                    label = { Text("Automation name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("recording_name_input")
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onDiscardRecording,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("recording_discard_button")
                    ) {
                        Text(stringResource(R.string.automation_discard))
                    }
                    Button(
                        onClick = { onSaveRecording(session.suggestedName) },
                        enabled = session.steps.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("recording_save_button")
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.automation_save))
                    }
                }
            }
        }

        // Captured Steps List
        MaterialGroupSectionHeader(title = "Captured Actions (${session.steps.size})")
        if (session.steps.isEmpty()) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Tap Minimize or interact with fields, checkboxes, links, and buttons on the webpage to capture steps live.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp)
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(GroupedListSpacing)) {
                session.steps.forEachIndexed { idx, step ->
                    val shape = groupedListItemShape(idx, session.steps.size)
                    Surface(
                        shape = shape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("recorded_step_row_${idx + 1}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${idx + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = step.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (step.value.isNotBlank()) {
                                    Text(
                                        text = "Value: ${step.value}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(
                                onClick = { editingStep = step },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("recorded_step_edit_${idx + 1}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = stringResource(R.string.automation_edit),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { onDeleteStep(step.id) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("recorded_step_delete_${idx + 1}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = stringResource(R.string.automation_delete),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AutomationInstructionsContent(
    draftText: String,
    steps: List<AutomationStep>,
    currentDomain: String,
    isExecutionRunning: Boolean = false,
    onTextChange: (String) -> Unit,
    onAddInstructions: () -> Unit,
    onClear: () -> Unit,
    onReviewSteps: () -> Unit,
    onEditStep: (String, String, AutomationActionType, String) -> Unit,
    onEditStepDetailed: ((String, String, AutomationActionType, String, String, String) -> Unit)? = null,
    onDeleteStep: (String) -> Unit,
    onMoveStepUp: (String) -> Unit,
    onMoveStepDown: (String) -> Unit,
    onSaveWorkflow: (String) -> Unit,
    onExecute: () -> Unit
) {
    var editingStep by remember { mutableStateOf<AutomationStep?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var saveWorkflowName by remember(currentDomain) {
        mutableStateOf(if (currentDomain == "Start Page") "Manual Automation" else "$currentDomain Automation")
    }

    editingStep?.let { target ->
        EditAutomationStepDialog(
            step = target,
            allowActionTypeChange = true,
            onSave = { desc, actionType, valText ->
                onEditStep(target.id, desc, actionType, valText)
                editingStep = null
            },
            onSaveDetailed = { desc, actionType, valText, targetLabel, selector ->
                if (onEditStepDetailed != null) {
                    onEditStepDetailed(target.id, desc, actionType, valText, targetLabel, selector)
                } else {
                    onEditStep(target.id, desc, actionType, valText)
                }
                editingStep = null
            },
            onDismiss = { editingStep = null }
        )
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text(stringResource(R.string.automation_save)) },
            text = {
                OutlinedTextField(
                    value = saveWorkflowName,
                    onValueChange = { saveWorkflowName = it },
                    label = { Text("Workflow name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("instructions_save_name_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveWorkflow(saveWorkflowName)
                        showSaveDialog = false
                    },
                    modifier = Modifier.testTag("instructions_save_confirm_button")
                ) {
                    Text(stringResource(R.string.automation_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = draftText,
            onValueChange = onTextChange,
            label = { Text(stringResource(R.string.automation_mode_instructions)) },
            placeholder = { Text(stringResource(R.string.automation_instructions_placeholder)) },
            minLines = 3,
            maxLines = 6,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("instructions_input_field")
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FilledTonalButton(
                onClick = onAddInstructions,
                enabled = draftText.isNotBlank(),
                modifier = Modifier
                    .weight(1f)
                    .testTag("instructions_add_button")
            ) {
                Text(stringResource(R.string.automation_add_instructions), maxLines = 1)
            }
            OutlinedButton(
                onClick = onClear,
                enabled = draftText.isNotBlank() || steps.isNotEmpty(),
                modifier = Modifier.testTag("instructions_clear_button")
            ) {
                Text(stringResource(R.string.automation_clear))
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = onReviewSteps,
                enabled = draftText.isNotBlank() || steps.isNotEmpty(),
                modifier = Modifier
                    .weight(1f)
                    .testTag("instructions_review_button")
            ) {
                Text(stringResource(R.string.automation_review_steps), maxLines = 1)
            }
            Button(
                onClick = onExecute,
                enabled = !isExecutionRunning && (draftText.isNotBlank() || steps.isNotEmpty()),
                modifier = Modifier
                    .weight(1f)
                    .testTag("instructions_execute_button")
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.automation_execute))
            }
        }

        if (steps.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                MaterialGroupSectionHeader(
                    title = "Workflow Steps (${steps.size})",
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { showSaveDialog = true },
                    modifier = Modifier.testTag("instructions_save_button")
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.automation_save))
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(GroupedListSpacing)) {
                steps.forEachIndexed { idx, step ->
                    val shape = groupedListItemShape(idx, steps.size)
                    Surface(
                        shape = shape,
                        color = if (step.isDestructive) {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("instruction_step_row_${idx + 1}")
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${idx + 1}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = step.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = buildString {
                                            append(step.actionType.label)
                                            if (step.value.isNotBlank()) append(" • \"${step.value}\"")
                                            if (step.isDestructive) append(" • Confirmation required")
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (step.isDestructive) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.End,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                IconButton(
                                    onClick = { onMoveStepUp(step.id) },
                                    enabled = idx > 0,
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { onMoveStepDown(step.id) },
                                    enabled = idx < steps.lastIndex,
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { editingStep = step },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.automation_edit), modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { onDeleteStep(step.id) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteOutline,
                                        contentDescription = stringResource(R.string.automation_delete),
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
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

@Composable
private fun AutomationElementsContent(
    inspection: PageInspectionState,
    onRescan: () -> Unit,
    onUseElementInInstructions: (DetectedPageElement) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Detected Elements (${inspection.elements.size})",
                style = EmphasizedTypography.activeTabTitle,
                fontWeight = FontWeight.SemiBold
            )
            FilledTonalButton(onClick = onRescan, enabled = !inspection.isScanning) {
                Text(if (inspection.isScanning) "Scanning…" else stringResource(R.string.automation_scan_page))
            }
        }

        if (inspection.elements.isEmpty()) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = inspection.errorMessage ?: "No interactive buttons, inputs, or links detected on this page yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(GroupedListSpacing)) {
                inspection.elements.forEachIndexed { idx, element ->
                    MaterialGroupListItem(
                        index = idx,
                        totalCount = inspection.elements.size,
                        title = element.label,
                        subtitle = "${element.role} • ${element.selector}",
                        role = Role.Button,
                        onClick = { onUseElementInInstructions(element) },
                        leadingContent = {
                            SettingsIconBadge(icon = Icons.Outlined.ListAlt)
                        },
                        trailingContent = {
                            Text(
                                text = "Use",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AutomationSavedQuickListContent(
    workflows: List<AutomationWorkflowEntity>,
    isExecutionRunning: Boolean = false,
    onRunWorkflow: (Long) -> Unit,
    onOpenDetails: (Long) -> Unit,
    onOpenWorkspace: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        if (workflows.isEmpty()) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.automation_empty_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.automation_empty_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            val previewList = workflows.take(6)
            Column(verticalArrangement = Arrangement.spacedBy(GroupedListSpacing)) {
                previewList.forEachIndexed { idx, wf ->
                    MaterialGroupListItem(
                        index = idx,
                        totalCount = previewList.size,
                        title = wf.name,
                        subtitle = "${wf.domain} • ${wf.stepCount} steps • ${wf.type}",
                        role = Role.Button,
                        onClick = { onOpenDetails(wf.id) },
                        leadingContent = {
                            SettingsIconBadge(icon = Icons.Outlined.FlutterDash)
                        },
                        trailingContent = {
                            FilledTonalIconButton(
                                onClick = { onRunWorkflow(wf.id) },
                                enabled = !isExecutionRunning,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = stringResource(R.string.automation_run)
                                )
                            }
                        }
                    )
                }
            }
        }

        Button(onClick = onOpenWorkspace, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.automation_open_workspace))
        }
    }
}

@Composable
private fun AutomationRecentExecutionsContent(
    executions: List<AutomationExecutionEntity>,
    onOpenDetails: (Long) -> Unit,
    onOpenWorkspace: () -> Unit
) {
    val formatter = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        if (executions.isEmpty()) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "No recent automation executions yet. Run a saved automation or execute manual instructions to view results here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            val preview = executions.take(8)
            Column(verticalArrangement = Arrangement.spacedBy(GroupedListSpacing)) {
                preview.forEachIndexed { idx, exec ->
                    val succeeded = exec.status == "COMPLETED"
                    MaterialGroupListItem(
                        index = idx,
                        totalCount = preview.size,
                        title = exec.workflowName,
                        subtitle = "${exec.status} • ${exec.completedSteps}/${exec.totalSteps} steps • ${formatter.format(Date(exec.startedAt))}",
                        role = Role.Button,
                        onClick = { onOpenDetails(exec.workflowId) },
                        leadingContent = {
                            Icon(
                                imageVector = if (succeeded) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = if (succeeded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        },
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                        }
                    )
                }
            }
        }

        OutlinedButton(onClick = onOpenWorkspace, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.automation_open_workspace))
        }
    }
}

@Composable
fun AutomationExecutionStatusBanner(
    executionState: WorkflowExecutionState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null
) {
    val isRunning = executionState.status == "RUNNING"
    val isFailed = executionState.status == "FAILED"
    val isCancelled = executionState.status == "CANCELLED"
    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .testTag("automation_execution_banner")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = when {
                        isFailed -> Icons.Filled.ErrorOutline
                        isCancelled -> Icons.Filled.Stop
                        isRunning -> Icons.Outlined.FlutterDash
                        else -> Icons.Filled.CheckCircle
                    },
                    contentDescription = null,
                    tint = when {
                        isFailed -> MaterialTheme.colorScheme.error
                        isCancelled -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${executionState.workflowName} • ${executionState.status}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = executionState.verificationMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isRunning && onCancel != null) {
                    OutlinedButton(
                        onClick = onCancel,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("automation_cancel_execution_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.cancel))
                    }
                } else if (!isRunning) {
                    IconButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                    }
                }
            }
            if (isRunning && executionState.totalSteps > 0) {
                LinearProgressIndicator(
                    progress = {
                        (executionState.currentStepIndex.toFloat() / executionState.totalSteps.toFloat())
                            .coerceIn(0.05f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (executionState.stepLogs.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                executionState.stepLogs.takeLast(4).forEach { log ->
                    Text(
                        text = "• $log",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun DestructiveAutomationConfirmDialog(
    workflowName: String,
    destructiveSteps: List<AutomationStep>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("Confirm Sensitive Action") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "\"$workflowName\" contains actions that submit data or modify remote state:",
                    style = MaterialTheme.typography.bodyMedium
                )
                destructiveSteps.take(4).forEach { step ->
                    Text(
                        text = "• ${step.description}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.testTag("destructive_confirm_button")
            ) {
                Text(stringResource(R.string.automation_execute))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("destructive_cancel_button")
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun EditAutomationStepDialog(
    step: AutomationStep?,
    allowActionTypeChange: Boolean,
    onSave: (description: String, actionType: AutomationActionType, value: String) -> Unit,
    onDismiss: () -> Unit,
    onSaveDetailed: ((description: String, actionType: AutomationActionType, value: String, targetLabel: String, selector: String) -> Unit)? = null
) {
    var description by remember(step?.id) { mutableStateOf(step?.description.orEmpty()) }
    var targetLabel by remember(step?.id) { mutableStateOf(step?.targetLabel.orEmpty()) }
    var selector by remember(step?.id) { mutableStateOf(step?.selector.orEmpty()) }
    var value by remember(step?.id) { mutableStateOf(step?.value.orEmpty()) }
    var selectedType by remember(step?.id) { mutableStateOf(step?.actionType ?: AutomationActionType.CLICK) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (step == null) stringResource(R.string.automation_add_step) else "Edit Step") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Step description") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_step_description_input")
                )
                OutlinedTextField(
                    value = targetLabel,
                    onValueChange = { targetLabel = it },
                    label = { Text("Target element label") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_step_label_input")
                )
                OutlinedTextField(
                    value = selector,
                    onValueChange = { selector = it },
                    label = { Text("CSS selector (optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_step_selector_input")
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Input value / URL (optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_step_value_input")
                )
                if (allowActionTypeChange) {
                    Text(
                        text = "Action: ${selectedType.label}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            listOf(
                                AutomationActionType.CLICK,
                                AutomationActionType.INPUT,
                                AutomationActionType.SELECT,
                                AutomationActionType.TOGGLE
                            ),
                            listOf(
                                AutomationActionType.NAVIGATE,
                                AutomationActionType.SCROLL,
                                AutomationActionType.EXTRACT,
                                AutomationActionType.SUBMIT
                            )
                        ).forEach { chipRow ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                chipRow.forEach { type ->
                                    val isSelected = selectedType == type
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHighest
                                        },
                                        border = if (isSelected) {
                                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                        } else null,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { selectedType = type }
                                            .testTag("edit_step_action_${type.id.lowercase()}")
                                    ) {
                                        Text(
                                            text = type.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
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
            Button(
                onClick = {
                    val resolvedLabel = targetLabel.trim().ifBlank { description.trim() }
                    if (onSaveDetailed != null) {
                        onSaveDetailed(description, selectedType, value, resolvedLabel, selector.trim())
                    } else {
                        onSave(description, selectedType, value)
                    }
                },
                enabled = description.isNotBlank(),
                modifier = Modifier.testTag("edit_step_save_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
