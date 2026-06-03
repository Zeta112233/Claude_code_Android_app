package com.termux.app.autotasks;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AutoLoomManagerScriptTest {

    @Test
    public void innerScriptInstallsRequiredBinariesAtomically() {
        String script = AutoLoomManager.buildInnerScriptForTest(true);

        assertTrue(script.contains("_tgz='/tmp/loom-linux-arm64.tgz'"));
        assertTrue(script.contains("observer-server"));
        assertTrue(script.contains("driver-agent"));
        assertTrue(script.contains("slave-agent"));
        assertTrue(script.contains("/usr/local/bin/observer-server.new"));
        assertTrue(script.contains("mv -f /usr/local/bin/observer-server.new /usr/local/bin/observer-server"));
        assertTrue(script.contains("mv -f /usr/local/bin/driver-agent.new /usr/local/bin/driver-agent"));
        assertTrue(script.contains("mv -f /usr/local/bin/slave-agent.new /usr/local/bin/slave-agent"));
    }

    @Test
    public void innerScriptHasOnlineFallbackAndPartFiles() {
        String script = AutoLoomManager.buildInnerScriptForTest(false);

        assertTrue(script.contains("github.com/agentserver/loom/releases/latest/download"));
        assertTrue(script.contains(".part"));
        assertTrue(script.contains("driver-skills.tar.gz"));
        assertTrue(script.contains("sha256sums.txt"));
    }

    @Test
    public void innerScriptCreatesRuntimeDirectories() {
        String script = AutoLoomManager.buildInnerScriptForTest(true);

        assertTrue(script.contains("/home/claude/.loom/observer-local"));
        assertTrue(script.contains("/home/claude/.loom/slave-local"));
        assertTrue(script.contains("/home/claude/loom-driver"));
        assertTrue(script.contains("chown -R claude:claude /home/claude/.loom /home/claude/loom-driver"));
    }

    @Test
    public void innerScriptUsesLocalArchiveLayoutAndInstallsSkills() {
        String script = AutoLoomManager.buildInnerScriptForTest(true);

        assertTrue(script.contains("\"$_tmpdir/loom/bin/observer-server\""));
        assertTrue(script.contains("\"$_tmpdir/loom/bin/driver-agent\""));
        assertTrue(script.contains("\"$_tmpdir/loom/bin/slave-agent\""));
        assertTrue(script.contains("\"$_tmpdir/loom/skills\""));
        assertTrue(script.contains("/home/claude/loom-driver/.claude/skills"));
    }

    @Test
    public void innerScriptCleansOwnBashrcHook() {
        String script = AutoLoomManager.buildInnerScriptForTest(true);

        assertTrue(script.contains("sed -i '/.loom-setup/d' ~/.bashrc"));
        assertTrue(script.contains("rm -f ~/.loom-setup.sh"));
    }
}
