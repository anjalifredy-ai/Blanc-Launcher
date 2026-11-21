import net.kdt.pojavlaunch.aerix.hosting.*;
import java.io.File;
import java.nio.file.Files;
import java.util.List;

public class HostingStoreSmokeTest {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
    public static void main(String[] a) throws Exception {
        File root = Files.createTempDirectory("store").toFile();
        ServerStore store = new ServerStore(root);
        HostedServer one = store.create("Alpha", ServerSoftware.PAPER, "1.21.4", true);
        HostedServer two = store.create("Beta", ServerSoftware.FABRIC, "1.16.5", false);
        check(one.frontPort != two.frontPort && one.privatePort != two.privatePort, "ports unique");
        check(one.ramMb == 2048 && two.ramMb == 1536, "ram default by java version");
        two.onlineMode = false; two.description = "line1\nline2"; two.seed = "abc"; two.installed = true; two.publicPort = 40000;
        store.save(two);
        List<HostedServer> all = store.list();
        check(all.size() == 2 && all.get(0).name.equals("Alpha"), "list order");
        HostedServer back = store.get(two.id);
        check(!back.onlineMode && back.software == ServerSoftware.FABRIC && back.installed && back.publicPort == 40000 && back.seed.equals("abc"), "round trip");
        File work = store.dir(one.id);
        store.prepare(one, work);
        String props = new String(Files.readAllBytes(new File(work, "server.properties").toPath()));
        check(props.contains("online-mode=true") && props.contains("server-ip=127.0.0.1") && props.contains("server-port=" + one.privatePort), "props: " + props);
        check(new String(Files.readAllBytes(new File(work, "eula.txt").toPath())).contains("eula=true"), "eula");
        try { store.prepare(two, store.dir(two.id)); check(false, "eula must be required"); } catch (java.io.IOException expected) {}
        store.saveLogo(one.id, new byte[]{1, 2, 3});
        check(store.faviconDataUri(one.id).startsWith("data:image/png;base64,"), "favicon");
        store.installLogo(one.id, work);
        check(new File(work, "server-icon.png").isFile(), "logo installed");
        store.delete(one.id);
        check(store.list().size() == 1 && !work.exists(), "delete");
        System.out.println("Hosting store smoke test passed");
    }
}
