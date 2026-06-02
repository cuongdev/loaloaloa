package com.loaloaloa.ui.report

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loaloaloa.ui.components.BankBadge
import com.loaloaloa.ui.theme.LocalAppExtraColors
import com.loaloaloa.ui.theme.MoneyHeroStyle
import com.loaloaloa.ui.util.MoneyFormat
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(viewModel: ReportViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.exports.collect { data ->
            if (data.rowCount == 0) {
                Toast.makeText(context, "Chưa có giao dịch trong kỳ này để xuất", Toast.LENGTH_SHORT).show()
            } else {
                shareCsv(context, data.fileName, data.content)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Báo cáo", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    if (state.hasData) {
                        IconButton(onClick = viewModel::exportCsv) {
                            Icon(
                                imageVector = Icons.Filled.FileDownload,
                                contentDescription = "Xuất báo cáo CSV",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
            )
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

            PeriodChips(selected = state.period, onSelect = viewModel::selectPeriod)

            if (!state.hasData) {
                EmptyState()
            } else {
                HeroCard(period = state.period, total = state.periodTotal, count = state.periodCount)
                GrowthSection(cards = state.growth)
                TrendSection(state)
                InsightsSection(state)
                PerBankCard(slices = state.perBank)
                RecordsCard(records = state.records)
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Write [content] to a cached file and open the system share sheet with a content:// URI. */
private fun shareCsv(context: Context, fileName: String, content: String) {
    try {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(content, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, "Chia sẻ báo cáo").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (e: Exception) {
        Toast.makeText(context, "Không xuất được báo cáo", Toast.LENGTH_SHORT).show()
    }
}

private fun periodLabel(period: ReportPeriod): String = when (period) {
    ReportPeriod.TODAY -> "Hôm nay"
    ReportPeriod.WEEK -> "Tuần này"
    ReportPeriod.MONTH -> "Tháng này"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodChips(selected: ReportPeriod, onSelect: (ReportPeriod) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ReportPeriod.entries.forEach { period ->
            FilterChip(
                selected = period == selected,
                onClick = { onSelect(period) },
                label = { Text(periodLabel(period)) },
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Chưa có dữ liệu", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Báo cáo sẽ xuất hiện khi có giao dịch đến.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun HeroCard(period: ReportPeriod, total: Long, count: Int) {
    val extra = LocalAppExtraColors.current
    DashCard {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(periodLabel(period), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(MoneyFormat.format(total, true), style = MoneyHeroStyle, color = extra.income)
            Spacer(Modifier.height(4.dp))
            Text("$count giao dịch", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GrowthSection(cards: List<GrowthCard>) {
    if (cards.isEmpty()) return
    DashCard {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            SectionTitle("Tăng trưởng", Icons.AutoMirrored.Filled.TrendingUp)
            Spacer(Modifier.height(12.dp))
            cards.forEachIndexed { i, card ->
                if (i > 0) Spacer(Modifier.height(12.dp))
                GrowthRow(card)
            }
        }
    }
}

@Composable
private fun GrowthRow(card: GrowthCard) {
    val extra = LocalAppExtraColors.current
    val (icon, text, color) = when (card.status) {
        GrowthStatus.UP -> Triple(Icons.AutoMirrored.Filled.TrendingUp, "${card.deltaPercent}%", extra.income)
        GrowthStatus.DOWN -> Triple(Icons.AutoMirrored.Filled.TrendingDown, "${kotlin.math.abs(card.deltaPercent ?: 0)}%", extra.outgoing)
        GrowthStatus.FLAT -> Triple(Icons.AutoMirrored.Filled.TrendingFlat, "0%", MaterialTheme.colorScheme.onSurfaceVariant)
        GrowthStatus.NEW -> Triple(Icons.AutoMirrored.Filled.TrendingUp, "Mới", extra.income)
        GrowthStatus.NONE -> Triple(null, "—", MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(card.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(MoneyFormat.plain(card.currentAmount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun TrendSection(state: ReportUiState) {
    val extra = LocalAppExtraColors.current
    DashCard {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle("Xu hướng · ${periodLabel(state.period)}", Icons.Filled.BarChart)
                Text(MoneyFormat.plain(state.periodTotal), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            BarChart(state.periodBars, MaterialTheme.colorScheme.primary, extra.income)

            Spacer(Modifier.height(20.dp))
            SectionTitle("6 tháng qua")
            Spacer(Modifier.height(16.dp))
            BarChart(state.monthlyTrend, MaterialTheme.colorScheme.primary, extra.income)
        }
    }
}

@Composable
private fun InsightsSection(state: ReportUiState) {
    val extra = LocalAppExtraColors.current

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard(Modifier.weight(1f), "Giá trị TB/giao dịch", MoneyFormat.plain(state.averageTicket), Icons.Filled.Payments)
        StatCard(Modifier.weight(1f), "Số giao dịch", "${state.periodCount}", Icons.AutoMirrored.Filled.ReceiptLong)
    }

    // Peak hours
    val topHour = state.peakHours.maxByOrNull { it.income }?.takeIf { it.income > 0 }?.hour
    DashCard {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            SectionTitle("Giờ cao điểm", Icons.Filled.Schedule)
            if (state.peakHourHeadline != null) {
                Spacer(Modifier.height(4.dp))
                Text(state.peakHourHeadline, style = MaterialTheme.typography.bodyMedium, color = extra.income, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(16.dp))
            BarChart(
                bars = state.peakHours.map { h ->
                    ChartBar(label = if (h.hour % 6 == 0) "${h.hour}h" else "", value = h.income, highlight = h.hour == topHour)
                },
                barColor = MaterialTheme.colorScheme.primary,
                highlightColor = extra.income,
                height = 120,
            )
        }
    }

    // Busiest weekday
    val topWeekdayIdx = state.busiestWeekday.indices.maxByOrNull { state.busiestWeekday[it].income }
        ?.takeIf { state.busiestWeekday[it].income > 0 }
    DashCard {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            SectionTitle("Ngày bận nhất", Icons.Filled.CalendarMonth)
            if (state.busiestWeekdayHeadline != null) {
                Spacer(Modifier.height(4.dp))
                Text(state.busiestWeekdayHeadline, style = MaterialTheme.typography.bodyMedium, color = extra.income, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(16.dp))
            BarChart(
                bars = state.busiestWeekday.mapIndexed { i, w ->
                    ChartBar(label = w.label, value = w.income, highlight = i == topWeekdayIdx)
                },
                barColor = MaterialTheme.colorScheme.primary,
                highlightColor = extra.income,
                height = 120,
            )
        }
    }
}

@Composable
private fun PerBankCard(slices: List<BankSlice>) {
    if (slices.isEmpty()) return
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = MaterialTheme.colorScheme.primary
    DashCard {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            SectionTitle("Phân bổ theo ngân hàng/ví", Icons.Filled.PieChart)
            slices.forEach { slice ->
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BankBadge(name = slice.bankName, size = 32)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(slice.bankName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
                            Text(MoneyFormat.plain(slice.income), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(6.dp))
                        PercentBar(fraction = slice.percent / 100f, track = track, fill = fill)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("${slice.percent}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun RecordsCard(records: RecordsInfo) {
    DashCard {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            SectionTitle("Kỷ lục", Icons.Filled.EmojiEvents)
            Spacer(Modifier.height(12.dp))
            RecordRow("Doanh thu ngày cao nhất", records.bestDayLabel?.let { "$it · ${MoneyFormat.plain(records.bestDayAmount)}" } ?: "—")
            Spacer(Modifier.height(10.dp))
            RecordRow("Giao dịch lớn nhất", MoneyFormat.plain(records.biggestTransaction))
        }
    }
}

@Composable
private fun RecordRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

// --- shared building blocks --------------------------------------------------

@Composable
private fun DashCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) { content() }
}

@Composable
private fun SectionTitle(text: String, icon: ImageVector? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatCard(modifier: Modifier, label: String, value: String, icon: ImageVector? = null) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PercentBar(fraction: Float, track: Color, fill: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(track),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(fill),
        )
    }
}

@Composable
private fun BarChart(
    bars: List<ChartBar>,
    barColor: Color,
    highlightColor: Color,
    height: Int = 160,
) {
    val labelStyle = MaterialTheme.typography.labelSmall
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val maxValue = (bars.maxOfOrNull { it.value } ?: 0L).coerceAtLeast(1L)

    Column {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height.dp),
        ) {
            val n = bars.size.coerceAtLeast(1)
            val slot = size.width / n
            val barWidth = slot * 0.42f
            val chartHeight = size.height
            bars.forEachIndexed { i, bar ->
                val h = (bar.value.toFloat() / maxValue.toFloat()) * (chartHeight - 4f)
                val left = slot * i + (slot - barWidth) / 2f
                val top = chartHeight - h
                drawRoundRect(
                    color = if (bar.highlight) highlightColor else barColor,
                    topLeft = Offset(left, top),
                    size = Size(barWidth, h.coerceAtLeast(2f)),
                    cornerRadius = CornerRadius(6f, 6f),
                )
            }
            drawLine(
                color = axisColor,
                start = Offset(0f, chartHeight),
                end = Offset(size.width, chartHeight),
                strokeWidth = 2f,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            bars.forEach { bar ->
                Text(
                    bar.label,
                    style = labelStyle,
                    color = labelColor,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
