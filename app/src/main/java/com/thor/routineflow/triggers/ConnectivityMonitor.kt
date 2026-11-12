package com.thor.routineflow.triggers

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.core.content.ContextCompat
import com.thor.routineflow.model.TriggerEvent

/**
 * Watches connectivity and device-state broadcasts and forwards them to the
 * engine. Event triggers (screen on/off, unlock) become [TriggerEvent]s;
 * state triggers (Wi-Fi, Bluetooth, headphones, airplane mode, DND, charging,
 * battery) just ask the engine to re-evaluate against a fresh snapshot.
 *
 * Owned by [com.thor.routineflow.service.EngineService]: [register] on start,
 * [unregister] on destroy.
 */
class ConnectivityMonitor(
    context: Context,
    private val onEvent: (TriggerEvent) -> Unit,
    private val onEvaluate: () -> Unit
) {
    private val appContext = context.applicationContext
    private val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
    private var registered = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            onEvaluate()
        }

        override fun onLost(network: Network) {
            onEvaluate()
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            onEvaluate()
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device = intent.bluetoothDevice()
                    BluetoothState.connected(
                        runCatching { device?.name }.getOrNull(),
                        device?.address
                    )
                    onEvaluate()
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    BluetoothState.disconnected(intent.bluetoothDevice()?.address)
                    onEvaluate()
                }
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
                    val device = intent.bluetoothDevice()
                    when (state) {
                        BluetoothProfile.STATE_CONNECTED -> BluetoothState.connected(
                            runCatching { device?.name }.getOrNull(),
                            device?.address
                        )
                        BluetoothProfile.STATE_DISCONNECTED ->
                            BluetoothState.disconnected(device?.address)
                    }
                    onEvaluate()
                }
                Intent.ACTION_HEADSET_PLUG -> onEvaluate()
                Intent.ACTION_SCREEN_ON -> onEvent(TriggerEvent(type = "screen_on"))
                Intent.ACTION_SCREEN_OFF -> onEvent(TriggerEvent(type = "screen_off"))
                Intent.ACTION_USER_PRESENT -> onEvent(TriggerEvent(type = "unlocked"))
                Intent.ACTION_AIRPLANE_MODE_CHANGED -> onEvaluate()
                Intent.ACTION_POWER_CONNECTED,
                Intent.ACTION_POWER_DISCONNECTED,
                Intent.ACTION_BATTERY_CHANGED -> onEvaluate()
                ACTION_INTERRUPTION_FILTER_CHANGED -> onEvaluate()
            }
        }
    }

    fun register() {
        if (registered) return
        registered = true
        runCatching {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            connectivityManager?.registerNetworkCallback(request, networkCallback)
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(ACTION_INTERRUPTION_FILTER_CHANGED)
        }
        runCatching {
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }

    fun unregister() {
        if (!registered) return
        registered = false
        runCatching { connectivityManager?.unregisterNetworkCallback(networkCallback) }
        runCatching { appContext.unregisterReceiver(receiver) }
    }

    private fun Intent.bluetoothDevice(): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= 33) {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    companion object {
        /** System broadcast sent when the DND interruption filter changes. */
        const val ACTION_INTERRUPTION_FILTER_CHANGED =
            "android.app.action.INTERRUPTION_FILTER_CHANGED"
    }
}
