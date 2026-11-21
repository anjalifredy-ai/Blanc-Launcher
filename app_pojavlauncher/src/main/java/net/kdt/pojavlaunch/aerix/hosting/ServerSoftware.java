package net.kdt.pojavlaunch.aerix.hosting;

/** The server software a hosted server can run. OptiFine is a client-side mod and has no server. */
public enum ServerSoftware {
    VANILLA("Vanilla", false, "Mojang's own server. No plugins or mods."),
    PAPER("Paper", false, "Fast and stable. Runs Bukkit/Spigot/Paper plugins."),
    PURPUR("Purpur", false, "Paper with extra gameplay options. Runs plugins."),
    FABRIC("Fabric", true, "Lightweight modding. Put mods in the mods folder."),
    QUILT("Quilt", true, "Fabric-compatible modding platform."),
    FORGE("Forge", true, "Classic Forge mods (1.12.2 and newer)."),
    NEOFORGE("NeoForge", true, "Modern Forge successor (1.20.2 and newer).");

    public final String displayName;
    /** True when the software is set up by running its own installer jar. */
    public final boolean needsInstaller;
    public final String blurb;

    ServerSoftware(String displayName, boolean needsInstaller, String blurb) {
        this.displayName = displayName;
        this.needsInstaller = needsInstaller;
        this.blurb = blurb;
    }

    public static ServerSoftware fromName(String name) {
        if (name != null) {
            for (ServerSoftware s : values()) if (s.name().equalsIgnoreCase(name)) return s;
        }
        return PAPER;
    }
}
