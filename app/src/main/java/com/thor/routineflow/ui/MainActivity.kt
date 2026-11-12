package com.thor.routineflow.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.thor.routineflow.RoutineFlowApp
import com.thor.routineflow.ui.theme.RoutineFlowTheme

/**
 * Single-activity host for the whole app. Forwards ACTION_VIEW intents for a
 * shared .json backup file to the navigation graph, which routes them to
 * Settings where the import dialog lives.
 */
class MainActivity : ComponentActivity() {

    /** Uri string of a backup file shared into the app, awaiting import. */
    private var pendingImportUri by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleImportIntent(intent)
        val container = (application as RoutineFlowApp).container
        setContent {
            RoutineFlowTheme {
                RoutineFlowNav(
                    container = container,
                    pendingImportUri = pendingImportUri,
                    onImportConsumed = { pendingImportUri = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleImportIntent(intent)
    }

    private fun handleImportIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
            pendingImportUri = intent.data.toString()
        }
    }
}
