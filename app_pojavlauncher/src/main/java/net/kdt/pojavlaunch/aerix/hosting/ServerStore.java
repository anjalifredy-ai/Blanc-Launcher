package net.kdt.pojavlaunch.aerix.hosting;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Servers live in <root>/<id>/ ; server.json holds settings, everything else is the server's own data. */
public final class ServerStore {
    private final File root;

    public ServerStore(File root) {
        this.root = root;
    }

    public File dir(String id) {
        return new File(root, id);
    }

    private File config(String id) {
        return new File(dir(id), "server.json");
    }

    /** The folder the JVM runs in once installed (Quilt puts its files in a server/ subfolder). */
    public List<HostedServer> list() {
        List<HostedServer> out = new ArrayList<>();
        File[] dirs = root.listFiles(File::isDirectory);
        if (dirs == null) return out;
        for (File d : dirs) {
            HostedServer s = get(d.getName());
            if (s != null) out.add(s);
        }
        Collections.sort(out, (a, b) -> Long.compare(a.createdAt, b.createdAt));
        return out;
    }

    public HostedServer get(String id) {
        File f = config(id);
        if (!f.isFile()) return null;
        try {
            HostedServer s = HostedServer.fromJson(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            s.id = id;
            return s;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    public void save(HostedServer s) throws IOException {
        File d = dir(s.id);
        if (!d.isDirectory() && !d.mkdirs()) throw new IOException("cannot create " + d);
        File tmp = new File(d, "server.json.tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(s.toJson().toString().getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
        Files.move(tmp.toPath(), config(s.id).toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    public HostedServer create(String name, ServerSoftware software, String mcVersion, boolean eula) throws IOException {
        HostedServer s = new HostedServer();
        s.id = UUID.randomUUID().toString().substring(0, 8);
        s.name = name.trim().isEmpty() ? "My Server" : name.trim();
        s.software = software;
        s.mcVersion = mcVersion;
        s.eulaAccepted = eula;
        s.ramMb = JavaVersions.requiredFor(mcVersion) >= 17 ? 2048 : 1536;
        allocatePorts(s);
        save(s);
        return s;
    }

    /** Gives [s] a front port and private port no other server uses. */
    void allocatePorts(HostedServer s) {
        Set<Integer> used = new HashSet<>();
        for (HostedServer o : list()) {
            used.add(o.frontPort);
            used.add(o.privatePort);
        }
        int front = 25565;
        while (used.contains(front) || used.contains(front + 10000)) front++;
        s.frontPort = front;
        s.privatePort = front + 10000;
    }

    public void delete(String id) throws IOException {
        deleteRecursive(dir(id));
    }

    private static void deleteRecursive(File f) throws IOException {
        File[] children = f.listFiles();
        if (children != null && !Files.isSymbolicLink(f.toPath())) for (File c : children) deleteRecursive(c);
        if (f.exists() && !f.delete()) throw new IOException("cannot delete " + f);
    }

    /** Writes eula.txt and the settings this app controls into server.properties. Call before every start. */
    public void prepare(HostedServer s, File workDir) throws IOException {
        if (!workDir.isDirectory() && !workDir.mkdirs()) throw new IOException("cannot create " + workDir);
        if (!s.eulaAccepted) throw new IOException("The Minecraft EULA has not been accepted for this server");
        Files.write(new File(workDir, "eula.txt").toPath(),
                ("# Accepted in the launcher by the server owner\neula=true\n").getBytes(StandardCharsets.UTF_8));
        File props = new File(workDir, "server.properties");
        if (!props.exists()) Files.write(props.toPath(), new byte[0]);
        ServerProperties.apply(props, s.properties());
    }

    /** Where the server list icon is kept (the game itself reads server-icon.png from its working dir). */
    public File logoFile(String id) {
        return new File(dir(id), "logo.png");
    }

    public void saveLogo(String id, byte[] png) throws IOException {
        Files.write(logoFile(id).toPath(), png);
    }

    /** The icon as a data: URI for the sleeping server list entry, or null. */
    public String faviconDataUri(String id) {
        File f = logoFile(id);
        if (!f.isFile() || f.length() > 60_000) return null;
        try {
            return "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(Files.readAllBytes(f.toPath()));
        } catch (IOException e) {
            return null;
        }
    }

    /** Copies the logo next to the world so the awake server shows it too. */
    public void installLogo(String id, File workDir) throws IOException {
        File f = logoFile(id);
        if (f.isFile()) Files.copy(f.toPath(), new File(workDir, "server-icon.png").toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}
