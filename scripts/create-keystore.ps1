# create-keystore.ps1 - the owner runs this once, in their own terminal, to make the release signing key.
# You choose the password; it is typed only into keytool's own prompt and a hidden prompt here.
# Creates D:\atomix-launcher-tools\atomix-release.jks and keystore.properties (both outside the repo).
# KEEP A BACKUP of both files: without them you cannot install updates over the release build.
$ErrorActionPreference = 'Stop'
$tools = 'D:\atomix-launcher-tools'
$jks = "$tools\atomix-release.jks"
$props = "$tools\keystore.properties"
$keytool = "$tools\jdk17\bin\keytool.exe"

if (Test-Path $jks) { throw "$jks already exists - not overwriting it." }

Write-Host 'keytool will ask for a keystore password (at least 6 characters), then for your name etc. (any values are fine).'
& $keytool -genkeypair -v -keystore $jks -alias atomix -keyalg RSA -keysize 2048 -validity 10000
if ($LASTEXITCODE -ne 0) { throw 'keytool failed' }

$secure = Read-Host 'Type the same keystore password once more (saved to keystore.properties for Gradle)' -AsSecureString
$plain = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
@(
    "storeFile=$($jks -replace '\\', '/')"
    "storePassword=$plain"
    'keyAlias=atomix'
    "keyPassword=$plain"
) | Set-Content -Encoding ascii $props
$plain = $null
Write-Host "Done. Wrote $jks and $props. Back both up somewhere safe (e.g. a USB stick)." -ForegroundColor Green
