package com.luc.body

import android.Manifest
import android.app.AppOpsManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.core.content.ContextCompat
import com.luc.body.util.PrefsManager

class MainActivity : ComponentActivity() {
    private lateinit var prefs: PrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = PrefsManager(this)
        bindSettings()

        val settingsOverlay = findViewById<View>(R.id.settings_overlay)
        findViewById<View>(R.id.settings_button).setOnClickListener {
            settingsOverlay.visibility = View.VISIBLE
        }
        findViewById<View>(R.id.settings_close_button).setOnClickListener {
            settingsOverlay.visibility = View.GONE
        }
        settingsOverlay.setOnClickListener { settingsOverlay.visibility = View.GONE }
        findViewById<View>(R.id.settings_panel).setOnClickListener { /* Keep taps inside the panel. */ }
        onBackPressedDispatcher.addCallback(this) {
            if (settingsOverlay.visibility == View.VISIBLE) {
                settingsOverlay.visibility = View.GONE
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }

        findViewById<View>(R.id.start_button).setOnClickListener { startFromVisibleClick() }
        val showRestrictedSettings = Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA
        findViewById<View>(R.id.restricted_settings_container).visibility =
            if (showRestrictedSettings) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.app_details_button).setOnClickListener { openAppDetailsSettings() }
        findViewById<View>(R.id.settings_app_details_button).apply {
            visibility = if (showRestrictedSettings) View.VISIBLE else View.GONE
            setOnClickListener { openAppDetailsSettings() }
        }
        findViewById<Button>(R.id.permission_help_button).setOnClickListener {
            openAppDetailsSettings()
        }
        findViewById<View>(R.id.overlay_permission_button).setOnClickListener { openOverlaySettings() }
        findViewById<View>(R.id.usage_permission_button).setOnClickListener { openUsageSettings() }
        findViewById<View>(R.id.stop_button).setOnClickListener {
            stopService(Intent(this, OverlayService::class.java))
        }
        findViewById<View>(R.id.save_settings_button).setOnClickListener { saveSettings() }
        findViewById<View>(R.id.reset_position_button).setOnClickListener {
            prefs.resetPosition()
            ContextCompat.startForegroundService(
                this,
                Intent(this, OverlayService::class.java).setAction(OverlayServiceActions.ACTION_RESET_POSITION),
            )
            Toast.makeText(this, R.string.position_reset, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun startFromVisibleClick() {
        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings()
            return
        }
        startOverlayServiceFromVisibleClick()
    }

    private fun startOverlayServiceFromVisibleClick() {
        ContextCompat.startForegroundService(
            this,
            Intent(this, OverlayService::class.java)
                .setAction(OverlayServiceActions.ACTION_SHOW)
                .putExtra(OverlayServiceActions.EXTRA_RESET_POSITION_ON_SHOW, true),
        )
    }

    private fun updatePermissionStatus() {
        val overlay = Settings.canDrawOverlays(this)
        val battery = isIgnoringBatteryOptimizations()
        val usage = hasUsageAccess()
        val notification = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        updatePermissionRow(R.id.overlay_permission_status, overlay, required = true)
        updatePermissionRow(R.id.battery_permission_status, battery, required = false)
        updatePermissionRow(R.id.usage_permission_status, usage, required = false)
        updatePermissionRow(R.id.notification_permission_status, notification, required = false)
    }

    private fun updatePermissionRow(viewId: Int, granted: Boolean, required: Boolean) {
        val status = when {
            granted -> R.string.permission_granted
            required -> R.string.permission_required
            else -> R.string.permission_not_granted
        }
        findViewById<TextView>(viewId).apply {
            text = "● ${getString(status)}"
            setTextColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    if (granted) R.color.luc_accent_strong else R.color.luc_text_muted,
                ),
            )
        }
    }

    private fun bindSettings() {
        findViewById<EditText>(R.id.supabase_url_input).setText(prefs.supabaseUrl)
        findViewById<EditText>(R.id.supabase_key_input).setText(prefs.supabaseKey)
        findViewById<Switch>(R.id.boot_switch).isChecked = prefs.bootEnabled
        findViewById<Switch>(R.id.self_talk_switch).isChecked = prefs.selfTalkEnabled
        findViewById<Switch>(R.id.loneliness_switch).isChecked = prefs.lonelinessEnabled
        findViewById<Switch>(R.id.app_awareness_switch).isChecked = prefs.appAwarenessEnabled
        bindSeekBar(
            R.id.self_talk_frequency_seek,
            R.id.self_talk_frequency_label,
            prefs.selfTalkFrequencyMinutes,
            R.string.self_talk_frequency,
        )
    }

    private fun bindSeekBar(seekBarId: Int, labelId: Int, value: Int, labelFormat: Int) {
        val seekBar = findViewById<SeekBar>(seekBarId)
        val label = findViewById<TextView>(labelId)
        fun updateLabel(currentValue: Int) {
            label.text = getString(labelFormat, currentValue)
        }
        seekBar.progress = value
        updateLabel(value)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateLabel(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }

    private fun saveSettings() {
        prefs.supabaseUrl = findViewById<EditText>(R.id.supabase_url_input).text.toString()
        prefs.supabaseKey = findViewById<EditText>(R.id.supabase_key_input).text.toString()
        prefs.bootEnabled = findViewById<Switch>(R.id.boot_switch).isChecked
        prefs.selfTalkEnabled = findViewById<Switch>(R.id.self_talk_switch).isChecked
        prefs.selfTalkFrequencyMinutes = findViewById<SeekBar>(R.id.self_talk_frequency_seek).progress
        prefs.lonelinessEnabled = findViewById<Switch>(R.id.loneliness_switch).isChecked
        prefs.appAwarenessEnabled = findViewById<Switch>(R.id.app_awareness_switch).isChecked
        if (Settings.canDrawOverlays(this)) {
            ContextCompat.startForegroundService(
                this,
                Intent(this, OverlayService::class.java).setAction(OverlayServiceActions.ACTION_RELOAD),
            )
        }
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
    }

    private fun openOverlaySettings() {
        val primaryIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        } else {
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri())
        }
        openFirstAvailableSettings(
            primaryIntent,
            appDetailsIntent(),
            Intent(Settings.ACTION_SETTINGS),
        )
    }

    private fun openUsageSettings() {
        openFirstAvailableSettings(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
            appDetailsIntent(),
            Intent(Settings.ACTION_SETTINGS),
        )
    }

    private fun openAppDetailsSettings() {
        openFirstAvailableSettings(
            appDetailsIntent(),
            Intent(Settings.ACTION_APPLICATION_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
    }

    private fun openFirstAvailableSettings(vararg intents: Intent) {
        intents.forEach { intent ->
            try {
                startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
                // Try the next public Settings entry point on this device.
            } catch (_: SecurityException) {
                // OEM Settings can reject an otherwise valid public action.
            }
        }
        Toast.makeText(this, R.string.permission_settings_unavailable, Toast.LENGTH_LONG).show()
    }

    private fun packageUri(): Uri = Uri.parse("package:$packageName")

    private fun appDetailsIntent(): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri())

    private fun isIgnoringBatteryOptimizations(): Boolean =
        getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(AppOpsManager::class.java)
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

}
