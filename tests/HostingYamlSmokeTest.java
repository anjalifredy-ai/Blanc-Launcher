import net.kdt.pojavlaunch.aerix.hosting.*;
import java.io.File;
import java.nio.file.Files;

public class HostingYamlSmokeTest {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
    public static void main(String[] a) throws Exception {
        File dir = Files.createTempDirectory("yaml").toFile();
        new File(dir, "config").mkdirs();
        Files.write(new File(dir, "spigot.yml").toPath(), (
            "# comment\nworld-settings:\n  default:\n    mob-spawn-range: 8 # keep\n    entity-activation-range:\n      animals: 32\n      monsters: 32\n    merge-radius:\n      item: 2.5\n  other:\n    mob-spawn-range: 8\n").getBytes());
        Files.write(new File(dir, "config/paper-world-defaults.yml").toPath(), ("chunks:\n  max-auto-save-chunks-per-tick: 24\n  other: 1\nmisc:\n  redstone-implementation: VANILLA\n").getBytes());
        PerformancePreset.apply(dir);
        String spigot = new String(Files.readAllBytes(new File(dir, "spigot.yml").toPath()));
        check(spigot.contains("    mob-spawn-range: 4\n"), "default range changed: " + spigot);
        check(spigot.contains("  other:\n    mob-spawn-range: 8"), "other world untouched");
        check(spigot.contains("      animals: 16") && spigot.contains("      monsters: 24") && spigot.contains("item: 2.5") && spigot.contains("# comment"), "nested keys + others kept");
        String paper = new String(Files.readAllBytes(new File(dir, "config/paper-world-defaults.yml").toPath()));
        check(paper.contains("max-auto-save-chunks-per-tick: 8") && paper.contains("redstone-implementation: ALTERNATE_CURRENT") && paper.contains("other: 1"), "paper: " + paper);
        check(!paper.contains("optimize-explosions"), "missing keys never added");
        System.out.println("Hosting yaml smoke test passed");
    }
}
