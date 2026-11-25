package com.pinyinpeek.translation

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Bundled CC-CEDICT dictionary (125K entries, ~8 MB raw text in assets).
 * Fully offline — no network, no Google Play Services needed.
 * Longest-match segmentation over dictionary entries.
 */
class DictionaryEngine(context: Context) : TranslationEngine {

    override val id = TranslationEngineType.DICTIONARY.id
    override val displayName = TranslationEngineType.DICTIONARY.displayName
    override val description = "Word-by-word definitions from a bundled 125K-entry dictionary. Fully offline."

    data class Entry(val chinese: String, val pinyin: String, val english: String)

    private val entries by lazy { load(context) }

    override suspend fun translate(chineseText: String): String = gloss(chineseText)

    /** Looks up a word; returns null if not in the dictionary. */
    fun lookup(chinese: String): Entry? {
        if (chinese.isEmpty()) return null
        var lo = 0; var hi = entries.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            when {
                entries[mid].chinese < chinese -> lo = mid + 1
                entries[mid].chinese > chinese -> hi = mid - 1
                else -> return entries[mid]
            }
        }
        return null
    }

    /**
     * Segments [text] into dictionary matches (longest first). Returns a
     * word-by-word gloss like "你好: hello · 世界: world".
     */
    fun gloss(text: String): String {
        if (text.isEmpty()) return ""
        val parts = mutableListOf<String>()
        var pos = 0
        while (pos < text.length) {
            val maxLen = minOf(8, text.length - pos)
            var matched: Entry? = null
            var matchedLen = 0
            for (len in maxLen downTo 2) {
                val e = lookup(text.substring(pos, pos + len))
                if (e != null) { matched = e; matchedLen = len; break }
            }
            if (matched != null) {
                parts.add(matched.chinese + ": " + matched.english)
                pos += matchedLen
            } else {
                pos += 1 // skip unmatched char
            }
        }
        return parts.joinToString(" · ")
    }

    private fun load(context: Context): List<Entry> {
        Log.d("PinyinPeek", "dictionary: loading CC-CEDICT...")
        val start = System.currentTimeMillis()
        val result = mutableListOf<Entry>()
        context.assets.open("pinyin_peek_dict.txt").use { fis ->
            BufferedReader(InputStreamReader(fis, Charsets.UTF_8)).use { reader ->
                reader.forEachLine { line ->
                    val s1 = line.indexOf('\t'); if (s1 < 0) return@forEachLine
                    val s2 = line.indexOf('\t', s1 + 1); if (s2 < 0) return@forEachLine
                    result.add(Entry(line.substring(0, s1), line.substring(s1 + 1, s2), line.substring(s2 + 1)))
                }
            }
        }
        Log.d("PinyinPeek", "dictionary: loaded " + result.size + " entries in " + (System.currentTimeMillis() - start) + "ms")
        return result
    }
}