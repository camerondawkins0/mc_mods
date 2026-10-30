package com.slfrontier.quest;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The steps inside each story chapter — usually "go there", "do this", "report back". The
 * tracker, the journal and every pointer follow the current step, so a quest that sends you
 * to the goblin camp points at the camp, not at the town it started in.
 *
 * Which step is current is worked out on the server ({@link Story#stepDone}) and synced with
 * the player data; the last step of a chapter is finished when the chapter itself advances.
 */
public final class QuestSteps {
    /** One step; {@code counts} marks the step the chapter's progress counter belongs to. */
    public record Step(String id, boolean counts) {
    }

    private static final Map<QuestStage, List<Step>> STEPS = new EnumMap<>(QuestStage.class);

    static {
        put(QuestStage.MEET_GUIDE, step("talk_guide"));
        put(QuestStage.GOBLIN_HUNT, step("find_camp"), counting("defeat_goblins"), step("report_guide"));
        put(QuestStage.RAVENOUS_SNAKE, step("road_to_bridge"), step("slay_snake"));
        put(QuestStage.REACH_SECONDIL, step("reach_secondil"), step("meet_smith"));
        put(QuestStage.GRAY_ORE, counting("mine_ore"), step("deliver_ore"));
        put(QuestStage.NIGHTSLAYER_ENCOUNTER, step("find_den"), step("touch_altar"));
        put(QuestStage.INVITATION, step("return_guide"));
        put(QuestStage.MEET_VYSACHE, step("go_rabituza"), step("meet_vysache"));
        put(QuestStage.VORPAL_COLOSSEUM, step("enter_colosseum"));
        put(QuestStage.MOONBLADES, step("find_beetle"), step("bring_shell"));
        put(QuestStage.MUD_DIGGER_ROAD, step("marsh_road"), step("slay_digger"));
        put(QuestStage.THIRDREMA, step("reach_thirdrema"), step("meet_allies"));
        put(QuestStage.FIND_SETSUNA, step("mountain_pass"), step("find_setsuna"));
        put(QuestStage.TOMBGUARD, step("slay_tombguard"));
        put(QuestStage.LYCAGON_SHADOW, step("to_barrens"), step("wake_altar"));
        put(QuestStage.NIGHTSLAYER_FINAL, step("learn_crescent"), step("slay_lycagon"));
    }

    private QuestSteps() {
    }

    private static Step step(String id) {
        return new Step(id, false);
    }

    private static Step counting(String id) {
        return new Step(id, true);
    }

    private static void put(QuestStage stage, Step... steps) {
        STEPS.put(stage, List.of(steps));
    }

    public static List<Step> of(QuestStage stage) {
        return STEPS.getOrDefault(stage, List.of());
    }

    public static String textKey(QuestStage stage, Step step) {
        return "quest.slfrontier." + stage.name().toLowerCase() + ".step." + step.id();
    }
}
