import net.kdt.pojavlaunch.aerix.hosting.*;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.List;

public class HostingFilesSmokeTest {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
    static Object call(String n, Class<?>[] t, Object... a) throws Exception {
        Method m = Class.forName("net.kdt.pojavlaunch.aerix.hosting.Modrinth").getDeclaredMethod(n, t);
        m.setAccessible(true);
        return m.invoke(null, a);
    }
    public static void main(String[] x) throws Exception {
        File root = Files.createTempDirectory("files").toFile();
        ServerFiles.mkdir(root, "", "plugins");
        ServerFiles.importStream(root, "plugins", "a.jar", new ByteArrayInputStream(new byte[]{1, 2}));
        ServerFiles.writeText(root, "server.properties", "motd=hi\n");
        check(ServerFiles.readText(root, "server.properties").equals("motd=hi\n"), "read/write");
        List<ServerFiles.Entry> list = ServerFiles.list(root, "");
        check(list.get(0).name.equals("plugins") && list.get(0).directory && list.size() == 2, "folders first");
        for (String bad : new String[]{"../x", "plugins/../../x"}) {
            try { ServerFiles.resolve(root, bad); check(false, "must reject " + bad); } catch (IOException expected) {}
        }
        try { ServerFiles.safeName("../evil"); check(false, "name"); } catch (IOException expected) {}
        check(ServerFiles.isEditable("server.properties", 10) && ServerFiles.isEditable("config.yml", 10) && !ServerFiles.isEditable("a.jar", 10) && !ServerFiles.isEditable("big.txt", 10_000_000), "editable");
        ServerFiles.delete(root, "plugins");
        check(!new File(root, "plugins").exists(), "delete");
        try { ServerFiles.delete(root, ""); check(false, "root delete"); } catch (IOException expected) {}
        check(ServerFiles.contentDir(root, ServerSoftware.PAPER).getName().equals("plugins") && ServerFiles.contentDir(root, ServerSoftware.FABRIC).getName().equals("mods"), "content dir");

        Class<?>[] s = {String.class};
        List<?> hits = (List<?>) call("parseSearch", s, "{\"hits\":[{\"project_id\":\"AAA\",\"title\":\"EssentialsX\",\"description\":\"d\",\"author\":\"me\",\"downloads\":5}],\"total_hits\":1}");
        check(hits.size() == 1, "search parse");
        Object info = call("parseVersions", s, "[{\"version_type\":\"beta\",\"files\":[{\"url\":\"u0\",\"filename\":\"b.jar\",\"primary\":true,\"hashes\":{\"sha1\":\"h0\"}}],\"dependencies\":[]},"
                + "{\"version_type\":\"release\",\"files\":[{\"url\":\"u1\",\"filename\":\"x-1.0.jar\",\"primary\":false},{\"url\":\"u2\",\"filename\":\"x-1.0-main.jar\",\"primary\":true,\"hashes\":{\"sha1\":\"h2\"}}],"
                + "\"dependencies\":[{\"project_id\":\"P7dR8mSH\",\"dependency_type\":\"required\"},{\"project_id\":\"OPT\",\"dependency_type\":\"optional\"}]}]");
        Class<?> c = info.getClass();
        java.lang.reflect.Field url = c.getDeclaredField("url"); url.setAccessible(true);
        java.lang.reflect.Field deps = c.getDeclaredField("requiredProjects"); deps.setAccessible(true);
        check("u2".equals(url.get(info)) && ((List<?>) deps.get(info)).size() == 1, "prefers release + primary file + required deps only");
        String u = (String) call("searchUrl", new Class<?>[]{ServerSoftware.class, String.class, String.class}, ServerSoftware.PAPER, "1.21.11", "essentials");
        check(u.contains("project_type%3Aplugin") && u.contains("versions%3A1.21.11") && u.contains("categories%3Apaper"), "facets: " + u);
        String u2 = (String) call("searchUrl", new Class<?>[]{ServerSoftware.class, String.class, String.class}, ServerSoftware.FABRIC, "1.21.11", "");
        check(u2.contains("project_type%3Amod") && u2.contains("index=downloads"), "mod facets: " + u2);
        System.out.println("Hosting files smoke test passed");
    }
}
