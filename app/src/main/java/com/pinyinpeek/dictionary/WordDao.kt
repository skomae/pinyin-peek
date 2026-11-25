package com.pinyinpeek.dictionary

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WordDao {
    @Query("SELECT * FROM words ORDER BY savedAt DESC")
    suspend fun allWords(): List<Word>

    @Query("""
        SELECT * FROM words
        WHERE chinese LIKE '%' || :query || '%'
           OR pinyinPlain LIKE '%' || :query || '%'
           OR english LIKE '%' || :query || '%'
        ORDER BY savedAt DESC
    """)
    suspend fun search(query: String): List<Word>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(word: Word): Long

    @Query("DELETE FROM words WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM words WHERE chinese = :chinese)")
    suspend fun exists(chinese: String): Boolean
}