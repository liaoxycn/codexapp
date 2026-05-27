param(
    [string]$OutputName = "app-release-min-plus.apk"
)

$ErrorActionPreference = "Stop"

function Get-BuildToolsPath {
    $sdkRoot = $env:ANDROID_SDK_ROOT
    if ([string]::IsNullOrWhiteSpace($sdkRoot)) {
        $sdkRoot = Join-Path $env:LOCALAPPDATA "Android\Sdk"
    }
    $buildToolsDir = Join-Path $sdkRoot "build-tools\35.0.0"
    if (-not (Test-Path $buildToolsDir)) {
        throw "Android build-tools not found: $buildToolsDir"
    }
    return $buildToolsDir
}

function Invoke-Checked {
    param(
        [string]$FilePath,
        [string[]]$Arguments,
        [string]$WorkingDirectory = (Get-Location).Path
    )
    $process = Start-Process -FilePath $FilePath -ArgumentList $Arguments -WorkingDirectory $WorkingDirectory -NoNewWindow -PassThru -Wait
    if ($process.ExitCode -ne 0) {
        throw "$FilePath failed with exit code $($process.ExitCode)"
    }
}

$root = Split-Path -Parent $PSScriptRoot
$apkDir = Join-Path $root "app\build\outputs\apk\release"
$unsignedApk = Join-Path $apkDir "app-release-unsigned.apk"
$alignedApk = Join-Path $apkDir $OutputName
$debugKeystore = Join-Path $env:USERPROFILE ".android\debug.keystore"

Write-Host "1/3 build release APK"
Invoke-Checked -FilePath (Join-Path $root "gradlew.bat") -Arguments @(":app:assembleRelease") -WorkingDirectory $root

Write-Host "2/3 zipalign and sign"
$buildTools = Get-BuildToolsPath
$zipalign = Join-Path $buildTools "zipalign.exe"
$apksigner = Join-Path $buildTools "apksigner.bat"

if (Test-Path $alignedApk) {
    Remove-Item -LiteralPath $alignedApk -Force
}

Invoke-Checked -FilePath $zipalign -Arguments @("-f", "-p", "4", $unsignedApk, $alignedApk) -WorkingDirectory $root
Invoke-Checked -FilePath $apksigner -Arguments @(
    "sign",
    "--ks", $debugKeystore,
    "--ks-key-alias", "androiddebugkey",
    "--ks-pass", "pass:android",
    "--key-pass", "pass:android",
    $alignedApk
) -WorkingDirectory $root
Invoke-Checked -FilePath $apksigner -Arguments @("verify", "--verbose", $alignedApk) -WorkingDirectory $root

Write-Host "3/3 done"
Write-Host $alignedApk
