package net.kdt.pojavlaunch.aerix.hosting;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Process;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import net.kdt.pojavlaunch.ExitActivity;
import net.kdt.pojavlaunch.Logger;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.multirt.Runtime;
import net.kdt.pojavlaunch.utils.JREUtils;
import net.kdt.pojavlaunch.utils.jre.JavaRunner;
import net.kdt.pojavlaunch.utils.jre.LaunchMemory;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Runs one JVM, then the process ends. Lives in its own process (":serverjvm") because a JVM can be
 * loaded only once per process; HostService starts a fresh one for every wake-up.
 */
public class ServerJvmService extends Service {
    private static final String TAG = "ServerJvmService";
    public static final String ACTION_RUN = "net.kdt.pojavlaunch.aerix.hosting.RUN";
    public static final String ACTION_COMMAND = "net.kdt.pojavlaunch.aerix.hosting.COMMAND";
    public static final String ACTION_KILL = "net.kdt.pojavlaunch.aerix.hosting.KILL";
    public static final String EXTRA_ROOT = "root";
    public static final String EXTRA_SERVER_ID = "id";
    public static final String EXTRA_INSTALL_ARGS = "installArgs"; // present = run installer.jar with these
    public static final String EXTRA_COMMAND = "command";
    public static final int NOTIFICATION_ID = 7302;

    public static final String CONSOLE_LOG = "aerix-console.log";
    public static final String CONSOLE_LOG_PREVIOUS = "aerix-console.prev.log";
    public static final String EXIT_FILE = "aerix-exit.txt";

    private boolean started;
    private FileOutputStream stdin;
    private File serverDir;

    @Override
    public IBinder onBind(Intent intent) {
        return new Binder();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) return START_NOT_STICKY;
        switch (intent.getAction()) {
            case ACTION_RUN:
                if (!started) {
                    started = true;
                    begin(intent);
                }
                break;
            case ACTION_COMMAND:
                sendCommand(intent.getStringExtra(EXTRA_COMMAND));
                break;
            case ACTION_KILL:
                Process.killProcess(Process.myPid());
                break;
            default:
                break;
        }
        return START_NOT_STICKY;
    }

    private void begin(Intent intent) {
        Tools.buildNotificationChannel(getApplicationContext());
        Intent kill = new Intent(this, ServerJvmService.class).setAction(ACTION_KILL);
        PendingIntent killIntent = PendingIntent.getService(this, 7302, kill, PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new NotificationCompat.Builder(this, getString(R.string.notif_channel_id))
                .setContentTitle("Minecraft server")
                .setContentText("The server is running")
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Force stop", killIntent)
                .setSmallIcon(R.drawable.notif_icon)
                .setOngoing(true)
                .setNotificationSilent()
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        final File root = new File(intent.getStringExtra(EXTRA_ROOT));
        final String id = intent.getStringExtra(EXTRA_SERVER_ID);
        final String[] installArgs = intent.getStringArrayExtra(EXTRA_INSTALL_ARGS);
        final Context app = getApplicationContext();
        Thread thread = new Thread(() -> run(app, root, id, installArgs), "server-jvm");
        thread.start();
    }

    private void run(Context context, File root, String id, String[] installArgs) {
        ServerStore store = new ServerStore(root);
        serverDir = store.dir(id);
        File exitFile = new File(serverDir, EXIT_FILE);
        try {
            HostedServer server = store.get(id);
            if (server == null) throw new IOException("server " + id + " no longer exists");

            LaunchPlan plan;
            if (installArgs != null) {
                plan = LaunchPlanner.installerPlan(new File(serverDir, "installer.jar"), Arrays.asList(installArgs));
                plan.workDir = serverDir;
            } else {
                plan = LaunchPlanner.forServer(serverDir, server.software);
                store.prepare(server, plan.workDir);
                store.installLogo(id, plan.workDir);
            }
            Runtime runtime = ServerRuntime.ensure(context, server.mcVersion);

            exitFile.delete();
            // Console output goes to a file the UI tails; the previous run is kept for reference.
            File log = new File(serverDir, CONSOLE_LOG);
            File previous = new File(serverDir, CONSOLE_LOG_PREVIOUS);
            if (log.isFile()) {
                previous.delete();
                log.renameTo(previous);
            }
            if (!log.createNewFile() && !log.isFile()) throw new IOException("cannot create the console log");

            ExitActivity.setQuietExitHandler((code, isSignal) -> {
                Logger.appendToLog("[Aerix] The Java program exited with code " + code + (isSignal ? " (signal)" : "") + ".\n");
                writeExit(exitFile, code, isSignal);
            });

            JREUtils.chdir(plan.workDir.getAbsolutePath());
            Logger.begin(log.getAbsolutePath());
            attachStdin();
            JavaRunner.nativeSetupExit(context);

            int ramMb = installArgs != null ? 1024 : server.ramMb;
            ramMb = LaunchMemory.clamp(context, ramMb);
            Logger.appendToLog("[Aerix] Starting " + (installArgs != null ? "installer" : server.software.displayName + " " + server.mcVersion)
                    + " with Java " + runtime.javaVersion + ", " + ramMb + " MB\n");
            List<String> classpath = new ArrayList<>(plan.classpath);
            JavaRunner.startJvm(runtime, new ArrayList<>(plan.jvmArgs), classpath, plan.mainClass, new ArrayList<>(plan.appArgs), ramMb);
            // startJvm returns normally when the program's main thread and every other thread finished
            // (the JVM shut down by itself, e.g. an installer finishing). That is a clean exit.
            Logger.appendToLog("[Aerix] The Java program finished.\n");
            writeExit(exitFile, 0, false);
            Process.killProcess(Process.myPid());
        } catch (Throwable error) {
            Log.e(TAG, "Server JVM failed", error);
            try {
                writeFailure(exitFile, error);
            } finally {
                Process.killProcess(Process.myPid());
            }
        }
    }

    private void attachStdin() throws ErrnoException {
        FileDescriptor[] pipe = Os.pipe();
        Os.dup2(pipe[0], 0);
        Os.close(pipe[0]);
        stdin = new FileOutputStream(pipe[1]);
    }

    private synchronized void sendCommand(String command) {
        if (stdin == null || command == null) return;
        Logger.appendToLog("> " + command + "\n");
        try {
            stdin.write((command + "\n").getBytes(StandardCharsets.UTF_8));
            stdin.flush();
        } catch (IOException e) {
            Log.w(TAG, "Could not send command", e);
        }
    }

    private static void writeExit(File file, int code, boolean signal) {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write((code + (signal ? " signal" : "")).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // The host also notices the process ending.
        }
    }

    private static void writeFailure(File file, Throwable error) {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(("-3 " + String.valueOf(error.getMessage()).replace('\n', ' ')).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }
}
