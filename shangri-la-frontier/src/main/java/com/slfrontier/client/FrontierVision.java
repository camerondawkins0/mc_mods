package com.slfrontier.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.slfrontier.SLFrontier;
import com.slfrontier.SlfConfig;
import com.slfrontier.world.Frontier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * "Frontier Vision": a colour grade over the Frontier (see post_effect/frontier.json and
 * shaders/post/frontier_grade.fsh) whose tint drifts with the region you're in — cold and dim in
 * the Nightslayer Woods, washed out in the Lightless Barrens, glowing in the Prismatic Grotto.
 * Stays off when a shader mod is installed, and never replaces another post effect (spectating a
 * creeper, say).
 *
 * <p>Post effect uniforms are fixed when the chain loads, so the drifting values reach the shader
 * through a tiny texture instead: one texel per value, rewritten every tick.
 */
@EventBusSubscriber(modid = SLFrontier.MODID, value = Dist.CLIENT)
public final class FrontierVision {
    private static final Identifier EFFECT = SLFrontier.id("frontier");
    /** The post chain's "Params" input, {@code slfrontier:frontier_grade_params} (textures/effect/ is implied there). */
    private static final Identifier PARAMS = SLFrontier.id("textures/effect/frontier_grade_params.png");
    /** Each value is packed into 16 bits over [PARAM_MIN, PARAM_MIN + PARAM_RANGE]; the shader unpacks the same way. */
    private static final float PARAM_MIN = -1f, PARAM_RANGE = 4f;
    private static final boolean SHADER_MOD = ModList.get().isLoaded("iris") || ModList.get().isLoaded("oculus");

    /** saturation, contrast, warmth, tint r/g/b, bloom, vignette */
    private record Grade(float sat, float contrast, float warmth, float r, float g, float b, float bloom, float vignette) {
    }

    private static final Grade DEFAULT = new Grade(1.12f, 1.06f, 0.03f, 1f, 1f, 1f, 0.35f, 0.32f);
    private static boolean announced;
    private static final float[] current = {1.12f, 1.06f, 0.03f, 1f, 1f, 1f, 0.35f, 0.32f};
    @Nullable
    private static DynamicTexture params;

    private FrontierVision() {
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        GameRenderer renderer = mc.gameRenderer;
        Identifier effect = renderer.currentPostEffect();
        boolean ours = EFFECT.equals(effect);
        boolean wanted = mc.player != null && mc.level != null && Frontier.isFrontier(mc.level) && !SHADER_MOD
                && SlfConfig.FRONTIER_VISION.get();
        if (wanted && effect == null) {
            // The parameter texture must exist before the chain loads and looks it up.
            upload();
            renderer.setPostEffect(EFFECT);
            if (!announced && mc.getShaderManager().getPostChain(EFFECT, LevelTargetBundle.MAIN_TARGETS) != null) {
                announced = true;
                SLFrontier.LOGGER.info("Frontier Vision on");
            }
        } else if (!wanted && ours) {
            renderer.clearPostEffect();
        }
        if (!wanted || !ours) return;
        Grade target = grade(mc);
        // Night: a touch less colour, a touch more glow around the lanterns.
        float stars = mc.gameRenderer.mainCamera().attributeProbe().getValue(EnvironmentAttributes.STAR_BRIGHTNESS, 1f);
        float night = Mth.clamp(stars * 2f, 0f, 1f);
        float[] want = {target.sat() - 0.12f * night, target.contrast(), target.warmth() - 0.03f * night, target.r(), target.g(),
                target.b() + 0.04f * night, target.bloom() + 0.25f * night, target.vignette() + 0.08f * night};
        for (int i = 0; i < current.length; i++) {
            current[i] += (want[i] - current[i]) * 0.04f;
        }
        upload();
    }

    /** Writes {@link #current} into the parameter texture (red = high byte, green = low byte) and sends it to the GPU. */
    private static void upload() {
        if (params == null) {
            params = new DynamicTexture(PARAMS::toString, current.length, 1, true);
            Minecraft.getInstance().getTextureManager().register(PARAMS, params);
        }
        NativeImage image = params.getPixels();
        for (int i = 0; i < current.length; i++) {
            int v = Mth.clamp(Math.round((current[i] - PARAM_MIN) / PARAM_RANGE * 65535f), 0, 65535);
            image.setPixel(i, 0, 0xFF000000 | (v >> 8) << 16 | (v & 0xFF) << 8);
        }
        params.upload();
    }

    private static Grade grade(Minecraft mc) {
        String biome = mc.level.getBiome(mc.player.blockPosition()).unwrapKey().map(k -> k.identifier().getPath()).orElse("");
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
