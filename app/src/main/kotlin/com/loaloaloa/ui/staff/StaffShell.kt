package com.loaloaloa.ui.staff

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loaloaloa.data.model.RelayRole
import com.loaloaloa.data.model.TransactionRecord
import com.loaloaloa.relay.RelayDevice
import com.loaloaloa.ui.history.HistoryScreen
import com.loaloaloa.ui.navigation.StaffConnStatus
import com.loaloaloa.ui.navigation.connectionStatusOf
import com.loaloaloa.ui.report.ReportScreen
import com.loaloaloa.ui.settings.DevicesUiState
import com.loaloaloa.ui.settings.QrScanner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Staff (spoke) shell shown when appMode == STAFF. Unpaired: shows a scan card. Paired: shows a
 * bottom navigation bar with four tabs — Loa (connection + TTS switch), Lịch sử, Báo cáo, Thiết bị.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffShell(
    onOpenTts: () -> Unit,
    onOpenShift: () -> Unit = {},
    onRequestBatteryExemption: () -> Unit = {},
    pairToken: String? = null,
    onPairTokenHandled: () -> Unit = {},
    viewModel: StaffViewModel = hiltViewModel(),
) {
    val s by viewModel.uiState.collectAsStateWithLifecycle()
    val latest by viewModel.latest.collectAsStateWithLifecycle()
    val scanInvalid by viewModel.scanInvalid.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(pairToken) {
        if (pairToken != null) {
            viewModel.pairFromScan(pairToken)
            onPairTokenHandled()
        }
    }
    LaunchedEffect(scanInvalid) {
        if (scanInvalid) {
            snackbarHostState.showSnackbar("Mã QR không hợp lệ. Hãy quét đúng mã trên máy shop.")
            viewModel.clearScanInvalid()
        }
    }

    var scanning by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> scanning = granted }
    fun startScan() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) scanning = true else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (scanning) {
        QrScanner(
            onResult = { scanning = false; viewModel.pairFromScan(it) },
            onClose = { scanning = false },
        )
        return
    }

    val status = connectionStatusOf(s.relayRole, s.relayRegisterState)
    val isPaired = s.relayRole == RelayRole.SPOKE

    if (!isPaired) {
        // Unpaired state — no bottom nav, just the scan card
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Nhân viên", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
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
                Spacer(Modifier.size(4.dp))
                UnpairedCard(onScan = ::startScan)
                Spacer(Modifier.size(16.dp))
            }
        }
        return
    }

    // Paired state — bottom navigation shell
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nhân viên", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Filled.GraphicEq, contentDescription = null) },
                    label = { Text("Loa") },
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Filled.History, contentDescription = null) },
                    label = { Text("Lịch sử") },
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Filled.BarChart, contentDescription = null) },
                    label = { Text("Báo cáo") },
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Filled.Devices, contentDescription = null) },
                    label = { Text("Thiết bị") },
                )
            }
        },
    ) { padding ->
        when (selectedTab) {
            0 -> LoaTab(
                modifier = Modifier.padding(padding),
                status = status,
                latest = latest,
                enableService = s.enableService,
                onLoaToggle = viewModel::setLoaEnabled,
                onRetry = viewModel::retryRegister,
                onRequestBatteryExemption = onRequestBatteryExemption,
                onOpenTts = onOpenTts,
                onOpenShift = onOpenShift,
                onUnpair = viewModel::unpairToShop,
            )
            1 -> Box(Modifier.padding(padding)) { HistoryScreen() }
            2 -> Box(Modifier.padding(padding)) { ReportScreen() }
            3 -> {
                LaunchedEffect(Unit) { viewModel.refreshDevices() }
                DevicesTab(
                    modifier = Modifier.padding(padding),
                    devices = devices,
                    onRevoke = viewModel::revokeDevice,
                )
            }
        }
    }
}

@Composable
private fun LoaTab(
    modifier: Modifier = Modifier,
    status: StaffConnStatus,
    latest: TransactionRecord?,
    enableService: Boolean,
    onLoaToggle: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onOpenTts: () -> Unit,
    onOpenShift: () -> Unit,
    onUnpair: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.size(4.dp))

        // Master "Bật loa" switch — placed first so it's immediately obvious
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        ) {
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.GraphicEq,
                    contentDescription = null,
                    tint = if (enableService) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    "Bật loa",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Switch(checked = enableService, onCheckedChange = onLoaToggle)
            }
        }

        StatusCard(status = status, latest = latest, onRetry = onRetry)

        OutlinedButton(onClick = onRequestBatteryExemption, modifier = Modifier.fillMaxWidth()) {
            Text("Tắt tối ưu pin (giúp nhận thông báo ổn định)")
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        ) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(12.dp))
                Text("Cài đặt loa", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                OutlinedButton(onClick = onOpenTts) { Text("Mở") }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        ) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(12.dp))
                Text("Chốt ca / Bàn giao", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                OutlinedButton(onClick = onOpenShift) { Text("Mở") }
            }
        }

        OutlinedButton(onClick = onUnpair, modifier = Modifier.fillMaxWidth()) {
            Text("Huỷ ghép / Đổi sang Shop")
        }

        Spacer(Modifier.size(16.dp))
    }
}

@Composable
private fun DevicesTab(
    modifier: Modifier = Modifier,
    devices: DevicesUiState,
    onRevoke: (String) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.size(4.dp))

        when {
            devices.loading -> {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            devices.error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Text(
                        "Không tải được danh sách thiết bị. Kiểm tra kết nối và thử lại.",
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            devices.loaded && devices.devices.isEmpty() -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                ) {
                    Text(
                        "Chưa có thiết bị nhân viên nào được ghép.",
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                devices.devices.forEach { device ->
                    DeviceRow(device = device, onRevoke = { onRevoke(device.token) })
                }
            }
        }

        Spacer(Modifier.size(16.dp))
    }
}

@Composable
private fun DeviceRow(
    device: RelayDevice,
    onRevoke: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Devices, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val displayLabel = device.label.ifBlank { "Thiết bị không tên" }
                Text(displayLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (device.ts > 0) {
                    val dateStr = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale("vi")).format(Date(device.ts * 1000))
                    Text(
                        "Ghép lúc $dateStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedButton(onClick = onRevoke) { Text("Huỷ ghép") }
        }
    }
}

@Composable
private fun UnpairedCard(onScan: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Ghép với máy shop", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Quét mã QR đang hiện trên máy shop để nghe biến động số dư trên loa. Không cần đăng nhập ngân hàng.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Quét mã QR")
            }
        }
    }
}

@Composable
private fun StatusCard(
    status: StaffConnStatus,
    latest: TransactionRecord?,
    onRetry: () -> Unit,
) {
    val (title, detail, isError) = when (status) {
        StaffConnStatus.CONNECTED -> Triple("Đã kết nối", "Máy này đang nghe biến động số dư từ shop.", false)
        StaffConnStatus.REGISTERING -> Triple("Đang đăng ký thiết bị…", "Đang kết nối tới shop, vui lòng đợi.", false)
        StaffConnStatus.NO_FCM -> Triple(
            "Lỗi: thiếu Google Play Services",
            "Máy này không có Google Play Services nên không nhận được thông báo. Dùng máy Android khác hoặc bản web.",
            true,
        )
        StaffConnStatus.NOT_PAIRED -> Triple("Chưa ghép", "", true)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val lastText = latest?.let { "Nghe gần nhất: " + lastHeard(it) }
            if (lastText != null) {
                Text(lastText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (status != StaffConnStatus.CONNECTED) {
                OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Thử lại") }
            }
        }
    }
}

/** "HH:mm dd/MM" for the latest received transaction's timestamp. */
private fun lastHeard(record: TransactionRecord): String =
    SimpleDateFormat("HH:mm dd/MM", Locale("vi")).format(Date(record.transaction.timestamp))
