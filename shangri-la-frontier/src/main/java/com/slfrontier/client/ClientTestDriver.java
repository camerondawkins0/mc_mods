package com.slfrontier.client;

import com.slfrontier.SLFrontier;
import com.slfrontier.client.screen.DialogueScreen;
import com.slfrontier.client.screen.FrontierScreen;
import com.slfrontier.entity.FrontierBoss;
import com.slfrontier.network.ModNetwork;
import com.slfrontier.progression.Progression;
import com.slfrontier.registry.ModAttachments;
import com.slfrontier.registry.ModEntities;
import com.slfrontier.registry.ModItems;
import com.slfrontier.skill.PlayerAnims;
import com.slfrontier.skill.Skill;
import com.slfrontier.world.FrontierTravel;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Automated client walkthrough used only by CI (enabled with -Dslfrontier.clientTest=true).
 * It logs the player into the Frontier, lines up every creature, opens the menus and takes
 * screenshots, then quits. Never active in normal play.
 */
public final class ClientTestDriver {
    public static final boolean ENABLED = Boolean.getBoolean("slfrontier.clientTest");
    private static int ticks = -1;
    private static final List<Mob> spawned = new ArrayList<>();

    private ClientTestDriver() {
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        ticks++;
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) return;
        switch (ticks) {
            case 60 -> server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
                FrontierTravel.login(sp);
            });
            case 200 -> server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
                ServerLevel level = sp.serverLevel();
                level.setDayTime(6000);
                level.setWeatherParameters(6000, 0, false, false);
                Progression.grantExp(sp, 3000);
                var data = ModAttachments.get(sp);
                for (Skill s : Skill.values()) data.unlock(s);
                data.setCursed(true);
                sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MOONBLADE_WAXING.get()));
                sp.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.MOONBLADE_WANING.get()));
                sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(ModItems.BIRD_MASK.get()));
                sp.getAbilities().invulnerable = true;
                sp.getAbilities().mayfly = true;
                sp.getAbilities().flying = true;
                sp.onUpdateAbilities();
                sp.teleportTo(level, sp.getX(), sp.getY() + 1, sp.getZ(), 0f, 15f);
                lineUp(level, sp, List.of(ModEntities.GOBLIN, ModEntities.VORPAL_BUNNY, ModEntities.EMPIRE_BEE,
                        ModEntities.SHADOW_WOLF, ModEntities.EMUL, ModEntities.BILAC, ModEntities.GUIDE,
                        ModEntities.BLACKSMITH, ModEntities.SETSUNA), 1.6, 6);
            });
            case 320 -> shot(mc, "01_small_creatures");
            case 330 -> server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
                clear();
                lineUp(sp.serverLevel(), sp, List.of(ModEntities.QUAD_BEETLE, ModEntities.MUD_DIGGER, ModEntities.WEZAEMON,
                        ModEntities.VYSACHE, ModEntities.EMPIRE_BEE_QUEEN), 4.0, 12);
            });
            case 440 -> shot(mc, "02_big_creatures");
            case 450 -> server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
                clear();
                lineUp(sp.serverLevel(), sp, List.of(ModEntities.LYCAGON), 0, 12);
            });
            case 560 -> shot(mc, "03_lycagon");
            case 570 -> server.execute(() -> {
                for (Mob m : spawned) {
                    if (m instanceof FrontierBoss boss) boss.playAnim(5);
                }
            });
            case 585 -> shot(mc, "04_lycagon_attack");
            case 600 -> {
                server.execute(ClientTestDriver::clear);
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                ClientAnims.start(mc.player.getId(), PlayerAnims.PARRY, 200);
            }
            case 640 -> shot(mc, "05_player_parry_pose");
            case 650 -> ClientAnims.start(mc.player.getId(), PlayerAnims.CRESCENT, 200);
            case 700 -> shot(mc, "06_player_crescent_pose");
            case 710 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.setScreen(new FrontierScreen());
            }
            case 740 -> shot(mc, "07_status_menu");
            case 750 -> mc.setScreen(new DialogueScreen(new ModNetwork.OpenDialogue(mc.player.getId(), "entity.slfrontier.emul",
                    List.of("dialogue.slfrontier.emul.invite.1"), List.of("dialogue.slfrontier.option.accept_invitation",
                    "dialogue.slfrontier.option.decline"))));
            case 800 -> shot(mc, "08_dialogue");
            case 810 -> {
                mc.setScreen(null);
                mc.player.setXRot(10f);
            }
            case 850 -> shot(mc, "09_fastera");
            case 870 -> {
                SLFrontier.LOGGER.info("Client test finished");
                mc.stop();
            }
            default -> {
            }
        }
    }

    private static void lineUp(ServerLevel level, ServerPlayer sp, List<? extends Supplier<? extends EntityType<? extends Mob>>> types,
                               double spacing, double distance) {
        double width = spacing * (types.size() - 1);
        for (int i = 0; i < types.size(); i++) {
            Mob mob = types.get(i).get().create(level);
            if (mob == null) continue;
            double x = sp.getX() - width / 2 + i * spacing;
            double z = sp.getZ() + distance;
            mob.moveTo(x, sp.getY() - 1, z, 180f, 0f);
            mob.setYHeadRot(180f);
            mob.setYBodyRot(180f);
            mob.setNoAi(true);
            mob.setPersistenceRequired();
            level.addFreshEntity(mob);
            spawned.add(mob);
        }
    }

    private static void clear() {
        for (Mob m : spawned) m.discard();
        spawned.clear();
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(),
                msg -> SLFrontier.LOGGER.info("Screenshot {}: {}", name, msg.getString()));
    }
}
