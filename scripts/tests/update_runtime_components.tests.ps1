param(
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
)

$ErrorActionPreference = "Stop"
$ScriptPath = Join-Path $RepoRoot "scripts\update_runtime_components.ps1"
$TmpRoot = Join-Path $RepoRoot ".tmp\runtime-update-tests"

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

$fixturePath = Join-Path $TmpRoot "versions.json"
$fixture = [ordered]@{
    components = [ordered]@{
        codex = [ordered]@{
            version = "1.2.3"
            package = "@openai/codex"
            source = "https://registry.npmjs.org/@openai%2fcodex"
        }
        claude = [ordered]@{
            version = "4.5.6"
            package = "@anthropic-ai/claude-code"
            source = "https://registry.npmjs.org/@anthropic-ai%2fclaude-code"
        }
        agentserver = [ordered]@{
            version = "v7.8.9"
            repo = "agentserver/agentserver"
            source = "https://github.com/agentserver/agentserver"
            arm64Asset = "agentserver-linux-arm64.tar.gz"
            arm64DownloadUrl = "https://github.com/agentserver/agentserver/releases/download/v7.8.9/agentserver-linux-arm64.tar.gz"
        }
        loom = [ordered]@{
            version = "v10.11.12"
            repo = "agentserver/loom"
            source = "https://github.com/agentserver/loom"
        }
    }
}
$fixture | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 -LiteralPath $fixturePath

$dryRunDir = Join-Path $TmpRoot "dry-run"
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $ScriptPath `
    -VersionsJson $fixturePath `
    -DryRun `
    -OutputDir $dryRunDir `
    -ReleaseTag "v9.9" `
    -AppVersionName "9.9.0" `
    -AppVersionCode 999 | Out-Null
Assert-True ($LASTEXITCODE -eq 0) "DryRun should exit zero with fixture versions"

$planPath = Join-Path $dryRunDir "runtime-components-plan.json"
$notesPath = Join-Path $dryRunDir "release-notes.md"
Assert-True (Test-Path -LiteralPath $planPath) "DryRun should write a runtime component plan"
Assert-True (Test-Path -LiteralPath $notesPath) "DryRun should write release notes"

$plan = Get-Content -Encoding UTF8 -Raw -LiteralPath $planPath | ConvertFrom-Json
Assert-True ($plan.release.tag -eq "v9.9") "Plan should include the release tag"
Assert-True ($plan.release.appVersionName -eq "9.9.0") "Plan should include app versionName"
Assert-True ($plan.release.appVersionCode -eq 999) "Plan should include app versionCode"
Assert-True ($plan.components.codex.version -eq "1.2.3") "Plan should include Codex version"
Assert-True ($plan.components.claude.version -eq "4.5.6") "Plan should include Claude version"
Assert-True ($plan.components.agentserver.version -eq "v7.8.9") "Plan should include AgentServer version"
Assert-True ($plan.components.loom.version -eq "v10.11.12") "Plan should include Loom version"
Assert-True ($plan.targets.runtimeManifest -match "runtime-versions\.json$") "Plan should target runtime-versions.json"
Assert-True ($plan.targets.runtimeVersionsJava -match "RuntimeVersions\.java$") "Plan should target RuntimeVersions.java"

$notes = Get-Content -Encoding UTF8 -Raw -LiteralPath $notesPath
Assert-Contains $notes "PortalAgent v9.9" "Release notes should name the release tag"
Assert-Contains $notes "Codex: 1.2.3" "Release notes should include Codex"
Assert-Contains $notes "Claude Code: 4.5.6" "Release notes should include Claude"
Assert-Contains $notes "AgentServer: v7.8.9" "Release notes should include AgentServer"
Assert-Contains $notes "Loom: v10.11.12" "Release notes should include Loom"

$scriptContent = Get-Content -Encoding UTF8 -Raw -LiteralPath $ScriptPath
Assert-Contains $scriptContent "prepare_loom_addon.ps1" "Upgrade script should refresh the Loom bundle"
Assert-Contains $scriptContent "run_release_gate.ps1" "Upgrade script should be able to run the release gate"
Assert-Contains $scriptContent "agentserver-linux-arm64.tgz" "Upgrade script should refresh the bundled AgentServer archive"

Write-Output "update_runtime_components.tests.ps1 passed"
