package com.loaloaloa.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loaloaloa.data.model.TransactionRecord
import com.loaloaloa.permission.BatteryOptimizationHelper
import com.loaloaloa.permission.NotificationAccessHelper
import com.loaloaloa.ui.components.BankBadge
import com.loaloaloa.ui.components.MoneyText
import com.loaloaloa.ui.theme.LocalAppExtraColors
import com.loaloaloa.ui.theme.MoneyHeroStyle
import com.loaloaloa.ui.util.DateLabels
import com.loaloaloa.ui.util.MoneyFormat
import com.loaloaloa.ui.util.OnResume
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onRequestNotificationAccess: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onOpenShift: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var detail by remember { mutableStateOf<TransactionRecord?>(null) }

    // Re-check granted permissions whenever Home resumes (e.g. after the user
    // returns from the system notification-access / battery settings screen).
    OnResume {
        viewModel.updatePermissions(
            notificationAccessGranted = NotificationAccessHelper.isGranted(context),
            isIgnoringBatteryOptimizations = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context),
        )
    }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            item {
                ServiceStatusCard(
                    enabled = state.serviceEnabled,
                    onToggle = { wantOn ->
                        if (wantOn && !state.notificationAccessGranted) {
                            onRequestNotificationAccess()
                        } else {
                            viewModel.setServiceEnabled(wantOn)
                        }
                    },
                )
            }

            if (!state.notificationAccessGranted) {
                item {
                    WarningChip(
                        text = "Cần cấp quyền đọc thông báo",
                        actionLabel = "Cấp quyền",
                        onAction = onRequestNotificationAccess,
                    )
                }
            }
            if (state.batteryWarning) {
                item {
                    WarningChip(
                        text = "Nên tắt tối ưu hoá pin",
                        actionLabel = "Cài đặt",
                        onAction = onRequestBatteryExemption,
                    )
                }
            }

            item { TodayIncomeCard(income = state.todayIncome, count = state.todayCount) }

            item { ShiftEntryCard(active = state.shiftActive, onClick = onOpenShift) }

            item {
                Text(
                    "Giao dịch gần đây",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (state.recent.isEmpty()) {
                item {
                    Text(
                        "Chưa có giao dịch nào",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.recent, key = { it.id }) { record ->
                    RecentRow(record, onClick = { detail = record })
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    detail?.let { rec ->
        RecentDetailDialog(
            record = rec,
            onSaveNote = { note -> viewModel.updateNote(rec.id, note) },
            onDismiss = { detail = null },
        )
    }
}

@Composable
private fun ServiceStatusCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (enabled) "Đang hoạt động" else "Đã tắt",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (enabled) "Đang lắng nghe thông báo từ ngân hàng" else "Bật để bắt đầu đọc thông báo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun WarningChip(text: String, actionLabel: String, onAction: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.18f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(text, style = MaterialTheme.typography.labelLarge)
            }
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun ShiftEntryCard(active: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (active) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            },
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(
                        if (active) "Đang trong ca" else "Chốt ca bàn giao",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (active) "Bấm để xem tổng & chốt ca" else "Theo dõi tiền trong ca để bàn giao",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TodayIncomeCard(income: Long, count: Int) {
    val extra = LocalAppExtraColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Đã nhận hôm nay",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                MoneyFormat.format(income, isIncome = true),
                style = MoneyHeroStyle,
                color = extra.income,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "$count giao dịch",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecentRow(record: TransactionRecord, onClick: () -> Unit) {
    val tx = record.transaction
    val time = DateLabels.timeLabel(tx.timestamp, ZoneId.systemDefault())
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    BankBadge(tx.bankName)
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        tx.bankName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "$time • ${tx.rawText}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (record.note.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.EditNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.size(4.dp))
                            Text(
                                record.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
            MoneyText(
                amount = tx.amount,
                isIncome = tx.isIncome,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

/** Detail + note editor for a recent transaction. Mirrors the History screen's dialog. */
@Composable
private fun RecentDetailDialog(
    record: TransactionRecord,
    onSaveNote: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val tx = record.transaction
    var note by remember(record.id) { mutableStateOf(record.note) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tx.bankName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MoneyText(tx.amount, tx.isIncome, style = MaterialTheme.typography.headlineSmall)
                Text(tx.rawText, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Ghi chú") },
                    placeholder = { Text("Thêm chú thích cho giao dịch...") },
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSaveNote(note.trim())
                onDismiss()
            }) { Text("Lưu") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Đóng") } },
    )
}
