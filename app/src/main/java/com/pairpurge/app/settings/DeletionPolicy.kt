package com.pairpurge.app.settings

/** A null duration disables automatic deletion. */
data class DeletionPolicy(val days: Int? = null) {
    init { require(days == null || days in 1..3650) }

    fun isExpired(lastConnection: Long, now: Long): Boolean =
        days?.let { now >= lastConnection && now - lastConnection >= it * DAY_MILLIS } ?: false

    fun shouldDelete(
        lastConnection: Long,
        now: Long,
        protected: Boolean,
        connected: Boolean?,
    ): Boolean = !protected && connected == false && isExpired(lastConnection, now)

    companion object {
        const val DAY_MILLIS = 86_400_000L
    }
}
