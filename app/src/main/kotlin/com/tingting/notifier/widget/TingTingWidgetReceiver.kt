package com.tingting.notifier.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Platform entry point for [TingTingWidget]. Registered in the manifest with the
 * APPWIDGET_UPDATE intent-filter and the @xml/tingting_widget_info metadata. The
 * widget itself is refreshed on demand from
 * [com.tingting.notifier.ingest.TransactionIngestor] via `updateAll`, so the provider
 * info uses `updatePeriodMillis=0`.
 */
class TingTingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TingTingWidget()
}
