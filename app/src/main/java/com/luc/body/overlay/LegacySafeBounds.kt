package com.luc.body.overlay

import kotlin.math.max

data class EdgeInsetsPx(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    fun maxPerEdge(other: EdgeInsetsPx): EdgeInsetsPx = EdgeInsetsPx(
        left = max(left, other.left),
        top = max(top, other.top),
        right = max(right, other.right),
        bottom = max(bottom, other.bottom),
    )
}

object LegacySafeBounds {
    fun fromRealDisplay(
        width: Int,
        height: Int,
        systemInsets: EdgeInsetsPx,
        cutoutInsets: EdgeInsetsPx,
    ): SafeBoundsPx = OverlayMovementBounds.fromDisplay(
        displayBounds = SafeBoundsPx(0, 0, width, height),
        insets = systemInsets.maxPerEdge(cutoutInsets),
    )
}

object OverlayMovementBounds {
    fun fromDisplay(displayBounds: SafeBoundsPx, insets: EdgeInsetsPx): SafeBoundsPx =
        SafeBoundsPx(
            left = displayBounds.left,
            top = displayBounds.top,
            right = displayBounds.right,
            bottom = (displayBounds.bottom - insets.bottom).coerceAtLeast(displayBounds.top),
        )
}
