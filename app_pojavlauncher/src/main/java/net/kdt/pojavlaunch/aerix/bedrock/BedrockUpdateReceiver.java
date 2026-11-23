package net.kdt.pojavlaunch.aerix.bedrock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Keeps a copy of Minecraft right after Google Play installs or updates it. */
public final class BedrockUpdateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getData() == null) return;
        if (!BedrockDetector.PACKAGE.equals(intent.getData().getSchemeSpecificPart())) return;
        final Context app = context.getApplicationContext();
        final PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                BedrockVersions.autoSave(app);
            } finally {
                pending.finish();
            }
        }, "bedrock-autosave").start();
    }
}
