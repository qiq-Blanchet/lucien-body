package com.luc.body.overlay

import com.luc.body.gesture.FlingGesture
import com.luc.body.state.Expression

data class MiniMenuActions(
    val onPoke: () -> Unit,
    val onPetHead: () -> Unit,
    val onHide: () -> Unit,
)

data class OverlayInteractionCallbacks(
    val onTap: (eventTimeMs: Long) -> Unit,
    val onStuckTap: (eventTimeMs: Long) -> Unit,
    val onDoubleTap: (firstEventTimeMs: Long, secondEventTimeMs: Long) -> Unit,
    val onHeartParticles: () -> Unit,
    val onLongPressMenu: (MiniMenuActions) -> Unit,
    val menuActions: MiniMenuActions,
    val onDragStarted: (fromStuck: Boolean) -> Unit,
    val onDragEnded: (isStuck: Boolean) -> Unit,
    val onFling: (FlingGesture, Expression) -> Unit,
    val onFlingSettled: (isStuck: Boolean) -> Unit,
) {
    fun dispatchTap(fromStuck: Boolean, eventTimeMs: Long) {
        if (fromStuck) onStuckTap(eventTimeMs) else onTap(eventTimeMs)
    }

    fun dispatchDoubleTap(firstEventTimeMs: Long, secondEventTimeMs: Long) {
        onDoubleTap(firstEventTimeMs, secondEventTimeMs)
        onHeartParticles()
    }

    fun dispatchLongPress() = onLongPressMenu(menuActions)
}
