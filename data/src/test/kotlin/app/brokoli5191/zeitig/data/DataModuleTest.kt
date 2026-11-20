package app.brokoli5191.zeitig.data

import org.junit.Test
import kotlin.test.assertEquals

class DataModuleTest {
    @Test
    fun userAgentProductIsStable() {
        assertEquals("Zeitig", DataModule.USER_AGENT_PRODUCT)
    }
}
