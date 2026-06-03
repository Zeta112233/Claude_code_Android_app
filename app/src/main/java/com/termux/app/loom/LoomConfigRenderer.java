package com.termux.app.loom;

public final class LoomConfigRenderer {

    private LoomConfigRenderer() {
    }

    public static String renderObserverConfig(LoomSettings s, String observerHome) {
        String dbPath = joinPath(observerHome, "observer.db");
        return ""
            + "listen_addr: " + s.observerListenAddr + "\n"
            + "db_path: " + dbPath + "\n"
            + "api_keys:\n"
            + "  - id: bootstrap\n"
            + "    key: " + LoomSettings.yamlQuote(s.workspaceApiKey) + "\n"
            + "    note: Android app bootstrap key\n";
    }

    public static String renderDriverConfig(LoomSettings s, String projectDir, String tokenDir) {
        return ""
            + "server:\n"
            + "  url: " + s.agentServerUrl + "\n"
            + "  name: " + s.driverName + "\n"
            + "credentials:\n"
            + "  sandbox_id: \"\"\n"
            + "  tunnel_token: \"\"\n"
            + "  proxy_token: \"\"\n"
            + "  workspace_id: \"\"\n"
            + "  short_id: \"\"\n"
            + "listen_addr: 127.0.0.1:0\n"
            + "discovery:\n"
            + "  display_name: " + s.driverName + "\n"
            + "  description: Loom Android driver\n"
            + "  skills:\n"
            + "    - chat\n"
            + "    - bash\n"
            + "    - file\n"
            + "    - register_mcp\n"
            + "agent:\n"
            + "  kind: claude\n"
            + "claude:\n"
            + "  bin: claude\n"
            + "  workdir: " + projectDir + "\n"
            + "  extra_args: []\n"
            + "planner:\n"
            + "  bin: loom-planner\n"
            + "  timeout_sec: 300\n"
            + "  extra_args: []\n"
            + "fanout:\n"
            + "  max_concurrency: 4\n"
            + "  default_policy: local\n"
            + "  policy_by_skill: {}\n"
            + "  subtask_defaults: {}\n"
            + "driver_defaults:\n"
            + "  target_display_name: " + s.slaveName + "\n"
            + "  task_timeout_sec: 300\n"
            + "  audit_log_dir: " + joinPath(projectDir, "logs") + "\n"
            + "  disable_uid_check: true\n"
            + "  max_dir_cache_entries: 256\n"
            + "  artifact_transport: observer\n"
            + "observer:\n"
            + "  enabled: true\n"
            + "  url: " + s.observerUrl + "\n"
            + "  workspace_id: " + s.workspaceId + "\n"
            + "  agent_id: " + s.driverName + "\n"
            + "  api_key: " + LoomSettings.yamlQuote(s.workspaceApiKey) + "\n"
            + "  token_state_path: " + joinPath(tokenDir, "observer.token") + "\n";
    }

    public static String renderSlaveConfig(
        LoomSettings s,
        String slaveHome,
        int cpuCores,
        String arch,
        int memoryGb) {
        StringBuilder yaml = new StringBuilder();
        yaml.append("server:\n");
        yaml.append("  url: ").append(s.agentServerUrl).append("\n");
        yaml.append("  name: ").append(s.slaveName).append("\n");
        yaml.append("credentials:\n");
        yaml.append("  sandbox_id: \"\"\n");
        yaml.append("  tunnel_token: \"\"\n");
        yaml.append("  proxy_token: \"\"\n");
        yaml.append("  short_id: \"\"\n");
        yaml.append("agent:\n");
        yaml.append("  kind: claude\n");
        yaml.append("claude:\n");
        yaml.append("  bin: claude\n");
        yaml.append("  workdir: ").append(slaveHome).append("\n");
        yaml.append("  extra_args: []\n");
        yaml.append("mcp_servers: {}\n");
        yaml.append("discovery:\n");
        yaml.append("  display_name: ").append(s.slaveName).append("\n");
        yaml.append("  description: Loom Android slave\n");
        yaml.append("  skills:\n");
        yaml.append("    - chat\n");
        yaml.append("    - bash\n");
        yaml.append("    - permissions\n");
        yaml.append("    - register_mcp\n");
        yaml.append("    - file\n");
        yaml.append("planner:\n");
        yaml.append("  bin: loom-planner\n");
        yaml.append("  timeout_sec: 300\n");
        yaml.append("  extra_args: []\n");
        yaml.append("fanout:\n");
        yaml.append("  max_concurrency: 4\n");
        yaml.append("  default_policy: local\n");
        yaml.append("  policy_by_skill: {}\n");
        yaml.append("resources:\n");
        yaml.append("  cpu:\n");
        yaml.append("    cores: ").append(cpuCores).append("\n");
        yaml.append("    arch: ").append(arch).append("\n");
        yaml.append("  memory_gb: ").append(memoryGb).append("\n");
        yaml.append("  tags:\n");
        appendTags(yaml, s.tags, "    ");
        yaml.append("observer:\n");
        yaml.append("  enabled: true\n");
        yaml.append("  url: ").append(s.observerUrl).append("\n");
        yaml.append("  workspace_id: ").append(s.workspaceId).append("\n");
        yaml.append("  agent_id: ").append(s.slaveName).append("\n");
        yaml.append("  api_key: ").append(LoomSettings.yamlQuote(s.workspaceApiKey)).append("\n");
        yaml.append("  token_state_path: ").append(joinPath(slaveHome, "observer.token")).append("\n");
        return yaml.toString();
    }

    private static void appendTags(StringBuilder yaml, String tags, String indent) {
        String tagString = tags == null || tags.trim().isEmpty() ? "android" : tags;
        String[] parts = tagString.split(",");
        boolean appended = false;
        for (String part : parts) {
            String tag = part.trim();
            if (!tag.isEmpty()) {
                yaml.append(indent).append("- ").append(tag).append("\n");
                appended = true;
            }
        }
        if (!appended) {
            yaml.append(indent).append("- android\n");
        }
    }

    private static String joinPath(String dir, String file) {
        if (dir.endsWith("/")) {
            return dir + file;
        }
        return dir + "/" + file;
    }
}
