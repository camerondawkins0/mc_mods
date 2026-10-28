package com.slfrontier.client;

import com.mojang.math.Axis;
import com.slfrontier.SLFrontier;
import com.slfrontier.SlfConfig;
import com.slfrontier.client.map.MapStore;
import com.slfrontier.client.screen.FrontierScreen;
import com.slfrontier.client.screen.MapScreen;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
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
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = SLFrontier.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private static boolean jumpWasDown;
    private static boolean airJumpSent;

    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientAnims.tick();
        FrontierHud.tick();
        if (ClientTestDriver.ENABLED) ClientTestDriver.tick();
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        MapStore.tick();
        while (KeyBindings.MENU.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new FrontierScreen());
        }
        while (KeyBindings.MAP.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new MapScreen());
        }
        while (KeyBindings.MINIMAP.consumeClick()) {
            SlfConfig.SHOW_MINIMAP.set(!SlfConfig.SHOW_MINIMAP.get());
        }
        for (int i = 0; i < KeyBindings.SKILLS.length; i++) {
            while (KeyBindings.SKILLS[i].consumeClick()) {
                PacketDistributor.sendToServer(new ModNetwork.UseSkill(i));
            }
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
                && data.stamina() >= Skill.DOUBLE_JUMP.staminaCost && mc.screen == null) {
            airJumpSent = true;
            PacketDistributor.sendToServer(new ModNetwork.DoubleJump());
        }
        jumpWasDown = jumpDown;
    }

    @SubscribeEvent
    public static void onGuiLayer(RenderGuiLayerEvent.Pre event) {
        var name = event.getName();
        if ((name.equals(VanillaGuiLayers.PLAYER_HEALTH) || name.equals(VanillaGuiLayers.ARMOR_LEVEL)
                || name.equals(VanillaGuiLayers.EXPERIENCE_BAR) || name.equals(VanillaGuiLayers.EXPERIENCE_LEVEL))
                && FrontierHud.hideVanillaBars()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        if (FrontierHud.renderBossBar(event.getGuiGraphics(), event.getBossEvent(), event.getX(), event.getY())) {
            event.setIncrement(event.getIncrement() + 6);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onJoin(ClientPlayerNetworkEvent.LoggingIn event) {
        MapStore.onJoin();
    }

    @SubscribeEvent
    public static void onLeave(ClientPlayerNetworkEvent.LoggingOut event) {
        MapStore.onLeave();
        ClientState.reset();
    }

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        float spin = ClientAnims.bodySpin(event.getEntity().getId(), event.getPartialTick());
        if (spin != 0f) {
            event.getPoseStack().pushPose();
            event.getPoseStack().mulPose(Axis.YP.rotationDegrees(-spin));
            spinPushed.add(event.getEntity().getId());
        }
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (spinPushed.remove(event.getEntity().getId())) {
            event.getPoseStack().popPose();
        }
    }

    private static final java.util.Set<Integer> spinPushed = new java.util.HashSet<>();
}
