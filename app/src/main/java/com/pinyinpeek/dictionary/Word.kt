package com.pinyinpeek.dictionary

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "words")
data class Word(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The Chinese characters, e.g. "你好". */
    val chinese: String,
    /** Pinyin with tone marks, e.g. "nǐ hǎo". */
    val pinyin: String,
    /** Pinyin without tone marks (for accent-insensitive search), e.g. "ni hao". */
    val pinyinPlain: String,
    /** English translation, e.g. "hello". */
    val english: String,
    /** When this word was saved. */
    val savedAt: Long = System.currentTimeMillis(),
)