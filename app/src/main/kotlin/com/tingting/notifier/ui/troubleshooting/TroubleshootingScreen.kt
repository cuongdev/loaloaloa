package com.tingting.notifier.ui.troubleshooting

import android.os.Build
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tingting.notifier.permission.BatteryOptimizationHelper
import com.tingting.notifier.permission.NotificationAccessHelper
import com.tingting.notifier.permission.OemAutostart
import com.tingting.notifier.reliability.ListenerRebinder
import com.tingting.notifier.ui.onboarding.launchIntentSafely
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TroubleshootingScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    // Lazily-created TTS engine for the "test Vietnamese voice" action.
    val tts = remember {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale("vi", "VN")
            }
        }
        engine
    }
    DisposableEffect(Unit) {
        onDispose { tts?.stop(); tts?.shutdown() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Khắc phục sự cố", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Nếu app không đọc thông báo, hãy thử các bước sau.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            item {
                GuideCard(
                    icon = Icons.Filled.NotificationsActive,
                    title = "Cấp lại quyền đọc thông báo",
                    desc = "Mở cài đặt quyền truy cập thông báo",
                    action = "Mở",
                ) {
                    launchIntentSafely(context, "Không mở được cài đặt") { NotificationAccessHelper.settingsIntent() }
                }
            }
            item {
                GuideCard(
                    icon = Icons.Filled.BatteryChargingFull,
                    title = "Tắt tối ưu hoá pin",
                    desc = "Giúp app chạy nền ổn định",
                    action = "Thiết lập",
                ) {
                    launchIntentSafely(context, "Thiết bị không hỗ trợ") { BatteryOptimizationHelper.requestIntent(context.packageName) }
                }
            }
            item {
                GuideCard(
                    icon = Icons.Filled.RocketLaunch,
                    title = "Cho phép tự khởi động",
                    desc = "Tuỳ hãng máy (Xiaomi, Oppo, Vivo...)",
                    action = "Mở cài đặt",
                ) {
                    launchIntentSafely(context, "Không tìm thấy cài đặt tự khởi động") { OemAutostart.autostartIntent(Build.MANUFACTURER) }
                }
            }
            item {
                GuideCard(
                    icon = Icons.Filled.RecordVoiceOver,
                    title = "Kiểm tra giọng đọc tiếng Việt",
                    desc = "Phát thử câu thông báo mẫu",
                    action = "Phát thử",
                ) {
                    val sample = "Ting ting! Bạn vừa nhận được 500.000 đồng"
                    val ok = tts?.speak(sample, TextToSpeech.QUEUE_FLUSH, null, "tt-test")
                    if (ok == null || ok == TextToSpeech.ERROR) {
                        Toast.makeText(context, "Chưa sẵn sàng giọng đọc tiếng Việt", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            item {
                GuideCard(
                    icon = Icons.Filled.RestartAlt,
                    title = "Khởi động lại dịch vụ",
                    desc = "Làm mới kết nối lắng nghe thông báo",
                    action = "Khởi động lại",
                ) {
                    ListenerRebinder.rebind(context)
                    Toast.makeText(context, "Đã làm mới kết nối", Toast.LENGTH_SHORT).show()
                }
            }
            item { Spacer(Modifier.size(8.dp)) }
        }
    }
}

@Composable
private fun GuideCard(
    icon: ImageVector,
    title: String,
    desc: String,
    action: String,
    onAction: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.size(8.dp))
            OutlinedButton(onClick = onAction) { Text(action) }
        }
    }
}
