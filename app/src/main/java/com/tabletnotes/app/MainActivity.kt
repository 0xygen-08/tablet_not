package com.tabletnotes.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tabletnotes.app.core.designsystem.theme.TabletNotesTheme
import com.tabletnotes.app.feature.editor.ui.NotesWorkspaceViewModel
import com.tabletnotes.app.ui.navigation.TabletNotesShell
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val workspaceViewModel: NotesWorkspaceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyImmersiveSystemBars()

        setContent {
            var currentTheme by rememberSaveable {
                mutableStateOf(workspaceViewModel.getSavedThemePreset())
            }

            TabletNotesTheme(preset = currentTheme) {
                TabletNotesShell(
                    currentTheme = currentTheme,
                    onCycleTheme = {
                        val next = currentTheme.next()
                        currentTheme = next
                        workspaceViewModel.saveThemePreset(next)
                    },
                    onSelectTheme = { preset ->
                        currentTheme = preset
                        workspaceViewModel.saveThemePreset(preset)
                    },
                    workspaceViewModel = workspaceViewModel
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyImmersiveSystemBars()
        }
    }

    override fun onPause() {
        workspaceViewModel.flushPendingSaveImmediate()
        super.onPause()
    }

    override fun onStop() {
        workspaceViewModel.flushPendingSaveImmediate()
        super.onStop()
    }

    private fun applyImmersiveSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.navigationBars())
    }
}
