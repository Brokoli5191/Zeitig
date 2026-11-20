package app.brokoli5191.zeitig.data.ogd

import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OgdTimeTest {
    @Test
    fun parsesOffsetWithoutColon() {
        assertEquals(Instant.parse("2026-11-20T21:03:30Z"), OgdTime.parse("2026-11-20T22:03:30.000+0100"))
    }

    @Test
    fun parsesSummerTimeOffset() {
        assertEquals(Instant.parse("2026-07-01T05:00:00Z"), OgdTime.parse("2026-07-01T07:00:00.000+0200"))
    }

    @Test
    fun parsesOffsetWithColonAndZulu() {
        assertEquals(Instant.parse("2026-11-20T21:03:30Z"), OgdTime.parse("2026-11-20T22:03:30+01:00"))
        assertEquals(Instant.parse("2026-11-20T21:03:30Z"), OgdTime.parse("2026-11-20T21:03:30Z"))
    }

    @Test
    fun parsesWithoutSecondsOrFraction() {
        assertEquals(Instant.parse("2026-11-20T21:03:00Z"), OgdTime.parse("2026-11-20T22:03+0100"))
        assertEquals(Instant.parse("2026-11-20T21:03:30.123Z"), OgdTime.parse("2026-11-20T22:03:30.123+0100"))
    }

    @Test
    fun localTimeWithoutOffsetIsReadAsVienna() {
        assertEquals(Instant.parse("2026-11-20T21:03:30Z"), OgdTime.parse("2026-11-20T22:03:30"))
        assertEquals(Instant.parse("2026-07-01T05:00:00Z"), OgdTime.parse("2026-07-01T07:00:00"))
    }

    @Test
    fun rejectsGarbage() {
        assertNull(OgdTime.parse(null))
        assertNull(OgdTime.parse(""))
        assertNull(OgdTime.parse("   "))
        assertNull(OgdTime.parse("not a time"))
        assertNull(OgdTime.parse("2026-13-40T99:99:99.000+0100"))
    }

    @Test
    fun keepsTheOffsetAcrossTheMarchTransition() {
        // 01:59 CET and 03:01 CEST on 29 March 2026 are only two minutes apart.
        val before = OgdTime.parse("2026-03-29T01:59:00.000+0100")!!
        val after = OgdTime.parse("2026-03-29T03:01:00.000+0200")!!
        assertEquals(120, after.epochSecond - before.epochSecond)
    }
}
