package com.slfrontier.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.slfrontier.SLFrontier;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class KeyBindings {
    /** Controls → Key Binds section; its name is the "key.category.slfrontier.main" translation. */
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(SLFrontier.id("main"));

    public static final KeyMapping[] SKILLS = {
            new KeyMapping("key.slfrontier.skill_1", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY),
            new KeyMapping("key.slfrontier.skill_2", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY),
            new KeyMapping("key.slfrontier.skill_3", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY),
            new KeyMapping("key.slfrontier.skill_4", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY),
    };
    public static final KeyMapping MENU =
            new KeyMapping("key.slfrontier.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);
    public static final KeyMapping MAP =
            new KeyMapping("key.slfrontier.map", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, CATEGORY);
    public static final KeyMapping MINIMAP =
            new KeyMapping("key.slfrontier.minimap", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, CATEGORY);

    private static Object[] textArgs;

    private KeyBindings() {
    }

    /**
     * The current key names, as translation arguments for text that mentions the Frontier's keys. Lang
     * strings pick them by position: %1$s menu, %2$s map, %3$s minimap, %4$s..%7$s skill slots 1-4.
     * Keybind components resolve when drawn, so rebinding a key updates text already on screen.
     */
    public static Object[] textArgs() {
        if (textArgs == null) {
            textArgs = new Object[]{key(MENU), key(MAP), key(MINIMAP), key(SKILLS[0]), key(SKILLS[1]), key(SKILLS[2]), key(SKILLS[3])};
        }
        return textArgs;
    }

    /** A translatable text followed by {@link #textArgs()} (after any arguments of its own). */
    public static Component text(String key, Object... args) {
        if (args.length == 0) return Component.translatable(key, textArgs());
        Object[] keys = textArgs();
        Object[] all = java.util.Arrays.copyOf(args, args.length + keys.length);
        System.arraycopy(keys, 0, all, args.length, keys.length);
        return Component.translatable(key, all);
    }

    private static Component key(KeyMapping mapping) {
        return Component.keybind(mapping.getName());
    }
}
