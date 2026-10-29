package com.slfrontier.client;

import com.slfrontier.SlfConfig;
import com.slfrontier.client.map.MapView;
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
import com.slfrontier.skill.Skill;
import com.slfrontier.skill.SkillExecutor;
import com.slfrontier.world.Frontier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.biome.Biome;

import java.util.List;

/**
 * The Frontier HUD: avatar frame (level, HP, stamina, EXP), skill bar with radial
 * cooldowns, minimap, quest tracker with distance to the objective, target frame,
 * region titles, announcements and the restyled boss bar.
 */
public final class FrontierHud {
    private static final MapView MINIMAP = new MapView();

    private static String announceTitle;
    private static String announceSub;
    private static int announceColor;
    private static int announceTicks;
    private static final int ANNOUNCE_LENGTH = 80;

    private static final int[] lastCooldown = new int[PlayerData.SKILL_SLOTS];
    private static final int[] readyFlash = new int[PlayerData.SKILL_SLOTS];

    private static ResourceKey<Biome> region;
    private static ResourceKey<Biome> pendingRegion;
    private static int pendingTicks;
    private static int regionTicks;
    private static long clientTicks;

    private FrontierHud() {
    }

    public static void announce(String title, String subtitle, int color) {
        announceTitle = title;
        announceSub = subtitle;
        announceColor = color;
        announceTicks = ANNOUNCE_LENGTH;
    }

    public static void tick() {
        clientTicks++;
        if (announceTicks > 0 && !com.slfrontier.client.overlay.TravelOverlay.isActive()) announceTicks--;
        if (regionTicks > 0) regionTicks--;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !Frontier.isIn(player)) {
            region = null;
            return;
        }
        PlayerData data = ModAttachments.get(player);
        for (int i = 0; i < PlayerData.SKILL_SLOTS; i++) {
            Skill s = data.equipped(i);
            int cd = s == null ? 0 : data.cooldown(s);
            if (lastCooldown[i] > 0 && cd == 0) readyFlash[i] = 10;
            if (readyFlash[i] > 0) readyFlash[i]--;
            lastCooldown[i] = cd;
        }
        // Region titles: show the area name once the player has been in a new biome for a moment.
        Holder<Biome> here = player.level().getBiome(player.blockPosition());
        ResourceKey<Biome> key = here.unwrapKey().orElse(null);
        if (key != null && !key.equals(region)) {
            if (key.equals(pendingRegion)) {
                if (++pendingTicks > 30) {
                    boolean first = region == null;
                    region = key;
                    if (!first || clientTicks > 60) regionTicks = 90;
                }
            } else {
                pendingRegion = key;
                pendingTicks = 0;
            }
        }
    }

    public static boolean hideVanillaBars() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && Frontier.isIn(p) && SlfConfig.FRONTIER_HUD.get() && SlfConfig.HIDE_VANILLA_BARS.get();
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui) return;
        Font font = mc.font;
        float pt = delta.getGameTimeDeltaPartialTick(false);
        int w = g.guiWidth();
        int h = g.guiHeight();
        Gfx.alpha = 1f;
        if (Frontier.isIn(player) && SlfConfig.FRONTIER_HUD.get()) {
            PlayerData data = ModAttachments.get(player);
            renderAvatar(g, font, player, data);
            renderSkillBar(g, font, data, w, h, pt);
            // Screens like dialogue draw over the right side; keep it clear while one is open.
            boolean screenOpen = mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen);
            if (!screenOpen) {
                int ty = 6;
                if (SlfConfig.SHOW_MINIMAP.get()) ty = renderMinimap(g, font, player, w, pt) + 6;
                renderTracker(g, font, player, data, w, ty);
            }
            renderTarget(g, font, mc, player, w);
            renderRegionTitle(g, font, w, h);
        }
        renderAnnouncement(g, font, w, h, pt);
    }

    // ---- Avatar frame ----------------------------------------------------------------

    private static void renderAvatar(GuiGraphics g, Font font, LocalPlayer player, PlayerData data) {
        int x = 6, y = 6, fw = 154, fh = 42;
        Gfx.panel(g, x, y, fw, fh, 6, 0xD0142338, 0xD8080F1C, Theme.BORDER, Theme.ACCENT);
        float[] hex = Gfx.ngon(x + 17, y + 21, 12, 6, Mth.PI / 6);
        Gfx.poly(g, hex, y + 9, y + 33, 0xF02A5F88, 0xF0123A5A);
        Gfx.outline(g, hex, 1f, Theme.GOLD);
        Gfx.text(g, font, Component.literal("Lv"), x + 13.5f, y + 12f, 0.6f, 0xFFC9A030, false);
        String lv = String.valueOf(data.level());
        g.drawString(font, lv, x + 17 - font.width(lv) / 2, y + 19, Theme.GOLD, true);
        int bx = x + 34, bw = fw - 40;
        g.drawString(font, player.getDisplayName(), bx, y + 4, 0xFFFFFFFF, true);
        if (data.statPoints() > 0) {
            String sp = "+" + data.statPoints() + " SP";
            g.drawString(font, sp, x + fw - 6 - font.width(sp), y + 4, Theme.GREEN, true);
        }
        float hp = player.getHealth(), maxHp = player.getMaxHealth();
        Gfx.bar(g, bx, y + 15, bw, 6, hp / maxHp, Theme.HP_A, Theme.HP_B, Theme.BAR_BACK, Math.max(1, Math.round(maxHp / 10)));
        Gfx.text(g, font, Component.literal(Math.round(hp) + " / " + Math.round(maxHp)), bx + 2, y + 15.5f, 0.6f, 0xFFFFFFFF, true);
        float st = Mth.clamp(data.stamina() / data.maxStamina(), 0f, 1f);
        boolean low = st < 0.3f;
        Gfx.bar(g, bx, y + 24, bw, 4, st, low ? Theme.ST_LOW_A : Theme.ST_A, low ? Theme.ST_LOW_B : Theme.ST_B, Theme.BAR_BACK, 0);
        int need = PlayerData.expToNext(data.level());
        float exp = data.level() >= PlayerData.MAX_LEVEL ? 1f : Mth.clamp(data.exp() / (float) need, 0f, 1f);
        Gfx.bar(g, bx, y + 31, bw, 2, exp, Theme.EXP_A, Theme.EXP_B, Theme.BAR_BACK, 0);
        int armor = player.getArmorValue();
        if (armor > 0) {
            String a = "DEF " + armor;
            Gfx.text(g, font, Component.literal(a), x + fw - 6 - font.width(a) * 0.6f, y + 35f, 0.6f, Theme.TEXT_DIM, false);
        }
        // Condition chips
        int cx = x;
        int cy = y + fh + 3;
        if (data.cursed()) cx = chip(g, font, cx, cy, Component.translatable("hud.slfrontier.cursed"), Theme.RED);
        if (player.hasEffect(com.slfrontier.registry.ModEffects.DEATH_PENALTY)) {
            chip(g, font, cx, cy, Component.translatable("effect.slfrontier.death_penalty"), 0xFFB0B0C8);
        }
    }

    private static int chip(GuiGraphics g, Font font, int x, int y, Component text, int color) {
        int tw = font.width(text) + 8;
        Gfx.chamfer(g, x, y, tw, 11, 2, 0, 2, 0, Theme.withA(color, 0.35f), Theme.withA(color, 0.15f));
        g.drawString(font, text, x + 4, y + 2, color, true);
        return x + tw + 3;
    }

    // ---- Skill bar -------------------------------------------------------------------

    private static void renderSkillBar(GuiGraphics g, Font font, PlayerData data, int w, int h, float pt) {
        int sx = w / 2 + 91 + 8;
        int sy = h - 23;
        for (int i = 0; i < PlayerData.SKILL_SLOTS; i++) {
            int bx = sx + i * 24;
            Skill skill = data.equipped(i);
            int accent = skill == null ? Theme.BORDER : Theme.skillColor(skill);
            Gfx.chamfer(g, bx, sy, 22, 22, 4, 0, 4, 0, 0xC0101C2C, 0xD0060C16);
            Gfx.outline(g, Gfx.chamferPoints(bx, sy, 22, 22, 4, 0, 4, 0), 1f, accent);
            if (skill != null) {
                Gfx.icon(g, Theme.skillIcon(skill), bx + 2, sy + 2, 18, 32);
                int cd = data.cooldown(skill);
                if (cd > 0) {
                    float frac = Mth.clamp((cd - pt) / (float) Math.max(1, skill.cooldown), 0f, 1f);
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 200);
                    g.enableScissor(bx + 1, sy + 1, bx + 21, sy + 21);
                    Gfx.pie(g, bx + 11, sy + 11, 16, frac, 0xB0060A14);
                    g.disableScissor();
                    String secs = cd >= 20 ? String.valueOf((cd + 19) / 20) : String.format("%.1f", cd / 20f);
                    g.drawString(font, secs, bx + 11 - font.width(secs) / 2, sy + 7, 0xFFFFFFFF, true);
                    g.pose().popPose();
                } else if (data.stamina() < skill.staminaCost) {
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 200);
                    Gfx.chamfer(g, bx, sy, 22, 22, 4, 0, 4, 0, 0x60FF2020, 0x80801010);
                    g.pose().popPose();
                }
                if (readyFlash[i] > 0) {
                    float f = readyFlash[i] / 10f;
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 210);
                    Gfx.chamfer(g, bx, sy, 22, 22, 4, 0, 4, 0, Theme.withA(accent, 0.6f * f), Theme.withA(0xFFFFFF, 0.3f * f));
                    g.pose().popPose();
                }
            }
            String key = KeyBindings.SKILLS[i].getTranslatedKeyMessage().getString();
            g.pose().pushPose();
            g.pose().translate(0, 0, 250);
            Gfx.text(g, font, Component.literal(key), bx + 1, sy - 7, 0.75f, Theme.TEXT_DIM, true);
            g.pose().popPose();
        }
    }

    // ---- Minimap ---------------------------------------------------------------------

    /** Draws the minimap in the top-right corner; returns its bottom edge. */
    private static int renderMinimap(GuiGraphics g, Font font, LocalPlayer player, int w, float pt) {
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
        Gfx.text(g, font, Component.literal("N"), nc[0] - 2.2f, nc[1] - 3f, 0.75f, Theme.RED, true);
        // Region and coordinates
        int by = y0 + size + 5;
        if (region != null) {
            Component name = Component.translatable("biome." + region.location().getNamespace() + "." + region.location().getPath());
            float tw = font.width(name) * 0.75f;
            Gfx.text(g, font, name, x0 + size / 2f - tw / 2, by, 0.75f, Theme.ACCENT, true);
            by += 8;
        }
        String coords = player.getBlockX() + ", " + player.getBlockY() + ", " + player.getBlockZ();
        float cw = font.width(coords) * 0.6f;
        Gfx.text(g, font, Component.literal(coords), x0 + size / 2f - cw / 2, by, 0.6f, Theme.TEXT_DIM, true);
        return by + 6;
    }

    // ---- Quest tracker ---------------------------------------------------------------

    private static void renderTracker(GuiGraphics g, Font font, LocalPlayer player, PlayerData data, int w, int y) {
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
            y = trackerEntry(g, font, player, qx, y, qw, "main_quest", Component.translatable(stage.titleKey()),
                    objective, progress, progress >= 0 ? stage.goal : 0, target, Theme.GOLD) + 4;
        }
        if (side != null && data.sideState(side) == PlayerData.SideState.ACTIVE) {
            trackerEntry(g, font, player, qx, y, qw, "side_quest", Component.translatable(side.titleKey()),
                    Component.translatable(side.objectiveKey()), data.sideProgress(side), side.goal, null, Theme.ACCENT);
        }
    }

    private static int trackerEntry(GuiGraphics g, Font font, LocalPlayer player, int x, int y, int w, String icon, Component title,
                                    Component objective, int progress, int goal, ModNetwork.Marker target, int color) {
        List<FormattedCharSequence> lines = font.split(objective, w - 12);
        int hgt = 14 + lines.size() * 9 + (goal > 0 ? 6 : 0) + (target != null ? 10 : 0) + 3;
        Gfx.chamfer(g, x, y, w, hgt, 4, 0, 4, 0, 0xB0101C2C, 0x90060C16);
        Gfx.rect(g, x, y + 4, x + 1.5f, y + hgt, color);
        Gfx.icon(g, Theme.icon(icon), x + 4, y + 3, 9, 16);
        g.drawString(font, font.plainSubstrByWidth(title.getString(), w - 18), x + 16, y + 4, color, true);
        int ly = y + 15;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, x + 6, ly, 0xFFD8E4F0, false);
            ly += 9;
        }
        if (goal > 0) {
            Gfx.bar(g, x + 6, ly + 1, w - 40, 3, Math.min(1f, progress / (float) goal), color, Gfx.lerpColor(color, 0xFF000000, 0.35f),
                    Theme.BAR_BACK, Math.min(goal, 12));
            String p = Math.min(progress, goal) + "/" + goal;
            g.drawString(font, p, x + w - 6 - font.width(p), ly - 1, Theme.TEXT, false);
            ly += 6;
        }
        if (target != null) {
            double dx = target.pos().getX() + 0.5 - player.getX(), dz = target.pos().getZ() + 0.5 - player.getZ();
            int dist = (int) Math.sqrt(dx * dx + dz * dz);
            float bearing = (float) Math.atan2(dz, dx);
            float facing = (player.getYRot() + 90f) * Mth.DEG_TO_RAD;
            MapView.icon(g, Theme.icon("quest_arrow"), x + 10, ly + 5, 8, bearing - facing);
            g.drawString(font, Component.translatable("hud.slfrontier.distance", dist, Component.translatable(target.nameKey())),
                    x + 17, ly + 1, Theme.TEXT_DIM, false);
        }
        return y + hgt;
    }

    // ---- Target ----------------------------------------------------------------------

    private static void renderTarget(GuiGraphics g, Font font, Minecraft mc, LocalPlayer player, int w) {
        Entity target = mc.crosshairPickEntity;
        if (!(target instanceof LivingEntity living) || !(target instanceof LeveledMob leveled) || target instanceof FrontierBoss) return;
        Component name = Component.translatable("hud.slfrontier.target", living.getDisplayName(), leveled.frontierLevel());
        int tw = Math.max(110, font.width(name) + 16);
        int tx = w / 2 - tw / 2;
        Gfx.chamfer(g, tx, 4, tw, 22, 4, 0, 4, 0, 0xC0101C2C, 0xD0060C16);
        Gfx.outline(g, Gfx.chamferPoints(tx, 4, tw, 22, 4, 0, 4, 0), 1f, 0xFF7A2A36);
        int lvDiff = leveled.frontierLevel() - ModAttachments.get(player).level();
        int nameColor = lvDiff >= 10 ? Theme.RED : lvDiff >= 4 ? 0xFFFFB050 : 0xFFFFFFFF;
        g.drawString(font, name, w / 2 - font.width(name) / 2, 7, nameColor, true);
        Gfx.bar(g, tx + 5, 18, tw - 10, 4, Mth.clamp(living.getHealth() / living.getMaxHealth(), 0f, 1f), Theme.HP_A, Theme.HP_B,
                Theme.BAR_BACK, 10);
        if (SkillExecutor.isBehind(player, living)) {
            Component weak = Component.translatable("hud.slfrontier.weak_point").withStyle(ChatFormatting.BOLD);
            int ww = font.width(weak);
            Gfx.text(g, font, weak, w / 2f - ww * 0.35f, 28, 0.7f, Theme.GOLD, true);
        }
    }

    // ---- Region title ----------------------------------------------------------------

    private static void renderRegionTitle(GuiGraphics g, Font font, int w, int h) {
        if (regionTicks <= 0 || region == null) return;
        float a = Math.min(1f, Math.min(regionTicks / 20f, (90 - regionTicks) / 12f));
        Component name = Component.translatable("biome." + region.location().getNamespace() + "." + region.location().getPath());
        Component sub = Component.translatable("hud.slfrontier.region");
        int y = h / 5;
        float tw = font.width(name) * 1.6f;
        Gfx.alpha = a;
        Gfx.hgrad(g, w / 2f - tw / 2 - 40, y + 15, w / 2f, y + 16, 0x005FE0FF, 0xC05FE0FF);
        Gfx.hgrad(g, w / 2f, y + 15, w / 2f + tw / 2 + 40, y + 16, 0xC05FE0FF, 0x005FE0FF);
        Gfx.text(g, font, sub, w / 2f - font.width(sub) * 0.35f, y - 9, 0.7f, Theme.TEXT_DIM, true);
        Gfx.text(g, font, name, w / 2f - tw / 2, y, 1.6f, 0xFFFFFFFF, true);
        Gfx.alpha = 1f;
    }

    // ---- Announcements ---------------------------------------------------------------

    private static void renderAnnouncement(GuiGraphics g, Font font, int w, int h, float pt) {
        if (announceTicks <= 0 || announceTitle == null) return;
        float t = ANNOUNCE_LENGTH - announceTicks + pt;
        float in = Mth.clamp(t / 8f, 0f, 1f);
        float out = Mth.clamp((announceTicks - pt) / 15f, 0f, 1f);
        float a = Math.min(in, out);
        Component title = Component.translatable(announceTitle).withStyle(ChatFormatting.BOLD);
        Component sub = Component.translatable(announceSub);
        int y = h / 3 - 12;
        float scale = 2f;
        float tw = font.width(title) * scale;
        float band = Math.max(tw, font.width(sub)) / 2 + 50;
        float grow = band * (0.6f + 0.4f * in);
        Gfx.alpha = a;
        Gfx.hgrad(g, w / 2f - grow, y - 6, w / 2f, y + 32, 0x00060C16, 0xC0060C16);
        Gfx.hgrad(g, w / 2f, y - 6, w / 2f + grow, y + 32, 0xC0060C16, 0x00060C16);
        int c = announceColor | 0xFF000000;
        Gfx.hgrad(g, w / 2f - grow, y - 7, w / 2f, y - 6, Theme.withA(c, 0f), c);
        Gfx.hgrad(g, w / 2f, y - 7, w / 2f + grow, y - 6, c, Theme.withA(c, 0f));
        Gfx.hgrad(g, w / 2f - grow, y + 32, w / 2f, y + 33, Theme.withA(c, 0f), c);
        Gfx.hgrad(g, w / 2f, y + 32, w / 2f + grow, y + 33, c, Theme.withA(c, 0f));
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        Gfx.text(g, font, title, w / 2f - tw / 2 + (1 - in) * 20, y, scale, c, true);
        Gfx.text(g, font, sub, w / 2f - font.width(sub) / 2f, y + 20, 1f, 0xFFFFFFFF, true);
        g.pose().popPose();
        Gfx.alpha = 1f;
    }

    // ---- Boss bar --------------------------------------------------------------------

    /** Draws Frontier bosses' health bars in the Frontier style; returns false for other bosses. */
    public static boolean renderBossBar(GuiGraphics g, LerpingBossEvent event, int x, int y) {
        if (!(event.getName().getContents() instanceof TranslatableContents tc) || !tc.getKey().startsWith("entity.slfrontier.")) {
            return false;
        }
        Font font = Minecraft.getInstance().font;
        int w = 240;
        int cx = x + 91;
        int bx = cx - w / 2;
        Component name = event.getName().copy().withStyle(ChatFormatting.BOLD);
        g.drawString(font, name, cx - font.width(name) / 2, y - 9, 0xFFFFFFFF, true);
        int color = switch (event.getColor()) {
            case PURPLE -> 0xFFB04ADF;
            case BLUE -> 0xFF4AA8FF;
            case RED -> 0xFFFF4A5A;
            case GREEN -> 0xFF5ADF6A;
            case YELLOW -> 0xFFFFD34F;
            default -> 0xFFE0E0F0;
        };
        Gfx.chamfer(g, bx - 3, y - 2, w + 6, 10, 3, 0, 3, 0, 0xD0101C2C, 0xD0060C16);
        Gfx.bar(g, bx, y, w, 6, event.getProgress(), color, Gfx.lerpColor(color, 0xFF000000, 0.45f), 0xE0050A12, 10);
        Gfx.outline(g, Gfx.chamferPoints(bx - 3, y - 2, w + 6, 10, 3, 0, 3, 0), 1f, Gfx.lerpColor(color, 0xFFFFFFFF, 0.2f));
        return true;
    }
}
