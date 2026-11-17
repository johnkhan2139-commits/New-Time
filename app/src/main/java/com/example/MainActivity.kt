package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.preferences.TimePreferences
import com.example.ui.navigation.TimeAppShell
import com.example.ui.theme.TimeTheme
import com.google.firebase.FirebaseApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { FirebaseApp.initializeApp(this) }
        enableEdgeToEdge()
        setContent {
            val preferences = remember { TimePreferences(applicationContext) }
            val isDarkTheme by preferences.isDarkTheme.collectAsStateWithLifecycle(initialValue = true)
            TimeTheme(darkTheme = isDarkTheme) {
                TimeAppShell(preferences = preferences)
            }
        }
    }
}
