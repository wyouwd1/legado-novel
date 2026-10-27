package io.legado.app.ui.widget.components.changeSource

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.ToggleChip

private val WORD_COUNT_PRESETS = listOf(0, 500, 1000, 2000, 3000, 5000)

/**
 * 换源结果的「最少字数」选择对话框。
 *
 * 入口在换源 / 换章 Sheet 的「⋮」菜单里，与「校验作者」「加载详情」等选项并列。
 * [minWordCount] 为 0 表示不限；预设值之外可自定义任意非负整数。
 * 选中预设即应用并关闭；自定义值需点「确定」应用。
 */
@Composable
fun ChangeSourceWordCountDialog(
    show: Boolean,
    minWordCount: Int,
    onDismissRequest: () -> Unit,
    onConfirm: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var customInput by rememberSaveable { mutableStateOf("") }
    var customMode by rememberSaveable { mutableStateOf(false) }

    // 每次打开时按当前值重置输入状态，避免上次残留。
    LaunchedEffect(show) {
        if (show) {
            customMode = minWordCount > 0 && minWordCount !in WORD_COUNT_PRESETS
            customInput = if (customMode) minWordCount.toString() else ""
        }
    }

    AppAlertDialog(
        show = show,
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.change_source_min_word_count),
        content = {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WORD_COUNT_PRESETS.forEach { value ->
                    ToggleChip(
                        label = if (value == 0) {
                            stringResource(R.string.change_source_word_count_unlimited)
                        } else {
                            stringResource(R.string.change_source_word_count_at_least, value)
                        },
                        selected = !customMode && minWordCount == value,
                        onToggle = { onConfirm(value) },
                    )
                }
                ToggleChip(
                    label = stringResource(R.string.custom),
                    selected = customMode,
                    onToggle = { customMode = true },
                )
            }
            if (customMode) {
                AppTextField(
                    value = customInput,
                    onValueChange = { input ->
                        customInput = input.filter(Char::isDigit).take(7)
                    },
                    label = stringResource(R.string.change_source_word_count),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
            }
        },
        confirmText = stringResource(android.R.string.ok),
        confirmEnabled = customMode && (customInput.toIntOrNull() ?: -1) >= 0,
        onConfirm = {
            customInput.toIntOrNull()?.let(onConfirm)
        },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = onDismissRequest,
    )
}
