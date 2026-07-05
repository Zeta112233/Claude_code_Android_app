package com.portalagent.mcp;

import android.content.ComponentName;
import android.content.Context;
import android.os.SystemClock;
import android.provider.Settings;

import com.portalagent.provider.AssistantProvider;
import com.portalagent.provider.ProviderProfile;
import com.portalagent.provider.ProviderSettingsStore;
import com.portalagent.settings.WorkspaceAccessSettingsStore;
import com.portalagent.settings.WorkspaceAccessSettingsStore.AppCapability;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class WorkspaceAccessPolicy {

    private static final long RECENT_LAUNCH_INHERIT_WINDOW_MS = 45000;
    private static final Object RECENT_LAUNCH_LOCK = new Object();

    private static String sRecentLaunchPackage = "";
    private static long sRecentLaunchAtMs = 0;

    private WorkspaceAccessPolicy() {
    }

    public static void enforceFilePath(Context context, String path) throws Exception {
        resolveAllowedFile(context, path);
    }

    public static File resolveAllowedFile(Context context, String path) throws Exception {
        Context appContext = requireContext(context);
        String safePath = clean(path);
        if (safePath.isEmpty()) {
            throw denied("file", "missing path");
        }

        File target = translateProviderHomePath(appContext, safePath).getCanonicalFile();
        for (File root : allowedFileRoots(appContext)) {
            if (isSameOrChild(target, root)) {
                return target;
            }
        }
        throw denied("file", "path is outside allowed workspace: " + safePath);
    }

    public static void enforceAppOpen(Context context, String packageName) throws Exception {
        enforceAllowedPackage(context, "app.open", packageName, AppCapability.LAUNCH);
    }

    public static void enforceAccessibilityForeground(Context context, String toolName) throws Exception {
        if (!McpAccessibilityService.isRunning()) {
            throw denied(toolName, "cannot verify foreground app because accessibility is not enabled");
        }
        McpAccessibilityService service = McpAccessibilityService.getInstance();
        AppCapability capability = capabilityForAccessibilityTool(toolName);
        String packageName = service == null ? "" : effectiveAccessibilityPackageForTool(
            context, toolName, service.getCurrentPackage(), service.getCurrentActivity(),
            service.getLastApplicationPackage(), service.getActiveWindowPackage());
        enforceAllowedPackage(context, toolName, packageName, capability);
    }

    public static void recordAppLaunchAttempt(String packageName) {
        String safePackage = clean(packageName);
        synchronized (RECENT_LAUNCH_LOCK) {
            sRecentLaunchPackage = safePackage;
            sRecentLaunchAtMs = safePackage.isEmpty() ? 0 : SystemClock.elapsedRealtime();
        }
    }

    public static void enforceAdbForeground(Context context, String toolName, JSONObject currentActivity)
            throws Exception {
        String packageName = effectiveAdbPackage(context, currentActivity);
        enforceAllowedPackage(context, toolName, packageName, capabilityForAdbTool(toolName));
        enforceAllowedPackage(context, toolName, packageName, AppCapability.ADB);
    }

    public static boolean isAppAllowed(Context context, String packageName) {
        return isAppAllowed(context, packageName, null);
    }

    public static boolean isAppAllowed(Context context, String packageName, AppCapability capability) {
        try {
            WorkspaceAccessSettingsStore store = new WorkspaceAccessSettingsStore(requireContext(context));
            return capability == null
                ? store.isAppAllowed(clean(packageName))
                : store.isAppAllowed(clean(packageName), capability);
        } catch (Exception e) {
            return false;
        }
    }

    public static String effectiveAccessibilityPackage(Context context, String currentPackage,
                                                       String lastApplicationPackage) {
        String current = clean(currentPackage);
        String lastApplication = clean(lastApplicationPackage);
        if (!lastApplication.isEmpty() && isCurrentInputMethodPackage(context, current)) {
            return lastApplication;
        }
        return current;
    }

    public static String effectiveAccessibilityPackageForTool(Context context, String toolName,
                                                              String currentPackage,
                                                              String lastApplicationPackage) {
        return effectiveAccessibilityPackageForTool(
            context, toolName, currentPackage, "", lastApplicationPackage);
    }

    public static String effectiveAccessibilityPackageForTool(Context context, String toolName,
                                                              String currentPackage,
                                                              String currentActivity,
                                                              String lastApplicationPackage) {
        return effectiveAccessibilityPackageForTool(
            context, toolName, currentPackage, currentActivity, lastApplicationPackage, "");
    }

    public static String effectiveAccessibilityPackageForTool(Context context, String toolName,
                                                              String currentPackage,
                                                              String currentActivity,
                                                              String lastApplicationPackage,
                                                              String activeWindowPackage) {
        String activeWindow = clean(activeWindowPackage);
        String current = clean(currentPackage);
        String currentActivityName = clean(currentActivity);
        String lastApplication = clean(lastApplicationPackage);
        String recentApp = recentLaunchPackage();
        Context appContext = context == null ? null : context.getApplicationContext();
        String ownPackage = appContext == null ? "" : clean(appContext.getPackageName());

        if (!activeWindow.isEmpty()) {
            String activeEffective = effectiveAccessibilityPackage(context, activeWindow, lastApplication);
            if (shouldUseRecentLaunchApplication(appContext, ownPackage, activeEffective,
                    currentActivityName, lastApplication, recentApp)) {
                return recentApp;
            }
            return activeEffective;
        }

        String effective = effectiveAccessibilityPackage(context, current, lastApplication);
        if (shouldUseRecentLaunchApplication(appContext, ownPackage, effective,
                currentActivityName, lastApplication, recentApp)) {
            return recentApp;
        }
        return effective;
    }

    public static boolean isCurrentInputMethodPackage(Context context, String packageName) {
        String safePackage = clean(packageName);
        if (safePackage.isEmpty() || context == null) return false;
        String method = Settings.Secure.getString(
            context.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        if (method == null || method.trim().isEmpty()) return false;
        ComponentName component = ComponentName.unflattenFromString(method);
        if (component != null) {
            return safePackage.equals(component.getPackageName());
        }
        int slash = method.indexOf('/');
        String methodPackage = slash >= 0 ? method.substring(0, slash) : method;
        return safePackage.equals(clean(methodPackage));
    }

    static String effectiveAdbPackage(Context context, JSONObject currentActivity) {
        if (currentActivity == null) return "";
        String current = clean(currentActivity.optString("package", ""));
        String focusedApp = clean(currentActivity.optString("focused_app_package", ""));
        if (!focusedApp.isEmpty() && isCurrentInputMethodPackage(context, current)) {
            return focusedApp;
        }
        return current;
    }

    private static void enforceAllowedPackage(Context context, String toolName, String packageName,
                                              AppCapability capability) throws Exception {
        String safePackage = clean(packageName);
        if (safePackage.isEmpty()) {
            throw denied(toolName, "cannot verify foreground app package");
        }
        if (!isAppAllowed(context, safePackage, capability)) {
            throw denied(toolName, "app is not in workspace allowlist for "
                + capability.id() + ": " + safePackage);
        }
    }

    private static AppCapability capabilityForAccessibilityTool(String toolName) {
        String safeTool = clean(toolName);
        if ("ui.get_accessibility_tree".equals(safeTool) || "screen.capture".equals(safeTool)) {
            return AppCapability.OBSERVE;
        }
        return AppCapability.INTERACT;
    }

    private static AppCapability capabilityForAdbTool(String toolName) {
        String safeTool = clean(toolName);
        if ("adb.screenshot".equals(safeTool)) {
            return AppCapability.OBSERVE;
        }
        return AppCapability.INTERACT;
    }

    private static String recentLaunchPackage() {
        synchronized (RECENT_LAUNCH_LOCK) {
            if (sRecentLaunchPackage.isEmpty()) return "";
            long ageMs = SystemClock.elapsedRealtime() - sRecentLaunchAtMs;
            if (ageMs < 0 || ageMs > RECENT_LAUNCH_INHERIT_WINDOW_MS) {
                sRecentLaunchPackage = "";
                sRecentLaunchAtMs = 0;
                return "";
            }
            return sRecentLaunchPackage;
        }
    }

    private static boolean shouldUseRecentLaunchApplication(Context context, String ownPackage,
                                                            String effectivePackage,
                                                            String currentActivity,
                                                            String lastApplicationPackage,
                                                            String recentLaunchPackage) {
        return !recentLaunchPackage.isEmpty()
            && !ownPackage.isEmpty()
            && ownPackage.equals(effectivePackage)
            && !isDeclaredActivityName(context, ownPackage, currentActivity)
            && recentLaunchPackage.equals(lastApplicationPackage);
    }

    static boolean isDeclaredActivityName(Context context, String packageName, String activityName) {
        Context appContext = context == null ? null : context.getApplicationContext();
        String safePackage = clean(packageName);
        String safeActivity = clean(activityName);
        if (appContext == null || safePackage.isEmpty() || safeActivity.isEmpty()) return false;
        if (safeActivity.startsWith(".")) {
            safeActivity = safePackage + safeActivity;
        }
        try {
            appContext.getPackageManager().getActivityInfo(
                new ComponentName(safePackage, safeActivity), 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static void resetRecentLaunchForTests() {
        synchronized (RECENT_LAUNCH_LOCK) {
            sRecentLaunchPackage = "";
            sRecentLaunchAtMs = 0;
        }
    }

    private static List<File> allowedFileRoots(Context context) throws Exception {
        List<File> roots = new ArrayList<>();
        for (String path : WorkspaceAccessSettingsStore.DEFAULT_ANDROID_DIRS) {
            roots.add(new File(path).getCanonicalFile());
        }

        AssistantProvider provider = new ProviderSettingsStore(context).getSelectedProvider();
        ProviderProfile profile = ProviderProfile.forProvider(provider);
        roots.add(hostPathForProviderHome(context, profile).getCanonicalFile());
        return roots;
    }

    private static File translateProviderHomePath(Context context, String path) {
        AssistantProvider provider = new ProviderSettingsStore(context).getSelectedProvider();
        ProviderProfile profile = ProviderProfile.forProvider(provider);
        String home = clean(profile.home);
        if (path.equals(home) || path.startsWith(home + "/")) {
            String relative = path.substring(home.length());
            while (relative.startsWith("/")) {
                relative = relative.substring(1);
            }
            return new File(hostPathForProviderHome(context, profile), relative);
        }
        return new File(path);
    }

    private static File hostPathForProviderHome(Context context, ProviderProfile profile) {
        File ubuntuRoot = new File(context.getFilesDir(),
            "usr/var/lib/proot-distro/installed-rootfs/ubuntu");
        String relativeHome = clean(profile.home);
        while (relativeHome.startsWith("/")) {
            relativeHome = relativeHome.substring(1);
        }
        return new File(ubuntuRoot, relativeHome);
    }

    private static boolean isSameOrChild(File target, File root) {
        String targetPath = target.getPath();
        String rootPath = root.getPath();
        if (targetPath.equals(rootPath)) return true;
        if (!rootPath.endsWith(File.separator)) {
            rootPath += File.separator;
        }
        return targetPath.startsWith(rootPath);
    }

    private static Context requireContext(Context context) throws Exception {
        if (context == null) {
            throw denied("workspace", "missing Android context");
        }
        return context.getApplicationContext();
    }

    private static Exception denied(String toolName, String reason) {
        return new SecurityException("Workspace access denied for " + toolName + ": " + reason);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
