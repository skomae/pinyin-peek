package com.pinyinpeek.translation

import android.content.Context
import android.content.SharedPreferences

/** Persists the user's chosen translation engine. */
class EngineSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("pinyin_peek_settings", Context.MODE_PRIVATE)

    var engineType: TranslationEngineType
        get() = TranslationEngineType.fromId(prefs.getString(KEY_ENGINE, TranslationEngineType.GOOGLE_MT.id)!!)
        set(value) = prefs.edit().putString(KEY_ENGINE, value.id).apply()

    companion object {
        private const val KEY_ENGINE = "translation_engine"
    }
}