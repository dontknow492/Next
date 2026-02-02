package com.ghost.tagger.di


import com.ghost.tagger.data.models.settings.SettingsManager
import com.ghost.tagger.data.repository.GalleryRepository
import com.ghost.tagger.data.repository.SettingsRepository
import com.ghost.tagger.data.viewmodels.ApiKeyViewModel
import com.ghost.tagger.data.viewmodels.GalleryViewModel
import com.ghost.tagger.data.viewmodels.MainViewModel
import com.ghost.tagger.data.viewmodels.SettingsViewModel
import com.ghost.tagger.data.viewmodels.TaggerViewModel
import org.koin.dsl.module
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

// Define appModule as a top-level val so 'main.kt' can see it
val appModule = module {
    single { SettingsManager("GhostAITagger") }
    single { SettingsRepository(get(), CoroutineScope(SupervisorJob() + Dispatchers.IO)) }
    single { GalleryRepository() }


    single {
        SettingsRepository(
            manager = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        )
    }
    factory { SettingsViewModel(get()) }
    factory { GalleryViewModel(get(), get()) }
    factory { MainViewModel(get()) }
    factory { TaggerViewModel( get() ) }
    factory { ApiKeyViewModel(get()) }
}