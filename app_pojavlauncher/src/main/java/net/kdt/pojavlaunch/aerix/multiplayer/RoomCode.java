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

    private static int check(int relay, int hi, int lo, int salt) {
        return (relay * 31 + hi * 17 + lo * 7 + salt * 13 + 91) & 0xFF;
    }

    /** [salt] is any 0..255; it only makes codes for the same port look different. */
    public static String encode(int relayIndex, int port, int salt) {
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
