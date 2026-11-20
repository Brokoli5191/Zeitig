package app.brokoli5191.zeitig.data.ogd

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// DTOs follow "Wiener Linien Realtime | Schnittstellendokumentation" V1.6 (19.10.2026), section 3.
// Everything is optional or defaulted: the parser must survive missing and unknown fields. Fields
// whose documented type disagrees with real responses (e.g. `attributes.rbl`, string vs. number)
// are kept as JsonElement and converted leniently.

@Serializable
internal data class MonitorResponseDto(
    val data: MonitorDataDto? = null,
    val message: MessageDto? = null,
)

@Serializable
internal data class MessageDto(
    val value: String? = null,
    val messageCode: Int? = null,
    val serverTime: String? = null,
)

@Serializable
internal data class MonitorDataDto(
    val monitors: List<MonitorDto> = emptyList(),
    val trafficInfos: List<TrafficInfoDto> = emptyList(),
    val trafficInfoCategories: List<TrafficInfoCategoryDto> = emptyList(),
)

@Serializable
internal data class MonitorDto(
    val locationStop: LocationStopDto? = null,
    val lines: List<LineDto> = emptyList(),
    val refTrafficInfoNames: List<String> = emptyList(),
)

@Serializable
internal data class LocationStopDto(
    val geometry: GeometryDto? = null,
    val properties: LocationPropertiesDto? = null,
)

@Serializable
internal data class GeometryDto(
    val coordinates: List<Double> = emptyList(),
)

@Serializable
internal data class LocationPropertiesDto(
    val name: JsonElement? = null,
    val title: String? = null,
    val municipality: String? = null,
    val gate: String? = null,
    val attributes: JsonObject? = null,
)

@Serializable
internal data class LineDto(
    val name: String? = null,
    val towards: String? = null,
    val direction: String? = null,
    val platform: String? = null,
    val richtungsId: JsonElement? = null,
    val barrierFree: Boolean? = null,
    val realtimeSupported: Boolean? = null,
    val trafficjam: Boolean? = null,
    val departures: DeparturesDto? = null,
    val type: String? = null,
    val lineId: Int? = null,
)

@Serializable
internal data class DeparturesDto(
    val departure: List<DepartureDto> = emptyList(),
)

@Serializable
internal data class DepartureDto(
    val departureTime: DepartureTimeDto? = null,
    val vehicle: VehicleDto? = null,
)

@Serializable
internal data class DepartureTimeDto(
    val timePlanned: String? = null,
    val timeReal: String? = null,
    val countdown: Int? = null,
)

@Serializable
internal data class VehicleDto(
    val name: String? = null,
    val towards: String? = null,
    val direction: String? = null,
    val platform: String? = null,
    val barrierFree: Boolean? = null,
    val foldingRamp: Boolean? = null,
    val realtimeSupported: Boolean? = null,
    val type: String? = null,
)

@Serializable
internal data class TrafficInfoCategoryDto(
    val id: Int? = null,
    val name: String? = null,
    val title: String? = null,
)

@Serializable
internal data class TrafficInfoDto(
    val refTrafficInfoCategoryId: Int? = null,
    val name: String? = null,
    val title: String? = null,
    val description: String? = null,
    val time: TrafficInfoTimeDto? = null,
    val relatedLines: List<String> = emptyList(),
    val relatedStops: List<JsonElement> = emptyList(),
    val attributes: JsonObject? = null,
    val status: String? = null,
)

@Serializable
internal data class TrafficInfoTimeDto(
    val start: String? = null,
    val end: String? = null,
    val resume: String? = null,
)
