[CmdletBinding()]
param(
    [ValidateSet("all", "host", "device", "android-tools", "agentserver", "loom", "agent")]
    [string] $Suite = "host",

    [string] $Device = "",
    [string] $ApkPath = "",
    [string] $OutputDir = "",

    [switch] $DryRun,
    [switch] $ListSuites,
    [switch] $SkipGradle,

    [int] $GradleTimeoutMinutes = 15,
    [int] $AdbTimeoutSeconds = 60
)

$ErrorActionPreference = "Stop"

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $OutputDir = Join-Path $RepoRoot "release-test-artifacts\$stamp"
} elseif (-not [System.IO.Path]::IsPathRooted($OutputDir)) {
    $OutputDir = Join-Path $RepoRoot $OutputDir
}

$Suites = [ordered]@{
    "host" = "Gradle unit tests, APK build, local artifacts, documentation sanity"
    "device" = "ADB install, launch, version, screenshot, logcat crash scan"
    "android-tools" = "Android MCP/local device tool smoke checks"
    "agentserver" = "AgentServer command tests and on-device runtime probes"
    "loom" = "Loom command tests and on-device runtime probes"
    "agent" = "Codex/Claude provider, session, and on-device runtime probes"
}

if ($ListSuites) {
    foreach ($name in $Suites.Keys) {
        Write-Output ("{0}`t{1}" -f $name, $Suites[$name])
    }
    exit 0
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$EvidenceDir = Join-Path $OutputDir "artifacts"
New-Item -ItemType Directory -Force -Path $EvidenceDir | Out-Null

$Results = New-Object System.Collections.Generic.List[object]
$Script:ResolvedDevice = $Device
$Script:ResolvedApk = $ApkPath

function New-Check {
    param(
        [string] $Id,
        [string] $Suite,
        [string] $Description,
        [string] $Level,
        [scriptblock] $Run
    )
    [pscustomobject]@{
        Id = $Id
        Suite = $Suite
        Description = $Description
        Level = $Level
        Run = $Run
    }
}

function New-CheckResult {
    param(
        [string] $Id,
        [string] $Suite,
        [string] $Description,
        [string] $Level,
        [string] $Status,
        [int] $DurationMs,
        [string] $EvidencePath = "",
        [string] $FailureReason = ""
    )
    [pscustomobject]@{
        id = $Id
        suite = $Suite
        description = $Description
        level = $Level
        status = $Status
        durationMs = $DurationMs
        evidencePath = $EvidencePath
        failureReason = $FailureReason
    }
}

function Write-TextFile {
    param(
        [string] $Path,
        [string] $Content
    )
    $parent = Split-Path -Parent $Path
    if ($parent) {
        New-Item -ItemType Directory -Force -Path $parent | Out-Null
    }
    [System.IO.File]::WriteAllText($Path, $Content, [System.Text.UTF8Encoding]::new($false))
}

function Invoke-ExternalCommand {
    param(
        [string] $FilePath,
        [string[]] $Arguments = @(),
        [int] $TimeoutSeconds = 300,
        [string] $EvidenceName = ""
    )
    if ([string]::IsNullOrWhiteSpace($EvidenceName)) {
        $EvidenceName = "command-" + ([Guid]::NewGuid().ToString("N")) + ".txt"
    }
    $outFile = Join-Path $EvidenceDir ($EvidenceName + ".out")
    $errFile = Join-Path $EvidenceDir ($EvidenceName + ".err")
    $combinedFile = Join-Path $EvidenceDir ($EvidenceName + ".txt")

    $psi = New-Object System.Diagnostics.ProcessStartInfo
    if ($FilePath.EndsWith(".bat", [System.StringComparison]::OrdinalIgnoreCase) -or
        $FilePath.EndsWith(".cmd", [System.StringComparison]::OrdinalIgnoreCase)) {
        $psi.FileName = "$env:ComSpec"
        $cmdLine = '"' + $FilePath + '" ' + (($Arguments | ForEach-Object { ConvertTo-CommandLineArgument $_ }) -join " ")
        $psi.Arguments = "/c $cmdLine"
    } else {
        $psi.FileName = $FilePath
        $psi.Arguments = (($Arguments | ForEach-Object { ConvertTo-CommandLineArgument $_ }) -join " ")
    }
    $psi.WorkingDirectory = $RepoRoot
    $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.CreateNoWindow = $true

    $seenEnv = New-Object "System.Collections.Generic.HashSet[string]" ([System.StringComparer]::OrdinalIgnoreCase)
    foreach ($key in @($psi.EnvironmentVariables.Keys)) {
        if (-not $seenEnv.Add([string]$key)) {
            $psi.EnvironmentVariables.Remove($key)
        }
    }
    if ([System.Environment]::OSVersion.Platform -eq [System.PlatformID]::Win32NT -and
        -not $psi.EnvironmentVariables.ContainsKey("HOST_OS")) {
        $psi.EnvironmentVariables["HOST_OS"] = "windows"
    }

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $psi
    [void]$process.Start()
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()

    $completed = $process.WaitForExit($TimeoutSeconds * 1000)
    if (-not $completed) {
        try { $process.Kill() } catch {}
        $stdout = $stdoutTask.Result
        $stderr = $stderrTask.Result
        Write-TextFile $combinedFile ("COMMAND TIMED OUT AFTER $TimeoutSeconds seconds`n`nSTDOUT`n$stdout`nSTDERR`n$stderr")
        return [pscustomobject]@{
            ExitCode = 124
            Output = $stdout + "`n" + $stderr
            EvidencePath = $combinedFile
        }
    }

    $stdout = $stdoutTask.Result
    $stderr = $stderrTask.Result
    Write-TextFile $outFile $stdout
    Write-TextFile $errFile $stderr
    Write-TextFile $combinedFile ("COMMAND: $FilePath $($Arguments -join ' ')`nEXIT: $($process.ExitCode)`n`nSTDOUT`n$stdout`nSTDERR`n$stderr")

    [pscustomobject]@{
        ExitCode = $process.ExitCode
        Output = $stdout + "`n" + $stderr
        EvidencePath = $combinedFile
    }
}

function ConvertTo-CommandLineArgument {
    param([string] $Value)
    if ($null -eq $Value) { return '""' }
    if ($Value -notmatch '[\s"]') { return $Value }
    return '"' + ($Value -replace '\\(?=\\*")', '$&' -replace '"', '\"') + '"'
}

function ConvertTo-ShellSingleQuotedArgument {
    param([string] $Value)
    if ($null -eq $Value) { return "''" }
    $singleQuoteEscape = "'" + '"' + "'" + '"' + "'"
    return "'" + $Value.Replace("'", $singleQuoteEscape) + "'"
}

function Get-AdbPath {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($null -eq $cmd) { return "" }
    return $cmd.Source
}

function Invoke-Adb {
    param(
        [string[]] $Arguments,
        [string] $EvidenceName,
        [int] $TimeoutSeconds = $AdbTimeoutSeconds
    )
    $adb = Get-AdbPath
    if ([string]::IsNullOrWhiteSpace($adb)) {
        $reason = if ([string]::IsNullOrWhiteSpace($Device)) { "adb not found" } else { "adb not found; cannot locate device $Device" }
        return [pscustomobject]@{
            ExitCode = 127
            Output = $reason
            EvidencePath = ""
        }
    }
    Invoke-ExternalCommand -FilePath $adb -Arguments $Arguments -TimeoutSeconds $TimeoutSeconds -EvidenceName $EvidenceName
}

function Get-AdbDeviceArgument {
    if ([string]::IsNullOrWhiteSpace($Script:ResolvedDevice)) { return @() }
    return @("-s", $Script:ResolvedDevice)
}

function Resolve-Device {
    if (-not [string]::IsNullOrWhiteSpace($Script:ResolvedDevice)) {
        return $Script:ResolvedDevice
    }
    $res = Invoke-Adb -Arguments @("devices") -EvidenceName "device-adb-devices"
    if ($res.ExitCode -ne 0) {
        throw $res.Output
    }
    $line = ($res.Output -split "`r?`n" | Where-Object { $_ -match "^\S+\s+device$" } | Select-Object -First 1)
    if ([string]::IsNullOrWhiteSpace($line)) {
        throw "No online ADB device found"
    }
    $Script:ResolvedDevice = ($line -split "\s+")[0]
    return $Script:ResolvedDevice
}

function Resolve-Apk {
    if (-not [string]::IsNullOrWhiteSpace($Script:ResolvedApk)) {
        if (-not [System.IO.Path]::IsPathRooted($Script:ResolvedApk)) {
            $Script:ResolvedApk = Join-Path $RepoRoot $Script:ResolvedApk
        }
        return $Script:ResolvedApk
    }

    $candidates = @()
    $buildDir = Join-Path $RepoRoot "app\build\outputs\apk\debug"
    $releaseDir = Join-Path $RepoRoot "release"
    if (Test-Path -LiteralPath $buildDir) {
        $candidates += Get-ChildItem -LiteralPath $buildDir -Filter "*universal*.apk" -ErrorAction SilentlyContinue
    }
    if (Test-Path -LiteralPath $releaseDir) {
        $candidates += Get-ChildItem -LiteralPath $releaseDir -Filter "*.apk" -ErrorAction SilentlyContinue
    }
    $selected = $candidates | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($null -eq $selected) {
        throw "No APK found. Build first or pass -ApkPath."
    }
    $Script:ResolvedApk = $selected.FullName
    return $Script:ResolvedApk
}

function Invoke-Gradle {
    param(
        [string[]] $Arguments,
        [string] $EvidenceName
    )
    if ($SkipGradle) {
        return [pscustomobject]@{
            ExitCode = 0
            Output = "Skipped by -SkipGradle"
            EvidencePath = ""
        }
    }
    $gradle = Join-Path $RepoRoot "gradlew.bat"
    Invoke-ExternalCommand -FilePath $gradle -Arguments $Arguments -TimeoutSeconds ($GradleTimeoutMinutes * 60) -EvidenceName $EvidenceName
}

function Assert-ExitZero {
    param(
        [object] $CommandResult,
        [string] $Message
    )
    if ($CommandResult.ExitCode -ne 0) {
        throw "$Message (exit $($CommandResult.ExitCode)): $($CommandResult.Output)"
    }
    return $CommandResult.EvidencePath
}

function Invoke-ProotCommand {
    param(
        [string] $User,
        [string] $Command,
        [string] $EvidenceName,
        [int] $TimeoutSeconds = 90
    )
    $deviceArgs = Get-AdbDeviceArgument
    $innerCommand = "bash -lc " + (ConvertTo-ShellSingleQuotedArgument $Command)
    $prootCommand = "/data/data/com.portalagent/files/usr/bin/proot-distro login --user $User ubuntu -- $innerCommand"
    $adbShellCommand = "run-as com.portalagent sh -lc " + (ConvertTo-ShellSingleQuotedArgument $prootCommand)
    Invoke-Adb -Arguments ($deviceArgs + @("shell", $adbShellCommand)) -EvidenceName $EvidenceName -TimeoutSeconds $TimeoutSeconds
}

function Get-RuntimeComponentVersion {
    param([Parameter(Mandatory = $true)][string] $Name)
    $manifestPath = Join-Path $RepoRoot "app\src\main\assets\runtime-versions.json"
    $manifest = Get-Content -Encoding UTF8 -Raw -LiteralPath $manifestPath | ConvertFrom-Json
    $component = $manifest.components.PSObject.Properties[$Name].Value
    if ($null -eq $component -or [string]::IsNullOrWhiteSpace([string]$component.version)) {
        throw "Runtime manifest missing version for $Name"
    }
    return [string]$component.version
}

function Assert-ProotRuntimeOutput {
    param(
        [object] $CommandResult,
        [string] $Message
    )
    $evidence = Assert-ExitZero $CommandResult $Message
    if ($CommandResult.Output -match "Error: no command provided|inaccessible or not found|not found") {
        throw "$Message. Runtime output contains an error: $($CommandResult.Output)"
    }
    return $evidence
}

function Get-SelectedSuites {
    if ($Suite -eq "all") {
        return @("host", "device", "android-tools", "agentserver", "loom", "agent")
    }
    return @($Suite)
}

$Checks = New-Object System.Collections.Generic.List[object]

$Checks.Add((New-Check "host.unit-tests" "host" "Run app debug unit tests" "P0" {
    $res = Invoke-Gradle -Arguments @(":app:testDebugUnitTest", "--no-daemon", "--stacktrace") -EvidenceName "host-unit-tests"
    Assert-ExitZero $res "Gradle unit tests failed"
}))
$Checks.Add((New-Check "host.assemble-debug" "host" "Build debug APK" "P0" {
    $res = Invoke-Gradle -Arguments @(":app:assembleDebug", "--no-daemon", "--stacktrace") -EvidenceName "host-assemble-debug"
    Assert-ExitZero $res "Debug APK build failed"
}))
$Checks.Add((New-Check "host.apk-present" "host" "Resolve an APK artifact" "P0" {
    $apk = Resolve-Apk
    if (-not (Test-Path -LiteralPath $apk)) {
        throw "APK does not exist: $apk"
    }
    Write-TextFile (Join-Path $EvidenceDir "apk-path.txt") $apk
    return (Join-Path $EvidenceDir "apk-path.txt")
}))
$Checks.Add((New-Check "host.apk-sha256" "host" "Generate APK sha256" "P0" {
    $apk = Resolve-Apk
    $hash = Get-FileHash -Algorithm SHA256 -LiteralPath $apk
    $path = Join-Path $EvidenceDir "apk.sha256"
    Write-TextFile $path ("{0}  {1}" -f $hash.Hash.ToLowerInvariant(), (Split-Path -Leaf $apk))
    return $path
}))
$Checks.Add((New-Check "host.required-assets" "host" "Verify key README/release assets exist" "P0" {
    $required = @(
        "app\src\main\res\drawable-nodpi\portalagent_logo.png",
        "docs\images\readme\home.png",
        "docs\images\readme\collaboration.png",
        "docs\images\readme\settings.png",
        "docs\images\readme\api-tools.png",
        "app\src\main\assets\runtime-versions.json",
        "docs\release\runtime-versions.json",
        "release\release-notes.md",
        "LICENSE.md",
        "SECURITY.md"
    )
    $missing = @()
    foreach ($item in $required) {
        $path = Join-Path $RepoRoot $item
        if (-not (Test-Path -LiteralPath $path)) { $missing += $item }
    }
    if ($missing.Count -gt 0) {
        throw "Missing required assets: $($missing -join ', ')"
    }
    $evidence = Join-Path $EvidenceDir "required-assets.txt"
    Write-TextFile $evidence ($required -join "`n")
    return $evidence
}))
$Checks.Add((New-Check "host.runtime-versions" "host" "Verify pinned runtime versions and release notes" "P0" {
    $manifestPath = Join-Path $RepoRoot "app\src\main\assets\runtime-versions.json"
    $releaseManifestPath = Join-Path $RepoRoot "docs\release\runtime-versions.json"
    $javaPath = Join-Path $RepoRoot "app\src\main\java\com\portalagent\setup\RuntimeVersions.java"
    $notesPath = Join-Path $RepoRoot "release\release-notes.md"
    $missing = @($manifestPath, $releaseManifestPath, $javaPath, $notesPath) | Where-Object { -not (Test-Path -LiteralPath $_) }
    if ($missing.Count -gt 0) {
        throw "Missing runtime version files: $($missing -join ', ')"
    }

    $manifest = Get-Content -Encoding UTF8 -Raw -LiteralPath $manifestPath | ConvertFrom-Json
    $releaseManifest = Get-Content -Encoding UTF8 -Raw -LiteralPath $releaseManifestPath | ConvertFrom-Json
    $java = Get-Content -Encoding UTF8 -Raw -LiteralPath $javaPath
    $notes = Get-Content -Encoding UTF8 -Raw -LiteralPath $notesPath
    $componentNames = @("codex", "claude", "agentserver", "loom")
    $versions = @{}
    foreach ($name in $componentNames) {
        $component = $manifest.components.PSObject.Properties[$name].Value
        $releaseComponent = $releaseManifest.components.PSObject.Properties[$name].Value
        if ($null -eq $component -or $null -eq $releaseComponent) {
            throw "Runtime manifest missing component: $name"
        }
        $version = [string]$component.version
        if ([string]::IsNullOrWhiteSpace($version) -or $version -eq "latest" -or $version -eq "0.0.0" -or $version -eq "v0.0.0") {
            throw "Runtime component $name has an unpinned or placeholder version: $version"
        }
        if ($version -ne [string]$releaseComponent.version) {
            throw "Runtime manifest mismatch for $name`: app has $version, docs has $($releaseComponent.version)"
        }
        if (-not $notes.Contains($version)) {
            throw "release-notes.md does not include $name version $version"
        }
        $versions[$name] = $version
    }

    $javaExpectations = @{
        CODEX_VERSION = $versions.codex
        CLAUDE_CODE_VERSION = $versions.claude
        AGENTSERVER_VERSION = $versions.agentserver
        LOOM_VERSION = $versions.loom
    }
    foreach ($key in $javaExpectations.Keys) {
        $needle = 'public static final String ' + $key + ' = "' + $javaExpectations[$key] + '";'
        if (-not $java.Contains($needle)) {
            throw "RuntimeVersions.java does not include $needle"
        }
    }

    $evidence = Join-Path $EvidenceDir "runtime-versions.txt"
    Write-TextFile $evidence (($versions.GetEnumerator() | Sort-Object Name | ForEach-Object { "$($_.Name)=$($_.Value)" }) -join "`n")
    return $evidence
}))
$Checks.Add((New-Check "host.placeholder-scan" "host" "Scan release-facing docs for placeholders" "P1" {
    $files = @("README.md", "docs\readme\README.zh-CN.md", "docs\testing\release-gate-plan.md", "release\release-notes.md") |
        ForEach-Object { Join-Path $RepoRoot $_ } |
        Where-Object { Test-Path -LiteralPath $_ }
    $hits = @()
    foreach ($file in $files) {
        $content = Get-Content -Encoding UTF8 -Raw -LiteralPath $file
        if ($content -match "TBD|TODO|FIXME|placeholder|\]\(\)|href=`"`"|src=`"`"") {
            $hits += $file
        }
    }
    if ($hits.Count -gt 0) {
        throw "Placeholder or empty link found in: $($hits -join ', ')"
    }
    $evidence = Join-Path $EvidenceDir "placeholder-scan.txt"
    Write-TextFile $evidence ($files -join "`n")
    return $evidence
}))

$Checks.Add((New-Check "device.adb-device" "device" "Resolve requested ADB device" "P0" {
    $requested = if ([string]::IsNullOrWhiteSpace($Device)) { "<first online device>" } else { $Device }
    $res = Invoke-Adb -Arguments @("devices") -EvidenceName "device-adb-devices"
    if ($res.ExitCode -ne 0) {
        throw "Cannot resolve ADB device $requested. $($res.Output)"
    }
    if (-not [string]::IsNullOrWhiteSpace($Device) -and $res.Output -notmatch [regex]::Escape($Device) + "\s+device") {
        throw "Requested device $Device is not online. adb devices output: $($res.Output)"
    }
    Resolve-Device | Out-Null
    return $res.EvidencePath
}))
$Checks.Add((New-Check "device.install-apk" "device" "Install APK on device" "P0" {
    $deviceId = Resolve-Device
    $apk = Resolve-Apk
    $res = Invoke-Adb -Arguments @("-s", $deviceId, "install", "-r", $apk) -EvidenceName "device-install-apk" -TimeoutSeconds 180
    Assert-ExitZero $res "ADB install failed"
}))
$Checks.Add((New-Check "device.wake-screen" "device" "Wake screen and dismiss keyguard before visual checks" "P1" {
    $deviceId = Resolve-Device
    $cmd = "input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard 2>/dev/null || true; input keyevent KEYCODE_MENU 2>/dev/null || true"
    $res = Invoke-Adb -Arguments @("-s", $deviceId, "shell", "sh", "-c", $cmd) -EvidenceName "device-wake-screen"
    Assert-ExitZero $res "Could not wake device screen"
}))
$Checks.Add((New-Check "device.launch-app" "device" "Launch PortalAgent" "P0" {
    $deviceId = Resolve-Device
    $res = Invoke-Adb -Arguments @("-s", $deviceId, "shell", "monkey", "-p", "com.portalagent", "-c", "android.intent.category.LAUNCHER", "1") -EvidenceName "device-launch-app"
    Assert-ExitZero $res "PortalAgent launch failed"
}))
$Checks.Add((New-Check "device.package-version" "device" "Read installed package version" "P0" {
    $deviceId = Resolve-Device
    $res = Invoke-Adb -Arguments @("-s", $deviceId, "shell", "dumpsys", "package", "com.portalagent") -EvidenceName "device-package-version"
    Assert-ExitZero $res "Could not read package info"
    if ($res.Output -notmatch "versionName" -or $res.Output -notmatch "versionCode") {
        throw "Package info does not include versionName/versionCode"
    }
    return $res.EvidencePath
}))
$Checks.Add((New-Check "device.screenshot" "device" "Capture non-trivial device screenshot" "P1" {
    $deviceId = Resolve-Device
    $remote = "/sdcard/Download/portalagent-release-gate.png"
    $local = Join-Path $EvidenceDir "device-screenshot.png"
    $capture = Invoke-Adb -Arguments @("-s", $deviceId, "shell", "screencap", "-p", $remote) -EvidenceName "device-screenshot-capture"
    Assert-ExitZero $capture "Device screenshot capture failed" | Out-Null
    $pull = Invoke-Adb -Arguments @("-s", $deviceId, "pull", $remote, $local) -EvidenceName "device-screenshot-pull"
    Assert-ExitZero $pull "Device screenshot pull failed" | Out-Null
    $length = (Get-Item -LiteralPath $local).Length
    if ($length -lt 20000) {
        throw "Screenshot is too small ($length bytes), likely black or blank: $local"
    }
    return $local
}))
$Checks.Add((New-Check "device.logcat-crash-scan" "device" "Scan recent logcat for PortalAgent crashes" "P0" {
    $deviceId = Resolve-Device
    $res = Invoke-Adb -Arguments @("-s", $deviceId, "logcat", "-d", "-t", "800") -EvidenceName "device-logcat-tail"
    Assert-ExitZero $res "Could not read logcat"
    if ($res.Output -match "FATAL EXCEPTION" -and $res.Output -match "com\.portalagent") {
        throw "Recent logcat contains PortalAgent fatal exception"
    }
    return $res.EvidencePath
}))

$Checks.Add((New-Check "android-tools.mcp-forward" "android-tools" "Forward host port to Android MCP server" "P0" {
    $deviceId = Resolve-Device
    $res = Invoke-Adb -Arguments @("-s", $deviceId, "forward", "tcp:18765", "tcp:8765") -EvidenceName "android-tools-mcp-forward"
    Assert-ExitZero $res "ADB forward to MCP server failed"
}))
$Checks.Add((New-Check "android-tools.mcp-tools-list" "android-tools" "Call MCP tools/list through forwarded port" "P0" {
    $body = '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'
    $evidence = Join-Path $EvidenceDir "android-tools-mcp-tools-list.txt"
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Method Post -Uri "http://127.0.0.1:18765/mcp" -ContentType "application/json" -Body $body -TimeoutSec 10
        Write-TextFile $evidence $response.Content
    } catch {
        throw "MCP tools/list failed: $($_.Exception.Message)"
    }
    $content = Get-Content -Raw -LiteralPath $evidence
    if ($content -notmatch "tools" -and $content -notmatch "result") {
        throw "MCP tools/list response did not include result/tools"
    }
    return $evidence
}))
$Checks.Add((New-Check "android-tools.audit-log" "android-tools" "Collect MCP audit log tail when available" "P1" {
    $deviceId = Resolve-Device
    $cmd = "cat files/home/mcp-audit.log 2>/dev/null | tail -n 80 || true"
    $res = Invoke-Adb -Arguments @("-s", $deviceId, "shell", "run-as", "com.portalagent", "sh", "-lc", $cmd) -EvidenceName "android-tools-mcp-audit-tail"
    Assert-ExitZero $res "Could not query MCP audit log"
}))

$Checks.Add((New-Check "agentserver.unit-tests" "agentserver" "Run AgentServer-focused unit tests" "P0" {
    $res = Invoke-Gradle -Arguments @(":app:testDebugUnitTest", "--tests", "com.portalagent.agentserver.*", "--no-daemon", "--stacktrace") -EvidenceName "agentserver-unit-tests"
    Assert-ExitZero $res "AgentServer unit tests failed"
}))
$Checks.Add((New-Check "agentserver.runtime-binary" "agentserver" "Probe AgentServer binary inside Codex runtime" "P0" {
    Resolve-Device | Out-Null
    $expected = (Get-RuntimeComponentVersion "agentserver").TrimStart("v")
    $res = Invoke-ProotCommand -User "codex" -Command "command -v agentserver && agentserver version 2>&1" -EvidenceName "agentserver-runtime-binary"
    $evidence = Assert-ProotRuntimeOutput $res "AgentServer binary probe failed"
    if ($res.Output -notmatch [regex]::Escape($expected)) {
        throw "AgentServer version output does not include expected $expected. Output: $($res.Output)"
    }
    return $evidence
}))
$Checks.Add((New-Check "agentserver.log-tail" "agentserver" "Collect AgentServer log tails" "P1" {
    Resolve-Device | Out-Null
    $res = Invoke-ProotCommand -User "codex" -Command "tail -n 80 ~/agentserver-codex-agent.log 2>/dev/null || true; tail -n 80 ~/.agentserver-pipe.jsonl 2>/dev/null || true" -EvidenceName "agentserver-log-tail"
    Assert-ExitZero $res "AgentServer log tail failed"
}))

$Checks.Add((New-Check "loom.unit-tests" "loom" "Run Loom-focused unit tests" "P0" {
    $res = Invoke-Gradle -Arguments @(":app:testDebugUnitTest", "--tests", "com.portalagent.loom.*", "--no-daemon", "--stacktrace") -EvidenceName "loom-unit-tests"
    Assert-ExitZero $res "Loom unit tests failed"
}))
$Checks.Add((New-Check "loom.runtime-binaries" "loom" "Probe Loom role binaries inside Codex runtime" "P0" {
    Resolve-Device | Out-Null
    $res = Invoke-ProotCommand -User "codex" -Command "command -v observer-server && command -v driver-agent && command -v slave-agent" -EvidenceName "loom-runtime-binaries"
    Assert-ProotRuntimeOutput $res "Loom binary probe failed"
}))
$Checks.Add((New-Check "loom.process-scan" "loom" "Collect Loom process scan" "P1" {
    Resolve-Device | Out-Null
    $res = Invoke-ProotCommand -User "codex" -Command "ps -ef | grep -E 'observer-server|driver-agent|slave-agent' | grep -v grep || true" -EvidenceName "loom-process-scan"
    Assert-ExitZero $res "Loom process scan failed"
}))

$Checks.Add((New-Check "agent.unit-tests" "agent" "Run provider/session-focused unit tests" "P0" {
    $res = Invoke-Gradle -Arguments @(":app:testDebugUnitTest", "--tests", "com.portalagent.provider.*", "--tests", "com.portalagent.session.*", "--tests", "com.portalagent.chat.*", "--no-daemon", "--stacktrace") -EvidenceName "agent-unit-tests"
    Assert-ExitZero $res "Agent provider/session tests failed"
}))
$Checks.Add((New-Check "agent.codex-setup-upgrade" "agent" "Run pinned Codex setup inside Ubuntu" "P0" {
    Resolve-Device | Out-Null
    $scriptPath = "/data/data/com.portalagent/files/home/.codex-inner-setup.sh"
    $res = Invoke-ProotCommand -User "root" -Command "test -f $scriptPath && source $scriptPath" -EvidenceName "agent-codex-setup-upgrade" -TimeoutSeconds 420
    Assert-ExitZero $res "Codex setup upgrade failed"
}))
$Checks.Add((New-Check "agent.claude-setup-upgrade" "agent" "Run pinned Claude setup inside Ubuntu" "P0" {
    Resolve-Device | Out-Null
    $scriptPath = "/data/data/com.portalagent/files/home/.claude-inner-setup.sh"
    $res = Invoke-ProotCommand -User "root" -Command "test -f $scriptPath && source $scriptPath" -EvidenceName "agent-claude-setup-upgrade" -TimeoutSeconds 600
    Assert-ExitZero $res "Claude setup upgrade failed"
}))
$Checks.Add((New-Check "agent.codex-runtime" "agent" "Probe Codex runtime" "P0" {
    Resolve-Device | Out-Null
    $expected = Get-RuntimeComponentVersion "codex"
    $res = Invoke-ProotCommand -User "codex" -Command "whoami; command -v codex; codex --version 2>&1; test -f ~/.codex/config.toml" -EvidenceName "agent-codex-runtime"
    $evidence = Assert-ProotRuntimeOutput $res "Codex runtime probe failed"
    if ($res.Output -notmatch [regex]::Escape($expected)) {
        throw "Codex version output does not include expected $expected. Output: $($res.Output)"
    }
    return $evidence
}))
$Checks.Add((New-Check "agent.claude-runtime" "agent" "Probe Claude runtime" "P0" {
    Resolve-Device | Out-Null
    $expected = Get-RuntimeComponentVersion "claude"
    $res = Invoke-ProotCommand -User "claude" -Command "whoami; command -v claude; claude --version 2>&1; test -f ~/.claude.json" -EvidenceName "agent-claude-runtime"
    $evidence = Assert-ProotRuntimeOutput $res "Claude runtime probe failed"
    if ($res.Output -notmatch [regex]::Escape($expected)) {
        throw "Claude version output does not include expected $expected. Output: $($res.Output)"
    }
    return $evidence
}))

$selectedSuites = Get-SelectedSuites
$selectedChecks = $Checks | Where-Object { $selectedSuites -contains $_.Suite }

foreach ($check in $selectedChecks) {
    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    if ($DryRun) {
        $watch.Stop()
        $Results.Add((New-CheckResult -Id $check.Id -Suite $check.Suite -Description $check.Description -Level $check.Level -Status "dry-run" -DurationMs ([int]$watch.ElapsedMilliseconds)))
        Write-Output ("DRY-RUN {0} [{1}] {2}" -f $check.Id, $check.Level, $check.Description)
        continue
    }
    if ($SkipGradle -and ($check.Id -match "unit-tests|assemble-debug")) {
        $watch.Stop()
        $Results.Add((New-CheckResult -Id $check.Id -Suite $check.Suite -Description $check.Description -Level $check.Level -Status "skipped" -DurationMs ([int]$watch.ElapsedMilliseconds) -FailureReason "Skipped by -SkipGradle"))
        Write-Output ("SKIP {0}: skipped by -SkipGradle" -f $check.Id)
        continue
    }

    try {
        $evidence = & $check.Run
        $watch.Stop()
        $Results.Add((New-CheckResult -Id $check.Id -Suite $check.Suite -Description $check.Description -Level $check.Level -Status "passed" -DurationMs ([int]$watch.ElapsedMilliseconds) -EvidencePath ([string]$evidence)))
        Write-Output ("PASS {0}" -f $check.Id)
    } catch {
        $watch.Stop()
        $Results.Add((New-CheckResult -Id $check.Id -Suite $check.Suite -Description $check.Description -Level $check.Level -Status "failed" -DurationMs ([int]$watch.ElapsedMilliseconds) -FailureReason $_.Exception.Message))
        Write-Output ("FAIL {0}: {1}" -f $check.Id, $_.Exception.Message)
    }
}

$summary = [ordered]@{
    generatedAt = (Get-Date).ToString("o")
    suite = $Suite
    device = $Script:ResolvedDevice
    apkPath = $Script:ResolvedApk
    outputDir = $OutputDir
    total = $Results.Count
    passed = @($Results | Where-Object { $_.status -eq "passed" }).Count
    failed = @($Results | Where-Object { $_.status -eq "failed" }).Count
    skipped = @($Results | Where-Object { $_.status -eq "skipped" }).Count
    dryRun = @($Results | Where-Object { $_.status -eq "dry-run" }).Count
}

$report = [ordered]@{
    summary = $summary
    results = $Results
}

$jsonPath = Join-Path $OutputDir "release-test-report.json"
$mdPath = Join-Path $OutputDir "release-test-summary.md"
Write-TextFile $jsonPath (($report | ConvertTo-Json -Depth 8))

$md = New-Object System.Text.StringBuilder
[void]$md.AppendLine("# PortalAgent Release Gate Summary")
[void]$md.AppendLine("")
[void]$md.AppendLine(('- Suite: `{0}`' -f $Suite))
[void]$md.AppendLine(('- Generated: `{0}`' -f $summary.generatedAt))
[void]$md.AppendLine(('- Device: `{0}`' -f $(if ($summary.device) { $summary.device } else { "" })))
[void]$md.AppendLine(('- APK: `{0}`' -f $(if ($summary.apkPath) { $summary.apkPath } else { "" })))
[void]$md.AppendLine(("- Total: {0}, Passed: {1}, Failed: {2}, DryRun: {3}" -f $summary.total, $summary.passed, $summary.failed, $summary.dryRun))
[void]$md.AppendLine("")
[void]$md.AppendLine("| Status | Level | Suite | ID | Evidence | Failure |")
[void]$md.AppendLine("| --- | --- | --- | --- | --- | --- |")
foreach ($result in $Results) {
    $failure = ($result.failureReason -replace "\r?\n", " ") -replace "\|", "\|"
    $evidence = $result.evidencePath
    [void]$md.AppendLine(('| {0} | {1} | {2} | `{3}` | `{4}` | {5} |' -f $result.status, $result.level, $result.suite, $result.id, $evidence, $failure))
}
Write-TextFile $mdPath $md.ToString()

Write-Output ("Report: {0}" -f $jsonPath)
Write-Output ("Summary: {0}" -f $mdPath)

if ($summary.failed -gt 0) {
    exit 1
}
exit 0
