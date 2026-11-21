#!/usr/bin/env bash
# One-time setup of the release signing key for .github/workflows/release.yml (Linux, macOS).
#
# Creates the release keystore on this computer and stores the four repository secrets the
# release workflow needs. The password is random and never printed. Needs a JDK (keytool) and the
# GitHub CLI, logged in with `gh auth login`.
#
# Keystore and password end up in $OUT_DIR (default: ~/zeitig-signing, outside the repository).
# Back both up offline: GitHub secrets cannot be read back, and without the key no installed copy
# of Zeitig can ever be updated again.
#
#   scripts/setup-signing.sh [owner/repo]
set -euo pipefail

repo="${1:-Brokoli5191/Zeitig}"
out_dir="${OUT_DIR:-$HOME/zeitig-signing}"
alias_name="zeitig"
keystore="$out_dir/zeitig-release.jks"

keytool_bin="$(command -v keytool || true)"
if [[ -z "$keytool_bin" && -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/keytool" ]]; then keytool_bin="$JAVA_HOME/bin/keytool"; fi
[[ -n "$keytool_bin" ]] || { echo "keytool not found: install a JDK first." >&2; exit 1; }
command -v gh >/dev/null || { echo "gh not found: install the GitHub CLI first." >&2; exit 1; }
gh auth status --hostname github.com >/dev/null || { echo "Run 'gh auth login' first." >&2; exit 1; }

[[ ! -e "$keystore" ]] || { echo "$keystore already exists. Refusing to replace a release key." >&2; exit 1; }
mkdir -p "$out_dir"
chmod 700 "$out_dir"

# 32 random bytes as Base64, letters and digits only: nothing keytool, gh or a shell could misread.
password="$(head -c 32 /dev/urandom | base64 | tr -d '\n=+/')"

# Passed through the environment, so the password never appears in a process list.
ZEITIG_SIGNING_PASSWORD="$password" "$keytool_bin" -genkeypair -storetype PKCS12 \
  -keystore "$keystore" -alias "$alias_name" -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Zeitig" -storepass:env ZEITIG_SIGNING_PASSWORD -keypass:env ZEITIG_SIGNING_PASSWORD

# Piped through stdin, so no value appears on a command line.
base64 < "$keystore" | tr -d '\n' | gh secret set SIGNING_KEYSTORE_B64 --repo "$repo"
printf '%s' "$password" | gh secret set SIGNING_STORE_PASSWORD --repo "$repo"
printf '%s' "$alias_name" | gh secret set SIGNING_KEY_ALIAS --repo "$repo"
# PKCS12 keystores use one password for store and key.
printf '%s' "$password" | gh secret set SIGNING_KEY_PASSWORD --repo "$repo"

umask 077
printf '%s' "$password" > "$out_dir/password.txt"

echo
echo "Done: the four signing secrets are set on $repo."
echo "Keystore and password are in $out_dir."
echo "Back up both offline now (for example a password manager and a USB stick), then delete password.txt."
