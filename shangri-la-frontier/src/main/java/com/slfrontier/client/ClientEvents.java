package com.slfrontier.client;

import com.mojang.math.Axis;
import com.slfrontier.SLFrontier;
import com.slfrontier.SlfConfig;
import com.slfrontier.client.anim.PlayerAnim;
import com.slfrontier.client.fx.CameraShake;
import com.slfrontier.client.fx.DamageNumbers;
import com.slfrontier.client.fx.FxRenderer;
import com.slfrontier.client.overlay.EnemyHealthBars;
import com.slfrontier.client.overlay.QuestPointer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import com.slfrontier.client.map.MapStore;
import com.slfrontier.client.screen.FrontierScreen;
import com.slfrontier.client.screen.MapScreen;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ToastAddEvent;
import com.slfrontier.client.screen.DialogueScreen;
import net.minecraft.client.gui.components.toasts.RecipeToast;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import com.slfrontier.data.PlayerData;
import com.slfrontier.network.ModNetwork;
import com.slfrontier.registry.ModAttachments;
import com.slfrontier.skill.Skill;
import com.slfrontier.world.Frontier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

@EventBusSubscriber(modid = SLFrontier.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private static boolean jumpWasDown;
    private static boolean airJumpSent;

    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientAnims.tick();
        com.slfrontier.client.fx.Impact.tick();
        FrontierHud.tick();
        FxRenderer.tick();
        DamageNumbers.tick();
        CameraShake.tick();
        if (ClientTestDriver.ENABLED) ClientTestDriver.tick();
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        MapStore.tick();
        if (!mc.isPaused()) ModAttachments.get(player).tickCooldowns();
        while (KeyBindings.MENU.consumeClick()) {
            if (mc.gui.screen() == null) mc.gui.setScreen(new FrontierScreen());
        }
        while (KeyBindings.MAP.consumeClick()) {
            if (mc.gui.screen() == null) mc.gui.setScreen(new MapScreen());
        }
        while (KeyBindings.MINIMAP.consumeClick()) {
            SlfConfig.SHOW_MINIMAP.set(!SlfConfig.SHOW_MINIMAP.get());
            com.slfrontier.client.overlay.HudNotice.minimapToggled(SlfConfig.SHOW_MINIMAP.get());
        }
        for (int i = 0; i < KeyBindings.SKILLS.length; i++) {
            while (KeyBindings.SKILLS[i].consumeClick()) useSkill(mc, player, i);
        }

        if (!Frontier.isIn(player)) return;
        PlayerData data = ModAttachments.get(player);

        // Out of stamina: no sprinting.
        if (data.stamina() < 1f && player.isSprinting()) {
            player.setSprinting(false);
        }

        // Double jump: press jump again while airborne.
        boolean jumpDown = mc.options.keyJump.isDown();
        if (player.onGround()) airJumpSent = false;
        if (jumpDown && !jumpWasDown && !player.onGround() && !airJumpSent && data.hasSkill(Skill.DOUBLE_JUMP)
                && !player.getAbilities().flying && !player.isInWater() && !player.isFallFlying()
                && data.stamina() >= Skill.DOUBLE_JUMP.staminaCost && mc.gui.screen() == null) {
            airJumpSent = true;
            ClientPacketDistributor.sendToServer(new ModNetwork.DoubleJump());
        }
        jumpWasDown = jumpDown;
    }

    /**
     * A skill key press. Skills only exist in the Frontier, so outside it the keys do nothing. Inside, a
     * press the client already knows can't work (empty slot, cooling down, not enough stamina) shakes the
     * slot at once; the server still decides, and explains cooldowns and stamina itself.
     */
    private static void useSkill(Minecraft mc, LocalPlayer player, int slot) {
        if (!Frontier.isIn(player)) return;
        PlayerData data = ModAttachments.get(player);
        Skill skill = data.equipped(slot);
        if (skill == null || skill.passive) {
            FrontierHud.skillDenied(slot);
            if (skill == null) {
                mc.gui.hud.setOverlayMessage(KeyBindings.text("hud.slfrontier.empty_slot", slot + 1), false);
            }
            return;
        }
        if (data.cooldown(skill) > 0 || data.stamina() < skill.staminaCost) FrontierHud.skillDenied(slot);
        // Move vector: x is the strafe (left) impulse, y the forward impulse.
        var move = player.input.getMoveVector();
        ClientPacketDistributor.sendToServer(new ModNetwork.UseSkill(slot, (byte) Math.signum(move.x), (byte) Math.signum(move.y)));
    }

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Post event) {
        if (ClientTestDriver.ENABLED) ClientTestDriver.onFrame();
    }

    /** World overlays snapshot what they draw while the level's render state is extracted... */
    @SubscribeEvent
    public static void onExtractLevel(ExtractLevelRenderStateEvent event) {
        float pt = event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        var state = event.getRenderState();
        FxRenderer.extract(state, event.getCamera(), pt);
        DamageNumbers.extract(state, event.getCamera(), pt);
        QuestPointer.extract(state, event.getCamera(), pt);
        EnemyHealthBars.extract(state, event.getCamera(), pt);
    }

    /** ...and submit it with the level's geometry, to draw after translucent terrain and particles. */
    @SubscribeEvent
    public static void onSubmitGeometry(SubmitCustomGeometryEvent event) {
        var state = event.getLevelRenderState();
        FxRenderer.submit(state, event.getSubmitNodeCollector(), event.getPoseStack());
        DamageNumbers.submit(state, event.getSubmitNodeCollector(), event.getPoseStack());
        QuestPointer.submit(state, event.getSubmitNodeCollector(), event.getPoseStack());
        EnemyHealthBars.submit(state, event.getSubmitNodeCollector(), event.getPoseStack());
    }

    // The FOV punch of heavy hits lives in CameraMixin: ComputeFov also fires for the hand's FOV now.

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float[] shake = CameraShake.offsets((float) event.getPartialTick());
        if (shake != null) {
            event.setYaw(event.getYaw() + shake[0]);
            event.setPitch(event.getPitch() + shake[1]);
            event.setRoll(event.getRoll() + shake[2]);
        }
    }

    @SubscribeEvent
    public static void onGuiStart(RenderGuiEvent.Pre event) {
        FrontierHud.beginFrame();
    }

    @SubscribeEvent
    public static void onGuiLayer(RenderGuiLayerEvent.Pre event) {
        var name = event.getName();
        // Dialogue is a cutscene: only chat and subtitles stay up.
        if (Minecraft.getInstance().gui.screen() instanceof DialogueScreen
                && !name.equals(VanillaGuiLayers.CHAT) && !name.equals(VanillaGuiLayers.SUBTITLE_OVERLAY)) {
            event.setCanceled(true);
            return;
        }
        // Effects are chips under the avatar frame in the Frontier; vanilla's icons would sit on the minimap.
        if (name.equals(VanillaGuiLayers.EFFECTS) && FrontierHud.drawsEffects()) {
            event.setCanceled(true);
            return;
        }
        boolean bar = name.equals(VanillaGuiLayers.CONTEXTUAL_INFO_BAR) || name.equals(VanillaGuiLayers.CONTEXTUAL_INFO_BAR_BACKGROUND);
        if ((name.equals(VanillaGuiLayers.PLAYER_HEALTH) || name.equals(VanillaGuiLayers.ARMOR_LEVEL)
                || name.equals(VanillaGuiLayers.EXPERIENCE_LEVEL) || bar && showsExperienceBar())
                && FrontierHud.hideVanillaBars()) {
            event.setCanceled(true);
        }
    }

    /**
     * The bar above the hotbar is shared by experience, the locator and a mount's jump charge; only the
     * experience bar gives way to the Frontier HUD. Mirrors vanilla's choice in {@code Hud#nextContextualInfoState}.
     */
    private static boolean showsExperienceBar() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null || !mc.gameMode.hasExperience()) return false;
        var mount = player.jumpableVehicle();
        if (!player.connection.getWaypointManager().hasWaypoints()) return mount == null;
        boolean jumping = mount != null && (player.getJumpRidingScale() > 0 || mount.getJumpCooldown() > 0);
        return !jumping && player.experienceDisplayStartTick + 100 > player.tickCount;
    }

    @SubscribeEvent
    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        if (FrontierHud.renderBossBar(event.getGuiGraphics(), event.getBossEvent(), event.getX(), event.getY())) {
            event.setIncrement(event.getIncrement() + 6);
            event.setCanceled(true);
        }
        FrontierHud.bossBarDrawn(event.getY(), event.getIncrement());
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (Minecraft.getInstance().gui.screen() instanceof DialogueScreen) event.setCanceled(true);
    }

    /** "New recipes unlocked" points at the vanilla recipe book, which the Frontier doesn't use; it also covers the minimap. */
    @SubscribeEvent
    public static void onToast(ToastAddEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (event.getToast() instanceof RecipeToast && player != null && Frontier.isIn(player)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onJoin(ClientPlayerNetworkEvent.LoggingIn event) {
        MapStore.onJoin();
    }

    @SubscribeEvent
    public static void onLeave(ClientPlayerNetworkEvent.LoggingOut event) {
        MapStore.onLeave();
        ClientState.reset();
        FrontierHud.clear();
        FxRenderer.clear();
        DamageNumbers.clear();
        com.slfrontier.client.fx.Impact.clear();
    }

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre<?> event) {
        AvatarRenderState state = event.getRenderState();
        PlayerAnim.Root root = state.getRenderData(ClientAnims.ROOT);
        if (root == null) return;
        var pose = event.getPoseStack();
        pose.pushPose();
        // Work in the body's frame so "lean forward" means forward for wherever the player faces.
        float bodyYaw = state.bodyRot;
        pose.translate(0, 0.9 + root.dy(), 0);
        pose.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
        pose.translate(0, 0, root.dz());
        pose.mulPose(Axis.YP.rotationDegrees(-root.yaw()));
        pose.mulPose(Axis.XP.rotationDegrees(root.pitch()));
        pose.mulPose(Axis.ZP.rotationDegrees(root.roll()));
        pose.mulPose(Axis.YP.rotationDegrees(bodyYaw));
        pose.translate(0, -0.9, 0);
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post<?> event) {
        if (event.getRenderState().getRenderData(ClientAnims.ROOT) != null) {
            event.getPoseStack().popPose();
        }
    }
}
