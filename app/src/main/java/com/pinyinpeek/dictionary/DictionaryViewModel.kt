package com.pinyinpeek.dictionary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pinyinpeek.pinyin.PinyinConverter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DictState(
    val words: List<Word> = emptyList(),
    val query: String = "",
    val isLoading: Boolean = false,
)

class DictionaryViewModel(application: Application) : AndroidViewModel(application) {

    private val db = WordDatabase.get(application)

    private val _state = MutableStateFlow(DictState())
    val state = _state.asStateFlow()

    init {
        loadAll()
    }

    fun loadAll() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val words = db.wordDao().allWords()
            _state.value = DictState(words = words, isLoading = false)
        }
    }

    fun search(query: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(query = query, isLoading = true)
            val normalized = PinyinConverter.normalizeQuery(query)
            val words = if (normalized.isBlank()) {
                db.wordDao().allWords()
            } else {
                db.wordDao().search(normalized)
            }
            _state.value = DictState(words = words, query = query, isLoading = false)
        }
    }

    fun delete(word: Word) {
        viewModelScope.launch {
            db.wordDao().delete(word.id)
            loadAll()
        }
    }
}