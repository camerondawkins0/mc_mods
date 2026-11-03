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

    public static final DeferredHolder<SoundEvent, SoundEvent> SLASH = sound("combat.slash");
    public static final DeferredHolder<SoundEvent, SoundEvent> HEAVY_SLASH = sound("combat.heavy_slash");
    public static final DeferredHolder<SoundEvent, SoundEvent> CROSS_SLASH = sound("combat.cross_slash");
    public static final DeferredHolder<SoundEvent, SoundEvent> IMPACT = sound("combat.impact");
    public static final DeferredHolder<SoundEvent, SoundEvent> CRIT = sound("combat.crit");
    public static final DeferredHolder<SoundEvent, SoundEvent> FINISHER = sound("combat.finisher");
    public static final DeferredHolder<SoundEvent, SoundEvent> PARRY = sound("combat.parry");
    public static final DeferredHolder<SoundEvent, SoundEvent> PARRY_FAIL = sound("combat.parry_fail");
    public static final DeferredHolder<SoundEvent, SoundEvent> STUN = sound("combat.stun");
    public static final DeferredHolder<SoundEvent, SoundEvent> DASH = sound("skill.dash");
    public static final DeferredHolder<SoundEvent, SoundEvent> PIERCE = sound("skill.pierce");
    public static final DeferredHolder<SoundEvent, SoundEvent> CYCLONE = sound("skill.cyclone");
    public static final DeferredHolder<SoundEvent, SoundEvent> MOON_WAVE = sound("skill.moon_wave");
    public static final DeferredHolder<SoundEvent, SoundEvent> SKY_CLEAR = sound("skill.sky_clear");
    public static final DeferredHolder<SoundEvent, SoundEvent> CRESCENT_SLAM = sound("skill.crescent_slam");
    public static final DeferredHolder<SoundEvent, SoundEvent> SKILL_READY = sound("player.skill_ready");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAMINA_EMPTY = sound("player.stamina_empty");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEVEL_UP = sound("player.level_up");
    public static final DeferredHolder<SoundEvent, SoundEvent> SKILL_LEARNED = sound("player.skill_learned");
    public static final DeferredHolder<SoundEvent, SoundEvent> FULL_DIVE_IN = sound("player.full_dive_in");
    public static final DeferredHolder<SoundEvent, SoundEvent> FULL_DIVE_OUT = sound("player.full_dive_out");
    public static final DeferredHolder<SoundEvent, SoundEvent> STEP = sound("quest.step");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMPLETE = sound("quest.complete");
    public static final DeferredHolder<SoundEvent, SoundEvent> WAYPOINT = sound("world.waypoint");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISCOVER = sound("world.discover");
    public static final DeferredHolder<SoundEvent, SoundEvent> CRAFT_DONE = sound("world.craft_done");
    public static final DeferredHolder<SoundEvent, SoundEvent> TELEGRAPH = sound("boss.telegraph");
    public static final DeferredHolder<SoundEvent, SoundEvent> PHASE = sound("boss.phase");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEFEATED = sound("boss.defeated");
    public static final DeferredHolder<SoundEvent, SoundEvent> CLICK = sound("ui.click");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOVER = sound("ui.hover");
    public static final DeferredHolder<SoundEvent, SoundEvent> OPEN = sound("ui.open");
    public static final DeferredHolder<SoundEvent, SoundEvent> CLOSE = sound("ui.close");
    public static final DeferredHolder<SoundEvent, SoundEvent> DENY = sound("ui.deny");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(SLFrontier.id(name)));
    }

    private ModSounds() {
    }
}
