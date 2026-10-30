package com.slfrontier.client;

import com.slfrontier.SLFrontier;
import com.slfrontier.SlfConfig;
import com.slfrontier.world.Frontier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * "Frontier Vision": a colour grade over the Frontier (see shaders/program/frontier_grade.fsh)
 * whose tint drifts with the region you're in — cold and dim in the Nightslayer Woods, washed
 * out in the Lightless Barrens, glowing in the Prismatic Grotto. Stays off when a shader mod
 * is installed, and never replaces another post effect (spectating a creeper, say).
 */
@EventBusSubscriber(modid = SLFrontier.MODID, value = Dist.CLIENT)
public final class FrontierVision {
    private static final ResourceLocation EFFECT = SLFrontier.id("shaders/post/frontier.json");
    private static final boolean SHADER_MOD = ModList.get().isLoaded("iris") || ModList.get().isLoaded("oculus");

    /** saturation, contrast, warmth, tint r/g/b, bloom, vignette */
    private record Grade(float sat, float contrast, float warmth, float r, float g, float b, float bloom, float vignette) {
    }

    private static final Grade DEFAULT = new Grade(1.12f, 1.06f, 0.03f, 1f, 1f, 1f, 0.35f, 0.32f);
    private static boolean announced;
    private static final float[] current = {1.12f, 1.06f, 0.03f, 1f, 1f, 1f, 0.35f, 0.32f};

    private FrontierVision() {
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        GameRenderer renderer = mc.gameRenderer;
        PostChain effect = renderer.currentEffect();
        boolean ours = effect != null && EFFECT.toString().equals(effect.getName());
        boolean wanted = mc.player != null && mc.level != null && Frontier.isFrontier(mc.level) && !SHADER_MOD
                && SlfConfig.FRONTIER_VISION.get();
        if (wanted && effect == null) {
            renderer.loadEffect(EFFECT);
            if (!announced && renderer.currentEffect() != null) {
                announced = true;
                SLFrontier.LOGGER.info("Frontier Vision on");
            }
        } else if (!wanted && ours) {
            renderer.shutdownEffect();
        }
        if (!wanted || !ours) return;
        Grade target = grade(mc);
        // Night: a touch less colour, a touch more glow around the lanterns.
        float night = Mth.clamp(mc.level.getStarBrightness(1f) * 2f, 0f, 1f);
        float[] want = {target.sat() - 0.12f * night, target.contrast(), target.warmth() - 0.03f * night, target.r(), target.g(),
                target.b() + 0.04f * night, target.bloom() + 0.25f * night, target.vignette() + 0.08f * night};
        String[] names = {"Saturation", "Contrast", "Warmth", "TintR", "TintG", "TintB", "Bloom", "Vignette"};
        for (int i = 0; i < current.length; i++) {
            current[i] += (want[i] - current[i]) * 0.04f;
            effect.setUniform(names[i], current[i]);
        }
    }

    private static Grade grade(Minecraft mc) {
        String biome = mc.level.getBiome(mc.player.blockPosition()).unwrapKey().map(k -> k.location().getPath()).orElse("");
        return switch (biome) {
            case "nightslayer_woods", "nightslayer_den" -> new Grade(0.9f, 1.1f, -0.05f, 0.88f, 0.9f, 1.08f, 0.45f, 0.42f);
            case "lightless_barrens", "shadow_altar" -> new Grade(0.72f, 1.14f, -0.02f, 0.94f, 0.9f, 1.02f, 0.3f, 0.46f);
            case "prismatic_forest_grotto", "empire_bee_hive" -> new Grade(1.25f, 1.05f, 0f, 0.98f, 1f, 1.07f, 0.6f, 0.3f);
            case "hidden_garden", "wezaemon_tomb", "rabbit_isle", "rabituza" -> new Grade(1.2f, 1.04f, 0.05f, 1.05f, 0.98f, 1.02f, 0.45f, 0.3f);
            case "diremarsh_waste", "mud_digger_lair" -> new Grade(0.9f, 1.05f, 0.02f, 1f, 1.02f, 0.9f, 0.3f, 0.38f);
            case "frontier_peaks" -> new Grade(1.02f, 1.1f, -0.02f, 0.98f, 1f, 1.05f, 0.4f, 0.3f);
            default -> DEFAULT;
        };
    }
}
