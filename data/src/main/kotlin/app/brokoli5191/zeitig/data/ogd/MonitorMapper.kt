package app.brokoli5191.zeitig.data.ogd

import app.brokoli5191.zeitig.core.model.Departure
import app.brokoli5191.zeitig.core.model.Direction
import app.brokoli5191.zeitig.core.model.Disruption
import app.brokoli5191.zeitig.core.model.DisruptionKind
import app.brokoli5191.zeitig.core.model.StopBoard
import app.brokoli5191.zeitig.core.model.StopId
import app.brokoli5191.zeitig.core.model.TransportType
import app.brokoli5191.zeitig.core.provider.FailureReason
import app.brokoli5191.zeitig.core.provider.ProviderResult
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import java.time.Instant

/** Turns a raw `monitor` / `trafficInfoList` response into domain objects. */
internal object MonitorMapper {
    private const val CODE_OK = 1
    private const val CODE_DB_UNAVAILABLE = 311
    private const val CODE_UNKNOWN_STOP = 312
    private const val CODE_RATE_LIMIT = 316
    private const val CODE_INVALID_PARAMETER = 320
    private const val CODE_MISSING_PARAMETER = 321
    private const val CODE_NO_DATA = 322

    fun parse(
        body: String,
        receivedAt: Instant,
    ): ProviderResult {
        val dto =
            try {
                OgdJson.decodeFromString(MonitorResponseDto.serializer(), body)
            } catch (e: SerializationException) {
                return ProviderResult.Failure(FailureReason.PARSE, e.message)
            } catch (e: IllegalArgumentException) {
                return ProviderResult.Failure(FailureReason.PARSE, e.message)
            }
        failureFor(dto.message)?.let { return it }
        val data = dto.data ?: return ProviderResult.Failure(FailureReason.PARSE, "response without data")
        val fetchedAt = OgdTime.parse(dto.message?.serverTime) ?: receivedAt
        return ProviderResult.Success(StopBoard(fetchedAt, departures(data), disruptions(data)))
    }

    private fun failureFor(message: MessageDto?): ProviderResult.Failure? {
        val code = message?.messageCode ?: return null
        val reason =
            when (code) {
                CODE_OK -> return null
                CODE_RATE_LIMIT -> FailureReason.RATE_LIMITED
                CODE_UNKNOWN_STOP -> FailureReason.UNKNOWN_STOP
                CODE_INVALID_PARAMETER, CODE_MISSING_PARAMETER -> FailureReason.INVALID_REQUEST
                CODE_DB_UNAVAILABLE, CODE_NO_DATA -> FailureReason.SERVER
                else -> FailureReason.SERVER
            }
        return ProviderResult.Failure(reason, "OGD $code: ${message.value.orEmpty()}")
    }

    private fun departures(data: MonitorDataDto): List<Departure> =
        data.monitors.flatMap { monitor ->
            val properties = monitor.locationStop?.properties
            val stopId = properties?.attributes?.get("rbl").asIntOrNull()?.let(::StopId) ?: return@flatMap emptyList()
            monitor.lines.flatMap { line ->
                line.departures?.departure.orEmpty().mapNotNull { departure ->
                    val time = departure.departureTime ?: return@mapNotNull null
                    val planned = OgdTime.parse(time.timePlanned) ?: OgdTime.parse(time.timeReal) ?: return@mapNotNull null
                    val vehicle = departure.vehicle
                    Departure(
                        stopId = stopId,
                        line = vehicle?.name ?: line.name ?: return@mapNotNull null,
                        towards = (vehicle?.towards ?: line.towards).orEmpty().trim(),
                        direction = Direction.fromOgd(vehicle?.direction ?: line.direction),
                        type = TransportType.fromOgd(vehicle?.type ?: line.type),
                        planned = planned,
                        realtime = OgdTime.parse(time.timeReal),
                        barrierFree = vehicle?.barrierFree ?: line.barrierFree,
                        platform = vehicle?.platform ?: line.platform ?: properties?.gate,
                    )
                }
            }
        }

    private fun disruptions(data: MonitorDataDto): List<Disruption> {
        val categoryNames = data.trafficInfoCategories.mapNotNull { c -> c.id?.let { it to c.name.orEmpty() } }.toMap()
        // The monitor links disruptions to stop points through refTrafficInfoNames.
        val stopsByInfo = HashMap<String, MutableSet<StopId>>()
        for (monitor in data.monitors) {
            val stopId =
                monitor.locationStop
                    ?.properties
                    ?.attributes
                    ?.get("rbl")
                    .asIntOrNull()
                    ?.let(::StopId) ?: continue
            for (name in monitor.refTrafficInfoNames) stopsByInfo.getOrPut(name) { LinkedHashSet() } += stopId
        }
        return data.trafficInfos.mapNotNull { info ->
            val id = info.name ?: return@mapNotNull null
            if (info.status.equals("resolved", ignoreCase = true)) return@mapNotNull null
            val kind = kindOf(categoryNames[info.refTrafficInfoCategoryId], info.refTrafficInfoCategoryId)
            val attributes = info.attributes
            val reason = attributes?.get("reason").asStringOrNull()
            val attributeStops = (attributes?.get("relatedStops") as? JsonArray).orEmpty().mapNotNull { it.asIntOrNull() }
            val relatedStops =
                (info.relatedStops.mapNotNull { it.asIntOrNull() } + attributeStops).map(::StopId).toSet() +
                    stopsByInfo[id].orEmpty()
            val attributeLines = (attributes?.get("relatedLines") as? JsonArray).orEmpty().mapNotNull { it.asStringOrNull() }
            Disruption(
                id = id,
                kind = kind,
                title = info.title?.replace('\n', ' ')?.trim().orEmpty(),
                description =
                    listOfNotNull(info.description?.trim(), reason?.trim()).filter {
                        it.isNotEmpty()
                    }.distinct().joinToString("\n"),
                relatedLines = (info.relatedLines + attributeLines).map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
                relatedStops = relatedStops,
                start = OgdTime.parse(info.time?.start),
                end = OgdTime.parse(info.time?.end),
            )
        }
    }

    // Names are documented; the numeric fallback reflects the ids observed in live responses.
    private fun kindOf(
        categoryName: String?,
        categoryId: Int?,
    ): DisruptionKind =
        when (categoryName?.lowercase() ?: "") {
            "stoerungkurz" -> {
                DisruptionKind.SHORT
            }

            "stoerunglang" -> {
                DisruptionKind.LONG
            }

            "aufzugsinfo" -> {
                DisruptionKind.ELEVATOR
            }

            "fahrtreppeninfo" -> {
                DisruptionKind.ESCALATOR
            }

            "information" -> {
                DisruptionKind.INFO
            }

            else -> {
                when (categoryId) {
                    1 -> DisruptionKind.ELEVATOR
                    2 -> DisruptionKind.LONG
                    3 -> DisruptionKind.SHORT
                    else -> DisruptionKind.INFO
                }
            }
        }
}
