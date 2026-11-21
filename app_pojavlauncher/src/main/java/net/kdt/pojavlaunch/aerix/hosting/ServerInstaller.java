package net.kdt.pojavlaunch.aerix.hosting;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Downloads a server's software. Installer-based software is finished by running the installer (see HostService). */
public final class ServerInstaller {
    private ServerInstaller() {}

    public interface Progress {
        void onStatus(String message);
    }

    /** What is left to do after the download. Null means the server is ready. */
    public static final class Pending {
        public final List<String> installerArgs;

        Pending(List<String> installerArgs) {
            this.installerArgs = installerArgs;
        }
    }

    public static Pending download(ServerStore store, HostedServer server, Progress progress) throws IOException {
        File dir = store.dir(server.id);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        progress.onStatus("Looking up " + server.software.displayName + " " + server.mcVersion + "...");
        ServerCatalog.Artifact artifact = ServerCatalog.artifact(server.software, server.mcVersion);
        File target = new File(dir, artifact.fileName);
        final long[] last = {0};
        progress.onStatus("Downloading " + server.software.displayName + "...");
        Http.download(artifact.url, target, artifact.sha1, artifact.sha256, (done, total) -> {
            long now = System.currentTimeMillis();
            if (now - last[0] < 700) return;
            last[0] = now;
            String text = total > 0
                    ? "Downloading " + server.software.displayName + "... " + (done * 100 / total) + "%"
                    : "Downloading " + server.software.displayName + "... " + (done / 1024) + " KB";
            progress.onStatus(text);
        });
        if (artifact.installerArgs == null) {
            LaunchPlanner.mainClassOf(target); // a bad download fails here rather than at first start
            return null;
        }
        List<String> args = new ArrayList<>(artifact.installerArgs);
        if (server.software == ServerSoftware.QUILT) args.add("--install-dir=" + dir.getAbsolutePath());
        return new Pending(args);
    }

    /** After an installer ran: checks the server can be launched and removes the installer. */
    public static void finish(ServerStore store, HostedServer server) throws IOException {
        File dir = store.dir(server.id);
        LaunchPlanner.forServer(dir, server.software);
        File installer = new File(dir, "installer.jar");
        if (installer.isFile()) installer.delete();
        File log = new File(dir, "installer.jar.log");
        if (log.isFile()) log.delete();
    }
}
