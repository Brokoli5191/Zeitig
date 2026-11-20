package app.brokoli5191.zeitig.core.search

import app.brokoli5191.zeitig.core.model.Diva
import app.brokoli5191.zeitig.core.model.GeoPoint
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StopSearchTest {
    private fun station(
        diva: Int,
        name: String,
        weight: Int = 1,
    ) = SearchableStation(Diva(diva), name, GeoPoint(48.2, 16.37), weight)

    private val search =
        StopSearch(
            listOf(
                station(1, "Stephansplatz", weight = 6),
                station(2, "Schwedenplatz", weight = 10),
                station(3, "Hütteldorfer Straße", weight = 4),
                station(4, "Hütteldorf", weight = 8),
                station(5, "Karlsplatz", weight = 12),
                station(6, "Karl-Schweighofer-Gasse"),
                station(7, "Strebersdorf"),
                station(8, "Praterstern S U", weight = 9),
                station(9, "Friedrich-Engels-Platz", weight = 3),
                station(10, "Hauptstraße"),
            ),
        )

    private fun first(query: String) = search.search(query).first().station.name

    @Test
    fun `exact name wins`() {
        assertEquals("Stephansplatz", first("Stephansplatz"))
        assertEquals(10, search.size)
    }

    @Test
    fun `prefix while typing`() {
        assertEquals("Schwedenplatz", first("schwed"))
        assertEquals("Strebersdorf", first("streb"))
    }

    @Test
    fun `umlauts and street abbreviations fold`() {
        assertEquals("Hütteldorfer Straße", first("huetteldorfer str."))
        assertEquals("Hütteldorfer Straße", first("Hutteldorfer Strasse"))
        assertEquals("Hauptstraße", first("Hauptstr"))
        assertEquals("Friedrich-Engels-Platz", first("friedrich engels pl."))
    }

    @Test
    fun `typos within the edit budget still match`() {
        assertEquals("Stephansplatz", first("Stefansplatz"))
        assertEquals("Praterstern S U", first("pratrestern"))
    }

    @Test
    fun `every query token must match`() {
        val results = search.search("karl gasse").map { it.station.name }
        assertEquals(listOf("Karl-Schweighofer-Gasse"), results)
    }

    @Test
    fun `ties break by weight then by shorter name`() {
        val results = search.search("hutteldorf").map { it.station.name }
        assertEquals(listOf("Hütteldorf", "Hütteldorfer Straße"), results)
    }

    @Test
    fun `short garbage and empty queries return nothing`() {
        assertTrue(search.search("").isEmpty())
        assertTrue(search.search("   ").isEmpty())
        assertTrue(search.search("xyz").isEmpty())
        assertTrue(search.search("zzzzzzzzzz").isEmpty())
    }

    @Test
    fun `limit caps the result list`() {
        assertEquals(3, search.search("h").size)
        assertEquals(2, search.search("h", limit = 2).size)
    }

    @Test
    fun `bounded edit distance handles transpositions and early exit`() {
        assertEquals(1, boundedDamerauLevenshtein("stpehan", "stephan", 2))
        assertEquals(0, boundedDamerauLevenshtein("abc", "abc", 1))
        assertEquals(2, boundedDamerauLevenshtein("abcd", "xbcy", 2))
        assertEquals(2, boundedDamerauLevenshtein("abcdef", "ab", 1), "length gap exceeds budget")
        assertEquals(2, boundedDamerauLevenshtein("aaaa", "bbbb", 1), "rows exceed budget early")
    }
}
