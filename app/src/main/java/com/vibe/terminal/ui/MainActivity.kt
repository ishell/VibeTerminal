package com.vibe.terminal.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.os.LocaleListCompat
import androidx.navigation.compose.rememberNavController
import com.vibe.terminal.data.preferences.UserPreferences
import com.vibe.terminal.ui.navigation.VibeNavHost
import com.vibe.terminal.ui.theme.VibeTerminalTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var userPreferences: UserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            // Observe language changes and apply locale
            val language by userPreferences.language.collectAsState(initial = UserPreferences.LANGUAGE_SYSTEM)

            LaunchedEffect(language) {
                applyLocale(language)
            }

            VibeTerminalTheme(darkTheme = true) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    VibeNavHost(navController = navController)
                }
            }
        }
    }

    private fun applyLocale(language: String) {
        val localeList = when (language) {
            UserPreferences.LANGUAGE_EN -> LocaleListCompat.forLanguageTags("en")
            UserPreferences.LANGUAGE_ZH -> LocaleListCompat.forLanguageTags("zh")
            else -> LocaleListCompat.getEmptyLocaleList() // System default
        }
        AppCompatDelegate.setApplicationLocales(localeList)
    }
}
