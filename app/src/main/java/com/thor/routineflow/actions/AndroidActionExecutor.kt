package com.thor.routineflow.actions

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.AlarmClock
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.thor.routineflow.R
import com.thor.routineflow.RoutineFlowApp
import com.thor.routineflow.admin.RoutineFlowDeviceAdmin
import com.thor.routineflow.di.AppContainer
import com.thor.routineflow.engine.ActionExecutor
import com.thor.routineflow.engine.ActionResult
import com.thor.routineflow.engine.ExecutionContext
import com.thor.routineflow.engine.RuleEngine
import com.thor.routineflow.model.ActionConfig
import com.thor.routineflow.model.ActionType
import com.thor.routineflow.triggers.RoutineFlowNotificationListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Runs leaf actions against real Android APIs. Every action catches its own
 * failures and returns [ActionResult.Failed] with a plain-language reason —
 * [execute] never throws, and no action ever reports success it did not earn.
 *
 * DELAY / IF / ELSE / END_IF are handled by RoutineRunner and return Skipped
 * if they reach here. The six privileged actions (Shizuku / root) always fail
 * with an honest explanation: v1 does not pretend to run them.
 */
class AndroidActionExecutor(
    context: Context,
    @Suppress("unused") private val container: AppContainer
) : ActionExecutor {

    private val appContext = context.applicationContext
    private val tts = TtsHelper(appContext)
    private val notificationIdCounter = AtomicInteger(1)

    override suspend fun execute(action: ActionConfig, context: ExecutionContext): ActionResult {
        return try {
            val type = ActionType.fromKey(action.type)
                ?: return ActionResult.Failed("Unknown action '${action.type}'")
            when (type) {
                ActionType.DELAY, ActionType.IF, ActionType.ELSE, ActionType.END_IF ->
                    ActionResult.Skipped("Handled by the routine runner")

                ActionType.MOBILE_DATA_TOGGLE,
                ActionType.AIRPLANE_TOGGLE,
                ActionType.WIFI_TOGGLE_DIRECT,
                ActionType.DARK_MODE ->
                    ActionResult.Failed("${type.title} needs Shizuku — coming in a later version")

                ActionType.GPS_TOGGLE,
                ActionType.FORCE_STOP_APP ->
                    ActionResult.Failed("${type.title} needs root — not available in RoutineFlow v1")

                else -> runAction(type, action.params, context)
            }
        } catch (e: Exception) {
            ActionResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    fun shutdown() {
        tts.shutdown()
    }

    private suspend fun runAction(
        type: ActionType,
        params: Map<String, String>,
        context: ExecutionContext
    ): ActionResult {
        return when (type) {
            ActionType.WIFI_PANEL -> openWifiPanel()
            ActionType.BLUETOOTH_TOGGLE -> setBluetooth(params.enabled())
            ActionType.DND_SET -> setDnd(params.enabled())
            ActionType.RINGER_MODE -> setRingerMode(params["mode"] ?: "normal")
            ActionType.BRIGHTNESS -> setBrightness(params.percent("percent", 50))
            ActionType.AUTO_BRIGHTNESS -> setAutoBrightness(params.enabled())
            ActionType.SCREEN_TIMEOUT -> setScreenTimeout(params["timeoutMs"]?.toLongOrNull() ?: 30_000L)
            ActionType.WALLPAPER_COLOR -> setWallpaperColor(params["color"] ?: "#6750A4")
            ActionType.VOLUME_RING -> setVolume(AudioManager.STREAM_RING, params.percent("percent", 70))
            ActionType.VOLUME_MEDIA -> setVolume(AudioManager.STREAM_MUSIC, params.percent("percent", 70))
            ActionType.VOLUME_ALARM -> setVolume(AudioManager.STREAM_ALARM, params.percent("percent", 80))
            ActionType.VOLUME_NOTIFICATION -> setVolume(
                AudioManager.STREAM_NOTIFICATION, params.percent("percent", 70)
            )
            ActionType.AUTO_ROTATE -> setAutoRotate(params.enabled())
            ActionType.FLASHLIGHT -> setFlashlight(params.enabled())
            ActionType.LOCK_SCREEN -> lockScreen()
            ActionType.MEDIA_PLAY_PAUSE -> toggleMedia()
            ActionType.CLEAR_NOTIFICATIONS -> clearNotifications()
            ActionType.OPEN_BATTERY_SAVER_SETTINGS -> openSettings(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            ActionType.OPEN_LOCATION_SETTINGS -> openSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            ActionType.OPEN_NFC_SETTINGS -> openSettings(Settings.ACTION_NFC_SETTINGS)
            ActionType.OPEN_MOBILE_DATA_SETTINGS -> openSettings(Settings.ACTION_NETWORK_OPERATOR_SETTINGS)
            ActionType.LAUNCH_APP -> launchApp(params["packageName"].orEmpty())
            ActionType.OPEN_URL -> openUrl(context.substitute(params["url"].orEmpty()))
            ActionType.SET_ALARM_INTENT -> setAlarmIntent(
                params["time"] ?: "07:00",
                context.substitute(params["label"].orEmpty())
            )
            ActionType.SET_TIMER_INTENT -> setTimerIntent(params["seconds"]?.toIntOrNull() ?: 300)
            ActionType.SHOW_NOTIFICATION -> showNotification(
                context.substitute(params["title"] ?: "RoutineFlow"),
                context.substitute(params["text"].orEmpty())
            )
            ActionType.SPEAK_TEXT -> speak(context.substitute(params["text"].orEmpty()))
            ActionType.SPEAK_TIME -> speakTime()
            ActionType.SPEAK_BATTERY -> speakBattery()
            ActionType.VIBRATE -> vibrate(params["durationMs"]?.toLongOrNull() ?: 200L)
            ActionType.SHOW_TOAST -> showToast(context.substitute(params["text"].orEmpty()))
            ActionType.PLAY_SOUND -> playSound()
            ActionType.COPY_CLIPBOARD -> copyClipboard(context.substitute(params["text"].orEmpty()))
            ActionType.HTTP_GET -> httpGet(context.substitute(params["url"].orEmpty()))
            ActionType.RUN_ROUTINE -> context.runRoutine(params["routineId"].orEmpty())
            ActionType.SET_ROUTINE_ENABLED -> context.setRoutineEnabled(
                params["routineId"].orEmpty(), params.enabled()
            )
            else -> ActionResult.Failed("Action '${type.key}' is not implemented")
        }
    }

    // -- Settings ------------------------------------------------------------------

    private fun openWifiPanel(): ActionResult {
        val intent = if (Build.VERSION.SDK_INT >= 29) {
            Intent(Settings.Panel.ACTION_WIFI)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }
        return startActivity(intent, "Opened Wi-Fi settings")
    }

    private fun setBluetooth(enabled: Boolean): ActionResult {
        if (Build.VERSION.SDK_INT >= 31 && !hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            return ActionResult.Failed("Bluetooth permission is not granted")
        }
        val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter
            ?: return ActionResult.Failed("This device has no Bluetooth adapter")
        val ok = runCatching {
            if (enabled) adapter.enable() else adapter.disable()
        }.getOrDefault(false)
        return if (ok) {
            ActionResult.Success("Bluetooth ${if (enabled) "on" else "off"}")
        } else {
            ActionResult.Failed(
                "The system did not allow turning Bluetooth ${if (enabled) "on" else "off"} — " +
                    "check the permission and any system consent prompt"
            )
        }
    }

    private fun setDnd(enabled: Boolean): ActionResult {
        val manager = appContext.getSystemService(NotificationManager::class.java)
            ?: return ActionResult.Failed("Notification service is unavailable")
        if (!manager.isNotificationPolicyAccessGranted) {
            return ActionResult.Failed("Do Not Disturb access is not granted — enable it in Settings")
        }
        return runCatching {
            manager.setInterruptionFilter(
                if (enabled) NotificationManager.INTERRUPTION_FILTER_NONE
                else NotificationManager.INTERRUPTION_FILTER_ALL
            )
            ActionResult.Success("Do Not Disturb ${if (enabled) "on" else "off"}")
        }.getOrElse { ActionResult.Failed(it.message ?: "Could not change Do Not Disturb") }
    }

    private fun setRingerMode(mode: String): ActionResult {
        val audio = audioManager() ?: return ActionResult.Failed("Audio service is unavailable")
        val value = when (mode) {
            "vibrate" -> AudioManager.RINGER_MODE_VIBRATE
            "silent" -> AudioManager.RINGER_MODE_SILENT
            else -> AudioManager.RINGER_MODE_NORMAL
        }
        return runCatching {
            audio.ringerMode = value
            ActionResult.Success("Ringer mode: $mode")
        }.getOrElse {
            ActionResult.Failed(
                "Could not set the ringer mode — silent mode may need Do Not Disturb access"
            )
        }
    }

    private fun setBrightness(percent: Int): ActionResult {
        if (!Settings.System.canWrite(appContext)) {
            return ActionResult.Failed("Modify system settings is not granted — enable it in Settings")
        }
        return runCatching {
            val resolver = appContext.contentResolver
            Settings.System.putInt(
                resolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(
                resolver, Settings.System.SCREEN_BRIGHTNESS,
                (percent.coerceIn(0, 100) * 255 / 100).coerceIn(1, 255)
            )
            ActionResult.Success("Brightness $percent%")
        }.getOrElse { ActionResult.Failed(it.message ?: "Could not set brightness") }
    }

    private fun setAutoBrightness(enabled: Boolean): ActionResult {
        if (!Settings.System.canWrite(appContext)) {
            return ActionResult.Failed("Modify system settings is not granted — enable it in Settings")
        }
        return runCatching {
            Settings.System.putInt(
                appContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                if (enabled) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            ActionResult.Success("Auto-brightness ${if (enabled) "on" else "off"}")
        }.getOrElse { ActionResult.Failed(it.message ?: "Could not change auto-brightness") }
    }

    private fun setScreenTimeout(timeoutMs: Long): ActionResult {
        if (!Settings.System.canWrite(appContext)) {
            return ActionResult.Failed("Modify system settings is not granted — enable it in Settings")
        }
        return runCatching {
            Settings.System.putLong(
                appContext.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT,
                timeoutMs.coerceIn(5_000L, 30 * 60_000L)
            )
            ActionResult.Success("Screen timeout ${timeoutMs / 1000}s")
        }.getOrElse { ActionResult.Failed(it.message ?: "Could not set screen timeout") }
    }

    private fun setWallpaperColor(colorString: String): ActionResult {
        val color = try {
            Color.parseColor(colorString)
        } catch (e: IllegalArgumentException) {
            return ActionResult.Failed("'$colorString' is not a valid colour (use #RRGGBB)")
        }
        return runCatching {
            val bitmap = Bitmap.createBitmap(512, 1024, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(color)
            val wallpaper = android.app.WallpaperManager.getInstance(appContext)
            wallpaper.setBitmap(bitmap)
            bitmap.recycle()
            ActionResult.Success("Wallpaper set to $colorString")
        }.getOrElse { ActionResult.Failed(it.message ?: "Could not set the wallpaper") }
    }

    private fun setVolume(stream: Int, percent: Int): ActionResult {
        val audio = audioManager() ?: return ActionResult.Failed("Audio service is unavailable")
        return runCatching {
            val max = audio.getStreamMaxVolume(stream)
            audio.setStreamVolume(stream, percent.coerceIn(0, 100) * max / 100, 0)
            ActionResult.Success("Volume $percent%")
        }.getOrElse { ActionResult.Failed(it.message ?: "Could not set the volume") }
    }

    private fun setAutoRotate(enabled: Boolean): ActionResult {
        if (!Settings.System.canWrite(appContext)) {
            return ActionResult.Failed("Modify system settings is not granted — enable it in Settings")
        }
        return runCatching {
            Settings.System.putInt(
                appContext.contentResolver, Settings.System.ACCELEROMETER_ROTATION,
                if (enabled) 1 else 0
            )
            ActionResult.Success("Auto-rotate ${if (enabled) "on" else "off"}")
        }.getOrElse { ActionResult.Failed(it.message ?: "Could not change auto-rotate") }
    }

    private fun setFlashlight(enabled: Boolean): ActionResult {
        if (!hasPermission(Manifest.permission.CAMERA)) {
            return ActionResult.Failed("Camera permission is not granted (the torch needs it)")
        }
        val cameraManager = appContext.getSystemService(CameraManager::class.java)
            ?: return ActionResult.Failed("Camera service is unavailable")
        return runCatching {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return ActionResult.Failed("This device has no flash")
            cameraManager.setTorchMode(cameraId, enabled)
            ActionResult.Success("Flashlight ${if (enabled) "on" else "off"}")
        }.getOrElse { ActionResult.Failed(it.message ?: "Could not control the flashlight") }
    }

    private fun lockScreen(): ActionResult {
        return if (RoutineFlowDeviceAdmin.lockNow(appContext)) {
            ActionResult.Success("Screen locked")
        } else {
            ActionResult.Failed("Device admin is not active — enable it in Settings to use lock screen")
        }
    }

    private fun toggleMedia(): ActionResult {
        val manager = appContext.getSystemService(MediaSessionManager::class.java)
            ?: return ActionResult.Failed("Media session service is unavailable")
        val component = ComponentName(appContext, RoutineFlowNotificationListener::class.java)
        val sessions = try {
            manager.getActiveSessions(component)
        } catch (e: SecurityException) {
            return ActionResult.Failed(
                "Notification access is not enabled — RoutineFlow needs it to see media sessions"
            )
        }
        val session = sessions.firstOrNull()
            ?: return ActionResult.Failed("No media session is playing right now")
        return runCatching {
            val playing = session.playbackState?.state == PlaybackState.STATE_PLAYING
            if (playing) session.controller.transportControls.pause()
            else session.controller.transportControls.play()
            ActionResult.Success(if (playing) "Media paused" else "Media playing")
        }.getOrElse { ActionResult.Failed(it.message ?: "Could not control media playback") }
    }

    private fun clearNotifications(): ActionResult {
        return if (RoutineFlowNotificationListener.cancelAllNotifications(appContext)) {
            ActionResult.Success("Notifications cleared")
        } else {
            ActionResult.Failed("Notification access is not enabled — RoutineFlow needs it to clear notifications")
        }
    }

    // -- Apps ----------------------------------------------------------------------

    private fun openSettings(action: String): ActionResult {
        return startActivity(Intent(action), "Opened settings")
    }

    private fun launchApp(packageName: String): ActionResult {
        if (packageName.isEmpty()) return ActionResult.Failed("No app was chosen")
        val intent = appContext.packageManager.getLaunchIntentForPackage(packageName)
            ?: return ActionResult.Failed("That app is not installed or has no launch screen")
        return startActivity(intent, "Launched $packageName")
    }

    private fun openUrl(url: String): ActionResult {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return ActionResult.Failed("'$url' is not a valid http(s) link")
        }
        return startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)), "Opened $url")
    }

    private fun setAlarmIntent(time: String, label: String): ActionResult {
        val minutes = RuleEngine.parseTime(time)
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, minutes / 60)
            putExtra(AlarmClock.EXTRA_MINUTES, minutes % 60)
            if (label.isNotEmpty()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
        }
        return startActivity(intent, "Alarm ready in the clock app")
    }

    private fun setTimerIntent(seconds: Int): ActionResult {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds.coerceIn(1, 24 * 60 * 60))
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
        return startActivity(intent, "Timer ready in the clock app")
    }

    // -- Notify & speak ------------------------------------------------------------

    private fun showNotification(title: String, text: String): ActionResult {
        if (Build.VERSION.SDK_INT >= 33 &&
            !hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            return ActionResult.Failed("Notifications permission is not granted")
        }
        val launchIntent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(
                appContext, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val notification = NotificationCompat.Builder(appContext, RoutineFlowApp.CHANNEL_ROUTINES)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title.ifEmpty { "RoutineFlow" })
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        return runCatching {
            val manager = appContext.getSystemService(NotificationManager::class.java)
                ?: return ActionResult.Failed("Notification service is unavailable")
            manager.notify(notificationIdCounter.getAndIncrement(), notification)
            ActionResult.Success("Notification shown")
        }.getOrElse { ActionResult.Failed(it.message ?: "Could not show the notification") }
    }

    private suspend fun speak(text: String): ActionResult {
        if (text.isBlank()) return ActionResult.Failed("Nothing to speak")
        return if (tts.speak(text)) ActionResult.Success("Spoke the text")
        else ActionResult.Failed("Text-to-speech is not available on this device")
    }

    private suspend fun speakTime(): ActionResult {
        val formatted = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
        return speak("The time is $formatted")
    }

    private suspend fun speakBattery(): ActionResult {
        val percent = batteryPercent()
        val text = if (percent != null) "Battery is $percent percent"
        else "Battery level is unknown"
        return speak(text)
    }

    private fun vibrate(durationMs: Long): ActionResult {
        return runCatching {
            val duration = durationMs.coerceIn(10L, 5_000L)
            val effect = VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
            if (Build.VERSION.SDK_INT >= 31) {
                val manager = appContext.getSystemService(VibratorManager::class.java)
                val vibrator = manager?.defaultVibrator
                if (vibrator?.hasVibrator() != true) {
                    return ActionResult.Failed("This device has no vibrator")
                }
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                val vibrator = appContext.getSystemService(Vibrator::class.java)
                if (vibrator?.hasVibrator() != true) {
                    return ActionResult.Failed("This device has no vibrator")
                }
                vibrator.vibrate(effect)
            }
            ActionResult.Success("Vibrated for ${duration}ms")
        }.getOrElse { ActionResult.Failed(it.message ?: "Could not vibrate") }
    }

    private suspend fun showToast(text: String): ActionResult {
        if (text.isBlank()) return ActionResult.Failed("Nothing to show")
        withContext(Dispatchers.Main) {
            Toast.makeText(appContext, text, Toast.LENGTH_SHORT).show()
        }
        return ActionResult.Success("Message shown")
    }

    private fun playSound(): ActionResult {
        return runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(appContext, uri)
                ?: return ActionResult.Failed("No notification sound is set on this device")
            ringtone.play()
            ActionResult.Success("Sound played")
        }.getOrElse { ActionResult.Failed(it.message ?: "Could not play a sound") }
    }

    private fun copyClipboard(text: String): ActionResult {
        val clipboard = appContext.getSystemService(ClipboardManager::class.java)
            ?: return ActionResult.Failed("Clipboard service is unavailable")
        return runCatching {
            clipboard.setPrimaryClip(ClipData.newPlainText("RoutineFlow", text))
            ActionResult.Success("Copied to clipboard")
        }.getOrElse { ActionResult.Failed(it.message ?: "Could not copy to the clipboard") }
    }

    // -- Network (HTTP GET only) ---------------------------------------------------

    private fun httpGet(urlString: String): ActionResult {
        if (!urlString.startsWith("http://") && !urlString.startsWith("https://")) {
            return ActionResult.Failed("'$urlString' is not a valid http(s) URL")
        }
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(urlString).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = true
            connection.connect()
            val code = connection.responseCode
            if (code in 200..299) ActionResult.Success("HTTP $code")
            else ActionResult.Failed("HTTP $code")
        } catch (e: Exception) {
            ActionResult.Failed(e.message ?: "Request failed")
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    // -- Helpers -------------------------------------------------------------------

    private fun startActivity(intent: Intent, successMessage: String): ActionResult {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
            ActionResult.Success(successMessage)
        } catch (e: android.content.ActivityNotFoundException) {
            ActionResult.Failed("No app can open that — is it installed?")
        } catch (e: SecurityException) {
            ActionResult.Failed(e.message ?: "The system refused to open that")
        }
    }

    private fun audioManager(): AudioManager? =
        appContext.getSystemService(AudioManager::class.java)

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) ==
            PackageManager.PERMISSION_GRANTED

    private fun batteryPercent(): Int? = runCatching {
        val battery = appContext.registerReceiver(
            null,
            android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: return null
        val level = battery.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
        val scale = battery.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
        if (level >= 0 && scale > 0) level * 100 / scale else null
    }.getOrNull()

    private fun Map<String, String>.enabled(): Boolean =
        this["enabled"]?.lowercase() != "off"

    private fun Map<String, String>.percent(key: String, default: Int): Int =
        this[key]?.toIntOrNull()?.coerceIn(0, 100) ?: default
}
