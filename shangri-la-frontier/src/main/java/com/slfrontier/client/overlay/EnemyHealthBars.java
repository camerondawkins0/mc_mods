package com.slfrontier.client.overlay;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.slfrontier.SlfConfig;
import com.slfrontier.client.ui.Gfx;
import com.slfrontier.client.ui.Theme;
import com.slfrontier.entity.FrontierBoss;
import com.slfrontier.entity.LeveledMob;
import com.slfrontier.registry.ModAttachments;
import com.slfrontier.world.Frontier;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Health bars over nearby monsters that are fighting you, were just hit or are under the
 * crosshair; each fades out a few seconds after the last of those. A bar shows the monster's
 * name (and level), its HP and a pale trailing segment for the damage it just took. Bars are
 * billboards in the world, grow a little with distance to stay readable and are hidden by
 * terrain like any other object. Bosses keep their boss bars instead.
 */
public final class EnemyHealthBars {
    private static final double RANGE = 24;
    private static final double AGGRO_RANGE = 16;
    /** Ticks a bar stays fully visible after the last hit, aim or aggression, then fades over FADE. */
    private static final int HOLD = 60, FADE = 20;
    /** Ticks the damage trail waits before draining down to the new health. */
    private static final int TRAIL_DELAY = 10;
    private static final float WIDTH = 40, LEVELED_WIDTH = 46, HEIGHT = 4;
    private static final int FRAME = 0xD0060C16, TRAIL = 0xFFFFE3A0;

    /** Alpha-blended, depth-tested and depth-writing quads, drawn in the order given. */
    private static final RenderType BAR = RenderType.create("slfrontier_health_bar", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 4096, false, false, RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));

    private static final class Bar {
        float health;
        float trail;
        int trailHold;
        int lastActive = -10_000;

        Bar(float health) {
            this.health = health;
            this.trail = health;
        }
    }

    private record Shown(Mob mob, Bar bar, double x, double y, double z, double dist, float alpha) {
    }

    private static final Map<Integer, Bar> BARS = new HashMap<>();
    private static int clock;

    private EnemyHealthBars() {
    }

    public static void clear() {
        BARS.clear();
    }

    private static boolean tracks(Mob mob) {
        return mob instanceof Enemy && !(mob instanceof FrontierBoss) && !mob.isRemoved();
    }

    public static void tick() {
        clock++;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (!SlfConfig.ENEMY_HEALTH_BARS.get() || player == null || level == null) {
            BARS.clear();
            return;
        }
        Set<Integer> seen = new HashSet<>();
        for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(RANGE), EnemyHealthBars::tracks)) {
            if (mob.distanceToSqr(player) > RANGE * RANGE) continue;
            seen.add(mob.getId());
            float hp = mob.getHealth();
            Bar bar = BARS.computeIfAbsent(mob.getId(), id -> new Bar(hp));
            boolean damaged = hp < bar.health - 0.01f;
            if (damaged) bar.trailHold = TRAIL_DELAY;
            if (damaged || mob.hurtTime > 0 || mob == mc.crosshairPickEntity || targets(mob, player)) bar.lastActive = clock;
            bar.health = hp;
            if (hp >= bar.trail) {
                bar.trail = hp;
            } else if (bar.trailHold > 0) {
                bar.trailHold--;
            } else {
                bar.trail = Math.max(hp, bar.trail - Math.max(mob.getMaxHealth() * 0.02f, (bar.trail - hp) * 0.2f));
            }
        }
        BARS.keySet().retainAll(seen);
    }

    /**
     * Whether the monster is after the player. Targets aren't synced to clients, but the
     * "aggressive" flag is, and an aggressive monster facing you is coming for you.
     */
    private static boolean targets(Mob mob, LocalPlayer player) {
        if (!mob.isAggressive() || mob.distanceToSqr(player) > AGGRO_RANGE * AGGRO_RANGE) return false;
        double dx = player.getX() - mob.getX(), dz = player.getZ() - mob.getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        return Math.abs(Mth.wrapDegrees(yaw - mob.getYHeadRot())) < 60f;
    }

    private static float visibility(Bar bar, float pt) {
        float since = clock + pt - bar.lastActive;
        return since <= HOLD ? 1f : Mth.clamp(1f - (since - HOLD) / FADE, 0f, 1f);
    }

    public static void render(Camera camera, float pt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (BARS.isEmpty() || player == null || level == null || mc.options.hideGui) return;
        Vec3 cam = camera.getPosition();
        Vector3f look = camera.getLookVector();
        List<Shown> shown = new ArrayList<>();
        for (Map.Entry<Integer, Bar> e : BARS.entrySet()) {
            float alpha = visibility(e.getValue(), pt);
            if (alpha <= 0.02f) continue;
            Entity entity = level.getEntity(e.getKey());
            if (!(entity instanceof Mob mob) || mob.isInvisibleTo(player)) continue;
            if (mob.isDeadOrDying()) alpha *= Mth.clamp(1f - (mob.deathTime + pt) / 20f, 0f, 1f);
            double x = Mth.lerp(pt, mob.xo, mob.getX()) - cam.x;
            double y = Mth.lerp(pt, mob.yo, mob.getY()) + mob.getBbHeight() + (mob.shouldShowName() ? 0.95 : 0.45) - cam.y;
            double z = Mth.lerp(pt, mob.zo, mob.getZ()) - cam.z;
            if (x * look.x() + y * look.y() + z * look.z() < 0 || alpha <= 0.02f) continue;
            shown.add(new Shown(mob, e.getValue(), x, y, z, Math.sqrt(x * x + y * y + z * z), alpha));
        }
        if (shown.isEmpty()) return;
        // Far to near, so nearer bars blend over farther ones.
        shown.sort((p, q) -> Double.compare(q.dist(), p.dist()));
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack pose = new PoseStack();
        VertexConsumer v = buffers.getBuffer(BAR);
        for (Shown s : shown) {
            billboard(pose, camera, s);
            drawBar(v, pose.last().pose(), s);
            pose.popPose();
        }
        buffers.endBatch(BAR);
        Font font = mc.font;
        int playerLevel = Frontier.isIn(player) ? ModAttachments.get(player).level() : -1;
        for (Shown s : shown) {
            if (s.alpha < 0.05f) continue;
            billboard(pose, camera, s);
            Component name = s.mob instanceof LeveledMob leveled
                    ? Component.translatable("hud.slfrontier.target", s.mob.getDisplayName(), leveled.frontierLevel())
                    : s.mob.getDisplayName();
            pose.translate(0, -8.5f, -3f);
            pose.scale(0.75f, 0.75f, 0.75f);
            int color = nameColor(s.mob, playerLevel);
            font.drawInBatch(name, -font.width(name) / 2f, 0, ((int) (s.alpha * 255) << 24) | (color & 0xFFFFFF), true,
                    pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            pose.popPose();
        }
        buffers.endBatch();
    }

    /** Pushes a camera-facing frame above the monster, in "pixels" that grow with distance. */
    private static void billboard(PoseStack pose, Camera camera, Shown s) {
        pose.pushPose();
        pose.translate(s.x, s.y, s.z);
        pose.mulPose(camera.rotation());
        float k = 0.022f * (1f + (float) Math.max(0, s.dist - 6) * 0.045f);
        pose.scale(k, -k, k);
    }

    /** Frame, empty track, trailing damage, HP fill and a highlight, each a little nearer the camera. */
    private static void drawBar(VertexConsumer v, Matrix4f m, Shown s) {
        float w = s.mob instanceof LeveledMob ? LEVELED_WIDTH : WIDTH;
        float x0 = -w / 2, x1 = w / 2;
        float max = Math.max(1f, s.mob.getMaxHealth());
        float hp = Mth.clamp(s.bar.health / max, 0f, 1f), trail = Mth.clamp(s.bar.trail / max, 0f, 1f);
        float a = s.alpha;
        rect(v, m, x0 - 1, -1, x1 + 1, HEIGHT + 1, 0f, fade(FRAME, a), fade(FRAME, a));
        rect(v, m, x0, 0, x1, HEIGHT, -0.4f, fade(Theme.BAR_BACK, a), fade(Theme.BAR_BACK, a));
        float fx = x0 + w * hp;
        if (trail > hp) rect(v, m, fx, 0, x0 + w * trail, HEIGHT, -0.8f, fade(TRAIL, a), fade(TRAIL, a * 0.85f));
        if (hp > 0) {
            rect(v, m, x0, 0, fx, HEIGHT, -1.2f, fade(Theme.HP_A, a), fade(Theme.HP_B, a));
            rect(v, m, x0, 0, fx, HEIGHT / 3f, -1.6f, fade(0x50FFFFFF, a), fade(0x18FFFFFF, a));
        }
        for (int i = 1; i < 4; i++) {
            float tx = x0 + w * i / 4f;
            rect(v, m, tx, 0, tx + 0.5f, HEIGHT, -2f, fade(0x60000000, a), fade(0x60000000, a));
        }
        rect(v, m, x0 - 1, -1, x1 + 1, -0.5f, -2f, fade(0xFF7A2A36, a), fade(0xFF7A2A36, a));
    }

    private static int nameColor(Mob mob, int playerLevel) {
        if (!(mob instanceof LeveledMob leveled) || playerLevel < 0) return 0xFFFFFFFF;
        int diff = leveled.frontierLevel() - playerLevel;
        return diff >= 10 ? Theme.RED : diff >= 4 ? 0xFFFFB050 : 0xFFFFFFFF;
    }

    private static int fade(int argb, float alpha) {
        return Gfx.withAlpha(argb, ((argb >>> 24) & 0xFF) / 255f * alpha);
    }

    /** An axis-aligned quad in the billboard plane with a vertical gradient; y grows downward. */
    private static void rect(VertexConsumer v, Matrix4f m, float x0, float y0, float x1, float y1, float z, int top, int bottom) {
        v.addVertex(m, x0, y0, z).setColor(top);
        v.addVertex(m, x0, y1, z).setColor(bottom);
        v.addVertex(m, x1, y1, z).setColor(bottom);
        v.addVertex(m, x1, y0, z).setColor(top);
    }
}
