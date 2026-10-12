package com.simmer.app.di

import android.content.Context
import androidx.room.Room
import com.simmer.app.data.AppDatabase
import com.simmer.app.data.RecipeDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "simmer.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideRecipeDao(db: AppDatabase): RecipeDao = db.recipeDao()
}
