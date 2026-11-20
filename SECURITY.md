# Security policy

## Supported versions

Only the latest release receives fixes.

## Reporting a vulnerability

Please report vulnerabilities privately through
[GitHub Security Advisories](https://github.com/Brokoli5191/Zeitig/security/advisories/new).
Do not open a public issue. You can expect a first answer within a week.

## Verifying releases

Every release APK is built by GitHub Actions, signed, and published with a SHA-256 checksum and a
build-provenance attestation:

```sh
sha256sum -c SHA256SUMS.txt
gh attestation verify zeitig-v<version>.apk --repo Brokoli5191/Zeitig
```
