package com.slfrontier.network;

import com.slfrontier.SLFrontier;
import com.slfrontier.client.ClientHooks;
import com.slfrontier.data.PlayerData;
import com.slfrontier.data.Stat;
import com.slfrontier.progression.Progression;
import com.slfrontier.quest.Story;
import com.slfrontier.registry.ModAttachments;
import com.slfrontier.skill.Skill;
import com.slfrontier.skill.SkillExecutor;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;

/** All custom packets. Everything the client asks for is validated on the server. */
public final class ModNetwork {
    private ModNetwork() {
    }

    // ---- Server -> client ----------------------------------------------------------

    /** Full avatar state (level, stats, skills, quest) for HUD and screens. */
    public record SyncData(CompoundTag tag) implements CustomPacketPayload {
        public static final Type<SyncData> TYPE = new Type<>(SLFrontier.id("sync_data"));
        public static final StreamCodec<ByteBuf, SyncData> CODEC =
                ByteBufCodecs.COMPOUND_TAG.map(SyncData::new, SyncData::tag);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Opens the NPC dialogue screen. Lines and options are translation keys. */
    public record OpenDialogue(int entityId, String speakerKey, List<String> lines, List<String> options)
            implements CustomPacketPayload {
        public static final Type<OpenDialogue> TYPE = new Type<>(SLFrontier.id("open_dialogue"));
        public static final StreamCodec<ByteBuf, OpenDialogue> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, OpenDialogue::entityId,
                ByteBufCodecs.STRING_UTF8, OpenDialogue::speakerKey,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), OpenDialogue::lines,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), OpenDialogue::options,
                OpenDialogue::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Plays a third-person body animation (skill pose) on a player. */
    public record PlayAnimation(int entityId, int animation, int duration) implements CustomPacketPayload {
        public static final Type<PlayAnimation> TYPE = new Type<>(SLFrontier.id("play_animation"));
        public static final StreamCodec<ByteBuf, PlayAnimation> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, PlayAnimation::entityId,
                ByteBufCodecs.VAR_INT, PlayAnimation::animation,
                ByteBufCodecs.VAR_INT, PlayAnimation::duration,
                PlayAnimation::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** A big centered notification ("LEVEL UP!", "Unique Scenario EX started", ...). */
    public record Announce(String titleKey, String subtitleKey, int color) implements CustomPacketPayload {
        public static final Type<Announce> TYPE = new Type<>(SLFrontier.id("announce"));
        public static final StreamCodec<ByteBuf, Announce> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Announce::titleKey,
                ByteBufCodecs.STRING_UTF8, Announce::subtitleKey,
                ByteBufCodecs.INT, Announce::color,
                Announce::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ---- Client -> server ----------------------------------------------------------

    public record UseSkill(int slot) implements CustomPacketPayload {
        public static final Type<UseSkill> TYPE = new Type<>(SLFrontier.id("use_skill"));
        public static final StreamCodec<ByteBuf, UseSkill> CODEC =
                ByteBufCodecs.VAR_INT.map(UseSkill::new, UseSkill::slot);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record DoubleJump() implements CustomPacketPayload {
        public static final Type<DoubleJump> TYPE = new Type<>(SLFrontier.id("double_jump"));
        public static final StreamCodec<ByteBuf, DoubleJump> CODEC = StreamCodec.unit(new DoubleJump());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record AllocateStat(int stat) implements CustomPacketPayload {
        public static final Type<AllocateStat> TYPE = new Type<>(SLFrontier.id("allocate_stat"));
        public static final StreamCodec<ByteBuf, AllocateStat> CODEC =
                ByteBufCodecs.VAR_INT.map(AllocateStat::new, AllocateStat::stat);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record EquipSkill(String skill, int slot) implements CustomPacketPayload {
        public static final Type<EquipSkill> TYPE = new Type<>(SLFrontier.id("equip_skill"));
        public static final StreamCodec<ByteBuf, EquipSkill> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, EquipSkill::skill,
                ByteBufCodecs.VAR_INT, EquipSkill::slot,
                EquipSkill::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record DialogueChoice(int entityId, int option) implements CustomPacketPayload {
        public static final Type<DialogueChoice> TYPE = new Type<>(SLFrontier.id("dialogue_choice"));
        public static final StreamCodec<ByteBuf, DialogueChoice> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, DialogueChoice::entityId,
                ByteBufCodecs.VAR_INT, DialogueChoice::option,
                DialogueChoice::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ---- Registration --------------------------------------------------------------

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(SyncData.TYPE, SyncData.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientHooks.handleSync(payload.tag())));
        registrar.playToClient(OpenDialogue.TYPE, OpenDialogue.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientHooks.openDialogue(payload)));
        registrar.playToClient(PlayAnimation.TYPE, PlayAnimation.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientHooks.playAnimation(payload)));
        registrar.playToClient(Announce.TYPE, Announce.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientHooks.announce(payload)));

        registrar.playToServer(UseSkill.TYPE, UseSkill.CODEC, ModNetwork::handleUseSkill);
        registrar.playToServer(DoubleJump.TYPE, DoubleJump.CODEC, ModNetwork::handleDoubleJump);
        registrar.playToServer(AllocateStat.TYPE, AllocateStat.CODEC, ModNetwork::handleAllocate);
        registrar.playToServer(EquipSkill.TYPE, EquipSkill.CODEC, ModNetwork::handleEquip);
        registrar.playToServer(DialogueChoice.TYPE, DialogueChoice.CODEC, ModNetwork::handleDialogue);
    }

    private static void handleUseSkill(UseSkill payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                SkillExecutor.trySlot(player, payload.slot());
            }
        });
    }

    private static void handleDoubleJump(DoubleJump payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                SkillExecutor.doubleJump(player);
            }
        });
    }

    private static void handleAllocate(AllocateStat payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                Stat stat = Stat.byIndex(payload.stat());
                if (stat != null && ModAttachments.get(player).allocate(stat)) {
                    Progression.applyStats(player);
                }
            }
        });
    }

    private static void handleEquip(EquipSkill payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                PlayerData data = ModAttachments.get(player);
                if (payload.skill().isEmpty()) {
                    data.equip(payload.slot(), null);
                    return;
                }
                Skill skill = Skill.byId(payload.skill());
                if (skill != null && !skill.passive && data.hasSkill(skill)) {
                    data.equip(payload.slot(), skill);
                }
            }
        });
    }

    private static void handleDialogue(DialogueChoice payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                Story.onDialogueChoice(player, payload.entityId(), payload.option());
            }
        });
    }

    // ---- Helpers -------------------------------------------------------------------

    public static void sync(ServerPlayer player) {
        PlayerData data = ModAttachments.get(player);
        data.lastSyncedHash = data.syncHash();
        PacketDistributor.sendToPlayer(player, new SyncData(data.save()));
    }

    public static void announce(ServerPlayer player, String titleKey, String subtitleKey, int color) {
        PacketDistributor.sendToPlayer(player, new Announce(titleKey, subtitleKey, color));
    }

    public static void animate(ServerPlayer player, int animation, int duration) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new PlayAnimation(player.getId(), animation, duration));
    }
}
