package com.portalagent.setup;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AutoClaudeManagerScriptTest {

    @Test
    public void innerScriptDoesNotBlockOnInteractiveAuthPrompts() throws Exception {
        String script = AutoClaudeManager.buildInnerScriptForTest();

        assertTrue(script.contains("CLAUDE_TARGET_VERSION='" + RuntimeVersions.CLAUDE_CODE_VERSION + "'"));
        assertTrue(script.contains("npm install -g @anthropic-ai/claude-code@" + RuntimeVersions.CLAUDE_CODE_VERSION + " --include=optional"));
        assertTrue(script.contains("_chome=/home/claude"));
        assertTrue(script.contains("hasCompletedOnboarding"));
        assertTrue(script.contains("claude mcp add --transport http android-mcp"));

        assertFalse(script.contains("read -r _auth"));
        assertFalse(script.contains("read -r _key"));
        assertFalse(script.contains("read -r _ep"));
        assertFalse(script.contains("read -r _base"));
        assertFalse(script.contains("Anthropic API Key"));
    }

    @Test
    public void innerScriptUsesX64NativePackageOnX86_64() {
        String script = AutoClaudeManager.buildInnerScriptForTest(
            RuntimeArch.forAbiForTest("x86_64"));

        assertTrue(script.contains("@anthropic-ai/claude-code-linux-x64@" + RuntimeVersions.CLAUDE_CODE_VERSION));
        assertFalse(script.contains("npm install -g @anthropic-ai/claude-code-linux-arm64 --registry"));
    }

    @Test
    public void innerScriptUsesPinnedClaudeReleaseVersion() {
        String script = AutoClaudeManager.buildInnerScriptForTest();

        assertTrue(script.contains(RuntimeVersions.CLAUDE_CODE_NPM_SPEC));
        assertTrue(script.contains(RuntimeArch.forAbiForTest("arm64-v8a").claudeNativePackage()
            + "@" + RuntimeVersions.CLAUDE_CODE_VERSION));
        assertFalse(script.contains("npm install -g @anthropic-ai/claude-code --include=optional 2>&1"));
    }

    @Test
    public void innerScriptUpgradesClaudeWhenInstalledVersionDiffers() {
        String script = AutoClaudeManager.buildInnerScriptForTest();

        assertTrue(script.contains("claude --version"));
        assertTrue(script.contains("[ \"$_claude_current\" = \"$CLAUDE_TARGET_VERSION\" ]"));
        assertTrue(script.contains("npm install -g " + RuntimeVersions.CLAUDE_CODE_NPM_SPEC));
    }
}
