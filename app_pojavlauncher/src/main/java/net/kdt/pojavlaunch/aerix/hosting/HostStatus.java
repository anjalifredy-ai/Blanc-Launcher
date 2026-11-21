package net.kdt.pojavlaunch.aerix.hosting;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** What the host process tells the UI. Written to a file so the two processes need no IPC. */
public final class HostStatus {
    public static final String OFF = "OFF";
    public static final String SLEEPING = "SLEEPING";
    public static final String STARTING = "STARTING";
    public static final String RUNNING = "RUNNING";
    public static final String STOPPING = "STOPPING";
    public static final String INSTALLING = "INSTALLING";
    public static final String ERROR = "ERROR";

    /** The UI treats a status older than this as "host process is gone". */
    public static final long STALE_MS = 12_000;

    public String serverId = "";
    public String state = OFF;
    public String message = "";
    public int players = -1;
    public int maxPlayers = 0;
    public String tunnel = "";
    /** host:port friends can use, or empty. */
    public String publicAddress = "";
    public long updatedAt = 0;

    public boolean isFresh(long now) {
        return now - updatedAt < STALE_MS;
    }

    public void write(File file) {
        updatedAt = System.currentTimeMillis();
        JsonObject o = new JsonObject();
        o.addProperty("serverId", serverId);
        o.addProperty("state", state);
        o.addProperty("message", message);
        o.addProperty("players", players);
        o.addProperty("maxPlayers", maxPlayers);
        o.addProperty("tunnel", tunnel);
        o.addProperty("publicAddress", publicAddress);
        o.addProperty("updatedAt", updatedAt);
        try {
            File tmp = new File(file.getPath() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(o.toString().getBytes(StandardCharsets.UTF_8));
            }
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // The next update tries again.
        }
    }

    public static HostStatus read(File file) {
        HostStatus s = new HostStatus();
        try {
            JsonObject o = new JsonParser().parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
            s.serverId = str(o, "serverId");
            s.state = str(o, "state");
            s.message = str(o, "message");
            s.players = o.has("players") ? o.get("players").getAsInt() : -1;
            s.maxPlayers = o.has("maxPlayers") ? o.get("maxPlayers").getAsInt() : 0;
            s.tunnel = str(o, "tunnel");
            s.publicAddress = str(o, "publicAddress");
            s.updatedAt = o.has("updatedAt") ? o.get("updatedAt").getAsLong() : 0;
        } catch (IOException | RuntimeException e) {
            return new HostStatus();
        }
        if (s.state.isEmpty()) s.state = OFF;
        return s;
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }
}
