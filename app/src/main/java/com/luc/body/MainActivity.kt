package com.luc.body

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private var startAfterNotificationRequest = false
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        if (startAfterNotificationRequest) {
            startAfterNotificationRequest = false
            startOverlayServiceFromVisibleClick()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.start_button).setOnClickListener { startFromVisibleClick() }
        findViewById<Button>(R.id.stop_button).setOnClickListener {
            stopService(Intent(this, OverlayService::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun startFromVisibleClick() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"),
                ),
            )
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            startAfterNotificationRequest = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        startOverlayServiceFromVisibleClick()
    }

    private fun startOverlayServiceFromVisibleClick() {
        ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java))
    }

    private fun updatePermissionStatus() {
        val overlay = Settings.canDrawOverlays(this)
        val notification = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        findViewById<TextView>(R.id.permission_status).text = getString(
            R.string.permission_status,
            getString(if (overlay) R.string.permission_granted else R.string.permission_required),
            getString(if (notification) R.string.permission_granted else R.string.permission_optional),
        )
    }
}
