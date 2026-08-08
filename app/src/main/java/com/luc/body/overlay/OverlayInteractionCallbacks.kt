package com.luc.body.overlay

import com.luc.body.gesture.FlingGesture
import com.luc.body.state.Expression

data class MiniMenuActions(
    val onPoke: () -> Unit,
    val onPetHead: () -> Unit,
    val onHide: () -> Unit,
    val onSettings: () -> Unit,
)

data class OverlayInteractionCallbacks(
    val onTap: () -> Unit,
    val onStuckTap: () -> Unit,
    val onDoubleTap: () -> Unit,
    val onHeartParticles: () -> Unit,
    val onLongPressMenu: (MiniMenuActions) -> Unit,
    val menuActions: MiniMenuActions,
    val onDragStarted: (fromStuck: Boolean) -> Unit,
    val onDragEnded: (isStuck: Boolean) -> Unit,
    val onFling: (FlingGesture, Expression) -> Unit,
    val onFlingSettled: (isStuck: Boolean) -> Unit,
) {
    fun dispatchTap(fromStuck: Boolean) {
        if (fromStuck) onStuckTap() else onTap()
    }

    fun dispatchDoubleTap() {
        onDoubleTap()
        onHeartParticles()
    }

    fun dispatchLongPress() = onLongPressMenu(menuActions)
}
