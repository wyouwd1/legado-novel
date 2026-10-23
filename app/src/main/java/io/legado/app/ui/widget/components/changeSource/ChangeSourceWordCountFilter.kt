package io.legado.app.ui.widget.components.changeSource

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.ToggleChip
import io.legado.app.ui.widget.components.text.AppText

private val WORD_COUNT_PRESETS = listOf(0, 500, 1000, 2000, 3000, 5000)

/**
 * 换源结果的最少字数筛选。
 *
 * [minWordCount] 为 0 表示不限；预设值之外通过「自定义」输入任意正整数。
 */
@Composable
fun ChangeSourceWordCountFilter(
    minWordCount: Int,
    onMinWordCountChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCustomDialog by rememberSaveable { mutableStateOf(false) }
    var customInput by rememberSaveable { mutableStateOf("") }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(
            text = stringResource(R.string.change_source_word_count),
            style = LegadoTheme.typography.labelMediumEmphasized,
            modifier = Modifier.padding(end = 8.dp),
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WORD_COUNT_PRESETS.forEach { value ->
                ToggleChip(
                    label = if (value == 0) {
                        stringResource(R.string.change_source_word_count_unlimited)
                    } else {
                        stringResource(R.string.change_source_word_count_at_least, value)
                    },
                    selected = minWordCount == value,
                    onToggle = { onMinWordCountChange(value) },
                )
            }
            if (minWordCount > 0 && minWordCount !in WORD_COUNT_PRESETS) {
                ToggleChip(
                    label = stringResource(R.string.change_source_word_count_at_least, minWordCount),
                    selected = true,
                    onToggle = {
                        customInput = minWordCount.toString()
                        showCustomDialog = true
                    },
                )
            }
            ToggleChip(
                label = stringResource(R.string.custom),
                selected = false,
                onToggle = {
                    customInput = minWordCount.toString()
                    showCustomDialog = true
                },
            )
        }
    }

    if (showCustomDialog) {
        AppAlertDialog(
            show = true,
            onDismissRequest = { showCustomDialog = false },
            title = stringResource(R.string.change_source_min_word_count),
            content = {
                AppTextField(
                    value = customInput,
                    onValueChange = { input ->
                        customInput = input.filter(Char::isDigit).take(7)
                    },
                    label = stringResource(R.string.change_source_word_count),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmText = stringResource(android.R.string.ok),
            confirmEnabled = (customInput.toIntOrNull() ?: -1) >= 0,
            onConfirm = {
                customInput.toIntOrNull()?.let(onMinWordCountChange)
                showCustomDialog = false
            },
            dismissText = stringResource(android.R.string.cancel),
            onDismiss = { showCustomDialog = false },
        )
    }
}
