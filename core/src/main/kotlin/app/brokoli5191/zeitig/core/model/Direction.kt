package app.brokoli5191.zeitig.core.model

/** OGD direction flag: `H` (hin, outbound) or `R` (retour, inbound). */
public enum class Direction {
    OUTBOUND,
    INBOUND,
    ;

    public companion object {
        public fun fromOgd(value: String?): Direction? =
            when (value?.trim()?.uppercase()) {
                "H", "1" -> OUTBOUND
                "R", "2" -> INBOUND
                else -> null
            }
    }
}
