<#
.SYNOPSIS
  One-time setup of the release signing key for .github/workflows/release.yml.

.DESCRIPTION
  Creates the release keystore on this computer and stores the four repository secrets the
  release workflow needs (SIGNING_KEYSTORE_B64, SIGNING_STORE_PASSWORD, SIGNING_KEY_ALIAS,
  SIGNING_KEY_PASSWORD). The password is random and never printed.

  Needs a JDK (keytool on PATH or in JAVA_HOME) and the GitHub CLI, logged in with `gh auth login`.

  Keystore and password end up in -OutDir (default: ~\zeitig-signing, outside the repository).
  Back both up offline: GitHub secrets cannot be read back, and without the key no installed copy
  of Zeitig can ever be updated again.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\setup-signing.ps1
#>
param(
    [string]$Repo = "Brokoli5191/Zeitig",
    [string]$OutDir = (Join-Path $HOME "zeitig-signing"),
    [string]$Alias = "zeitig"
)
$ErrorActionPreference = "Stop"

function Find-Tool([string]$Name) {
    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    if ($env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME "bin\$Name.exe"
        if (Test-Path $candidate) { return $candidate }
    }
    throw "$Name not found. Install a JDK (for keytool) and the GitHub CLI (gh) first."
}

$keytool = Find-Tool "keytool"
$gh = Find-Tool "gh"

& $gh auth status --hostname github.com
if ($LASTEXITCODE -ne 0) { throw "The GitHub CLI is not logged in. Run 'gh auth login' first." }

$keystore = Join-Path $OutDir "zeitig-release.jks"
if (Test-Path $keystore) { throw "$keystore already exists. Refusing to replace a release key." }
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

# 32 random bytes as Base64, letters and digits only: nothing keytool, gh or a shell could misread.
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$password = [Convert]::ToBase64String($bytes).TrimEnd("=").Replace("+", "").Replace("/", "")

# Passed through the environment, so the password never appears in a process list.
$env:ZEITIG_SIGNING_PASSWORD = $password
try {
    & $keytool -genkeypair -storetype PKCS12 -keystore $keystore -alias $Alias `
        -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Zeitig" `
        -storepass:env ZEITIG_SIGNING_PASSWORD -keypass:env ZEITIG_SIGNING_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw "keytool failed." }
} finally {
    Remove-Item Env:ZEITIG_SIGNING_PASSWORD -ErrorAction SilentlyContinue
}

$secrets = [ordered]@{
    SIGNING_KEYSTORE_B64   = [Convert]::ToBase64String([IO.File]::ReadAllBytes($keystore))
    SIGNING_STORE_PASSWORD = $password
    SIGNING_KEY_ALIAS      = $Alias
    SIGNING_KEY_PASSWORD   = $password # PKCS12 keystores use one password for store and key
}
foreach ($name in $secrets.Keys) {
    # --body= rather than a pipe: PowerShell appends a line break to everything it pipes into a program.
    & $gh secret set $name --repo $Repo "--body=$($secrets[$name])"
    if ($LASTEXITCODE -ne 0) { throw "Setting $name failed." }
}

Set-Content -Path (Join-Path $OutDir "password.txt") -Value $password -NoNewline

Write-Host ""
Write-Host "Done: the four signing secrets are set on $Repo."
Write-Host "Keystore and password are in $OutDir."
Write-Host "Back up both offline now (for example a password manager and a USB stick), then delete password.txt."
