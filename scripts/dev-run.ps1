param(
    [string]$GatewayDir = (Join-Path $PSScriptRoot "..\desktop-gateway"),
    [string]$AppId = "com.codex.mobile",
    [string]$Activity = ".MainActivity",
    [string]$GatewayHost = "127.0.0.1",
    [string]$GatewayBindHost = "0.0.0.0",
    [int]$GatewayPort = 8765,
    [string]$GatewayPath = "/mobile",
    [string]$AvdName = "codexflow_api35",
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
    Add-Content -LiteralPath $scriptLog -Value $line
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
        Write-Log "启动模拟器 $AvdName"
        Start-Process -FilePath $EmulatorExe -ArgumentList @(
            "-avd", $AvdName,
            "-netdelay", "none",
            "-netspeed", "full"
        ) -WindowStyle Hidden | Out-Null
    }

    Write-Log "等待模拟器启动"
    & $AdbExe wait-for-device | Out-Null
    $deadline = (Get-Date).AddMinutes(4)
    while ((Get-Date) -lt $deadline) {
        $bootCompleted = & $AdbExe shell getprop sys.boot_completed 2>$null
        if ($bootCompleted.Trim() -eq "1") {
            return
        }
        Start-Sleep -Seconds 2
    }
    throw "模拟器启动超时: $AvdName"
}

function Tail-LogIfExists {
    param([string]$Path, [int]$Lines = 80)
    if (Test-Path -LiteralPath $Path) {
        Write-Host ""
        Write-Host "---- $Path ----"
        Get-Content -LiteralPath $Path -Tail $Lines | ForEach-Object { Write-Host $_ }
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

New-Item -ItemType Directory -Path $artifactsDir -Force | Out-Null
Set-Content -LiteralPath $scriptLog -Value ""

try {
    Write-Log "1/5 停旧进程"
    Stop-ProcessByName -Names @("node", "tsx", "desktop-gateway", "emulator")

    $adbExe = Get-PlatformToolsAdb
    $emulatorExe = Get-AvdBin
    Write-Log "2/5 启动模拟器并等待就绪"
    Ensure-EmulatorRunning -AvdName $AvdName -AdbExe $adbExe -EmulatorExe $emulatorExe

    Write-Log "3/5 启动 gateway dev"
    $gatewayRoot = Resolve-Path $GatewayDir
    $gatewayPathArg = $GatewayPath.Replace('"', '\"')
    $envLine = 'set CODEX_MOBILE_GATEWAY_HOST=' + $GatewayBindHost + ' && set CODEX_MOBILE_GATEWAY_PORT=' + $GatewayPort + ' && set CODEX_MOBILE_GATEWAY_PATH=' + $gatewayPathArg + ' && npm run dev'
    $cmdLine = $envLine + ' 1>> "' + $gatewayLog + '" 2>> "' + $gatewayErr + '"'
    Start-Process -FilePath "cmd.exe" -ArgumentList @("/c", $cmdLine) -WorkingDirectory $gatewayRoot -WindowStyle Hidden | Out-Null

    Write-Log "等待 gateway 监听 $GatewayHost`:$GatewayPort"
    if (-not (Wait-ForPort -Address $GatewayHost -Port $GatewayPort -TimeoutSeconds 50)) {
        throw "gateway 未在 50 秒内监听 $GatewayHost`:$GatewayPort"
    }
    if (-not (Test-PortOpen -Address $GatewayHost -Port $GatewayPort)) {
        throw "gateway 端口检查失败: $GatewayHost`:$GatewayPort"
    }

    Write-Log "4/5 安装 debug 包"
    Invoke-Checked -FilePath (Join-Path $root "gradlew.bat") -Arguments @(":app:installDebug") -WorkingDirectory $root -DisplayName "installDebug"

    Write-Log "5/5 打开 app"
    if (-not $SkipOpenApp) {
        Invoke-Checked -FilePath $adbExe -Arguments @("shell", "am", "start", "-n", "$AppId/$Activity") -WorkingDirectory $root -DisplayName "adb start activity"
    }

    Tail-LogIfExists -Path $gatewayLog -Lines 20
    Write-Log "done"
}
catch {
    Write-Log "FAILED: $($_.Exception.Message)"
    Tail-LogIfExists -Path $gatewayLog
    Tail-LogIfExists -Path $gatewayErr
    Tail-LogIfExists -Path $scriptLog
    throw
}
