<p align="right"><b>English</b> · <a href="README.de.md">Deutsch</a></p>

# Zeitig

**When do *I* have to leave?** Zeitig combines Wiener Linien real-time departures with the walking
time it learns for each of your routes and tells you the exact moment to walk out the door.

[![CI](https://img.shields.io/github/actions/workflow/status/Brokoli5191/Zeitig/ci.yml?branch=main&label=CI&logo=githubactions&logoColor=white)](https://github.com/Brokoli5191/Zeitig/actions/workflows/ci.yml)
[![Latest release](https://img.shields.io/github/v/release/Brokoli5191/Zeitig?include_prereleases&sort=semver&label=release)](https://github.com/Brokoli5191/Zeitig/releases)
[![Downloads](https://img.shields.io/github/downloads/Brokoli5191/Zeitig/total?label=downloads)](https://github.com/Brokoli5191/Zeitig/releases)
[![Licence: GPL-3.0-or-later](https://img.shields.io/badge/licence-GPL--3.0--or--later-blue)](LICENSE)
[![minSdk 28](https://img.shields.io/badge/minSdk-28-3DDC84?logo=android&logoColor=white)](app/build.gradle.kts)
[![Kotlin 2.4](https://img.shields.io/badge/Kotlin-2.4.21-7F52FF?logo=kotlin&logoColor=white)](gradle/libs.versions.toml)

> Work in progress. The app is built milestone by milestone in pull requests; this README becomes
> the product page once the first release is out.

## Why

Transit apps answer "when does the tram come?". The question you actually have at the door is
"when do I have to go?". Zeitig answers it with

```
leaveAt = departure − walkTime − buffer
```

and gets the `walkTime` right by learning it from your real trips instead of guessing.

## Data

Datenquelle: Stadt Wien – [data.wien.gv.at](https://data.wien.gv.at) (CC BY 4.0). Real-time data
by Wiener Linien. Zeitig is not affiliated with Wiener Linien.

## Licence

[GPL-3.0-or-later](LICENSE)
