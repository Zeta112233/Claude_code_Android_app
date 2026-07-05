# Agent 行为限制三层规划

## 背景

当前“工作目录限制”页面只保存了 Android 应用白名单，并展示默认目录范围；它还没有接入 Codex、Claude、MCP 工具或 AgentServer 的执行链路。因此现阶段它不是安全边界，Agent 仍然可以通过 MCP 工具、CLI shell、ADB fallback 或已有权限访问超出预期的资源。

目标是把限制从 UI 配置推进到可执行边界，并按风险和实现成本分三层落地。

## Layer 1: MCP 工具防火墙

第一层先限制 PortalAgent 自己暴露给模型的 Android MCP 工具。这个层级不解决 CLI shell 对 Ubuntu 文件系统的直接访问，但可以立即降低手机能力误用风险。

### 文件工具

- `file.check_exists`
- `file.list`
- `file.read`

执行前必须校验目标路径的 canonical path 是否落在允许根目录内：

- 当前 provider 的 Ubuntu home 对应 host 路径。
- Android 默认公共目录：`Download`、`Documents`、`Pictures`、`DCIM`、`Movies`、`Music`。

路径校验必须解析符号链接，避免通过 symlink 跳出允许目录。

### 应用和 UI 工具

工作目录限制页中的“允许 Agent 操作的应用”列表是 Android App 操作边界的唯一配置入口。列表应支持按 App 配置能力，而不是一个勾选框授予全部权限：

- `launch`：允许 `app.open` 打开该 App。
- `observe`：允许 `ui.get_accessibility_tree`、`screen.capture`、`adb.screenshot` 观察该 App 的界面。
- `interact`：允许 `ui.tap`、`ui.swipe`、`ui.click_text`、`ui.input_text`、`adb.tap`、`adb.swipe`、`adb.input_text`、`adb.keyevent` 操作该 App。
- `adb`：允许通过宿主机 ADB Companion 对该 App 执行 ADB 通道能力。ADB 操作必须同时满足 `adb` 和对应的 `observe` / `interact` 能力。

执行规则：

- `app.open` 只允许打开具备 `launch` 能力的包名。
- `ui.get_accessibility_tree` 和 `screen.capture` 必须先确认当前前台应用具备 `observe` 能力。
- `ui.tap`、`ui.swipe`、`ui.click_text`、`ui.input_text` 必须先确认当前前台应用具备 `interact` 能力。
- `adb.screenshot` 必须先通过 ADB Companion 读取当前前台包名，并确认该包名同时具备 `observe` 和 `adb` 能力。
- `adb.tap`、`adb.swipe`、`adb.input_text`、`adb.keyevent` 必须先通过 ADB Companion 读取当前前台包名，并确认该包名同时具备 `interact` 和 `adb` 能力。

低风险发现工具可以保留：

- `app.get_current_activity`
- `adb.get_status`
- `adb.current_activity`
- `android.get_status`

这些工具用于让用户或 Agent 判断当前状态，不直接执行跨应用操作。

`camera.take_photo` 不适合直接绑定到“前台 App 白名单”，因为它采集的是物理环境而不是某个 App 的界面。第一层暂时仍依赖 Android `CAMERA` 权限控制；后续应补一个独立的能力开关或一次性确认策略。

### 审计

防火墙拒绝应作为 tool error 返回，并继续进入现有 MCP audit/trace 流，便于排查和回放。

## Layer 2: 手机端微容器 / Sandbox Runner

第二层解决 CLI shell 本身的文件系统访问问题。它应该成为 Codex、Claude、AgentServer 和 Loom 启动命令的统一入口，而不是让各处直接调用 `proot-distro login`。

推荐实现为 `SandboxRunner`：

- 统一构造 proot 启动命令。
- 每个任务进入受控 `/workspace`。
- 只 bind 用户允许的目录。
- 不默认暴露整个 `/storage`。
- 不默认暴露另一个 provider 的 home。
- 可选使用临时 HOME 或 provider scoped HOME。
- 支持只读挂载和可写挂载区分。
- 所有启动参数和挂载表写入审计日志。

这不是 Docker 级强隔离。Android 手机上没有常规容器运行时，proot 更接近“文件系统视图隔离”。但对当前 PortalAgent 的误操作控制目标，它是比 prompt 可靠得多的执行边界。

微容器不适合作为 Android App UI 操作限制的主边界。原因是 `AccessibilityService`、`MediaProjection`、ADB Companion、`PackageManager`、相机等能力都运行在 Android App / 系统服务侧，不在 Ubuntu/proot 文件系统视图里；Agent 即使在 proot 里被限制，仍可能通过 MCP HTTP 工具请求 Android 侧执行跨 App 操作。因此 App 限制必须先在 Layer 1 的工具入口强制执行，微容器只能作为 Layer 2 限制 shell 和文件系统访问。

微容器可以作为辅助边界：

- 让 shell 看不到未授权脚本、token、配置和外部目录。
- 让 Agent 无法直接修改工作目录限制配置文件或运行时数据库。
- 只把受控 MCP endpoint、capabilities 文件和允许工作目录暴露给 provider。
- 把 Android App 操作统一收口到 MCP 工具，避免 shell 绕开审计直接调用隐藏入口。

## Layer 3: Prompt / AGENTS / CLAUDE 软约束

第三层用于降低模型主动越界的概率：

- 在 `AGENTS.md`、`CLAUDE.md` 和 provider 配置里写明允许目录、允许 App、禁止行为。
- 在 capabilities 文件里暴露当前限制状态。
- 在任务开始前把限制摘要注入上下文。

这层不能当安全边界。模型可能忽略、误解或通过 shell 绕过提示约束，所以它只能作为辅助。

## 落地顺序

1. 先实现 Layer 1，让现有设置页真正影响 MCP 工具。
2. 再引入 `SandboxRunner`，把所有 provider 和协作进程启动统一收口。
3. 最后补充 prompt 约束和 UI 展示，让用户清楚当前限制状态。

## 验收标准

- 未勾选 App 时，`app.open`、`ui.*`、`screen.capture`、`adb.*` 执行型工具会拒绝操作。
- 勾选某个 App 后，只允许在该 App 前台执行 UI、截图和 ADB 操作。
- `file.*` 不能读取允许目录外的文件，也不能通过 symlink 逃逸。
- 拒绝原因清晰返回给 Agent，并进入 audit/trace。
- 后续 Layer 2 完成后，Codex/Claude shell 也无法访问未挂载目录。
