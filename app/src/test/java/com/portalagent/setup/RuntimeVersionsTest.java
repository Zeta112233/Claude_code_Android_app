package com.portalagent.setup;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RuntimeVersionsTest {

    @Test
    public void releaseRuntimeVersionsArePinnedAndExposed() {
        assertTrue(RuntimeVersions.CODEX_NPM_SPEC.startsWith("@openai/codex@"));
        assertTrue(RuntimeVersions.CLAUDE_CODE_NPM_SPEC.startsWith("@anthropic-ai/claude-code@"));
        assertTrue(RuntimeVersions.AGENTSERVER_VERSION.startsWith("v"));
        assertTrue(RuntimeVersions.LOOM_VERSION.startsWith("v"));

        assertFalse(RuntimeVersions.CODEX_VERSION.isEmpty());
        assertFalse(RuntimeVersions.CLAUDE_CODE_VERSION.isEmpty());
        assertFalse(RuntimeVersions.AGENTSERVER_VERSION.isEmpty());
        assertFalse(RuntimeVersions.LOOM_VERSION.isEmpty());
    }
}
