package app.brokoli5191.zeitig.core.model

/** A platform/direction stop point ("Haltepunkt") from the reference data. */
public data class StopPoint(
    val id: StopId,
    val diva: Diva?,
    val name: String,
    val municipality: String?,
    val location: GeoPoint?,
)

/** A station ("Haltestelle") grouping the stop points that share a DIVA number. */
public data class Station(
    val diva: Diva,
    val name: String,
    val municipality: String?,
    val location: GeoPoint?,
)

/** A line as listed in the reference data. [sortKey] orders lines the way Wiener Linien does. */
public data class Line(
    val id: Int,
    val name: String,
    val sortKey: Int,
    val hasRealtime: Boolean,
    val type: TransportType,
)

/** One stop sequence of a line in one direction. */
public data class LinePattern(
    val lineId: Int,
    val patternId: Int,
    val direction: Direction,
    val stops: List<StopId>,
)
