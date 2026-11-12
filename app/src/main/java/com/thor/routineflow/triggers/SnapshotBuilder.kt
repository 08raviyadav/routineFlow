package com.thor.routineflow.triggers

import android.app.NotificationManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.Settings
import com.thor.routineflow.model.TriggerSnapshot

/**
 * Last connected Bluetooth device, tracked from the ACL / profile broadcasts
 * in [ConnectivityMonitor]. Reading the live connection state through profile
 * proxies (A2DP / HEADSET) is slow and racy; the broadcast trail is the
 * reliable source on real phones.
 */
object BluetoothState {
    @Volatile var lastDeviceName: String? = null
        private set
    @Volatile var lastDeviceAddress: String? = null
        private set

    fun connected(name: String?, address: String?) {
        lastDeviceName = name
        lastDeviceAddress = address
    }

    fun disconnected(address: String?) {
        if (address == null || address == lastDeviceAddress) {
            lastDeviceName = null
            lastDeviceAddress = null
        }
    }
}

/**
 * Assembles a [TriggerSnapshot] — a point-in-time view of the phone — from
 * system services. Every read is guarded: a missing permission or an
 * unavailable service yields null for that field, never a crash. A null field
 * means "unknown", and the rule engine treats unknown state as "does not match".
 */
object SnapshotBuilder {

    fun build(context: Context): TriggerSnapshot {
        val appContext = context.applicationContext

        var batteryPercent: Int? = null
        var isCharging: Boolean? = null
        runCatching {
            val battery = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            battery?.let {
                val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) batteryPercent = (level * 100 / scale)
                val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            }
        }

        var wifiSsid: String? = null
        runCatching {
            val wifi = appContext.getSystemService(WifiManager::class.java)
            val ssid = wifi?.connectionInfo?.ssid
            if (!ssid.isNullOrEmpty() && ssid != "<unknown ssid>") {
                wifiSsid = ssid.trim('"')
            }
        }

        var isHeadphonesConnected: Boolean? = null
        runCatching {
            val audio = appContext.getSystemService(AudioManager::class.java)
            if (audio != null) {
                val devices = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                isHeadphonesConnected = devices.any {
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        it.type == AudioDeviceInfo.TYPE_USB_HEADSET
                }
            }
        }

        var isAirplaneMode: Boolean? = null
        runCatching {
            isAirplaneMode = Settings.Global.getInt(
                appContext.contentResolver,
                Settings.Global.AIRPLANE_MODE_ON, 0
            ) != 0
        }

        var isDndOn: Boolean? = null
        runCatching {
            val notifications = appContext.getSystemService(NotificationManager::class.java)
            if (notifications != null) {
                isDndOn = notifications.currentInterruptionFilter !=
                    NotificationManager.INTERRUPTION_FILTER_ALL
            }
        }

        var isScreenOn: Boolean? = null
        runCatching {
            val power = appContext.getSystemService(PowerManager::class.java)
            isScreenOn = power?.isInteractive
        }

        var clipboardText: String? = null
        runCatching {
            val clipboard = appContext.getSystemService(ClipboardManager::class.java)
            if (clipboard?.hasPrimaryClip() == true) {
                clipboardText = clipboard.primaryClip
                    ?.getItemAt(0)
                    ?.coerceToText(appContext)
                    ?.toString()
                    ?.take(500)
            }
        }

        return TriggerSnapshot(
            batteryPercent = batteryPercent,
            isCharging = isCharging,
            wifiSsid = wifiSsid,
            bluetoothDeviceName = BluetoothState.lastDeviceName,
            bluetoothDeviceAddress = BluetoothState.lastDeviceAddress,
            isHeadphonesConnected = isHeadphonesConnected,
            isAirplaneMode = isAirplaneMode,
            isDndOn = isDndOn,
            isScreenOn = isScreenOn,
            foregroundPackage = AppUsageMonitor.lastForegroundPackage,
            clipboardText = clipboardText,
            lastNotificationPackage = RoutineFlowNotificationListener.lastPackage,
            lastNotificationText = RoutineFlowNotificationListener.lastText,
            lastNotificationAt = RoutineFlowNotificationListener.lastAt,
            callState = CallStateMonitor.currentState
        )
    }
}
