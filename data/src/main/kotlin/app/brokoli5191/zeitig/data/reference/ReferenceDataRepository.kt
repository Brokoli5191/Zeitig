package app.brokoli5191.zeitig.data.reference

import app.brokoli5191.zeitig.core.network.TransitNetwork
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Loads the reference network once and keeps it in memory. A refreshed snapshot in
 * [refreshedDirectory] wins over the bundled one when it is complete and parses.
 */
public class ReferenceDataRepository(
    private val bundled: SnapshotSource,
    private val refreshedDirectory: File,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private var cached: TransitNetwork? = null

    public suspend fun network(): TransitNetwork =
        mutex.withLock {
            cached ?: withContext(ioDispatcher) { load() }.also { cached = it }
        }

    /** Drops the in-memory copy so the next [network] call reads the newest snapshot. */
    public suspend fun invalidate() {
        mutex.withLock { cached = null }
    }

    private fun load(): TransitNetwork {
        val refreshed = DirectorySnapshotSource(refreshedDirectory)
        if (refreshed.isComplete()) {
            try {
                return SnapshotLoader.load(refreshed, gapFiller = bundled)
            } catch (_: IOException) {
                // Fall through to the bundled snapshot; a broken refresh must never break the app.
            } catch (_: IllegalArgumentException) {
            }
        }
        return SnapshotLoader.load(bundled)
    }
}

/** Parses a complete snapshot into a [TransitNetwork]. */
public object SnapshotLoader {
    /**
     * Upstream snapshots occasionally drop the DIVA or the coordinates of individual stop points
     * (observed 2026-11-20 for e.g. Raxstraße). With [gapFiller], such gaps are filled from an older
     * snapshot, so a refresh never makes a stop unlocatable.
     */
    public fun load(
        source: SnapshotSource,
        gapFiller: SnapshotSource? = null,
    ): TransitNetwork {
        var stopPoints = read(source, SnapshotFiles.STOP_POINTS, OgdCsv::stopPoints)
        var stations = read(source, SnapshotFiles.STATIONS, OgdCsv::stations)
        if (gapFiller != null && (stopPoints.any { it.location == null || it.diva == null } || stations.any { it.location == null })) {
            val oldPoints = read(gapFiller, SnapshotFiles.STOP_POINTS, OgdCsv::stopPoints).associateBy { it.id }
            stopPoints =
                stopPoints.map { point ->
                    val old = oldPoints[point.id] ?: return@map point
                    point.copy(
                        diva = point.diva ?: old.diva,
                        municipality = point.municipality ?: old.municipality,
                        location = point.location ?: old.location,
                    )
                }
            val oldStations = read(gapFiller, SnapshotFiles.STATIONS, OgdCsv::stations).associateBy { it.diva }
            stations = stations.map { station -> station.copy(location = station.location ?: oldStations[station.diva]?.location) }
        }
        return TransitNetwork(
            stopPoints = stopPoints,
            stations = stations,
            lines = read(source, SnapshotFiles.LINES, OgdCsv::lines),
            patterns = read(source, SnapshotFiles.PATTERNS, OgdCsv::patterns),
        )
    }

    private fun <T> read(
        source: SnapshotSource,
        file: String,
        parse: (java.io.BufferedReader) -> T,
    ): T = source.open(file).bufferedReader(Charsets.UTF_8).use(parse)

    public fun version(source: SnapshotSource): String? =
        source.open(
            SnapshotFiles.VERSION,
        ).bufferedReader(Charsets.UTF_8).use(OgdCsv::version)
}
