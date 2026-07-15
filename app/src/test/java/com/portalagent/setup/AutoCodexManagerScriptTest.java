package com.portalagent.setup;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AutoCodexManagerScriptTest {

    @Test
    public void innerScriptSetsUpCodexUserCliAndInstructionsNonInteractively() {
        String script = AutoCodexManager.buildInnerScriptForTest();

        assertTrue(script.contains("id codex >/dev/null 2>&1 || useradd -m -s /bin/bash codex"));
        assertTrue(script.contains("npm install -g @openai/codex@" + RuntimeVersions.CODEX_VERSION));
        assertTrue(script.contains("command -v codex"));
        assertTrue(script.contains("/home/codex/AGENTS.md"));
        assertTrue(script.contains("/home/codex/.codex/skills/android-phone/SKILL.md"));
        assertTrue(script.contains("[mcp_servers.android-mcp]"));
        assertTrue(script.contains("type = \"streamable_http\""));
        assertTrue(script.contains("url = \"http://127.0.0.1:8765/mcp\""));
        assertFalse(script.contains("OpenAI API Key"));
        assertFalse(script.contains("read -r _openai_key"));
        assertFalse(script.contains("export OPENAI_API_KEY"));
        assertFalse(script.contains("ANTHROPIC_API_KEY"));
        assertTrue(script.contains("sed -i '/.codex-setup/d' ~/.bashrc"));
        assertTrue(script.contains("rm -f ~/.codex-setup.sh"));
    }

    @Test
    public void innerScriptExitsBeforeOpenAiPromptWhenCodexSetupAlreadyCompleted() {
        String script = AutoCodexManager.buildInnerScriptForTest();

        assertTrue(script.contains("CODEX_TARGET_VERSION='" + RuntimeVersions.CODEX_VERSION + "'"));
        int sentinel = script.indexOf("/home/codex/.codex/setup-complete");
        int earlyExit = script.indexOf("[ \"$_codex_current\" = \"$CODEX_TARGET_VERSION\" ] && [ -f \"$CODEX_SETUP_SENTINEL\" ]");
        int sentinelTouch = script.indexOf("touch \"$CODEX_SETUP_SENTINEL\"");

        assertTrue("script should define a persistent setup-complete sentinel", sentinel >= 0);
        assertTrue("script should check installed Codex version and sentinel before exiting", earlyExit >= 0);
        assertTrue("successful setup should leave the persistent sentinel",
            sentinelTouch >= 0);
    }

    @Test
    public void innerScriptDoesNotWriteOpenAiKeyFromRootHook() {
        String script = AutoCodexManager.buildInnerScriptForTest();

        assertFalse(script.contains("shell_quote()"));
        assertFalse(script.contains("_openai_key"));
        assertFalse(script.contains("export OPENAI_API_KEY=\\\"%s\\\""));
    }

    @Test
    public void innerScriptUsesPinnedCodexReleaseVersion() {
        String script = AutoCodexManager.buildInnerScriptForTest();

        assertTrue(script.contains(RuntimeVersions.CODEX_NPM_SPEC));
        assertFalse(script.contains("npm install -g @openai/codex 2>&1"));
    }

    @Test
    public void innerScriptUpgradesCodexWhenInstalledVersionDiffers() {
        String script = AutoCodexManager.buildInnerScriptForTest();

        assertTrue(script.contains("codex --version"));
        assertTrue(script.contains("[ \"$_codex_current\" != \"$CODEX_TARGET_VERSION\" ]"));
        assertTrue(script.contains("npm install -g " + RuntimeVersions.CODEX_NPM_SPEC));
    }
}
