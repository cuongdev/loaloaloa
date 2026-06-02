package com.loaloaloa.ui.history

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.EditNote
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loaloaloa.data.model.TransactionRecord
import com.loaloaloa.ui.components.BankBadge
import com.loaloaloa.ui.components.MoneyText
import com.loaloaloa.ui.theme.LocalAppExtraColors
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
    var searchActive by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<TransactionRecord?>(null) }
    var confirmDelete by remember { mutableStateOf<TransactionRecord?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var datePickerOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lịch sử", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = {
                        searchActive = !searchActive
                        if (!searchActive) viewModel.setSearchQuery("")
                    }) {
                        Icon(
                            if (searchActive) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = if (searchActive) "Đóng tìm kiếm" else "Tìm kiếm",
                        )
                    }
                    if (state.canDelete) {
                        IconButton(onClick = { confirmDeleteAll = true }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Xoá tất cả")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            if (searchActive) {
                val focusRequester = remember { FocusRequester() }
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .focusRequester(focusRequester),
                    placeholder = { Text("Tìm giao dịch...") },
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
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
            }
            FilterRow(
                state = state,
                onDirection = viewModel::setDirection,
                onBank = viewModel::setBankFilter,
                onOpenDatePicker = { datePickerOpen = true },
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { SummaryCard(income = state.incomeTotal, outgoing = state.outgoingTotal) }

                if (state.isEmpty) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ReceiptLong,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Chưa có giao dịch nào",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
                            onDelete = if (state.canDelete) { { confirmDelete = row.record } } else null,
                        )
                    }
                }

                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }

    detail?.let { rec ->
        TransactionDetailDialog(
            record = rec,
            onSaveNote = { note -> viewModel.updateNote(rec.id, note) },
            onDismiss = { detail = null },
        )
    }

    confirmDelete?.let { rec ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Xoá giao dịch?") },
            text = { Text("Giao dịch này sẽ bị xoá khỏi lịch sử. Bạn có thể hoàn tác ngay sau đó.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    viewModel.delete(rec)
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = "Đã xoá giao dịch",
                            actionLabel = "Hoàn tác",
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.undoDelete(rec)
                        }
                    }
                }) { Text("Xoá") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Huỷ") }
            },
        )
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
    val net = income - outgoing
    val extra = LocalAppExtraColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                "Còn lại",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MoneyText(
                kotlin.math.abs(net),
                isIncome = net >= 0,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SummaryStat(Modifier.weight(1f), "Tiền vào", income, isIncome = true, Icons.Filled.ArrowDownward, extra.income)
                SummaryStat(Modifier.weight(1f), "Tiền ra", outgoing, isIncome = false, Icons.Filled.ArrowUpward, extra.outgoing)
            }
        }
    }
}

/** One income/outgoing stat in the [SummaryCard]: a tinted arrow badge + label + amount. */
@Composable
private fun SummaryStat(
    modifier: Modifier,
    label: String,
    amount: Long,
    isIncome: Boolean,
    icon: ImageVector,
    tint: Color,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(30.dp).clip(CircleShape).background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.size(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MoneyText(amount, isIncome = isIncome, style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun TransactionRowItem(
    row: HistoryRow,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?,
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
                    )
                    if (row.note.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.EditNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.size(4.dp))
                            Text(
                                row.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            MoneyText(row.amount, row.isIncome, style = MaterialTheme.typography.titleSmall)
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Xoá", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TransactionDetailDialog(
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
