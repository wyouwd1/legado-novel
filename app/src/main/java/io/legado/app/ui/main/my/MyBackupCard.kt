package io.legado.app.ui.main.my

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.config.backupConfig.BackupOptionSheet
import io.legado.app.ui.config.backupConfig.RestoreOptionSheet
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.series.SmallTonalButton
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.progressIndicator.AppContainedLoadingIndicator
import io.legado.app.ui.widget.components.text.AppText
import java.text.DateFormat
import java.util.Date

@Composable
fun MyBackupCard(
    latestBackup: MyBackupUi?,
    isLoading: Boolean,
    isLoadError: Boolean,
    isActionRunning: Boolean,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
) {
    val lastBackupText = when {
        isLoading -> stringResource(R.string.home_loading_webdav_backup)
        latestBackup != null -> {
            val date = remember(latestBackup.lastModify) {
                DateFormat.getDateTimeInstance(
                    DateFormat.MEDIUM,
                    DateFormat.SHORT,
                ).format(Date(latestBackup.lastModify))
            }
            stringResource(R.string.home_latest_backup_value, date)
        }

        isLoadError -> stringResource(R.string.home_webdav_backup_load_error)
        else -> stringResource(R.string.home_no_webdav_backup)
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppIcon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = null,
                    tint = LegadoTheme.colorScheme.primary,
                    modifier = Modifier.offset(y = 1.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    AppText(
                        text = stringResource(R.string.home_webdav_backup),
                        style = LegadoTheme.typography.titleMediumEmphasized,
                    )
                    AppText(
                        text = lastBackupText,
                        style = LegadoTheme.typography.bodySmall,
                        color = if (isLoadError) {
                            LegadoTheme.colorScheme.error
                        } else {
                            LegadoTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                if (isLoading || isActionRunning) {
                    AppContainedLoadingIndicator(
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            if (isLoadError && !isLoading) {
                SmallTonalButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onRetry,
                    icon = Icons.Default.Refresh,
                    text = stringResource(R.string.retry),
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SmallTonalButton(
                        modifier = Modifier.weight(1f),
                        onClick = onBackup,
                        enabled = !isActionRunning,
                        icon = Icons.Default.CloudUpload,
                        text = stringResource(R.string.backup),
                    )
                    SmallTonalButton(
                        modifier = Modifier.weight(1f),
                        onClick = onRestore,
                        enabled = !isActionRunning,
                        icon = Icons.Default.CloudDownload,
                        text = stringResource(R.string.restore),
                    )
                    SmallTonalButton(
                        modifier = Modifier.weight(1f),
                        onClick = onOpenSettings,
                        enabled = !isActionRunning,
                        icon = Icons.Default.Settings,
                        text = stringResource(R.string.setting),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyBackupSheets(
    state: MyUiState,
    onIntent: (MyIntent) -> Unit,
) {
    val restoreDialog = state.activeBackupDialog as? MyBackupDialog.ConfirmRestore
    AppAlertDialog(
        show = restoreDialog != null,
        onDismissRequest = { onIntent(MyIntent.DismissBackupDialog) },
        title = stringResource(R.string.restore_confirmation),
        text = restoreDialog?.let {
            stringResource(R.string.home_webdav_restore_confirmation, it.backupName)
        },
        confirmText = stringResource(R.string.ok),
        onConfirm = { onIntent(MyIntent.ConfirmRestore) },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { onIntent(MyIntent.DismissBackupDialog) },
    )

    BackupOptionSheet(
        show = state.activeBackupSheet == MyBackupSheet.BackupOptions,
        onDismissRequest = { onIntent(MyIntent.DismissBackupSheet) },
        onBackupToLocal = {
            onIntent(MyIntent.BackupDestinationSelected(MyBackupDestination.Local))
        },
        onBackupToNetwork = {
            onIntent(MyIntent.BackupDestinationSelected(MyBackupDestination.WebDav))
        },
        onBackupToLocalAndNetwork = {
            onIntent(MyIntent.BackupDestinationSelected(MyBackupDestination.LocalAndWebDav))
        },
    )
    RestoreOptionSheet(
        show = state.activeBackupSheet == MyBackupSheet.RestoreOptions,
        onDismissRequest = { onIntent(MyIntent.DismissBackupSheet) },
        onRestoreFromLocal = { onIntent(MyIntent.RestoreFromLocal) },
        onRestoreFromNetwork = { onIntent(MyIntent.RestoreFromNetwork) },
    )
}
