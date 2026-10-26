package com.slfrontier.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class KeyBindings {
    public static final String CATEGORY = "key.categories.slfrontier";

    public static final KeyMapping[] SKILLS = {
            new KeyMapping("key.slfrontier.skill_1", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY),
            new KeyMapping("key.slfrontier.skill_2", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY),
            new KeyMapping("key.slfrontier.skill_3", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY),
            new KeyMapping("key.slfrontier.skill_4", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY),
    };
    public static final KeyMapping MENU =
            new KeyMapping("key.slfrontier.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);

    private KeyBindings() {
    }
}
