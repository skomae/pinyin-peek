package com.pinyinpeek.pleco

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Integration with Pleco Chinese Dictionary via its URL scheme. */
object PlecoIntegration {

    /**
     * Opens Pleco to show [chineseWord], optionally pre-filling the reader mode
     * or a specific dictionary entry.
     *
     * Pleco URL scheme: `plecoapi://x-callback-url/...` or the simpler
     * `plecoflashcard://x-callback-url/xxx`
     */
    fun lookUp(context: Context, chineseWord: String) {
        // Pleco supports x-callback-url for looking up words
        val uri = Uri.parse("plecoapi://x-callback-url/q?q=$chineseWord&x-source=PinyinPeek")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        }
        // Fallback: try the market link to install Pleco
    }

    /**
     * Opens Pleco's flashcard system to add [chineseWord] with [pinyin] and [definition].
     * Falls back to the basic lookup if flashcard API isn't available.
     */
    fun addToFlashcards(context: Context, chineseWord: String, pinyin: String, definition: String) {
        // Pleco x-callback-url for adding flashcards (varies by version)
        val uri = Uri.parse(
            "plecoapi://x-callback-url/addnote?" +
            "word=$chineseWord&pinyin=$pinyin&def=$definition&x-source=PinyinPeek"
        )
        val intent = Intent(Intent.ACTION_VIEW, uri)
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            // Fall back to basic lookup
            lookUp(context, chineseWord)
        }
    }
}