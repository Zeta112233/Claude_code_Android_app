package com.portalagent.settings;

import com.portalagent.provider.AssistantProvider;
import com.portalagent.provider.ProviderProfile;
import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class WorkspaceAccessSettingsStore {

    public static final String PREFS_NAME = "workspace_access_settings";
    private static final String KEY_ALLOWED_APPS = "allowed_apps";
    private static final String KEY_ALLOWED_APP_LAUNCH = "allowed_app_launch";
    private static final String KEY_ALLOWED_APP_OBSERVE = "allowed_app_observe";
    private static final String KEY_ALLOWED_APP_INTERACT = "allowed_app_interact";
    private static final String KEY_ALLOWED_APP_ADB = "allowed_app_adb";

    public enum AppCapability {
        LAUNCH(KEY_ALLOWED_APP_LAUNCH, "launch"),
        OBSERVE(KEY_ALLOWED_APP_OBSERVE, "observe"),
        INTERACT(KEY_ALLOWED_APP_INTERACT, "interact"),
        ADB(KEY_ALLOWED_APP_ADB, "adb");

        private final String prefsKey;
        private final String id;

        AppCapability(String prefsKey, String id) {
            this.prefsKey = prefsKey;
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    public static final String[] DEFAULT_ANDROID_DIRS = {
        "/storage/emulated/0/Download",
        "/storage/emulated/0/Documents",
        "/storage/emulated/0/Pictures",
        "/storage/emulated/0/DCIM",
        "/storage/emulated/0/Movies",
        "/storage/emulated/0/Music"
    };

    private final SharedPreferences prefs;

    public WorkspaceAccessSettingsStore(Context context) {
        if (context == null) throw new IllegalArgumentException("context required");
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public Set<String> allowedApps() {
        Set<String> apps = legacyAllowedApps();
        for (AppCapability capability : AppCapability.values()) {
            apps.addAll(capabilityApps(capability));
        }
        return apps;
    }

    public boolean isAppAllowed(String packageName) {
        String safe = clean(packageName);
        return !safe.isEmpty() && allowedApps().contains(safe);
    }

    public boolean isAppAllowed(String packageName, AppCapability capability) {
        String safe = clean(packageName);
        if (safe.isEmpty() || capability == null) return false;
        if (capabilityApps(capability).contains(safe)) return true;

        // Backward compatibility for configs written before capability-level app access.
        return legacyAllowedApps().contains(safe) && !hasExplicitCapabilityConfig(safe);
    }

    public boolean isAppFullyAllowed(String packageName) {
        String safe = clean(packageName);
        if (safe.isEmpty()) return false;
        for (AppCapability capability : AppCapability.values()) {
            if (!isAppAllowed(safe, capability)) return false;
        }
        return true;
    }

    public void setAppAllowed(String packageName, boolean allowed) {
        String safe = clean(packageName);
        if (safe.isEmpty()) return;
        Set<String> apps = legacyAllowedApps();
        SharedPreferences.Editor editor = prefs.edit();
        if (allowed) {
            apps.add(safe);
            for (AppCapability capability : AppCapability.values()) {
                Set<String> capabilityApps = capabilityApps(capability);
                capabilityApps.add(safe);
                editor.putStringSet(capability.prefsKey, capabilityApps);
            }
        } else {
            apps.remove(safe);
            for (AppCapability capability : AppCapability.values()) {
                Set<String> capabilityApps = capabilityApps(capability);
                capabilityApps.remove(safe);
                editor.putStringSet(capability.prefsKey, capabilityApps);
            }
        }
        editor.putStringSet(KEY_ALLOWED_APPS, apps).apply();
    }

    public void setAppCapabilityAllowed(String packageName, AppCapability capability, boolean allowed) {
        String safe = clean(packageName);
        if (safe.isEmpty() || capability == null) return;

        if (legacyAllowedApps().contains(safe) && !hasExplicitCapabilityConfig(safe)) {
            migrateLegacyAppToCapabilities(safe);
        }

        Set<String> capabilityApps = capabilityApps(capability);
        if (allowed) {
            capabilityApps.add(safe);
        } else {
            capabilityApps.remove(safe);
        }

        prefs.edit()
            .putStringSet(capability.prefsKey, capabilityApps)
            .putStringSet(KEY_ALLOWED_APPS, legacySummaryWith(safe, hasAnyCapabilityAllowed(safe, capability, allowed)))
            .apply();
    }

    public int allowedAppCount() {
        return allowedApps().size();
    }

    public static String ubuntuUserScope(AssistantProvider provider) {
        ProviderProfile profile = ProviderProfile.forProvider(
            provider == null ? AssistantProvider.CODEX : provider);
        return profile.home + "/**";
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private Set<String> legacyAllowedApps() {
        return new HashSet<>(prefs.getStringSet(KEY_ALLOWED_APPS, Collections.emptySet()));
    }

    private Set<String> capabilityApps(AppCapability capability) {
        return new HashSet<>(prefs.getStringSet(capability.prefsKey, Collections.emptySet()));
    }

    private boolean hasExplicitCapabilityConfig(String packageName) {
        String safe = clean(packageName);
        if (safe.isEmpty()) return false;
        for (AppCapability capability : AppCapability.values()) {
            if (capabilityApps(capability).contains(safe)) return true;
        }
        return false;
    }

    private void migrateLegacyAppToCapabilities(String packageName) {
        SharedPreferences.Editor editor = prefs.edit();
        for (AppCapability capability : AppCapability.values()) {
            Set<String> apps = capabilityApps(capability);
            apps.add(packageName);
            editor.putStringSet(capability.prefsKey, apps);
        }
        editor.apply();
    }

    private Set<String> legacySummaryWith(String packageName, boolean allowed) {
        Set<String> apps = legacyAllowedApps();
        if (allowed) {
            apps.add(packageName);
        } else {
            apps.remove(packageName);
        }
        return apps;
    }

    private boolean hasAnyCapabilityAllowed(String packageName, AppCapability changedCapability,
                                            boolean changedAllowed) {
        String safe = clean(packageName);
        if (safe.isEmpty()) return false;
        for (AppCapability capability : AppCapability.values()) {
            if (capability == changedCapability) {
                if (changedAllowed) return true;
            } else if (capabilityApps(capability).contains(safe)) {
                return true;
            }
        }
        return false;
    }
}
