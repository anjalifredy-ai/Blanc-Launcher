package net.kdt.pojavlaunch.aerix.hosting;

import java.io.File;
import java.io.IOException;

/**
 * Lighter Paper/Spigot settings for a phone. Paper only writes its config files after the first start, so
 * this runs before every start and only touches keys that exist.
 */
public final class PerformancePreset {
    private PerformancePreset() {}

    public static void apply(File serverDir) {
        try {
            YamlEdit.apply(new File(serverDir, "config/paper-world-defaults.yml"),
                    "chunks.max-auto-save-chunks-per-tick", "8",
                    "environment.optimize-explosions", "true",
                    "environment.treasure-maps.enabled", "false",
                    "collisions.max-entity-collisions", "2",
                    "misc.redstone-implementation", "ALTERNATE_CURRENT",
                    "entities.armor-stands.tick", "false",
                    "hopper.disable-move-event", "true");
            YamlEdit.apply(new File(serverDir, "spigot.yml"),
                    "world-settings.default.entity-activation-range.animals", "16",
                    "world-settings.default.entity-activation-range.monsters", "24",
                    "world-settings.default.entity-activation-range.raiders", "32",
                    "world-settings.default.entity-activation-range.misc", "8",
                    "world-settings.default.entity-activation-range.water", "8",
                    "world-settings.default.entity-activation-range.villagers", "16",
                    "world-settings.default.entity-activation-range.flying-monsters", "24",
                    "world-settings.default.entity-tracking-range.players", "48",
                    "world-settings.default.entity-tracking-range.animals", "32",
                    "world-settings.default.entity-tracking-range.monsters", "32",
                    "world-settings.default.entity-tracking-range.misc", "16",
                    "world-settings.default.entity-tracking-range.other", "32",
                    "world-settings.default.mob-spawn-range", "4",
                    "world-settings.default.tick-inactive-villagers", "false");
            YamlEdit.apply(new File(serverDir, "bukkit.yml"),
                    "spawn-limits.monsters", "40",
                    "spawn-limits.animals", "8",
                    "spawn-limits.water-animals", "3",
                    "spawn-limits.water-ambient", "3",
                    "spawn-limits.water-underground-creature", "3",
                    "spawn-limits.axolotls", "3",
                    "spawn-limits.ambient", "2");
        } catch (IOException ignored) {
            // The preset is optional; the server starts with its own settings.
        }
    }
}
