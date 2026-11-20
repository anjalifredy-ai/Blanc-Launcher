package aerix.client.mod;

import net.fabricmc.api.ClientModInitializer;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Lightweight, zero-dependency Fabric client mod for Aerix Client (1.16.5 - 1.21.4+):
 * 1. Cycles animated cape frames onto the player's OpenGL DynamicTexture
 * 2. Hot-reloads WaveyCapes physics config
 * 3. Hot-applies live client runtime options (Fullbright, ZOOM, Server Boost)
 * 4. Live-applies resource / shader packs without restart (ClientPackLiveApply)
 */
public final class AerixClientMod implements ClientModInitializer {

    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    private Class<?> clsMinecraftClient;
    private Method mGetInstance;
    private Method mGetTextureManager;
    private Field fPlayer;
    private Field fRunDirectory;
    private Field fOptions;

    private Class<?> clsAbstractPlayer;
    private Method mGetCapeLegacy;
    private Method mGetSkinTextures;
    private Method mSkinCompCape;

    private Class<?> clsIdentifier;
    private Class<?> clsAbstractTexture;
    private Class<?> clsDynamicTexture;
    private Method mDynUpload;
    private Method mDynGetImage;

    private Class<?> clsTextureManager;
    private Method mTexRegister;
    private Method mTexGet;

    private Class<?> clsNativeImage;
    private Method mNativeRead;
    private Method mNativeCopyFrom;

    private boolean handlesResolved = false;
    private boolean modernRuntime = false;
    private ModernCapeBridge modernCapeBridge;
    private final ClientVisualOverrides visualOverrides = new ClientVisualOverrides();
    private final ClientAdaptiveDistance adaptiveDistance = new ClientAdaptiveDistance();
    private final ClientLiveOptions liveOptions = new ClientLiveOptions();
    private final ClientPackLiveApply packLiveApply = new ClientPackLiveApply();
    private String capeStatus = "waiting for the game";
    private long lastStatusWrite;

    private final List<byte[]> framePngBytes = new ArrayList<byte[]>();
    private final List<Object> frameNativeImages = new ArrayList<Object>();
    private long lastCapeMetaMtime = -1L;
    private long lastWaveyMtime = -1L;
    private int frameIndex = 0;
    private int slowCounter = 0;

    @Override
    public void onInitializeClient() {
        if (!STARTED.compareAndSet(false, true)) return;
        ClientTelemetry.start();
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                runLoop();
            }
        }, "Aerix-Client-Animator");
        worker.setDaemon(true);
        worker.start();
    }

    private void runLoop() {
        final AtomicBoolean renderPending = new AtomicBoolean(false);
        long nextResolveAttempt = 0L;
        while (true) {
            try {
                Thread.sleep(95L);
                long now = System.currentTimeMillis();
                if (!handlesResolved && now >= nextResolveAttempt) {
                    resolveHandles();
                    nextResolveAttempt = now + 2000L;
                }
                if (mGetInstance == null) continue;
                final Object mc = mGetInstance.invoke(null);
                if (!(mc instanceof Executor)) continue;
                if (!renderPending.compareAndSet(false, true)) continue;

                ((Executor) mc).execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            onRenderThreadTick(mc);
                        } catch (Throwable ignored) {
                        } finally {
                            renderPending.set(false);
                        }
                    }
                });
            } catch (Throwable ignored) {
            }
        }
    }

    private boolean resolveHandles() {
        try {
            ClassLoader cl = AerixClientMod.class.getClassLoader();
            try {
                clsMinecraftClient = Class.forName("net.minecraft.class_310", false, cl);
            } catch (ClassNotFoundException oldNamesUnavailable) {
                clsMinecraftClient = Class.forName("net.minecraft.client.Minecraft", false, cl);
                mGetInstance = clsMinecraftClient.getDeclaredMethod("getInstance");
                mGetInstance.setAccessible(true);
                fRunDirectory = findFieldSafe(clsMinecraftClient, "gameDirectory");
                modernRuntime = true;
                handlesResolved = true;
                return true;
            }
            mGetInstance = clsMinecraftClient.getDeclaredMethod("method_1551");
            mGetInstance.setAccessible(true);
            mGetTextureManager = clsMinecraftClient.getDeclaredMethod("method_1531");
            mGetTextureManager.setAccessible(true);
            fPlayer = findFieldSafe(clsMinecraftClient, "field_1724");
            fRunDirectory = findFieldSafe(clsMinecraftClient, "field_1697");
            fOptions = findFieldSafe(clsMinecraftClient, "field_1690");

            clsAbstractPlayer = Class.forName("net.minecraft.class_742", false, cl);
            mGetCapeLegacy = findMethodSafe(clsAbstractPlayer, "method_3119");
            mGetSkinTextures = findMethodSafe(clsAbstractPlayer, "method_52814");
            if (mGetSkinTextures != null) {
                Class<?> clsSkinTex = mGetSkinTextures.getReturnType();
                mSkinCompCape = findMethodSafe(clsSkinTex, "comp_1627");
                if (mSkinCompCape == null) {
                    mSkinCompCape = findMethodSafe(clsSkinTex, "capeTexture");
                }
            }

            clsIdentifier = Class.forName("net.minecraft.class_2960", false, cl);
            clsAbstractTexture = Class.forName("net.minecraft.class_1044", false, cl);
            clsDynamicTexture = Class.forName("net.minecraft.class_1043", false, cl);
            mDynUpload = clsDynamicTexture.getDeclaredMethod("method_4524");
            mDynUpload.setAccessible(true);
            mDynGetImage = clsDynamicTexture.getDeclaredMethod("method_4525");
            mDynGetImage.setAccessible(true);

            clsTextureManager = Class.forName("net.minecraft.class_1060", false, cl);
            mTexRegister = clsTextureManager.getDeclaredMethod("method_4616", clsIdentifier, clsAbstractTexture);
            mTexRegister.setAccessible(true);
            mTexGet = findMethodSafe(clsTextureManager, "method_4619", clsIdentifier);

            clsNativeImage = Class.forName("net.minecraft.class_1011", false, cl);
            mNativeRead = clsNativeImage.getDeclaredMethod("method_4309", InputStream.class);
            mNativeRead.setAccessible(true);
            mNativeCopyFrom = clsNativeImage.getDeclaredMethod("method_4317", clsNativeImage);
            mNativeCopyFrom.setAccessible(true);

            handlesResolved = true;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private void onRenderThreadTick(Object mc) throws Exception {
        File runDir = resolveRunDir(mc);
        Object world = ClientLiveOptions.multiplayerState(mc);
        liveOptions.tick(mc, runDir, world);
        packLiveApply.tick(mc, runDir);
        if (!modernRuntime) visualOverrides.tick(mc, runDir);
        adaptiveDistance.tick(mc, runDir);
        boolean assetSkin = modernRuntime || (handlesResolved && mSkinCompCape != null &&
                clsIdentifier != null && !clsIdentifier.isAssignableFrom(mSkinCompCape.getReturnType()));
        slowCounter++;
        if (slowCounter >= 8) {
            slowCounter = 0;
            syncWaveyCapesIfNeeded(runDir);
            if (handlesResolved) reloadCapeFramesIfNeeded(runDir);
        } else if (handlesResolved && frameNativeImages.isEmpty()) {
            reloadCapeFramesIfNeeded(runDir);
        }
        if (assetSkin) {
            if (modernCapeBridge == null)
                modernCapeBridge = new ModernCapeBridge(AerixClientMod.class.getClassLoader(), modernRuntime);
            modernCapeBridge.tick(mc, runDir);
            writeStatus(runDir, world, modernCapeBridge.status());
            return;
        }
        writeStatus(runDir, world, legacyCapeStatus());

        if (!handlesResolved || frameNativeImages.isEmpty() || fPlayer == null) return;
        Object player = fPlayer.get(mc);
        if (player == null) return;

        Object capeId = resolvePlayerCapeIdentifier(player);
        if (capeId == null) {
            capeStatus = "no cape in the skin profile";
            return;
        }

        Object texMgr = mGetTextureManager.invoke(mc);
        if (texMgr == null) return;

        frameIndex = (frameIndex + 1) % frameNativeImages.size();
        Object srcImage = frameNativeImages.get(frameIndex);

        Object existingTex = mTexGet != null ? mTexGet.invoke(texMgr, capeId) : null;
        if (existingTex != null && clsDynamicTexture.isInstance(existingTex)) {
            Object dstImage = mDynGetImage.invoke(existingTex);
            if (dstImage != null) {
                mNativeCopyFrom.invoke(dstImage, srcImage);
                mDynUpload.invoke(existingTex);
                return;
            }
        }

        byte[] seedPng = framePngBytes.get(frameIndex);
        Object ownedImg = mNativeRead.invoke(null, new ByteArrayInputStream(seedPng));
        Object dynTex = instantiateDynamicTexture(ownedImg);
        if (dynTex != null) {
            mTexRegister.invoke(texMgr, capeId, dynTex);
        }
    }

    private Object instantiateDynamicTexture(Object nativeImg) {
        try {
            for (Constructor<?> c : clsDynamicTexture.getConstructors()) {
                Class<?>[] p = c.getParameterTypes();
                if (p.length == 1 && p[0].isAssignableFrom(clsNativeImage)) {
                    return c.newInstance(nativeImg);
                }
            }
            for (Constructor<?> c : clsDynamicTexture.getConstructors()) {
                Class<?>[] p = c.getParameterTypes();
                if (p.length == 2 && p[0].isAssignableFrom(clsNativeImage) && p[1] == boolean.class) {
                    return c.newInstance(nativeImg, Boolean.TRUE);
                }
                if (p.length == 2 && p[0] == Supplier.class && p[1].isAssignableFrom(clsNativeImage)) {
                    Supplier<String> label = new Supplier<String>() {
                        @Override public String get() { return "aerix_cape"; }
                    };
                    return c.newInstance(label, nativeImg);
                }
                if (p.length == 3 && p[0] == Supplier.class && p[1].isAssignableFrom(clsNativeImage) && p[2] == boolean.class) {
                    Supplier<String> label = new Supplier<String>() {
                        @Override public String get() { return "aerix_cape"; }
                    };
                    return c.newInstance(label, nativeImg, Boolean.TRUE);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private Object resolvePlayerCapeIdentifier(Object player) {
        try {
            if (mGetCapeLegacy != null) return mGetCapeLegacy.invoke(player);
            if (mGetSkinTextures != null && mSkinCompCape != null) {
                Object skinTex = mGetSkinTextures.invoke(player);
                if (skinTex != null) return mSkinCompCape.invoke(skinTex);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void reloadCapeFramesIfNeeded(File runDir) {
        File dir = new File(runDir, "config/aerix_cape_frames");
        File meta = new File(dir, "meta.properties");
        File f0 = new File(dir, "frame_00.png");
        long mtime = meta.isFile() ? meta.lastModified() : (f0.isFile() ? f0.lastModified() : -1L);
        if (mtime <= 0L || mtime == lastCapeMetaMtime) return;
        lastCapeMetaMtime = mtime;

        List<byte[]> loadedBytes = new ArrayList<byte[]>();
        List<Object> loadedImages = new ArrayList<Object>();
        for (int i = 0; i < 16; i++) {
            String name = String.format(java.util.Locale.ROOT, "frame_%02d.png", Integer.valueOf(i));
            File f = new File(dir, name);
            if (!f.isFile()) break;
            try {
                byte[] bytes = readAllBytes(f);
                Object img = mNativeRead.invoke(null, new ByteArrayInputStream(bytes));
                if (img != null) {
                    loadedBytes.add(bytes);
                    loadedImages.add(img);
                }
            } catch (Throwable ignored) {
            }
        }
        if (loadedImages.isEmpty()) return;

        for (Object old : frameNativeImages) {
            if (old instanceof AutoCloseable) {
                try { ((AutoCloseable) old).close(); } catch (Throwable ignored) {}
            }
        }
        framePngBytes.clear();
        frameNativeImages.clear();
        framePngBytes.addAll(loadedBytes);
        frameNativeImages.addAll(loadedImages);
        frameIndex = 0;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void syncWaveyCapesIfNeeded(File runDir) {
        File cfgFile = new File(runDir, "config/waveycapes.json");
        if (!cfgFile.isFile()) return;
        long mtime = cfgFile.lastModified();
        if (mtime == lastWaveyMtime) return;
        try {
            String json = new String(readAllBytes(cfgFile), StandardCharsets.UTF_8);
            boolean wavesOn = json.contains("\"WAVES\"");
            Class<?> modBase = Class.forName("dev.tr7zw.waveycapes.versionless.ModBase", false, AerixClientMod.class.getClassLoader());
            Field fCfg = modBase.getDeclaredField("config");
            fCfg.setAccessible(true);
            Object cfg = fCfg.get(null);
            if (cfg == null) return;
            boolean wind = setEnumField(cfg, "windMode", wavesOn ? "WAVES" : "NONE");
            boolean style = setEnumField(cfg, "capeStyle", wavesOn ? "SMOOTH" : "BLOCKY");
            boolean movement = setEnumField(cfg, "capeMovement", wavesOn ? "BASIC_SIMULATION_3D" : "VANILLA");
            if (wind && style && movement) lastWaveyMtime = mtime;
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean setEnumField(Object target, String fieldName, String enumConst) {
        try {
            Field f = target.getClass().getField(fieldName);
            Class<?> t = f.getType();
            if (t.isEnum()) {
                Object val = Enum.valueOf((Class<Enum>) t, enumConst);
                f.set(target, val);
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private String legacyCapeStatus() {
        if (!handlesResolved) return "game texture handles unavailable on this version";
        if (frameNativeImages.isEmpty()) return "no cape frames in config/aerix_cape_frames yet";
        if (fPlayer == null) return "player handle unavailable";
        return "animating " + frameNativeImages.size() + " frames";
    }

    private void writeStatus(File runDir, Object world, String bridgeStatus) {
        if (bridgeStatus != null) capeStatus = bridgeStatus;
        long now = System.currentTimeMillis();
        if (now - lastStatusWrite < 2000L) return;
        lastStatusWrite = now;
        Properties out = new Properties();
        out.setProperty("modVersion", "1.2.0");
        out.setProperty("runtime", modernRuntime ? "mojang-mapped" : "intermediary");
        out.setProperty("cape", capeStatus);
        out.setProperty("live", liveOptions.statusLine());
        out.setProperty("zoom", liveOptions.isZoomActive() ? "held" : "idle");
        out.setProperty("pack", packLiveApply.statusLine());
        out.setProperty("ts", String.valueOf(now));
        try {
            File f = new File(runDir, "config/aerix_client_status.properties");
            f.getParentFile().mkdirs();
            File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                out.store(fos, "Aerix Client status");
            }
            if (!tmp.renameTo(f)) {
                try (FileOutputStream fos = new FileOutputStream(f)) {
                    out.store(fos, "Aerix Client status");
                }
                tmp.delete();
            }
        } catch (Throwable ignored) {
        }
    }

    private File resolveRunDir(Object mc) {
        try {
            if (fRunDirectory != null) {
                Object dir = fRunDirectory.get(mc);
                if (dir instanceof File) return (File) dir;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static byte[] readAllBytes(File f) throws Exception {
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[(int) Math.min(f.length(), 8 * 1024 * 1024)];
            int off = 0;
            while (off < buf.length) {
                int n = in.read(buf, off, buf.length - off);
                if (n < 0) break;
                off += n;
            }
            if (off == buf.length) return buf;
            byte[] exact = new byte[off];
            System.arraycopy(buf, 0, exact, 0, off);
            return exact;
        }
    }

    private static Field findFieldSafe(Class<?> cls, String name) {
        try {
            Field f = cls.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Method findMethodSafe(Class<?> cls, String name, Class<?>... params) {
        try {
            Method m = cls.getDeclaredMethod(name, params);
            m.setAccessible(true);
            return m;
        } catch (Throwable t) {
            return null;
        }
    }
}
