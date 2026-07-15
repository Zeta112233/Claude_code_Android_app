param(
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
)

$ErrorActionPreference = "Stop"
$ScriptPath = Join-Path $RepoRoot "scripts\run_release_gate.ps1"
$TmpRoot = Join-Path $RepoRoot ".tmp\release-gate-tests"

function Assert-True {
    param(
        [bool] $Condition,
        [string] $Message
    )
    if (-not $Condition) {
        throw $Message
    }
}

function Assert-Contains {
    param(
        [string] $Text,
        [string] $Needle,
        [string] $Message
    )
    if (-not $Text.Contains($Needle)) {
        throw $Message
    }
}

if (Test-Path -LiteralPath $TmpRoot) {
    Remove-Item -LiteralPath $TmpRoot -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $TmpRoot | Out-Null

$listOutput = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $ScriptPath -ListSuites 2>&1 | Out-String
Assert-Contains $listOutput "host" "ListSuites should include host"
Assert-Contains $listOutput "device" "ListSuites should include device"
Assert-Contains $listOutput "agentserver" "ListSuites should include agentserver"
Assert-Contains $listOutput "loom" "ListSuites should include loom"
Assert-Contains $listOutput "agent" "ListSuites should include agent"
Assert-Contains $listOutput "android-tools" "ListSuites should include android-tools"

$scriptContent = Get-Content -Encoding UTF8 -Raw -LiteralPath $ScriptPath
Assert-Contains $scriptContent 'HOST_OS' "Release gate should define HOST_OS for Windows NDK builds"
Assert-Contains $scriptContent 'windows' "Release gate should set HOST_OS to windows for Windows NDK builds"
Assert-Contains $scriptContent 'KEYCODE_WAKEUP' "Device suite should wake the screen before launch/screenshot"
Assert-Contains $scriptContent 'host.runtime-versions' "Release gate should verify pinned runtime versions"
Assert-Contains $scriptContent 'runtime-versions.json' "Release gate should verify runtime version manifests"
Assert-Contains $scriptContent 'release-notes.md' "Release gate should verify release notes include runtime versions"
Assert-Contains $scriptContent 'ConvertTo-ShellSingleQuotedArgument' "Release gate should quote proot shell commands"
Assert-Contains $scriptContent 'run-as com.portalagent sh -lc' "Release gate should run proot commands through one quoted adb shell command"
Assert-Contains $scriptContent 'agent.codex-setup-upgrade' "Agent suite should run the pinned Codex setup before probing"
Assert-Contains $scriptContent 'agent.claude-setup-upgrade' "Agent suite should run the pinned Claude setup before probing"

$dryRunDir = Join-Path $TmpRoot "dry-run"
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $ScriptPath -Suite all -DryRun -OutputDir $dryRunDir | Out-Null
Assert-True (Test-Path -LiteralPath (Join-Path $dryRunDir "release-test-report.json")) "DryRun should write JSON report"
Assert-True (Test-Path -LiteralPath (Join-Path $dryRunDir "release-test-summary.md")) "DryRun should write Markdown summary"
$dryRunReport = Get-Content -Encoding UTF8 -Raw -LiteralPath (Join-Path $dryRunDir "release-test-report.json") | ConvertFrom-Json
Assert-True ($dryRunReport.summary.total -gt 0) "DryRun report should include planned checks"
Assert-True ($dryRunReport.summary.failed -eq 0) "DryRun should not fail checks"
Assert-True ($dryRunReport.results[0].PSObject.Properties.Name -contains "id") "Report entries should include id"
Assert-True ($dryRunReport.results[0].PSObject.Properties.Name -contains "suite") "Report entries should include suite"
Assert-True ($dryRunReport.results[0].PSObject.Properties.Name -contains "status") "Report entries should include status"
Assert-True ($dryRunReport.results[0].PSObject.Properties.Name -contains "evidencePath") "Report entries should include evidencePath"
Assert-True ($dryRunReport.results[0].PSObject.Properties.Name -contains "failureReason") "Report entries should include failureReason"

$missingDeviceDir = Join-Path $TmpRoot "missing-device"
$missingOutput = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $ScriptPath -Suite device -Device "__missing_device__" -OutputDir $missingDeviceDir 2>&1 | Out-String
$missingExitCode = $LASTEXITCODE
Assert-True ($missingExitCode -ne 0) "Device suite should fail when requested device is unavailable"
Assert-Contains $missingOutput "__missing_device__" "Missing-device output should name the requested device"
Assert-True (Test-Path -LiteralPath (Join-Path $missingDeviceDir "release-test-report.json")) "Failed run should still write JSON report"

Write-Output "run_release_gate.tests.ps1 passed"
