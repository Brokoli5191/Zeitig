package app.brokoli5191.zeitig.core

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ZeitigTest {
    @Test
    fun `module name is stable`() {
        assertEquals("Zeitig", Zeitig.NAME)
    }
}
