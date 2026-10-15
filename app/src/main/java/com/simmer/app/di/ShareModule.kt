package com.simmer.app.di

import com.simmer.app.data.CaptionFetcher
import com.simmer.app.data.InstagramCaptionFetcher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ShareModule {
    @Binds
    @Singleton
    abstract fun bindCaptionFetcher(fetcher: InstagramCaptionFetcher): CaptionFetcher
}
