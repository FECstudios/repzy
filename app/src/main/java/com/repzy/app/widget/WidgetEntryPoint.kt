package com.repzy.app.widget

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Glance widget'ları Hilt'in enjekte edebildiği bir sınıf değil (sistem yaratıyor),
 * bu yüzden bağımlılığa EntryPoint üzerinden ulaşıyoruz.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {

    fun widgetSnapshotStore(): WidgetSnapshotStore

    companion object {
        fun resolve(context: Context): WidgetEntryPoint =
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                WidgetEntryPoint::class.java,
            )
    }
}
