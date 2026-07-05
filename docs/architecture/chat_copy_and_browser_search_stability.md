# 主页复制按钮与浏览器搜索稳定性设计

更新日期：2026-07-05

## 目标

主页聊天气泡需要接近 ChatGPT 网页版的复制体验：

- 输出完成后，在气泡右下角显示图标型复制按钮。
- Assistant 正在流式输出或仍是占位内容时，不显示复制按钮。
- 文本本身支持长按自由选择和系统复制菜单。

同时，浏览器搜索任务需要避免在“打开浏览器、输入查询、读取搜索结果”链路上卡死。

## 复制按钮设计

### 数据层

`ChatMessage` 增加 `outputComplete` 字段作为 UI 判断依据：

- 普通历史消息默认 `true`。
- `ChatMessage.assistantStreaming(...)` 创建的 assistant 输出为 `false`。
- Provider 结束回调、进程结束回调或 session 最终结果到达时，调用 `markLastAssistantOutputComplete()` 将最后一条 assistant 输出标记为完成。
- `ChatTranscriptStore` 持久化 `outputComplete`，历史会话恢复后不会丢失状态。

这个状态必须由数据层驱动，不能只依赖当前 RecyclerView 是否还在刷新。原因是会话恢复、进程异常退出、Codex session transcript 同步都需要一致行为。

### UI 层

用户消息和 assistant 消息布局使用同一个视觉规则：

- 气泡内容仍由 `TextView` 显示，并设置 `android:textIsSelectable="true"`，支持长按自由选择。
- 右下角使用 `ImageButton` + `ic_copy_20`，不再用文字徽标。
- 按钮点击复制完整原文，不复制 Markdown 渲染后的富文本。

显示规则：

| 消息类型 | 显示复制按钮 |
| --- | --- |
| 用户消息 | 文本非空时显示 |
| Assistant 完成消息 | `outputComplete == true` 且文本非空、不是占位符时显示 |
| Assistant 流式消息 | 不显示 |
| Assistant 占位消息 | 不显示 |
| 系统消息 | 仅支持长按选择，不显示右下角按钮 |

### 状态流转

1. 用户发起请求，UI 插入 user 消息。
2. Assistant 开始输出时插入 `assistantStreaming("…")` 或流式正文，此时 `outputComplete=false`。
3. 流式更新期间只更新文本，不显示复制按钮。
4. Provider 返回最终结果或进程结束后，标记最后一条 assistant 输出完成并刷新该项。
5. 完成后右下角复制图标出现。

## 浏览器搜索稳定性设计

浏览器自动化链路的两个易卡点是 UI 树遍历和输入框焦点。

### UI 树遍历预算

`ui.get_accessibility_tree` 必须带预算：

- `max_nodes` 默认 400，返回节点硬上限 1200。
- 访问节点数上限为 `max_nodes * 6`。
- 单次遍历有短超时预算。
- 节点文本截断，避免 WebView 大文本拖慢响应。
- 返回 `visited_nodes`、`max_nodes`、`truncated`，让 Agent 能判断是否需要缩小深度或滚动分段读取。

`ui.click_text` 也必须带遍历预算。它和 UI 树读取一样会进入浏览器 WebView 节点树，不能无上限递归；超出预算时返回明确错误，而不是等待 MCP 请求超时。

### 坐标有效性

`ui.get_accessibility_tree` 返回 `screen_bounds`、`screen_width`、`screen_height` 和 `active_window_bounds`。Agent 使用坐标工具前必须以这些字段为坐标系来源。

`ui.tap` 和 `ui.swipe` 会拒绝屏幕外坐标。Accessibility 的 gesture callback 只能说明系统接受了手势派发，不能证明点中了目标；因此越界坐标必须在工具层返回错误，避免 Agent 把无效点击误判为成功。

### 前台应用判定

Accessibility 的最近窗口事件可能短暂停留在 PortalAgent 自己、输入法或系统浮层上。策略判断顺序为：

1. 优先读取 `getRootInActiveWindow().getPackageName()`，用 active window root package 判断真实操作对象。
2. 如果 active window 是输入法包名，则映射回最近一个真实应用。
3. 如果 active window 真的是 PortalAgent 或系统权限窗口，则不继承最近应用的 allowlist。
4. 如果 active/current 是 PortalAgent 的非 Activity 临时窗口，并且 45 秒内刚通过 `app.open` 启动过同一个最近真实应用，则 observe 和 interact 工具都继承到该应用。
5. 只有拿不到 active window 时，才回退到最近窗口事件和最近启动应用的保守推断。

这样可以修复“Edge 已在前台，但 interact 工具被 `com.portalagent` allowlist 拒绝”的误拦截，同时不会让真实 PortalAgent 页面或系统权限弹窗继承 Edge 权限。

### 输入框兜底

`ui.input_text` 的策略：

1. 优先使用 Android 当前 `FOCUS_INPUT`。
2. 如果没有焦点或焦点不是可编辑节点，读取当前窗口树。
3. 找到第一个可见 `editable` 节点，执行 `ACTION_FOCUS`。
4. 再执行 `ACTION_SET_TEXT`。

这只改变允许 App 内的输入可靠性，不改变应用白名单边界；所有 `ui.*` 调用仍先经过 `WorkspaceAccessPolicy.enforceAccessibilityForeground(...)`。

### 搜索整理能力边界

当前阶段可通过浏览器 UI 树读取搜索结果标题和摘要，足够支持“搜索后整理”的基础流程。但 WebView 对内容暴露不稳定，后续建议补两条能力：

- 网页内容读取工具：在允许网络和用户授权的前提下，由 Agent 直接抓取搜索结果或目标网页正文。
- 截图/OCR 兜底：当 WebView 不暴露文本节点时，用截图识别可见内容。

浏览器 UI 自动化仍负责可见操作和用户可审计路径，不应成为唯一的信息抽取通道。

## 验收标准

- Assistant 流式输出过程中不出现复制按钮。
- Assistant 输出完成后才出现右下角复制图标。
- 用户和 assistant 文本都能长按选择部分文字。
- Edge 允许后，`app.open`、`ui.click_text`、`ui.input_text`、`ui.get_accessibility_tree` 能完成中文搜索链路。
- 深层搜索结果页 UI 树能在预算内返回，必要时通过 `truncated` 提醒 Agent 分段读取。
