package com.simmer.app.di

import android.content.Context
import android.content.res.Configuration
import com.simmer.app.R
import com.simmer.app.data.ImportJob
import com.simmer.app.data.ImportRunner
import com.simmer.app.data.RecipeDraftStore
import com.simmer.app.domain.RecipePipeline
import com.simmer.app.domain.newRecipeId
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
import java.util.Locale
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
        runner = ImportRunner { request, id, onStage ->
            RecipePipeline(com.simmer.app.pipeline.FakeScriptedReader()).run(request, id, truncatedNote(context, request.lang), onStage)
        },
        drafts = drafts,
        persistence = SharedPrefsImportPersistence(context),
        host = AndroidImportHost(context),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        newId = { newRecipeId() },
    )

    /** The "This recipe is long" note in the app language, which may differ from the device's. */
    private fun truncatedNote(context: Context, lang: String): String {
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale(lang)) }
        return context.createConfigurationContext(config).getString(R.string.import_truncated_note)
    }
}
