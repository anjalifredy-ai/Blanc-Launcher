package net.kdt.pojavlaunch.aerix.hosting;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;

/**
 * Works out how to start an installed server, and how to run the installers of the modded ones.
 * Pure Java (java.io / java.util.jar only) so it is testable on a desktop JVM.
 */
public final class LaunchPlanner {
    private LaunchPlanner() {}

    /** Flags every server gets. The jline ones make console input plain stdin, which Android pipes in. */
    private static void commonJvmArgs(LaunchPlan plan) {
        plan.jvmArgs.add("-Dfile.encoding=UTF-8");
        plan.jvmArgs.add("-Djava.awt.headless=true");
        plan.jvmArgs.add("-Djline.terminal=jline.UnsupportedTerminal");
        plan.jvmArgs.add("-Dterminal.jline=false");
        plan.jvmArgs.add("-Dterminal.ansi=false");
    }

    /** The plan for running the installed server in [dir]. Throws if the install is missing or unrecognised. */
    public static LaunchPlan forServer(File dir, ServerSoftware software) throws IOException {
        switch (software) {
            case VANILLA:
            case PAPER:
            case PURPUR:
                return jarPlan(requireFile(dir, "server.jar", software), true);
            case FABRIC:
                return jarPlan(requireFile(dir, "fabric-server-launch.jar", software), true);
            case QUILT: {
                File jar = new File(dir, "quilt-server-launch.jar");
                if (!jar.isFile()) jar = new File(new File(dir, "server"), "quilt-server-launch.jar");
                if (!jar.isFile()) throw new IOException("Quilt is not installed yet (quilt-server-launch.jar is missing)");
                return jarPlan(jar, true);
            }
            case FORGE:
            case NEOFORGE:
                return forgeFamily(dir, software);
            default:
                throw new IOException("unsupported software " + software);
        }
    }

    /** The plan for running [installerJar] with [args] inside [dir]. */
    public static LaunchPlan installerPlan(File installerJar, List<String> args) throws IOException {
        LaunchPlan plan = jarPlan(installerJar, false);
        plan.appArgs.addAll(args);
        return plan;
    }

    private static File requireFile(File dir, String name, ServerSoftware software) throws IOException {
        File file = new File(dir, name);
        if (!file.isFile()) throw new IOException(software.displayName + " is not installed yet (" + name + " is missing)");
        return file;
    }

    private static LaunchPlan jarPlan(File jar, boolean serverArgs) throws IOException {
        LaunchPlan plan = new LaunchPlan();
        commonJvmArgs(plan);
        plan.mainClass = mainClassOf(jar);
        plan.classpath.add(jar.getAbsolutePath());
        if (serverArgs) plan.appArgs.add("nogui");
        return plan;
    }

    public static String mainClassOf(File jar) throws IOException {
        try (JarFile jarFile = new JarFile(jar)) {
            if (jarFile.getManifest() == null) throw new IOException(jar.getName() + " has no manifest");
            String main = jarFile.getManifest().getMainAttributes().getValue("Main-Class");
            if (main == null || main.trim().isEmpty()) throw new IOException(jar.getName() + " has no Main-Class");
            return main.trim();
        }
    }

    private static LaunchPlan forgeFamily(File dir, ServerSoftware software) throws IOException {
        File argsFile = findArgsFile(dir, software);
        if (argsFile != null) return argsFilePlan(dir, argsFile);
        // Forge up to 1.16.5 installs a plain launchable jar instead of an args file.
        File[] jars = dir.listFiles((d, name) -> {
            String n = name.toLowerCase();
            return n.endsWith(".jar") && n.startsWith("forge-") && !n.contains("installer") && !n.contains("shim");
        });
        if (jars != null && jars.length > 0) {
            Arrays.sort(jars, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            return jarPlan(jars[0], true);
        }
        throw new IOException(software.displayName + " is not installed yet (no launch files were produced)");
    }

    private static File findArgsFile(File dir, ServerSoftware software) {
        String[] roots = software == ServerSoftware.NEOFORGE
                ? new String[]{"libraries/net/neoforged/neoforge", "libraries/net/neoforged/forge"}
                : new String[]{"libraries/net/minecraftforge/forge"};
        for (String root : roots) {
            File[] versions = new File(dir, root).listFiles(File::isDirectory);
            if (versions == null) continue;
            Arrays.sort(versions, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (File version : versions) {
                File args = new File(version, "unix_args.txt");
                if (args.isFile()) return args;
            }
        }
        return null;
    }

    private static final Set<String> TAKES_VALUE = new HashSet<>(Arrays.asList(
            "-p", "--module-path", "--upgrade-module-path", "--add-modules", "--add-opens", "--add-exports",
            "--add-reads", "--limit-modules", "--patch-module", "-cp", "-classpath", "--class-path",
            "--enable-native-access"));
    private static final Set<String> PATH_OPTIONS = new HashSet<>(Arrays.asList(
            "-p", "--module-path", "--upgrade-module-path"));
    private static final Set<String> CLASSPATH_OPTIONS = new HashSet<>(Arrays.asList(
            "-cp", "-classpath", "--class-path"));

    /** Turns Forge/NeoForge's unix_args.txt (JVM options, then the main class, then program args) into a plan. */
    public static LaunchPlan argsFilePlan(File dir, File argsFile) throws IOException {
        LaunchPlan plan = new LaunchPlan();
        commonJvmArgs(plan);
        List<String> tokens = new ArrayList<>();
        for (String line : Files.readAllLines(argsFile.toPath(), StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            for (String token : trimmed.split("\\s+")) {
                if (token.length() >= 2 && token.startsWith("\"") && token.endsWith("\"")) {
                    token = token.substring(1, token.length() - 1);
                }
                tokens.add(token);
            }
        }
        int i = 0;
        while (i < tokens.size()) {
            String token = tokens.get(i);
            if (!token.startsWith("-")) break; // the main class
            if (TAKES_VALUE.contains(token) && i + 1 < tokens.size()) {
                String value = tokens.get(i + 1);
                if (CLASSPATH_OPTIONS.contains(token)) {
                    for (String entry : value.split(":")) if (!entry.isEmpty()) plan.classpath.add(absolute(dir, entry));
                } else if (PATH_OPTIONS.contains(token)) {
                    plan.jvmArgs.add(token);
                    plan.jvmArgs.add(absolutePathList(dir, value));
                } else {
                    plan.jvmArgs.add(token);
                    plan.jvmArgs.add(value);
                }
                i += 2;
                continue;
            }
            if (token.startsWith("-DlibraryDirectory=")) {
                plan.jvmArgs.add("-DlibraryDirectory=" + absolute(dir, token.substring("-DlibraryDirectory=".length())));
            } else {
                plan.jvmArgs.add(token);
            }
            i++;
        }
        if (i >= tokens.size()) throw new IOException(argsFile.getName() + " does not name a main class");
        plan.mainClass = tokens.get(i++);
        while (i < tokens.size()) plan.appArgs.add(tokens.get(i++));
        plan.appArgs.add("nogui");
        return plan;
    }

    private static String absolute(File dir, String path) {
        File f = new File(path);
        return f.isAbsolute() ? path : new File(dir, path).getAbsolutePath();
    }

    private static String absolutePathList(File dir, String list) {
        StringBuilder out = new StringBuilder();
        for (String entry : list.split(":")) {
            if (entry.isEmpty()) continue;
            if (out.length() > 0) out.append(':');
            out.append(absolute(dir, entry));
        }
        return out.toString();
    }
}
