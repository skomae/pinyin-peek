package com.pinyinpeek.translation

import android.content.Context
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
 * On-device Chinese→English translation via ML Kit, with a bundled CC-CEDICT
 * dictionary fallback when the model can't be downloaded (no network, etc.).
 */
class Translator(context: Context) {

    private val translator by lazy {
        Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.CHINESE)
                .setTargetLanguage(TranslateLanguage.ENGLISH)
                .build()
        )
    }

    private val dictionary by lazy { DictionaryEngine(context) }

    /**
     * Translates [chineseText] to English. Tries ML Kit first; if the model
     * can't be downloaded or translation fails, falls back to the bundled
     * dictionary.
     */
    suspend fun translate(chineseText: String): String {
        if (chineseText.isBlank()) return ""
        try {
            val result = translateWithMlKit(chineseText)
            if (result.isNotBlank()) return result
        } catch (e: TimeoutCancellationException) {
            Log.w("PinyinPeek", "ML Kit timed out, using dictionary fallback")
        } catch (e: Exception) {
            Log.w("PinyinPeek", "ML Kit failed (" + e.message + "), using dictionary fallback")
        }
        // Fallback: bundled dictionary
        val gloss = dictionary.gloss(chineseText)
        Log.d("PinyinPeek", "dictionary fallback result=\"" + gloss.take(60) + "\"")
        return gloss
    }

    private suspend fun translateWithMlKit(text: String): String {
        Log.d("PinyinPeek", "translator: downloadModelIfNeeded...")
        withTimeout(20_000L) {
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
        }
        Log.d("PinyinPeek", "translator: model ready, translating...")
        val english = withTimeout(10_000L) {
            translator.translate(text.trim()).await()
        }
        Log.d("PinyinPeek", "translator: result=\"" + english.take(60) + "\"")
        return english
    }

    fun close() { translator.close() }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { e -> cont.resumeWithException(e) }
    addOnCanceledListener { cont.cancel() }
}