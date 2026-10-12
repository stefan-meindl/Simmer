package com.simmer.app.di

import android.content.Context
import com.simmer.app.domain.StepTimerStore
import com.simmer.app.timer.AndroidTimerHost
import com.simmer.app.timer.SharedPrefsTimerPersistence
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TimerModule {

    @Provides
    @Singleton
    fun provideStepTimerStore(@ApplicationContext context: Context): StepTimerStore =
        StepTimerStore(
            clock = System::currentTimeMillis,
            persistence = SharedPrefsTimerPersistence(context),
            host = AndroidTimerHost(context),
        )
}
