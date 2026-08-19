package com.walnut.beaconfinder.data.processing

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CooldownTracker @Inject constructor() {

    private val cooldowns = ConcurrentHashMap<String, Long>()

    fun canNotify(identityKey: String, cooldownMs: Long = 30_000L): Boolean {
        val lastNotification = cooldowns[identityKey] ?: 0L
        val now = System.currentTimeMillis()
        if (now - lastNotification < cooldownMs) {
            return false
        }
        cooldowns[identityKey] = now
        return true
    }

    fun recordNotification(identityKey: String) {
        cooldowns[identityKey] = System.currentTimeMillis()
    }

    fun reset(identityKey: String) {
        cooldowns.remove(identityKey)
    }

    fun clear() = cooldowns.clear()
}
