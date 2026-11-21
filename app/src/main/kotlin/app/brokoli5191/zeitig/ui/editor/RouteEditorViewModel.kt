package app.brokoli5191.zeitig.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.brokoli5191.zeitig.AppContainer
import app.brokoli5191.zeitig.core.journey.Journey
import app.brokoli5191.zeitig.core.journey.LegPoint
import app.brokoli5191.zeitig.core.journey.Place
import app.brokoli5191.zeitig.core.model.Diva
import app.brokoli5191.zeitig.core.model.GeoPoint
import app.brokoli5191.zeitig.core.network.LineAtStop
import app.brokoli5191.zeitig.core.provider.FailureReason
import app.brokoli5191.zeitig.core.reminder.ReminderPlan
import app.brokoli5191.zeitig.core.route.Route
import app.brokoli5191.zeitig.core.routing.GeocodeMatch
import app.brokoli5191.zeitig.core.routing.GeocodeResult
import app.brokoli5191.zeitig.core.routing.Itinerary
import app.brokoli5191.zeitig.core.routing.PlanRequest
import app.brokoli5191.zeitig.core.routing.PlanResult
import app.brokoli5191.zeitig.core.schedule.Schedule
import app.brokoli5191.zeitig.core.schedule.ScheduleCalendar
import app.brokoli5191.zeitig.core.schedule.ScheduleMode
import app.brokoli5191.zeitig.core.search.StationMatch
import app.brokoli5191.zeitig.core.share.SharedPlace
import app.brokoli5191.zeitig.core.share.SharedRoutes
import app.brokoli5191.zeitig.data.share.ShortLinkResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.util.Locale

enum class TimeMode { WINDOW, ARRIVE_BY, NONE }

enum class PlaceTarget { ORIGIN, DESTINATION, BOARDING }

/** Progress of a route shared from a map app into the editor. */
enum class ImportStatus {
    READING,

    /** Nothing in the link Zeitig can use. */
    UNREADABLE,

    /** The short link could not be expanded (offline, or the issuer did not answer). */
    OFFLINE,

    /** Start and destination are in, but connections need the online planner the user has not enabled. */
    ASK_ONLINE,
}

data class PlaceUi(
    val name: String,
    val location: GeoPoint?,
    val diva: Diva? = null,
)

data class PlaceSearchUi(
    val target: PlaceTarget,
    val query: String = "",
    val stations: List<StationMatch> = emptyList(),
    val addresses: List<GeocodeMatch> = emptyList(),
    val searchingOnline: Boolean = false,
    val locating: Boolean = false,
    val locationFailed: Boolean = false,
)

data class EditorUiState(
    val routeId: Long = 0,
    val name: String = "",
    val origin: PlaceUi? = null,
    val destination: PlaceUi? = null,
    val timeMode: TimeMode = TimeMode.WINDOW,
    val days: Set<DayOfWeek> = Schedule.WORKDAYS,
    val windowStart: LocalTime = LocalTime.of(7, 15),
    val windowEnd: LocalTime = LocalTime.of(8, 30),
    val arriveBy: LocalTime = LocalTime.of(8, 0),
    val reminders: Set<Int> = ReminderPlan.DEFAULT.toSet(),
    val bufferMinutes: Int = 1,
    val accessible: Boolean = false,
    val notifications: Boolean = true,
    val routingEnabled: Boolean = false,
    val itineraries: List<Itinerary> = emptyList(),
    val selected: Int? = null,
    val planning: Boolean = false,
    val planFailure: FailureReason? = null,
    val boarding: PlaceUi? = null,
    /** Exact position of the chosen stop point (the station position is only approximate). */
    val boardingLocation: GeoPoint? = null,
    val boardingLines: List<LineAtStop> = emptyList(),
    val boardingLine: LineAtStop? = null,
    val search: PlaceSearchUi? = null,
    val saving: Boolean = false,
    val import: ImportStatus? = null,
) {
    val journey: Journey?
        get() =
            if (routingEnabled) {
                selected?.let { itineraries.getOrNull(it)?.journey }
            } else {
                val line = boardingLine ?: return null
                val stop = boarding ?: return null
                Journey.simple(
                    origin = Place(origin?.name.orEmpty(), origin?.location),
                    stop = LegPoint(stop.name, boardingLocation ?: stop.location, line.stopId),
                    line = line.line.name,
                    type = line.line.type,
                    towards = line.towards,
                    boarding = line.target,
                    plannedWalk = Duration.ZERO,
                )
            }

    val canSearch: Boolean get() = routingEnabled && origin?.location != null && destination?.location != null && !planning
    val canSave: Boolean get() = name.isNotBlank() && journey != null && !saving && (timeMode == TimeMode.NONE || days.isNotEmpty())
}

class RouteEditorViewModel(
    private val container: AppContainer,
    private val routeId: Long,
    private val sharedText: String? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(EditorUiState(routeId = routeId))
    val state: StateFlow<EditorUiState> = _state.asStateFlow()
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            val settings = container.settings.current()
            _state.update {
                it.copy(
                    routingEnabled = settings.routingEnabled,
                    reminders = settings.defaultReminders.toSet(),
                    bufferMinutes = settings.defaultBufferSeconds / 60,
                    accessible = settings.accessibleByDefault,
                )
            }
            if (routeId != 0L) container.routes.route(routeId)?.let(::load)
            sharedText?.let(::importShared)
        }
    }

    // --- routes shared from map apps ----------------------------------------------------------

    /** Reads a route shared from Google Maps & co.; also the retry after a failure. */
    fun importShared(text: String = sharedText.orEmpty()) {
        _state.update { it.copy(import = ImportStatus.READING) }
        viewModelScope.launch {
            val link = SharedRoutes.findLink(text)
            val expanded =
                if (link != null && SharedRoutes.isShortLink(link)) {
                    when (val result = container.shortLinks.resolve(link)) {
                        is ShortLinkResult.Resolved -> {
                            result.url
                        }

                        is ShortLinkResult.Failed -> {
                            val status = if (result.reason == FailureReason.NETWORK) ImportStatus.OFFLINE else ImportStatus.UNREADABLE
                            _state.update { it.copy(import = status) }
                            return@launch
                        }
                    }
                } else {
                    text
                }
            val route = SharedRoutes.parse(expanded)
            if (route == null) {
                _state.update { it.copy(import = ImportStatus.UNREADABLE) }
                return@launch
            }
            val online = _state.value.routingEnabled
            val origin = route.origin?.let { place(it, online) }
            val destination = place(route.destination, online)
            _state.update { s ->
                s.copy(
                    origin = origin ?: s.origin,
                    destination = destination,
                    name = s.name.ifBlank { destination.name },
                    itineraries = emptyList(),
                    selected = null,
                    import = if (online) null else ImportStatus.ASK_ONLINE,
                )
            }
            if (online) {
                if (_state.value.canSearch) plan()
            } else {
                suggestBoarding()
            }
        }
    }

    /** The user agreed to online planning for this route; it stays on, as in the settings. */
    fun enableOnlinePlanning() {
        viewModelScope.launch {
            container.settings.update { it.copy(routingEnabled = true) }
            _state.update { it.copy(routingEnabled = true, import = null) }
            val s = _state.value
            val origin = s.origin?.takeIf { it.location == null }?.let { place(SharedPlace(it.name, null), true) }
            val destination = s.destination?.takeIf { it.location == null }?.let { place(SharedPlace(it.name, null), true) }
            _state.update { it.copy(origin = origin ?: it.origin, destination = destination ?: it.destination) }
            if (_state.value.canSearch) plan()
        }
    }

    fun dismissImport() = _state.update { it.copy(import = null) }

    /** A name without a position gets one from the geocoder when online planning is on. */
    private suspend fun place(
        shared: SharedPlace,
        online: Boolean,
    ): PlaceUi {
        val location = shared.location
        val name = shared.name ?: location?.let { String.format(Locale.ROOT, "%.5f, %.5f", it.latitude, it.longitude) }.orEmpty()
        if (location != null || !online || name.isBlank()) return PlaceUi(name, location)
        val match = (container.planner.geocode(name, limit = 1) as? GeocodeResult.Success)?.matches?.firstOrNull()
        return PlaceUi(name, match?.location)
    }

    /** Offline: start from the nearest station to the origin, so only the line is left to pick. */
    private suspend fun suggestBoarding() {
        val from = _state.value.origin?.location ?: return
        if (_state.value.boarding != null) return
        val network = container.network()
        val nearest =
            network.stations.values
                .mapNotNull { station -> station.location?.let { station to it.distanceTo(from) } }
                .filter { (station, distance) -> distance <= SUGGEST_RADIUS_M && network.linesAtStation(station.diva).isNotEmpty() }
                .minByOrNull { it.second }
                ?.first ?: return
        _state.update {
            it.copy(boarding = PlaceUi(nearest.name, nearest.location, nearest.diva), boardingLines = network.linesAtStation(nearest.diva))
        }
    }

    private fun load(route: Route) {
        val first = route.journey.firstTransit
        _state.update { current ->
            val base =
                current.copy(
                    name = route.name,
                    origin = PlaceUi(route.origin.name, route.origin.location),
                    destination = PlaceUi(route.destination.name, route.destination.location),
                    reminders = route.reminders.offsets.toSet(),
                    bufferMinutes = route.buffer.toMinutes().toInt(),
                    accessible = route.accessible,
                    notifications = route.notificationsEnabled,
                    // An existing journey stays as it is unless the user searches again.
                    routingEnabled = route.journey.transitLegs.size > 1 || route.journey.legs.size > 2 || current.routingEnabled,
                    itineraries =
                        listOf(
                            Itinerary(
                                route.journey,
                                java.time.Instant.EPOCH,
                                java.time.Instant.EPOCH.plus(route.journey.plannedTotal),
                                false,
                            ),
                        ),
                    selected = 0,
                    boarding = first?.let { PlaceUi(it.from.name, it.from.location) },
                )
            val schedule = route.schedule
            when (val mode = schedule?.mode) {
                is ScheduleMode.Window -> {
                    base.copy(
                        timeMode = TimeMode.WINDOW,
                        days = schedule.days,
                        windowStart = mode.start,
                        windowEnd = mode.end,
                    )
                }

                is ScheduleMode.ArriveBy -> {
                    base.copy(timeMode = TimeMode.ARRIVE_BY, days = schedule.days, arriveBy = mode.arrival)
                }

                null -> {
                    base.copy(timeMode = TimeMode.NONE)
                }
            }
        }
        if (first?.boarding != null) {
            viewModelScope.launch {
                val network = container.network()
                val stop = network.stopPoints[first.boarding!!.stopId]
                val lines = stop?.diva?.let(network::linesAtStation).orEmpty()
                _state.update { s ->
                    s.copy(
                        boardingLines = lines,
                        boardingLine =
                            lines.firstOrNull {
                                it.target == first.boarding
                            },
                        boardingLocation = stop?.location,
                    )
                }
            }
        }
    }

    fun setName(value: String) = _state.update { it.copy(name = value) }

    /** Arrive-by and depart searches give different connections, so online results are dropped. */
    fun setTimeMode(mode: TimeMode) = _state.update { it.copy(timeMode = mode).withoutStaleItineraries() }

    fun setDays(days: Set<DayOfWeek>) = _state.update { it.copy(days = days) }

    fun setWindow(
        start: LocalTime? = null,
        end: LocalTime? = null,
    ) = _state.update { it.copy(windowStart = start ?: it.windowStart, windowEnd = end ?: it.windowEnd) }

    fun setArriveBy(time: LocalTime) = _state.update { it.copy(arriveBy = time) }

    fun toggleReminder(minutes: Int) =
        _state.update { s ->
            val next = if (minutes in s.reminders) s.reminders - minutes else s.reminders + minutes
            s.copy(reminders = next.ifEmpty { setOf(0) })
        }

    fun setBuffer(minutes: Int) = _state.update { it.copy(bufferMinutes = minutes.coerceIn(0, 10)) }

    fun setAccessible(on: Boolean) = _state.update { it.copy(accessible = on).withoutStaleItineraries() }

    private fun EditorUiState.withoutStaleItineraries(): EditorUiState =
        if (routingEnabled) {
            copy(
                itineraries = emptyList(),
                selected = null,
            )
        } else {
            this
        }

    fun setNotifications(on: Boolean) = _state.update { it.copy(notifications = on) }

    fun select(index: Int) = _state.update { it.copy(selected = index) }

    // --- place search --------------------------------------------------------------------------

    fun openSearch(target: PlaceTarget) = _state.update { it.copy(search = PlaceSearchUi(target)) }

    fun closeSearch() {
        searchJob?.cancel()
        _state.update { it.copy(search = null) }
    }

    fun query(text: String) {
        val search = _state.value.search ?: return
        _state.update { it.copy(search = search.copy(query = text)) }
        searchJob?.cancel()
        searchJob =
            viewModelScope.launch {
                delay(SEARCH_DEBOUNCE_MS)
                val stations = container.network().search.search(text, limit = 8)
                _state.update { s -> s.copy(search = s.search?.copy(stations = stations)) }
                if (_state.value.routingEnabled && search.target != PlaceTarget.BOARDING && text.trim().length >= 3) {
                    _state.update { s -> s.copy(search = s.search?.copy(searchingOnline = true)) }
                    val result = container.planner.geocode(text, limit = 6)
                    _state.update { s ->
                        s.copy(
                            search =
                                s.search?.copy(
                                    searchingOnline = false,
                                    addresses = (result as? GeocodeResult.Success)?.matches.orEmpty(),
                                ),
                        )
                    }
                }
            }
    }

    fun chooseStation(match: StationMatch) {
        val station = match.station
        choose(PlaceUi(station.name, station.location, station.diva))
    }

    fun chooseAddress(match: GeocodeMatch) = choose(PlaceUi(match.name, match.location))

    fun chooseCoordinates(
        label: String,
        point: GeoPoint,
    ) = choose(PlaceUi(label, point))

    fun locating(on: Boolean) = _state.update { s -> s.copy(search = s.search?.copy(locating = on, locationFailed = false)) }

    fun locationFailed() = _state.update { s -> s.copy(search = s.search?.copy(locating = false, locationFailed = true)) }

    private fun choose(place: PlaceUi) {
        val target = _state.value.search?.target ?: return
        searchJob?.cancel()
        _state.update { s ->
            when (target) {
                PlaceTarget.ORIGIN -> {
                    s.copy(origin = place, itineraries = emptyList(), selected = null)
                }

                PlaceTarget.DESTINATION -> {
                    s.copy(
                        destination = place,
                        itineraries = emptyList(),
                        selected = null,
                        name =
                            s.name.ifBlank {
                                place.name
                            },
                    )
                }

                PlaceTarget.BOARDING -> {
                    s.copy(boarding = place, boardingLines = emptyList(), boardingLine = null)
                }
            }.copy(search = null)
        }
        if (target == PlaceTarget.BOARDING && place.diva != null) {
            viewModelScope.launch {
                val network = container.network()
                val lines = network.linesAtStation(place.diva)
                _state.update { it.copy(boardingLines = lines) }
            }
        }
    }

    fun chooseLine(line: LineAtStop) {
        viewModelScope.launch {
            val network = container.network()
            val location = network.stopPoints[line.stopId]?.location
            _state.update { s ->
                s.copy(
                    boardingLine = line,
                    boardingLocation = location,
                    destination = s.destination ?: PlaceUi(line.towards, null),
                    name = s.name.ifBlank { "${line.line.name} ${line.towards}" },
                )
            }
        }
    }

    // --- planning ------------------------------------------------------------------------------

    fun plan() {
        val s = _state.value
        val from = s.origin?.location ?: return
        val to = s.destination?.location ?: return
        _state.update { it.copy(planning = true, planFailure = null, itineraries = emptyList(), selected = null) }
        viewModelScope.launch {
            val schedule = schedule(_state.value)
            val calendar = ScheduleCalendar()
            val now = container.clock.instant()
            val occurrence = schedule?.let { calendar.next(it, now, Duration.ofMinutes(30)) }
            val (time, arriveBy) =
                when {
                    occurrence?.arriveBy != null -> occurrence.arriveBy!! to true
                    occurrence != null -> occurrence.start to false
                    else -> now to false
                }
            val result =
                container.planner.plan(
                    PlanRequest(from, to, time, arriveBy = arriveBy, accessible = s.accessible, itineraries = 5),
                )
            _state.update {
                when (result) {
                    is PlanResult.Success -> {
                        it.copy(
                            planning = false,
                            itineraries = result.itineraries,
                            selected = if (result.itineraries.isEmpty()) null else 0,
                        )
                    }

                    is PlanResult.Failure -> {
                        it.copy(planning = false, planFailure = result.reason)
                    }
                }
            }
        }
    }

    fun save(onSaved: (Long) -> Unit) {
        val s = _state.value
        if (!s.canSave) return
        val journey = s.journey ?: return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val origin = Place(s.origin?.name ?: journey.origin.name, s.origin?.location)
            val route =
                Route(
                    id = s.routeId,
                    name = s.name.trim(),
                    origin = origin,
                    destination = Place(s.destination?.name ?: journey.destination.name, s.destination?.location),
                    journey = journey,
                    schedule = schedule(s),
                    reminders = ReminderPlan(s.reminders.toList()),
                    buffer = Duration.ofMinutes(s.bufferMinutes.toLong()),
                    accessible = s.accessible,
                    notificationsEnabled = s.notifications,
                )
            val id = container.routes.save(route)
            onSaved(id)
        }
    }

    private fun schedule(s: EditorUiState): Schedule? =
        when (s.timeMode) {
            TimeMode.NONE -> null
            TimeMode.WINDOW -> if (s.windowStart == s.windowEnd) null else Schedule(s.days, ScheduleMode.Window(s.windowStart, s.windowEnd))
            TimeMode.ARRIVE_BY -> Schedule(s.days, ScheduleMode.ArriveBy(s.arriveBy))
        }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        const val SUGGEST_RADIUS_M = 1_500.0
    }
}
