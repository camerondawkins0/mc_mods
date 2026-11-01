package com.slfrontier.client.fx;

import com.slfrontier.SlfConfig;
import com.slfrontier.client.ClientAnims;
import com.slfrontier.entity.FrontierBoss;
import com.slfrontier.network.ModNetwork;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * How hard a hit lands, on the attacker's screen. Every hit flashes the monster white; heavier
 * ones (criticals, weak points, skills, combo finishers, kills) add a hit-stop, where the
 * attacker's and the victim's animations hold for a few ticks, and a quick FOV punch. Skill
 * hits, finishers and boss kills also burst the screen with a flash and speed lines.
 *
 * Driven by the server's {@link ModNetwork.Hit} packets, so it only plays for hits that
 * connected. Times are on the {@link ClientAnims#clock()} so held animations resume smoothly.
 */
public final class Impact {
    private record Window(float start, float end) {
    }

    /** The hit-flash holds full white this long, then fades out by FLASH_TICKS. */
    private static final float FLASH_HOLD = 2, FLASH_TICKS = 5, FOV_TICKS = 8, BURST_TICKS = 9;
    private static final int SPEED_LINES = 48;

    private static final Map<Integer, List<Window>> FREEZES = new HashMap<>();
    /** Entities whose vanilla swing is held for this many more ticks. */
    private static final Map<Integer, Integer> SWING_HOLD = new HashMap<>();
    private static final Map<Integer, Float> FLASH = new HashMap<>();
    /** Stretches every impact timing; the client test raises it so its slow software frames catch the effects. */
    public static float testStretch = 1;
    private static float fovStart = -100, fovStrength;
    private static float burstStart = -100, burstStrength;
    private static long burstSeed;

    private Impact() {
    }

    public static void onHit(ModNetwork.Hit hit) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        float now = ClientAnims.clock() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        int f = hit.flags();
        Entity victim = mc.level.getEntity(hit.entityId());
        boolean boss = victim instanceof FrontierBoss;
        boolean kill = (f & ModNetwork.Hit.KILL) != 0;
        boolean big = (f & (ModNetwork.Hit.SKILL | ModNetwork.Hit.FINISHER)) != 0;
        boolean sharp = (f & (ModNetwork.Hit.CRIT | ModNetwork.Hit.WEAK)) != 0;

        FLASH.put(hit.entityId(), now);
        int stop = kill && (big || boss) ? 4 : kill || big ? 3 : sharp ? 2 : 0;
        float fov = kill && (big || boss) ? 0.08f : kill || big ? 0.055f : sharp ? 0.03f : 0f;
        float burst = big ? (kill ? 1f : 0.7f) : kill && boss ? 1f : 0f;

        if (stop > 0 && SlfConfig.HIT_STOP.get()) {
            freeze(mc.player.getId(), now, stop);
            freeze(hit.entityId(), now, stop);
        }
        if (SlfConfig.IMPACT_SCREEN.get()) {
            if (fov > 0 && fov >= fovPunch(now) / fovScale()) {
                fovStart = now;
                fovStrength = fov;
            }
            if (burst > 0 && burst >= burstLeft(now)) {
                burstStart = now;
                burstStrength = burst;
                burstSeed = mc.level.getRandom().nextLong();
            }
        }
        if ((big || kill) && SlfConfig.CAMERA_SHAKE.get()) CameraShake.add(big && kill ? 0.9f : 0.5f, 5);
    }

    // ---- Hit-stop ------------------------------------------------------------------

    /** Holds an entity's animations for {@code ticks}; overlapping hit-stops merge rather than stack. */
    private static void freeze(int id, float now, float ticks) {
        List<Window> list = FREEZES.computeIfAbsent(id, k -> new ArrayList<>());
        Window last = list.isEmpty() ? null : list.get(list.size() - 1);
        if (last != null && last.end() >= now) {
            list.set(list.size() - 1, new Window(last.start(), Math.max(last.end(), now + ticks)));
        } else {
            list.add(new Window(now, now + ticks));
        }
        SWING_HOLD.merge(id, Mth.ceil(ticks), Math::max);
    }

    /** Animation time an entity lost to hit-stops between two clock times. */
    public static float lost(int id, float from, float to) {
        List<Window> list = FREEZES.get(id);
        if (list == null) return 0;
        float lost = 0;
        for (Window w : list) lost += Math.max(0, Math.min(to, w.end()) - Math.max(from, w.start()));
        return lost;
    }

    /**
     * An animation's play time in milliseconds ({@code millis}, by the entity's own clock) with the
     * time its entity spent held by hit-stops taken out: held creatures resume where they left off.
     */
    public static long heldBack(int id, long millis, float partialTick) {
        float now = ClientAnims.clock() + partialTick;
        float lost = lost(id, now - millis / 50f, now);
        return Math.max(0, millis - (long) (lost * 50));
    }

    /** White flash strength (0..1) on a monster that was just hit. */
    public static float flash(int id, float partialTick) {
        Float start = FLASH.get(id);
        if (start == null) return 0;
        float t = ClientAnims.clock() + partialTick - start;
        t /= testStretch;
        if (t < 0 || t > FLASH_TICKS) return 0;
        return t <= FLASH_HOLD ? 1 : 1 - (t - FLASH_HOLD) / (FLASH_TICKS - FLASH_HOLD);
    }

    /** Runs after the level ticks: holds vanilla swings (and so the slash animations driven by them). */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            for (Iterator<Map.Entry<Integer, Integer>> it = SWING_HOLD.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<Integer, Integer> e = it.next();
                if (mc.level.getEntity(e.getKey()) instanceof LivingEntity living && living.swinging && living.swingTime > 0) {
                    living.swingTime--;
                    living.attackAnim = living.oAttackAnim;
                }
                if (e.getValue() <= 1) it.remove();
                else e.setValue(e.getValue() - 1);
            }
        } else {
            SWING_HOLD.clear();
        }
        float now = ClientAnims.clock();
        // Old hit-stops fold into one window of the same total length rather than vanishing, so
        // long-running loops (a monster's idle) don't skip ahead when they're forgotten.
        FREEZES.entrySet().removeIf(e -> {
            if (mc.level == null || mc.level.getEntity(e.getKey()) == null) return true;
            List<Window> list = e.getValue();
            int old = 0;
            float held = 0;
            while (old < list.size() && list.get(old).end() < now - 400) {
                held += list.get(old).end() - list.get(old).start();
                old++;
            }
            if (old > 1) {
                Window first = list.get(0);
                list.subList(0, old).clear();
                list.add(0, new Window(first.start(), first.start() + held));
            }
            return list.isEmpty();
        });
        FLASH.values().removeIf(start -> now - start > (FLASH_TICKS + 1) * testStretch);
    }

    public static void clear() {
        FREEZES.clear();
        SWING_HOLD.clear();
        FLASH.clear();
        fovStart = burstStart = -100;
    }

    // ---- FOV punch and screen burst --------------------------------------------------

    private static float fovScale() {
        return Math.max(0.01f, Minecraft.getInstance().options.fovEffectScale().get().floatValue());
    }

    private static float fovPunch(float now) {
        float t = now - fovStart;
        if (t < 0 || t > FOV_TICKS) return 0;
        // Snaps in over a tick, then eases back out.
        float shape = t < 1 ? t : 1 - Mth.square((t - 1) / (FOV_TICKS - 1));
        return fovStrength * Mth.clamp(shape, 0f, 1f) * fovScale();
    }

    /** Fraction to narrow the field of view by this frame (respects the FOV Effects slider). */
    public static float fovPunchAt(float partialTick) {
        return fovPunch(ClientAnims.clock() + partialTick);
    }

    private static float burstLeft(float now) {
        float t = now - burstStart;
        return t < 0 || t > BURST_TICKS ? 0 : burstStrength * (1 - t / BURST_TICKS);
    }

    /** GUI layer under the HUD: a white flash (unless lightning flashes are hidden) and speed lines. */
    public static void renderOverlay(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden() || mc.player == null) return;
        float t = (ClientAnims.clock() + delta.getGameTimeDeltaPartialTick(false) - burstStart) / testStretch;
        if (t < 0 || t > BURST_TICKS) return;
        float fade = 1 - t / BURST_TICKS;
        int w = g.guiWidth(), h = g.guiHeight();
        if (t < 3 && !mc.options.hideLightningFlash().get()) {
            int a = (int) (burstStrength * 0.22f * (1 - t / 3) * 255);
            g.fill(0, 0, w, h, a << 24 | 0xFFFFFF);
        }
        RandomSource random = RandomSource.create(burstSeed);
        float cx = w / 2f, cy = h / 2f;
        float outer = (float) Math.hypot(cx, cy) + 4;
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        for (int i = 0; i < SPEED_LINES; i++) {
            float angle = random.nextFloat() * Mth.TWO_PI;
            // Lines streak in from the edges, then pull back out as they fade.
            float reach = 0.42f + 0.33f * random.nextFloat();
            float inner = outer * (reach + (1 - reach) * (1 - fade) * 0.8f);
            int thick = 1 + (random.nextFloat() < 0.3f ? 1 : 0);
            int alpha = (int) (fade * burstStrength * (110 + 90 * random.nextFloat()));
            if (alpha <= 4) continue;
            g.pose().pushMatrix();
            g.pose().rotate(angle);
            g.fill((int) inner, -thick / 2, (int) outer, thick - thick / 2, alpha << 24 | 0xFFFFFF);
            g.pose().popMatrix();
        }
        g.pose().popMatrix();
    }
}
