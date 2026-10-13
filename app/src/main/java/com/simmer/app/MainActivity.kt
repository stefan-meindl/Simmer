package com.simmer.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dagger.hilt.android.AndroidEntryPoint
import com.simmer.app.ui.MainScreen

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    /** True when a notification asked to open Settings; cleared once the screen has navigated. */
    private var openSettings by mutableStateOf(false)

    /** True when a notification asked to open Add recipe; cleared once the screen has navigated. */
    private var openAddRecipe by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only a fresh launch counts; after a config change or process restore the intent is stale.
        if (savedInstanceState == null) {
            openSettings = intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)
            openAddRecipe = intent.getBooleanExtra(EXTRA_OPEN_ADD_RECIPE, false)
        }
        setContent {
            MainScreen(
                openSettings = openSettings,
                onSettingsOpened = { openSettings = false },
                openAddRecipe = openAddRecipe,
                onAddRecipeOpened = { openAddRecipe = false },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)) openSettings = true
        if (intent.getBooleanExtra(EXTRA_OPEN_ADD_RECIPE, false)) openAddRecipe = true
    }

    companion object {
        /** Set by the model download notification: tapping it opens the Settings tab. */
        const val EXTRA_OPEN_SETTINGS = "open_settings"

        /** Set by the Pipeline notifications: tapping one opens Add recipe (Working, Review or error stage). */
        const val EXTRA_OPEN_ADD_RECIPE = "open_add_recipe"
    }
}
