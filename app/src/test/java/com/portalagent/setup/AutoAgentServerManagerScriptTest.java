package com.portalagent.setup;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AutoAgentServerManagerScriptTest {

    @Test
    public void arm64ScriptKeepsCurrentVersionAndArchiveName() {
        String script = AutoAgentServerManager.buildInnerScriptForTest(
            true, RuntimeArch.forAbiForTest("arm64-v8a"));

        assertTrue(script.contains("_tgz='/tmp/agentserver-linux-arm64.tar.gz'"));
        assertTrue(script.contains("releases/download/" + RuntimeVersions.AGENTSERVER_VERSION + "/agentserver-linux-arm64.tar.gz"));
    }

    @Test
    public void x86_64ScriptUsesSameVersionAmd64Fallback() {
        String script = AutoAgentServerManager.buildInnerScriptForTest(
            false, RuntimeArch.forAbiForTest("x86_64"));

        assertTrue(script.contains("_tgz='/tmp/agentserver-linux-amd64.tar.gz'"));
        assertTrue(script.contains("releases/download/" + RuntimeVersions.AGENTSERVER_VERSION + "/agentserver-linux-amd64.tar.gz"));
        assertFalse(script.contains("agentserver-linux-arm64.tar.gz"));
    }
}
