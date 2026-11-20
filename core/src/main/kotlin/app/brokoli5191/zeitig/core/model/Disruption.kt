package app.brokoli5191.zeitig.core.model

import java.time.Instant

public enum class DisruptionKind {
    /** `stoerungkurz` – short line text shown on the platform displays. */
    SHORT,

    /** `stoerunglang` – operations-centre disruption with a full description. */
    LONG,

    /** `aufzugsinfo` – elevator out of service. */
    ELEVATOR,

    /** `fahrtreppeninfo` – escalator out of service. */
    ESCALATOR,

    /** `information` and anything unknown. */
    INFO,
}

public data class Disruption(
    val id: String,
    val kind: DisruptionKind,
    val title: String,
    val description: String,
    val relatedLines: Set<String>,
    val relatedStops: Set<StopId>,
    val start: Instant? = null,
    val end: Instant? = null,
) {
    /** Relevant for a favourite when it names the line or the stop – or names neither (general). */
    public fun affects(target: LineTarget): Boolean {
        val lineHit = relatedLines.any { it.equals(target.line, ignoreCase = true) }
        val stopHit = target.stopId in relatedStops
        return when (kind) {
            DisruptionKind.ELEVATOR, DisruptionKind.ESCALATOR -> stopHit || (relatedStops.isEmpty() && lineHit)
            else -> lineHit || stopHit
        }
    }
}
