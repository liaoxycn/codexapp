param(
    [string]$OutputName = "CodexMobile.apk"
)

$ErrorActionPreference = "Stop"

function Get-BuildToolsPath {
    $sdkRoot = $env:ANDROID_SDK_ROOT
    if ([string]::IsNullOrWhiteSpace($sdkRoot)) {
        $sdkRoot = Join-Path $env:LOCALAPPDATA "Android\Sdk"
    }
    $buildToolsRoot = Join-Path $sdkRoot "build-tools"
    $preferred = Join-Path $buildToolsRoot "35.0.0"
    if (Test-Path $preferred) {
        return $preferred
    }
    if (-not (Test-Path $buildToolsRoot)) {
        throw "Android build-tools not found: $buildToolsRoot"
    }
    $fallback = Get-ChildItem -Path $buildToolsRoot -Directory |
        Where-Object { $_.Name -match '^\d+(\.\d+){1,2}$' } |
        Sort-Object { [version]$_.Name } -Descending |
        Select-Object -First 1
    if ($null -eq $fallback) {
        throw "Android build-tools not found under: $buildToolsRoot"
    }
    return $fallback.FullName
}

function Ensure-DebugKeystore {
    $keystoreDir = Join-Path $env:USERPROFILE ".android"
    if (-not (Test-Path $keystoreDir)) {
        New-Item -ItemType Directory -Path $keystoreDir | Out-Null
    }
    $debugKeystore = Join-Path $keystoreDir "debug.keystore"
    if (Test-Path $debugKeystore) {
        return $debugKeystore
    }

    $keytool = (Get-Command keytool -ErrorAction Stop).Source
    & $keytool `
        -genkeypair `
        -keystore $debugKeystore `
        -storetype JKS `
        -storepass android `
        -keypass android `
        -alias androiddebugkey `
        -keyalg RSA `
        -keysize 2048 `
        -validity 10000 `
        -dname "CN=Android Debug,O=Android,C=US" | Out-Null

    return $debugKeystore
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

Write-Host "1/3 build release APK"
Invoke-Checked -FilePath (Join-Path $root "gradlew.bat") -Arguments @(":app:assembleRelease") -WorkingDirectory $root

Write-Host "2/3 zipalign and sign"
$buildTools = Get-BuildToolsPath
$zipalign = Join-Path $buildTools "zipalign.exe"
$apksigner = Join-Path $buildTools "apksigner.bat"
$debugKeystore = Ensure-DebugKeystore

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
