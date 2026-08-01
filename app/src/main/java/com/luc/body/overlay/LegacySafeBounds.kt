package com.luc.body.overlay

import kotlin.math.max

data class EdgeInsetsPx(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

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
