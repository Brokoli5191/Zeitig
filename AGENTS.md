# Zeitig – agent guide

Android app that answers "when do I have to leave?" for Vienna public transport: Wiener Linien
real-time departures minus the user's learned walking time minus a buffer.

## Rule zero: verify before trusting versions

Never take a version number, API flag or platform rule from memory. Check Maven Central, Google
Maven, the Gradle plugin portal, release notes or the official docs first, and record non-obvious
choices in `docs/DECISIONS.md` (context, choice, rejected alternatives).

## Build commands

```sh
./gradlew spotlessApply                      # format (ktlint via Spotless)
./gradlew spotlessCheck lint testDebugUnitTest verifyRoborazziDebug :core:koverVerify assembleDebug   # = CI
./gradlew recordRoborazziDebug               # re-record reference screenshots into docs/screenshots/
./gradlew :core:test :core:koverHtmlReport   # engine tests + coverage report (gate: 90 % lines)
./gradlew :app:assembleRelease -PappVersionName=1.2.0 -PappVersionCode=1020099   # needs SIGNING_* env
```

JDK 21, Gradle wrapper only. Long tasks: run in the background and poll. In the cloud container,
Maven Central rate-limits bursts (HTTP 429); use `--no-parallel` and
`-Dorg.gradle.internal.repository.max.tentatives=12 -Dorg.gradle.internal.repository.initial.backoff=1500`.

## Module map

| Module | Kind | Content |
|---|---|---|
| `:core` | Kotlin/JVM, no Android | Domain model, leave-time engine, walk-time model, schedules, stop search, transit network index, `DepartureProvider` / routing interfaces |
| `:data` | Android library | Wiener Linien OGD client + parser, global rate limiter, reference-data snapshot/refresh, Transitous routing client, Room, DataStore |
| `:app` | Android app | Compose UI, theme, notifications, services, widget, tile, `AppContainer` (manual DI) |

Package root: `app.brokoli5191.zeitig` (permanent – never change the application ID).

## Conventions

- Kotlin + Jetpack Compose + Material 3 Expressive, single activity, ViewModel + `StateFlow` (UDF).
- Manual DI via `AppContainer`; no Hilt/Koin. No Retrofit; OkHttp + kotlinx.serialization.
- No Google Play Services, no trackers, no analytics. Network access only for: the Wiener Linien
  provider and CSV refresh, opt-in Transitous routing/geocoding, and map tiles (basemap.at).
- All OGD requests go through the single global rate limiter (≥ 15 s apart).
- Inject `java.time.Clock` everywhere; all API times are parsed with their offset and handled in
  `Europe/Vienna`.
- Experimental Material 3 APIs only inside `app/.../ui/expressive/`.
- Screens are stateless (`state` + callbacks) so Roborazzi can render them without ViewModels.
- Strings: English in `values/`, German with Austrian usage in `values-de/`.
- Conventional Commits, small. One branch + PR per milestone, labelled `feature`/`fix`/`maintenance`.
- Never commit secrets; signing values exist only as GitHub Actions secrets.
- Things that need a real phone go into `docs/DEVICE-CHECKS.md` and `scripts/device-checks.sh`.
