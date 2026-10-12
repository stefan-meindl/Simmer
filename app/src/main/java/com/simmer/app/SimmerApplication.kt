package com.simmer.app

import android.app.Application
import com.simmer.app.data.RecipeRepository
import com.simmer.app.timer.TimerNotifications
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class SimmerApplication : Application() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AppEntryPoint {
        fun recipeRepository(): RecipeRepository
    }

    override fun onCreate() {
        super.onCreate()
        TimerNotifications.createChannels(this)
        val repo = EntryPointAccessors.fromApplication(this, AppEntryPoint::class.java).recipeRepository()
        MainScope().launch {
            repo.seedBuiltInsIfEmpty(assets)
        }
    }
}
