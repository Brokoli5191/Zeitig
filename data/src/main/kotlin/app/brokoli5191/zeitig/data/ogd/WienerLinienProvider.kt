package app.brokoli5191.zeitig.data.ogd

import app.brokoli5191.zeitig.core.provider.DepartureProvider
import app.brokoli5191.zeitig.core.provider.DepartureRequest
import app.brokoli5191.zeitig.core.provider.ProviderResult

/** [DepartureProvider] backed by the Wiener Linien real-time monitor. */
public class WienerLinienProvider(
    private val api: WienerLinienApi,
) : DepartureProvider {
    override val id: String = "wienerlinien"

    override suspend fun departures(request: DepartureRequest): ProviderResult =
        api.monitor(
            stopIds = request.stopIds,
            stations = request.stations,
            trafficInfo = if (request.includeDisruptions) WienerLinienApi.DEFAULT_TRAFFIC_INFO else emptyList(),
        )
}
