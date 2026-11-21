package app.brokoli5191.zeitig.data.share

import app.brokoli5191.zeitig.core.provider.FailureReason
import app.brokoli5191.zeitig.data.net.HttpClients
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShortLinkResolverTest {
    private val server = MockWebServer()
    private lateinit var resolver: ShortLinkResolver

    private val target = "https://www.google.com/maps/dir/Stephansplatz/Westbahnhof/data=!4m2!3e3?g_st=ac"

    @Before
    fun setUp() {
        server.start()
        // The mock server stands in for maps.app.goo.gl: its own URLs count as short links.
        resolver = ShortLinkResolver(HttpClients.create("test"), isShortLink = { it.port == server.port })
    }

    @After
    fun tearDown() = server.close()

    private fun respond(
        code: Int,
        location: String? = null,
    ) = server.enqueue(
        MockResponse
            .Builder()
            .code(code)
            .apply { location?.let { addHeader("Location", it) } }
            .build(),
    )

    @Test
    fun `reads the redirect target without following it`() =
        runBlocking<Unit> {
            respond(302, target)

            val result = resolver.resolve(server.url("/AbCdEf?g_st=ac").toString())

            assertEquals(ShortLinkResult.Resolved(target), result)
            val request = server.takeRequest()
            assertEquals("/AbCdEf?g_st=ac", request.target)
            assertEquals(1, server.requestCount, "google.com itself is never contacted")
            assertNull(request.headers["Cookie"])
        }

    @Test
    fun `follows a chain of short links, relative locations included`() =
        runBlocking<Unit> {
            respond(301, "/second")
            respond(302, target)

            assertEquals(ShortLinkResult.Resolved(target), resolver.resolve(server.url("/first").toString()))
            assertEquals(2, server.requestCount)
        }

    @Test
    fun `gives up on endless short-link loops`() =
        runBlocking<Unit> {
            repeat(3) { respond(302, "/again") }

            assertEquals(ShortLinkResult.Failed(FailureReason.PARSE), resolver.resolve(server.url("/loop").toString()))
        }

    @Test
    fun `maps status codes to failure reasons`() =
        runBlocking<Unit> {
            respond(404)
            respond(429)
            respond(503)
            respond(200)
            respond(302)

            assertEquals(ShortLinkResult.Failed(FailureReason.INVALID_REQUEST), resolver.resolve(server.url("/gone").toString()))
            assertEquals(ShortLinkResult.Failed(FailureReason.RATE_LIMITED), resolver.resolve(server.url("/busy").toString()))
            assertEquals(ShortLinkResult.Failed(FailureReason.SERVER), resolver.resolve(server.url("/down").toString()))
            assertEquals(ShortLinkResult.Failed(FailureReason.PARSE), resolver.resolve(server.url("/page").toString()))
            assertEquals(ShortLinkResult.Failed(FailureReason.PARSE), resolver.resolve(server.url("/nowhere").toString()))
        }

    @Test
    fun `offline and malformed input`() =
        runBlocking<Unit> {
            val url = server.url("/offline").toString()
            server.close()

            assertEquals(ShortLinkResult.Failed(FailureReason.NETWORK), resolver.resolve(url))
            assertEquals(ShortLinkResult.Failed(FailureReason.INVALID_REQUEST), resolver.resolve("geo:48.2,16.3"))
        }

    @Test
    fun `links that are already expanded need no request`() =
        runBlocking<Unit> {
            val plain = ShortLinkResolver(HttpClients.create("test"))

            assertEquals(ShortLinkResult.Resolved(target), plain.resolve(target))
        }
}
