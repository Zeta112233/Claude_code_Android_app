package com.termux.app.autotasks;

import androidx.annotation.NonNull;

import com.termux.app.TermuxActivity;
import com.termux.app.mcp.AuditLogger;
import com.termux.app.mcp.McpHttpServer;
import com.termux.app.mcp.tools.AndroidStatusTool;
import com.termux.app.mcp.tools.CameraTool;
import com.termux.app.mcp.tools.FileTool;

public class AutoTaskCoordinator {

    private final ApiSelfCheckManager mApiSelfCheckManager;
    private final AutoClaudeLauncher   mAutoClaudeLauncher;
    private final ApiHttpBridgeServer  mApiHttpBridgeServer;
    private final McpHttpServer        mMcpHttpServer;
    @SuppressWarnings("FieldCanBeLocal")
    private final AutoClaudeManager    mAutoClaudeManager;
    @SuppressWarnings("FieldCanBeLocal")
    private final AutoAgentServerManager mAutoAgentServerManager;
    private boolean mEnabled = true;

    public AutoTaskCoordinator(@NonNull TermuxActivity activity) {
        // Start background asset extraction / script writing first
        mAutoClaudeManager      = new AutoClaudeManager(activity);
        mAutoAgentServerManager = new AutoAgentServerManager(activity);
        mApiSelfCheckManager    = new ApiSelfCheckManager(activity);
        mAutoClaudeLauncher     = new AutoClaudeLauncher(activity);
        // Legacy read-only HTTP API bridge (kept for backward compatibility)
        mApiHttpBridgeServer = new ApiHttpBridgeServer(activity);
        mApiHttpBridgeServer.start();
        // MCP Server: exposes Android hardware capabilities to Claude Code
        String termuxHome = activity.getFilesDir().getParent() + "/home";
        AuditLogger audit = new AuditLogger(termuxHome);
        mMcpHttpServer = new McpHttpServer(activity, audit);
        mMcpHttpServer.registerTool(new AndroidStatusTool());
        mMcpHttpServer.registerTool(new CameraTool());
        mMcpHttpServer.registerTool(new FileTool(FileTool.Kind.CHECK_EXISTS));
        mMcpHttpServer.registerTool(new FileTool(FileTool.Kind.LIST));
        mMcpHttpServer.registerTool(new FileTool(FileTool.Kind.READ));
        mMcpHttpServer.start();
        new CapabilitiesManager(activity).generateAsync();
    }

    public void setEnabled(boolean enabled) {
        mEnabled = enabled;
        mApiSelfCheckManager.setEnabled(enabled);
    }

    public void setApiSelfCheckEnabled(boolean enabled) {
        mApiSelfCheckManager.setEnabled(enabled);
    }

    public void init() {
        if (!mEnabled) return;
        mApiSelfCheckManager.initViews();
    }

    public void onStart() {
        if (!mEnabled) return;
        mApiSelfCheckManager.start();
    }

    public void onResume() {
        if (!mEnabled) return;
        mAutoClaudeLauncher.maybeAutoLaunchSetup();
    }

    public void onSessionReady() {
        if (!mEnabled) return;
        mApiSelfCheckManager.tryPrintPending();
        mAutoClaudeLauncher.maybeAutoLaunchSetup();
    }

    public void onDestroy() {
        mApiSelfCheckManager.shutdown();
        mApiHttpBridgeServer.stop();
        mMcpHttpServer.stop();
    }
}
