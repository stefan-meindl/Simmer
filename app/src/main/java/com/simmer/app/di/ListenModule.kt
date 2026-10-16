package com.simmer.app.di

import android.content.Context
import com.simmer.app.data.ListenModel
import com.simmer.app.data.ListenSession
import com.simmer.app.data.WhisperModelController
import com.simmer.app.listen.PlaybackAudioSource
import com.simmer.app.listen.SharedPrefsListenPersistence
import com.simmer.app.listen.WhisperTranscriber
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
object ListenModule {

    @Provides
    fun provideListenModel(controller: WhisperModelController): ListenModel = controller

    /** The one Listen run for the app's lifetime; its own scope, so leaving the screen does not stop it. */
    @Provides
    @Singleton
    fun provideListenSession(
        @ApplicationContext context: Context,
        audio: PlaybackAudioSource,
        model: WhisperModelController,
    ): ListenSession = ListenSession(
        audio = audio,
        transcriber = WhisperTranscriber { model.modelPath() },
        model = model,
        persistence = SharedPrefsListenPersistence(context),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )
}
