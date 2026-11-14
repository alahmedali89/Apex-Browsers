package com.example.automation

import org.json.JSONArray
import org.json.JSONObject

enum class AutomationToolbarStatus {
    IDLE,
    ELEMENTS_DETECTED,
    RECORDING,
    PAUSED,
    EXECUTING,
    COMPLETED,
    ERROR
}

enum class AutomationHudMode {
    OVERVIEW,
    RECORDING,
    INSTRUCTIONS,
    ELEMENTS,
    SAVED,
    RECENT
}

enum class AutomationType(val id: String, val label: String) {
    RECORDED("RECORDED", "Recorded"),
    MANUAL("MANUAL", "Manual");

    companion object {
        fun fromId(raw: String?): AutomationType {
            return entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: MANUAL
        }
    }
}

enum class AutomationActionType(val id: String, val label: String) {
    CLICK("CLICK", "Click"),
    INPUT("INPUT", "Enter Text"),
    SELECT("SELECT", "Select"),
    TOGGLE("TOGGLE", "Toggle"),
    SCROLL("SCROLL", "Scroll"),
    NAVIGATE("NAVIGATE", "Navigate"),
    EXTRACT("EXTRACT", "Extract"),
    SUBMIT("SUBMIT", "Submit");

    companion object {
        fun fromId(raw: String?): AutomationActionType {
            return entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: CLICK
        }
    }
}

enum class RecordingStatus {
    IDLE,
    RECORDING,
    PAUSED,
    STOPPED,
    CANCELLED,
    ERROR
}

enum class AutomationWorkspaceCategory(val label: String) {
    ALL("All"),
    RECORDED("Recorded"),
    MANUAL("Manual"),
    RECENT("Recent")
}

data class AutomationStep(
    val id: String,
    val order: Int,
    val actionType: AutomationActionType,
    val targetLabel: String,
    val selector: String = "",
    val value: String = "",
    val description: String,
    val isDestructive: Boolean = false
)

data class DetectedPageElement(
    val id: String,
    val tagName: String,
    val role: String,
    val label: String,
    val selector: String,
    val inputType: String = "",
    val isInteractive: Boolean = true
)

data class PageInspectionState(
    val domain: String = "Start Page",
    val url: String = "apex://home",
    val elements: List<DetectedPageElement> = emptyList(),
    val isScanning: Boolean = false,
    val lastScannedAt: Long? = null,
    val errorMessage: String? = null
)

data class RecordingErrorInfo(
    val code: AutomationErrorCode,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class RecordedDomEvent(
    val sessionId: String,
    val tabId: String,
    val origin: String,
    val sequenceNumber: Long,
    val eventType: String,
    val targetLabel: String,
    val selector: String,
    val value: String,
    val isDestructive: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

sealed class RecordingOriginResult {
    data class Valid(
        val canonicalOrigin: String,
        val displayOrigin: String,
        val scheme: String,
        val host: String,
        val effectivePort: Int
    ) : RecordingOriginResult()

    data class OpaqueOrUnsupported(
        val scheme: String,
        val reason: String
    ) : RecordingOriginResult()
}

data class RecorderLifecycleResult(
    val success: Boolean,
    val errorCode: AutomationErrorCode = if (success) AutomationErrorCode.NONE else AutomationErrorCode.RECORDING_INSTALL_FAILED,
    val message: String = ""
)

data class ActiveRecordingSession(
    val sessionId: String = "",
    val initiatingTabId: String = "",
    val initialOrigin: String = "",
    val currentOrigin: String = "",
    val status: RecordingStatus = RecordingStatus.IDLE,
    val steps: List<AutomationStep> = emptyList(),
    val targetDomain: String = "",
    val targetUrl: String = "",
    val suggestedName: String = "",
    val startedAt: Long = 0L,
    val lastSequenceNumber: Long = 0L,
    val recordingError: RecordingErrorInfo? = null,
    val pendingOriginChangeUrl: String? = null
)

enum class AutomationErrorCode {
    NONE,
    EMPTY_WORKFLOW,
    UNSUPPORTED_SCHEMA,
    INVALID_URL,
    INVALID_SELECTOR,
    ELEMENT_NOT_FOUND,
    ELEMENT_AMBIGUOUS,
    ELEMENT_DISABLED,
    OPTION_NOT_FOUND,
    JS_TIMEOUT,
    JS_EXCEPTION,
    WEBVIEW_UNAVAILABLE,
    NAVIGATION_FAILED,
    NAVIGATION_TIMEOUT,
    CANCELLED,
    RECORDING_TAB_CLOSED,
    RECORDING_ORIGIN_CHANGED,
    RECORDING_SESSION_INVALID,
    RECORDING_SESSION_MISMATCH,
    RECORDING_TAB_MISMATCH,
    RECORDING_DOCUMENT_UNAVAILABLE,
    RECORDING_INSTALL_FAILED,
    RECORDING_CLEANUP_FAILED
}

data class WorkflowExecutionState(
    val workflowId: Long,
    val workflowName: String,
    val status: String, // RUNNING, COMPLETED, FAILED, CANCELLED
    val currentStepIndex: Int,
    val totalSteps: Int,
    val stepLogs: List<String> = emptyList(),
    val verificationMessage: String = "",
    val isAwaitingConfirmation: Boolean = false,
    val pendingDestructiveSteps: List<AutomationStep> = emptyList(),
    val errorCode: AutomationErrorCode = AutomationErrorCode.NONE
)

sealed class AutomationStepDecodeResult {
    data class Success(
        val schemaVersion: Int,
        val steps: List<AutomationStep>,
        val isLegacyUnversioned: Boolean
    ) : AutomationStepDecodeResult()

    data class UnsupportedVersion(
        val foundVersion: Int,
        val message: String
    ) : AutomationStepDecodeResult()

    data class MalformedJson(
        val message: String
    ) : AutomationStepDecodeResult()

    fun stepsOrEmpty(): List<AutomationStep> = when (this) {
        is Success -> steps
        is UnsupportedVersion, is MalformedJson -> emptyList()
    }
}

object AutomationStepCodec {
    const val CURRENT_SCHEMA_VERSION = 1

    fun encode(steps: List<AutomationStep>): String {
        val root = JSONObject()
        root.put("schemaVersion", CURRENT_SCHEMA_VERSION)
        root.put("steps", encodeStepsArray(steps))
        return root.toString()
    }

    fun encodeStepsArray(steps: List<AutomationStep>): JSONArray {
        val array = JSONArray()
        steps.forEachIndexed { index, step ->
            val obj = JSONObject()
            obj.put("id", step.id)
            obj.put("order", index + 1)
            obj.put("actionType", step.actionType.id)
            obj.put("targetLabel", step.targetLabel)
            obj.put("selector", step.selector)
            obj.put("value", step.value)
            obj.put("description", step.description)
            obj.put("isDestructive", step.isDestructive)
            array.put(obj)
        }
        return array
    }

    fun decodeSafely(rawJson: String?): AutomationStepDecodeResult {
        if (rawJson.isNullOrBlank()) {
            return AutomationStepDecodeResult.Success(
                schemaVersion = CURRENT_SCHEMA_VERSION,
                steps = emptyList(),
                isLegacyUnversioned = true
            )
        }
        val trimmed = rawJson.trim()
        return try {
            when {
                trimmed.startsWith("[") -> {
                    val array = JSONArray(trimmed)
                    AutomationStepDecodeResult.Success(
                        schemaVersion = 0,
                        steps = parseStepArray(array),
                        isLegacyUnversioned = true
                    )
                }
                trimmed.startsWith("{") -> {
                    val root = JSONObject(trimmed)
                    if (!root.has("schemaVersion")) {
                        val array = root.optJSONArray("steps")
                            ?: return AutomationStepDecodeResult.MalformedJson("Workflow steps array is missing.")
                        AutomationStepDecodeResult.Success(
                            schemaVersion = 0,
                            steps = parseStepArray(array),
                            isLegacyUnversioned = true
                        )
                    } else {
                        val rawVersion = root.opt("schemaVersion")
                        val version = (rawVersion as? Number)?.toInt()
                            ?: return AutomationStepDecodeResult.UnsupportedVersion(
                                foundVersion = -1,
                                message = "Unsupported workflow steps schema version: '$rawVersion'."
                            )
                        if (version < 1 || version > CURRENT_SCHEMA_VERSION) {
                            AutomationStepDecodeResult.UnsupportedVersion(
                                foundVersion = version,
                                message = "Unsupported workflow steps schema version $version (supported: v1..v$CURRENT_SCHEMA_VERSION)."
                            )
                        } else {
                            val array = root.optJSONArray("steps")
                                ?: return AutomationStepDecodeResult.MalformedJson("Workflow steps array is missing.")
                            AutomationStepDecodeResult.Success(
                                schemaVersion = version,
                                steps = parseStepArray(array),
                                isLegacyUnversioned = false
                            )
                        }
                    }
                }
                else -> AutomationStepDecodeResult.MalformedJson("Invalid workflow steps JSON format.")
            }
        } catch (t: Throwable) {
            AutomationStepDecodeResult.MalformedJson(t.message ?: "Failed to parse workflow steps JSON.")
        }
    }

    private fun parseStepArray(array: JSONArray): List<AutomationStep> = buildList {
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optString("id").ifBlank { "step_${i + 1}" }
            val actionType = AutomationActionType.fromId(obj.optString("actionType"))
            val targetLabel = obj.optString("targetLabel", "")
            val selector = obj.optString("selector", "")
            val value = obj.optString("value", "")
            val description = obj.optString("description", "").ifBlank {
                buildStepSummary(actionType, targetLabel, value)
            }
            val isDestructive = obj.optBoolean("isDestructive", false)
            add(
                AutomationStep(
                    id = id,
                    order = i + 1,
                    actionType = actionType,
                    targetLabel = targetLabel,
                    selector = selector,
                    value = value,
                    description = description,
                    isDestructive = isDestructive
                )
            )
        }
    }

    fun decode(rawJson: String?): List<AutomationStep> {
        return decodeSafely(rawJson).stepsOrEmpty()
    }

    fun isClauseDestructive(lowerClause: String): Boolean {
        val keywords = listOf(
            "delete", "remove", "erase", "wipe", "clear all",
            "purchase", "buy now", "checkout", "pay ", "payment",
            "submit", "transfer", "send money", "confirm order", "unsubscribe"
        )
        return keywords.any { lowerClause.contains(it) }
    }

    fun applyStepEdit(
        existing: AutomationStep,
        description: String,
        actionType: AutomationActionType,
        value: String,
        targetLabel: String = existing.targetLabel,
        selector: String = existing.selector,
        destructiveClassifier: (String) -> Boolean = { clause -> isClauseDestructive(clause) }
    ): AutomationStep {
        val cleanDesc = description.trim().ifBlank { existing.description }
        val cleanLabel = targetLabel.trim().ifBlank { existing.targetLabel }
        val cleanSelector = selector.trim()
        val preservedOrEscalatedDestructive = existing.isDestructive ||
            actionType == AutomationActionType.SUBMIT ||
            destructiveClassifier(cleanDesc.lowercase()) ||
            destructiveClassifier(cleanLabel.lowercase())
        return existing.copy(
            description = cleanDesc,
            actionType = actionType,
            targetLabel = cleanLabel,
            selector = cleanSelector,
            value = value,
            isDestructive = preservedOrEscalatedDestructive
        )
    }

    fun buildStepSummary(
        actionType: AutomationActionType,
        targetLabel: String,
        value: String = ""
    ): String {
        val cleanTarget = targetLabel.trim().ifBlank { "page element" }
        return when (actionType) {
            AutomationActionType.CLICK -> "$cleanTarget clicked"
            AutomationActionType.INPUT -> if (value.isNotBlank()) {
                "$cleanTarget entered"
            } else {
                "$cleanTarget field focused"
            }
            AutomationActionType.SELECT -> "$cleanTarget selected"
            AutomationActionType.TOGGLE -> "$cleanTarget toggled"
            AutomationActionType.SCROLL -> "Scroll $cleanTarget"
            AutomationActionType.NAVIGATE -> "Navigate to $cleanTarget"
            AutomationActionType.EXTRACT -> "Extract $cleanTarget"
            AutomationActionType.SUBMIT -> "Submit $cleanTarget"
        }
    }

    fun reindex(steps: List<AutomationStep>): List<AutomationStep> {
        return steps.mapIndexed { idx, step -> step.copy(order = idx + 1) }
    }
}
