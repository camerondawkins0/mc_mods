package com.slfrontier.client.ui;

import com.slfrontier.SLFrontier;
import com.slfrontier.data.Stat;
import com.slfrontier.skill.Skill;
import net.minecraft.resources.ResourceLocation;

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
    public static final int TEXT_FAINT = 0xFF5A6E86;
    public static final int RED = 0xFFFF5A6A;
    public static final int GREEN = 0xFF7FE07F;
    public static final int VIOLET = 0xFFB57CFF;

    public static final int HP_A = 0xFFFF4A5A, HP_B = 0xFFB01E2E;
    public static final int ST_A = 0xFF9FF05A, ST_B = 0xFF3E9A2A;
    public static final int ST_LOW_A = 0xFFFFD34F, ST_LOW_B = 0xFFC98A10;
    public static final int EXP_A = 0xFFFFE680, EXP_B = 0xFFE0A020;
    public static final int BAR_BACK = 0xC0050A12;

    private Theme() {
    }

    public static int withA(int argb, float a) {
        return Gfx.withAlpha(argb, a);
    }

    public static ResourceLocation skillIcon(Skill skill) {
        return SLFrontier.id("textures/gui/skill/" + skill.icon() + ".png");
    }

    public static ResourceLocation statIcon(Stat stat) {
        return SLFrontier.id("textures/gui/stat/" + stat.name().toLowerCase() + ".png");
    }

    public static ResourceLocation icon(String name) {
        return SLFrontier.id("textures/gui/icon/" + name + ".png");
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
