package com.termux.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.termux.R;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.function.Consumer;

/**
 * AgentServer 配置与管理页面。
 *
 * agentserver 是静态链接的 ARM64 二进制，直接运行在 Termux 环境，不需要 proot/Ubuntu。
 * 二进制位于 ~/bin/agentserver，由 AutoAgentServerManager 从 APK assets 提取。
 */
public class AgentServerFragment extends Fragment {

    private static final String PREFS_NAME       = "agentserver_config";
    private static final String KEY_SERVER_URL   = "server_url";
    private static final String KEY_SANDBOX_CODE = "sandbox_code";
    private static final String KEY_DEVICE_NAME  = "device_name";
    private static final String KEY_SANDBOX_ID   = "sandbox_id";

    private TextView   mStatusText;
    private TextView   mInfoText;
    private EditText   mUrlEdit;
    private EditText   mCodeEdit;
    private EditText   mDeviceNameEdit;
    private TextView   mLogText;
    private ScrollView mLogScroll;

    private Thread  mActiveThread;
    private String  mLastSandboxId = "";
    private boolean mConnected          = false;
    private boolean mRetryWithoutResume = false;

    // ─────────────────────────────────────────────────────────────────────────
    // Fragment 生命周期
    // ─────────────────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_agent_server, container, false);
        mStatusText     = v.findViewById(R.id.agentserver_status_text);
        mInfoText       = v.findViewById(R.id.agentserver_info);
        mUrlEdit        = v.findViewById(R.id.agentserver_url);
        mCodeEdit       = v.findViewById(R.id.agentserver_code);
        mDeviceNameEdit = v.findViewById(R.id.agentserver_device_name);
        mLogText        = v.findViewById(R.id.agentserver_log);
        mLogScroll      = v.findViewById(R.id.agentserver_log_scroll);
        v.findViewById(R.id.btn_agentserver_connect)   .setOnClickListener(b -> doConnect());
        v.findViewById(R.id.btn_agentserver_stop)      .setOnClickListener(b -> doStop());
        v.findViewById(R.id.btn_agentserver_refresh)   .setOnClickListener(b -> checkStatus());
        v.findViewById(R.id.btn_agentserver_clear_log) .setOnClickListener(b -> clearLog());
        loadPrefs();
        return v;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cancelActiveThread();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 操作
    // ─────────────────────────────────────────────────────────────────────────

    private void checkStatus() {
        String prefix  = getPrefix();
        String home    = prefix + "/../home";
        String asBin   = home + "/bin/agentserver";
        String logFile = home + "/agentserver-agent.log";

        String script =
            "if [ ! -x '" + asBin + "' ]; then\n" +
            "  echo '[!] AgentServer 未安装（~/bin/agentserver 不存在）'; exit 1\n" +
            "fi\n" +
            "echo \"版本: $('"+asBin+"' version 2>/dev/null)\"\n" +
            "echo ''\n" +
            "if pgrep -f 'agentserver claudecode' >/dev/null 2>&1; then\n" +
            "  echo '[*] Agent 运行中'\n" +
            "  pgrep -a -f 'agentserver claudecode' 2>/dev/null | grep -v grep | head -5\n" +
            "else\n" +
            "  echo '[-] Agent 未运行'\n" +
            "fi\n" +
            "echo ''\n" +
            "echo '=== 最近日志（最后 30 行）==='\n" +
            "tail -30 '" + logFile + "' 2>/dev/null || echo '（无日志文件）'\n";

        runScript(script, "刷新状态", null);
    }

    /**
     * 启动 agentserver claudecode，nohup 后台运行。
     * 连接成功后从日志解析 sandbox ID 并持久化，下次用 --resume 复用同一沙盒。
     */
    private void doConnect() {
        mConnected = false;
        mRetryWithoutResume = false;
        String url    = mUrlEdit.getText().toString().trim();
        String code   = mCodeEdit != null ? mCodeEdit.getText().toString().trim() : "";
        String device = mDeviceNameEdit.getText().toString().trim();
        if (url.isEmpty()) {
            Toast.makeText(getContext(), "请填写服务器地址", Toast.LENGTH_SHORT).show();
            return;
        }
        savePrefs();

        String prefix  = getPrefix();
        String home    = prefix + "/../home";
        String asBin   = home + "/bin/agentserver";
        String claudeBin = home + "/bin/claude";
        String logFile = home + "/agentserver-agent.log";

        // 从 ApiKeyStore 读取激活的 API Key，内联注入环境变量
        ApiKeyStore keyStore = new ApiKeyStore(requireContext());
        String activeId = keyStore.getActiveId();
        String apiKey = "", apiBaseUrl = "";
        if (activeId != null) {
            for (ApiKeyStore.Entry e : keyStore.loadAll()) {
                if (e.id.equals(activeId)) { apiKey = e.value; apiBaseUrl = e.baseUrl; break; }
            }
        }
        String envPrefix = "";
        if (!apiKey.isEmpty()) {
            envPrefix = "ANTHROPIC_API_KEY='" + apiKey.replace("'", "'\\''") + "' ";
            if (!apiBaseUrl.isEmpty())
                envPrefix += "ANTHROPIC_BASE_URL='" + apiBaseUrl.replace("'", "'\\''") + "' ";
        }

        String safeUrl    = url.replace("'", "'\\''");
        String nameFlag   = device.isEmpty() ? "" : " --name '" + device.replace("'", "'\\''") + "'";
        String resumeId   = !code.isEmpty() ? code : mLastSandboxId;
        String resumeFlag = resumeId.isEmpty() ? "" : " --resume '" + resumeId.replace("'", "'\\''") + "'";

        String agentArgs  = "claudecode --server '" + safeUrl + "'" +
                            " --claude-bin '" + claudeBin + "'" +
                            resumeFlag + nameFlag + " --skip-open-browser";
        final String finalEnvPrefix = envPrefix;

        String script =
            "if [ ! -x '" + asBin + "' ]; then\n" +
            "  echo '[!] AgentServer 未安装，请重启 App 等待自动安装'; exit 1\n" +
            "fi\n" +
            // 停掉已有进程
            "for _p in $(pgrep -f 'agentserver claudecode' 2>/dev/null);" +
            " do [ \"$_p\" != \"$$\" ] && kill \"$_p\" 2>/dev/null; done; sleep 1\n" +
            "> '" + logFile + "'\n" +
            // Go 静态二进制读 /etc/resolv.conf，Android 上该文件不存在或指向无效 DNS。
            // 用 proot -b 仅做 resolv.conf 绑定，注入真实 DNS（8.8.8.8 + 1.1.1.1）。
            "_rc=\"$HOME/.as-resolv.conf\"\n" +
            "printf 'nameserver 8.8.8.8\\nnameserver 1.1.1.1\\n' > \"$_rc\"\n" +
            "_proot=$(command -v proot 2>/dev/null)\n" +
            "echo '[*] 正在启动 AgentServer...'\n" +
            "if [ -n \"$_proot\" ]; then\n" +
            "  nohup \"$_proot\" -b \"$_rc:/etc/resolv.conf\" sh -c '" +
            finalEnvPrefix + "exec '\"'\"'" + asBin + "'\"'\"' " + agentArgs + "'" +
            " >> '" + logFile + "' 2>&1 &\n" +
            "else\n" +
            "  nohup sh -c '" + finalEnvPrefix + "exec '\"'\"'" + asBin + "'\"'\"' " + agentArgs + "'" +
            " >> '" + logFile + "' 2>&1 &\n" +
            "fi\n" +
            "AS_PID=$!\n" +
            "echo '[*] 等待启动（5 秒）...'\n" +
            "sleep 5\n" +
            "echo ''\n" +
            "echo '=== 当前日志 ==='\n" +
            "cat '" + logFile + "' 2>/dev/null || echo '（无日志）'\n" +
            "echo ''\n" +
            "if kill -0 $AS_PID 2>/dev/null; then\n" +
            "  echo \"[*] Agent 进程运行中（PID: $AS_PID）\"\n" +
            "else\n" +
            "  echo '[!] Agent 进程已退出'\n" +
            "fi\n";

        runScript(script, "连接 AgentServer", line -> {
            if (line.contains("Failed to load session") || line.contains("session not found")
                    || line.contains("got 401") || line.contains("status code 101 but got")) {
                mLastSandboxId = "";
                saveSandboxId("");
                mRetryWithoutResume = true;
                post(() -> {
                    setStatus("● 重试中", "#F57C00");
                    setInfo("沙盒 token 已过期，即将重新创建连接...");
                });
                return;
            }
            int idx = line.indexOf("tunnel connected (sandbox:");
            if (idx < 0) return;
            int start = line.indexOf(':', idx + "tunnel connected ".length()) + 1;
            int end   = line.lastIndexOf(')');
            if (start > 0 && end > start) {
                String sandboxId = line.substring(start, end).trim();
                if (!sandboxId.isEmpty()) {
                    mLastSandboxId = sandboxId;
                    saveSandboxId(sandboxId);
                    mConnected = true;
                    final String sid = sandboxId;
                    post(() -> {
                        setStatus("● 已连接", "#388E3C");
                        setInfo("AgentServer 已连接到服务器（沙盒: " + sid.substring(0, 8) + "...）");
                    });
                }
            }
        });
    }

    private void doStop() {
        mConnected = false;
        runScript(
            "pkill -f 'agentserver claudecode' 2>/dev/null" +
            " && echo '[*] Agent 已断开连接'" +
            " || echo '[!] 未找到运行中的 Agent 进程'",
            "断开连接", null
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 命令执行
    // ─────────────────────────────────────────────────────────────────────────

    private void runScript(String bashScript, String label, @Nullable Consumer<String> lineCallback) {
        cancelActiveThread();
        clearLog();
        appendLog("▶ " + label + "\n");
        setStatus("● 运行中", "#F57C00");
        setInfo("正在执行...");

        String prefix = getPrefix();
        String bash   = prefix + "/bin/bash";
        String sysPath = System.getenv("PATH");
        if (sysPath == null) sysPath = "";
        String termuxPath = prefix + "/bin:" + prefix + "/bin/applets:" + sysPath;
        final String finalPrefix = prefix;
        final String finalPath   = termuxPath;

        mActiveThread = new Thread(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(bash, "-c", bashScript);
                pb.redirectErrorStream(true);
                java.util.Map<String, String> env = pb.environment();
                env.putAll(System.getenv());
                env.put("PATH",   finalPath);
                env.put("PREFIX", finalPrefix);
                env.put("HOME",   finalPrefix + "/../home");
                Process p = pb.start();

                BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
                String line;
                while ((line = br.readLine()) != null) {
                    if (Thread.currentThread().isInterrupted()) { p.destroy(); return; }
                    if (lineCallback != null) lineCallback.accept(line);
                    final String l = line;
                    post(() -> appendLog(l + "\n"));
                }
                p.waitFor();
                int exit = p.exitValue();
                post(() -> {
                    appendLog("─────────────── 完成（exit " + exit + "）\n");
                    updateStatusFromLog(exit);
                });
            } catch (InterruptedException ignored) {
            } catch (Exception e) {
                post(() -> {
                    appendLog("[!] 执行出错：" + e.getMessage() + "\n");
                    setStatus("● 错误", "#E53935");
                    setInfo("执行出错");
                });
            }
        }, "agentserver-cmd");
        mActiveThread.setDaemon(true);
        mActiveThread.start();
    }

    private void updateStatusFromLog(int exitCode) {
        if (exitCode != 0) {
            String log = mLogText.getText().toString();
            if (log.contains("未安装")) {
                setStatus("● 未安装", "#888888");
                setInfo("AgentServer 未安装，请重启应用等待自动安装");
            } else {
                setStatus("● 失败", "#E53935");
                setInfo("命令执行失败，请查看日志");
            }
            return;
        }
        if (mConnected) {
            setStatus("● 已连接", "#388E3C");
            setInfo("AgentServer 已连接到服务器" +
                (mLastSandboxId.isEmpty() ? "" : "（沙盒: " + mLastSandboxId.substring(0, 8) + "...）"));
        } else if (mRetryWithoutResume) {
            mRetryWithoutResume = false;
            setStatus("● Token 已过期", "#F57C00");
            setInfo("沙盒 token 已过期，旧 ID 已清除，请点击「连接」重新创建沙盒");
            appendLog("\n[!] 沙盒 token 过期（401），已清除旧 ID，请重新点击「连接」\n");
        } else {
            setStatus("● 已安装", "#555555");
            setInfo("Agent 进程已启动，但未检测到 tunnel 连接");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI 辅助
    // ─────────────────────────────────────────────────────────────────────────

    private String getPrefix() {
        String p = System.getenv("PREFIX");
        return (p != null && !p.isEmpty()) ? p : "/data/data/com.termux/files/usr";
    }

    private void appendLog(String text) {
        mLogText.append(text);
        mLogScroll.post(() -> mLogScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void clearLog() {
        if (mLogText != null) mLogText.setText("");
    }

    private void setStatus(String text, String colorHex) {
        mStatusText.setText(text);
        mStatusText.setTextColor(Color.parseColor(colorHex));
    }

    private void setInfo(String text) {
        mInfoText.setText(text);
    }

    private void loadPrefs() {
        SharedPreferences p = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        mUrlEdit.setText(p.getString(KEY_SERVER_URL, ""));
        mCodeEdit.setText(p.getString(KEY_SANDBOX_CODE, ""));
        mDeviceNameEdit.setText(p.getString(KEY_DEVICE_NAME, ""));
        mLastSandboxId = p.getString(KEY_SANDBOX_ID, "");
    }

    private void savePrefs() {
        requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SERVER_URL,   mUrlEdit.getText().toString().trim())
            .putString(KEY_SANDBOX_CODE, mCodeEdit.getText().toString().trim())
            .putString(KEY_DEVICE_NAME,  mDeviceNameEdit.getText().toString().trim())
            .apply();
    }

    private void saveSandboxId(String sandboxId) {
        requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_SANDBOX_ID, sandboxId).apply();
    }

    private void cancelActiveThread() {
        if (mActiveThread != null && mActiveThread.isAlive()) mActiveThread.interrupt();
    }

    private void post(Runnable r) {
        if (getActivity() != null) getActivity().runOnUiThread(r);
    }
}
