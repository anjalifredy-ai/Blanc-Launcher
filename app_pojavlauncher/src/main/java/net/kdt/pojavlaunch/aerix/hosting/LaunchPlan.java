package net.kdt.pojavlaunch.aerix.hosting;

import java.util.ArrayList;
import java.util.List;

/** Everything needed to start a JVM: what JavaRunner.startJvm takes, minus the runtime and memory. */
public final class LaunchPlan {
    public final List<String> jvmArgs = new ArrayList<>();
    public final List<String> classpath = new ArrayList<>();
    public final List<String> appArgs = new ArrayList<>();
    public String mainClass;
    /** Directory the JVM should run in (the server's data directory). */
    public java.io.File workDir;
}
