package com.termux.app.loom;

public final class LoomCommandBuilder {

    public static final String PROOT_USER = "claude";
    public static final String OBSERVER_HOME = "/home/claude/.loom/observer-local";
    public static final String DRIVER_HOME = "/home/claude/.loom/driver-local";
    public static final String DRIVER_PROJECT = "/home/claude/loom-driver";
    public static final String SLAVE_HOME = "/home/claude/.loom/slave-local";

    private static final String OBSERVER_PROCESS = "observer-server --config .*observer-local/observer.yaml";
    private static final String SLAVE_PROCESS = "slave-agent .*\\.loom/slave-local/config.yaml";

    private LoomCommandBuilder() {
    }

    public static String statusScript(String prefix) {
        String observerLog = logPath(prefix, "loom-observer.log");
        String slaveLog = logPath(prefix, "loom-slave.log");
        String driverRegisterLog = logPath(prefix, "loom-driver-register.log");
        String ubuntuStatus = ""
            + "set +e\n"
            + "echo '[loom] Ubuntu binaries'\n"
            + "command -v observer-server\n"
            + "command -v driver-agent\n"
            + "command -v slave-agent\n"
            + "echo '[loom] processes'\n"
            + "pgrep -f '" + OBSERVER_PROCESS + "' && echo 'observer: running' || echo 'observer: stopped'\n"
            + "pgrep -f '" + SLAVE_PROCESS + "' && echo 'slave: running' || echo 'slave: stopped'";

        return header()
            + "echo '[loom] Termux binaries'\n"
            + "command -v proot-distro\n"
            + readableCommand("pgrep -f '" + OBSERVER_PROCESS + "'")
            + readableCommand("pgrep -f '" + SLAVE_PROCESS + "'")
            + proot(ubuntuStatus) + "\n"
            + tailLog("observer log", observerLog)
            + tailLog("slave log", slaveLog)
            + tailLog("driver register log", driverRegisterLog);
    }

    public static String startObserverScript(String prefix) {
        String observerLog = logPath(prefix, "loom-observer.log");
        String command = ""
            + "cd " + OBSERVER_HOME + "\n"
            + "nohup observer-server --config observer.yaml >> " + shellQuote(observerLog) + " 2>&1 &";

        return header()
            + "command -v proot-distro\n"
            + proot(command) + "\n";
    }

    public static String stopObserverScript() {
        String command = "pkill -f '" + OBSERVER_PROCESS + "'";
        return header()
            + "command -v proot-distro\n"
            + readableCommand(command)
            + proot(command) + "\n";
    }

    public static String registerDriverScript() {
        String command = ""
            + "cd " + DRIVER_PROJECT + "\n"
            + DRIVER_PROJECT + "/driver-agent register --config " + DRIVER_PROJECT + "/config.yaml";

        return header()
            + "command -v proot-distro\n"
            + proot(command) + " >> \"$HOME/loom-driver-register.log\" 2>&1\n";
    }

    public static String startSlaveScript(String prefix) {
        String slaveLog = logPath(prefix, "loom-slave.log");
        String command = ""
            + "pkill -0 -f '" + SLAVE_PROCESS + "' && echo 'slave: already running' && exit 0\n"
            + "nohup slave-agent " + SLAVE_HOME + "/config.yaml >> " + shellQuote(slaveLog) + " 2>&1 &";

        return header()
            + "command -v proot-distro\n"
            + readableCommand("pkill -0 -f '" + SLAVE_PROCESS + "'")
            + proot(command) + "\n";
    }

    public static String stopSlaveScript() {
        String command = "pkill -f '" + SLAVE_PROCESS + "'";
        return header()
            + "command -v proot-distro\n"
            + readableCommand(command)
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
