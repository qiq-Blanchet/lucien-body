package com.luc.body

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.luc.body.network.PollingLoop
import com.luc.body.network.SupabaseClient
import com.luc.body.network.SupabaseConfig
import com.luc.body.overlay.OverlayController
import com.luc.body.overlay.OverlayGeometry
import com.luc.body.state.Cancelable
import com.luc.body.state.DelayScheduler
import com.luc.body.state.Expression
import com.luc.body.state.StateCoordinator
import java.util.UUID
import java.util.concurrent.Executor

class OverlayService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainExecutor = Executor { command -> mainHandler.post(command) }
    private val scheduler = HandlerDelayScheduler(mainHandler)

    private var overlayController: OverlayController? = null
    private var stateCoordinator: StateCoordinator? = null
    private var supabaseClient: SupabaseClient? = null
    private var pollingLoop: PollingLoop? = null
    private var runtimeStarted = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        enterForeground(
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_text))
                .setOngoing(true)
                .build(),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        return if (ensureRuntimeStarted()) START_STICKY else START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        pollingLoop?.stop()
        stateCoordinator?.close()
        overlayController?.remove()
        supabaseClient?.cancelAll()
        pollingLoop = null
        stateCoordinator = null
        overlayController = null
        supabaseClient = null
        runtimeStarted = false
        super.onDestroy()
    }

    private fun ensureRuntimeStarted(): Boolean {
        if (runtimeStarted) return true
        val config = try {
            SupabaseConfig(
                baseUrl = BuildConfig.SUPABASE_URL,
                publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
            ).requireValid()
        } catch (_: IllegalArgumentException) {
            stopSelf()
            return false
        }

        val client = SupabaseClient(config)
        lateinit var coordinator: StateCoordinator
        val controller = OverlayController(
            context = this,
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager,
            geometry = OverlayGeometry(resources.displayMetrics.density),
            onTap = {
                coordinator.onLocalTap()
                client.postTap(UUID.randomUUID()) { }
            },
        )
        coordinator = StateCoordinator(
            uiSink = controller,
            scheduler = scheduler,
            localExpression = { Expression.HAPPY },
        )
        val poller = PollingLoop(
            client = client,
            scheduler = scheduler,
            ownerExecutor = mainExecutor,
            onState = coordinator::onRemoteState,
        )

        try {
            controller.show()
        } catch (_: RuntimeException) {
            coordinator.close()
            controller.remove()
            client.cancelAll()
            stopSelf()
            return false
        }

        overlayController = controller
        stateCoordinator = coordinator
        supabaseClient = client
        pollingLoop = poller
        runtimeStarted = true
        poller.start()
        return true
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun enterForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private companion object {
        const val CHANNEL_ID = "luc_overlay"
        const val NOTIFICATION_ID = 1
    }
}

private class HandlerDelayScheduler(
    private val handler: Handler,
) : DelayScheduler {
    override fun schedule(delayMs: Long, action: () -> Unit): Cancelable {
        val runnable = Runnable(action)
        handler.postDelayed(runnable, delayMs)
        return Cancelable { handler.removeCallbacks(runnable) }
    }
}
