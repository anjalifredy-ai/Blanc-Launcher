package net.kdt.pojavlaunch.aerix.hosting;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** File access inside one server's folder. Every path is checked so nothing outside the folder is reachable. */
public final class ServerFiles {
    private ServerFiles() {}

    public static final long MAX_EDIT_BYTES = 512 * 1024;
    private static final List<String> TEXT_EXTENSIONS = Arrays.asList(
            "txt", "properties", "yml", "yaml", "json", "json5", "toml", "cfg", "conf", "config", "log", "md", "xml",
            "ini", "mcmeta", "sk", "csv", "lang", "secret", "list");

    public static final class Entry {
        public final String name;
        public final boolean directory;
        public final long size;

        Entry(String name, boolean directory, long size) {
            this.name = name;
            this.directory = directory;
            this.size = size;
        }
    }

    /** Resolves [relative] inside [root]. Throws if it would leave the folder (.. or symlinks). */
    public static File resolve(File root, String relative) throws IOException {
        File base = root.getCanonicalFile();
        File target = relative == null || relative.isEmpty() ? base : new File(base, relative).getCanonicalFile();
        String basePath = base.getPath();
        if (!target.getPath().equals(basePath) && !target.getPath().startsWith(basePath + File.separator)) {
            throw new IOException("That path is outside the server folder");
        }
        return target;
    }

    public static String join(String dir, String name) {
        return dir == null || dir.isEmpty() ? name : dir + "/" + name;
    }

    public static String parent(String dir) {
        if (dir == null) return "";
        int i = dir.lastIndexOf('/');
        return i < 0 ? "" : dir.substring(0, i);
    }

    /** Folders first, then files, each alphabetical. */
    public static List<Entry> list(File root, String dir) throws IOException {
        File folder = resolve(root, dir);
        File[] children = folder.listFiles();
        List<Entry> out = new ArrayList<>();
        if (children == null) return out;
        for (File c : children) {
            if (c.getName().endsWith(".part")) continue;
            out.add(new Entry(c.getName(), c.isDirectory(), c.isDirectory() ? 0 : c.length()));
        }
        Collections.sort(out, (a, b) -> {
            if (a.directory != b.directory) return a.directory ? -1 : 1;
            return a.name.toLowerCase(Locale.ROOT).compareTo(b.name.toLowerCase(Locale.ROOT));
        });
        return out;
    }

    public static boolean isEditable(String name, long size) {
        if (size > MAX_EDIT_BYTES) return false;
        int dot = name.lastIndexOf('.');
        if (dot < 0) return !name.startsWith(".") || name.length() > 1; // files like "eula" with no extension
        return TEXT_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    public static String readText(File root, String path) throws IOException {
        File f = resolve(root, path);
        if (!f.isFile()) throw new IOException("Not a file");
        if (f.length() > MAX_EDIT_BYTES) throw new IOException("File is too big to edit here");
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    public static void writeText(File root, String path, String text) throws IOException {
        File f = resolve(root, path);
        if (f.isDirectory()) throw new IOException("That is a folder");
        File tmp = new File(f.getParentFile(), f.getName() + ".part");
        Files.write(tmp.toPath(), text.getBytes(StandardCharsets.UTF_8));
        Files.move(tmp.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    public static void delete(File root, String path) throws IOException {
        File f = resolve(root, path);
        if (f.equals(root.getCanonicalFile())) throw new IOException("The server folder itself cannot be deleted here");
        deleteRecursive(f);
    }

    private static void deleteRecursive(File f) throws IOException {
        File[] children = f.listFiles();
        if (children != null && !Files.isSymbolicLink(f.toPath())) for (File c : children) deleteRecursive(c);
        if (f.exists() && !f.delete()) throw new IOException("Could not delete " + f.getName());
    }

    public static String safeName(String name) throws IOException {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty() || n.equals(".") || n.equals("..") || n.contains("/") || n.contains("\\") || n.indexOf('\0') >= 0) {
            throw new IOException("Invalid name");
        }
        return n;
    }

    public static void mkdir(File root, String dir, String name) throws IOException {
        File f = resolve(root, join(dir, safeName(name)));
        if (!f.exists() && !f.mkdirs()) throw new IOException("Could not create the folder");
    }

    public static void importStream(File root, String dir, String name, InputStream in) throws IOException {
        File folder = resolve(root, dir);
        File target = resolve(root, join(dir, safeName(name)));
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Could not create " + folder.getName());
        File tmp = new File(folder, target.getName() + ".part");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        Files.move(tmp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    /** plugins/ for Paper-like servers, mods/ for modded ones. Quilt keeps its files under server/. */
    public static File contentDir(File serverDir, ServerSoftware software) {
        File base = serverDir;
        File sub = new File(serverDir, "server");
        if (software == ServerSoftware.QUILT && sub.isDirectory()) base = sub;
        return new File(base, Modrinth.usesPlugins(software) ? "plugins" : "mods");
    }
}
