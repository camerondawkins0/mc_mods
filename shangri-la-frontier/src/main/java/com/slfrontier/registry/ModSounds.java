package com.slfrontier.registry;

import com.slfrontier.SLFrontier;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Frontier's own sounds, synthesised by tools/sfx.py (which also writes this file and
 * assets/slfrontier/sounds.json). Replace any .ogg under assets/slfrontier/sounds to change one.
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SLFrontier.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_SLASH = sound("combat.slash");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_HEAVY_SLASH = sound("combat.heavy_slash");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_CROSS_SLASH = sound("combat.cross_slash");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_IMPACT = sound("combat.impact");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_CRIT = sound("combat.crit");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_FINISHER = sound("combat.finisher");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_PARRY = sound("combat.parry");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_PARRY_FAIL = sound("combat.parry_fail");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_STUN = sound("combat.stun");
    public static final DeferredHolder<SoundEvent, SoundEvent> SKILL_DASH = sound("skill.dash");
    public static final DeferredHolder<SoundEvent, SoundEvent> SKILL_PIERCE = sound("skill.pierce");
    public static final DeferredHolder<SoundEvent, SoundEvent> SKILL_CYCLONE = sound("skill.cyclone");
    public static final DeferredHolder<SoundEvent, SoundEvent> SKILL_MOON_WAVE = sound("skill.moon_wave");
    public static final DeferredHolder<SoundEvent, SoundEvent> SKILL_SKY_CLEAR = sound("skill.sky_clear");
    public static final DeferredHolder<SoundEvent, SoundEvent> SKILL_CRESCENT_SLAM = sound("skill.crescent_slam");
    public static final DeferredHolder<SoundEvent, SoundEvent> PLAYER_SKILL_READY = sound("player.skill_ready");
    public static final DeferredHolder<SoundEvent, SoundEvent> PLAYER_HEARTBEAT = sound("player.heartbeat");
    public static final DeferredHolder<SoundEvent, SoundEvent> PLAYER_STAMINA_EMPTY = sound("player.stamina_empty");
    public static final DeferredHolder<SoundEvent, SoundEvent> PLAYER_LEVEL_UP = sound("player.level_up");
    public static final DeferredHolder<SoundEvent, SoundEvent> PLAYER_SKILL_LEARNED = sound("player.skill_learned");
    public static final DeferredHolder<SoundEvent, SoundEvent> PLAYER_FULL_DIVE_IN = sound("player.full_dive_in");
    public static final DeferredHolder<SoundEvent, SoundEvent> PLAYER_FULL_DIVE_OUT = sound("player.full_dive_out");
    public static final DeferredHolder<SoundEvent, SoundEvent> QUEST_STEP = sound("quest.step");
    public static final DeferredHolder<SoundEvent, SoundEvent> QUEST_COMPLETE = sound("quest.complete");
    public static final DeferredHolder<SoundEvent, SoundEvent> WORLD_WAYPOINT = sound("world.waypoint");
    public static final DeferredHolder<SoundEvent, SoundEvent> WORLD_DISCOVER = sound("world.discover");
    public static final DeferredHolder<SoundEvent, SoundEvent> WORLD_CRAFT_DONE = sound("world.craft_done");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_TELEGRAPH = sound("boss.telegraph");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_PHASE = sound("boss.phase");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_PARRY_GLINT = sound("boss.parry_glint");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_DEFEATED = sound("boss.defeated");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_CLICK = sound("ui.click");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_HOVER = sound("ui.hover");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_OPEN = sound("ui.open");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_CLOSE = sound("ui.close");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_DENY = sound("ui.deny");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_TYPE = sound("ui.type");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(SLFrontier.id(name)));
    }

    private ModSounds() {
    }
}
