package com.luc.body

import android.Manifest
import android.app.AlertDialog
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.luc.body.util.PrefsManager

class MainActivity : ComponentActivity() {
    private lateinit var prefs: PrefsManager
    private var permissionGuideStep = initialPermissionGuideStep()
    private var permissionDialogShowing = false
    private var returningFromPermissionSettings = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        updatePermissionStatus()
        startOverlayServiceFromVisibleClick()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = PrefsManager(this)
        permissionGuideStep = savedInstanceState?.getInt(STATE_PERMISSION_GUIDE_STEP) ?: 0

        bindSettings()

        findViewById<Button>(R.id.start_button).setOnClickListener { startFromVisibleClick() }
        findViewById<Button>(R.id.app_details_button).apply {
            visibility = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) View.VISIBLE else View.GONE
            setOnClickListener { openAppDetailsSettings() }
        }
        findViewById<TextView>(R.id.restricted_settings_help).visibility =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.overlay_permission_button).setOnClickListener { openOverlaySettings() }
        findViewById<Button>(R.id.usage_permission_button).setOnClickListener { openUsageSettings() }
        findViewById<Button>(R.id.stop_button).setOnClickListener {
            stopService(Intent(this, OverlayService::class.java))
        }
        findViewById<Button>(R.id.save_settings_button).setOnClickListener { saveSettings() }
        findViewById<Button>(R.id.reset_position_button).setOnClickListener {
            prefs.resetPosition()
            ContextCompat.startForegroundService(
                this,
                Intent(this, OverlayService::class.java).setAction(OverlayServiceActions.ACTION_RESET_POSITION),
            )
            Toast.makeText(this, R.string.position_reset, Toast.LENGTH_SHORT).show()
        }

        findViewById<android.view.View>(android.R.id.content).post { showNextPermissionGuide() }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        if (returningFromPermissionSettings) {
            returningFromPermissionSettings = false
            findViewById<android.view.View>(android.R.id.content).post { showNextPermissionGuide() }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_PERMISSION_GUIDE_STEP, permissionGuideStep)
        super.onSaveInstanceState(outState)
    }

    private fun startFromVisibleClick() {
        if (!Settings.canDrawOverlays(this)) {
            showPermissionDialog(
                title = R.string.overlay_permission_title,
                message = R.string.overlay_permission_reason,
                optional = false,
                onContinue = ::openOverlaySettings,
            )
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            AlertDialog.Builder(this)
                .setTitle(R.string.notification_permission_title)
                .setMessage(R.string.notification_permission_reason)
                .setPositiveButton(R.string.permission_continue) { _, _ ->
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                .setNegativeButton(R.string.permission_not_now) { _, _ ->
                    startOverlayServiceFromVisibleClick()
                }
                .show()
            return
        }
        startOverlayServiceFromVisibleClick()
    }

    private fun startOverlayServiceFromVisibleClick() {
        ContextCompat.startForegroundService(
            this,
            Intent(this, OverlayService::class.java).setAction(OverlayServiceActions.ACTION_SHOW),
        )
    }

    private fun updatePermissionStatus() {
        val overlay = Settings.canDrawOverlays(this)
        val battery = isIgnoringBatteryOptimizations()
        val usage = hasUsageAccess()
        val notification = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        findViewById<TextView>(R.id.permission_status).text = getString(
            R.string.permission_status,
            getString(if (overlay) R.string.permission_granted else R.string.permission_required),
            getString(if (battery) R.string.permission_granted else R.string.permission_optional),
            getString(if (usage) R.string.permission_granted else R.string.permission_optional),
            getString(if (notification) R.string.permission_granted else R.string.permission_optional),
        )
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
        bindSeekBar(
            R.id.pet_size_seek,
            R.id.pet_size_label,
            prefs.petSizeDp,
            R.string.pet_size_setting,
        )
        bindSeekBar(
            R.id.bubble_duration_seek,
            R.id.bubble_duration_label,
            prefs.bubbleDurationSeconds,
            R.string.bubble_duration_setting,
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
        prefs.petSizeDp = findViewById<SeekBar>(R.id.pet_size_seek).progress
        prefs.bubbleDurationSeconds = findViewById<SeekBar>(R.id.bubble_duration_seek).progress
        if (Settings.canDrawOverlays(this)) {
            ContextCompat.startForegroundService(
                this,
                Intent(this, OverlayService::class.java).setAction(OverlayServiceActions.ACTION_RELOAD),
            )
        }
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
    }

    private fun showNextPermissionGuide() {
        if (permissionDialogShowing) return
        when (permissionGuideStep) {
            GUIDE_RESTRICTED_SETTINGS -> {
                permissionGuideStep++
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                    showRestrictedSettingsDialog()
                } else {
                    showNextPermissionGuide()
                }
            }

            GUIDE_OVERLAY -> {
                permissionGuideStep++
                if (Settings.canDrawOverlays(this)) showNextPermissionGuide() else showPermissionDialog(
                    title = R.string.overlay_permission_title,
                    message = R.string.overlay_permission_reason,
                    optional = false,
                    onContinue = ::openOverlaySettings,
                )
            }

            GUIDE_BATTERY -> {
                permissionGuideStep++
                if (isIgnoringBatteryOptimizations()) showNextPermissionGuide() else showPermissionDialog(
                    title = R.string.battery_permission_title,
                    message = R.string.battery_permission_reason,
                    optional = true,
                    onContinue = ::openBatterySettings,
                )
            }

            GUIDE_USAGE -> {
                permissionGuideStep++
                if (hasUsageAccess()) showNextPermissionGuide() else showPermissionDialog(
                    title = R.string.usage_permission_title,
                    message = R.string.usage_permission_reason,
                    optional = true,
                    onContinue = ::openUsageSettings,
                )
            }
        }
    }

    private fun showRestrictedSettingsDialog() {
        permissionDialogShowing = true
        AlertDialog.Builder(this)
            .setTitle(R.string.restricted_settings_title)
            .setMessage(R.string.restricted_settings_reason)
            .setPositiveButton(R.string.open_app_details_button) { _, _ ->
                permissionDialogShowing = false
                openAppDetailsSettings()
            }
            .setNegativeButton(R.string.restricted_settings_done) { _, _ ->
                permissionDialogShowing = false
                showNextPermissionGuide()
            }
            .setOnCancelListener {
                permissionDialogShowing = false
                showNextPermissionGuide()
            }
            .show()
    }

    private fun showPermissionDialog(
        title: Int,
        message: Int,
        optional: Boolean,
        onContinue: () -> Unit,
    ) {
        permissionDialogShowing = true
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.permission_continue) { _, _ ->
                permissionDialogShowing = false
                onContinue()
            }
            .apply {
                if (optional) {
                    setNegativeButton(R.string.permission_not_now) { _, _ ->
                        permissionDialogShowing = false
                        showNextPermissionGuide()
                    }
                }
            }
            .setOnCancelListener {
                permissionDialogShowing = false
                showNextPermissionGuide()
            }
            .show()
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

    private fun openBatterySettings() {
        openFirstAvailableSettings(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri()),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            appDetailsIntent(),
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
                returningFromPermissionSettings = true
                startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
                // Try the next public Settings entry point on this device.
            } catch (_: SecurityException) {
                // OEM Settings can reject an otherwise valid public action.
            }
        }
        returningFromPermissionSettings = false
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

    private companion object {
        const val GUIDE_RESTRICTED_SETTINGS = 0
        const val GUIDE_OVERLAY = 1
        const val GUIDE_BATTERY = 2
        const val GUIDE_USAGE = 3
        const val STATE_PERMISSION_GUIDE_STEP = "permission_guide_step"

        fun initialPermissionGuideStep(): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                GUIDE_RESTRICTED_SETTINGS
            } else {
                GUIDE_OVERLAY
            }
    }
}
