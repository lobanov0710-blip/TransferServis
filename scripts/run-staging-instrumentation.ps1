# STAGING-06: isolated instrumentation tests (PowerShell 5.1+, Windows 11).
# Uses a brand-new disposable AVD on emulator-5556. Never touches emulator-5554.
# QA APK has an intentionally invalid staging URL and an empty access token.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$emulator = Join-Path $sdk 'emulator\emulator.exe'
$avdManager = Join-Path $sdk 'cmdline-tools\latest\bin\avdmanager.bat'
$gradle = Join-Path $repo 'gradlew.bat'
$serial = 'emulator-5556'
$avdName = 'TS52_QA_' + (Get-Date -Format 'yyyyMMdd_HHmmss') + '_' + ([guid]::NewGuid().ToString('N').Substring(0, 6))
$reportDir = Join-Path $env:TEMP 'TransferServis_Instrumentation'
$report = Join-Path $reportDir ($avdName + '.txt')
$created = $false
$started = $false
foreach ($needed in @($adb, $emulator, $gradle)) {
    if (-not (Test-Path -LiteralPath $needed)) { throw "Required file missing: $needed" }
}
if (-not (Test-Path -LiteralPath $avdManager)) {
    $tools = @(Get-ChildItem -Path (Join-Path $sdk 'cmdline-tools') -Recurse -Filter avdmanager.bat -File -ErrorAction SilentlyContinue)
    if ($tools.Count -eq 0) { throw 'Install Android SDK Command-line Tools (latest) in SDK Manager.' }
    $avdManager = $tools[0].FullName
}
$imagesRoot = Join-Path $sdk 'system-images'
if (-not (Test-Path -LiteralPath $imagesRoot)) { throw 'Install an Android 36+ x86_64 system image.' }
# Discover image IDs from SDK directory names; Pkg.Path is often absent.
$images = @(
    Get-ChildItem -LiteralPath $imagesRoot -Recurse -Filter source.properties -File |
    ForEach-Object {
        $relative = $_.Directory.FullName.Substring($imagesRoot.Length).TrimStart('\', '/')
        $parts = @($relative -split '[\\/]')
        if ($parts.Length -eq 3 -and
            $parts[0] -match '^android-(?:36|37|38|39)(?:\.\d+)?$' -and
            $parts[2] -eq 'x86_64') {
            'system-images;' + ($parts -join ';')
        }
    }
)
if ($images.Count -eq 0) { throw 'Install an Android 36+ x86_64 system image via SDK Manager.' }
$image = @($images | Sort-Object { if ($_ -match ';google_apis;') { 0 } else { 1 } })[0]

# The QA port must be free. Never reuse or overwrite an existing Android device.
$devices = (& $adb devices) -join [Environment]::NewLine
if ($devices -match '(?m)^emulator-5556\s') { throw 'QA serial in use. No existing emulator will be touched.' }
foreach ($port in @(5556, 5557)) {
    if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) {
        throw "TCP port $port in use; stop here rather than disturb an existing emulator."
    }
}
New-Item -ItemType Directory -Path $reportDir -Force | Out-Null
Push-Location $repo
try {
    Write-Host '[1/5] Building QA APKs with no usable server access'
    $buildArgs = @(':app:assembleDebug', ':app:assembleDebugAndroidTest',
        '-PtransferStagingBaseUrl=https://staging-not-configured.invalid/',
        '-PtransferStagingAccessToken=', '--console=plain')
    & $gradle @buildArgs
    if ($LASTEXITCODE -ne 0) { throw 'QA APK build failed' }
    $apk = Join-Path $repo 'app\build\outputs\apk\debug\app-debug.apk'
    $testsApk = Join-Path $repo 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
    if (-not (Test-Path $apk) -or -not (Test-Path $testsApk)) { throw 'Built APK files missing' }

    Write-Host "[2/5] Creating a NEW disposable AVD: $avdName"
    'no' | & $avdManager create avd --name $avdName --package $image
    if ($LASTEXITCODE -ne 0) { throw 'QA AVD creation failed' }
    $created = $true

    Write-Host "[3/5] Starting QA emulator: $serial"
    $out = Join-Path $reportDir ($avdName + '-emulator.log')
    $err = Join-Path $reportDir ($avdName + '-emulator-errors.log')
    Start-Process -FilePath $emulator -ArgumentList @(
        '-avd', $avdName, '-port', '5556', '-no-window', '-no-audio',
        '-no-snapshot-load', '-no-snapshot-save', '-gpu', 'swiftshader_indirect'
    ) -RedirectStandardOutput $out -RedirectStandardError $err | Out-Null
    $started = $true

    $booted = $false
    for ($i = 0; $i -lt 100; $i++) {
        Start-Sleep -Seconds 3
        $detected = (& $adb devices) -join [Environment]::NewLine
        if ($detected -match '(?m)^emulator-5556\s+device\s*$') {
            $boot = (& $adb -s $serial shell getprop sys.boot_completed | Out-String).Trim()
            if ($boot -eq '1') { $booted = $true; break }
        }
    }
    if (-not $booted) { throw "QA emulator boot failed. See $err" }
    $actualAvd = (& $adb -s $serial emu avd name 2>$null | Out-String)
    if (-not $actualAvd.Contains($avdName)) { throw 'QA serial is not our temporary AVD; aborting' }

    Write-Host '[4/5] Installing on the disposable QA emulator ONLY'
    & $adb -s $serial install -r $apk
    if ($LASTEXITCODE -ne 0) { throw 'QA app install failed' }
    & $adb -s $serial install -r $testsApk
    if ($LASTEXITCODE -ne 0) { throw 'QA test package install failed' }
    $lines = @(& $adb -s $serial shell pm list instrumentation)
    $runnerLine = @($lines | Where-Object { $_ -match 'target=ru\.transferservis\.app\.staging\)' } | Select-Object -First 1)
    if ($runnerLine.Count -ne 1 -or $runnerLine[0] -notmatch '^instrumentation:([^\s]+)') {
        throw 'Instrumentation runner for staging app not found'
    }
    $runner = $Matches[1]

    Write-Host '[5/5] Running ALL Android instrumentation tests'
    $result = @(& $adb -s $serial shell am instrument -w $runner 2>&1)
    $exitCode = $LASTEXITCODE
    $result | Out-File -LiteralPath $report -Encoding UTF8
    $text = $result -join [Environment]::NewLine
    @($result | Where-Object { $_ -match 'OK \(\d+ tests?\)|FAILURES!!!|INSTRUMENTATION_CODE:' }) |
        ForEach-Object { Write-Host $_ }
    if ($exitCode -ne 0 -or $text -notmatch 'OK \(\d+ tests?\)' -or
        $text -match 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=Process crashed') {
        throw "Tests failed; see report at $report"
    }
    Write-Host 'QA_INSTRUMENTATION_PASSED=True'
}
finally {
    if ($started) {
        $connected = (& $adb devices) -join [Environment]::NewLine
        if ($connected -match '(?m)^emulator-5556\s+device\s*$') {
            $currentName = (& $adb -s $serial emu avd name | Out-String)
            if ($currentName.Contains($avdName)) {
                & $adb -s $serial emu kill | Out-Null
            }
        }
        for ($i = 0; $i -lt 20; $i++) {
            $connected = (& $adb devices) -join [Environment]::NewLine
            if ($connected -notmatch '(?m)^emulator-5556\s') { break }
            Start-Sleep -Seconds 2
        }
    }
    $connected = (& $adb devices) -join [Environment]::NewLine
    if ($created -and $connected -notmatch '(?m)^emulator-5556\s') {
        & $avdManager delete avd --name $avdName | Out-Null
    } elseif ($created) {
        Write-Warning "QA emulator is still connected. AVD $avdName retained rather than deleting a running device."
    }
    # Restore locally configured Debug APK for developer convenience.
    # Never echo or upload private Gradle properties or resulting APKs.
    Write-Host 'Restoring local Debug build configuration'
    & $gradle ':app:assembleDebug' '--console=plain' | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Warning 'APK restore failed; the already installed emulator-5554 app is unaffected.'
    }
    Pop-Location
    Write-Host "QA report path: $report"
}
