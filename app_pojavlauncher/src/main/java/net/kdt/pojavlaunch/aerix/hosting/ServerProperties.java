package net.kdt.pojavlaunch.aerix.hosting;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Edits server.properties in place: known keys are replaced, unknown keys and comments are left alone,
 * and keys that were missing are appended. java.util.Properties would reorder the file and drop comments.
 */
public final class ServerProperties {
    private ServerProperties() {}

    /** Sets every entry of [values] in [file], creating the file if it does not exist yet. */
    public static void apply(File file, Map<String, String> values) throws IOException {
        List<String> lines = new ArrayList<>();
        if (file.isFile()) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) lines.add(line);
            }
        }
        Map<String, String> pending = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : values.entrySet()) pending.put(e.getKey(), clean(e.getValue()));
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            int eq = trimmed.indexOf('=');
            if (eq <= 0) continue;
            String key = trimmed.substring(0, eq).trim();
            if (pending.containsKey(key)) {
                lines.set(i, key + "=" + pending.remove(key));
            }
        }
        for (Map.Entry<String, String> e : pending.entrySet()) lines.add(e.getKey() + "=" + e.getValue());
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            for (String line : lines) writer.write(line + "\n");
        }
        if (!tmp.renameTo(file)) {
            file.delete();
            if (!tmp.renameTo(file)) throw new IOException("could not write " + file);
        }
    }

    /** Reads one key, or null if the file or the key is missing. */
    public static String read(File file, String key) throws IOException {
        if (!file.isFile()) return null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith("#")) continue;
                int eq = trimmed.indexOf('=');
                if (eq > 0 && trimmed.substring(0, eq).trim().equals(key)) return trimmed.substring(eq + 1);
            }
        }
        return null;
    }

    /** A property value is one line; the MOTD may use \n written out as backslash-n. */
    private static String clean(String value) {
        if (value == null) return "";
        return value.replace("\r", "").replace("\n", "\\n");
    }
}
