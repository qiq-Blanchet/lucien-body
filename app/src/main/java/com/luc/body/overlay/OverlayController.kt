package com.luc.body.overlay

import android.content.Context
import android.graphics.Rect
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
        } catch (error: RuntimeException) {
            cleanUpFailedShow(pet, bubble, petAdded, bubbleAdded)
            throw error
        }
    }

    fun moveBy(deltaX: Float, deltaY: Float) {
        val tracker = placementTracker ?: return
        applyPlacement(tracker.moveBy(deltaX, deltaY))
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
        val bounds = Rect().also { windowManager.defaultDisplay.getRectSize(it) }
        return SafeBoundsPx(bounds.left, bounds.top, bounds.right, bounds.bottom)
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
}
