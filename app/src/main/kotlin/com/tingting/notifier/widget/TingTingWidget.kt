package com.tingting.notifier.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.tingting.notifier.MainActivity
import com.tingting.notifier.R
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first

/**
 * Home-screen widget rendering the design in /tmp/widget_full.png: a teal rounded card
 * with the brand row (megaphone + "TingTing" + green status dot when the service is on),
 * a large "Hôm nay" income hero, and the latest transaction line + time.
 *
 * Glance widgets are not `@AndroidEntryPoint`, so data is loaded inside [provideGlance]
 * via the Hilt [WidgetEntryPoint]. All display strings come from the pure
 * [WidgetState] mapper so this file is only framework glue.
 */
class TingTingWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java,
        )
        val records = entryPoint.transactionRepository().observeRecords().first()
        val serviceEnabled = entryPoint.userSettingsRepository().settings.first().enableService
        val zone = ZoneId.systemDefault()
        val state = WidgetState.from(
            records = records,
            today = LocalDate.now(zone),
            zone = zone,
            serviceEnabled = serviceEnabled,
        )

        provideContent {
            GlanceTheme {
                Content(state)
            }
        }
    }

    private companion object {
        val Teal = Color(0xFF0EA5A4)
        val White = Color(0xFFFFFFFF)
        val GreenDot = Color(0xFF82FF99)
        val WhiteFaint = Color(0x33FFFFFF) // white @ 20%
        val WhiteSoft = Color(0xCCFFFFFF) // white @ 80%
    }

    @androidx.compose.runtime.Composable
    private fun Content(state: WidgetState) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Teal)
                .cornerRadius(24.dp)
                .padding(16.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            BrandRow(state.serviceEnabled)
            Spacer(GlanceModifier.height(10.dp))
            TodayHero(state.todayTotal)
            Spacer(GlanceModifier.height(8.dp))
            LatestRow(state)
        }
    }

    @androidx.compose.runtime.Composable
    private fun BrandRow(serviceEnabled: Boolean) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = GlanceModifier
                    .size(24.dp)
                    .background(WhiteFaint)
                    .cornerRadius(6.dp)
                    .padding(3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.widget_megaphone),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize(),
                )
            }
            Spacer(GlanceModifier.width(8.dp))
            Text(
                text = "TingTing",
                style = TextStyle(
                    color = ColorProvider(White),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(GlanceModifier.defaultWeight())
            if (serviceEnabled) {
                StatusPill()
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun StatusPill() {
        Row(
            modifier = GlanceModifier
                .background(WhiteFaint)
                .cornerRadius(12.dp)
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = GlanceModifier
                    .size(8.dp)
                    .background(GreenDot)
                    .cornerRadius(4.dp),
            ) {}
            Spacer(GlanceModifier.width(5.dp))
            Text(
                text = "Đang hoạt động",
                style = TextStyle(color = ColorProvider(WhiteSoft), fontSize = 12.sp),
            )
        }
    }

    @androidx.compose.runtime.Composable
    private fun TodayHero(todayTotal: String) {
        Column {
            Text(
                text = "HÔM NAY",
                style = TextStyle(color = ColorProvider(WhiteSoft), fontSize = 12.sp),
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = todayTotal,
                style = TextStyle(
                    color = ColorProvider(White),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }

    @androidx.compose.runtime.Composable
    private fun LatestRow(state: WidgetState) {
        val line = state.latestLine ?: "Chưa có giao dịch"
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = GlanceModifier
                    .size(20.dp)
                    .background(White)
                    .cornerRadius(10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (state.latestLine != null) state.latestBadge else "",
                    style = TextStyle(
                        color = ColorProvider(Teal),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
            Spacer(GlanceModifier.width(8.dp))
            Text(
                text = line,
                maxLines = 1,
                style = TextStyle(color = ColorProvider(WhiteSoft), fontSize = 13.sp),
                modifier = GlanceModifier.defaultWeight(),
            )
            state.latestTime?.let { time ->
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    text = time,
                    style = TextStyle(color = ColorProvider(WhiteSoft), fontSize = 12.sp),
                )
            }
        }
    }
}
