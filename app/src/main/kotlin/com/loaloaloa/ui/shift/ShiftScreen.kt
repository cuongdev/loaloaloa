package com.loaloaloa.ui.shift

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loaloaloa.ui.theme.LocalAppExtraColors
import com.loaloaloa.ui.theme.MoneyHeroStyle
import com.loaloaloa.ui.util.MoneyFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val STARTED_FMT = DateTimeFormatter.ofPattern("dd/MM 'lúc' HH:mm")
private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_FMT = DateTimeFormatter.ofPattern("dd/MM")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftScreen(
    onBack: () -> Unit,
    viewModel: ShiftViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    var showStartSheet by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.shares.collect { text -> shareSummary(context, text) }
    }

    if (showStartSheet) {
        StartShiftSheet(
            zone = zone,
            onDismiss = { showStartSheet = false },
            onNow = { showStartSheet = false; viewModel.startShiftNow() },
            onTodayStart = { showStartSheet = false; viewModel.startShiftFromTodayStart() },
            onPickTime = { showStartSheet = false; showTimePicker = true },
        )
    }
    if (showTimePicker) {
        ShiftTimePickerDialog(
            zone = zone,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute -> showTimePicker = false; viewModel.startShiftAtToday(hour, minute) },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chốt ca", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            if (state.active) {
                ActiveShift(
                    state = state,
                    zone = zone,
                    onClose = viewModel::closeShift,
                    onCancel = viewModel::cancelShift,
                    onChangeStart = { showStartSheet = true },
                )
            } else {
                IdleShift(onStart = { showStartSheet = true })
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun IdleShift(onStart: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Chưa có ca nào đang mở", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Bấm “Bắt đầu ca” và chọn mốc tính: từ bây giờ, từ đầu hôm nay, hoặc một giờ cụ thể. " +
                    "App cộng dồn tiền vào/ra để bạn chốt số khi bàn giao.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
    Button(
        onClick = onStart,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null)
        Spacer(Modifier.height(0.dp))
        Text("  Bắt đầu ca", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ActiveShift(
    state: ShiftUiState,
    zone: ZoneId,
    onClose: () -> Unit,
    onCancel: () -> Unit,
    onChangeStart: () -> Unit,
) {
    val extra = LocalAppExtraColors.current
    var confirmClose by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }

    // Hero: money received so far this shift.
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val started = state.startedAt?.let { Instant.ofEpochMilli(it).atZone(zone).format(STARTED_FMT) }
            Text(
                "Đang trong ca" + (started?.let { " · bắt đầu $it" } ?: ""),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(MoneyFormat.format(state.incomeTotal, true), style = MoneyHeroStyle, color = extra.income)
            Spacer(Modifier.height(4.dp))
            Text(
                "${state.incomeCount} lượt tiền vào",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onChangeStart) { Text("Đổi giờ bắt đầu") }
        }
    }

    // Outgoing + net + total count.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard(Modifier.weight(1f), "Tiền ra", MoneyFormat.format(state.outgoingTotal, false), extra.outgoing)
        StatCard(
            Modifier.weight(1f),
            "Chênh lệch",
            MoneyFormat.format(state.net, state.net >= 0),
            if (state.net >= 0) extra.income else extra.outgoing,
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard(Modifier.weight(1f), "Tổng giao dịch", "${state.count}", MaterialTheme.colorScheme.onSurface)
    }

    Spacer(Modifier.height(4.dp))
    Button(
        onClick = { confirmClose = true },
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        Icon(Icons.Filled.IosShare, contentDescription = null)
        Text("  Chốt ca & chia sẻ", fontWeight = FontWeight.Bold)
    }
    OutlinedButton(
        onClick = { confirmCancel = true },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
        Text("Huỷ ca (không lưu)")
    }

    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            confirmButton = {
                TextButton(onClick = { confirmClose = false; onClose() }) { Text("Chốt ca") }
            },
            dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("Để sau") } },
            title = { Text("Chốt ca này?") },
            text = { Text("Tổng kết sẽ được chia sẻ để bàn giao, và ca hiện tại sẽ kết thúc.") },
        )
    }
    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            confirmButton = {
                TextButton(onClick = { confirmCancel = false; onCancel() }) { Text("Huỷ ca") }
            },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Quay lại") } },
            title = { Text("Huỷ ca?") },
            text = { Text("Ca sẽ kết thúc mà không chia sẻ tổng kết. Lịch sử giao dịch vẫn được giữ.") },
        )
    }
}

@Composable
private fun StatCard(modifier: Modifier, label: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = valueColor)
        }
    }
}

/** Lets the user pick what instant the shift's running total counts from. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartShiftSheet(
    zone: ZoneId,
    onDismiss: () -> Unit,
    onNow: () -> Unit,
    onTodayStart: () -> Unit,
    onPickTime: () -> Unit,
) {
    val now = remember { ZonedDateTime.now(zone) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                "Tính tiền ca từ lúc nào?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            StartOption(
                icon = Icons.Filled.PlayArrow,
                title = "Bây giờ",
                subtitle = "Bắt đầu đếm từ ${now.format(TIME_FMT)}",
                onClick = onNow,
            )
            StartOption(
                icon = Icons.Filled.WbSunny,
                title = "Từ đầu hôm nay",
                subtitle = "Gộp tất cả giao dịch từ 00:00 ${now.format(DATE_FMT)}",
                onClick = onTodayStart,
            )
            StartOption(
                icon = Icons.Filled.Schedule,
                title = "Chọn giờ khác…",
                subtitle = "Tự nhập giờ ca bắt đầu (vd 06:00)",
                onClick = onPickTime,
            )
        }
    }
}

@Composable
private fun StartOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Pick a clock time today as the shift start (for shops that open at a fixed hour). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShiftTimePickerDialog(
    zone: ZoneId,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit,
) {
    val now = remember { ZonedDateTime.now(zone) }
    val timeState = rememberTimePickerState(
        initialHour = now.hour,
        initialMinute = now.minute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(timeState.hour, timeState.minute) }) { Text("Xong") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Huỷ") } },
        title = { Text("Ca bắt đầu lúc") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = timeState)
            }
        },
    )
}

/** Open the system share sheet with the plain-text handover summary. */
private fun shareSummary(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, "Chốt ca Loa Loa Loa")
    }
    context.startActivity(
        Intent.createChooser(send, "Chia sẻ chốt ca").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
