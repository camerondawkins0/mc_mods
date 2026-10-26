package com.slfrontier.quest;

/** The main story line. Stored on the player as an ordinal. */
public enum QuestStage {
    NOT_STARTED(0),
    MEET_GUIDE(0),
    GOBLIN_HUNT(5),
    GRAY_ORE(6),
    NIGHTSLAYER_ENCOUNTER(0),
    INVITATION(0),
    MEET_VYSACHE(0),
    MOONBLADES(0),
    FIND_SETSUNA(0),
    TOMBGUARD(0),
    NIGHTSLAYER_FINAL(0),
    COMPLETE(0);

    /** Number of things to collect or defeat, 0 when the stage is a single action. */
    public final int goal;

    QuestStage(int goal) {
        this.goal = goal;
    }

    public String titleKey() {
        return "quest.slfrontier." + name().toLowerCase() + ".title";
    }

    public String objectiveKey() {
        return "quest.slfrontier." + name().toLowerCase() + ".objective";
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
}
