package com.termux.app.loom;

import org.junit.Assert;
import org.junit.Test;

public class LoomCommandBuilderTest {
    @Test
    public void statusChecksAllRoleBinariesAndProcesses() {
        String script = LoomCommandBuilder.statusScript("/data/data/com.termux/files/usr");

        Assert.assertTrue(script.contains("command -v proot-distro"));
        Assert.assertTrue(script.contains("command -v observer-server"));
        Assert.assertTrue(script.contains("command -v driver-agent"));
        Assert.assertTrue(script.contains("command -v slave-agent"));
        Assert.assertTrue(script.contains("pgrep -f 'observer-server --config .*observer-local/observer.yaml'"));
        Assert.assertTrue(script.contains("pgrep -f 'slave-agent .*\\.loom/slave-local/config.yaml'"));
    }

    @Test
    public void startObserverUsesNohupAndSpecificConfig() {
        String script = LoomCommandBuilder.startObserverScript("/data/data/com.termux/files/usr");

        Assert.assertTrue(script.contains("cd /home/claude/.loom/observer-local"));
        Assert.assertTrue(script.contains("nohup observer-server --config observer.yaml"));
        Assert.assertTrue(script.contains("loom-observer.log"));
    }

    @Test
    public void stopScriptsUseNarrowKillPatterns() {
        Assert.assertTrue(LoomCommandBuilder.stopObserverScript()
            .contains("pkill -f 'observer-server --config .*observer-local/observer.yaml'"));
        Assert.assertTrue(LoomCommandBuilder.stopSlaveScript()
            .contains("pkill -f 'slave-agent .*\\.loom/slave-local/config.yaml'"));
    }

    @Test
    public void registerDriverUsesExpectedProjectPath() {
        String script = LoomCommandBuilder.registerDriverScript();

        Assert.assertTrue(script.contains("/home/claude/loom-driver/driver-agent register"));
        Assert.assertTrue(script.contains("--config /home/claude/loom-driver/config.yaml"));
    }
}
