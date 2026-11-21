package net.kdt.pojavlaunch.aerix.hosting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Knows where each server software is downloaded from. The network entry points are [versions] and
 * [artifact]; the parse* methods are pure so they can be tested without a network.
 */
public final class ServerCatalog {
    private ServerCatalog() {}

    /** What has to be downloaded to set a server up. */
    public static final class Artifact {
        public final String url;
        public final String fileName;
        public final String sha1;
        public final String sha256;
        /** Extra program arguments, for installers (e.g. --installServer). */
        public final List<String> installerArgs;

        Artifact(String url, String fileName, String sha1, String sha256, List<String> installerArgs) {
            this.url = url;
            this.fileName = fileName;
            this.sha1 = sha1;
            this.sha256 = sha256;
            this.installerArgs = installerArgs;
        }
    }

    private static final String MOJANG_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String PAPER_API = "https://fill.papermc.io/v3/projects/paper";
    private static final String PURPUR_API = "https://api.purpurmc.org/v2/purpur";
    private static final String FABRIC_META = "https://meta.fabricmc.net/v2/versions";
    private static final String QUILT_META = "https://meta.quiltmc.org/v3/versions";
    private static final String FORGE_PROMOS = "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json";
    private static final String FORGE_MAVEN = "https://maven.minecraftforge.net/net/minecraftforge/forge/";
    private static final String NEOFORGE_MAVEN = "https://maven.neoforged.net/releases/net/neoforged/neoforge/";

    /** Oldest Minecraft version offered, per software. Older servers need legacy launch handling. */
    private static final String OLDEST = "1.12.2";

    // ---------------------------------------------------------------- versions

    public static List<String> versions(ServerSoftware software) throws IOException {
        List<String> all;
        switch (software) {
            case VANILLA: all = parseMojangVersions(Http.get(MOJANG_MANIFEST)); break;
            case PAPER: all = parsePaperVersions(Http.get(PAPER_API)); break;
            case PURPUR: all = parsePurpurVersions(Http.get(PURPUR_API)); break;
            case FABRIC: all = parseFabricGameVersions(Http.get(FABRIC_META + "/game")); break;
            case QUILT: all = parseFabricGameVersions(Http.get(QUILT_META + "/game")); break;
            case FORGE: all = new ArrayList<>(parseForgePromos(Http.get(FORGE_PROMOS)).keySet()); break;
            case NEOFORGE: all = new ArrayList<>(parseNeoForgeMetadata(Http.get(NEOFORGE_MAVEN + "maven-metadata.xml")).keySet()); break;
            default: throw new IOException("unsupported software");
        }
        List<String> filtered = new ArrayList<>();
        for (String v : all) if (VersionOrder.isRelease(v) && VersionOrder.compare(v, OLDEST) >= 0) filtered.add(v);
        Collections.sort(filtered, VersionOrder.NEWEST_FIRST);
        if (filtered.isEmpty()) throw new IOException("no " + software.displayName + " versions were found");
        return filtered;
    }

    // --------------------------------------------------------------- artifacts

    public static Artifact artifact(ServerSoftware software, String mc) throws IOException {
        switch (software) {
            case VANILLA: return vanilla(mc);
            case PAPER: return paper(mc);
            case PURPUR:
                return new Artifact(PURPUR_API + "/" + mc + "/latest/download", "server.jar", null, null, null);
            case FABRIC: return fabric(mc);
            case QUILT: return quilt(mc);
            case FORGE: return forge(mc);
            case NEOFORGE: return neoforge(mc);
            default: throw new IOException("unsupported software");
        }
    }

    private static Artifact vanilla(String mc) throws IOException {
        String url = parseMojangVersionUrl(Http.get(MOJANG_MANIFEST), mc);
        if (url == null) throw new IOException("Minecraft " + mc + " was not found");
        JsonObject server = new JsonParser().parse(Http.get(url)).getAsJsonObject()
                .getAsJsonObject("downloads").getAsJsonObject("server");
        if (server == null) throw new IOException("Minecraft " + mc + " has no server download");
        return new Artifact(server.get("url").getAsString(), "server.jar",
                server.has("sha1") ? server.get("sha1").getAsString() : null, null, null);
    }

    private static Artifact paper(String mc) throws IOException {
        return parsePaperBuild(Http.get(PAPER_API + "/versions/" + mc + "/builds/latest"));
    }

    private static Artifact fabric(String mc) throws IOException {
        String loader = firstStable(Http.get(FABRIC_META + "/loader"), "version");
        String installer = firstStable(Http.get(FABRIC_META + "/installer"), "version");
        String base = FABRIC_META + "/loader/" + mc + "/" + loader + "/" + installer + "/server/jar";
        return new Artifact(base, "fabric-server-launch.jar", null, null, null);
    }

    private static Artifact quilt(String mc) throws IOException {
        JsonArray installers = new JsonParser().parse(Http.get(QUILT_META + "/installer")).getAsJsonArray();
        if (installers.size() == 0) throw new IOException("Quilt installer list is empty");
        String url = installers.get(0).getAsJsonObject().get("url").getAsString();
        List<String> args = new ArrayList<>();
        args.add("install");
        args.add("server");
        args.add(mc);
        args.add("--download-server");
        return new Artifact(url, "installer.jar", null, null, args);
    }

    private static Artifact forge(String mc) throws IOException {
        Map<String, String> promos = parseForgePromos(Http.get(FORGE_PROMOS));
        String build = promos.get(mc);
        if (build == null) throw new IOException("Forge has no build for " + mc);
        String full = mc + "-" + build;
        return new Artifact(FORGE_MAVEN + full + "/forge-" + full + "-installer.jar", "installer.jar", null, null,
                java.util.Collections.singletonList("--installServer"));
    }

    private static Artifact neoforge(String mc) throws IOException {
        Map<String, String> builds = parseNeoForgeMetadata(Http.get(NEOFORGE_MAVEN + "maven-metadata.xml"));
        String build = builds.get(mc);
        if (build == null) throw new IOException("NeoForge has no build for " + mc);
        return new Artifact(NEOFORGE_MAVEN + build + "/neoforge-" + build + "-installer.jar", "installer.jar", null, null,
                java.util.Collections.singletonList("--installServer"));
    }

    // ------------------------------------------------------------------ parsing

    static List<String> parseMojangVersions(String json) {
        List<String> out = new ArrayList<>();
        for (JsonElement e : new JsonParser().parse(json).getAsJsonObject().getAsJsonArray("versions")) {
            JsonObject v = e.getAsJsonObject();
            if ("release".equals(v.get("type").getAsString())) out.add(v.get("id").getAsString());
        }
        return out;
    }

    static String parseMojangVersionUrl(String json, String id) {
        for (JsonElement e : new JsonParser().parse(json).getAsJsonObject().getAsJsonArray("versions")) {
            JsonObject v = e.getAsJsonObject();
            if (id.equals(v.get("id").getAsString())) return v.get("url").getAsString();
        }
        return null;
    }

    /** Fill v3 lists versions grouped by minor ("1.21": ["1.21.4", ...]); a flat array is accepted too. */
    static List<String> parsePaperVersions(String json) {
        JsonElement versions = new JsonParser().parse(json).getAsJsonObject().get("versions");
        Set<String> out = new LinkedHashSet<>();
        if (versions.isJsonObject()) {
            for (Map.Entry<String, JsonElement> group : versions.getAsJsonObject().entrySet()) {
                for (JsonElement v : group.getValue().getAsJsonArray()) out.add(v.getAsString());
            }
        } else {
            for (JsonElement v : versions.getAsJsonArray()) out.add(v.getAsString());
        }
        return new ArrayList<>(out);
    }

    static Artifact parsePaperBuild(String json) throws IOException {
        JsonObject build = new JsonParser().parse(json).getAsJsonObject();
        JsonObject downloads = build.getAsJsonObject("downloads");
        JsonObject server = downloads == null ? null : downloads.getAsJsonObject("server:default");
        if (server == null) throw new IOException("Paper has no server download for this version");
        String sha256 = null;
        if (server.has("checksums") && server.getAsJsonObject("checksums").has("sha256")) {
            sha256 = server.getAsJsonObject("checksums").get("sha256").getAsString();
        }
        return new Artifact(server.get("url").getAsString(), "server.jar", null, sha256, null);
    }

    static List<String> parsePurpurVersions(String json) {
        List<String> out = new ArrayList<>();
        for (JsonElement v : new JsonParser().parse(json).getAsJsonObject().getAsJsonArray("versions")) out.add(v.getAsString());
        return out;
    }

    /** Fabric and Quilt meta: [{"version":"1.21.4","stable":true}, ...]. Only stable game versions. */
    static List<String> parseFabricGameVersions(String json) {
        List<String> out = new ArrayList<>();
        for (JsonElement e : new JsonParser().parse(json).getAsJsonArray()) {
            JsonObject v = e.getAsJsonObject();
            if (!v.has("stable") || v.get("stable").getAsBoolean()) out.add(v.get("version").getAsString());
        }
        return out;
    }

    /** First entry whose "stable" is true (the meta lists newest first). */
    static String firstStable(String json, String key) throws IOException {
        for (JsonElement e : new JsonParser().parse(json).getAsJsonArray()) {
            JsonObject v = e.getAsJsonObject();
            if (!v.has("stable") || v.get("stable").getAsBoolean()) return v.get(key).getAsString();
        }
        throw new IOException("no stable version listed");
    }

    /** promotions_slim.json -> {mcVersion: forgeBuild}, recommended build preferred over latest. */
    static Map<String, String> parsePromos(String json) {
        Map<String, String> latest = new LinkedHashMap<>();
        Map<String, String> recommended = new LinkedHashMap<>();
        JsonObject promos = new JsonParser().parse(json).getAsJsonObject().getAsJsonObject("promos");
        for (Map.Entry<String, JsonElement> e : promos.entrySet()) {
            String key = e.getKey();
            int dash = key.lastIndexOf('-');
            if (dash < 0) continue;
            String mc = key.substring(0, dash);
            String kind = key.substring(dash + 1);
            if (kind.equals("recommended")) recommended.put(mc, e.getValue().getAsString());
            else if (kind.equals("latest")) latest.put(mc, e.getValue().getAsString());
        }
        Map<String, String> out = new LinkedHashMap<>(latest);
        out.putAll(recommended);
        return out;
    }

    static Map<String, String> parseForgePromos(String json) {
        return parsePromos(json);
    }

    private static final Pattern XML_VERSION = Pattern.compile("<version>([^<]+)</version>");

    /** NeoForge versions are 21.4.111 for Minecraft 1.21.4; 21.0.5 is for 1.21. Stable builds only. */
    static String neoForgeMinecraft(String version) {
        if (version.contains("-")) return null;
        String[] parts = version.split("\\.");
        if (parts.length < 2) return null;
        try {
            int a = Integer.parseInt(parts[0]);
            int b = Integer.parseInt(parts[1]);
            if (a < 20 || a >= 26) return null;
            return "1." + a + (b == 0 ? "" : "." + b);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** maven-metadata.xml -> {mcVersion: newest stable neoforge version}. */
    static Map<String, String> parseNeoForgeMetadata(String xml) {
        Map<String, String> out = new LinkedHashMap<>();
        Matcher m = XML_VERSION.matcher(xml);
        while (m.find()) {
            String v = m.group(1).trim();
            String mc = neoForgeMinecraft(v);
            if (mc == null) continue;
            String existing = out.get(mc);
            if (existing == null || VersionOrder.compare(v, existing) > 0) out.put(mc, v);
        }
        return out;
    }
}
