package com.slfrontier.registry;

import com.slfrontier.SLFrontier;
import com.slfrontier.data.PlayerData;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, SLFrontier.MODID);

    public static final Supplier<AttachmentType<PlayerData>> PLAYER_DATA = ATTACHMENTS.register("player_data",
            () -> AttachmentType.serializable(PlayerData::new).copyOnDeath().build());

    private ModAttachments() {
    }

    public static PlayerData get(Player player) {
        return player.getData(PLAYER_DATA);
    }
}
