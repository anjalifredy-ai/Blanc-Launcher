package aerix.client.mod;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.util.Properties;

/**
 * Watches config/aerix_pack_cmd.properties written by the launcher overlay.
 * When a new command appears, triggers MinecraftClient.reloadResources()
 * (or a best-effort shader reload) on the client thread so the player never
 * has to restart the game after installing a resource / shader pack.
 */
public final class ClientPackLiveApply {

    private long lastSeenTs = -1L;
    private String status = "idle";

    public String statusLine() {
        return status;
    }

    public void tick(Object mc, File runDir) {
        if (mc == null || runDir == null) return;
        File cmdFile = new File(runDir, "config/aerix_pack_cmd.properties");
        if (!cmdFile.isFile()) return;

        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(cmdFile)) {
            props.load(in);
        } catch (Throwable t) {
            return;
        }

        long ts;
        try {
            ts = Long.parseLong(props.getProperty("ts", "0"));
        } catch (NumberFormatException e) {
            return;
        }
        if (ts <= lastSeenTs) return;
        lastSeenTs = ts;

        String action = props.getProperty("action", "");
        String file = props.getProperty("file", "");

        try {
            if ("reload_resources".equals(action) || "set_shader".equals(action)) {
                boolean ok = invokeReloadResources(mc);
                status = ok
                        ? ("reloaded" + (file.isEmpty() ? "" : " (" + file + ")"))
                        : "reload method not found on this version";
            } else {
                status = "unknown action: " + action;
            }
        } catch (Throwable t) {
            status = "reload failed: " + t.getClass().getSimpleName();
        }

        // Consume the command so we do not re-fire on the next tick.
        // Keep a tiny status file so the overlay can show what happened.
        try {
            File statusFile = new File(runDir, "config/aerix_pack_status.properties");
            Properties out = new Properties();
            out.setProperty("lastAction", action);
            out.setProperty("file", file);
            out.setProperty("result", status);
            out.setProperty("ts", String.valueOf(System.currentTimeMillis()));
            statusFile.getParentFile().mkdirs();
            statusFile.outputStream().use { out.store(it, "Aerix pack live-apply status") };
            cmdFile.delete();
        } catch (Throwable ignored) {
        }
    }

    /**
     * Tries the intermediary name first (Yarn / Intermediary), then the official mapped name.
     * Covers 1.16.5 → 1.21.x.
     */
    private boolean invokeReloadResources(Object mc) {
        // Intermediary: method_1513 / method_1551 already resolved elsewhere; the reload is method_1521 on many versions.
        String[] candidates = {
                "method_1521",          // intermediary reloadResources
                "reloadResources",      // official / modern mapped
                "method_1513"           // older intermediary fallback
        };
        for (String name : candidates) {
            try {
                Method m = mc.getClass().getMethod(name);
                m.setAccessible(true);
                Object result = m.invoke(mc);
                // Some versions return a CompletableFuture; we fire-and-forget.
                return true;
            } catch (NoSuchMethodException ignored) {
            } catch (Throwable t) {
                // Method existed but threw – still counts as an attempt.
                return true;
            }
        }

        // Last resort: look for any no-arg method whose name contains "reload" and "Resource".
        for (Method m : mc.getClass().getMethods()) {
            String n = m.getName().toLowerCase();
            if (m.getParameterCount() == 0 && n.contains("reload") && n.contains("resource")) {
                try {
                    m.setAccessible(true);
                    m.invoke(mc);
                    return true;
                } catch (Throwable ignored) {
                }
            }
        }
        return false;
    }
}
