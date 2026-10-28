package com.slfrontier.quest;

import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * One page of NPC dialogue. Lines and options are translation keys; a line may carry
 * arguments as {@code key|arg1|arg2}.
 */
public final class Dialogue {
    public record Option(String labelKey, Consumer<ServerPlayer> action) {
    }

    private final String speakerKey;
    private final List<String> lines = new ArrayList<>();
    private final List<Option> options = new ArrayList<>();

    private Dialogue(String speakerKey) {
        this.speakerKey = speakerKey;
    }

    public static Dialogue of(String npcId) {
        return new Dialogue("entity.slfrontier." + npcId);
    }

    /** Adds lines {@code dialogue.slfrontier.<node>.1 .. .count}. */
    public Dialogue node(String node, int count) {
        for (int i = 1; i <= count; i++) lines.add("dialogue.slfrontier." + node + "." + i);
        return this;
    }

    public Dialogue line(String key, Object... args) {
        StringBuilder sb = new StringBuilder(key);
        for (Object a : args) sb.append('|').append(a);
        lines.add(sb.toString());
        return this;
    }

    public Dialogue option(String option, Consumer<ServerPlayer> action) {
        options.add(new Option("dialogue.slfrontier.option." + option, action));
        return this;
    }

    /**
     * An option with a full translation key and arguments ({@code key|arg|...}); arguments
     * starting with '@' are translation keys themselves.
     */
    public Dialogue optionKey(String key, Consumer<ServerPlayer> action, Object... args) {
        StringBuilder sb = new StringBuilder(key);
        for (Object a : args) sb.append('|').append(a);
        options.add(new Option(sb.toString(), action));
        return this;
    }

    public boolean hasOptions() {
        return !options.isEmpty();
    }

    public Dialogue close() {
        return option("bye", p -> {
        });
    }

    public String speakerKey() {
        return speakerKey;
    }

    public List<String> lines() {
        return lines;
    }

    public List<Option> options() {
        return options;
    }
}
