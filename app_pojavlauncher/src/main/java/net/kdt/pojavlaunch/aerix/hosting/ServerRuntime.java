package net.kdt.pojavlaunch.aerix.hosting;

import android.content.Context;

import net.kdt.pojavlaunch.NewJREUtil;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.multirt.Runtime;
import net.kdt.pojavlaunch.utils.jre.RuntimeSelectionException;

import java.io.IOException;

/** Finds, or installs, the Java the server's Minecraft version needs. */
public final class ServerRuntime {
    private ServerRuntime() {}

    public static Runtime ensure(Context context, String mcVersion) throws IOException {
        int major = JavaVersions.requiredFor(mcVersion);
        String name = MultiRTUtils.getExactJreName(major);
        if (name == null) {
            for (NewJREUtil.ExternalRuntime rt : NewJREUtil.ExternalRuntime.values()) {
                if (rt.majorVersion != major) continue;
                try {
                    rt.downloadRuntime(context.getAssets());
                } catch (RuntimeSelectionException e) {
                    throw new IOException("Java " + major + " could not be installed", e);
                }
                MultiRTUtils.forceReread(rt.name);
                break;
            }
            name = MultiRTUtils.getExactJreName(major);
        }
        if (name == null) name = MultiRTUtils.getNearestJreName(major);
        if (name == null) throw new IOException("Java " + major + " is not available (needed by Minecraft " + mcVersion + ")");
        Runtime runtime = MultiRTUtils.read(name);
        if (runtime == null) throw new IOException("Java runtime " + name + " is unreadable");
        if (runtime.javaVersion < major) {
            throw new IOException("Java " + major + " is needed by Minecraft " + mcVersion + " but only Java " + runtime.javaVersion + " is installed");
        }
        return runtime;
    }
}
