package com.luc.body

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.luc.body.util.PrefsManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = PrefsManager(context)
        if (!shouldStartOnBoot(intent?.action, prefs.bootEnabled, Settings.canDrawOverlays(context))) return

        ContextCompat.startForegroundService(
            context,
            Intent(context, OverlayService::class.java).setAction(OverlayServiceActions.ACTION_SHOW),
        )
    }

    companion object {
        internal fun shouldStartOnBoot(
            action: String?,
            bootEnabled: Boolean,
            canDrawOverlays: Boolean,
        ): Boolean = action == Intent.ACTION_BOOT_COMPLETED && bootEnabled && canDrawOverlays
    }
}
