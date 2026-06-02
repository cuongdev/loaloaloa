package com.loaloaloa.ui.banks

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loaloaloa.data.repository.UserSettingsRepository
import com.loaloaloa.parser.BankRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A supported bank/wallet or user-added app entry. */
data class BankEntry(val packageName: String, val displayName: String)

/** An installed launcher app offered in the "add app" picker. */
data class InstalledApp(val packageName: String, val label: String)

data class BanksUiState(
    val query: String = "",
    val banks: List<BankEntry> = emptyList(),
    /** User-added notification sources, shown above the built-in list with a remove action. */
    val customApps: List<BankEntry> = emptyList(),
)

@HiltViewModel
class BanksViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: UserSettingsRepository,
) : ViewModel() {

    private val all: List<BankEntry> = BankRegistry.supportedPackages()
        .mapNotNull { pkg -> BankRegistry.nameFor(pkg)?.let { BankEntry(pkg, it) } }
        .sortedBy { it.displayName.lowercase() }

    private val query = MutableStateFlow("")

    val uiState: StateFlow<BanksUiState> = combine(query, settings.settings) { q, s ->
        val term = q.trim().lowercase()
        BanksUiState(
            query = q,
            banks = if (term.isEmpty()) all else all.filter { it.displayName.lowercase().contains(term) },
            customApps = s.customApps
                .map { (pkg, name) -> BankEntry(pkg, name) }
                .sortedBy { it.displayName.lowercase() },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BanksUiState(banks = all))

    /**
     * Launcher apps the user can add as sources, or null while not yet loaded. Excludes our own
     * package and anything already covered by [BankRegistry]. Loaded lazily via [loadInstalledApps]
     * because querying the PackageManager is slow.
     */
    private val _installed = MutableStateFlow<List<InstalledApp>?>(null)
    val installed: StateFlow<List<InstalledApp>?> = _installed

    fun setQuery(value: String) { query.value = value }

    /** Populate [installed] off the main thread. Safe to call repeatedly (re-queries each time). */
    fun loadInstalledApps() {
        viewModelScope.launch {
            _installed.value = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val known = BankRegistry.supportedPackages()
                pm.queryIntentActivities(intent, 0)
                    .asSequence()
                    .map { it.activityInfo.packageName }
                    .filter { it != context.packageName && it !in known }
                    .distinct()
                    .map { pkg ->
                        InstalledApp(
                            packageName = pkg,
                            label = runCatching {
                                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                            }.getOrDefault(pkg),
                        )
                    }
                    .sortedBy { it.label.lowercase() }
                    .toList()
            }
        }
    }

    /** Add (or rename) a user source, capturing its display name now. */
    fun addCustomApp(packageName: String, label: String) {
        viewModelScope.launch {
            val current = settings.settings.first().customApps
            settings.setCustomApps(current + (packageName to label))
        }
    }

    fun removeCustomApp(packageName: String) {
        viewModelScope.launch {
            val current = settings.settings.first().customApps
            settings.setCustomApps(current - packageName)
        }
    }
}
