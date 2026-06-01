package com.tingting.notifier.ui.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tingting.notifier.data.model.TransactionModel
import com.tingting.notifier.data.model.WebhookConfig
import com.tingting.notifier.data.model.WebhookTrigger
import com.tingting.notifier.webhook.buildPayload
import com.tingting.notifier.webhook.toJson

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebhookScreen(
    onBack: () -> Unit,
    viewModel: WebhookViewModel = hiltViewModel(),
) {
    val settings by viewModel.uiState.collectAsStateWithLifecycle()
    val testState by viewModel.testState.collectAsStateWithLifecycle()
    val webhook = settings.webhook
    val snackbarHostState = remember { SnackbarHostState() }

    var enabled by remember { mutableStateOf(webhook.enabled) }
    var url by remember { mutableStateOf(webhook.url) }
    var secret by remember { mutableStateOf(webhook.secret) }
    var trigger by remember { mutableStateOf(webhook.trigger) }
    var secretVisible by remember { mutableStateOf(false) }

    // Re-seed the form once the persisted settings arrive (the StateFlow starts at a
    // blank default, then emits the real values from DataStore).
    LaunchedEffect(webhook) {
        enabled = webhook.enabled
        url = webhook.url
        secret = webhook.secret
        trigger = webhook.trigger
    }

    fun currentConfig(): WebhookConfig = webhook.copy(
        enabled = enabled,
        url = url.trim(),
        secret = secret.trim(),
        trigger = trigger,
    )

    val testing = testState is WebhookTestState.Testing

    // Surface the test outcome as a Snackbar, then reset the state.
    LaunchedEffect(testState) {
        when (val s = testState) {
            is WebhookTestState.Success -> {
                snackbarHostState.showSnackbar("Gửi thử thành công (HTTP ${s.code})")
                viewModel.clearTest()
            }
            is WebhookTestState.Failure -> {
                snackbarHostState.showSnackbar("Lỗi: ${s.message}")
                viewModel.clearTest()
            }
            else -> Unit
        }
    }

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shadowElevation = 8.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { viewModel.test(currentConfig()) },
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
                    Button(onClick = { viewModel.setWebhookConfig(currentConfig()) }, modifier = Modifier.weight(1f)) {
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

            // Intro card
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.Top) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.SyncAlt, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Spacer(Modifier.size(16.dp))
                    Text(
                        "Mỗi khi nhận được giao dịch mới, TingTing sẽ gửi (POST) dữ liệu sang URL của bạn — để tích hợp Google Sheet, Telegram, hệ thống POS, n8n...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Enable + status
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                Row(
                    Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Bật Webhook", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.size(12.dp))
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text(if (enabled) "Đang hoạt động" else "Đang tắt") },
                            colors = AssistChipDefaults.assistChipColors(
                                disabledContainerColor = if (enabled) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                disabledLabelColor = if (enabled) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }

            // Config fields
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("URL Webhook") },
                        placeholder = { Text("https://hook.us1.make.com/...") },
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

                    Text("Loại giao dịch gửi đi", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TriggerChip("Tiền vào", trigger == WebhookTrigger.INCOME) { trigger = WebhookTrigger.INCOME }
                        TriggerChip("Tiền ra", trigger == WebhookTrigger.OUTGOING) { trigger = WebhookTrigger.OUTGOING }
                        TriggerChip("Cả hai", trigger == WebhookTrigger.BOTH) { trigger = WebhookTrigger.BOTH }
                    }
                }
            }

            // JSON payload preview (dark rounded container, like the design)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Code, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.size(8.dp))
                        Text("Xem trước payload", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.inverseSurface,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            prettyPreview(),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TriggerChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
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
    // Insert a newline after each top-level comma + after the opening brace for readability.
    return json
        .replace("{", "{\n  ")
        .replace(",\"", ",\n  \"")
        .replace("}", "\n}")
}
