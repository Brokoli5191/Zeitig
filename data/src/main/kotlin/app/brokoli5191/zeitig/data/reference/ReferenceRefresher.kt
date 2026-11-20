package app.brokoli5191.zeitig.data.reference

import app.brokoli5191.zeitig.data.net.RateLimiter
import app.brokoli5191.zeitig.data.net.await
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant

public sealed interface RefreshResult {
    public data object NotDue : RefreshResult

    public data class UpToDate(
        val version: String?,
    ) : RefreshResult

    public data class Updated(
        val version: String?,
    ) : RefreshResult

    public data class Failed(
        val reason: String,
    ) : RefreshResult
}

/**
 * Keeps the reference snapshot fresh: at most once per [interval] it compares the upstream version
 * file with the active snapshot and, if it differs, downloads all files into a staging directory,
 * validates them and swaps them in. The bundled snapshot is never modified.
 */
public class ReferenceRefresher(
    private val client: OkHttpClient,
    private val rateLimiter: RateLimiter,
    private val clock: Clock,
    private val bundled: SnapshotSource,
    private val rootDirectory: File,
    private val ioDispatcher: CoroutineDispatcher,
    private val baseUrl: HttpUrl = DEFAULT_BASE_URL.toHttpUrl(),
    private val interval: Duration = Duration.ofDays(7),
) {
    public val currentDirectory: File = File(rootDirectory, "current")
    private val stagingDirectory = File(rootDirectory, "staging")
    private val retiredDirectory = File(rootDirectory, "retired")
    private val stampFile = File(rootDirectory, "last-check")

    public suspend fun refreshIfDue(force: Boolean = false): RefreshResult =
        withContext(ioDispatcher) {
            if (!force && !isDue()) return@withContext RefreshResult.NotDue
            try {
                val versionText = download(SnapshotFiles.VERSION)
                val remoteVersion = OgdCsv.version(versionText.byteInputStream().bufferedReader())
                if (remoteVersion != null && remoteVersion == activeVersion()) {
                    stamp()
                    return@withContext RefreshResult.UpToDate(remoteVersion)
                }
                stagingDirectory.deleteRecursively()
                check(stagingDirectory.mkdirs()) { "cannot create $stagingDirectory" }
                for (file in SnapshotFiles.DATA) File(stagingDirectory, file).writeText(download(file))
                File(stagingDirectory, SnapshotFiles.VERSION).writeText(versionText)
                validate(DirectorySnapshotSource(stagingDirectory))
                swapIn()
                stamp()
                RefreshResult.Updated(remoteVersion)
            } catch (e: IOException) {
                stagingDirectory.deleteRecursively()
                RefreshResult.Failed(e.message ?: e.javaClass.simpleName)
            } catch (e: IllegalArgumentException) {
                stagingDirectory.deleteRecursively()
                RefreshResult.Failed(e.message ?: "invalid snapshot")
            } catch (e: IllegalStateException) {
                stagingDirectory.deleteRecursively()
                RefreshResult.Failed(e.message ?: "invalid snapshot")
            }
        }

    public fun lastCheck(): Instant? = stampFile.takeIf { it.isFile }?.readText()?.trim()?.toLongOrNull()?.let(Instant::ofEpochMilli)

    private fun isDue(): Boolean {
        val last = lastCheck() ?: return true
        return Duration.between(last, clock.instant()) >= interval
    }

    private fun activeVersion(): String? {
        val current = DirectorySnapshotSource(currentDirectory)
        return if (current.isComplete()) SnapshotLoader.version(current) else SnapshotLoader.version(bundled)
    }

    private suspend fun download(fileName: String): String =
        rateLimiter.withPermit {
            val request = Request.Builder().url(baseUrl.newBuilder().addPathSegment(fileName).build()).build()
            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $fileName")
                response.body.string()
            }
        }

    private fun validate(source: DirectorySnapshotSource) {
        val network = SnapshotLoader.load(source)
        require(network.stopPoints.size >= MIN_STOP_POINTS) { "only ${network.stopPoints.size} stop points" }
        require(network.stations.size >= MIN_STATIONS) { "only ${network.stations.size} stations" }
        require(network.lines.size >= MIN_LINES) { "only ${network.lines.size} lines" }
        require(network.patterns.isNotEmpty()) { "no line patterns" }
    }

    private fun swapIn() {
        retiredDirectory.deleteRecursively()
        if (currentDirectory.exists() && !currentDirectory.renameTo(retiredDirectory)) {
            throw IOException("cannot retire $currentDirectory")
        }
        if (!stagingDirectory.renameTo(currentDirectory)) {
            retiredDirectory.renameTo(currentDirectory)
            throw IOException("cannot activate $stagingDirectory")
        }
        retiredDirectory.deleteRecursively()
    }

    private fun stamp() {
        rootDirectory.mkdirs()
        stampFile.writeText(clock.millis().toString())
    }

    public companion object {
        public const val DEFAULT_BASE_URL: String = "https://www.wienerlinien.at/ogd_realtime/doku/ogd/"
        private const val MIN_STOP_POINTS = 1_000
        private const val MIN_STATIONS = 500
        private const val MIN_LINES = 50
    }
}
