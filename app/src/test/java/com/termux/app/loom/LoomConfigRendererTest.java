package com.termux.app.loom;

import org.junit.Assert;
import org.junit.Test;

public class LoomConfigRendererTest {

    @Test
    public void observerConfigContainsListenDbAndApiKey() {
        LoomSettings settings = LoomSettings.defaults()
            .withObserverListenAddr("127.0.0.1:8090")
            .withWorkspaceApiKey("secret-key");

        String yaml = LoomConfigRenderer.renderObserverConfig(settings, "/home/claude/.loom/observer-local");

        Assert.assertTrue(yaml.contains("listen_addr: 127.0.0.1:8090"));
        Assert.assertTrue(yaml.contains("db_path: /home/claude/.loom/observer-local/observer.db"));
        Assert.assertTrue(yaml.contains("id: bootstrap"));
        Assert.assertTrue(yaml.contains("key: \"secret-key\""));
    }

    @Test
    public void driverConfigContainsAgentServerObserverAndClaudeBackend() {
        LoomSettings settings = LoomSettings.defaults()
            .withAgentServerUrl("https://agent.example.com")
            .withObserverUrl("http://127.0.0.1:8090")
            .withWorkspaceId("ws-phone")
            .withWorkspaceApiKey("secret-key")
            .withDriverName("driver-phone");

        String yaml = LoomConfigRenderer.renderDriverConfig(
            settings,
            "/home/claude/loom-driver",
            "/home/claude/.loom/driver-local");

        Assert.assertTrue(yaml.contains("url: https://agent.example.com"));
        Assert.assertTrue(yaml.contains("name: driver-phone"));
        Assert.assertTrue(yaml.contains("kind: claude"));
        Assert.assertTrue(yaml.contains("workdir: /home/claude/loom-driver"));
        Assert.assertTrue(yaml.contains("url: http://127.0.0.1:8090"));
        Assert.assertTrue(yaml.contains("workspace_id: ws-phone"));
        Assert.assertTrue(yaml.contains("agent_id: driver-phone"));
        Assert.assertTrue(yaml.contains("api_key: \"secret-key\""));
        Assert.assertTrue(yaml.contains("token_state_path: /home/claude/.loom/driver-local/observer.token"));
    }

    @Test
    public void slaveConfigContainsSkillsResourcesAndTags() {
        LoomSettings settings = LoomSettings.defaults()
            .withAgentServerUrl("https://agent.example.com")
            .withObserverUrl("http://127.0.0.1:8090")
            .withWorkspaceId("ws-phone")
            .withWorkspaceApiKey("secret-key")
            .withSlaveName("slave-phone")
            .withTags("android,phone,aarch64");

        String yaml = LoomConfigRenderer.renderSlaveConfig(
            settings,
            "/home/claude/.loom/slave-local",
            8,
            "aarch64",
            6);

        Assert.assertTrue(yaml.contains("name: slave-phone"));
        Assert.assertTrue(yaml.contains("- chat"));
        Assert.assertTrue(yaml.contains("- bash"));
        Assert.assertTrue(yaml.contains("- file"));
        Assert.assertTrue(yaml.contains("- register_mcp"));
        Assert.assertTrue(yaml.contains("cores: 8"));
        Assert.assertTrue(yaml.contains("arch: aarch64"));
        Assert.assertTrue(yaml.contains("memory_gb: 6"));
        Assert.assertTrue(yaml.contains("- android"));
        Assert.assertTrue(yaml.contains("- phone"));
        Assert.assertTrue(yaml.contains("- aarch64"));
    }
}
