package net.kdt.pojavlaunch.aerix.hosting;

import java.util.Comparator;

/** Orders Minecraft version strings ("1.21.4", "26.3", "1.8.9") numerically, newest first. */
public final class VersionOrder {
    private VersionOrder() {}

    /** True for plain release numbers; false for snapshots, pre-releases and other labels. */
    public static boolean isRelease(String v) {
        return v != null && v.matches("\\d+(\\.\\d+){1,3}");
    }

    public static int compare(String a, String b) {
        String[] x = a.split("\\.");
        String[] y = b.split("\\.");
        int n = Math.max(x.length, y.length);
        for (int i = 0; i < n; i++) {
            long p = i < x.length ? parse(x[i]) : 0;
            long q = i < y.length ? parse(y[i]) : 0;
            if (p != q) return p < q ? -1 : 1;
        }
        return 0;
    }

    private static long parse(String s) {
        try { return Long.parseLong(s.replaceAll("[^0-9]", "")); } catch (NumberFormatException e) { return 0; }
    }

    public static final Comparator<String> NEWEST_FIRST = (a, b) -> compare(b, a);
}
