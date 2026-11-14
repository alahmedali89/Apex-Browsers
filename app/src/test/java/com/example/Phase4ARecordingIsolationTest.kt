package com.example

import android.content.Context
import android.os.Looper
import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.automation.ActiveRecordingSession
import com.example.automation.AutomationActionType
import com.example.automation.AutomationEngine
import com.example.automation.AutomationErrorCode
import com.example.automation.AutomationHudMode
import com.example.automation.AutomationStep
import com.example.automation.PageInspectionState
import com.example.automation.RecordedDomEvent
import com.example.automation.RecordingErrorInfo
import com.example.automation.RecordingOriginResult
import com.example.automation.RecordingStatus
import com.example.data.local.ApexDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.BrowserRepository
import com.example.engine.FastDownloadEngine
import com.example.ui.browser.AutomationHudSheet
import com.example.ui.theme.ApexBrowserTheme
import com.example.ui.viewmodel.BrowserViewModel
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase4ARecordingIsolationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private class RecordingFakeWebView(
        context: Context,
        var fakeUrl: String? = null,
        private val onEval: ((script: String, callback: ValueCallback<String>?) -> Unit)? = null
    ) : WebView(context) {
        val evaluatedScripts = mutableListOf<String>()
        var destroyedBeforeDetach = false
        var wasDestroyed = false

        override fun getUrl(): String? = fakeUrl

        override fun evaluateJavascript(script: String, resultCallback: ValueCallback<String>?) {
            evaluatedScripts.add(script)
            if (onEval != null) {
                onEval.invoke(script, resultCallback)
                return
            }
            val sidMatch = Regex("""sessionId:\s*("(?:[^"\\]|\\.)*")""").find(script)
            val sid = sidMatch?.groupValues?.getOrNull(1)?.let { JSONObject("{\"v\":$it}").optString("v") } ?: ""
            val payload = if (script.contains("__apexRecorderInstance = inst")) {
                JSONObject().apply {
                    put("ok", true)
                    put("sessionId", sid)
                }.toString()
            } else {
                JSONObject().apply {
                    put("ok", true)
                    put("removed", true)
                }.toString()
            }
            resultCallback?.onReceiveValue(JSONObject.quote(payload))
        }

        override fun destroy() {
            val sawUninstall = evaluatedScripts.any { it.contains("var inst = window.__apexRecorderInstance") }
            if (!sawUninstall && evaluatedScripts.any { it.contains("__apexRecorderInstance = inst") }) {
                destroyedBeforeDetach = true
            }
            wasDestroyed = true
            super.destroy()
        }
    }

    private fun createTestContextAndVm(
        engine: AutomationEngine = AutomationEngine()
    ): Triple<ApexDatabase, BrowserViewModel, Context> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val prefsRepo = UserPreferencesRepository(context)
        val sharedPrefs = context.getSharedPreferences("phase4a_test_${UUID.randomUUID()}", Context.MODE_PRIVATE)
        val repo = BrowserRepository(db.apexDao(), prefsRepo, sharedPrefs)
        val dl = FastDownloadEngine(context, repo)
        val vm = BrowserViewModel(
            repository = repo,
            fastDownloadEngine = dl,
            automationEngine = engine
        )
        return Triple(db, vm, context)
    }

    @Test
    fun parseOrigin_normalizesDefaultPorts_andDistinguishesSchemesPortsSubdomainsAndLookalikes() {
        val engine = AutomationEngine()

        val httpsDefault = engine.parseOrigin("https://Example.COM/login?next=/home")
        val httpsExplicit443 = engine.parseOrigin("https://example.com:443/account")
        assertTrue(httpsDefault is RecordingOriginResult.Valid)
        assertEquals(httpsDefault, httpsExplicit443)
        assertEquals("https://example.com:443", (httpsDefault as RecordingOriginResult.Valid).canonicalOrigin)

        val https8443 = engine.parseOrigin("https://example.com:8443/login")
        val http80 = engine.parseOrigin("http://example.com/login")
        val subdomain = engine.parseOrigin("https://sub.example.com/login")
        val lookalike = engine.parseOrigin("https://example.com.evil.test/login")
        val userinfoSpoof = engine.parseOrigin("https://example.com@evil.test/login")

        assertTrue(https8443 is RecordingOriginResult.Valid)
        assertTrue(http80 is RecordingOriginResult.Valid)
        assertTrue(subdomain is RecordingOriginResult.Valid)
        assertTrue(lookalike is RecordingOriginResult.Valid)
        assertTrue(userinfoSpoof is RecordingOriginResult.Valid)

        val canonicals = setOf(
            httpsDefault.canonicalOrigin,
            (https8443 as RecordingOriginResult.Valid).canonicalOrigin,
            (http80 as RecordingOriginResult.Valid).canonicalOrigin,
            (subdomain as RecordingOriginResult.Valid).canonicalOrigin,
            (lookalike as RecordingOriginResult.Valid).canonicalOrigin
        )
        assertEquals(5, canonicals.size)
        assertNotEquals(httpsDefault.canonicalOrigin, (userinfoSpoof as RecordingOriginResult.Valid).canonicalOrigin)

        val opaqueInputs = listOf(
            "",
            "null",
            "about:blank",
            "data:text/html,<h1>Hi</h1>",
            "file:///sdcard/test.html",
            "javascript:alert(1)",
            "blob:https://example.com/abc",
            "apex://home",
            "apex://news"
        )
        opaqueInputs.forEach { raw ->
            assertTrue("Expected OpaqueOrUnsupported for $raw", engine.parseOrigin(raw) is RecordingOriginResult.OpaqueOrUnsupported)
        }
    }

    @Test
    fun startRecording_bindsSessionTabOrigin_andFailsCleanlyOnOpaqueOrMissingDocumentOrTimeout() = runBlocking {
        val shortEngine = AutomationEngine(jsEvalTimeoutMs = 150L)
        val (db, vm, context) = createTestContextAndVm(shortEngine)
        try {
            // 1. Fail on apex://home (opaque internal page)
            vm.startRecording()
            val homeSession = vm.uiState.value.recordingSession
            assertEquals(RecordingStatus.ERROR, homeSession.status)
            assertEquals(AutomationErrorCode.RECORDING_DOCUMENT_UNAVAILABLE, homeSession.recordingError?.code)
            assertTrue(homeSession.sessionId.isEmpty())

            // 2. Fail on HTTP tab without registered WebView
            val tabA = vm.uiState.value.activeTabId
            vm.submitNavigation("https://shop.example.com/checkout", tabA)
            vm.startRecording()
            val noWvSession = vm.uiState.value.recordingSession
            assertEquals(RecordingStatus.ERROR, noWvSession.status)
            assertEquals(AutomationErrorCode.RECORDING_DOCUMENT_UNAVAILABLE, noWvSession.recordingError?.code)

            // 3. Fail cleanly if JS installation fails / times out (never reports RECORDING prematurely)
            val failingWv = RecordingFakeWebView(context, "https://shop.example.com/checkout") { _, cb ->
                val failJson = JSONObject().put("ok", false).put("reason", "origin_mismatch").toString()
                cb?.onReceiveValue(JSONObject.quote(failJson))
            }
            vm.registerWebView(tabA, failingWv)
            vm.startRecording()
            val failedInstall = vm.uiState.value.recordingSession
            assertEquals(RecordingStatus.ERROR, failedInstall.status)
            assertEquals(AutomationErrorCode.RECORDING_ORIGIN_CHANGED, failedInstall.recordingError?.code)

            // 4. Fail on JS timeout
            val hangingWv = RecordingFakeWebView(context, "https://shop.example.com/checkout") { _, _ ->
                // Intentionally never invokes callback
            }
            vm.registerWebView(tabA, hangingWv)
            vm.startRecording()
            delay(250L)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertEquals(RecordingStatus.ERROR, vm.uiState.value.recordingSession.status)
            assertEquals(AutomationErrorCode.RECORDING_INSTALL_FAILED, vm.uiState.value.recordingSession.recordingError?.code)

            // 5. Succeed cleanly when valid WebView confirms installation
            val goodWv = RecordingFakeWebView(context, "https://shop.example.com/checkout")
            vm.registerWebView(tabA, goodWv)
            vm.startRecording()
            val active = vm.uiState.value.recordingSession
            assertEquals(RecordingStatus.RECORDING, active.status)
            assertTrue(active.sessionId.isNotBlank())
            assertEquals(tabA, active.initiatingTabId)
            assertEquals("https://shop.example.com:443", active.initialOrigin)
            assertEquals("https://shop.example.com:443", active.currentOrigin)
            assertEquals(0L, active.lastSequenceNumber)
            assertTrue(goodWv.evaluatedScripts.last().contains(active.sessionId))
            assertTrue(goodWv.evaluatedScripts.last().contains(tabA))
        } finally {
            db.close()
        }
    }

    @Test
    fun tabSwitching_andForeignTabOrWebViewEvents_areStrictlyIsolated() {
        val (db, vm, context) = createTestContextAndVm()
        try {
            val tabA = vm.uiState.value.activeTabId
            val wvA = RecordingFakeWebView(context, "https://app.example.com/form")
            vm.submitNavigation("https://app.example.com/form", tabA)
            vm.registerWebView(tabA, wvA)

            vm.startRecording()
            val session = vm.uiState.value.recordingSession
            assertEquals(RecordingStatus.RECORDING, session.status)

            // Create Tab B with same origin and switch to it
            vm.createNewTab("https://app.example.com/other")
            val tabB = vm.uiState.value.activeTabId
            assertNotEquals(tabA, tabB)
            val wvB = RecordingFakeWebView(context, "https://app.example.com/other")
            vm.registerWebView(tabB, wvB)
            val scriptsBeforeSwitch = wvB.evaluatedScripts.size

            vm.selectTab(tabB)
            assertEquals(scriptsBeforeSwitch, wvB.evaluatedScripts.size)
            assertTrue(wvB.evaluatedScripts.none { it.contains("__apexRecorderInstance = inst") })
            assertEquals(RecordingStatus.RECORDING, vm.uiState.value.recordingSession.status)
            assertEquals(tabA, vm.uiState.value.recordingSession.initiatingTabId)

            // Page finish on Tab B must NEVER install the recorder into Tab B
            vm.onWebViewPageFinished(tabB, "Other", "https://app.example.com/other", false, false)
            assertTrue(wvB.evaluatedScripts.none { it.contains("__apexRecorderInstance = inst") })

            // Event from Tab B (wrong tabId / sourceTabId) rejected
            val resTabMismatch = vm.processRecordedDomEvent(
                RecordedDomEvent(session.sessionId, tabB, "https://app.example.com", 1L, "CLICK", "Save", "#save", "", false),
                tabB, wvB, "https://app.example.com/other"
            )
            assertFalse(resTabMismatch.success)
            assertEquals(AutomationErrorCode.RECORDING_TAB_MISMATCH, resTabMismatch.errorCode)

            // Spoofed event pretending tabId == tabA coming from native WebView wvB rejected
            val resWvMismatch = vm.processRecordedDomEvent(
                RecordedDomEvent(session.sessionId, tabA, "https://app.example.com", 1L, "CLICK", "Save", "#save", "", false),
                tabA, wvB, "https://app.example.com/form"
            )
            assertFalse(resWvMismatch.success)
            assertEquals(AutomationErrorCode.RECORDING_TAB_MISMATCH, resWvMismatch.errorCode)
            assertTrue(vm.uiState.value.recordingSession.steps.isEmpty())

            // Valid event from Tab A still succeeds while user is viewing Tab B
            val resValid = vm.processRecordedDomEvent(
                RecordedDomEvent(session.sessionId, tabA, "https://app.example.com", 1L, "CLICK", "Submit", "#btn", "", false),
                tabA, wvA, "https://app.example.com/form"
            )
            assertTrue(resValid.success)
            assertEquals(1, vm.uiState.value.recordingSession.steps.size)
            assertEquals(1L, vm.uiState.value.recordingSession.lastSequenceNumber)
        } finally {
            db.close()
        }
    }

    @Test
    fun sameOriginNavigationContinues_andCrossOriginOrOpaqueNavigationStopsRecordingAndPreservesSteps() {
        val (db, vm, context) = createTestContextAndVm()
        try {
            val tabA = vm.uiState.value.activeTabId
            val wvA = RecordingFakeWebView(context, "https://portal.example.com/step1")
            vm.submitNavigation("https://portal.example.com/step1", tabA)
            vm.registerWebView(tabA, wvA)
            vm.startRecording()
            val sid = vm.uiState.value.recordingSession.sessionId

            assertTrue(
                vm.processRecordedDomEvent(
                    RecordedDomEvent(sid, tabA, "https://portal.example.com", 1L, "INPUT", "Name", "#name", "Ada", false),
                    tabA, wvA, "https://portal.example.com/step1"
                ).success
            )

            // Same-origin SPA/document navigation preserves session & reinstalls cleanly with seq=1
            wvA.fakeUrl = "https://portal.example.com/step2"
            vm.onWebViewPageStarted(tabA, "https://portal.example.com/step2")
            vm.onWebViewUrlCommitted(tabA, "https://portal.example.com/step2")
            vm.onWebViewPageFinished(tabA, "Step 2", "https://portal.example.com/step2", true, false)

            assertEquals(RecordingStatus.RECORDING, vm.uiState.value.recordingSession.status)
            assertEquals(sid, vm.uiState.value.recordingSession.sessionId)
            assertTrue(wvA.evaluatedScripts.last().contains("seq: 1"))

            // Cross-origin navigation (e.g. redirect/click to evil.example.org) immediately stops recording
            wvA.fakeUrl = "https://evil.example.org/landing"
            vm.onWebViewUrlCommitted(tabA, "https://evil.example.org/landing")

            val stopped = vm.uiState.value.recordingSession
            assertEquals(RecordingStatus.STOPPED, stopped.status)
            assertEquals("", stopped.sessionId)
            assertEquals(AutomationErrorCode.RECORDING_ORIGIN_CHANGED, stopped.recordingError?.code)
            assertEquals(1, stopped.steps.size)
            assertTrue(wvA.evaluatedScripts.last().contains("var inst = window.__apexRecorderInstance"))

            // Subsequent spoofed/late event on new origin is rejected
            val late = vm.processRecordedDomEvent(
                RecordedDomEvent(sid, tabA, "https://evil.example.org", 2L, "CLICK", "Hack", "#x", "", false),
                tabA, wvA, "https://evil.example.org/landing"
            )
            assertFalse(late.success)
            assertEquals(1, vm.uiState.value.recordingSession.steps.size)
        } finally {
            db.close()
        }
    }

    @Test
    fun opaqueOrSubdomainOrPortChange_andDocumentFailure_haltRecording() {
        val (db, vm, context) = createTestContextAndVm()
        try {
            // Subdomain cross-origin stop
            val tabA = vm.uiState.value.activeTabId
            val wvA = RecordingFakeWebView(context, "https://example.com/home")
            vm.submitNavigation("https://example.com/home", tabA)
            vm.registerWebView(tabA, wvA)
            vm.startRecording()
            vm.onWebViewPageStarted(tabA, "https://sub.example.com/home")
            assertEquals(RecordingStatus.STOPPED, vm.uiState.value.recordingSession.status)
            assertEquals(AutomationErrorCode.RECORDING_ORIGIN_CHANGED, vm.uiState.value.recordingSession.recordingError?.code)
            vm.discardRecording()

            // data: URI navigation stop
            wvA.fakeUrl = "https://example.com/home"
            vm.submitNavigation("https://example.com/home", tabA)
            vm.startRecording()
            vm.onWebViewPageStarted(tabA, "data:text/html,<script></script>")
            assertEquals(RecordingStatus.STOPPED, vm.uiState.value.recordingSession.status)
            assertEquals(AutomationErrorCode.RECORDING_ORIGIN_CHANGED, vm.uiState.value.recordingSession.recordingError?.code)
            vm.discardRecording()

            // Document load failure stops with ERROR + RECORDING_DOCUMENT_UNAVAILABLE
            wvA.fakeUrl = "https://example.com/home"
            vm.submitNavigation("https://example.com/home", tabA)
            vm.startRecording()
            vm.onWebViewPageLoadFailedWithReason(tabA, "https://example.com/home", "ERR_NAME_NOT_RESOLVED")
            val errored = vm.uiState.value.recordingSession
            assertEquals(RecordingStatus.ERROR, errored.status)
            assertEquals("", errored.sessionId)
            assertEquals(AutomationErrorCode.RECORDING_DOCUMENT_UNAVAILABLE, errored.recordingError?.code)
        } finally {
            db.close()
        }
    }

    @Test
    fun closingInitiatingTab_cancelsRecordingAndDetachesBeforeDestroy_acrossClosePaths() {
        val (db, vm, context) = createTestContextAndVm()
        try {
            // 1. closeTab on initiating tab cancels & detaches before destroy
            val tabA = vm.uiState.value.activeTabId
            val wvA = RecordingFakeWebView(context, "https://bank.example.com")
            vm.submitNavigation("https://bank.example.com", tabA)
            vm.registerWebView(tabA, wvA)
            vm.startRecording()
            assertEquals(RecordingStatus.RECORDING, vm.uiState.value.recordingSession.status)

            // Closing a non-initiating tab leaves recording active
            vm.createNewTab("https://news.example.com", stayInSwitcher = false)
            val tabB = vm.uiState.value.activeTabId
            vm.closeTab(tabB)
            assertEquals(RecordingStatus.RECORDING, vm.uiState.value.recordingSession.status)

            // Closing initiating tab cancels recording
            vm.closeTab(tabA)
            val cancelled = vm.uiState.value.recordingSession
            assertEquals(RecordingStatus.CANCELLED, cancelled.status)
            assertEquals("", cancelled.sessionId)
            assertEquals(AutomationErrorCode.RECORDING_TAB_CLOSED, cancelled.recordingError?.code)
            assertFalse(wvA.destroyedBeforeDetach)

            // 2. closeSelectedTabs cancels paused recording on initiating tab
            val tabC = vm.uiState.value.activeTabId
            val wvC = RecordingFakeWebView(context, "https://app.example.com")
            vm.submitNavigation("https://app.example.com", tabC)
            vm.registerWebView(tabC, wvC)
            vm.startRecording()
            vm.pauseRecording()
            assertEquals(RecordingStatus.PAUSED, vm.uiState.value.recordingSession.status)
            vm.closeSelectedTabs(setOf(tabC))
            assertEquals(RecordingStatus.CANCELLED, vm.uiState.value.recordingSession.status)
            assertEquals(AutomationErrorCode.RECORDING_TAB_CLOSED, vm.uiState.value.recordingSession.recordingError?.code)

            // 3. closeAllIncognitoTabs cancels recording started inside an incognito tab
            vm.createNewIncognitoTab("https://private.example.com")
            val incTab = vm.uiState.value.activeTabId
            val wvInc = RecordingFakeWebView(context, "https://private.example.com")
            vm.registerWebView(incTab, wvInc)
            vm.startRecording()
            assertEquals(RecordingStatus.RECORDING, vm.uiState.value.recordingSession.status)
            vm.closeAllIncognitoTabs()
            assertEquals(RecordingStatus.CANCELLED, vm.uiState.value.recordingSession.status)
            assertEquals(AutomationErrorCode.RECORDING_TAB_CLOSED, vm.uiState.value.recordingSession.recordingError?.code)
        } finally {
            db.close()
        }
    }

    @Test
    fun pauseResumeStopDiscard_sequenceReplay_andStaleSessionValidation() {
        val (db, vm, context) = createTestContextAndVm()
        try {
            val tabA = vm.uiState.value.activeTabId
            val wvA = RecordingFakeWebView(context, "https://docs.example.com/edit")
            vm.submitNavigation("https://docs.example.com/edit", tabA)
            vm.registerWebView(tabA, wvA)

            vm.startRecording()
            val sid1 = vm.uiState.value.recordingSession.sessionId
            assertTrue(
                vm.processRecordedDomEvent(
                    RecordedDomEvent(sid1, tabA, "https://docs.example.com", 1L, "CLICK", "Edit", "#edit", "", false),
                    tabA, wvA, "https://docs.example.com/edit"
                ).success
            )

            // Duplicate / replayed sequence 1 rejected
            val replay = vm.processRecordedDomEvent(
                RecordedDomEvent(sid1, tabA, "https://docs.example.com", 1L, "CLICK", "Edit", "#edit", "", false),
                tabA, wvA, "https://docs.example.com/edit"
            )
            assertFalse(replay.success)
            assertEquals(AutomationErrorCode.RECORDING_SESSION_INVALID, replay.errorCode)

            // Stale / wrong sessionId rejected without disturbing active session
            val mismatch = vm.processRecordedDomEvent(
                RecordedDomEvent("old_session_id", tabA, "https://docs.example.com", 2L, "CLICK", "Delete", "#del", "", true),
                tabA, wvA, "https://docs.example.com/edit"
            )
            assertFalse(mismatch.success)
            assertEquals(AutomationErrorCode.RECORDING_SESSION_MISMATCH, mismatch.errorCode)
            assertEquals(RecordingStatus.RECORDING, vm.uiState.value.recordingSession.status)

            // Pause uninstalls & drops events; Resume validates origin and continues sequence
            vm.pauseRecording()
            assertEquals(RecordingStatus.PAUSED, vm.uiState.value.recordingSession.status)
            assertFalse(
                vm.processRecordedDomEvent(
                    RecordedDomEvent(sid1, tabA, "https://docs.example.com", 2L, "CLICK", "WhilePaused", "#p", "", false),
                    tabA, wvA, "https://docs.example.com/edit"
                ).success
            )
            vm.resumeRecording()
            assertEquals(RecordingStatus.RECORDING, vm.uiState.value.recordingSession.status)

            // Pause then cross-origin nav -> resume refuses to reattach
            vm.pauseRecording()
            wvA.fakeUrl = "https://other.example.net"
            vm.submitNavigation("https://other.example.net", tabA)
            assertEquals(RecordingStatus.STOPPED, vm.uiState.value.recordingSession.status)
            vm.resumeRecording()
            assertEquals(RecordingStatus.STOPPED, vm.uiState.value.recordingSession.status)

            // Discard from another tab still detaches initiating tab
            vm.discardRecording()
            assertEquals(RecordingStatus.IDLE, vm.uiState.value.recordingSession.status)
            assertTrue(vm.uiState.value.recordingSession.steps.isEmpty())

            // Stop recording invalidates session and rejects late events
            wvA.fakeUrl = "https://docs.example.com/edit"
            vm.submitNavigation("https://docs.example.com/edit", tabA)
            vm.startRecording()
            val sid2 = vm.uiState.value.recordingSession.sessionId
            assertNotEquals(sid1, sid2)
            vm.stopRecording()
            assertEquals(RecordingStatus.STOPPED, vm.uiState.value.recordingSession.status)
            assertEquals("", vm.uiState.value.recordingSession.sessionId)
            val lateAfterStop = vm.processRecordedDomEvent(
                RecordedDomEvent(sid2, tabA, "https://docs.example.com", 1L, "CLICK", "Late", "#late", "", false),
                tabA, wvA, "https://docs.example.com/edit"
            )
            assertFalse(lateAfterStop.success)
            assertEquals(AutomationErrorCode.RECORDING_SESSION_INVALID, lateAfterStop.errorCode)
        } finally {
            db.close()
        }
    }

    @Test
    fun passwordProtection_neverRecordsBulletsOrSecretValues_andScriptIsIdempotentSingleInstance() {
        val engine = AutomationEngine()
        val installJs = engine.buildRecorderInstallScript(
            sessionId = "sess_pw_1",
            tabId = "tab_pw_1",
            expectedOrigin = "https://auth.example.com:443",
            initialSequenceNumber = 0L
        )
        // Must NEVER emit fake bullet mask or capture password text
        assertFalse(installJs.contains("••••••"))
        assertTrue(installJs.contains("type === 'password' ? ''"))
        assertTrue(installJs.contains("window.__apexRecorderInstance"))
        assertTrue(installJs.contains("document.removeEventListener('focusin'"))
        assertTrue(installJs.contains("document.removeEventListener('change'"))
        assertTrue(installJs.contains("document.removeEventListener('click'"))
        assertTrue(installJs.contains("onRecordedEventJson"))
        assertFalse(installJs.contains("ApexAutomationBridge.onRecordedEvent("))

        val (db, vm, context) = createTestContextAndVm(engine)
        try {
            val tabA = vm.uiState.value.activeTabId
            val wvA = RecordingFakeWebView(context, "https://auth.example.com/signin")
            vm.submitNavigation("https://auth.example.com/signin", tabA)
            vm.registerWebView(tabA, wvA)
            vm.startRecording()
            val sid = vm.uiState.value.recordingSession.sessionId

            val res = vm.processRecordedDomEvent(
                RecordedDomEvent(sid, tabA, "https://auth.example.com", 1L, "INPUT", "Password", "#password", "••••••", false),
                tabA, wvA, "https://auth.example.com/signin"
            )
            assertTrue(res.success)
            val captured = vm.uiState.value.recordingSession.steps.single()
            assertEquals("", captured.value)
        } finally {
            db.close()
        }
    }

    @Test
    fun recordingHud_rendersCancelledErrorAndOtherTabStatusCleanly() {
        var sessionState = ActiveRecordingSession(
            sessionId = "",
            initiatingTabId = "tab_1",
            status = RecordingStatus.CANCELLED,
            recordingError = RecordingErrorInfo(
                AutomationErrorCode.RECORDING_TAB_CLOSED,
                "Recording cancelled because the recording tab was closed."
            )
        )
        composeTestRule.setContent {
            ApexBrowserTheme(dynamicColor = false) {
                AutomationHudSheet(
                    hudMode = AutomationHudMode.RECORDING,
                    pageInspection = PageInspectionState(domain = "example.com", url = "https://example.com"),
                    recordingSession = sessionState,
                    activeTabId = "tab_2",
                    instructionDraftText = "",
                    instructionDraftSteps = emptyList(),
                    executionState = null,
                    workflows = emptyList(),
                    executions = emptyList(),
                    onSelectMode = {},
                    onScanPage = {},
                    onStartRecording = {},
                    onPauseRecording = {},
                    onResumeRecording = {},
                    onStopRecording = {},
                    onMinimizeRecording = {},
                    onRenameRecordingDraft = {},
                    onEditRecordingStep = { _, _, _ -> },
                    onDeleteRecordingStep = {},
                    onSaveRecording = {},
                    onDiscardRecording = {},
                    onInstructionTextChange = {},
                    onAddInstructions = {},
                    onClearInstructions = {},
                    onReviewInstructionSteps = {},
                    onEditInstructionStep = { _, _, _, _ -> },
                    onDeleteInstructionStep = {},
                    onMoveInstructionStepUp = {},
                    onMoveInstructionStepDown = {},
                    onSaveInstructionsAsWorkflow = {},
                    onExecuteInstructions = {},
                    onRunWorkflow = {},
                    onOpenWorkflowDetails = {},
                    onOpenWorkspace = {},
                    onOpenNanoRetrieverPanel = {},
                    onConfirmDestructiveExecution = {},
                    onCancelDestructiveExecution = {},
                    onClearExecutionBanner = {},
                    onDismiss = {}
                )
            }
        }
        composeTestRule.onNodeWithTag("recording_status_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Recording cancelled").assertIsDisplayed()
        composeTestRule.onNodeWithText("Recording cancelled because the recording tab was closed.").assertIsDisplayed()
        composeTestRule.onNodeWithTag("recording_start_button").assertIsDisplayed()
    }
}
