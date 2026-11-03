package com.slfrontier.client;

import com.slfrontier.SLFrontier;
import com.slfrontier.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.jetbrains.annotations.Nullable;

/** Client-side sound cues: UI blips, panels opening and closing, and sounds only the local player hears. */
@EventBusSubscriber(modid = SLFrontier.MODID, value = Dist.CLIENT)
public final class ClientSfx {
    /** Set by the Opening event that precedes a Closing in the same screen change. */
    private static boolean switchingToOurs;

    private ClientSfx() {
    }

    /** A UI sound (not placed in the world; the Master/UI volume applies). */
    public static void ui(Holder<SoundEvent> sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound.value(), pitch, volume));
    }

    /** A sound in the world that only this client hears (e.g. the feel of its own hits). */
    public static void local(double x, double y, double z, Holder<SoundEvent> sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) mc.level.playLocalSound(x, y, z, sound.value(), SoundSource.PLAYERS, volume, pitch, false);
    }

    private static boolean ours(@Nullable Screen screen) {
        return screen != null && screen.getClass().getPackageName().startsWith("com.slfrontier.client.screen");
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        switchingToOurs = ours(event.getNewScreen());
        if (switchingToOurs && !ours(event.getCurrentScreen())) ui(ModSounds.UI_OPEN, 1f, 1f);
    }

    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event) {
        // Moving between the Frontier's own screens (menu tabs, map) is one panel, not a close.
        if (ours(event.getScreen()) && !switchingToOurs) ui(ModSounds.UI_CLOSE, 1f, 1f);
        switchingToOurs = false;
    }
}
