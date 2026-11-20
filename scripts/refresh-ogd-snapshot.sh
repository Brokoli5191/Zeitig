#!/usr/bin/env bash
# Downloads the Wiener Linien reference CSVs into the given directory (default: the bundled snapshot).
# Each file must start with its expected header, otherwise the script aborts and leaves the target
# untouched. Used by .github/workflows/refresh-stops.yml; safe to run locally.
set -euo pipefail

target="${1:-data/src/main/assets/ogd}"
base="https://www.wienerlinien.at/ogd_realtime/doku/ogd"
declare -A headers=(
  [wienerlinien-ogd-haltepunkte.csv]="StopID;DIVA;StopText;Municipality;MunicipalityID;Longitude;Latitude"
  [wienerlinien-ogd-haltestellen.csv]="DIVA;PlatformText;Municipality;MunicipalityID;Longitude;Latitude"
  [wienerlinien-ogd-linien.csv]="LineID;LineText;SortingHelp;Realtime;MeansOfTransport"
  [wienerlinien-ogd-fahrwegverlaeufe.csv]="LineID;PatternID;StopSeqCount;StopID;Direction"
  [wienerlinien-ogd-version.csv]="DateValidFrom;VersionDate"
)

staging="$(mktemp -d)"
trap 'rm -rf "$staging"' EXIT

for file in "${!headers[@]}"; do
  curl --fail --silent --show-error --location --retry 3 --retry-delay 5 \
    --user-agent "Zeitig snapshot refresh (+https://github.com/Brokoli5191/Zeitig)" \
    --output "$staging/$file" "$base/$file"
  first_line="$(head -n 1 "$staging/$file" | tr -d '\r\357\273\277')"
  if [[ "$first_line" != "${headers[$file]}" ]]; then
    echo "::error::$file has an unexpected header: $first_line" >&2
    exit 1
  fi
  lines="$(wc -l < "$staging/$file")"
  echo "$file: $lines lines"
  sleep 2 # be gentle with the operator's server
done

mkdir -p "$target"
cp "$staging"/*.csv "$target"/
