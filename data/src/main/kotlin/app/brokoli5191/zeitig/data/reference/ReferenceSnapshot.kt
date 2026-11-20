package app.brokoli5191.zeitig.data.reference

import java.io.File
import java.io.InputStream

/** The five files of a reference-data snapshot, by their upstream names. */
public object SnapshotFiles {
    public const val STOP_POINTS: String = "wienerlinien-ogd-haltepunkte.csv"
    public const val STATIONS: String = "wienerlinien-ogd-haltestellen.csv"
    public const val LINES: String = "wienerlinien-ogd-linien.csv"
    public const val PATTERNS: String = "wienerlinien-ogd-fahrwegverlaeufe.csv"
    public const val VERSION: String = "wienerlinien-ogd-version.csv"

    public val DATA: List<String> = listOf(STOP_POINTS, STATIONS, LINES, PATTERNS)
    public val ALL: List<String> = DATA + VERSION
}

/** Where a snapshot is read from: the APK's bundled assets or a refreshed copy on disk. */
public fun interface SnapshotSource {
    public fun open(fileName: String): InputStream
}

public class DirectorySnapshotSource(
    private val directory: File,
) : SnapshotSource {
    override fun open(fileName: String): InputStream = File(directory, fileName).inputStream()

    public fun isComplete(): Boolean = SnapshotFiles.ALL.all { File(directory, it).isFile }
}
