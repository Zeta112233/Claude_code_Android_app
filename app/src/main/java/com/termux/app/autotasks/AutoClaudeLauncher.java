package com.termux.app.autotasks;

import androidx.annotation.NonNull;

import com.termux.app.TermuxActivity;

import java.io.File;

/**
 * Triggers the Claude Code native setup script in the Termux terminal on first launch.
 * No Ubuntu/proot-distro required — Claude runs natively via the musl binary.
 */
public class AutoClaudeLauncher {

    private final TermuxActivity mActivity;
    private boolean mLaunched = false;

    public AutoClaudeLauncher(@NonNull TermuxActivity activity) {
        mActivity = activity;
    }

    /**
     * Called when the terminal session is ready. Sends the setup script to the terminal
     * if Claude is not yet installed. Safe to call multiple times — runs only once.
     */
    public void maybeAutoLaunchSetup() {
        if (mLaunched) return;
        mLaunched = true;

        new Thread(() -> {
            String home = mActivity.getFilesDir().getParent() + "/home";
            if (new File(home + "/bin/claude").exists()) return;

            String scriptPath = new File(mActivity.getFilesDir(),
                AutoClaudeManager.INNER_SCRIPT_REL).getAbsolutePath();

            // Wait up to 5s for AutoClaudeManager background thread to finish writing the script
            long deadline = System.currentTimeMillis() + 5000;
            while (!new File(scriptPath).exists() && System.currentTimeMillis() < deadline) {
                try { Thread.sleep(100); } catch (InterruptedException ignored) { break; }
            }
            if (!new File(scriptPath).exists()) return;

            mActivity.runOnUiThread(() -> mActivity.sendTerminalInput("bash '" + scriptPath + "'\n"));
        }, "claude-launcher").start();
    }
}
