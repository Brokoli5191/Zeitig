# Decisions

One entry per non-obvious decision: context, choice, rejected alternatives. Newest at the bottom.
Versions were verified against Maven Central, Google Maven and the Gradle plugin portal on the date
given; re-verify before relying on them.

## D1 – Toolchain versions (verified 2026-11-20)

- **Context:** The brief names Compose BOM 2026.11.01 and asks to verify everything.
- **Choice:** AGP 9.5.2 (latest stable; 9.6 is alpha), Gradle 9.9.2, Kotlin 2.4.21, KSP 2.3.14,
  Compose BOM 2026.11.01 (still the newest BOM: Compose 1.13.0, Material 3 1.5.0), JDK 21 (Temurin
  in CI, matching `release.yml`). The wrapper pins the distribution SHA-256.
- **Rejected:** AGP 9.6 alphas and Kotlin 2.5.0-RC – no pre-releases in a project that ships.

## D2 – compileSdk 37.1, targetSdk 37

- **Context:** Compose BOM 2026.11.01 requires `compileSdk 37.1`; Play's target floor since
  31 Aug 2026 is 36.
- **Choice:** `compileSdk { version = release(37) { minorApiLevel = 1 } }`, `targetSdk = 37`,
  `minSdk = 28`. AGP 9.5.2 configures and builds against platform `android-37.1`.
- **Rejected:** targetSdk 36 – only needed if the stable AGP did not support 37.

## D3 – AGP 9 built-in Kotlin

- **Context:** AGP 9 compiles Kotlin itself; the `org.jetbrains.kotlin.android` plugin is no longer
  applied to Android modules.
- **Choice:** Android modules apply only `com.android.*` (+ Compose compiler, serialization, KSP
  plugins where needed) and configure Kotlin through the `kotlin { compilerOptions { } }` block.
  `:core` is a plain `org.jetbrains.kotlin.jvm` module with `com.android.lint` for Android Lint.
- **Rejected:** Keeping `kotlin-android` with `android.builtInKotlin=false` – a deprecated opt-out.

## D4 – Module split

- **Context:** Brief requires `:app`, `:core`, `:data`; the engine must be pure Kotlin.
- **Choice:** `:core` holds the domain model, leave-time engine, walk-time model, time windows,
  stop-search normalisation/scoring and the `DepartureProvider` interface. `:data` (Android library)
  holds everything with I/O: Wiener Linien client and DTOs, rate limiter, CSV snapshot and refresh,
  Room, DataStore. `:app` holds UI, notifications, services, widget, tile and the `AppContainer`.
- **Rejected:** A separate `:provider-wienerlinien` module – one provider in v1 does not justify it;
  the interface keeps the door open.

## D5 – Licence: GPL-3.0-or-later

- **Context:** The brief makes GPL-3.0-or-later the default if no other licence is chosen.
- **Choice:** GPL-3.0-or-later (full text in `LICENSE`). Compatible with every dependency used
  (Apache-2.0, MIT).
- **Rejected:** Apache-2.0 / MIT – would allow closed forks of a community transit tool.

## D6 – Branches, PRs and merging

- **Context:** One branch + PR per milestone; the cloud proxy rejects tag pushes and branch
  deletions; GitHub Actions is the source of truth.
- **Choice:** A one-commit bootstrap on `main` (licence and git hygiene) so PRs have a base; then
  `milestone/<n>-<topic>` branches, each with one PR labelled for release notes. A PR is merged
  with a merge commit once CI is green, keeping the Conventional Commits of the branch intact.
- **Rejected:** Stacked PRs without merging – every later milestone would carry all earlier diffs.

## D7 – Material 3 Expressive: what is still experimental (checked in 1.5.0 sources)

- **Context:** Experimental APIs must be confined to `ui/expressive/`.
- **Finding:** Stable in material3 1.5.0: `MaterialExpressiveTheme`, `MotionScheme.expressive()`,
  `Horizontal/VerticalFloatingToolbar`, `ButtonGroup`, `ToggleButton`, `SplitButtonLayout`,
  `Linear/CircularWavyProgressIndicator`, `Medium/LargeFlexibleTopAppBar`, `SegmentedListItem`,
  the new `SearchBar`, emphasized typography. Still experimental: `LoadingIndicator`,
  `ContainedLoadingIndicator`, `MaterialShapes`, `Morph.toPath` (`@ExperimentalMaterial3ExpressiveApi`)
  and `AppBarWithSearch` (`@ExperimentalMaterial3Api`). Compose 1.13 foundation's `PolygonShape`
  and `MorphPolygonShape` and animation's `CapturedAnimatedVisibility` are stable.
- **Choice:** Wrap the experimental ones in `ui/expressive/`; use the stable ones directly.

## D8 – Data source licence and attribution

- **Context:** Wiener Linien OGD (real-time API and reference CSVs).
- **Finding:** wienerlinien.at/open-data: "Alle Daten der Wiener Linien werden unter der
  CCBY-Lizenz veröffentlicht" – use allowed incl. commercially with attribution; no API key. The City
  of Vienna publishes OGD under CC BY 4.0 with the credit line "Datenquelle: Stadt Wien –
  data.wien.gv.at". The data.gv.at dataset page itself is rendered client-side and could not be
  fetched from the build container, so the licence version (4.0) follows the city's standard.
- **Choice:** Show "Datenquelle: Stadt Wien – data.wien.gv.at (CC BY 4.0), Echtzeitdaten: Wiener
  Linien" in About and README. No operator logos anywhere (trademarks).

## D9 – Test frameworks

- **Choice:** `:core` uses JUnit Jupiter 6 + `kotlin.test` assertions (parameterised DST tests);
  Android modules use JUnit 4 because Robolectric and Roborazzi run on the JUnit 4 runner.
  Coverage for `:core` via Kover with a 90 % line rule (`:core:koverVerify` in CI).
- **Rejected:** Mixing Truth/AssertK – `kotlin.test` is enough and adds no dependency.

## D10 – Screenshot setup

- **Choice:** Roborazzi with Robolectric native graphics, device qualifier `w411dp-h891dp-xhdpi`
  (Galaxy S23 logical size, rendered at 2×) to keep PNGs small but sharp. `outputDir` is
  `docs/screenshots/`, which the README embeds directly.

## D11 – Scope change: journey planning and map (user request, 2026-11-20)

- **Context:** After milestone 1 the brief was extended: find the fastest route with an API, show it
  on a map, plan routes with reminders at 5/3/1/0 minutes, choose on which weekdays a route is
  taken, plus arrival-time planning with automatic re-planning, disruption/barrier-free warnings,
  ETA sharing and statistics. This overrides the v1 non-goals "no journey planner" and "no map SDK".
- **Choice:**
  - Routing via **Transitous** (MOTIS 2 API, `api.transitous.org`): free, no key, open source and
    open data; verified with a Vienna query (U1 legs, transfers, leg geometry). It has no Wiener
    Linien real-time data, so the first transit leg is matched to a WL stop point and the WL monitor
    supplies the real-time departure that drives `leaveAt`. Volunteer-run and best effort,
    attribution required; its logs keep IP, time and request URL (which contains origin and
    destination), so online routing is **opt-in** with a privacy notice. Simple routes (stop +
    line + direction) keep working fully offline without it.
  - Map: **MapLibre Native** (Compose wrapper `org.maplibre.compose`) with **basemap.at** vector
    tiles (official Austrian basemap, hosted by Stadt Wien). No Google Play Services.
- **Rejected:** VAO "Start" (free tier needs a contract and an API key, which cannot stay secret in
  an open-source APK); Wiener Linien's own routing service (listed on their open-data page, but no
  current endpoint documentation found); ÖBB HAFAS `mgate.exe` (undocumented, grey zone);
  on-device RAPTOR over GTFS (large preprocessing effort for v1); osmdroid (raster only).
- **Not taken from the offered extras:** public-holiday and vacation pause.

## D12 – Bundled reference snapshot includes the line patterns

- **Context:** Routing results use Austrian global stop IDs (`at:49:1320:0:2`), not Wiener Linien
  stop IDs, and the WL monitor needs the latter.
- **Choice:** Bundle all five CSVs, including `fahrwegverlaeufe` (1.5 MB raw, ≈ 330 KB compressed).
  The stop sequences let the app list lines/directions per stop offline and match a planned
  boarding (line, position, next stop) to the right WL stop point and direction.
- **Rejected:** Matching by name only (ambiguous across directions), a live `diva` monitor query
  per match (costs the rate-limited budget and fails at night).

## D13 – One rate limiter for every request to wienerlinien.at

- **Choice:** A single suspending `RateLimiter` (≥ 15 s between request starts, FIFO) guards the
  monitor, `trafficInfoList` and the CSV refresh. Callers queue instead of failing; batching several
  stops into one monitor request keeps the budget usable.

## D14 – Runtime refresh of the reference data

- **Choice:** At most weekly, triggered from the foreground: fetch the version file; when it differs,
  download into a staging directory, validate (parses, plausible counts), then swap in atomically.
  A failed refresh leaves the active snapshot untouched and is retried next time. The bundled
  snapshot is never modified, and gaps in a refreshed snapshot are filled from it.
- **Finding:** Upstream snapshots flicker – between two downloads on 2026-11-20 individual stop
  points lost (and others regained) DIVA and coordinates. Both the runtime loader and the monthly
  workflow therefore fill such gaps from the previous data (`scripts/fill-ogd-gaps.py`).

## D15 – Search normalisation

- **Choice:** Fold case, diacritics, `ß`→`ss` and the digraphs `ae/oe/ue`, expand `Str.`, `Pl.`,
  `G.`, `St.`, `Hbf`; apply the same transformation to index and query. Tokens match exactly, by
  prefix, or (≥ 4 letters) within an optimal-string-alignment distance of 1–2. Ties break by the
  number of stop points of a station (a proxy for importance).

## D16 – Monthly snapshot workflow

- **Choice:** `refresh-stops.yml` downloads the CSVs, fills gaps, ignores version-only changes,
  runs `:data:testDebugUnitTest` itself and opens a PR with `GITHUB_TOKEN`.
- **Caveat:** PRs created with `GITHUB_TOKEN` do not trigger CI, and creating them requires the
  repository setting "Allow GitHub Actions to create and approve pull requests".

## D17 – Journeys: the first boarding drives the leave time

- **Context:** Routes now come from a journey planner (several legs, transfers), but real-time data
  exists only per Wiener Linien stop point.
- **Choice:** A `Journey` is a list of walk and transit legs. The first transit leg carries a
  `LineTarget` (matched WL stop point, line, direction); its live departures decide `leaveAt`.
  Everything after the boarding is the planned "onward" time and gives the arrival estimate.
  Offline "simple" routes are a two-leg journey (walk + one line).
- **Rejected:** Re-planning the whole journey online on every refresh (rate limits of a volunteer
  service, no real-time there anyway).

## D18 – Leave-time engine details

- **Grace:** `leaveAt ≥ now − min(60 s, buffer)` counts as catchable. Without it, the "Leave now!"
  state would flip to the next departure a second after the alarm. The grace never exceeds the
  buffer the user chose, so with buffer 0 the rule is exactly `leaveAt ≥ now` as specified.
- **Arrive-by:** choose the *latest* catchable departure whose estimated arrival meets the deadline
  (leave as late as possible); if none does, the earliest one plus how late it will be.
- **Too late vs. no service:** departures within 60 minutes that are all unreachable give
  `TooLate`; no departures at all give `NoService`. Neither is an empty screen.
- **Identity of a run:** stop + line + *planned* time, so real-time drift does not look like a new
  vehicle; reminders and missed-departure detection key on it.

## D19 – Learning the walking time

- **Choice:** EWMA (α = 0.3) seeded with the first estimate and replayed over the accepted trips
  (oldest first), at most 50 trips. A trip > 2 × median is rejected, but only once three trips
  exist – a first estimate that is far off must not lock out the real values. Trips under 20 s are
  implausible (e.g. "I'm there" tapped immediately).
- **Rejected:** A running EWMA without stored samples – could neither drop old trips nor recompute
  after a reset or a settings change.

## D20 – Reminders 5/3/1/now

- **Choice:** Configurable minutes before `leaveAt` (default 5, 3, 1, 0). Each fires once per vehicle
  run; a different run resets them. When several are overdue at once only the most urgent is
  delivered, and one more than 90 s late is skipped silently – except "leave now", which always
  arrives.

## D21 – Transitous client

- **Choice:** `v6/plan` and `v1/geocode` of the MOTIS 2 API (as in the upstream `openapi.yaml`;
  v6 plan verified on api.transitous.org on 2026-11-20). Parameters used: `fromPlace`/`toPlace`
  as coordinates, `time` (ISO instant), `arriveBy`, `numItineraries`, `language`, and
  `pedestrianProfile=WHEELCHAIR` when barrier-free mode is on. A separate 1-request-per-second
  limiter protects the volunteer service from typing-driven geocoding bursts.
- **Mapping:** street modes become walk legs, all other modes transit legs with a transport type;
  leg geometry is decoded from the encoded polyline at the given precision (6). Stop names drop the
  "Wien " prefix. After planning, every leg is matched against the WL network (`TransitNetwork.attach`);
  legs of other operators (ÖBB, Badner Bahn) remain unmatched and use the timetable.
- **Geocoding:** results are biased to the search centre (Vienna by default) and those further than
  80 km away are dropped, because the global index also returns e.g. Munich's "Karlsplatz (Stachus)".
  Address nodes that differ by a few metres collapse into one suggestion.
- **Attribution:** Transitous sources page and © OpenStreetMap contributors (About screen, README).

## D22 – Map: MapLibre Native directly, basemap.at style rewritten

- **Choice:** MapLibre Android SDK 13.7.1 (BSD) in an `AndroidView`, not the Compose wrapper
  (`org.maplibre.compose` 0.20 is pre-1.0). The date-versioned `-globe` artifacts on Maven Central
  look like an experimental channel and are not used. basemap.at's style uses relative sprite/glyph
  URLs and an Esri VectorTileServer root instead of TileJSON; `BasemapStyle.rewrite` makes them
  absolute and sets an explicit tile template. The rewritten style is cached for 30 days.
- **Fallback:** a schematic Canvas drawing of the legs (also used in screenshot tests, where
  Robolectric cannot run OpenGL) when tiles are off, while the style loads, or offline.
- **Size:** MapLibre's native libraries dominate the APK. x86 (32-bit) is dropped, and
  `jniLibs.useLegacyPackaging = true` compresses them inside the APK – downloads matter more than
  install size for an app distributed as a single APK.
- **To verify on the device:** rendering, gestures and memory on the Galaxy S23.

## D23 – UI architecture

- **Navigation 3** (1.2.2) with the Material 3 adaptive list-detail scene strategy (experimental,
  wrapped in `ui/expressive/AdaptivePanes.kt`): one pane on phones, routes and detail side by side
  on wide windows. Predictive back comes with it. Shared bounds connect a route card with its
  detail hero; they degrade to nothing outside navigation and with motion off.
- **State:** every screen is a stateless composable fed by one `StateFlow` from its ViewModel;
  `RouteStatuses` polls the shared live cache only while a screen collects (`WhileSubscribed`), so
  nothing polls when the app is in the background.
- **No live re-sorting:** the home list keeps the user's order, so it never "dances".
- **Material 3 1.5 deprecations:** the old `ListItem(headlineContent=…)` and value-based `Slider`
  overloads are deprecated; small wrappers (`RowItem`, `ValueSlider`) use the new APIs.
- **Icons:** Material Symbols Rounded (Apache-2.0) converted to vector drawables by a script;
  only the icons in use are kept.

## D24 – Appearance and motion

- **Colours:** wallpaper colours (Android 12+) by default. With a forced contrast level, the
  wallpaper's primary colour seeds MaterialKolor at that contrast; without dynamic colour, one of
  eight curated seeds with a scheme style (Tonal spot, Vibrant, Expressive, Fidelity). Pure black
  overrides background and surface containers in the dark scheme.
- **Contrast "follow system":** `UiModeManager.getContrast()` with a change listener (Android 14+).
- **Motion:** "follow system" maps an animator duration scale of 0 to reduced motion. Full uses
  `MotionScheme.expressive()`, reduced `MotionScheme.standard()` and no digit roll, off a scheme of
  snap specs. Press feedback and seed-selection morphs run in the draw/layer phase.
- **Language:** `LocaleManager` on Android 13+ (listed in the system's app-language settings);
  Android 9–12 wrap the activity context. App-bundle language splits are disabled so switching
  never misses resources.

## D25 – Screenshot variants

- **Choice:** every main screen in light/dark × English/German, plus high contrast, pure black and
  200 % font scale (German, the longer language). Seed colours instead of wallpaper colours and
  motion off keep the images deterministic. Robolectric 4.18.1 renders at the target SDK (37).

## D26 – Live countdown: one specialUse foreground service, only when needed

- **Context:** Reminders at 5/3/1/0 minutes must arrive on time with the screen off. Android 12+
  restricts starting foreground services from the background, Doze defers alarms and network, and
  exact alarms need a permission that new installs on Android 14+ do not have by default.
- **Choice:** `MonitoringService`, a foreground service of type `specialUse` (with the subtype
  property for review), runs only while a route's window is active or the user watches a route,
  and stops by itself after 3 hours. The countdown in its notification is a system chronometer
  counting down to `leaveAt`, so it is exact to the second without per-second updates. On
  Android 16 the notification asks to be promoted to a Live Update (`setRequestPromotedOngoing`,
  `ProgressStyle`, `POST_PROMOTED_NOTIFICATIONS`); where the user or the device refuses, it stays
  an ordinary ongoing notification.
- **Network:** every 30 s while the screen is on; with the screen off only right before a reminder.
  All requests still pass the global 15 s limiter and the shared live cache.
- **Rejected:** WorkManager (15-minute minimum, not exact); `USE_EXACT_ALARM` (reserved for alarm
  clock and calendar apps); `shortService` (3-minute limit); `dataSync` (daily time limit on
  Android 15); a service that runs all day.

## D27 – Waking up with and without exact alarms

- **Window start:** one alarm per route at its next window. With "Alarms & reminders" granted it
  is exact and starts the service directly (exact allow-while-idle alarms may start foreground
  services). Without it, an inexact `setWindow` alarm (10-minute window, the minimum since
  Android 12) posts a "time window has started – start the countdown" notification, and opening
  the app during a window starts the countdown too. Alarms are re-armed on every route change,
  after boot, app update, time or time-zone change and when the permission changes.
- **Inside a running service:** the next reminder gets a backup alarm (exact, or inexact 9 minutes
  early) and, in the last 10 minutes before it, a partial wake lock, so the coroutine timer is not
  stretched by deep sleep.
- **Samsung Now Bar:** whether One UI shows third-party Live Updates there is not documented; it is
  a device check, not a promise.

## D28 – A stop means "this window", not "forever"

- **Choice:** "Stop" (notification action or swipe, tile, route toggle) records the time; a window
  that began before it stays stopped, the next occurrence runs normally. Turning off one route
  mutes only that route for its current window. "I'm leaving" mutes the route as well, so no
  reminder for the next tram follows the user to the stop. Manually watched routes expire after
  3 hours, so a process death never revives yesterday's watch.
- **Disruptions:** announced once per disruption id; an id is forgotten after a week without being
  seen, so a long lift outage is not announced every morning.

## D29 – Measuring the walk

- **Choice:** `TripService`, a foreground service of type `location`, started from the app or the
  "I'm leaving" notification action – both count as while-in-use starts. Without location
  permission the action opens the app, which asks first. Location comes from `LocationManager`
  (fused provider on Android 12+, else GPS and network), every 5 s or 5 m. Arrival is a fix within
  40 m of the boarding stop with an accuracy of at most 60 m, or the "Arrived" button; after
  30 minutes the measurement ends unrecorded. Only the duration is stored, never a position.

## D30 – Widget and tile

- **Widget:** Glance, refreshed by the service after each evaluation. The countdown is a
  `RemoteViews` chronometer counting down by itself; outside a countdown the widget shows the next
  window. Tapping opens the app.
- **Tile:** shows the most urgent route and starts or stops the countdown. Whether a tile tap may
  start a foreground service from the background is not documented; if the system refuses, the
  tile opens the app, which starts it while visible.
