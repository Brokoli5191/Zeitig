package app.brokoli5191.zeitig.core.model

/** Vehicle category, mapped from the OGD `type` values (`ptMetro`, `ptTram`, …). */
public enum class TransportType {
    METRO,
    TRAM,
    BUS,
    NIGHT_BUS,
    ON_DEMAND_BUS,
    TRAIN,
    OTHER,
    ;

    public companion object {
        public fun fromOgd(value: String?): TransportType =
            when (value) {
                "ptMetro" -> METRO
                "ptTram", "ptTramWLB", "ptTramVRT" -> TRAM
                "ptBusCity", "ptBusRegion" -> BUS
                "ptBusNight" -> NIGHT_BUS
                "ptRufBus" -> ON_DEMAND_BUS
                "ptTrainS", "ptTrain" -> TRAIN
                else -> OTHER
            }
    }
}
