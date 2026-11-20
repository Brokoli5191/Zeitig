package app.brokoli5191.zeitig.core.model

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** WGS84 coordinate in decimal degrees. */
public data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(latitude in -90.0..90.0) { "latitude out of range: $latitude" }
        require(longitude in -180.0..180.0) { "longitude out of range: $longitude" }
    }

    /** Great-circle distance in metres (haversine, mean Earth radius). */
    public fun distanceTo(other: GeoPoint): Double {
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(other.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(other.longitude - longitude)
        val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    public companion object {
        public const val EARTH_RADIUS_M: Double = 6_371_008.8
    }
}
