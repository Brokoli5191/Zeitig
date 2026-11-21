# Contributing to Zeitig

Thanks for helping Vienna leave on time.

## Ground rules

- No accounts, no backend, no analytics, no ads, no Google Play Services. Pull requests that add
  any of these will be declined, however small.
- New dependencies need a reason in `docs/DECISIONS.md` (licence, size, maintenance, network use).
- Network access exists only for the Wiener Linien provider and the reference-data refresh, the
  opt-in Transitous routing and address search, basemap.at map tiles while the map is on, and
  expanding a Google Maps short link the user shares into the app.

## Workflow

1. Open an issue first for anything bigger than a typo.
2. Branch from `main`, keep commits small and use [Conventional Commits](https://www.conventionalcommits.org/)
   (`feat:`, `fix:`, `perf:`, `docs:`, `build:`, `ci:`, `test:`, `refactor:`, `chore:`).
3. Run the same task set as CI before pushing:

   ```sh
   ./gradlew spotlessApply
   ./gradlew spotlessCheck lint testDebugUnitTest verifyRoborazziDebug :core:koverVerify assembleDebug
   ```

4. If you changed UI, re-record the reference screenshots with `./gradlew recordRoborazziDebug` and
   commit the images in `docs/screenshots/` together with the code.
5. Behaviour only a real phone can show (notifications, alarms, Doze, location, widget, tile) gets
   an item in `docs/DEVICE-CHECKS.md` and `scripts/device-checks.sh`.
6. Label the pull request with exactly one of `feature`, `fix` or `maintenance`; release notes are
   generated from these labels.

## Code style

- Kotlin official style via ktlint (Spotless). Compose functions are PascalCase.
- Unidirectional data flow: `ViewModel` exposes one `StateFlow<UiState>`; screens are stateless
  composables that take state and callbacks, so they can be screenshot-tested without a ViewModel.
- Experimental Material 3 APIs are only used inside `app/src/main/kotlin/.../ui/expressive/`.
- Every animation must communicate a state change, a spatial relationship or direct feedback.

## Translations

English lives in `values/`, German (Austrian usage: Jänner, Bim, Stiege) in `values-de/`.
