package app.brokoli5191.zeitig.data.routing

import kotlinx.serialization.Serializable

// MOTIS 2 API as served by Transitous (`/api/v6/plan`, `/api/v1/geocode`), modelled after the
// upstream openapi.yaml. Only the fields Zeitig uses; everything optional so additions never break.

@Serializable
internal data class PlanResponseDto(
    val itineraries: List<ItineraryDto> = emptyList(),
)

@Serializable
internal data class ItineraryDto(
    val startTime: String? = null,
    val endTime: String? = null,
    val transfers: Int? = null,
    val legs: List<LegDto> = emptyList(),
)

@Serializable
internal data class LegDto(
    val mode: String? = null,
    val from: PlaceDto? = null,
    val to: PlaceDto? = null,
    val duration: Long? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val realTime: Boolean? = null,
    val distance: Double? = null,
    val routeShortName: String? = null,
    val displayName: String? = null,
    val headsign: String? = null,
    val routeColor: String? = null,
    val legGeometry: EncodedPolylineDto? = null,
    val intermediateStops: List<PlaceDto> = emptyList(),
)

@Serializable
internal data class PlaceDto(
    val name: String? = null,
    val stopId: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
)

@Serializable
internal data class EncodedPolylineDto(
    val points: String? = null,
    val precision: Int? = null,
)

@Serializable
internal data class ErrorDto(
    val error: String? = null,
)

@Serializable
internal data class GeocodeMatchDto(
    val type: String? = null,
    val name: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val zip: String? = null,
    val areas: List<AreaDto> = emptyList(),
)

@Serializable
internal data class AreaDto(
    val name: String? = null,
    val adminLevel: Double? = null,
)
