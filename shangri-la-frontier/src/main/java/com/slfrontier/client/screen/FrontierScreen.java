package com.slfrontier.client.screen;

import com.slfrontier.client.FrontierHud;
import com.slfrontier.data.PlayerData;
import com.slfrontier.data.Stat;
import com.slfrontier.network.ModNetwork;
import com.slfrontier.quest.QuestStage;
import com.slfrontier.registry.ModAttachments;
import com.slfrontier.skill.Skill;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** The avatar menu (default key K): Status, Skills and Quest tabs. */
public class FrontierScreen extends Screen {
    private enum Tab { STATUS, SKILLS, QUEST }

    private static Tab lastTab = Tab.STATUS;
    private Tab tab = lastTab;
    private int left, top;
    private static final int W = 300, H = 200;
    private int lastHash;

    public FrontierScreen() {
        super(Component.translatable("gui.slfrontier.menu"));
    }

    private PlayerData data() {
        return ModAttachments.get(Minecraft.getInstance().player);
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        lastHash = data().syncHash();
        int tx = left;
        for (Tab t : Tab.values()) {
            Button b = Button.builder(Component.translatable("gui.slfrontier.tab." + t.name().toLowerCase()), btn -> {
                tab = t;
                lastTab = t;
                rebuild();
            }).bounds(tx, top - 22, 80, 20).build();
            b.active = t != tab;
            addRenderableWidget(b);
            tx += 82;
        }
        PlayerData d = data();
        if (tab == Tab.STATUS) {
            int y = top + 50;
            for (Stat stat : Stat.values()) {
                Button plus = Button.builder(Component.literal("+"), btn ->
                        PacketDistributor.sendToServer(new ModNetwork.AllocateStat(stat.ordinal())))
                        .bounds(left + 150, y - 4, 16, 16)
                        .tooltip(Tooltip.create(Component.translatable(stat.translationKey() + ".desc")))
                        .build();
                plus.active = d.statPoints() > 0;
                addRenderableWidget(plus);
                y += 19;
            }
        } else if (tab == Tab.SKILLS) {
            int y = top + 36;
            for (Skill skill : activeSkills(d)) {
                for (int slot = 0; slot < PlayerData.SKILL_SLOTS; slot++) {
                    final int s = slot;
                    Button b = Button.builder(Component.literal(String.valueOf(slot + 1)), btn ->
                            PacketDistributor.sendToServer(new ModNetwork.EquipSkill(skill.id, s)))
                            .bounds(left + 196 + slot * 24, y - 3, 20, 14).build();
                    b.active = d.equipped(slot) != skill;
                    addRenderableWidget(b);
                }
                y += 18;
                if (y > top + H - 20) break;
            }
        }
    }

    private static List<Skill> activeSkills(PlayerData d) {
        List<Skill> list = new ArrayList<>();
        for (Skill s : Skill.values()) {
            if (d.hasSkill(s) && !s.passive) list.add(s);
        }
        return list;
    }

    @Override
    public void tick() {
        if (data().syncHash() != lastHash) rebuild();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left - 6, top - 2, left + W + 6, top + H + 6, 0xE0101420);
        g.fill(left - 6, top - 2, left + W + 6, top - 1, 0xFF7FE7FF);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        PlayerData d = data();
        switch (tab) {
            case STATUS -> renderStatus(g, d);
            case SKILLS -> renderSkills(g, d);
            case QUEST -> renderQuest(g, d);
        }
    }

    private void renderStatus(GuiGraphics g, PlayerData d) {
        var player = Minecraft.getInstance().player;
        g.drawString(font, player.getDisplayName(), left + 4, top + 6, 0xFFFFFF, true);
        g.drawString(font, Component.translatable("hud.slfrontier.level", d.level()), left + 4, top + 18, 0xFFD34F, true);
        String exp = d.level() >= PlayerData.MAX_LEVEL ? "MAX" : d.exp() + " / " + PlayerData.expToNext(d.level());
        g.drawString(font, Component.translatable("gui.slfrontier.exp", exp), left + 60, top + 18, 0xE0B43A, false);
        g.drawString(font, Component.translatable("gui.slfrontier.points", d.statPoints()), left + 4, top + 32, 0x7FFF7F, false);
        int y = top + 50;
        for (Stat stat : Stat.values()) {
            g.drawString(font, Component.translatable(stat.translationKey()), left + 8, y, 0xE0E0E0, false);
            String v = String.valueOf(d.stat(stat));
            g.drawString(font, v, left + 140 - font.width(v), y, 0xFFFFFF, true);
            y += 19;
        }
        int rx = left + 180;
        g.drawString(font, Component.translatable("gui.slfrontier.derived"), rx, top + 50, 0x7FE7FF, false);
        g.drawString(font, Component.translatable("gui.slfrontier.stamina", Math.round(d.stamina()), Math.round(d.maxStamina())), rx, top + 64, 0xA0FFA0, false);
        g.drawString(font, Component.translatable("gui.slfrontier.crit", String.format("%.1f", (0.05f + d.stat(Stat.LUC) * 0.005f) * 100)), rx, top + 76, 0xFFA0A0, false);
        g.drawString(font, Component.translatable("gui.slfrontier.cdr", Math.min(50, d.stat(Stat.TEC))), rx, top + 88, 0xC0C0FF, false);
        if (d.cursed()) {
            g.drawString(font, Component.translatable("hud.slfrontier.cursed"), rx, top + 108, 0xFF4040, true);
            int cy = top + 120;
            for (FormattedCharSequence s : font.split(Component.translatable("gui.slfrontier.cursed_desc"), 110)) {
                g.drawString(font, s, rx, cy, 0xC08080, false);
                cy += 10;
            }
        }
    }

    private void renderSkills(GuiGraphics g, PlayerData d) {
        g.drawString(font, Component.translatable("gui.slfrontier.skills_header"), left + 4, top + 6, 0x7FE7FF, true);
        g.drawString(font, Component.translatable("gui.slfrontier.slots_header"), left + 196, top + 22, 0xA0A0A0, false);
        int y = top + 36;
        for (Skill skill : activeSkills(d)) {
            g.renderItem(FrontierHud.icon(skill), left + 2, y - 5);
            g.drawString(font, Component.translatable(skill.nameKey()), left + 22, y, 0xFFFFFF, false);
            Skill evo = skill.evolution();
            String extra = evo != null && !d.hasSkill(evo)
                    ? d.uses(skill) + "/" + skill.evolutionUses()
                    : skill.staminaCost + " ST";
            g.drawString(font, extra, left + 150, y, 0xA0A0A0, false);
            y += 18;
            if (y > top + H - 20) break;
        }
        if (d.hasSkill(Skill.DOUBLE_JUMP)) {
            g.drawString(font, Component.translatable("gui.slfrontier.passive", Component.translatable(Skill.DOUBLE_JUMP.nameKey())),
                    left + 4, top + H - 10, 0xA0FFA0, false);
        }
    }

    private void renderQuest(GuiGraphics g, PlayerData d) {
        QuestStage stage = d.stage();
        g.drawString(font, Component.translatable("gui.slfrontier.current_chapter"), left + 4, top + 6, 0x7FE7FF, true);
        g.drawString(font, Component.translatable(stage.titleKey()), left + 4, top + 20, 0xFFD34F, true);
        int y = top + 34;
        for (FormattedCharSequence s : font.split(Component.translatable(stage.objectiveKey()), W - 10)) {
            g.drawString(font, s, left + 4, y, 0xE0E0E0, false);
            y += 11;
        }
        y += 8;
        g.drawString(font, Component.translatable("gui.slfrontier.chapters"), left + 4, y, 0x7FE7FF, false);
        y += 12;
        for (QuestStage s : QuestStage.values()) {
            if (s == QuestStage.NOT_STARTED || s.ordinal() > stage.ordinal()) continue;
            boolean done = s.ordinal() < stage.ordinal() || stage == QuestStage.COMPLETE;
            g.drawString(font, Component.literal(done ? "✔ " : "➤ ").append(Component.translatable(s.titleKey())),
                    left + 8, y, done ? 0x80C080 : 0xFFFFFF, false);
            y += 10;
            if (y > top + H - 8) break;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
