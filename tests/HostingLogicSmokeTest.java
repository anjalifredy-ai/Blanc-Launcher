import net.kdt.pojavlaunch.aerix.hosting.BoreTunnel;
import net.kdt.pojavlaunch.aerix.hosting.IdleTracker;
import net.kdt.pojavlaunch.aerix.hosting.LaunchPlan;
import net.kdt.pojavlaunch.aerix.hosting.LaunchPlanner;
import net.kdt.pojavlaunch.aerix.hosting.ServerSoftware;
import net.kdt.pojavlaunch.aerix.hosting.McProtocol;
import net.kdt.pojavlaunch.aerix.hosting.ServerProperties;
import net.kdt.pojavlaunch.aerix.hosting.SleepListener;
import net.kdt.pojavlaunch.aerix.hosting.StatusPing;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Desktop-JVM checks for the pure-Java parts of Aerix server hosting. */
public class HostingLogicSmokeTest {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    static int freePort() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) { return s.getLocalPort(); }
    }

    public static void main(String[] args) throws Exception {
        varInts();
        listener();
        bore();
        properties();
        idle();
        planner();
        System.out.println("Hosting logic smoke test passed");
    }

    static void varInts() throws Exception {
        int[] values = {0, 1, 127, 128, 255, 25565, 2097151, Integer.MAX_VALUE, -1};
        for (int v : values) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            McProtocol.writeVarInt(out, v);
            check(McProtocol.readVarInt(new ByteArrayInputStream(out.toByteArray())) == v, "varint " + v);
        }
        byte[] hs = McProtocol.buildHandshake(767, "play.example.com", 25565, 2);
        McProtocol.Frame f = McProtocol.readFrame(new ByteArrayInputStream(hs));
        McProtocol.Handshake h = McProtocol.parseHandshake(f);
        check(h.protocol == 767 && h.host.equals("play.example.com") && h.port == 25565 && h.nextState == 2, "handshake round trip");
        check(McProtocol.jsonString("a\"b\n").equals("\"a\\\"b\\n\""), "json escaping");
    }

    static String readDisconnect(InputStream in) throws Exception {
        McProtocol.Frame frame = McProtocol.readFrame(in);
        check(frame != null && frame.id == 0x00, "login disconnect packet id");
        return McProtocol.readString(new ByteArrayInputStream(frame.payload), 4096);
    }

    static void sendLogin(Socket s, String name) throws Exception {
        OutputStream out = s.getOutputStream();
        out.write(McProtocol.buildHandshake(767, "localhost", 25565, 2));
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        McProtocol.writeString(payload, name);
        payload.write(new byte[16]); // the UUID newer clients append
        out.write(McProtocol.packet(0x00, payload.toByteArray()));
        out.flush();
    }

    static void listener() throws Exception {
        final AtomicReference<String> woken = new AtomicReference<>();
        final AtomicInteger wakeCalls = new AtomicInteger();
        int port = freePort();
        SleepListener listener = new SleepListener(port, name -> { woken.set(name); wakeCalls.incrementAndGet(); return true; });
        listener.info.versionName = "Paper 1.21.4";
        listener.info.maxPlayers = 12;
        listener.info.motd = "My World";
        listener.start();
        try {
            // Server list while sleeping.
            StatusPing.Result sleeping = StatusPing.ping("127.0.0.1", port, 3000);
            check(sleeping.online == 0 && sleeping.max == 12, "sleeping status counts");
            check("Paper 1.21.4".equals(sleeping.version), "sleeping status version");

            // Raw status to read the MOTD text.
            try (Socket s = new Socket("127.0.0.1", port)) {
                s.setSoTimeout(3000);
                OutputStream out = s.getOutputStream();
                out.write(McProtocol.buildHandshake(767, "localhost", port, 1));
                out.write(McProtocol.packet(0x00, new byte[0]));
                out.flush();
                McProtocol.readVarInt(s.getInputStream()); // length
                McProtocol.readVarInt(s.getInputStream()); // id
                String json = McProtocol.readString(s.getInputStream(), 65536);
                check(json.contains("Sleeping") && json.contains("My World"), "sleeping motd: " + json);
                // Ping/pong echo.
                out.write(McProtocol.packet(0x01, new byte[]{0, 0, 0, 0, 0, 0, 0, 42}));
                out.flush();
                McProtocol.Frame pong = McProtocol.readFrame(s.getInputStream());
                check(pong != null && pong.id == 0x01 && pong.payload[7] == 42, "pong echoes ping");
            }

            // A join attempt wakes the server and shows a message.
            try (Socket s = new Socket("127.0.0.1", port)) {
                s.setSoTimeout(3000);
                sendLogin(s, "Steve");
                String message = readDisconnect(s.getInputStream());
                check(message.contains("starting"), "wake kick message: " + message);
            }
            check("Steve".equals(woken.get()) && wakeCalls.get() == 1, "host told to wake for Steve");

            // While starting, joining again does not ask for another start.
            listener.setStarting();
            try (Socket s = new Socket("127.0.0.1", port)) {
                s.setSoTimeout(3000);
                sendLogin(s, "Alex");
                String message = readDisconnect(s.getInputStream());
                check(message.contains("still starting"), "starting kick message: " + message);
            }
            check(wakeCalls.get() == 1, "no second wake while starting");

            // Garbage must not take the listener down.
            try (Socket s = new Socket("127.0.0.1", port)) {
                s.getOutputStream().write(new byte[]{(byte) 0xFE, 0x01, (byte) 0xFA, 0x00, 0x0B});
                s.getOutputStream().write(new byte[4096]);
            }
            try (Socket s = new Socket("127.0.0.1", port)) {
                s.getOutputStream().write(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x07});
            }
            check(StatusPing.ping("127.0.0.1", port, 3000).online == 0, "still answering after garbage");

            // Relay mode pipes bytes to the real server.
            final ServerSocket echo = new ServerSocket(0);
            Thread echoThread = new Thread(() -> {
                try {
                    Socket c = echo.accept();
                    InputStream in = c.getInputStream();
                    OutputStream out = c.getOutputStream();
                    int b;
                    while ((b = in.read()) >= 0) { out.write(b); out.flush(); }
                } catch (Exception ignored) {}
            });
            echoThread.setDaemon(true);
            echoThread.start();
            listener.setRelay(echo.getLocalPort());
            try (Socket s = new Socket("127.0.0.1", port)) {
                s.setSoTimeout(3000);
                s.getOutputStream().write("hello relay".getBytes(StandardCharsets.UTF_8));
                s.getOutputStream().flush();
                byte[] got = new byte["hello relay".length()];
                int off = 0;
                while (off < got.length) { int n = s.getInputStream().read(got, off, got.length - off); check(n > 0, "relay closed early"); off += n; }
                check("hello relay".equals(new String(got, StandardCharsets.UTF_8)), "relay echo");
            }
            // Real server gone: relay target refuses, listener falls back to the sleeping answer.
            echo.close();
            listener.setRelay(freePort());
            check(StatusPing.ping("127.0.0.1", port, 3000).online == 0, "falls back when relay target is down");
        } finally {
            listener.stop();
        }
    }

    // A tiny stand-in for a bore relay, written from the protocol description.
    static void bore() throws Exception {
        final int controlPort = freePort();
        final int localPort = freePort();
        final ServerSocket relay = new ServerSocket(controlPort);
        final ServerSocket local = new ServerSocket(localPort);
        final CountDownLatch dataDone = new CountDownLatch(1);
        final AtomicReference<String> visitorGot = new AtomicReference<>();
        final AtomicInteger helloRequests = new AtomicInteger();
        final AtomicReference<Integer> lastRequested = new AtomicReference<>();
        final AtomicInteger assignedPort = new AtomicInteger(40123);

        // Local "minecraft": answers one line with "pong:<line>".
        Thread localThread = new Thread(() -> {
            try {
                Socket c = local.accept();
                java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(c.getInputStream()));
                String line = r.readLine();
                c.getOutputStream().write(("pong:" + line + "\n").getBytes(StandardCharsets.UTF_8));
                c.getOutputStream().flush();
                c.close();
            } catch (Exception ignored) {}
        });
        localThread.setDaemon(true);
        localThread.start();

        Thread relayThread = new Thread(() -> {
            try {
                Socket control = null;
                String pendingId = "123e4567-e89b-12d3-a456-426614174000";
                while (true) {
                    Socket c = relay.accept();
                    InputStream in = c.getInputStream();
                    String first = readNul(in);
                    if (first.startsWith("{\"Hello\":")) {
                        helloRequests.incrementAndGet();
                        int requested = Integer.parseInt(first.substring(9, first.length() - 1));
                        lastRequested.set(requested);
                        if (requested != 0) { // pretend the remembered port is taken
                            c.getOutputStream().write("{\"Error\":\"port already in use\"}\0".getBytes(StandardCharsets.UTF_8));
                            c.close();
                            continue;
                        }
                        control = c;
                        c.getOutputStream().write(("{\"Hello\":" + assignedPort.get() + "}\0").getBytes(StandardCharsets.UTF_8));
                        c.getOutputStream().write("\"Heartbeat\"\0".getBytes(StandardCharsets.UTF_8));
                        c.getOutputStream().write(("{\"Connection\":\"" + pendingId + "\"}\0").getBytes(StandardCharsets.UTF_8));
                        c.getOutputStream().flush();
                    } else if (first.startsWith("{\"Accept\":")) {
                        check(first.contains(pendingId), "accept carries the connection id");
                        c.getOutputStream().write("visitor-says-hi\n".getBytes(StandardCharsets.UTF_8));
                        c.getOutputStream().flush();
                        java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(in));
                        visitorGot.set(r.readLine());
                        dataDone.countDown();
                    }
                }
            } catch (Exception ignored) {}
        });
        relayThread.setDaemon(true);
        relayThread.start();

        final AtomicInteger publicPort = new AtomicInteger(-1);
        final CountDownLatch online = new CountDownLatch(1);
        BoreTunnel tunnel = new BoreTunnel("127.0.0.1", controlPort, localPort, 40999, new BoreTunnel.Listener() {
            @Override public void onState(String state) { if ("Online".equals(state)) online.countDown(); }
            @Override public void onPublicPort(int port) { publicPort.set(port); }
        });
        tunnel.start();
        try {
            check(online.await(8, TimeUnit.SECONDS), "tunnel came online");
            check(publicPort.get() == 40123, "assigned public port reported");
            check(helloRequests.get() == 2 && lastRequested.get() == 0, "retries with port 0 when the remembered port is taken");
            check(dataDone.await(8, TimeUnit.SECONDS), "visitor traffic reached the local service and back");
            check("pong:visitor-says-hi".equals(visitorGot.get()), "end to end through the tunnel: " + visitorGot.get());
        } finally {
            tunnel.stop();
            relay.close();
            local.close();
        }
    }

    static String readNul(InputStream in) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) > 0) b.write(c);
        return b.toString("UTF-8");
    }

    static void properties() throws Exception {
        File dir = Files.createTempDirectory("aerix-props").toFile();
        File file = new File(dir, "server.properties");
        Files.write(file.toPath(), ("#Minecraft server properties\n#Thu Jan 01\nmotd=A Minecraft Server\nonline-mode=true\nserver-port=25565\nunknown-key=keep me\n").getBytes(StandardCharsets.UTF_8));
        Map<String, String> v = new LinkedHashMap<>();
        v.put("online-mode", "false");
        v.put("motd", "Line one\nLine two");
        v.put("max-players", "8");
        ServerProperties.apply(file, v);
        String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        check(text.startsWith("#Minecraft server properties\n#Thu Jan 01\n"), "comments kept");
        check(text.contains("online-mode=false\n") && !text.contains("online-mode=true"), "existing key replaced");
        check(text.contains("motd=Line one\\nLine two\n"), "multi-line motd escaped");
        check(text.contains("server-port=25565\n") && text.contains("unknown-key=keep me\n"), "other keys untouched");
        check(text.endsWith("max-players=8\n"), "missing key appended");
        check("false".equals(ServerProperties.read(file, "online-mode")), "read back");
        File fresh = new File(dir, "fresh.properties");
        ServerProperties.apply(fresh, v);
        check(fresh.isFile() && ServerProperties.read(fresh, "max-players").equals("8"), "creates a missing file");
    }

    static void idle() {
        IdleTracker t = new IdleTracker();
        long limit = 5 * 60_000L;
        check(!t.shouldSleep(0, 0, limit), "not yet");
        check(!t.shouldSleep(0, 4 * 60_000L, limit), "still within limit");
        check(t.shouldSleep(0, 5 * 60_000L, limit), "sleeps after limit");
        check(!t.shouldSleep(2, 6 * 60_000L, limit), "players reset the clock");
        check(!t.shouldSleep(0, 7 * 60_000L, limit), "clock restarts after players leave");
        check(t.shouldSleep(0, 12 * 60_000L, limit), "sleeps again after another full limit");
    }

    static File makeJar(File file, String mainClass) throws Exception {
        java.util.jar.Manifest manifest = new java.util.jar.Manifest();
        manifest.getMainAttributes().put(java.util.jar.Attributes.Name.MANIFEST_VERSION, "1.0");
        if (mainClass != null) manifest.getMainAttributes().put(java.util.jar.Attributes.Name.MAIN_CLASS, mainClass);
        try (java.util.jar.JarOutputStream out = new java.util.jar.JarOutputStream(new java.io.FileOutputStream(file), manifest)) {
            out.putNextEntry(new java.util.zip.ZipEntry("x.txt"));
            out.write(1);
            out.closeEntry();
        }
        return file;
    }

    static void planner() throws Exception {
        File dir = Files.createTempDirectory("aerix-plan").toFile();

        // Vanilla / Paper style: server.jar with a Main-Class.
        makeJar(new File(dir, "server.jar"), "io.papermc.paperclip.Main");
        LaunchPlan paper = LaunchPlanner.forServer(dir, ServerSoftware.PAPER);
        check("io.papermc.paperclip.Main".equals(paper.mainClass), "paper main class");
        check(paper.classpath.size() == 1 && paper.classpath.get(0).endsWith("server.jar"), "paper classpath");
        check(paper.appArgs.contains("nogui"), "nogui");
        check(paper.jvmArgs.contains("-Dterminal.jline=false"), "plain stdin flags");

        // Missing install is a clear error, not a crash later.
        try {
            LaunchPlanner.forServer(dir, ServerSoftware.FABRIC);
            check(false, "missing fabric launch jar should throw");
        } catch (java.io.IOException expected) {
            check(expected.getMessage().contains("Fabric"), "message names the software");
        }
        makeJar(new File(dir, "fabric-server-launch.jar"), "net.fabricmc.loader.impl.launch.server.FabricServerLauncher");
        check("net.fabricmc.loader.impl.launch.server.FabricServerLauncher".equals(
                LaunchPlanner.forServer(dir, ServerSoftware.FABRIC).mainClass), "fabric main class");

        // Installer plan carries the installer args and no nogui.
        File installer = makeJar(new File(dir, "installer.jar"), "net.minecraftforge.installer.SimpleInstaller");
        LaunchPlan inst = LaunchPlanner.installerPlan(installer, java.util.Arrays.asList("--installServer"));
        check(inst.appArgs.size() == 1 && inst.appArgs.get(0).equals("--installServer"), "installer args");

        // Modern Forge: unix_args.txt.
        File forgeDir = Files.createTempDirectory("aerix-forge").toFile();
        File versionDir = new File(forgeDir, "libraries/net/minecraftforge/forge/1.20.1-47.2.0");
        versionDir.mkdirs();
        Files.write(new File(versionDir, "unix_args.txt").toPath(), (
                "-DlegacyClassPath=libraries/a.jar:libraries/b.jar\n"
              + "-DlibraryDirectory=libraries\n"
              + "-p libraries/cpw/mods/bootstraplauncher/1.1.2/bootstraplauncher-1.1.2.jar:libraries/cpw/mods/securejarhandler/2.1.4/securejarhandler-2.1.4.jar\n"
              + "--add-modules ALL-MODULE-PATH\n"
              + "--add-opens java.base/java.util.jar=cpw.mods.securejarhandler\n"
              + "cpw.mods.bootstraplauncher.BootstrapLauncher --launchTarget forgeserver --fml.forgeVersion 47.2.0 --fml.mcVersion 1.20.1\n"
        ).getBytes(StandardCharsets.UTF_8));
        LaunchPlan forge = LaunchPlanner.forServer(forgeDir, ServerSoftware.FORGE);
        check("cpw.mods.bootstraplauncher.BootstrapLauncher".equals(forge.mainClass), "forge main class: " + forge.mainClass);
        check(forge.appArgs.equals(java.util.Arrays.asList("--launchTarget", "forgeserver", "--fml.forgeVersion", "47.2.0", "--fml.mcVersion", "1.20.1", "nogui")), "forge app args: " + forge.appArgs);
        int p = forge.jvmArgs.indexOf("-p");
        check(p >= 0 && forge.jvmArgs.get(p + 1).startsWith(forgeDir.getAbsolutePath() + "/libraries/cpw/mods/bootstraplauncher"), "module path made absolute");
        check(forge.jvmArgs.get(p + 1).contains(":" + forgeDir.getAbsolutePath() + "/libraries/cpw/mods/securejarhandler"), "every module path entry absolute");
        check(forge.jvmArgs.contains("-DlibraryDirectory=" + forgeDir.getAbsolutePath() + "/libraries"), "library directory absolute");
        int am = forge.jvmArgs.indexOf("--add-modules");
        check(am >= 0 && forge.jvmArgs.get(am + 1).equals("ALL-MODULE-PATH"), "option values stay together");
        check(forge.jvmArgs.indexOf("cpw.mods.bootstraplauncher.BootstrapLauncher") < 0, "main class is not a JVM arg");

        // Old Forge (<= 1.16.5): a plain forge-*.jar.
        File oldDir = Files.createTempDirectory("aerix-oldforge").toFile();
        makeJar(new File(oldDir, "forge-1.12.2-14.23.5.2860.jar"), "net.minecraftforge.fml.relauncher.ServerLaunchWrapper");
        makeJar(new File(oldDir, "forge-1.12.2-14.23.5.2860-installer.jar"), "x.Installer");
        LaunchPlan old = LaunchPlanner.forServer(oldDir, ServerSoftware.FORGE);
        check(old.classpath.get(0).endsWith("forge-1.12.2-14.23.5.2860.jar") && "net.minecraftforge.fml.relauncher.ServerLaunchWrapper".equals(old.mainClass), "legacy forge jar, not the installer");

        // NeoForge looks under the neoforged folder.
        File neoDir = Files.createTempDirectory("aerix-neo").toFile();
        File neoVersion = new File(neoDir, "libraries/net/neoforged/neoforge/21.4.111");
        neoVersion.mkdirs();
        Files.write(new File(neoVersion, "unix_args.txt").toPath(), "-DlibraryDirectory=libraries\nnet.neoforged.bootstraplauncher.BootstrapLauncher --launchTarget neoforgeserver\n".getBytes(StandardCharsets.UTF_8));
        check("net.neoforged.bootstraplauncher.BootstrapLauncher".equals(LaunchPlanner.forServer(neoDir, ServerSoftware.NEOFORGE).mainClass), "neoforge args file");
    }
}
