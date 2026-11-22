package net.kdt.pojavlaunch.aerix.hosting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Browse and install plugins (Paper, Purpur) and mods (Fabric, Quilt, Forge, NeoForge) from Modrinth. */
public final class Modrinth {
    private Modrinth() {}

    private static final String API = "https://api.modrinth.com/v2";

    public static final class Hit {
        public final String id;
        public final String title;
        public final String description;
        public final String author;
        public final long downloads;

        Hit(String id, String title, String description, String author, long downloads) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.author = author;
            this.downloads = downloads;
        }
    }

    static final class FileInfo {
        String url;
        String filename;
        String sha1;
        List<String> requiredProjects = new ArrayList<>();
    }

    public static boolean usesPlugins(ServerSoftware s) {
        return s == ServerSoftware.PAPER || s == ServerSoftware.PURPUR;
    }

    public static boolean supported(ServerSoftware s) {
        return s != ServerSoftware.VANILLA;
    }

    /** Modrinth "loaders" that run on this software. */
    static List<String> loaders(ServerSoftware s) {
        switch (s) {
            case PAPER: return Arrays.asList("paper", "spigot", "bukkit");
            case PURPUR: return Arrays.asList("purpur", "paper", "spigot", "bukkit");
            case FABRIC: return Arrays.asList("fabric");
            case QUILT: return Arrays.asList("quilt", "fabric");
            case FORGE: return Arrays.asList("forge");
            case NEOFORGE: return Arrays.asList("neoforge");
            default: return new ArrayList<>();
        }
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    static String searchUrl(ServerSoftware software, String mc, String query) {
        JsonArray facets = new JsonArray();
        facets.add(group("project_type:" + (usesPlugins(software) ? "plugin" : "mod")));
        JsonArray loaderGroup = new JsonArray();
        for (String l : loaders(software)) loaderGroup.add("categories:" + l);
        facets.add(loaderGroup);
        facets.add(group("versions:" + mc));
        String q = query == null ? "" : query.trim();
        return API + "/search?query=" + enc(q) + "&facets=" + enc(facets.toString()) + "&limit=25&index=" + (q.isEmpty() ? "downloads" : "relevance");
    }

    private static JsonArray group(String one) {
        JsonArray a = new JsonArray();
        a.add(one);
        return a;
    }

    public static List<Hit> search(ServerSoftware software, String mc, String query) throws IOException {
        if (!supported(software)) throw new IOException(software.displayName + " does not support plugins or mods");
        return parseSearch(Http.get(searchUrl(software, mc, query)));
    }

    static List<Hit> parseSearch(String json) {
        List<Hit> out = new ArrayList<>();
        JsonArray hits = new JsonParser().parse(json).getAsJsonObject().getAsJsonArray("hits");
        for (JsonElement e : hits) {
            JsonObject h = e.getAsJsonObject();
            out.add(new Hit(str(h, "project_id"), str(h, "title"), str(h, "description"), str(h, "author"),
                    h.has("downloads") ? h.get("downloads").getAsLong() : 0));
        }
        return out;
    }

    static FileInfo parseVersions(String json) throws IOException {
        JsonArray versions = new JsonParser().parse(json).getAsJsonArray();
        JsonObject chosen = null;
        for (JsonElement e : versions) {
            JsonObject v = e.getAsJsonObject();
            if (!v.has("files") || v.getAsJsonArray("files").size() == 0) continue;
            if ("release".equals(str(v, "version_type"))) {
                chosen = v;
                break;
            }
            if (chosen == null) chosen = v;
        }
        if (chosen == null) throw new IOException("No download is available for this Minecraft version");
        JsonObject file = null;
        for (JsonElement f : chosen.getAsJsonArray("files")) {
            JsonObject o = f.getAsJsonObject();
            if (file == null) file = o;
            if (o.has("primary") && o.get("primary").getAsBoolean()) {
                file = o;
                break;
            }
        }
        FileInfo info = new FileInfo();
        info.url = str(file, "url");
        info.filename = ServerFiles.safeName(str(file, "filename"));
        if (file.has("hashes") && file.getAsJsonObject("hashes").has("sha1")) info.sha1 = file.getAsJsonObject("hashes").get("sha1").getAsString();
        if (chosen.has("dependencies")) {
            for (JsonElement d : chosen.getAsJsonArray("dependencies")) {
                JsonObject dep = d.getAsJsonObject();
                if ("required".equals(str(dep, "dependency_type")) && dep.has("project_id") && !dep.get("project_id").isJsonNull()) {
                    info.requiredProjects.add(dep.get("project_id").getAsString());
                }
            }
        }
        return info;
    }

    private static FileInfo latest(String project, ServerSoftware software, String mc) throws IOException {
        JsonArray loaders = new JsonArray();
        for (String l : loaders(software)) loaders.add(l);
        JsonArray game = new JsonArray();
        game.add(mc);
        String url = API + "/project/" + enc(project) + "/version?loaders=" + enc(loaders.toString()) + "&game_versions=" + enc(game.toString());
        return parseVersions(Http.get(url));
    }

    /** Installs the project and the projects it requires. Returns the file names that were added. */
    public static List<String> install(File contentDir, ServerSoftware software, String mc, String project) throws IOException {
        List<String> added = new ArrayList<>();
        installInto(contentDir, software, mc, project, new HashSet<String>(), 0, added);
        return added;
    }

    private static void installInto(File dir, ServerSoftware software, String mc, String project, Set<String> seen, int depth, List<String> added) throws IOException {
        if (!seen.add(project) || depth > 2) return;
        FileInfo info = latest(project, software, mc);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Could not create " + dir.getName());
        File target = new File(dir, info.filename);
        if (!target.isFile()) {
            Http.download(info.url, target, info.sha1, null, (done, total) -> {});
            added.add(info.filename);
        }
        for (String dep : info.requiredProjects) {
            try {
                installInto(dir, software, mc, dep, seen, depth + 1, added);
            } catch (IOException e) {
                // A dependency without a build for this version should not undo the main install.
            }
        }
    }

    private static String str(JsonObject o, String k) {
        return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }
}
