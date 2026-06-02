package com.loaloaloa.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.loaloaloa.data.model.DEFAULT_RELAY_SENDER_URL
import com.loaloaloa.data.model.RelayRole
import com.loaloaloa.data.model.UserSettings
import com.loaloaloa.relay.RelayDevice
import com.loaloaloa.relay.RelayEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Chia sẻ thông báo": pair this device into a relay room. The shop phone (which sees the bank
 * notifications) becomes the HUB and publishes a pairing QR; each employee phone scans it to become
 * a SPOKE and hear the same transactions on its loa — no bank login needed on the employee phone.
 *
 * This screen only manages role + pairing via [RelayViewModel]; encryption/transport lives in the
 * relay package and is driven by FCM.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelaySettingsScreen(
    onBack: () -> Unit,
    viewModel: RelayViewModel = hiltViewModel(),
    incomingToken: String? = null,
    onIncomingTokenHandled: () -> Unit = {},
) {
    val s by viewModel.uiState.collectAsStateWithLifecycle()
    val scanInvalid by viewModel.scanInvalid.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // A pairing link opened from outside (the App-Link QR/URL) lands here — apply it once, the same
    // way a camera scan would, then clear it so it isn't re-applied on recomposition.
    LaunchedEffect(incomingToken) {
        if (incomingToken != null) {
            viewModel.pairFromScan(incomingToken)
            onIncomingTokenHandled()
        }
    }

    // Auto-load the paired-devices list once this device is acting as a hub.
    LaunchedEffect(s.relayRole) {
        if (s.relayRole == RelayRole.HUB) viewModel.refreshDevices()
    }

    var confirmUnpair by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> scanning = granted }

    fun startScan() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) scanning = true else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(scanInvalid) {
        if (scanInvalid) {
            snackbarHostState.showSnackbar("Mã QR không hợp lệ. Hãy quét đúng mã trên máy shop.")
            viewModel.clearScanInvalid()
        }
    }

    if (scanning) {
        QrScanner(
            onResult = {
                scanning = false
                viewModel.pairFromScan(it)
            },
            onClose = { scanning = false },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chia sẻ thông báo", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            IntroCard()

            when (s.relayRole) {
                RelayRole.NONE -> UnpairedSection(
                    onCreateRoom = viewModel::createRoom,
                    onScan = ::startScan,
                )
                RelayRole.HUB -> HubSection(
                    token = viewModel.pairingLink(s),
                    senderUrl = s.relayRoom.senderUrl,
                    devices = devices,
                    onRefreshDevices = viewModel::refreshDevices,
                    onRevokeDevice = viewModel::revokeDevice,
                    onUnpair = { confirmUnpair = true },
                )
                RelayRole.SPOKE -> SpokeSection(
                    settings = s,
                    onRescan = ::startScan,
                    onUnpair = { confirmUnpair = true },
                )
            }

            if (confirmUnpair) {
                AlertDialog(
                    onDismissRequest = { confirmUnpair = false },
                    title = { Text("Huỷ phòng?") },
                    text = {
                        Text(
                            "Máy này sẽ rời phòng và ngừng nhận / chia sẻ thông báo giao dịch. " +
                                "Bạn có chắc muốn huỷ ghép không?",
                        )
                    },
                    confirmButton = {
                        Button(onClick = {
                            confirmUnpair = false
                            viewModel.unpair()
                        }) { Text("Huỷ phòng") }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { confirmUnpair = false }) { Text("Không") }
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun IntroCard() {
    InfoCard(
        "Máy shop (đã đăng nhập ngân hàng) sẽ tạo một “phòng” và hiện mã QR. Mỗi máy nhân viên quét " +
            "mã đó để cùng nghe thông báo giao dịch trên loa — không cần đăng nhập ngân hàng trên máy " +
            "nhân viên. Thông báo được mã hoá đầu-cuối nên máy chủ trung gian không đọc được nội dung.",
    )
}

// --- Unpaired: choose to be a hub or a spoke ---------------------------------

@Composable
private fun UnpairedSection(
    onCreateRoom: (String) -> Unit,
    onScan: () -> Unit,
) {
    var url by remember { mutableStateOf(DEFAULT_RELAY_SENDER_URL) }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(Icons.Filled.Store, "Máy shop — tạo phòng")
            Text(
                "Nhập địa chỉ máy chủ chuyển tiếp (Cloudflare Worker) của bạn rồi tạo phòng. Máy này sẽ " +
                    "chuyển tiếp mọi giao dịch tới các máy nhân viên đã ghép.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL máy chủ chuyển tiếp") },
                placeholder = { Text("https://ten-cua-ban.workers.dev") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onCreateRoom(url) },
                enabled = url.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.QrCode2, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Tạo phòng & hiện mã QR")
            }
        }
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(Icons.Filled.QrCodeScanner, "Máy nhân viên — quét mã")
            Text(
                "Quét mã QR đang hiện trên máy shop để tham gia phòng và nghe thông báo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Quét mã QR")
            }
        }
    }
}

// --- Hub: show the pairing QR + token -----------------------------------------

@Composable
private fun HubSection(
    token: String?,
    senderUrl: String,
    devices: DevicesUiState,
    onRefreshDevices: () -> Unit,
    onRevokeDevice: (String) -> Unit,
    onUnpair: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SectionTitle(Icons.Filled.QrCode2, "Mã ghép phòng")
            Text(
                "Cho mỗi máy nhân viên quét mã QR này. Máy đã cài app sẽ tự mở và ghép phòng; máy " +
                    "chưa cài thì quét bằng camera sẽ được dẫn tới trang tải app. Giữ mã bí mật — " +
                    "ai quét được cũng nghe được giao dịch của bạn.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (token != null) {
                QrImage(token)
                CopyableToken(token)
            } else {
                Text(
                    "Phòng chưa sẵn sàng.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    PairedDevicesCard(
        devices = devices,
        onRefresh = onRefreshDevices,
        onRevoke = onRevokeDevice,
    )

    if (senderUrl.isNotBlank()) {
        InfoCard("Máy chủ chuyển tiếp: $senderUrl")
    }

    OutlinedButton(onClick = onUnpair, modifier = Modifier.fillMaxWidth()) {
        Text("Huỷ phòng")
    }
}

/** The hub's list of paired employee phones, each revocable, with loading/empty/error states. */
@Composable
private fun PairedDevicesCard(
    devices: DevicesUiState,
    onRefresh: () -> Unit,
    onRevoke: (String) -> Unit,
) {
    var pendingRevoke by remember { mutableStateOf<RelayDevice?>(null) }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    SectionTitle(Icons.Filled.Devices, "Máy nhân viên đã ghép")
                }
                IconButton(onClick = onRefresh, enabled = !devices.loading) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Tải lại")
                }
            }

            when {
                devices.loading && devices.devices.isEmpty() -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(12.dp))
                        Text("Đang tải…", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                devices.error && devices.devices.isEmpty() -> {
                    Text(
                        "Không tải được danh sách. Kiểm tra kết nối mạng rồi thử lại.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                        Text("Thử lại")
                    }
                }

                devices.loaded && devices.devices.isEmpty() -> {
                    Text(
                        "Chưa có máy nào ghép. Cho máy nhân viên quét mã QR ở trên để tham gia.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> {
                    devices.devices.forEachIndexed { index, device ->
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }
                        DeviceRow(device = device, onRevoke = { pendingRevoke = device })
                    }
                }
            }

            if (devices.events.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Text(
                    "Lịch sử ghép / thu hồi",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start),
                )
                devices.events.take(30).forEach { e ->
                    val paired = e.type == "pair"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (paired) "＋" else "✕",
                            color = if (paired) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.size(10.dp))
                        Text(
                            e.label.ifBlank { "Thiết bị" } + " · " + if (paired) "Ghép" else "Thu hồi",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            relayEventTime(e.ts),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    pendingRevoke?.let { device ->
        AlertDialog(
            onDismissRequest = { pendingRevoke = null },
            title = { Text("Thu hồi máy này?") },
            text = {
                Text(
                    "“${device.displayName()}” sẽ không còn nhận thông báo giao dịch nữa. " +
                        "Máy đó có thể quét lại mã QR để tham gia lần nữa.",
                )
            },
            confirmButton = {
                Button(onClick = {
                    onRevoke(device.token)
                    pendingRevoke = null
                }) { Text("Thu hồi") }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingRevoke = null }) { Text("Huỷ") }
            },
        )
    }
}

@Composable
private fun DeviceRow(device: RelayDevice, onRevoke: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Smartphone,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                device.displayName(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            val subtitle = device.pairedAtText()
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(onClick = onRevoke) {
            Text("Thu hồi", color = MaterialTheme.colorScheme.error)
        }
    }
}

/** A friendly device name, falling back when a spoke paired before labels existed. */
private fun RelayDevice.displayName(): String = label.ifBlank { "Máy nhân viên" }

/** Audit-event time, e.g. "14:05 02/06" (the Sender stamps events in epoch-seconds). */
private fun relayEventTime(ts: Long): String =
    if (ts <= 0) "" else java.text.SimpleDateFormat("HH:mm dd/MM", java.util.Locale("vi")).format(java.util.Date(ts * 1000))

/** "Ghép lúc dd/MM HH:mm" for the pairing time, or null when unknown (ts == 0). */
private fun RelayDevice.pairedAtText(): String? {
    if (ts <= 0) return null
    val formatted = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(ts * 1000))
    return "Ghép lúc $formatted"
}

// --- Spoke: joined a room ------------------------------------------------------

@Composable
private fun SpokeSection(
    settings: UserSettings,
    onRescan: () -> Unit,
    onUnpair: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(Icons.Filled.QrCodeScanner, "Đã tham gia phòng")
            Text(
                "Máy này sẽ nghe thông báo giao dịch do máy shop chuyển tiếp. Không cần đăng nhập ngân hàng.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (settings.relayRoom.senderUrl.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Máy chủ: ${settings.relayRoom.senderUrl}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    OutlinedButton(onClick = onRescan, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
        Spacer(Modifier.size(8.dp))
        Text("Quét lại mã khác")
    }
    OutlinedButton(onClick = onUnpair, modifier = Modifier.fillMaxWidth()) {
        Text("Rời phòng")
    }
}

// --- Shared bits ---------------------------------------------------------------

@Composable
private fun QrImage(token: String) {
    // Encode once per token; the bitmap is pure w.r.t. the token so it survives recomposition.
    val bitmap: Bitmap? = remember(token) {
        runCatching { BarcodeEncoder().encodeBitmap(token, BarcodeFormat.QR_CODE, QR_SIZE_PX, QR_SIZE_PX) }
            .getOrNull()
    }
    if (bitmap != null) {
        Surface(color = androidx.compose.ui.graphics.Color.White, shape = MaterialTheme.shapes.medium) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Mã QR ghép phòng",
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .aspectRatio(1f)
                    .padding(12.dp),
            )
        }
    }
}

@Composable
private fun CopyableToken(token: String) {
    val clipboard = LocalClipboardManager.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                token,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            IconButton(onClick = { clipboard.setText(AnnotatedString(token)) }) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Sao chép mã")
            }
        }
    }
}

@Composable
private fun SectionTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(8.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun InfoCard(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Box(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val QR_SIZE_PX = 720
