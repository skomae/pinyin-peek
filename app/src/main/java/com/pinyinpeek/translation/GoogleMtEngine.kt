package com.pinyinpeek.translation

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device neural machine translation via Google ML Kit (same as VoicePad).
 * The ~30 MB model downloads on first use; then translation works offline.
 */
class GoogleMtEngine : TranslationEngine {

    override val id = TranslationEngineType.GOOGLE_MT.id
    override val displayName = TranslationEngineType.GOOGLE_MT.displayName
    override val description = "Google ML Kit neural translation (~30 MB model, downloads once)."

    private val translator by lazy {
        Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.CHINESE)
                .setTargetLanguage(TranslateLanguage.ENGLISH)
                .build()
        )
    }

    override suspend fun translate(chineseText: String): String {
        if (chineseText.isBlank()) return ""
        Log.d("PinyinPeek", "google-mt: downloadModelIfNeeded...")
        try {
            withTimeout(20_000L) {
                translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            }
            Log.d("PinyinPeek", "google-mt: model ready, translating...")
            val english = withTimeout(10_000L) {
                translator.translate(chineseText.trim()).await()
            }
            Log.d("PinyinPeek", "google-mt: result=\"" + english.take(60) + "\"")
            return english
        } catch (e: TimeoutCancellationException) {
            Log.w("PinyinPeek", "google-mt: timed out")
            throw e
        } catch (e: Exception) {
            Log.w("PinyinPeek", "google-mt: failed (" + e.message + ")")
            throw e
        }
    }

    override fun close() { translator.close() }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { e -> cont.resumeWithException(e) }
    addOnCanceledListener { cont.cancel() }
}