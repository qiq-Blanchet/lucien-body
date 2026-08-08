package com.luc.body.behavior

import com.luc.body.state.Cancelable
import com.luc.body.state.ContextSource
import com.luc.body.state.DelayScheduler
import com.luc.body.state.Expression
import com.luc.body.state.StateCoordinator

fun interface ForegroundPackageSource {
    fun currentPackage(): String?
}

fun interface AppEventSink {
    fun report(eventType: String, payload: Map<String, String>)
}

data class AppPackageCategories(
    val launchers: Set<String>,
)

enum class AppScene {
    DESKTOP,
    OTHER,
}

class AppSceneClassifier(
    private val categories: AppPackageCategories,
) {
    fun classify(packageName: String): AppScene = when (packageName) {
        in categories.launchers -> AppScene.DESKTOP
        else -> AppScene.OTHER
    }
}

class AppSenseManager(
    private val coordinator: StateCoordinator,
    private val scheduler: DelayScheduler,
    private val packageSource: ForegroundPackageSource,
    private val classifier: AppSceneClassifier,
    private val eventSink: AppEventSink,
    enabled: Boolean = true,
) : AutoCloseable {
    private var enabled = enabled
    private var closed = false
    private var generation = 0L
    private var task: Cancelable? = null
    private var previousPackage: String? = null
    private var previousScene: AppScene? = null

    fun start() {
        if (closed || task != null || !enabled) return
        scheduleNext()
    }

    fun setEnabled(enabled: Boolean) {
        if (closed || this.enabled == enabled) return
        this.enabled = enabled
        cancelScheduled()
        if (enabled) scheduleNext()
    }

    override fun close() {
        if (closed) return
        closed = true
        cancelScheduled()
    }

    private fun scheduleNext() {
        val callbackGeneration = ++generation
        val scheduled = scheduler.schedule(POLL_INTERVAL_MS) {
            if (closed || !enabled || callbackGeneration != generation) return@schedule
            task = null
            pollOnce()
            scheduleNext()
        }
        if (closed || !enabled || callbackGeneration != generation) {
            scheduled.cancel()
        } else {
            task = scheduled
        }
    }

    private fun pollOnce() {
        val packageName = packageSource.currentPackage()?.takeIf(String::isNotBlank) ?: return
        if (packageName == previousPackage) return

        val scene = classifier.classify(packageName)
        eventSink.report(APP_FOREGROUND_EVENT, mapOf(PACKAGE_PAYLOAD_KEY to packageName))
        when (scene) {
            AppScene.DESKTOP -> {
                coordinator.setContextState(ContextSource.APP, Expression.IDLE)
                if (previousScene != null && previousScene != AppScene.DESKTOP) {
                    coordinator.onTransientState(Expression.WAVING)
                }
            }
            AppScene.OTHER -> coordinator.setContextState(ContextSource.APP, Expression.PEEKING)
        }
        previousPackage = packageName
        previousScene = scene
    }

    private fun cancelScheduled() {
        generation += 1
        task?.cancel()
        task = null
    }

    companion object {
        const val POLL_INTERVAL_MS = 5_000L
        const val APP_FOREGROUND_EVENT = "app_foreground"
        const val PACKAGE_PAYLOAD_KEY = "package"
    }
}
