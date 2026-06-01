package com.tingting.notifier.ui.banks

import androidx.lifecycle.ViewModel
import com.tingting.notifier.parser.BankRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import androidx.lifecycle.viewModelScope

/** A supported bank/wallet entry (read-only list). */
data class BankEntry(val packageName: String, val displayName: String)

data class BanksUiState(
    val query: String = "",
    val banks: List<BankEntry> = emptyList(),
)

@HiltViewModel
class BanksViewModel @Inject constructor() : ViewModel() {

    private val all: List<BankEntry> = BankRegistry.supportedPackages()
        .mapNotNull { pkg -> BankRegistry.nameFor(pkg)?.let { BankEntry(pkg, it) } }
        .sortedBy { it.displayName.lowercase() }

    private val query = MutableStateFlow("")

    val uiState: StateFlow<BanksUiState> = query
        .map { q ->
            val term = q.trim().lowercase()
            BanksUiState(
                query = q,
                banks = if (term.isEmpty()) all else all.filter { it.displayName.lowercase().contains(term) },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BanksUiState(banks = all))

    fun setQuery(value: String) { query.value = value }
}
