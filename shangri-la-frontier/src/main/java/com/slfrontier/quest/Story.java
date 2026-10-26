package com.slfrontier.quest;

import com.mojang.datafixers.util.Pair;
import com.slfrontier.data.PlayerData;
import com.slfrontier.entity.boss.LycagonEntity;
import com.slfrontier.entity.boss.WezaemonEntity;
import com.slfrontier.entity.mob.GoblinEntity;
import com.slfrontier.entity.npc.EmulEntity;
import com.slfrontier.entity.npc.FrontierNpc;
import com.slfrontier.network.ModNetwork;
import com.slfrontier.progression.Progression;
import com.slfrontier.registry.ModAttachments;
import com.slfrontier.registry.ModEntities;
import com.slfrontier.registry.ModItems;
import com.slfrontier.skill.Skill;
import com.slfrontier.world.Frontier;
import com.slfrontier.world.FrontierTravel;
import com.slfrontier.world.TownData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The main story of the mod, loosely following Sunraku's first steps in the anime:
 * Fastera's guide, the gray ore errand, being marked by Lycagon, Emul's invitation
 * to Rabituza, the Moonblades, Setsuna's Unique Scenario EX against Wezaemon the
 * Tombguard, and finally the hunt for Lycagon itself.
 */
public final class Story {
    public record Target(BlockPos pos, String nameKey) {
    }

    private record Pending(int entityId, List<Dialogue.Option> options) {
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private Story() {
    }

    // ---- Talking ---------------------------------------------------------------------

    public static void onTalk(ServerPlayer player, FrontierNpc npc) {
        Dialogue dialogue = switch (npc.npcId()) {
            case "frontier_guide" -> guide(player);
            case "frontier_blacksmith" -> blacksmith(player);
            case "emul" -> emul(player, (EmulEntity) npc);
            case "vysache" -> vysache(player);
            case "bilac" -> bilac(player);
            case "setsuna" -> setsuna(player, npc);
            default -> Dialogue.of(npc.npcId()).node("generic", 1).close();
        };
        open(player, npc, dialogue);
    }

    private static void open(ServerPlayer player, Entity npc, Dialogue dialogue) {
        PENDING.put(player.getUUID(), new Pending(npc.getId(), dialogue.options()));
        PacketDistributor.sendToPlayer(player, new ModNetwork.OpenDialogue(npc.getId(), dialogue.speakerKey(), dialogue.lines(),
                dialogue.options().stream().map(Dialogue.Option::labelKey).toList()));
    }

    public static void onDialogueChoice(ServerPlayer player, int entityId, int option) {
        Pending pending = PENDING.remove(player.getUUID());
        if (pending == null || pending.entityId() != entityId) return;
        Entity npc = player.level().getEntity(entityId);
        if (npc == null || npc.distanceToSqr(player) > 10 * 10) return;
        if (option < 0 || option >= pending.options().size()) return;
        pending.options().get(option).action().accept(player);
        ModNetwork.sync(player);
    }

    /** Opens a follow-up page from the same NPC, e.g. after an option is chosen. */
    private static void followUp(ServerPlayer player, String npcId, Dialogue dialogue) {
        Entity npc = player.level().getEntitiesOfClass(FrontierNpc.class, player.getBoundingBox().inflate(10),
                e -> e.npcId().equals(npcId)).stream().findFirst().orElse(null);
        if (npc != null) open(player, npc, dialogue);
    }

    // ---- Fastera ---------------------------------------------------------------------

    private static Dialogue guide(ServerPlayer player) {
        PlayerData data = ModAttachments.get(player);
        QuestStage stage = data.stage();
        Dialogue d = Dialogue.of("frontier_guide");
        switch (stage) {
            case NOT_STARTED, MEET_GUIDE -> d.node("guide.welcome", 4)
                    .option("accept_journey", Story::startJourney)
                    .option("ask_frontier", p -> followUp(p, "frontier_guide", Dialogue.of("frontier_guide")
                            .node("guide.about", 4).option("accept_journey", Story::startJourney).close()));
            case GOBLIN_HUNT -> {
                if (data.questProgress() >= QuestStage.GOBLIN_HUNT.goal) {
                    d.node("guide.goblins_done", 3).option("thanks", Story::finishGoblins);
                } else {
                    d.line("dialogue.slfrontier.guide.goblins_progress.1", data.questProgress(), QuestStage.GOBLIN_HUNT.goal)
                            .node("guide.goblins_progress_tip", 1).close();
                }
            }
            case GRAY_ORE -> d.node("guide.go_blacksmith", 2).close();
            case NIGHTSLAYER_ENCOUNTER -> d.node("guide.nightslayer_rumor", 4).close();
            case INVITATION, MEET_VYSACHE, MOONBLADES -> d.node("guide.marked", 3).close();
            case FIND_SETSUNA, TOMBGUARD -> d.node("guide.tombguard_rumor", 2).close();
            case NIGHTSLAYER_FINAL -> d.node("guide.final", 2).close();
            case COMPLETE -> d.node("guide.complete", 2).close();
        }
        return d;
    }

    private static void startJourney(ServerPlayer player) {
        PlayerData data = ModAttachments.get(player);
        if (data.stage().atLeast(QuestStage.GOBLIN_HUNT)) return;
        give(player, new ItemStack(ModItems.MERCENARY_TWIN_BLADES.get()));
        give(player, new ItemStack(ModItems.MERCENARY_TWIN_BLADES.get()));
        give(player, new ItemStack(ModItems.FRONTIER_COMPASS.get()));
        give(player, new ItemStack(Items.BREAD, 8));
        advance(player, QuestStage.GOBLIN_HUNT);
        player.sendSystemMessage(Component.translatable("message.slfrontier.tip.twin_blades").withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.translatable("message.slfrontier.tip.keys").withStyle(ChatFormatting.GRAY));
    }

    private static void finishGoblins(ServerPlayer player) {
        PlayerData data = ModAttachments.get(player);
        if (data.stage() != QuestStage.GOBLIN_HUNT || data.questProgress() < QuestStage.GOBLIN_HUNT.goal) return;
        give(player, new ItemStack(ModItems.BIRD_MASK.get()));
        Progression.grantExp(player, 60);
        advance(player, QuestStage.GRAY_ORE);
    }

    private static Dialogue blacksmith(ServerPlayer player) {
        PlayerData data = ModAttachments.get(player);
        Dialogue d = Dialogue.of("frontier_blacksmith");
        int ore = Progression.countItem(player, ModItems.GRAY_ORE_CHUNK.get());
        if (data.stage() == QuestStage.GRAY_ORE) {
            if (ore >= QuestStage.GRAY_ORE.goal) {
                d.node("smith.has_ore", 2).option("hand_over_ore", p -> {
                    if (ModAttachments.get(p).stage() != QuestStage.GRAY_ORE) return;
                    if (Progression.takeItem(p, ModItems.GRAY_ORE_CHUNK.get(), QuestStage.GRAY_ORE.goal) < QuestStage.GRAY_ORE.goal) return;
                    give(p, new ItemStack(ModItems.MARSH_DAGGER.get()));
                    give(p, new ItemStack(ModItems.MARSH_DAGGER.get()));
                    Progression.grantExp(p, 120);
                    advance(p, QuestStage.NIGHTSLAYER_ENCOUNTER);
                });
            } else {
                d.line("dialogue.slfrontier.smith.need_ore.1", ore, QuestStage.GRAY_ORE.goal).node("smith.need_ore_tip", 1);
            }
        } else if (!data.stage().atLeast(QuestStage.GRAY_ORE)) {
            d.node("smith.idle", 1);
        } else {
            d.node("smith.regular", 1);
        }
        if (data.stage().atLeast(QuestStage.NIGHTSLAYER_ENCOUNTER)) {
            d.option("buy_holy_water", p -> {
                if (Progression.countItem(p, ModItems.GRAY_ORE_CHUNK.get()) < 1 || Progression.countItem(p, Items.GLASS_BOTTLE) < 1) {
                    p.sendSystemMessage(Component.translatable("message.slfrontier.smith.holy_water_cost").withStyle(ChatFormatting.GRAY));
                    return;
                }
                Progression.takeItem(p, ModItems.GRAY_ORE_CHUNK.get(), 1);
                Progression.takeItem(p, Items.GLASS_BOTTLE, 1);
                give(p, new ItemStack(ModItems.HOLY_WATER.get(), 2));
            });
        }
        if (!data.flag("mud_bounty") && Progression.hasItem(player, ModItems.MUD_DIGGER_FIN.get())) {
            d.option("show_fin", p -> {
                PlayerData pd = ModAttachments.get(p);
                if (pd.flag("mud_bounty") || Progression.takeItem(p, ModItems.MUD_DIGGER_FIN.get(), 1) < 1) return;
                pd.setFlag("mud_bounty", true);
                give(p, new ItemStack(ModItems.REBIRTH_TEARJEWEL.get()));
                Progression.grantExp(p, 300);
                p.sendSystemMessage(Component.translatable("message.slfrontier.smith.bounty").withStyle(ChatFormatting.GOLD));
            });
        }
        return d.close();
    }

    // ---- Emul & Rabituza ------------------------------------------------------------

    private static Dialogue emul(ServerPlayer player, EmulEntity emul) {
        PlayerData data = ModAttachments.get(player);
        Dialogue d = Dialogue.of("emul");
        if (emul.owner() != null && !emul.isPartnerOf(player)) {
            return d.node("emul.other", 1).close();
        }
        if (emul.isPartnerOf(player)) {
            d.node(switch (data.stage()) {
                case MOONBLADES -> "emul.partner_moonblades";
                case FIND_SETSUNA, TOMBGUARD -> "emul.partner_tombguard";
                case NIGHTSLAYER_FINAL -> "emul.partner_final";
                case COMPLETE -> "emul.partner_complete";
                default -> "emul.partner";
            }, 2);
            d.option("go_rabituza", FrontierTravel::toRabituza);
            d.option("emul_rest", p -> {
                EmulEntity e = EmulEntity.findCompanion(p, 16);
                if (e != null) e.discard();
            });
            return d.close();
        }
        if (data.stage() == QuestStage.INVITATION) {
            return d.node("emul.invite", 5)
                    .option("accept_invitation", p -> {
                        if (ModAttachments.get(p).stage() != QuestStage.INVITATION) return;
                        advance(p, QuestStage.MEET_VYSACHE);
                        ModNetwork.announce(p, "announce.slfrontier.unique_scenario", "announce.slfrontier.invitation", 0xC9A0FF);
                        emul.discard();
                        FrontierTravel.toRabituza(p);
                    })
                    .option("decline", p -> {
                    });
        }
        if (data.cursed()) return d.node("emul.marked_stranger", 2).close();
        return d.node("emul.shy", 2).close();
    }

    private static Dialogue vysache(ServerPlayer player) {
        PlayerData data = ModAttachments.get(player);
        Dialogue d = Dialogue.of("vysache");
        switch (data.stage()) {
            case MEET_VYSACHE -> d.node("vysache.meet", 6).option("bow", p -> {
                PlayerData pd = ModAttachments.get(p);
                if (pd.stage() != QuestStage.MEET_VYSACHE) return;
                give(p, new ItemStack(ModItems.VORPAL_CHOPPERS.get()));
                give(p, new ItemStack(ModItems.VORPAL_CHOPPERS.get()));
                give(p, new ItemStack(ModItems.VORPAL_BELL.get()));
                give(p, new ItemStack(ModItems.VORPAL_SOUL_COLLAR.get()));
                pd.setFlag("emul_companion", true);
                pd.setFlag("rabituza_visited", true);
                if (pd.unlock(Skill.RABBIT_GUN)) Progression.announceSkill(p, Skill.RABBIT_GUN);
                EmulEntity.summonFor(p);
                advance(p, QuestStage.MOONBLADES);
            });
            case MOONBLADES -> {
                boolean shell = Progression.hasItem(player, ModItems.QUAD_BEETLE_SHELL.get());
                boolean choppers = Progression.countItem(player, ModItems.VORPAL_CHOPPERS.get()) >= 2;
                if (shell && choppers) {
                    d.node("vysache.awaken", 4).option("offer_blades", p -> {
                        if (ModAttachments.get(p).stage() != QuestStage.MOONBLADES) return;
                        if (Progression.countItem(p, ModItems.VORPAL_CHOPPERS.get()) < 2 || !Progression.hasItem(p, ModItems.QUAD_BEETLE_SHELL.get())) return;
                        Progression.takeItem(p, ModItems.VORPAL_CHOPPERS.get(), 2);
                        Progression.takeItem(p, ModItems.QUAD_BEETLE_SHELL.get(), 1);
                        give(p, new ItemStack(ModItems.MOONBLADE_WAXING.get()));
                        give(p, new ItemStack(ModItems.MOONBLADE_WANING.get()));
                        PlayerData pd = ModAttachments.get(p);
                        if (pd.unlock(Skill.MOON_REFLECTION)) Progression.announceSkill(p, Skill.MOON_REFLECTION);
                        p.level().playSound(null, p.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 1f, 0.8f);
                        ModNetwork.announce(p, "announce.slfrontier.moonblades", "announce.slfrontier.moonblades.sub", 0xEDEDFF);
                        Progression.grantExp(p, 500);
                        advance(p, QuestStage.FIND_SETSUNA);
                    });
                } else {
                    d.node(choppers ? "vysache.need_shell" : "vysache.need_choppers", 2);
                }
            }
            case FIND_SETSUNA, TOMBGUARD -> d.node("vysache.setsuna", 3);
            case NIGHTSLAYER_FINAL -> {
                d.node("vysache.final", 3);
                if (!data.hasSkill(Skill.CRESCENT_VORPAL)) {
                    d.option("learn_crescent", p -> {
                        if (ModAttachments.get(p).unlock(Skill.CRESCENT_VORPAL)) {
                            Progression.announceSkill(p, Skill.CRESCENT_VORPAL);
                            ModNetwork.announce(p, "announce.slfrontier.skill_learned", Skill.CRESCENT_VORPAL.nameKey(), 0xEDEDFF);
                        }
                    });
                }
            }
            case COMPLETE -> d.node("vysache.complete", 2);
            default -> d.node("vysache.stranger", 2);
        }
        return d.option("return_fastera", FrontierTravel::toFastera).close();
    }

    private static Dialogue bilac(ServerPlayer player) {
        Dialogue d = Dialogue.of("bilac").node("bilac.hello", 2);
        d.option("forge_ducusram", p -> craft(p, new ItemStack(ModItems.DUCUSRAM.get()),
                req(ModItems.MARSH_DAGGER.get(), 1), req(ModItems.GRAY_ORE_CHUNK.get(), 3)));
        d.option("forge_bee_blades", p -> craft(p, new ItemStack(ModItems.EMPIRE_BEE_TWINBLADES.get(), 2),
                req(ModItems.EMPIRE_BEE_QUEEN_CROWN.get(), 1), req(ModItems.EMPIRE_BEE_STINGER.get(), 4)));
        d.option("forge_battlehorn", p -> craft(p, new ItemStack(ModItems.BATTLEHORN_HELM.get()),
                req(ModItems.QUAD_BEETLE_SHELL.get(), 2)));
        d.option("brew_tonic", p -> craft(p, new ItemStack(ModItems.REALLOCATION_TONIC.get()),
                req(ModItems.PRISMATIC_SHARD.get(), 4)));
        if (ModAttachments.get(player).stage().atLeast(QuestStage.MOONBLADES)) {
            d.option("return_fastera", FrontierTravel::toFastera);
        }
        return d.close();
    }

    private record Req(Item item, int count) {
    }

    private static Req req(Item item, int count) {
        return new Req(item, count);
    }

    private static void craft(ServerPlayer player, ItemStack result, Req... reqs) {
        for (Req r : reqs) {
            if (Progression.countItem(player, r.item()) < r.count()) {
                MutableList missing = new MutableList();
                for (Req q : reqs) {
                    missing.add(Component.literal(q.count() + "x ").append(q.item().getDescription()));
                }
                player.sendSystemMessage(Component.translatable("message.slfrontier.bilac.missing", missing.join())
                        .withStyle(ChatFormatting.GRAY));
                return;
            }
        }
        for (Req r : reqs) Progression.takeItem(player, r.item(), r.count());
        give(player, result);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 1f, 1.2f);
        player.sendSystemMessage(Component.translatable("message.slfrontier.bilac.done", result.getHoverName()).withStyle(ChatFormatting.GOLD));
    }

    /** Tiny helper to join components with commas. */
    private static final class MutableList {
        private final java.util.ArrayList<Component> parts = new java.util.ArrayList<>();

        void add(Component c) {
            parts.add(c);
        }

        Component join() {
            var out = Component.empty();
            for (int i = 0; i < parts.size(); i++) {
                if (i > 0) out.append(", ");
                out.append(parts.get(i));
            }
            return out;
        }
    }

    // ---- The Hidden Garden ----------------------------------------------------------

    private static Dialogue setsuna(ServerPlayer player, FrontierNpc setsuna) {
        PlayerData data = ModAttachments.get(player);
        Dialogue d = Dialogue.of("setsuna");
        switch (data.stage()) {
            case FIND_SETSUNA -> d.node("setsuna.meet", 6)
                    .option("accept_scenario", p -> {
                        if (ModAttachments.get(p).stage() != QuestStage.FIND_SETSUNA) return;
                        give(p, new ItemStack(ModItems.HOLY_WATER.get(), 4));
                        advance(p, QuestStage.TOMBGUARD);
                        ModNetwork.announce(p, "announce.slfrontier.unique_scenario_ex", "announce.slfrontier.tombguard", 0x8FD8FF);
                        p.getServer().getPlayerList().broadcastSystemMessage(Component.translatable(
                                "message.slfrontier.scenario_ex_started", p.getDisplayName()).withStyle(ChatFormatting.AQUA), false);
                        summonWezaemon(p, setsuna);
                    })
                    .option("not_yet", p -> {
                    });
            case TOMBGUARD -> {
                boolean present = !player.level().getEntitiesOfClass(WezaemonEntity.class, setsuna.getBoundingBox().inflate(48)).isEmpty();
                d.node("setsuna.fight", 2);
                if (!present) d.option("call_tombguard", p -> summonWezaemon(p, setsuna));
                d.close();
            }
            case NIGHTSLAYER_FINAL, COMPLETE -> d.node("setsuna.thanks", 4).close();
            default -> d.node("setsuna.early", 2).close();
        }
        return d;
    }

    private static void summonWezaemon(ServerPlayer player, FrontierNpc setsuna) {
        ServerLevel level = player.serverLevel();
        if (!level.getEntitiesOfClass(WezaemonEntity.class, setsuna.getBoundingBox().inflate(48)).isEmpty()) return;
        WezaemonEntity wez = ModEntities.WEZAEMON.get().create(level);
        if (wez == null) return;
        BlockPos pos = setsuna.blockPosition();
        double angle = Math.atan2(player.getZ() - setsuna.getZ(), player.getX() - setsuna.getX());
        double x = setsuna.getX() + Math.cos(angle) * 7;
        double z = setsuna.getZ() + Math.sin(angle) * 7;
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) x, (int) z);
        wez.moveTo(x, Math.max(y, pos.getY()), z, 0, 0);
        wez.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
        wez.setTarget(player);
        level.addFreshEntity(wez);
        level.sendParticles(ParticleTypes.SOUL, wez.getX(), wez.getY() + 1.5, wez.getZ(), 80, 0.8, 1.5, 0.8, 0.02);
        level.playSound(null, wez.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1f, 0.6f);
    }

    // ---- Combat milestones ----------------------------------------------------------

    public static void onKill(ServerPlayer player, LivingEntity killed) {
        PlayerData data = ModAttachments.get(player);
        if (data.stage() == QuestStage.GOBLIN_HUNT && killed instanceof GoblinEntity && data.questProgress() < QuestStage.GOBLIN_HUNT.goal) {
            data.setQuestProgress(data.questProgress() + 1);
            player.displayClientMessage(Component.translatable("message.slfrontier.goblins", data.questProgress(), QuestStage.GOBLIN_HUNT.goal)
                    .withStyle(ChatFormatting.GREEN), true);
            if (data.questProgress() >= QuestStage.GOBLIN_HUNT.goal) {
                player.sendSystemMessage(Component.translatable("message.slfrontier.goblins_done").withStyle(ChatFormatting.GREEN));
            }
        }
    }

    public static void onMarkedByLycagon(ServerPlayer player) {
        PlayerData data = ModAttachments.get(player);
        data.setCursed(true);
        ModNetwork.announce(player, "announce.slfrontier.marked", "announce.slfrontier.marked.sub", 0xB01E2E);
        player.sendSystemMessage(Component.translatable("message.slfrontier.marked").withStyle(ChatFormatting.DARK_RED));
        if (data.stage() == QuestStage.NIGHTSLAYER_ENCOUNTER) {
            advance(player, QuestStage.INVITATION);
            ServerLevel level = player.serverLevel();
            EmulEntity emul = ModEntities.EMUL.get().create(level);
            if (emul != null) {
                emul.moveTo(player.getX() + 2, player.getY(), player.getZ() + 2, player.getYRot() + 180, 0);
                level.addFreshEntity(emul);
                level.sendParticles(ParticleTypes.CHERRY_LEAVES, emul.getX(), emul.getY() + 0.5, emul.getZ(), 20, 0.4, 0.4, 0.4, 0);
            }
        }
    }

    public static void onLycagonSlain(ServerLevel level, LycagonEntity lycagon, @Nullable Entity killer) {
        Component who = killer != null ? killer.getDisplayName() : Component.literal("?");
        level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("message.slfrontier.lycagon.slain", who)
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        for (ServerPlayer p : participants(level, lycagon, killer, 64)) {
            PlayerData data = ModAttachments.get(p);
            if (data.cursed()) {
                data.setCursed(false);
                p.sendSystemMessage(Component.translatable("message.slfrontier.curse_lifted").withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            if (data.stage() == QuestStage.NIGHTSLAYER_FINAL) {
                Progression.grantExp(p, 2000);
                give(p, new ItemStack(ModItems.LYCAGON_FANG.get()));
                advance(p, QuestStage.COMPLETE);
                ModNetwork.announce(p, "announce.slfrontier.complete", "announce.slfrontier.complete.sub", 0xFFD34F);
            }
        }
    }

    public static void onWezaemonSlain(ServerLevel level, WezaemonEntity wezaemon, @Nullable Entity killer) {
        level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("message.slfrontier.wezaemon.subjugated")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);
        for (ServerPlayer p : participants(level, wezaemon, killer, 48)) {
            PlayerData data = ModAttachments.get(p);
            if (data.stage() == QuestStage.TOMBGUARD) {
                give(p, new ItemStack(ModItems.SEITEN_SKILLBOOK.get()));
                Progression.grantExp(p, 1500);
                advance(p, QuestStage.NIGHTSLAYER_FINAL);
                ModNetwork.announce(p, "announce.slfrontier.scenario_clear", "announce.slfrontier.tombguard", 0x8FD8FF);
                p.sendSystemMessage(Component.translatable("dialogue.slfrontier.setsuna.farewell").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
            }
        }
    }

    /** Everyone near the boss, plus whoever landed the final blow (even from far away). */
    private static java.util.Set<ServerPlayer> participants(ServerLevel level, Entity boss, @Nullable Entity killer, double radius) {
        java.util.Set<ServerPlayer> players = new java.util.LinkedHashSet<>(
                level.getEntitiesOfClass(ServerPlayer.class, boss.getBoundingBox().inflate(radius)));
        if (killer instanceof ServerPlayer sp) players.add(sp);
        return players;
    }

    // ---- Stage helpers ---------------------------------------------------------------

    public static void advance(ServerPlayer player, QuestStage stage) {
        PlayerData data = ModAttachments.get(player);
        data.setStage(stage);
        player.sendSystemMessage(Component.translatable("message.slfrontier.quest_updated",
                Component.translatable(stage.titleKey()).withStyle(ChatFormatting.GOLD)).withStyle(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.translatable(stage.objectiveKey()).withStyle(ChatFormatting.GRAY));
        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.6f, 1.2f);
        ModNetwork.sync(player);
    }

    public static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    // ---- Compass ---------------------------------------------------------------------

    @Nullable
    public static Target compassTarget(ServerPlayer player) {
        if (!Frontier.isIn(player)) return null;
        ServerLevel level = player.serverLevel();
        PlayerData data = ModAttachments.get(player);
        TownData towns = TownData.get(level);
        BlockPos here = player.blockPosition();
        return switch (data.stage()) {
            case NOT_STARTED, MEET_GUIDE, GOBLIN_HUNT -> towns.fastera() == null ? null : new Target(towns.fastera(), "target.slfrontier.fastera");
            case GRAY_ORE -> Progression.countItem(player, ModItems.GRAY_ORE_CHUNK.get()) >= QuestStage.GRAY_ORE.goal
                    ? (towns.fastera() == null ? null : new Target(towns.fastera(), "target.slfrontier.fastera"))
                    : biome(level, here, Frontier.DIREMARSH_WASTE, "target.slfrontier.diremarsh");
            case NIGHTSLAYER_ENCOUNTER, NIGHTSLAYER_FINAL -> structure(level, here, Frontier.NIGHTSLAYER_DEN, "target.slfrontier.den");
            case INVITATION -> null;
            case MEET_VYSACHE -> towns.rabituza() == null ? null : new Target(towns.rabituza(), "target.slfrontier.rabituza");
            case MOONBLADES -> Progression.hasItem(player, ModItems.QUAD_BEETLE_SHELL.get()) && towns.rabituza() != null
                    ? new Target(towns.rabituza(), "target.slfrontier.rabituza")
                    : biome(level, here, Frontier.PRISMATIC_FOREST_GROTTO, "target.slfrontier.grotto");
            case FIND_SETSUNA, TOMBGUARD -> structure(level, here, Frontier.WEZAEMON_TOMB, "target.slfrontier.tomb");
            case COMPLETE -> null;
        };
    }

    @Nullable
    private static Target structure(ServerLevel level, BlockPos from, TagKey<Structure> tag, String name) {
        BlockPos pos = level.findNearestMapStructure(tag, from, 100, false);
        return pos == null ? null : new Target(pos, name);
    }

    @Nullable
    private static Target biome(ServerLevel level, BlockPos from, ResourceKey<Biome> biome, String name) {
        Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(h -> h.is(biome), from, 4000, 32, 64);
        return found == null ? null : new Target(found.getFirst(), name);
    }

    public static void forget(ServerPlayer player) {
        PENDING.remove(player.getUUID());
    }
}
