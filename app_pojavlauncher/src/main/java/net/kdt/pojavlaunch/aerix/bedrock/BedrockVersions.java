package net.kdt.pojavlaunch.aerix.bedrock;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * The player's own Bedrock builds. A build can only be added from a file the player already has (an
 * APK, or a bundle of split APKs), and only if it is signed by the same key as the Google Play copy on
 * this phone, so only genuine Mojang builds of a game the player owns are accepted.
 */
public final class BedrockVersions {
    private BedrockVersions() {}

    public static final class Version {
        public final String id;
        public final String versionName;
        public final long versionCode;
        public final File dir;
        public final long sizeBytes;

        Version(String id, String versionName, long versionCode, File dir, long sizeBytes) {
            this.id = id;
            this.versionName = versionName;
            this.versionCode = versionCode;
            this.dir = dir;
            this.sizeBytes = sizeBytes;
        }
    }

    public static File root(Context context) {
        return new File(context.getFilesDir(), "bedrock/versions");
    }

    public static List<Version> list(Context context) {
        List<Version> out = new ArrayList<>();
        File[] dirs = root(context).listFiles(File::isDirectory);
        if (dirs == null) return out;
        for (File d : dirs) {
            try {
                JsonObject o = new JsonParser().parse(new String(Files.readAllBytes(new File(d, "version.json").toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
                long size = 0;
                File[] files = d.listFiles();
                if (files != null) for (File f : files) size += f.length();
                out.add(new Version(d.getName(), o.get("versionName").getAsString(), o.get("versionCode").getAsLong(), d, size));
            } catch (IOException | RuntimeException ignored) {
                // A half-imported folder is skipped; import cleans up after itself.
            }
        }
        Collections.sort(out, (a, b) -> Long.compare(b.versionCode, a.versionCode));
        return out;
    }

    public static void delete(Version v) throws IOException {
        File[] files = v.dir.listFiles();
        if (files != null) for (File f : files) if (!f.delete()) throw new IOException("Could not delete " + f.getName());
        if (!v.dir.delete()) throw new IOException("Could not delete " + v.dir.getName());
    }

    /**
     * Keeps a copy of the Minecraft that is installed from Google Play right now, so the player still has this
     * version after Play updates the app. It is the player's own installed copy, copied on the same phone.
     */
    public static Version saveInstalled(Context context) throws IOException {
        BedrockDetector.Result installed = BedrockDetector.detect(context);
        if (!installed.allowed()) throw new IOException("Install Minecraft from Google Play first");
        android.content.pm.ApplicationInfo app;
        try {
            app = context.getPackageManager().getApplicationInfo(BedrockDetector.PACKAGE, 0);
        } catch (PackageManager.NameNotFoundException e) {
            throw new IOException("Minecraft is not installed");
        }
        String id = String.valueOf(installed.versionCode);
        File dest = new File(root(context), id);
        if (dest.exists()) throw new IOException("Version " + installed.versionName + " is already saved");
        if (!dest.mkdirs()) throw new IOException("Could not create the version folder");
        try {
            copy(new java.io.FileInputStream(app.sourceDir), new File(dest, "base.apk"));
            if (app.splitSourceDirs != null) {
                for (String split : app.splitSourceDirs) {
                    copy(new java.io.FileInputStream(split), new File(dest, new File(split).getName()));
                }
            }
            JsonObject meta = new JsonObject();
            meta.addProperty("versionName", installed.versionName);
            meta.addProperty("versionCode", installed.versionCode);
            Files.write(new File(dest, "version.json").toPath(), meta.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            File[] left = dest.listFiles();
            if (left != null) for (File f : left) f.delete();
            dest.delete();
            throw e;
        }
        return new Version(id, installed.versionName, installed.versionCode, dest, 0);
    }

    /**
     * Saves the installed Play Store Minecraft when this exact version is not saved yet. Returns the new
     * version, or null when nothing was needed. Called when the launcher opens and when Play updates the app,
     * so every version the launcher gets to see is kept without the player pressing anything.
     */
    public static synchronized Version autoSave(Context context) {
        try {
            BedrockDetector.Result installed = BedrockDetector.detect(context);
            if (!installed.allowed()) return null;
            for (Version v : list(context)) if (v.versionCode == installed.versionCode) return null;
            return saveInstalled(context);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Imports a .apk, or a bundle (.xapk / .apks / .zip) holding the split APKs. */
    public static Version importFrom(Context context, Uri uri) throws IOException {
        BedrockDetector.Result installed = BedrockDetector.detect(context);
        if (!installed.allowed()) throw new IOException("Install Minecraft from Google Play first");
        File work = new File(context.getCacheDir(), "bedrock-import-" + System.nanoTime());
        if (!work.mkdirs()) throw new IOException("Could not create a work folder");
        try {
            List<File> apks = new ArrayList<>();
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IOException("Could not open the file");
                java.io.BufferedInputStream buffered = new java.io.BufferedInputStream(in);
                buffered.mark(8);
                int a = buffered.read(), b = buffered.read();
                buffered.reset();
                if (a != 'P' || b != 'K') throw new IOException("That is not an APK or a bundle of APKs");
                // An APK is also a zip, so look inside: a bundle holds .apk files, an APK holds AndroidManifest.xml.
                File whole = new File(work, "import.bin");
                copy(buffered, whole);
                if (hasEntry(whole, "AndroidManifest.xml")) {
                    File apk = new File(work, "base.apk");
                    if (!whole.renameTo(apk)) throw new IOException("Could not read the APK");
                    apks.add(apk);
                } else {
                    apks.addAll(extractApks(whole, work));
                    //noinspection ResultOfMethodCallIgnored
                    whole.delete();
                }
            }
            if (apks.isEmpty()) throw new IOException("No APK was found in that file");

            File base = null;
            PackageInfo baseInfo = null;
            for (File apk : apks) {
                PackageInfo info = inspect(context, apk);
                if (info != null && BedrockDetector.PACKAGE.equals(info.packageName) && !isSplit(info)) {
                    base = apk;
                    baseInfo = info;
                    break;
                }
            }
            if (base == null) throw new IOException("This is not a Minecraft (Bedrock) APK");
            if (!sameSigner(context, base, baseInfo)) {
                throw new IOException("This build is not signed by the same key as the Minecraft from Google Play on this phone. Only genuine builds are accepted.");
            }
            long code = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? baseInfo.getLongVersionCode() : baseInfo.versionCode;
            String name = baseInfo.versionName == null ? String.valueOf(code) : baseInfo.versionName;
            String id = String.valueOf(code);
            File dest = new File(root(context), id);
            if (dest.exists()) throw new IOException("Version " + name + " is already imported");
            if (!dest.mkdirs()) throw new IOException("Could not create the version folder");
            try {
                for (File apk : apks) {
                    File target = new File(dest, apk == base ? "base.apk" : apk.getName());
                    if (!apk.renameTo(target)) copy(new java.io.FileInputStream(apk), target);
                }
                JsonObject meta = new JsonObject();
                meta.addProperty("versionName", name);
                meta.addProperty("versionCode", code);
                Files.write(new File(dest, "version.json").toPath(), meta.toString().getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                File[] left = dest.listFiles();
                if (left != null) for (File f : left) f.delete();
                dest.delete();
                throw e;
            }
            return new Version(id, name, code, dest, 0);
        } finally {
            File[] left = work.listFiles();
            if (left != null) for (File f : left) f.delete();
            work.delete();
        }
    }

    private static boolean isSplit(PackageInfo info) {
        return info.applicationInfo != null && info.applicationInfo.splitNames != null && info.applicationInfo.splitNames.length > 0
                || (info.splitNames != null && info.splitNames.length > 0);
    }

    @SuppressWarnings("deprecation")
    private static PackageInfo inspect(Context context, File apk) {
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        return context.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), flags);
    }

    @SuppressWarnings("deprecation")
    private static boolean sameSigner(Context context, File apk, PackageInfo apkInfo) {
        try {
            Set<String> wanted = digests(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? context.getPackageManager().getPackageInfo(BedrockDetector.PACKAGE, PackageManager.GET_SIGNING_CERTIFICATES)
                    : context.getPackageManager().getPackageInfo(BedrockDetector.PACKAGE, PackageManager.GET_SIGNATURES));
            Set<String> got = digests(apkInfo);
            return !got.isEmpty() && wanted.containsAll(got);
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private static Set<String> digests(PackageInfo info) {
        Set<String> out = new HashSet<>();
        Signature[] sigs;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            sigs = info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners();
        } else {
            sigs = info.signatures;
        }
        if (sigs == null) return out;
        try {
            for (Signature s : sigs) {
                byte[] d = MessageDigest.getInstance("SHA-256").digest(s.toByteArray());
                StringBuilder sb = new StringBuilder();
                for (byte b : d) sb.append(String.format("%02x", b));
                out.add(sb.toString());
            }
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    private static boolean hasEntry(File zip, String name) throws IOException {
        try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(zip)) {
            return z.getEntry(name) != null;
        }
    }

    /** Only *.apk entries are taken, by file name alone, so a bundle can never write outside the work folder. */
    private static List<File> extractApks(File bundle, File work) throws IOException {
        List<File> out = new ArrayList<>();
        try (ZipInputStream zin = new ZipInputStream(new java.io.BufferedInputStream(new java.io.FileInputStream(bundle)))) {
            ZipEntry e;
            int n = 0;
            while ((e = zin.getNextEntry()) != null) {
                String name = e.getName();
                if (e.isDirectory() || !name.toLowerCase(java.util.Locale.ROOT).endsWith(".apk")) continue;
                String leaf = name.substring(name.lastIndexOf('/') + 1);
                File target = new File(work, (n++) + "-" + leaf.replaceAll("[^A-Za-z0-9._-]", "_"));
                copy(zin, target, false);
                out.add(target);
            }
        }
        return out;
    }

    private static void copy(InputStream in, File target) throws IOException {
        copy(in, target, true);
    }

    private static void copy(InputStream in, File target, boolean close) throws IOException {
        try (FileOutputStream out = new FileOutputStream(target)) {
            byte[] buf = new byte[256 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } finally {
            if (close) in.close();
        }
    }
}
