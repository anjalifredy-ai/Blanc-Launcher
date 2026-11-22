package net.kdt.pojavlaunch.aerix.bedrock;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

/** Finds the user's own copy of Minecraft (Bedrock) that was installed from Google Play. */
public final class BedrockDetector {
    private BedrockDetector() {}

    public static final String PACKAGE = "com.mojang.minecraftpe";
    private static final String PLAY_STORE = "com.android.vending";

    public static final class Result {
        public final boolean installed;
        public final boolean fromPlayStore;
        public final String versionName;
        public final long versionCode;
        public final String installer;

        Result(boolean installed, boolean fromPlayStore, String versionName, long versionCode, String installer) {
            this.installed = installed;
            this.fromPlayStore = fromPlayStore;
            this.versionName = versionName;
            this.versionCode = versionCode;
            this.installer = installer;
        }

        /** Bedrock mode is available only for a Play Store copy. */
        public boolean allowed() {
            return installed && fromPlayStore;
        }
    }

    @SuppressWarnings("deprecation")
    public static Result detect(Context context) {
        PackageManager pm = context.getPackageManager();
        PackageInfo info;
        try {
            info = pm.getPackageInfo(PACKAGE, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return new Result(false, false, "", 0, null);
        }
        String installer = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                installer = pm.getInstallSourceInfo(PACKAGE).getInstallingPackageName();
            } else {
                installer = pm.getInstallerPackageName(PACKAGE);
            }
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
            // Unknown installer is treated as "not from Play".
        }
        long code = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? info.getLongVersionCode() : info.versionCode;
        return new Result(true, PLAY_STORE.equals(installer), info.versionName == null ? "" : info.versionName, code, installer);
    }
}
