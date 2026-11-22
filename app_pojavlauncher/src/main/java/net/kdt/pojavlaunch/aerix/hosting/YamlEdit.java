package net.kdt.pojavlaunch.aerix.hosting;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Changes values of keys that already exist in a YAML file, without touching anything else (comments,
 * order and unknown keys stay). A key that is not in the file is skipped, never added.
 */
public final class YamlEdit {
    private YamlEdit() {}

    private static final Pattern KEY = Pattern.compile("^(\\s*)([A-Za-z0-9_.\\-]+):(\\s*)(.*)$");

    /** Path is dot separated, e.g. "world-settings.default.mob-spawn-range". Returns true when the line was changed. */
    public static boolean set(List<String> lines, String path, String value) {
        String[] want = path.split("\\.");
        List<Integer> indents = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("-")) continue;
            Matcher m = KEY.matcher(line);
            if (!m.matches()) continue;
            int indent = m.group(1).length();
            while (!indents.isEmpty() && indents.get(indents.size() - 1) >= indent) {
                indents.remove(indents.size() - 1);
                keys.remove(keys.size() - 1);
            }
            indents.add(indent);
            keys.add(m.group(2));
            if (keys.size() != want.length) continue;
            boolean same = true;
            for (int k = 0; k < want.length; k++) if (!keys.get(k).equals(want[k])) { same = false; break; }
            if (!same) continue;
            String rest = m.group(4);
            if (rest.isEmpty() || rest.startsWith("#")) return false; // a section, not a value
            lines.set(i, m.group(1) + m.group(2) + ": " + value);
            return true;
        }
        return false;
    }

    /** Applies [values] (path, value pairs) to the file if it exists. Returns how many keys changed. */
    public static int apply(File file, String... values) throws IOException {
        if (!file.isFile()) return 0;
        List<String> lines = new ArrayList<>(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8));
        int changed = 0;
        for (int i = 0; i + 1 < values.length; i += 2) {
            if (set(lines, values[i], values[i + 1])) changed++;
        }
        if (changed > 0) {
            File tmp = new File(file.getPath() + ".tmp");
            Files.write(tmp.toPath(), lines, StandardCharsets.UTF_8);
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        return changed;
    }
}
