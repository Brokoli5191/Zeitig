package app.brokoli5191.zeitig.data.share

import app.brokoli5191.zeitig.core.provider.FailureReason
import app.brokoli5191.zeitig.core.share.SharedRoutes
import app.brokoli5191.zeitig.data.net.await
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Result of expanding a map short link. */
public sealed interface ShortLinkResult {
    public data class Resolved(
        val url: String,
    ) : ShortLinkResult

    public data class Failed(
        val reason: FailureReason,
    ) : ShortLinkResult
}

/**
 * Expands a map short link such as `maps.app.goo.gl/…` by reading the redirect target from the
 * issuer *without following it*: the only request goes to the short-link host, and the full map
 * page is never loaded. Runs only when the user shares a link into the app.
 */
public class ShortLinkResolver(
    client: OkHttpClient,
    private val isShortLink: (HttpUrl) -> Boolean = { SharedRoutes.isShortLink(it.toString()) },
    private val maxHops: Int = 3,
) {
    private val client =
        client
            .newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()

    public suspend fun resolve(link: String): ShortLinkResult {
        var current = link.toHttpUrlOrNull()?.let(::upgraded) ?: return ShortLinkResult.Failed(FailureReason.INVALID_REQUEST)
        repeat(maxHops) {
            if (!isShortLink(current)) return ShortLinkResult.Resolved(current.toString())
            val request =
                Request
                    .Builder()
                    .url(current)
                    .get()
                    .build()
            val response =
                try {
                    client.newCall(request).await()
                } catch (_: IOException) {
                    return ShortLinkResult.Failed(FailureReason.NETWORK)
                }
            current =
                response.use {
                    val target = it.header("Location")?.let { location -> current.resolve(location) }
                    when {
                        it.code in 300..399 && target != null -> upgraded(target)
                        it.code == 404 || it.code == 410 -> return ShortLinkResult.Failed(FailureReason.INVALID_REQUEST)
                        it.code == 429 -> return ShortLinkResult.Failed(FailureReason.RATE_LIMITED)
                        it.code >= 500 -> return ShortLinkResult.Failed(FailureReason.SERVER)
                        else -> return ShortLinkResult.Failed(FailureReason.PARSE)
                    }
                }
        }
        return if (isShortLink(current)) ShortLinkResult.Failed(FailureReason.PARSE) else ShortLinkResult.Resolved(current.toString())
    }

    /** Short-link hosts all speak HTTPS; never send the request in clear text. */
    private fun upgraded(url: HttpUrl): HttpUrl =
        if (url.scheme == "http" && SharedRoutes.isShortLink(url.toString())) url.newBuilder().scheme("https").build() else url
}
