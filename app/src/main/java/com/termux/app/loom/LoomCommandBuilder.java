package com.termux.app.loom;

public final class LoomCommandBuilder {

    public static final String PROOT_USER = "claude";
    public static final String OBSERVER_HOME = "/home/claude/.loom/observer-local";
    public static final String DRIVER_HOME = "/home/claude/.loom/driver-local";
    public static final String DRIVER_PROJECT = "/home/claude/loom-driver";
    public static final String SLAVE_HOME = "/home/claude/.loom/slave-local";

    private static final String OBSERVER_PROCESS = "observer-server --config .*observer-local/observer.yaml";
    private static final String SLAVE_PROCESS = "slave-agent .*\\.loom/slave-local/config.yaml";
    private static final String OBSERVER_CONFIG = OBSERVER_HOME + "/observer.yaml";
    private static final String SLAVE_CONFIG = SLAVE_HOME + "/config.yaml";

    private LoomCommandBuilder() {
    }

    public static String statusScript(String prefix) {
        String observerLog = logPath(prefix, "loom-observer.log");
        String slaveLog = logPath(prefix, "loom-slave.log");
        String driverRegisterLog = logPath(prefix, "loom-driver-register.log");
        String ubuntuStatus = ""
            + "set +e\n"
            + loomPidsFunction()
            + "echo '[loom] Ubuntu binaries'\n"
            + "command -v observer-server\n"
            + "command -v driver-agent\n"
            + "command -v slave-agent\n"
            + "echo '[loom] processes'\n"
            + "observer_pids=$(loom_pids '" + OBSERVER_PROCESS + "')\n"
            + "test -n \"$observer_pids\" && echo 'observer: running' || echo 'observer: stopped'\n"
            + "slave_pids=$(loom_pids '" + SLAVE_PROCESS + "')\n"
            + "test -n \"$slave_pids\" && echo 'slave: running' || echo 'slave: stopped'";

        return header()
            + "echo '[loom] Termux binaries'\n"
            + "command -v proot-distro\n"
            + readableCommand("pgrep -f '" + OBSERVER_PROCESS + "'")
            + readableCommand("pgrep -f '" + SLAVE_PROCESS + "'")
            + readableCommand("ps -p \"$p\" -o args= | grep -Eq 'bash -lc|proot-distro login' && continue")
            + proot(ubuntuStatus) + "\n"
            + tailLog("observer log", observerLog)
            + tailLog("slave log", slaveLog)
            + tailLog("driver register log", driverRegisterLog);
    }

    public static String startObserverScript(String prefix) {
        String observerLog = logPath(prefix, "loom-observer.log");
        String command = ""
            + "cd " + OBSERVER_HOME + "\n"
            + "exec observer-server --config " + OBSERVER_CONFIG;

        return header()
            + "command -v proot-distro\n"
            + readableCommand("nohup observer-server --config observer.yaml")
            + nohupProot(command, observerLog) + "\n";
    }

    public static String stopObserverScript() {
        String command = stopCommand(OBSERVER_PROCESS, "observer");
        return header()
            + "command -v proot-distro\n"
            + readableCommand("pkill -f '" + OBSERVER_PROCESS + "'")
            + proot(command) + "\n";
    }

    public static String registerDriverScript() {
        String command = ""
            + "cd " + DRIVER_PROJECT + "\n"
            + DRIVER_PROJECT + "/driver-agent register --config " + DRIVER_PROJECT + "/config.yaml";

        return header()
            + "set -o pipefail\n"
            + "command -v proot-distro\n"
            + proot(command) + " 2>&1 | tee -a \"$HOME/loom-driver-register.log\"\n";
    }

    public static String startSlaveScript(String prefix) {
        String slaveLog = logPath(prefix, "loom-slave.log");
        String command = ""
            + loomPidsFunction()
            + "pids=$(loom_pids '" + SLAVE_PROCESS + "')\n"
            + "if [ -n \"$pids\" ]; then echo 'slave: already running'; exit 0; fi\n"
            + "exec slave-agent " + SLAVE_CONFIG;

        return header()
            + "command -v proot-distro\n"
            + nohupProot(command, slaveLog) + "\n";
    }

    public static String stopSlaveScript() {
        String command = stopCommand(SLAVE_PROCESS, "slave");
        return header()
            + "command -v proot-distro\n"
            + readableCommand("pkill -f '" + SLAVE_PROCESS + "'")
            + proot(command) + "\n";
    }

    private static String header() {
        return "#!/data/data/com.termux/files/usr/bin/bash\n"
            + "set -e\n";
    }

    private static String proot(String innerCommand) {
        return "proot-distro login --user " + PROOT_USER + " ubuntu -- bash -lc "
            + shellQuote(innerCommand);
    }

    private static String nohupProot(String innerCommand, String logPath) {
        return "nohup " + proot(innerCommand) + " >> " + shellQuote(logPath) + " 2>&1 &";
    }

    private static String loomPidsFunction() {
        return "loom_pids() {\n"
            + "    pattern=\"$1\"\n"
            + "    pgrep -f \"$pattern\" 2>/dev/null | while read -r p; do\n"
            + "        [ \"$p\" = \"$$\" ] && continue\n"
            + "        ps -p \"$p\" -o args= 2>/dev/null | grep -Eq 'bash -lc|proot-distro login' && continue\n"
            + "        echo \"$p\"\n"
            + "    done\n"
            + "}\n";
    }

    private static String stopCommand(String processPattern, String name) {
        return ""
            + "set +e\n"
            + loomPidsFunction()
            + "pids=$(loom_pids '" + processPattern + "')\n"
            + "if [ -z \"$pids\" ]; then echo '" + name + ": not running'; exit 0; fi\n"
            + "kill $pids 2>/dev/null || true\n"
            + "echo '" + name + ": stopped'\n";
    }

    private static String logPath(String prefix, String fileName) {
        return prefix + "/../home/" + fileName;
    }

    private static String tailLog(String label, String path) {
        return "echo '[loom] recent " + label + "'\n"
            + "test -f " + shellQuote(path) + " && tail -n 40 " + shellQuote(path) + " || true\n";
    }

    private static String readableCommand(String command) {
        return "# " + command + "\n";
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
