package dev.proxboss.aoestore.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.proxboss.aoestore.data.ApiException
import dev.proxboss.aoestore.data.AppSummary
import dev.proxboss.aoestore.data.SettingsStore
import dev.proxboss.aoestore.data.StoreRepository
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val apps: List<AppSummary> = emptyList(),
    val query: String = "",
    val category: String? = null,
    val error: String? = null,
    val offline: Boolean = false,
    val serverUrl: String = "",
)

class CatalogViewModel(
    private val repository: StoreRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settings.serverUrl.distinctUntilChanged().collectLatest { url ->
                _state.update { it.copy(serverUrl = url) }
                load(url, userInitiated = false)
            }
        }
    }

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
    }

    fun onCategorySelected(value: String?) {
        _state.update { it.copy(category = value) }
    }

    fun refresh() {
        viewModelScope.launch {
            load(_state.value.serverUrl, userInitiated = true)
        }
    }

    private suspend fun load(serverUrl: String, userInitiated: Boolean) {
        if (!userInitiated) {
            val cached = repository.cachedCatalog(serverUrl)
            if (cached != null) {
                _state.update {
                    it.copy(apps = cached, isLoading = false, error = null, offline = false)
                }
            } else {
                _state.update { it.copy(isLoading = true, error = null) }
            }
        }
        _state.update { it.copy(isRefreshing = userInitiated) }

        try {
            val apps = repository.refreshCatalog(serverUrl)
            _state.update {
                it.copy(
                    apps = apps,
                    isLoading = false,
                    isRefreshing = false,
                    error = null,
                    offline = false,
                )
            }
        } catch (error: Exception) {
            val hasData = _state.value.apps.isNotEmpty()
            _state.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    offline = hasData,
                    error = if (hasData) null else friendlyMessage(error),
                )
            }
        }
    }

    private fun friendlyMessage(error: Exception): String = when (error) {
        is ApiException -> error.message ?: "Erreur du serveur"
        is IOException -> "Impossible de joindre le serveur. Vérifiez l’URL et le réseau local."
        else -> error.message ?: "Erreur inconnue"
    }
}
