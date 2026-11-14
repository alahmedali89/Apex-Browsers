package com.example.engine

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.nl.translate.TranslateRemoteModel
import kotlinx.coroutines.tasks.await
import java.util.Locale

data class LanguageInfo(
    val code: String,
    val displayName: String,
    val nativeName: String
)

object LanguageCatalog {
    private val languageMap = mapOf(
        "en" to LanguageInfo("en", "English", "English"),
        "en-us" to LanguageInfo("en-us", "English (United States)", "English (United States)"),
        "en-gb" to LanguageInfo("en-gb", "English (United Kingdom)", "English (United Kingdom)"),
        "en-in" to LanguageInfo("en-in", "English (India)", "English (India)"),
        "ur" to LanguageInfo("ur", "Urdu", "اردو"),
        "hi" to LanguageInfo("hi", "Hindi", "हिन्दी"),
        "es" to LanguageInfo("es", "Spanish", "Español"),
        "fr" to LanguageInfo("fr", "French", "Français"),
        "de" to LanguageInfo("de", "German", "Deutsch"),
        "ar" to LanguageInfo("ar", "Arabic", "العربية"),
        "bn" to LanguageInfo("bn", "Bengali", "বাংলা"),
        "zh" to LanguageInfo("zh", "Chinese (Simplified)", "简体中文"),
        "ja" to LanguageInfo("ja", "Japanese", "日本語"),
        "ko" to LanguageInfo("ko", "Korean", "한국어"),
        "pt" to LanguageInfo("pt", "Portuguese", "Português"),
        "ru" to LanguageInfo("ru", "Russian", "Русский"),
        "it" to LanguageInfo("it", "Italian", "Italiano"),
        "tr" to LanguageInfo("tr", "Turkish", "Türkçe"),
        "nl" to LanguageInfo("nl", "Dutch", "Nederlands"),
        "pl" to LanguageInfo("pl", "Polish", "Polski"),
        "vi" to LanguageInfo("vi", "Vietnamese", "Tiếng Việt"),
        "th" to LanguageInfo("th", "Thai", "ไทย"),
        "id" to LanguageInfo("id", "Indonesian", "Bahasa Indonesia"),
        "fa" to LanguageInfo("fa", "Persian", "فارسی")
    )

    val supportedLanguages: List<LanguageInfo> = languageMap.values.toList()

    fun defaultPreferredLanguageTags(): List<String> = listOf("en-in", "ur", "en-gb", "en-us", "en")

    fun normalizeMlKitCode(code: String?): String? {
        if (code.isNullOrBlank()) return null
        val cleaned = code.lowercase(Locale.ROOT).trim().replace('_', '-')
        if (languageMap.containsKey(cleaned)) return cleaned
        val prefix = cleaned.substringBefore('-')
        if (languageMap.containsKey(prefix)) return prefix
        return try {
            val mlKitLang = TranslateLanguage.fromLanguageTag(cleaned) ?: TranslateLanguage.fromLanguageTag(prefix)
            mlKitLang?.lowercase(Locale.ROOT) ?: cleaned
        } catch (e: Exception) {
            cleaned
        }
    }

    fun describeLanguage(code: String): LanguageInfo {
        val normalized = normalizeMlKitCode(code) ?: code.lowercase(Locale.ROOT)
        languageMap[normalized]?.let { return it }
        val prefix = normalized.substringBefore('-')
        languageMap[prefix]?.let { return it }
        
        val display = try {
            Locale.forLanguageTag(normalized).getDisplayName(Locale.getDefault()).ifBlank { normalized }
        } catch (e: Exception) {
            normalized.uppercase(Locale.ROOT)
        }
        val native = try {
            val loc = Locale.forLanguageTag(normalized)
            loc.getDisplayName(loc).ifBlank { display }
        } catch (e: Exception) {
            display
        }
        return LanguageInfo(code = normalized, displayName = display, nativeName = native)
    }
}

open class LanguageTranslationEngine {
    private val remoteModelManager by lazy { RemoteModelManager.getInstance() }
    private val languageIdentifier by lazy { LanguageIdentification.getClient() }

    open suspend fun refreshDownloadedModels(): Set<String> {
        return try {
            val models = remoteModelManager.getDownloadedModels(TranslateRemoteModel::class.java).await()
            models.map { it.language.lowercase(Locale.ROOT) }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    open suspend fun downloadLanguageModel(mlKitCode: String, requireWifi: Boolean): Result<Unit> {
        return try {
            val normalized = LanguageCatalog.normalizeMlKitCode(mlKitCode) ?: mlKitCode
            val model = TranslateRemoteModel.Builder(normalized).build()
            val conditionsBuilder = DownloadConditions.Builder()
            if (requireWifi) {
                conditionsBuilder.requireWifi()
            }
            remoteModelManager.download(model, conditionsBuilder.build()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun deleteLanguageModel(mlKitCode: String): Result<Unit> {
        return try {
            val normalized = LanguageCatalog.normalizeMlKitCode(mlKitCode) ?: mlKitCode
            val model = TranslateRemoteModel.Builder(normalized).build()
            remoteModelManager.deleteDownloadedModel(model).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun detectLanguage(text: String): String? {
        if (text.isBlank()) return null
        return try {
            val langCode = languageIdentifier.identifyLanguage(text).await()
            if (langCode == "und") null else LanguageCatalog.normalizeMlKitCode(langCode)
        } catch (e: Exception) {
            null
        }
    }

    open suspend fun translateSegments(
        segments: List<String>,
        sourceCode: String,
        targetCode: String,
        requireWifiForDownload: Boolean
    ): Result<List<String>> {
        if (segments.isEmpty()) return Result.success(emptyList())
        val srcNorm = LanguageCatalog.normalizeMlKitCode(sourceCode) ?: TranslateLanguage.ENGLISH
        val tgtNorm = LanguageCatalog.normalizeMlKitCode(targetCode) ?: TranslateLanguage.ENGLISH
        
        if (srcNorm == tgtNorm) return Result.success(segments)

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(srcNorm)
            .setTargetLanguage(tgtNorm)
            .build()
        val translator = Translation.getClient(options)

        return try {
            val conditionsBuilder = DownloadConditions.Builder()
            if (requireWifiForDownload) {
                conditionsBuilder.requireWifi()
            }
            translator.downloadModelIfNeeded(conditionsBuilder.build()).await()
            val translated = segments.map { segment ->
                if (segment.isBlank()) segment
                else {
                    try {
                        translator.translate(segment).await()
                    } catch (e: Exception) {
                        segment
                    }
                }
            }
            Result.success(translated)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                translator.close()
            } catch (ignored: Exception) {}
        }
    }
}
