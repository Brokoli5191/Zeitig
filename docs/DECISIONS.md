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
