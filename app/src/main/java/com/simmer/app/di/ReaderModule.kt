package com.simmer.app.di

import android.content.Context
import com.simmer.app.data.ReaderController
import com.simmer.app.reader.AndroidThermalMonitor
import com.simmer.app.reader.GemmaReader
import com.simmer.app.reader.GemmaReaderRegistry
import com.simmer.app.reader.LiteRtEngineFactory
import com.simmer.app.reader.GeminiNanoReader
import com.simmer.app.reader.MlKitNanoClient
import com.simmer.app.reader.NanoClient
import com.simmer.app.reader.ReaderSelector
import com.simmer.app.reader.ReaderStateSource
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
    fun provideNanoClient(): NanoClient = MlKitNanoClient()

    @Provides
    @Singleton
    fun provideGeminiNanoReader(client: NanoClient): GeminiNanoReader = GeminiNanoReader(client)

    /** Gemini Nano where ML Kit reports it Available or Downloadable, Gemma everywhere else. */
    @Provides
    @Singleton
    fun provideReaderSelector(client: NanoClient, nano: GeminiNanoReader, gemma: GemmaReader): ReaderSelector =
        ReaderSelector(availability = { client.availability() }, nano = nano, gemma = gemma)

    @Provides
    @Singleton
    fun provideReaderStateSource(selector: ReaderSelector, controller: ReaderController): ReaderStateSource =
        // App-lifetime scope: a Gemini Nano download must outlive the Settings screen.
        ReaderStateSource(selector, controller, CoroutineScope(SupervisorJob() + Dispatchers.Default))
}
