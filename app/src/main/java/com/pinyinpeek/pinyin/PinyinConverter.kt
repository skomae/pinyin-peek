package com.pinyinpeek.pinyin

import android.icu.text.Transliterator

/** Converts Chinese characters to pinyin and normalises for search. */
object PinyinConverter {

    private val hanToPinyin by lazy { Transliterator.getInstance("Han-Latin") }

    /** Converts [text] to pinyin with tone marks, e.g. "你好" → "nǐ hǎo". */
    fun toPinyin(text: String): String {
        if (!text.any { isChinese(it) }) return text
        val raw = hanToPinyin.transliterate(text)
        return raw.trimStart().replace(Regex("  +"), " ").trim()
    }

    /**
     * Splits [text] into individual Chinese characters and returns the pinyin
     * for each one. Non-Chinese characters and punctuation pass through.
     *
     * Example: "你好世界" → [PinyinChar('你',"nǐ"), PinyinChar('好',"hǎo"),
     *                           PinyinChar('世',"shì"), PinyinChar('界',"jiè")]
     */
    data class PinyinChar(val char: Char, val pinyin: String)

    fun toPinyinChars(text: String): List<PinyinChar> {
        return text.map { c ->
            if (isChinese(c)) {
                val p = hanToPinyin.transliterate(c.toString()).trimStart().trim()
                PinyinChar(c, p)
            } else {
                PinyinChar(c, "")
            }
        }
    }

    /** Strips tone marks from pinyin for accent-insensitive search. */
    fun stripTones(pinyin: String): String {
        val sb = StringBuilder(pinyin.length)
        for (c in pinyin) sb.append(TONE_MAP[c] ?: c)
        return sb.toString().trim().replace(Regex(" +"), " ")
    }

    /** Normalises a search query: strips tones, lowercases, trims. */
    fun normalizeQuery(query: String): String = stripTones(query).lowercase().trim()

    private fun isChinese(c: Char): Boolean =
        Character.isIdeographic(c.code) || c.code in 0x3000..0x303F || c.code in 0x20000..0x2FA1F

    private val TONE_MAP = buildMap {
        put('ā', 'a'); put('á', 'a'); put('ǎ', 'a'); put('à', 'a')
        put('ō', 'o'); put('ó', 'o'); put('ǒ', 'o'); put('ò', 'o')
        put('ē', 'e'); put('é', 'e'); put('ě', 'e'); put('è', 'e')
        put('ī', 'i'); put('í', 'i'); put('ǐ', 'i'); put('ì', 'i')
        put('ū', 'u'); put('ú', 'u'); put('ǔ', 'u'); put('ù', 'u')
        put('ǖ', 'v'); put('ǘ', 'v'); put('ǚ', 'v'); put('ǜ', 'v')
        put('Ā', 'A'); put('Á', 'A'); put('Ǎ', 'A'); put('À', 'A')
        put('Ō', 'O'); put('Ó', 'O'); put('Ǒ', 'O'); put('Ò', 'O')
        put('Ē', 'E'); put('É', 'E'); put('Ě', 'E'); put('È', 'E')
        put('Ī', 'I'); put('Í', 'I'); put('Ǐ', 'I'); put('Ì', 'I')
        put('Ū', 'U'); put('Ú', 'U'); put('Ǔ', 'U'); put('Ù', 'U')
        put('Ǖ', 'V'); put('Ǘ', 'V'); put('Ǚ', 'V'); put('Ǜ', 'V')
    }
}