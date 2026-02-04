package com.ghost.tagger.di


import com.ghost.tagger.core.ModelManager
import com.ghost.tagger.data.models.settings.SettingsManager
import com.ghost.tagger.data.repository.GalleryRepository
import com.ghost.tagger.data.repository.ImageRepository
import com.ghost.tagger.data.repository.SettingsRepository
import com.ghost.tagger.ui.viewmodels.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

// Define appModule as a top-level val so 'main.kt' can see it
val appModule = module {
    single { SettingsManager("GhostAITagger") }
    single { SettingsRepository(get(), CoroutineScope(SupervisorJob() + Dispatchers.IO)) }
    single { GalleryRepository() }
    single { ImageRepository(get(), get()) }


    single {
        SettingsRepository(
            manager = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        )
    }
    factory { SettingsViewModel(get()) }
    factory { GalleryViewModel(get(), get()) }
    factory { MainViewModel(get()) }
    factory { TaggerViewModel(get()) }
    factory { ApiKeyViewModel(get()) }
    factory { ImageDetailViewModel(ModelManager, get(), get()) }
    factory { BatchDetailViewModel(ModelManager, get(), get()) }
}