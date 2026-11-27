package com.blanc.mod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;

public final class BlancClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof TitleScreen) {
                Screens.getButtons(screen).add(Button.builder(Component.literal("Blanc Realms"),
                        b -> Minecraft.getInstance().setScreen(new BlancScreen(screen))).bounds(6, 6, 100, 20).build());
            } else if (screen instanceof JoinMultiplayerScreen) {
                Screens.getButtons(screen).add(Button.builder(Component.literal("Blanc Realm"),
                        b -> Minecraft.getInstance().setScreen(new BlancScreen(screen))).bounds(6, 6, 90, 20).build());
            }
        });
    }
}
