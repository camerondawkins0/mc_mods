package com.slfrontier.client;

import com.slfrontier.SlfConfig;
import com.slfrontier.client.map.MapView;
import com.slfrontier.client.overlay.QuestPointer;
import com.slfrontier.client.overlay.TravelOverlay;
import com.slfrontier.client.ui.Gfx;
import com.slfrontier.client.ui.Theme;
import com.slfrontier.data.PlayerData;
import com.slfrontier.entity.FrontierBoss;
import com.slfrontier.entity.LeveledMob;
import com.slfrontier.network.ModNetwork;
import com.slfrontier.quest.QuestStage;
import com.slfrontier.quest.QuestSteps;
import com.slfrontier.quest.SideQuest;
import com.slfrontier.registry.ModAttachments;
import com.slfrontier.registry.ModEffects;
import com.slfrontier.registry.ModSounds;
import com.slfrontier.skill.Skill;
import com.slfrontier.skill.SkillExecutor;
import com.slfrontier.world.Frontier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Frontier HUD: avatar frame (level, HP, stamina, EXP, conditions and effects), skill bar
 * with radial cooldowns, minimap, quest tracker with distance to the objective, target frame,
 * region titles, queued announcements, the low-health warning and the restyled boss bar.
 */
public final class FrontierHud {
    private static final MapView MINIMAP = new MapView();

    // ---- Announcements: shown one after another, so two in the same tick both get seen ----
    private record Announcement(String titleKey, String subKey, int color) {
    }

    private static final ArrayDeque<Announcement> QUEUE = new ArrayDeque<>();
    private static final int ANNOUNCE_LENGTH = 80, ANNOUNCE_FADE_OUT = 15;
    /** With another announcement waiting, the current one leaves after this long instead of its full time. */
    private static final int ANNOUNCE_MIN_SHOW = 40;
    @Nullable
    private static Announcement announcement;
    private static Component announceTitle = Component.empty();
    private static Component announceSub = Component.empty();
    private static int announceTicks;

    private static final int[] lastCooldown = new int[PlayerData.SKILL_SLOTS];
    private static final int[] readyFlash = new int[PlayerData.SKILL_SLOTS];
    /** Ticks left on a slot's "can't use that now" shake. */
    private static final int[] denyShake = new int[PlayerData.SKILL_SLOTS];
    private static final int DENY_SHAKE = 5;

    // ---- Region titles ----
    /** A region's title shows again only after this many ticks (two minutes), so border-hopping stays quiet. */
    private static final int REGION_COOLDOWN = 20 * 120;
    private static final Map<ResourceKey<Biome>, Long> REGION_SHOWN = new HashMap<>();
    private static ResourceKey<Biome> region;
    private static ResourceKey<Biome> pendingRegion;
    private static int pendingTicks;
    private static int regionTicks;
    private static long clientTicks;

    /** Out-of-stamina cue: plays once per drain, at most every 3 s. */
    private static boolean staminaDrained;
    private static long staminaCueTick = -1000;

    // ---- Low health: below LOW_HP the HP bar pulses, the screen edges redden and a heartbeat plays ----
    private static final float LOW_HP = 0.3f, CRITICAL_HP = 0.1f;
    /** 0 above LOW_HP, rising to 1 at CRITICAL_HP and below. */
    private static float lowHealth;
    private static int beatTimer;
    private static long lastBeat = -1000;

    /** Bottom of the boss bars drawn this frame (0 when none), so the target frame can sit below them. */
    private static int bossBottom;

    /** Damage trails of the boss bars, by boss event. */
    private static final class Trail {
        float trail, last;
        long holdUntil, lastMs, seen;

        Trail(float progress, long now) {
            trail = last = progress;
            lastMs = seen = now;
        }
    }

    private static final Map<UUID, Trail> TRAILS = new HashMap<>();
    private static final int TRAIL_COLOR = 0xFFFFE3A0;
    private static final float[] NO_PHASES = {};
    private static final float[] WEZAEMON_PHASES = {0.66f, 0.33f};
    private static final float[] HALF = {0.5f};
    private static final float[] MUD_DIGGER_PHASES = {0.2f};

    /** Wrapped tracker objectives, recomputed only when the text, width or language changes. */
    private static final class Wrap {
        @Nullable
        Component text;
        int width;
        @Nullable
        Language language;
        List<FormattedCharSequence> lines = List.of();
    }

    private static final Wrap[] WRAPS = {new Wrap(), new Wrap()};

    private FrontierHud() {
    }

    public static void announce(String title, String subtitle, int color) {
        Announcement a = new Announcement(title, subtitle, color);
        // The same announcement twice in a row (a re-sent packet) isn't news.
        if (a.equals(announcement) && announceTicks > 0 || QUEUE.contains(a)) return;
        if (QUEUE.size() >= 6) QUEUE.pollFirst();
        QUEUE.add(a);
        if (announceTicks <= 0) nextAnnouncement();
    }

    private static void nextAnnouncement() {
        announcement = QUEUE.poll();
        if (announcement == null) {
            announceTicks = 0;
            return;
        }
        announceTitle = Component.translatable(announcement.titleKey()).withStyle(ChatFormatting.BOLD);
        // Subtitles that mention a key name it as bound (KeyBindings.textArgs).
        announceSub = KeyBindings.text(announcement.subKey());
        announceTicks = ANNOUNCE_LENGTH;
    }

    /** A skill key was pressed but the client already knows the skill can't fire: shake its slot and buzz. */
    public static void skillDenied(int slot) {
        if (slot < 0 || slot >= denyShake.length) return;
        if (denyShake[slot] <= DENY_SHAKE - 2) ClientSfx.ui(ModSounds.UI_DENY, 1f, 0.7f);
        denyShake[slot] = DENY_SHAKE;
    }

    /** Called before the HUD layers draw each frame. */
    public static void beginFrame() {
        bossBottom = 0;
    }

    /** Notes where a boss bar (Frontier or vanilla) ends, from the boss bar customisation event. */
    public static void bossBarDrawn(int y, int increment) {
        bossBottom = Math.max(bossBottom, y + increment);
    }

    public static void clear() {
        QUEUE.clear();
        announcement = null;
        announceTicks = 0;
        REGION_SHOWN.clear();
        region = pendingRegion = null;
        regionTicks = 0;
        TRAILS.clear();
        lowHealth = 0;
        beatTimer = 0;
    }

    public static void tick() {
        clientTicks++;
        if (announceTicks > 0 && !TravelOverlay.isActive()) {
            announceTicks--;
            // Something else is waiting: start fading once this one has been up long enough to read.
            if (!QUEUE.isEmpty() && announceTicks > ANNOUNCE_FADE_OUT && ANNOUNCE_LENGTH - announceTicks >= ANNOUNCE_MIN_SHOW) {
                announceTicks = ANNOUNCE_FADE_OUT;
            }
        }
        if (announceTicks <= 0 && !QUEUE.isEmpty()) nextAnnouncement();
        if (regionTicks > 0) regionTicks--;
        for (int i = 0; i < denyShake.length; i++) if (denyShake[i] > 0) denyShake[i]--;
        if (!TRAILS.isEmpty()) {
            long now = Util.getMillis();
            TRAILS.values().removeIf(t -> now - t.seen > 10_000);
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !Frontier.isIn(player)) {
            region = null;
            lowHealth = 0;
            beatTimer = 0;
            return;
        }
        PlayerData data = ModAttachments.get(player);
        for (int i = 0; i < PlayerData.SKILL_SLOTS; i++) {
            Skill s = data.equipped(i);
            int cd = s == null ? 0 : data.cooldown(s);
            if (lastCooldown[i] > 0 && cd == 0) {
                readyFlash[i] = 10;
                if (s.cooldown >= 40) ClientSfx.ui(ModSounds.PLAYER_SKILL_READY, 1f, 0.8f);
            }
            if (readyFlash[i] > 0) readyFlash[i]--;
            lastCooldown[i] = cd;
        }
        boolean drained = data.stamina() < 1f;
        if (drained && !staminaDrained && clientTicks - staminaCueTick > 60) {
            staminaCueTick = clientTicks;
            ClientSfx.ui(ModSounds.PLAYER_STAMINA_EMPTY, 1f, 1f);
        }
        staminaDrained = drained;
        tickLowHealth(mc, player);
        // Region titles: show the area name once the player has been in a new biome for a moment,
        // and not again for a while (biome borders are easy to cross back and forth).
        Holder<Biome> here = player.level().getBiome(player.blockPosition());
        ResourceKey<Biome> key = here.unwrapKey().orElse(null);
        if (key != null && !key.equals(region)) {
            if (key.equals(pendingRegion)) {
                if (++pendingTicks > 30) {
                    boolean first = region == null;
                    region = key;
                    Long shown = REGION_SHOWN.get(key);
                    if ((!first || clientTicks > 60) && (shown == null || clientTicks - shown > REGION_COOLDOWN)) {
                        regionTicks = 90;
                        REGION_SHOWN.put(key, clientTicks);
                        ClientSfx.ui(ModSounds.WORLD_DISCOVER, 1f, 0.5f);
                    }
                }
            } else {
                pendingRegion = key;
                pendingTicks = 0;
            }
        }
    }

    /** The heartbeat speeds up as health drops: every 30 ticks at 30% HP, every 16 at 10% and below. */
    private static void tickLowHealth(Minecraft mc, LocalPlayer player) {
        float frac = player.getHealth() / Math.max(1f, player.getMaxHealth());
        boolean low = frac < LOW_HP && player.isAlive() && !player.isCreative() && !player.isSpectator();
        lowHealth = low ? Mth.clamp((LOW_HP - frac) / (LOW_HP - CRITICAL_HP), 0f, 1f) : 0f;
        if (!low || mc.isPaused()) {
            if (!low) beatTimer = 0;
            return;
        }
        if (--beatTimer <= 0) {
            lastBeat = clientTicks;
            beatTimer = Math.round(Mth.lerp(lowHealth, 30f, 16f));
            ClientSfx.ui(ModSounds.PLAYER_HEARTBEAT, 1f + 0.08f * lowHealth, 0.55f + 0.35f * lowHealth);
        }
    }

    /** 0..1: the throb of the last heartbeat, a sharp rise and a quick fall. */
    private static float beat(float pt) {
        if (lowHealth <= 0) return 0;
        float t = clientTicks + pt - lastBeat;
        if (t < 0 || t > 10) return 0;
        float f = 1 - t / 10f;
        return f * f;
    }

    public static boolean hideVanillaBars() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && Frontier.isIn(p) && SlfConfig.FRONTIER_HUD.get() && SlfConfig.HIDE_VANILLA_BARS.get();
    }

    /** Whether the Frontier HUD draws the player's effects (so vanilla's icons, which sit on the minimap, give way). */
    public static boolean drawsEffects() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && Frontier.isIn(p) && SlfConfig.FRONTIER_HUD.get();
    }

    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gui.hud.isHidden()) return;
        Font font = mc.font;
        float pt = delta.getGameTimeDeltaPartialTick(false);
        int w = g.guiWidth();
        int h = g.guiHeight();
        Gfx.alpha = 1f;
        if (Frontier.isIn(player) && SlfConfig.FRONTIER_HUD.get()) {
            PlayerData data = ModAttachments.get(player);
            renderAvatar(g, font, player, data, pt);
            renderSkillBar(g, font, data, w, h, pt);
            // Screens like dialogue draw over the right side; keep it clear while one is open.
            boolean screenOpen = mc.gui.screen() != null && !(mc.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen);
            if (!screenOpen) {
                int ty = 6;
                if (SlfConfig.SHOW_MINIMAP.get()) ty = renderMinimap(g, font, player, w, pt) + 6;
                renderTracker(g, font, player, data, w, ty, pt);
            }
            renderTarget(g, font, mc, player, w);
            renderRegionTitle(g, font, w, h);
        }
        renderAnnouncement(g, font, w, h, pt);
    }

    // ---- Low-health vignette ---------------------------------------------------------

    /** GUI layer above the camera overlays: the screen edges redden and throb with the heartbeat. */
    public static void renderLowHealth(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (lowHealth <= 0 || mc.player == null || mc.gui.hud.isHidden() || !SlfConfig.IMPACT_SCREEN.get()
                || !Frontier.isIn(mc.player)) {
            return;
        }
        float pt = delta.getGameTimeDeltaPartialTick(false);
        float a = Mth.clamp(0.22f + 0.3f * lowHealth + 0.22f * beat(pt), 0f, 0.8f);
        int w = g.guiWidth(), h = g.guiHeight();
        int edge = Gfx.withAlpha(0x8A0A14, a), clear = Gfx.withAlpha(0x8A0A14, 0f);
        float bandY = h * (0.16f + 0.06f * lowHealth), bandX = w * (0.1f + 0.05f * lowHealth);
        Gfx.alpha = 1f;
        Gfx.quad(g, 0, 0, edge, 0, bandY, clear, w, bandY, clear, w, 0, edge);
        Gfx.quad(g, 0, h - bandY, clear, 0, h, edge, w, h, edge, w, h - bandY, clear);
        Gfx.quad(g, 0, 0, edge, 0, h, edge, bandX, h, clear, bandX, 0, clear);
        Gfx.quad(g, w - bandX, 0, clear, w - bandX, h, clear, w, h, edge, w, 0, edge);
        Gfx.flush(g);
    }

    // ---- Avatar frame ----------------------------------------------------------------

    private static void renderAvatar(GuiGraphicsExtractor g, Font font, LocalPlayer player, PlayerData data, float pt) {
        int x = 6, y = 6, fw = 154, fh = 42;
        Gfx.panel(g, x, y, fw, fh, 6, 0xD0142338, 0xD8080F1C, Theme.BORDER, Theme.ACCENT);
        float[] hex = Gfx.ngon(x + 17, y + 21, 12, 6, Mth.PI / 6);
        Gfx.poly(g, hex, y + 9, y + 33, 0xF02A5F88, 0xF0123A5A);
        Gfx.outline(g, hex, 1f, Theme.GOLD);
        Gfx.text(g, font, Component.literal("Lv"), x + 14.5f, y + 12f, 0.5f, 0xFFC9A030, false);
        String lv = String.valueOf(data.level());
        g.text(font, lv, x + 17 - font.width(lv) / 2, y + 19, Theme.GOLD, true);
        int bx = x + 34, bw = fw - 40;
        g.text(font, player.getDisplayName(), bx, y + 4, 0xFFFFFFFF, true);
        if (data.statPoints() > 0) {
            String sp = "+" + data.statPoints() + " SP";
            g.text(font, sp, x + fw - 6 - font.width(sp), y + 4, Theme.GREEN, true);
        }
        float hp = player.getHealth(), maxHp = player.getMaxHealth();
        if (lowHealth > 0) {
            // A red glow around the HP bar that throbs with the heartbeat.
            float b = beat(pt);
            int glow = Gfx.withAlpha(Theme.RED, 0.25f + 0.2f * lowHealth + 0.45f * b);
            Gfx.chamfer(g, bx - 1.5f, y + 13.5f, bw + 3, 9, 0, 0, 4, 0, glow, Gfx.withAlpha(Theme.RED, 0.1f + 0.3f * b));
        }
        int hpA = lowHealth > 0 ? Gfx.lerpColor(Theme.HP_A, 0xFFFFC0C8, beat(pt) * 0.6f) : Theme.HP_A;
        Gfx.bar(g, bx, y + 15, bw, 6, hp / maxHp, hpA, Theme.HP_B, Theme.BAR_BACK, Math.max(1, Math.round(maxHp / 10)));
        Gfx.text(g, font, Component.literal(Math.round(hp) + " / " + Math.round(maxHp)), bx + 2, y + 16f, 0.5f, 0xFFFFFFFF, true);
        float st = Mth.clamp(data.stamina() / data.maxStamina(), 0f, 1f);
        boolean low = st < 0.3f;
        Gfx.bar(g, bx, y + 24, bw, 4, st, low ? Theme.ST_LOW_A : Theme.ST_A, low ? Theme.ST_LOW_B : Theme.ST_B, Theme.BAR_BACK, 0);
        int need = PlayerData.expToNext(data.level());
        float exp = data.level() >= PlayerData.MAX_LEVEL ? 1f : Mth.clamp(data.exp() / (float) need, 0f, 1f);
        Gfx.bar(g, bx, y + 31, bw, 2, exp, Theme.EXP_A, Theme.EXP_B, Theme.BAR_BACK, 0);
        int armor = player.getArmorValue();
        if (armor > 0) {
            String a = "DEF " + armor;
            Gfx.text(g, font, Component.literal(a), x + fw - 6 - font.width(a) * 0.5f, y + 35.5f, 0.5f, Theme.TEXT_DIM, false);
        }
        renderConditions(g, font, player, data, x, y + fh + 3, fw);
    }

    /** Chips under the avatar frame: Lycagon's Mark, the Death Penalty, then every other effect as icon and time left. */
    private static void renderConditions(GuiGraphicsExtractor g, Font font, LocalPlayer player, PlayerData data, int x, int y, int fw) {
        int cx = x, cy = y;
        int maxX = x + fw + 60;
        if (data.cursed()) cx = chip(g, font, cx, cy, Component.translatable("hud.slfrontier.cursed"), Theme.RED);
        var active = player.getActiveEffects();
        if (active.isEmpty()) return;
        List<MobEffectInstance> effects = new ArrayList<>(active);
        effects.sort(Comparator.reverseOrder());
        for (MobEffectInstance e : effects) {
            if (!e.showIcon() || !IClientMobEffectExtensions.of(e).isVisibleInGui(e)) continue;
            boolean penalty = e.is(ModEffects.DEATH_PENALTY);
            String time = duration(e);
            Component label = penalty ? Component.translatable("effect.slfrontier.death_penalty") : null;
            int tw = 13 + font.width(time) + 4 + (label != null ? font.width(label) + 4 : 0);
            if (cx > x && cx + tw > maxX) {
                cx = x;
                cy += 14;
            }
            int color = penalty ? 0xFFB0B0C8 : e.getEffect().value().isBeneficial() ? Theme.GREEN : Theme.RED;
            Gfx.chamfer(g, cx, cy, tw, 11, 2, 0, 2, 0, Theme.withA(color, 0.35f), Theme.withA(color, 0.15f));
            // Blink like vanilla's icons in the last ten seconds.
            float alpha = e.endsWithin(200) ? 0.55f + 0.45f * Mth.cos(e.getDuration() * Mth.PI / 5f) : 1f;
            g.blitSprite(RenderPipelines.GUI_TEXTURED, Hud.getMobEffectSprite(e.getEffect()), cx + 2, cy + 1, 9, 9, ARGB.white(alpha));
            int tx = cx + 13;
            if (label != null) {
                g.text(font, label, tx, cy + 2, color, true);
                tx += font.width(label) + 4;
            }
            g.text(font, time, tx, cy + 2, e.endsWithin(200) ? 0xFFFFD0A0 : 0xFFFFFFFF, true);
            cx += tw + 3;
        }
    }

    private static String duration(MobEffectInstance e) {
        if (e.isInfiniteDuration()) return "∞";
        int s = (e.getDuration() + 19) / 20;
        if (s >= 3600) return (s / 3600) + "h";
        if (s >= 60) return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
        return s + "s";
    }

    private static int chip(GuiGraphicsExtractor g, Font font, int x, int y, Component text, int color) {
        int tw = font.width(text) + 8;
        Gfx.chamfer(g, x, y, tw, 11, 2, 0, 2, 0, Theme.withA(color, 0.35f), Theme.withA(color, 0.15f));
        g.text(font, text, x + 4, y + 2, color, true);
        return x + tw + 3;
    }

    // ---- Skill bar -------------------------------------------------------------------

    private static void renderSkillBar(GuiGraphicsExtractor g, Font font, PlayerData data, int w, int h, float pt) {
        int sx = w / 2 + 91 + 8;
        int sy = h - 23;
        for (int i = 0; i < PlayerData.SKILL_SLOTS; i++) {
            int bx = sx + i * 24;
            Skill skill = data.equipped(i);
            int accent = skill == null ? Theme.BORDER : Theme.skillColor(skill);
            float shake = denyShake[i] > 0 ? Math.max(0, denyShake[i] - pt) : 0;
            g.pose().pushMatrix();
            if (shake > 0) g.pose().translate(Mth.sin(shake * 2.6f) * shake * 0.7f, 0);
            Gfx.chamfer(g, bx, sy, 22, 22, 4, 0, 4, 0, 0xC0101C2C, 0xD0060C16);
            Gfx.outline(g, Gfx.chamferPoints(bx, sy, 22, 22, 4, 0, 4, 0), 1f, shake > 0 ? Gfx.lerpColor(accent, Theme.RED, shake / DENY_SHAKE) : accent);
            if (skill != null) {
                Gfx.icon(g, Theme.skillIcon(skill), bx + 2, sy + 2, 18, 32);
                int cd = data.cooldown(skill);
                // Overlays land above the icon: the GUI draws overlapping elements in submission order.
                if (cd > 0) {
                    float frac = Mth.clamp((cd - pt) / (float) Math.max(1, skill.cooldown), 0f, 1f);
                    g.enableScissor(bx + 1, sy + 1, bx + 21, sy + 21);
                    Gfx.pie(g, bx + 11, sy + 11, 16, frac, 0xB0060A14);
                    g.disableScissor();
                    String secs = cd >= 20 ? String.valueOf((cd + 19) / 20) : String.format("%.1f", cd / 20f);
                    g.text(font, secs, bx + 11 - font.width(secs) / 2, sy + 7, 0xFFFFFFFF, true);
                } else if (data.stamina() < skill.staminaCost) {
                    Gfx.chamfer(g, bx, sy, 22, 22, 4, 0, 4, 0, 0x60FF2020, 0x80801010);
                }
                if (readyFlash[i] > 0) {
                    float f = readyFlash[i] / 10f;
                    Gfx.chamfer(g, bx, sy, 22, 22, 4, 0, 4, 0, Theme.withA(accent, 0.6f * f), Theme.withA(0xFFFFFF, 0.3f * f));
                }
            }
            if (shake > 0) {
                float f = shake / DENY_SHAKE;
                Gfx.chamfer(g, bx, sy, 22, 22, 4, 0, 4, 0, Theme.withA(Theme.RED, 0.45f * f), Theme.withA(Theme.RED, 0.2f * f));
            }
            g.pose().popMatrix();
            // Key names: full size when they fit over the slot, half size for long ones ("Mouse Button 4").
            Component key = KeyBindings.SKILLS[i].getTranslatedKeyMessage();
            int kw = font.width(key);
            if (kw <= 22) {
                Gfx.text(g, font, key, bx + 1, sy - 9, 1f, Theme.TEXT_DIM, true);
            } else {
                Gfx.text(g, font, Component.literal(font.plainSubstrByWidth(key.getString(), 44)), bx + 1, sy - 5, 0.5f, Theme.TEXT_DIM, true);
            }
        }
    }

    // ---- Minimap ---------------------------------------------------------------------

    /** Draws the minimap in the top-right corner; returns its bottom edge. */
    private static int renderMinimap(GuiGraphicsExtractor g, Font font, LocalPlayer player, int w, float pt) {
        int size = SlfConfig.MINIMAP_SIZE.get();
        int x0 = w - size - 8, y0 = 8;
        Gfx.panel(g, x0 - 3, y0 - 3, size + 6, size + 6, 5, 0xD0142338, 0xD8080F1C, Theme.BORDER, Theme.ACCENT);
        MINIMAP.setRect(x0, y0, x0 + size, y0 + size);
        MINIMAP.centerX = Mth.lerp(pt, player.xo, player.getX());
        MINIMAP.centerZ = Mth.lerp(pt, player.zo, player.getZ());
        MINIMAP.zoom = 1f;
        boolean rotates = SlfConfig.MINIMAP_ROTATES.get();
        float yaw = player.getViewYRot(pt);
        MINIMAP.rotation = rotates ? -(yaw + 180f) * Mth.DEG_TO_RAD : 0f;
        MINIMAP.draw(g);
        g.enableScissor(x0, y0, x0 + size, y0 + size);
        for (ModNetwork.Marker m : ClientState.waypoints()) {
            float[] s = MINIMAP.toScreen(m.pos().getX() + 0.5, m.pos().getZ() + 0.5);
            if (MINIMAP.contains(s[0], s[1])) {
                MapView.icon(g, Theme.icon(m.kind() == ModNetwork.Marker.TOWN ? "town" : "waypoint"), s[0], s[1], 8, 0);
            }
        }
        g.disableScissor();
        ModNetwork.Marker quest = ClientState.questMarker();
        if (quest != null) {
            float[] s = MINIMAP.toScreen(quest.pos().getX() + 0.5, quest.pos().getZ() + 0.5);
            float[] c = MINIMAP.clampToEdge(s[0], s[1], 4);
            MapView.icon(g, Theme.icon("main_quest"), c[0], c[1], c[2] > 0 ? 7 : 9, 0);
        }
        MapView.icon(g, Theme.icon("player"), MINIMAP.screenCX(), MINIMAP.screenCY(), 9,
                rotates ? 0f : (yaw + 180f) * Mth.DEG_TO_RAD);
        // North marker
        float[] n = MINIMAP.toScreen(MINIMAP.centerX, MINIMAP.centerZ - 1000);
        float[] nc = MINIMAP.clampToEdge(n[0], n[1], 1);
        Gfx.text(g, font, Component.literal("N"), nc[0] - 2.5f, nc[1] - 3.5f, 1f, Theme.RED, true);
        // Region and coordinates, full size where they fit under the map.
        int by = y0 + size + 5;
        float cx = x0 + size / 2f;
        if (region != null) {
            Component name = Component.translatable(region.identifier().toLanguageKey("biome"));
            int tw = font.width(name);
            if (tw <= size + 6) {
                Gfx.text(g, font, name, cx - tw / 2f, by, 1f, Theme.ACCENT, true);
                by += 10;
            } else {
                Gfx.text(g, font, name, cx - tw / 4f, by, 0.5f, Theme.ACCENT, true);
                by += 6;
            }
        }
        String coords = player.getBlockX() + ", " + player.getBlockY() + ", " + player.getBlockZ();
        int cw = font.width(coords);
        if (cw <= size + 6) {
            Gfx.text(g, font, Component.literal(coords), cx - cw / 2f, by, 1f, Theme.TEXT_DIM, true);
            return by + 9;
        }
        Gfx.text(g, font, Component.literal(coords), cx - cw / 4f, by, 0.5f, Theme.TEXT_DIM, true);
        return by + 5;
    }

    // ---- Quest tracker ---------------------------------------------------------------

    private static void renderTracker(GuiGraphicsExtractor g, Font font, LocalPlayer player, PlayerData data, int w, int y, float pt) {
        QuestStage stage = data.stage();
        int qw = 156;
        int qx = w - qw - 8;
        SideQuest side = SideQuest.byId(data.tracked());
        if (stage != QuestStage.NOT_STARTED && (side == null || stage != QuestStage.COMPLETE)) {
            ModNetwork.Marker target = ClientState.questMarker();
            Component objective = Component.translatable(stage.objectiveKey());
            int progress = stage.goal > 0 ? data.questProgress() : -1;
            List<QuestSteps.Step> steps = QuestSteps.of(stage);
            if (!steps.isEmpty()) {
                // Show the step the player is on, not the whole chapter.
                QuestSteps.Step step = steps.get(Math.min(data.questStep(), steps.size() - 1));
                objective = Component.translatable(QuestSteps.textKey(stage, step));
                if (!step.counts()) progress = -1;
            }
            y = trackerEntry(g, font, player, 0, qx, y, qw, "main_quest", Component.translatable(stage.titleKey()),
                    objective, progress, progress >= 0 ? stage.goal : 0, target, Theme.GOLD, pt) + 4;
        }
        if (side != null && data.sideState(side) == PlayerData.SideState.ACTIVE) {
            trackerEntry(g, font, player, 1, qx, y, qw, "side_quest", Component.translatable(side.titleKey()),
                    Component.translatable(side.objectiveKey()), data.sideProgress(side), side.goal, null, Theme.ACCENT, pt);
        }
    }

    private static List<FormattedCharSequence> wrap(int slot, Font font, Component text, int width) {
        Wrap c = WRAPS[slot];
        Language language = Language.getInstance();
        if (c.width != width || c.language != language || !text.equals(c.text)) {
            c.text = text;
            c.width = width;
            c.language = language;
            c.lines = font.split(text, width);
        }
        return c.lines;
    }

    private static int trackerEntry(GuiGraphicsExtractor g, Font font, LocalPlayer player, int slot, int x, int y, int w, String icon,
                                    Component title, Component objective, int progress, int goal, @Nullable ModNetwork.Marker target,
                                    int color, float pt) {
        List<FormattedCharSequence> lines = wrap(slot, font, objective, w - 12);
        int hgt = 14 + lines.size() * 9 + (goal > 0 ? 6 : 0) + (target != null ? 10 : 0) + 3;
        Gfx.chamfer(g, x, y, w, hgt, 4, 0, 4, 0, 0xB0101C2C, 0x90060C16);
        Gfx.rect(g, x, y + 4, x + 1.5f, y + hgt, color);
        Gfx.icon(g, Theme.icon(icon), x + 4, y + 3, 9, 16);
        g.text(font, ellipsize(font, title.getString(), w - 18), x + 16, y + 4, color, true);
        int ly = y + 15;
        for (FormattedCharSequence line : lines) {
            g.text(font, line, x + 6, ly, 0xFFD8E4F0, false);
            ly += 9;
        }
        if (goal > 0) {
            Gfx.bar(g, x + 6, ly + 1, w - 40, 3, Math.min(1f, progress / (float) goal), color, Gfx.lerpColor(color, 0xFF000000, 0.35f),
                    Theme.BAR_BACK, Math.min(goal, 12));
            String p = Math.min(progress, goal) + "/" + goal;
            g.text(font, p, x + w - 6 - font.width(p), ly - 1, Theme.TEXT, false);
            ly += 6;
        }
        if (target != null) {
            double px = Mth.lerp(pt, player.xo, player.getX()), pz = Mth.lerp(pt, player.zo, player.getZ());
            double dx = target.pos().getX() + 0.5 - px, dz = target.pos().getZ() + 0.5 - pz;
            double dist = Math.sqrt(dx * dx + dz * dz);
            float bearing = (float) Math.atan2(dz, dx);
            float facing = (player.getViewYRot(pt) + 90f) * Mth.DEG_TO_RAD;
            MapView.icon(g, Theme.icon("quest_arrow"), x + 10, ly + 5, 8, bearing - facing);
            Component line = Component.translatable("hud.slfrontier.distance", QuestPointer.distance(dist),
                    Component.translatable(target.nameKey()));
            g.text(font, ellipsize(font, line.getString(), w - 21), x + 17, ly + 1, Theme.TEXT_DIM, false);
        }
        return y + hgt;
    }

    /** Cuts text to a width, with an ellipsis when anything was cut. */
    private static String ellipsize(Font font, String text, int width) {
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, width - font.width("…")) + "…";
    }

    // ---- Target ----------------------------------------------------------------------

    private static void renderTarget(GuiGraphicsExtractor g, Font font, Minecraft mc, LocalPlayer player, int w) {
        Entity target = mc.crosshairPickEntity;
        if (!(target instanceof LivingEntity living) || !(target instanceof LeveledMob leveled) || target instanceof FrontierBoss) return;
        Component name = Component.translatable("hud.slfrontier.target", living.getDisplayName(), leveled.frontierLevel());
        int tw = Math.max(110, font.width(name) + 16);
        int tx = w / 2 - tw / 2;
        // Below any boss bars (an add during a boss fight), else at the top.
        int ty = bossBottom > 0 ? bossBottom - 12 : 4;
        Gfx.chamfer(g, tx, ty, tw, 22, 4, 0, 4, 0, 0xC0101C2C, 0xD0060C16);
        Gfx.outline(g, Gfx.chamferPoints(tx, ty, tw, 22, 4, 0, 4, 0), 1f, 0xFF7A2A36);
        int lvDiff = leveled.frontierLevel() - ModAttachments.get(player).level();
        int nameColor = lvDiff >= 10 ? Theme.RED : lvDiff >= 4 ? 0xFFFFB050 : 0xFFFFFFFF;
        g.text(font, name, w / 2 - font.width(name) / 2, ty + 3, nameColor, true);
        Gfx.bar(g, tx + 5, ty + 14, tw - 10, 4, Mth.clamp(living.getHealth() / living.getMaxHealth(), 0f, 1f), Theme.HP_A, Theme.HP_B,
                Theme.BAR_BACK, 10);
        if (SkillExecutor.isBehind(player, living)) {
            Component weak = Component.translatable("hud.slfrontier.weak_point").withStyle(ChatFormatting.BOLD);
            g.text(font, weak, w / 2 - font.width(weak) / 2, ty + 25, Theme.GOLD, true);
        }
    }

    // ---- Region title ----------------------------------------------------------------

    private static void renderRegionTitle(GuiGraphicsExtractor g, Font font, int w, int h) {
        if (regionTicks <= 0 || region == null) return;
        float a = Math.min(1f, Math.min(regionTicks / 20f, (90 - regionTicks) / 12f));
        Component name = Component.translatable(region.identifier().toLanguageKey("biome"));
        Component sub = Component.translatable("hud.slfrontier.region");
        int y = h / 5;
        float tw = font.width(name) * 2f;
        Gfx.alpha = a;
        Gfx.hgrad(g, w / 2f - tw / 2 - 40, y + 19, w / 2f, y + 20, 0x005FE0FF, 0xC05FE0FF);
        Gfx.hgrad(g, w / 2f, y + 19, w / 2f + tw / 2 + 40, y + 20, 0xC05FE0FF, 0x005FE0FF);
        Gfx.text(g, font, sub, w / 2f - font.width(sub) / 2f, y - 11, 1f, Theme.TEXT_DIM, true);
        Gfx.text(g, font, name, w / 2f - tw / 2, y, 2f, 0xFFFFFFFF, true);
        Gfx.alpha = 1f;
    }

    // ---- Announcements ---------------------------------------------------------------

    private static void renderAnnouncement(GuiGraphicsExtractor g, Font font, int w, int h, float pt) {
        if (announceTicks <= 0 || announcement == null) return;
        float t = ANNOUNCE_LENGTH - announceTicks + pt;
        float in = Mth.clamp(t / 8f, 0f, 1f);
        float out = Mth.clamp((announceTicks - pt) / ANNOUNCE_FADE_OUT, 0f, 1f);
        float a = Math.min(in, out);
        Component title = announceTitle;
        Component sub = announceSub;
        int y = h / 3 - 12;
        float scale = 2f;
        float tw = font.width(title) * scale;
        float band = Math.max(tw, font.width(sub)) / 2 + 50;
        float grow = band * (0.6f + 0.4f * in);
        Gfx.alpha = a;
        Gfx.hgrad(g, w / 2f - grow, y - 6, w / 2f, y + 32, 0x00060C16, 0xC0060C16);
        Gfx.hgrad(g, w / 2f, y - 6, w / 2f + grow, y + 32, 0xC0060C16, 0x00060C16);
        int c = announcement.color() | 0xFF000000;
        Gfx.hgrad(g, w / 2f - grow, y - 7, w / 2f, y - 6, Theme.withA(c, 0f), c);
        Gfx.hgrad(g, w / 2f, y - 7, w / 2f + grow, y - 6, c, Theme.withA(c, 0f));
        Gfx.hgrad(g, w / 2f - grow, y + 32, w / 2f, y + 33, Theme.withA(c, 0f), c);
        Gfx.hgrad(g, w / 2f, y + 32, w / 2f + grow, y + 33, c, Theme.withA(c, 0f));
        Gfx.text(g, font, title, w / 2f - tw / 2 + (1 - in) * 20, y, scale, c, true);
        Gfx.text(g, font, sub, w / 2f - font.width(sub) / 2f, y + 20, 1f, 0xFFFFFFFF, true);
        Gfx.alpha = 1f;
    }

    // ---- Boss bar --------------------------------------------------------------------

    /** Draws Frontier bosses' health bars in the Frontier style; returns false for other bosses. */
    public static boolean renderBossBar(GuiGraphicsExtractor g, LerpingBossEvent event, int x, int y) {
        if (!(event.getName().getContents() instanceof TranslatableContents tc) || !tc.getKey().startsWith("entity.slfrontier.")) {
            return false;
        }
        Font font = Minecraft.getInstance().font;
        int w = 240;
        int cx = x + 91;
        int bx = cx - w / 2;
        int by = y + 2;
        Component name = event.getName().copy().withStyle(ChatFormatting.BOLD);
        g.text(font, name, cx - font.width(name) / 2, y - 7, 0xFFFFFFFF, true);
        int color = switch (event.getColor()) {
            case PURPLE -> 0xFFB04ADF;
            case BLUE -> 0xFF4AA8FF;
            case RED -> 0xFFFF4A5A;
            case GREEN -> 0xFF5ADF6A;
            case YELLOW -> 0xFFFFD34F;
            case PINK -> 0xFFFF7FC8;
            // Kirin: lightning gold, so a full bar doesn't read as an empty pale track.
            case WHITE -> 0xFFF0D870;
            default -> 0xFFE0E0F0;
        };
        float progress = Mth.clamp(event.getProgress(), 0f, 1f);
        String pct = Mth.ceil(progress * 100) + "%";
        g.text(font, pct, bx + w - font.width(pct), y - 7, Theme.TEXT_DIM, true);
        float trail = trail(event.getId(), progress);
        Gfx.chamfer(g, bx - 3, by - 2, w + 6, 10, 3, 0, 3, 0, 0xD0101C2C, 0xD0060C16);
        // The damage just taken drains away behind the fill, like the monsters' health bars.
        Gfx.bar(g, bx, by, w, 6, trail, TRAIL_COLOR, Gfx.withAlpha(TRAIL_COLOR, 0.85f), 0xE0050A12, 0);
        Gfx.bar(g, bx, by, w, 6, progress, color, Gfx.lerpColor(color, 0xFF000000, 0.45f), 0, 10);
        Gfx.outline(g, Gfx.chamferPoints(bx - 3, by - 2, w + 6, 10, 3, 0, 3, 0), 1f, Gfx.lerpColor(color, 0xFFFFFFFF, 0.2f));
        // Phase thresholds: where the fight changes.
        for (float f : phases(tc.getKey())) {
            float nx = bx + w * f;
            int c = progress > f ? Theme.GOLD : 0x90FFFFFF;
            Gfx.rectQueued(g, nx - 0.5f, by - 1, nx + 0.5f, by + 7, c);
            Gfx.tri(g, nx - 2.5f, by - 4, c, nx + 2.5f, by - 4, c, nx, by - 1, c);
        }
        Gfx.flush(g);
        return true;
    }

    private static float[] phases(String key) {
        return switch (key) {
            case "entity.slfrontier.wezaemon" -> WEZAEMON_PHASES;
            case "entity.slfrontier.lycagon", "entity.slfrontier.lycagon_shadow", "entity.slfrontier.kirin" -> HALF;
            case "entity.slfrontier.mud_digger" -> MUD_DIGGER_PHASES;
            default -> NO_PHASES;
        };
    }

    /** The boss bar's damage trail: holds for half a second after a hit, then drains to the health. */
    private static float trail(UUID id, float progress) {
        long now = Util.getMillis();
        Trail t = TRAILS.get(id);
        if (t == null) {
            TRAILS.put(id, new Trail(progress, now));
            return progress;
        }
        if (progress < t.last - 0.0005f) t.holdUntil = now + 500;
        if (progress >= t.trail) {
            t.trail = progress;
        } else if (now > t.holdUntil) {
            float dt = Math.min(0.1f, (now - t.lastMs) / 1000f);
            t.trail = Math.max(progress, t.trail - Math.max(0.03f, (t.trail - progress) * 3f) * dt);
        }
        t.last = progress;
        t.lastMs = now;
        t.seen = now;
        return t.trail;
    }
}
