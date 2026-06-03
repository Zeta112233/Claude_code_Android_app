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
            + "agentserver:\n"
            + "  url: " + s.agentServerUrl + "\n"
            + "  name: " + s.driverName + "\n"
            + "credentials:\n"
            + "  username: \"\"\n"
            + "  password: \"\"\n"
            + "discovery:\n"
            + "  enabled: true\n"
            + "agent:\n"
            + "  kind: claude\n"
            + "claude:\n"
            + "  bin: claude\n"
            + "  workdir: " + projectDir + "\n"
            + "planner:\n"
            + "  enabled: true\n"
            + "fanout:\n"
            + "  enabled: true\n"
            + "driver_defaults:\n"
            + "  audit_log_dir: " + joinPath(projectDir, "logs") + "\n"
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
        yaml.append("agentserver:\n");
        yaml.append("  url: ").append(s.agentServerUrl).append("\n");
        yaml.append("  name: ").append(s.slaveName).append("\n");
        yaml.append("credentials:\n");
        yaml.append("  username: \"\"\n");
        yaml.append("  password: \"\"\n");
        yaml.append("agent:\n");
        yaml.append("  kind: claude\n");
        yaml.append("claude:\n");
        yaml.append("  workdir: ").append(slaveHome).append("\n");
        yaml.append("mcp_servers: []\n");
        yaml.append("discovery:\n");
        yaml.append("  skills:\n");
        yaml.append("    - chat\n");
        yaml.append("    - bash\n");
        yaml.append("    - permissions\n");
        yaml.append("    - register_mcp\n");
        yaml.append("    - file\n");
        yaml.append("planner:\n");
        yaml.append("  enabled: true\n");
        yaml.append("fanout:\n");
        yaml.append("  enabled: true\n");
        yaml.append("resources:\n");
        yaml.append("  cpu:\n");
        yaml.append("    cores: ").append(cpuCores).append("\n");
        yaml.append("    arch: ").append(arch).append("\n");
        yaml.append("  memory_gb: ").append(memoryGb).append("\n");
        yaml.append("tags:\n");
        appendTags(yaml, s.tags);
        yaml.append("observer:\n");
        yaml.append("  enabled: true\n");
        yaml.append("  url: ").append(s.observerUrl).append("\n");
        yaml.append("  workspace_id: ").append(s.workspaceId).append("\n");
        yaml.append("  agent_id: ").append(s.slaveName).append("\n");
        yaml.append("  api_key: ").append(LoomSettings.yamlQuote(s.workspaceApiKey)).append("\n");
        yaml.append("  token_state_path: ").append(joinPath(slaveHome, "observer.token")).append("\n");
        return yaml.toString();
    }

    private static void appendTags(StringBuilder yaml, String tags) {
        String tagString = tags == null || tags.trim().isEmpty() ? "android" : tags;
        String[] parts = tagString.split(",");
        boolean appended = false;
        for (String part : parts) {
            String tag = part.trim();
            if (!tag.isEmpty()) {
                yaml.append("  - ").append(tag).append("\n");
                appended = true;
            }
        }
        if (!appended) {
            yaml.append("  - android\n");
        }
    }

    private static String joinPath(String dir, String file) {
        if (dir.endsWith("/")) {
            return dir + file;
        }
        return dir + "/" + file;
    }
}
