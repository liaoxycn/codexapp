param(
    [string]$GatewayDir = (Join-Path $PSScriptRoot "..\desktop-gateway"),
    [string]$AppId = "com.codex.mobile",
    [string]$Activity = ".MainActivity",
    [string]$GatewayHost = "127.0.0.1",
    [string]$GatewayBindHost = "0.0.0.0",
    [int]$GatewayPort = 8765,
    [string]$GatewayPath = "/mobile",
    [string]$AvdName = "codexflow_api35",
    [int]$FinishDelaySeconds = 8,
    [switch]$SkipOpenApp
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$artifactsDir = Join-Path $root "artifacts"
$gatewayLog = Join-Path $artifactsDir "gateway-dev.log"
$gatewayErr = Join-Path $artifactsDir "gateway-dev.err.log"
$scriptLog = Join-Path $artifactsDir "dev-run.log"

function Write-Log {
    param([string]$Message)
    $line = "[{0}] {1}" -f (Get-Date -Format "HH:mm:ss"), $Message
    Write-Host $line
    Add-Content -Encoding utf8 -LiteralPath $scriptLog -Value $line
}

function Stop-ProcessByName {
    param([string[]]$Names)
    foreach ($name in $Names) {
        Get-Process -Name $name -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
    }
}

function Remove-AvdLocks {
    param([string]$AvdName)
    $avdDir = Join-Path $env:USERPROFILE ".android\avd\$AvdName.avd"
    if (-not (Test-Path -LiteralPath $avdDir)) {
        return
    }
    Get-ChildItem -LiteralPath $avdDir -Force -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like "*.lock" } |
        ForEach-Object {
            Remove-Item -LiteralPath $_.FullName -Recurse -Force -ErrorAction SilentlyContinue
        }
}

function Invoke-Checked {
    param(
        [string]$FilePath,
        [string[]]$Arguments,
        [string]$WorkingDirectory = (Get-Location).Path,
        [string]$DisplayName = $FilePath
    )
    $process = Start-Process -FilePath $FilePath -ArgumentList $Arguments -WorkingDirectory $WorkingDirectory -NoNewWindow -PassThru -Wait
    if ($process.ExitCode -ne 0) {
        throw "$DisplayName failed with exit code $($process.ExitCode)"
    }
}

function Get-SdkPath {
    $sdkRoot = $env:ANDROID_SDK_ROOT
    if ([string]::IsNullOrWhiteSpace($sdkRoot)) {
        $sdkRoot = Join-Path $env:LOCALAPPDATA "Android\Sdk"
    }
    return $sdkRoot
}

function Get-AvdBin {
    $sdkRoot = Get-SdkPath
    $emulatorExe = Join-Path $sdkRoot "emulator\emulator.exe"
    if (-not (Test-Path -LiteralPath $emulatorExe)) {
        throw "Emulator not found: $emulatorExe"
    }
    return $emulatorExe
}

function Get-PlatformToolsAdb {
    $sdkRoot = Get-SdkPath
    $adbExe = Join-Path $sdkRoot "platform-tools\adb.exe"
    if (-not (Test-Path -LiteralPath $adbExe)) {
        throw "ADB not found: $adbExe"
    }
    return $adbExe
}

function Wait-ForPort {
    param(
        [string]$Address,
        [int]$Port,
        [int]$TimeoutSeconds = 45
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $client = [System.Net.Sockets.TcpClient]::new()
            $async = $client.BeginConnect($Address, $Port, $null, $null)
            if ($async.AsyncWaitHandle.WaitOne(700)) {
                $client.EndConnect($async)
                $client.Close()
                return $true
            }
            $client.Close()
        } catch {
        }
        Start-Sleep -Milliseconds 600
    }
    return $false
}

function Ensure-EmulatorRunning {
    param(
        [string]$AvdName,
        [string]$AdbExe,
        [string]$EmulatorExe
    )
    $runningEmulator = & $AdbExe devices | Select-String '^emulator-\d+\s+device\b'

    if (-not $runningEmulator) {
        Write-Log "Starting emulator $AvdName"
        Start-Process -FilePath $EmulatorExe -ArgumentList @(
            "-avd", $AvdName,
            "-no-window",
            "-netdelay", "none",
            "-netspeed", "full"
        ) -WindowStyle Hidden | Out-Null
    }

    Write-Log "Waiting for emulator boot"
    & $AdbExe wait-for-device | Out-Null
    $deadline = (Get-Date).AddMinutes(4)
    while ((Get-Date) -lt $deadline) {
        $bootCompleted = & $AdbExe shell getprop sys.boot_completed 2>$null
        if ($bootCompleted.Trim() -eq "1") {
            return
        }
        Start-Sleep -Seconds 2
    }
    throw "Emulator boot timeout: $AvdName"
}

function Tail-LogIfExists {
    param([string]$Path, [int]$Lines = 80)
    if (Test-Path -LiteralPath $Path) {
        Write-Host ""
        Write-Host "---- $Path ----"
        Get-Content -Encoding utf8 -LiteralPath $Path -Tail $Lines | ForEach-Object { Write-Host $_ }
    }
}

function Test-PortOpen {
    param(
        [string]$Address,
        [int]$Port
    )
    try {
        $client = [System.Net.Sockets.TcpClient]::new()
        $async = $client.BeginConnect($Address, $Port, $null, $null)
        if ($async.AsyncWaitHandle.WaitOne(500)) {
            $client.EndConnect($async)
            $client.Close()
            return $true
        }
        $client.Close()
    } catch {
        return $false
    }
    return $false
}

function Get-DebugApkPath {
    param([string]$Root)
    $apkPath = Join-Path $Root "app\build\outputs\apk\debug\app-debug.apk"
    if (-not (Test-Path -LiteralPath $apkPath)) {
        throw "Debug APK not found: $apkPath"
    }
    return $apkPath
}

function Wait-ForPackageInstalled {
    param(
        [string]$AdbExe,
        [string]$PackageName,
        [int]$TimeoutSeconds = 20
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $packagePath = & $AdbExe shell pm path $PackageName 2>$null
        if ($packagePath -and $packagePath.Trim().StartsWith("package:")) {
            return $true
        }
        Start-Sleep -Milliseconds 600
    }
    return $false
}

function Wait-ForForegroundPackage {
    param(
        [string]$AdbExe,
        [string]$PackageName,
        [int]$TimeoutSeconds = 20
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $packagePattern = [regex]::Escape($PackageName)
    $foregroundPattern = "(topResumedActivity|mResumedActivity|ResumedActivity|mCurrentFocus|mFocusedApp).*$packagePattern"
    while ((Get-Date) -lt $deadline) {
        $activityDump = (& $AdbExe shell dumpsys activity activities 2>$null) | Out-String
        if ($activityDump -match $foregroundPattern) {
            return $true
        }
        Start-Sleep -Milliseconds 700
    }
    return $false
}

function Ensure-PackageInstalled {
    param(
        [string]$AdbExe,
        [string]$PackageName,
        [string]$ApkPath
    )
    if (Wait-ForPackageInstalled -AdbExe $AdbExe -PackageName $PackageName) {
        return
    }
    Write-Log "installDebug finished but package missing, fallback to adb install -r"
    Invoke-Checked -FilePath $AdbExe -Arguments @("install", "-r", $ApkPath) -DisplayName "adb install -r"
    if (-not (Wait-ForPackageInstalled -AdbExe $AdbExe -PackageName $PackageName -TimeoutSeconds 25)) {
        throw "package not installed after fallback: $PackageName"
    }
}

function Ensure-AppForeground {
    param(
        [string]$AdbExe,
        [string]$PackageName,
        [string]$ActivityName
    )
    Invoke-Checked -FilePath $AdbExe -Arguments @("shell", "am", "start", "-n", "$PackageName/$ActivityName") -DisplayName "adb start activity"
    if (Wait-ForForegroundPackage -AdbExe $AdbExe -PackageName $PackageName) {
        return
    }
    Write-Log "am start returned but app not foreground, fallback to monkey launcher"
    Invoke-Checked -FilePath $AdbExe -Arguments @("shell", "monkey", "-p", $PackageName, "-c", "android.intent.category.LAUNCHER", "1") -DisplayName "adb monkey launch"
    if (-not (Wait-ForForegroundPackage -AdbExe $AdbExe -PackageName $PackageName -TimeoutSeconds 25)) {
        throw "app did not reach foreground: $PackageName"
    }
}

function Main {
    New-Item -ItemType Directory -Path $artifactsDir -Force | Out-Null

    try {
        Write-Log "1/6 stop old processes"
        Stop-ProcessByName -Names @("node", "tsx", "desktop-gateway", "emulator", "qemu-system-x86_64", "adb")
        Remove-AvdLocks -AvdName $AvdName
        Start-Sleep -Milliseconds 800

        Set-Content -Encoding utf8 -LiteralPath $scriptLog -Value ""
        Set-Content -Encoding utf8 -LiteralPath $gatewayLog -Value ""
        Set-Content -Encoding utf8 -LiteralPath $gatewayErr -Value ""
        $adbExe = Get-PlatformToolsAdb
        $emulatorExe = Get-AvdBin
        $apkPath = Get-DebugApkPath -Root $root

        Write-Log "2/6 start emulator and wait"
        Ensure-EmulatorRunning -AvdName $AvdName -AdbExe $adbExe -EmulatorExe $emulatorExe

        Write-Log "3/6 start gateway dev"
        $gatewayRoot = Resolve-Path $GatewayDir
        $gatewayPathArg = $GatewayPath.Replace('"', '\"')
        $cmdLine = 'set "CODEX_MOBILE_GATEWAY_HOST=' + $GatewayBindHost.Trim() + '" && set "CODEX_MOBILE_GATEWAY_PORT=' + $GatewayPort + '" && set "CODEX_MOBILE_GATEWAY_PATH=' + $gatewayPathArg.Trim() + '" && npm run dev 1>> "' + $gatewayLog + '" 2>> "' + $gatewayErr + '"'
        Start-Process -FilePath "cmd.exe" -ArgumentList @("/c", $cmdLine) -WorkingDirectory $gatewayRoot -WindowStyle Hidden | Out-Null

        Write-Log "waiting for gateway on $GatewayHost`:$GatewayPort"
        if (-not (Wait-ForPort -Address $GatewayHost -Port $GatewayPort -TimeoutSeconds 50)) {
            throw "gateway not listening within 50 seconds: $GatewayHost`:$GatewayPort"
        }
        if (-not (Test-PortOpen -Address $GatewayHost -Port $GatewayPort)) {
            throw "gateway port check failed: $GatewayHost`:$GatewayPort"
        }

        Write-Log "4/6 build debug apk"
        Invoke-Checked -FilePath (Join-Path $root "gradlew.bat") -Arguments @(":app:preDebugBuild") -WorkingDirectory $root -DisplayName "assembleDebug"
        Invoke-Checked -FilePath (Join-Path $root "gradlew.bat") -Arguments @(":app:compileDebugKotlin") -WorkingDirectory $root -DisplayName "assembleDebug"
        Invoke-Checked -FilePath (Join-Path $root "gradlew.bat") -Arguments @(":app:assembleDebug") -WorkingDirectory $root -DisplayName "assembleDebug"

        Write-Log "5/6 install debug apk"
        Invoke-Checked -FilePath (Join-Path $root "gradlew.bat") -Arguments @(":app:installDebug") -WorkingDirectory $root -DisplayName "installDebug"
        Ensure-PackageInstalled -AdbExe $adbExe -PackageName $AppId -ApkPath $apkPath

        Write-Log "6/6 open app"
        if (-not $SkipOpenApp) {
            Ensure-AppForeground -AdbExe $adbExe -PackageName $AppId -ActivityName $Activity
        }

        Tail-LogIfExists -Path $gatewayLog -Lines 20
        Write-Log "all steps done, waiting before exit"
        Start-Sleep -Seconds $FinishDelaySeconds
        Write-Log "done"
    } catch {
        Write-Log "FAILED: $($_.Exception.Message)"
        Tail-LogIfExists -Path $gatewayLog
        Tail-LogIfExists -Path $gatewayErr
        Tail-LogIfExists -Path $scriptLog
        throw
    }
}

Main
