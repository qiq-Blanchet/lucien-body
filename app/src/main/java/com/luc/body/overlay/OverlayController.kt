package com.luc.body.overlay

import android.content.Context
import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.Point
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.webkit.WebView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.OverScroller
import com.luc.body.gesture.GestureClassifier
import com.luc.body.gesture.FlingGesture
import com.luc.body.gesture.PetGestureController
import com.luc.body.state.UiSink
import com.luc.body.state.VisibleState
import com.luc.body.web.WebRenderer

class OverlayController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val geometry: OverlayGeometry,
    private val petSizeDp: Int,
    private val initialPosition: Pair<Int, Int>?,
    private val onPositionSettled: (Int, Int) -> Unit,
    private val interactions: OverlayInteractionCallbacks,
) : UiSink {
    private var petView: WebView? = null
    private var bubbleView: WebView? = null
    private var renderer: WebRenderer? = null
    private var petParams: WindowManager.LayoutParams? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var placementTracker: OverlayPlacementTracker? = null
    private var componentCallbacks: ComponentCallbacks? = null
    private var popupWindow: PopupWindow? = null
    private var flingScroller: OverScroller? = null
    private var flingRunnable: Runnable? = null
    private var savedPosition = initialPosition

    fun show() {
        if (petView != null || bubbleView != null) return

        val density = context.resources.displayMetrics.density
        val pet = WebView(context)
        val bubble = WebView(context)
        val newRenderer = WebRenderer(pet, bubble)
        val newPetParams = OverlayWindowSpec.pet(petSizeDp).toLayoutParams(density)
        val newBubbleParams = OverlayWindowSpec.bubble().toLayoutParams(density)
        val tracker = OverlayPlacementTracker(geometry, ::safeBounds)

        petView = pet
        bubbleView = bubble
        renderer = newRenderer
        petParams = newPetParams
        bubbleParams = newBubbleParams
        placementTracker = tracker
        applyPlacement(tracker.initialPlacement(savedPosition))
        pet.setOnTouchListener(
            PetGestureController(
                classifier = GestureClassifier.fromDensity(density),
                isStuck = { placementTracker?.isStuck == true },
                onDragStart = { fromStuck ->
                    stopFling()
                    interactions.onDragStarted(fromStuck)
                },
                onMove = ::moveBy,
                onDragEnd = { cancelled, fling -> finishDrag(cancelled, fling) },
                onTap = interactions::dispatchTap,
                onDoubleTap = interactions::dispatchDoubleTap,
                onLongPress = interactions::dispatchLongPress,
                onFling = ::startFling,
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

    fun showHeartParticles() {
        renderer?.showHeartParticles()
    }

    fun resetPosition() {
        val placement = placementTracker?.initialPlacement() ?: return
        applyPlacement(placement)
        settlePosition(placement)
    }

    fun showMiniMenu(actions: MiniMenuActions) {
        val anchor = petView ?: return
        popupWindow?.dismiss()
        val density = context.resources.displayMetrics.density
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((8 * density).toInt(), (6 * density).toInt(), (8 * density).toInt(), (6 * density).toInt())
            setBackgroundColor(Color.WHITE)
        }
        listOf(
            "戳一下" to actions.onPoke,
            "摸摸头" to actions.onPetHead,
            "隐藏" to actions.onHide,
            "设置" to actions.onSettings,
        ).forEach { (label, action) ->
            content.addView(Button(context).apply {
                text = label
                isAllCaps = false
                setOnClickListener {
                    popupWindow?.dismiss()
                    action()
                }
            })
        }
        popupWindow = PopupWindow(
            content,
            (132 * density).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            true,
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            setOnDismissListener { if (popupWindow === this) popupWindow = null }
            showAsDropDown(anchor, 0, -(petSizeDp * density).toInt(), Gravity.CENTER_HORIZONTAL)
        }
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
        stopFling()
        popupWindow?.dismiss()
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
        popupWindow = null
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

    private fun finishDrag(cancelled: Boolean, fling: FlingGesture?) {
        val tracker = placementTracker ?: return
        val result = tracker.finishDrag(allowSnap = !cancelled && fling == null)
        applyPlacement(result.placement)
        settlePosition(result.placement)
        interactions.onDragEnded(result.edge != null)
    }

    private fun startFling(fling: FlingGesture) {
        val pet = petView ?: return
        val tracker = placementTracker ?: return
        val start = tracker.currentPlacement ?: return
        val bounds = safeBounds()
        val maxX = (bounds.right - geometry.petSizePx).coerceAtLeast(bounds.left)
        val maxY = (bounds.bottom - geometry.petSizePx).coerceAtLeast(bounds.top)
        val overfling = ViewConfiguration.get(context).scaledOverflingDistance
        val scroller = OverScroller(context).apply {
            fling(
                start.petX,
                start.petY,
                fling.velocityX.toInt(),
                fling.velocityY.toInt(),
                bounds.left,
                maxX,
                bounds.top,
                maxY,
                overfling,
                overfling,
            )
        }
        flingScroller = scroller
        var previousX = start.petX
        var previousY = start.petY
        lateinit var animation: Runnable
        animation = Runnable {
            if (flingScroller !== scroller || petView !== pet) return@Runnable
            if (scroller.computeScrollOffset()) {
                moveBy((scroller.currX - previousX).toFloat(), (scroller.currY - previousY).toFloat())
                previousX = scroller.currX
                previousY = scroller.currY
                pet.postOnAnimation(animation)
            } else {
                flingScroller = null
                flingRunnable = null
                tracker.currentPlacement?.let(::settlePosition)
            }
        }
        flingRunnable = animation
        interactions.onFling(fling, fling.direction.expression)
        pet.postOnAnimation(animation)
    }

    private fun stopFling() {
        flingScroller?.forceFinished(true)
        petView?.let { view -> flingRunnable?.let(view::removeCallbacks) }
        flingScroller = null
        flingRunnable = null
    }

    private fun settlePosition(placement: OverlayPlacementPx) {
        savedPosition = placement.petX to placement.petY
        onPositionSettled(placement.petX, placement.petY)
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
