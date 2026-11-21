# Releasing

## Once: the signing key

The release workflow signs the APK with a key that exists only as four GitHub Actions secrets.
Create it once on your own computer, never in CI or a chat. Back it up: without this key no
installed copy of Zeitig can ever be updated again.

### By hand on Ubuntu

1. Create the keystore in a terminal. `keytool` asks for a password twice; with PKCS12 it is also
   the key password.

   ```sh
   sudo apt update && sudo apt install -y default-jre-headless
   mkdir -p ~/zeitig-signing && cd ~/zeitig-signing
   keytool -genkeypair -v -storetype PKCS12 -keystore zeitig-release.jks -alias zeitig \
     -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Zeitig"
   base64 -w0 zeitig-release.jks > zeitig-release.b64
   xdg-open zeitig-release.b64
   ```

2. Open [Settings › Secrets and variables › Actions › New repository secret](https://github.com/Brokoli5191/Zeitig/settings/secrets/actions/new)
   and add four secrets, one after the other:

   | Name | Secret |
   |---|---|
   | `SIGNING_KEYSTORE_B64` | the whole line in `zeitig-release.b64` (Ctrl+A, Ctrl+C in the editor) |
   | `SIGNING_STORE_PASSWORD` | the password |
   | `SIGNING_KEY_ALIAS` | `zeitig` |
   | `SIGNING_KEY_PASSWORD` | the same password |

   The *Settings* tab only shows when you are signed in as the owner; in a narrow window it hides
   behind "…".

3. Delete the Base64 copy and back up the rest offline (password manager, USB stick):

   ```sh
   rm ~/zeitig-signing/zeitig-release.b64
   ```

### With a script

`scripts/setup-signing.sh` (Linux, macOS) and `scripts/setup-signing.ps1` (Windows) do the same in
one run: random password, keystore, and the four secrets through the GitHub CLI (`gh auth login`
first). Keystore and password end up in `~/zeitig-signing`.

## Each release

1. Make sure `main` is green.
2. *Actions › Release › Run workflow* on `main`, with the version: `0.1.0-rc1`, `1.2.0`, …
   The scheme and the version codes are in the README under "Releases".
3. The workflow builds, signs and verifies the APK, attests it and only then creates the tag and
   the GitHub release. If a secret is missing, it stops in the first step and names it; no tag is
   left behind.

## Baseline Profile

*Actions › Baseline profile › Run workflow* regenerates the profile on an emulator and opens a pull
request when it changed. Merge it before a release.
