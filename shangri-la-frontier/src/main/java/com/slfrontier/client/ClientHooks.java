package com.slfrontier.client;

import com.slfrontier.client.screen.DialogueScreen;
import com.slfrontier.network.ModNetwork;
import com.slfrontier.registry.ModAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

/**
 * Entry points for packets that arrive on the client. Only ever called on the
 * physical client, so it may touch client-only classes.
 */
public final class ClientHooks {
    private ClientHooks() {
    }

    public static void handleSync(CompoundTag tag) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            ModAttachments.get(player).load(tag);
        }
    }

    public static void openDialogue(ModNetwork.OpenDialogue payload) {
        Minecraft.getInstance().setScreen(new DialogueScreen(payload));
    }

    public static void playAnimation(ModNetwork.PlayAnimation payload) {
        ClientAnims.start(payload.entityId(), payload.animation(), payload.duration());
    }

    public static void announce(ModNetwork.Announce payload) {
        FrontierHud.announce(payload.titleKey(), payload.subtitleKey(), payload.color());
    }
}
