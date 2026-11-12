package com.thor.routineflow.triggers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.thor.routineflow.model.TriggerEvent

/**
 * Call-state tracking. Emits CALL_STATE events with data state=ringing when a
 * call starts ringing and state=ended when the phone returns to idle after a
 * call. [currentState] ("ringing" | "offhook" | "idle") feeds the snapshot.
 *
 * Uses TelephonyCallback on API 31+ and the legacy PhoneStateListener below
 * that. No-op when READ_PHONE_STATE is not granted.
 */
class CallStateMonitor(
    context: Context,
    private val onEvent: (TriggerEvent) -> Unit
) {
    companion object {
        @Volatile
        var currentState: String = "idle"
            private set
    }

    private val appContext = context.applicationContext
    private val telephonyManager = appContext.getSystemService(TelephonyManager::class.java)
    private var telephonyCallback: TelephonyCallback? = null
    private var phoneStateListener: PhoneStateListener? = null
    private var wasActive = false

    fun register() {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (Build.VERSION.SDK_INT >= 31) {
            registerCallbackApi31()
        } else {
            @Suppress("DEPRECATION")
            registerListenerLegacy()
        }
    }

    @RequiresApi(31)
    private fun registerCallbackApi31() {
        val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                handleState(state)
            }
        }
        telephonyCallback = callback
        runCatching {
            telephonyManager?.registerTelephonyCallback(appContext.mainExecutor, callback)
        }
    }

    @Suppress("DEPRECATION")
    private fun registerListenerLegacy() {
        val listener = object : PhoneStateListener() {
            @Deprecated("Deprecated in Java")
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                handleState(state)
            }
        }
        phoneStateListener = listener
        runCatching {
            telephonyManager?.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    private fun handleState(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                currentState = "ringing"
                wasActive = true
                onEvent(TriggerEvent(type = "call_state", data = mapOf("state" to "ringing")))
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                currentState = "offhook"
                wasActive = true
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                currentState = "idle"
                if (wasActive) {
                    wasActive = false
                    onEvent(TriggerEvent(type = "call_state", data = mapOf("state" to "ended")))
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    fun unregister() {
        telephonyCallback?.let { callback ->
            runCatching { telephonyManager?.unregisterTelephonyCallback(callback) }
            telephonyCallback = null
        }
        phoneStateListener?.let { listener ->
            runCatching { telephonyManager?.listen(listener, PhoneStateListener.LISTEN_NONE) }
            phoneStateListener = null
        }
    }
}
