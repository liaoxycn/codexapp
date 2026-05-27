param(
    [string]$WebDavUrl = $env:CODEX_WEBDAV_URL,
    [string]$WebDavUser = $env:CODEX_WEBDAV_USER,
    [string]$WebDavPassword = $env:CODEX_WEBDAV_PASSWORD,
    [string]$RemotePath = "codex",
    [string]$OutputName = "CodexMobile.apk"
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($WebDavUrl) -or [string]::IsNullOrWhiteSpace($WebDavUser) -or [string]::IsNullOrWhiteSpace($WebDavPassword)) {
    throw "Set WebDAV parameters with -WebDavUrl -WebDavUser -WebDavPassword or CODEX_WEBDAV_URL CODEX_WEBDAV_USER CODEX_WEBDAV_PASSWORD"
}

& (Join-Path $PSScriptRoot "build-apk.ps1") -OutputName $OutputName
& (Join-Path $PSScriptRoot "upload-apk.ps1") -WebDavUrl $WebDavUrl -WebDavUser $WebDavUser -WebDavPassword $WebDavPassword -RemotePath $RemotePath -ApkPath (Join-Path (Split-Path -Parent $PSScriptRoot) "app\build\outputs\apk\release\$OutputName")
