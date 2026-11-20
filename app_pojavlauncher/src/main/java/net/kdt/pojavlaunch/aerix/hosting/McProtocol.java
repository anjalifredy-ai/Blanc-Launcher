package net.kdt.pojavlaunch.aerix.hosting;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * The few pieces of the Minecraft Java Edition network protocol that Aerix hosting needs while the real
 * server is asleep: reading the handshake, answering a server-list ping, and refusing a login with a
 * readable message. Pure Java so it can be unit-tested on a desktop JVM.
 */
public final class McProtocol {
    private McProtocol() {}

    /** Largest pre-login packet we accept; real handshake/status/login packets are far smaller. */
    public static final int MAX_PACKET = 4096;

    public static int readVarInt(InputStream in) throws IOException {
        int value = 0;
        int position = 0;
        while (true) {
            int b = in.read();
            if (b < 0) throw new EOFException("stream closed inside a VarInt");
            value |= (b & 0x7F) << position;
            if ((b & 0x80) == 0) return value;
            position += 7;
            if (position >= 35) throw new IOException("VarInt is too big");
        }
    }

    public static void writeVarInt(OutputStream out, int value) throws IOException {
        while (true) {
            if ((value & ~0x7F) == 0) {
                out.write(value);
                return;
            }
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }

    public static void writeString(OutputStream out, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    /** Reads a length-prefixed UTF-8 string out of an already-framed packet body. */
    public static String readString(InputStream in, int maxBytes) throws IOException {
        int length = readVarInt(in);
        if (length < 0 || length > maxBytes) throw new IOException("string length out of range: " + length);
        byte[] bytes = new byte[length];
        new DataInputStream(in).readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** A framed packet: [length][id][payload]. */
    public static byte[] packet(int id, byte[] payload) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeVarInt(body, id);
        body.write(payload);
        ByteArrayOutputStream framed = new ByteArrayOutputStream();
        writeVarInt(framed, body.size());
        framed.write(body.toByteArray());
        return framed.toByteArray();
    }

    /** What a frame looked like once read: its packet id and the bytes after the id. */
    public static final class Frame {
        public final int id;
        public final byte[] payload;

        Frame(int id, byte[] payload) {
            this.id = id;
            this.payload = payload;
        }
    }

    /** Reads one frame. Returns null on a clean end of stream before the first byte. */
    public static Frame readFrame(InputStream in) throws IOException {
        int first = in.read();
        if (first < 0) return null;
        int length = first & 0x7F;
        if ((first & 0x80) != 0) {
            int rest = readVarInt(in);
            length |= (rest << 7);
        }
        if (length <= 0 || length > MAX_PACKET) throw new IOException("bad packet length: " + length);
        byte[] data = new byte[length];
        new DataInputStream(in).readFully(data);
        java.io.ByteArrayInputStream body = new java.io.ByteArrayInputStream(data);
        int id = readVarInt(body);
        byte[] payload = new byte[body.available()];
        int read = body.read(payload, 0, payload.length);
        if (read < payload.length && payload.length > 0) throw new EOFException("short packet");
        return new Frame(id, payload);
    }

    /** The handshake that opens every connection. */
    public static final class Handshake {
        public final int protocol;
        public final String host;
        public final int port;
        /** 1 = status (server list), 2 = login, 3 = transfer (1.20.5+). */
        public final int nextState;

        Handshake(int protocol, String host, int port, int nextState) {
            this.protocol = protocol;
            this.host = host;
            this.port = port;
            this.nextState = nextState;
        }
    }

    public static Handshake parseHandshake(Frame frame) throws IOException {
        if (frame.id != 0x00) throw new IOException("expected handshake, got packet " + frame.id);
        java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream(frame.payload);
        int protocol = readVarInt(in);
        String host = readString(in, 512);
        int hi = in.read();
        int lo = in.read();
        if (hi < 0 || lo < 0) throw new EOFException("handshake has no port");
        int nextState = readVarInt(in);
        return new Handshake(protocol, host, (hi << 8) | lo, nextState);
    }

    /** Builds the handshake a client would send (used by the status pinger and by tests). */
    public static byte[] buildHandshake(int protocol, String host, int port, int nextState) throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeVarInt(payload, protocol);
        writeString(payload, host);
        payload.write((port >> 8) & 0xFF);
        payload.write(port & 0xFF);
        writeVarInt(payload, nextState);
        return packet(0x00, payload.toByteArray());
    }

    /** Status response packet (id 0x00) carrying the JSON shown in the server list. */
    public static byte[] statusResponse(String json) throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeString(payload, json);
        return packet(0x00, payload.toByteArray());
    }

    /** Login Disconnect (id 0x00 in the login state): shows [message] on the client's screen. */
    public static byte[] loginDisconnect(String message) throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeString(payload, "{\"text\":" + jsonString(message) + "}");
        return packet(0x00, payload.toByteArray());
    }

    /** Server-list JSON. [protocol] echoes the client's so it is shown as compatible rather than red. */
    public static String statusJson(String versionName, int protocol, int online, int max, String motd, String faviconDataUri) {
        StringBuilder json = new StringBuilder(256);
        json.append("{\"version\":{\"name\":").append(jsonString(versionName))
                .append(",\"protocol\":").append(protocol)
                .append("},\"players\":{\"max\":").append(max)
                .append(",\"online\":").append(online)
                .append(",\"sample\":[]},\"description\":{\"text\":").append(jsonString(motd)).append('}');
        if (faviconDataUri != null && faviconDataUri.startsWith("data:image/png;base64,")) {
            json.append(",\"favicon\":").append(jsonString(faviconDataUri));
        }
        return json.append('}').toString();
    }

    public static String jsonString(String s) {
        StringBuilder out = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
            }
        }
        return out.append('"').toString();
    }
}
