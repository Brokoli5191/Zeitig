package app.brokoli5191.zeitig.data.routing

import app.brokoli5191.zeitig.core.model.GeoPoint
import app.brokoli5191.zeitig.core.network.TransitNetwork
import app.brokoli5191.zeitig.core.provider.FailureReason
import app.brokoli5191.zeitig.core.routing.GeocodeResult
import app.brokoli5191.zeitig.core.routing.JourneyPlanner
import app.brokoli5191.zeitig.core.routing.PlanRequest
import app.brokoli5191.zeitig.core.routing.PlanResult
import app.brokoli5191.zeitig.data.net.RateLimiter
import app.brokoli5191.zeitig.data.net.await
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.Locale

/**
 * Journey planning and address search via Transitous (volunteer-run MOTIS instance). Only called on
 * explicit user action and when a scheduled route needs a timetable for a non-WL first leg; a small
 * limiter keeps typing-driven geocoding from bursting. The request URL contains the coordinates of
 * origin and destination – callers must have the user's opt-in (see the privacy section).
 */
public class TransitousPlanner(
    private val client: OkHttpClient,
    private val rateLimiter: RateLimiter,
    private val network: suspend () -> TransitNetwork?,
    private val baseUrl: HttpUrl = DEFAULT_BASE_URL.toHttpUrl(),
    /** Results further away than this from the search centre are dropped (Vienna and surroundings). */
    private val geocodeRadiusMeters: Double = 80_000.0,
) : JourneyPlanner {
    override val id: String = "transitous"

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            explicitNulls = false
        }

    override suspend fun plan(request: PlanRequest): PlanResult {
        val url =
            baseUrl
                .newBuilder()
                .addPathSegments("v6/plan")
                .addQueryParameter("fromPlace", coordinate(request.from))
                .addQueryParameter("toPlace", coordinate(request.to))
                .addQueryParameter("time", request.time.toString())
                .addQueryParameter("arriveBy", request.arriveBy.toString())
                .addQueryParameter("numItineraries", request.itineraries.toString())
                .addQueryParameter("pedestrianProfile", if (request.accessible) "WHEELCHAIR" else "FOOT")
                .addQueryParameter("language", request.language)
                .build()
        return when (val body = get(url)) {
            is Body.Ok -> {
                try {
                    val itineraries = TransitousMapper.itineraries(json.decodeFromString(PlanResponseDto.serializer(), body.text))
                    val wienerLinien = network()
                    PlanResult.Success(
                        if (wienerLinien == null) itineraries else itineraries.map { it.copy(journey = wienerLinien.attach(it.journey)) },
                    )
                } catch (e: SerializationException) {
                    PlanResult.Failure(FailureReason.PARSE, e.message)
                } catch (e: IllegalArgumentException) {
                    PlanResult.Failure(FailureReason.PARSE, e.message)
                }
            }

            is Body.Failed -> {
                PlanResult.Failure(body.reason, body.message)
            }
        }
    }

    override suspend fun geocode(
        query: String,
        near: GeoPoint?,
        limit: Int,
    ): GeocodeResult {
        val text = query.trim()
        if (text.length < MIN_QUERY) return GeocodeResult.Success(emptyList())
        val center = near ?: VIENNA
        val url =
            baseUrl
                .newBuilder()
                .addPathSegments("v1/geocode")
                .addQueryParameter("text", text)
                .addQueryParameter("language", Locale.getDefault().language.ifEmpty { "de" })
                .addQueryParameter("place", coordinate(center))
                .addQueryParameter("numResults", (limit * 2).coerceAtMost(MAX_RESULTS).toString())
                .build()
        return when (val body = get(url)) {
            is Body.Ok -> {
                try {
                    val dtos = json.decodeFromString(ListSerializer(GeocodeMatchDto.serializer()), body.text)
                    GeocodeResult.Success(TransitousMapper.geocode(dtos, center, geocodeRadiusMeters, limit))
                } catch (e: SerializationException) {
                    GeocodeResult.Failure(FailureReason.PARSE, e.message)
                } catch (e: IllegalArgumentException) {
                    GeocodeResult.Failure(FailureReason.PARSE, e.message)
                }
            }

            is Body.Failed -> {
                GeocodeResult.Failure(body.reason, body.message)
            }
        }
    }

    private sealed interface Body {
        data class Ok(
            val text: String,
        ) : Body

        data class Failed(
            val reason: FailureReason,
            val message: String?,
        ) : Body
    }

    private suspend fun get(url: HttpUrl): Body =
        rateLimiter.withPermit {
            try {
                client.newCall(Request.Builder().url(url).header("Accept", "application/json").build()).await().use(::read)
            } catch (e: IOException) {
                Body.Failed(FailureReason.NETWORK, e.message)
            }
        }

    private fun read(response: Response): Body {
        val text = response.body.string()
        return when {
            response.isSuccessful -> Body.Ok(text)
            response.code == HTTP_TOO_MANY_REQUESTS -> Body.Failed(FailureReason.RATE_LIMITED, "HTTP 429")
            response.code >= HTTP_SERVER_ERROR -> Body.Failed(FailureReason.SERVER, "HTTP ${response.code}")
            else -> Body.Failed(FailureReason.INVALID_REQUEST, errorMessage(text) ?: "HTTP ${response.code}")
        }
    }

    private fun errorMessage(text: String): String? =
        try {
            json.decodeFromString(ErrorDto.serializer(), text).error
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun coordinate(point: GeoPoint): String = String.format(Locale.ROOT, "%.6f,%.6f", point.latitude, point.longitude)

    public companion object {
        public const val DEFAULT_BASE_URL: String = "https://api.transitous.org/api/"
        public const val SOURCES_URL: String = "https://transitous.org/sources/"
        public val VIENNA: GeoPoint = GeoPoint(48.2082, 16.3738)
        private const val MIN_QUERY = 2
        private const val MAX_RESULTS = 20
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_SERVER_ERROR = 500
    }
}
