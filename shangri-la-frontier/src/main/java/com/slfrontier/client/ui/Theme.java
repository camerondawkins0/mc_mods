package com.slfrontier.client.ui;

import com.slfrontier.SLFrontier;
import com.slfrontier.data.Stat;
import com.slfrontier.skill.Skill;
import net.minecraft.resources.Identifier;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Colours and textures shared by every Frontier screen and the HUD. */
public final class Theme {
    // Panels: deep navy glass with a cyan rim, like the in-game windows of Shangri-La Frontier.
    public static final int PANEL_TOP = 0xE6142338;
    public static final int PANEL_BOTTOM = 0xEE080F1C;
    public static final int INSET_TOP = 0xB00C1626;
    public static final int INSET_BOTTOM = 0xC0060C16;
    public static final int INSET_BORDER = 0xFF1F3A55;
    public static final int BORDER = 0xFF2E5F86;
    public static final int ACCENT = 0xFF5FE0FF;
    public static final int ACCENT_SOFT = 0xFF3FA8D0;
    public static final int GOLD = 0xFFFFD34F;
    public static final int TEXT = 0xFFEAF6FF;
    public static final int TEXT_DIM = 0xFF8EA6C0;
    /** Hints and disabled text: about 5:1 on the panel navy, still a step below TEXT_DIM. */
    public static final int TEXT_FAINT = 0xFF7D90A8;
    public static final int RED = 0xFFFF5A6A;
    public static final int GREEN = 0xFF7FE07F;
    public static final int VIOLET = 0xFFB57CFF;

    public static final int HP_A = 0xFFFF4A5A, HP_B = 0xFFB01E2E;
    public static final int ST_A = 0xFF9FF05A, ST_B = 0xFF3E9A2A;
    public static final int ST_LOW_A = 0xFFFFD34F, ST_LOW_B = 0xFFC98A10;
    public static final int EXP_A = 0xFFFFE680, EXP_B = 0xFFE0A020;
    public static final int BAR_BACK = 0xC0050A12;

    // Identifiers are looked up every frame by the HUD and screens: build each one once.
    private static final Map<String, Identifier> ICONS = new HashMap<>();
    private static final Map<Skill, Identifier> SKILL_ICONS = new EnumMap<>(Skill.class);
    private static final Map<Stat, Identifier> STAT_ICONS = new EnumMap<>(Stat.class);

    private Theme() {
    }

    public static int withA(int argb, float a) {
        return Gfx.withAlpha(argb, a);
    }

    public static Identifier skillIcon(Skill skill) {
        return SKILL_ICONS.computeIfAbsent(skill, s -> SLFrontier.id("textures/gui/skill/" + s.icon() + ".png"));
    }

    public static Identifier statIcon(Stat stat) {
        return STAT_ICONS.computeIfAbsent(stat, s -> SLFrontier.id("textures/gui/stat/" + s.name().toLowerCase() + ".png"));
    }

    public static Identifier icon(String name) {
        return ICONS.computeIfAbsent(name, n -> SLFrontier.id("textures/gui/icon/" + n + ".png"));
    }

    /** Accent colour for a skill's card and HUD slot. */
    public static int skillColor(Skill skill) {
        return switch (skill) {
            case TAP_STEP, SLIDE_STEP, DRILL_PIERCER, PERFECT_PARRY -> ACCENT;
            case GLOWING_PIERCE -> GOLD;
            case REPEL_COUNTER, CYCLONE_SLASH -> RED;
            case DOUBLE_JUMP -> GREEN;
            case RABBIT_GUN, CRESCENT_VORPAL -> VIOLET;
            case MOON_REFLECTION -> 0xFFC8D4FF;
            case SKY_CLEAR -> 0xFF8FD8FF;
            default -> ACCENT;
        };
    }
}
