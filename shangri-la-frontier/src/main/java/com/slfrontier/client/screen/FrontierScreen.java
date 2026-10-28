package com.slfrontier.client.screen;

import com.slfrontier.client.KeyBindings;
import com.slfrontier.client.ui.Gfx;
import com.slfrontier.client.ui.SlfButton;
import com.slfrontier.client.ui.Theme;
import com.slfrontier.data.PlayerData;
import com.slfrontier.data.Stat;
import com.slfrontier.network.ModNetwork;
import com.slfrontier.progression.Progression;
import com.slfrontier.quest.QuestStage;
import com.slfrontier.quest.SideQuest;
import com.slfrontier.registry.ModAttachments;
import com.slfrontier.registry.ModEffects;
import com.slfrontier.skill.Skill;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The avatar menu (default key K): Status, Skills and Quests, drawn as a Shangri-La
 * Frontier system window. The Map tab opens {@link MapScreen}.
 */
public class FrontierScreen extends Screen {
    public enum Tab { STATUS, SKILLS, QUESTS, MAP }

    private static Tab lastTab = Tab.STATUS;
    private static Skill selectedSkill = Skill.TAP_STEP;
    private static String selectedQuest = "";

    private Tab tab;
    private int left, top, w, h;
    private int lastHash;
    private float openTicks;
    private double skillScroll, questScroll;
    @Nullable
    private Skill dragging;
    private final List<QuestRow> questRows = new ArrayList<>();

    public FrontierScreen() {
        this(lastTab == Tab.MAP ? Tab.STATUS : lastTab);
    }

    public FrontierScreen(Tab tab) {
        super(Component.translatable("gui.slfrontier.menu"));
        this.tab = tab;
    }

    private static PlayerData data() {
        return ModAttachments.get(Minecraft.getInstance().player);
    }

    // ---- Layout ----------------------------------------------------------------------

    @Override
    protected void init() {
        w = Math.min(430, width - 16);
        h = Math.min(244, height - 36);
        left = (width - w) / 2;
        top = (height - h) / 2 + 9;
        rebuild();
    }

    private int cx0() {
        return left + 8;
    }

    private int cy0() {
        return top + 22;
    }

    private int cx1() {
        return left + w - 8;
    }

    private int cy1() {
        return top + h - 8;
    }

    private void rebuild() {
        clearWidgets();
        lastHash = data().syncHash();
        int tx = left + 10;
        for (Tab t : Tab.values()) {
            String icon = switch (t) {
                case STATUS -> "player";
                case SKILLS -> "target";
                case QUESTS -> "main_quest";
                case MAP -> "waypoint";
            };
            Component label = Component.translatable("gui.slfrontier.tab." + t.name().toLowerCase());
            int bw = font.width(label) + 26;
            addRenderableWidget(new SlfButton(tx, top - 17, bw, 17, label, SlfButton.Style.TAB, b -> switchTab(t))
                    .icon(Theme.icon(icon), 16).selected(t == tab));
            tx += bw + 3;
        }
        PlayerData d = data();
        switch (tab) {
            case STATUS -> buildStatus(d);
            case SKILLS -> buildSkills(d);
            case QUESTS -> buildQuests(d);
            default -> {
            }
        }
    }

    private void switchTab(Tab t) {
        if (t == Tab.MAP) {
            Minecraft.getInstance().setScreen(new MapScreen());
            return;
        }
        tab = t;
        lastTab = t;
        rebuild();
    }

    @Override
    public void tick() {
        openTicks++;
        if (data().syncHash() != lastHash) rebuild();
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (KeyBindings.MENU.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float t = Mth.clamp((openTicks + partialTick) / 5f, 0f, 1f);
        renderBackground(g, mouseX, mouseY, partialTick);
        g.pose().pushPose();
        g.pose().translate(0, (1 - t) * 8, 0);
        Gfx.alpha = t;
        Gfx.panel(g, left, top, w, h, 8);
        renderTitle(g);
        PlayerData d = data();
        switch (tab) {
            case STATUS -> renderStatus(g, d, mouseX, mouseY);
            case SKILLS -> renderSkills(g, d, mouseX, mouseY);
            case QUESTS -> renderQuests(g, d, mouseX, mouseY);
            default -> {
            }
        }
        for (var r : renderables) r.render(g, mouseX, mouseY, partialTick);
        Gfx.alpha = 1f;
        g.pose().popPose();
        if (dragging != null) {
            Gfx.icon(g, Theme.skillIcon(dragging), mouseX - 10, mouseY - 10, 20, 32);
        }
        renderTooltips(g, d, mouseX, mouseY);
    }

    private void renderTitle(GuiGraphics g) {
        LocalPlayer player = Minecraft.getInstance().player;
        float[] diamond = Gfx.ngon(left + 16, top + 10, 3.5f, 4, 0);
        Gfx.poly(g, diamond, top + 6, top + 14, Theme.ACCENT, Theme.ACCENT_SOFT);
        g.drawString(font, Component.translatable("gui.slfrontier.system").withStyle(ChatFormatting.BOLD), left + 24, top + 6,
                Gfx.fade(Theme.ACCENT), false);
        Component right = Component.translatable("gui.slfrontier.title_right", player.getDisplayName(), data().level());
        g.drawString(font, right, left + w - 12 - font.width(right), top + 6, Gfx.fade(Theme.TEXT_DIM), false);
        Gfx.hgrad(g, left + 10, top + 17, left + w / 2f, top + 18, Theme.withA(Theme.ACCENT, 0.05f), Theme.withA(Theme.ACCENT, 0.5f));
        Gfx.hgrad(g, left + w / 2f, top + 17, left + w - 10, top + 18, Theme.withA(Theme.ACCENT, 0.5f), Theme.withA(Theme.ACCENT, 0.05f));
    }

    // ---- STATUS ----------------------------------------------------------------------

    private int statX() {
        return cx0() + 106;
    }

    private int statW() {
        return Math.min(170, (cx1() - cx0() - 106) / 2 + 30);
    }

    private void buildStatus(PlayerData d) {
        int y = cy0() + 14;
        int bx = statX() + statW() - 15;
        for (Stat stat : Stat.values()) {
            SlfButton plus = new SlfButton(bx, y + 1, 14, 12, Component.literal("+"), SlfButton.Style.SMALL, b -> {
                int times = hasShiftDown() ? 5 : 1;
                for (int i = 0; i < times; i++) PacketDistributor.sendToServer(new ModNetwork.AllocateStat(stat.ordinal()));
            });
            plus.active = d.statPoints() > 0;
            addRenderableWidget(plus);
            y += 19;
        }
    }

    private void renderStatus(GuiGraphics g, PlayerData d, int mouseX, int mouseY) {
        LocalPlayer player = Minecraft.getInstance().player;
        int x0 = cx0(), y0 = cy0(), y1 = cy1();
        // Avatar preview
        int mw = 98, mh = y1 - y0 - 44;
        Gfx.inset(g, x0, y0, mw, mh);
        for (int i = 0; i < 5; i++) {
            float ry = y0 + mh - 10 - i * 3;
            Gfx.hgrad(g, x0 + 10 + i * 6, ry, x0 + mw / 2f, ry + 1, 0, Theme.withA(Theme.ACCENT, 0.15f + 0.05f * i));
            Gfx.hgrad(g, x0 + mw / 2f, ry, x0 + mw - 10 - i * 6, ry + 1, Theme.withA(Theme.ACCENT, 0.15f + 0.05f * i), 0);
        }
        Gfx.flush(g);
        int scale = Math.min(44, mh / 3);
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, x0 + 4, y0 + 4, x0 + mw - 4, y0 + mh - 4, scale, 0.0625f,
                mouseX, mouseY, player);
        // Level badge
        float[] hex = Gfx.ngon(x0 + 14, y0 + 14, 11, 6, Mth.PI / 6);
        Gfx.poly(g, hex, y0 + 3, y0 + 25, 0xF02A5F88, 0xF0123A5A);
        Gfx.outline(g, hex, 1f, Theme.GOLD);
        String lv = String.valueOf(d.level());
        g.drawString(font, lv, x0 + 14 - font.width(lv) / 2, y0 + 10, Gfx.fade(Theme.GOLD), true);
        g.drawString(font, Component.translatable("gui.slfrontier.job"), x0 + 28, y0 + 5, Gfx.fade(Theme.TEXT_DIM), false);
        // Vital bars
        int by = y0 + mh + 4;
        vital(g, x0, by, mw, Component.translatable("gui.slfrontier.hp"), player.getHealth(), player.getMaxHealth(), Theme.HP_A, Theme.HP_B);
        vital(g, x0, by + 13, mw, Component.translatable("gui.slfrontier.st"), d.stamina(), d.maxStamina(), Theme.ST_A, Theme.ST_B);
        int need = PlayerData.expToNext(d.level());
        boolean max = d.level() >= PlayerData.MAX_LEVEL;
        vital(g, x0, by + 26, mw, Component.translatable("gui.slfrontier.exp_short"), max ? 1 : d.exp(), max ? 1 : need, Theme.EXP_A, Theme.EXP_B);

        // Attributes
        int sx = statX(), sw = statW();
        Gfx.header(g, font, Component.translatable("gui.slfrontier.attributes"), sx, y0, sw - 40);
        Component sp = Component.translatable("gui.slfrontier.sp", d.statPoints());
        int spw = font.width(sp) + 8;
        Gfx.chamfer(g, sx + sw - spw, y0 - 2, spw, 11, 2, 0, 2, 0, d.statPoints() > 0 ? 0xD02E7A3A : 0xC0202830, 0xD0143A1C);
        g.drawString(font, sp, sx + sw - spw + 4, y0, Gfx.fade(d.statPoints() > 0 ? Theme.GREEN : Theme.TEXT_FAINT), false);
        int y = y0 + 14;
        int maxStat = 10;
        for (Stat stat : Stat.values()) maxStat = Math.max(maxStat, d.stat(stat));
        for (Stat stat : Stat.values()) {
            boolean hover = mouseX >= sx && mouseX < sx + sw - 18 && mouseY >= y && mouseY < y + 16;
            if (hover) Gfx.chamfer(g, sx - 2, y - 1, sw - 16, 16, 2, 0, 2, 0, 0x30FFFFFF, 0x10FFFFFF);
            Gfx.icon(g, Theme.statIcon(stat), sx, y + 1, 12, 16);
            g.drawString(font, Component.translatable(stat.translationKey()), sx + 16, y + 3, Gfx.fade(Theme.TEXT), false);
            String v = String.valueOf(d.stat(stat));
            g.drawString(font, v, sx + 58 - font.width(v), y + 3, Gfx.fade(0xFFFFFFFF), true);
            Gfx.bar(g, sx + 62, y + 5, sw - 82, 4, d.stat(stat) / (float) maxStat, Theme.ACCENT, Theme.ACCENT_SOFT, Theme.BAR_BACK, 0);
            y += 19;
        }
        g.drawString(font, Component.translatable("gui.slfrontier.alloc_hint"), sx, y + 2, Gfx.fade(Theme.TEXT_FAINT), false);

        // Parameters & conditions
        int px = sx + sw + 10, pw = cx1() - px;
        Gfx.header(g, font, Component.translatable("gui.slfrontier.parameters"), px, y0, pw);
        int py = y0 + 14;
        py = param(g, px, py, pw, "gui.slfrontier.p.hp", String.format("%.0f", player.getMaxHealth()));
        py = param(g, px, py, pw, "gui.slfrontier.p.atk", String.format("%.1f", player.getAttributeValue(Attributes.ATTACK_DAMAGE)));
        py = param(g, px, py, pw, "gui.slfrontier.p.crit", String.format("%.1f%%", Progression.critChance(player) * 100));
        py = param(g, px, py, pw, "gui.slfrontier.p.cdr", "-" + Math.min(50, d.stat(Stat.TEC)) + "%");
        py = param(g, px, py, pw, "gui.slfrontier.p.speed", String.format("%.0f%%", player.getAttributeValue(Attributes.MOVEMENT_SPEED) / 0.1 * 100));
        py = param(g, px, py, pw, "gui.slfrontier.p.aspd", String.format("%.2f", player.getAttributeValue(Attributes.ATTACK_SPEED)));
        py = param(g, px, py, pw, "gui.slfrontier.p.parry", String.format("%.2fs", Math.min(20, 10 + d.stat(Stat.TEC) / 5) / 20f));
        py += 6;
        Gfx.header(g, font, Component.translatable("gui.slfrontier.condition"), px, py, pw);
        py += 13;
        boolean any = false;
        if (d.cursed()) {
            any = true;
            g.drawString(font, Component.translatable("hud.slfrontier.cursed"), px, py, Gfx.fade(Theme.RED), true);
            py = Gfx.wrapped(g, font, Component.translatable("gui.slfrontier.cursed_desc"), px, py + 10, pw, Theme.withA(0xC08080, 1f), 9) + 3;
        }
        if (player.hasEffect(ModEffects.DEATH_PENALTY)) {
            any = true;
            g.drawString(font, Component.translatable("effect.slfrontier.death_penalty"), px, py, Gfx.fade(0xFFC0C0D0), true);
            py += 11;
        }
        if (!any) g.drawString(font, Component.translatable("gui.slfrontier.condition_normal"), px, py, Gfx.fade(Theme.GREEN), false);
    }

    private void vital(GuiGraphics g, int x, int y, int w, Component label, float value, float max, int a, int b) {
        g.drawString(font, label, x, y, Gfx.fade(Theme.TEXT_DIM), false);
        String v = Math.round(value) + "/" + Math.round(max);
        g.drawString(font, v, x + w - font.width(v), y, Gfx.fade(Theme.TEXT), false);
        Gfx.bar(g, x, y + 9, w, 3, max <= 0 ? 0 : value / max, a, b, Theme.BAR_BACK, 0);
    }

    private int param(GuiGraphics g, int x, int y, int w, String key, String value) {
        g.drawString(font, Component.translatable(key), x, y, Gfx.fade(Theme.TEXT_DIM), false);
        g.drawString(font, value, x + w - font.width(value), y, Gfx.fade(Theme.TEXT), false);
        Gfx.rect(g, x, y + 10, x + w, y + 10.5f, 0x301F3A55);
        return y + 12;
    }

    // ---- SKILLS ----------------------------------------------------------------------

    private static final int ROW = 24;

    private int listW() {
        return (cx1() - cx0()) * 45 / 100;
    }

    private List<Skill> skillOrder() {
        List<Skill> list = new ArrayList<>();
        for (Skill s : Skill.values()) if (!s.passive) list.add(s);
        for (Skill s : Skill.values()) if (s.passive) list.add(s);
        return list;
    }

    private void buildSkills(PlayerData d) {
    }

    private int slotX(int i) {
        int dx = cx0() + listW() + 8;
        int dw = cx1() - dx;
        return dx + (dw - (4 * 28 - 4)) / 2 + i * 28;
    }

    private int slotY() {
        return cy1() - 44;
    }

    private void renderSkills(GuiGraphics g, PlayerData d, int mouseX, int mouseY) {
        int x0 = cx0(), y0 = cy0(), lw = listW(), y1 = cy1();
        Gfx.inset(g, x0, y0, lw, y1 - y0);
        List<Skill> skills = skillOrder();
        int visible = (y1 - y0 - 4) / ROW;
        skillScroll = Mth.clamp(skillScroll, 0, Math.max(0, skills.size() - visible));
        g.enableScissor(x0 + 1, y0 + 1, x0 + lw - 1, y1 - 1);
        int y = y0 + 2 - (int) (skillScroll * ROW);
        for (Skill s : skills) {
            boolean has = d.hasSkill(s);
            boolean sel = s == selectedSkill;
            boolean hover = mouseX >= x0 && mouseX < x0 + lw && mouseY >= y && mouseY < y + ROW - 1 && mouseY >= y0 && mouseY < y1;
            int accent = Theme.skillColor(s);
            if (sel) {
                Gfx.chamfer(g, x0 + 2, y, lw - 4, ROW - 2, 3, 0, 3, 0, Theme.withA(accent, 0.35f), Theme.withA(accent, 0.12f));
                Gfx.rect(g, x0 + 2, y, x0 + 3.5f, y + ROW - 2, accent);
            } else if (hover) {
                Gfx.chamfer(g, x0 + 2, y, lw - 4, ROW - 2, 3, 0, 3, 0, 0x30FFFFFF, 0x10FFFFFF);
            }
            Gfx.alpha *= has ? 1f : 0.45f;
            Gfx.icon(g, Theme.skillIcon(s), x0 + 6, y + 1, 20, 32);
            Gfx.alpha = Mth.clamp((openTicks) / 5f, 0f, 1f);
            Component name = Component.translatable(s.nameKey());
            g.drawString(font, name, x0 + 30, y + 3, Gfx.fade(has ? (sel ? 0xFFFFFFFF : Theme.TEXT) : Theme.TEXT_FAINT), has);
            Component sub = has ? skillSummary(s, d) : Component.translatable(s.unlockKey());
            g.drawString(font, sub, x0 + 30, y + 13, Gfx.fade(has ? Theme.TEXT_DIM : 0xFF6A7A90), false);
            if (!has) Gfx.icon(g, Theme.icon("lock"), x0 + lw - 16, y + 6, 10, 16);
            int slot = equippedSlot(d, s);
            if (slot >= 0) {
                String key = KeyBindings.SKILLS[slot].getTranslatedKeyMessage().getString();
                int kw = font.width(key) + 6;
                Gfx.chamfer(g, x0 + lw - 8 - kw, y + 5, kw, 11, 2, 0, 2, 0, Theme.withA(accent, 0.6f), Theme.withA(accent, 0.3f));
                g.drawString(font, key, x0 + lw - 5 - kw, y + 7, Gfx.fade(0xFFFFFFFF), true);
            }
            y += ROW;
        }
        g.disableScissor();
        if (skills.size() > visible) {
            float frac = (float) visible / skills.size();
            float sy = y0 + 2 + (y1 - y0 - 4) * (float) (skillScroll / skills.size());
            Gfx.rect(g, x0 + lw - 3, sy, x0 + lw - 1.5f, sy + (y1 - y0 - 4) * frac, Theme.withA(Theme.ACCENT, 0.6f));
        }

        // Details
        int dx = x0 + lw + 8, dw = cx1() - dx;
        Skill s = selectedSkill;
        boolean has = d.hasSkill(s);
        int accent = Theme.skillColor(s);
        Gfx.chamfer(g, dx, y0, 40, 40, 6, 0, 6, 0, Theme.withA(accent, 0.3f), 0x90081018);
        Gfx.outline(g, Gfx.chamferPoints(dx, y0, 40, 40, 6, 0, 6, 0), 1f, accent);
        Gfx.alpha *= has ? 1f : 0.45f;
        Gfx.icon(g, Theme.skillIcon(s), dx + 4, y0 + 4, 32, 32);
        Gfx.alpha = Mth.clamp((openTicks) / 5f, 0f, 1f);
        Gfx.text(g, font, Component.translatable(s.nameKey()), dx + 46, y0 + 2, 1.25f, Theme.GOLD, true);
        Component kind = Component.translatable(s.passive ? "gui.slfrontier.passive_tag"
                : isEvolved(s) ? "gui.slfrontier.evolved_tag" : "gui.slfrontier.active_tag");
        int kw = font.width(kind) + 8;
        Gfx.chamfer(g, dx + 46, y0 + 16, kw, 11, 2, 0, 2, 0, Theme.withA(accent, 0.45f), Theme.withA(accent, 0.2f));
        g.drawString(font, kind, dx + 50, y0 + 18, Gfx.fade(0xFFFFFFFF), false);
        if (!has) {
            g.drawString(font, Component.translatable("gui.slfrontier.locked"), dx + 50 + kw, y0 + 18, Gfx.fade(Theme.TEXT_FAINT), false);
        }
        int ty = Gfx.wrapped(g, font, Component.translatable(s.descKey()), dx, y0 + 46, dw, Theme.TEXT, 10) + 4;
        if (!s.passive) {
            ty = param(g, dx, ty, dw, "gui.slfrontier.cost", s.staminaCost + " ST");
            float cd = s.cooldown * Progression.cooldownScale(d) / 20f;
            ty = param(g, dx, ty, dw, "gui.slfrontier.cooldown", String.format("%.1fs", cd));
        } else {
            ty = param(g, dx, ty, dw, "gui.slfrontier.cost", s.staminaCost + " ST");
        }
        if (!has) {
            ty = Gfx.wrapped(g, font, Component.translatable("gui.slfrontier.unlock", Component.translatable(s.unlockKey())),
                    dx, ty + 2, dw, Theme.TEXT_DIM, 10);
        }
        Skill evo = s.evolution();
        if (evo != null && has && !d.hasSkill(evo)) {
            g.drawString(font, Component.translatable("gui.slfrontier.evolution", Component.translatable(evo.nameKey())),
                    dx, ty + 2, Gfx.fade(Theme.ACCENT), false);
            String prog = d.uses(s) + "/" + s.evolutionUses();
            g.drawString(font, prog, dx + dw - font.width(prog), ty + 2, Gfx.fade(Theme.TEXT), false);
            Gfx.bar(g, dx, ty + 12, dw, 4, d.uses(s) / (float) s.evolutionUses(), Theme.ACCENT, Theme.ACCENT_SOFT, Theme.BAR_BACK, 10);
        }

        // Slots
        int sy = slotY();
        g.drawString(font, Component.translatable("gui.slfrontier.slots_header"), dx, sy - 12, Gfx.fade(Theme.TEXT_DIM), false);
        for (int i = 0; i < PlayerData.SKILL_SLOTS; i++) {
            int sx = slotX(i);
            boolean hover = mouseX >= sx && mouseX < sx + 24 && mouseY >= sy && mouseY < sy + 24;
            Skill eq = d.equipped(i);
            int ac = eq != null ? Theme.skillColor(eq) : Theme.BORDER;
            Gfx.chamfer(g, sx, sy, 24, 24, 4, 0, 4, 0, hover ? 0xE0254060 : 0xD0101C2C, 0xE0081018);
            Gfx.outline(g, Gfx.chamferPoints(sx, sy, 24, 24, 4, 0, 4, 0), 1f, hover ? Theme.ACCENT : ac);
            if (eq != null) Gfx.icon(g, Theme.skillIcon(eq), sx + 2, sy + 2, 20, 32);
            String key = KeyBindings.SKILLS[i].getTranslatedKeyMessage().getString();
            g.drawString(font, key, sx + 12 - font.width(key) / 2, sy + 26, Gfx.fade(Theme.TEXT_DIM), false);
        }
        Component hint = Component.translatable("gui.slfrontier.slot_hint");
        Gfx.text(g, font, hint, dx + (cx1() - dx) / 2f - font.width(hint) * 0.375f, cy1() - 8, 0.75f, Theme.TEXT_FAINT, false);
    }

    private static boolean isEvolved(Skill s) {
        for (Skill o : Skill.values()) if (o.evolution() == s) return true;
        return false;
    }

    private static int equippedSlot(PlayerData d, Skill s) {
        for (int i = 0; i < PlayerData.SKILL_SLOTS; i++) if (d.equipped(i) == s) return i;
        return -1;
    }

    private Component skillSummary(Skill s, PlayerData d) {
        if (s.passive) return Component.translatable("gui.slfrontier.passive_tag");
        Skill evo = s.evolution();
        if (evo != null && !d.hasSkill(evo)) {
            return Component.translatable("gui.slfrontier.skill_line_evo", s.staminaCost, d.uses(s), s.evolutionUses());
        }
        return Component.translatable("gui.slfrontier.skill_line", s.staminaCost, String.format("%.1f", s.cooldown * Progression.cooldownScale(d) / 20f));
    }

    @Nullable
    private Skill skillAt(double mx, double my) {
        int x0 = cx0(), y0 = cy0(), lw = listW(), y1 = cy1();
        if (mx < x0 || mx >= x0 + lw || my < y0 || my >= y1) return null;
        int idx = (int) ((my - y0 - 2) / ROW + skillScroll);
        List<Skill> skills = skillOrder();
        return idx >= 0 && idx < skills.size() ? skills.get(idx) : null;
    }

    private int slotAt(double mx, double my) {
        int sy = slotY();
        for (int i = 0; i < PlayerData.SKILL_SLOTS; i++) {
            int sx = slotX(i);
            if (mx >= sx && mx < sx + 24 && my >= sy && my < sy + 24) return i;
        }
        return -1;
    }

    private void equip(Skill skill, int slot) {
        PlayerData d = data();
        if (skill == null || !d.hasSkill(skill) || skill.passive) return;
        PacketDistributor.sendToServer(new ModNetwork.EquipSkill(skill.id, slot));
    }

    // ---- QUESTS ----------------------------------------------------------------------

    private record QuestRow(String id, Component label, int color, @Nullable String icon, boolean header, float progress) {
    }

    private void buildQuests(PlayerData d) {
        questRows.clear();
        QuestStage current = d.stage();
        int act = -1;
        questRows.add(new QuestRow("", Component.translatable("gui.slfrontier.main_story"), Theme.ACCENT, null, true, -1));
        for (QuestStage s : QuestStage.values()) {
            if (s == QuestStage.NOT_STARTED) continue;
            if (s.act != act) {
                act = s.act;
                boolean reached = current.act >= act;
                questRows.add(new QuestRow("", Component.translatable(QuestStage.actKey(act)), reached ? Theme.TEXT_DIM : Theme.TEXT_FAINT,
                        null, true, -1));
            }
            boolean done = s.ordinal() < current.ordinal() || current == QuestStage.COMPLETE;
            boolean now = s == current && current != QuestStage.COMPLETE;
            if (s.ordinal() > current.ordinal()) {
                questRows.add(new QuestRow("locked:" + s.name(), Component.literal("? ? ?"), Theme.TEXT_FAINT, "lock", false, -1));
            } else {
                questRows.add(new QuestRow("main:" + s.name(), Component.translatable(s.titleKey()),
                        now ? Theme.GOLD : done ? 0xFF8CC88C : Theme.TEXT, now ? "main_quest" : "check", false,
                        now && s.goal > 0 ? Math.min(1f, d.questProgress() / (float) s.goal) : -1));
            }
        }
        List<SideQuest> active = new ArrayList<>(), avail = new ArrayList<>(), done = new ArrayList<>();
        for (SideQuest q : SideQuest.values()) {
            switch (d.sideState(q)) {
                case ACTIVE -> active.add(q);
                case AVAILABLE -> avail.add(q);
                case DONE -> done.add(q);
                default -> {
                }
            }
        }
        questRows.add(new QuestRow("", Component.translatable("gui.slfrontier.side_quests"), Theme.ACCENT, null, true, -1));
        if (active.isEmpty() && avail.isEmpty()) {
            questRows.add(new QuestRow("", Component.translatable("gui.slfrontier.no_side"), Theme.TEXT_FAINT, null, false, -1));
        }
        for (SideQuest q : active) {
            questRows.add(new QuestRow("side:" + q.id, Component.translatable(q.titleKey()), Theme.TEXT, "side_quest", false,
                    Math.min(1f, d.sideProgress(q) / (float) q.goal)));
        }
        for (SideQuest q : avail) {
            questRows.add(new QuestRow("avail:" + q.id, Component.translatable(q.titleKey()), Theme.TEXT_DIM, "side_quest", false, -1));
        }
        if (!done.isEmpty()) {
            questRows.add(new QuestRow("", Component.translatable("gui.slfrontier.completed"), Theme.TEXT_FAINT, null, true, -1));
            for (SideQuest q : done) {
                questRows.add(new QuestRow("done:" + q.id, Component.translatable(q.titleKey()), 0xFF6A8A6A, "check", false, -1));
            }
        }
        if (selectedQuest.isEmpty() || questRows.stream().noneMatch(r -> r.id().equals(selectedQuest))) {
            selectedQuest = "main:" + (current == QuestStage.NOT_STARTED ? QuestStage.MEET_GUIDE : current).name();
        }
        // Track button
        String trackId = trackIdFor(selectedQuest);
        if (trackId != null) {
            boolean tracking = d.tracked().equals(trackId);
            int dx = cx0() + listW() + 8;
            addRenderableWidget(new SlfButton(cx1() - 70, cy1() - 16, 70, 16,
                    Component.translatable(tracking ? "gui.slfrontier.tracking" : "gui.slfrontier.track"),
                    SlfButton.Style.PRIMARY, b -> PacketDistributor.sendToServer(new ModNetwork.TrackQuest(trackId)))
                    .selected(tracking).icon(Theme.icon("target"), 16));
        }
    }

    /** "" tracks the main story; side quest ids track themselves; anything else can't be tracked. */
    @Nullable
    private static String trackIdFor(String row) {
        if (row.startsWith("main:")) {
            QuestStage s = QuestStage.byName(row.substring(5));
            return s == data().stage() && s != QuestStage.COMPLETE ? "" : null;
        }
        if (row.startsWith("side:")) return row.substring(5);
        return null;
    }

    private void renderQuests(GuiGraphics g, PlayerData d, int mouseX, int mouseY) {
        int x0 = cx0(), y0 = cy0(), lw = listW(), y1 = cy1();
        Gfx.inset(g, x0, y0, lw, y1 - y0);
        int rowH = 13;
        int visible = (y1 - y0 - 4) / rowH;
        questScroll = Mth.clamp(questScroll, 0, Math.max(0, questRows.size() - visible));
        g.enableScissor(x0 + 1, y0 + 1, x0 + lw - 1, y1 - 1);
        int y = y0 + 3 - (int) (questScroll * rowH);
        for (QuestRow r : questRows) {
            boolean sel = !r.id().isEmpty() && r.id().equals(selectedQuest);
            boolean hover = !r.header() && !r.id().isEmpty() && mouseX >= x0 && mouseX < x0 + lw && mouseY >= y && mouseY < y + rowH
                    && mouseY >= y0 && mouseY < y1;
            if (sel) {
                Gfx.chamfer(g, x0 + 2, y - 1, lw - 4, rowH, 2, 0, 2, 0, 0x5A3FA8D0, 0x203FA8D0);
                Gfx.rect(g, x0 + 2, y - 1, x0 + 3.5f, y + rowH - 1, Theme.ACCENT);
            } else if (hover) {
                Gfx.chamfer(g, x0 + 2, y - 1, lw - 4, rowH, 2, 0, 2, 0, 0x28FFFFFF, 0x10FFFFFF);
            }
            if (r.header()) {
                g.drawString(font, r.label(), x0 + 5, y + 2, Gfx.fade(r.color()), false);
            } else {
                int tx = x0 + 8;
                if (r.icon() != null) {
                    Gfx.icon(g, Theme.icon(r.icon()), tx, y + 1, 9, 16);
                    tx += 12;
                }
                int maxW = lw - (tx - x0) - (r.progress() >= 0 ? 30 : 6);
                String s = font.plainSubstrByWidth(r.label().getString(), maxW);
                g.drawString(font, s, tx, y + 2, Gfx.fade(r.color()), false);
                if (r.progress() >= 0) Gfx.bar(g, x0 + lw - 28, y + 4, 22, 3, r.progress(), Theme.GOLD, 0xFFC98A10, Theme.BAR_BACK, 0);
            }
            y += rowH;
        }
        g.disableScissor();

        int dx = x0 + lw + 8, dw = cx1() - dx;
        if (selectedQuest.startsWith("main:")) {
            QuestStage s = QuestStage.byName(selectedQuest.substring(5));
            if (s != null) renderMainDetail(g, d, s, dx, y0, dw);
        } else if (selectedQuest.contains(":")) {
            SideQuest q = SideQuest.byId(selectedQuest.substring(selectedQuest.indexOf(':') + 1));
            if (q != null) renderSideDetail(g, d, q, dx, y0, dw, selectedQuest.startsWith("avail:"));
        }
    }

    private void renderMainDetail(GuiGraphics g, PlayerData d, QuestStage s, int x, int y, int w) {
        g.drawString(font, Component.translatable(QuestStage.actKey(s.act)), x, y, Gfx.fade(Theme.TEXT_DIM), false);
        Gfx.text(g, font, Component.translatable(s.titleKey()), x, y + 11, 1.25f, Theme.GOLD, true);
        Gfx.icon(g, Theme.icon("waypoint"), x, y + 25, 9, 16);
        g.drawString(font, Component.translatable(s.locationKey()), x + 12, y + 26, Gfx.fade(Theme.ACCENT), false);
        int ty = Gfx.wrapped(g, font, Component.translatable(s.journalKey()), x, y + 40, w, Theme.TEXT, 10) + 6;
        boolean current = s == d.stage() && s != QuestStage.COMPLETE;
        boolean done = s.ordinal() < d.stage().ordinal() || d.stage() == QuestStage.COMPLETE;
        int objLines = font.split(Component.translatable(s.objectiveKey()), w - 10).size();
        int boxH = 18 + objLines * 9 + (s.goal > 0 && current ? 8 : 0);
        int boxY = Math.min(ty, cy1() - 20 - boxH);
        Gfx.inset(g, x, boxY, w, boxH);
        g.drawString(font, Component.translatable(done ? "gui.slfrontier.objective_done" : "gui.slfrontier.objective"),
                x + 5, boxY + 4, Gfx.fade(done ? Theme.GREEN : Theme.ACCENT), false);
        Gfx.wrapped(g, font, Component.translatable(s.objectiveKey()), x + 5, boxY + 15, w - 10, done ? Theme.TEXT_DIM : Theme.TEXT, 9);
        if (s.goal > 0 && current) {
            String p = Math.min(d.questProgress(), s.goal) + "/" + s.goal;
            g.drawString(font, p, x + w - 5 - font.width(p), boxY + 4, Gfx.fade(Theme.TEXT), false);
            Gfx.bar(g, x + 5, boxY + boxH - 8, w - 10, 3, d.questProgress() / (float) s.goal, Theme.GOLD, 0xFFC98A10, Theme.BAR_BACK, s.goal);
        }
    }

    private void renderSideDetail(GuiGraphics g, PlayerData d, SideQuest q, int x, int y, int w, boolean available) {
        g.drawString(font, Component.translatable("gui.slfrontier.side_quest"), x, y, Gfx.fade(Theme.TEXT_DIM), false);
        Gfx.text(g, font, Component.translatable(q.titleKey()), x, y + 11, 1.25f, Theme.GOLD, true);
        g.drawString(font, Component.translatable("gui.slfrontier.giver", Component.translatable("entity.slfrontier." + q.giver)),
                x, y + 26, Gfx.fade(Theme.ACCENT), false);
        int ty = Gfx.wrapped(g, font, Component.translatable(q.descKey()), x, y + 40, w, Theme.TEXT, 10) + 6;
        int objLines = font.split(Component.translatable(q.objectiveKey()), w - 10).size();
        int boxH = 22 + objLines * 9;
        int boxY = Math.min(ty, cy1() - 30 - boxH - 12);
        Gfx.inset(g, x, boxY, w, boxH);
        boolean done = d.sideState(q) == PlayerData.SideState.DONE;
        g.drawString(font, Component.translatable(done ? "gui.slfrontier.objective_done" : "gui.slfrontier.objective"),
                x + 5, boxY + 4, Gfx.fade(done ? Theme.GREEN : Theme.ACCENT), false);
        Gfx.wrapped(g, font, Component.translatable(q.objectiveKey()), x + 5, boxY + 15, w - 10, Theme.TEXT, 9);
        if (!available && !done) {
            String p = Math.min(d.sideProgress(q), q.goal) + "/" + q.goal;
            g.drawString(font, p, x + w - 5 - font.width(p), boxY + 4, Gfx.fade(Theme.TEXT), false);
            Gfx.bar(g, x + 5, boxY + boxH - 7, w - 10, 3, d.sideProgress(q) / (float) q.goal, Theme.GOLD, 0xFFC98A10, Theme.BAR_BACK, 0);
        }
        if (available) {
            g.drawString(font, Component.translatable("gui.slfrontier.talk_to_accept"), x, boxY + boxH + 4, Gfx.fade(Theme.TEXT_DIM), false);
        }
        // Rewards
        int ry = boxY + boxH + 16;
        g.drawString(font, Component.translatable("gui.slfrontier.rewards"), x, ry, Gfx.fade(Theme.TEXT_DIM), false);
        int rx = x + font.width(Component.translatable("gui.slfrontier.rewards")) + 6;
        if (q.exp > 0) {
            String e = q.exp + " EXP";
            g.drawString(font, e, rx, ry, Gfx.fade(Theme.EXP_A), true);
            rx += font.width(e) + 6;
        }
        for (ItemStack stack : q.rewards()) {
            g.renderItem(stack, rx, ry - 4);
            g.renderItemDecorations(font, stack, rx, ry - 4);
            rx += 18;
        }
    }

    @Nullable
    private String questAt(double mx, double my) {
        int x0 = cx0(), y0 = cy0(), lw = listW(), y1 = cy1();
        if (mx < x0 || mx >= x0 + lw || my < y0 || my >= y1) return null;
        int idx = (int) ((my - y0 - 3) / 13 + questScroll);
        if (idx < 0 || idx >= questRows.size()) return null;
        QuestRow r = questRows.get(idx);
        return r.header() || r.id().isEmpty() || r.id().startsWith("locked:") ? null : r.id();
    }

    // ---- Input -----------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (tab == Tab.SKILLS) {
            Skill s = skillAt(mx, my);
            if (s != null) {
                selectedSkill = s;
                if (button == 0 && data().hasSkill(s) && !s.passive) dragging = s;
                return true;
            }
            int slot = slotAt(mx, my);
            if (slot >= 0) {
                if (button == 1) PacketDistributor.sendToServer(new ModNetwork.EquipSkill("", slot));
                else equip(selectedSkill, slot);
                return true;
            }
        } else if (tab == Tab.QUESTS) {
            String q = questAt(mx, my);
            if (q != null) {
                selectedQuest = q;
                rebuild();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging != null) {
            int slot = slotAt(mx, my);
            if (slot >= 0) equip(dragging, slot);
            dragging = null;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (tab == Tab.SKILLS) skillScroll -= sy;
        if (tab == Tab.QUESTS) questScroll -= sy;
        return true;
    }

    private void renderTooltips(GuiGraphics g, PlayerData d, int mouseX, int mouseY) {
        if (tab == Tab.STATUS) {
            int sx = statX(), sw = statW();
            int y = cy0() + 14;
            for (Stat stat : Stat.values()) {
                if (mouseX >= sx && mouseX < sx + sw && mouseY >= y && mouseY < y + 16) {
                    g.renderTooltip(font, font.split(Component.translatable(stat.translationKey() + ".desc"), 180), mouseX, mouseY);
                }
                y += 19;
            }
        }
    }
}
