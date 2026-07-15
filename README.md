<h1 align="center">
  <img src="app/src/main/res/drawable-nodpi/portalagent_logo.png" alt="PortalAgent" width="72" valign="middle" /> PortalAgent
</h1>

<p align="center">
  <a href="release/"><img alt="Android APK" src="https://img.shields.io/badge/APK-release-2F80ED"></a>
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android-3DDC84">
  <img alt="Status" src="https://img.shields.io/badge/status-experimental-orange">
  <img alt="Providers" src="https://img.shields.io/badge/providers-Codex%20%7C%20Claude-4B6EF5">
  <a href="LICENSE.md"><img alt="License" src="https://img.shields.io/badge/license-GPLv3-green.svg"></a>
</p>

<p align="center">
  <sub><a href="docs/readme/README.zh-CN.md">中文</a></sub>
</p>

<p align="center">
  <strong>A phone-native workspace for mobile AI agents.</strong><br/>
  Run Codex or Claude from Android, grant device capabilities deliberately, and keep agent activity visible in one local app.
</p>

<h3 align="center"><a href="release/"><ins>Download APK</ins></a></h3>

<p align="center">
  <img src="docs/images/readme/home.png" alt="PortalAgent home chat workspace" width="250" />
  <img src="docs/images/readme/collaboration.png" alt="PortalAgent collaboration runtime" width="250" />
  <img src="docs/images/readme/settings.png" alt="PortalAgent settings and access controls" width="250" />
</p>

PortalAgent turns an Android phone into a local agent workspace. The app brings together chat, provider keys, a Termux-derived runtime, Android device tools, collaboration status, and repair controls so mobile agent work can be inspected and recovered from the phone itself.

It is not a general sandbox for untrusted prompts or unknown remote operators. PortalAgent is designed around explicit user control: grant the capabilities you need, keep the app's activity visible, and treat powerful device-control tools as trusted automation.

## Features

<table>
<tr>
<td width="50%" valign="middle">

### Mobile Agent Workspace

Start conversations from Android and keep the agent's responses, tool activity, attachments, and session controls in one phone-native interface.

Supports provider workflows for **Codex** and **Claude**.

</td>
<td width="50%">
  <img src="docs/images/readme/home.png" alt="PortalAgent home chat workspace" width="100%" />
</td>
</tr>
<tr>
<td width="50%" valign="middle">

### Collaboration Runtime

Bind a Driver workspace, manage local Slave roles, and monitor AgentServer/Loom collaboration state directly from the phone.

Use it when the Android device should participate as a mobile agent node instead of only acting as a remote screen.

</td>
<td width="50%">
  <img src="docs/images/readme/collaboration.png" alt="PortalAgent collaboration runtime dashboard" width="100%" />
</td>
</tr>
<tr>
<td width="50%" valign="middle">

### Device Tools With User Control

Expose selected Android capabilities to the agent: screenshots, UI trees, taps, text input, clipboard, camera, sensors, network state, battery state, and ADB-assisted fallback.

Capabilities remain visible and should be granted only for the task at hand.

</td>
<td width="50%">
  <img src="docs/images/readme/api-tools.png" alt="PortalAgent device API self-check screen" width="100%" />
</td>
</tr>
<tr>
<td width="50%" valign="middle">

### Access And Repair Surface

Review automation settings, app/file access boundaries, terminal preferences, plugins, and environment repair tools without leaving the app.

If setup is interrupted, the repair flow rechecks the local runtime and Android capability bridges.

</td>
<td width="50%">
  <img src="docs/images/readme/settings.png" alt="PortalAgent settings screen" width="100%" />
</td>
</tr>
</table>

**Also included:**

- **Local Android runtime** - Termux-derived terminal components plus an Ubuntu proot environment for agent tooling.
- **Provider separation** - Codex and Claude workflows keep their own configuration, keys, prompts, and histories.
- **Permission-first operation** - Android screen capture, accessibility, storage, camera, sensor, and app-operation capabilities are surfaced as user decisions.
- **Workspace restrictions** - File and app operations can be narrowed to approved locations and packages.
- **Runtime repair** - Re-run environment checks when provider tools, collaboration binaries, or Android bridges fail.
- **Real-device validation path** - Build, install, and test on a connected Android device with standard Gradle and ADB commands.

---

## Supported Agents

PortalAgent focuses on mobile workflows for CLI-based coding agents that can run inside the bundled Linux environment.

<p>
  <kbd>Codex</kbd> &nbsp;
  <kbd>Claude Code</kbd>
</p>

Provider support is experimental and should be validated on the target device before relying on it for long-running work.

---

## Install

### Android APK

Install a prebuilt APK from [`release/`](release/) or from project releases when available.

```powershell
adb devices
adb install -r release\portal-agent_apt-android-7-debug_universal.apk
```

On first run:

1. Open PortalAgent and keep it in the foreground while the local environment is prepared.
2. Open the Keys page and configure the provider you want to use.
3. Grant only the Android capabilities needed for your task.
4. Return to Home for chat, or open Collaboration for trusted remote workflows.

If setup is interrupted, use `Settings -> App Settings -> Environment Repair`.

### Build From Source

Requirements:

- JDK 21.
- Android SDK 36.
- Android NDK `29.0.14206865`.
- Git LFS.
- PowerShell on Windows for the commands below.

```powershell
git lfs install
git lfs pull
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

Install the locally built debug APK:

```powershell
adb install -r app\build\outputs\apk\debug\portal-agent_apt-android-7-debug_universal.apk
```

Check the installed package version:

```powershell
adb shell dumpsys package com.portalagent | findstr /i "versionName versionCode"
```

---

## Trust And Safety

PortalAgent is an experimental local automation app. When permissions are granted, it can expose powerful phone-control abilities to local agents and collaboration workflows.

Do not treat PortalAgent as a hardened security boundary. Its permission prompts, workspace settings, and tool checks are intended to reduce accidental access and make behavior easier to inspect; they are not a guarantee that prompts, tools, dependencies, remote services, or the underlying Android/Linux runtime cannot behave unexpectedly.

Use PortalAgent only with agents, prompts, repositories, accounts, and remote collaborators you are prepared to trust on that device. Avoid using it with sensitive apps or private data unless you have reviewed the required permissions and understand the risk.

Before enabling a capability, decide whether the agent should be allowed to:

- See screen content from other apps.
- Click, type, scroll, or launch selected apps.
- Read files from approved folders.
- Use camera, clipboard, sensors, or device status tools.
- Connect to remote collaboration services.

The current product boundary is permission-first and tool-level. It is a user-control model, not a complete operating system sandbox, malware containment layer, or defense against malicious instructions.

Security policy: [SECURITY.md](SECURITY.md)

---

## Developing

Detailed design notes live under [`docs/`](docs/). Runtime internals, compatibility notes, and implementation plans should stay there instead of crowding the README opening.

Useful starting points:

- [`docs/architecture/agent_behavior_restriction_plan.md`](docs/architecture/agent_behavior_restriction_plan.md)
- [`docs/architecture/termux_api_full_integration.md`](docs/architecture/termux_api_full_integration.md)
- [`docs/architecture/chat_copy_and_browser_search_stability.md`](docs/architecture/chat_copy_and_browser_search_stability.md)

Repository layout:

| Path | Purpose |
| --- | --- |
| `app/` | Android app, product UI, setup orchestration, phone tools, provider screens, and collaboration UI. |
| `terminal-emulator/`, `terminal-view/`, `termux-shared/` | Termux-derived terminal and shared Android libraries. |
| `docs/` | Architecture notes, plans, screenshots, and project documentation. |
| `release/` | Locally tracked APK artifacts and large assets managed through Git LFS. |
| `.github/` | Issue templates, workflows, funding metadata, and dependency automation. |

Issues and pull requests are welcome. Useful reports include the device model, Android version, ABI, PortalAgent version, permission state, redacted setup logs, and screenshots when UI behavior is involved.

Do not attach raw API keys, workspace tokens, unredacted private screenshots, or private file listings.

## License

PortalAgent is built on Termux-derived code and keeps the corresponding licensing model. See [LICENSE.md](LICENSE.md) for GPLv3 and upstream library exceptions.
