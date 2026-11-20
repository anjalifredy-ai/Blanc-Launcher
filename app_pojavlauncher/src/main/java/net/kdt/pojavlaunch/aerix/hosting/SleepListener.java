package net.kdt.pojavlaunch.aerix.hosting;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Owns the public Minecraft port while hosting is on. It is what makes "sleep when empty, wake on join"
 * possible:
 * <ul>
 *   <li>SLEEPING: answers the server list with a "sleeping" message and, when somebody tries to join,
 *       asks the host to start the real server and tells the player to retry shortly.</li>
 *   <li>STARTING: same, but says the server is already starting.</li>
 *   <li>RELAY: pipes every connection to the real server running on a private local port.</li>
 * </ul>
 * Plain Java (no Android classes) so the protocol handling can be tested on a desktop JVM.
 */
public final class SleepListener {
    public enum Mode { SLEEPING, STARTING, RELAY }

    /** Host-side hooks. Called on connection threads, never on the main thread. */
    public interface Host {
        /** A player tried to join while the server was not running. Return true if a start was begun. */
        boolean onWakeRequested(String playerName);
    }

    /** Text and numbers shown in the server list while the server is not running. */
    public static final class Info {
        public volatile String versionName = "Aerix";
        public volatile int maxPlayers = 20;
        public volatile String motd = "Server is sleeping";
        public volatile String favicon = null;
        public volatile String sleepingKick = "Server is starting.\nJoin again in about a minute.";
        public volatile String startingKick = "Server is still starting.\nJoin again in a few seconds.";
    }

    private static final int MAX_CONNECTIONS = 64;

    private final int port;
    private final Host host;
    public final Info info = new Info();

    private volatile Mode mode = Mode.SLEEPING;
    private volatile int relayPort = -1;
    private volatile boolean running;
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private ExecutorService pool;
    private final AtomicInteger open = new AtomicInteger();

    public SleepListener(int port, Host host) {
        this.port = port;
        this.host = host;
    }

    public Mode mode() { return mode; }

    public void setSleeping() { mode = Mode.SLEEPING; relayPort = -1; }

    public void setStarting() { mode = Mode.STARTING; relayPort = -1; }

    public void setRelay(int targetPort) { relayPort = targetPort; mode = Mode.RELAY; }

    public synchronized void start() throws IOException {
        if (running) return;
        ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(port), 50);
        serverSocket = socket;
        running = true;
        pool = Executors.newCachedThreadPool(new ThreadFactory() {
            private final AtomicInteger n = new AtomicInteger();
            @Override public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "aerix-sleep-conn-" + n.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        });
        acceptThread = new Thread(this::acceptLoop, "aerix-sleep-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public synchronized void stop() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        if (pool != null) pool.shutdownNow();
        serverSocket = null;
    }

    public int port() { return port; }

    private void acceptLoop() {
        while (running) {
            final Socket client;
            try {
                client = serverSocket.accept();
            } catch (IOException e) {
                if (!running) return;
                continue;
            }
            if (open.get() >= MAX_CONNECTIONS) {
                closeQuietly(client);
                continue;
            }
            open.incrementAndGet();
            try {
                pool.execute(() -> {
                    try { handle(client); }
                    catch (Throwable ignored) { /* a bad client must never take the listener down */ }
                    finally { open.decrementAndGet(); closeQuietly(client); }
                });
            } catch (RuntimeException rejected) {
                open.decrementAndGet();
                closeQuietly(client);
            }
        }
    }

    private void handle(Socket client) throws IOException {
        client.setTcpNoDelay(true);
        if (mode == Mode.RELAY) {
            int target = relayPort;
            Socket upstream = null;
            try {
                upstream = new Socket();
                upstream.connect(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), target), 2000);
            } catch (IOException notReadyYet) {
                closeQuietly(upstream);
                upstream = null;
            }
            if (upstream != null) {
                pipe(client, upstream);
                return;
            }
            // The real server stopped answering: fall through and behave as a sleeping server.
        }
        client.setSoTimeout(8000);
        InputStream in = client.getInputStream();
        OutputStream out = client.getOutputStream();
        McProtocol.Frame first = McProtocol.readFrame(in);
        if (first == null) return;
        McProtocol.Handshake handshake = McProtocol.parseHandshake(first);
        if (handshake.nextState == 1) {
            answerStatus(in, out, handshake.protocol);
        } else if (handshake.nextState == 2 || handshake.nextState == 3) {
            answerLogin(in, out);
        }
    }

    private void answerStatus(InputStream in, OutputStream out, int clientProtocol) throws IOException {
        McProtocol.Frame request = McProtocol.readFrame(in);
        if (request == null || request.id != 0x00) return;
        boolean starting = mode == Mode.STARTING;
        String motd = starting ? "§eStarting§r - " + info.motd : "§7Sleeping§r - join to wake it up\n" + info.motd;
        String json = McProtocol.statusJson(info.versionName, clientProtocol, 0, info.maxPlayers, motd, info.favicon);
        out.write(McProtocol.statusResponse(json));
        out.flush();
        // Ping: echo the 8-byte payload back as the pong.
        McProtocol.Frame ping = McProtocol.readFrame(in);
        if (ping != null && ping.id == 0x01) {
            out.write(McProtocol.packet(0x01, ping.payload));
            out.flush();
        }
    }

    private void answerLogin(InputStream in, OutputStream out) throws IOException {
        McProtocol.Frame loginStart = McProtocol.readFrame(in);
        String name = "player";
        if (loginStart != null && loginStart.id == 0x00) {
            try {
                name = McProtocol.readString(new java.io.ByteArrayInputStream(loginStart.payload), 64);
            } catch (IOException ignored) {}
        }
        String kick;
        if (mode == Mode.STARTING) {
            kick = info.startingKick;
        } else {
            boolean began = false;
            try { began = host.onWakeRequested(name); } catch (RuntimeException ignored) {}
            kick = began ? info.sleepingKick : info.startingKick;
        }
        out.write(McProtocol.loginDisconnect(kick));
        out.flush();
    }

    private static void pipe(final Socket a, final Socket b) {
        Thread forward = new Thread(() -> copy(a, b), "aerix-relay-up");
        forward.setDaemon(true);
        forward.start();
        copy(b, a);
        closeQuietly(a);
        closeQuietly(b);
    }

    private static void copy(Socket from, Socket to) {
        byte[] buffer = new byte[16 * 1024];
        try {
            InputStream in = from.getInputStream();
            OutputStream out = to.getOutputStream();
            int n;
            while ((n = in.read(buffer)) >= 0) {
                out.write(buffer, 0, n);
                out.flush();
            }
        } catch (SocketTimeoutException ignored) {
        } catch (IOException ignored) {
        } finally {
            try { to.shutdownOutput(); } catch (IOException ignored) {}
        }
    }

    private static void closeQuietly(Socket s) {
        if (s == null) return;
        try { s.close(); } catch (IOException ignored) {}
    }
}
