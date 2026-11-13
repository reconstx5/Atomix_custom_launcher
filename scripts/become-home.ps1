# become-home.ps1 - make Atomix the TV's Home screen, one reversible step at a time.
#   .\scripts\become-home.ps1 -Step 1   back up the current Home, set Atomix as Home, press Home, verify
#   .\scripts\become-home.ps1 -Step 2   reboot test: reboot, reconnect, press Home, verify again
#   .\scripts\become-home.ps1 -Step 3   ONLY if 1-2 fail and the owner agreed: disable the stock launcher
#                                       (recorded in D:\TV\disabled-by-us.txt), then reboot test again
# Undo any of these with .\scripts\restore-stock-home.ps1 (also called from D:\TV\undo.ps1).
param([Parameter(Mandatory)][ValidateSet(1, 2, 3)][int]$Step)

. D:\TV\tvlib.ps1
$ErrorActionPreference = 'Continue'
$atomix = 'dev.atomix.launcher/.HomeActivity'
$stock = 'com.google.android.tvlauncher'

function Get-HomeActivity {
    (Tv shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME |
        Select-Object -Last 1).Trim()
}

function Test-AtomixHome {
    $resolved = Get-HomeActivity
    Key HOME
    Start-Sleep 4
    $focus = Get-TvFocus
    $ok = ($resolved -eq $atomix) -and ("$focus" -match 'dev\.atomix\.launcher/\.HomeActivity')
    "Home resolves to: $resolved"
    "Foreground:       $focus"
    if ($ok) { 'PASS - Atomix is Home' } else { 'FAIL - Atomix is not Home' }
}

function Wait-TvReboot {
    Tv reboot | Out-Null
    'Rebooting... (this takes about a minute)'
    Start-Sleep 25
    for ($i = 0; $i -lt 40; $i++) {
        Tv connect $TvSerial | Out-Null
        $booted = (Tv shell getprop sys.boot_completed 2>$null) -join ''
        if ($booted.Trim() -eq '1') { Start-Sleep 10; 'TV is back'; return $true }
        Start-Sleep 5
    }
    'TV did not come back within ~4 minutes'
    return $false
}

switch ($Step) {
    1 {
        $dir = "D:\TV\backup\launcher-$(Get-Date -Format yyyyMMdd-HHmmss)"
        New-Item -ItemType Directory -Force $dir | Out-Null
        Get-HomeActivity | Set-Content -Encoding utf8 "$dir\home.txt"
        "Backed up current Home ($(Get-Content "$dir\home.txt")) to $dir\home.txt"
        Tv shell cmd package set-home-activity --user 0 $atomix
        Test-AtomixHome
    }
    2 {
        if (Wait-TvReboot) { Test-AtomixHome }
    }
    3 {
        Tv shell pm disable-user --user 0 $stock
        $list = 'D:\TV\disabled-by-us.txt'
        if (-not ((Get-Content $list -ErrorAction SilentlyContinue) -contains $stock)) { Add-Content $list $stock }
        "Disabled $stock (recorded in $list)"
        if (Wait-TvReboot) { Test-AtomixHome }
        'Now check by hand and report each: Live TV, HDMI inputs, casting, the Assistant button.'
    }
}
