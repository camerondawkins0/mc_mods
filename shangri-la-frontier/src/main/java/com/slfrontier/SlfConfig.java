package com.slfrontier;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Mod options. Client options live in config/slfrontier-client.toml; server options in
 * each world's serverconfig/slfrontier-server.toml (synced to players).
 */
public final class SlfConfig {
    public static final ModConfigSpec CLIENT_SPEC;
    public static final ModConfigSpec SERVER_SPEC;

    // Client
    public static final ModConfigSpec.BooleanValue FRONTIER_HUD;
    public static final ModConfigSpec.BooleanValue HIDE_VANILLA_BARS;
    public static final ModConfigSpec.BooleanValue SHOW_MINIMAP;
    public static final ModConfigSpec.IntValue MINIMAP_SIZE;
    public static final ModConfigSpec.BooleanValue MINIMAP_ROTATES;
    public static final ModConfigSpec.BooleanValue DAMAGE_NUMBERS;
    public static final ModConfigSpec.BooleanValue CAMERA_SHAKE;
    public static final ModConfigSpec.BooleanValue FIRST_PERSON_SKILL_ANIMS;

    // Server
    public static final ModConfigSpec.BooleanValue FAST_TRAVEL;
    public static final ModConfigSpec.IntValue FAST_TRAVEL_COOLDOWN;
    public static final ModConfigSpec.BooleanValue FAST_TRAVEL_IN_COMBAT;
    public static final ModConfigSpec.BooleanValue FAST_TRAVEL_NEEDS_ATTUNEMENT;
    public static final ModConfigSpec.BooleanValue RESPAWN_AT_WAYPOINT;

    static {
        ModConfigSpec.Builder c = new ModConfigSpec.Builder();
        c.push("hud");
        FRONTIER_HUD = c.comment("Draw the Frontier HUD (avatar frame, skill bar, quest tracker) inside the Frontier.")
                .define("frontierHud", true);
        HIDE_VANILLA_BARS = c.comment("Hide vanilla hearts, armor and the XP bar inside the Frontier (the avatar frame shows them).")
                .define("hideVanillaBars", true);
        DAMAGE_NUMBERS = c.comment("Show floating damage numbers when you hit monsters.").define("damageNumbers", true);
        c.pop();
        c.push("minimap");
        SHOW_MINIMAP = c.comment("Show the minimap in the corner of the screen inside the Frontier (toggle in game with N).")
                .define("showMinimap", true);
        MINIMAP_SIZE = c.comment("Minimap size in GUI pixels.").defineInRange("size", 84, 48, 160);
        MINIMAP_ROTATES = c.comment("Rotate the minimap with the camera instead of keeping north up.").define("rotates", false);
        c.pop();
        c.push("effects");
        CAMERA_SHAKE = c.comment("Shake the camera on heavy hits and boss slams.").define("cameraShake", true);
        FIRST_PERSON_SKILL_ANIMS = c.comment("Animate your arms in first person when using skills.").define("firstPersonSkillAnimations", true);
        c.pop();
        CLIENT_SPEC = c.build();

        ModConfigSpec.Builder s = new ModConfigSpec.Builder();
        s.push("fastTravel");
        FAST_TRAVEL = s.comment("Allow fast travel between attuned waypoints from the Frontier map.").define("enabled", true);
        FAST_TRAVEL_COOLDOWN = s.comment("Seconds between fast travels.").defineInRange("cooldownSeconds", 30, 0, 3600);
        FAST_TRAVEL_IN_COMBAT = s.comment("Allow fast travel while a boss is nearby or right after taking damage.").define("allowInCombat", false);
        FAST_TRAVEL_NEEDS_ATTUNEMENT = s.comment("Only waypoints the player has attuned to (by touching them) can be travelled to.")
                .define("requireAttunement", true);
        RESPAWN_AT_WAYPOINT = s.comment("Respawn at the last waypoint you attuned to after dying in the Frontier.")
                .define("respawnAtLastWaypoint", true);
        s.pop();
        SERVER_SPEC = s.build();
    }

    private SlfConfig() {
    }
}
