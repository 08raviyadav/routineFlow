package com.thor.routineflow.admin

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import com.thor.routineflow.di.AppContainer
import com.thor.routineflow.model.ModeProfile

/**
 * Applies and reverts Mode settings. [activate] snapshots the current values
 * of every setting it is about to change into [ModeProfile.revertSnapshotJson]
 * (a simple `key=value;` string), applies the mode, and marks it active;
 * [deactivate] restores the snapshot and clears it.
 *
 * A setting whose permission is missing is left unchanged and skipped —
 * activation never fails or crashes because of one setting.
 */
object ModeApplier {

    private const val KEY_DND = "dnd"
    private const val KEY_RINGER = "ringer"
    private const val KEY_BRIGHTNESS = "brightness"
    private const val KEY_AUTO_BRIGHTNESS = "autoBrightness"
    private const val KEY_MEDIA_VOLUME = "mediaVolume"
    private const val KEY_RING_VOLUME = "ringVolume"
    private const val KEY_SCREEN_TIMEOUT = "screenTimeout"
    private const val KEY_AUTO_ROTATE = "autoRotate"

    suspend fun activate(context: Context, mode: ModeProfile, container: AppContainer): ModeProfile {
        val appContext = context.applicationContext
        val settings = mode.settings
        val snapshot = mutableMapOf<String, String>()

        if (settings.dnd != null) {
            currentDnd(appContext)?.let { snapshot[KEY_DND] = it.toString() }
            setDnd(appContext, settings.dnd)
        }
        if (settings.ringerMode != null) {
            currentRingerMode(appContext)?.let { snapshot[KEY_RINGER] = it.toString() }
            setRingerMode(appContext, settings.ringerMode)
        }
        if (settings.brightness != null) {
            currentBrightness(appContext)?.let { snapshot[KEY_BRIGHTNESS] = it.toString() }
            setBrightness(appContext, settings.brightness)
        }
        if (settings.autoBrightness != null) {
            currentAutoBrightness(appContext)?.let { snapshot[KEY_AUTO_BRIGHTNESS] = it.toString() }
            setAutoBrightness(appContext, settings.autoBrightness)
        }
        if (settings.mediaVolume != null) {
            currentVolume(appContext, AudioManager.STREAM_MUSIC)?.let {
                snapshot[KEY_MEDIA_VOLUME] = it.toString()
            }
            setVolumePercent(appContext, AudioManager.STREAM_MUSIC, settings.mediaVolume)
        }
        if (settings.ringVolume != null) {
            currentVolume(appContext, AudioManager.STREAM_RING)?.let {
                snapshot[KEY_RING_VOLUME] = it.toString()
            }
            setVolumePercent(appContext, AudioManager.STREAM_RING, settings.ringVolume)
        }
        if (settings.screenTimeoutMs != null) {
            currentScreenTimeout(appContext)?.let { snapshot[KEY_SCREEN_TIMEOUT] = it.toString() }
            setScreenTimeout(appContext, settings.screenTimeoutMs)
        }
        if (settings.autoRotate != null) {
            currentAutoRotate(appContext)?.let { snapshot[KEY_AUTO_ROTATE] = it.toString() }
            setAutoRotate(appContext, settings.autoRotate)
        }

        val updated = mode.copy(isActive = true, revertSnapshotJson = encodeSnapshot(snapshot))
        container.modeRepository.save(updated)
        return updated
    }

    suspend fun deactivate(context: Context, mode: ModeProfile, container: AppContainer): ModeProfile {
        val appContext = context.applicationContext
        val snapshot = decodeSnapshot(mode.revertSnapshotJson)
        snapshot[KEY_DND]?.toBooleanStrictOrNull()?.let { setDnd(appContext, it) }
        snapshot[KEY_RINGER]?.toIntOrNull()?.let { setRingerModeValue(appContext, it) }
        snapshot[KEY_BRIGHTNESS]?.toIntOrNull()?.let { setBrightness(appContext, it) }
        snapshot[KEY_AUTO_BRIGHTNESS]?.toBooleanStrictOrNull()?.let { setAutoBrightness(appContext, it) }
        snapshot[KEY_MEDIA_VOLUME]?.toIntOrNull()?.let {
            setVolumeRaw(appContext, AudioManager.STREAM_MUSIC, it)
        }
        snapshot[KEY_RING_VOLUME]?.toIntOrNull()?.let {
            setVolumeRaw(appContext, AudioManager.STREAM_RING, it)
        }
        snapshot[KEY_SCREEN_TIMEOUT]?.toLongOrNull()?.let { setScreenTimeout(appContext, it) }
        snapshot[KEY_AUTO_ROTATE]?.toBooleanStrictOrNull()?.let { setAutoRotate(appContext, it) }

        val updated = mode.copy(isActive = false, revertSnapshotJson = null)
        container.modeRepository.save(updated)
        return updated
    }

    // -- Current-value reads (null when unreadable) --------------------------------

    private fun currentDnd(context: Context): Boolean? {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return null
        return runCatching {
            manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        }.getOrNull()
    }

    private fun currentRingerMode(context: Context): Int? {
        val audio = context.getSystemService(AudioManager::class.java) ?: return null
        return runCatching { audio.ringerMode }.getOrNull()
    }

    private fun currentBrightness(context: Context): Int? = runCatching {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    }.getOrNull()

    private fun currentAutoBrightness(context: Context): Boolean? = runCatching {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE) ==
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
    }.getOrNull()

    private fun currentVolume(context: Context, stream: Int): Int? {
        val audio = context.getSystemService(AudioManager::class.java) ?: return null
        return runCatching { audio.getStreamVolume(stream) }.getOrNull()
    }

    private fun currentScreenTimeout(context: Context): Long? = runCatching {
        Settings.System.getLong(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)
    }.getOrNull()

    private fun currentAutoRotate(context: Context): Boolean? = runCatching {
        Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION) == 1
    }.getOrNull()

    // -- Writers (no-ops when the needed grant is missing) -------------------------

    private fun setDnd(context: Context, on: Boolean) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (!manager.isNotificationPolicyAccessGranted) return
        runCatching {
            manager.setInterruptionFilter(
                if (on) NotificationManager.INTERRUPTION_FILTER_NONE
                else NotificationManager.INTERRUPTION_FILTER_ALL
            )
        }
    }

    private fun setRingerMode(context: Context, mode: String) {
        val value = when (mode) {
            "vibrate" -> AudioManager.RINGER_MODE_VIBRATE
            "silent" -> AudioManager.RINGER_MODE_SILENT
            else -> AudioManager.RINGER_MODE_NORMAL
        }
        setRingerModeValue(context, value)
    }

    private fun setRingerModeValue(context: Context, value: Int) {
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        runCatching { audio.ringerMode = value }
    }

    private fun setBrightness(context: Context, percentOrRaw: Int) {
        if (!Settings.System.canWrite(context)) return
        // Mode brightness is stored 0..255; clamp defensively.
        val raw = percentOrRaw.coerceIn(1, 255)
        runCatching {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                raw
            )
        }
    }

    private fun setAutoBrightness(context: Context, enabled: Boolean) {
        if (!Settings.System.canWrite(context)) return
        runCatching {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                if (enabled) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
        }
    }

    private fun setVolumePercent(context: Context, stream: Int, percent: Int) {
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        runCatching {
            val max = audio.getStreamMaxVolume(stream)
            audio.setStreamVolume(stream, (percent.coerceIn(0, 100) * max / 100), 0)
        }
    }

    private fun setVolumeRaw(context: Context, stream: Int, value: Int) {
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        runCatching {
            val max = audio.getStreamMaxVolume(stream)
            audio.setStreamVolume(stream, value.coerceIn(0, max), 0)
        }
    }

    private fun setScreenTimeout(context: Context, timeoutMs: Long) {
        if (!Settings.System.canWrite(context)) return
        runCatching {
            Settings.System.putLong(
                context.contentResolver,
                Settings.System.SCREEN_OFF_TIMEOUT,
                timeoutMs.coerceIn(5_000L, 30 * 60_000L)
            )
        }
    }

    private fun setAutoRotate(context: Context, enabled: Boolean) {
        if (!Settings.System.canWrite(context)) return
        runCatching {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                if (enabled) 1 else 0
            )
        }
    }

    // -- Snapshot codec: "key=value;key=value;" ------------------------------------

    private fun encodeSnapshot(values: Map<String, String>): String =
        values.entries.joinToString(separator = ";") { "${it.key}=${it.value}" }
            .let { if (it.isEmpty()) "" else "$it;" }

    private fun decodeSnapshot(encoded: String?): Map<String, String> {
        if (encoded.isNullOrEmpty()) return emptyMap()
        return encoded.split(";")
            .mapNotNull { part ->
                val eq = part.indexOf('=')
                if (eq <= 0) null else part.substring(0, eq) to part.substring(eq + 1)
            }.toMap()
    }
}
