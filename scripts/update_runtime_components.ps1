[CmdletBinding()]
param(
    [string] $VersionsJson = "",
    [string] $OutputDir = "",
    [string] $ReleaseTag = "",
    [string] $AppVersionName = "",
    [int] $AppVersionCode = 0,
    [switch] $DryRun,
    [switch] $CheckOnly,
    [switch] $RunGate,
    [ValidateSet("all", "host", "device", "android-tools", "agentserver", "loom", "agent")]
    [string] $GateSuite = "host",
    [string] $Device = "",
    [switch] $SkipGradle,
    [int] $GateGradleTimeoutMinutes = 15,
    [string] $ProxyUrl = ""
)

$ErrorActionPreference = "Stop"

$RepoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $OutputDir = Join-Path $RepoRoot ".tmp\runtime-update-$stamp"
} elseif (-not [System.IO.Path]::IsPathRooted($OutputDir)) {
    $OutputDir = Join-Path $RepoRoot $OutputDir
}
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

if ([string]::IsNullOrWhiteSpace($ProxyUrl)) {
    if (-not [string]::IsNullOrWhiteSpace($env:HTTPS_PROXY)) {
        $ProxyUrl = $env:HTTPS_PROXY
    } elseif (-not [string]::IsNullOrWhiteSpace($env:HTTP_PROXY)) {
        $ProxyUrl = $env:HTTP_PROXY
    }
}
if (-not [string]::IsNullOrWhiteSpace($ProxyUrl)) {
    $env:HTTPS_PROXY = $ProxyUrl
    $env:HTTP_PROXY = $ProxyUrl
}

$Targets = [ordered]@{
    runtimeManifest = "app/src/main/assets/runtime-versions.json"
    runtimeVersionsJava = "app/src/main/java/com/portalagent/setup/RuntimeVersions.java"
    agentServerAsset = "app/src/main/assets/agentserver-linux-arm64.tgz"
    loomAsset = "app/src/main/assets/loom-linux-arm64.tgz"
    releaseNotes = "release/release-notes.md"
    releaseManifest = "docs/release/runtime-versions.json"
    gradleBuild = "app/build.gradle"
}

function Resolve-RepoPath {
    param([Parameter(Mandatory = $true)][string] $RelativePath)
    return [System.IO.Path]::GetFullPath((Join-Path $RepoRoot $RelativePath))
}

function Write-Utf8NoBom {
    param(
        [Parameter(Mandatory = $true)][string] $Path,
        [Parameter(Mandatory = $true)][string] $Content
    )
    $parent = Split-Path -Parent $Path
    if ($parent) {
        New-Item -ItemType Directory -Force -Path $parent | Out-Null
    }
    [System.IO.File]::WriteAllText($Path, $Content, [System.Text.UTF8Encoding]::new($false))
}

function Invoke-JsonRequest {
    param([Parameter(Mandatory = $true)][string] $Uri)

    $params = @{
        Uri = $Uri
        Headers = @{ "User-Agent" = "PortalAgentRuntimeUpdater" }
    }
    if (-not [string]::IsNullOrWhiteSpace($ProxyUrl)) {
        $params.Proxy = $ProxyUrl
    }
    return Invoke-RestMethod @params
}

function Invoke-Download {
    param(
        [Parameter(Mandatory = $true)][string] $Uri,
        [Parameter(Mandatory = $true)][string] $Destination
    )

    $parent = Split-Path -Parent $Destination
    if ($parent) {
        New-Item -ItemType Directory -Force -Path $parent | Out-Null
    }
    $part = "$Destination.part"
    if (Test-Path -LiteralPath $part) {
        Remove-Item -LiteralPath $part -Force
    }
    $params = @{
        Uri = $Uri
        OutFile = $part
        Headers = @{ "User-Agent" = "PortalAgentRuntimeUpdater" }
    }
    if (-not [string]::IsNullOrWhiteSpace($ProxyUrl)) {
        $params.Proxy = $ProxyUrl
    }
    Invoke-WebRequest @params
    if (-not (Test-Path -LiteralPath $part) -or (Get-Item -LiteralPath $part).Length -le 0) {
        throw "Downloaded asset is empty: $Uri"
    }
    Move-Item -LiteralPath $part -Destination $Destination -Force
}

function Assert-SafeTarEntries {
    param([Parameter(Mandatory = $true)][string] $TarPath)

    $entries = tar -tzf $TarPath
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to list tar entries for $TarPath"
    }
    foreach ($entry in $entries) {
        $normalized = $entry.Replace("\", "/")
        if ($normalized.StartsWith("/") -or $normalized -match "^[A-Za-z]:/" -or
                $normalized -eq ".." -or $normalized.StartsWith("../") -or
                $normalized.Contains("/../")) {
            throw "Unsafe tar entry in $TarPath`: $entry"
        }
    }
}

function Get-CurrentAppVersion {
    $gradle = Resolve-RepoPath $Targets.gradleBuild
    $content = Get-Content -Encoding UTF8 -Raw -LiteralPath $gradle
    $name = [regex]::Match($content, 'versionName\s+"([^"]+)"').Groups[1].Value
    $codeText = [regex]::Match($content, 'versionCode\s+(\d+)').Groups[1].Value
    return [pscustomobject]@{
        Name = $name
        Code = [int]$codeText
    }
}

function Get-LatestVersions {
    $codex = Invoke-JsonRequest -Uri "https://registry.npmjs.org/@openai%2fcodex"
    $claude = Invoke-JsonRequest -Uri "https://registry.npmjs.org/@anthropic-ai%2fclaude-code"
    $agentServer = Invoke-JsonRequest -Uri "https://api.github.com/repos/agentserver/agentserver/releases/latest"
    $loom = Invoke-JsonRequest -Uri "https://api.github.com/repos/agentserver/loom/releases/latest"

    $agentAsset = @($agentServer.assets | Where-Object { $_.name -eq "agentserver-linux-arm64.tar.gz" } | Select-Object -First 1)
    $agentUrl = if ($agentAsset) {
        $agentAsset.browser_download_url
    } else {
        "https://github.com/agentserver/agentserver/releases/download/$($agentServer.tag_name)/agentserver-linux-arm64.tar.gz"
    }

    return [ordered]@{
        components = [ordered]@{
            codex = [ordered]@{
                version = $codex.'dist-tags'.latest
                package = "@openai/codex"
                source = "https://registry.npmjs.org/@openai%2fcodex"
            }
            claude = [ordered]@{
                version = $claude.'dist-tags'.latest
                package = "@anthropic-ai/claude-code"
                source = "https://registry.npmjs.org/@anthropic-ai%2fclaude-code"
            }
            agentserver = [ordered]@{
                version = $agentServer.tag_name
                repo = "agentserver/agentserver"
                source = "https://github.com/agentserver/agentserver"
                arm64Asset = "agentserver-linux-arm64.tar.gz"
                arm64DownloadUrl = $agentUrl
            }
            loom = [ordered]@{
                version = $loom.tag_name
                repo = "agentserver/loom"
                source = "https://github.com/agentserver/loom"
            }
        }
    }
}

function ConvertTo-OrderedRuntimeVersions {
    param([Parameter(Mandatory = $true)] $InputObject)

    $c = $InputObject.components
    $agentVersion = [string]$c.agentserver.version
    $agentUrl = [string]$c.agentserver.arm64DownloadUrl
    if ([string]::IsNullOrWhiteSpace($agentUrl)) {
        $agentUrl = "https://github.com/agentserver/agentserver/releases/download/$agentVersion/agentserver-linux-arm64.tar.gz"
    }
    return [ordered]@{
        components = [ordered]@{
            codex = [ordered]@{
                version = [string]$c.codex.version
                package = "@openai/codex"
                source = "https://registry.npmjs.org/@openai%2fcodex"
            }
            claude = [ordered]@{
                version = [string]$c.claude.version
                package = "@anthropic-ai/claude-code"
                source = "https://registry.npmjs.org/@anthropic-ai%2fclaude-code"
            }
            agentserver = [ordered]@{
                version = $agentVersion
                repo = "agentserver/agentserver"
                source = "https://github.com/agentserver/agentserver"
                arm64Asset = "agentserver-linux-arm64.tar.gz"
                arm64DownloadUrl = $agentUrl
            }
            loom = [ordered]@{
                version = [string]$c.loom.version
                repo = "agentserver/loom"
                source = "https://github.com/agentserver/loom"
            }
        }
    }
}

function New-RuntimeManifest {
    param(
        [Parameter(Mandatory = $true)] $Versions,
        [Parameter(Mandatory = $true)] [string] $GeneratedAt
    )
    return [ordered]@{
        generatedAt = $GeneratedAt
        components = $Versions.components
    }
}

function New-Plan {
    param(
        [Parameter(Mandatory = $true)] $Versions,
        [Parameter(Mandatory = $true)] [string] $GeneratedAt
    )
    return [ordered]@{
        generatedAt = $GeneratedAt
        mode = $(if ($DryRun) { "dry-run" } elseif ($CheckOnly) { "check-only" } else { "apply" })
        release = [ordered]@{
            tag = $ReleaseTag
            appVersionName = $AppVersionName
            appVersionCode = $AppVersionCode
        }
        components = $Versions.components
        targets = $Targets
    }
}

function New-RuntimeVersionsJava {
    param([Parameter(Mandatory = $true)] $Versions)

    $codex = $Versions.components.codex.version
    $claude = $Versions.components.claude.version
    $agent = $Versions.components.agentserver.version
    $loom = $Versions.components.loom.version
    return @"
package com.portalagent.setup;

public final class RuntimeVersions {

    public static final String CODEX_VERSION = "$codex";
    public static final String CLAUDE_CODE_VERSION = "$claude";
    public static final String AGENTSERVER_VERSION = "$agent";
    public static final String LOOM_VERSION = "$loom";

    public static final String CODEX_NPM_SPEC = "@openai/codex@" + CODEX_VERSION;
    public static final String CLAUDE_CODE_NPM_SPEC =
        "@anthropic-ai/claude-code@" + CLAUDE_CODE_VERSION;

    private RuntimeVersions() {}
}
"@
}

function New-ReleaseNotes {
    param(
        [Parameter(Mandatory = $true)] $Versions,
        [string] $GateReport = ""
    )

    $codex = $Versions.components.codex.version
    $claude = $Versions.components.claude.version
    $agent = $Versions.components.agentserver.version
    $loom = $Versions.components.loom.version
    $gateLine = if ([string]::IsNullOrWhiteSpace($GateReport)) {
        'Release gate: run `scripts/run_release_gate.ps1` before publishing, or use `-RunGate`.'
    } else {
        "Release gate: $GateReport"
    }
    return @"
# PortalAgent $ReleaseTag

- App: $AppVersionName (versionCode $AppVersionCode)
- Codex: $codex (``@openai/codex@$codex``)
- Claude Code: $claude (``@anthropic-ai/claude-code@$claude``)
- AgentServer: $agent
- Loom: $loom
- $gateLine

Install the universal APK attached below. First-run setup installs the pinned Codex and Claude CLI versions listed above and deploys the bundled AgentServer and Loom ARM64 archives.
"@
}

function Update-AppVersion {
    if ([string]::IsNullOrWhiteSpace($AppVersionName) -and $AppVersionCode -le 0) {
        return
    }
    $path = Resolve-RepoPath $Targets.gradleBuild
    $content = Get-Content -Encoding UTF8 -Raw -LiteralPath $path
    if (-not [string]::IsNullOrWhiteSpace($AppVersionName)) {
        $content = [regex]::Replace($content, '(?m)^(\s*)versionName\s+"[^"]+"', "`${1}versionName `"$AppVersionName`"")
    }
    if ($AppVersionCode -gt 0) {
        $content = [regex]::Replace($content, '(?m)^(\s*)versionCode\s+\d+', "`${1}versionCode $AppVersionCode")
    }
    Write-Utf8NoBom -Path $path -Content $content
}

function Update-AgentServerAsset {
    param([Parameter(Mandatory = $true)] $Versions)

    $url = $Versions.components.agentserver.arm64DownloadUrl
    $download = Join-Path $OutputDir "agentserver-linux-arm64.tar.gz"
    Invoke-Download -Uri $url -Destination $download
    Assert-SafeTarEntries -TarPath $download
    Move-Item -LiteralPath $download -Destination (Resolve-RepoPath $Targets.agentServerAsset) -Force
}

function Update-LoomAsset {
    param([Parameter(Mandatory = $true)] $Versions)

    $script = Resolve-RepoPath "scripts/prepare_loom_addon.ps1"
    $args = @(
        "-NoProfile",
        "-ExecutionPolicy", "Bypass",
        "-File", $script,
        "-Version", $Versions.components.loom.version,
        "-OutFile", $Targets.loomAsset,
        "-WorkDir", "build/loom-addon"
    )
    & powershell.exe @args
    if ($LASTEXITCODE -ne 0) {
        throw "prepare_loom_addon.ps1 failed with exit code $LASTEXITCODE"
    }
}

function Invoke-ReleaseGate {
    $gateOut = Join-Path $OutputDir "release-gate"
    $script = Resolve-RepoPath "scripts/run_release_gate.ps1"
    $args = @(
        "-NoProfile",
        "-ExecutionPolicy", "Bypass",
        "-File", $script,
        "-Suite", $GateSuite,
        "-OutputDir", $gateOut,
        "-GradleTimeoutMinutes", $GateGradleTimeoutMinutes
    )
    if (-not [string]::IsNullOrWhiteSpace($Device)) {
        $args += @("-Device", $Device)
    }
    if ($SkipGradle) {
        $args += "-SkipGradle"
    }
    & powershell.exe @args
    if ($LASTEXITCODE -ne 0) {
        throw "run_release_gate.ps1 failed with exit code $LASTEXITCODE"
    }
    return (Join-Path $gateOut "release-test-summary.md")
}

$currentVersion = Get-CurrentAppVersion
if ([string]::IsNullOrWhiteSpace($AppVersionName)) {
    $AppVersionName = $currentVersion.Name
}
if ($AppVersionCode -le 0) {
    $AppVersionCode = $currentVersion.Code
}
if ([string]::IsNullOrWhiteSpace($ReleaseTag)) {
    $ReleaseTag = "v$AppVersionName"
}

$rawVersions = if (-not [string]::IsNullOrWhiteSpace($VersionsJson)) {
    Get-Content -Encoding UTF8 -Raw -LiteralPath $VersionsJson | ConvertFrom-Json
} else {
    Get-LatestVersions
}
$versions = ConvertTo-OrderedRuntimeVersions -InputObject $rawVersions
$generatedAt = (Get-Date).ToUniversalTime().ToString("o")

$plan = New-Plan -Versions $versions -GeneratedAt $generatedAt
$planPath = Join-Path $OutputDir "runtime-components-plan.json"
Write-Utf8NoBom -Path $planPath -Content (($plan | ConvertTo-Json -Depth 10))

$notes = New-ReleaseNotes -Versions $versions
$notesPath = Join-Path $OutputDir "release-notes.md"
Write-Utf8NoBom -Path $notesPath -Content $notes

if ($DryRun -or $CheckOnly) {
    Write-Output "Plan: $planPath"
    Write-Output "Release notes: $notesPath"
    exit 0
}

Update-AgentServerAsset -Versions $versions
Update-LoomAsset -Versions $versions

$manifest = New-RuntimeManifest -Versions $versions -GeneratedAt $generatedAt
$manifestJson = $manifest | ConvertTo-Json -Depth 10
Write-Utf8NoBom -Path (Resolve-RepoPath $Targets.runtimeManifest) -Content $manifestJson
Write-Utf8NoBom -Path (Resolve-RepoPath $Targets.releaseManifest) -Content $manifestJson
Write-Utf8NoBom -Path (Resolve-RepoPath $Targets.runtimeVersionsJava) -Content (New-RuntimeVersionsJava -Versions $versions)
Update-AppVersion

$gateReport = ""
if ($RunGate) {
    $gateReport = Invoke-ReleaseGate
}
$finalNotes = New-ReleaseNotes -Versions $versions -GateReport $gateReport
Write-Utf8NoBom -Path (Resolve-RepoPath $Targets.releaseNotes) -Content $finalNotes
Write-Utf8NoBom -Path $notesPath -Content $finalNotes

Write-Output "Updated runtime manifest: $(Resolve-RepoPath $Targets.runtimeManifest)"
Write-Output "Updated RuntimeVersions.java: $(Resolve-RepoPath $Targets.runtimeVersionsJava)"
Write-Output "Release notes: $(Resolve-RepoPath $Targets.releaseNotes)"
if ($RunGate) {
    Write-Output "Release gate summary: $gateReport"
}
