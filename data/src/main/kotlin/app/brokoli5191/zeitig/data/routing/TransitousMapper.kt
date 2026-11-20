package app.brokoli5191.zeitig.data.routing

import app.brokoli5191.zeitig.core.journey.Journey
import app.brokoli5191.zeitig.core.journey.Leg
import app.brokoli5191.zeitig.core.journey.LegPoint
import app.brokoli5191.zeitig.core.model.GeoPoint
import app.brokoli5191.zeitig.core.model.TransportType
import app.brokoli5191.zeitig.core.routing.GeocodeMatch
import app.brokoli5191.zeitig.core.routing.Itinerary
import app.brokoli5191.zeitig.core.routing.PlaceKind
import app.brokoli5191.zeitig.core.routing.Polyline
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException

internal object TransitousMapper {
    fun itineraries(dto: PlanResponseDto): List<Itinerary> = dto.itineraries.mapNotNull(::itinerary)

    private fun itinerary(dto: ItineraryDto): Itinerary? {
        val legs = dto.legs.mapNotNull(::leg)
        if (legs.isEmpty()) return null
        val departure = instant(dto.startTime) ?: return null
        val arrival = instant(dto.endTime) ?: return null
        return Itinerary(
            journey = Journey(legs),
            departure = departure,
            arrival = arrival,
            realtime = dto.legs.any { it.realTime == true },
        )
    }

    private fun leg(dto: LegDto): Leg? {
        val from = point(dto.from) ?: return null
        val to = point(dto.to) ?: return null
        val duration =
            dto.duration?.let(Duration::ofSeconds)
                ?: run {
                    val start = instant(dto.startTime) ?: return null
                    val end = instant(dto.endTime) ?: return null
                    Duration.between(start, end)
                }
        val geometry = dto.legGeometry?.points?.let { Polyline.decode(it, dto.legGeometry.precision ?: DEFAULT_PRECISION) }.orEmpty()
        val mode = dto.mode?.uppercase() ?: return null
        return when {
            mode in STREET_MODES -> {
                Leg.Walk(from, to, duration, dto.distance, geometry)
            }

            else -> {
                Leg.Transit(
                    from = from,
                    to = to,
                    duration = duration,
                    line = (dto.routeShortName ?: dto.displayName)?.trim()?.takeIf { it.isNotEmpty() } ?: return null,
                    type = transportType(mode),
                    headsign = dto.headsign?.trim().orEmpty(),
                    intermediateStops = dto.intermediateStops.mapNotNull(::point),
                    geometry = geometry,
                    color = color(dto.routeColor),
                )
            }
        }
    }

    private fun point(dto: PlaceDto?): LegPoint? {
        dto ?: return null
        val location = locationOf(dto.lat, dto.lon)
        val name =
            when (val raw = dto.name?.trim()) {
                null, "" -> return null

                // MOTIS names the request coordinates START and END.
                "START", "END" -> raw

                else -> raw.removePrefix("Wien ").ifEmpty { raw }
            }
        return LegPoint(name, location)
    }

    fun geocode(
        matches: List<GeocodeMatchDto>,
        near: GeoPoint?,
        maxDistanceMeters: Double,
        limit: Int,
    ): List<GeocodeMatch> =
        matches
            .mapNotNull { dto ->
                val location = locationOf(dto.lat, dto.lon) ?: return@mapNotNull null
                val name = dto.name?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                if (near != null && near.distanceTo(location) > maxDistanceMeters) return@mapNotNull null
                GeocodeMatch(
                    name = name,
                    location = location,
                    kind =
                        when (dto.type?.uppercase()) {
                            "STOP" -> PlaceKind.STOP
                            "ADDRESS" -> PlaceKind.ADDRESS
                            else -> PlaceKind.PLACE
                        },
                    area =
                        dto.areas
                            .filter { (it.adminLevel ?: 0.0) >= DISTRICT_LEVEL && !it.name.isNullOrBlank() }
                            .minByOrNull { it.adminLevel ?: Double.MAX_VALUE }
                            ?.name ?: dto.areas.lastOrNull { !it.name.isNullOrBlank() }?.name,
                    postalCode = dto.zip,
                )
            }
            // The geocoder returns one entry per address node; collapse duplicates a few metres apart.
            .fold(mutableListOf<GeocodeMatch>()) { acc, match ->
                if (acc.none { it.name == match.name && it.location.distanceTo(match.location) < DUPLICATE_RADIUS_M }) acc += match
                acc
            }.take(limit)

    private fun locationOf(
        lat: Double?,
        lon: Double?,
    ): GeoPoint? {
        if (lat == null || lon == null) return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return GeoPoint(lat, lon)
    }

    private fun instant(value: String?): Instant? =
        try {
            value?.let(Instant::parse)
        } catch (_: DateTimeParseException) {
            null
        }

    private fun color(hex: String?): Int? =
        hex?.trim()?.removePrefix("#")?.takeIf {
            it.length == 6
        }?.toIntOrNull(16)?.or(0xFF000000.toInt())

    fun transportType(mode: String): TransportType =
        when (mode) {
            "SUBWAY", "METRO" -> TransportType.METRO
            "TRAM" -> TransportType.TRAM
            "BUS", "COACH", "ODM" -> TransportType.BUS
            in RAIL_MODES -> TransportType.TRAIN
            else -> TransportType.OTHER
        }

    private val STREET_MODES = setOf("WALK", "BIKE", "CAR", "FLEX")
    private val RAIL_MODES =
        setOf("RAIL", "REGIONAL_RAIL", "REGIONAL_FAST_RAIL", "HIGHSPEED_RAIL", "LONG_DISTANCE", "NIGHT_RAIL", "SUBURBAN")
    private const val DEFAULT_PRECISION = 6
    private const val DISTRICT_LEVEL = 9.0
    private const val DUPLICATE_RADIUS_M = 100.0
}
