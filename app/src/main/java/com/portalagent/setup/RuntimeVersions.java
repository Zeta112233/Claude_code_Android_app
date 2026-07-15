package com.portalagent.setup;

public final class RuntimeVersions {

    public static final String CODEX_VERSION = "0.144.4";
    public static final String CLAUDE_CODE_VERSION = "2.1.210";
    public static final String AGENTSERVER_VERSION = "v0.69.9";
    public static final String LOOM_VERSION = "v0.0.10";

    public static final String CODEX_NPM_SPEC = "@openai/codex@" + CODEX_VERSION;
    public static final String CLAUDE_CODE_NPM_SPEC =
        "@anthropic-ai/claude-code@" + CLAUDE_CODE_VERSION;

    private RuntimeVersions() {}
}