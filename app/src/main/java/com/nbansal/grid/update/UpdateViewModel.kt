package com.nbansal.grid.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val version: String, val notes: String) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data object ReadyToInstall : UpdateState
    data class Error(val message: String) : UpdateState
}

class UpdateViewModel(app: Application) : AndroidViewModel(app) {
    private val checker = UpdateChecker()
    private val installer = UpdateInstaller(app)

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** One-shot messages for the Snackbar. */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    private var release: ReleaseInfo? = null
    private var apk: File? = null
    private var job: Job? = null

    init {
        viewModelScope.launch { InstallEvents.failures.collect { _messages.tryEmit(it) } }
    }

    /** [silent] = auto-check on launch: only surfaces something if an update exists. */
    fun check(silent: Boolean) {
        if (_state.value is UpdateState.Checking || _state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Checking
        job = viewModelScope.launch {
            try {
                val r = checker.check()
                release = r
                if (r != null) {
                    _state.value = UpdateState.Available(r.version, r.notes)
                } else {
                    _state.value = UpdateState.UpToDate
                    if (!silent) _messages.tryEmit("You're up to date")
                    _state.value = UpdateState.Idle
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = UpdateState.Error(e.message ?: "Update check failed")
                if (!silent) _messages.tryEmit("Couldn't check for updates: ${e.message ?: "unknown error"}")
                _state.value = UpdateState.Idle
            }
        }
    }

    fun downloadAndInstall() {
        val r = release ?: return
        if (_state.value is UpdateState.Downloading) return
        if (!installer.canInstall()) {
            installer.openInstallPermissionSettings()
            _messages.tryEmit("Allow Grid. to install apps, then come back and tap Download & install")
            return
        }
        job = viewModelScope.launch {
            try {
                _state.value = UpdateState.Downloading(0f)
                installer.download(r.apkUrl, r.apkSize).collect { ev ->
                    when (ev) {
                        is DownloadEvent.Progress -> _state.value = UpdateState.Downloading(ev.fraction)
                        is DownloadEvent.Done -> apk = ev.file
                    }
                }
                _state.value = UpdateState.ReadyToInstall
                install()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit("Download failed: ${e.message ?: "unknown error"}")
                _state.value = UpdateState.Available(r.version, r.notes)
            }
        }
    }

    fun install() {
        val f = apk ?: return
        if (!installer.canInstall()) {
            installer.openInstallPermissionSettings()
            _messages.tryEmit("Allow Grid. to install apps, then come back and tap Install")
            return
        }
        viewModelScope.launch {
            try {
                installer.install(f)
            } catch (e: Exception) {
                _messages.tryEmit("Install failed: ${e.message ?: "unknown error"}")
            }
        }
    }

    fun dismiss() {
        job?.cancel()
        _state.value = UpdateState.Idle
    }
}
