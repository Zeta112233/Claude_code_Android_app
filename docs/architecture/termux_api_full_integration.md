# PortalAgent Termux:API 全量接入与功能整合

更新日期：2026-07-05

## 目标

PortalAgent 不再只内置少量设备 API，而是把 Termux:API 的完整 Android 侧能力合入主 App。整合原则是：

- Android 原生能力在 PortalAgent 进程内统一承载。
- Agent 只通过 PortalAgent 暴露的 MCP、HTTP bridge、Termux:API intent/命令路径访问能力。
- 低风险只读能力可以走 HTTP 直连；短信、通话、文件写入、录音、定位等敏感能力保留 Android 权限、用户选择或交互流程。
- 工作目录限制和 App allowlist 仍是 Agent 行为限制的主入口，不能被 API 接入绕过。

## 当前 APP 功能梳理

PortalAgent 当前由以下几层组成：

| 模块 | 作用 |
| --- | --- |
| 主对话 | 本机 Codex / Claude 对话、流式输出、历史会话、上传附件、记忆/技能文件浏览 |
| Termux / Ubuntu runtime | 以 Termux 为底座部署 Ubuntu proot，分别承载 Codex、Claude、AgentServer、Loom 等运行时 |
| API Key 管理 | 按 provider 隔离保存 Codex / Claude 的 key、base URL、环境变量和配置 |
| Android MCP | 通过 `McpHttpServer` 暴露屏幕截图、UI 操作、文件读取、App 打开、ADB companion 等工具 |
| API HTTP bridge | 在 Android 进程内监听 `127.0.0.1:17681`，供 Ubuntu 内的 Agent 用 `curl` 调用低风险设备能力 |
| Termux:API 内核 | 内置完整 `com.termux.api` receiver、services、activities、providers 和 API classes |
| 协作运行时 | AgentServer workspace、Loom driver/slave、本机 slave 管理和任务派发 |
| 自动化 Boost | 从成功 MCP 调用轨迹沉淀低风险可复用操作配方 |
| 工作目录/应用限制 | 配置允许目录和允许 Agent 操作的 App，并在 MCP 工具入口执行 Layer 1 防火墙 |

## Termux:API 接入状态

本轮接入完成了 Android 侧 Termux:API 全量内核：

- `TermuxApiReceiver` 已按上游完整分发逻辑接入 45 个 `api_method`。
- `app/src/main/java/com/termux/api/apis/` 已包含完整 API 类集合。
- Manifest 已补齐相关权限、Activity、Service、Provider、NFC tech filter 和共享文件 provider。
- 资源已补齐 `DialogTheme`、`TransparentTheme`、对话布局、NFC XML、分享字符串、KeepAlive service 文案等。
- Gradle 已补齐 `androidx.biometric`、`androidx.media`、`androidx.documentfile`。
- 新增 `TermuxApiCatalog`，把全部 API 以机器可读 JSON 暴露给 App、HTTP bridge 和 `capabilities.json`。

## API 清单

完整目录运行时也可通过以下方式获取：

```bash
curl -s http://127.0.0.1:17681/termux-api/catalog
termux-api-catalog
```

当前登记的 45 个方法级 API：

| api_method | 命令/能力 | 类别 | 风险 | HTTP 直连 |
| --- | --- | --- | --- | --- |
| AudioInfo | `termux-audio-info` | audio | read | - |
| BatteryStatus | `termux-battery-status` | power | read | `/battery` |
| Brightness | `termux-brightness` | display | mutating | - |
| CameraInfo | `termux-camera-info` | camera | read | `/camera` |
| CameraPhoto | `termux-camera-photo` | camera | capture | - |
| CallLog | `termux-call-log` | phone | sensitive_read | - |
| Clipboard | `termux-clipboard-get/set` | clipboard | sensitive_read_write | `/clipboard` |
| ContactList | `termux-contact-list` | contacts | sensitive_read | - |
| Dialog | `termux-dialog` | ui | user_interactive | - |
| Download | `termux-download` | network | mutating | - |
| Fingerprint | `termux-fingerprint` | auth | user_interactive | - |
| InfraredFrequencies | `termux-infrared-frequencies` | infrared | read | - |
| InfraredTransmit | `termux-infrared-transmit` | infrared | mutating | - |
| JobScheduler | `termux-job-scheduler` | jobs | mutating | - |
| Keystore | `termux-keystore` | security | sensitive | - |
| Location | `termux-location` | location | sensitive_read | - |
| MediaPlayer | `termux-media-player` | media | mutating | - |
| MediaScanner | `termux-media-scan` | media | mutating | - |
| MicRecorder | `termux-microphone-record` | audio | capture | - |
| Nfc | `termux-nfc` | nfc | user_interactive | - |
| NotificationList | `termux-notification-list` | notifications | sensitive_read | - |
| Notification | `termux-notification` | notifications | mutating | - |
| NotificationChannel | `termux-notification-channel` | notifications | mutating | - |
| NotificationRemove | `termux-notification-remove` | notifications | mutating | - |
| NotificationReply | `termux-notification-reply` | notifications | sensitive_write | - |
| SAF | `termux-saf-*` | storage | sensitive_read_write | - |
| Sensor | `termux-sensor` | sensors | read | `/sensors` |
| Share | `termux-share` | sharing | user_interactive | - |
| SmsInbox | `termux-sms-inbox` | sms | sensitive_read | - |
| SmsSend | `termux-sms-send` | sms | sensitive_write | - |
| StorageGet | `termux-storage-get` | storage | user_interactive | - |
| SpeechToText | `termux-speech-to-text` | audio | capture | - |
| TelephonyCall | `termux-telephony-call` | phone | sensitive_write | - |
| TelephonyCellInfo | `termux-telephony-cellinfo` | phone | sensitive_read | - |
| TelephonyDeviceInfo | `termux-telephony-deviceinfo` | phone | sensitive_read | - |
| TextToSpeech | `termux-tts-speak/engines` | audio | mutating | - |
| Toast | `termux-toast` | ui | mutating | - |
| Torch | `termux-torch` | camera | mutating | - |
| Usb | `termux-usb` | usb | user_interactive | - |
| Vibrate | `termux-vibrate` | haptics | mutating | - |
| Volume | `termux-volume` | audio | mutating | - |
| Wallpaper | `termux-wallpaper` | display | mutating | - |
| WifiConnectionInfo | `termux-wifi-connectioninfo` | wifi | read | `/wifi` |
| WifiScanInfo | `termux-wifi-scaninfo` | wifi | sensitive_read | - |
| WifiEnable | `termux-wifi-enable` | wifi | mutating | - |

## 对 Agent 的暴露方式

当前分三类暴露：

| 暴露方式 | 范围 | 说明 |
| --- | --- | --- |
| HTTP 直连 | `/battery`、`/camera`、`/sensors`、`/wifi`、`/clipboard`、`/termux-api/catalog` | 供 Ubuntu/proot 内 Agent 用 `curl` 调用。只保留低风险读接口和目录查询。 |
| Termux:API receiver | 全部 45 个 `api_method` | 走 Android intent、权限检查、Activity/Service/Provider 流程。敏感 API 不做无门槛 HTTP 直连。 |
| MCP 工具 | 屏幕、UI、文件、App、ADB、相机等 PortalAgent 自有工具 | 已接入工作目录限制和允许操作 App 列表，是 Agent 行为限制的主要执行点。 |

## 安全边界

不要把“全部 API 接入”理解为“全部 API 可以被 Agent 静默调用”。

- HTTP bridge 仍是 loopback-only，且默认只开放少量低风险读端点。
- `SmsSend`、`TelephonyCall`、`MicRecorder`、`Location`、`ContactList`、`CallLog`、`SAF` 等敏感能力必须依赖 Android 权限、用户选择或额外策略。
- 文件系统边界仍由工作目录限制和后续 SandboxRunner 实现，Termux:API 的 SAF/StorageGet 只能作为用户选择文件/目录的补充入口。
- App 操作边界仍由 `WorkspaceAccessPolicy` 在 MCP 工具入口执行；微容器不能替代 UI/Accessibility/ADB 层的 App allowlist。

## 后续整合方向

1. 给 `TermuxApiCatalog` 增加 UI 展示页，在“设备 API 工具”中显示完整能力、权限状态和风险等级。
2. 对少数可安全自动化的 Termux:API 增加显式 MCP 工具，例如只读音频信息、音量查询、Toast 测试等。
3. 高风险 API 统一走“用户确认 + 审计日志 + 单次授权”流程，避免 Agent 静默发送短信、拨号、读取联系人或录音。
4. Layer 2 引入 SandboxRunner 后，将 shell/proot 进程的文件系统视图收窄到允许目录和必要桥接文件。
