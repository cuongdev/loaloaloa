package com.tingting.notifier.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tingting.notifier.data.model.TransactionRecord
import com.tingting.notifier.ui.components.BankBadge
import com.tingting.notifier.ui.components.MoneyText
import com.tingting.notifier.ui.theme.LocalAppExtraColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<TransactionRecord?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var datePickerOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lịch sử", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { confirmDeleteAll = true }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Xoá tất cả")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    placeholder = { Text("Tìm theo số tiền, nội dung, số TK...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "Xoá tìm kiếm")
                            }
                        }
                    },
                    singleLine = true,
                )
            }
            item {
                FilterRow(
                    state = state,
                    onDirection = viewModel::setDirection,
                    onBank = viewModel::setBankFilter,
                    onOpenDatePicker = { datePickerOpen = true },
                )
            }
            item { SummaryCard(income = state.incomeTotal, outgoing = state.outgoingTotal) }

            if (state.isEmpty) {
                item {
                    Text(
                        "Chưa có giao dịch nào",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }

            state.sections.forEach { section ->
                item(key = "h-${section.header}") {
                    Text(
                        section.header,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                }
                items(section.rows, key = { it.id }) { row ->
                    TransactionRowItem(
                        row = row,
                        onClick = { detail = row.record },
                        onDelete = {
                            viewModel.delete(row.record)
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = "Đã xoá giao dịch",
                                    actionLabel = "Hoàn tác",
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    viewModel.undoDelete(row.record)
                                }
                            }
                        },
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    detail?.let { rec ->
        TransactionDetailDialog(record = rec, onDismiss = { detail = null })
    }

    if (datePickerOpen) {
        HistoryDateRangeDialog(
            onConfirm = { from, to ->
                viewModel.setDateRange(from, to)
                datePickerOpen = false
            },
            onClear = {
                viewModel.clearDateRange()
                datePickerOpen = false
            },
            onDismiss = { datePickerOpen = false },
        )
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Xoá tất cả?") },
            text = { Text("Toàn bộ lịch sử giao dịch sẽ bị xoá. Hành động này không thể hoàn tác.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAll()
                    confirmDeleteAll = false
                }) { Text("Xoá tất cả") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) { Text("Huỷ") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRow(
    state: HistoryUiState,
    onDirection: (DirectionFilter) -> Unit,
    onBank: (String?) -> Unit,
    onOpenDatePicker: () -> Unit,
) {
    var bankMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = state.direction == DirectionFilter.ALL,
            onClick = { onDirection(DirectionFilter.ALL) },
            label = { Text("Tất cả") },
        )
        FilterChip(
            selected = state.direction == DirectionFilter.INCOME,
            onClick = { onDirection(DirectionFilter.INCOME) },
            label = { Text("Tiền vào") },
        )
        FilterChip(
            selected = state.direction == DirectionFilter.OUTGOING,
            onClick = { onDirection(DirectionFilter.OUTGOING) },
            label = { Text("Tiền ra") },
        )
        FilterChip(
            selected = !state.dateRange.isAll,
            onClick = onOpenDatePicker,
            label = { Text(dateRangeLabel(state.dateRange)) },
            leadingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
        )
        Box {
            val label = state.availableBanks.firstOrNull { it.appId == state.bankFilter }?.displayName ?: "Ngân hàng"
            FilterChip(
                selected = state.bankFilter != null,
                onClick = { bankMenu = true },
                label = { Text(label) },
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            )
            DropdownMenu(expanded = bankMenu, onDismissRequest = { bankMenu = false }) {
                DropdownMenuItem(text = { Text("Tất cả ngân hàng") }, onClick = {
                    onBank(null); bankMenu = false
                })
                state.availableBanks.forEach { bank ->
                    DropdownMenuItem(text = { Text(bank.displayName) }, onClick = {
                        onBank(bank.appId); bankMenu = false
                    })
                }
            }
        }
    }
}

private val dayFmt = DateTimeFormatter.ofPattern("dd/MM", Locale("vi"))

/** Chip label for the current date range: "Tất cả ngày" or "dd/MM – dd/MM" (system zone). */
private fun dateRangeLabel(range: HistoryViewModel.DateRange): String {
    if (range.isAll) return "Tất cả ngày"
    val zone = ZoneId.systemDefault()
    val from = Instant.ofEpochMilli(range.from).atZone(zone).toLocalDate().format(dayFmt)
    val to = Instant.ofEpochMilli(range.to).atZone(zone).toLocalDate().format(dayFmt)
    return if (from == to) from else "$from – $to"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryDateRangeDialog(
    onConfirm: (Long?, Long?) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerState = rememberDateRangePickerState()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onConfirm(pickerState.selectedStartDateMillis, pickerState.selectedEndDateMillis)
            }) { Text("Áp dụng") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) { Text("Tất cả ngày") }
                TextButton(onClick = onDismiss) { Text("Huỷ") }
            }
        },
    ) {
        DateRangePicker(state = pickerState, modifier = Modifier.weight(1f, fill = false))
    }
}

@Composable
private fun SummaryCard(income: Long, outgoing: Long) {
    val extra = LocalAppExtraColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Row(Modifier.fillMaxWidth().padding(20.dp)) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Tiền vào", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                MoneyText(income, isIncome = true, style = MaterialTheme.typography.titleLarge)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Tiền ra", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                MoneyText(outgoing, isIncome = false, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun TransactionRowItem(
    row: HistoryRow,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
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
                BankBadge(row.bankName)
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(row.bankName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${row.timeLabel} • ${row.memo}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            MoneyText(row.amount, row.isIncome, style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = "Xoá", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TransactionDetailDialog(record: TransactionRecord, onDismiss: () -> Unit) {
    val tx = record.transaction
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tx.bankName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyText(tx.amount, tx.isIncome, style = MaterialTheme.typography.headlineSmall)
                Text(tx.rawText, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Đóng") } },
    )
}
