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
    [int]$BuildTimeoutMinutes = 15,
    [int]$EmulatorTimeoutMinutes = 4,
    [switch]$SkipOpenApp
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$artifactsDir = Join-Path $root "artifacts"
$gatewayLog = Join-Path $artifactsDir "gateway-dev.log"
$gatewayErr = Join-Path $artifactsDir "gateway-dev.err.log"
$scriptLog = Join-Path $artifactsDir "dev-run.log"
$buildLog = Join-Path $artifactsDir "apk-build.log"
$buildErr = Join-Path $artifactsDir "apk-build.err.log"
$debugApk = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"

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

function Start-LoggedProcess {
    param(
        [string]$FilePath,
        [string[]]$Arguments,
        [string]$WorkingDirectory,
        [string]$StdOutPath,
        [string]$StdErrPath
    )
    Set-Content -Encoding utf8 -LiteralPath $StdOutPath -Value ""
    Set-Content -Encoding utf8 -LiteralPath $StdErrPath -Value ""
    return Start-Process `
        -FilePath $FilePath `
        -ArgumentList $Arguments `
        -WorkingDirectory $WorkingDirectory `
        -RedirectStandardOutput $StdOutPath `
        -RedirectStandardError $StdErrPath `
        -WindowStyle Hidden `
        -PassThru
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

function Get-RunningEmulatorSerial {
    param([string]$AdbExe)
    $devices = & $AdbExe devices 2>$null
    foreach ($line in $devices) {
        if ($line -match '^(emulator-\d+)\s+device\b') {
            return $Matches[1]
        }
    }
    return $null
}

function Get-AnyEmulatorSerial {
    param([string]$AdbExe)
    $devices = & $AdbExe devices 2>$null
    foreach ($line in $devices) {
        if ($line -match '^(emulator-\d+)\s+') {
            return $Matches[1]
        }
    }
    return $null
}

function Start-EmulatorIfNeeded {
    param(
        [string]$AvdName,
        [string]$AdbExe,
        [string]$EmulatorExe
    )
    $emulator = Get-AnyEmulatorSerial -AdbExe $AdbExe
    if ($emulator) {
        Write-Log "Emulator already running: $emulator"
        return
    }

    Write-Log "Starting emulator $AvdName"
    Start-Process -FilePath $EmulatorExe -ArgumentList @(
        "-avd", $AvdName,
        "-netdelay", "none",
        "-netspeed", "full"
    ) -WindowStyle Hidden | Out-Null
}

function Test-EmulatorBooted {
    param([string]$AdbExe)
    $serial = Get-RunningEmulatorSerial -AdbExe $AdbExe
    if (-not $serial) {
        return $false
    }

    $bootCompleted = & $AdbExe -s $serial shell getprop sys.boot_completed 2>$null
    return ($bootCompleted.Trim() -eq "1")
}

function Stop-ProcessTree {
    param([System.Diagnostics.Process]$Process)
    if ($null -eq $Process -or $Process.HasExited) {
        return
    }
    & taskkill.exe /PID $Process.Id /T /F | Out-Null
}

function Start-DebugApkBuild {
    param([string]$Root)
    Write-Log "Starting debug APK build"
    return Start-LoggedProcess `
        -FilePath (Join-Path $Root "gradlew.bat") `
        -Arguments @(":app:assembleDebug", "--console=plain") `
        -WorkingDirectory $Root `
        -StdOutPath $buildLog `
        -StdErrPath $buildErr
}

function Wait-ForBuildAndEmulator {
    param(
        [System.Diagnostics.Process]$BuildProcess,
        [string]$AdbExe,
        [int]$BuildTimeoutMinutes,
        [int]$EmulatorTimeoutMinutes
    )
    $buildDone = $false
    $emulatorDone = $false
    $buildDeadline = (Get-Date).AddMinutes($BuildTimeoutMinutes)
    $emulatorDeadline = (Get-Date).AddMinutes($EmulatorTimeoutMinutes)

    while (-not ($buildDone -and $emulatorDone)) {
        if (-not $buildDone) {
            if ($BuildProcess.HasExited) {
                if ($BuildProcess.ExitCode -ne 0) {
                    throw "debug APK build failed with exit code $($BuildProcess.ExitCode)"
                }
                $buildDone = $true
                Write-Log "Debug APK build done"
            } elseif ((Get-Date) -gt $buildDeadline) {
                Stop-ProcessTree -Process $BuildProcess
                throw "debug APK build timeout after $BuildTimeoutMinutes minutes"
            }
        }

        if (-not $emulatorDone) {
            if (Test-EmulatorBooted -AdbExe $AdbExe) {
                $emulatorDone = $true
                Write-Log "Emulator boot done"
            } elseif ((Get-Date) -gt $emulatorDeadline) {
                Stop-ProcessTree -Process $BuildProcess
                throw "Emulator boot timeout after $EmulatorTimeoutMinutes minutes"
            }
        }

        if (-not ($buildDone -and $emulatorDone)) {
            Start-Sleep -Seconds 2
        }
    }
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

function Main {
    New-Item -ItemType Directory -Path $artifactsDir -Force | Out-Null

    try {
        Set-Content -Encoding utf8 -LiteralPath $scriptLog -Value ""

        $adbExe = Get-PlatformToolsAdb
        $emulatorExe = Get-AvdBin

        Write-Log "1/4 start emulator and build APK in parallel"
        Start-EmulatorIfNeeded -AvdName $AvdName -AdbExe $adbExe -EmulatorExe $emulatorExe
        $buildProcess = Start-DebugApkBuild -Root $root
        Wait-ForBuildAndEmulator `
            -BuildProcess $buildProcess `
            -AdbExe $adbExe `
            -BuildTimeoutMinutes $BuildTimeoutMinutes `
            -EmulatorTimeoutMinutes $EmulatorTimeoutMinutes

        Write-Log "2/4 restart gateway dev"
        Stop-ProcessByName -Names @("node", "tsx", "desktop-gateway")
        Start-Sleep -Milliseconds 800
        Set-Content -Encoding utf8 -LiteralPath $gatewayLog -Value ""
        Set-Content -Encoding utf8 -LiteralPath $gatewayErr -Value ""

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

        Write-Log "3/4 install debug apk"
        if (-not (Test-Path -LiteralPath $debugApk)) {
            throw "debug APK not found after build: $debugApk"
        }
        $deviceSerial = Get-RunningEmulatorSerial -AdbExe $adbExe
        if (-not $deviceSerial) {
            throw "no booted emulator device found for install"
        }
        Invoke-Checked -FilePath $adbExe -Arguments @("-s", $deviceSerial, "install", "-r", $debugApk) -WorkingDirectory $root -DisplayName "adb install"

        Write-Log "4/4 open app"
        if (-not $SkipOpenApp) {
            Invoke-Checked -FilePath $adbExe -Arguments @("-s", $deviceSerial, "shell", "am", "start", "-n", "$AppId/$Activity") -WorkingDirectory $root -DisplayName "adb start activity"
        }

        Tail-LogIfExists -Path $gatewayLog -Lines 20
        Write-Log "all steps done, waiting before exit"
        Start-Sleep -Seconds $FinishDelaySeconds
        Write-Log "done"
    } catch {
        Write-Log "FAILED: $($_.Exception.Message)"
        Tail-LogIfExists -Path $gatewayLog
        Tail-LogIfExists -Path $gatewayErr
        Tail-LogIfExists -Path $buildLog
        Tail-LogIfExists -Path $buildErr
        Tail-LogIfExists -Path $scriptLog
        throw
    }
}

Main
