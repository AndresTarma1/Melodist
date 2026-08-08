package example.nucleus.di

import example.nucleus.data.local.createDataStore
import example.nucleus.data.repository.JvmConfigRepository
import example.nucleus.data.repository.UserPreferencesRepository
import org.koin.dsl.module

val dataStoreModule = module {
    single { createDataStore() }
    single { UserPreferencesRepository(get()) }
    single { JvmConfigRepository(get()) }
}