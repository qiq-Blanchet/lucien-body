package com.luc.body.sprite

class SpritePlaybackGate(
    private val catalog: SpriteCatalog,
    private val monotonicClockMs: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private var currentStateId: String? = null
    private var currentAsset: SpriteAsset? = null
    private var holdUntilMs = 0L

    fun select(stateId: String): SpriteAsset {
        val nowMs = monotonicClockMs()
        val current = currentAsset
        if (stateId == currentStateId && current != null && nowMs < holdUntilMs) return current

        return catalog.choose(stateId).also { selected ->
            currentStateId = stateId
            currentAsset = selected
            holdUntilMs = nowMs +
                selected.cycleDurationMs.coerceAtLeast(MINIMUM_CYCLE_MS) +
                LOAD_START_GUARD_MS
        }
    }

    private companion object {
        const val MINIMUM_CYCLE_MS = 1_000L
        const val LOAD_START_GUARD_MS = 1_000L
    }
}
