package com.blanc.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Enter a Blanc realm address (name-1A4S6.blanc) and join it. Buttons only, so it survives small UI API changes. */
public final class BlancScreen extends Screen {
    private final Screen parent;
    private EditBox box;
    private Button join;

    public BlancScreen(Screen parent) {
        super(Component.literal("Blanc Realms"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = this.height / 2 - 30;
        box = new EditBox(this.font, cx - 110, y, 220, 20, Component.literal("Realm address"));
        box.setMaxLength(80);
        box.setHint(Component.literal("myrealm-1A4S6.blanc"));
        this.addRenderableWidget(box);
        join = Button.builder(Component.literal("Join Realm"), b -> doJoin()).bounds(cx - 110, y + 28, 220, 20).build();
        this.addRenderableWidget(join);
        this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> Minecraft.getInstance().setScreen(parent))
                .bounds(cx - 110, y + 54, 220, 20).build());
    }

    private void doJoin() {
        String target = BlancCode.resolve(box.getValue());
        if (target == null) {
            join.setMessage(Component.literal("Not a valid Blanc address"));
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ServerData data = new ServerData("Blanc Realm", target, ServerData.Type.OTHER);
        ServerAddress addr = ServerAddress.parse(target);
        connect(parent, mc, addr, data);
    }

    /** ConnectScreen.startConnecting changed its parameter list between versions, so fill it by type. */
    private static void connect(Screen parent, Minecraft mc, ServerAddress addr, ServerData data) {
        try {
            Class<?> cls = Class.forName("net.minecraft.client.gui.screens.ConnectScreen");
            for (Method m : cls.getDeclaredMethods()) {
                if (!m.getName().equals("startConnecting") || !Modifier.isStatic(m.getModifiers())) continue;
                Class<?>[] types = m.getParameterTypes();
                Object[] args = new Object[types.length];
                for (int i = 0; i < types.length; i++) {
                    Class<?> t = types[i];
                    if (t.isAssignableFrom(Screen.class) && Screen.class.isAssignableFrom(t) || t == Screen.class) args[i] = parent;
                    else if (t == Minecraft.class) args[i] = mc;
                    else if (t == ServerAddress.class) args[i] = addr;
                    else if (t == ServerData.class) args[i] = data;
                    else if (t == boolean.class) args[i] = Boolean.FALSE;
                    else args[i] = null;
                }
                m.setAccessible(true);
                m.invoke(null, args);
                return;
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
