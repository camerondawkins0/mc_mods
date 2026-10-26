package com.slfrontier.client;

import com.slfrontier.data.PlayerData;
import com.slfrontier.entity.FrontierBoss;
import com.slfrontier.entity.LeveledMob;
import com.slfrontier.quest.QuestStage;
import com.slfrontier.registry.ModAttachments;
import com.slfrontier.registry.ModItems;
import com.slfrontier.skill.Skill;
import com.slfrontier.world.Frontier;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The Frontier HUD: level, EXP and stamina, skill slots, quest tracker and target info. */
public final class FrontierHud {
    private static String announceTitle;
    private static String announceSub;
    private static int announceColor;
    private static int announceTicks;

    private FrontierHud() {
    }

    public static void announce(String title, String subtitle, int color) {
        announceTitle = title;
        announceSub = subtitle;
        announceColor = color;
        announceTicks = 70;
    }

    public static void tick() {
        if (announceTicks > 0) announceTicks--;
    }

    public static ItemStack icon(Skill skill) {
        return switch (skill) {
            case TAP_STEP -> new ItemStack(Items.FEATHER);
            case SLIDE_STEP -> new ItemStack(Items.PHANTOM_MEMBRANE);
            case DRILL_PIERCER -> new ItemStack(Items.ARROW);
            case GLOWING_PIERCE -> new ItemStack(Items.SPECTRAL_ARROW);
            case PERFECT_PARRY -> new ItemStack(Items.SHIELD);
            case REPEL_COUNTER -> new ItemStack(Items.IRON_SWORD);
            case CYCLONE_SLASH -> new ItemStack(Items.WIND_CHARGE);
            case DOUBLE_JUMP -> new ItemStack(Items.RABBIT_FOOT);
            case RABBIT_GUN -> new ItemStack(Items.CARROT);
            case MOON_REFLECTION -> new ItemStack(ModItems.MOONBLADE_WAXING.get());
            case CRESCENT_VORPAL -> new ItemStack(ModItems.TWIN_MOON.get());
            case SKY_CLEAR -> new ItemStack(ModItems.SEITEN_SKILLBOOK.get());
        };
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui) return;
        Font font = mc.font;
        int w = g.guiWidth();
        int h = g.guiHeight();
        renderAnnouncement(g, font, w, h);
        if (!Frontier.isIn(player)) return;
        PlayerData data = ModAttachments.get(player);

        // --- Level / EXP / stamina panel (top-left) ---
        int x = 6, y = 6;
        g.fill(x - 3, y - 3, x + 106, y + 33, 0x90000000);
        g.drawString(font, Component.translatable("hud.slfrontier.level", data.level()), x, y, 0xFFD34F, true);
        if (data.statPoints() > 0) {
            g.drawString(font, Component.translatable("hud.slfrontier.points", data.statPoints()), x + 50, y, 0x7FFF7F, true);
        }
        int need = PlayerData.expToNext(data.level());
        float expFrac = data.level() >= PlayerData.MAX_LEVEL ? 1f : Mth.clamp(data.exp() / (float) need, 0f, 1f);
        bar(g, x, y + 11, 100, 4, expFrac, 0xFFE0B43A, 0xFF3A2F12);
        float stFrac = Mth.clamp(data.stamina() / data.maxStamina(), 0f, 1f);
        int stColor = stFrac > 0.3f ? 0xFF59D16A : 0xFFE0C23A;
        bar(g, x, y + 18, 100, 5, stFrac, stColor, 0xFF15301A);
        g.drawString(font, Component.translatable("hud.slfrontier.stamina"), x, y + 25, 0xA0FFA0, false);
        if (data.cursed()) {
            g.drawString(font, Component.translatable("hud.slfrontier.cursed"), x, y + 37, 0xFF4040, true);
        }

        // --- Skill slots (right of the hotbar) ---
        int sx = w / 2 + 91 + 8;
        int sy = h - 22;
        for (int i = 0; i < PlayerData.SKILL_SLOTS; i++) {
            int bx = sx + i * 22;
            g.fill(bx, sy, bx + 20, sy + 20, 0xA0000000);
            g.fill(bx, sy, bx + 20, sy + 1, 0xFF7FE7FF);
            Skill skill = data.equipped(i);
            if (skill != null) {
                g.renderItem(icon(skill), bx + 2, sy + 2);
                int cd = data.cooldown(skill);
                if (cd > 0) {
                    float frac = Mth.clamp(cd / (float) skill.cooldown, 0f, 1f);
                    int top = sy + 20 - Math.round(20 * frac);
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 200);
                    g.fill(bx, top, bx + 20, sy + 20, 0xB0101030);
                    String secs = String.valueOf((cd + 19) / 20);
                    g.drawString(font, secs, bx + 10 - font.width(secs) / 2, sy + 6, 0xFFFFFF, true);
                    g.pose().popPose();
                }
                if (data.stamina() < skill.staminaCost) {
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 200);
                    g.fill(bx, sy, bx + 20, sy + 20, 0x60FF2020);
                    g.pose().popPose();
                }
            }
            String key = KeyBindings.SKILLS[i].getTranslatedKeyMessage().getString();
            g.pose().pushPose();
            g.pose().translate(0, 0, 250);
            g.drawString(font, key, bx + 1, sy - 9, 0xC0C0C0, true);
            g.pose().popPose();
        }

        // --- Quest tracker (top-right) ---
        QuestStage stage = data.stage();
        if (stage != QuestStage.NOT_STARTED) {
            Component title = Component.translatable(stage.titleKey());
            Component objective = stage.goal > 0
                    ? Component.translatable(stage.objectiveKey()).append(" (" + Math.min(data.questProgress(), stage.goal) + "/" + stage.goal + ")")
                    : Component.translatable(stage.objectiveKey());
            int qw = 150;
            int qx = w - qw - 6;
            var lines = font.split(objective, qw);
            g.fill(qx - 3, 3, w - 3, 16 + lines.size() * 10, 0x80000000);
            g.drawString(font, title, qx, 6, 0xFFD34F, true);
            int ly = 17;
            for (var line : lines) {
                g.drawString(font, line, qx, ly, 0xDDDDDD, false);
                ly += 10;
            }
        }

        // --- Target info (top-center) ---
        Entity target = mc.crosshairPickEntity;
        if (target instanceof LivingEntity living && target instanceof LeveledMob leveled && !(target instanceof FrontierBoss)) {
            Component name = Component.translatable("hud.slfrontier.target", living.getDisplayName(), leveled.frontierLevel());
            int tw = Math.max(80, font.width(name) + 8);
            int tx = w / 2 - tw / 2;
            g.fill(tx, 4, tx + tw, 24, 0x90000000);
            g.drawString(font, name, w / 2 - font.width(name) / 2, 7, 0xFFFFFF, true);
            bar(g, tx + 4, 18, tw - 8, 3, Mth.clamp(living.getHealth() / living.getMaxHealth(), 0f, 1f), 0xFFD03030, 0xFF301010);
        }
    }

    private static void bar(GuiGraphics g, int x, int y, int width, int height, float frac, int color, int back) {
        g.fill(x, y, x + width, y + height, back);
        g.fill(x, y, x + Math.round(width * frac), y + height, color);
    }

    private static void renderAnnouncement(GuiGraphics g, Font font, int w, int h) {
        if (announceTicks <= 0 || announceTitle == null) return;
        float alpha = Math.min(1f, announceTicks / 15f);
        int a = Math.max(8, (int) (alpha * 255)) << 24;
        Component title = Component.translatable(announceTitle);
        Component sub = Component.translatable(announceSub);
        g.pose().pushPose();
        g.pose().translate(w / 2f, h / 3f, 300);
        g.pose().scale(2f, 2f, 1f);
        g.drawString(font, title, -font.width(title) / 2, -10, (announceColor & 0xFFFFFF) | a, true);
        g.pose().popPose();
        g.drawString(font, sub, w / 2 - font.width(sub) / 2, h / 3 + 14, 0xFFFFFF | a, true);
    }
}
