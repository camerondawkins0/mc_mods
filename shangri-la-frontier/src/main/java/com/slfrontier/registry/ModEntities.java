package com.slfrontier.registry;

import com.slfrontier.SLFrontier;
import com.slfrontier.entity.boss.EmpireBeeQueenEntity;
import com.slfrontier.entity.boss.LycagonEntity;
import com.slfrontier.entity.boss.MudDiggerEntity;
import com.slfrontier.entity.boss.WezaemonEntity;
import com.slfrontier.entity.mob.EmpireBeeEntity;
import com.slfrontier.entity.mob.GoblinEntity;
import com.slfrontier.entity.mob.QuadBeetleEntity;
import com.slfrontier.entity.mob.ShadowWolfEntity;
import com.slfrontier.entity.mob.VorpalBunnyEntity;
import com.slfrontier.entity.npc.EmulEntity;
import com.slfrontier.entity.npc.FrontierNpc;
import com.slfrontier.entity.projectile.HolyWaterEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, SLFrontier.MODID);

    // Monsters
    public static final DeferredHolder<EntityType<?>, EntityType<GoblinEntity>> GOBLIN = ENTITIES.register("goblin",
            () -> EntityType.Builder.of(GoblinEntity::new, MobCategory.MONSTER).sized(0.6f, 1.05f).clientTrackingRange(8).build("goblin"));
    public static final DeferredHolder<EntityType<?>, EntityType<VorpalBunnyEntity>> VORPAL_BUNNY = ENTITIES.register("vorpal_bunny",
            () -> EntityType.Builder.of(VorpalBunnyEntity::new, MobCategory.MONSTER).sized(0.5f, 0.65f).clientTrackingRange(8).build("vorpal_bunny"));
    public static final DeferredHolder<EntityType<?>, EntityType<EmpireBeeEntity>> EMPIRE_BEE = ENTITIES.register("empire_bee",
            () -> EntityType.Builder.of(EmpireBeeEntity::new, MobCategory.MONSTER).sized(0.8f, 0.7f).clientTrackingRange(8).build("empire_bee"));
    public static final DeferredHolder<EntityType<?>, EntityType<QuadBeetleEntity>> QUAD_BEETLE = ENTITIES.register("quad_beetle",
            () -> EntityType.Builder.of(QuadBeetleEntity::new, MobCategory.MONSTER).sized(2.2f, 1.5f).clientTrackingRange(10).build("quad_beetle"));
    public static final DeferredHolder<EntityType<?>, EntityType<ShadowWolfEntity>> SHADOW_WOLF = ENTITIES.register("shadow_wolf",
            () -> EntityType.Builder.of(ShadowWolfEntity::new, MobCategory.MONSTER).sized(0.8f, 0.9f).clientTrackingRange(10).build("shadow_wolf"));

    // Bosses
    public static final DeferredHolder<EntityType<?>, EntityType<LycagonEntity>> LYCAGON = ENTITIES.register("lycagon",
            () -> EntityType.Builder.of(LycagonEntity::new, MobCategory.MONSTER).sized(2.8f, 3.4f).clientTrackingRange(16).fireImmune().build("lycagon"));
    public static final DeferredHolder<EntityType<?>, EntityType<WezaemonEntity>> WEZAEMON = ENTITIES.register("wezaemon",
            () -> EntityType.Builder.of(WezaemonEntity::new, MobCategory.MONSTER).sized(1.0f, 2.7f).clientTrackingRange(16).fireImmune().build("wezaemon"));
    public static final DeferredHolder<EntityType<?>, EntityType<MudDiggerEntity>> MUD_DIGGER = ENTITIES.register("mud_digger",
            () -> EntityType.Builder.of(MudDiggerEntity::new, MobCategory.MONSTER).sized(2.0f, 2.2f).clientTrackingRange(12).build("mud_digger"));
    public static final DeferredHolder<EntityType<?>, EntityType<EmpireBeeQueenEntity>> EMPIRE_BEE_QUEEN = ENTITIES.register("empire_bee_queen",
            () -> EntityType.Builder.of(EmpireBeeQueenEntity::new, MobCategory.MONSTER).sized(1.6f, 1.6f).clientTrackingRange(12).build("empire_bee_queen"));

    // NPCs
    public static final DeferredHolder<EntityType<?>, EntityType<FrontierNpc>> GUIDE = npc("frontier_guide", 0.6f, 1.95f);
    public static final DeferredHolder<EntityType<?>, EntityType<FrontierNpc>> BLACKSMITH = npc("frontier_blacksmith", 0.6f, 1.95f);
    public static final DeferredHolder<EntityType<?>, EntityType<FrontierNpc>> VYSACHE = npc("vysache", 0.9f, 2.4f);
    public static final DeferredHolder<EntityType<?>, EntityType<FrontierNpc>> BILAC = npc("bilac", 0.5f, 1.2f);
    public static final DeferredHolder<EntityType<?>, EntityType<FrontierNpc>> SETSUNA = npc("setsuna", 0.6f, 1.8f);
    public static final DeferredHolder<EntityType<?>, EntityType<EmulEntity>> EMUL = ENTITIES.register("emul",
            () -> EntityType.Builder.of(EmulEntity::new, MobCategory.MISC).sized(0.45f, 0.75f).clientTrackingRange(10).build("emul"));

    // Projectiles
    public static final DeferredHolder<EntityType<?>, EntityType<HolyWaterEntity>> HOLY_WATER = ENTITIES.register("holy_water",
            () -> EntityType.Builder.<HolyWaterEntity>of(HolyWaterEntity::new, MobCategory.MISC).sized(0.25f, 0.25f)
                    .clientTrackingRange(4).updateInterval(10).build("holy_water"));

    private ModEntities() {
    }

    private static DeferredHolder<EntityType<?>, EntityType<FrontierNpc>> npc(String name, float w, float h) {
        return ENTITIES.register(name, () -> EntityType.Builder.of(FrontierNpc::new, MobCategory.MISC).sized(w, h).clientTrackingRange(10).build(name));
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(GOBLIN.get(), GoblinEntity.createAttributes().build());
        event.put(VORPAL_BUNNY.get(), VorpalBunnyEntity.createAttributes().build());
        event.put(EMPIRE_BEE.get(), EmpireBeeEntity.createAttributes().build());
        event.put(QUAD_BEETLE.get(), QuadBeetleEntity.createAttributes().build());
        event.put(SHADOW_WOLF.get(), ShadowWolfEntity.createAttributes().build());
        event.put(LYCAGON.get(), LycagonEntity.createAttributes().build());
        event.put(WEZAEMON.get(), WezaemonEntity.createAttributes().build());
        event.put(MUD_DIGGER.get(), MudDiggerEntity.createAttributes().build());
        event.put(EMPIRE_BEE_QUEEN.get(), EmpireBeeQueenEntity.createAttributes().build());
        event.put(GUIDE.get(), FrontierNpc.createAttributes().build());
        event.put(BLACKSMITH.get(), FrontierNpc.createAttributes().build());
        event.put(VYSACHE.get(), FrontierNpc.createAttributes().build());
        event.put(BILAC.get(), FrontierNpc.createAttributes().build());
        event.put(SETSUNA.get(), FrontierNpc.createAttributes().build());
        event.put(EMUL.get(), FrontierNpc.createAttributes().build());
    }

    public static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        var op = RegisterSpawnPlacementsEvent.Operation.REPLACE;
        event.register(GOBLIN.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkAnyLightMonsterSpawnRules, op);
        event.register(VORPAL_BUNNY.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkAnyLightMonsterSpawnRules, op);
        event.register(EMPIRE_BEE.get(), SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkAnyLightMonsterSpawnRules, op);
        event.register(QUAD_BEETLE.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkAnyLightMonsterSpawnRules, op);
        event.register(SHADOW_WOLF.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules, op);
    }
}
