package com.portalagent.setup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RuntimeArchTest {

    @Test
    public void arm64KeepsBundledRuntimeNames() {
        RuntimeArch arch = RuntimeArch.forAbiForTest("arm64-v8a");

        assertEquals("aarch64", arch.prootDistroArch());
        assertEquals("arm64", arch.ubuntuBaseArch());
        assertEquals("agentserver-linux-arm64.tar.gz", arch.agentServerArchiveName());
        assertEquals("agentserver-linux-arm64.tgz", arch.agentServerBundledAssetName());
        assertEquals("linux-arm64", arch.loomAssetArch());
        assertEquals("@anthropic-ai/claude-code-linux-arm64", arch.claudeNativePackage());
        assertTrue(arch.supportsBundledSnapshot());
        assertTrue(arch.supportsBundledTermuxTools());
        assertTrue(arch.supportsBundledAgentServer());
        assertTrue(arch.supportsBundledLoom());
    }

    @Test
    public void x86_64UsesOnlineAmd64RuntimeNames() {
        RuntimeArch arch = RuntimeArch.forAbiForTest("x86_64");

        assertEquals("x86_64", arch.prootDistroArch());
        assertEquals("amd64", arch.ubuntuBaseArch());
        assertEquals("agentserver-linux-amd64.tar.gz", arch.agentServerArchiveName());
        assertEquals("https://github.com/agentserver/agentserver/releases/download/" +
                RuntimeVersions.AGENTSERVER_VERSION + "/agentserver-linux-amd64.tar.gz",
            arch.agentServerDownloadUrl());
        assertEquals("linux-amd64", arch.loomAssetArch());
        assertEquals("@anthropic-ai/claude-code-linux-x64", arch.claudeNativePackage());
        assertEquals("x86_64", arch.resourceArchTag());
        assertFalse(arch.supportsBundledSnapshot());
        assertFalse(arch.supportsBundledTermuxTools());
        assertFalse(arch.supportsBundledAgentServer());
        assertFalse(arch.supportsBundledLoom());
        assertTrue(arch.supportsLinuxBinaries());
    }
}
