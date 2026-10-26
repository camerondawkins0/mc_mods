package com.slfrontier.command;

import com.mojang.authlib.GameProfile;
import com.slfrontier.SLFrontier;
import com.slfrontier.data.PlayerData;
import com.slfrontier.data.Stat;
import com.slfrontier.entity.boss.LycagonEntity;
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
                ModEntities.SETSUNA, ModEntities.EMUL);
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
            if (stage.ordinal() % 3 == 0) {
                check(failures, "compass " + stage, () -> Story.compassTarget(player));
            }
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
            if (data.stage() != QuestStage.NIGHTSLAYER_FINAL) throw new IllegalStateException("tombguard not cleared");
        });
        check(failures, "travel", () -> {
            FrontierTravel.toRabituza(player);
            FrontierTravel.toFastera(player);
            if (!Frontier.isIn(player)) throw new IllegalStateException("left the frontier");
        });

        // Clean up anything the test summoned.
        for (var e : player.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                player.getBoundingBox().inflate(64), m -> m instanceof WezaemonEntity || m instanceof com.slfrontier.entity.npc.EmulEntity)) {
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

    private static void check(List<String> failures, String name, Runnable test) {
        try {
            test.run();
        } catch (Throwable t) {
            SLFrontier.LOGGER.error("Story test '{}' failed", name, t);
            failures.add(name + ": " + t);
        }
    }
}
