<h1 align="center">PortalAgent</h1>

<p align="center">
  <b>Run a local agent runtime on Android. Let your phone observe, act, and collaborate.</b>
</p>

<p align="center">
  <a href="#get-the-android-app">Android App</a> ·
  <a href="#quick-start">Quick Start</a> ·
  <a href="#why-portalagent">Why</a> ·
  <a href="#runtime-architecture">Architecture</a> ·
  <a href="#build-from-source">Build</a> ·
  <a href="#status-and-safety">Safety</a> ·
  <a href="#repository-map">Repository Map</a> ·
  <a href="SECURITY.md">Security</a>
</p>

<p align="center">
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android-3DDC84">
  <img alt="Runtime" src="https://img.shields.io/badge/runtime-Termux%20%2B%20Ubuntu-black">
  <img alt="Agents" src="https://img.shields.io/badge/agents-Codex%20%7C%20Claude-blue">
  <a href="LICENSE.md"><img alt="License" src="https://img.shields.io/badge/license-GPLv3-green.svg"></a>
</p>

PortalAgent is a phone-native agent runtime for Android. It packages a
Termux-based execution layer, an Ubuntu proot environment, Codex/Claude
provider support, Android MCP tools, and AgentServer/Loom collaboration into
one APK-managed mobile runtime.

Instead of treating the phone as a passive remote screen, PortalAgent makes the
phone the runtime. The agent can observe Android state, use local files and
shell tools, operate approved apps through accessibility/ADB channels, and join
remote collaboration workflows as a mobile agent node.

## Get The Android App

The Android app is the recommended way to try PortalAgent. It handles runtime
deployment, provider setup, Android permissions, phone-control tools, and
collaboration status from a phone-native UI.

Download a prebuilt APK from [`release/`](release/) or the project releases
when available. You can also build the debug APK locally:

```powershell
.\gradlew.bat :app:assembleDebug
adb install -r app\build\outputs\apk\debug\portal-agent_apt-android-7-debug_universal.apk
```

If you clone the repository, install Git LFS first and pull large assets:

```powershell
git lfs install
git lfs pull
```

<table>
  <tr>
    <td align="center">
      <img src="docs/images/readme/home.png" width="240" alt="PortalAgent home chat screen" />
    </td>
    <td align="center">
      <img src="docs/images/readme/collaboration.png" width="240" alt="PortalAgent collaboration runtime screen" />
    </td>
    <td align="center">
      <img src="docs/images/readme/settings.png" width="240" alt="PortalAgent settings screen" />
    </td>
  </tr>
  <tr>
    <td align="center"><strong>Chat with a local agent</strong></td>
    <td align="center"><strong>Run collaboration workflows</strong></td>
    <td align="center"><strong>Control runtime permissions</strong></td>
  </tr>
</table>

## Quick Start

1. Install the APK and open PortalAgent.
2. Keep the app in the foreground and wait on the Terminal tab while the
   runtime is deployed.
3. Open the Keys tab and configure a Codex or Claude provider.
4. Grant only the Android capabilities you want the agent to use, such as
   screen capture, accessibility, and optional ADB companion access.
5. Return to Home and start a chat, or open Collaboration to bind a Driver,
   start local Loom roles, and connect to AgentServer workflows.

Runtime setup is complete when the terminal shows:

```text
[✓] PortalAgent environment setup successful.
```

If setup is interrupted, open `Settings -> App Settings -> Environment Repair`.
The repair flow rechecks Ubuntu, Termux tools, Codex/Claude setup,
AgentServer, Loom, and Android capability wrappers.

## Why PortalAgent?

Modern mobile agents need more than screenshot reasoning. They need a local
runtime close to the apps, files, permissions, and device state they are asked
to use.

| Capability | Why it matters |
| --- | --- |
| **Phone-native runtime** | The Android device hosts the terminal layer, Ubuntu runtime, Android tools, provider configs, logs, and collaboration state. |
| **Codex and Claude provider isolation** | Each provider gets separate Linux users, homes, keys, prompts, and history. |
| **Android MCP bridge** | Models can call structured tools for screenshots, UI trees, taps, swipes, text input, app launch, files, sensors, network state, and ADB fallback. |
| **Collaboration runtime** | AgentServer and Loom support Driver binding, local Observer/Driver/Slave roles, and remote task dispatch. |
| **Progressive permission model** | Users grant phone capabilities explicitly instead of giving every tool full device access by default. |

## Runtime Architecture

| Layer | Role |
| --- | --- |
| Android app | Main UI, provider settings, permission prompts, runtime repair, collaboration dashboard, and MCP/HTTP bridges. |
| Termux layer | APK-bundled terminal substrate and bootstrap environment. |
| Ubuntu proot | Shared Linux runtime for Node.js, Codex/Claude CLI setup, AgentServer, Loom binaries, and provider users. |
| Provider users | `codex` and `claude` isolate API keys, home directories, prompts, settings, and histories. |
| Android MCP tools | Capability bridge for UI automation, screenshots, files, device state, app launch, clipboard, sensors, and ADB fallback. |
| Collaboration tools | AgentServer and Loom provide workspace binding, Driver/Slave operation, and remote orchestration. |

### Runtime Assets

PortalAgent keeps the APK practical by bundling only the current ARM64 offline
runtime assets and using online fallback where needed.

| Asset | Current behavior |
| --- | --- |
| Ubuntu snapshot | Bundled for `arm64-v8a` as `ubuntu-claude-aarch64-20260521.tar.xz`. |
| Termux tool debs | Bundled for `aarch64`; non-ARM64 devices use Termux package fallback. |
| AgentServer | Bundled for `linux-arm64`; `x86_64` downloads `agentserver-linux-amd64.tar.gz` from the same pinned version. |
| Loom | Bundled for `linux-arm64`; `x86_64` downloads matching `*.linux-amd64` release assets. |
| Claude native binary | Uses `@anthropic-ai/claude-code-linux-arm64` on ARM64 and `@anthropic-ai/claude-code-linux-x64` on x86_64. |

### Android Architecture Support

| Android ABI | Status |
| --- | --- |
| `arm64-v8a` | Primary supported path with bundled offline runtime assets. |
| `x86_64` | Supported through online runtime fallback for Windows/desktop Android emulators. |
| `armeabi-v7a`, `x86` | App bootstrap can build, but AgentServer/Loom/Claude native runtime support is not the main path yet. |

## Status And Safety

PortalAgent is under active development. It can operate powerful phone-control
channels when authorized, so use it as a trusted local runtime rather than a
generic untrusted app sandbox.

Before sharing logs or traces, review them for:

- API keys and provider tokens.
- Screenshots and UI text from private apps.
- Local file paths and workspace content.
- AgentServer/Loom workspace credentials.
- Android package names and app data paths.

The current workspace restriction feature narrows the directories and app data
the agent is expected to use, but it should be treated as a product-level
control boundary, not a complete OS security sandbox.

Security policy: [SECURITY.md](SECURITY.md)

## Build From Source

Requirements:

- JDK 21.
- Android SDK 36.
- Android NDK `29.0.14206865`.
- Git LFS for bundled snapshot/APK assets.
- PowerShell on Windows for the commands below.

Build a debug APK:

```powershell
git lfs pull
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

Install to a connected device:

```powershell
adb devices
adb install -r app\build\outputs\apk\debug\portal-agent_apt-android-7-debug_universal.apk
```

Check the installed version:

```powershell
adb shell dumpsys package com.portalagent | findstr /i "versionName versionCode"
```

Current app version in Gradle:

```text
versionName 0.118.0
versionCode 118
```

## Repository Map

| Path | Purpose |
| --- | --- |
| `app/` | Android app, setup orchestration, MCP bridge, provider UI, collaboration UI, and packaged runtime assets. |
| `terminal-emulator/`, `terminal-view/`, `termux-shared/` | Termux-derived terminal and shared libraries. |
| `docs/superpowers/specs/` | Design notes for provider support, Loom integration, automation boost, and collaboration boundaries. |
| `docs/images/readme/` | README screenshots. |
| `release/` | Locally tracked release APK artifacts via Git LFS. |
| `.github/` | Issue templates and CI workflows. |

Useful design notes:

- [`docs/superpowers/specs/2026-06-04-codex-provider-support-design.md`](docs/superpowers/specs/2026-06-04-codex-provider-support-design.md)
- [`docs/superpowers/specs/2026-06-03-loom-offline-addon-integration-design.md`](docs/superpowers/specs/2026-06-03-loom-offline-addon-integration-design.md)
- [`docs/superpowers/specs/2026-06-10-automation-boost-design.md`](docs/superpowers/specs/2026-06-10-automation-boost-design.md)
- [`docs/superpowers/specs/2026-06-16-agentserver-loom-connection-boundary-design.md`](docs/superpowers/specs/2026-06-16-agentserver-loom-connection-boundary-design.md)
- [`docs/superpowers/specs/2026-06-16-agentserver-loom-unified-collaboration-design.md`](docs/superpowers/specs/2026-06-16-agentserver-loom-unified-collaboration-design.md)

## Troubleshooting

| Symptom | First check |
| --- | --- |
| Runtime setup never reaches success | Keep the app foregrounded, confirm network access, then run Environment Repair. |
| Screenshot tools are unavailable | Regrant screen capture permission after app restart or Android permission reset. |
| UI tools cannot click a target app | Confirm accessibility is enabled and that the app is allowed in PortalAgent workspace/app access settings. |
| AgentServer is connected but Loom asks for Driver binding | This is expected; AgentServer workspace connection and Loom Driver binding use separate credentials. |
| x86_64 emulator cannot use bundled snapshot | Expected; x86_64 uses online Ubuntu and amd64 binary fallback to avoid shipping a second large image. |

## Contributing

Issues and pull requests are welcome. For useful reports, include:

- Device model, Android version, and ABI.
- PortalAgent version and APK source.
- Whether the runtime is ARM64 bundled mode or x86_64 online fallback.
- Redacted terminal logs from setup or Environment Repair.
- Reproduction steps and screenshots when UI behavior is involved.

Do not attach raw API keys, workspace tokens, unredacted app screenshots, or
private file listings.

## License

PortalAgent is built on Termux-derived code and keeps the corresponding
licensing model. See [LICENSE.md](LICENSE.md) for GPLv3 and upstream library
exceptions.
