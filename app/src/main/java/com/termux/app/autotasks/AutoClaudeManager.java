package com.termux.app.autotasks;

import androidx.annotation.NonNull;

import com.termux.app.TermuxActivity;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * native-termux branch — installs Claude Code natively in Termux (no proot needed).
 *
 * Strategy:
 *   1. Download @anthropic-ai/claude-code-linux-arm64 (glibc ARM64 binary) via npm pack
 *   2. Use patchelf to replace the ELF interpreter with the one from the Ubuntu proot rootfs
 *   3. Create ~/bin/claude wrapper that sets LD_LIBRARY_PATH to Ubuntu glibc libs
 *   4. Configure API key and register MCP server
 *
 * The Ubuntu rootfs is still installed (for AgentServer), and its glibc is reused
 * here as a shared library source — but claude itself runs directly in Termux context.
 *
 * The setup script runs in the Termux terminal (not inside proot), triggered by
 * AutoUbuntuManager right after Ubuntu is installed and before login.
 */
public class AutoClaudeManager {

    /** Path of the Termux-side native setup script, relative to filesDir. */
    static final String INNER_SCRIPT_REL = "home/.claude-native-setup.sh";

    private final TermuxActivity mActivity;

    public AutoClaudeManager(@NonNull TermuxActivity activity) {
        mActivity = activity;
        Thread t = new Thread(this::writeNativeScript, "claude-native-setup-write");
        t.setDaemon(true);
        t.start();
    }

    /** Absolute path of the native setup script (fixed, doesn't depend on thread completion). */
    @NonNull
    public String getInnerScriptPath() {
        return new File(mActivity.getFilesDir(), INNER_SCRIPT_REL).getAbsolutePath();
    }

    // -------------------------------------------------------------------------

    private void writeNativeScript() {
        File scriptFile = new File(mActivity.getFilesDir(), INNER_SCRIPT_REL);
        try {
            scriptFile.getParentFile().mkdirs();
            try (FileWriter w = new FileWriter(scriptFile)) {
                w.write(buildClaudeNativeScript());
            }
        } catch (IOException ignored) {}
    }

    /**
     * Bash script that runs in the TERMUX shell (not inside proot) to install Claude Code.
     *
     * Steps:
     *   1. Idempotent guard (~/bin/claude already exists → self-clean and exit)
     *   2. Verify Ubuntu rootfs glibc is available (needed for patchelf target)
     *   3. pkg install nodejs patchelf (if absent)
     *   4. npm pack @anthropic-ai/claude-code-linux-arm64
     *   5. patchelf --set-interpreter → create ~/bin/claude wrapper with LD_LIBRARY_PATH
     *   6. Interactive API key + base URL selection → write to ~/.bashrc
     *   7. Register Android MCP server (claude mcp add)
     *   8. Self-cleanup
     */
    private String buildClaudeNativeScript() {
        StringBuilder s = new StringBuilder();
        s.append("#!/bin/bash\n");
        s.append("# Claude Code native setup — runs in Termux, not inside proot\n\n");

        // ── 幂等保护 ──────────────────────────────────────────────────────────
        s.append("if [ -f \"$HOME/bin/claude\" ]; then\n");
        s.append("    echo '[*] Claude Code 已安装 (~/bin/claude)，跳过'\n");
        s.append("    sed -i '/.claude-native-setup/d' ~/.bashrc 2>/dev/null\n");
        s.append("    rm -f ~/.claude-native-setup.sh\n");
        s.append("    return 0 2>/dev/null || exit 0\n");
        s.append("fi\n\n");

        // ── 欢迎界面 ─────────────────────────────────────────────────────────
        s.append("echo ''\n");
        s.append("echo '================================'\n");
        s.append("echo '  Claude Code 首次配置（原生）'\n");
        s.append("echo '================================'\n");
        s.append("echo ''\n\n");

        // ── 校验 Ubuntu glibc 存在 ──────────────────────────────────────────
        s.append("UBUNTU_ROOTFS=\"$PREFIX/var/lib/proot-distro/installed-rootfs/ubuntu\"\n");
        s.append("UBUNTU_LINKER=\"$UBUNTU_ROOTFS/usr/lib/ld-linux-aarch64.so.1\"\n");
        s.append("UBUNTU_GLIBC_LIBS=\"$UBUNTU_ROOTFS/usr/lib/aarch64-linux-gnu:$UBUNTU_ROOTFS/lib/aarch64-linux-gnu\"\n\n");
        s.append("if [ ! -f \"$UBUNTU_LINKER\" ]; then\n");
        s.append("    echo '[!] Ubuntu rootfs glibc 尚未就绪，安装中断'\n");
        s.append("    return 1 2>/dev/null || exit 1\n");
        s.append("fi\n\n");

        // ── Step 1: 安装 nodejs + patchelf ───────────────────────────────────
        s.append("command -v npm >/dev/null 2>&1 || {\n");
        s.append("    echo '[1/4] 安装 Node.js + npm...'\n");
        s.append("    pkg install -y nodejs 2>&1\n");
        s.append("}\n");
        s.append("command -v patchelf >/dev/null 2>&1 || {\n");
        s.append("    echo '[1/4] 安装 patchelf...'\n");
        s.append("    pkg install -y patchelf 2>&1\n");
        s.append("}\n");
        s.append("npm config set registry https://registry.npmmirror.com 2>/dev/null\n\n");

        // ── Step 2: 下载 linux-arm64 glibc 二进制 ────────────────────────────
        s.append("echo '[2/4] 下载 Claude Code linux-arm64 二进制...'\n");
        s.append("cd \"$HOME\"\n");
        s.append("npm pack @anthropic-ai/claude-code-linux-arm64 2>&1 | tail -3\n");
        s.append("_tarball=$(ls anthropic-ai-claude-code-linux-arm64-*.tgz 2>/dev/null | head -1)\n");
        s.append("if [ -z \"$_tarball\" ]; then\n");
        s.append("    echo '[!] 下载失败，请检查网络后重试'\n");
        s.append("    return 1 2>/dev/null || exit 1\n");
        s.append("fi\n");
        s.append("mkdir -p \"$HOME/.claude-native\"\n");
        s.append("tar xzf \"$_tarball\" -C \"$HOME/.claude-native\" 2>/dev/null\n");
        s.append("rm -f \"$_tarball\"\n");
        s.append("_bin=\"$HOME/.claude-native/package/claude\"\n");
        s.append("if [ ! -f \"$_bin\" ]; then\n");
        s.append("    echo '[!] 未找到 claude 二进制'\n");
        s.append("    return 1 2>/dev/null || exit 1\n");
        s.append("fi\n\n");

        // ── Step 3: patchelf + 创建 wrapper ──────────────────────────────────
        s.append("echo '[3/4] 修复 ELF interpreter 并创建启动脚本...'\n");
        s.append("patchelf --set-interpreter \"$UBUNTU_LINKER\" \"$_bin\"\n");
        s.append("chmod +x \"$_bin\"\n");
        s.append("mkdir -p \"$HOME/bin\"\n");
        // wrapper 用 printf 写，避免 here-doc 变量展开问题
        s.append("printf '#!/bin/bash\\nexport LD_LIBRARY_PATH=\"%s\"\\nexec \"%s\" \"$@\"\\n' \\\n");
        s.append("    \"$UBUNTU_GLIBC_LIBS\" \"$_bin\" > \"$HOME/bin/claude\"\n");
        s.append("chmod +x \"$HOME/bin/claude\"\n");
        // 将 ~/bin 加入 PATH（幂等）
        s.append("grep -qF 'HOME/bin' ~/.bashrc 2>/dev/null || {\n");
        s.append("    printf '\\nexport PATH=\"$HOME/bin:$PATH\"\\n' >> ~/.bashrc\n");
        s.append("}\n");
        s.append("export PATH=\"$HOME/bin:$PATH\"\n\n");

        // ── Step 4: API Key 配置 ──────────────────────────────────────────────
        s.append("echo ''\n");
        s.append("echo '认证方式：'\n");
        s.append("echo '  1) API 密钥（推荐，无需浏览器）'\n");
        s.append("echo '  2) 跳过（稍后手动配置）'\n");
        s.append("printf '选择 [1/2，默认 1]: '\n");
        s.append("read -r _auth\n");
        s.append("[ -z \"$_auth\" ] && _auth=1\n\n");

        s.append("if [ \"$_auth\" = \"1\" ]; then\n");
        s.append("    printf 'Anthropic API Key: '\n");
        s.append("    read -r _key\n");
        s.append("    if [ -n \"$_key\" ]; then\n");
        s.append("        echo ''\n");
        s.append("        echo 'API 接入点：'\n");
        s.append("        echo '  1) 官方  https://api.anthropic.com'\n");
        s.append("        echo '  2) 中科院 https://code.ai.cs.ac.cn  [国内推荐]'\n");
        s.append("        echo '  3) 自定义 URL'\n");
        s.append("        printf '选择 [1/2/3，默认 2]: '\n");
        s.append("        read -r _ep\n");
        s.append("        [ -z \"$_ep\" ] && _ep=2\n");
        s.append("        case \"$_ep\" in\n");
        s.append("            1) _base='' ;;\n");
        s.append("            3) printf 'Base URL: '; read -r _base ;;\n");
        s.append("            *) _base='https://code.ai.cs.ac.cn' ;;\n");
        s.append("        esac\n");
        s.append("        grep -qF 'ANTHROPIC_API_KEY' ~/.bashrc 2>/dev/null || {\n");
        s.append("            printf '\\n# Claude Code\\nexport ANTHROPIC_API_KEY=\"%s\"\\n' \"$_key\" >> ~/.bashrc\n");
        s.append("            [ -n \"$_base\" ] && printf 'export ANTHROPIC_BASE_URL=\"%s\"\\n' \"$_base\" >> ~/.bashrc\n");
        s.append("        }\n");
        s.append("        export ANTHROPIC_API_KEY=\"$_key\"\n");
        s.append("        [ -n \"$_base\" ] && export ANTHROPIC_BASE_URL=\"$_base\"\n");
        s.append("        echo '[*] API Key 已写入 ~/.bashrc'\n");
        s.append("    fi\n");
        s.append("fi\n\n");

        // ── MCP 注册 ──────────────────────────────────────────────────────────
        s.append("echo '[4/4] 注册 Android MCP Server...'\n");
        s.append("\"$HOME/bin/claude\" mcp add --transport http android-mcp http://127.0.0.1:8765/mcp 2>&1 || true\n");
        s.append("echo '[*] MCP 注册完成'\n\n");

        // ── 自我清除 ──────────────────────────────────────────────────────────
        s.append("sed -i '/.claude-native-setup/d' ~/.bashrc 2>/dev/null\n");
        s.append("rm -f ~/.claude-native-setup.sh\n\n");

        // ── 完成提示 ──────────────────────────────────────────────────────────
        s.append("echo ''\n");
        s.append("echo '安装完成！输入 claude 启动 Claude Code'\n");
        s.append("echo '（无需进入 Ubuntu，直接在终端运行）'\n");
        s.append("echo '================================'\n");
        s.append("echo ''\n");

        return s.toString();
    }
}
