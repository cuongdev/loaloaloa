package com.loaloaloa.ui.staff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loaloaloa.data.model.AudioOutput
import com.loaloaloa.data.model.SpeakOption
import com.loaloaloa.ui.settings.SettingsViewModel

/**
 * The few loa controls a staff device actually needs: speak direction, force-max-volume, audio
 * output, and read-content. Backed by the existing [SettingsViewModel] so behaviour matches the
 * shop's full settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffTtsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val s by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cài đặt loa", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SwitchRow("Phát âm lượng tối đa", s.forceMaxVolume, viewModel::setForceMaxVolume)
            SwitchRow("Đọc nội dung giao dịch", s.speakContent, viewModel::setSpeakContent)
            Text(
                "Hướng giao dịch đọc",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = s.speakOption == SpeakOption.INCOME_ONLY,
                    onClick = { viewModel.setSpeakOption(SpeakOption.INCOME_ONLY) },
                    label = { Text("Tiền vào") },
                )
                FilterChip(
                    selected = s.speakOption == SpeakOption.OUTGOING_ONLY,
                    onClick = { viewModel.setSpeakOption(SpeakOption.OUTGOING_ONLY) },
                    label = { Text("Tiền ra") },
                )
                FilterChip(
                    selected = s.speakOption == SpeakOption.BOTH,
                    onClick = { viewModel.setSpeakOption(SpeakOption.BOTH) },
                    label = { Text("Cả hai") },
                )
            }
            Text(
                "Kênh âm thanh",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = s.audioOutput == AudioOutput.NOTIFICATION,
                    onClick = { viewModel.setAudioOutput(AudioOutput.NOTIFICATION) },
                    label = { Text("Thông báo") },
                )
                FilterChip(
                    selected = s.audioOutput == AudioOutput.ALARM,
                    onClick = { viewModel.setAudioOutput(AudioOutput.ALARM) },
                    label = { Text("Báo thức") },
                )
                FilterChip(
                    selected = s.audioOutput == AudioOutput.MEDIA,
                    onClick = { viewModel.setAudioOutput(AudioOutput.MEDIA) },
                    label = { Text("Media") },
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
