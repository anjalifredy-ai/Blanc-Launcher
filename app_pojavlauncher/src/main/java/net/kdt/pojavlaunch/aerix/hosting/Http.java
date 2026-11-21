package net.kdt.pojavlaunch.aerix.hosting;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Small HTTP helper for the hosting downloads: text GET and verified file download with progress. */
final class Http {
    private Http() {}

    interface Progress { void onBytes(long done, long total); }

    private static final String UA = "AerixHost/1.0 (+https://github.com/anjalifredy-ai/Blanc-Launcher)";

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept", "application/json, */*");
        int code = c.getResponseCode();
        if (code / 100 != 2) {
            c.disconnect();
            throw new IOException("HTTP " + code + " from " + url);
        }
        return c;
    }

    static String get(String url) throws IOException {
        HttpURLConnection c = open(url);
        try (InputStream in = c.getInputStream()) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > 32 * 1024 * 1024) throw new IOException("response too large: " + url);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            c.disconnect();
        }
    }

    /** Downloads to [dest] via a temp file. [sha1] or [sha256] (either may be null) is verified when given. */
    static void download(String url, File dest, String sha1, String sha256, Progress progress) throws IOException {
        File tmp = new File(dest.getParentFile(), dest.getName() + ".part");
        HttpURLConnection c = open(url);
        try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(tmp)) {
            long total = c.getContentLengthLong();
            MessageDigest d1 = digest("SHA-1");
            MessageDigest d256 = digest("SHA-256");
            byte[] buf = new byte[64 * 1024];
            long done = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                d1.update(buf, 0, n);
                d256.update(buf, 0, n);
                done += n;
                if (progress != null) progress.onBytes(done, total);
            }
            out.getFD().sync();
            if (sha1 != null && !sha1.equalsIgnoreCase(hex(d1.digest()))) throw new IOException("SHA-1 mismatch for " + dest.getName());
            if (sha256 != null && !sha256.equalsIgnoreCase(hex(d256.digest()))) throw new IOException("SHA-256 mismatch for " + dest.getName());
        } catch (IOException e) {
            tmp.delete();
            throw e;
        } finally {
            c.disconnect();
        }
        if (dest.exists() && !dest.delete()) throw new IOException("could not replace " + dest);
        if (!tmp.renameTo(dest)) throw new IOException("could not move download into place: " + dest);
    }

    private static MessageDigest digest(String algorithm) throws IOException {
        try { return MessageDigest.getInstance(algorithm); } catch (java.security.NoSuchAlgorithmException e) { throw new IOException(e); }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
