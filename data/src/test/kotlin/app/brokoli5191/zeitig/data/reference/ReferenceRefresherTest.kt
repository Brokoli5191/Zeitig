package app.brokoli5191.zeitig.data.reference

import app.brokoli5191.zeitig.data.Fixtures
import app.brokoli5191.zeitig.data.net.HttpClients
import app.brokoli5191.zeitig.data.net.RateLimiter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration as KDuration

class ReferenceRefresherTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val server = MockWebServer()
    private val bundled = DirectorySnapshotSource(Fixtures.bundledSnapshot)
    private val bundledVersion = SnapshotLoader.version(bundled)!!
    private var remoteVersion = bundledVersion
    private var serveHtml = false
    private val now = Instant.parse("2026-11-20T21:00:00Z")

    @Before
    fun setUp() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val name = request.url.pathSegments.last()
                    if (serveHtml && name != SnapshotFiles.VERSION) {
                        return MockResponse.Builder().body("<html>403</html>").build()
                    }
                    val body =
                        if (name == SnapshotFiles.VERSION) {
                            "DateValidFrom;VersionDate\r\n$remoteVersion;$remoteVersion\r\n"
                        } else {
                            File(Fixtures.bundledSnapshot, name).takeIf { it.isFile }?.readText()
                                ?: return MockResponse.Builder().code(404).build()
                        }
                    return MockResponse.Builder().body(body).build()
                }
            }
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun refresher(
        root: File,
        at: Instant = now,
    ) = ReferenceRefresher(
        client = HttpClients.create("test"),
        rateLimiter = RateLimiter(KDuration.ZERO),
        clock = Clock.fixed(at, ZoneOffset.UTC),
        bundled = bundled,
        rootDirectory = root,
        ioDispatcher = Dispatchers.IO,
        baseUrl = server.url("/doku/ogd/"),
    )

    @Test
    fun sameVersionOnlyRecordsTheCheck() =
        runBlocking<Unit> {
            val root = temp.newFolder()
            val result = refresher(root).refreshIfDue()
            assertEquals(RefreshResult.UpToDate(bundledVersion), result)
            assertEquals(1, server.requestCount, "only the version file is fetched")
            assertEquals(now, refresher(root).lastCheck())
            assertFalse(File(root, "current").exists())
        }

    @Test
    fun checksAtMostOncePerWeekUnlessForced() =
        runBlocking<Unit> {
            val root = temp.newFolder()
            refresher(root).refreshIfDue()
            assertEquals(RefreshResult.NotDue, refresher(root, now.plus(Duration.ofDays(6))).refreshIfDue())
            assertIs<RefreshResult.UpToDate>(refresher(root, now.plus(Duration.ofDays(6))).refreshIfDue(force = true))
            // The forced check on day 6 restarts the week.
            assertEquals(RefreshResult.NotDue, refresher(root, now.plus(Duration.ofDays(12))).refreshIfDue())
            assertIs<RefreshResult.UpToDate>(refresher(root, now.plus(Duration.ofDays(13))).refreshIfDue())
        }

    @Test
    fun newVersionIsDownloadedValidatedAndSwappedIn() =
        runBlocking<Unit> {
            val root = temp.newFolder()
            remoteVersion = "2026-12-01T06:00:00+01:00"
            val result = refresher(root).refreshIfDue()
            assertEquals(RefreshResult.Updated(remoteVersion), result)
            val current = DirectorySnapshotSource(File(root, "current"))
            assertTrue(current.isComplete())
            assertEquals(remoteVersion, SnapshotLoader.version(current))
            assertFalse(File(root, "staging").exists())

            // The repository now prefers the refreshed copy.
            val repository = ReferenceDataRepository(bundled, File(root, "current"), Dispatchers.IO)
            assertTrue(repository.network().stopPoints.isNotEmpty())

            // Unchanged upstream afterwards: nothing is downloaded again.
            val requestsBefore = server.requestCount
            assertIs<RefreshResult.UpToDate>(refresher(root, now.plus(Duration.ofDays(8))).refreshIfDue())
            assertEquals(requestsBefore + 1, server.requestCount)
        }

    @Test
    fun invalidDownloadKeepsTheActiveSnapshot() =
        runBlocking<Unit> {
            val root = temp.newFolder()
            remoteVersion = "2026-12-01T06:00:00+01:00"
            serveHtml = true
            val result = refresher(root).refreshIfDue()
            assertIs<RefreshResult.Failed>(result)
            assertFalse(File(root, "current").exists())
            assertFalse(File(root, "staging").exists())
            assertEquals(null, refresher(root).lastCheck(), "a failed attempt is retried next time")
        }

    @Test
    fun gapsInARefreshedSnapshotAreFilledFromTheBundledOne() =
        runBlocking<Unit> {
            val current = temp.newFolder("current")
            SnapshotFiles.ALL.forEach { File(Fixtures.bundledSnapshot, it).copyTo(File(current, it)) }
            val stopFile = File(current, SnapshotFiles.STOP_POINTS)
            // Upstream sometimes publishes rows like this one (seen 2026-11-20 for stop 299).
            stopFile.writeText(stopFile.readText().replace(Regex("""(?m)^299;[^\r\n]*"""), "299;;Raxstraße;;;0000000000;0000000000"))
            val network = ReferenceDataRepository(bundled, current, Dispatchers.IO).network()
            val stop = network.stopPoints.getValue(app.brokoli5191.zeitig.core.model.StopId(299))
            assertEquals("Raxstraße", stop.name)
            assertTrue(stop.location != null && stop.diva != null, stop.toString())
        }

    @Test
    fun brokenRefreshedCopyFallsBackToTheBundledSnapshot() =
        runBlocking<Unit> {
            val current = temp.newFolder("current")
            SnapshotFiles.ALL.forEach { File(current, it).writeText("garbage") }
            val repository = ReferenceDataRepository(bundled, current, Dispatchers.IO)
            assertTrue(repository.network().stopPoints.size > 4_000)
            repository.invalidate()
            assertTrue(repository.network().lines.isNotEmpty())
        }
}
