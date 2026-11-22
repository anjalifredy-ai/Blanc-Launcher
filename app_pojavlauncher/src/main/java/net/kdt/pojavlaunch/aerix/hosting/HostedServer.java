package net.kdt.pojavlaunch.aerix.hosting;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.LinkedHashMap;
import java.util.Map;

/** One hosted server's settings. Stored as server.json (Gson tree API, no reflection). */
public final class HostedServer {
    public String id;
    public String name = "My Server";
    public String description = "A Minecraft server";
    public ServerSoftware software = ServerSoftware.PAPER;
    public String mcVersion = "";
    public boolean onlineMode = true;
    public int maxPlayers = 10;
    public int ramMb = 2048;
    /** Port players (or the tunnel) connect to. Held by the always-on listener. */
    public int frontPort = 25565;
    /** Loopback port the real server binds to while awake. */
    public int privatePort = 35565;
    public String gamemode = "survival";
    public String difficulty = "normal";
    public boolean pvp = true;
    public boolean whitelist = false;
    public boolean allowFlight = false;
    public boolean commandBlocks = false;
    public int viewDistance = 6;
    public String seed = "";
    public boolean autoSleep = true;
    public int idleMinutes = 5;
    public boolean tunnelEnabled = true;
    public String relayHost = "bore.pub";
    /** Last public port the relay gave us; asked for again so the address stays the same. */
    public int publicPort = 0;
    /** Lighter Paper/Spigot/Bukkit config values for a phone (see PerformancePreset). */
    public boolean performancePreset = true;
    public boolean eulaAccepted = false;
    /** Set once the software has been downloaded and (if needed) installed. */
    public boolean installed = false;
    public long createdAt = System.currentTimeMillis();

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("name", name);
        o.addProperty("description", description);
        o.addProperty("software", software.name());
        o.addProperty("mcVersion", mcVersion);
        o.addProperty("onlineMode", onlineMode);
        o.addProperty("maxPlayers", maxPlayers);
        o.addProperty("ramMb", ramMb);
        o.addProperty("frontPort", frontPort);
        o.addProperty("privatePort", privatePort);
        o.addProperty("gamemode", gamemode);
        o.addProperty("difficulty", difficulty);
        o.addProperty("pvp", pvp);
        o.addProperty("whitelist", whitelist);
        o.addProperty("allowFlight", allowFlight);
        o.addProperty("commandBlocks", commandBlocks);
        o.addProperty("viewDistance", viewDistance);
        o.addProperty("seed", seed);
        o.addProperty("autoSleep", autoSleep);
        o.addProperty("idleMinutes", idleMinutes);
        o.addProperty("tunnelEnabled", tunnelEnabled);
        o.addProperty("relayHost", relayHost);
        o.addProperty("publicPort", publicPort);
        o.addProperty("performancePreset", performancePreset);
        o.addProperty("eulaAccepted", eulaAccepted);
        o.addProperty("installed", installed);
        o.addProperty("createdAt", createdAt);
        return o;
    }

    public static HostedServer fromJson(String json) {
        JsonObject o = new JsonParser().parse(json).getAsJsonObject();
        HostedServer s = new HostedServer();
        s.id = str(o, "id", null);
        s.name = str(o, "name", s.name);
        s.description = str(o, "description", s.description);
        s.software = ServerSoftware.fromName(str(o, "software", "PAPER"));
        s.mcVersion = str(o, "mcVersion", "");
        s.onlineMode = bool(o, "onlineMode", s.onlineMode);
        s.maxPlayers = num(o, "maxPlayers", s.maxPlayers);
        s.ramMb = num(o, "ramMb", s.ramMb);
        s.frontPort = num(o, "frontPort", s.frontPort);
        s.privatePort = num(o, "privatePort", s.privatePort);
        s.gamemode = str(o, "gamemode", s.gamemode);
        s.difficulty = str(o, "difficulty", s.difficulty);
        s.pvp = bool(o, "pvp", s.pvp);
        s.whitelist = bool(o, "whitelist", s.whitelist);
        s.allowFlight = bool(o, "allowFlight", s.allowFlight);
        s.commandBlocks = bool(o, "commandBlocks", s.commandBlocks);
        s.viewDistance = num(o, "viewDistance", s.viewDistance);
        s.seed = str(o, "seed", s.seed);
        s.autoSleep = bool(o, "autoSleep", s.autoSleep);
        s.idleMinutes = num(o, "idleMinutes", s.idleMinutes);
        s.tunnelEnabled = bool(o, "tunnelEnabled", s.tunnelEnabled);
        s.relayHost = str(o, "relayHost", s.relayHost);
        s.publicPort = num(o, "publicPort", s.publicPort);
        s.performancePreset = bool(o, "performancePreset", s.performancePreset);
        s.eulaAccepted = bool(o, "eulaAccepted", s.eulaAccepted);
        s.installed = bool(o, "installed", s.installed);
        s.createdAt = (long) num(o, "createdAt", 0);
        if (o.has("createdAt")) s.createdAt = o.get("createdAt").getAsLong();
        return s;
    }

    /** The server.properties values this server's settings control. */
    public Map<String, String> properties() {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("server-ip", "127.0.0.1");
        p.put("server-port", String.valueOf(privatePort));
        p.put("online-mode", String.valueOf(onlineMode));
        p.put("motd", description.replace('\n', ' '));
        p.put("max-players", String.valueOf(maxPlayers));
        p.put("gamemode", gamemode);
        p.put("difficulty", difficulty);
        p.put("pvp", String.valueOf(pvp));
        p.put("white-list", String.valueOf(whitelist));
        p.put("enforce-whitelist", String.valueOf(whitelist));
        p.put("allow-flight", String.valueOf(allowFlight));
        p.put("enable-command-block", String.valueOf(commandBlocks));
        p.put("view-distance", String.valueOf(viewDistance));
        p.put("simulation-distance", String.valueOf(Math.min(viewDistance, 5)));
        // Phone-friendly: no blocking disk writes, no watchdog kills on a slow tick, no native epoll lookup,
        // and fewer entities sent to each player.
        p.put("sync-chunk-writes", "false");
        p.put("max-tick-time", "-1");
        p.put("use-native-transport", "false");
        p.put("entity-broadcast-range-percentage", "75");
        p.put("enable-query", "false");
        p.put("enable-rcon", "false");
        p.put("network-compression-threshold", "256");
        if (!seed.isEmpty()) p.put("level-seed", seed);
        return p;
    }

    private static String str(JsonObject o, String k, String def) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : def;
    }

    private static boolean bool(JsonObject o, String k, boolean def) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsBoolean() : def;
    }

    private static int num(JsonObject o, String k, int def) {
        try {
            return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsInt() : def;
        } catch (RuntimeException e) {
            return def;
        }
    }
}
