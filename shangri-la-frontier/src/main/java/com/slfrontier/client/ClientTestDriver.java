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
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Automated client walkthrough used only by CI (enabled with -Dslfrontier.clientTest=true).
 * It logs the player into the Frontier, then plays a list of scenes — lining up creatures,
 * showing weapons, opening menus — taking screenshots, and quits. Never active in normal play.
 *
 * <p>{@code -Dslfrontier.clientTest.only=weapons,menus} runs a subset of scenes.
 */
public final class ClientTestDriver {
    public static final boolean ENABLED = Boolean.getBoolean("slfrontier.clientTest");
    private static final Set<String> ONLY = new HashSet<>(Arrays.asList(
            System.getProperty("slfrontier.clientTest.only", "").split(",")));

    /** A timeline step; a step with a condition holds the timeline until it is true (or times out). */
    private record Step(int at, Consumer<Minecraft> action, java.util.function.Predicate<Minecraft> until, int maxWait) {
    }

    private static final List<Step> STEPS = new ArrayList<>();
    private static int cursor;
    private static int ticks = -1;
    private static int clientTicks;
    private static int nextStep;
    private static int waited;
    private static final List<Entity> spawned = new ArrayList<>();

    private ClientTestDriver() {
    }

    // ---- Timeline building -----------------------------------------------------------

    private static void at(int delay, Consumer<Minecraft> action) {
        cursor += delay;
        STEPS.add(new Step(cursor, action, null, 0));
    }

    /** Pauses the timeline until {@code until} holds (the integrated server can lag behind the client). */
    private static void waitFor(java.util.function.Predicate<Minecraft> until, int maxWait) {
        STEPS.add(new Step(cursor, mc -> {
        }, until, maxWait));
    }

    private static void server(int delay, Consumer<ServerPlayer> action) {
        at(delay, mc -> {
            MinecraftServer server = mc.getSingleplayerServer();
            if (server != null) server.execute(() -> action.accept(server.getPlayerList().getPlayers().get(0)));
        });
    }

    private static void shot(int delay, String name) {
        // Software rendering is slow: grab after the next freshly rendered frame, not the stale one.
        at(delay, mc -> {
            pendingShot = name;
            shotFrame = frames;
        });
    }

    private static String pendingShot;
    private static long frames;
    private static long shotFrame;

    /** Called after every rendered frame. */
    public static void onFrame() {
        frames++;
        if (pendingShot != null && frames > shotFrame + 1) {
            Minecraft mc = Minecraft.getInstance();
            String name = pendingShot;
            pendingShot = null;
            SLFrontier.LOGGER.info("Client test: shot {} anim={} fx={}", name,
                    mc.player == null ? -1 : ClientAnims.playing(mc.player.getId()), com.slfrontier.client.fx.FxRenderer.count());
            Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(),
                    msg -> SLFrontier.LOGGER.info("Screenshot {}: {}", name, msg.getString()));
        }
    }

    private static boolean wants(String scene) {
        return ONLY.size() <= 1 && ONLY.contains("") || ONLY.contains(scene);
    }

    static {
        server(60, FrontierTravel::login);
        server(140, sp -> {
            ServerLevel level = sp.serverLevel();
            level.setDayTime(6000);
            level.setWeatherParameters(6000, 0, false, false);
            Progression.grantExp(sp, 3000);
            var data = ModAttachments.get(sp);
            for (Skill s : Skill.values()) data.unlock(s);
            data.setCursed(true);
            sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MOONBLADE_WAXING.get()));
            sp.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.MOONBLADE_WANING.get()));
            sp.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.BIRD_MASK.get()));
            sp.getAbilities().invulnerable = true;
            sp.getAbilities().mayfly = true;
            sp.getAbilities().flying = true;
            sp.onUpdateAbilities();
            sp.teleportTo(level, sp.getX(), sp.getY() + 1, sp.getZ(), 0f, 15f);
        });
        cursor += 40;
        if (wants("creatures")) creatures();
        if (wants("weapons")) weapons();
        if (wants("poses")) poses();
        if (wants("skills")) skills();
        if (wants("menus")) menus();
        at(20, mc -> {
            SLFrontier.LOGGER.info("Client test finished");
            mc.stop();
        });
    }

    // ---- Scenes ----------------------------------------------------------------------

    private static void creatures() {
        server(0, sp -> lineUp(sp, List.of(ModEntities.GOBLIN, ModEntities.VORPAL_BUNNY, ModEntities.EMPIRE_BEE,
                ModEntities.SHADOW_WOLF, ModEntities.EMUL, ModEntities.BILAC, ModEntities.GUIDE,
                ModEntities.BLACKSMITH, ModEntities.SETSUNA), 1.6, 6));
        shot(120, "01_small_creatures");
        server(10, sp -> {
            clear();
            lineUp(sp, List.of(ModEntities.QUAD_BEETLE, ModEntities.MUD_DIGGER, ModEntities.WEZAEMON,
                    ModEntities.VYSACHE, ModEntities.EMPIRE_BEE_QUEEN), 4.0, 12);
        });
        shot(110, "02_big_creatures");
        server(10, sp -> {
            clear();
            lineUp(sp, List.of(ModEntities.LYCAGON), 0, 12);
        });
        shot(110, "03_lycagon");
        server(10, sp -> {
            for (Entity m : spawned) {
                if (m instanceof FrontierBoss boss) boss.playAnim(5);
            }
        });
        shot(15, "04_lycagon_attack");
        server(10, sp -> clear());
    }

    private static final List<Supplier<Item>> WEAPONS = List.of(
            ModItems.MERCENARY_TWIN_BLADES::get, ModItems.MARSH_DAGGER::get, ModItems.DUCUSRAM::get,
            ModItems.VORPAL_CHOPPERS::get, ModItems.EMPIRE_BEE_TWINBLADES::get, ModItems.MOONBLADE_WAXING::get,
            ModItems.MOONBLADE_WANING::get, ModItems.TWIN_MOON::get, ModItems.TOMBGUARD_KATANA::get);

    private static void weapons() {
        // Armor stands show the third-person transforms, dropped items the ground ones.
        server(0, sp -> {
            ServerLevel level = sp.serverLevel();
            double width = 1.3 * (WEAPONS.size() - 1);
            for (int i = 0; i < WEAPONS.size(); i++) {
                ArmorStand stand = EntityType.ARMOR_STAND.create(level);
                if (stand == null) continue;
                stand.setShowArms(true);
                stand.setNoBasePlate(true);
                stand.setNoGravity(true);
                stand.moveTo(sp.getX() - width / 2 + i * 1.3, sp.getY() - 1, sp.getZ() + 4.5, 180f, 0f);
                stand.setYBodyRot(180f);
                stand.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(WEAPONS.get(i).get()));
                level.addFreshEntity(stand);
                spawned.add(stand);
                ItemEntity drop = new ItemEntity(level, sp.getX() - width / 2 + i * 1.3, sp.getY() - 0.6, sp.getZ() + 2.6,
                        new ItemStack(WEAPONS.get(i).get()), 0, 0, 0);
                drop.setNeverPickUp();
                drop.setNoGravity(true);
                drop.setUnlimitedLifetime();
                level.addFreshEntity(drop);
                spawned.add(drop);
            }
            sp.teleportTo(level, sp.getX(), sp.getY(), sp.getZ(), 0f, 20f);
            for (int i = 0; i < WEAPONS.size(); i++) sp.getInventory().setItem(i, new ItemStack(WEAPONS.get(i).get()));
        });
        shot(100, "10_weapons_held");
        at(5, mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        shot(30, "11_weapons_third_person");
        at(5, mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        server(5, sp -> {
            sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.TOMBGUARD_KATANA.get()));
            sp.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            sp.teleportTo(sp.serverLevel(), sp.getX(), sp.getY(), sp.getZ(), 0f, 0f);
        });
        shot(30, "12_first_person_katana");
        server(5, sp -> sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.TWIN_MOON.get())));
        shot(30, "13_first_person_twin_moon");
        server(5, sp -> {
            sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MOONBLADE_WAXING.get()));
            sp.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.MOONBLADE_WANING.get()));
        });
        shot(30, "14_first_person_moonblades");
        at(5, mc -> mc.setScreen(new InventoryScreen(mc.player)));
        shot(30, "15_inventory_icons");
        at(5, mc -> mc.setScreen(null));
        server(5, sp -> clear());
    }

    private static void poses() {
        at(0, mc -> {
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            ClientAnims.start(mc.player.getId(), PlayerAnims.PARRY, 200);
        });
        shot(40, "05_player_parry_pose");
        at(10, mc -> ClientAnims.start(mc.player.getId(), PlayerAnims.CRESCENT, 200));
        shot(50, "06_player_crescent_pose");
        at(10, mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
    }

    private static void useSkill(ServerPlayer sp, Skill skill) {
        var data = ModAttachments.get(sp);
        data.setCooldown(skill, 0);
        data.setStamina(data.maxStamina());
        boolean used = com.slfrontier.skill.SkillExecutor.use(sp, skill);
        SLFrontier.LOGGER.info("Client test: skill {} used={} main={} cd={}", skill.id, used, sp.getMainHandItem(), data.cooldown(skill));
    }

    private static void skills() {
        server(0, sp -> {
            // Slow the game down so the (slow, software-rendered) client gets frames mid-animation.
            sp.getServer().tickRateManager().setTickRate(4f);
            clear();
            ServerLevel level = sp.serverLevel();
            sp.getAbilities().flying = false;
            sp.onUpdateAbilities();
            sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MOONBLADE_WAXING.get()));
            sp.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.MOONBLADE_WANING.get()));
            sp.teleportTo(level, sp.getX(), sp.getY() - 1, sp.getZ(), 0f, 10f);
            lineUp(sp, List.of(ModEntities.GOBLIN, ModEntities.GOBLIN, ModEntities.GOBLIN), 2.2, 4.5);
        });
        at(20, mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        String[] names = {"20_skill_drill_piercer", "21_skill_cyclone_slash", "22_skill_moon_reflection",
                "23_skill_perfect_parry", "24_skill_sky_clear", "25_skill_slide_step"};
        Skill[] list = {Skill.GLOWING_PIERCE, Skill.CYCLONE_SLASH, Skill.MOON_REFLECTION, Skill.PERFECT_PARRY, Skill.SKY_CLEAR,
                Skill.SLIDE_STEP};
        int[] delays = {4, 4, 4, 3, 9, 2};
        int[] anims = {PlayerAnims.PIERCE, PlayerAnims.SPIN, PlayerAnims.CRESCENT, PlayerAnims.PARRY, PlayerAnims.SKY_CLEAR,
                PlayerAnims.DASH};
        for (int i = 0; i < list.length; i++) {
            Skill skill = list[i];
            server(25, sp -> {
                sp.teleportTo(sp.serverLevel(), sp.getX(), sp.getY(), sp.getZ(), 0f, 10f);
                useSkill(sp, skill);
            });
            int anim = anims[i];
            waitFor(mc -> ClientAnims.playing(mc.player.getId()) == anim, 200);
            shot(delays[i], names[i]);
        }
        server(25, sp -> {
            sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.TWIN_MOON.get()));
            sp.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            useSkill(sp, Skill.CRESCENT_VORPAL);
        });
        waitFor(mc -> ClientAnims.playing(mc.player.getId()) == PlayerAnims.LEAP, 200);
        shot(5, "26_skill_crescent_vorpal_leap");
        waitFor(mc -> ClientAnims.playing(mc.player.getId()) == PlayerAnims.SLAM, 200);
        shot(4, "27_skill_crescent_vorpal_slam");
        at(25, mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        server(5, sp -> {
            sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MOONBLADE_WAXING.get()));
            sp.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.MOONBLADE_WANING.get()));
            useSkill(sp, Skill.MOON_REFLECTION);
        });
        waitFor(mc -> ClientAnims.playing(mc.player.getId()) == PlayerAnims.CRESCENT, 200);
        shot(4, "28_first_person_moon_reflection");
        server(20, sp -> useSkill(sp, Skill.GLOWING_PIERCE));
        waitFor(mc -> ClientAnims.playing(mc.player.getId()) == PlayerAnims.PIERCE, 200);
        shot(5, "29_first_person_pierce");
        server(20, sp -> {
            clear();
            sp.getServer().tickRateManager().setTickRate(20f);
        });
    }

    private static void menus() {
        // A mid-story avatar: a side quest in progress, a waypoint attuned, a goblin to target.
        server(0, sp -> {
            var data = ModAttachments.get(sp);
            data.setStage(com.slfrontier.quest.QuestStage.GRAY_ORE);
            data.acceptSide(com.slfrontier.quest.SideQuest.GOBLIN_CULL);
            data.setSideProgress(com.slfrontier.quest.SideQuest.GOBLIN_CULL, 7);
            data.setTracked(com.slfrontier.quest.SideQuest.GOBLIN_CULL.id);
            data.setUses(Skill.TAP_STEP, 23);
            ServerLevel level = sp.serverLevel();
            net.minecraft.core.BlockPos crystal = sp.blockPosition().offset(-3, -1, 3);
            level.setBlock(crystal, com.slfrontier.registry.ModBlocks.WAYPOINT_CRYSTAL.get().defaultBlockState(), 3);
            com.slfrontier.block.WaypointBlock.attune(level, crystal, sp);
            sp.getAbilities().flying = false;
            sp.onUpdateAbilities();
            sp.teleportTo(level, sp.getX(), sp.getY() - 1, sp.getZ(), 0f, 5f);
            lineUp(sp, List.of(ModEntities.GOBLIN), 0, 3.5);
            com.slfrontier.world.MapSync.invalidate(sp);
        });
        shot(120, "16_hud");
        server(5, sp -> clear());
        at(5, mc -> mc.setScreen(new FrontierScreen(FrontierScreen.Tab.STATUS)));
        shot(30, "07_status_menu");
        at(5, mc -> mc.setScreen(new FrontierScreen(FrontierScreen.Tab.SKILLS)));
        shot(30, "17_skills_menu");
        at(5, mc -> mc.setScreen(new FrontierScreen(FrontierScreen.Tab.QUESTS)));
        shot(30, "18_quest_journal");
        at(5, mc -> mc.setScreen(new com.slfrontier.client.screen.MapScreen()));
        shot(40, "19_map");
        at(5, mc -> mc.setScreen(null));
        server(5, sp -> lineUp(sp, List.of(ModEntities.EMUL), 0, 2.5));
        at(30, mc -> {
            int id = spawned.isEmpty() ? mc.player.getId() : spawned.get(0).getId();
            mc.setScreen(new DialogueScreen(new ModNetwork.OpenDialogue(id, "entity.slfrontier.emul",
                    List.of("dialogue.slfrontier.emul.invite.5"), List.of("dialogue.slfrontier.option.accept_invitation",
                    "dialogue.slfrontier.option.side_offer|@sidequest.slfrontier.lily_offering.title",
                    "dialogue.slfrontier.option.decline"))));
        });
        shot(50, "08_dialogue");
        at(10, mc -> {
            mc.setScreen(null);
            mc.player.setXRot(10f);
        });
        server(0, sp -> clear());
        shot(40, "09_fastera");
    }

    // ---- Driver ----------------------------------------------------------------------

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        clientTicks++;
        if (clientTicks % 200 == 0) {
            SLFrontier.LOGGER.info("Client test heartbeat: tick {}, screen {}, in world {}", clientTicks,
                    mc.screen == null ? "none" : mc.screen.getClass().getName(), mc.player != null);
        }
        // Vanilla warns once about worlds with datapack dimensions; accept it.
        if (mc.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen screen && clientTicks % 20 == 0) {
            String skip = net.minecraft.network.chat.Component.translatable("selectWorld.backupJoinSkipButton").getString();
            for (var child : screen.children()) {
                if (child instanceof net.minecraft.client.gui.components.Button button && button.getMessage().getString().equals(skip)) {
                    SLFrontier.LOGGER.info("Client test: accepting experimental-settings warning");
                    button.onPress();
                    break;
                }
            }
        }
        if (mc.player == null && clientTicks == 4000) {
            Screenshot.grab(mc.gameDirectory, "00_never_joined_world.png", mc.getMainRenderTarget(), msg -> {
            });
        }
        if (mc.player == null && clientTicks == 4040) {
            SLFrontier.LOGGER.error("Client test never joined the world");
            mc.stop();
        }
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
        ticks++;
        while (nextStep < STEPS.size() && STEPS.get(nextStep).at() <= ticks) {
            Step step = STEPS.get(nextStep);
            if (step.until() != null && !step.until().test(mc) && waited < step.maxWait()) {
                // Hold the timeline: everything after this step slides back by a tick.
                waited++;
                ticks--;
                break;
            }
            waited = 0;
            try {
                step.action().accept(mc);
            } catch (Throwable t) {
                SLFrontier.LOGGER.error("Client test step {} failed", nextStep, t);
            }
            nextStep++;
        }
    }

    private static void lineUp(ServerPlayer sp, List<? extends Supplier<? extends EntityType<? extends Mob>>> types,
                               double spacing, double distance) {
        ServerLevel level = sp.serverLevel();
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
        for (Entity m : spawned) m.discard();
        spawned.clear();
    }
}
