package com.tabletnotes.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.tabletnotes.app.core.designsystem.theme.TabletNotesTheme
import com.tabletnotes.app.core.designsystem.theme.ThemePreset
import com.tabletnotes.app.ui.navigation.TabletNotesShell
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            var currentTheme by rememberSaveable {
                mutableStateOf(ThemePreset.NIGHT_DARK)
            }

            TabletNotesTheme(preset = currentTheme) {
                TabletNotesShell(
                    currentTheme = currentTheme,
                    onCycleTheme = { currentTheme = currentTheme.next() },
                    onSelectTheme = { currentTheme = it }
                )
            }
        }
    }
}
