package com.pinyinpeek.dictionary

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Word::class], version = 1, exportSchema = false)
abstract class WordDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao

    companion object {
        @Volatile private var instance: WordDatabase? = null

        fun get(context: Context): WordDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WordDatabase::class.java,
                    "pinyin_peek.db"
                ).build().also { instance = it }
            }
        }
    }
}