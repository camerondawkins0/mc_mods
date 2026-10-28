package com.slfrontier.quest;

import org.jetbrains.annotations.Nullable;

/**
 * The main story line, in order. Stored on the player by name (older saves stored the
 * ordinal; {@link #LEGACY} maps those back), so chapters can be inserted freely.
 */
public enum QuestStage {
    NOT_STARTED(0, 0),
    // Act I: Firstia, the Rampant Forest and Secondil
    MEET_GUIDE(1, 0),
    GOBLIN_HUNT(1, 5),
    RAVENOUS_SNAKE(1, 0),
    REACH_SECONDIL(1, 0),
    GRAY_ORE(1, 6),
    // Act II: Lycagon's mark, Rabituza and the Moonblades
    NIGHTSLAYER_ENCOUNTER(2, 0),
    INVITATION(2, 0),
    MEET_VYSACHE(2, 0),
    VORPAL_COLOSSEUM(2, 0),
    MOONBLADES(2, 0),
    // Act III: the road to Thirdrema and Unique Scenario EX
    MUD_DIGGER_ROAD(3, 0),
    THIRDREMA(3, 0),
    FIND_SETSUNA(3, 0),
    TOMBGUARD(3, 0),
    // Act IV: the Nightslayer
    LYCAGON_SHADOW(4, 0),
    NIGHTSLAYER_FINAL(4, 0),
    COMPLETE(4, 0);

    /** Stage order of saves written before stages were stored by name. */
    private static final String[] LEGACY = {"NOT_STARTED", "MEET_GUIDE", "GOBLIN_HUNT", "GRAY_ORE", "NIGHTSLAYER_ENCOUNTER",
            "INVITATION", "MEET_VYSACHE", "MOONBLADES", "FIND_SETSUNA", "TOMBGUARD", "NIGHTSLAYER_FINAL", "COMPLETE"};

    /** The act (part of the story) this chapter belongs to; 0 before logging in. */
    public final int act;
    /** Number of things to collect or defeat, 0 when the stage is a single action. */
    public final int goal;

    QuestStage(int act, int goal) {
        this.act = act;
        this.goal = goal;
    }

    private String key() {
        return "quest.slfrontier." + name().toLowerCase();
    }

    public String titleKey() {
        return key() + ".title";
    }

    public String objectiveKey() {
        return key() + ".objective";
    }

    /** Longer journal entry shown in the quest log. */
    public String journalKey() {
        return key() + ".journal";
    }

    /** Where the chapter takes place. */
    public String locationKey() {
        return key() + ".location";
    }

    public static String actKey(int act) {
        return "quest.slfrontier.act." + act;
    }

    public QuestStage next() {
        QuestStage[] values = values();
        return values[Math.min(ordinal() + 1, values.length - 1)];
    }

    public boolean atLeast(QuestStage other) {
        return ordinal() >= other.ordinal();
    }

    public static QuestStage byOrdinal(int i) {
        QuestStage[] values = values();
        return values[Math.max(0, Math.min(i, values.length - 1))];
    }

    @Nullable
    public static QuestStage byName(String name) {
        for (QuestStage s : values()) {
            if (s.name().equals(name)) return s;
        }
        return null;
    }

    public static QuestStage fromLegacyOrdinal(int i) {
        QuestStage s = byName(LEGACY[Math.max(0, Math.min(i, LEGACY.length - 1))]);
        return s == null ? NOT_STARTED : s;
    }
}
