package app.brokoli5191.zeitig.core.routing

import app.brokoli5191.zeitig.core.model.GeoPoint
import kotlin.math.pow

/** Decoder for the encoded polyline format (Google's algorithm) at a given decimal [precision]. */
public object Polyline {
    public fun decode(
        encoded: String,
        precision: Int = 5,
    ): List<GeoPoint> {
        require(precision in 1..9) { "unsupported precision $precision" }
        val factor = 10.0.pow(precision)
        val points = ArrayList<GeoPoint>(encoded.length / 4)
        var index = 0
        var lat = 0L
        var lon = 0L
        while (index < encoded.length) {
            val (dLat, afterLat) = next(encoded, index) ?: break
            val (dLon, afterLon) = next(encoded, afterLat) ?: break
            index = afterLon
            lat += dLat
            lon += dLon
            val latitude = lat / factor
            val longitude = lon / factor
            if (latitude in -90.0..90.0 && longitude in -180.0..180.0) points += GeoPoint(latitude, longitude)
        }
        return points
    }

    /** Reads one zig-zag varint; null when the string ends mid-value (truncated input). */
    private fun next(
        encoded: String,
        start: Int,
    ): Pair<Long, Int>? {
        var result = 0L
        var shift = 0
        var index = start
        while (index < encoded.length) {
            val b = encoded[index++].code - 63
            result = result or ((b and 0x1f).toLong() shl shift)
            shift += 5
            if (b < 0x20) {
                val value = if (result and 1L != 0L) (result shr 1).inv() else result shr 1
                return value to index
            }
        }
        return null
    }

    public fun encode(
        points: List<GeoPoint>,
        precision: Int = 5,
    ): String {
        val factor = 10.0.pow(precision)
        val out = StringBuilder()
        var lastLat = 0L
        var lastLon = 0L
        for (point in points) {
            val lat = Math.round(point.latitude * factor)
            val lon = Math.round(point.longitude * factor)
            write(lat - lastLat, out)
            write(lon - lastLon, out)
            lastLat = lat
            lastLon = lon
        }
        return out.toString()
    }

    private fun write(
        value: Long,
        out: StringBuilder,
    ) {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        while (v >= 0x20) {
            out.append(((0x20 or (v and 0x1f).toInt()) + 63).toChar())
            v = v shr 5
        }
        out.append((v.toInt() + 63).toChar())
    }
}
