package com.loaloaloa.ui.onboarding

import android.content.ActivityNotFoundException
import android.content.Context
import android.os.Build
import android.widget.Toast
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loaloaloa.permission.BatteryOptimizationHelper
import com.loaloaloa.permission.NotificationAccessHelper
import com.loaloaloa.permission.OemAutostart
import com.loaloaloa.ui.util.OnResume

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    fun refresh() = viewModel.refresh(
        notificationAccessGranted = NotificationAccessHelper.isGranted(context),
        batteryExempt = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context),
        autostartAvailable = OemAutostart.autostartIntent(Build.MANUFACTURER) != null,
    )

    OnResume { refresh() }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(80.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Campaign, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(40.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Thiết lập Loa Loa Loa", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Loa Loa Loa đọc nội dung thông báo giao dịch từ ứng dụng ngân hàng của bạn để " +
                    "đọc to số tiền trên loa. Nếu bạn bật chia sẻ, máy shop gửi giao dịch đã " +
                    "mã hoá đầu-cuối tới máy nhân viên đã ghép. Dữ liệu được xử lý trên máy; máy " +
                    "chủ trung gian không đọc được nội dung. Bạn có thể tắt quyền bất cứ lúc nào.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = {
                launchIntentSafely(context, "Không mở được trình duyệt") {
                    android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://loaloaloa.haveuever.workers.dev/privacy"),
                    )
                }
            }) { Text("Chính sách quyền riêng tư") }
            Spacer(Modifier.height(24.dp))

            StepCard(
                number = 1,
                icon = Icons.Filled.Notifications,
                title = "Quyền đọc thông báo",
                subtitle = "Để nhận thông báo từ app ngân hàng",
                done = state.notificationAccessGranted,
                actionLabel = "Cấp quyền",
                primary = true,
                onAction = {
                    launchIntentSafely(context, "Không mở được cài đặt quyền thông báo") {
                        NotificationAccessHelper.settingsIntent()
                    }
                },
            )
            Spacer(Modifier.height(12.dp))
            StepCard(
                number = 2,
                icon = Icons.Filled.BatteryChargingFull,
                title = "Tắt tối ưu hoá pin",
                subtitle = "Giúp app chạy nền ổn định",
                done = state.batteryExempt,
                actionLabel = "Thiết lập",
                primary = true,
                onAction = {
                    launchIntentSafely(context, "Thiết bị không hỗ trợ cài đặt này") {
                        BatteryOptimizationHelper.requestIntent(context.packageName)
                    }
                },
            )
            Spacer(Modifier.height(12.dp))
            StepCard(
                number = 3,
                icon = Icons.Filled.RocketLaunch,
                title = "Cho phép tự khởi động",
                subtitle = "Tuỳ hãng máy (Xiaomi, Oppo, Vivo...)",
                done = false,
                actionLabel = "Mở cài đặt",
                primary = false,
                enabled = state.autostartAvailable,
                onAction = {
                    launchIntentSafely(context, "Không tìm thấy cài đặt tự khởi động trên máy này") {
                        OemAutostart.autostartIntent(Build.MANUFACTURER)
                    }
                },
            )

            Spacer(Modifier.weight(1f))
            Button(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = state.canFinish,
            ) {
                Text("Hoàn tất", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StepCard(
    number: Int,
    icon: ImageVector,
    title: String,
    subtitle: String,
    done: Boolean,
    actionLabel: String,
    primary: Boolean,
    enabled: Boolean = true,
    onAction: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(32.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text("$number", style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                if (done) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Đã xong", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
                    }
                } else if (primary) {
                    Button(onClick = onAction, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(actionLabel) }
                } else {
                    OutlinedButton(onClick = onAction, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(actionLabel) }
                }
            }
        }
    }
}

/** Launch an Intent provided by [provider], swallowing [ActivityNotFoundException] with a Toast fallback. */
internal fun launchIntentSafely(context: Context, fallbackMessage: String, provider: () -> android.content.Intent?) {
    val intent = provider()
    if (intent == null) {
        Toast.makeText(context, fallbackMessage, Toast.LENGTH_SHORT).show()
        return
    }
    try {
        context.startActivity(intent.apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) })
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, fallbackMessage, Toast.LENGTH_SHORT).show()
    }
}
