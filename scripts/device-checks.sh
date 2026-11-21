#!/usr/bin/env bash
# Guided checks on a real phone (reference: Galaxy S23, Android 16 / One UI 8). Collects what adb can
# see, walks through what only a person can see, and writes a Markdown report to the current
# directory. See docs/DEVICE-CHECKS.md for the meaning of each item.
#
#   scripts/device-checks.sh [-s serial] [-a app.apk] [--no-install]
#
# Exact-alarm app-op and location permissions it toggles are restored on exit; Doze is unforced.
set -euo pipefail

pkg="app.brokoli5191.zeitig"
apk="app/build/outputs/apk/debug/app-debug.apk"
serial=""
install=1

while [[ $# -gt 0 ]]; do
  case "$1" in
    -s) serial="$2"; shift 2 ;;
    -a) apk="$2"; shift 2 ;;
    --no-install) install=0; shift ;;
    -h|--help) sed -n '2,9p' "$0"; exit 0 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done

command -v adb >/dev/null || { echo "adb not found (Android SDK platform-tools)" >&2; exit 1; }
if [[ -z "$serial" ]]; then
  serial="$(adb devices | awk 'NR > 1 && $2 == "device" { print $1; exit }')"
fi
[[ -n "$serial" ]] || { echo "no device in 'adb devices' (USB debugging on?)" >&2; exit 1; }

a() { adb -s "$serial" "$@"; }
# Device commands never abort the run: a missing command or an empty result is part of the report.
sh_() { { a shell "$@" 2>/dev/null || true; } | tr -d '\r'; }
pick() { grep -E "$@" || true; }
first() { sed -n "1,${1}p"; }

report="device-checks-$(date +%Y-%m-%d-%H%M).md"
log() { printf '%s\n' "$*" | tee -a "$report"; }
block() { { echo '```'; cat; echo '```'; } | tee -a "$report" >/dev/null; }
ask() {
  # ask <id> <question>: the answer goes into the report.
  local answer
  printf '\n[%s] %s\n  > ' "$1" "$2"
  read -r answer || answer=""
  log "- **$1** $2 — ${answer:-no answer}"
}
pause() { printf '\n%s\n  (Enter to continue) ' "$1"; read -r _ || true; }

# Restore what we change, whatever happens.
exact_before="$(sh_ appops get "$pkg" SCHEDULE_EXACT_ALARM | awk -F': ' '{print $2}' | cut -d';' -f1)"
granted() { sh_ dumpsys package "$pkg" | grep -F "$1: granted=true" >/dev/null; } # no -q: an early exit would trip pipefail
fine_before=0; coarse_before=0
granted android.permission.ACCESS_FINE_LOCATION && fine_before=1
granted android.permission.ACCESS_COARSE_LOCATION && coarse_before=1
cleanup() {
  sh_ dumpsys deviceidle unforce >/dev/null
  if [[ -n "$exact_before" ]]; then sh_ appops set "$pkg" SCHEDULE_EXACT_ALARM "$exact_before" >/dev/null; fi
  if [[ "$fine_before" == 1 ]]; then sh_ pm grant "$pkg" android.permission.ACCESS_FINE_LOCATION; fi
  if [[ "$coarse_before" == 1 ]]; then sh_ pm grant "$pkg" android.permission.ACCESS_COARSE_LOCATION; fi
}
trap cleanup EXIT

: >"$report"
log "# Zeitig device checks – $(date '+%Y-%m-%d %H:%M')"
log ""
log "## 1 Device"
{
  echo "model:    $(sh_ getprop ro.product.model)"
  echo "android:  $(sh_ getprop ro.build.version.release) (SDK $(sh_ getprop ro.build.version.sdk))"
  echo "one ui:   $(sh_ getprop ro.build.version.oneui)"
  echo "build:    $(sh_ getprop ro.build.display.id)"
} | block

log ""
log "## 2 Install"
if [[ "$install" == 1 ]]; then
  [[ -f "$apk" ]] || { echo "APK not found: $apk (run ./gradlew assembleDebug or pass -a)" >&2; exit 1; }
  a install -r "$apk" | block
fi
sh_ dumpsys package "$pkg" | pick 'versionName|versionCode|targetSdk|minSdk' | sed 's/^ *//' | block
sh_ dumpsys package "$pkg" | pick -i 'foregroundServiceType|MonitoringService|TripService' | sed 's/^ *//' | block

pause "Open Zeitig, create a route (or keep yours), allow notifications, location and – if you want – Alarms & reminders."

log ""
log "## 3 Permissions"
sh_ dumpsys package "$pkg" | sed -n '/runtime permissions:/,/^ *[a-z]* permissions:$/p' | pick 'LOCATION|NOTIFICATIONS' | sed 's/^ *//' | block
{
  echo "SCHEDULE_EXACT_ALARM: $(sh_ appops get "$pkg" SCHEDULE_EXACT_ALARM)"
  echo "standby bucket:       $(sh_ am get-standby-bucket "$pkg")"
  echo "app locales:          $(sh_ cmd locale get-app-locales "$pkg")"
} | block

log ""
log "## 4–7 Countdown and Live Update"
pause "Open a route and tap the bell (watch). Wait until the countdown notification appears."
sh_ dumpsys activity services "$pkg" | pick 'ServiceRecord|isForeground|foregroundServiceType|types=' | sed 's/^ *//' | block
sh_ dumpsys notification --noredact | pick -A12 "pkg=$pkg" | pick 'NotificationRecord|channel=|flags=|PROMOTED|chronometer|when=' | sed 's/^ *//' | first 40 | block
ask "5" "Does the notification count down to the leave time and show Stop and I'm leaving? (y/n, notes)"
ask "6" "Live Update: status-bar chip / promoted on the lock screen? Settings › Notifications › Live updates state?"
ask "7" "Samsung Now Bar: does the countdown appear there?"

log ""
log "## 8–10 Reminders, Doze, screen off"
pause "Edit a route so its window starts in ~7 minutes (reminders 5/3/1/now). Then lock the phone."
sh_ dumpsys alarm | pick -B1 -A4 "$pkg" | sed 's/^ *//' | first 40 | block
sh_ dumpsys deviceidle force-idle | block
log "Doze forced at $(date +%H:%M:%S)."
ask "8" "Did all four reminders arrive on time (±30 s)? Note the times you saw."
sh_ dumpsys deviceidle unforce | block
ask "10" "With the screen off for 5 min, did the phone stay quiet (no extra alerts, no visible polling)?"

log ""
log "## 11–13 Exact alarms and stop semantics"
sh_ appops set "$pkg" SCHEDULE_EXACT_ALARM deny
pause "Exact alarms are now DENIED. Let a route window start (edit one to start in 2 min) and wait for it."
ask "11" "Did a 'time window has started' notification appear, and did tapping it start the countdown?"
sh_ appops set "$pkg" SCHEDULE_EXACT_ALARM allow
pause "Exact alarms are now ALLOWED. Edit the route so its window starts in 2 min again and wait."
sh_ dumpsys alarm | pick -B1 -A4 "$pkg" | sed 's/^ *//' | first 20 | block
ask "12" "Did the countdown start by itself at the window start?"
ask "13" "Tap Stop, reopen the app within the window: does the countdown stay stopped?"

log ""
log "## 14–15 Missed departure, disruptions"
ask "14" "Ignore a 'leave now' alert: about a minute later, 'departure missed' with the next option?"
ask "15" "A route over a disrupted line: one disruption notification, not repeated next time?"

log ""
log "## 16–18 Walk measurement"
pause "Tap 'I'm leaving' in a reminder and walk to the stop (or start it from the route screen)."
sh_ dumpsys activity services "$pkg" | pick 'TripService|isForeground|types=' | sed 's/^ *//' | block
ask "16" "Within ~40 m of the stop: 'Walk recorded: N min'? N plausible?"
# Revoking a runtime permission kills the app process; that is expected.
sh_ pm revoke "$pkg" android.permission.ACCESS_FINE_LOCATION
sh_ pm revoke "$pkg" android.permission.ACCESS_COARSE_LOCATION
ask "17" "Location revoked now: tap 'I'm leaving' in a reminder – does the app open and ask for location?"
ask "18" "'Arrived' button records the walk; a 30-minute timeout records nothing and says so?"

log ""
log "## 19–20 Widget and tile"
ask "19" "Widget: next window outside a countdown, live countdown during one, light/dark correct?"
sh_ cmd statusbar add-tile "$pkg/.tile.ZeitigTileService" | block
pause "Swipe Zeitig away in Recents, then tap the Zeitig tile in Quick Settings."
sh_ dumpsys activity services "$pkg" | pick 'MonitoringService|isForeground' | sed 's/^ *//' | block
ask "20" "Tile tap with the app closed: did the countdown start directly, or did the app open first?"

log ""
log "## 21–25 Map, language, location once, reboot, battery"
ask "21" "Map: tiles render, pan/zoom/rotate smooth, schematic map while loading/offline?"
sh_ cmd locale set-app-locales "$pkg" --locales de-AT | block
ask "22a" "App language set to de-AT: German (Austria) texts, dates like 'Mo., 23. Nov.'?"
sh_ cmd locale set-app-locales "$pkg" --locales en | block
ask "22b" "App language set to en: English texts?"
sh_ cmd locale set-app-locales "$pkg" --locales "" | block # back to the system language
ask "23" "Route editor 'my location once': one fix, then the location indicator disappears?"
ask "24" "After a reboot: 'adb shell dumpsys alarm | grep $pkg' lists the window alarm again?"
ask "25" "Settings › Battery › App power management: is Zeitig 'Deep sleeping' or restricted by default?"

log ""
log "## 26 Cold start"
printf '\nRun the start-up benchmark now? It installs a debug-signed benchmarkRelease build (fails over a\nrelease-signed install) and takes about 10 minutes. (y/N) '
read -r run_bench || run_bench=""
if [[ "$run_bench" == [yY]* ]]; then
  ANDROID_SERIAL="$serial" ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest 2>&1 | tail -n 30 | block
  find baselineprofile/build/outputs -name '*benchmarkData.json' -print 2>/dev/null | block
fi
ask "26" "Median timeToInitialDisplayMs without / with Baseline Profile?"

log ""
log "## 27 Share from Google Maps"
ask "27" "Google Maps: plan a transit route, Share → 'Plan in Zeitig'. Start and destination taken over, connection planned (or online planning offered)? A shared place becomes the destination?"

log ""
log "Report written to $report"
