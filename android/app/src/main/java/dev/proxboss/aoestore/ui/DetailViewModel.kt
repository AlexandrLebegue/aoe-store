package dev.proxboss.aoestore.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.proxboss.aoestore.data.ApiException
import dev.proxboss.aoestore.data.ApkDownloader
import dev.proxboss.aoestore.data.AppDetail
import dev.proxboss.aoestore.data.ChecksumMismatchException
import dev.proxboss.aoestore.data.SettingsStore
import dev.proxboss.aoestore.data.StoreRepository
import dev.proxboss.aoestore.data.UrlMapper
import dev.proxboss.aoestore.data.VersionInfo
import dev.proxboss.aoestore.data.VersionResolver
import dev.proxboss.aoestore.install.ApkInstaller
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface InstallUiState {
    data object Idle : InstallUiState
    data class Downloading(val bytesRead: Long, val totalBytes: Long) : InstallUiState
    data object Verifying : InstallUiState
    data object Ready : InstallUiState
    data class Failed(val message: String) : InstallUiState
}

data class DetailUiState(
    val isLoading: Boolean = true,
    val detail: AppDetail? = null,
    val error: String? = null,
    val installedVersionCode: Int? = null,
    val installState: InstallUiState = InstallUiState.Idle,
    val activeVersion: VersionInfo? = null,
    val needsUnknownSources: Boolean = false,
    val historyExpanded: Boolean = false,
    val message: String? = null,
    val serverUrl: String = "",
) {
    val latestVersion: VersionInfo?
        get() = detail?.let { VersionResolver.latest(it) }
}

class DetailViewModel(
    private val repository: StoreRepository,
    private val settings: SettingsStore,
    private val downloader: ApkDownloader,
    private val installer: ApkInstaller,
    private val slug: String,
) : ViewModel() {

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private var pendingApk: File? = null
    private var installJob: Job? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val serverUrl = settings.serverUrl.first()
                val detail = repository.appDetail(serverUrl, slug)
                val installed = installer.installedVersionCode(detail.packageName)
                _state.update {
                    it.copy(
                        isLoading = false,
                        detail = detail,
                        serverUrl = serverUrl,
                        installedVersionCode = installed,
                        error = null,
                        needsUnknownSources = !installer.canInstallPackages() && !isUpToDate(installed, detail),
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(isLoading = false, error = friendlyMessage(error))
                }
            }
        }
    }

    fun toggleHistory() {
        _state.update { it.copy(historyExpanded = !it.historyExpanded) }
    }

    fun install(version: VersionInfo) {
        val detail = _state.value.detail ?: return
        val installed = installer.installedVersionCode(detail.packageName)
        if (installed != null && installed >= version.versionCode) {
            openApp()
            return
        }
        if (installJob?.isActive == true) return

        installJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    installState = InstallUiState.Downloading(0, version.sizeBytes),
                    activeVersion = version,
                    message = null,
                )
            }
            try {
                val serverUrl = settings.serverUrl.first()
                val downloadUrl = UrlMapper.resolve(serverUrl, version.downloadUrl)
                    ?: UrlMapper.download(serverUrl, detail.slug, version.version)
                val file = downloader.download(
                    url = downloadUrl,
                    expectedSha256 = version.sha256,
                    fileName = "${detail.slug}-${version.version}.apk",
                ) { bytesRead, total ->
                    _state.update {
                        it.copy(installState = InstallUiState.Downloading(bytesRead, total))
                    }
                }
                _state.update { it.copy(installState = InstallUiState.Verifying) }
                pendingApk = file
                if (!installer.canInstallPackages()) {
                    _state.update {
                        it.copy(
                            installState = InstallUiState.Ready,
                            needsUnknownSources = true,
                            message = "Autorisez l’installation depuis AoE Store pour continuer.",
                        )
                    }
                    return@launch
                }
                installer.install(file)
                pendingApk = null
                _state.update {
                    it.copy(
                        installState = InstallUiState.Ready,
                        needsUnknownSources = false,
                        message = "Assistant d’installation ouvert.",
                    )
                }
            } catch (error: ChecksumMismatchException) {
                _state.update {
                    it.copy(
                        installState = InstallUiState.Failed(error.message ?: "Somme SHA-256 invalide"),
                        message = "Le fichier téléchargé est corrompu, réessayez.",
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        installState = InstallUiState.Failed(friendlyMessage(error)),
                        message = friendlyMessage(error),
                    )
                }
            }
        }
    }

    fun onResume() {
        val detail = _state.value.detail ?: return
        val installed = installer.installedVersionCode(detail.packageName)
        val canInstall = installer.canInstallPackages()
        val active = _state.value.activeVersion

        if (installed != null && active != null && installed >= active.versionCode) {
            pendingApk = null
            _state.update {
                it.copy(
                    installedVersionCode = installed,
                    installState = InstallUiState.Idle,
                    activeVersion = null,
                    needsUnknownSources = false,
                    message = "Application installée.",
                )
            }
            return
        }

        _state.update {
            it.copy(
                installedVersionCode = installed,
                needsUnknownSources = !canInstall && pendingApk != null,
            )
        }

        val file = pendingApk
        if (file != null && canInstall) {
            pendingApk = null
            runCatching { installer.install(file) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            installState = InstallUiState.Ready,
                            needsUnknownSources = false,
                            message = "Assistant d’installation ouvert.",
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            installState = InstallUiState.Failed(error.message ?: "Installation impossible"),
                            message = error.message ?: "Installation impossible",
                        )
                    }
                }
        }
    }

    fun openUnknownSourcesSettings() {
        runCatching { installer.openUnknownSourcesSettings() }
            .onFailure { error ->
                _state.update { it.copy(message = error.message ?: "Réglages inaccessibles") }
            }
    }

    fun openApp() {
        val detail = _state.value.detail ?: return
        if (!installer.openInstalled(detail.packageName)) {
            _state.update { it.copy(message = "L’application n’est pas encore installée.") }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun isUpToDate(installed: Int?, detail: AppDetail): Boolean {
        val latest = VersionResolver.latest(detail) ?: return false
        return installed != null && installed >= latest.versionCode
    }

    private fun friendlyMessage(error: Exception): String = when (error) {
        is ApiException -> error.message ?: "Erreur du serveur"
        is IOException -> "Connexion au serveur impossible. Vérifiez le réseau et l’URL du serveur."
        else -> error.message ?: "Erreur inconnue"
    }
}
