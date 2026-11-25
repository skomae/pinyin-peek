package com.pinyinpeek.translation

/** A pluggable Chinese→English translation backend. */
interface TranslationEngine {
    val id: String
    val displayName: String
    val description: String

    /** Translates [chineseText] to English (or a word-by-word gloss). */
    suspend fun translate(chineseText: String): String

    /** Releases resources; default no-op. */
    fun close() {}
}

/** The selectable translation engines. */
enum class TranslationEngineType(val id: String, val displayName: String) {
    DICTIONARY("dictionary", "Dictionary"),
    GOOGLE_MT("google_mt", "Google MT"),
    HANLP("hanlp", "HanLP");

    companion object {
        fun fromId(id: String): TranslationEngineType =
            entries.firstOrNull { it.id == id } ?: DICTIONARY
    }
}