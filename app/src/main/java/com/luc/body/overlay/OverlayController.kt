package com.luc.body.overlay

import android.content.Context
import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.graphics.Point
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.webkit.WebView
import com.luc.body.gesture.GestureClassifier
import com.luc.body.gesture.PetGestureController
import com.luc.body.state.UiSink
import com.luc.body.state.VisibleState
import com.luc.body.web.WebRenderer

class OverlayController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val geometry: OverlayGeometry,
    private val onTap: () -> Unit,
) : UiSink {
    private var petView: WebView? = null
    private var bubbleView: WebView? = null
    private var renderer: WebRenderer? = null
    private var petParams: WindowManager.LayoutParams? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var placementTracker: OverlayPlacementTracker? = null
    private var componentCallbacks: ComponentCallbacks? = null

    fun show() {
        if (petView != null || bubbleView != null) return

        val density = context.resources.displayMetrics.density
        val pet = WebView(context)
        val bubble = WebView(context)
        val newRenderer = WebRenderer(pet, bubble)
        val newPetParams = OverlayWindowSpec.pet().toLayoutParams(density)
        val newBubbleParams = OverlayWindowSpec.bubble().toLayoutParams(density)
        val tracker = OverlayPlacementTracker(geometry, ::safeBounds)

        petView = pet
        bubbleView = bubble
        renderer = newRenderer
        petParams = newPetParams
        bubbleParams = newBubbleParams
        placementTracker = tracker
        applyPlacement(tracker.initialPlacement())
        pet.setOnTouchListener(
            PetGestureController(
                classifier = GestureClassifier.fromDensity(density),
                onMove = ::moveBy,
                onTap = onTap,
            ),
        )
        var bubbleAdded = false
        var petAdded = false
        try {
            windowManager.addView(bubble, newBubbleParams)
            bubbleAdded = true
            windowManager.addView(pet, newPetParams)
            petAdded = true
            registerConfigurationCallback(pet)
            postReclampAfterInsets(pet)
        } catch (error: RuntimeException) {
            cleanUpFailedShow(pet, bubble, petAdded, bubbleAdded)
            throw error
        }
    }

    fun moveBy(deltaX: Float, deltaY: Float) {
        val tracker = placementTracker ?: return
        applyPlacement(tracker.moveBy(deltaX, deltaY))
    }

    private fun reclampToCurrentBounds() {
        val tracker = placementTracker ?: return
        applyPlacement(tracker.reclampToCurrentBounds())
    }

    override fun render(state: VisibleState) {
        renderer?.render(state)
    }

    fun remove() {
        val pet = petView
        val bubble = bubbleView
        if (pet == null && bubble == null) return
        pet?.setOnTouchListener(null)
        pet?.let(::removeViewIgnoringAlreadyRemoved)
        bubble?.let(::removeViewIgnoringAlreadyRemoved)
        pet?.stopLoading()
        bubble?.stopLoading()
        pet?.destroy()
        bubble?.destroy()
        clearReferences()
    }

    private fun cleanUpFailedShow(
        pet: WebView,
        bubble: WebView,
        petAdded: Boolean,
        bubbleAdded: Boolean,
    ) {
        pet.setOnTouchListener(null)
        if (petAdded) removeViewIgnoringAlreadyRemoved(pet)
        if (bubbleAdded) removeViewIgnoringAlreadyRemoved(bubble)
        pet.stopLoading()
        bubble.stopLoading()
        pet.destroy()
        bubble.destroy()
        clearReferences()
    }

    private fun clearReferences() {
        componentCallbacks?.let(context::unregisterComponentCallbacks)
        componentCallbacks = null
        petView = null
        bubbleView = null
        renderer = null
        petParams = null
        bubbleParams = null
        placementTracker = null
    }

    private fun applyPlacement(placement: OverlayPlacementPx) {
        val bubble = bubbleView ?: return
        val pet = petView ?: return
        val currentBubbleParams = bubbleParams ?: return
        val currentPetParams = petParams ?: return
        currentBubbleParams.x = placement.bubbleX
        currentBubbleParams.y = placement.bubbleY
        currentPetParams.x = placement.petX
        currentPetParams.y = placement.petY
        if (bubble.isAttachedToWindow) windowManager.updateViewLayout(bubble, currentBubbleParams)
        if (pet.isAttachedToWindow) windowManager.updateViewLayout(pet, currentPetParams)
    }

    @Suppress("DEPRECATION")
    private fun safeBounds(): SafeBoundsPx {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
            )
            return SafeBoundsPx(
                left = metrics.bounds.left + insets.left,
                top = metrics.bounds.top + insets.top,
                right = metrics.bounds.right - insets.right,
                bottom = metrics.bounds.bottom - insets.bottom,
            )
        }
        @Suppress("DEPRECATION")
        val display = windowManager.defaultDisplay
        val rootInsets = petView?.rootWindowInsets
        if (rootInsets != null) {
            val realSize = Point().also(display::getRealSize)
            return LegacySafeBounds.fromRealDisplay(
                width = realSize.x,
                height = realSize.y,
                systemInsets = EdgeInsetsPx(
                    left = rootInsets.stableInsetLeft,
                    top = rootInsets.stableInsetTop,
                    right = rootInsets.stableInsetRight,
                    bottom = rootInsets.stableInsetBottom,
                ),
                cutoutInsets = displayCutoutInsets(rootInsets),
            )
        }
        val availableSize = Point().also(display::getSize)
        return SafeBoundsPx(0, 0, availableSize.x, availableSize.y)
    }

    private fun displayCutoutInsets(windowInsets: WindowInsets): EdgeInsetsPx {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return ZERO_INSETS
        val cutout = windowInsets.displayCutout ?: return ZERO_INSETS
        return EdgeInsetsPx(
            left = cutout.safeInsetLeft,
            top = cutout.safeInsetTop,
            right = cutout.safeInsetRight,
            bottom = cutout.safeInsetBottom,
        )
    }

    private fun registerConfigurationCallback(pet: WebView) {
        val callbacks = object : ComponentCallbacks {
            @Suppress("DEPRECATION")
            override fun onConfigurationChanged(newConfig: Configuration) {
                postReclampAfterInsets(pet)
            }

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onLowMemory() = Unit
        }
        context.registerComponentCallbacks(callbacks)
        componentCallbacks = callbacks
    }

    private fun postReclampAfterInsets(pet: WebView) {
        pet.requestApplyInsets()
        pet.post {
            if (petView !== pet) return@post
            pet.requestApplyInsets()
            pet.post {
                if (petView === pet) reclampToCurrentBounds()
            }
        }
    }

    private fun OverlayWindowSpec.toLayoutParams(density: Float): WindowManager.LayoutParams {
        val size = sizePx(density)
        return WindowManager.LayoutParams(size.width, size.height, type, flags, pixelFormat).apply {
            gravity = this@toLayoutParams.gravity
        }
    }

    private fun removeViewIgnoringAlreadyRemoved(view: View) {
        try {
            windowManager.removeView(view)
        } catch (_: IllegalArgumentException) {
            // A second removal is harmless during service teardown.
        }
    }

    private companion object {
        val ZERO_INSETS = EdgeInsetsPx(0, 0, 0, 0)
    }
}
