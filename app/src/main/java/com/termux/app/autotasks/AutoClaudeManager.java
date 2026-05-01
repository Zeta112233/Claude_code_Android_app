package com.termux.app.autotasks;

import androidx.annotation.NonNull;

import com.termux.app.TermuxActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;

/**
 * native-termux branch — installs Claude Code natively in Termux, NO proot required.
 *
 * Strategy:
 *   1. Bundle assets/musl-linker-aarch64 (ld-musl-aarch64.so.1 from Alpine Linux, ~707 KB)
 *   2. Download @anthropic-ai/claude-code-linux-arm64-musl via npm pack
 *   3. patchelf --set-interpreter → the extracted musl linker
 *   4. Create ~/bin/claude wrapper (no LD_LIBRARY_PATH needed; musl is self-contained)
 *   5. Configure API key and register MCP server
 *
 * Ubuntu rootfs is NOT required. Both Claude and agentserver run natively in Termux.
 */
public class AutoClaudeManager {

    /** Asset name of the bundled musl linker (no extension avoids AAPT2 processing). */
    static final String MUSL_ASSET_NAME = "musl-linker-aarch64";

    /** Path where the musl linker is extracted, relative to filesDir. */
    static final String MUSL_LINKER_REL = "home/.claude-native/ld-musl-aarch64.so.1";

    /** Termux-side native setup script, relative to filesDir. */
    static final String INNER_SCRIPT_REL = "home/.claude-native-setup.sh";

    private final TermuxActivity mActivity;

    public AutoClaudeManager(@NonNull TermuxActivity activity) {
        mActivity = activity;
        Thread t = new Thread(this::prepare, "claude-native-setup-write");
        t.setDaemon(true);
        t.start();
    }

    @NonNull
    public String getInnerScriptPath() {
        return new File(mActivity.getFilesDir(), INNER_SCRIPT_REL).getAbsolutePath();
    }

    // -------------------------------------------------------------------------

    private void prepare() {
        extractMuslLinker();
        writeNativeScript();
    }

    /** Copies assets/musl-linker-aarch64 → home/.claude-native/ld-musl-aarch64.so.1 */
    private void extractMuslLinker() {
        File dest = new File(mActivity.getFilesDir(), MUSL_LINKER_REL);
        if (dest.exists() && dest.length() > 0) return; // already extracted
        dest.getParentFile().mkdirs();
        File tmp = new File(dest.getParent(), "ld-musl-aarch64.so.1.tmp");
        try (InputStream in = mActivity.getAssets().open(MUSL_ASSET_NAME);
             FileOutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            out.flush();
        } catch (IOException e) {
            tmp.delete();
            return;
        }
        if (!tmp.renameTo(dest)) tmp.delete();
        dest.setExecutable(true, false);
    }

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
     * Bash script that runs in the TERMUX shell to install Claude Code natively.
     * Uses the musl variant — self-contained, no Ubuntu glibc dependency.
     */
    private String buildClaudeNativeScript() {
        String muslLinker = new File(mActivity.getFilesDir(), MUSL_LINKER_REL).getAbsolutePath();

        StringBuilder s = new StringBuilder();
        s.append("#!/bin/bash\n");
        s.append("# Claude Code native setup (musl, no Ubuntu required)\n\n");

        // ── 幂等保护 ──────────────────────────────────────────────────────────
        s.append("if [ -f \"$HOME/bin/claude\" ]; then\n");
        s.append("    echo '[*] Claude Code 已安装 (~/bin/claude)'\n");
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

        // ── 校验 musl linker 已提取 ───────────────────────────────────────────
        s.append("_musl='").append(muslLinker).append("'\n");
        s.append("if [ ! -f \"$_musl\" ]; then\n");
        s.append("    echo '[!] musl linker 未就绪，请重启 App 后重试'\n");
        s.append("    return 1 2>/dev/null || exit 1\n");
        s.append("fi\n");
        s.append("chmod +x \"$_musl\"\n\n");

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

        // ── Step 2: 下载 musl 版 binary ───────────────────────────────────────
        s.append("echo '[2/4] 下载 Claude Code linux-arm64-musl 二进制...'\n");
        s.append("cd \"$HOME\"\n");
        s.append("npm pack @anthropic-ai/claude-code-linux-arm64-musl 2>&1 | tail -3\n");
        s.append("_tarball=$(ls anthropic-ai-claude-code-linux-arm64-musl-*.tgz 2>/dev/null | head -1)\n");
        s.append("if [ -z \"$_tarball\" ]; then\n");
        s.append("    echo '[!] 下载失败，请检查网络后重试'\n");
        s.append("    return 1 2>/dev/null || exit 1\n");
        s.append("fi\n");
        s.append("_pkgdir=\"$HOME/.claude-native\"\n");
        s.append("mkdir -p \"$_pkgdir\"\n");
        s.append("tar xzf \"$_tarball\" -C \"$_pkgdir\" 2>/dev/null\n");
        s.append("rm -f \"$_tarball\"\n");
        s.append("_bin=\"$_pkgdir/package/claude\"\n");
        s.append("if [ ! -f \"$_bin\" ]; then\n");
        s.append("    echo '[!] 未找到 claude 二进制'\n");
        s.append("    return 1 2>/dev/null || exit 1\n");
        s.append("fi\n\n");

        // ── Step 3: patchelf + wrapper ────────────────────────────────────────
        s.append("echo '[3/4] 修复 ELF interpreter...'\n");
        // musl linker IS libc — create symlink so musl can resolve its own SONAME
        s.append("ln -sf ld-musl-aarch64.so.1 \"$_pkgdir/libc.musl-aarch64.so.1\" 2>/dev/null\n");
        s.append("patchelf \\\n");
        s.append("    --set-interpreter \"$_musl\" \\\n");
        s.append("    --set-rpath \"$_pkgdir\" \\\n");
        s.append("    \"$_bin\"\n");
        s.append("chmod +x \"$_bin\"\n\n");
        // wrapper — musl is self-contained, no LD_LIBRARY_PATH required.
        // Use full Termux bash path (/bin/bash does not exist on Android).
        // Single-quoted printf format keeps $@ literal so bash writes "$@" to wrapper correctly.
        s.append("mkdir -p \"$HOME/bin\"\n");
        s.append("printf '#!%s/bin/bash\\nexec \"%s\" \"$@\"\\n' \"$PREFIX\" \"$_bin\" > \"$HOME/bin/claude\"\n");
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
        s.append("echo '安装完成！输入 claude 启动 Claude Code（无需 Ubuntu proot）'\n");
        s.append("echo '================================'\n");
        s.append("echo ''\n");

        return s.toString();
    }
}
