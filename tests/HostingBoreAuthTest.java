import java.lang.reflect.Method;
public class HostingBoreAuthTest {
    public static void main(String[] a) throws Exception {
        Method m = Class.forName("net.kdt.pojavlaunch.aerix.hosting.BoreTunnel").getDeclaredMethod("answer", String.class, String.class);
        m.setAccessible(true);
        String tag = (String) m.invoke(null, "hunter2", "123e4567-e89b-12d3-a456-426614174000");
        System.out.println(tag);
        if (!tag.equals(System.getProperty("expect"))) throw new AssertionError("tag mismatch");
        System.out.println("Bore auth test passed");
    }
}
