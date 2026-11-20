# Device checks

Everything here needs a real phone: Robolectric and CI cannot show what One UI does with a
foreground service, a Live Update or a widget. Reference device: Samsung Galaxy S23, Android 16 /
One UI 8. Run the guided script, then work through the manual items it prints.

```sh
./gradlew assembleDebug
scripts/device-checks.sh                      # first device from `adb devices`
scripts/device-checks.sh -s <serial> -a path/to/app.apk
```

The script writes a Markdown report (`device-checks-<date>.md` in the current directory). Attach it
to an issue when something is off. It does not change anything outside the app: permissions and
app-ops it toggles are restored at the end, and Doze is unforced.

> A debug APK cannot be installed over a release build (different signatures). Uninstall the
> release first or pass the release APK with `-a`. Uninstalling deletes the routes on the phone.

## What is checked

| # | Area | How | Expected |
|---|---|---|---|
| 1 | Device | `getprop` | Model, Android, One UI and SDK level end up in the report. |
| 2 | Install | `adb install -r` | Installs; `dumpsys package` lists `specialUse` and `location` service types. |
| 3 | Permissions | `dumpsys package`, `appops` | Location, notifications and "Alarms & reminders" match what was granted in the app. |
| 4 | Countdown start | Watch a route in the app | `dumpsys activity services` shows `MonitoringService` in the foreground with type `specialUse`. |
| 5 | Countdown notification | Look at the status bar and lock screen | The chronometer counts down to the leave time and shows "Stop" and "I'm leaving". |
| 6 | Live Update (Android 16) | Status bar chip, lock screen, *Settings › Notifications › Live updates* | The countdown appears as a chip or promoted notification when allowed; otherwise an ordinary ongoing notification. |
| 7 | Samsung Now Bar | Lock screen | Note whether the countdown appears in the Now Bar. Not documented for third-party apps; either result is fine, it goes into the README. |
| 8 | Reminders 5/3/1/now | Route with a window starting in 7 minutes | Four alerts on the "Leave reminders" channel, each within ±30 s, the last one "leave now". |
| 9 | Doze | `dumpsys deviceidle force-idle` while a reminder is due | The reminder still arrives on time; the countdown keeps running. |
| 10 | Screen off | Screen off for 5 minutes with a countdown running | No polling (OGD requests only right before a reminder; see logcat `OkHttp` if enabled). |
| 11 | Exact alarms off | `appops set … SCHEDULE_EXACT_ALARM deny`, window starts | A "time window has started" notification instead of an automatic start; tapping it starts the countdown. |
| 12 | Exact alarms on | `appops set … SCHEDULE_EXACT_ALARM allow`, window starts | The countdown starts by itself at the window start (`dumpsys alarm` shows the alarm). |
| 13 | Stop semantics | Tap "Stop", reopen the app within the same window | The countdown stays stopped; the next window starts it again. |
| 14 | Missed departure | Do not leave when "leave now" fires | About a minute later: "departure missed" with the next option. |
| 15 | Disruption | Route over a line with a current disruption | One notification per disruption, not again on the next morning. |
| 16 | Trip from the notification | "I'm leaving" in a reminder, walk to the stop | `TripService` runs with type `location`; within 40 m of the stop: "Walk recorded: N min". |
| 17 | Trip without location | Revoke location, tap "I'm leaving" | The app opens and asks for location first. |
| 18 | Trip manual / timeout | "Arrived" button; or wait 30 min | Manual arrival is recorded; a timeout records nothing and says so. |
| 19 | Widget | Add the Zeitig widget | Shows the next window; during a countdown it counts down by itself. Light/dark follows the system. |
| 20 | Tile | `cmd statusbar add-tile`, tap it with the app swiped away | Starts or stops the countdown. If Android refuses the background start, the app opens and starts it; note which happened. |
| 21 | Map | Open a route with the map enabled | basemap.at tiles render, pan/zoom/rotate work, no jank; the schematic map shows while loading and offline. |
| 22 | Language | System app-language settings, or `cmd locale set-app-locales` | The app switches between German (Austria) and English without restart artefacts; dates use de-AT. |
| 23 | "My location once" | Route editor | One fix, then location stops (no location indicator afterwards). |
| 24 | Reboot | Reboot with a scheduled route | `dumpsys alarm` lists the window alarm again after boot. |
| 25 | Battery | *Settings › Battery › App power management* | Zeitig is not "Deep sleeping"; note the default Samsung puts it in. |

## Notes for the README

Results of items 6, 7, 20 and 25 on the S23 go into the README's "Known limitations" section.
