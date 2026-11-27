package com.audiobookdownloader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

/** Only small input values belong in saved state. The catalog owns its result cache. */
internal class AbSearchInputModel(private val saved: SavedStateHandle) : ViewModel() {
    private var queryValue by mutableStateOf(saved.get<String>("query").orEmpty())
    private var sourceValue by mutableStateOf(saved.get<String>("source"))

    var query: String
        get() = queryValue
        set(value) {
            queryValue = value
            saved["query"] = value
        }

    fun initializeSource(defaultSource: String) {
        if (sourceValue == null) source = defaultSource
    }

    var source: String
        get() = sourceValue.orEmpty()
        set(value) {
            sourceValue = value
            saved["source"] = value
        }
}
