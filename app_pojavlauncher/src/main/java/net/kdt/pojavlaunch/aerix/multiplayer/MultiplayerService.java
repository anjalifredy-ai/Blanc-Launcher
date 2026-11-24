package net.kdt.pojavlaunch.aerix.multiplayer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.aerix.hosting.BoreTunnel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Random;

/**
 * Runs in its own process so a shared world keeps working while the game is on screen.
 * HOST: watches the game log for "Open to LAN", puts a 10-player proxy and a tunnel in front of it, shows the code.
 * JOIN: opens a local port that leads to the host's tunnel, so Minecraft can connect to 127.0.0.1:port.
 */
public final class MultiplayerService extends Service {
    public static final String ACTION_HOST = "blanc.mp.HOST";
    public static final String ACTION_JOIN = "blanc.mp.JOIN";
    public static final String ACTION_STOP = "blanc.mp.STOP";
    public static final String EXTRA_LOG = "log";
    public static final String EXTRA_CODE = "code";
    public static final String EXTRA_LOCAL_PORT = "localPort";
    public static final int MAX_PLAYERS = 10;
    private static final int NOTIFICATION_ID = 7410;

    private final Object lock = new Object();
    private volatile boolean watching;
    private Thread watcher;
    private CappedProxy proxy;
    private BoreTunnel tunnel;
    private String mode = "";
    private String state = "";
    private String code = "";
    private String message = "";
    private Thread reporter;

    public static File stateFile(Context context) {
        File dir = new File(context.getFilesDir(), "multiplayer");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, "state.txt");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Tools.buildNotificationChannel(getApplicationContext());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_STOP : intent.getAction();
        if (ACTION_HOST.equals(action)) {
            startHost(intent.getStringExtra(EXTRA_LOG));
        } else if (ACTION_JOIN.equals(action)) {
            startJoin(intent.getStringExtra(EXTRA_CODE), intent.getIntExtra(EXTRA_LOCAL_PORT, 0));
        } else {
            shutdown();
            return START_NOT_STICKY;
        }
        return START_NOT_STICKY;
    }

    private void set(String newState, String newMessage) {
        synchronized (lock) {
            state = newState;
            message = newMessage;
        }
        publish();
    }

    private void publish() {
        String text;
        int players = proxy == null ? 0 : proxy.connections();
        synchronized (lock) {
            text = "mode=" + mode + "\nstate=" + state + "\ncode=" + code + "\nplayers=" + players
                    + "\nmax=" + MAX_PLAYERS + "\nmessage=" + message.replace('\n', ' ') + "\n";
        }
        try (FileOutputStream out = new FileOutputStream(stateFile(this))) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, notification());
    }

    private Notification notification() {
        String title;
        String text;
        synchronized (lock) {
            if (mode.equals("host")) {
                title = code.isEmpty() ? "Blanc world" : "World code: " + code;
                text = code.isEmpty() ? message : "Friends enter this code in Blanc > Multiplayer. " + (proxy == null ? 0 : proxy.connections()) + "/" + MAX_PLAYERS + " online";
            } else {
                title = "Joined a Blanc world";
                text = message;
            }
        }
        Intent open = getPackageManager().getLaunchIntentForPackage(getPackageName());
        PendingIntent content = open == null ? null : PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 7411,
                new Intent(this, MultiplayerService.class).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, getString(R.string.notif_channel_id))
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(content)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop sharing", stop)
                .setSmallIcon(R.drawable.notif_icon)
                .setOngoing(true)
                .setNotificationSilent()
                .build();
    }

    private void foreground() {
        Notification n = notification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }
    }

    // ------------------------------------------------------------------ host

    private void startHost(final String logPath) {
        stopWork();
        synchronized (lock) { mode = "host"; code = ""; }
        set("WAITING", "In your world press Esc, then Open to LAN, then Start LAN World.");
        foreground();
        watching = true;
        watcher = new Thread(() -> watchLog(new File(logPath)), "blanc-lan-watch");
        watcher.setDaemon(true);
        watcher.start();
        startReporter();
    }

    private void watchLog(File log) {
        long pos = log.length();
        int lastPort = -1;
        StringBuilder partial = new StringBuilder();
        while (watching) {
            try {
                Thread.sleep(700);
                if (!log.exists()) continue;
                long len = log.length();
                if (len < pos) pos = 0; // the game restarted and truncated its log
                if (len == pos) continue;
                try (RandomAccessFile f = new RandomAccessFile(log, "r")) {
                    f.seek(pos);
                    byte[] buf = new byte[(int) Math.min(len - pos, 256 * 1024)];
                    int n = f.read(buf);
                    if (n <= 0) continue;
                    pos += n;
                    partial.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                }
                int nl;
                while ((nl = partial.indexOf("\n")) >= 0) {
                    String line = partial.substring(0, nl);
                    partial.delete(0, nl + 1);
                    int port = LanPort.parse(line);
                    if (port > 0 && port != lastPort) {
                        lastPort = port;
                        openTunnel(port);
                    }
                }
            } catch (InterruptedException e) {
                return;
            } catch (IOException | RuntimeException ignored) {
            }
        }
    }

    private void openTunnel(int lanPort) {
        stopWork();
        watching = true;
        set("CONNECTING", "Opening your world to friends...");
        try {
            proxy = new CappedProxy("127.0.0.1", lanPort, MAX_PLAYERS);
            int proxyPort = proxy.start(0);
            tunnel = new BoreTunnel(RoomCode.RELAYS[0], proxyPort, 0, new BoreTunnel.Listener() {
                @Override public void onState(String s) {
                    synchronized (lock) { if (code.isEmpty()) message = s; }
                    publish();
                }
                @Override public void onPublicPort(int port) {
                    synchronized (lock) { code = RoomCode.encode(0, port, new Random().nextInt(256)); }
                    set("ONLINE", "Your world is open. Share the code.");
                }
            });
            tunnel.start();
        } catch (IOException | RuntimeException e) {
            set("ERROR", "Could not share the world: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ join

    private void startJoin(String codeText, int localPort) {
        stopWork();
        RoomCode.Room room = RoomCode.decode(codeText);
        synchronized (lock) { mode = "join"; code = codeText == null ? "" : codeText; }
        foreground();
        if (room == null) {
            set("ERROR", "That code is not valid.");
            return;
        }
        try {
            proxy = new CappedProxy(room.relayHost, room.port, 4);
            int port = proxy.start(localPort);
            set("ONLINE", "Minecraft connects through 127.0.0.1:" + port);
            startReporter();
        } catch (IOException | RuntimeException e) {
            set("ERROR", "Could not open the local port: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ shared

    private void startReporter() {
        if (reporter != null && reporter.isAlive()) return;
        reporter = new Thread(() -> {
            while (true) {
                try { Thread.sleep(3000); } catch (InterruptedException e) { return; }
                if (state.equals("ONLINE")) publish();
            }
        }, "blanc-mp-report");
        reporter.setDaemon(true);
        reporter.start();
    }

    private void stopWork() {
        if (tunnel != null) { tunnel.stop(); tunnel = null; }
        if (proxy != null) { proxy.stop(); proxy = null; }
    }

    private void shutdown() {
        watching = false;
        if (watcher != null) watcher.interrupt();
        if (reporter != null) reporter.interrupt();
        stopWork();
        synchronized (lock) { state = "OFF"; code = ""; message = ""; }
        try {
            //noinspection ResultOfMethodCallIgnored
            stateFile(this).delete();
        } catch (RuntimeException ignored) {
        }
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        watching = false;
        stopWork();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
