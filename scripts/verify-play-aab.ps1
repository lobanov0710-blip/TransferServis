<#
.SYNOPSIS
  RELEASE-PLAY-01: verify an Android App Bundle without exposing upload keys.
  This script NEVER signs or uploads a bundle.
.EXAMPLE
  .\scripts\verify-play-aab.ps1 -Mode Unsigned
.EXAMPLE
  .\scripts\verify-play-aab.ps1 -Mode Signed -BundlePath 'D:\TransferServisRelease\app-release.aab' -ExpectedCertificateSha256 '03:0A:83:6C:E4:95:45:8A:EE:39:DB:63:30:E9:94:39:A4:33:82:7C:70:47:2A:07:16:67:D2:EE:1B:56:16:89'
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Unsigned', 'Signed')]
    [string] $Mode,

    [string] $BundlePath = 'app/build/outputs/bundle/release/app-release.aab',

    [string] $ExpectedCertificateSha256 = '',

    [string] $ExpectedPackage = 'ru.transferservis.app',
    [string] $ExpectedVersionCode = '1',
    [string] $ExpectedVersionName = '1.0'
)

$ErrorActionPreference = 'Stop'

function Fail([string] $Reason) {
    throw "PLAY_BUNDLE_PREFLIGHT_FAILED: $Reason"
}

# A valid JAR signature made with the WRONG upload key is not a valid release.
# Require the pin before trying to read the bundle.
$expectedPin = ''
if ($Mode -eq 'Signed') {
    if ([string]::IsNullOrWhiteSpace($ExpectedCertificateSha256)) {
        Fail 'Signed mode requires -ExpectedCertificateSha256'
    }
    $expectedPin = ($ExpectedCertificateSha256 -replace ':', '').Trim().ToUpperInvariant()
    if ($expectedPin -notmatch '^[0-9A-F]{64}$') {
        Fail 'Expected certificate SHA-256 must be 64 hexadecimal characters'
    }
}

if (-not (Test-Path -LiteralPath $BundlePath -PathType Leaf)) {
    Fail "Bundle not found: $BundlePath"
}
$bundle = (Resolve-Path -LiteralPath $BundlePath).Path
if ((Get-Item -LiteralPath $bundle).Length -eq 0) {
    Fail 'Bundle must not be empty'
}
foreach ($tool in @('jar', 'jarsigner', 'keytool')) {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
        Fail "Missing Java tool: $tool"
    }
}

# Verify the archive is a release AAB, not an APK or a renamed ZIP.
$entries = @(& jar tf $bundle 2>&1)
if ($LASTEXITCODE -ne 0) { Fail 'JDK jar cannot inspect bundle' }
foreach ($required in @(
    'BundleConfig.pb',
    'base/manifest/AndroidManifest.xml',
    'base/dex/classes.dex'
)) {
    if ($entries -notcontains $required) {
        Fail "AAB missing required entry: $required"
    }
}

# Root-level JAR signing material only. Nested library entries are irrelevant.
$signatures = @($entries | Where-Object {
    $_ -match '(?i)^META-INF/[^/]+\.(SF|RSA|DSA|EC)$'
})
if ($Mode -eq 'Unsigned') {
    if ($signatures.Count -gt 0) {
        Fail 'Unsigned AAB unexpectedly contains signing entries'
    }
    Write-Output 'PLAY_AAB_UNSIGNED=true'
}
else {
    if ($signatures.Count -eq 0) {
        Fail 'Signed AAB contains no JAR signature files'
    }
    # App bundles are JAR-signed, unlike APK Signature Scheme v2/v3.
    $verification = @(& jarsigner -verify -verbose $bundle 2>&1)
    if ($LASTEXITCODE -ne 0 -or $verification.Count -eq 0) {
        Fail 'jarsigner verification failed'
    }
    $certInfo = (& keytool -J-Duser.language=en -J-Duser.country=US -printcert -jarfile $bundle 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) { Fail 'Unable to extract AAB signer certificate' }
    $match = [regex]::Match($certInfo, '(?im)^\s*SHA256:\s*([0-9a-fA-F:]{64,96})\s*$')
    if (-not $match.Success) { Fail 'Unable to parse signer certificate SHA-256' }
    $actualPin = ($match.Groups[1].Value -replace ':', '').ToUpperInvariant()
    if ($actualPin -ne $expectedPin) {
        Fail 'AAB signing certificate differs from the approved upload certificate'
    }
    Write-Output 'PLAY_AAB_SIGNATURE_VALID=true'
    Write-Output 'PLAY_AAB_CERTIFICATE_MATCH=true'
    Write-Output "CERTIFICATE_SHA256=$actualPin"
}

# Source-config verification is separate from bundletool's compiled manifest.
# We do NOT claim these values were extracted from the binary AAB.
$gradleFile = 'app/build.gradle.kts'
if (-not (Test-Path -LiteralPath $gradleFile -PathType Leaf)) {
    Fail 'Run from the TransferServis repository root'
}
$gradle = Get-Content -LiteralPath $gradleFile -Raw
$checks = @(
    @('applicationId', 'applicationId\s*=\s*"' + [regex]::Escape($ExpectedPackage) + '"'),
    @('versionCode', 'versionCode\s*=\s*' + [regex]::Escape($ExpectedVersionCode) + '(?!\d)'),
    @('versionName', 'versionName\s*=\s*"' + [regex]::Escape($ExpectedVersionName) + '"'),
    @('FCM disabled', 'buildConfigField\("boolean",\s*"PASSENGER_PUSH_NETWORK_ENABLED",\s*"false"\)')
)
foreach ($check in $checks) {
    if ($gradle -notmatch $check[1]) {
        Fail "Release source configuration mismatch: $($check[0])"
    }
}
if ($gradle -notmatch 'https://transfer-servis52\.ru/') {
    Fail 'Production API gateway is not present in release Gradle configuration'
}

$hash = (Get-FileHash -LiteralPath $bundle -Algorithm SHA256).Hash
Write-Output "RELEASE_CONFIG_PACKAGE=$ExpectedPackage"
Write-Output "RELEASE_CONFIG_VERSION_CODE=$ExpectedVersionCode"
Write-Output "RELEASE_CONFIG_VERSION_NAME=$ExpectedVersionName"
Write-Output 'RELEASE_CONFIG_PUSH_NETWORK=false'
Write-Output "AAB_SHA256=$hash"
Write-Output "AAB_FILE=$bundle"
Write-Output 'PLAY_BUNDLE_PREFLIGHT=SUCCESS'
$global:LASTEXITCODE = 0
