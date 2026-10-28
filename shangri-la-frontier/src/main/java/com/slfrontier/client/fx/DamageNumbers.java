package com.slfrontier.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.slfrontier.SlfConfig;
import com.slfrontier.network.ModNetwork;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Floating damage numbers over monsters the player hits (crits gold and bigger, weak points orange). */
public final class DamageNumbers {
    private static final int LIFE = 28;

    private record Number(Vec3 pos, float amount, int flags, float start, float drift) {
    }

    private static final List<Number> NUMBERS = new ArrayList<>();
    private static float clock;

    private DamageNumbers() {
    }

    public static void add(int entityId, float amount, int flags) {
        if (!SlfConfig.DAMAGE_NUMBERS.get()) return;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        Entity e = level.getEntity(entityId);
        if (e == null) return;
        float drift = (float) (level.random.nextGaussian() * 0.25);
        NUMBERS.add(new Number(e.position().add(0, e.getBbHeight() + 0.25, 0), amount, flags, clock, drift));
        if ((flags & ModNetwork.Hit.CRIT) != 0 && SlfConfig.CAMERA_SHAKE.get()) CameraShake.add(0.6f, 5);
    }

    public static void tick() {
        clock++;
        NUMBERS.removeIf(n -> clock - n.start > LIFE);
    }

    public static void clear() {
        NUMBERS.clear();
    }

    public static void render(Camera camera, float partialTick) {
        if (NUMBERS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        Vec3 cam = camera.getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack pose = new PoseStack();
        for (Number n : NUMBERS) {
            float age = clock + partialTick - n.start;
            float t = age / LIFE;
            boolean crit = (n.flags & ModNetwork.Hit.CRIT) != 0;
            boolean weak = (n.flags & ModNetwork.Hit.WEAK) != 0;
            float rise = 0.9f * (1 - (1 - t) * (1 - t));
            float pop = age < 4 ? 1.8f - 0.2f * age : 1f;
            float scale = 0.026f * pop * (crit ? 1.45f : 1f) * (1 - Math.max(0, t - 0.8f) * 2);
            int alpha = (int) (255 * Mth.clamp((1 - t) * 3, 0, 1));
            if (alpha < 8) continue;
            int color = crit ? 0xFFD34F : weak ? 0xFF9A3A : 0xFFFFFF;
            String text = n.amount >= 10 ? String.valueOf(Math.round(n.amount)) : String.format("%.1f", n.amount);
            Component label = Component.literal(crit ? text + "!" : text);
            pose.pushPose();
            pose.translate(n.pos.x - cam.x + n.drift * t, n.pos.y - cam.y + rise, n.pos.z - cam.z);
            pose.mulPose(camera.rotation());
            pose.scale(scale, -scale, scale);
            float x = -font.width(label) / 2f;
            font.drawInBatch(label, x, 0, (alpha << 24) | color, true, pose.last().pose(), buffers, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
            if (weak || crit) {
                Component tag = Component.translatable(crit ? "hud.slfrontier.crit" : "hud.slfrontier.weak_point");
                pose.scale(0.55f, 0.55f, 0.55f);
                font.drawInBatch(tag, -font.width(tag) / 2f, -11, (alpha << 24) | color, true, pose.last().pose(), buffers,
                        Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
            }
            pose.popPose();
        }
        buffers.endBatch();
    }
}
