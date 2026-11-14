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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FlutterDash
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.automation.AutomationActionType
import com.example.automation.AutomationStep
import com.example.automation.AutomationStepCodec
import com.example.automation.AutomationType
import com.example.automation.WorkflowExecutionState
import com.example.data.local.AutomationExecutionEntity
import com.example.data.local.AutomationWorkflowEntity
import com.example.ui.theme.EmphasizedTypography
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationDetailsScreenOverlay(
    workflow: AutomationWorkflowEntity,
    executions: List<AutomationExecutionEntity>,
    executionState: WorkflowExecutionState?,
    onRunWorkflow: (Long) -> Unit,
    onRenameWorkflow: (Long, String) -> Unit,
    onUpdateSteps: (Long, List<AutomationStep>) -> Unit,
    onAddStep: (Long, AutomationActionType, String, String, String) -> Unit,
    onConfirmDestructiveExecution: () -> Unit,
    onCancelDestructiveExecution: () -> Unit,
    onClearExecutionBanner: () -> Unit,
    backHandlerEnabled: Boolean = true,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onCancelExecution: () -> Unit = {}
) {
    BackHandler(enabled = backHandlerEnabled, onBack = onDismiss)

    val steps = remember(workflow.stepsJson) { AutomationStepCodec.decode(workflow.stepsJson) }
    val type = remember(workflow.type) { AutomationType.fromId(workflow.type) }
    val dateFormatter = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    var isEditingMode by rememberSaveable(workflow.id) { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showAddStepDialog by remember { mutableStateOf(false) }
    var editingStep by remember { mutableStateOf<AutomationStep?>(null) }

    if (executionState?.isAwaitingConfirmation == true) {
        DestructiveAutomationConfirmDialog(
            workflowName = executionState.workflowName,
            destructiveSteps = executionState.pendingDestructiveSteps,
            onConfirm = onConfirmDestructiveExecution,
            onDismiss = onCancelDestructiveExecution
        )
    }

    if (showRenameDialog) {
        RenameAutomationDialog(
            currentName = workflow.name,
            onConfirm = { newName ->
                onRenameWorkflow(workflow.id, newName)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false }
        )
    }

    if (showAddStepDialog) {
        EditAutomationStepDialog(
            step = null,
            allowActionTypeChange = true,
            onSave = { desc, actionType, value ->
                onAddStep(workflow.id, actionType, desc, value, desc)
                showAddStepDialog = false
            },
            onSaveDetailed = { desc, actionType, value, targetLabel, selector ->
                val resolvedTarget = targetLabel.ifBlank { desc }
                if (selector.isBlank()) {
                    onAddStep(workflow.id, actionType, resolvedTarget, value, desc)
                } else {
                    val newStep = AutomationStep(
                        id = "step_${java.util.UUID.randomUUID()}",
                        order = steps.size + 1,
                        actionType = actionType,
                        targetLabel = resolvedTarget,
                        selector = selector,
                        value = value,
                        description = desc.trim().ifBlank { resolvedTarget },
                        isDestructive = actionType == AutomationActionType.SUBMIT ||
                            AutomationStepCodec.isClauseDestructive(desc.lowercase()) ||
                            AutomationStepCodec.isClauseDestructive(resolvedTarget.lowercase())
                    )
                    onUpdateSteps(workflow.id, steps + newStep)
                }
                showAddStepDialog = false
            },
            onDismiss = { showAddStepDialog = false }
        )
    }

    editingStep?.let { target ->
        EditAutomationStepDialog(
            step = target,
            allowActionTypeChange = true,
            onSave = { desc, actionType, value ->
                val updated = steps.map {
                    if (it.id == target.id) {
                        AutomationStepCodec.applyStepEdit(
                            existing = it,
                            description = desc,
                            actionType = actionType,
                            value = value
                        )
                    } else {
                        it
                    }
                }
                onUpdateSteps(workflow.id, updated)
                editingStep = null
            },
            onSaveDetailed = { desc, actionType, value, targetLabel, selector ->
                val updated = steps.map {
                    if (it.id == target.id) {
                        AutomationStepCodec.applyStepEdit(
                            existing = it,
                            description = desc,
                            actionType = actionType,
                            value = value,
                            targetLabel = targetLabel,
                            selector = selector
                        )
                    } else {
                        it
                    }
                }
                onUpdateSteps(workflow.id, updated)
                editingStep = null
            },
            onDismiss = { editingStep = null }
        )
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
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("details_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back)
                        )
                    }
                },
                title = {
                    Text(
                        text = workflow.name,
                        style = EmphasizedTypography.headlineHero,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    IconButton(
                        onClick = { showRenameDialog = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("details_rename_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DriveFileRenameOutline,
                            contentDescription = stringResource(R.string.automation_rename)
                        )
                    }
                }
            )
        },
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .testTag("automation_details_screen")
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
                    .testTag("automation_details_list")
            ) {
                // Summary & Primary Run / Edit Controls
                item(key = "details_summary_card") {
                    ElevatedCard(
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(18.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                SettingsIconBadge(icon = Icons.Outlined.FlutterDash, highlighted = true)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = workflow.domain,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${type.label} workflow • ${steps.size} step${if (steps.size == 1) "" else "s"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            val lastExecText = workflow.lastExecutedAt?.let { ts ->
                                "${formatExecutionStatusLabel(workflow.lastExecutionStatus)} • ${dateFormatter.format(Date(ts))}"
                            } ?: "Never executed"

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Last Execution: $lastExecText",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (workflow.lastExecutionSummary.isNotBlank()) {
                                    Text(
                                        text = workflow.lastExecutionSummary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = { onRunWorkflow(workflow.id) },
                                    enabled = steps.isNotEmpty() && executionState?.status != "RUNNING",
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("details_run_button")
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.automation_run))
                                }

                                FilledTonalButton(
                                    onClick = { isEditingMode = !isEditingMode },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("details_edit_toggle_button")
                                ) {
                                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isEditingMode) "Done Editing" else stringResource(R.string.automation_edit))
                                }
                            }
                        }
                    }
                }

                // Live Execution Progress & Step-by-Step Verification Updates
                if (executionState != null &&
                    !executionState.isAwaitingConfirmation &&
                    executionState.workflowId == workflow.id
                ) {
                    item(key = "details_execution_live_card") {
                        AutomationExecutionStatusBanner(
                            executionState = executionState,
                            onCancel = onCancelExecution,
                            onDismiss = onClearExecutionBanner
                        )
                    }
                }

                // Ordered Steps Header + Add Step button
                item(key = "details_steps_header") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        MaterialGroupSectionHeader(
                            title = "Ordered Steps (${steps.size})",
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalButton(
                            onClick = { showAddStepDialog = true },
                            modifier = Modifier.testTag("details_add_step_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.automation_add_step))
                        }
                    }
                }

                if (steps.isEmpty()) {
                    item(key = "details_steps_empty") {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "This workflow has no steps yet. Tap Add Step to add an action.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    itemsIndexed(
                        items = steps,
                        key = { _, s -> s.id }
                    ) { idx, step ->
                        val shape = groupedListItemShape(idx, steps.size)
                        val isCurrentlyRunningStep = executionState != null &&
                            executionState.workflowId == workflow.id &&
                            executionState.status == "RUNNING" &&
                            executionState.currentStepIndex == idx

                        Surface(
                            shape = shape,
                            color = when {
                                isCurrentlyRunningStep -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                step.isDestructive -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                                else -> MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            border = if (isCurrentlyRunningStep) {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            } else {
                                null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("details_step_row_${idx + 1}")
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
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
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = buildString {
                                                append(step.actionType.label)
                                                if (step.value.isNotBlank()) append(" • \"${step.value}\"")
                                                if (step.selector.isNotBlank()) append(" • ${step.selector}")
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                if (isCurrentlyRunningStep) {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }

                                Row(
                                    horizontalArrangement = Arrangement.End,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (idx > 0) {
                                                val mutable = steps.toMutableList()
                                                val moved = mutable.removeAt(idx)
                                                mutable.add(idx - 1, moved)
                                                onUpdateSteps(workflow.id, mutable)
                                            }
                                        },
                                        enabled = idx > 0,
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("details_step_up_${idx + 1}")
                                    ) {
                                        Icon(Icons.Filled.ArrowUpward, contentDescription = "Move step up", modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            if (idx < steps.lastIndex) {
                                                val mutable = steps.toMutableList()
                                                val moved = mutable.removeAt(idx)
                                                mutable.add(idx + 1, moved)
                                                onUpdateSteps(workflow.id, mutable)
                                            }
                                        },
                                        enabled = idx < steps.lastIndex,
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("details_step_down_${idx + 1}")
                                    ) {
                                        Icon(Icons.Filled.ArrowDownward, contentDescription = "Move step down", modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = { editingStep = step },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("details_step_edit_${idx + 1}")
                                    ) {
                                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.automation_edit), modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            onUpdateSteps(workflow.id, steps.filterNot { it.id == step.id })
                                        },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("details_step_delete_${idx + 1}")
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

                // Execution History for this workflow
                item(key = "details_history_header") {
                    MaterialGroupSectionHeader(
                        title = "Execution History (${executions.size})",
                        icon = Icons.Outlined.History
                    )
                }

                if (executions.isEmpty()) {
                    item(key = "details_history_empty") {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "No executions recorded for this workflow yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    itemsIndexed(
                        items = executions,
                        key = { _, e -> "hist_${e.id}" }
                    ) { idx, exec ->
                        val ok = exec.status == "COMPLETED"
                        MaterialGroupListItem(
                            index = idx,
                            totalCount = executions.size,
                            title = "${formatExecutionStatusLabel(exec.status)} • ${exec.completedSteps}/${exec.totalSteps} steps",
                            subtitle = "${dateFormatter.format(Date(exec.startedAt))} — ${exec.verificationSummary}",
                            leadingContent = {
                                Icon(
                                    imageVector = if (ok) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                                    contentDescription = null,
                                    tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
