package com.tingting.notifier.widget

import com.tingting.notifier.data.repository.TransactionRepository
import com.tingting.notifier.data.repository.UserSettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt access seam for the Glance widget. Glance [GlanceAppWidget]s are instantiated
 * by the platform (via [TingTingWidgetReceiver]) and cannot be `@AndroidEntryPoint`, so
 * the widget pulls its singletons through this [EntryPoint] with
 * `EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)`.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun transactionRepository(): TransactionRepository
    fun userSettingsRepository(): UserSettingsRepository
}
