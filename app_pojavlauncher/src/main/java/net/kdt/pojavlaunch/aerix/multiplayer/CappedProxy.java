package net.kdt.pojavlaunch.aerix.multiplayer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A loopback TCP forwarder that refuses new connections once [max] are open. The host puts it between the
 * tunnel and the LAN world (max 10 players); a joiner puts it between Minecraft and the relay.
 */
public final class CappedProxy {
    private final String targetHost;
    private final int targetPort;
    private final int max;
    private final AtomicInteger open = new AtomicInteger();
    private volatile boolean running;
    private volatile String lastError;

    /** The most recent failure while forwarding a connection, or null. */
    public String lastError() { return lastError; }
    private ServerSocket server;

    public CappedProxy(String targetHost, int targetPort, int max) {
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.max = max;
    }

    /** Listens on [listenPort] (0 = any free port) and returns the port in use. */
    public synchronized int start(int listenPort) throws IOException {
        server = new ServerSocket(listenPort, 50, InetAddress.getByName("127.0.0.1"));
        running = true;
        Thread t = new Thread(this::acceptLoop, "blanc-proxy-accept");
        t.setDaemon(true);
        t.start();
        return server.getLocalPort();
    }

    public synchronized void stop() {
        running = false;
        try { if (server != null) server.close(); } catch (IOException ignored) {}
    }

    public int connections() { return open.get(); }

    private void acceptLoop() {
        while (running) {
            final Socket client;
            try { client = server.accept(); } catch (IOException e) { return; }
            if (open.get() >= max) { closeQuietly(client); continue; }
            open.incrementAndGet();
            Thread t = new Thread(() -> handle(client), "blanc-proxy-conn");
            t.setDaemon(true);
            t.start();
        }
    }

    private void handle(Socket client) {
        Socket upstream = new Socket();
        try {
            upstream.connect(new InetSocketAddress(targetHost, targetPort), 8000);
            client.setTcpNoDelay(true);
            upstream.setTcpNoDelay(true);
            final Socket up = upstream;
            Thread back = new Thread(() -> pump(up, client), "blanc-proxy-back");
            back.setDaemon(true);
            back.start();
            pump(client, upstream);
        } catch (IOException e) {
            lastError = e.getClass().getSimpleName() + ": " + e.getMessage() + " (" + targetHost + ":" + targetPort + ")";
        } finally {
            closeQuietly(client);
            closeQuietly(upstream);
            open.decrementAndGet();
        }
    }

    private static void pump(Socket from, Socket to) {
        byte[] buf = new byte[16 * 1024];
        try {
            InputStream in = from.getInputStream();
            OutputStream out = to.getOutputStream();
            int n;
            while ((n = in.read(buf)) >= 0) { out.write(buf, 0, n); out.flush(); }
        } catch (IOException ignored) {
        } finally {
            closeQuietly(from);
            closeQuietly(to);
        }
    }

    private static void closeQuietly(Socket s) {
        try { s.close(); } catch (IOException ignored) {}
    }
}
