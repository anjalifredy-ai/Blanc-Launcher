import net.kdt.pojavlaunch.aerix.multiplayer.*;
import java.io.*;
import java.net.*;

public class MultiplayerSmokeTest {
    static void check(boolean ok, String msg) { if (!ok) throw new AssertionError(msg); }
    public static void main(String[] a) throws Exception {
        for (int port : new int[]{1024, 25565, 54321, 65535}) {
            String code = RoomCode.encode(0, port, port * 7);
            check(code.split("-").length == 5, code);
            RoomCode.Room r = RoomCode.decode(code.toUpperCase().replace('-', ' '));
            check(r != null && r.port == port && r.relayHost.equals("bore.pub"), "roundtrip " + code);
        }
        String c = RoomCode.encode(0, 40000, 3);
        String[] p = c.split("-"); p[2] = p[2].equals("amber") ? "anchor" : "amber";
        check(RoomCode.decode(String.join("-", p)) == null, "tampered code accepted");
        check(RoomCode.decode("hello world") == null, "garbage");
        check(LanPort.parse("[Server thread/INFO]: Started serving on 51234") == 51234, "modern");
        check(LanPort.parse("[Server thread/INFO]: Local game hosted on port 40111") == 40111, "old");
        check(LanPort.parse("Can't keep up!") == -1, "other");
        // proxy: echo server, cap 2
        ServerSocket echo = new ServerSocket(0);
        new Thread(() -> { try { while (true) { Socket s = echo.accept(); new Thread(() -> { try { InputStream i = s.getInputStream(); OutputStream o = s.getOutputStream(); int n; byte[] b = new byte[64]; while ((n = i.read(b)) >= 0) { o.write(b, 0, n); o.flush(); } } catch (IOException e) {} }).start(); } } catch (IOException e) {} }).start();
        CappedProxy px = new CappedProxy("127.0.0.1", echo.getLocalPort(), 2);
        int port = px.start(0);
        Socket s1 = new Socket("127.0.0.1", port), s2 = new Socket("127.0.0.1", port);
        s1.getOutputStream().write(7); check(s1.getInputStream().read() == 7, "echo 1");
        s2.getOutputStream().write(9); check(s2.getInputStream().read() == 9, "echo 2");
        Socket s3 = new Socket("127.0.0.1", port); s3.setSoTimeout(2000);
        int r3; try { r3 = s3.getInputStream().read(); } catch (IOException e) { r3 = -1; }
        check(r3 == -1, "third connection should be refused");
        px.stop();
        System.out.println("Multiplayer smoke test passed");
        System.exit(0);
    }
}
