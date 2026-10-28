package com.slfrontier.skill;

import org.jetbrains.annotations.Nullable;

/**
 * Active and passive skills. Several skills evolve into stronger forms after being used
 * enough times, the same way Tap Step became Slide Step and Perfect Parry became
 * Repel Counter for Sunraku.
 */
public enum Skill {
    // id, stamina cost, cooldown ticks, unlock level (0 = special unlock), passive
    TAP_STEP("tap_step", 10, 30, 1, false),
    SLIDE_STEP("slide_step", 14, 30, 0, false),
    DRILL_PIERCER("drill_piercer", 15, 80, 3, false),
    GLOWING_PIERCE("glowing_pierce", 18, 80, 0, false),
    PERFECT_PARRY("perfect_parry", 8, 60, 5, false),
    REPEL_COUNTER("repel_counter", 10, 60, 0, false),
    CYCLONE_SLASH("cyclone_slash", 20, 120, 8, false),
    DOUBLE_JUMP("double_jump", 12, 0, 10, true),
    RABBIT_GUN("rabbit_gun", 20, 200, 0, false),
    MOON_REFLECTION("moon_reflection", 25, 160, 0, false),
    CRESCENT_VORPAL("crescent_vorpal", 50, 900, 0, false),
    SKY_CLEAR("sky_clear", 40, 600, 0, false);

    public final String id;
    public final int staminaCost;
    public final int cooldown;
    public final int unlockLevel;
    public final boolean passive;

    Skill(String id, int staminaCost, int cooldown, int unlockLevel, boolean passive) {
        this.id = id;
        this.staminaCost = staminaCost;
        this.cooldown = cooldown;
        this.unlockLevel = unlockLevel;
        this.passive = passive;
    }

    /** The skill this one evolves into, if any. */
    @Nullable
    public Skill evolution() {
        return switch (this) {
            case TAP_STEP -> SLIDE_STEP;
            case DRILL_PIERCER -> GLOWING_PIERCE;
            case PERFECT_PARRY -> REPEL_COUNTER;
            default -> null;
        };
    }

    /** Uses (or successful parries) needed before {@link #evolution()} unlocks. */
    public int evolutionUses() {
        return switch (this) {
            case TAP_STEP -> 40;
            case DRILL_PIERCER -> 30;
            case PERFECT_PARRY -> 15;
            default -> 0;
        };
    }

    public String nameKey() {
        return "skill.slfrontier." + id;
    }

    public String descKey() {
        return "skill.slfrontier." + id + ".desc";
    }

    /** How the skill is learned, shown on locked skill cards. */
    public String unlockKey() {
        return "skill.slfrontier." + id + ".unlock";
    }

    /** Icon texture name under textures/gui/skill. */
    public String icon() {
        return id;
    }

    @Nullable
    public static Skill byId(String id) {
        for (Skill s : values()) {
            if (s.id.equals(id)) return s;
        }
        return null;
    }
}
