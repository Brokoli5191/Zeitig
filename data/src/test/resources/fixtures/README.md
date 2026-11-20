# Wiener Linien OGD fixtures

Recorded from `https://www.wienerlinien.at/ogd_realtime/` on 2026-11-20 (CET, +0100):

| File | Request |
|---|---|
| `monitor_diva_schwedenplatz.json` | `monitor?diva=60201198&activateTrafficInfo=stoerungkurz&activateTrafficInfo=stoerunglang&activateTrafficInfo=aufzugsinfo` |
| `monitor_stopids.json` | `monitor?stopId=4113&stopId=4116&activateTrafficInfo=…` |
| `monitor_elevator_keplerplatz.json` | `monitor?stopId=4103&stopId=4126&activateTrafficInfo=…` (elevator out of service) |
| `monitor_invalid.json` | `monitor?stopId=999999` (unknown stop → empty monitor list, code 1) |
| `trafficinfolist_aufzug.json` | `trafficInfoList?name=aufzugsinfo` |

Synthetic edge cases (hand-written, documented in the tests): `monitor_scheduled_only.json`
(no `timeReal`, string `rbl`, unknown fields, DST night, broken timestamps), `monitor_rate_limited.json`
(code 316), `monitor_unknown_stop.json` (code 312), `monitor_malformed.json` (truncated JSON).

Nothing personal is contained – the responses describe public timetables. Data: Stadt Wien –
data.wien.gv.at (CC BY 4.0).
