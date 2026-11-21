package net.kdt.pojavlaunch.aerix.hosting;

/** Which Java a Minecraft server version needs. */
public final class JavaVersions {
    private JavaVersions() {}

    /**
     * 1.16.5 and older: 8. 1.17 to 1.20.4: 17. 1.20.5 to 1.21.x: 21. The year-based scheme (26.x, 27.x...)
     * that follows 1.21 is assumed to need 25, the newest runtime Aerix ships.
     */
    public static int requiredFor(String mcVersion) {
        try {
            String[] parts = mcVersion.split("\\.");
            int first = Integer.parseInt(parts[0].replaceAll("[^0-9]", ""));
            if (first >= 26) return 25;
            int minor = parts.length > 1 ? Integer.parseInt(parts[1].replaceAll("[^0-9]", "")) : 0;
            int patch = parts.length > 2 ? Integer.parseInt(parts[2].replaceAll("[^0-9]", "")) : 0;
            if (minor <= 16) return 8;
            if (minor < 20) return 17;
            if (minor == 20) return patch >= 5 ? 21 : 17;
            return 21;
        } catch (RuntimeException e) {
            return 21;
        }
    }
}
