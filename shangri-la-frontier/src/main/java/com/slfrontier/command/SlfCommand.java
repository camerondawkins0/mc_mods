package com.slfrontier.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.slfrontier.SLFrontier;
import com.slfrontier.data.PlayerData;
import com.slfrontier.data.Stat;
import com.slfrontier.network.ModNetwork;
import com.slfrontier.progression.Progression;
import com.slfrontier.quest.QuestStage;
import com.slfrontier.registry.ModAttachments;
import com.slfrontier.registry.ModEntities;
import com.slfrontier.registry.ModItems;
import com.slfrontier.skill.Skill;
import com.slfrontier.world.Frontier;
import com.slfrontier.world.FrontierTravel;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@code /slf} — status for everyone, plus admin tools for testing the story:
 * {@code /slf level}, {@code /slf exp}, {@code /slf quest}, {@code /slf skill},
 * {@code /slf curse}, {@code /slf login|logout} and {@code /slf debug selftest}.
 */
public final class SlfCommand {
    private SlfCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("slf")
                .then(Commands.literal("status").executes(ctx -> status(ctx, ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("level").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("level", IntegerArgumentType.integer(1, PlayerData.MAX_LEVEL))
                                        .executes(ctx -> setLevel(EntityArgument.getPlayer(ctx, "player"), IntegerArgumentType.getInteger(ctx, "level"))))))
                .then(Commands.literal("exp").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                            Progression.grantExp(p, IntegerArgumentType.getInteger(ctx, "amount"));
                                            return 1;
                                        }))))
                .then(Commands.literal("quest").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("stage", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(QuestStage.values()).map(s -> s.name().toLowerCase()), b))
                                        .executes(SlfCommand::setQuest))))
                .then(Commands.literal("skill").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("skill", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(Skill.values()).map(s -> s.id), b))
                                        .executes(ctx -> {
                                            ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                            Skill skill = Skill.byId(StringArgumentType.getString(ctx, "skill"));
                                            if (skill == null) return 0;
                                            ModAttachments.get(p).unlock(skill);
                                            Progression.announceSkill(p, skill);
                                            return 1;
                                        }))))
                .then(Commands.literal("curse").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("cursed", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            ModAttachments.get(EntityArgument.getPlayer(ctx, "player")).setCursed(BoolArgumentType.getBool(ctx, "cursed"));
                                            return 1;
                                        }))))
                .then(Commands.literal("login").requires(s -> s.hasPermission(2))
                        .executes(ctx -> {
                            FrontierTravel.login(ctx.getSource().getPlayerOrException());
                            return 1;
                        }))
                .then(Commands.literal("logout").requires(s -> s.hasPermission(2))
                        .executes(ctx -> {
                            FrontierTravel.logout(ctx.getSource().getPlayerOrException());
                            return 1;
                        }))
                .then(Commands.literal("debug").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("selftest").executes(SlfCommand::selfTest))));
    }

    private static int status(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        PlayerData d = ModAttachments.get(player);
        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> Component.translatable("command.slfrontier.status.header", player.getDisplayName(), d.level(), d.exp(),
                PlayerData.expToNext(d.level())).withStyle(ChatFormatting.GOLD), false);
        StringBuilder stats = new StringBuilder();
        for (Stat s : Stat.values()) stats.append(s.name()).append(' ').append(d.stat(s)).append("  ");
        src.sendSuccess(() -> Component.literal(stats.toString().trim()).withStyle(ChatFormatting.YELLOW), false);
        src.sendSuccess(() -> Component.translatable("command.slfrontier.status.quest",
                Component.translatable(d.stage().titleKey())).withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int setLevel(ServerPlayer player, int level) {
        PlayerData d = ModAttachments.get(player);
        int gained = level - d.level();
        d.setLevel(level);
        if (gained > 0) d.addStatPoints(gained * 3);
        for (Skill s : Skill.values()) {
            if (s.unlockLevel > 0 && s.unlockLevel <= level) d.unlock(s);
        }
        Progression.applyStats(player);
        ModNetwork.sync(player);
        return 1;
    }

    private static int setQuest(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String name = StringArgumentType.getString(ctx, "stage").toUpperCase();
        for (QuestStage stage : QuestStage.values()) {
            if (stage.name().equals(name)) {
                ModAttachments.get(player).setStage(stage);
                ModNetwork.sync(player);
                ctx.getSource().sendSuccess(() -> Component.literal("Quest stage set to " + stage.name()), true);
                return 1;
            }
        }
        ctx.getSource().sendFailure(Component.literal("Unknown stage " + name));
        return 0;
    }

    /** Sanity checks used by CI: the dimension exists and every template loads. */
    private static int selfTest(CommandContext<CommandSourceStack> ctx) {
        List<String> problems = new ArrayList<>();
        ServerLevel frontier = ctx.getSource().getServer().getLevel(Frontier.LEVEL);
        if (frontier == null) {
            problems.add("Frontier dimension is missing");
        } else {
            for (String t : List.of("town/fastera", "town/rabituza", "wezaemon_tomb", "nightslayer_den", "empire_bee_hive",
                    "mud_digger_lair", "divinity_ruins")) {
                if (frontier.getStructureManager().get(SLFrontier.id(t)).isEmpty()) problems.add("Missing template " + t);
            }
            FrontierTravel.ensureFastera(frontier);
            FrontierTravel.ensureRabituza(frontier);
        }
        if (ModItems.ITEMS.getEntries().isEmpty()) problems.add("No items registered");
        if (ModEntities.LYCAGON.get() == null) problems.add("Lycagon not registered");
        if (problems.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("Self-test passed"), false);
            SLFrontier.LOGGER.info("Self-test passed");
            return 1;
        }
        for (String p : problems) {
            ctx.getSource().sendFailure(Component.literal(p));
            SLFrontier.LOGGER.error("Self-test problem: {}", p);
        }
        return 0;
    }
}
