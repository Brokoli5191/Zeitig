package app.brokoli5191.zeitig.core.search

import app.brokoli5191.zeitig.core.model.Diva
import app.brokoli5191.zeitig.core.model.GeoPoint
import kotlin.math.min

/** A station as the search knows it. [weight] breaks ties, e.g. the number of stop points. */
public data class SearchableStation(
    val diva: Diva,
    val name: String,
    val location: GeoPoint?,
    val weight: Int = 0,
)

public data class StationMatch(
    val station: SearchableStation,
    val score: Int,
)

/**
 * Offline fuzzy search over the bundled station list. Every query token must match a station token
 * as exact word, prefix, or – for tokens of four letters and more – with a small edit distance.
 */
public class StopSearch(
    stations: List<SearchableStation>,
) {
    private class Entry(
        val station: SearchableStation,
        val normalized: String,
        val tokens: List<String>,
    )

    private val entries =
        stations.map { station ->
            val normalized = SearchText.normalize(station.name)
            Entry(station, normalized, SearchText.tokens(normalized))
        }

    public val size: Int get() = entries.size

    public fun search(
        query: String,
        limit: Int = 20,
    ): List<StationMatch> {
        val normalizedQuery = SearchText.normalize(query)
        val queryTokens = SearchText.tokens(normalizedQuery)
        if (queryTokens.isEmpty()) return emptyList()
        return entries
            .mapNotNull { entry -> score(entry, normalizedQuery, queryTokens)?.let { StationMatch(entry.station, it) } }
            .sortedWith(
                compareByDescending<StationMatch> { it.score }
                    .thenByDescending { it.station.weight }
                    .thenBy { it.station.name.length }
                    .thenBy { it.station.name },
            ).take(limit)
    }

    private fun score(
        entry: Entry,
        normalizedQuery: String,
        queryTokens: List<String>,
    ): Int? {
        if (entry.normalized == normalizedQuery) return EXACT_NAME
        var total = 0
        for ((index, queryToken) in queryTokens.withIndex()) {
            val best = entry.tokens.withIndex().maxOfOrNull { (position, token) -> tokenScore(queryToken, token, position == index) } ?: 0
            if (best == 0) return null
            total += best
        }
        if (entry.normalized.startsWith(normalizedQuery)) total += NAME_PREFIX_BONUS
        return total
    }

    private fun tokenScore(
        query: String,
        token: String,
        samePosition: Boolean,
    ): Int {
        val positional = if (samePosition) POSITION_BONUS else 0
        return when {
            token == query -> {
                EXACT_TOKEN + positional
            }

            token.startsWith(query) -> {
                PREFIX_TOKEN + positional
            }

            query.length >= FUZZY_MIN_LENGTH -> {
                val allowed = if (query.length >= 7) 2 else 1
                val candidate = token.take(query.length + allowed)
                val distance = boundedDamerauLevenshtein(query, candidate, allowed)
                val prefixDistance = boundedDamerauLevenshtein(query, token.take(query.length), allowed)
                if (min(distance, prefixDistance) <= allowed) FUZZY_TOKEN + positional - min(distance, prefixDistance) else 0
            }

            else -> {
                0
            }
        }
    }

    private companion object {
        const val EXACT_NAME = 10_000
        const val NAME_PREFIX_BONUS = 50
        const val EXACT_TOKEN = 100
        const val PREFIX_TOKEN = 70
        const val FUZZY_TOKEN = 40
        const val POSITION_BONUS = 10
        const val FUZZY_MIN_LENGTH = 4
    }
}

/** Optimal-string-alignment distance with an early exit once every cell exceeds [maxDistance]. */
internal fun boundedDamerauLevenshtein(
    a: String,
    b: String,
    maxDistance: Int,
): Int {
    if (kotlin.math.abs(a.length - b.length) > maxDistance) return maxDistance + 1
    val rows = a.length + 1
    val cols = b.length + 1
    val d = Array(rows) { IntArray(cols) }
    for (i in 0 until rows) d[i][0] = i
    for (j in 0 until cols) d[0][j] = j
    for (i in 1 until rows) {
        var rowMin = Int.MAX_VALUE
        for (j in 1 until cols) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            var value = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) value = min(value, d[i - 2][j - 2] + 1)
            d[i][j] = value
            rowMin = min(rowMin, value)
        }
        if (rowMin > maxDistance) return maxDistance + 1
    }
    return d[a.length][b.length]
}
