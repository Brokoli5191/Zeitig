package app.brokoli5191.zeitig.core.share

import app.brokoli5191.zeitig.core.model.GeoPoint
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** A place handed over by another app: a name, a position, or both. */
public data class SharedPlace(
    val name: String?,
    val location: GeoPoint?,
)

/** Start and destination of a shared route. [origin] is null when the sharer meant "where I am". */
public data class SharedRoute(
    val origin: SharedPlace?,
    val destination: SharedPlace,
)

/**
 * Reads what map apps put on the share sheet: Google Maps directions, places, searches and Maps
 * URLs (also wrapped in a consent redirect), `geo:` URIs, Apple Maps, OpenStreetMap and OsmAnd
 * links, and bare coordinates. Short links have to be expanded first, see [isShortLink].
 */
public object SharedRoutes {
    private val LINK = Regex("""(?:https?://|geo:)\S+""", RegexOption.IGNORE_CASE)
    private val COORDINATES = Regex("""^\s*(-?\d{1,2}(?:\.\d+)?)\s*[,;]\s*(-?\d{1,3}(?:\.\d+)?)\s*$""")
    private val GEO_QUERY = Regex("""^\s*(-?\d{1,2}(?:\.\d+)?)\s*,\s*(-?\d{1,3}(?:\.\d+)?)\s*(?:\((.*)\))?\s*$""")
    private val GOOGLE_HOST = Regex("""^(?:www\.|maps\.)?google\.[a-z]{2,3}(?:\.[a-z]{2})?$""")
    private val TOKEN = Regex("""^(\d+)([A-Za-z])(.*)$""")

    /** The first link in a shared text; map apps often put a sentence in front of it. */
    public fun findLink(text: String): String? {
        var link = LINK.find(text)?.value ?: return null
        // Punctuation of the surrounding sentence; a ")" only when it closes nothing in the link.
        while (link.isNotEmpty()) {
            val last = link.last()
            link =
                when {
                    last in ".,;!?'\">" -> link.dropLast(1)
                    last == ')' && link.count { it == '(' } < link.count { it == ')' } -> link.dropLast(1)
                    else -> return link
                }
        }
        return null
    }

    /** A link only its issuer can expand: maps.app.goo.gl and goo.gl/maps. */
    public fun isShortLink(url: String): Boolean {
        val uri = uri(url) ?: return false
        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && scheme != "http") return false
        return when (uri.host?.lowercase()) {
            "maps.app.goo.gl" -> true
            "goo.gl" -> uri.rawPath.orEmpty().startsWith("/maps")
            else -> false
        }
    }

    /** Parses a shared text or link; null when it holds nothing Zeitig can turn into a route. */
    public fun parse(text: String): SharedRoute? {
        findLink(text)?.let { link -> parseLink(link)?.let { return it } }
        return coordinates(text)?.let { SharedRoute(null, SharedPlace(null, it)) }
    }

    private fun parseLink(link: String): SharedRoute? {
        if (link.startsWith("geo:", ignoreCase = true)) return parseGeo(link.substring(4))
        val uri = uri(link) ?: return null
        val host = uri.host?.lowercase() ?: return null
        val query = queryOf(uri)
        return when {
            // EU visitors may get the target wrapped in a cookie-consent redirect.
            host.startsWith("consent.") -> query["continue"]?.let(::parseLink)

            GOOGLE_HOST.matches(host) -> parseGoogle(uri, query)

            host == "maps.apple.com" -> parseApple(query)

            host == "openstreetmap.org" || host.endsWith(".openstreetmap.org") -> parseOsm(query)

            host == "osmand.net" -> placeOf(query["lat"], query["lon"])?.let { SharedRoute(null, it) }

            else -> null
        }
    }

    // --- Google Maps ---------------------------------------------------------------------------

    private fun parseGoogle(
        uri: URI,
        query: Map<String, String>,
    ): SharedRoute? {
        val segments = uri.rawPath.orEmpty().split('/')
        val kind = segments.getOrNull(segments.indexOf("maps") + 1).orEmpty()
        if (query["api"] == "1") {
            return when (kind) {
                "dir" -> place(query["destination"])?.let { SharedRoute(place(query["origin"]), it) }
                else -> place(query["query"])?.let { SharedRoute(null, it) }
            }
        }
        val data = segments.firstOrNull { it.startsWith("data=") }?.removePrefix("data=")
        return when (kind) {
            "dir" -> parseDirections(segments.drop(segments.indexOf("dir") + 1), data)
            "place" -> parsePlace(segments.getOrNull(segments.indexOf("place") + 1), data)
            "search" -> place(segments.getOrNull(segments.indexOf("search") + 1)?.let(::decode))?.let { SharedRoute(null, it) }
            else -> parseClassic(query)
        }
    }

    /** `/maps/dir/<origin>/<destination>/@view/data=…`: names in the path, positions in the data. */
    private fun parseDirections(
        afterDir: List<String>,
        data: String?,
    ): SharedRoute? {
        val names =
            afterDir
                .takeWhile { !it.startsWith("@") && !it.startsWith("data=") }
                .map { decode(it).trim() }
                .dropLastWhile { it.isEmpty() } // a trailing slash, not an empty destination
        val waypoints = data?.let { waypointsOf(parseData(it)) }.orEmpty()
        val count = if (waypoints.size >= 2) waypoints.size else names.size
        if (count < 2) return null
        val places =
            (0 until count).map { index ->
                val name = names.getOrNull(index)?.takeIf { it.isNotEmpty() }
                val waypoint = waypoints.getOrNull(index)
                val position = waypoint?.position() ?: name?.let(::coordinates)
                when {
                    waypoint?.isMyLocation() == true -> null
                    name != null && coordinates(name) != null -> SharedPlace(null, position)
                    name != null || position != null -> SharedPlace(name, position)
                    else -> null
                }
            }
        val destination = places.last() ?: return null
        return SharedRoute(places.first(), destination)
    }

    /** `/maps/place/<name>/@view/data=…`: the place's own position sits in the data block. */
    private fun parsePlace(
        rawName: String?,
        data: String?,
    ): SharedRoute? {
        val name = rawName?.let(::decode)?.trim()?.takeIf { it.isNotEmpty() }
        val position = data?.let { parseData(it).firstNotNullOfOrNull { node -> node.position() } }
        if (name == null && position == null) return null
        return SharedRoute(null, SharedPlace(name, position))
    }

    /** Old-style `?q=`, `?daddr=`/`?saddr=` links. */
    private fun parseClassic(query: Map<String, String>): SharedRoute? {
        val destination = place(query["daddr"]) ?: place(query["q"]) ?: place(query["query"]) ?: return null
        return SharedRoute(place(query["saddr"]), destination)
    }

    // --- other apps ------------------------------------------------------------------------------

    /** `geo:lat,lng`, `geo:0,0?q=lat,lng(Label)` or `geo:0,0?q=Some+address`. */
    private fun parseGeo(rest: String): SharedRoute? {
        val position = coordinates(rest.substringBefore('?').substringBefore(';'))?.takeUnless { it.latitude == 0.0 && it.longitude == 0.0 }
        val q =
            rest
                .substringAfter('?', "")
                .split('&')
                .firstOrNull { it.startsWith("q=") }
                ?.removePrefix("q=")
                ?.let(::decode)
        val labelled = q?.let { GEO_QUERY.matchEntire(it) }
        val place =
            when {
                labelled != null -> {
                    SharedPlace(
                        labelled.groupValues[3].trim().ifEmpty {
                            null
                        },
                        point(labelled.groupValues[1], labelled.groupValues[2]),
                    )
                }

                !q.isNullOrBlank() -> {
                    SharedPlace(q.trim(), position)
                }

                position != null -> {
                    SharedPlace(null, position)
                }

                else -> {
                    return null
                }
            }
        return SharedRoute(null, place)
    }

    private fun parseApple(query: Map<String, String>): SharedRoute? {
        val destination =
            place(query["daddr"])
                ?: query["q"]?.let { SharedPlace(it.trim(), query["ll"]?.let(::coordinates)) }
                ?: place(query["ll"])
                ?: place(query["address"])
                ?: return null
        return SharedRoute(place(query["saddr"]), destination)
    }

    private fun parseOsm(query: Map<String, String>): SharedRoute? {
        query["route"]?.split(';')?.mapNotNull(::coordinates)?.takeIf { it.size >= 2 }?.let { points ->
            return SharedRoute(SharedPlace(null, points.first()), SharedPlace(null, points.last()))
        }
        return placeOf(query["mlat"], query["mlon"])?.let { SharedRoute(null, it) }
    }

    // --- Google's data block -------------------------------------------------------------------

    /** One token of the `!1m5!1m4!1s…!8m2!3d48.2!4d16.3` notation; `m<n>` groups the next n tokens. */
    private class Node(
        val field: Int,
        val type: Char,
        val value: String,
        val children: List<Node>,
    ) {
        fun selfAndDescendants(): Sequence<Node> = sequenceOf(this) + children.asSequence().flatMap { it.selfAndDescendants() }

        fun child(
            field: Int,
            type: Char,
        ): Node? = children.firstOrNull { it.field == field && it.type == type }

        /** `!8m2!3d<lat>!4d<lng>` (current links) or `!2m2!1d<lng>!2d<lat>` (older ones). */
        fun position(): GeoPoint? =
            selfAndDescendants().firstNotNullOfOrNull { node ->
                val lat3 = node.child(3, 'd')?.value
                val lng4 = node.child(4, 'd')?.value
                val lng1 = node.child(1, 'd')?.value
                val lat2 = node.child(2, 'd')?.value
                when {
                    lat3 != null && lng4 != null -> point(lat3, lng4)
                    node.field == 2 && node.type == 'm' && lng1 != null && lat2 != null -> point(lat2, lng1)
                    else -> null
                }
            }

        /** `!4e1` directly in a waypoint marks "Your location". */
        fun isMyLocation(): Boolean = child(4, 'e')?.value == "1"
    }

    private fun parseData(data: String): List<Node> {
        val tokens =
            data.split('!').filter { it.isNotEmpty() }.map { token ->
                val match = TOKEN.matchEntire(token)
                if (match ==
                    null
                ) {
                    Triple(0, '?', token)
                } else {
                    Triple(match.groupValues[1].toInt(), match.groupValues[2][0], match.groupValues[3])
                }
            }
        var next = 0

        fun read(count: Int): List<Node> {
            val nodes = ArrayList<Node>()
            var remaining = count
            while (remaining > 0 && next < tokens.size) {
                val (field, type, value) = tokens[next++]
                remaining--
                if (type == 'm') {
                    val start = next
                    val children = read(value.toIntOrNull() ?: 0)
                    remaining -= next - start
                    nodes += Node(field, type, value, children)
                } else {
                    nodes += Node(field, type, value, emptyList())
                }
            }
            return nodes
        }
        return read(Int.MAX_VALUE)
    }

    /** The waypoints of a directions block: the message whose children are two or more `1m` entries. */
    private fun waypointsOf(nodes: List<Node>): List<Node> =
        nodes
            .asSequence()
            .flatMap { it.selfAndDescendants() }
            .map { node -> node.children.filter { it.field == 1 && it.type == 'm' } }
            .firstOrNull { it.size >= 2 }
            .orEmpty()

    // --- helpers -------------------------------------------------------------------------------

    private fun place(text: String?): SharedPlace? {
        val value = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return coordinates(value)?.let { SharedPlace(null, it) } ?: SharedPlace(value, null)
    }

    private fun placeOf(
        lat: String?,
        lng: String?,
    ): SharedPlace? = point(lat ?: return null, lng ?: return null)?.let { SharedPlace(null, it) }

    private fun coordinates(text: String): GeoPoint? = COORDINATES.matchEntire(text)?.let { point(it.groupValues[1], it.groupValues[2]) }

    private fun point(
        lat: String,
        lng: String,
    ): GeoPoint? {
        val latitude = lat.toDoubleOrNull() ?: return null
        val longitude = lng.toDoubleOrNull() ?: return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return GeoPoint(latitude, longitude)
    }

    private fun queryOf(uri: URI): Map<String, String> =
        uri.rawQuery
            .orEmpty()
            .split('&')
            .filter { '=' in it }
            .associate { it.substringBefore('=') to decode(it.substringAfter('=')) }

    private fun decode(value: String): String =
        try {
            URLDecoder.decode(value, StandardCharsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            value
        }

    private fun uri(link: String): URI? =
        runCatching { URI(link) }.getOrNull()
            ?: runCatching { URI(link.replace(" ", "%20").replace("|", "%7C").replace("\"", "%22")) }.getOrNull()
}
