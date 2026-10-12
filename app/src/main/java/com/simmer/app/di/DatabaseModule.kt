package com.simmer.app.di

import android.content.Context
import androidx.room.Room
import com.simmer.app.data.AppDatabase
import com.simmer.app.data.MIGRATION_1_2
import com.simmer.app.data.RecipeDao
import com.simmer.app.data.ShoppingDao
import com.simmer.app.domain.CookingProgressStore
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
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideCookingProgress(): CookingProgressStore = CookingProgressStore()

    @Provides
    fun provideRecipeDao(db: AppDatabase): RecipeDao = db.recipeDao()

    @Provides
    fun provideShoppingDao(db: AppDatabase): ShoppingDao = db.shoppingDao()
}
