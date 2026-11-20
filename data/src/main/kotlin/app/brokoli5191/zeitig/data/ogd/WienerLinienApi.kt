package app.brokoli5191.zeitig.data.ogd

import app.brokoli5191.zeitig.core.model.Diva
import app.brokoli5191.zeitig.core.model.StopId
import app.brokoli5191.zeitig.core.provider.FailureReason
import app.brokoli5191.zeitig.core.provider.ProviderResult
import app.brokoli5191.zeitig.data.net.RateLimiter
import app.brokoli5191.zeitig.data.net.await
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Clock

/**
 * Thin client for `ogd_realtime`. Every request passes the shared [RateLimiter], so the fair-use
 * rule (≥ 15 s between requests) holds across screens, services and widgets.
 */
public class WienerLinienApi(
    private val client: OkHttpClient,
    private val rateLimiter: RateLimiter,
    private val clock: Clock,
    private val baseUrl: HttpUrl = DEFAULT_BASE_URL.toHttpUrl(),
) {
    public suspend fun monitor(
        stopIds: Collection<StopId>,
        stations: Collection<Diva> = emptyList(),
        trafficInfo: Collection<String> = DEFAULT_TRAFFIC_INFO,
    ): ProviderResult {
        val url =
            baseUrl
                .newBuilder()
                .addPathSegment("monitor")
                .apply {
                    stopIds.distinct().sortedBy { it.value }.forEach { addQueryParameter("stopId", it.value.toString()) }
                    stations.distinct().sortedBy { it.value }.forEach { addQueryParameter("diva", it.value.toString()) }
                    trafficInfo.forEach { addQueryParameter("activateTrafficInfo", it) }
                }.build()
        return get(url)
    }

    /** Disruptions for lines (`relatedLine`) and/or stop points (`relatedStop`), e.g. along a route. */
    public suspend fun trafficInfoList(
        lines: Collection<String> = emptyList(),
        stopIds: Collection<StopId> = emptyList(),
        categories: Collection<String> = DEFAULT_TRAFFIC_INFO,
    ): ProviderResult {
        val url =
            baseUrl
                .newBuilder()
                .addPathSegment("trafficInfoList")
                .apply {
                    lines.distinct().sorted().forEach { addQueryParameter("relatedLine", it) }
                    stopIds.distinct().sortedBy { it.value }.forEach { addQueryParameter("relatedStop", it.value.toString()) }
                    categories.forEach { addQueryParameter("name", it) }
                }.build()
        return get(url)
    }

    private suspend fun get(url: HttpUrl): ProviderResult =
        rateLimiter.withPermit {
            val request =
                Request
                    .Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .build()
            try {
                client.newCall(request).await().use { response ->
                    when {
                        response.code == HTTP_TOO_MANY_REQUESTS -> ProviderResult.Failure(FailureReason.RATE_LIMITED, "HTTP 429")
                        response.code in 500..599 -> ProviderResult.Failure(FailureReason.SERVER, "HTTP ${response.code}")
                        !response.isSuccessful -> ProviderResult.Failure(FailureReason.INVALID_REQUEST, "HTTP ${response.code}")
                        else -> MonitorMapper.parse(response.body.string(), clock.instant())
                    }
                }
            } catch (e: IOException) {
                ProviderResult.Failure(FailureReason.NETWORK, e.message)
            }
        }

    public companion object {
        public const val DEFAULT_BASE_URL: String = "https://www.wienerlinien.at/ogd_realtime/"
        public val DEFAULT_TRAFFIC_INFO: List<String> = listOf("stoerungkurz", "stoerunglang", "aufzugsinfo")
        private const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
