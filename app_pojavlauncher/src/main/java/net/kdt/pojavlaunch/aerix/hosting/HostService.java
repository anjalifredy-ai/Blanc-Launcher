package net.kdt.pojavlaunch.aerix.hosting;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Process;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The always-on part of hosting (process ":host"). It owns the public port, the tunnel and the idle
 * timer, and starts a ServerJvmService only while somebody wants to play.
 */
public class HostService extends Service implements SleepListener.Host {
    private static final String TAG = "HostService";
    public static final String ACTION_ATTACH = "net.kdt.pojavlaunch.aerix.hosting.ATTACH";
    public static final String ACTION_INSTALL = "net.kdt.pojavlaunch.aerix.hosting.INSTALL";
    public static final String ACTION_START = "net.kdt.pojavlaunch.aerix.hosting.START";
    public static final String ACTION_STOP = "net.kdt.pojavlaunch.aerix.hosting.STOP";
    public static final String ACTION_DETACH = "net.kdt.pojavlaunch.aerix.hosting.DETACH";
    public static final String ACTION_COMMAND = "net.kdt.pojavlaunch.aerix.hosting.HOST_COMMAND";
    public static final String EXTRA_SERVER_ID = "id";
    public static final String EXTRA_COMMAND = "command";
    private static final int NOTIFICATION_ID = 7301;
    private static final long START_TIMEOUT_MS = 15 * 60_000L;
    private static final long STOP_TIMEOUT_MS = 90_000L;
    private static final long PLAYER_POLL_MS = 15_000L;

    private final Object lock = new Object();
    private final HostStatus status = new HostStatus();
    private final IdleTracker idle = new IdleTracker();
    private final ExecutorService work = Executors.newSingleThreadExecutor();
    private ScheduledExecutorService timer;
    private File root;
    private File statusFile;
    private ServerStore store;
    private PowerManager.WakeLock wakeLock;

    private HostedServer server;
    private SleepListener listener;
    private BoreTunnel tunnel;

    private boolean jvmActive;
    private boolean installing;
    private boolean detachWhenStopped;
    private boolean stopAskedByUs;
    private long stateSince;
    private long lastPlayerPoll;
    private ServiceConnection jvmConnection;

    @Override
    public void onCreate() {
        Tools.buildNotificationChannel(getApplicationContext());
        root = new File(getFilesDir(), "hosted_servers");
        //noinspection ResultOfMethodCallIgnored
        root.mkdirs();
        store = new ServerStore(root);
        statusFile = HostClient.statusFile(root);
        timer = Executors.newSingleThreadScheduledExecutor();
        timer.scheduleWithFixedDelay(() -> {
            try {
                tick();
            } catch (Throwable t) {
                Log.e(TAG, "tick failed", t);
            }
        }, 2, 3, TimeUnit.SECONDS);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        showForeground();
        if (intent == null || intent.getAction() == null) {
            maybeShutdown();
            return START_NOT_STICKY;
        }
        final String action = intent.getAction();
        final String id = intent.getStringExtra(EXTRA_SERVER_ID);
        final String command = intent.getStringExtra(EXTRA_COMMAND);
        work.execute(() -> {
            try {
                switch (action) {
                    case ACTION_ATTACH: attach(id); break;
                    case ACTION_INSTALL: install(id); break;
                    case ACTION_START: attach(id); startServer(); break;
                    case ACTION_STOP: stopServer(false); break;
                    case ACTION_DETACH: detach(); break;
                    case ACTION_COMMAND: sendCommand(command); break;
                    default: break;
                }
            } catch (Throwable t) {
                Log.e(TAG, "action " + action + " failed", t);
                synchronized (lock) {
                    status.message = String.valueOf(t.getMessage());
                    publish();
                }
            }
        });
        return START_NOT_STICKY;
    }

    // ------------------------------------------------------------------ hosting

    private void attach(String id) {
        synchronized (lock) {
            if (installing) return;
            if (server != null && !server.id.equals(id) && jvmActive) {
                status.message = "Another server is running. Stop it first.";
                publish();
                return;
            }
            HostedServer loaded = store.get(id);
            if (loaded == null) {
                setState(HostStatus.ERROR, "That server no longer exists");
                return;
            }
            if (!loaded.installed) {
                setState(HostStatus.ERROR, "This server is not installed yet");
                return;
            }
            boolean samePort = server != null && listener != null && server.id.equals(id) && server.frontPort == loaded.frontPort
                    && server.tunnelEnabled == loaded.tunnelEnabled && server.relayHost.equals(loaded.relayHost);
            server = loaded;
            status.serverId = id;
            status.maxPlayers = loaded.maxPlayers;
            if (!samePort) restartListener();
            updateInfo();
            acquireWakeLock();
            if (!jvmActive && listener != null) setState(HostStatus.SLEEPING, "Waiting for a player to join");
        }
    }

    private void restartListener() {
        if (listener != null) listener.stop();
        if (tunnel != null) tunnel.stop();
        listener = null;
        tunnel = null;
        final HostedServer s = server;
        SleepListener fresh = new SleepListener(s.frontPort, this);
        try {
            fresh.start();
        } catch (IOException e) {
            setState(HostStatus.ERROR, "Port " + s.frontPort + " is already in use");
            return;
        }
        listener = fresh;
        if (jvmActive) {
            listener.setRelay(s.privatePort);
        } else {
            listener.setSleeping();
        }
        status.publicAddress = "";
        status.tunnel = "";
        if (s.tunnelEnabled) {
            final File portFile = new File(store.dir(s.id), "tunnel-port.txt");
            tunnel = new BoreTunnel(s.relayHost, s.frontPort, readPort(portFile), new BoreTunnel.Listener() {
                @Override
                public void onState(String text) {
                    synchronized (lock) {
                        status.tunnel = text;
                        publish();
                    }
                }

                @Override
                public void onPublicPort(int port) {
                    try {
                        Files.write(portFile.toPath(), String.valueOf(port).getBytes(StandardCharsets.UTF_8));
                    } catch (IOException ignored) {
                        // Only affects whether the address stays the same next time.
                    }
                    synchronized (lock) {
                        status.publicAddress = s.relayHost + ":" + port;
                        publish();
                    }
                }
            });
            tunnel.start();
        }
    }

    private static int readPort(File file) {
        try {
            return Integer.parseInt(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim());
        } catch (IOException | RuntimeException e) {
            return 0;
        }
    }

    private void updateInfo() {
        if (listener == null || server == null) return;
        SleepListener.Info info = listener.info;
        info.versionName = server.software.displayName + " " + server.mcVersion;
        info.maxPlayers = server.maxPlayers;
        info.motd = server.description.isEmpty() ? server.name : server.description;
        info.favicon = store.faviconDataUri(server.id);
        info.sleepingKick = server.name + " is waking up!\nJoin again in about a minute.";
        info.startingKick = server.name + " is still starting.\nJoin again in a few seconds.";
    }

    @Override
    public boolean onWakeRequested(String playerName) {
        synchronized (lock) {
            if (!HostStatus.SLEEPING.equals(status.state) || jvmActive || installing) return false;
            beginStart();
        }
        return true;
    }

    private void startServer() {
        synchronized (lock) {
            if (server == null || !HostStatus.SLEEPING.equals(status.state) || jvmActive || installing) return;
            beginStart();
        }
    }

    /** Called with the lock held, in state SLEEPING. */
    private void beginStart() {
        final HostedServer s = server;
        setState(HostStatus.STARTING, "Preparing...");
        listener.setStarting();
        stopAskedByUs = false;
        work.execute(() -> {
            try {
                setMessage("Checking Java...");
                ServerRuntime.ensure(this, s.mcVersion);
                LaunchPlanner.forServer(store.dir(s.id), s.software);
                setMessage("Starting " + s.software.displayName + " " + s.mcVersion + "...");
                launchJvm(s, null);
            } catch (Throwable t) {
                Log.e(TAG, "start failed", t);
                synchronized (lock) {
                    if (listener != null) listener.setSleeping();
                    setState(HostStatus.SLEEPING, "Could not start: " + t.getMessage());
                }
            }
        });
    }

    private void launchJvm(HostedServer s, java.util.List<String> installerArgs) {
        Intent run = new Intent(this, ServerJvmService.class)
                .setAction(ServerJvmService.ACTION_RUN)
                .putExtra(ServerJvmService.EXTRA_ROOT, root.getAbsolutePath())
                .putExtra(ServerJvmService.EXTRA_SERVER_ID, s.id);
        if (installerArgs != null) {
            run.putExtra(ServerJvmService.EXTRA_INSTALL_ARGS, installerArgs.toArray(new String[0]));
        }
        //noinspection ResultOfMethodCallIgnored
        new File(store.dir(s.id), ServerJvmService.EXIT_FILE).delete();
        synchronized (lock) {
            jvmActive = true;
            stateSince = System.currentTimeMillis();
            lastPlayerPoll = 0;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(run); else startService(run);
        jvmConnection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder service) {}

            @Override
            public void onServiceDisconnected(ComponentName name) {
                work.execute(HostService.this::onJvmGone);
            }
        };
        bindService(new Intent(this, ServerJvmService.class), jvmConnection, Context.BIND_AUTO_CREATE);
    }

    private void onJvmGone() {
        String exit = "";
        HostedServer s;
        boolean wasInstall;
        synchronized (lock) {
            if (!jvmActive) return;
            jvmActive = false;
            s = server;
            wasInstall = installing;
        }
        try {
            if (jvmConnection != null) unbindService(jvmConnection);
        } catch (RuntimeException ignored) {
            // Already unbound.
        }
        jvmConnection = null;
        if (s != null) {
            try {
                exit = new String(Files.readAllBytes(new File(store.dir(s.id), ServerJvmService.EXIT_FILE).toPath()), StandardCharsets.UTF_8).trim();
            } catch (IOException ignored) {
                exit = "";
            }
        }
        if (wasInstall) {
            finishInstall(s, exit);
            return;
        }
        synchronized (lock) {
            boolean clean = exit.equals("0") || stopAskedByUs;
            if (listener != null) listener.setSleeping();
            idle.reset();
            status.players = -1;
            if (clean) {
                setState(HostStatus.SLEEPING, "Server is sleeping. It starts again when someone joins.");
            } else {
                setState(HostStatus.SLEEPING, "Server stopped unexpectedly" + (exit.isEmpty() ? "" : " (" + exit + ")") + ". Check the console.");
            }
            if (detachWhenStopped) {
                work.execute(this::detach);
            }
        }
    }

    private void stopServer(boolean automatic) {
        synchronized (lock) {
            if (!jvmActive || installing) return;
            if (HostStatus.STOPPING.equals(status.state)) return;
            stopAskedByUs = true;
            setState(HostStatus.STOPPING, automatic ? "No players for a while. Going to sleep..." : "Stopping...");
        }
        sendCommand("stop");
    }

    private void sendCommand(String command) {
        if (command == null || command.trim().isEmpty()) return;
        synchronized (lock) {
            if (!jvmActive) return;
        }
        startService(new Intent(this, ServerJvmService.class)
                .setAction(ServerJvmService.ACTION_COMMAND)
                .putExtra(ServerJvmService.EXTRA_COMMAND, command.trim()));
    }

    private void killJvm() {
        startService(new Intent(this, ServerJvmService.class).setAction(ServerJvmService.ACTION_KILL));
    }

    private void detach() {
        synchronized (lock) {
            if (jvmActive) {
                detachWhenStopped = true;
                stopServer(false);
                return;
            }
        }
        shutdown();
    }

    private void shutdown() {
        synchronized (lock) {
            if (listener != null) listener.stop();
            if (tunnel != null) tunnel.stop();
            listener = null;
            tunnel = null;
            status.state = HostStatus.OFF;
            status.message = "";
            status.players = -1;
            status.publicAddress = "";
            status.tunnel = "";
            publish();
        }
        releaseWakeLock();
        stopForeground(true);
        stopSelf();
        new Thread(() -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ignored) {
                // Going away anyway.
            }
            Process.killProcess(Process.myPid());
        }, "host-exit").start();
    }

    private void maybeShutdown() {
        synchronized (lock) {
            if (listener != null || jvmActive || installing) return;
        }
        shutdown();
    }

    // ------------------------------------------------------------------ install

    private void install(String id) {
        final HostedServer s;
        synchronized (lock) {
            if (installing || jvmActive) return;
            s = store.get(id);
            if (s == null) return;
            if (server != null && listener != null && !server.id.equals(id)) {
                status.message = "Stop hosting the current server before installing another.";
                publish();
                return;
            }
            installing = true;
            server = s;
            status.serverId = id;
            setState(HostStatus.INSTALLING, "Starting...");
        }
        try {
            ServerInstaller.Pending pending = ServerInstaller.download(store, s, this::setMessage);
            if (pending == null) {
                markInstalled(s);
                return;
            }
            setMessage("Preparing Java...");
            ServerRuntime.ensure(this, s.mcVersion);
            setMessage("Running the " + s.software.displayName + " installer (this takes a few minutes)...");
            launchJvm(s, pending.installerArgs);
        } catch (Throwable t) {
            Log.e(TAG, "install failed", t);
            failInstall("Install failed: " + t.getMessage());
        }
    }

    private void finishInstall(HostedServer s, String exit) {
        try {
            if (!exit.equals("0")) throw new IOException("the installer stopped (" + (exit.isEmpty() ? "no result" : exit) + "). Check the console.");
            ServerInstaller.finish(store, s);
            markInstalled(s);
        } catch (IOException e) {
            failInstall("Install failed: " + e.getMessage());
        }
    }

    private void markInstalled(HostedServer s) {
        try {
            HostedServer latest = store.get(s.id);
            if (latest != null) {
                latest.installed = true;
                store.save(latest);
            }
        } catch (IOException e) {
            failInstall("Could not save the server: " + e.getMessage());
            return;
        }
        synchronized (lock) {
            installing = false;
            setState(HostStatus.OFF, "Installed. Press Start to host it.");
            if (listener == null) server = null;
        }
        maybeShutdown();
    }

    private void failInstall(String message) {
        synchronized (lock) {
            installing = false;
            setState(HostStatus.ERROR, message);
            if (listener == null) server = null;
        }
        timer.schedule(this::maybeShutdownKeepStatus, 20, TimeUnit.SECONDS);
    }

    /** After a failed install the error stays visible for a while, then the process goes away. */
    private void maybeShutdownKeepStatus() {
        synchronized (lock) {
            if (listener != null || jvmActive || installing) return;
        }
        shutdown();
    }

    // -------------------------------------------------------------------- timer

    private void tick() {
        final HostedServer s;
        final String state;
        final boolean active;
        synchronized (lock) {
            s = server;
            state = status.state;
            active = jvmActive;
        }
        if (s == null) {
            synchronized (lock) { publish(); }
            return;
        }
        long now = System.currentTimeMillis();

        if (active && new File(store.dir(s.id), ServerJvmService.EXIT_FILE).isFile()) {
            work.execute(this::onJvmGone);
        }

        if (active && !installing) {
            if (HostStatus.STARTING.equals(state)) {
                if (now - stateSince > START_TIMEOUT_MS) {
                    setMessage("Start timed out");
                    killJvm();
                } else if (ping(s) != null) {
                    synchronized (lock) {
                        if (listener != null) listener.setRelay(s.privatePort);
                        idle.reset();
                        setState(HostStatus.RUNNING, "Server is online");
                    }
                }
            } else if (HostStatus.RUNNING.equals(state) && now - lastPlayerPoll >= PLAYER_POLL_MS) {
                lastPlayerPoll = now;
                StatusPing.Result r = ping(s);
                if (r != null) {
                    synchronized (lock) {
                        status.players = r.online;
                        status.maxPlayers = r.max;
                    }
                    if (s.autoSleep && idle.shouldSleep(r.online, now, Math.max(1, s.idleMinutes) * 60_000L)) {
                        stopServer(true);
                    }
                }
            } else if (HostStatus.STOPPING.equals(state) && now - stateSince > STOP_TIMEOUT_MS) {
                setMessage("The server did not stop in time. Forcing it.");
                killJvm();
            }
        }
        synchronized (lock) {
            updateNotification();
            publish();
        }
    }

    private StatusPing.Result ping(HostedServer s) {
        try {
            return StatusPing.ping("127.0.0.1", s.privatePort, 1500);
        } catch (IOException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------- status

    private void setState(String state, String message) {
        synchronized (lock) {
            status.state = state;
            status.message = message;
            stateSince = System.currentTimeMillis();
            publish();
        }
    }

    private void setMessage(String message) {
        synchronized (lock) {
            status.message = message;
            publish();
        }
    }

    private void publish() {
        status.write(statusFile);
    }

    private void showForeground() {
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }
    }

    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification());
    }

    private Notification buildNotification() {
        String title = server != null ? server.name : "Server hosting";
        String text;
        synchronized (lock) {
            text = status.state.equals(HostStatus.RUNNING)
                    ? "Online" + (status.players >= 0 ? " - " + status.players + " players" : "")
                    : status.state.equals(HostStatus.SLEEPING) ? "Sleeping - starts when someone joins"
                    : status.message.isEmpty() ? status.state : status.message;
            if (!status.publicAddress.isEmpty()) text += "\n" + status.publicAddress;
        }
        Intent open = getPackageManager().getLaunchIntentForPackage(getPackageName());
        PendingIntent content = open == null ? null
                : PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, HostService.class).setAction(ACTION_DETACH);
        PendingIntent stopIntent = PendingIntent.getService(this, 7301, stop, PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, getString(R.string.notif_channel_id))
                .setContentTitle(title)
                .setContentText(text.split("\n")[0])
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(content)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop hosting", stopIntent)
                .setSmallIcon(R.drawable.notif_icon)
                .setOngoing(true)
                .setNotificationSilent()
                .build();
    }

    private void acquireWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) return;
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm == null) return;
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "aerix:hosting");
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire();
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        wakeLock = null;
    }

    @Override
    public void onDestroy() {
        if (timer != null) timer.shutdownNow();
        releaseWakeLock();
    }
}
