package com.maho_ya.learningmvi

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
internal object ViewModelMovieModule {
    @Provides
    @ViewModelScoped
    fun provideBarcodeProcessor() =
        BarcodeProcessor()
}
