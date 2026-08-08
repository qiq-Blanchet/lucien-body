package com.luc.body.sprite

import java.util.Locale
import kotlin.random.Random

data class SpriteAsset(
    val fileName: String,
    val url: String,
)

class SpriteCatalog(
    assets: List<SpriteAsset>,
    private val stateIds: Set<String> = DEFAULT_STATE_IDS,
    private val randomIndex: (Int) -> Int = Random.Default::nextInt,
) {
    private val pools = assets
        .filter { it.fileName.endsWith(".svg", ignoreCase = true) }
        .groupBy { asset -> stateFor(asset.fileName) }
        .filterKeys { it != null }
        .mapKeys { (stateId, _) -> requireNotNull(stateId) }
        .mapValues { (_, pool) -> pool.sortedBy { it.fileName } }
    private val lastSelections = mutableMapOf<String, SpriteAsset>()

    init {
        require(!pools[IDLE].isNullOrEmpty()) { "An idle sprite is required" }
    }

    fun choose(requestedStateId: String): SpriteAsset {
        val stateId = requestedStateId.lowercase(Locale.ROOT).takeIf(pools::containsKey) ?: IDLE
        val pool = requireNotNull(pools[stateId])
        var index = randomIndex(pool.size)
        require(index in pool.indices) { "Random index must be within the sprite pool" }
        if (pool.size >= 2 && pool[index] == lastSelections[stateId]) index = (index + 1) % pool.size
        return pool[index].also { lastSelections[stateId] = it }
    }

    private fun stateFor(fileName: String): String? {
        val baseName = fileName.substringBeforeLast('.', missingDelimiterValue = fileName)
        return stateIds
            .asSequence()
            .filter { stateId -> baseName == stateId || baseName.startsWith("${stateId}_") }
            .maxByOrNull(String::length)
    }

    companion object {
        const val IDLE = "idle"
        val DEFAULT_STATE_IDS = setOf(
            "idle", "happy", "angry", "sleepy", "thinking", "talking", "love", "smug", "shocked",
            "confused", "shy", "proud", "sulky", "lonely_1", "lonely_2", "lonely_3", "waving",
            "peeking", "morning", "night", "eating", "dancing", "dizzy", "clingy", "grabbed", "stuck",
            "stuck_tap", "stuck_grab",
        )
    }
}
