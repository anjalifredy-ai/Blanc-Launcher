package com.blanc.mod;

import java.util.Locale;

/** Same short code as the launcher: 5 symbols = relay(2) + port(16) + check(7). */
public final class BlancCode {
    private static final String[] RELAYS = {"bore.pub"};
    private static final String ALPHA = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    /** Returns "host:port" for "1A4S6", "name-1A4S6.blanc" or "name.1A4S6.blanc"; null if it is not a Blanc address. */
    public static String resolve(String text) {
        if (text == null) return null;
        String t = text.trim();
        if (t.toLowerCase(Locale.ROOT).endsWith(".blanc")) {
            t = t.substring(0, t.length() - 6);
            int i = Math.max(t.lastIndexOf('-'), t.lastIndexOf('.'));
            if (i >= 0) t = t.substring(i + 1);
        }
        t = t.toUpperCase(Locale.ROOT).replaceAll("[\\s\\-_.,]", "").replace('O', '0').replace('I', '1').replace('L', '1').replace('U', 'V');
        if (t.length() != 5) return null;
        long v = 0;
        for (int i = 0; i < 5; i++) {
            int k = ALPHA.indexOf(t.charAt(i));
            if (k < 0) return null;
            v = (v << 5) | k;
        }
        int relay = (int) (v >> 23) & 3, port = (int) (v >> 7) & 0xFFFF, chk = (int) (v & 0x7F);
        if (port < 1 || relay >= RELAYS.length) return null;
        int expect = (relay * 53 + (port >> 8) * 31 + (port & 0xFF) * 17 + 29) & 0x7F;
        if (chk != expect) return null;
        return RELAYS[relay] + ":" + port;
    }

    private BlancCode() {}
}
