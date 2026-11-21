import net.kdt.pojavlaunch.aerix.hosting.JavaVersions;
import net.kdt.pojavlaunch.aerix.hosting.VersionOrder;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Offline checks of version ordering, Java selection and catalog parsing (fixtures written from the APIs' documented shapes). */
public class HostingCatalogSmokeTest {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    static Object call(String name, Class<?>[] types, Object... args) throws Exception {
        Class<?> c = Class.forName("net.kdt.pojavlaunch.aerix.hosting.ServerCatalog");
        Method m = c.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    public static void main(String[] args) throws Exception {
        check(VersionOrder.compare("1.21.4", "1.21.10") < 0, "numeric not lexical");
        check(VersionOrder.compare("26.3", "1.21.11") > 0, "year scheme is newer");
        check(VersionOrder.compare("1.21", "1.21.0") == 0, "missing patch equals zero");
        check(VersionOrder.isRelease("1.21.4") && !VersionOrder.isRelease("1.21-pre1") && !VersionOrder.isRelease("24w14a"), "release filter");
        List<String> sorted = new java.util.ArrayList<>(Arrays.asList("1.20.1", "1.21.10", "1.21.4", "26.3", "1.12.2"));
        java.util.Collections.sort(sorted, VersionOrder.NEWEST_FIRST);
        check(sorted.equals(Arrays.asList("26.3", "1.21.10", "1.21.4", "1.20.1", "1.12.2")), "sorted: " + sorted);

        check(JavaVersions.requiredFor("1.12.2") == 8 && JavaVersions.requiredFor("1.16.5") == 8, "java 8");
        check(JavaVersions.requiredFor("1.17.1") == 17 && JavaVersions.requiredFor("1.20.4") == 17 && JavaVersions.requiredFor("1.20") == 17, "java 17");
        check(JavaVersions.requiredFor("1.20.5") == 21 && JavaVersions.requiredFor("1.20.6") == 21 && JavaVersions.requiredFor("1.21.4") == 21, "java 21");
        check(JavaVersions.requiredFor("26.1") == 25 && JavaVersions.requiredFor("26.3") == 25, "java 25");

        Class<?>[] s = {String.class};
        List<String> paper = (List<String>) call("parsePaperVersions", s, "{\"project\":{},\"versions\":{\"1.21\":[\"1.21.8\",\"1.21.7\"],\"1.20\":[\"1.20.6\"]}}");
        check(paper.equals(Arrays.asList("1.21.8", "1.21.7", "1.20.6")), "paper grouped versions: " + paper);
        check(((List<String>) call("parsePaperVersions", s, "{\"versions\":[\"1.21.8\",\"1.21.7\"]}")).size() == 2, "paper flat versions");

        Object art = call("parsePaperBuild", s, "{\"id\":60,\"channel\":\"STABLE\",\"downloads\":{\"server:default\":{\"name\":\"paper-1.21.8-60.jar\",\"checksums\":{\"sha256\":\"abc\"},\"size\":1,\"url\":\"https://fill-data.papermc.io/v1/objects/x/paper-1.21.8-60.jar\"}}}");
        java.lang.reflect.Field url = art.getClass().getDeclaredField("url");
        java.lang.reflect.Field sha = art.getClass().getDeclaredField("sha256");
        check("https://fill-data.papermc.io/v1/objects/x/paper-1.21.8-60.jar".equals(url.get(art)) && "abc".equals(sha.get(art)), "paper artifact");
        try { call("parsePaperBuild", s, "{\"downloads\":{}}"); check(false, "missing download must throw"); } catch (java.lang.reflect.InvocationTargetException expected) { check(expected.getCause() instanceof java.io.IOException, "IOException"); }

        List<String> mojang = (List<String>) call("parseMojangVersions", s, "{\"versions\":[{\"id\":\"1.21.4\",\"type\":\"release\",\"url\":\"u1\"},{\"id\":\"24w14a\",\"type\":\"snapshot\",\"url\":\"u2\"}]}");
        check(mojang.equals(Arrays.asList("1.21.4")), "mojang releases only");
        check("u1".equals(call("parseMojangVersionUrl", new Class<?>[]{String.class, String.class}, "{\"versions\":[{\"id\":\"1.21.4\",\"type\":\"release\",\"url\":\"u1\"}]}", "1.21.4")), "mojang url");

        List<String> fabric = (List<String>) call("parseFabricGameVersions", s, "[{\"version\":\"1.21.5\",\"stable\":true},{\"version\":\"25w10a\",\"stable\":false}]");
        check(fabric.equals(Arrays.asList("1.21.5")), "fabric stable only");
        check("0.16.10".equals(call("firstStable", new Class<?>[]{String.class, String.class}, "[{\"version\":\"0.17.0-beta\",\"stable\":false},{\"version\":\"0.16.10\",\"stable\":true}]", "version")), "first stable loader");

        Map<String, String> promos = (Map<String, String>) call("parsePromos", s, "{\"homepage\":\"x\",\"promos\":{\"1.20.1-latest\":\"47.4.0\",\"1.20.1-recommended\":\"47.3.0\",\"1.21.4-latest\":\"54.0.1\",\"1.7.10_pre4-latest\":\"1\"}}");
        check("47.3.0".equals(promos.get("1.20.1")) && "54.0.1".equals(promos.get("1.21.4")), "forge prefers recommended: " + promos);

        Map<String, String> neo = (Map<String, String>) call("parseNeoForgeMetadata", s,
                "<metadata><versioning><versions><version>20.4.237</version><version>21.0.5</version><version>21.1.77</version><version>21.1.100</version><version>21.4.0-beta</version><version>21.4.111</version><version>26.1.0.1</version></versions></versioning></metadata>");
        check("21.1.100".equals(neo.get("1.21.1")) && "21.0.5".equals(neo.get("1.21")) && "20.4.237".equals(neo.get("1.20.4")) && "21.4.111".equals(neo.get("1.21.4")), "neoforge mapping: " + neo);
        check(!neo.containsKey("1.26") && neo.size() == 4, "beta and unknown scheme skipped: " + neo);

        System.out.println("Hosting catalog smoke test passed");
    }
}
