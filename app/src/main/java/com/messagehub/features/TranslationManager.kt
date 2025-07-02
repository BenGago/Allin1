package com.messagehub.features

import android.content.Context
import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranslationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val TAG = "TranslationManager"
        private const val CONFIDENCE_THRESHOLD = 0.5f
    }
    
    private val languageIdentifier = LanguageIdentification.getClient()
    
    suspend fun detectAndTranslate(text: String, targetLanguage: String = "en"): TranslationResult = withContext(Dispatchers.IO) {
        try {
            // Detect language
            val detectedLanguage = languageIdentifier.identifyLanguage(text).await()
            
            if (detectedLanguage == "und") {
                return@withContext TranslationResult(
                    originalText = text,
                    translatedText = null,
                    detectedLanguage = "unknown",
                    confidence = 0f,
                    error = "Could not detect language"
                )
            }
            
            // Don't translate if already in target language
            if (detectedLanguage == targetLanguage) {
                return@withContext TranslationResult(
                    originalText = text,
                    translatedText = null,
                    detectedLanguage = detectedLanguage,
                    confidence = 1f,
                    error = null
                )
            }
            
            // Create translator
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(detectedLanguage)
                .setTargetLanguage(targetLanguage)
                .build()
            
            val translator = Translation.getClient(options)
            
            // Download model if needed
            val conditions = DownloadConditions.Builder()
                .requireWifi()
                .build()
            
            translator.downloadModelIfNeeded(conditions).await()
            
            // Translate
            val translatedText = translator.translate(text).await()
            
            TranslationResult(
                originalText = text,
                translatedText = translatedText,
                detectedLanguage = detectedLanguage,
                confidence = 1f,
                error = null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Translation failed", e)
            TranslationResult(
                originalText = text,
                translatedText = null,
                detectedLanguage = "unknown",
                confidence = 0f,
                error = e.message
            )
        }
    }
    
    fun getLanguageName(languageCode: String): String {
        return when (languageCode) {
            "en" -> "English"
            "es" -> "Spanish"
            "fr" -> "French"
            "de" -> "German"
            "it" -> "Italian"
            "pt" -> "Portuguese"
            "ru" -> "Russian"
            "ja" -> "Japanese"
            "ko" -> "Korean"
            "zh" -> "Chinese"
            "ar" -> "Arabic"
            "hi" -> "Hindi"
            else -> languageCode.uppercase()
        }
    }
}

data class TranslationResult(
    val originalText: String,
    val translatedText: String?,
    val detectedLanguage: String,
    val confidence: Float,
    val error: String?
)
