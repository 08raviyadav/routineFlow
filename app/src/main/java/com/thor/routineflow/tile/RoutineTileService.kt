package com.thor.routineflow.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.thor.routineflow.RoutineFlowApp
import com.thor.routineflow.service.EngineService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Quick Settings tile: one tap runs the favourite routine. When no favourite
 * is set the tile is inactive and tapping it opens the app instead.
 */
class RoutineTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val routine = favouriteRoutine()
            withContext(Dispatchers.Main) {
                if (routine != null) {
                    EngineService.runRoutineNow(applicationContext, routine.id)
                    Toast.makeText(
                        applicationContext,
                        "Running ${routine.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    val intent = packageManager.getLaunchIntentForPackage(packageName)
                    if (intent != null) {
                        startActivityAndCollapse(intent)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun refreshTile() {
        scope.launch {
            val routine = favouriteRoutine()
            withContext(Dispatchers.Main) {
                val tile = qsTile ?: return@withContext
                tile.label = routine?.name ?: "RoutineFlow"
                tile.state = if (routine != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                tile.updateTile()
            }
        }
    }

    private suspend fun favouriteRoutine(): com.thor.routineflow.model.Routine? {
        val container = (application as RoutineFlowApp).container
        val favouriteId = runCatching { container.preferences.getFavouriteRoutineId() }
            .getOrNull() ?: return null
        return runCatching { container.routineRepository.getById(favouriteId) }.getOrNull()
    }
}
