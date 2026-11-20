package app.brokoli5191.zeitig.data.map

import app.brokoli5191.zeitig.data.Fixtures
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class BasemapStyleTest {
    @Test
    fun relativeUrlsBecomeAbsoluteAndTheSourceGetsATileTemplate() {
        val rewritten = Json.parseToJsonElement(BasemapStyle.rewrite(Fixtures.text("basemap/root_trimmed.json"))).jsonObject
        assertEquals("https://mapsneu.wien.gv.at/basemapv/bmapv/3857/resources/sprites/sprite", rewritten["sprite"]!!.jsonPrimitive.content)
        assertEquals(
            "https://mapsneu.wien.gv.at/basemapv/bmapv/3857/resources/fonts/{fontstack}/{range}.pbf",
            rewritten["glyphs"]!!.jsonPrimitive.content,
        )
        val source = rewritten["sources"]!!.jsonObject["esri"]!!.jsonObject
        assertFalse("url" in source)
        assertEquals(BasemapStyle.TILE_TEMPLATE, source["tiles"]!!.jsonArray.single().jsonPrimitive.content)
        assertEquals(BasemapStyle.ATTRIBUTION, source["attribution"]!!.jsonPrimitive.content)
        assertEquals(2, rewritten["layers"]!!.jsonArray.size)
    }

    @Test
    fun rejectsSomethingThatIsNotAStyle() {
        assertFailsWith<IllegalArgumentException> { BasemapStyle.rewrite("<html>403</html>") }
        assertFailsWith<IllegalArgumentException> { BasemapStyle.rewrite("""{"version": 8}""") }
    }
}
