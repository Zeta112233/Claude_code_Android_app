# PortalAgent 发布门禁测试方案

本文档定义 PortalAgent 升级 AgentServer、Loom、Codex/Claude Agent 或 Android 设备工具后的发布前测试门禁。目标是让 Agent 能自行运行固定命令、读取结构化报告，并在全部必需项通过后再发布 APK。

## 入口命令

统一入口是：

```powershell
.\scripts\run_release_gate.ps1 -Suite all -Device <adb-serial>
```

常用子集：

```powershell
.\scripts\run_release_gate.ps1 -Suite host
.\scripts\run_release_gate.ps1 -Suite device -Device <adb-serial>
.\scripts\run_release_gate.ps1 -Suite android-tools -Device <adb-serial>
.\scripts\run_release_gate.ps1 -Suite agentserver -Device <adb-serial>
.\scripts\run_release_gate.ps1 -Suite loom -Device <adb-serial>
.\scripts\run_release_gate.ps1 -Suite agent -Device <adb-serial>
```

列出可用 suite：

```powershell
.\scripts\run_release_gate.ps1 -ListSuites
```

只生成计划和报告结构，不执行外部命令：

```powershell
.\scripts\run_release_gate.ps1 -Suite all -DryRun
```

调试脚本或只验证真机探针时，可以跳过 Gradle 项：

```powershell
.\scripts\run_release_gate.ps1 -Suite agentserver -Device <adb-serial> -SkipGradle
```

`-SkipGradle` 会把相关 Gradle 检查标记为 `skipped`，不能作为发布通过证据。

## 发布判定

| Level | 含义 | 发布规则 |
| --- | --- | --- |
| P0 | 必须通过的功能、安全或构建门禁 | 任一失败则不能发布 |
| P1 | 强烈建议通过的可观测性、真机质量或诊断门禁 | 失败时必须在发布说明中解释并确认风险 |
| P2 | 可延后的非阻塞体验项 | 不阻断发布，但应跟踪 |

当前脚本实现 P0/P1。P2 主要保留给人工验收和后续 UI 截图质量检查。

## Suite 设计

### host

在开发机上运行，不依赖真机。

覆盖：

- `:app:testDebugUnitTest`
- `:app:assembleDebug`
- APK 产物解析
- APK sha256 生成
- README/release 关键资产存在性检查
- 发布相关文档占位符和空链接扫描

适用场景：

- 任意代码改动
- 任意运行时资产升级
- 发布前第一道门禁

### device

依赖 ADB 真机。

覆盖：

- ADB 设备解析
- APK 安装
- App 启动
- `dumpsys package com.portalagent` 版本读取
- 截图非空检查
- 最近 logcat 中 PortalAgent fatal crash 扫描

适用场景：

- 任意 APK 发布
- Android UI、权限、运行时初始化相关改动

### android-tools

依赖 ADB 真机和已启动的 PortalAgent。

覆盖：

- ADB forward 到 Android MCP server
- `tools/list` MCP 基础调用
- MCP audit log tail 收集

适用场景：

- MCP 工具新增或修改
- 屏幕截图、无障碍、文件、App 操作、ADB fallback、设备状态能力改动
- Agent 提示词或 Provider 配置改动后，确认工具注册仍可用

### agentserver

覆盖 Host 单测和真机运行时探针。

Host：

- `com.portalagent.agentserver.*`

Device：

- Codex runtime 内 `agentserver` binary 探针
- AgentServer 相关日志 tail 收集

适用场景：

- AgentServer binary 升级
- AgentServer 连接命令变化
- Codex Connector / Claude claudecode 接入逻辑变化
- AgentServer workspace、sandbox、name、token 处理变化

### loom

覆盖 Host 单测和真机运行时探针。

Host：

- `com.portalagent.loom.*`

Device：

- Codex runtime 内 `observer-server`
- Codex runtime 内 `driver-agent`
- Codex runtime 内 `slave-agent`
- Loom 进程扫描

适用场景：

- Loom binary 升级
- Driver binding 逻辑变化
- Observer/Slave 生命周期脚本变化
- local slave registry 或 discovery card 发布逻辑变化

### agent

覆盖 Provider、Session、Chat 相关单测和真机运行时探针。

Host：

- `com.portalagent.provider.*`
- `com.portalagent.session.*`
- `com.portalagent.chat.*`

Device：

- Codex runtime 中 `codex` 和 `~/.codex/config.toml`
- Claude runtime 中 `claude` 和 `~/.claude/settings.json`

适用场景：

- Codex CLI 升级
- Claude Code 升级
- Provider key/base URL 写入逻辑变化
- Home 会话、历史恢复、工具输出渲染变化

## 升级路径

### 升级 AgentServer

必须执行：

```powershell
.\scripts\run_release_gate.ps1 -Suite host
.\scripts\run_release_gate.ps1 -Suite device -Device <adb-serial>
.\scripts\run_release_gate.ps1 -Suite agentserver -Device <adb-serial>
```

人工补充验收：

- 用 AgentServer Web UI 生成 Codex Connector 命令。
- 在手机 Collaboration 页面粘贴并启动。
- 派发一个只读任务：读取设备状态。
- 派发一个受控 Android 工具任务：截图或打开允许 App。
- 停止连接后确认进程清理。
- 检查日志不泄漏 API key、workspace token 或私有截图。

### 升级 Loom

必须执行：

```powershell
.\scripts\run_release_gate.ps1 -Suite host
.\scripts\run_release_gate.ps1 -Suite device -Device <adb-serial>
.\scripts\run_release_gate.ps1 -Suite loom -Device <adb-serial>
```

人工补充验收：

- Driver bind 成功。
- 重复 bind 时复用有效 credentials。
- Observer 启动、状态刷新、停止。
- 本机 Slave 创建、启动、暂停、重启、删除。
- discovery card 发布失败时 fallback 行为可见。

### 升级 Codex/Claude Agent

必须执行：

```powershell
.\scripts\run_release_gate.ps1 -Suite host
.\scripts\run_release_gate.ps1 -Suite device -Device <adb-serial>
.\scripts\run_release_gate.ps1 -Suite agent -Device <adb-serial>
.\scripts\run_release_gate.ps1 -Suite android-tools -Device <adb-serial>
```

人工补充验收：

- Codex 和 Claude 各完成一轮基础聊天。
- 中断响应后 UI 状态恢复。
- 新建会话和恢复历史不串 Provider 数据。
- Agent 调用 MCP 工具时，聊天界面和 audit log 都能看到工具活动。

### 升级 Android MCP / 手机能力

必须执行：

```powershell
.\scripts\run_release_gate.ps1 -Suite host
.\scripts\run_release_gate.ps1 -Suite device -Device <adb-serial>
.\scripts\run_release_gate.ps1 -Suite android-tools -Device <adb-serial>
```

人工补充验收：

- 截图未授权时失败，授权后成功。
- 无障碍未授权时 UI 工具失败。
- 已允许 App 可 observe/click/input。
- 未允许 App 被拒绝。
- 文件工具只能访问允许目录。
- ADB fallback 只在 ADB companion 可用且策略允许时工作。

## 报告产物

每次运行会生成：

```text
release-test-report.json
release-test-summary.md
artifacts/
```

默认目录：

```text
release-test-artifacts/<yyyyMMdd-HHmmss>/
```

可以通过 `-OutputDir` 指定：

```powershell
.\scripts\run_release_gate.ps1 -Suite host -OutputDir .tmp\release-gate-host
```

JSON report 中每条结果包含：

| 字段 | 含义 |
| --- | --- |
| `id` | 稳定测试 ID |
| `suite` | 所属 suite |
| `description` | 检查说明 |
| `level` | P0/P1 |
| `status` | `passed` / `failed` / `dry-run` |
| `durationMs` | 用时 |
| `evidencePath` | 命令输出、截图、日志等证据路径 |
| `failureReason` | 失败原因 |

Agent 发布判断规则：

1. 读取 `release-test-report.json`。
2. 如果存在 `level=P0` 且 `status=failed`，停止发布。
3. 如果存在 `level=P1` 且 `status=failed`，生成风险说明并等待人工确认。
4. 只有 P0 全部通过时，才允许创建 tag 或上传 APK。

## 当前限制

- 真实模型聊天、AgentServer 远程任务派发、Loom Driver OAuth/绑定仍需要人工凭据和远端服务，当前脚本只自动化到本地 runtime probe 与日志收集。
- 截图检查只用文件大小判断是否明显黑屏或空白，后续可以升级为像素检测。
- Gradle 任务需要较长超时。脚本默认 `-GradleTimeoutMinutes 15`，不要用短超时判断单测失败。
- 如果 ADB daemon 在沙箱环境中无法启动，应在本机或已授权环境下重跑；这属于环境失败，不等同于 APK 功能失败。

## 后续增强

- 增加 `-RequireLiveAgent`，在提供测试 API key 时自动跑 Codex/Claude 基础聊天。
- 增加 AgentServer test workspace 参数，自动派发只读任务和受控工具任务。
- 增加 Loom Driver/Slave 远端绑定参数，自动验证 bind、start、stop、delete。
- 增加截图像素检测，替代当前的文件大小阈值。
- 将 `host` suite 接入 GitHub Actions，release workflow 在发布前解析 JSON report。
