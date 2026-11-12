package com.thor.routineflow.triggers

import android.content.ClipboardManager
import android.content.Context
import com.thor.routineflow.model.TriggerEvent

/** Clipboard watcher: emits CLIPBOARD_CHANGED with the new text (truncated to 500 chars). */
class ClipboardMonitor(
    context: Context,
    private val onEvent: (TriggerEvent) -> Unit
) {
    private val appContext = context.applicationContext
    private val clipboardManager = appContext.getSystemService(ClipboardManager::class.java)

    private val listener = ClipboardManager.OnPrimaryClipChangedListener {
        val text = runCatching {
            if (clipboardManager?.hasPrimaryClip() == true) {
                clipboardManager.primaryClip
                    ?.getItemAt(0)
                    ?.coerceToText(appContext)
                    ?.toString()
                    ?.take(500)
            } else {
                null
            }
        }.getOrNull()
        val data = if (text != null) mapOf("text" to text) else emptyMap()
        onEvent(TriggerEvent(type = "clipboard_changed", data = data))
    }

    fun register() {
        runCatching { clipboardManager?.addPrimaryClipChangedListener(listener) }
    }

    fun unregister() {
        runCatching { clipboardManager?.removePrimaryClipChangedListener(listener) }
    }
}
