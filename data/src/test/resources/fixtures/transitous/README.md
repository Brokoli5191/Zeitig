# Transitous (MOTIS 2) fixtures

Recorded from `https://api.transitous.org/api/` on 2026-11-20 for Monday 2026-11-23. To keep the
repository small, only the first three itineraries are kept and the turn-by-turn `steps`,
`alternatives`, `direct` and `debugOutput` fields are removed; everything else is verbatim.

| File | Request |
|---|---|
| `plan_transfer_kagran_meidling.json` | `v6/plan?fromPlace=48.2433,16.4339&toPlace=48.1745,16.3337&time=2026-11-23T06:30:00Z&numItineraries=3&language=de` (U1 → train) |
| `plan_arriveby_stephansplatz_hbf.json` | `v6/plan?fromPlace=48.2085,16.3721&toPlace=48.1859,16.3773&time=2026-11-23T06:45:00Z&arriveBy=true` |
| `plan_wheelchair_schwedenplatz_westbahnhof.json` | `v6/plan?…&pedestrianProfile=WHEELCHAIR` (U4 → U6) |
| `geocode_mariahilfer_strasse.json` | `v1/geocode?text=Mariahilfer Straße 85&language=de` |
| `geocode_stop_karlsplatz.json` | `v1/geocode?text=Karlsplatz&language=de&place=48.2082,16.3738&numResults=5` |
| `plan_bad_request.json` | `v6/plan?fromPlace=nonsense&…` (HTTP 404, `{"error": …}`) |

Routing data: Transitous (transitous.org/sources) and © OpenStreetMap contributors.
