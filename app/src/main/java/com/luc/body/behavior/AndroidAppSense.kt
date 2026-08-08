package com.luc.body.behavior

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

class UsageStatsForegroundPackageSource(context: Context) : ForegroundPackageSource {
    private val usageStats = context.getSystemService(UsageStatsManager::class.java)

    override fun currentPackage(): String? = runCatching {
        val now = System.currentTimeMillis()
        val events = usageStats.queryEvents(now - LOOKBACK_MS, now)
        val event = UsageEvents.Event()
        var latestPackage: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)
            ) {
                latestPackage = event.packageName
            }
        }
        latestPackage
    }.getOrNull()

    private companion object {
        const val LOOKBACK_MS = 10_000L
    }
}

fun launcherPackages(context: Context): Set<String> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val results = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.queryIntentActivities(
            intent,
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
        )
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
    }
    return results.mapNotNullTo(mutableSetOf()) { it.activityInfo?.packageName }
}
