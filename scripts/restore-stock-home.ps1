# restore-stock-home.ps1 - put the TV's original Home screen back. Safe to run any time.
# Re-enables the stock launcher, sets Home to whatever the newest become-home backup recorded
# (the stock Google TV launcher if there is no backup), and verifies. Atomix stays installed.
. D:\TV\tvlib.ps1
$ErrorActionPreference = 'Continue'
$stock = 'com.google.android.tvlauncher'
$target = "$stock/.MainActivity"

$backup = Get-ChildItem D:\TV\backup -Directory -Filter 'launcher-*' -ErrorAction SilentlyContinue |
    Sort-Object Name -Descending | Select-Object -First 1
if ($backup -and (Test-Path "$($backup.FullName)\home.txt")) {
    $saved = (Get-Content "$($backup.FullName)\home.txt" | Select-Object -First 1).Trim()
    if ($saved -match '^[\w.]+/[\w.$]+$' -and $saved -notmatch '^dev\.atomix\.launcher/') { $target = $saved }
}

Tv shell pm enable $stock | Out-Null
$list = 'D:\TV\disabled-by-us.txt'
if (Test-Path $list) { (Get-Content $list) | Where-Object { $_ -ne $stock } | Set-Content $list }

Tv shell cmd package set-home-activity --user 0 $target
$resolved = (Tv shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME |
    Select-Object -Last 1).Trim()
Key HOME
Start-Sleep 4
"Home resolves to: $resolved"
"Foreground:       $(Get-TvFocus)"
if ($resolved -eq $target) { "PASS - Home restored to $target" } else { "FAIL - expected $target" }

# Atomix stays installed. To remove it as well, uncomment:
# Tv uninstall dev.atomix.launcher
