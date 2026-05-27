param(
    [string]$WebDavUrl = "https://dav.liaoxy.top",
    [string]$WebDavUser = "liaoxy",
    [string]$WebDavPassword = "REDACTED",
    [string]$RemotePath = "codex",
    [string]$OutputName = "CodexMobile.apk"
)

$ErrorActionPreference = "Stop"

& (Join-Path $PSScriptRoot "build-apk.ps1") -OutputName $OutputName
& (Join-Path $PSScriptRoot "upload-apk.ps1") -WebDavUrl $WebDavUrl -WebDavUser $WebDavUser -WebDavPassword $WebDavPassword -RemotePath $RemotePath -ApkPath (Join-Path (Split-Path -Parent $PSScriptRoot) "app\build\outputs\apk\release\$OutputName")
