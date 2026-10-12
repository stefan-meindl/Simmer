package com.simmer.app.di

import com.simmer.app.data.RecipeRepository
import com.simmer.app.data.RecipeWriter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class EditModule {
    @Binds
    abstract fun bindRecipeWriter(repository: RecipeRepository): RecipeWriter
}
