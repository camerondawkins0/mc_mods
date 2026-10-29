package com.slfrontier.quest;

import com.mojang.datafixers.util.Pair;
import com.slfrontier.crafting.FrontierCrafting;
import com.slfrontier.crafting.Station;
import com.slfrontier.data.PlayerData;
import com.slfrontier.entity.boss.LycagonEntity;
import com.slfrontier.entity.boss.RavenousSnakeEntity;
import com.slfrontier.entity.boss.WezaemonEntity;
import com.slfrontier.entity.mob.GoblinEntity;
import com.slfrontier.entity.npc.AllyNpc;
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
import com.slfrontier.world.Landmarks;
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
 * The main story of the mod, following Sunraku's road in the anime: Firstia's guide, the
 * Ravenous Snake at the bridge to Secondil, the gray ore errand, being marked by Lycagon,
 * Emul's invitation to Rabituza and the Vorpal Colosseum, the Moonblades, the Mud Digger
 * on the road to Thirdrema, Pencilgon and Oikatzo at the Serpent's Apple, Setsuna's Unique
 * Scenario EX against Wezaemon the Tombguard, Lycagon's shadow in the Lightless Barrens,
 * and finally the hunt for Lycagon itself.
 */
public final class Story {
    public record Target(BlockPos pos, String nameKey) {
    }

    private record Pending(int entityId, List<Dialogue.Option> options) {
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    /** Tag on allies called into a fight, so they can be sent home afterwards. */
    private static final String ALLY_TAG = "slfrontier_summoned_ally";

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
            case "pencilgon" -> pencilgon(player);
            case "oikatzo" -> oikatzo(player);
            default -> Dialogue.of(npc.npcId()).node("generic", 1).close();
        };
        addSideQuestOptions(player, npc.npcId(), dialogue);
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
            case RAVENOUS_SNAKE -> d.node("guide.snake_hint", 3).close();
            case REACH_SECONDIL -> d.node("guide.secondil", 2).close();
            case GRAY_ORE -> d.node("guide.go_blacksmith", 2).close();
            case NIGHTSLAYER_ENCOUNTER -> d.node("guide.nightslayer_rumor", 4).close();
            case INVITATION -> {
                d.node("guide.marked", 3);
                if (player.level().getEntitiesOfClass(EmulEntity.class, player.getBoundingBox().inflate(48),
                        e -> e.owner() == null).isEmpty()) {
                    d.option("find_emul", Story::spawnInvitingEmul);
                }
                d.close();
            }
            case MEET_VYSACHE, VORPAL_COLOSSEUM -> d.node("guide.marked", 3).option("go_rabituza", FrontierTravel::toRabituza).close();
            case MOONBLADES -> d.node("guide.marked", 3).close();
            case MUD_DIGGER_ROAD, THIRDREMA -> d.node("guide.thirdrema", 2).close();
            case FIND_SETSUNA, TOMBGUARD -> d.node("guide.tombguard_rumor", 2).close();
            case LYCAGON_SHADOW -> d.node("guide.shadow", 2).close();
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
        advance(player, QuestStage.RAVENOUS_SNAKE);
    }

    private static Dialogue blacksmith(ServerPlayer player) {
        PlayerData data = ModAttachments.get(player);
        Dialogue d = Dialogue.of("frontier_blacksmith");
        int ore = Progression.countItem(player, ModItems.GRAY_ORE_CHUNK.get());
        if (data.stage() == QuestStage.REACH_SECONDIL) {
            return d.node("smith.meet", 4).option("take_ore_job", p -> {
                if (ModAttachments.get(p).stage() != QuestStage.REACH_SECONDIL) return;
                Progression.grantExp(p, 80);
                advance(p, QuestStage.GRAY_ORE);
            }).close();
        }
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
        if (data.stage().atLeast(QuestStage.GRAY_ORE)) {
            d.option("open_forge", p -> FrontierCrafting.openNear(p, Station.FORGE, "frontier_blacksmith"));
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
                case VORPAL_COLOSSEUM -> "emul.partner_colosseum";
                case MOONBLADES -> "emul.partner_moonblades";
                case MUD_DIGGER_ROAD -> "emul.partner_mud_digger";
                case THIRDREMA -> "emul.partner_thirdrema";
                case FIND_SETSUNA, TOMBGUARD -> "emul.partner_tombguard";
                case LYCAGON_SHADOW -> "emul.partner_shadow";
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
                advance(p, QuestStage.VORPAL_COLOSSEUM);
            });
            case VORPAL_COLOSSEUM -> d.node("vysache.colosseum", 4)
                    .option("begin_trial", ColosseumTrial::start)
                    .option("not_yet", p -> {
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
                        advance(p, QuestStage.MUD_DIGGER_ROAD);
                    });
                } else {
                    d.node(choppers ? "vysache.need_shell" : "vysache.need_choppers", 2);
                }
            }
            case MUD_DIGGER_ROAD, THIRDREMA -> d.node("vysache.thirdrema", 3);
            case FIND_SETSUNA, TOMBGUARD -> d.node("vysache.setsuna", 3);
            case LYCAGON_SHADOW -> d.node("vysache.shadow", 4);
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
        d.option("open_forge", p -> FrontierCrafting.openNear(p, Station.FORGE, "bilac"));
        if (ModAttachments.get(player).stage().atLeast(QuestStage.VORPAL_COLOSSEUM)) {
            d.option("return_fastera", FrontierTravel::toFastera);
        }
        return d.close();
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
        if (ModAttachments.get(player).flag("allies_joined")) callAllies(player);
    }

    /** Pencilgon and Oikatzo log in next to the player to fight the Tombguard together. */
    private static void callAllies(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        int i = 0;
        for (var type : List.of(ModEntities.PENCILGON, ModEntities.OIKATZO)) {
            if (!level.getEntitiesOfClass(AllyNpc.class, player.getBoundingBox().inflate(48), e -> e.getType() == type.get()).isEmpty()) continue;
            AllyNpc ally = type.get().create(level);
            if (ally == null) continue;
            double a = player.getYRot() * Math.PI / 180 + (i++ == 0 ? 2.2 : -2.2);
            ally.moveTo(player.getX() - Math.sin(a) * 2.5, player.getY(), player.getZ() + Math.cos(a) * 2.5, player.getYRot(), 0);
            ally.addTag(ALLY_TAG);
            level.addFreshEntity(ally);
            level.sendParticles(ParticleTypes.END_ROD, ally.getX(), ally.getY() + 1, ally.getZ(), 30, 0.3, 0.8, 0.3, 0.05);
        }
        player.sendSystemMessage(Component.translatable("message.slfrontier.allies_join").withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    /** Summoned allies log out once the fight is over. */
    private static void dismissAllies(ServerLevel level, Entity around) {
        for (AllyNpc ally : level.getEntitiesOfClass(AllyNpc.class, around.getBoundingBox().inflate(64), e -> e.getTags().contains(ALLY_TAG))) {
            level.sendParticles(ParticleTypes.END_ROD, ally.getX(), ally.getY() + 1, ally.getZ(), 30, 0.3, 0.8, 0.3, 0.05);
            ally.discard();
        }
    }

    // ---- Thirdrema: the Serpent's Apple ----------------------------------------------

    private static Dialogue pencilgon(ServerPlayer player) {
        PlayerData data = ModAttachments.get(player);
        Dialogue d = Dialogue.of("pencilgon");
        QuestStage stage = data.stage();
        if (stage == QuestStage.THIRDREMA) {
            return d.node("pencilgon.meet", 6).option("ask_scenario", p -> {
                if (ModAttachments.get(p).stage() != QuestStage.THIRDREMA) return;
                PlayerData pd = ModAttachments.get(p);
                pd.setFlag("allies_joined", true);
                give(p, new ItemStack(ModItems.HOLY_WATER.get(), 2));
                Progression.grantExp(p, 300);
                advance(p, QuestStage.FIND_SETSUNA);
                ModNetwork.announce(p, "announce.slfrontier.party", "announce.slfrontier.party.sub", 0xC9A0FF);
            }).close();
        }
        if (stage == QuestStage.FIND_SETSUNA || stage == QuestStage.TOMBGUARD) return d.node("pencilgon.ready", 3).close();
        if (stage.atLeast(QuestStage.LYCAGON_SHADOW)) return d.node("pencilgon.after", 3).close();
        return d.node("pencilgon.stranger", 2).close();
    }

    private static Dialogue oikatzo(ServerPlayer player) {
        QuestStage stage = ModAttachments.get(player).stage();
        Dialogue d = Dialogue.of("oikatzo");
        if (stage == QuestStage.THIRDREMA) return d.node("oikatzo.meet", 3).close();
        if (stage == QuestStage.FIND_SETSUNA || stage == QuestStage.TOMBGUARD) return d.node("oikatzo.ready", 2).close();
        if (stage.atLeast(QuestStage.LYCAGON_SHADOW)) return d.node("oikatzo.after", 2).close();
        return d.node("oikatzo.stranger", 2).close();
    }

    // ---- Combat milestones ----------------------------------------------------------

    public static void onKill(ServerPlayer player, LivingEntity killed) {
        PlayerData data = ModAttachments.get(player);
        String typeId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(killed.getType()).toString();
        for (String id : List.copyOf(data.activeSideIds())) {
            SideQuest q = SideQuest.byId(id);
            if (q != null && q.objective == SideQuest.Objective.KILL && q.target.equals(typeId) && data.sideProgress(q) < q.goal) {
                data.setSideProgress(q, data.sideProgress(q) + 1);
                player.displayClientMessage(Component.translatable("message.slfrontier.side_progress",
                        Component.translatable(q.titleKey()), data.sideProgress(q), q.goal).withStyle(ChatFormatting.AQUA), true);
                if (data.sideProgress(q) >= q.goal) sideReady(player, q);
            }
        }
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
            spawnInvitingEmul(player);
        }
    }

    /** Emul hops over to invite the marked player to Rabituza. */
    private static void spawnInvitingEmul(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        EmulEntity emul = ModEntities.EMUL.get().create(level);
        if (emul != null) {
            emul.moveTo(player.getX() + 2, player.getY(), player.getZ() + 2, player.getYRot() + 180, 0);
            level.addFreshEntity(emul);
            level.sendParticles(ParticleTypes.CHERRY_LEAVES, emul.getX(), emul.getY() + 0.5, emul.getZ(), 20, 0.4, 0.4, 0.4, 0);
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
                advance(p, QuestStage.LYCAGON_SHADOW);
                ModNetwork.announce(p, "announce.slfrontier.scenario_clear", "announce.slfrontier.tombguard", 0x8FD8FF);
                p.sendSystemMessage(Component.translatable("dialogue.slfrontier.setsuna.farewell").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
            }
        }
        dismissAllies(level, wezaemon);
    }

    public static void onRavenousSnakeSlain(ServerLevel level, Entity snake, @Nullable Entity killer) {
        level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("message.slfrontier.snake.slain",
                killer != null ? killer.getDisplayName() : Component.literal("?")).withStyle(ChatFormatting.GREEN), false);
        for (ServerPlayer p : participants(level, snake, killer, 48)) {
            if (ModAttachments.get(p).stage() == QuestStage.RAVENOUS_SNAKE) {
                Progression.grantExp(p, 250);
                advance(p, QuestStage.REACH_SECONDIL);
                ModNetwork.announce(p, "announce.slfrontier.area_boss", "entity.slfrontier.ravenous_snake", 0x8AE070);
            }
        }
    }

    public static void onMudDiggerSlain(ServerLevel level, Entity digger, @Nullable Entity killer) {
        for (ServerPlayer p : participants(level, digger, killer, 48)) {
            if (ModAttachments.get(p).stage() == QuestStage.MUD_DIGGER_ROAD) {
                Progression.grantExp(p, 600);
                advance(p, QuestStage.THIRDREMA);
                ModNetwork.announce(p, "announce.slfrontier.area_boss", "entity.slfrontier.mud_digger", 0xB89A5A);
            }
        }
    }

    public static void onWoodmageSlain(ServerLevel level, Entity woodmage, @Nullable Entity killer) {
        level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("message.slfrontier.woodmage.slain",
                killer != null ? killer.getDisplayName() : Component.literal("?")).withStyle(ChatFormatting.GOLD), false);
    }

    /** The last round of the Vorpal Colosseum has been won. */
    public static void onColosseumCleared(ServerPlayer player) {
        PlayerData data = ModAttachments.get(player);
        ModNetwork.announce(player, "announce.slfrontier.colosseum", "announce.slfrontier.colosseum.sub", 0xFFD34F);
        if (data.stage() != QuestStage.VORPAL_COLOSSEUM) return;
        Progression.grantExp(player, 700);
        give(player, new ItemStack(ModItems.REALLOCATION_TONIC.get()));
        give(player, new ItemStack(ModItems.REBIRTH_TEARJEWEL.get()));
        advance(player, QuestStage.MOONBLADES);
    }

    public static void onLycagonShadowSlain(ServerLevel level, Entity shadow, @Nullable Entity killer) {
        level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("message.slfrontier.shadow.slain",
                killer != null ? killer.getDisplayName() : Component.literal("?")).withStyle(ChatFormatting.LIGHT_PURPLE), false);
        for (ServerPlayer p : participants(level, shadow, killer, 64)) {
            PlayerData data = ModAttachments.get(p);
            if (data.stage() == QuestStage.LYCAGON_SHADOW) {
                data.setFlag("contusion", true);
                Progression.grantExp(p, 1800);
                advance(p, QuestStage.NIGHTSLAYER_FINAL);
                ModNetwork.announce(p, "announce.slfrontier.contusion", "announce.slfrontier.contusion.sub", 0xB070FF);
                p.sendSystemMessage(Component.translatable("message.slfrontier.guiding_light").withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }
    }

    // ---- Per-player story checks ------------------------------------------------------

    /** Runs once a second for players in the Frontier. */
    public static void tickPlayer(ServerPlayer player) {
        PlayerData data = ModAttachments.get(player);
        if (data.stage() == QuestStage.RAVENOUS_SNAKE) {
            ServerLevel level = player.serverLevel();
            BlockPos bridge = Landmarks.built(level, Landmarks.Site.SERPENT_BRIDGE);
            if (bridge != null && player.blockPosition().closerThan(bridge, 26)
                    && level.getEntitiesOfClass(RavenousSnakeEntity.class, new net.minecraft.world.phys.AABB(bridge).inflate(64)).isEmpty()) {
                spawnRavenousSnake(level, player, bridge);
            }
        }
    }

    /** The serpent rises out of the river under the bridge. */
    private static void spawnRavenousSnake(ServerLevel level, ServerPlayer player, BlockPos bridge) {
        RavenousSnakeEntity snake = ModEntities.RAVENOUS_SNAKE.get().create(level);
        if (snake == null) return;
        BlockPos arena = Landmarks.marker(level, Landmarks.Site.SERPENT_BRIDGE, "arena");
        BlockPos at = arena != null ? arena : bridge;
        snake.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        snake.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
        snake.setTarget(player);
        level.addFreshEntity(snake);
        level.sendParticles(ParticleTypes.SPLASH, snake.getX(), snake.getY() + 1, snake.getZ(), 120, 2, 1, 2, 0.2);
        level.playSound(null, at, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 1.5f, 0.6f);
        ModNetwork.announce(player, "announce.slfrontier.area_boss_appears", "entity.slfrontier.ravenous_snake", 0x8AE070);
    }

    /** Everyone near the boss, plus whoever landed the final blow (even from far away). */
    private static java.util.Set<ServerPlayer> participants(ServerLevel level, Entity boss, @Nullable Entity killer, double radius) {
        java.util.Set<ServerPlayer> players = new java.util.LinkedHashSet<>(
                level.getEntitiesOfClass(ServerPlayer.class, boss.getBoundingBox().inflate(radius)));
        if (killer instanceof ServerPlayer sp) players.add(sp);
        return players;
    }

    // ---- Side quests ----------------------------------------------------------------

    /** NPCs offer the side quests they give, and accept them back when they are done. */
    private static void addSideQuestOptions(ServerPlayer player, String npcId, Dialogue dialogue) {
        PlayerData data = ModAttachments.get(player);
        for (SideQuest q : SideQuest.values()) {
            if (!q.giver.equals(npcId)) continue;
            switch (data.sideState(q)) {
                case AVAILABLE -> dialogue.optionKey("dialogue.slfrontier.option.side_offer",
                        p -> followUp(p, npcId, Dialogue.of(npcId).line(q.descKey())
                                .optionKey("dialogue.slfrontier.option.side_accept", pp -> acceptSide(pp, q), "@" + q.titleKey())
                                .option("decline", pp -> {
                                })), "@" + q.titleKey());
                case ACTIVE -> {
                    if (sideComplete(player, data, q)) {
                        dialogue.optionKey("dialogue.slfrontier.option.side_turn_in", p -> turnInSide(p, q), "@" + q.titleKey());
                    }
                }
                default -> {
                }
            }
        }
    }

    private static void acceptSide(ServerPlayer player, SideQuest q) {
        PlayerData data = ModAttachments.get(player);
        if (data.sideState(q) != PlayerData.SideState.AVAILABLE) return;
        data.acceptSide(q);
        if (data.tracked().isEmpty() || SideQuest.byId(data.tracked()) == null) data.setTracked(q.id);
        updateSideProgress(player, data, q);
        player.sendSystemMessage(Component.translatable("message.slfrontier.side_accepted",
                Component.translatable(q.titleKey()).withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.translatable(q.objectiveKey()).withStyle(ChatFormatting.GRAY));
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
        ModNetwork.sync(player);
    }

    private static boolean sideComplete(ServerPlayer player, PlayerData data, SideQuest q) {
        updateSideProgress(player, data, q);
        return data.sideProgress(q) >= q.goal;
    }

    private static void turnInSide(ServerPlayer player, SideQuest q) {
        PlayerData data = ModAttachments.get(player);
        if (data.sideState(q) != PlayerData.SideState.ACTIVE || !sideComplete(player, data, q)) return;
        if (q.objective == SideQuest.Objective.COLLECT) {
            Item item = itemById(q.target);
            if (item == null || Progression.takeItem(player, item, q.goal) < q.goal) return;
        }
        data.completeSide(q);
        for (ItemStack reward : q.rewards()) give(player, reward);
        if (q.exp > 0) Progression.grantExp(player, q.exp);
        ModNetwork.announce(player, "announce.slfrontier.side_complete", q.titleKey(), 0x7FE7FF);
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.4f);
        ModNetwork.sync(player);
    }

    private static void sideReady(ServerPlayer player, SideQuest q) {
        player.sendSystemMessage(Component.translatable("message.slfrontier.side_ready",
                Component.translatable(q.titleKey()).withStyle(ChatFormatting.AQUA),
                Component.translatable("entity.slfrontier." + q.giver)).withStyle(ChatFormatting.GREEN));
    }

    /** Collect and level objectives are measured from the player's state. */
    private static void updateSideProgress(ServerPlayer player, PlayerData data, SideQuest q) {
        int before = data.sideProgress(q);
        switch (q.objective) {
            case COLLECT -> {
                Item item = itemById(q.target);
                data.setSideProgress(q, item == null ? 0 : Math.min(q.goal, Progression.countItem(player, item)));
            }
            case LEVEL -> data.setSideProgress(q, Math.min(q.goal, data.level()));
            default -> {
            }
        }
        if (before < q.goal && data.sideProgress(q) >= q.goal && q.objective != SideQuest.Objective.KILL) sideReady(player, q);
    }

    /** Runs once a second from the player tick. */
    public static void tickSideQuests(ServerPlayer player) {
        PlayerData data = ModAttachments.get(player);
        // Saves from before side quests existed: the Mud Digger bounty was a flag.
        if (data.flag("mud_bounty") && data.sideState(SideQuest.SOLO_KILLER) != PlayerData.SideState.DONE) {
            data.completeSide(SideQuest.SOLO_KILLER);
        }
        for (String id : List.copyOf(data.activeSideIds())) {
            SideQuest q = SideQuest.byId(id);
            if (q != null && q.objective != SideQuest.Objective.KILL) updateSideProgress(player, data, q);
        }
    }

    @Nullable
    private static Item itemById(String id) {
        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(id);
        if (rl == null) return null;
        Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
        return item == Items.AIR ? null : item;
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
            case RAVENOUS_SNAKE -> site(level, Landmarks.Site.SERPENT_BRIDGE, "target.slfrontier.serpent_bridge");
            case REACH_SECONDIL -> site(level, Landmarks.Site.SECONDIL, "target.slfrontier.secondil");
            case GRAY_ORE -> Progression.countItem(player, ModItems.GRAY_ORE_CHUNK.get()) >= QuestStage.GRAY_ORE.goal
                    ? site(level, Landmarks.Site.SECONDIL, "target.slfrontier.secondil")
                    : biome(level, here, Frontier.DIREMARSH_WASTE, "target.slfrontier.diremarsh");
            case NIGHTSLAYER_ENCOUNTER, NIGHTSLAYER_FINAL -> structure(level, here, Frontier.NIGHTSLAYER_DEN, "target.slfrontier.den");
            case INVITATION -> null;
            case MEET_VYSACHE -> towns.rabituza() == null ? null : new Target(towns.rabituza(), "target.slfrontier.rabituza");
            case VORPAL_COLOSSEUM -> {
                BlockPos arena = Landmarks.marker(level, Landmarks.Site.RABITUZA, "arena");
                yield arena != null ? new Target(arena, "target.slfrontier.colosseum")
                        : towns.rabituza() == null ? null : new Target(towns.rabituza(), "target.slfrontier.rabituza");
            }
            case MUD_DIGGER_ROAD -> structure(level, here, Frontier.MUD_DIGGER_LAIR, "target.slfrontier.mud_digger");
            case THIRDREMA -> site(level, Landmarks.Site.THIRDREMA, "target.slfrontier.thirdrema");
            case LYCAGON_SHADOW -> structure(level, here, Frontier.SHADOW_ALTAR, "target.slfrontier.shadow_altar");
            case MOONBLADES -> Progression.hasItem(player, ModItems.QUAD_BEETLE_SHELL.get()) && towns.rabituza() != null
                    ? new Target(towns.rabituza(), "target.slfrontier.rabituza")
                    : biome(level, here, Frontier.PRISMATIC_FOREST_GROTTO, "target.slfrontier.grotto");
            case FIND_SETSUNA, TOMBGUARD -> structure(level, here, Frontier.WEZAEMON_TOMB, "target.slfrontier.tomb");
            case COMPLETE -> null;
        };
    }

    private static Target site(ServerLevel level, Landmarks.Site site, String name) {
        return new Target(Landmarks.where(level, site), name);
    }

    /** Structure and biome searches are expensive; reuse results while the player stays nearby. */
    private record Located(BlockPos origin, @Nullable BlockPos found, long time) {
    }

    private static final Map<String, Located> LOCATE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    @Nullable
    private static BlockPos cachedLocate(ServerLevel level, BlockPos from, String key, java.util.function.Supplier<BlockPos> search) {
        Located hit = LOCATE_CACHE.get(key);
        long now = level.getGameTime();
        if (hit != null && hit.origin().distSqr(from) < 256 * 256 && now - hit.time() < 20 * 120) return hit.found();
        BlockPos found = search.get();
        LOCATE_CACHE.put(key, new Located(from, found, now));
        return found;
    }

    @Nullable
    private static Target structure(ServerLevel level, BlockPos from, TagKey<Structure> tag, String name) {
        BlockPos pos = cachedLocate(level, from, "s:" + tag.location() + "@" + (from.getX() >> 8) + "," + (from.getZ() >> 8),
                () -> level.findNearestMapStructure(tag, from, 100, false));
        return pos == null ? null : new Target(pos, name);
    }

    @Nullable
    private static Target biome(ServerLevel level, BlockPos from, ResourceKey<Biome> biome, String name) {
        BlockPos pos = cachedLocate(level, from, "b:" + biome.location() + "@" + (from.getX() >> 8) + "," + (from.getZ() >> 8), () -> {
            Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(h -> h.is(biome), from, 4000, 32, 64);
            return found == null ? null : found.getFirst();
        });
        return pos == null ? null : new Target(pos, name);
    }

    public static void forget(ServerPlayer player) {
        PENDING.remove(player.getUUID());
    }
}
