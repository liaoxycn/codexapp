param(
    [string]$WebDavUrl = $env:CODEX_WEBDAV_URL,
    [string]$WebDavUser = $env:CODEX_WEBDAV_USER,
    [string]$WebDavPassword = $env:CODEX_WEBDAV_PASSWORD,
    [string]$RemotePath = "codex",
    [string]$ApkPath = (Join-Path (Split-Path -Parent $PSScriptRoot) "app\build\outputs\apk\release\CodexMobile.apk")
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($WebDavUrl) -or [string]::IsNullOrWhiteSpace($WebDavUser) -or [string]::IsNullOrWhiteSpace($WebDavPassword)) {
    throw "Set WebDAV parameters with -WebDavUrl -WebDavUser -WebDavPassword or CODEX_WEBDAV_URL CODEX_WEBDAV_USER CODEX_WEBDAV_PASSWORD"
}

if (-not (Test-Path -LiteralPath $ApkPath)) {
    throw "APK not found: $ApkPath"
}

$null = Add-Type -AssemblyName System.Net.Http
$http = [System.Net.Http.HttpClient]::new()
$content = $null
try {
    $bytes = [System.Text.Encoding]::UTF8.GetBytes("$WebDavUser`:$WebDavPassword")
    $http.DefaultRequestHeaders.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new("Basic", [Convert]::ToBase64String($bytes))
    $content = [System.Net.Http.StreamContent]::new([System.IO.File]::OpenRead($ApkPath))
    $content.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse("application/vnd.android.package-archive")

    $remoteName = [System.Uri]::EscapeDataString((Split-Path -Leaf $ApkPath))
    if ([string]::IsNullOrWhiteSpace($RemotePath)) {
        $remoteUrl = ($WebDavUrl.TrimEnd("/") + "/" + $remoteName)
    } else {
        $remoteFolder = $RemotePath.Trim().Trim("/")
        $remoteUrl = ($WebDavUrl.TrimEnd("/") + "/" + ([System.Uri]::EscapeDataString($remoteFolder)) + "/" + $remoteName)
    }

    $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Put, $remoteUrl)
    $request.Content = $content
    $response = $http.SendAsync($request).GetAwaiter().GetResult()
    if (-not $response.IsSuccessStatusCode) {
        throw "WebDAV upload failed: $($response.StatusCode) $($response.ReasonPhrase)"
    }

    Write-Host "done"
    Write-Host $ApkPath
    Write-Host $remoteUrl
}
finally {
    if ($null -ne $content) {
        $content.Dispose()
    }
    $http.Dispose()
}
