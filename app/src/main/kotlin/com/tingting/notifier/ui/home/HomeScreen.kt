package com.tingting.notifier.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tingting.notifier.data.model.TransactionRecord
import com.tingting.notifier.permission.BatteryOptimizationHelper
import com.tingting.notifier.permission.NotificationAccessHelper
import com.tingting.notifier.ui.components.BankBadge
import com.tingting.notifier.ui.components.MoneyText
import com.tingting.notifier.ui.theme.LocalAppExtraColors
import com.tingting.notifier.ui.theme.MoneyHeroStyle
import com.tingting.notifier.ui.util.MoneyFormat
import com.tingting.notifier.ui.util.OnResume

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onRequestNotificationAccess: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

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
                items(state.recent, key = { it.id }) { RecentRow(it) }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
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
private fun RecentRow(record: TransactionRecord) {
    val tx = record.transaction
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    BankBadge(tx.bankName)
                }
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(
                        tx.bankName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            MoneyText(
                amount = tx.amount,
                isIncome = tx.isIncome,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
