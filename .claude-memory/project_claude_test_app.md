---
name: Claude_code_test_app 项目状态
description: Termux-based Android app，自动部署 Ubuntu proot + Claude Code，含简化UI/API Key管理/Android API HTTP 桥
type: project
originSessionId: 3bf05154-517a-4745-8bfa-918c03ae7ab3
---
项目路径：C:\ZRS_Works\Claude_code_test_app
Git 仓库：https://github.com/Zeta112233/Claude_code_Android_app.git（remote 名 `app`）

**Why:** 用户想在 Android 上通过 Termux 自动安装 Ubuntu proot 环境并部署 Claude Code，同时提供对非技术用户友好的 ChatGPT 风格简化 UI，以及 Android 硬件 API 的调用能力。机构使用自建平台 `https://code.ai.cs.ac.cn`，API Key 以 `ms-` 前缀开头。

**How to apply:** 理解项目时注意 Phase 划分，当前已完成 Phase 1-4，Chat UI 已验证可用。

---

## 已完成阶段

### Phase 1 & 2：Ubuntu 自动安装（AutoUbuntuManager）
- proot-distro 4.38 只读 `$PREFIX/etc/proot-distro/ubuntu.sh`（系统文件），用户配置目录对 URL 无效
- 本地 bundle 通过 URL-based sed 替换（而非变量名匹配）注入系统文件
- backup_sys/restore_sys 模式保证多次镜像尝试从干净状态出发
- signal 11 问题：在系统 ubuntu.sh 末尾追加 `distro_setup() { true; }` 绕过

### Phase 3：Claude Code 自动部署（AutoClaudeManager）
- 在 Ubuntu 首次登录时通过 .bashrc source hook 触发交互式安装向导
- 内容：TUNA apt 镜像 → 安装 nodejs npm curl → npmmirror → 安装 claude-code → API Key 配置 → 自我清除
- **已优化**：去掉 `| grep | tail` 管道过滤，apt 和 npm 输出直接显示（含进度条）；加 `[1/3][2/3][3/3]` 步骤编号

### Phase 3 补充：Android API HTTP 桥（ApiHttpBridgeServer）
- **根本原因**：Termux 二进制是 Android bionic 编译，Ubuntu glibc 环境无法执行
- **解决方案**：Android app 内运行 HTTP 服务器，监听 `127.0.0.1:17681`
- 端点：`GET /battery /camera /sensors /wifi /clipboard`
- Ubuntu 内用 `curl -s http://127.0.0.1:17681/battery` 获取实时数据

### Phase 4：简化 UI + 底部导航栏（三 Tab）

#### 底部导航结构
| Tab | ID | Fragment |
|-----|----|----------|
| 主页 | `nav_home` | `HomeFragment` |
| 终端 | `nav_terminal` | 原 TermuxActivity 终端视图 |
| 密钥 | `nav_apikey` | `ApiKeyFragment` |

- 默认启动显示"终端"Tab（保持原有体验）
- Tab 切换用 show/hide 而非 replace（保留 Fragment 实例，避免重建）
- `home_fragment_container`（FrameLayout）复用承载 HomeFragment 和 ApiKeyFragment

#### 简化 UI（HomeFragment）—— ChatGPT 风格 ✅ 已验证可用
- RecyclerView + ChatAdapter（用户蓝色右侧气泡 / Claude 灰色左侧气泡）
- **架构：ProcessBuilder 独立子进程**，完全不占用终端 session
  - 命令：`bash proot-distro login ubuntu -- sh -c '...'`
  - stdout 输出 JSONL，后台线程逐行解析，主线程更新 UI
  - type=assistant 事件取最后一条快照；type=result 表示完成
- **API Key 内联注入**（关键）：`ANTHROPIC_API_KEY='...' ANTHROPIC_BASE_URL='...' claude -p ...`
  - 不用 `--dangerously-skip-permissions`（root 下被拒绝）
  - 不依赖 `.bashrc` 加载（非交互 shell 被早返回阻断）
- 会话控制：启动（终端新 session）/ 停止（destroy 子进程）/ 重启 / 新建会话（清除 --continue 标志）

#### API Key 管理（ApiKeyFragment）
- 存储：`SharedPreferences`（prefs name: `api_keys_store`）
- 每条含：id / alias / value / **baseUrl**（ANTHROPIC_BASE_URL，空=官方默认）
- 添加对话框默认预填 `https://code.ai.cs.ac.cn`
- "设为当前" → Java File I/O 写入 ubuntu `~/.bashrc`（无终端命令）+ 供"启动"按钮读取

#### "启动"按钮（交互式终端 Claude）
- 点击时从 ApiKeyStore 读取激活 Key，inline 注入命令：
  `proot-distro login ubuntu -- sh -c 'ANTHROPIC_API_KEY='...' claude'`
- 不依赖 `.bashrc`，启动后直接已登录

### MCP Server Phase 1+2（2026-04-30 完成）
- 新 MCP HTTP 服务器监听 `127.0.0.1:8765`，Claude Code 通过 `claude mcp add --transport http android-mcp http://127.0.0.1:8765/mcp` 接入
- **为何不能去掉 proot**：Claude Code install.cjs 硬拒 `android arm64`，必须在 Ubuntu glibc 环境里运行
- **proot 文件边界**：`/storage/emulated/0/` 在 proot 内不可见；解决方案：MCP tool 直接返回 base64，不传路径
- **CameraX 无头拍照**：用 `ProcessLifecycleOwner.get()` 绑定生命周期，不需要 Activity；CountDownLatch 同步等待（12s timeout）
- **依赖**：build.gradle 新增 `androidx.camera:camera-*:1.3.4` + `androidx.lifecycle:lifecycle-process:2.7.0`
- **工具清单**：`android.get_status` / `camera.take_photo` / `file.check_exists` / `file.list` / `file.read`
- AutoClaudeManager 安装脚本末尾加 `su -l claude -c "claude mcp add ..."` 步骤（Step 6），注册 MCP 工具
- 审计日志写到 Termux HOME `~/mcp-audit.log`（[ISO8601Z] tool=xxx task_id=xxx result=ok/error）

---

## 核心文件

| 文件 | 说明 |
|------|------|
| `TermuxActivity.java` | 主 Activity，含 setupBottomNav / setActiveApiKey（Java File I/O）/ addNewSessionFromHome / sendTerminalInput / hasActiveSession |
| `HomeFragment.java` | 简化 UI Fragment，ProcessBuilder 独立进程，API Key 内联注入 |
| `ChatAdapter.java` | RecyclerView 适配器（USER/ASSISTANT 两种气泡） |
| `ChatMessage.java` | 消息数据类 |
| `ApiKeyFragment.java` | API Key 管理页面，含 Base URL 输入 |
| `ApiKeyStore.java` | Key 持久化存储（含 baseUrl 字段） |
| `ApiKeyAdapter.java` | Key 列表 RecyclerView 适配器 |
| `autotasks/AutoUbuntuManager.java` | Ubuntu 安装脚本生成 |
| `autotasks/AutoClaudeManager.java` | Claude Code 安装向导脚本 |
| `autotasks/ApiHttpBridgeServer.java` | HTTP API 桥，port 17681 |
| `mcp/McpHttpServer.java` | MCP JSON-RPC 2.0 服务器，port 8765 |
| `mcp/McpTool.java` | MCP 工具接口 |
| `mcp/AuditLogger.java` | 审计日志 ~/mcp-audit.log |
| `mcp/tools/AndroidStatusTool.java` | android.get_status 工具 |
| `mcp/tools/CameraTool.java` | camera.take_photo 工具（CameraX） |
| `mcp/tools/FileTool.java` | file.check_exists / file.list / file.read 工具 |

---

## 重要技术细节
- Java source/target level = 8，不能用 Java 9+ 语法
- minSdkVersion = 21
- ubuntu `.bashrc` 顶部有 `[ -z "$PS1" ] && return`，非交互 shell 永远不会执行后续 env var 行
- Claude Code 在 proot ubuntu root 环境下拒绝 `--dangerously-skip-permissions`
- ADB 设备 serial：`R1LM45S11867DC`
- 构建命令：`./gradlew assembleDebug`
- 安装命令：`adb -s R1LM45S11867DC install -r app/build/outputs/apk/debug/claude-code-test-app_apt-android-7-debug_universal.apk`
