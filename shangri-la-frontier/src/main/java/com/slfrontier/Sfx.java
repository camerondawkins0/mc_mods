package com.slfrontier;

import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/** Playing the Frontier's sounds (registry.ModSounds) from the server. */
public final class Sfx {
    private Sfx() {
    }

    /** Only this player hears it: personal cues such as level ups and quest updates. */
    public static void personal(ServerPlayer player, Holder<SoundEvent> sound, float volume, float pitch) {
        player.connection.send(new ClientboundSoundPacket(sound, SoundSource.PLAYERS, player.getX(), player.getEyeY(), player.getZ(),
                volume, pitch, player.getRandom().nextLong()));
    }

    /** Heard by everyone near the entity. */
    public static void at(Entity entity, Holder<SoundEvent> sound, SoundSource source, float volume, float pitch) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), sound, source, volume, pitch);
    }
}
