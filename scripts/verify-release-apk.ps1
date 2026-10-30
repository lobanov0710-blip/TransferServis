<#
.SYNOPSIS
  Локальная проверка APK до/после подписания. Не создаёт ключ и не подписывает APK.
.EXAMPLE
  .\scripts\verify-release-apk.ps1 -Mode Unsigned
.EXAMPLE
  .\scripts\verify-release-apk.ps1 -Mode Signed -ApkPath 'D:\Release\TransferServis-1.0.apk' -ExpectedCertificateSha256 '<production-certificate-SHA256>'
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Unsigned', 'Signed')]
    [string] $Mode,

    [string] $ApkPath = 'app/build/outputs/apk/release/app-release-unsigned.apk',

    [string] $SdkRoot = '',

    [string] $ExpectedPackage = 'ru.transferservis.app',

    [string] $ExpectedVersionCode = '1',

    [string] $ExpectedVersionName = '1.0',

    [string] $ExpectedCertificateSha256 = ''
)

$ErrorActionPreference = 'Stop'

function Fail([string] $Reason) {
    throw "RELEASE_PREFLIGHT_FAILED: $Reason"
}

# Require a certificate pin so a valid signature made with the wrong keystore
# cannot pass the release gate. Normalize colon-delimited or plain hex SHA-256.
$expectedCertificateNormalized = ''
if ($Mode -eq 'Signed') {
    if ([string]::IsNullOrWhiteSpace($ExpectedCertificateSha256)) {
        Fail 'Для Signed требуется -ExpectedCertificateSha256 с отпечатком production сертификата'
    }
    $expectedCertificateNormalized = ($ExpectedCertificateSha256 -replace ':', '').Trim().ToUpperInvariant()
    if ($expectedCertificateNormalized -notmatch '^[0-9A-F]{64}$') {
        Fail 'Некорректный SHA-256 сертификата: требуется 64 шестнадцатеричных символа'
    }
}

if (-not (Test-Path -LiteralPath $ApkPath -PathType Leaf)) {
    Fail "APK не найден: $ApkPath"
}
$apk = (Resolve-Path -LiteralPath $ApkPath).Path
if ((Get-Item -LiteralPath $apk).Length -eq 0) {
    Fail 'APK пустой'
}

$candidates = @()
if (-not [string]::IsNullOrWhiteSpace($SdkRoot)) {
    $candidates += $SdkRoot
}
if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_SDK_ROOT)) {
    $candidates += $env:ANDROID_SDK_ROOT
}
if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_HOME)) {
    $candidates += $env:ANDROID_HOME
}
if (-not [string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
    $candidates += (Join-Path $env:LOCALAPPDATA 'Android\Sdk')
}

$toolsDirectory = $null
foreach ($candidate in $candidates) {
    $buildTools = Join-Path $candidate 'build-tools'
    if (-not (Test-Path -LiteralPath $buildTools -PathType Container)) {
        continue
    }
    $versions = @(
        Get-ChildItem -LiteralPath $buildTools -Directory |
            Sort-Object -Property @{ Expression = {
                try { [version] $_.Name } catch { [version] '0.0' }
            }; Descending = $true }
    )
    foreach ($folder in $versions) {
        $aapt = Join-Path $folder.FullName 'aapt.exe'
        $zipalign = Join-Path $folder.FullName 'zipalign.exe'
        $apksigner = Join-Path $folder.FullName 'apksigner.bat'
        if ((Test-Path -LiteralPath $aapt -PathType Leaf) -and
            (Test-Path -LiteralPath $zipalign -PathType Leaf) -and
            (Test-Path -LiteralPath $apksigner -PathType Leaf)) {
            $toolsDirectory = $folder.FullName
            break
        }
    }
    if ($null -ne $toolsDirectory) { break }
}
if ($null -eq $toolsDirectory) {
    Fail 'Нужны Android SDK Build Tools (aapt.exe, zipalign.exe, apksigner.bat); укажите -SdkRoot'
}

$aapt = Join-Path $toolsDirectory 'aapt.exe'
$zipalign = Join-Path $toolsDirectory 'zipalign.exe'
$apksigner = Join-Path $toolsDirectory 'apksigner.bat'

# Не разрешаем случайно проверять staging вместо production Release.
$badging = @(& $aapt dump badging $apk)
if ($LASTEXITCODE -ne 0) {
    Fail 'aapt не смог прочитать метаданные APK'
}
$packageLine = $badging | Where-Object { $_ -match '^package:' } | Select-Object -First 1
if ([string]::IsNullOrWhiteSpace($packageLine)) {
    Fail 'В APK отсутствует строка package'
}
$packageRegex = "name='([^']+)'.*versionCode='([^']+)'.*versionName='([^']+)'"
$match = [regex]::Match($packageLine, $packageRegex)
if (-not $match.Success) {
    Fail 'Не удалось прочитать applicationId/versionCode/versionName из aapt'
}
$id = $match.Groups[1].Value
$code = $match.Groups[2].Value
$name = $match.Groups[3].Value
if ($id -ne $ExpectedPackage) {
    Fail "Неверный applicationId: $id"
}
if ($code -ne $ExpectedVersionCode -or $name -ne $ExpectedVersionName) {
    Fail "Неверная версия: versionCode=$code, versionName=$name"
}

& $zipalign -c -v 4 $apk | Out-Null
if ($LASTEXITCODE -ne 0) {
    Fail 'Проверка zipalign завершилась ошибкой'
}

# Для неподписанного APK ошибка apksigner ожидается; подавляем только
# вывод этой конкретной команды, не скрываем результат других проверок.
$originalPreference = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try {
    $verificationOutput = (& $apksigner verify --verbose --print-certs $apk 2>&1 | Out-String)
    $verificationExit = $LASTEXITCODE
}
finally {
    $ErrorActionPreference = $originalPreference
}

if ($Mode -eq 'Unsigned') {
    if ($verificationExit -eq 0) {
        Fail 'Ожидался неподписанный APK, но подпись действительна'
    }
    Write-Output 'APK_UNSIGNED_VERIFIED=true'
}
else {
    if ($verificationExit -ne 0) {
        Fail 'Подпись APK не прошла apksigner verify'
    }
    $fingerprintMatch = [regex]::Match(
        $verificationOutput,
        'Signer #1 certificate SHA-256 digest:\s*([0-9A-Fa-f]{64})'
    )
    if (-not $fingerprintMatch.Success) {
        Fail 'Нет SHA-256 сертификата в выводе apksigner'
    }
    $actualCertificate = $fingerprintMatch.Groups[1].Value.ToUpperInvariant()
    if ($expectedCertificateNormalized -ne $actualCertificate) {
        Fail 'Сертификат APK не совпадает с ожидаемым сертификатом выпуска'
    }
    Write-Output 'RELEASE_CERTIFICATE_MATCH=true'
    Write-Output 'APK_SIGNED_VERIFIED=true'
    Write-Output "CERTIFICATE_SHA256=$actualCertificate"
}
$hash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash
Write-Output "PACKAGE=$id"
Write-Output "VERSION_CODE=$code"
Write-Output "VERSION_NAME=$name"
Write-Output "APK_SHA256=$hash"
Write-Output "APK_FILE=$apk"
Write-Output 'RELEASE_PREFLIGHT=SUCCESS'

# apksigner intentionally returns a nonzero native exit code for unsigned APKs.
# That failure is expected and has already been checked above in Unsigned mode.
# Clear the process exit state AFTER all checks pass so the invoking PowerShell
# host (including GitHub Actions pwsh) does not report a false failure.
$global:LASTEXITCODE = 0
