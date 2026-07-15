<h1 align="center">
  <img src="../../app/src/main/res/drawable-nodpi/portalagent_logo.png" alt="PortalAgent" width="72" valign="middle" /> PortalAgent
</h1>

<p align="center">
  <a href="../../release/"><img alt="Android APK" src="https://img.shields.io/badge/APK-release-2F80ED"></a>
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android-3DDC84">
  <img alt="Status" src="https://img.shields.io/badge/status-experimental-orange">
  <img alt="Providers" src="https://img.shields.io/badge/providers-Codex%20%7C%20Claude-4B6EF5">
  <a href="../../LICENSE.md"><img alt="License" src="https://img.shields.io/badge/license-GPLv3-green.svg"></a>
</p>

<p align="center">
  <sub><a href="../../README.md">English</a></sub>
</p>

<p align="center">
  <strong>面向移动 AI Agent 的手机原生工作台。</strong><br/>
  在 Android 上运行 Codex 或 Claude，按需授予设备能力，并在一个本地应用里查看 Agent 活动。
</p>

<h3 align="center"><a href="../../release/"><ins>下载 APK</ins></a></h3>

<p align="center">
  <img src="../images/readme/home.png" alt="PortalAgent 主页聊天工作台" width="250" />
  <img src="../images/readme/collaboration.png" alt="PortalAgent 协作运行时" width="250" />
  <img src="../images/readme/settings.png" alt="PortalAgent 设置和访问控制" width="250" />
</p>

PortalAgent 会把 Android 手机变成一个本地 Agent 工作台。应用把聊天、Provider 密钥、基于 Termux 的运行时、Android 设备工具、协作状态和修复入口放在一起，让移动端 Agent 的工作过程可以在手机上被查看、控制和恢复。

它不是用于运行不可信 Prompt 或未知远程操作者的通用沙箱。PortalAgent 的设计重点是明确的用户控制：只授予任务需要的能力，保持应用行为可见，并把强大的设备控制工具当作可信自动化来使用。

## 功能

<table>
<tr>
<td width="50%" valign="middle">

### 移动 Agent 工作台

从 Android 发起对话，并在手机原生界面中保留 Agent 回复、工具活动、附件和会话控制。

支持 **Codex** 和 **Claude** 的 Provider 工作流。

</td>
<td width="50%">
  <img src="../images/readme/home.png" alt="PortalAgent 主页聊天工作台" width="100%" />
</td>
</tr>
<tr>
<td width="50%" valign="middle">

### 协作运行时

绑定 Driver 工作区，管理本机 Slave 角色，并直接在手机上查看 AgentServer/Loom 的协作状态。

当 Android 设备需要作为移动 Agent 节点参与任务，而不只是远程屏幕时，可以使用这部分能力。

</td>
<td width="50%">
  <img src="../images/readme/collaboration.png" alt="PortalAgent 协作运行时面板" width="100%" />
</td>
</tr>
<tr>
<td width="50%" valign="middle">

### 用户可控的设备工具

按需向 Agent 暴露 Android 能力：截图、UI 树、点击、文本输入、剪贴板、相机、传感器、网络状态、电池状态，以及 ADB 辅助回退。

这些能力应保持可见，并且只在当前任务需要时授予。

</td>
<td width="50%">
  <img src="../images/readme/api-tools.png" alt="PortalAgent 设备 API 自检页面" width="100%" />
</td>
</tr>
<tr>
<td width="50%" valign="middle">

### 访问控制与修复入口

在应用内查看自动化设置、应用/文件访问边界、终端偏好、插件和环境修复工具。

如果初始化被中断，可以通过修复流程重新检查本地运行时和 Android 能力桥接。

</td>
<td width="50%">
  <img src="../images/readme/settings.png" alt="PortalAgent 设置页面" width="100%" />
</td>
</tr>
</table>

**还包括：**

- **本地 Android 运行时** - 基于 Termux 的终端组件，以及用于 Agent 工具的 Ubuntu proot 环境。
- **Provider 隔离** - Codex 和 Claude 工作流使用各自的配置、密钥、Prompt 和历史记录。
- **权限优先的操作方式** - Android 截屏、无障碍、存储、相机、传感器和应用操作能力都作为用户决策暴露。
- **工作区限制** - 文件和应用操作可以收窄到允许的目录和包名。
- **运行时修复** - 当 Provider 工具、协作二进制或 Android 桥接失败时，可以重新运行环境检查。
- **真机验证路径** - 使用标准 Gradle 和 ADB 命令在已连接 Android 设备上构建、安装和测试。

---

## 支持的 Agent

PortalAgent 聚焦于可以在内置 Linux 环境中运行的 CLI 编码 Agent 的移动工作流。

<p>
  <kbd>Codex</kbd> &nbsp;
  <kbd>Claude Code</kbd>
</p>

Provider 支持仍处于实验阶段。用于长时间任务前，请先在目标设备上验证。

---

## 安装

### Android APK

从 [`release/`](../../release/) 安装预构建 APK；如果项目 Releases 中提供了正式包，也可以从 Releases 下载。

```powershell
adb devices
adb install -r release\portal-agent_apt-android-7-debug_universal.apk
```

首次运行：

1. 打开 PortalAgent，并在本地环境准备期间保持应用在前台。
2. 打开 Keys 页面，配置要使用的 Provider。
3. 只授予当前任务需要的 Android 能力。
4. 回到 Home 开始聊天，或打开 Collaboration 进入可信远程协作流程。

如果初始化被中断，使用 `Settings -> App Settings -> Environment Repair`。

### 从源码构建

要求：

- JDK 21。
- Android SDK 36。
- Android NDK `29.0.14206865`。
- Git LFS。
- Windows 上执行下面命令时使用 PowerShell。

```powershell
git lfs install
git lfs pull
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

安装本地构建的 debug APK：

```powershell
adb install -r app\build\outputs\apk\debug\portal-agent_apt-android-7-debug_universal.apk
```

检查已安装包版本：

```powershell
adb shell dumpsys package com.portalagent | findstr /i "versionName versionCode"
```

---

## 信任与安全

PortalAgent 是实验性的本地自动化应用。用户授予权限后，它可以把较强的手机控制能力暴露给本地 Agent 和协作工作流。

不要把 PortalAgent 视为强化过的安全边界。权限弹窗、工作区设置和工具检查主要用于降低误操作风险，并让行为更容易被查看；它们不能保证 Prompt、工具、依赖、远程服务或底层 Android/Linux 运行时一定不会出现非预期行为。

请只在你愿意信任该设备上的 Agent、Prompt、仓库、账号和远程协作者时使用 PortalAgent。涉及敏感应用或私有数据时，应先确认所需权限，并理解相应风险。

启用能力前，请确认 Agent 是否应当被允许：

- 查看其它应用的屏幕内容。
- 点击、输入、滚动或启动指定应用。
- 读取已批准目录中的文件。
- 使用相机、剪贴板、传感器或设备状态工具。
- 连接远程协作服务。

当前产品边界是权限优先、工具级别的控制模型。它不是完整的操作系统沙箱、恶意软件隔离层，也不能防御恶意指令。

安全策略：[SECURITY.md](../../SECURITY.md)

---

## 开发

详细设计说明位于 [`docs/`](../)。运行时内部结构、兼容性说明和实现计划应保留在文档目录中，避免让 README 开头变得过重。

推荐入口：

- [`docs/architecture/agent_behavior_restriction_plan.md`](../architecture/agent_behavior_restriction_plan.md)
- [`docs/architecture/termux_api_full_integration.md`](../architecture/termux_api_full_integration.md)
- [`docs/architecture/chat_copy_and_browser_search_stability.md`](../architecture/chat_copy_and_browser_search_stability.md)

仓库结构：

| 路径 | 用途 |
| --- | --- |
| `app/` | Android 应用、产品 UI、初始化编排、手机工具、Provider 页面和协作 UI。 |
| `terminal-emulator/`, `terminal-view/`, `termux-shared/` | 基于 Termux 的终端和 Android 共享库。 |
| `docs/` | 架构说明、计划、截图和项目文档。 |
| `release/` | 本地跟踪的 APK 产物和由 Git LFS 管理的大文件。 |
| `.github/` | Issue 模板、工作流、赞助信息和依赖自动化配置。 |

欢迎提交 Issues 和 Pull Requests。有效的问题报告通常包括设备型号、Android 版本、ABI、PortalAgent 版本、权限状态、已脱敏的初始化日志，以及复现 UI 行为所需的截图。

请不要附带原始 API Key、工作区 Token、未脱敏的私人截图或私人文件列表。

## License

PortalAgent 基于 Termux 派生代码构建，并保留对应的许可模式。GPLv3 和上游库例外见 [LICENSE.md](../../LICENSE.md)。
