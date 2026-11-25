package com.pinyinpeek.translation

import android.util.Log
import com.hankcs.hanlp.HanLP
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * HanLP-powered word segmentation + pinyin, backed by the bundled CC-CEDICT
 * dictionary for definitions.
 *
 * Fully offline — HanLP's portable jar bundles its core dictionary and pinyin
 * data, no model download and no network. Segments text with HanLP's CRF/Viterbi
 * segmenter (better word boundaries than the dictionary's naive longest-match),
 * looks each word up in CC-CEDICT for a definition, and falls back to HanLP
 * pinyin (with tone marks) for out-of-vocabulary words.
 */
class HanlpEngine(private val dictionary: DictionaryEngine) : TranslationEngine {

    override val id = TranslationEngineType.HANLP.id
    override val displayName = TranslationEngineType.HANLP.displayName
    override val description = "HanLP word segmentation + pinyin + dictionary. Fully offline."

    override suspend fun translate(chineseText: String): String = withContext(Dispatchers.Default) {
        if (chineseText.isBlank()) return@withContext ""
        Log.d("PinyinPeek", "hanlp: segmenting \"" + chineseText.take(40) + "\"")
        val parts = mutableListOf<String>()
        for (term in HanLP.segment(chineseText.trim())) {
            val word = term.word.trim()
            if (word.isEmpty()) continue
            val entry = dictionary.lookup(word)
            if (entry != null) {
                parts.add(entry.chinese + ": " + entry.english)
            } else {
                // HanLP emits a "none" pinyin for characters outside its pinyin
                // table (rare/extended hanzi); skip those rather than show "none".
                val pinyin = HanLP.convertToPinyinList(word)
                    .filter { it.getPinyinWithToneMark() != "none" }
                    .joinToString("") { it.getPinyinWithToneMark() }
                parts.add(if (pinyin.isEmpty()) word else word + ": " + pinyin)
            }
        }
        val result = parts.joinToString(" · ")
        Log.d("PinyinPeek", "hanlp: result=\"" + result.take(60) + "\"")
        result
    }
}
