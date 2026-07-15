# Release Upload Runbook

Use this before pushing a release commit or tag. The required order is:

1. Upgrade runtime components.
2. Run release gate verification.
3. Commit, push, tag, and publish only after both pass.

## 1. Runtime Upgrade

This detects the latest Codex, Claude Code, AgentServer, and Loom versions, refreshes bundled runtime assets, updates manifests, bumps the app version when requested, and regenerates release notes.

Check the latest versions without changing repo files:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\update_runtime_components.ps1 `
  -CheckOnly `
  -OutputDir .tmp\runtime-check `
  -ReleaseTag vX.Y `
  -AppVersionName 0.NNN.0 `
  -AppVersionCode NNN
```

Apply the upgrade:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\update_runtime_components.ps1 `
  -OutputDir .tmp\runtime-update `
  -ReleaseTag vX.Y `
  -AppVersionName 0.NNN.0 `
  -AppVersionCode NNN
```

If network access needs the local proxy, add:

```powershell
-ProxyUrl http://127.0.0.1:7890
```

After the upgrade, inspect:

- `app/src/main/assets/runtime-versions.json`
- `docs/release/runtime-versions.json`
- `app/src/main/java/com/portalagent/setup/RuntimeVersions.java`
- `release/release-notes.md`
- `app/src/main/assets/agentserver-linux-arm64.tgz`
- `app/src/main/assets/loom-linux-arm64.tgz`

## 2. Release Gate Verification

Run script self-tests first:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\tests\update_runtime_components.tests.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\tests\run_release_gate.tests.ps1
```

Run the host gate. This covers unit tests, APK build, release-facing assets, runtime version consistency, and placeholder scan:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\run_release_gate.ps1 `
  -Suite host `
  -OutputDir .tmp\release-gate-host `
  -GradleTimeoutMinutes 20
```

Run device and runtime gates against the connected Android device:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\run_release_gate.ps1 `
  -Suite device `
  -Device <ADB_SERIAL> `
  -OutputDir .tmp\release-gate-device `
  -GradleTimeoutMinutes 20

powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\run_release_gate.ps1 `
  -Suite android-tools `
  -Device <ADB_SERIAL> `
  -OutputDir .tmp\release-gate-android-tools `
  -GradleTimeoutMinutes 20

powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\run_release_gate.ps1 `
  -Suite agentserver `
  -Device <ADB_SERIAL> `
  -OutputDir .tmp\release-gate-agentserver `
  -SkipGradle `
  -GradleTimeoutMinutes 20

powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\run_release_gate.ps1 `
  -Suite loom `
  -Device <ADB_SERIAL> `
  -OutputDir .tmp\release-gate-loom `
  -SkipGradle `
  -GradleTimeoutMinutes 20

powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\run_release_gate.ps1 `
  -Suite agent `
  -Device <ADB_SERIAL> `
  -OutputDir .tmp\release-gate-agent `
  -SkipGradle `
  -GradleTimeoutMinutes 20
```

The `agent` suite deploys pinned Codex and Claude setup scripts inside Ubuntu before checking versions. The `agentserver` and `loom` suites verify the installed runtime binaries.

## 3. Upload After Passing

Only after the upgrade and all required gates pass:

```powershell
git add <changed files>
git commit -m "<release commit message>"
git push origin master
git tag -a vX.Y -m "PortalAgent vX.Y"
git push origin vX.Y
```

The tag push triggers `.github/workflows/release_apk.yml`, which builds the APK and publishes GitHub Release notes from `release/release-notes.md`.
