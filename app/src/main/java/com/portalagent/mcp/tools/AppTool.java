package com.portalagent.mcp.tools;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import com.portalagent.mcp.McpAccessibilityService;
import com.portalagent.mcp.McpTool;
import com.portalagent.mcp.WorkspaceAccessPolicy;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * App control tools.
 *
 * Kind.OPEN         → app.open(package_name)
 * Kind.GET_ACTIVITY → app.get_current_activity()
 */
public class AppTool implements McpTool {

    private static final long LAUNCH_FOREGROUND_TIMEOUT_MS = 4000;
    private static final long LAUNCH_FOREGROUND_POLL_MS = 100;

    public enum Kind { OPEN, GET_ACTIVITY }

    private final Kind mKind;

    public AppTool(Kind kind) { mKind = kind; }

    @Override public String getName() {
        return mKind == Kind.OPEN ? "app.open" : "app.get_current_activity";
    }

    @Override public String getDescription() {
        if (mKind == Kind.OPEN) {
            return "Launch an app by its package name. " +
                   "Example: app.open({\"package_name\":\"com.android.settings\"})";
        }
        return "Get the package name and activity class of the current foreground app. " +
               "Requires accessibility permission.";
    }

    @Override public String getInputSchema() {
        if (mKind == Kind.OPEN) {
            return "{\"type\":\"object\",\"required\":[\"package_name\"],\"properties\":{" +
                "\"task_id\":{\"type\":\"string\"}," +
                "\"package_name\":{\"type\":\"string\"," +
                    "\"description\":\"Android package name, e.g. com.android.settings\"}" +
                "}}";
        }
        return "{\"type\":\"object\",\"properties\":{\"task_id\":{\"type\":\"string\"}}}";
    }

    @Override
    public String call(JSONObject args, Context context) throws Exception {
        if (mKind == Kind.OPEN) {
            return openApp(args, context);
        } else {
            return getCurrentActivity();
        }
    }

    // ── app.open ──────────────────────────────────────────────────────────────

    private String openApp(JSONObject args, Context context) throws Exception {
        String pkg = args.getString("package_name");
        PackageManager pm = context.getPackageManager();
        Intent launch = pm.getLaunchIntentForPackage(pkg);
        if (launch == null) {
            throw new Exception("App not found or not launchable: " + pkg +
                ". Tip: use android.get_status() or check installed apps.");
        }
        WorkspaceAccessPolicy.enforceAppOpen(context, pkg);
        WorkspaceAccessPolicy.recordAppLaunchAttempt(pkg);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(launch);
        String observed = waitForForegroundPackage(pkg);
        if (pkg.equals(observed)) {
            return text("Launched app: " + pkg);
        }
        return text("Launched app: " + pkg
            + "; foreground package not verified yet"
            + (observed.isEmpty() ? "" : " (current: " + observed + ")"));
    }

    // ── app.get_current_activity ──────────────────────────────────────────────

    private String getCurrentActivity() throws Exception {
        if (!McpAccessibilityService.isRunning()) {
            return text("Accessibility permission not granted. " +
                "Please enable 'PortalAgent' in Settings → Accessibility.");
        }
        McpAccessibilityService svc = McpAccessibilityService.getInstance();
        JSONObject result = new JSONObject();
        result.put("package",  svc.getCurrentPackage());
        result.put("activity", svc.getCurrentActivity());
        result.put("active_window_package", svc.getActiveWindowPackage());
        result.put("last_application_package", svc.getLastApplicationPackage());
        result.put("last_application_activity", svc.getLastApplicationActivity());
        result.put("effective_package", svc.getEffectiveForegroundPackage());
        result.put("effective_activity", svc.getEffectiveForegroundActivity());
        return text(result.toString(2));
    }

    private static String waitForForegroundPackage(String packageName) {
        if (!McpAccessibilityService.isRunning()) return "";
        long deadline = System.currentTimeMillis() + LAUNCH_FOREGROUND_TIMEOUT_MS;
        String observed = "";
        while (System.currentTimeMillis() < deadline) {
            McpAccessibilityService svc = McpAccessibilityService.getInstance();
            if (svc == null) return observed;
            observed = svc.getEffectiveForegroundPackage();
            if (packageName.equals(observed)) return observed;
            try {
                Thread.sleep(LAUNCH_FOREGROUND_POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return observed;
            }
        }
        McpAccessibilityService svc = McpAccessibilityService.getInstance();
        return svc == null ? observed : svc.getEffectiveForegroundPackage();
    }

    private static String text(String msg) throws Exception {
        JSONObject item = new JSONObject();
        item.put("type", "text");
        item.put("text", msg);
        return new JSONArray().put(item).toString();
    }
}
