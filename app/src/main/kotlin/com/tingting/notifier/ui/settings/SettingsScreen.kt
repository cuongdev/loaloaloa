package com.tingting.notifier.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.AppBlocking
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.automirrored.filled.ShortText
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tingting.notifier.data.model.AudioOutput
import com.tingting.notifier.data.model.QuietHours
import com.tingting.notifier.data.model.SpeakOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenExcludedApps: () -> Unit,
    onOpenBanks: () -> Unit,
    onOpenApiSource: () -> Unit,
    onOpenTroubleshooting: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val s by viewModel.uiState.collectAsStateWithLifecycle()

    var speakOptionDialog by remember { mutableStateOf(false) }
    var audioOutputDialog by remember { mutableStateOf(false) }
    var quietStartDialog by remember { mutableStateOf(false) }
    var quietEndDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cài đặt", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Group: Giọng nói & âm thanh
            GroupHeader("Giọng nói & âm thanh")
            SettingsCard {
                NavRow(Icons.Filled.RecordVoiceOver, "Đọc loại giao dịch", value = speakOptionLabel(s.speakOption)) { speakOptionDialog = true }
                Divider()
                SwitchRow(Icons.Filled.NotificationsActive, "Âm báo Ting Ting", s.playChime, viewModel::setPlayChime)
                Divider()
                SwitchRow(Icons.Filled.Repeat, "Đọc lại 2 lần", s.repeat, viewModel::setRepeat)
                Divider()
                SwitchRow(Icons.AutoMirrored.Filled.ShortText, "Tin nhắn rút gọn", s.speakShortMessage, viewModel::setSpeakShortMessage)
            }

            // Group: Âm lượng & phát
            GroupHeader("Âm lượng & phát")
            SettingsCard {
                SwitchRow(Icons.AutoMirrored.Filled.VolumeUp, "Mở âm lượng tối đa", s.forceMaxVolume, viewModel::setForceMaxVolume)
                Divider()
                SwitchRow(Icons.AutoMirrored.Filled.VolumeOff, "Đọc cả khi im lặng", s.speakInSilentMode, viewModel::setSpeakInSilentMode)
                Divider()
                NavRow(Icons.Filled.AudioFile, "Luồng âm thanh", value = audioOutputLabel(s.audioOutput)) { audioOutputDialog = true }
            }

            // Group: Khác
            GroupHeader("Khác")
            SettingsCard {
                // Quiet hours: switch + start/end pickers
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.DoNotDisturbOn, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.size(12.dp))
                            Text("Giờ yên tĩnh", style = MaterialTheme.typography.bodyLarge)
                        }
                        Switch(
                            checked = s.quietHours.enabled,
                            onCheckedChange = { viewModel.setQuietHours(s.quietHours.copy(enabled = it)) },
                        )
                    }
                    if (s.quietHours.enabled) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 36.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            TextButton(onClick = { quietStartDialog = true }) { Text("Từ ${minutesLabel(s.quietHours.startMinutes)}") }
                            TextButton(onClick = { quietEndDialog = true }) { Text("Đến ${minutesLabel(s.quietHours.endMinutes)}") }
                        }
                    }
                }
                Divider()
                NavRow(Icons.Filled.AppBlocking, "Ứng dụng loại trừ", onClick = onOpenExcludedApps)
                Divider()
                NavRow(Icons.Filled.AccountBalance, "Ngân hàng hỗ trợ", onClick = onOpenBanks)
                Divider()
                NavRow(Icons.Filled.Api, "Nguồn API (SePay)", onClick = onOpenApiSource)
                Divider()
                NavRow(Icons.Filled.Build, "Khắc phục sự cố", onClick = onOpenTroubleshooting)
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (speakOptionDialog) {
        OptionDialog(
            title = "Đọc loại giao dịch",
            options = SpeakOption.entries.map { it to speakOptionLabel(it) },
            selected = s.speakOption,
            onSelect = { viewModel.setSpeakOption(it); speakOptionDialog = false },
            onDismiss = { speakOptionDialog = false },
        )
    }
    if (audioOutputDialog) {
        OptionDialog(
            title = "Luồng âm thanh",
            options = AudioOutput.entries.map { it to audioOutputLabel(it) },
            selected = s.audioOutput,
            onSelect = { viewModel.setAudioOutput(it); audioOutputDialog = false },
            onDismiss = { audioOutputDialog = false },
        )
    }
    if (quietStartDialog) {
        TimePickerDialog(
            initialMinutes = s.quietHours.startMinutes,
            onConfirm = { viewModel.setQuietHours(s.quietHours.copy(startMinutes = it)); quietStartDialog = false },
            onDismiss = { quietStartDialog = false },
        )
    }
    if (quietEndDialog) {
        TimePickerDialog(
            initialMinutes = s.quietHours.endMinutes,
            onConfirm = { viewModel.setQuietHours(s.quietHours.copy(endMinutes = it)); quietEndDialog = false },
            onDismiss = { quietEndDialog = false },
        )
    }
}

@Composable
private fun GroupHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column { content() }
    }
}

@Composable
private fun Divider() {
    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.size(12.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun NavRow(icon: ImageVector, title: String, value: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.size(12.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (value != null) {
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(4.dp))
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun <T> OptionDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = value == selected, onClick = { onSelect(value) })
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selected, onClick = { onSelect(value) })
                        Spacer(Modifier.size(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Đóng") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Huỷ") } },
        text = { TimePicker(state = state) },
    )
}

private fun speakOptionLabel(option: SpeakOption): String = when (option) {
    SpeakOption.INCOME_ONLY -> "Tiền vào"
    SpeakOption.OUTGOING_ONLY -> "Tiền ra"
    SpeakOption.BOTH -> "Cả hai"
}

private fun audioOutputLabel(output: AudioOutput): String = when (output) {
    AudioOutput.NOTIFICATION -> "Thông báo"
    AudioOutput.ALARM -> "Báo thức"
    AudioOutput.MEDIA -> "Nhạc"
}

private fun minutesLabel(minutes: Int): String =
    "%02d:%02d".format((minutes / 60) % 24, minutes % 60)
