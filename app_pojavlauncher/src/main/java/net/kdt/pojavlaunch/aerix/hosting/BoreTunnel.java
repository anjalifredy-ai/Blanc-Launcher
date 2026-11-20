package net.kdt.pojavlaunch.aerix.hosting;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Client for the open "bore" TCP tunnel protocol (https://github.com/ekzhang/bore). It publishes a local
 * TCP port on a public relay (default bore.pub) so friends can join without port forwarding. No account
 * and no native binary are needed: the protocol is a null-delimited JSON control channel on port 7835.
 *
 * <pre>
 * client -> {"Hello":PORT}\0        PORT = requested public port, 0 = any
 * server -> {"Hello":PORT}\0        the public port that was assigned
 * server -> "Heartbeat"\0           every ~500 ms
 * server -> {"Connection":"UUID"}\0 a visitor arrived
 *   client opens a NEW connection to :7835, sends {"Accept":"UUID"}\0, then both sides speak raw bytes;
 *   the client pipes that connection to the local port.
 * </pre>
 */
public final class BoreTunnel {
    public static final int CONTROL_PORT = 7835;
    public static final String DEFAULT_RELAY = "bore.pub";
    private static final int MAX_FRAME = 256;
    private static final Pattern HELLO = Pattern.compile("^\\{\"Hello\":(\\d+)\\}$");
    private static final Pattern CONNECTION = Pattern.compile("^\\{\"Connection\":\"([0-9a-fA-F-]{36})\"\\}$");
    private static final Pattern ERROR = Pattern.compile("^\\{\"Error\":\"(.*)\"\\}$");

    public interface Listener {
        /** Human-readable state, e.g. "Connecting", "Online", "Reconnecting". */
        void onState(String state);
        /** The public port the relay gave us. */
        void onPublicPort(int port);
    }

    private final String relayHost;
    private final int controlPort;
    private final int localPort;
    private final Listener listener;
    private volatile int preferredPort;
    private volatile boolean running;
    private volatile Socket control;
    private Thread thread;

    public BoreTunnel(String relayHost, int localPort, int preferredPort, Listener listener) {
        this(relayHost, CONTROL_PORT, localPort, preferredPort, listener);
    }

    /** [controlPort] is only different from 7835 when testing against a local fake relay. */
    public BoreTunnel(String relayHost, int controlPort, int localPort, int preferredPort, Listener listener) {
        this.relayHost = relayHost;
        this.controlPort = controlPort;
        this.localPort = localPort;
        this.preferredPort = preferredPort;
        this.listener = listener;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        thread = new Thread(this::loop, "aerix-bore");
        thread.setDaemon(true);
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        closeQuietly(control);
        if (thread != null) thread.interrupt();
    }

    private void loop() {
        long backoff = 2000;
        while (running) {
            boolean everOnline = false;
            try {
                listener.onState("Connecting to " + relayHost);
                everOnline = runSession();
            } catch (IOException e) {
                if (!running) break;
                listener.onState("Tunnel error: " + String.valueOf(e.getMessage()));
            }
            if (!running) break;
            backoff = everOnline ? 2000 : Math.min(backoff * 2, 30000);
            listener.onState("Reconnecting in " + (backoff / 1000) + "s");
            try { Thread.sleep(backoff); } catch (InterruptedException e) { break; }
        }
        listener.onState("Stopped");
    }

    /** One control session. Returns true if the tunnel reached the online state. */
    private boolean runSession() throws IOException {
        Socket socket = connect();
        control = socket;
        try {
            socket.setSoTimeout(15000);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            int want = preferredPort;
            int assigned = hello(in, out, want);
            if (assigned < 0 && want != 0) {
                // The remembered public port is taken. Ask for any port instead.
                closeQuietly(socket);
                socket = connect();
                control = socket;
                socket.setSoTimeout(15000);
                out = socket.getOutputStream();
                in = socket.getInputStream();
                assigned = hello(in, out, 0);
            }
            if (assigned < 0) throw new IOException("relay refused the tunnel");
            preferredPort = assigned;
            listener.onPublicPort(assigned);
            listener.onState("Online");
            while (running) {
                String message = readFrame(in);
                if (message == null) throw new IOException("relay closed the control connection");
                Matcher connection = CONNECTION.matcher(message);
                if (connection.matches()) {
                    spawnProxy(connection.group(1));
                } else if (message.startsWith("{\"Error\"")) {
                    Matcher error = ERROR.matcher(message);
                    throw new IOException(error.matches() ? error.group(1) : "relay error");
                }
                // "Heartbeat" and anything unknown: nothing to do.
            }
            return true;
        } finally {
            closeQuietly(socket);
        }
    }

    /** Sends Hello and returns the assigned port, or -1 if the relay answered with an error. */
    private int hello(InputStream in, OutputStream out, int port) throws IOException {
        writeFrame(out, "{\"Hello\":" + port + "}");
        String reply = readFrame(in);
        if (reply == null) throw new IOException("relay closed before answering");
        Matcher hello = HELLO.matcher(reply);
        if (hello.matches()) return Integer.parseInt(hello.group(1));
        if (reply.startsWith("{\"Error\"")) return -1;
        if (reply.startsWith("{\"Challenge\"")) throw new IOException("this relay needs a secret");
        throw new IOException("unexpected reply from relay");
    }

    private void spawnProxy(final String uuid) {
        Thread proxy = new Thread(() -> {
            Socket remote = null;
            Socket local = null;
            try {
                remote = connect();
                writeFrame(remote.getOutputStream(), "{\"Accept\":\"" + uuid + "\"}");
                local = new Socket();
                local.connect(new InetSocketAddress("127.0.0.1", localPort), 3000);
                pipe(remote, local);
            } catch (IOException ignored) {
                closeQuietly(remote);
                closeQuietly(local);
            }
        }, "aerix-bore-proxy");
        proxy.setDaemon(true);
        proxy.start();
    }

    private Socket connect() throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(relayHost, controlPort), 5000);
        socket.setTcpNoDelay(true);
        return socket;
    }

    static void writeFrame(OutputStream out, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        byte[] framed = new byte[bytes.length + 1];
        System.arraycopy(bytes, 0, framed, 0, bytes.length);
        framed[bytes.length] = 0;
        out.write(framed);
        out.flush();
    }

    /** Reads one null-terminated frame; null on end of stream. Strips the quotes of a bare string frame. */
    static String readFrame(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        while (true) {
            int b = in.read();
            if (b < 0) return buffer.size() == 0 ? null : buffer.toString("UTF-8");
            if (b == 0) {
                String text = buffer.toString("UTF-8");
                if (text.length() >= 2 && text.charAt(0) == '"' && text.charAt(text.length() - 1) == '"') {
                    return text.substring(1, text.length() - 1);
                }
                return text;
            }
            buffer.write(b);
            if (buffer.size() > MAX_FRAME) throw new IOException("control frame too long");
        }
    }

    private static void pipe(final Socket a, final Socket b) {
        Thread up = new Thread(() -> copy(a, b), "aerix-bore-up");
        up.setDaemon(true);
        up.start();
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
