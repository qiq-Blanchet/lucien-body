package com.luc.body

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import com.luc.body.behavior.AppEventSink
import com.luc.body.behavior.AppPackageCategories
import com.luc.body.behavior.AppSceneClassifier
import com.luc.body.behavior.AppSenseManager
import com.luc.body.behavior.LonelinessTracker
import com.luc.body.behavior.SelfTalkManager
import com.luc.body.behavior.TapFrequencyTracker
import com.luc.body.behavior.TimeSlotManager
import com.luc.body.behavior.UsageStatsForegroundPackageSource
import com.luc.body.behavior.launcherPackages
import com.luc.body.gesture.FlingGesture
import com.luc.body.network.ClawdEvent
import com.luc.body.network.EventBatcher
import com.luc.body.network.PollingLoop
import com.luc.body.network.SupabaseClient
import com.luc.body.network.SupabaseConfig
import com.luc.body.network.SupabaseRealtimeClient
import com.luc.body.overlay.MiniMenuActions
import com.luc.body.overlay.OverlayController
import com.luc.body.overlay.OverlayGeometry
import com.luc.body.overlay.OverlayInteractionCallbacks
import com.luc.body.state.Cancelable
import com.luc.body.state.DelayScheduler
import com.luc.body.state.Expression
import com.luc.body.state.StateCoordinator
import com.luc.body.util.PrefsManager
import java.util.concurrent.Executor
import kotlin.random.Random
import okhttp3.OkHttpClient

class OverlayService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainExecutor = Executor { command -> mainHandler.post(command) }
    private val scheduler = HandlerDelayScheduler(mainHandler)
    private val random = Random.Default

    private lateinit var prefs: PrefsManager
    private var overlayController: OverlayController? = null
    private var stateCoordinator: StateCoordinator? = null
    private var supabaseClient: SupabaseClient? = null
    private var realtimeClient: SupabaseRealtimeClient? = null
    private var eventBatcher: EventBatcher? = null
    private var httpClient: OkHttpClient? = null
    private var selfTalkManager: SelfTalkManager? = null
    private var lonelinessTracker: LonelinessTracker? = null
    private var appSenseManager: AppSenseManager? = null
    private var tapFrequencyTracker: TapFrequencyTracker? = null
    private var timeSlotManager: TimeSlotManager? = null
    private var timeSlotTask: Cancelable? = null
    private var runtimeStarted = false
    private var overlayVisible = true

    override fun onCreate() {
        super.onCreate()
        prefs = PrefsManager(this)
        createNotificationChannel()
        enterForeground(buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            OverlayServiceActions.ACTION_EXIT -> {
                stopSelf()
                return START_NOT_STICKY
            }
            OverlayServiceActions.ACTION_HIDE -> {
                if (!runtimeStarted) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                setOverlayVisible(false)
                return START_STICKY
            }
            OverlayServiceActions.ACTION_RESET_POSITION -> {
                if (!runtimeStarted) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                prefs.resetPosition()
                overlayController?.resetPosition()
                return START_STICKY
            }
            OverlayServiceActions.ACTION_RELOAD -> {
                if (!runtimeStarted) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                tearDownRuntime()
            }
        }

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!ensureRuntimeStarted()) return START_NOT_STICKY
        setOverlayVisible(true)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        tearDownRuntime()
        super.onDestroy()
    }

    private fun ensureRuntimeStarted(): Boolean {
        if (runtimeStarted) return true

        val density = resources.displayMetrics.density
        lateinit var coordinator: StateCoordinator
        lateinit var controller: OverlayController
        val interactions = OverlayInteractionCallbacks(
            onTap = { handleTap(fromStuck = false) },
            onStuckTap = { handleTap(fromStuck = true) },
            onDoubleTap = ::handleDoubleTap,
            onHeartParticles = { controller.showHeartParticles() },
            onLongPressMenu = { actions -> controller.showMiniMenu(actions) },
            menuActions = MiniMenuActions(
                onPoke = { handleTap(fromStuck = false) },
                onPetHead = ::handlePetHead,
                onHide = { sendServiceAction(OverlayServiceActions.ACTION_HIDE) },
                onSettings = {
                    startActivity(
                        Intent(this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    )
                },
            ),
            onDragStarted = { fromStuck ->
                recordInteraction()
                coordinator.beginDrag(fromStuck)
                enqueueEvent("drag_start", mapOf("from_stuck" to fromStuck))
            },
            onDragEnded = { isStuck ->
                coordinator.endDrag(isStuck)
                enqueueEvent("drag_end", mapOf("stuck" to isStuck))
                if (!isStuck && random.nextInt(5) == 0) coordinator.onTransientState(Expression.CLINGY)
            },
            onFling = { fling, expression -> handleFling(coordinator, fling, expression) },
            onFlingSettled = { isStuck -> coordinator.setStuck(isStuck) },
        )
        controller = OverlayController(
            context = this,
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager,
            geometry = OverlayGeometry(density, prefs.petSizeDp),
            petSizeDp = prefs.petSizeDp,
            initialPosition = prefs.savedPosition(),
            onPositionSettled = prefs::savePosition,
            interactions = interactions,
        )
        coordinator = StateCoordinator(
            uiSink = controller,
            scheduler = scheduler,
            localBubbleText = "在呢。",
            bubbleBaseDurationSeconds = prefs.bubbleDurationSeconds,
            localExpression = { Expression.HAPPY },
        )

        try {
            controller.show()
        } catch (_: RuntimeException) {
            coordinator.close()
            controller.remove()
            stopSelf()
            return false
        }

        overlayController = controller
        stateCoordinator = coordinator
        runtimeStarted = true
        overlayVisible = true

        val selfTalk = SelfTalkManager(
            coordinator = coordinator,
            scheduler = scheduler,
            frequencyMinutes = prefs.selfTalkFrequencyMinutes,
            enabled = prefs.selfTalkEnabled,
        )
        val loneliness = LonelinessTracker(
            coordinator = coordinator,
            selfTalkManager = selfTalk,
            scheduler = scheduler,
            enabled = prefs.lonelinessEnabled,
        )
        val timeSlot = TimeSlotManager(coordinator)
        val tapFrequency = TapFrequencyTracker(coordinator)
        selfTalkManager = selfTalk
        lonelinessTracker = loneliness
        timeSlotManager = timeSlot
        tapFrequencyTracker = tapFrequency

        timeSlot.refresh()
        scheduleTimeSlotRefresh()
        selfTalk.start()
        loneliness.start()

        startNetworkIfConfigured(coordinator, selfTalk, loneliness)
        val appSense = AppSenseManager(
            coordinator = coordinator,
            scheduler = scheduler,
            packageSource = UsageStatsForegroundPackageSource(this),
            classifier = AppSceneClassifier(AppPackageCategories(launcherPackages(this))),
            eventSink = AppEventSink { eventType, payload -> enqueueEvent(eventType, payload) },
            enabled = prefs.appAwarenessEnabled,
        )
        appSenseManager = appSense
        appSense.start()
        updateNotification()
        return true
    }

    private fun startNetworkIfConfigured(
        coordinator: StateCoordinator,
        selfTalk: SelfTalkManager,
        loneliness: LonelinessTracker,
    ) {
        val config = runCatching {
            SupabaseConfig(prefs.supabaseUrl, prefs.supabaseKey).requireValid()
        }.getOrNull() ?: return
        val okHttp = OkHttpClient()
        val client = SupabaseClient(config, okHttp)
        val poller = PollingLoop(
            client = client,
            scheduler = scheduler,
            ownerExecutor = mainExecutor,
            onState = { state ->
                selfTalk.onInteraction()
                loneliness.onInteraction()
                coordinator.onRemoteState(state)
            },
        )
        val realtime = SupabaseRealtimeClient(
            config = config,
            httpClient = okHttp,
            pollingLoop = poller,
            scheduler = scheduler,
            ownerExecutor = mainExecutor,
            onState = { state ->
                selfTalk.onInteraction()
                loneliness.onInteraction()
                coordinator.onRemoteState(state)
            },
        )
        httpClient = okHttp
        supabaseClient = client
        eventBatcher = EventBatcher(client, scheduler)
        realtimeClient = realtime
        realtime.start()
    }

    private fun handleTap(fromStuck: Boolean) {
        val coordinator = stateCoordinator ?: return
        recordInteraction()
        if (fromStuck) coordinator.onStuckTap("在呢。") else coordinator.onLocalTap()
        tapFrequencyTracker?.onTap()
        enqueueEvent(if (fromStuck) "stuck_tap" else "tap")
    }

    private fun handleDoubleTap() {
        val coordinator = stateCoordinator ?: return
        recordInteraction()
        coordinator.onDoubleTap(HAPPY_MESSAGES.random(random))
        enqueueEvent("double_tap")
    }

    private fun handlePetHead() {
        val coordinator = stateCoordinator ?: return
        recordInteraction()
        coordinator.startLocalReaction(Expression.HAPPY, "被摸了 ꒪¯꒳¯꒪")
        enqueueEvent("pet_head")
    }

    private fun handleFling(
        coordinator: StateCoordinator,
        fling: FlingGesture,
        expression: Expression,
    ) {
        coordinator.onTransientState(expression)
        enqueueEvent(
            "fling",
            mapOf(
                "direction" to fling.direction.name.lowercase(),
                "velocity_x" to fling.velocityX,
                "velocity_y" to fling.velocityY,
            ),
        )
    }

    private fun recordInteraction() {
        selfTalkManager?.onInteraction()
        lonelinessTracker?.onInteraction()
    }

    private fun enqueueEvent(eventType: String, payload: Map<String, Any?> = emptyMap()) {
        eventBatcher?.enqueue(ClawdEvent(eventType, payload))
    }

    private fun scheduleTimeSlotRefresh() {
        timeSlotTask?.cancel()
        timeSlotTask = scheduler.schedule(TIME_SLOT_REFRESH_MS) {
            if (!runtimeStarted) return@schedule
            timeSlotManager?.refresh()
            scheduleTimeSlotRefresh()
        }
    }

    private fun setOverlayVisible(visible: Boolean) {
        if (overlayVisible == visible && (visible.not() || overlayController != null)) return
        overlayVisible = visible
        if (visible) {
            overlayController?.show()
            stateCoordinator?.refresh()
        } else {
            overlayController?.remove()
        }
        updateNotification()
    }

    private fun tearDownRuntime() {
        if (!runtimeStarted && overlayController == null) return
        runtimeStarted = false
        timeSlotTask?.cancel()
        timeSlotTask = null
        appSenseManager?.close()
        lonelinessTracker?.close()
        selfTalkManager?.close()
        timeSlotManager?.close()
        tapFrequencyTracker?.close()
        realtimeClient?.close()
        eventBatcher?.close()
        supabaseClient?.cancelAll()
        stateCoordinator?.close()
        overlayController?.remove()
        httpClient?.dispatcher?.executorService?.shutdown()
        httpClient?.connectionPool?.evictAll()
        appSenseManager = null
        lonelinessTracker = null
        selfTalkManager = null
        timeSlotManager = null
        tapFrequencyTracker = null
        realtimeClient = null
        eventBatcher = null
        supabaseClient = null
        stateCoordinator = null
        overlayController = null
        httpClient = null
    }

    private fun sendServiceAction(action: String) {
        startService(Intent(this, OverlayService::class.java).setAction(action))
    }

    private fun buildNotification(): Notification {
        val toggleAction = if (overlayVisible) OverlayServiceActions.ACTION_HIDE else OverlayServiceActions.ACTION_SHOW
        val toggleTitle = if (overlayVisible) R.string.notification_hide else R.string.notification_show
        val contentIntent = PendingIntent.getActivity(
            this,
            REQUEST_CONTENT,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, getString(toggleTitle), servicePendingIntent(toggleAction, REQUEST_TOGGLE))
            .addAction(0, getString(R.string.notification_exit), servicePendingIntent(OverlayServiceActions.ACTION_EXIT, REQUEST_EXIT))
            .build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        this,
        requestCode,
        Intent(this, OverlayService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
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
        const val REQUEST_CONTENT = 10
        const val REQUEST_TOGGLE = 11
        const val REQUEST_EXIT = 12
        const val TIME_SLOT_REFRESH_MS = 60_000L
        val HAPPY_MESSAGES = listOf("在呢。", "今天也要加油", "今天辛苦了")
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
