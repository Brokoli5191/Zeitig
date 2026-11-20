<p align="right"><a href="README.md">English</a> · <b>Deutsch</b></p>

# Zeitig

**Wann muss *ich* los?** Zeitig verbindet die Echtzeit-Abfahrten der Wiener Linien mit der
Gehzeit, die es für jeden deiner Wege lernt, und sagt dir den Moment, in dem du bei der Tür
hinausmusst.

[![CI](https://img.shields.io/github/actions/workflow/status/Brokoli5191/Zeitig/ci.yml?branch=main&label=CI&logo=githubactions&logoColor=white)](https://github.com/Brokoli5191/Zeitig/actions/workflows/ci.yml)
[![Neueste Version](https://img.shields.io/github/v/release/Brokoli5191/Zeitig?include_prereleases&sort=semver&label=release)](https://github.com/Brokoli5191/Zeitig/releases)
[![Downloads](https://img.shields.io/github/downloads/Brokoli5191/Zeitig/total?label=downloads)](https://github.com/Brokoli5191/Zeitig/releases)
[![Lizenz: GPL-3.0-or-later](https://img.shields.io/badge/Lizenz-GPL--3.0--or--later-blue)](LICENSE)
[![minSdk 28](https://img.shields.io/badge/minSdk-28-3DDC84?logo=android&logoColor=white)](app/build.gradle.kts)
[![Kotlin 2.4](https://img.shields.io/badge/Kotlin-2.4.21-7F52FF?logo=kotlin&logoColor=white)](gradle/libs.versions.toml)

> In Arbeit. Die App entsteht Meilenstein für Meilenstein in Pull Requests; diese Seite wird mit
> dem ersten Release zur Produktseite.

## Warum

Öffi-Apps beantworten „Wann kommt die Bim?“. An der Wohnungstür willst du aber wissen: „Wann muss
ich gehen?“ Zeitig rechnet

```
losgehen = Abfahrt − Gehzeit − Puffer
```

und trifft die `Gehzeit`, weil es sie aus deinen echten Wegen lernt, statt zu raten.

## Daten

Datenquelle: Stadt Wien – [data.wien.gv.at](https://data.wien.gv.at) (CC BY 4.0). Echtzeitdaten
der Wiener Linien. Zeitig steht in keiner Verbindung zu den Wiener Linien.

## Lizenz

[GPL-3.0-or-later](LICENSE)
