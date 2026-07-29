package com.luc.body.state

fun interface Cancelable {
    fun cancel()
}

fun interface DelayScheduler {
    fun schedule(delayMs: Long, action: () -> Unit): Cancelable
}
