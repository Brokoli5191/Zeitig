package app.brokoli5191.zeitig.data.reference

import app.brokoli5191.zeitig.core.model.Direction
import app.brokoli5191.zeitig.core.model.Diva
import app.brokoli5191.zeitig.core.model.GeoPoint
import app.brokoli5191.zeitig.core.model.StopId
import app.brokoli5191.zeitig.core.model.TransportType
import app.brokoli5191.zeitig.data.Fixtures
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OgdCsvTest {
    private fun reader(text: String) = text.byteInputStream().bufferedReader()

    @Test
    fun parsesStopPointsWithCrLfBomAndUnknownPositions() {
        val csv =
            Char(0xFEFF) + "StopID;DIVA;StopText;Municipality;MunicipalityID;Longitude;Latitude\r\n" +
                "4427;60201198;Schwedenplatz;Wien;49000001;16.3781382;48.2118753\r\n" +
                "26;;Praterstern S U;;;0000000000;0000000000\r\n" +
                "x;1;Broken;Wien;1;1;1\r\n\r\n"
        val stops = OgdCsv.stopPoints(reader(csv))
        assertEquals(2, stops.size)
        assertEquals(StopId(4427), stops[0].id)
        assertEquals(Diva(60201198), stops[0].diva)
        assertEquals(GeoPoint(48.2118753, 16.3781382), stops[0].location)
        assertNull(stops[1].diva)
        assertNull(stops[1].location)
        assertNull(stops[1].municipality)
    }

    @Test
    fun columnsAreFoundByNameNotPosition() {
        val csv = "Latitude;Longitude;PlatformText;DIVA;Municipality\n48.2;16.37;Stephansplatz;60201234;Wien\n"
        val station = OgdCsv.stations(reader(csv)).single()
        assertEquals("Stephansplatz", station.name)
        assertEquals(GeoPoint(48.2, 16.37), station.location)
    }

    @Test
    fun missingColumnFailsLoudly() {
        assertFailsWith<OgdCsv.MissingColumnException> { OgdCsv.lines(reader("LineID;LineText\n1;U1\n")) }
    }

    @Test
    fun parsesLinesAndPatterns() {
        val lines = OgdCsv.lines(reader("LineID;LineText;SortingHelp;Realtime;MeansOfTransport\n301;U1;1;1;ptMetro\n149;49;49;0;ptTram\n"))
        assertEquals(listOf("U1", "49"), lines.map { it.name })
        assertEquals(TransportType.METRO, lines[0].type)
        assertTrue(lines[0].hasRealtime)
        assertEquals(false, lines[1].hasRealtime)

        val patterns =
            OgdCsv.patterns(
                reader(
                    "LineID;PatternID;StopSeqCount;StopID;Direction\n" +
                        "301;1;2;3;1\n301;1;0;1;1\n301;1;1;2;1\n301;2;0;3;2\n301;2;1;1;2\n301;3;0;9;9\n",
                ),
            )
        assertEquals(2, patterns.size, "pattern with an unknown direction is dropped")
        val outbound = patterns.single { it.direction == Direction.OUTBOUND }
        assertEquals(listOf(StopId(1), StopId(2), StopId(3)), outbound.stops, "ordered by StopSeqCount")
    }

    @Test
    fun parsesVersion() {
        assertEquals(
            "2026-11-20T21:50:00+01:00",
            OgdCsv.version(reader("DateValidFrom;VersionDate\r\n2026-11-20T21:50:00+01:00;2026-11-20T21:50:00+01:00\r\n")),
        )
        assertNull(OgdCsv.version(reader("DateValidFrom;VersionDate\r\n")))
    }

    @Test
    fun bundledSnapshotLoadsAndIsSearchable() {
        val network = SnapshotLoader.load(DirectorySnapshotSource(Fixtures.bundledSnapshot))
        assertTrue(network.stopPoints.size > 4_000, "stop points: ${network.stopPoints.size}")
        assertTrue(network.stations.size > 1_500, "stations: ${network.stations.size}")
        assertTrue(network.lines.size > 150, "lines: ${network.lines.size}")
        assertTrue(network.patterns.size > 300, "patterns: ${network.patterns.size}")

        val schwedenplatz = network.search.search("schwedenpl").first().station
        assertEquals(Diva(60201198), schwedenplatz.diva)
        val u4 = network.linesAtStation(schwedenplatz.diva).filter { it.line.name == "U4" }
        assertEquals(setOf(Direction.OUTBOUND, Direction.INBOUND), u4.map { it.direction }.toSet())
        assertTrue(u4.any { it.towards.startsWith("Heiligenstadt") }, u4.toString())
        assertNotNull(SnapshotLoader.version(DirectorySnapshotSource(Fixtures.bundledSnapshot)))
    }
}
