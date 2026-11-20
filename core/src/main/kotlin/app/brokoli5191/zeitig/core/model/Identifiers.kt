package app.brokoli5191.zeitig.core.model

/** A Wiener Linien "Haltepunkt" (platform/direction), the `stopId` (formerly RBL) of the monitor API. */
@JvmInline
public value class StopId(
    public val value: Int,
) {
    override fun toString(): String = value.toString()
}

/** A station ("Haltestelle") identified by its DIVA number; groups several [StopId]s. */
@JvmInline
public value class Diva(
    public val value: Int,
) {
    override fun toString(): String = value.toString()
}
