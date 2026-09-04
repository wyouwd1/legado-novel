package io.legado.app.ui.main.my

import android.app.Application
import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.EventBus
import io.legado.app.domain.gateway.BackupSettingsGateway
import io.legado.app.domain.model.WebDavBackup
import io.legado.app.domain.usecase.BackupRestoreUseCase
import io.legado.app.domain.usecase.WebDavBackupUseCase
import io.legado.app.service.WebService
import io.legado.app.utils.eventBus.FlowEventBus
import io.legado.app.utils.isContentScheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

@Stable
data class MyUiState(
    val isWebServiceRun: Boolean = false,
    val webServiceAddress: String = "",
    val latestBackup: MyBackupUi? = null,
    val isBackupLoading: Boolean = true,
    val isBackupLoadError: Boolean = false,
    val isBackupActionRunning: Boolean = false,
    val activeBackupDialog: MyBackupDialog? = null,
    val activeBackupSheet: MyBackupSheet? = null,
)

@Stable
data class MyBackupUi(
    val name: String,
    val lastModify: Long,
)

@Stable
sealed interface MyBackupDialog {
    data class ConfirmRestore(val backupName: String) : MyBackupDialog
}

@Stable
sealed interface MyBackupSheet {
    data object BackupOptions : MyBackupSheet
    data object RestoreOptions : MyBackupSheet
}

enum class MyBackupDestination(val mode: String) {
    Local("local"),
    WebDav("webdav"),
    LocalAndWebDav("both"),
}

sealed class PrefClickEvent {
    data class OpenUrl(val url: String) : PrefClickEvent()
    data class CopyUrl(val url: String) : PrefClickEvent()
    data class ShowMd(val title: String, val path: String) : PrefClickEvent()
    data class StartActivity(val destination: Class<*>, val configTag: String? = null) : PrefClickEvent()
    object OpenReadRecord : PrefClickEvent()
    object OpenBookCacheManage : PrefClickEvent()
    object OpenBookSourceManage : PrefClickEvent()
    object OpenHighlightTagRule : PrefClickEvent()
    object OpenAbout : PrefClickEvent()
    object OpenBackupSettings : PrefClickEvent()
    object OpenRssSourceManage : PrefClickEvent()
    object OpenRssHome : PrefClickEvent()
    object OpenRssFavorites : PrefClickEvent()
    object OpenRuleSub : PrefClickEvent()
    object ToggleWebService : PrefClickEvent()
    object ExitApp : PrefClickEvent()
}

sealed interface MyIntent {
    data object ToggleWebService : MyIntent
    data object BackupClick : MyIntent
    data class BackupDestinationSelected(val destination: MyBackupDestination) : MyIntent
    data class BackupDirectorySelected(
        val destination: MyBackupDestination,
        val path: String,
    ) : MyIntent

    data object RestoreClick : MyIntent
    data object RestoreFromLocal : MyIntent
    data object RestoreFromNetwork : MyIntent
    data class RestoreLocalFileSelected(val uri: String) : MyIntent
    data object ConfirmRestore : MyIntent
    data object RetryBackupInfo : MyIntent
    data object DismissBackupDialog : MyIntent
    data object DismissBackupSheet : MyIntent
}

sealed interface MyEffect {
    data class SelectBackupDirectory(
        val destination: MyBackupDestination,
    ) : MyEffect

    data class RequestBackupStoragePermission(
        val destination: MyBackupDestination,
        val path: String,
    ) : MyEffect

    data object SelectRestoreFile : MyEffect
    data class ShowMessage(
        @param:StringRes val messageRes: Int,
        val detail: String? = null,
    ) : MyEffect
}

class MyViewModel(
    application: Application,
    private val webDavBackupUseCase: WebDavBackupUseCase,
    private val backupRestoreUseCase: BackupRestoreUseCase,
    private val backupSettingsGateway: BackupSettingsGateway,
) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(
        MyUiState(
            isWebServiceRun = WebService.isRun,
            webServiceAddress = WebService.hostAddress
        )
    )
    val uiState: StateFlow<MyUiState> = _uiState.asStateFlow()
    private val _effects = MutableSharedFlow<MyEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private var backupRefreshJob: Job? = null
    private val backupActionMutex = Mutex()

    init {
        viewModelScope.launch {
            FlowEventBus.with<String>(EventBus.WEB_SERVICE)
                .collect { address ->
                    _uiState.update { state ->
                        state.copy(
                            isWebServiceRun = address.isNotEmpty(),
                            webServiceAddress = address
                        )
                    }
                }
        }
        refreshLatestBackup()
    }

    fun onIntent(intent: MyIntent) {
        when (intent) {
            MyIntent.ToggleWebService -> {
                val currentIsRun = _uiState.value.isWebServiceRun

                if (!currentIsRun) {
                    WebService.start(context)
                } else {
                    WebService.stop(context)
                    _uiState.update { it.copy(isWebServiceRun = false, webServiceAddress = "") }
                }

            }

            MyIntent.BackupClick -> _uiState.update {
                it.copy(activeBackupSheet = MyBackupSheet.BackupOptions)
            }

            is MyIntent.BackupDestinationSelected -> requestBackup(intent.destination)
            is MyIntent.BackupDirectorySelected -> backup(
                destination = intent.destination,
                path = intent.path,
                savePath = true,
            )

            MyIntent.RestoreClick -> _uiState.update {
                it.copy(activeBackupSheet = MyBackupSheet.RestoreOptions)
            }

            MyIntent.RestoreFromLocal -> {
                _uiState.update { it.copy(activeBackupSheet = null) }
                _effects.tryEmit(MyEffect.SelectRestoreFile)
            }

            MyIntent.RestoreFromNetwork -> {
                _uiState.update { it.copy(activeBackupSheet = null) }
                requestRestore()
            }

            is MyIntent.RestoreLocalFileSelected -> restoreLocal(intent.uri)
            MyIntent.ConfirmRestore -> restore()
            MyIntent.RetryBackupInfo -> refreshLatestBackup()
            MyIntent.DismissBackupDialog -> _uiState.update {
                it.copy(activeBackupDialog = null)
            }

            MyIntent.DismissBackupSheet -> _uiState.update {
                it.copy(activeBackupSheet = null)
            }
        }
    }

    private fun requestRestore() {
        val backup = _uiState.value.latestBackup
        if (backup == null) {
            _effects.tryEmit(
                MyEffect.ShowMessage(
                    if (_uiState.value.isBackupLoadError) {
                        R.string.home_webdav_backup_load_error
                    } else {
                        R.string.home_no_webdav_backup
                    }
                )
            )
            return
        }
        _uiState.update {
            it.copy(activeBackupDialog = MyBackupDialog.ConfirmRestore(backup.name))
        }
    }

    private fun requestBackup(destination: MyBackupDestination) {
        _uiState.update { it.copy(activeBackupSheet = null) }
        if (destination == MyBackupDestination.WebDav) {
            backup(destination = destination, path = null)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val path = backupSettingsGateway.currentSettings.backupPath
            if (path.isNullOrBlank()) {
                _effects.emit(MyEffect.SelectBackupDirectory(destination))
            } else if (!path.isContentScheme()) {
                _effects.emit(
                    MyEffect.RequestBackupStoragePermission(
                        destination = destination,
                        path = path,
                    )
                )
            } else {
                backup(destination = destination, path = path)
            }
        }
    }

    private fun backup(
        destination: MyBackupDestination,
        path: String?,
        savePath: Boolean = false,
    ) {
        if (!backupActionMutex.tryLock()) return
        _uiState.update { it.copy(isBackupActionRunning = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                runCatching {
                    if (savePath) {
                        backupSettingsGateway.update { it.copy(backupPath = path) }
                    }
                    if (destination != MyBackupDestination.Local) {
                        webDavBackupUseCase.refreshConfig()
                    }
                    backupRestoreUseCase.backup(path, destination.mode)
                }.onSuccess {
                    _effects.emit(MyEffect.ShowMessage(R.string.backup_success))
                    if (destination != MyBackupDestination.Local) {
                        refreshLatestBackup()
                    }
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    _effects.emit(
                        MyEffect.ShowMessage(
                            messageRes = R.string.backup_error,
                            detail = error.localizedMessage,
                        )
                    )
                }
            } finally {
                _uiState.update { it.copy(isBackupActionRunning = false) }
                backupActionMutex.unlock()
            }
        }
    }

    private fun restoreLocal(uri: String) {
        if (!backupActionMutex.tryLock()) return
        _uiState.update { it.copy(isBackupActionRunning = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                runCatching {
                    backupRestoreUseCase.restoreLocal(uri)
                }.onSuccess {
                    _effects.emit(MyEffect.ShowMessage(R.string.restore_success))
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    _effects.emit(
                        MyEffect.ShowMessage(
                            messageRes = R.string.restore_error,
                            detail = error.localizedMessage,
                        )
                    )
                }
            } finally {
                _uiState.update { it.copy(isBackupActionRunning = false) }
                backupActionMutex.unlock()
            }
        }
    }

    private fun restore() {
        val backup = _uiState.value.latestBackup ?: return
        _uiState.update { it.copy(activeBackupDialog = null) }
        if (!backupActionMutex.tryLock()) return
        _uiState.update { it.copy(isBackupActionRunning = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                runCatching {
                    webDavBackupUseCase.restore(backup.name)
                }.onSuccess {
                    _effects.emit(MyEffect.ShowMessage(R.string.restore_success))
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    _effects.emit(
                        MyEffect.ShowMessage(
                            messageRes = R.string.restore_error,
                            detail = error.localizedMessage,
                        )
                    )
                }
            } finally {
                _uiState.update { it.copy(isBackupActionRunning = false) }
                backupActionMutex.unlock()
            }
        }
    }

    private fun refreshLatestBackup() {
        backupRefreshJob?.cancel()
        backupRefreshJob = viewModelScope.launch(Dispatchers.IO) {
            loadLatestBackup()
        }
    }

    private suspend fun loadLatestBackup() {
        _uiState.update {
            it.copy(
                isBackupLoading = true,
                isBackupLoadError = false,
            )
        }
        try {
            val latest = webDavBackupUseCase.getLatestBackup()
            _uiState.update {
                it.copy(
                    latestBackup = latest?.toUi(),
                    isBackupLoading = false,
                    isBackupLoadError = false,
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            _uiState.update {
                it.copy(
                    isBackupLoading = false,
                    isBackupLoadError = true,
                )
            }
        }
    }

    private fun WebDavBackup.toUi() = MyBackupUi(
        name = name,
        lastModify = lastModify,
    )
}
