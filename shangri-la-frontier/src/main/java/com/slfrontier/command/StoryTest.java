package com.slfrontier.command;

import com.mojang.authlib.GameProfile;
import com.slfrontier.SLFrontier;
import com.slfrontier.data.PlayerData;
import com.slfrontier.data.Stat;
import com.slfrontier.entity.FrontierBoss;
import com.slfrontier.entity.boss.LycagonEntity;
import com.slfrontier.entity.boss.RavenousSnakeEntity;
import com.slfrontier.entity.boss.WezaemonEntity;
import com.slfrontier.entity.npc.FrontierNpc;
import com.slfrontier.progression.Progression;
import com.slfrontier.quest.QuestStage;
import com.slfrontier.quest.Story;
import com.slfrontier.registry.ModAttachments;
import com.slfrontier.registry.ModEntities;
import com.slfrontier.registry.ModItems;
import com.slfrontier.skill.Skill;
import com.slfrontier.skill.SkillExecutor;
import com.slfrontier.world.Frontier;
import com.slfrontier.world.FrontierTravel;
import com.slfrontier.world.Landmarks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Drives the story, skills and progression with a fake player so a dedicated server
 * (and CI) can catch runtime errors without anyone logging in.
 */
public final class StoryTest {
    private static final GameProfile PROFILE = new GameProfile(UUID.fromString("5e1f0000-0000-4000-8000-00000000510f"), "[SLF-Test]");

    private StoryTest() {
    }

    /** @return a list of failures; empty means everything ran. */
    public static List<String> run(ServerLevel frontier) {
        List<String> failures = new ArrayList<>();
        BlockPos spawn = FrontierTravel.ensureFastera(frontier);
        FakePlayer player = FakePlayerFactory.get(frontier, PROFILE);
        player.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0f, 0f);
        PlayerData data = ModAttachments.get(player);

        check(failures, "progression", () -> {
            Progression.grantExp(player, 50_000);
            if (data.level() <= 1) throw new IllegalStateException("no level gained");
            for (Stat stat : Stat.values()) data.allocate(stat);
            Progression.applyStats(player);
        });

        for (Skill skill : Skill.values()) {
            check(failures, "skill " + skill.id, () -> {
                data.unlock(skill);
                data.setCooldown(skill, 0);
                data.setStamina(data.maxStamina());
                data.iframeTicks = 0;
                ItemStack weapon = switch (skill) {
                    case MOON_REFLECTION -> new ItemStack(ModItems.MOONBLADE_WAXING.get());
                    case CRESCENT_VORPAL -> new ItemStack(ModItems.TWIN_MOON.get());
                    default -> new ItemStack(ModItems.DUCUSRAM.get());
                };
                player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
                if (skill == Skill.DOUBLE_JUMP) SkillExecutor.doubleJump(player);
                else SkillExecutor.use(player, skill);
            });
        }

        List<Supplier<? extends EntityType<? extends FrontierNpc>>> npcs = List.of(
                ModEntities.GUIDE, ModEntities.BLACKSMITH, ModEntities.VYSACHE, ModEntities.BILAC,
                ModEntities.SETSUNA, ModEntities.EMUL, ModEntities.PENCILGON, ModEntities.OIKATZO);
        for (QuestStage stage : QuestStage.values()) {
            for (var type : npcs) {
                check(failures, "dialogue " + stage + " / " + type.get().getDescriptionId(), () -> {
                    data.setStage(stage);
                    giveStoryItems(player);
                    FrontierNpc npc = type.get().create(player.serverLevel());
                    if (npc == null) throw new IllegalStateException("could not create npc");
                    npc.moveTo(player.getX() + 1, player.getY(), player.getZ(), 0f, 0f);
                    player.serverLevel().addFreshEntity(npc);
                    Story.onTalk(player, npc);
                    Story.onDialogueChoice(player, npc.getId(), 0);
                    npc.discard();
                });
            }
            check(failures, "compass " + stage, () -> {
                data.setStage(stage);
                Story.compassTarget(player);
            });
        }

        check(failures, "lycagon story hooks", () -> {
            data.setStage(QuestStage.NIGHTSLAYER_ENCOUNTER);
            Story.onMarkedByLycagon(player);
            if (!data.cursed()) throw new IllegalStateException("mark not applied");
            LycagonEntity lycagon = ModEntities.LYCAGON.get().create(player.serverLevel());
            if (lycagon == null) throw new IllegalStateException("no lycagon");
            lycagon.moveTo(player.getX(), player.getY(), player.getZ() + 4, 0f, 0f);
            lycagon.configureFor(player);
            data.setStage(QuestStage.NIGHTSLAYER_FINAL);
            Story.onLycagonSlain(player.serverLevel(), lycagon, player);
            if (data.cursed() || data.stage() != QuestStage.COMPLETE) throw new IllegalStateException("final not completed");
        });
        check(failures, "wezaemon story hooks", () -> {
            data.setStage(QuestStage.TOMBGUARD);
            WezaemonEntity wez = ModEntities.WEZAEMON.get().create(player.serverLevel());
            if (wez == null) throw new IllegalStateException("no wezaemon");
            wez.moveTo(player.getX(), player.getY(), player.getZ() + 4, 0f, 0f);
            Story.onWezaemonSlain(player.serverLevel(), wez, player);
            if (data.stage() != QuestStage.LYCAGON_SHADOW) throw new IllegalStateException("tombguard not cleared");
        });
        check(failures, "new chapter hooks", () -> {
            ServerLevel level = player.serverLevel();
            data.setStage(QuestStage.RAVENOUS_SNAKE);
            Story.onRavenousSnakeSlain(level, player, player);
            expect(data, QuestStage.REACH_SECONDIL);
            data.setStage(QuestStage.VORPAL_COLOSSEUM);
            Story.onColosseumCleared(player);
            expect(data, QuestStage.MOONBLADES);
            data.setStage(QuestStage.MUD_DIGGER_ROAD);
            Story.onMudDiggerSlain(level, player, player);
            expect(data, QuestStage.THIRDREMA);
            data.setStage(QuestStage.LYCAGON_SHADOW);
            Story.onLycagonShadowSlain(level, player, player);
            expect(data, QuestStage.NIGHTSLAYER_FINAL);
            if (!data.flag("contusion")) throw new IllegalStateException("contusion not set");
        });
        check(failures, "landmarks", () -> {
            var structures = frontier.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
            for (Landmarks.Site site : Landmarks.Site.values()) {
                BlockPos at = Landmarks.ensure(frontier, site);
                SLFrontier.LOGGER.info("Story test: {} is at {}", site.id, at);
                var structure = structures.get(SLFrontier.id(site.id));
                BlockPos center = Landmarks.center(site);
                frontier.getChunk(center);
                if (structure == null || !frontier.structureManager().getStructureWithPieceAt(center, structure).isValid()) {
                    throw new IllegalStateException(site.id + " did not generate at " + center);
                }
            }
            if (Landmarks.marker(frontier, Landmarks.Site.RABITUZA, "arena") == null) throw new IllegalStateException("no colosseum marker");
            if (Landmarks.marker(frontier, Landmarks.Site.SERPENT_BRIDGE, "arena") == null) throw new IllegalStateException("no bridge marker");
        });
        check(failures, "serpent ambush", () -> {
            BlockPos bridge = Landmarks.built(frontier, Landmarks.Site.SERPENT_BRIDGE);
            if (bridge == null) throw new IllegalStateException("bridge not built");
            data.setStage(QuestStage.RAVENOUS_SNAKE);
            player.moveTo(bridge.getX() + 0.5, bridge.getY(), bridge.getZ() + 0.5);
            Story.tickPlayer(player);
            // A fake player doesn't keep chunks loaded, so the new entity isn't listed until the next tick;
            // check the serpent itself.
            RavenousSnakeEntity snake = Story.lastSnake();
            if (snake == null || snake.level() != frontier || snake.distanceTo(player) > 64) {
                throw new IllegalStateException("the snake did not appear (bridge " + bridge + ", player " + player.blockPosition() + ")");
            }
            snake.discard();
            player.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
        });
        // Every boss runs its whole moveset once against the test player.
        List<Supplier<? extends EntityType<? extends FrontierBoss>>> bosses = List.of(ModEntities.LYCAGON, ModEntities.WEZAEMON,
                ModEntities.KIRIN, ModEntities.MUD_DIGGER, ModEntities.EMPIRE_BEE_QUEEN, ModEntities.RAVENOUS_SNAKE,
                ModEntities.WOODMAGE, ModEntities.LYCAGON_SHADOW);
        data.setStage(QuestStage.COMPLETE);
        player.getAbilities().invulnerable = true;
        for (var type : bosses) {
            check(failures, "moveset " + type.get().getDescriptionId(), () -> {
                FrontierBoss boss = type.get().create(frontier);
                if (boss == null) throw new IllegalStateException("could not create boss");
                boss.moveTo(player.getX() + 3, player.getY(), player.getZ() + 3, 0f, 0f);
                frontier.addFreshEntity(boss);
                boss.setTarget(player);
                boss.exerciseAttacks(player, 7);
                boss.discard();
            });
        }
        player.getAbilities().invulnerable = false;
        check(failures, "travel", () -> {
            FrontierTravel.toRabituza(player);
            FrontierTravel.toFastera(player);
            if (!Frontier.isIn(player)) throw new IllegalStateException("left the frontier");
        });

        // Clean up anything the test summoned.
        for (var e : frontier.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, player.getBoundingBox().inflate(128),
                m -> m instanceof FrontierBoss || m instanceof com.slfrontier.entity.FrontierMonster
                        || m instanceof com.slfrontier.entity.npc.EmulEntity || m instanceof com.slfrontier.entity.npc.AllyNpc)) {
            e.discard();
        }
        return failures;
    }

    private static void giveStoryItems(FakePlayer player) {
        player.getInventory().clearContent();
        player.getInventory().add(new ItemStack(ModItems.GRAY_ORE_CHUNK.get(), 16));
        player.getInventory().add(new ItemStack(ModItems.VORPAL_CHOPPERS.get()));
        player.getInventory().add(new ItemStack(ModItems.VORPAL_CHOPPERS.get()));
        player.getInventory().add(new ItemStack(ModItems.QUAD_BEETLE_SHELL.get(), 2));
        player.getInventory().add(new ItemStack(ModItems.MARSH_DAGGER.get()));
        player.getInventory().add(new ItemStack(ModItems.MUD_DIGGER_FIN.get()));
    }

    private static void expect(PlayerData data, QuestStage stage) {
        if (data.stage() != stage) throw new IllegalStateException("expected " + stage + " but was " + data.stage());
    }

    private static void check(List<String> failures, String name, Runnable test) {
        try {
            test.run();
        } catch (Throwable t) {
            SLFrontier.LOGGER.error("Story test '{}' failed", name, t);
            failures.add(name + ": " + t);
        }
    }
}
