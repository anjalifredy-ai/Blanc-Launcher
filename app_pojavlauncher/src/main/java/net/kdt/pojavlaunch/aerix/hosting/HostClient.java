package net.kdt.pojavlaunch.aerix.hosting;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/** What the UI uses to talk to the host process. */
public final class HostClient {
    private HostClient() {}

    public static File root(Context context) {
        File dir = new File(context.getFilesDir(), "hosted_servers");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    public static File statusFile(File root) {
        return new File(root, "host-status.json");
    }

    public static ServerStore store(Context context) {
        return new ServerStore(root(context));
    }

    /** The current status, with a stale file (host process gone) reported as OFF. */
    public static HostStatus status(Context context) {
        HostStatus s = HostStatus.read(statusFile(root(context)));
        if (!s.isFresh(System.currentTimeMillis()) && !HostStatus.OFF.equals(s.state)) {
            HostStatus off = new HostStatus();
            off.serverId = s.serverId;
            return off;
        }
        return s;
    }

    private static void send(Context context, String action, String id, String command) {
        Intent i = new Intent(context, HostService.class).setAction(action);
        if (id != null) i.putExtra(HostService.EXTRA_SERVER_ID, id);
        if (command != null) i.putExtra(HostService.EXTRA_COMMAND, command);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i);
        else context.startService(i);
    }

    public static void install(Context c, String id) { send(c, HostService.ACTION_INSTALL, id, null); }
    /** Start listening and hosting (sleeping until someone joins). */
    public static void host(Context c, String id) { send(c, HostService.ACTION_ATTACH, id, null); }
    /** Start listening and start the server right now. */
    public static void startNow(Context c, String id) { send(c, HostService.ACTION_START, id, null); }
    public static void stopServer(Context c, String id) { send(c, HostService.ACTION_STOP, id, null); }
    public static void stopHosting(Context c, String id) { send(c, HostService.ACTION_DETACH, id, null); }
    public static void command(Context c, String id, String text) { send(c, HostService.ACTION_COMMAND, id, text); }

    /** The last [maxBytes] of the server's console log. */
    public static String consoleTail(Context context, String id, int maxBytes) {
        String host = "";
        try {
            File hostLog = new File(store(context).dir(id), "aerix-host.log");
            if (hostLog.isFile()) host = new String(java.nio.file.Files.readAllBytes(hostLog.toPath()), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            host = "";
        }
        String body = serverLogTail(context, id, maxBytes);
        return host.isEmpty() ? body : host + body;
    }

    private static String serverLogTail(Context context, String id, int maxBytes) {
        File log = new File(store(context).dir(id), ServerJvmService.CONSOLE_LOG);
        if (!log.isFile()) return "";
        try (RandomAccessFile file = new RandomAccessFile(log, "r")) {
            long length = file.length();
            long start = Math.max(0, length - maxBytes);
            byte[] data = new byte[(int) (length - start)];
            file.seek(start);
            file.readFully(data);
            String text = new String(data, StandardCharsets.UTF_8);
            if (start > 0) {
                int nl = text.indexOf('\n');
                if (nl >= 0) text = text.substring(nl + 1);
            }
            return text;
        } catch (IOException e) {
            return "";
        }
    }
}
