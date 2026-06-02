package com.loaloaloa.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Platform entry point for [LoaLoaLoaWidget]. Registered in the manifest with the
 * APPWIDGET_UPDATE intent-filter and the @xml/loaloaloa_widget_info metadata. The
 * widget itself is refreshed on demand from
 * [com.loaloaloa.ingest.TransactionIngestor] via `updateAll`, so the provider
 * info uses `updatePeriodMillis=0`.
 */
class LoaLoaLoaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LoaLoaLoaWidget()
}
