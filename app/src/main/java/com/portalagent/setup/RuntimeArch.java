package com.portalagent.setup;

import android.os.Build;

import androidx.annotation.NonNull;

import java.util.Locale;

public final class RuntimeArch {

    private final String mAndroidAbi;
    private final String mTermuxArch;
    private final String mUbuntuBaseArch;
    private final String mAgentServerAssetArch;
    private final String mLoomAssetArch;
    private final String mClaudeNativePackage;
    private final String mResourceArchTag;
    private final String mAndroidPlatformArch;
    private final boolean mBundledSnapshotSupported;
    private final boolean mBundledTermuxToolsSupported;
    private final boolean mBundledAgentServerSupported;
    private final boolean mBundledLoomSupported;
    private final boolean mLinuxBinarySupported;

    private RuntimeArch(
            @NonNull String androidAbi,
            @NonNull String termuxArch,
            @NonNull String ubuntuBaseArch,
            @NonNull String agentServerAssetArch,
            @NonNull String loomAssetArch,
            @NonNull String claudeNativePackage,
            @NonNull String resourceArchTag,
            @NonNull String androidPlatformArch,
            boolean bundledSnapshotSupported,
            boolean bundledTermuxToolsSupported,
            boolean bundledAgentServerSupported,
            boolean bundledLoomSupported,
            boolean linuxBinarySupported) {
        mAndroidAbi = androidAbi;
        mTermuxArch = termuxArch;
        mUbuntuBaseArch = ubuntuBaseArch;
        mAgentServerAssetArch = agentServerAssetArch;
        mLoomAssetArch = loomAssetArch;
        mClaudeNativePackage = claudeNativePackage;
        mResourceArchTag = resourceArchTag;
        mAndroidPlatformArch = androidPlatformArch;
        mBundledSnapshotSupported = bundledSnapshotSupported;
        mBundledTermuxToolsSupported = bundledTermuxToolsSupported;
        mBundledAgentServerSupported = bundledAgentServerSupported;
        mBundledLoomSupported = bundledLoomSupported;
        mLinuxBinarySupported = linuxBinarySupported;
    }

    @NonNull
    public static RuntimeArch current() {
        if (!"Dalvik".equals(System.getProperty("java.vm.name"))) {
            return forAbiForTest("arm64-v8a");
        }
        try {
            String[] abis = Build.SUPPORTED_ABIS;
            if (abis != null && abis.length > 0 && abis[0] != null && !abis[0].isEmpty()) {
                return forAbiForTest(abis[0]);
            }
        } catch (Throwable ignored) {
        }
        return forAbiForTest("arm64-v8a");
    }

    @NonNull
    public static RuntimeArch forAbiForTest(@NonNull String abi) {
        String normalized = abi.toLowerCase(Locale.US);
        switch (normalized) {
            case "x86_64":
                return new RuntimeArch(
                    "x86_64",
                    "x86_64",
                    "amd64",
                    "amd64",
                    "linux-amd64",
                    "@anthropic-ai/claude-code-linux-x64",
                    "x86_64",
                    "x86_64",
                    false,
                    false,
                    false,
                    false,
                    true);
            case "x86":
                return new RuntimeArch(
                    "x86",
                    "i686",
                    "i386",
                    "",
                    "",
                    "",
                    "i686",
                    "x86",
                    false,
                    false,
                    false,
                    false,
                    false);
            case "armeabi-v7a":
            case "armeabi":
                return new RuntimeArch(
                    "armeabi-v7a",
                    "arm",
                    "armhf",
                    "",
                    "",
                    "",
                    "arm",
                    "arm",
                    false,
                    false,
                    false,
                    false,
                    false);
            case "arm64-v8a":
            default:
                return new RuntimeArch(
                    "arm64-v8a",
                    "aarch64",
                    "arm64",
                    "arm64",
                    "linux-arm64",
                    "@anthropic-ai/claude-code-linux-arm64",
                    "aarch64",
                    "arm64",
                    true,
                    true,
                    true,
                    true,
                    true);
        }
    }

    @NonNull
    public String androidAbi() {
        return mAndroidAbi;
    }

    @NonNull
    public String termuxArch() {
        return mTermuxArch;
    }

    @NonNull
    public String prootDistroArch() {
        return mTermuxArch;
    }

    @NonNull
    public String ubuntuBaseArch() {
        return mUbuntuBaseArch;
    }

    @NonNull
    public String agentServerAssetArch() {
        return mAgentServerAssetArch;
    }

    @NonNull
    public String loomAssetArch() {
        return mLoomAssetArch;
    }

    @NonNull
    public String claudeNativePackage() {
        return mClaudeNativePackage;
    }

    @NonNull
    public String resourceArchTag() {
        return mResourceArchTag;
    }

    @NonNull
    public String androidPlatformArch() {
        return mAndroidPlatformArch;
    }

    public boolean supportsBundledSnapshot() {
        return mBundledSnapshotSupported;
    }

    public boolean supportsBundledTermuxTools() {
        return mBundledTermuxToolsSupported;
    }

    public boolean supportsBundledAgentServer() {
        return mBundledAgentServerSupported;
    }

    public boolean supportsBundledLoom() {
        return mBundledLoomSupported;
    }

    public boolean supportsLinuxBinaries() {
        return mLinuxBinarySupported;
    }

    @NonNull
    public String agentServerArchiveName() {
        return mAgentServerAssetArch.isEmpty()
            ? ""
            : "agentserver-linux-" + mAgentServerAssetArch + ".tar.gz";
    }

    @NonNull
    public String agentServerBundledAssetName() {
        return mAgentServerAssetArch.isEmpty()
            ? ""
            : "agentserver-linux-" + mAgentServerAssetArch + ".tgz";
    }

    @NonNull
    public String agentServerTmpPath() {
        String archive = agentServerArchiveName();
        return archive.isEmpty() ? "" : "/tmp/" + archive;
    }

    @NonNull
    public String agentServerDownloadUrl() {
        String archive = agentServerArchiveName();
        return archive.isEmpty()
            ? ""
            : "https://github.com/agentserver/agentserver/releases/download/"
                + RuntimeVersions.AGENTSERVER_VERSION + "/" + archive;
    }

    @NonNull
    public String loomBundledAssetName() {
        return mLoomAssetArch.isEmpty()
            ? ""
            : "loom-" + mLoomAssetArch + ".tgz";
    }

    @NonNull
    public String loomTmpPath() {
        String name = loomBundledAssetName();
        return name.isEmpty() ? "" : "/tmp/" + name;
    }
}
