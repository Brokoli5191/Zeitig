package app.brokoli5191.zeitig.monitor

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class MonitorPrefsTest {
    private val prefs = MonitorPrefs(ApplicationProvider.getApplicationContext())
    private val t0 = Instant.parse("2026-11-23T06:30:00Z")

    @Test
    fun `a stop holds for the running window and releases the next one`() {
        val windowStart = t0
        assertTrue(prefs.allows(1, windowStart))

        prefs.stopAll(t0 + Duration.ofMinutes(10))

        assertFalse(prefs.allows(1, windowStart), "window that began before the stop")
        assertFalse(prefs.allows(2, windowStart), "a stop covers every route")
        assertTrue(prefs.allows(1, windowStart + Duration.ofDays(1)), "tomorrow's window")
    }

    @Test
    fun `unwatching mutes only that route for its current window`() {
        prefs.watch(1, t0)
        prefs.watch(2, t0)

        prefs.unwatch(1, t0 + Duration.ofMinutes(5))

        assertEquals(setOf(2L), prefs.watched(t0 + Duration.ofMinutes(5)))
        assertFalse(prefs.allows(1, t0))
        assertTrue(prefs.allows(2, t0))
    }

    @Test
    fun `watched routes expire after three hours`() {
        prefs.watch(7, t0)

        assertEquals(setOf(7L), prefs.watched(t0 + Duration.ofHours(2)))
        assertEquals(emptySet(), prefs.watched(t0 + Duration.ofHours(3)))
    }

    @Test
    fun `stopping everything clears the watch list and an own stop only clears it`() {
        prefs.watch(3, t0)
        prefs.stopAll(t0 + Duration.ofMinutes(1))
        assertEquals(emptySet(), prefs.watched(t0 + Duration.ofMinutes(1)))

        prefs.watch(4, t0 + Duration.ofMinutes(2))
        prefs.clearWatched()
        assertEquals(emptySet(), prefs.watched(t0 + Duration.ofMinutes(2)))
        assertTrue(prefs.allows(4, t0 + Duration.ofMinutes(3)), "the service ending by itself is no user stop")
    }

    @Test
    fun `disruptions are announced once while they last`() {
        assertEquals(setOf("a", "b"), prefs.unannounced(listOf("a", "b"), t0))
        assertEquals(setOf("c"), prefs.unannounced(listOf("a", "b", "c"), t0 + Duration.ofMinutes(30)))
        // Still active every day for two weeks: never announced again.
        for (day in 1L..14L) {
            assertEquals(emptySet(), prefs.unannounced(listOf("a"), t0 + Duration.ofDays(day)))
        }
        assertEquals(emptySet(), prefs.unannounced(emptyList(), t0))
    }

    @Test
    fun `a disruption unseen for more than a week counts as new`() {
        prefs.unannounced(listOf("lift"), t0)

        assertEquals(setOf("lift"), prefs.unannounced(listOf("lift"), t0 + Duration.ofDays(8)))
    }
}
