package net.kdt.pojavlaunch.aerix.multiplayer;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A Blanc world code: five words that hold the relay and the public port of a shared world.
 * Bytes: [relay][port high][port low][salt][check]. No server is needed to look a code up.
 */
public final class RoomCode {
    /** Index in this table is stored in the code. Only ever append; old codes keep working. */
    public static final String[] RELAYS = {"bore.pub"};

    private static final String[] WORDS = (""
            + "amber anchor apple arrow aspen atlas azure badge baker banjo basil beach beam bear "
            + "bell berry birch bison blaze bloom boat bolt bone brave bread brick bridge brook cabin "
            + "cacti camel candy canoe cargo cedar chalk charm cherry chess chief cider cliff cloud "
            + "clove cobra comet coral cosmo crane creek crown crystal daisy dance delta denim diver "
            + "dock dolphin dove dragon dream drift drum eagle earth ember epic fable fairy falcon "
            + "fern fiddle field flame flint flute forest fox frost galaxy garden gecko ghost giant "
            + "glade globe gold grape grove guitar harbor hawk hazel heart helm heron hill honey "
            + "horse hotel icicle igloo iris island ivory jade jelly jewel jolly jungle kayak kernel "
            + "kite koala lagoon lake lantern lava lemon lilac lion llama lotus lucky lunar magic "
            + "mango maple marble meadow melon mint mirror moon moss motor mount nacho navy nectar "
            + "nest night noble north novel oasis ocean olive onyx opal orbit otter oven owl paint "
            + "palm panda paper peach pearl pebble penguin pepper piano pilot pine pixel planet plum "
            + "polar pond poppy prism pulse puma quail quartz queen quest quiet quill rabbit radar "
            + "rain raven reef ridge river robin rocket rose ruby saddle sage sail salmon sand scarf "
            + "shadow shell shore silver sky slate snow solar sonic spark spice spring star stone "
            + "storm sugar summer sunny swan tango tiger timber toast torch tower trail tulip tundra "
            + "turtle twist umber unity valley velvet violet voyage wagon walnut water wave whale "
            + "willow wind winter wolf wonder yacht yarrow zebra zephyr zinc yeti zest wren vale tide "
            + "dune").split(" ");
    private static final Map<String, Integer> INDEX = new HashMap<>();
    static {
        for (int i = 0; i < WORDS.length; i++) INDEX.put(WORDS[i], i);
    }

    public static final class Room {
        public final String relayHost;
        public final int port;
        Room(String relayHost, int port) { this.relayHost = relayHost; this.port = port; }
    }

    private RoomCode() {}

    /** A realm address is "name-1A4S6.blanc": the code rides after the last dash. */
    public static String address(String name, String code) {
        String n = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
        if (n.isEmpty()) n = "realm";
        if (n.length() > 16) n = n.substring(0, 16);
        return n + "-" + code.toLowerCase(Locale.ROOT) + ".blanc";
    }

    /** Turns "name-1A4S6.blanc" (any case) into "1A4S6"; leaves other text untouched. */
    public static String realmToCode(String text) {
        String t = text.trim();
        if (!t.toLowerCase(Locale.ROOT).endsWith(".blanc")) return t;
        t = t.substring(0, t.length() - 6);
        int i = t.lastIndexOf('-');
        return i >= 0 ? t.substring(i + 1) : t;
    }

    private static int check(int relay, int hi, int lo, int salt) {
        return (relay * 31 + hi * 17 + lo * 7 + salt * 13 + 91) & 0xFF;
    }

    private static final String ALPHA = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    /** Short code like "1A4S6": 5 symbols x 5 bits = relay(2) + port(16) + check(7). */
    public static String encode(int relayIndex, int port, int salt) {
        if (relayIndex < 0 || relayIndex >= Math.min(4, RELAYS.length)) throw new IllegalArgumentException("relay");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("port");
        long v = ((long) relayIndex << 23) | ((long) port << 7) | shortCheck(relayIndex, port);
        char[] c = new char[5];
        for (int i = 4; i >= 0; i--) { c[i] = ALPHA.charAt((int) (v & 31)); v >>= 5; }
        return new String(c);
    }

    private static int shortCheck(int relay, int port) {
        return (relay * 53 + (port >> 8) * 31 + (port & 0xFF) * 17 + 29) & 0x7F;
    }

    private static Room decodeShort(String text) {
        String t = text.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s\\-_.,]", "")
                .replace('O', '0').replace('I', '1').replace('L', '1').replace('U', 'V');
        if (t.length() != 5) return null;
        long v = 0;
        for (int i = 0; i < 5; i++) {
            int k = ALPHA.indexOf(t.charAt(i));
            if (k < 0) return null;
            v = (v << 5) | k;
        }
        int relay = (int) (v >> 23) & 3, port = (int) (v >> 7) & 0xFFFF, chk = (int) (v & 0x7F);
        if (port < 1 || relay >= RELAYS.length || chk != shortCheck(relay, port)) return null;
        return new Room(RELAYS[relay], port);
    }

    /** Old five-word codes. */
    public static String encodeWords(int relayIndex, int port, int salt) {
        if (relayIndex < 0 || relayIndex >= RELAYS.length) throw new IllegalArgumentException("relay");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("port");
        int hi = port >> 8, lo = port & 0xFF, s = salt & 0xFF;
        int[] b = {relayIndex, hi, lo, s, check(relayIndex, hi, lo, s)};
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < b.length; i++) {
            if (i > 0) out.append('-');
            out.append(WORDS[b[i]]);
        }
        return out.toString();
    }

    /** Accepts spaces, dashes, commas and any capitalisation. Returns null for anything that is not a valid code. */
    public static Room decode(String text) {
        if (text == null) return null;
        text = realmToCode(text);
        Room quick = decodeShort(text);
        if (quick != null) return quick;
        String[] parts = text.trim().toLowerCase(Locale.ROOT).split("[\\s,.\\-_]+");
        if (parts.length != 5) return null;
        int[] b = new int[5];
        for (int i = 0; i < 5; i++) {
            Integer v = INDEX.get(parts[i]);
            if (v == null) return null;
            b[i] = v;
        }
        if (b[4] != check(b[0], b[1], b[2], b[3])) return null;
        if (b[0] >= RELAYS.length) return null;
        int port = (b[1] << 8) | b[2];
        if (port < 1) return null;
        return new Room(RELAYS[b[0]], port);
    }
}
