package app.brokoli5191.zeitig.monitor

import android.content.Context
import androidx.core.content.edit
import java.time.Duration
import java.time.Instant

/**
 * The little monitoring state that must outlive the service's process: manually watched routes,
 * the user's "stop" decisions and the disruptions already announced.
 */
class MonitorPrefs(
    context: Context,
) {
    private val prefs = context.getSharedPreferences("monitor", Context.MODE_PRIVATE)

    /** Routes the user watches outside their schedule window; an entry expires after [WATCH_LIMIT]. */
    fun watched(
        now: Instant,
    ): Set<Long> =
        stamped(WATCHED).filterValues {
            Duration.between(it, now) < WATCH_LIMIT
        }.keys.mapNotNullTo(HashSet(), String::toLongOrNull)

    fun watch(
        routeId: Long,
        now: Instant,
    ) = putStamped(WATCHED, stamped(WATCHED) + (routeId.toString() to now))

    fun unwatch(
        routeId: Long,
        now: Instant,
    ) {
        putStamped(WATCHED, stamped(WATCHED) - routeId.toString())
        prefs.edit { putLong(MUTED_PREFIX + routeId, now.toEpochMilli()) }
    }

    /** The user stopped the whole countdown. */
    fun stopAll(now: Instant) {
        prefs.edit {
            remove(WATCHED)
            putLong(LAST_USER_STOP, now.toEpochMilli())
        }
    }

    /** Clears the watch list when the service ends by itself. */
    fun clearWatched() = prefs.edit { remove(WATCHED) }

    /**
     * Whether the window occurrence of [routeId] that began at [windowStart] may run the countdown:
     * not if the user stopped it (for this route or entirely) after that window began. The next
     * occurrence starts after the stop and runs again.
     */
    fun allows(
        routeId: Long,
        windowStart: Instant,
    ): Boolean {
        val stoppedAt = maxOf(prefs.getLong(LAST_USER_STOP, 0L), prefs.getLong(MUTED_PREFIX + routeId, 0L))
        return windowStart.toEpochMilli() > stoppedAt
    }

    /**
     * Returns the disruption ids not announced before and remembers all of [ids]. An id is forgotten
     * once it has not been seen for [DISRUPTION_MEMORY], so a long lift outage is announced once,
     * not every morning.
     */
    fun unannounced(
        ids: Collection<String>,
        now: Instant,
    ): Set<String> {
        if (ids.isEmpty()) return emptySet()
        val all = stamped(DISRUPTIONS)
        val stored = all.filterValues { Duration.between(it, now) <= DISRUPTION_MEMORY }
        val fresh = ids.filterTo(LinkedHashSet()) { it !in stored }
        val stale = ids.any { id -> stored[id]?.let { Duration.between(it, now) > REFRESH_STAMP } == true }
        if (fresh.isNotEmpty() || stale || stored.size != all.size) {
            putStamped(DISRUPTIONS, stored + ids.associateWith { now })
        }
        return fresh
    }

    private fun stamped(key: String): Map<String, Instant> =
        prefs
            .getStringSet(key, emptySet())
            .orEmpty()
            .mapNotNull { entry ->
                val at = entry.substringBefore(SEPARATOR).toLongOrNull() ?: return@mapNotNull null
                entry.substringAfter(SEPARATOR) to Instant.ofEpochMilli(at)
            }.toMap()

    private fun putStamped(
        key: String,
        entries: Map<String, Instant>,
    ) = prefs.edit { putStringSet(key, entries.mapTo(HashSet()) { (id, at) -> "${at.toEpochMilli()}$SEPARATOR$id" }) }

    private companion object {
        const val WATCHED = "watched"
        const val LAST_USER_STOP = "last_user_stop"
        const val MUTED_PREFIX = "muted_"
        const val DISRUPTIONS = "announced_disruptions"
        const val SEPARATOR = '|'
        val WATCH_LIMIT: Duration = Duration.ofHours(3)
        val DISRUPTION_MEMORY: Duration = Duration.ofDays(7)
        val REFRESH_STAMP: Duration = Duration.ofHours(12)
    }
}
