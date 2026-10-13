package com.simmer.app.di

import android.content.Context
import com.simmer.app.data.ImportJob
import com.simmer.app.data.ImportRunner
import com.simmer.app.data.RecipeDraftStore
import com.simmer.app.domain.RecipePipeline
import com.simmer.app.pipeline.AndroidImportHost
import com.simmer.app.pipeline.SharedPrefsImportPersistence
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
object PipelineModule {

    /** The one Pipeline job for the app's lifetime; its own scope, so leaving the screen does not stop it. */
    @Provides
    @Singleton
    fun provideImportJob(
        @ApplicationContext context: Context,
        selector: ReaderSelector,
        drafts: RecipeDraftStore,
    ): ImportJob = ImportJob(
        runner = ImportRunner { text, rule, lang, id, onStage ->
            RecipePipeline(selector.current()).run(text, rule, lang, id, onStage)
        },
        drafts = drafts,
        persistence = SharedPrefsImportPersistence(context),
        host = AndroidImportHost(context),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        newId = { "u" + System.currentTimeMillis().toString(36) },
    )
}
