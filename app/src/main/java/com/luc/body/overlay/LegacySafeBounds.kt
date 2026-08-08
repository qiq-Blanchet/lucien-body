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
    ): SafeBoundsPx = SafeBoundsPx(
        left = max(systemInsets.left, cutoutInsets.left),
        top = max(systemInsets.top, cutoutInsets.top),
        right = width - max(systemInsets.right, cutoutInsets.right),
        bottom = height - max(systemInsets.bottom, cutoutInsets.bottom),
    )
}
