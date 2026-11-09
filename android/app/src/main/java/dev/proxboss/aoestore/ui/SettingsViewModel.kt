package dev.proxboss.aoestore.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.proxboss.aoestore.data.ApiException
import dev.proxboss.aoestore.data.SettingsStore
import dev.proxboss.aoestore.data.StoreRepository
import dev.proxboss.aoestore.data.UrlMapper
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val serverUrl: String = UrlMapper.DEFAULT_SERVER,
    val themeMode: String = "dark",
    val isTesting: Boolean = false,
    val testMessage: String? = null,
    val testOk: Boolean? = null,
    val saved: Boolean = false,
)

class SettingsViewModel(
    private val settings: SettingsStore,
    private val repository: StoreRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settings.serverUrl.collect { url ->
                _state.update { it.copy(serverUrl = url) }
            }
        }
        viewModelScope.launch {
            settings.themeMode.collect { mode ->
                _state.update { it.copy(themeMode = mode) }
            }
        }
    }

    fun onServerUrlChange(value: String) {
        _state.update { it.copy(serverUrl = value, saved = false, testMessage = null, testOk = null) }
    }

    fun saveServerUrl() {
        viewModelScope.launch {
            val normalized = UrlMapper.normalizeBase(_state.value.serverUrl)
            settings.setServerUrl(normalized)
            _state.update { it.copy(serverUrl = normalized, saved = true) }
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            settings.setThemeMode(mode)
            _state.update { it.copy(themeMode = mode) }
        }
    }

    fun testConnection() {
        if (_state.value.isTesting) return
        viewModelScope.launch {
            _state.update { it.copy(isTesting = true, testMessage = null, testOk = null) }
            val normalized = UrlMapper.normalizeBase(_state.value.serverUrl)
            try {
                val health = repository.health(normalized)
                _state.update {
                    it.copy(
                        isTesting = false,
                        testOk = true,
                        testMessage = "Connexion réussie — ${health.apps} application(s) au catalogue.",
                    )
                }
            } catch (error: Exception) {
                val message = when (error) {
                    is ApiException -> error.message ?: "Erreur du serveur"
                    is IOException -> "Serveur injoignable. Vérifiez l’adresse et le réseau."
                    else -> error.message ?: "Erreur inconnue"
                }
                _state.update { it.copy(isTesting = false, testOk = false, testMessage = message) }
            }
        }
    }
}
