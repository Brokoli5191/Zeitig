package app.brokoli5191.zeitig.data.reference

import app.brokoli5191.zeitig.core.model.Direction
import app.brokoli5191.zeitig.core.model.Diva
import app.brokoli5191.zeitig.core.model.GeoPoint
import app.brokoli5191.zeitig.core.model.Line
import app.brokoli5191.zeitig.core.model.LinePattern
import app.brokoli5191.zeitig.core.model.Station
import app.brokoli5191.zeitig.core.model.StopId
import app.brokoli5191.zeitig.core.model.StopPoint
import app.brokoli5191.zeitig.core.model.TransportType
import java.io.BufferedReader

/**
 * Parser for the Wiener Linien reference CSVs (`;`-separated, UTF-8, CRLF, no quoting). Columns are
 * looked up by header name, so a reordered upstream file still parses; malformed rows are skipped.
 */
public object OgdCsv {
    private val BYTE_ORDER_MARK = Char(0xFEFF).toString()

    public class MissingColumnException(
        column: String,
    ) : IllegalArgumentException("CSV column missing: $column")

    private class Table(
        reader: BufferedReader,
    ) {
        private val lines = reader.lineSequence().map { it.trimEnd('\r') }.filter { it.isNotBlank() }.iterator()
        private val header: Map<String, Int> =
            if (lines.hasNext()) {
                lines
                    .next()
                    .removePrefix(BYTE_ORDER_MARK)
                    .split(';')
                    .withIndex()
                    .associate { (i, name) -> name.trim().lowercase() to i }
            } else {
                emptyMap()
            }

        fun column(name: String): Int = header[name.lowercase()] ?: throw MissingColumnException(name)

        fun rows(): Sequence<List<String>> = lines.asSequence().map { line -> line.split(';').map(String::trim) }
    }

    private fun List<String>.at(index: Int): String? = getOrNull(index)?.takeIf { it.isNotEmpty() }

    private fun coordinate(
        latitude: String?,
        longitude: String?,
    ): GeoPoint? {
        val lat = latitude?.toDoubleOrNull() ?: return null
        val lon = longitude?.toDoubleOrNull() ?: return null
        if (lat == 0.0 && lon == 0.0) return null // upstream marks unknown positions as 0000000000
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return GeoPoint(lat, lon)
    }

    /** `wienerlinien-ogd-haltepunkte.csv`: StopID;DIVA;StopText;Municipality;MunicipalityID;Longitude;Latitude */
    public fun stopPoints(reader: BufferedReader): List<StopPoint> {
        val table = Table(reader)
        val id = table.column("StopID")
        val diva = table.column("DIVA")
        val name = table.column("StopText")
        val municipality = table.column("Municipality")
        val lon = table.column("Longitude")
        val lat = table.column("Latitude")
        return table
            .rows()
            .mapNotNull { row ->
                StopPoint(
                    id = StopId(row.at(id)?.toIntOrNull() ?: return@mapNotNull null),
                    diva = row.at(diva)?.toIntOrNull()?.let(::Diva),
                    name = row.at(name) ?: return@mapNotNull null,
                    municipality = row.at(municipality),
                    location = coordinate(row.at(lat), row.at(lon)),
                )
            }.toList()
    }

    /** `wienerlinien-ogd-haltestellen.csv`: DIVA;PlatformText;Municipality;MunicipalityID;Longitude;Latitude */
    public fun stations(reader: BufferedReader): List<Station> {
        val table = Table(reader)
        val diva = table.column("DIVA")
        val name = table.column("PlatformText")
        val municipality = table.column("Municipality")
        val lon = table.column("Longitude")
        val lat = table.column("Latitude")
        return table
            .rows()
            .mapNotNull { row ->
                Station(
                    diva = Diva(row.at(diva)?.toIntOrNull() ?: return@mapNotNull null),
                    name = row.at(name) ?: return@mapNotNull null,
                    municipality = row.at(municipality),
                    location = coordinate(row.at(lat), row.at(lon)),
                )
            }.toList()
    }

    /** `wienerlinien-ogd-linien.csv`: LineID;LineText;SortingHelp;Realtime;MeansOfTransport */
    public fun lines(reader: BufferedReader): List<Line> {
        val table = Table(reader)
        val id = table.column("LineID")
        val name = table.column("LineText")
        val sorting = table.column("SortingHelp")
        val realtime = table.column("Realtime")
        val means = table.column("MeansOfTransport")
        return table
            .rows()
            .mapNotNull { row ->
                Line(
                    id = row.at(id)?.toIntOrNull() ?: return@mapNotNull null,
                    name = row.at(name) ?: return@mapNotNull null,
                    sortKey = row.at(sorting)?.toIntOrNull() ?: Int.MAX_VALUE,
                    hasRealtime = row.at(realtime) == "1",
                    type = TransportType.fromOgd(row.at(means)),
                )
            }.toList()
    }

    /** `wienerlinien-ogd-fahrwegverlaeufe.csv`: LineID;PatternID;StopSeqCount;StopID;Direction */
    public fun patterns(reader: BufferedReader): List<LinePattern> {
        val table = Table(reader)
        val line = table.column("LineID")
        val pattern = table.column("PatternID")
        val sequence = table.column("StopSeqCount")
        val stop = table.column("StopID")
        val direction = table.column("Direction")

        data class Row(
            val line: Int,
            val pattern: Int,
            val sequence: Int,
            val stop: Int,
            val direction: Direction,
        )
        return table
            .rows()
            .mapNotNull { row ->
                Row(
                    line = row.at(line)?.toIntOrNull() ?: return@mapNotNull null,
                    pattern = row.at(pattern)?.toIntOrNull() ?: return@mapNotNull null,
                    sequence = row.at(sequence)?.toIntOrNull() ?: return@mapNotNull null,
                    stop = row.at(stop)?.toIntOrNull() ?: return@mapNotNull null,
                    direction = Direction.fromOgd(row.at(direction)) ?: return@mapNotNull null,
                )
            }.groupBy { Triple(it.line, it.pattern, it.direction) }
            .map { (key, rows) ->
                LinePattern(
                    lineId = key.first,
                    patternId = key.second,
                    direction = key.third,
                    stops = rows.sortedBy { it.sequence }.map { StopId(it.stop) },
                )
            }
    }

    /** `wienerlinien-ogd-version.csv`: DateValidFrom;VersionDate – returns the VersionDate text. */
    public fun version(reader: BufferedReader): String? {
        val table = Table(reader)
        val column = table.column("VersionDate")
        return table.rows().firstOrNull()?.at(column)
    }
}
