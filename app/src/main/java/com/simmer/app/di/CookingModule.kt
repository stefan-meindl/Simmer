package com.simmer.app.di

import android.content.Context
import com.simmer.app.data.SharedPrefsCookingPersistence
import com.simmer.app.domain.CookingProgressStore
import com.simmer.app.domain.CookingSessions
import com.simmer.app.domain.StepTimerStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CookingModule {

    @Provides
    @Singleton
    fun provideCookingProgressStore(@ApplicationContext context: Context): CookingProgressStore =
        CookingProgressStore(SharedPrefsCookingPersistence(context))

    @Provides
    @Singleton
    fun provideCookingSessions(progress: CookingProgressStore, timers: StepTimerStore): CookingSessions =
        CookingSessions(progress, timers)
}
