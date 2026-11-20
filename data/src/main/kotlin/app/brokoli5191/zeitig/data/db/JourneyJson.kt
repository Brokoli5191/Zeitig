package app.brokoli5191.zeitig.data.db

import app.brokoli5191.zeitig.core.journey.Journey
import app.brokoli5191.zeitig.core.journey.Leg
import app.brokoli5191.zeitig.core.journey.LegPoint
import app.brokoli5191.zeitig.core.model.Direction
import app.brokoli5191.zeitig.core.model.GeoPoint
import app.brokoli5191.zeitig.core.model.LineTarget
import app.brokoli5191.zeitig.core.model.StopId
import app.brokoli5191.zeitig.core.model.TransportType
import app.brokoli5191.zeitig.core.routing.Polyline
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Duration

/**
 * Storage format of a [Journey] (a JSON column). Geometry is stored as encoded polylines to keep rows
 * small. Versioned so a future schema can migrate old rows.
 */
internal object JourneyJson {
    private const val VERSION = 1
    private const val PRECISION = 6

    private val json =
        Json {
            ignoreUnknownKeys = true
            classDiscriminator = "kind"
        }

    @Serializable
    private data class JourneyDto(
        val v: Int = VERSION,
        val legs: List<LegDto>,
    )

    @Serializable
    private sealed interface LegDto {
        val from: PointDto
        val to: PointDto
        val seconds: Long
        val geometry: String

        @Serializable
        @SerialName("walk")
        data class Walk(
            override val from: PointDto,
            override val to: PointDto,
            override val seconds: Long,
            override val geometry: String = "",
            val meters: Double? = null,
        ) : LegDto

        @Serializable
        @SerialName("transit")
        data class Transit(
            override val from: PointDto,
            override val to: PointDto,
            override val seconds: Long,
            override val geometry: String = "",
            val line: String,
            val type: String,
            val headsign: String,
            val boardingStop: Int? = null,
            val boardingDirection: String? = null,
            val stops: List<PointDto> = emptyList(),
            val color: Int? = null,
        ) : LegDto
    }

    @Serializable
    private data class PointDto(
        val name: String,
        val lat: Double? = null,
        val lon: Double? = null,
        val stop: Int? = null,
    )

    fun encode(journey: Journey): String = json.encodeToString(JourneyDto.serializer(), JourneyDto(legs = journey.legs.map(::toDto)))

    fun decode(text: String): Journey = Journey(json.decodeFromString(JourneyDto.serializer(), text).legs.map(::fromDto))

    private fun toDto(leg: Leg): LegDto =
        when (leg) {
            is Leg.Walk -> {
                LegDto.Walk(
                    point(leg.from),
                    point(leg.to),
                    leg.duration.seconds,
                    Polyline.encode(leg.geometry, PRECISION),
                    leg.distanceMeters,
                )
            }

            is Leg.Transit -> {
                LegDto.Transit(
                    from = point(leg.from),
                    to = point(leg.to),
                    seconds = leg.duration.seconds,
                    geometry = Polyline.encode(leg.geometry, PRECISION),
                    line = leg.line,
                    type = leg.type.name,
                    headsign = leg.headsign,
                    boardingStop = leg.boarding?.stopId?.value,
                    boardingDirection = leg.boarding?.direction?.name,
                    stops = leg.intermediateStops.map(::point),
                    color = leg.color,
                )
            }
        }

    private fun fromDto(dto: LegDto): Leg =
        when (dto) {
            is LegDto.Walk -> {
                Leg.Walk(
                    point(dto.from),
                    point(dto.to),
                    Duration.ofSeconds(dto.seconds),
                    dto.meters,
                    Polyline.decode(dto.geometry, PRECISION),
                )
            }

            is LegDto.Transit -> {
                Leg.Transit(
                    from = point(dto.from),
                    to = point(dto.to),
                    duration = Duration.ofSeconds(dto.seconds),
                    line = dto.line,
                    type = runCatching { TransportType.valueOf(dto.type) }.getOrDefault(TransportType.OTHER),
                    headsign = dto.headsign,
                    boarding =
                        dto.boardingStop?.let {
                            LineTarget(
                                StopId(it),
                                dto.line,
                                dto.boardingDirection?.let { d ->
                                    runCatching { Direction.valueOf(d) }.getOrNull()
                                },
                            )
                        },
                    intermediateStops = dto.stops.map(::point),
                    geometry = Polyline.decode(dto.geometry, PRECISION),
                    color = dto.color,
                )
            }
        }

    private fun point(p: LegPoint) = PointDto(p.name, p.location?.latitude, p.location?.longitude, p.stopId?.value)

    private fun point(dto: PointDto) =
        LegPoint(
            name = dto.name,
            location = if (dto.lat != null && dto.lon != null) GeoPoint(dto.lat, dto.lon) else null,
            stopId = dto.stop?.let(::StopId),
        )
}
