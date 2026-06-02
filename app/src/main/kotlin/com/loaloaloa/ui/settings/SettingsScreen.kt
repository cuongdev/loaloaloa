package com.loaloaloa.ui.settings

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
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Webhook
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.ShortText
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loaloaloa.data.model.AudioOutput
import com.loaloaloa.data.model.QuietHours
import com.loaloaloa.data.model.RelayRole
import com.loaloaloa.data.model.SpeakOption
import com.loaloaloa.tts.SpeechTextBuilder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenExcludedApps: () -> Unit,
    onOpenBanks: () -> Unit,
    onOpenApiSource: () -> Unit,
    onOpenWebhook: () -> Unit,
    onOpenRelay: () -> Unit,
    onOpenTroubleshooting: () -> Unit,
    onOpenDebug: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val s by viewModel.uiState.collectAsStateWithLifecycle()

    var speakOptionDialog by remember { mutableStateOf(false) }
    var audioOutputDialog by remember { mutableStateOf(false) }
    var quietStartDialog by remember { mutableStateOf(false) }
    var quietEndDialog by remember { mutableStateOf(false) }
    var minAmountDialog by remember { mutableStateOf(false) }
    var lockedInfo by remember { mutableStateOf(false) }

    // On an employee (SPOKE) device the shop-config rows are locked: tapping one explains why
    // instead of navigating, so staff can't change the shop's wiring (relay, webhook, sources…).
    val locked = s.relayRole == RelayRole.SPOKE

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
                SwitchRow(Icons.Filled.NotificationsActive, "Âm báo Loa Loa", s.playChime, viewModel::setPlayChime)
                Divider()
                SwitchRow(Icons.Filled.Repeat, "Đọc lại 2 lần", s.repeat, viewModel::setRepeat)
                Divider()
                SwitchRow(Icons.AutoMirrored.Filled.ShortText, "Tin nhắn rút gọn", s.speakShortMessage, viewModel::setSpeakShortMessage)
                Divider()
                SwitchRow(Icons.Filled.Summarize, "Đọc tổng tiền trong ngày", s.speakDailyTotal, viewModel::setSpeakDailyTotal)
                Divider()
                SwitchRow(Icons.AutoMirrored.Filled.Notes, "Đọc nội dung giao dịch", s.speakContent, viewModel::setSpeakContent)
                Divider()
                NavRow(Icons.Filled.FilterAlt, "Chỉ đọc giao dịch từ", value = minAmountLabel(s.minAnnounceAmount)) { minAmountDialog = true }
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
                NavRow(Icons.Filled.AppBlocking, "Ứng dụng loại trừ", locked = locked, onClick = onOpenExcludedApps, onLocked = { lockedInfo = true })
                Divider()
                NavRow(Icons.Filled.AccountBalance, "Ngân hàng hỗ trợ", locked = locked, onClick = onOpenBanks, onLocked = { lockedInfo = true })
                Divider()
                NavRow(Icons.Filled.Api, "Nguồn API (SePay)", locked = locked, onClick = onOpenApiSource, onLocked = { lockedInfo = true })
                Divider()
                NavRow(Icons.Filled.Webhook, "Webhook", locked = locked, onClick = onOpenWebhook, onLocked = { lockedInfo = true })
                Divider()
                NavRow(Icons.Filled.Campaign, "Chia sẻ thông báo (loa nhân viên)", locked = locked, onClick = onOpenRelay, onLocked = { lockedInfo = true })
                Divider()
                NavRow(Icons.Filled.BugReport, "Gửi thông báo thử", locked = locked, onClick = onOpenDebug, onLocked = { lockedInfo = true })
                Divider()
                NavRow(Icons.Filled.Build, "Khắc phục sự cố", locked = locked, onClick = onOpenTroubleshooting, onLocked = { lockedInfo = true })
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
    if (minAmountDialog) {
        AmountDialog(
            initial = s.minAnnounceAmount,
            onConfirm = { viewModel.setMinAnnounceAmount(it); minAmountDialog = false },
            onDismiss = { minAmountDialog = false },
        )
    }
    if (lockedInfo) {
        AlertDialog(
            onDismissRequest = { lockedInfo = false },
            icon = { Icon(Icons.Filled.Lock, contentDescription = null) },
            title = { Text("Đã khoá") },
            text = { Text("Mục này chỉ chủ cửa hàng (máy chính) chỉnh được. Máy nhân viên chỉ chỉnh giọng nói, âm lượng và giờ yên tĩnh.") },
            confirmButton = { TextButton(onClick = { lockedInfo = false }) { Text("Đã hiểu") } },
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
private fun NavRow(
    icon: ImageVector,
    title: String,
    value: String? = null,
    locked: Boolean = false,
    onLocked: () -> Unit = {},
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = if (locked) onLocked else onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (locked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.size(12.dp))
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (locked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (value != null) {
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(4.dp))
            }
            Icon(
                if (locked) Icons.Filled.Lock else Icons.Filled.ChevronRight,
                contentDescription = if (locked) "Đã khoá" else null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

/**
 * Numeric entry for the "Chỉ đọc giao dịch từ" announce floor. Empty/0 means "announce
 * every amount". The field shows raw digits; the label elsewhere formats with separators.
 */
@Composable
private fun AmountDialog(
    initial: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(if (initial > 0) initial.toString() else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chỉ đọc giao dịch từ") },
        text = {
            Column {
                Text(
                    "Giao dịch nhỏ hơn số này sẽ không được đọc (vẫn lưu lịch sử). Để trống để đọc tất cả.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { input -> text = input.filter { it.isDigit() }.take(12) },
                    singleLine = true,
                    suffix = { Text("đ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text.toLongOrNull() ?: 0L) }) { Text("Lưu") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } },
    )
}

/** Label for the announce floor: 0 → "Tất cả", else the grouped amount with a 'đ' suffix. */
private fun minAmountLabel(amount: Long): String =
    if (amount <= 0) "Tất cả" else "${SpeechTextBuilder.formatAmount(amount)}đ"

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

