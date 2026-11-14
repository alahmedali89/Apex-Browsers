package com.example.automation

import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import com.example.data.local.AutomationWorkflowEntity
import java.net.URI
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

data class StepExecutionResult(
    val success: Boolean,
    val message: String,
    val errorCode: AutomationErrorCode = if (success) AutomationErrorCode.NONE else AutomationErrorCode.JS_EXCEPTION
)

data class WorkflowExecutionOutcome(
    val status: String, // COMPLETED, FAILED, CANCELLED
    val completedSteps: Int,
    val totalSteps: Int,
    val verificationSummary: String,
    val errorCode: AutomationErrorCode = if (status == "COMPLETED") AutomationErrorCode.NONE else AutomationErrorCode.JS_EXCEPTION
)

sealed class JsEvalResult {
    data class Success(val rawValue: String) : JsEvalResult()
    data class Timeout(val timeoutMs: Long) : JsEvalResult()
    data class Unavailable(val reason: String) : JsEvalResult()
    data class Failure(val reason: String) : JsEvalResult()
}

data class SelectorValidationResult(
    val isValid: Boolean,
    val reason: String = ""
)

class AutomationEngine(
    private val jsEvalTimeoutMs: Long = DEFAULT_JS_EVAL_TIMEOUT_MS,
    private val navTimeoutMs: Long = DEFAULT_NAV_TIMEOUT_MS,
    private val domReadyTimeoutMs: Long = DEFAULT_DOM_READY_TIMEOUT_MS,
    private val domStabilityPollMs: Long = DEFAULT_DOM_POLL_MS
) {

    companion object {
        const val DEFAULT_JS_EVAL_TIMEOUT_MS = 5_000L
        const val DEFAULT_NAV_TIMEOUT_MS = 15_000L
        const val DEFAULT_DOM_READY_TIMEOUT_MS = 3_500L
        const val DEFAULT_DOM_POLL_MS = 60L

        private const val JS_TARGET_HELPERS = """
            function apexCssEsc(v) {
                if (window.CSS && typeof window.CSS.escape === 'function') return window.CSS.escape(String(v));
                return String(v).replace(/([ !"#$%&'()*+,.\/:;<=>?@\[\\\]^`{|}~])/g, '\\$1');
            }
            function apexEscAttr(v) {
                return String(v).replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\r|\n/g, ' ');
            }
            function apexNormalize(str) {
                return String(str || '').replace(/\s+/g, ' ').trim().toLowerCase();
            }
            function apexStripRoleSuffix(hint) {
                return String(hint || '').replace(/\s+(?:field|checkbox|option|button)$/i, '').trim();
            }
            function apexGetLabel(el) {
                if (!el) return '';
                try {
                    const aria = apexNormalize(el.getAttribute && el.getAttribute('aria-label'));
                    if (aria) return aria;
                    if (el.id) {
                        const lbl = document.querySelector('label[for="' + apexCssEsc(el.id) + '"]');
                        if (lbl && (lbl.innerText || lbl.textContent)) {
                            const txt = apexNormalize(lbl.innerText || lbl.textContent);
                            if (txt) return txt;
                        }
                    }
                    const parentLbl = el.closest ? el.closest('label') : null;
                    if (parentLbl && (parentLbl.innerText || parentLbl.textContent)) {
                        const txt = apexNormalize(parentLbl.innerText || parentLbl.textContent);
                        if (txt) return txt;
                    }
                    const ph = apexNormalize(el.getAttribute && el.getAttribute('placeholder'));
                    if (ph) return ph;
                    const title = apexNormalize(el.getAttribute && el.getAttribute('title'));
                    if (title) return title;
                    const tag = (el.tagName || '').toLowerCase();
                    const type = (el.getAttribute && el.getAttribute('type') || '').toLowerCase();
                    const inner = (tag === 'input' || tag === 'textarea')
                        ? ((type === 'submit' || type === 'button' || type === 'reset') ? apexNormalize(el.value) : '')
                        : apexNormalize(el.innerText || el.textContent);
                    if (inner) return inner;
                    const name = apexNormalize(el.getAttribute && el.getAttribute('name'));
                    if (name) return name;
                } catch (_) {}
                return '';
            }
            function apexIsElementDisabled(el) {
                if (!el) return true;
                if (el.disabled === true) return true;
                const ariaDisabled = (el.getAttribute && el.getAttribute('aria-disabled')) || '';
                return ariaDisabled.toLowerCase() === 'true';
            }
            function apexElementMatchesHint(el, rawHint) {
                const normHint = apexNormalize(rawHint);
                if (!normHint) return { matched: true, exact: false };
                const strippedHint = apexStripRoleSuffix(normHint);
                const elLabel = apexGetLabel(el);
                if (elLabel === normHint || (strippedHint && elLabel === strippedHint)) {
                    return { matched: true, exact: true };
                }
                const tag = (el.tagName || '').toLowerCase();
                const type = (el.getAttribute && el.getAttribute('type') || '').toLowerCase();
                const valueText = (tag === 'input' && (type === 'submit' || type === 'button' || type === 'reset'))
                    ? (el.value || '')
                    : '';
                const bag = apexNormalize([
                    elLabel,
                    el.getAttribute && el.getAttribute('name'),
                    el.getAttribute && el.getAttribute('placeholder'),
                    el.getAttribute && el.getAttribute('aria-label'),
                    el.getAttribute && el.getAttribute('title'),
                    (tag !== 'input' && tag !== 'textarea') ? (el.innerText || el.textContent || '') : '',
                    valueText
                ].filter(Boolean).join(' '));
                if (bag.includes(normHint) || (strippedHint && bag.includes(strippedHint))) {
                    return { matched: true, exact: false };
                }
                return { matched: false, exact: false };
            }
            function apexResolveTarget(sel, labelHint, defaultQuery) {
                const trimmedSel = String(sel || '').trim();
                const trimmedHint = apexNormalize(labelHint);
                let candidates = [];
                if (trimmedSel) {
                    try {
                        candidates = Array.from(document.querySelectorAll(trimmedSel));
                    } catch (e) {
                        return {
                            ok: false,
                            code: 'INVALID_SELECTOR',
                            msg: 'Invalid CSS selector "' + trimmedSel + '": ' + (e && e.message ? e.message : String(e))
                        };
                    }
                    if (candidates.length === 0) {
                        return {
                            ok: false,
                            code: 'ELEMENT_NOT_FOUND',
                            msg: 'No element matched selector "' + trimmedSel + '"'
                        };
                    }
                    if (candidates.length === 1) {
                        const only = candidates[0];
                        if (apexIsElementDisabled(only)) {
                            return {
                                ok: false,
                                code: 'ELEMENT_DISABLED',
                                msg: 'Target element is disabled for "' + (labelHint || trimmedSel) + '"'
                            };
                        }
                        return { ok: true, el: only };
                    }
                    if (!trimmedHint) {
                        return {
                            ok: false,
                            code: 'ELEMENT_AMBIGUOUS',
                            msg: 'Ambiguous selector "' + trimmedSel + '" matched ' + candidates.length + ' elements'
                        };
                    }
                } else {
                    if (!trimmedHint) {
                        return {
                            ok: false,
                            code: 'INVALID_SELECTOR',
                            msg: 'Step has neither CSS selector nor target label'
                        };
                    }
                    try {
                        candidates = Array.from(document.querySelectorAll(defaultQuery));
                    } catch (e) {
                        return {
                            ok: false,
                            code: 'INVALID_SELECTOR',
                            msg: 'Invalid fallback query: ' + (e && e.message ? e.message : String(e))
                        };
                    }
                    if (candidates.length === 0) {
                        return {
                            ok: false,
                            code: 'ELEMENT_NOT_FOUND',
                            msg: 'No matching elements found on page for "' + labelHint + '"'
                        };
                    }
                }
                const exactMatches = [];
                const partialMatches = [];
                for (let i = 0; i < candidates.length; i++) {
                    const el = candidates[i];
                    const match = apexElementMatchesHint(el, trimmedHint);
                    if (match.matched) {
                        if (match.exact) exactMatches.push(el);
                        else partialMatches.push(el);
                    }
                }
                const chosen = exactMatches.length > 0 ? exactMatches : partialMatches;
                if (chosen.length === 0) {
                    return {
                        ok: false,
                        code: 'ELEMENT_NOT_FOUND',
                        msg: 'Target element not found for "' + (labelHint || trimmedSel) + '"'
                    };
                }
                if (chosen.length > 1) {
                    return {
                        ok: false,
                        code: 'ELEMENT_AMBIGUOUS',
                        msg: 'Ambiguous target: ' + chosen.length + ' elements match "' + (labelHint || trimmedSel) + '"'
                    };
                }
                const target = chosen[0];
                if (apexIsElementDisabled(target)) {
                    return {
                        ok: false,
                        code: 'ELEMENT_DISABLED',
                        msg: 'Target element is disabled for "' + (labelHint || trimmedSel) + '"'
                    };
                }
                return { ok: true, el: target };
            }
        """
    }

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    fun extractDomain(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isBlank() || trimmed.equals("apex://home", ignoreCase = true)) {
            return "Start Page"
        }
        if (trimmed.equals("apex://news", ignoreCase = true)) return "news.apex.browser"
        if (trimmed.equals("apex://store", ignoreCase = true)) return "store.apex.browser"
        return runCatching {
            URI(trimmed).host?.removePrefix("www.")?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: trimmed
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .substringBefore('/')
            .substringBefore('?')
            .ifBlank { "Web Page" }
    }

    fun validateSelector(rawSelector: String): SelectorValidationResult {
        val trimmed = rawSelector.trim()
        if (trimmed.isEmpty()) {
            return SelectorValidationResult(isValid = false, reason = "Selector is empty")
        }
        if (trimmed.any { it.code < 32 }) {
            return SelectorValidationResult(isValid = false, reason = "Selector contains invalid control characters")
        }
        if (trimmed.startsWith(">") || trimmed.startsWith("+") || trimmed.startsWith("~") || trimmed.startsWith(",") ||
            trimmed.endsWith(">") || trimmed.endsWith("+") || trimmed.endsWith("~") || trimmed.endsWith(",")
        ) {
            return SelectorValidationResult(isValid = false, reason = "Selector has leading or trailing combinator")
        }
        var quote: Char? = null
        var bracketDepth = 0
        var parenDepth = 0
        var escaped = false
        for (i in trimmed.indices) {
            val ch = trimmed[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (ch == '\\') {
                escaped = true
                continue
            }
            if (quote != null) {
                if (ch == quote) quote = null
                continue
            }
            when (ch) {
                '\'', '"' -> quote = ch
                '[' -> bracketDepth++
                ']' -> {
                    bracketDepth--
                    if (bracketDepth < 0) {
                        return SelectorValidationResult(isValid = false, reason = "Unmatched ']' in selector")
                    }
                }
                '(' -> parenDepth++
                ')' -> {
                    parenDepth--
                    if (parenDepth < 0) {
                        return SelectorValidationResult(isValid = false, reason = "Unmatched ')' in selector")
                    }
                }
            }
        }
        if (escaped) {
            return SelectorValidationResult(isValid = false, reason = "Trailing backslash in selector")
        }
        if (quote != null) {
            return SelectorValidationResult(isValid = false, reason = "Unclosed string quote in selector")
        }
        if (bracketDepth != 0) {
            return SelectorValidationResult(isValid = false, reason = "Unclosed '[' attribute bracket in selector")
        }
        if (parenDepth != 0) {
            return SelectorValidationResult(isValid = false, reason = "Unclosed '(' pseudo-class parenthesis in selector")
        }
        if (trimmed.contains(Regex(",\\s*,"))) {
            return SelectorValidationResult(isValid = false, reason = "Selector contains an empty list segment")
        }
        return SelectorValidationResult(isValid = true)
    }

    suspend fun scanPageElements(
        webView: WebView?,
        currentUrl: String
    ): PageInspectionState {
        val domain = extractDomain(currentUrl)
        if (currentUrl.isBlank() || currentUrl.equals("apex://home", ignoreCase = true) || webView == null) {
            return PageInspectionState(
                domain = domain,
                url = currentUrl,
                elements = emptyList(),
                isScanning = false,
                lastScannedAt = System.currentTimeMillis(),
                errorMessage = if (currentUrl.equals("apex://home", ignoreCase = true)) {
                    null
                } else {
                    "Open a webpage to inspect interactive DOM elements."
                }
            )
        }

        val scanScript = """
            (function() {
                try {
                    function escCss(v) {
                        if (window.CSS && typeof window.CSS.escape === 'function') return window.CSS.escape(String(v));
                        return String(v).replace(/([ !"#$%&'()*+,.\/:;<=>?@\[\\\]^`{|}~])/g, '\\$1');
                    }
                    function escAttr(v) {
                        return String(v).replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\r|\n/g, ' ');
                    }
                    const selectors = [
                        'input:not([type="hidden"])',
                        'textarea',
                        'select',
                        'button',
                        'a[href]',
                        '[role="button"]',
                        '[role="checkbox"]',
                        '[role="textbox"]',
                        '[type="submit"]'
                    ].join(', ');
                    const rawNodes = Array.from(document.querySelectorAll(selectors));
                    const seen = new Set();
                    const out = [];
                    function buildSelector(el) {
                        if (el.id) {
                            const idSel = '#' + escCss(el.id);
                            try { if (document.querySelectorAll(idSel).length === 1) return idSel; } catch (_) {}
                        }
                        const tag = (el.tagName || 'element').toLowerCase();
                        const testId = el.getAttribute('data-testid') || el.getAttribute('data-test');
                        if (testId) {
                            const attrName = el.getAttribute('data-testid') ? 'data-testid' : 'data-test';
                            const sel = tag + '[' + attrName + '="' + escAttr(testId) + '"]';
                            try { if (document.querySelectorAll(sel).length === 1) return sel; } catch (_) {}
                        }
                        const name = el.getAttribute('name');
                        if (name) {
                            const sel = tag + '[name="' + escAttr(name) + '"]';
                            try { if (document.querySelectorAll(sel).length === 1) return sel; } catch (_) {}
                        }
                        const aria = el.getAttribute('aria-label');
                        if (aria) {
                            const sel = tag + '[aria-label="' + escAttr(aria) + '"]';
                            try { if (document.querySelectorAll(sel).length === 1) return sel; } catch (_) {}
                        }
                        const parts = [];
                        let cur = el;
                        for (let depth = 0; cur && cur.nodeType === 1 && depth < 5; depth++) {
                            let part = cur.tagName.toLowerCase();
                            if (cur.id) {
                                parts.unshift('#' + escCss(cur.id));
                                const cand = parts.join(' > ');
                                try { if (document.querySelectorAll(cand).length === 1) return cand; } catch (_) {}
                            } else {
                                const parent = cur.parentElement;
                                if (parent) {
                                    const sameTag = Array.from(parent.children).filter(c => c.tagName === cur.tagName);
                                    if (sameTag.length > 1) {
                                        part += ':nth-of-type(' + (sameTag.indexOf(cur) + 1) + ')';
                                    }
                                }
                                parts.unshift(part);
                                const cand = parts.join(' > ');
                                try { if (document.querySelectorAll(cand).length === 1) return cand; } catch (_) {}
                            }
                            cur = cur.parentElement;
                        }
                        const type = el.getAttribute('type');
                        if (parts.length > 0) return parts.join(' > ');
                        if (type) return tag + '[type="' + escAttr(type) + '"]';
                        return tag;
                    }
                    function resolveLabel(el) {
                        const aria = (el.getAttribute('aria-label') || '').trim();
                        if (aria) return aria;
                        if (el.id) {
                            try {
                                const lbl = document.querySelector('label[for="' + escCss(el.id) + '"]');
                                if (lbl && lbl.innerText) return lbl.innerText.trim();
                            } catch (_) {}
                        }
                        const parentLabel = el.closest ? el.closest('label') : null;
                        if (parentLabel && parentLabel.innerText) return parentLabel.innerText.trim();
                        const ph = (el.getAttribute('placeholder') || '').trim();
                        if (ph) return ph;
                        const txt = (el.innerText || el.value || el.getAttribute('name') || el.getAttribute('title') || '').replace(/\s+/g, ' ').trim();
                        if (txt) return txt.slice(0, 64);
                        return (el.tagName || 'element').toLowerCase();
                    }
                    for (let i = 0; i < rawNodes.length && out.length < 40; i++) {
                        const el = rawNodes[i];
                        const rect = el.getBoundingClientRect ? el.getBoundingClientRect() : null;
                        if (rect && rect.width === 0 && rect.height === 0) continue;
                        const sel = buildSelector(el);
                        const tag = (el.tagName || '').toLowerCase();
                        const inputType = (el.getAttribute('type') || '').toLowerCase();
                        const role = el.getAttribute('role') || (
                            tag === 'input' ? (inputType || 'text') :
                            tag === 'a' ? 'link' :
                            tag
                        );
                        const label = resolveLabel(el);
                        const key = tag + '|' + sel + '|' + label;
                        if (seen.has(key)) continue;
                        seen.add(key);
                        out.push({
                            id: 'el_' + out.length,
                            tag: tag,
                            role: role,
                            label: label,
                            selector: sel,
                            inputType: inputType
                        });
                    }
                    return JSON.stringify({ elements: out });
                } catch (e) {
                    return JSON.stringify({ error: String(e), elements: [] });
                }
            })();
        """.trimIndent()

        val evalResult = evalJsWithResult(webView, scanScript)
        val raw = when (evalResult) {
            is JsEvalResult.Success -> evalResult.rawValue
            is JsEvalResult.Timeout -> {
                return PageInspectionState(
                    domain = domain,
                    url = currentUrl,
                    elements = emptyList(),
                    isScanning = false,
                    lastScannedAt = System.currentTimeMillis(),
                    errorMessage = "Page inspection timed out after ${evalResult.timeoutMs}ms."
                )
            }
            is JsEvalResult.Unavailable, is JsEvalResult.Failure -> {
                return PageInspectionState(
                    domain = domain,
                    url = currentUrl,
                    elements = emptyList(),
                    isScanning = false,
                    lastScannedAt = System.currentTimeMillis(),
                    errorMessage = "Unable to access active document DOM."
                )
            }
        }
        if (raw.isBlank() || raw == "null") {
            return PageInspectionState(
                domain = domain,
                url = currentUrl,
                elements = emptyList(),
                isScanning = false,
                lastScannedAt = System.currentTimeMillis(),
                errorMessage = "Unable to access active document DOM."
            )
        }

        return runCatching {
            val unquoted = if (raw.startsWith("\"") && raw.endsWith("\"")) {
                JSONTokener(raw).nextValue().toString()
            } else {
                raw
            }
            val obj = JSONObject(unquoted)
            val err = obj.optString("error", "").takeIf { it.isNotBlank() }
            val arr = obj.optJSONArray("elements") ?: JSONArray()
            val items = buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    add(
                        DetectedPageElement(
                            id = item.optString("id", "el_$i"),
                            tagName = item.optString("tag", "element"),
                            role = item.optString("role", "interactive"),
                            label = item.optString("label", "Interactive element").ifBlank { "Element" },
                            selector = item.optString("selector", ""),
                            inputType = item.optString("inputType", "")
                        )
                    )
                }
            }
            PageInspectionState(
                domain = domain,
                url = currentUrl,
                elements = items,
                isScanning = false,
                lastScannedAt = System.currentTimeMillis(),
                errorMessage = err
            )
        }.getOrElse {
            PageInspectionState(
                domain = domain,
                url = currentUrl,
                elements = emptyList(),
                isScanning = false,
                lastScannedAt = System.currentTimeMillis(),
                errorMessage = "Failed to parse page inspection result."
            )
        }
    }

    fun parseOrigin(rawUrl: String?): RecordingOriginResult {
        val trimmed = rawUrl?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed.equals("null", ignoreCase = true)) {
            return RecordingOriginResult.OpaqueOrUnsupported("", "No active document URL.")
        }

        val schemePrefix = trimmed.substringBefore(':', missingDelimiterValue = "").lowercase()
        if (schemePrefix in setOf("about", "data", "javascript", "blob", "file", "apex", "chrome", "intent", "mailto", "tel")) {
            return RecordingOriginResult.OpaqueOrUnsupported(
                scheme = schemePrefix,
                reason = "Unsupported or opaque URL scheme '$schemePrefix'."
            )
        }

        val authorityCandidate = trimmed.substringBefore('#').substringBefore('?')
        val uri = runCatching { URI(trimmed) }.getOrNull()
            ?: runCatching { URI(authorityCandidate) }.getOrNull()
            ?: return RecordingOriginResult.OpaqueOrUnsupported(
                scheme = schemePrefix,
                reason = "Malformed URL."
            )

        val scheme = uri.scheme?.lowercase()?.trim().orEmpty()
        if (scheme != "http" && scheme != "https") {
            return RecordingOriginResult.OpaqueOrUnsupported(
                scheme = scheme,
                reason = "Only HTTP and HTTPS origins support automation recording."
            )
        }

        val rawHost = uri.host?.trim().orEmpty()
        val host = rawHost.trimEnd('.').lowercase()
        if (host.isEmpty() || host.contains("..") || host.any { it.isWhitespace() }) {
            return RecordingOriginResult.OpaqueOrUnsupported(
                scheme = scheme,
                reason = "Missing or invalid host in URL."
            )
        }

        val effectivePort = when (val port = uri.port) {
            -1 -> if (scheme == "http") 80 else 443
            in 1..65535 -> port
            else -> return RecordingOriginResult.OpaqueOrUnsupported(
                scheme = scheme,
                reason = "Invalid port in URL."
            )
        }

        val canonicalOrigin = "$scheme://$host:$effectivePort"
        val isDefaultPort = (scheme == "http" && effectivePort == 80) ||
            (scheme == "https" && effectivePort == 443)
        val displayOrigin = if (isDefaultPort) "$scheme://$host" else canonicalOrigin

        return RecordingOriginResult.Valid(
            canonicalOrigin = canonicalOrigin,
            displayOrigin = displayOrigin,
            scheme = scheme,
            host = host,
            effectivePort = effectivePort
        )
    }

    fun areSameOrigin(firstUrl: String?, secondUrl: String?): Boolean {
        val first = parseOrigin(firstUrl)
        val second = parseOrigin(secondUrl)
        return first is RecordingOriginResult.Valid &&
            second is RecordingOriginResult.Valid &&
            first.canonicalOrigin == second.canonicalOrigin
    }

    internal fun buildRecorderInstallScript(
        sessionId: String,
        tabId: String,
        expectedOrigin: String,
        initialSequenceNumber: Long = 0L
    ): String {
        val qSessionId = JSONObject.quote(sessionId)
        val qTabId = JSONObject.quote(tabId)
        val qExpectedOrigin = JSONObject.quote(expectedOrigin)
        val safeInitialSeq = initialSequenceNumber.coerceAtLeast(0L)
        return """
            (function() {
                try {
                    if (window.__apexRecorderInstance && typeof window.__apexRecorderInstance.cleanup === 'function') {
                        try { window.__apexRecorderInstance.cleanup(); } catch (_) {}
                    }
                    window.__apexRecorderInstance = null;
                    window.__apexRecordingActive = false;
                    window.__apexRecorderInstalled = false;

                    function getCanonicalOrigin() {
                        try {
                            if (typeof window.origin === 'string' && window.origin === 'null') return '';
                            var loc = window.location;
                            if (!loc) return '';
                            var proto = String(loc.protocol || '').toLowerCase();
                            if (proto !== 'http:' && proto !== 'https:') return '';
                            var scheme = proto.slice(0, -1);
                            var host = String(loc.hostname || '').trim().replace(/\.+$/, '').toLowerCase();
                            if (!host) return '';
                            var rawPort = String(loc.port || '');
                            var port = rawPort ? parseInt(rawPort, 10) : (scheme === 'http' ? 80 : 443);
                            if (!port || isNaN(port) || port < 1 || port > 65535) return '';
                            return scheme + '://' + host + ':' + port;
                        } catch (_) {
                            return '';
                        }
                    }

                    var actualOrigin = getCanonicalOrigin();
                    var expectedOrigin = $qExpectedOrigin;
                    if (!actualOrigin || actualOrigin !== expectedOrigin) {
                        return JSON.stringify({
                            ok: false,
                            reason: 'origin_mismatch',
                            actualOrigin: actualOrigin || ''
                        });
                    }

                    function escCss(v) {
                        if (window.CSS && typeof window.CSS.escape === 'function') return window.CSS.escape(String(v));
                        return String(v).replace(/([ !"#$%&'()*+,.\/:;<=>?@\[\\\]^`{|}~])/g, '\\$1');
                    }
                    function escAttr(v) {
                        return String(v).replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\r|\n/g, ' ');
                    }
                    function buildSel(el) {
                        if (!el || !el.tagName) return '';
                        if (el.id) {
                            var idSel = '#' + escCss(el.id);
                            try { if (document.querySelectorAll(idSel).length === 1) return idSel; } catch (_) {}
                        }
                        var tag = el.tagName.toLowerCase();
                        var testId = el.getAttribute && (el.getAttribute('data-testid') || el.getAttribute('data-test'));
                        if (testId) {
                            var attrName = el.getAttribute('data-testid') ? 'data-testid' : 'data-test';
                            var sel = tag + '[' + attrName + '="' + escAttr(testId) + '"]';
                            try { if (document.querySelectorAll(sel).length === 1) return sel; } catch (_) {}
                        }
                        var name = el.getAttribute && el.getAttribute('name');
                        if (name) {
                            var nameSel = tag + '[name="' + escAttr(name) + '"]';
                            try { if (document.querySelectorAll(nameSel).length === 1) return nameSel; } catch (_) {}
                        }
                        var aria = el.getAttribute && el.getAttribute('aria-label');
                        if (aria) {
                            var ariaSel = tag + '[aria-label="' + escAttr(aria) + '"]';
                            try { if (document.querySelectorAll(ariaSel).length === 1) return ariaSel; } catch (_) {}
                        }
                        var parts = [];
                        var cur = el;
                        for (var depth = 0; cur && cur.nodeType === 1 && depth < 5; depth++) {
                            var part = cur.tagName.toLowerCase();
                            if (cur.id) {
                                parts.unshift('#' + escCss(cur.id));
                                var candId = parts.join(' > ');
                                try { if (document.querySelectorAll(candId).length === 1) return candId; } catch (_) {}
                            } else {
                                var parent = cur.parentElement;
                                if (parent) {
                                    var sameTag = Array.from(parent.children).filter(function(c) { return c.tagName === cur.tagName; });
                                    if (sameTag.length > 1) {
                                        part += ':nth-of-type(' + (sameTag.indexOf(cur) + 1) + ')';
                                    }
                                }
                                parts.unshift(part);
                                var cand = parts.join(' > ');
                                try { if (document.querySelectorAll(cand).length === 1) return cand; } catch (_) {}
                            }
                            cur = cur.parentElement;
                        }
                        var type = el.getAttribute && el.getAttribute('type');
                        if (parts.length > 0) return parts.join(' > ');
                        if (type) return tag + '[type="' + escAttr(type) + '"]';
                        return tag;
                    }
                    function getLabel(el) {
                        if (!el) return 'Element';
                        var aria = (el.getAttribute && el.getAttribute('aria-label') || '').trim();
                        if (aria) return aria;
                        if (el.id) {
                            try {
                                var lbl = document.querySelector('label[for="' + escCss(el.id) + '"]');
                                if (lbl && lbl.innerText) return lbl.innerText.trim();
                            } catch (_) {}
                        }
                        var parentLbl = el.closest ? el.closest('label') : null;
                        if (parentLbl && parentLbl.innerText) return parentLbl.innerText.trim();
                        var ph = (el.getAttribute && el.getAttribute('placeholder') || '').trim();
                        if (ph) return ph;
                        var name = (el.getAttribute && el.getAttribute('name') || '').trim();
                        if (name) return name;
                        var type = (el.getAttribute && el.getAttribute('type') || '').toLowerCase();
                        var rawText = type === 'password' ? '' : (el.innerText || el.value || '');
                        var txt = String(rawText).replace(/\s+/g, ' ').trim();
                        if (txt) return txt.slice(0, 48);
                        return (el.tagName || 'Element').toLowerCase();
                    }

                    var inst = {
                        sessionId: $qSessionId,
                        tabId: $qTabId,
                        expectedOrigin: expectedOrigin,
                        seq: $safeInitialSeq,
                        active: true,
                        destroyed: false,
                        lastDomEvent: null,
                        lastFocusKey: '',
                        onFocusIn: null,
                        onChange: null,
                        onClick: null,
                        cleanup: function() {
                            inst.destroyed = true;
                            inst.active = false;
                            try {
                                if (inst.onFocusIn) document.removeEventListener('focusin', inst.onFocusIn, true);
                                if (inst.onChange) document.removeEventListener('change', inst.onChange, true);
                                if (inst.onClick) document.removeEventListener('click', inst.onClick, true);
                            } catch (_) {}
                            if (window.__apexRecorderInstance === inst) {
                                window.__apexRecorderInstance = null;
                                window.__apexRecordingActive = false;
                                window.__apexRecorderInstalled = false;
                            }
                        }
                    };

                    function emit(domEvent, actionType, label, selector, value, isDestructive) {
                        if (inst.destroyed || !inst.active || window.__apexRecorderInstance !== inst) return;
                        var nowOrigin = getCanonicalOrigin();
                        if (!nowOrigin || nowOrigin !== inst.expectedOrigin) {
                            inst.cleanup();
                            return;
                        }
                        if (domEvent && inst.lastDomEvent === domEvent) return;
                        inst.lastDomEvent = domEvent || null;
                        inst.seq = inst.seq + 1;
                        var payload = JSON.stringify({
                            sessionId: inst.sessionId,
                            tabId: inst.tabId,
                            origin: nowOrigin,
                            sequenceNumber: inst.seq,
                            eventType: actionType,
                            targetLabel: label || 'Element',
                            selector: selector || '',
                            value: value || '',
                            isDestructive: Boolean(isDestructive),
                            timestamp: Date.now()
                        });
                        if (window.ApexAutomationBridge && window.ApexAutomationBridge.onRecordedEventJson) {
                            window.ApexAutomationBridge.onRecordedEventJson(payload);
                        }
                    }

                    inst.onFocusIn = function(e) {
                        try {
                            if (inst.destroyed || !inst.active) return;
                            var el = e.target;
                            if (!el || !el.tagName) return;
                            var tag = el.tagName.toUpperCase();
                            if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') {
                                var type = (el.getAttribute && el.getAttribute('type') || '').toLowerCase();
                                if (type !== 'checkbox' && type !== 'radio' && type !== 'submit' && type !== 'button') {
                                    var lbl = getLabel(el) + ' field';
                                    var sel = buildSel(el);
                                    var focusKey = lbl + '|' + sel;
                                    if (inst.lastFocusKey === focusKey) return;
                                    inst.lastFocusKey = focusKey;
                                    emit(e, 'SELECT', lbl, sel, '', false);
                                }
                            }
                        } catch (_) {}
                    };

                    inst.onChange = function(e) {
                        try {
                            if (inst.destroyed || !inst.active) return;
                            var el = e.target;
                            if (!el || !el.tagName) return;
                            inst.lastFocusKey = '';
                            var tag = el.tagName.toUpperCase();
                            var type = (el.getAttribute && el.getAttribute('type') || '').toLowerCase();
                            if (type === 'checkbox' || type === 'radio') {
                                emit(
                                    e,
                                    'TOGGLE',
                                    getLabel(el) + ' ' + (type === 'checkbox' ? 'checkbox' : 'option'),
                                    buildSel(el),
                                    el.checked ? 'checked' : 'unchecked',
                                    false
                                );
                            } else if (tag === 'SELECT') {
                                emit(e, 'SELECT', getLabel(el), buildSel(el), el.value || '', false);
                            } else if (tag === 'INPUT' || tag === 'TEXTAREA') {
                                var safeVal = type === 'password' ? '' : String(el.value || '').slice(0, 64);
                                emit(e, 'INPUT', getLabel(el), buildSel(el), safeVal, false);
                            }
                        } catch (_) {}
                    };

                    inst.onClick = function(e) {
                        try {
                            if (inst.destroyed || !inst.active) return;
                            var rawTarget = e.target;
                            var el = rawTarget && rawTarget.closest
                                ? (rawTarget.closest('button, a, [role="button"], input[type="submit"], input[type="button"]') || rawTarget)
                                : rawTarget;
                            if (!el || !el.tagName) return;
                            var tag = el.tagName.toUpperCase();
                            var type = (el.getAttribute && el.getAttribute('type') || '').toLowerCase();
                            if (tag === 'BUTTON' || tag === 'A' || (el.getAttribute && el.getAttribute('role') === 'button') || type === 'submit' || type === 'button') {
                                inst.lastFocusKey = '';
                                var lbl = getLabel(el);
                                var lower = lbl.toLowerCase();
                                var destructive = /delete|remove|pay|purchase|checkout|submit|confirm|reset|clear/.test(lower);
                                emit(
                                    e,
                                    type === 'submit' ? 'SUBMIT' : 'CLICK',
                                    lbl + (tag === 'BUTTON' ? ' button' : ''),
                                    buildSel(el),
                                    '',
                                    destructive
                                );
                            }
                        } catch (_) {}
                    };

                    window.__apexRecorderInstance = inst;
                    window.__apexRecordingActive = true;
                    window.__apexRecorderInstalled = true;
                    document.addEventListener('focusin', inst.onFocusIn, true);
                    document.addEventListener('change', inst.onChange, true);
                    document.addEventListener('click', inst.onClick, true);

                    return JSON.stringify({
                        ok: true,
                        sessionId: inst.sessionId,
                        tabId: inst.tabId,
                        origin: actualOrigin
                    });
                } catch (err) {
                    return JSON.stringify({
                        ok: false,
                        reason: 'exception',
                        message: String(err && err.message || err || 'Install error')
                    });
                }
            })();
        """.trimIndent()
    }

    internal fun buildRecorderUninstallScript(expectedSessionId: String? = null): String {
        val qSessionId = JSONObject.quote(expectedSessionId?.trim().orEmpty())
        return """
            (function() {
                try {
                    var targetSession = $qSessionId;
                    var inst = window.__apexRecorderInstance;
                    var removed = false;
                    if (inst) {
                        if (!targetSession || inst.sessionId === targetSession) {
                            if (typeof inst.cleanup === 'function') {
                                inst.cleanup();
                            }
                            window.__apexRecorderInstance = null;
                            window.__apexRecordingActive = false;
                            window.__apexRecorderInstalled = false;
                            removed = true;
                        }
                    } else {
                        window.__apexRecordingActive = false;
                        window.__apexRecorderInstalled = false;
                    }
                    return JSON.stringify({ ok: true, removed: removed });
                } catch (err) {
                    return JSON.stringify({
                        ok: false,
                        reason: 'exception',
                        message: String(err && err.message || err || 'Uninstall error')
                    });
                }
            })();
        """.trimIndent()
    }

    suspend fun installRecorderScript(
        webView: WebView?,
        sessionId: String,
        tabId: String,
        expectedOrigin: String,
        initialSequenceNumber: Long = 0L
    ): RecorderLifecycleResult {
        if (webView == null) {
            return RecorderLifecycleResult(
                success = false,
                errorCode = AutomationErrorCode.RECORDING_DOCUMENT_UNAVAILABLE,
                message = "WebView is unavailable for recording."
            )
        }
        val cleanSessionId = sessionId.trim()
        val cleanTabId = tabId.trim()
        val parsedOrigin = parseOrigin(expectedOrigin)
        if (cleanSessionId.isEmpty() || cleanTabId.isEmpty() || parsedOrigin !is RecordingOriginResult.Valid) {
            return RecorderLifecycleResult(
                success = false,
                errorCode = AutomationErrorCode.RECORDING_DOCUMENT_UNAVAILABLE,
                message = "Invalid recording session or website origin."
            )
        }
        val script = buildRecorderInstallScript(
            sessionId = cleanSessionId,
            tabId = cleanTabId,
            expectedOrigin = parsedOrigin.canonicalOrigin,
            initialSequenceNumber = initialSequenceNumber
        )
        return when (val eval = evalJsWithResult(webView, script)) {
            is JsEvalResult.Success -> {
                val json = parseEvalJsonObject(eval.rawValue)
                if (json == null) {
                    RecorderLifecycleResult(
                        success = false,
                        errorCode = AutomationErrorCode.RECORDING_INSTALL_FAILED,
                        message = "Recorder confirmation response was invalid."
                    )
                } else {
                    val ok = json.optBoolean("ok", false)
                    val confirmedSession = json.optString("sessionId", "")
                    if (ok && confirmedSession == cleanSessionId) {
                        RecorderLifecycleResult(success = true)
                    } else {
                        val reason = json.optString("reason", "")
                        val errCode = if (reason == "origin_mismatch") {
                            AutomationErrorCode.RECORDING_ORIGIN_CHANGED
                        } else {
                            AutomationErrorCode.RECORDING_INSTALL_FAILED
                        }
                        RecorderLifecycleResult(
                            success = false,
                            errorCode = errCode,
                            message = json.optString("message", "").ifBlank {
                                if (reason == "origin_mismatch") {
                                    "Page origin changed before recorder could attach."
                                } else {
                                    "Recorder failed to attach to webpage."
                                }
                            }
                        )
                    }
                }
            }
            is JsEvalResult.Timeout -> RecorderLifecycleResult(
                success = false,
                errorCode = AutomationErrorCode.RECORDING_INSTALL_FAILED,
                message = "Recorder installation timed out."
            )
            is JsEvalResult.Unavailable, is JsEvalResult.Failure -> RecorderLifecycleResult(
                success = false,
                errorCode = AutomationErrorCode.RECORDING_INSTALL_FAILED,
                message = "Recorder installation failed in WebView."
            )
        }
    }

    suspend fun uninstallRecorderScript(
        webView: WebView?,
        sessionId: String? = null
    ): RecorderLifecycleResult {
        if (webView == null) {
            return RecorderLifecycleResult(
                success = false,
                errorCode = AutomationErrorCode.RECORDING_CLEANUP_FAILED,
                message = "WebView unavailable during recorder cleanup."
            )
        }
        val script = buildRecorderUninstallScript(sessionId)
        return when (val eval = evalJsWithResult(webView, script)) {
            is JsEvalResult.Success -> {
                val json = parseEvalJsonObject(eval.rawValue)
                if (json?.optBoolean("ok", false) == true) {
                    RecorderLifecycleResult(success = true)
                } else {
                    RecorderLifecycleResult(
                        success = false,
                        errorCode = AutomationErrorCode.RECORDING_CLEANUP_FAILED,
                        message = "Recorder cleanup returned unexpected response."
                    )
                }
            }
            is JsEvalResult.Timeout, is JsEvalResult.Unavailable, is JsEvalResult.Failure -> {
                RecorderLifecycleResult(
                    success = false,
                    errorCode = AutomationErrorCode.RECORDING_CLEANUP_FAILED,
                    message = "Failed to uninstall recorder script."
                )
            }
        }
    }

    fun detachRecorderBestEffort(webView: WebView?, sessionId: String? = null) {
        if (webView == null) return
        val script = buildRecorderUninstallScript(sessionId)
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runCatching { webView.evaluateJavascript(script, null) }
        } else {
            mainHandler.post {
                runCatching { webView.evaluateJavascript(script, null) }
            }
        }
    }

    private fun parseEvalJsonObject(rawValue: String): JSONObject? {
        if (rawValue.isBlank() || rawValue == "null") return null
        return runCatching {
            val decoded = JSONTokener(rawValue).nextValue()
            val text = when (decoded) {
                is String -> decoded
                is JSONObject -> return@runCatching decoded
                else -> rawValue
            }
            if (text.isBlank()) null else JSONObject(text)
        }.getOrNull()
    }

    fun parseInstructionsToSteps(
        rawInstructions: String,
        currentUrl: String
    ): List<AutomationStep> {
        val lines = rawInstructions
            .split(Regex("[\n;]+|\\bthen\\b", RegexOption.IGNORE_CASE))
            .map { it.trim().removePrefix("-").removePrefix("•").replace(Regex("^\\d+[.)]\\s*"), "").trim() }
            .filter { it.isNotBlank() }

        if (lines.isEmpty()) return emptyList()

        return lines.mapIndexed { idx, clause ->
            val lower = clause.lowercase()
            val isDestructive = isClauseDestructive(lower)
            val actionType = when {
                lower.startsWith("go to ") || lower.startsWith("open ") || lower.startsWith("navigate ") || lower.contains("http://") || lower.contains("https://") ->
                    AutomationActionType.NAVIGATE
                lower.contains("type ") || lower.contains("enter ") || lower.contains("fill ") || lower.contains("input ") ->
                    AutomationActionType.INPUT
                lower.contains("check ") || lower.contains("toggle ") || lower.contains("checkbox") ->
                    AutomationActionType.TOGGLE
                lower.contains("select ") || lower.contains("choose ") || lower.contains("dropdown") ->
                    AutomationActionType.SELECT
                lower.contains("scroll") ->
                    AutomationActionType.SCROLL
                lower.contains("extract") || lower.contains("scrape") || lower.contains("collect") || lower.contains("summarize") ->
                    AutomationActionType.EXTRACT
                lower.contains("submit") || lower.contains("confirm") || lower.contains("delete") ->
                    AutomationActionType.SUBMIT
                else -> AutomationActionType.CLICK
            }

            val extractedValue = extractQuotedOrAfterKeyword(clause)
            val targetLabel = deriveTargetLabel(clause, actionType, extractedValue)
            val inferredSelector = inferSelector(actionType, targetLabel, lower)

            AutomationStep(
                id = "inst_${UUID.randomUUID()}",
                order = idx + 1,
                actionType = actionType,
                targetLabel = targetLabel,
                selector = inferredSelector,
                value = extractedValue,
                description = clause,
                isDestructive = isDestructive
            )
        }
    }

    fun isClauseDestructive(lowerClause: String): Boolean {
        val keywords = listOf(
            "delete", "remove", "erase", "wipe", "clear all",
            "purchase", "buy now", "checkout", "pay ", "payment",
            "submit", "transfer", "send money", "confirm order", "unsubscribe"
        )
        return keywords.any { lowerClause.contains(it) }
    }

    private fun extractQuotedOrAfterKeyword(clause: String): String {
        val quoted = Regex("""["']([^"']+)["']""").find(clause)?.groupValues?.getOrNull(1)
        if (!quoted.isNullOrBlank()) return quoted
        val urlMatch = Regex("""(https?://\S+|apex://\S+)""", RegexOption.IGNORE_CASE).find(clause)
        if (urlMatch != null) return urlMatch.groupValues[1]
        return ""
    }

    private fun deriveTargetLabel(
        clause: String,
        actionType: AutomationActionType,
        extractedValue: String
    ): String {
        return when (actionType) {
            AutomationActionType.NAVIGATE -> extractedValue.ifBlank {
                clause.replace(Regex("^(go to|open|navigate to|navigate)\\s+", RegexOption.IGNORE_CASE), "").trim()
            }
            AutomationActionType.SCROLL -> if (clause.contains("up", ignoreCase = true)) "up" else "down"
            else -> {
                val cleaned = clause
                    .replace(Regex("""["'][^"']+["']"""), "")
                    .replace(Regex("^(click|tap|press|enter|type|fill|select|choose|toggle|check|extract|submit)\\s+(on\\s+|the\\s+)?", RegexOption.IGNORE_CASE), "")
                    .trim()
                cleaned.ifBlank { clause.take(40) }
            }
        }
    }

    private fun inferSelector(
        actionType: AutomationActionType,
        targetLabel: String,
        lowerClause: String
    ): String {
        return when (actionType) {
            AutomationActionType.INPUT -> when {
                lowerClause.contains("email") -> "input[type=\"email\"], input[name*=\"mail\" i]"
                lowerClause.contains("password") -> "input[type=\"password\"]"
                lowerClause.contains("search") -> "input[type=\"search\"], input[name*=\"q\" i], input[name*=\"search\" i]"
                else -> "input:not([type=\"hidden\"]), textarea"
            }
            AutomationActionType.TOGGLE -> "input[type=\"checkbox\"], input[type=\"radio\"], [role=\"checkbox\"]"
            AutomationActionType.SELECT -> "select, [role=\"listbox\"], [role=\"option\"]"
            AutomationActionType.SUBMIT -> "button[type=\"submit\"], input[type=\"submit\"], button"
            AutomationActionType.CLICK -> "button, a, [role=\"button\"]"
            AutomationActionType.EXTRACT -> "article, h1, h2, h3, p, a"
            else -> ""
        }
    }

    suspend fun executeWorkflow(
        workflow: AutomationWorkflowEntity,
        steps: List<AutomationStep>,
        currentTabUrl: String,
        webViewProvider: () -> WebView?,
        onNavigateUrl: (String) -> Unit,
        onStepProgress: (stepIndex: Int, logMessage: String) -> Unit,
        tabId: String = "",
        navigationSynchronizer: TabNavigationSynchronizer? = null,
        onStepCompleted: (Int) -> Unit = {}
    ): WorkflowExecutionOutcome {
        if (steps.isEmpty()) {
            return WorkflowExecutionOutcome(
                status = "FAILED",
                completedSteps = 0,
                totalSteps = 0,
                verificationSummary = "Workflow has no executable steps.",
                errorCode = AutomationErrorCode.EMPTY_WORKFLOW
            )
        }

        // If active tab is on Start Page and workflow targets a specific URL, navigate and await settlement first
        var activeUrl = currentTabUrl
        if ((activeUrl.isBlank() || activeUrl.equals("apex://home", ignoreCase = true)) &&
            workflow.sourceUrl.isNotBlank() &&
            !workflow.sourceUrl.equals("apex://home", ignoreCase = true)
        ) {
            onStepProgress(0, "Opening ${workflow.domain}…")
            val navSyncOutcome = navigateAndAwaitSettlement(
                dest = workflow.sourceUrl,
                tabId = tabId,
                navigationSynchronizer = navigationSynchronizer,
                webViewProvider = webViewProvider,
                nextStep = steps.firstOrNull(),
                onNavigateUrl = onNavigateUrl
            )
            if (!navSyncOutcome.success) {
                return WorkflowExecutionOutcome(
                    status = "FAILED",
                    completedSteps = 0,
                    totalSteps = steps.size,
                    verificationSummary = navSyncOutcome.message,
                    errorCode = navSyncOutcome.errorCode
                )
            }
            activeUrl = navigationSynchronizer?.snapshot(tabId)?.activeUrl?.ifBlank { workflow.sourceUrl }
                ?: workflow.sourceUrl
        }

        var completedCount = 0
        val notes = mutableListOf<String>()

        for ((idx, step) in steps.withIndex()) {
            coroutineContext.ensureActive()
            val nextStep = steps.getOrNull(idx + 1)
            onStepProgress(idx, "Running step ${idx + 1}/${steps.size}: ${step.description}")

            val preSnap = if (tabId.isNotEmpty() && navigationSynchronizer != null) {
                navigationSynchronizer.snapshot(tabId)
            } else {
                null
            }

            val result = executeSingleStep(
                step = step,
                activeUrl = activeUrl,
                tabId = tabId,
                navigationSynchronizer = navigationSynchronizer,
                nextStep = nextStep,
                webViewProvider = webViewProvider,
                onNavigateUrl = { dest ->
                    activeUrl = dest
                    onNavigateUrl(dest)
                }
            )
            coroutineContext.ensureActive()

            if (!result.success) {
                notes.add("Step ${idx + 1} failed: ${result.message}")
                return WorkflowExecutionOutcome(
                    status = "FAILED",
                    completedSteps = completedCount,
                    totalSteps = steps.size,
                    verificationSummary = result.message,
                    errorCode = result.errorCode
                )
            }

            if (step.actionType == AutomationActionType.NAVIGATE) {
                activeUrl = navigationSynchronizer?.snapshot(tabId)?.activeUrl?.ifBlank { activeUrl } ?: activeUrl
            } else if (nextStep != null) {
                val postSyncResult = synchronizePostStepState(
                    step = step,
                    nextStep = nextStep,
                    tabId = tabId,
                    preSnapshot = preSnap,
                    navigationSynchronizer = navigationSynchronizer,
                    webViewProvider = webViewProvider
                )
                if (!postSyncResult.success) {
                    return WorkflowExecutionOutcome(
                        status = "FAILED",
                        completedSteps = completedCount,
                        totalSteps = steps.size,
                        verificationSummary = postSyncResult.message,
                        errorCode = postSyncResult.errorCode
                    )
                }
                if (tabId.isNotEmpty() && navigationSynchronizer != null) {
                    val liveUrl = navigationSynchronizer.snapshot(tabId).activeUrl
                    if (liveUrl.isNotBlank()) activeUrl = liveUrl
                }
            }

            completedCount++
            onStepCompleted(completedCount)
            notes.add("Step ${idx + 1}: ${result.message}")
            onStepProgress(idx + 1, "Verified: ${result.message}")
        }

        return WorkflowExecutionOutcome(
            status = "COMPLETED",
            completedSteps = completedCount,
            totalSteps = steps.size,
            verificationSummary = "Verified $completedCount/${steps.size} steps on ${extractDomain(activeUrl)}. ${notes.lastOrNull().orEmpty()}",
            errorCode = AutomationErrorCode.NONE
        )
    }

    internal suspend fun executeSingleStep(
        step: AutomationStep,
        activeUrl: String,
        tabId: String = "",
        navigationSynchronizer: TabNavigationSynchronizer? = null,
        nextStep: AutomationStep? = null,
        webViewProvider: () -> WebView?,
        onNavigateUrl: (String) -> Unit
    ): StepExecutionResult {
        coroutineContext.ensureActive()

        if (step.actionType == AutomationActionType.NAVIGATE) {
            val dest = step.value.ifBlank { step.targetLabel }.trim()
            if (dest.isBlank() || dest.startsWith("javascript:", ignoreCase = true)) {
                return StepExecutionResult(
                    success = false,
                    message = "Invalid or missing target URL for navigation step.",
                    errorCode = AutomationErrorCode.INVALID_URL
                )
            }
            return navigateAndAwaitSettlement(
                dest = dest,
                tabId = tabId,
                navigationSynchronizer = navigationSynchronizer,
                webViewProvider = webViewProvider,
                nextStep = nextStep,
                onNavigateUrl = onNavigateUrl
            )
        }

        val webView = webViewProvider()
        if (webView == null || activeUrl.equals("apex://home", ignoreCase = true)) {
            return StepExecutionResult(
                success = false,
                message = "No active webpage loaded in WebView to execute '${step.description}'.",
                errorCode = AutomationErrorCode.WEBVIEW_UNAVAILABLE
            )
        }

        val trimmedSelector = step.selector.trim()
        if (trimmedSelector.isNotEmpty()) {
            val validation = validateSelector(trimmedSelector)
            if (!validation.isValid) {
                return StepExecutionResult(
                    success = false,
                    message = "Unsafe or malformed CSS selector '$trimmedSelector': ${validation.reason}",
                    errorCode = AutomationErrorCode.INVALID_SELECTOR
                )
            }
        } else if (step.targetLabel.isBlank() && step.actionType != AutomationActionType.SCROLL) {
            return StepExecutionResult(
                success = false,
                message = "Step '${step.description}' is missing both selector and target label.",
                errorCode = AutomationErrorCode.INVALID_SELECTOR
            )
        }

        val selectorEsc = JSONObject.quote(trimmedSelector)
        val labelEsc = JSONObject.quote(step.targetLabel.trim())
        val valueEsc = JSONObject.quote(step.value)

        val script = buildStepExecutionScript(
            step = step,
            selectorEsc = selectorEsc,
            labelEsc = labelEsc,
            valueEsc = valueEsc
        )

        return when (val evalResult = evalJsWithResult(webView, script)) {
            is JsEvalResult.Timeout -> StepExecutionResult(
                success = false,
                message = "WebView DOM timed out after ${evalResult.timeoutMs}ms for '${step.description}'.",
                errorCode = AutomationErrorCode.JS_TIMEOUT
            )
            is JsEvalResult.Unavailable -> StepExecutionResult(
                success = false,
                message = "WebView became unavailable while executing '${step.description}': ${evalResult.reason}",
                errorCode = AutomationErrorCode.WEBVIEW_UNAVAILABLE
            )
            is JsEvalResult.Failure -> StepExecutionResult(
                success = false,
                message = "WebView script evaluation failed for '${step.description}': ${evalResult.reason}",
                errorCode = AutomationErrorCode.JS_EXCEPTION
            )
            is JsEvalResult.Success -> parseStepJsonResponse(evalResult.rawValue, step)
        }
    }

    internal fun buildStepExecutionScript(
        step: AutomationStep,
        selectorEsc: String = JSONObject.quote(step.selector.trim()),
        labelEsc: String = JSONObject.quote(step.targetLabel.trim()),
        valueEsc: String = JSONObject.quote(step.value)
    ): String {
        return when (step.actionType) {
            AutomationActionType.SCROLL -> {
                val dy = if (step.targetLabel.contains("up", ignoreCase = true)) -480 else 480
                """
                    (function() {
                        try {
                            window.scrollBy({ top: $dy, behavior: 'smooth' });
                            return JSON.stringify({ ok: true, code: 'NONE', msg: 'Scrolled viewport ${if (dy > 0) "down" else "up"}' });
                        } catch (e) {
                            return JSON.stringify({ ok: false, code: 'JS_EXCEPTION', msg: 'Scroll failed: ' + (e && e.message ? e.message : String(e)) });
                        }
                    })();
                """.trimIndent()
            }

            AutomationActionType.INPUT -> """
                (function() {
                    try {
                        $JS_TARGET_HELPERS
                        const sel = $selectorEsc;
                        const labelHint = $labelEsc;
                        const val = $valueEsc;
                        const resolved = apexResolveTarget(sel, labelHint, 'input:not([type="hidden"]), textarea');
                        if (!resolved.ok) {
                            return JSON.stringify({ ok: false, code: resolved.code, msg: resolved.msg });
                        }
                        const target = resolved.el;
                        if (target.readOnly) {
                            return JSON.stringify({ ok: false, code: 'ELEMENT_DISABLED', msg: 'Input field is read-only for "' + (labelHint || sel) + '"' });
                        }
                        target.focus();
                        const proto = Object.getPrototypeOf(target);
                        const desc = proto ? Object.getOwnPropertyDescriptor(proto, 'value') : null;
                        if (desc && desc.set) desc.set.call(target, val); else target.value = val;
                        target.dispatchEvent(new Event('input', { bubbles: true }));
                        target.dispatchEvent(new Event('change', { bubbles: true }));
                        return JSON.stringify({ ok: true, code: 'NONE', msg: 'Entered value in ' + (labelHint || sel) });
                    } catch (e) {
                        return JSON.stringify({ ok: false, code: 'JS_EXCEPTION', msg: 'Input step error: ' + (e && e.message ? e.message : String(e)) });
                    }
                })();
            """.trimIndent()

            AutomationActionType.TOGGLE -> """
                (function() {
                    try {
                        $JS_TARGET_HELPERS
                        const sel = $selectorEsc;
                        const labelHint = $labelEsc;
                        const desired = apexNormalize($valueEsc);
                        const resolved = apexResolveTarget(sel, labelHint, 'input[type="checkbox"], input[type="radio"], [role="checkbox"], [role="switch"]');
                        if (!resolved.ok) {
                            return JSON.stringify({ ok: false, code: resolved.code, msg: resolved.msg });
                        }
                        const target = resolved.el;
                        target.scrollIntoView({ behavior: 'smooth', block: 'center' });
                        const tag = (target.tagName || '').toLowerCase();
                        const type = (target.getAttribute && target.getAttribute('type') || '').toLowerCase();
                        if ((tag === 'input' && (type === 'checkbox' || type === 'radio')) && (desired === 'checked' || desired === 'unchecked')) {
                            const wantChecked = desired === 'checked';
                            if (Boolean(target.checked) !== wantChecked) {
                                target.click();
                            }
                        } else {
                            target.click();
                        }
                        return JSON.stringify({ ok: true, code: 'NONE', msg: 'Toggled ' + (labelHint || sel) });
                    } catch (e) {
                        return JSON.stringify({ ok: false, code: 'JS_EXCEPTION', msg: 'Toggle step error: ' + (e && e.message ? e.message : String(e)) });
                    }
                })();
            """.trimIndent()

            AutomationActionType.SELECT -> """
                (function() {
                    try {
                        $JS_TARGET_HELPERS
                        const sel = $selectorEsc;
                        const labelHint = $labelEsc;
                        const val = String($valueEsc || '').trim();
                        const valLower = val.toLowerCase();
                        const resolved = apexResolveTarget(sel, labelHint, 'select, [role="listbox"], [role="option"], input:not([type="hidden"]), textarea');
                        if (!resolved.ok) {
                            return JSON.stringify({ ok: false, code: resolved.code, msg: resolved.msg });
                        }
                        const target = resolved.el;
                        const tag = (target.tagName || '').toLowerCase();
                        if (tag === 'select') {
                            if (!val) {
                                target.focus();
                                return JSON.stringify({ ok: true, code: 'NONE', msg: 'Focused dropdown ' + (labelHint || sel) });
                            }
                            const options = Array.from(target.options || []);
                            const exactOpts = options.filter(function(o) {
                                return apexNormalize(o.value) === valLower || apexNormalize(o.text) === valLower;
                            });
                            const matchedOpts = exactOpts.length > 0
                                ? exactOpts
                                : options.filter(function(o) { return apexNormalize(o.text).includes(valLower); });
                            if (matchedOpts.length === 0) {
                                return JSON.stringify({ ok: false, code: 'OPTION_NOT_FOUND', msg: 'Option "' + val + '" not found in "' + (labelHint || sel) + '"' });
                            }
                            if (matchedOpts.length > 1) {
                                return JSON.stringify({ ok: false, code: 'ELEMENT_AMBIGUOUS', msg: 'Multiple options match "' + val + '" in "' + (labelHint || sel) + '"' });
                            }
                            const chosenOpt = matchedOpts[0];
                            chosenOpt.selected = true;
                            target.value = chosenOpt.value;
                            target.dispatchEvent(new Event('input', { bubbles: true }));
                            target.dispatchEvent(new Event('change', { bubbles: true }));
                            return JSON.stringify({ ok: true, code: 'NONE', msg: 'Selected "' + (chosenOpt.text || chosenOpt.value).trim() + '"' });
                        }
                        if ((tag === 'input' || tag === 'textarea') && !val) {
                            target.scrollIntoView({ behavior: 'smooth', block: 'center' });
                            target.focus();
                            return JSON.stringify({ ok: true, code: 'NONE', msg: 'Focused ' + (labelHint || sel) });
                        }
                        if (target.getAttribute && target.getAttribute('role') === 'listbox' && val) {
                            const roleOpts = Array.from(target.querySelectorAll('[role="option"]')).filter(function(o) {
                                return apexNormalize(o.innerText || o.textContent || o.getAttribute('data-value') || '').includes(valLower);
                            });
                            if (roleOpts.length === 0) {
                                return JSON.stringify({ ok: false, code: 'OPTION_NOT_FOUND', msg: 'Option "' + val + '" not found in listbox' });
                            }
                            if (roleOpts.length > 1) {
                                return JSON.stringify({ ok: false, code: 'ELEMENT_AMBIGUOUS', msg: 'Multiple listbox options match "' + val + '"' });
                            }
                            roleOpts[0].click();
                            return JSON.stringify({ ok: true, code: 'NONE', msg: 'Selected "' + val + '"' });
                        }
                        target.scrollIntoView({ behavior: 'smooth', block: 'center' });
                        target.click();
                        return JSON.stringify({ ok: true, code: 'NONE', msg: 'Selected ' + (labelHint || sel) });
                    } catch (e) {
                        return JSON.stringify({ ok: false, code: 'JS_EXCEPTION', msg: 'Select step error: ' + (e && e.message ? e.message : String(e)) });
                    }
                })();
            """.trimIndent()

            AutomationActionType.EXTRACT -> """
                (function() {
                    try {
                        const sel = String($selectorEsc || '').trim() || 'article, h1, h2, h3, p';
                        let nodes = [];
                        try {
                            nodes = Array.from(document.querySelectorAll(sel));
                        } catch (e) {
                            return JSON.stringify({ ok: false, code: 'INVALID_SELECTOR', msg: 'Invalid CSS selector "' + sel + '": ' + (e && e.message ? e.message : String(e)) });
                        }
                        if (nodes.length === 0) {
                            return JSON.stringify({ ok: false, code: 'ELEMENT_NOT_FOUND', msg: 'No matching content nodes found for "' + sel + '"' });
                        }
                        nodes.slice(0, 5).forEach(function(el) { el.style.outline = '2px solid #38bdf8'; });
                        return JSON.stringify({ ok: true, code: 'NONE', msg: 'Extracted & highlighted ' + nodes.length + ' DOM nodes' });
                    } catch (e) {
                        return JSON.stringify({ ok: false, code: 'JS_EXCEPTION', msg: 'Extract step error: ' + (e && e.message ? e.message : String(e)) });
                    }
                })();
            """.trimIndent()

            else -> """
                (function() {
                    try {
                        $JS_TARGET_HELPERS
                        const sel = $selectorEsc;
                        const labelHint = $labelEsc;
                        const defaultQuery = ${
                            if (step.actionType == AutomationActionType.SUBMIT) {
                                "'button[type=\"submit\"], input[type=\"submit\"], button'"
                            } else {
                                "'button, a, [role=\"button\"], input[type=\"submit\"], input[type=\"button\"]'"
                            }
                        };
                        const resolved = apexResolveTarget(sel, labelHint, defaultQuery);
                        if (!resolved.ok) {
                            return JSON.stringify({ ok: false, code: resolved.code, msg: resolved.msg });
                        }
                        const target = resolved.el;
                        target.scrollIntoView({ behavior: 'smooth', block: 'center' });
                        target.style.outline = '2px solid #38bdf8';
                        target.click();
                        return JSON.stringify({ ok: true, code: 'NONE', msg: 'Activated ' + ((target.innerText || labelHint || sel).trim().slice(0, 42) || 'element') });
                    } catch (e) {
                        return JSON.stringify({ ok: false, code: 'JS_EXCEPTION', msg: 'Activation step error: ' + (e && e.message ? e.message : String(e)) });
                    }
                })();
            """.trimIndent()
        }
    }

    private fun parseStepJsonResponse(raw: String, step: AutomationStep): StepExecutionResult {
        if (raw.isBlank() || raw == "null") {
            return StepExecutionResult(
                success = false,
                message = "WebView DOM did not respond for '${step.description}'.",
                errorCode = AutomationErrorCode.JS_EXCEPTION
            )
        }
        return runCatching {
            val unquoted = if (raw.startsWith("\"") && raw.endsWith("\"")) {
                JSONTokener(raw).nextValue().toString()
            } else {
                raw
            }
            val obj = JSONObject(unquoted)
            val ok = obj.optBoolean("ok", false)
            val rawCode = obj.optString("code", "")
            val parsedCode = runCatching {
                if (rawCode.isNotBlank()) AutomationErrorCode.valueOf(rawCode) else null
            }.getOrNull() ?: if (ok) AutomationErrorCode.NONE else AutomationErrorCode.JS_EXCEPTION
            StepExecutionResult(
                success = ok,
                message = obj.optString("msg", "Step evaluated"),
                errorCode = parsedCode
            )
        }.getOrElse {
            StepExecutionResult(
                success = false,
                message = "Invalid DOM response while running '${step.description}'.",
                errorCode = AutomationErrorCode.JS_EXCEPTION
            )
        }
    }

    private suspend fun navigateAndAwaitSettlement(
        dest: String,
        tabId: String,
        navigationSynchronizer: TabNavigationSynchronizer?,
        webViewProvider: () -> WebView?,
        nextStep: AutomationStep?,
        onNavigateUrl: (String) -> Unit
    ): StepExecutionResult {
        coroutineContext.ensureActive()
        val preEpoch = if (tabId.isNotEmpty() && navigationSynchronizer != null) {
            navigationSynchronizer.snapshot(tabId).epoch
        } else {
            0L
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            onNavigateUrl(dest)
        } else {
            withTimeoutOrNull(jsEvalTimeoutMs) {
                suspendCancellableCoroutine { cont ->
                    mainHandler.post {
                        if (cont.isActive) {
                            runCatching { onNavigateUrl(dest) }
                                .onSuccess { cont.resume(Unit) }
                                .onFailure { t ->
                                    if (t is CancellationException) throw t
                                    if (cont.isActive) cont.resume(Unit)
                                }
                        }
                    }
                }
            }
        }
        coroutineContext.ensureActive()

        if (tabId.isNotEmpty() && navigationSynchronizer != null) {
            val snapAfterInvoke = navigationSynchronizer.snapshot(tabId)
            val expectedEpoch = maxOf(preEpoch + 1L, snapAfterInvoke.epoch)
            when (val waitRes = navigationSynchronizer.awaitNavigationSettled(tabId, expectedEpoch, navTimeoutMs)) {
                is NavigationWaitResult.Failed -> {
                    return StepExecutionResult(
                        success = false,
                        message = "Navigation to $dest failed: ${waitRes.reason}",
                        errorCode = AutomationErrorCode.NAVIGATION_FAILED
                    )
                }
                is NavigationWaitResult.Timeout -> {
                    return StepExecutionResult(
                        success = false,
                        message = "Navigation to $dest timed out after ${waitRes.timeoutMs}ms.",
                        errorCode = AutomationErrorCode.NAVIGATION_TIMEOUT
                    )
                }
                is NavigationWaitResult.Completed -> {
                    val readyRes = awaitDomReady(
                        webViewProvider = webViewProvider,
                        nextStep = nextStep,
                        tabId = tabId,
                        navigationSynchronizer = navigationSynchronizer
                    )
                    if (!readyRes.success) return readyRes
                    return StepExecutionResult(
                        success = true,
                        message = "Navigated to ${waitRes.finalUrl.ifBlank { dest }}",
                        errorCode = AutomationErrorCode.NONE
                    )
                }
            }
        }

        // Direct WebView polling fallback if no external synchronizer was supplied
        awaitDomReady(
            webViewProvider = webViewProvider,
            nextStep = nextStep,
            tabId = tabId,
            navigationSynchronizer = null
        )
        return StepExecutionResult(
            success = true,
            message = "Navigated to $dest",
            errorCode = AutomationErrorCode.NONE
        )
    }

    private suspend fun synchronizePostStepState(
        step: AutomationStep,
        nextStep: AutomationStep,
        tabId: String,
        preSnapshot: TabNavigationSnapshot?,
        navigationSynchronizer: TabNavigationSynchronizer?,
        webViewProvider: () -> WebView?
    ): StepExecutionResult {
        coroutineContext.ensureActive()
        if (tabId.isNotEmpty() && navigationSynchronizer != null && preSnapshot != null) {
            // Brief non-blocking yield so WebView navigation callbacks queued by click()/submit() can land
            if (step.actionType == AutomationActionType.CLICK || step.actionType == AutomationActionType.SUBMIT) {
                delay(minOf(40L, domStabilityPollMs))
                coroutineContext.ensureActive()
            }
            val currentSnap = navigationSynchronizer.snapshot(tabId)
            if (currentSnap.phase == NavigationLifecyclePhase.FAILED &&
                currentSnap.epoch >= preSnapshot.epoch &&
                currentSnap.updatedAtMs > preSnapshot.updatedAtMs
            ) {
                return StepExecutionResult(
                    success = false,
                    message = "Navigation failed after '${step.description}': ${currentSnap.failureReason ?: "Load error"}",
                    errorCode = AutomationErrorCode.NAVIGATION_FAILED
                )
            }
            val navStarted = currentSnap.phase == NavigationLifecyclePhase.REQUESTED ||
                currentSnap.phase == NavigationLifecyclePhase.LOADING ||
                currentSnap.epoch > preSnapshot.epoch
            if (navStarted) {
                val targetEpoch = if (currentSnap.epoch > preSnapshot.epoch) {
                    currentSnap.epoch
                } else {
                    preSnapshot.epoch
                }
                when (val waitRes = navigationSynchronizer.awaitNavigationSettled(tabId, targetEpoch, navTimeoutMs)) {
                    is NavigationWaitResult.Failed -> return StepExecutionResult(
                        success = false,
                        message = "Navigation failed after '${step.description}': ${waitRes.reason}",
                        errorCode = AutomationErrorCode.NAVIGATION_FAILED
                    )
                    is NavigationWaitResult.Timeout -> return StepExecutionResult(
                        success = false,
                        message = "Navigation timed out after '${step.description}'.",
                        errorCode = AutomationErrorCode.NAVIGATION_TIMEOUT
                    )
                    is NavigationWaitResult.Completed -> Unit
                }
            }
        }
        return awaitDomReady(
            webViewProvider = webViewProvider,
            nextStep = nextStep,
            tabId = tabId,
            navigationSynchronizer = navigationSynchronizer
        )
    }

    internal suspend fun awaitDomReady(
        webViewProvider: () -> WebView?,
        nextStep: AutomationStep?,
        tabId: String,
        navigationSynchronizer: TabNavigationSynchronizer?
    ): StepExecutionResult {
        val wv = webViewProvider() ?: return StepExecutionResult(true, "DOM check skipped (no WebView)")
        val needsTargetPresence = nextStep != null &&
            nextStep.actionType != AutomationActionType.NAVIGATE &&
            nextStep.actionType != AutomationActionType.SCROLL &&
            (nextStep.selector.isNotBlank() || nextStep.targetLabel.isNotBlank())

        val nextSelEsc = JSONObject.quote(nextStep?.selector?.trim().orEmpty())
        val nextLabelEsc = JSONObject.quote(nextStep?.targetLabel?.trim().orEmpty())
        val nextQueryEsc = JSONObject.quote(
            when (nextStep?.actionType) {
                AutomationActionType.INPUT -> "input:not([type=\"hidden\"]), textarea"
                AutomationActionType.TOGGLE -> "input[type=\"checkbox\"], input[type=\"radio\"], [role=\"checkbox\"]"
                AutomationActionType.SELECT -> "select, [role=\"listbox\"], [role=\"option\"]"
                else -> "button, a, [role=\"button\"], input, select"
            }
        )

        val probeScript = """
            (function() {
                try {
                    $JS_TARGET_HELPERS
                    const readyState = String(document.readyState || '');
                    const url = String(window.location && window.location.href || '');
                    const childLen = document.body ? document.body.children.length : 0;
                    const textLen = document.body ? (document.body.innerText || '').length : 0;
                    let targetReady = true;
                    if ($needsTargetPresence) {
                        targetReady = apexResolveTarget($nextSelEsc, $nextLabelEsc, $nextQueryEsc).ok;
                    }
                    return JSON.stringify({
                        readyState: readyState,
                        url: url,
                        shape: childLen + ':' + textLen,
                        targetReady: Boolean(targetReady)
                    });
                } catch (e) {
                    return JSON.stringify({ readyState: 'error', targetReady: false });
                }
            })();
        """.trimIndent()

        val deadline = System.currentTimeMillis() + domReadyTimeoutMs
        var lastShape: String? = null
        var settledChecks = 0

        while (true) {
            coroutineContext.ensureActive()
            if (tabId.isNotEmpty() && navigationSynchronizer != null) {
                val snap = navigationSynchronizer.snapshot(tabId)
                if (snap.phase == NavigationLifecyclePhase.FAILED) {
                    return StepExecutionResult(
                        success = false,
                        message = snap.failureReason ?: "Page load failed",
                        errorCode = AutomationErrorCode.NAVIGATION_FAILED
                    )
                }
                if (snap.phase == NavigationLifecyclePhase.REQUESTED || snap.phase == NavigationLifecyclePhase.LOADING) {
                    when (val waitRes = navigationSynchronizer.awaitNavigationSettled(tabId, snap.epoch, navTimeoutMs)) {
                        is NavigationWaitResult.Failed -> return StepExecutionResult(
                            success = false,
                            message = waitRes.reason,
                            errorCode = AutomationErrorCode.NAVIGATION_FAILED
                        )
                        is NavigationWaitResult.Timeout -> return StepExecutionResult(
                            success = false,
                            message = "Page redirect timed out after ${waitRes.timeoutMs}ms.",
                            errorCode = AutomationErrorCode.NAVIGATION_TIMEOUT
                        )
                        is NavigationWaitResult.Completed -> Unit
                    }
                }
            }

            val evalRes = evalJsWithResult(wv, probeScript, timeoutMs = minOf(jsEvalTimeoutMs, 1_500L))
            when (evalRes) {
                is JsEvalResult.Unavailable, is JsEvalResult.Failure -> {
                    return StepExecutionResult(true, "DOM readiness check skipped")
                }
                is JsEvalResult.Timeout -> {
                    return StepExecutionResult(
                        success = false,
                        message = "DOM readiness probe timed out.",
                        errorCode = AutomationErrorCode.JS_TIMEOUT
                    )
                }
                is JsEvalResult.Success -> {
                    val parsed = runCatching {
                        val raw = evalRes.rawValue
                        val unquoted = if (raw.startsWith("\"") && raw.endsWith("\"")) {
                            JSONTokener(raw).nextValue().toString()
                        } else {
                            raw
                        }
                        JSONObject(unquoted)
                    }.getOrNull() ?: return StepExecutionResult(true, "DOM probe non-JSON")

                    val readyState = parsed.optString("readyState", "")
                    val shape = parsed.optString("shape", "")
                    val targetReady = parsed.optBoolean("targetReady", true)

                    if (readyState == "complete" && targetReady) {
                        return StepExecutionResult(true, "DOM ready")
                    }
                    if (readyState == "complete" && !needsTargetPresence) {
                        if (lastShape == shape) {
                            settledChecks++
                            if (settledChecks >= 1) return StepExecutionResult(true, "DOM stabilized")
                        } else {
                            settledChecks = 0
                        }
                    }
                    lastShape = shape
                }
            }

            if (System.currentTimeMillis() >= deadline) {
                return StepExecutionResult(true, "DOM readiness window reached")
            }
            delay(domStabilityPollMs)
        }
    }

    internal suspend fun evalJsWithResult(
        webView: WebView?,
        script: String,
        timeoutMs: Long = jsEvalTimeoutMs
    ): JsEvalResult {
        if (webView == null) {
            return JsEvalResult.Unavailable("WebView is null")
        }
        coroutineContext.ensureActive()

        val result = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<JsEvalResult> { cont ->
                val invokeEval = {
                    if (cont.isActive) {
                        try {
                            webView.evaluateJavascript(script) { value ->
                                if (cont.isActive) {
                                    cont.resume(JsEvalResult.Success(value ?: ""))
                                }
                            }
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (t: Throwable) {
                            if (cont.isActive) {
                                cont.resume(
                                    JsEvalResult.Failure(t.message ?: "WebView evaluateJavascript failed")
                                )
                            }
                        }
                    }
                }
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    invokeEval()
                } else {
                    val posted = mainHandler.post { invokeEval() }
                    if (!posted && cont.isActive) {
                        cont.resume(JsEvalResult.Unavailable("Failed to schedule JS evaluation on main thread"))
                    }
                }
            }
        }

        coroutineContext.ensureActive()
        return result ?: JsEvalResult.Timeout(timeoutMs)
    }
}
