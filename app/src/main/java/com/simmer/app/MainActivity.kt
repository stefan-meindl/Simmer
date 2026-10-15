package com.simmer.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.simmer.app.data.ShareInbox
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.simmer.app.ui.MainScreen

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    /** True when a notification asked to open Settings; cleared once the screen has navigated. */
    private var openSettings by mutableStateOf(false)

    /** True when a notification asked to open Add recipe; cleared once the screen has navigated. */
    private var openAddRecipe by mutableStateOf(false)

    @Inject lateinit var shareInbox: ShareInbox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only a fresh launch counts; after a config change or process restore the intent is stale.
        if (savedInstanceState == null) {
            openSettings = intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)
            openAddRecipe = intent.getBooleanExtra(EXTRA_OPEN_ADD_RECIPE, false)
            acceptShare(intent)
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
        acceptShare(intent)
    }

    /** Text shared to Simmer (an Instagram link, or any text) goes to Add recipe. */
    private fun acceptShare(intent: Intent) {
        if (intent.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() } ?: return
        shareInbox.offer(text)
        openAddRecipe = true
    }

    companion object {
        /** Set by the model download notification: tapping it opens the Settings tab. */
        const val EXTRA_OPEN_SETTINGS = "open_settings"

        /** Set by the Pipeline notifications: tapping one opens Add recipe (Working, Review or error stage). */
        const val EXTRA_OPEN_ADD_RECIPE = "open_add_recipe"
    }
}
