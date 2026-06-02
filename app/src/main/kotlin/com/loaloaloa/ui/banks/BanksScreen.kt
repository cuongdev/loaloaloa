package com.loaloaloa.ui.banks

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loaloaloa.ui.components.BankBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BanksScreen(
    onBack: () -> Unit,
    viewModel: BanksViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val installed by viewModel.installed.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }

    // The custom-apps section is prepended above the bank list, so LazyColumn's scroll-anchoring
    // would otherwise keep it hidden above the fold. Reveal it whenever the count grows (initial
    // load 0→N and each add); a shrink (remove) leaves the user's scroll position untouched.
    val listState = rememberLazyListState()
    var lastCustomCount by remember { mutableStateOf(0) }
    LaunchedEffect(state.customApps.size) {
        if (state.customApps.size > lastCustomCount) listState.scrollToItem(0)
        lastCustomCount = state.customApps.size
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ngân hàng & ứng dụng", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
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
                .padding(horizontal = 16.dp),
        ) {
            Text(
                "Hỗ trợ hơn 50 ngân hàng & ví điện tử. Bạn cũng có thể tự thêm ứng dụng khác để đọc thông báo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            FilledTonalButton(
                onClick = {
                    viewModel.loadInstalledApps()
                    showPicker = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Thêm ứng dụng")
            }
            Spacer(Modifier.size(12.dp))
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Tìm ngân hàng...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
            )
            Spacer(Modifier.size(12.dp))
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.customApps.isNotEmpty()) {
                    item(key = "custom-header") { SectionHeader("Ứng dụng bạn đã thêm") }
                    items(state.customApps, key = { "custom-${it.packageName}" }) { app ->
                        SourceCard(
                            packageName = app.packageName,
                            displayName = app.displayName,
                            trailing = {
                                IconButton(onClick = { viewModel.removeCustomApp(app.packageName) }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Xoá ${app.displayName}",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        )
                    }
                    item(key = "banks-header") { SectionHeader("Ngân hàng hỗ trợ") }
                }
                items(state.banks, key = { it.packageName }) { bank ->
                    SourceCard(packageName = bank.packageName, displayName = bank.displayName)
                }
            }
        }
    }

    if (showPicker) {
        AppPickerSheet(
            apps = installed,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            onDismiss = { showPicker = false },
            onPick = { app ->
                viewModel.addCustomApp(app.packageName, app.label)
                showPicker = false
            },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun SourceCard(
    packageName: String,
    displayName: String,
    trailing: @Composable (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(packageName = packageName, fallbackName = displayName, size = 48)
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(displayName, style = MaterialTheme.typography.titleSmall)
                Text(
                    packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (trailing != null) trailing()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPickerSheet(
    apps: List<InstalledApp>?,
    sheetState: androidx.compose.material3.SheetState,
    onDismiss: () -> Unit,
    onPick: (InstalledApp) -> Unit,
) {
    var search by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                "Chọn ứng dụng",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "App chỉ tạo giao dịch khi thông báo có số tiền đọc được.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.size(12.dp))
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Tìm ứng dụng...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
            )
            Spacer(Modifier.size(12.dp))

            when {
                apps == null -> {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                else -> {
                    val term = search.trim().lowercase()
                    val filtered = if (term.isEmpty()) {
                        apps
                    } else {
                        apps.filter {
                            it.label.lowercase().contains(term) || it.packageName.lowercase().contains(term)
                        }
                    }
                    if (filtered.isEmpty()) {
                        Text(
                            "Không tìm thấy ứng dụng nào",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 420.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(filtered, key = { it.packageName }) { app ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(MaterialTheme.shapes.medium)
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    AppIcon(packageName = app.packageName, fallbackName = app.label, size = 40)
                                    Spacer(Modifier.size(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(app.label, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            app.packageName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    FilledTonalButton(onClick = { onPick(app) }) { Text("Thêm") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The installed app's launcher icon, falling back to an initials [BankBadge] when the icon can't be
 * loaded (e.g. an uninstalled custom source). The bitmap is resolved once per [packageName].
 */
@Composable
private fun AppIcon(packageName: String, fallbackName: String, size: Int) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap().asImageBitmap() }
            .getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = fallbackName,
            modifier = Modifier.size(size.dp).clip(CircleShape),
        )
    } else {
        BankBadge(fallbackName, size = size)
    }
}
