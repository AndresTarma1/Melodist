package com.example.musicApp.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf


// Interesado en implementarlo en un futuro
val LocalAppLocale = compositionLocalOf { "en" }

@Composable
fun ProvideAppLocale(locale: String, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppLocale provides locale) {
        content()
    }
}