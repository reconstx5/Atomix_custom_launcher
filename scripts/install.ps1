# install.ps1 - build the Atomix launcher and install it on the TV.
#   .\scripts\install.ps1            debug build
#   .\scripts\install.ps1 -Release   release build
param([switch]$Release)
$ErrorActionPreference = 'Stop'
$tools = 'D:\atomix-launcher-tools'
$env:JAVA_HOME = "$tools\jdk17"
$env:ANDROID_HOME = "$tools\android-sdk"
$root = Split-Path $PSScriptRoot -Parent
$variant = if ($Release) { 'Release' } else { 'Debug' }

Push-Location $root
try {
    & .\gradlew.bat ":app:assemble$variant" --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed ($LASTEXITCODE)" }
} finally { Pop-Location }

$apk = Get-ChildItem "$root\app\build\outputs\apk\$($variant.ToLower())" -Filter *.apk | Select-Object -First 1
if (-not $apk) { throw 'No APK produced' }

. D:\TV\tvlib.ps1
$ErrorActionPreference = 'Continue'
$out = Tv install -r $apk.FullName
$out
if (($out -join "`n") -notmatch 'Success') { throw 'adb install failed' }

$list = 'D:\TV\installed-by-us.txt'
$pkg = 'dev.atomix.launcher'
if (-not ((Get-Content $list -ErrorAction SilentlyContinue) -contains $pkg)) { Add-Content $list $pkg }
"Installed $($apk.Name)"
