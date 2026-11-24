package net.kdt.pojavlaunch.aerix.multiplayer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds the port Minecraft opened for "Open to LAN" in a log line. Works for old and new versions. */
public final class LanPort {
    private static final Pattern MODERN = Pattern.compile("Started serving on (\\d{2,5})");
    private static final Pattern OLD = Pattern.compile("Local game hosted on port (\\d{2,5})");

    private LanPort() {}

    /** The port, or -1 when the line is about something else. */
    public static int parse(String line) {
        if (line == null) return -1;
        Matcher m = MODERN.matcher(line);
        if (!m.find()) {
            m = OLD.matcher(line);
            if (!m.find()) return -1;
        }
        try {
            int p = Integer.parseInt(m.group(1));
            return p >= 1024 && p <= 65535 ? p : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
