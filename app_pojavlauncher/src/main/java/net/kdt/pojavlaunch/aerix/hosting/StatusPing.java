package net.kdt.pojavlaunch.aerix.hosting;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minimal Server List Ping, used to ask the local server how many players are on it. */
public final class StatusPing {
    private StatusPing() {}

    public static final class Result {
        public final int online;
        public final int max;
        public final String version;

        Result(int online, int max, String version) {
            this.online = online;
            this.max = max;
            this.version = version;
        }
    }

    private static final Pattern ONLINE = Pattern.compile("\"online\"\\s*:\\s*(\\d+)");
    private static final Pattern MAX = Pattern.compile("\"max\"\\s*:\\s*(\\d+)");
    private static final Pattern VERSION = Pattern.compile("\"version\"\\s*:\\s*\\{[^}]*?\"name\"\\s*:\\s*\"([^\"]*)\"");

    /** Pings [host]:[port]. Throws on any network or protocol failure. */
    public static Result ping(String host, int port, int timeoutMs) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write(McProtocol.buildHandshake(-1, host, port, 1));
            out.write(McProtocol.packet(0x00, new byte[0]));
            out.flush();
            String json = readStatusJson(in);
            return parse(json);
        }
    }

    /** The status packet can carry a ~10 KB favicon, so it is read here with a roomier limit. */
    private static String readStatusJson(InputStream in) throws IOException {
        int length = McProtocol.readVarInt(in);
        if (length <= 0 || length > 262144) throw new IOException("bad status length " + length);
        byte[] data = new byte[length];
        int off = 0;
        while (off < length) {
            int n = in.read(data, off, length - off);
            if (n < 0) throw new IOException("status response cut short");
            off += n;
        }
        ByteArrayInputStream body = new ByteArrayInputStream(data);
        int id = McProtocol.readVarInt(body);
        if (id != 0x00) throw new IOException("unexpected status packet " + id);
        return McProtocol.readString(body, 262144);
    }

    static Result parse(String json) throws IOException {
        int playersAt = json.indexOf("\"players\"");
        if (playersAt < 0) throw new IOException("status has no players block");
        String tail = json.substring(playersAt);
        Matcher online = ONLINE.matcher(tail);
        if (!online.find()) throw new IOException("status has no online count");
        Matcher max = MAX.matcher(tail);
        Matcher version = VERSION.matcher(json);
        return new Result(Integer.parseInt(online.group(1)),
                max.find() ? Integer.parseInt(max.group(1)) : 0,
                version.find() ? version.group(1) : null);
    }
}
