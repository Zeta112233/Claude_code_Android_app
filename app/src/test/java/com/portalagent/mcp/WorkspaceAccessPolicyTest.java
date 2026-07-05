package com.portalagent.mcp;

import android.content.Context;
import android.provider.Settings;

import com.portalagent.provider.AssistantProvider;
import com.portalagent.provider.ProviderSettingsStore;
import com.portalagent.settings.WorkspaceAccessSettingsStore;
import com.portalagent.settings.WorkspaceAccessSettingsStore.AppCapability;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@RunWith(RobolectricTestRunner.class)
public class WorkspaceAccessPolicyTest {

    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(WorkspaceAccessSettingsStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit();
        context.getSharedPreferences(ProviderSettingsStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit();
        WorkspaceAccessPolicy.resetRecentLaunchForTests();
    }

    @Test
    public void allowsCurrentProviderUbuntuHome() throws Exception {
        new ProviderSettingsStore(context).setSelectedProvider(AssistantProvider.CODEX);
        File allowed = new File(context.getFilesDir(),
            "usr/var/lib/proot-distro/installed-rootfs/ubuntu/home/codex/project/readme.md");
        Assert.assertTrue(allowed.getParentFile().isDirectory() || allowed.getParentFile().mkdirs());
        Files.write(allowed.toPath(), "ok".getBytes(StandardCharsets.UTF_8));

        WorkspaceAccessPolicy.enforceFilePath(context, allowed.getAbsolutePath());
        Assert.assertEquals(allowed.getCanonicalFile(),
            WorkspaceAccessPolicy.resolveAllowedFile(context, "/home/codex/project/readme.md"));
    }

    @Test
    public void rejectsFilesOutsideAllowedRoots() throws Exception {
        File denied = new File(context.getFilesDir(), "private-secret.txt");
        Files.write(denied.toPath(), "secret".getBytes(StandardCharsets.UTF_8));

        try {
            WorkspaceAccessPolicy.enforceFilePath(context, denied.getAbsolutePath());
            Assert.fail("Expected outside file to be denied");
        } catch (SecurityException e) {
            Assert.assertTrue(e.getMessage().contains("outside allowed workspace"));
        }
    }

    @Test
    public void requiresAppAllowlistForAppOperations() throws Exception {
        try {
            WorkspaceAccessPolicy.enforceAppOpen(context, "com.example.allowed");
            Assert.fail("Expected app operation to be denied before allowlist");
        } catch (SecurityException e) {
            Assert.assertTrue(e.getMessage().contains("not in workspace allowlist"));
        }

        new WorkspaceAccessSettingsStore(context).setAppAllowed("com.example.allowed", true);

        WorkspaceAccessPolicy.enforceAppOpen(context, "com.example.allowed");
    }

    @Test
    public void appOpenRequiresLaunchCapability() throws Exception {
        WorkspaceAccessSettingsStore store = new WorkspaceAccessSettingsStore(context);
        store.setAppCapabilityAllowed("com.example.allowed", AppCapability.OBSERVE, true);

        try {
            WorkspaceAccessPolicy.enforceAppOpen(context, "com.example.allowed");
            Assert.fail("Expected app.open to require launch capability");
        } catch (SecurityException e) {
            Assert.assertTrue(e.getMessage().contains("for launch"));
        }

        store.setAppCapabilityAllowed("com.example.allowed", AppCapability.LAUNCH, true);

        WorkspaceAccessPolicy.enforceAppOpen(context, "com.example.allowed");
    }

    @Test
    public void legacyAppAllowlistStillGrantsAllCapabilities() {
        WorkspaceAccessSettingsStore store = new WorkspaceAccessSettingsStore(context);
        store.setAppAllowed("com.example.allowed", true);

        Assert.assertTrue(store.isAppAllowed("com.example.allowed", AppCapability.LAUNCH));
        Assert.assertTrue(store.isAppAllowed("com.example.allowed", AppCapability.OBSERVE));
        Assert.assertTrue(store.isAppAllowed("com.example.allowed", AppCapability.INTERACT));
        Assert.assertTrue(store.isAppAllowed("com.example.allowed", AppCapability.ADB));
        Assert.assertTrue(store.isAppFullyAllowed("com.example.allowed"));
    }

    @Test
    public void capabilityToggleOverridesLegacyAppAllowlistFallback() throws Exception {
        WorkspaceAccessSettingsStore store = new WorkspaceAccessSettingsStore(context);
        store.setAppAllowed("com.example.allowed", true);
        store.setAppCapabilityAllowed("com.example.allowed", AppCapability.LAUNCH, false);

        Assert.assertFalse(store.isAppAllowed("com.example.allowed", AppCapability.LAUNCH));
        Assert.assertTrue(store.isAppAllowed("com.example.allowed", AppCapability.OBSERVE));

        try {
            WorkspaceAccessPolicy.enforceAppOpen(context, "com.example.allowed");
            Assert.fail("Expected disabled launch capability to be enforced");
        } catch (SecurityException e) {
            Assert.assertTrue(e.getMessage().contains("for launch"));
        }
    }

    @Test
    public void accessibilityForegroundUsesLastApplicationWhenCurrentWindowIsInputMethod() {
        Settings.Secure.putString(context.getContentResolver(),
            Settings.Secure.DEFAULT_INPUT_METHOD, "com.baidu.input/.ImeService");

        Assert.assertEquals("com.microsoft.emmx",
            WorkspaceAccessPolicy.effectiveAccessibilityPackage(
                context, "com.baidu.input", "com.microsoft.emmx"));
        Assert.assertEquals("com.portalagent",
            WorkspaceAccessPolicy.effectiveAccessibilityPackage(
                context, "com.portalagent", "com.microsoft.emmx"));
        Assert.assertEquals("com.microsoft.emmx",
            WorkspaceAccessPolicy.effectiveAccessibilityPackage(
                context, "com.microsoft.emmx", "com.portalagent"));
    }

    @Test
    public void systemWindowsDoNotInheritLastApplicationAllowlist() {
        Settings.Secure.putString(context.getContentResolver(),
            Settings.Secure.DEFAULT_INPUT_METHOD, "com.baidu.input/.ImeService");

        Assert.assertEquals("com.android.permissioncontroller",
            WorkspaceAccessPolicy.effectiveAccessibilityPackage(
                context, "com.android.permissioncontroller", "com.microsoft.emmx"));
        Assert.assertEquals("com.android.systemui",
            WorkspaceAccessPolicy.effectiveAccessibilityPackage(
                context, "com.android.systemui", "com.microsoft.emmx"));
        Assert.assertEquals("com.android.launcher3",
            WorkspaceAccessPolicy.effectiveAccessibilityPackage(
                context, "com.android.launcher3", "com.microsoft.emmx"));
        Assert.assertEquals("com.portalagent",
            WorkspaceAccessPolicy.effectiveAccessibilityPackage(
                context, "com.portalagent", "com.microsoft.emmx"));
    }

    @Test
    public void toolsCanUseRecentLaunchWhenOwnTransientWindowIsCurrent() {
        String ownPackage = context.getPackageName();
        WorkspaceAccessPolicy.recordAppLaunchAttempt("com.microsoft.emmx");

        Assert.assertEquals("com.microsoft.emmx",
            WorkspaceAccessPolicy.effectiveAccessibilityPackageForTool(
                context, "ui.get_accessibility_tree", ownPackage,
                "android.widget.FrameLayout", "com.microsoft.emmx"));
        Assert.assertEquals("com.microsoft.emmx",
            WorkspaceAccessPolicy.effectiveAccessibilityPackageForTool(
                context, "screen.capture", ownPackage,
                "android.widget.FrameLayout", "com.microsoft.emmx"));
        Assert.assertEquals("com.microsoft.emmx",
            WorkspaceAccessPolicy.effectiveAccessibilityPackageForTool(
                context, "ui.tap", ownPackage,
                "android.widget.FrameLayout", "com.microsoft.emmx"));
        Assert.assertEquals("com.microsoft.emmx",
            WorkspaceAccessPolicy.effectiveAccessibilityPackageForTool(
                context, "ui.input_text", ownPackage,
                "android.widget.FrameLayout", "com.microsoft.emmx"));
        Assert.assertEquals(ownPackage,
            WorkspaceAccessPolicy.effectiveAccessibilityPackageForTool(
                context, "ui.get_accessibility_tree", ownPackage,
                ownPackage + ".PortalAgentActivity", "com.microsoft.emmx"));
        Assert.assertEquals(ownPackage,
            WorkspaceAccessPolicy.effectiveAccessibilityPackageForTool(
                context, "ui.tap", ownPackage,
                ownPackage + ".PortalAgentActivity", "com.microsoft.emmx"));
        Assert.assertEquals(ownPackage,
            WorkspaceAccessPolicy.effectiveAccessibilityPackageForTool(
                context, "ui.get_accessibility_tree", ownPackage,
                "android.widget.FrameLayout", "com.android.settings"));
    }

    @Test
    public void accessibilityToolsPreferActiveWindowPackageOverStalePortalAgentEvent() {
        String ownPackage = context.getPackageName();

        Assert.assertEquals("com.microsoft.emmx",
            WorkspaceAccessPolicy.effectiveAccessibilityPackageForTool(
                context, "ui.tap", ownPackage,
                "android.widget.FrameLayout", "com.microsoft.emmx",
                "com.microsoft.emmx"));
        Assert.assertEquals("com.microsoft.emmx",
            WorkspaceAccessPolicy.effectiveAccessibilityPackageForTool(
                context, "ui.get_accessibility_tree", ownPackage,
                "android.widget.FrameLayout", "com.microsoft.emmx",
                "com.microsoft.emmx"));
    }

    @Test
    public void activePortalAgentOrSystemWindowDoesNotInheritLastApplicationAllowlist() {
        String ownPackage = context.getPackageName();

        Assert.assertEquals(ownPackage,
            WorkspaceAccessPolicy.effectiveAccessibilityPackageForTool(
                context, "ui.tap", ownPackage,
                "android.widget.FrameLayout", "com.microsoft.emmx",
                ownPackage));
        Assert.assertEquals("com.android.permissioncontroller",
            WorkspaceAccessPolicy.effectiveAccessibilityPackageForTool(
                context, "ui.tap", ownPackage,
                "android.widget.FrameLayout", "com.microsoft.emmx",
                "com.android.permissioncontroller"));
    }

    @Test
    public void activeInputMethodWindowUsesLastApplicationPackage() {
        Settings.Secure.putString(context.getContentResolver(),
            Settings.Secure.DEFAULT_INPUT_METHOD, "com.baidu.input/.ImeService");

        Assert.assertEquals("com.microsoft.emmx",
            WorkspaceAccessPolicy.effectiveAccessibilityPackageForTool(
                context, "ui.input_text", "com.portalagent",
                "android.widget.FrameLayout", "com.microsoft.emmx",
                "com.baidu.input"));
    }

    @Test
    public void adbForegroundUsesFocusedAppWhenCurrentFocusIsInputMethod() throws Exception {
        Settings.Secure.putString(context.getContentResolver(),
            Settings.Secure.DEFAULT_INPUT_METHOD, "com.baidu.input/.ImeService");

        JSONObject currentActivity = new JSONObject()
            .put("package", "com.baidu.input")
            .put("activity", "android.inputmethodservice.SoftInputWindow")
            .put("focused_app_package", "com.microsoft.emmx")
            .put("focused_app_activity", "org.chromium.chrome.browser.ChromeTabbedActivity");

        Assert.assertEquals("com.microsoft.emmx",
            WorkspaceAccessPolicy.effectiveAdbPackage(context, currentActivity));
    }
}
