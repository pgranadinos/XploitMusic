package com.xploitmusic.app.presentation.home

import com.xploitmusic.app.data.local.entity.SearchHistoryEntity

/**
 * Unified suggestion model shown in the search dropdown.
 * Three sources merged in priority: History > Correction > Autocomplete.
 */
sealed class SearchSuggestion {
    /** From local Room DB — shown with 🕐 icon */
    data class HistoryItem(val entry: SearchHistoryEntity) : SearchSuggestion()

    /** From Piped /suggestions API — shown with 🔍 icon */
    data class AutocompleteItem(val text: String) : SearchSuggestion()
}
