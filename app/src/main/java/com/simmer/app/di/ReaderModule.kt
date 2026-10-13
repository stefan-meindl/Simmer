package com.simmer.app.di

import android.content.Context
import com.simmer.app.data.ReaderController
import com.simmer.app.reader.AndroidThermalMonitor
import com.simmer.app.reader.GemmaReader
import com.simmer.app.reader.GemmaReaderRegistry
import com.simmer.app.reader.LiteRtEngineFactory
import com.simmer.app.reader.NoNanoAvailability
import com.simmer.app.reader.ReaderSelector
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReaderModule {

    /** The one [GemmaReader] (and so one engine) for the app's lifetime. */
    @Provides
    @Singleton
    fun provideGemmaReader(
        @ApplicationContext context: Context,
        controller: ReaderController,
        registry: GemmaReaderRegistry,
    ): GemmaReader =
        GemmaReader(
            source = controller,
            factory = LiteRtEngineFactory(context),
            thermal = AndroidThermalMonitor(context),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            engineDispatcher = Dispatchers.IO,
        ).also { registry.reader = it }

    @Provides
    @Singleton
    fun provideReaderSelector(gemma: GemmaReader): ReaderSelector =
        ReaderSelector(availability = NoNanoAvailability(), nano = null, gemma = gemma)
}
