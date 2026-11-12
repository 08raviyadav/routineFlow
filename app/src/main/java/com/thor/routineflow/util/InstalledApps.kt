package com.thor.routineflow.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/** Launcher-app lookups for the app picker (LAUNCH_APP, APP_OPENED, APP_CLOSED). */
object InstalledApps {

    data class AppInfo(val packageName: String, val label: String)

    /** Every app with a launcher entry, sorted by display name. */
    fun launcherApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val infos = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        return infos.mapNotNull { resolve ->
            val packageName = resolve.activityInfo?.packageName ?: return@mapNotNull null
            val label = resolve.loadLabel(pm)?.toString() ?: packageName
            AppInfo(packageName, label)
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }

    /** Display label for a package, falling back to the package name when unknown. */
    fun labelFor(context: Context, packageName: String): String {
        return runCatching {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= 33) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(info)?.toString() ?: packageName
        }.getOrDefault(packageName)
    }
}
