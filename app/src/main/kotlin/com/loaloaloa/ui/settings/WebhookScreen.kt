package com.loaloaloa.ui.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Webhook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loaloaloa.data.model.TransactionModel
import com.loaloaloa.data.model.WebhookConfig
import com.loaloaloa.data.model.WebhookTrigger
import com.loaloaloa.data.model.WebhookType
import com.loaloaloa.data.model.effectiveWebhooks
import com.loaloaloa.webhook.buildPayload
import com.loaloaloa.webhook.parseGoogleFormLink
import com.loaloaloa.webhook.toJson

/**
 * Outbound destinations. Lists every configured webhook (generic / Telegram / Google Sheet);
 * all enabled ones fire together per transaction. Tapping one — or "Thêm webhook" — opens a
 * type-aware editor. Stays a single nav destination: the editor is shown in place of the list.
 */
@Composable
fun WebhookScreen(
    onBack: () -> Unit,
    viewModel: WebhookViewModel = hiltViewModel(),
) {
    val settings by viewModel.uiState.collectAsStateWithLifecycle()
    val testState by viewModel.testState.collectAsStateWithLifecycle()
    val webhooks = settings.effectiveWebhooks

    var editing by remember { mutableStateOf<WebhookConfig?>(null) }

    val current = editing
    if (current == null) {
        WebhookList(
            webhooks = webhooks,
            onBack = onBack,
            onAdd = { editing = WebhookConfig() },
            onEdit = { editing = it },
            onToggle = viewModel::toggleEnabled,
            onDelete = viewModel::deleteWebhook,
        )
    } else {
        WebhookEditor(
            initial = current,
            testState = testState,
            onTest = viewModel::test,
            onSave = {
                viewModel.saveWebhook(it)
                editing = null
            },
            onBack = {
                viewModel.clearTest()
                editing = null
            },
            onTestShown = viewModel::clearTest,
        )
    }
}

// --- List ---------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebhookList(
    webhooks: List<WebhookConfig>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (WebhookConfig) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
) {
    var confirmDelete by remember { mutableStateOf<WebhookConfig?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Webhook", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shadowElevation = 8.dp) {
                Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Thêm webhook")
                }
            }
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

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                Text(
                    "Mỗi giao dịch mới sẽ được gửi sang tất cả các đích đang bật — Telegram, Google Sheet, hệ thống POS, n8n… Bạn có thể thêm nhiều đích cùng lúc.",
                    modifier = Modifier.padding(20.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (webhooks.isEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                    Column(
                        Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Chưa có webhook nào", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Bấm “Thêm webhook” để gửi giao dịch sang Telegram, Google Sheet hoặc hệ thống của bạn.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                webhooks.forEach { wh ->
                    WebhookRowCard(
                        config = wh,
                        onClick = { onEdit(wh) },
                        onToggle = { onToggle(wh.id, it) },
                        onDelete = { confirmDelete = wh },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    confirmDelete?.let { wh ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Xoá webhook?") },
            text = { Text("Đích “${displayName(wh)}” sẽ bị xoá và không nhận giao dịch nữa.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(wh.id)
                    confirmDelete = null
                }) { Text("Xoá") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Huỷ") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebhookRowCard(
    config: WebhookConfig,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(typeIcon(config.type), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(displayName(config), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    buildString {
                        append("${typeName(config.type)} • ${triggerName(config.trigger)}")
                        if (config.shiftEnabled) {
                            append(" • Ca ${minutesLabel(config.shiftStartMinutes)}–${minutesLabel(config.shiftEndMinutes)}")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    targetSummary(config),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(checked = config.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Xoá", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// --- Editor -------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebhookEditor(
    initial: WebhookConfig,
    testState: WebhookTestState,
    onTest: (WebhookConfig) -> Unit,
    onSave: (WebhookConfig) -> Unit,
    onBack: () -> Unit,
    onTestShown: () -> Unit,
) {
    val isNew = initial.id.isBlank()
    val snackbarHostState = remember { SnackbarHostState() }

    var type by remember { mutableStateOf(initial.type) }
    var label by remember { mutableStateOf(initial.label) }
    var enabled by remember { mutableStateOf(if (isNew) true else initial.enabled) }
    var url by remember { mutableStateOf(initial.url) }
    var secret by remember { mutableStateOf(initial.secret) }
    var botToken by remember { mutableStateOf(initial.botToken) }
    var chatId by remember { mutableStateOf(initial.chatId) }
    var trigger by remember { mutableStateOf(initial.trigger) }
    var secretVisible by remember { mutableStateOf(false) }

    // Optional work-shift window: only forward to this destination while staff are on shift.
    var shiftEnabled by remember { mutableStateOf(initial.shiftEnabled) }
    var shiftStart by remember { mutableIntStateOf(initial.shiftStartMinutes) }
    var shiftEnd by remember { mutableIntStateOf(initial.shiftEndMinutes) }
    var shiftStartDialog by remember { mutableStateOf(false) }
    var shiftEndDialog by remember { mutableStateOf(false) }

    // Google Form: the form's formResponse URL lives in [url]; these hold the entry.* ids,
    // parsed in one step from a pasted pre-filled link.
    var formLink by remember { mutableStateOf("") }
    var formAmountEntry by remember { mutableStateOf(initial.formAmountEntry) }
    var formBankEntry by remember { mutableStateOf(initial.formBankEntry) }
    var formTimeEntry by remember { mutableStateOf(initial.formTimeEntry) }
    var formNoteEntry by remember { mutableStateOf(initial.formNoteEntry) }

    fun build(): WebhookConfig = initial.copy(
        label = label.trim(),
        type = type,
        enabled = enabled,
        url = url.trim(),
        secret = secret.trim(),
        botToken = botToken.trim(),
        chatId = chatId.trim(),
        formAmountEntry = formAmountEntry.trim(),
        formBankEntry = formBankEntry.trim(),
        formTimeEntry = formTimeEntry.trim(),
        formNoteEntry = formNoteEntry.trim(),
        trigger = trigger,
        shiftEnabled = shiftEnabled,
        shiftStartMinutes = shiftStart,
        shiftEndMinutes = shiftEnd,
    )

    val testing = testState is WebhookTestState.Testing

    LaunchedEffect(testState) {
        when (val s = testState) {
            is WebhookTestState.Success -> {
                snackbarHostState.showSnackbar("Gửi thử thành công (HTTP ${s.code})")
                onTestShown()
            }
            is WebhookTestState.Failure -> {
                snackbarHostState.showSnackbar("Lỗi: ${s.message}")
                onTestShown()
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isNew) "Thêm webhook" else "Sửa webhook",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shadowElevation = 8.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { onTest(build()) },
                        enabled = !testing,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (testing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(8.dp))
                            Text("Đang gửi")
                        } else {
                            Text("Gửi thử")
                        }
                    }
                    Button(onClick = { onSave(build()) }, modifier = Modifier.weight(1f)) {
                        Text("Lưu")
                    }
                }
            }
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

            // Type selector
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Loại đích", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        WebhookType.entries.forEach { t ->
                            FilterChip(selected = type == t, onClick = { type = t }, label = { Text(typeName(t)) })
                        }
                    }
                }
            }

            // Enable + label
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Bật đích này", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Tên gợi nhớ (tuỳ chọn)") },
                        placeholder = { Text(typeName(type)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Type-specific fields
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when (type) {
                        WebhookType.TELEGRAM -> {
                            OutlinedTextField(
                                value = botToken,
                                onValueChange = { botToken = it },
                                label = { Text("Bot Token") },
                                placeholder = { Text("123456:ABC-DEF...") },
                                singleLine = true,
                                visualTransformation = if (secretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { secretVisible = !secretVisible }) {
                                        Icon(
                                            if (secretVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                            contentDescription = if (secretVisible) "Ẩn" else "Hiện",
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = chatId,
                                onValueChange = { chatId = it },
                                label = { Text("Chat ID") },
                                placeholder = { Text("-1001234567890 hoặc 123456789") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        WebhookType.GOOGLE_FORM -> {
                            OutlinedTextField(
                                value = formLink,
                                onValueChange = { raw ->
                                    formLink = raw
                                    parseGoogleFormLink(raw)?.let { link ->
                                        url = link.responseUrl
                                        formAmountEntry = link.amountEntry
                                        formBankEntry = link.bankEntry
                                        formTimeEntry = link.timeEntry
                                        formNoteEntry = link.noteEntry
                                    }
                                },
                                label = { Text("Link Google Form (đã điền sẵn)") },
                                placeholder = { Text("https://docs.google.com/forms/d/e/.../viewform?...") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            val mapped = listOf(
                                "Số tiền" to formAmountEntry,
                                "Ngân hàng" to formBankEntry,
                                "Thời gian" to formTimeEntry,
                                "Nội dung" to formNoteEntry,
                            ).filter { it.second.isNotBlank() }
                            if (mapped.isNotEmpty()) {
                                Text(
                                    "Đã nhận diện: " + mapped.joinToString("  •  ") { "${it.first} → ${it.second}" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Text(
                                    "Dán link điền sẵn để tự nhận diện các trường (entry.*).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        WebhookType.GENERIC, WebhookType.GOOGLE_SHEET -> {
                            OutlinedTextField(
                                value = url,
                                onValueChange = { url = it },
                                label = { Text(if (type == WebhookType.GOOGLE_SHEET) "URL Apps Script (/exec)" else "URL Webhook") },
                                placeholder = { Text(if (type == WebhookType.GOOGLE_SHEET) "https://script.google.com/macros/s/.../exec" else "https://hook.us1.make.com/...") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = secret,
                                onValueChange = { secret = it },
                                label = { Text("Khoá bí mật (tuỳ chọn)") },
                                supportingText = { Text("Gửi kèm header X-Webhook-Secret") },
                                singleLine = true,
                                visualTransformation = if (secretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { secretVisible = !secretVisible }) {
                                        Icon(
                                            if (secretVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                            contentDescription = if (secretVisible) "Ẩn secret" else "Hiện secret",
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    Text("Loại giao dịch gửi đi", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TriggerChip("Tiền vào", trigger == WebhookTrigger.INCOME) { trigger = WebhookTrigger.INCOME }
                        TriggerChip("Tiền ra", trigger == WebhookTrigger.OUTGOING) { trigger = WebhookTrigger.OUTGOING }
                        TriggerChip("Cả hai", trigger == WebhookTrigger.BOTH) { trigger = WebhookTrigger.BOTH }
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "Chỉ gửi trong ca làm việc",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = shiftEnabled, onCheckedChange = { shiftEnabled = it })
                    }
                    if (shiftEnabled) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { shiftStartDialog = true }, modifier = Modifier.weight(1f)) {
                                Text("Từ ${minutesLabel(shiftStart)}")
                            }
                            OutlinedButton(onClick = { shiftEndDialog = true }, modifier = Modifier.weight(1f)) {
                                Text("Đến ${minutesLabel(shiftEnd)}")
                            }
                        }
                        Text(
                            "Nhân viên chỉ nhận thông báo trong khung giờ này. Hỗ trợ ca qua đêm (vd 22:00 → 06:00).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Per-type setup help
            when (type) {
                WebhookType.GOOGLE_SHEET -> HelpCard("Cách kết nối Google Sheet", GOOGLE_SHEET_STEPS, code = GOOGLE_SHEET_SCRIPT)
                WebhookType.GOOGLE_FORM -> HelpCard("Cách kết nối Google Form", GOOGLE_FORM_STEPS)
                WebhookType.TELEGRAM -> HelpCard("Cách lấy Bot Token & Chat ID", TELEGRAM_STEPS)
                WebhookType.GENERIC -> PayloadPreviewCard()
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    if (shiftStartDialog) {
        TimePickerDialog(
            initialMinutes = shiftStart,
            onConfirm = { shiftStart = it; shiftStartDialog = false },
            onDismiss = { shiftStartDialog = false },
        )
    }
    if (shiftEndDialog) {
        TimePickerDialog(
            initialMinutes = shiftEnd,
            onConfirm = { shiftEnd = it; shiftEndDialog = false },
            onDismiss = { shiftEndDialog = false },
        )
    }
}

@Composable
private fun HelpCard(title: String, steps: String, code: String? = null) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(steps, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (code != null) {
                CodeBlock(code)
            }
        }
    }
}

@Composable
private fun PayloadPreviewCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Code, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(8.dp))
                Text("Xem trước payload", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            CodeBlock(prettyPreview())
        }
    }
}

@Composable
private fun CodeBlock(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.inverseSurface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.inverseOnSurface,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TriggerChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

// --- helpers ------------------------------------------------------------------

private fun typeName(type: WebhookType): String = when (type) {
    WebhookType.GENERIC -> "Webhook"
    WebhookType.TELEGRAM -> "Telegram"
    WebhookType.GOOGLE_SHEET -> "Google Sheet"
    WebhookType.GOOGLE_FORM -> "Google Form"
}

private fun typeIcon(type: WebhookType): ImageVector = when (type) {
    WebhookType.GENERIC -> Icons.Filled.Webhook
    WebhookType.TELEGRAM -> Icons.AutoMirrored.Filled.Send
    WebhookType.GOOGLE_SHEET -> Icons.Filled.GridOn
    WebhookType.GOOGLE_FORM -> Icons.Filled.Description
}

private fun triggerName(trigger: WebhookTrigger): String = when (trigger) {
    WebhookTrigger.INCOME -> "Tiền vào"
    WebhookTrigger.OUTGOING -> "Tiền ra"
    WebhookTrigger.BOTH -> "Cả hai"
}

private fun displayName(config: WebhookConfig): String = config.label.ifBlank { typeName(config.type) }

private fun targetSummary(config: WebhookConfig): String = when (config.type) {
    WebhookType.TELEGRAM -> if (config.chatId.isBlank()) "Chưa cấu hình" else "Chat ID: ${config.chatId}"
    else -> config.url.ifBlank { "Chưa nhập URL" }
}

/** A pretty-printed sample payload (one key per line) for the read-only preview. */
private fun prettyPreview(): String {
    val sample = TransactionModel(
        appId = "com.mbmobile",
        bankName = "MB Bank",
        amount = 500_000L,
        isIncome = true,
        rawText = "Thanh toan don hang",
        timestamp = 0L,
    )
    val json = buildPayload(sample, "2026-06-01T21:46:00+07:00").toJson()
    return json
        .replace("{", "{\n  ")
        .replace(",\"", ",\n  \"")
        .replace("}", "\n}")
}

private const val GOOGLE_SHEET_STEPS =
    "1. Mở Google Sheet → Tiện ích mở rộng → Apps Script.\n" +
        "2. Dán đoạn mã bên dưới rồi Lưu.\n" +
        "3. Triển khai → Tuỳ chọn triển khai mới → Ứng dụng web, quyền “Bất kỳ ai”.\n" +
        "4. Sao chép URL kết thúc bằng /exec và dán vào ô URL ở trên."

private const val GOOGLE_SHEET_SCRIPT =
    "function doPost(e) {\n" +
        "  var d = JSON.parse(e.postData.contents);\n" +
        "  SpreadsheetApp.getActiveSheet()\n" +
        "    .appendRow([d.timestamp, d.bank, d.type, d.amount, d.content]);\n" +
        "  return ContentService.createTextOutput('ok');\n" +
        "}"

private const val GOOGLE_FORM_STEPS =
    "1. Tạo Google Form với 4 câu hỏi trả lời ngắn theo thứ tự: Số tiền, Ngân hàng, Thời gian, Nội dung.\n" +
        "2. Bấm ⋮ (góc trên phải) → “Lấy đường liên kết đã điền sẵn”.\n" +
        "3. Điền giá trị mẫu cho 4 câu hỏi rồi bấm “Lấy đường liên kết” và sao chép.\n" +
        "4. Dán link vào ô trên — app sẽ tự nhận diện các trường rồi bấm “Gửi thử”."

private const val TELEGRAM_STEPS =
    "1. Nhắn @BotFather → /newbot để tạo bot và lấy Bot Token.\n" +
        "2. Nhắn vài tin cho bot của bạn (hoặc thêm bot vào nhóm).\n" +
        "3. Mở @userinfobot để lấy Chat ID (với nhóm, dùng @RawDataBot).\n" +
        "4. Dán Token + Chat ID vào ô trên rồi bấm “Gửi thử”."
