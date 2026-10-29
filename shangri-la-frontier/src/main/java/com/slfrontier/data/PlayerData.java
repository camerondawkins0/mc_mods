package com.slfrontier.data;

import com.slfrontier.quest.QuestStage;
import com.slfrontier.quest.SideQuest;
import com.slfrontier.skill.Skill;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Everything the Frontier knows about a player's avatar: level, stats, stamina,
 * skills, story progress and where to send them when they log out.
 * Stored as a NeoForge data attachment and mirrored to the client.
 */
public class PlayerData implements INBTSerializable<CompoundTag> {
    public static final int MAX_LEVEL = 99;
    public static final int SKILL_SLOTS = 4;
    public static final float BASE_STAMINA = 100f;

    private int level = 1;
    private int exp = 0;
    private int statPoints = 0;
    private final int[] stats = new int[Stat.values().length];
    private float stamina = BASE_STAMINA;

    private final EnumSet<Skill> unlocked = EnumSet.noneOf(Skill.class);
    private final Skill[] equipped = new Skill[SKILL_SLOTS];
    private final EnumMap<Skill, Integer> cooldowns = new EnumMap<>(Skill.class);
    private final EnumMap<Skill, Integer> uses = new EnumMap<>(Skill.class);

    private QuestStage stage = QuestStage.NOT_STARTED;
    private int questProgress = 0;
    /** Index of the current step within the chapter (see QuestSteps). */
    private int questStep = 0;
    private final Set<String> flags = new HashSet<>();
    private boolean cursed = false;
    /** Active side quests and their progress, in the order they were accepted. */
    private final Map<String, Integer> sideProgress = new LinkedHashMap<>();
    private final Set<String> sideDone = new LinkedHashSet<>();
    /** Quest shown in the HUD tracker: "" for the main story, otherwise a side quest id. */
    private String tracked = "";

    // Map & fast travel
    private final Set<String> waypoints = new LinkedHashSet<>();
    private String respawnWaypoint = "";
    private long fastTravelReadyAt;

    private boolean hasReturn = false;
    private String returnDim = "minecraft:overworld";
    private double returnX, returnY, returnZ;
    private boolean hasFrontierPos = false;
    private long frontierPos;

    // Transient combat state (server side only, never saved)
    public int parryTicks;
    public int iframeTicks;
    /** Movement keys held when the last skill key was pressed (not saved). */
    private byte moveStrafe, moveForward;
    public int offhandTargetId = -1;
    public int offhandDelay;
    public int pierceTicks;
    public boolean pierceGlowing;
    public boolean airJumpUsed;
    public int lastSyncedHash;

    public PlayerData() {
        unlocked.add(Skill.TAP_STEP);
        equipped[0] = Skill.TAP_STEP;
    }

    // ---- Leveling ------------------------------------------------------------------

    public static int expToNext(int level) {
        return (int) (25 * Math.pow(level, 1.6));
    }

    public int level() {
        return level;
    }

    public int exp() {
        return exp;
    }

    public int statPoints() {
        return statPoints;
    }

    public void setLevel(int level) {
        this.level = Mth.clamp(level, 1, MAX_LEVEL);
        this.exp = 0;
    }

    public void setExp(int exp) {
        this.exp = Math.max(0, exp);
    }

    public void addStatPoints(int points) {
        this.statPoints = Math.max(0, statPoints + points);
    }

    public int stat(Stat stat) {
        return stats[stat.ordinal()];
    }

    public void setStat(Stat stat, int value) {
        stats[stat.ordinal()] = Math.max(0, value);
    }

    public boolean allocate(Stat stat) {
        if (statPoints <= 0) return false;
        statPoints--;
        stats[stat.ordinal()]++;
        return true;
    }

    public void resetStats() {
        int spent = 0;
        for (int i = 0; i < stats.length; i++) {
            spent += stats[i];
            stats[i] = 0;
        }
        statPoints += spent;
    }

    // ---- Stamina -------------------------------------------------------------------

    public float maxStamina() {
        return BASE_STAMINA + 3f * stat(Stat.STA);
    }

    public float stamina() {
        return stamina;
    }

    public void setStamina(float stamina) {
        this.stamina = Mth.clamp(stamina, 0, maxStamina());
    }

    // ---- Skills --------------------------------------------------------------------

    public boolean hasSkill(Skill skill) {
        return unlocked.contains(skill);
    }

    /** @return true if the skill was newly unlocked */
    public boolean unlock(Skill skill) {
        if (!unlocked.add(skill)) return false;
        if (!skill.passive) {
            for (int i = 0; i < SKILL_SLOTS; i++) {
                if (equipped[i] == null) {
                    equipped[i] = skill;
                    break;
                }
            }
        }
        return true;
    }

    public Set<Skill> unlockedSkills() {
        return unlocked;
    }

    @Nullable
    public Skill equipped(int slot) {
        return slot >= 0 && slot < SKILL_SLOTS ? equipped[slot] : null;
    }

    public void equip(int slot, @Nullable Skill skill) {
        if (slot < 0 || slot >= SKILL_SLOTS) return;
        if (skill != null) {
            for (int i = 0; i < SKILL_SLOTS; i++) {
                if (equipped[i] == skill) equipped[i] = null;
            }
        }
        equipped[slot] = skill;
    }

    /** Replace an evolved skill in its slot with the evolution. */
    public void replaceEquipped(Skill from, Skill to) {
        for (int i = 0; i < SKILL_SLOTS; i++) {
            if (equipped[i] == from) equipped[i] = to;
        }
    }

    public int cooldown(Skill skill) {
        return cooldowns.getOrDefault(skill, 0);
    }

    public void setCooldown(Skill skill, int ticks) {
        if (ticks <= 0) cooldowns.remove(skill);
        else cooldowns.put(skill, ticks);
    }

    public void tickCooldowns() {
        cooldowns.replaceAll((k, v) -> v - 1);
        cooldowns.values().removeIf(v -> v <= 0);
    }

    public int uses(Skill skill) {
        return uses.getOrDefault(skill, 0);
    }

    public void setUses(Skill skill, int count) {
        uses.put(skill, Math.max(0, count));
    }

    public int addUse(Skill skill) {
        int n = uses(skill) + 1;
        uses.put(skill, n);
        return n;
    }

    // ---- Story ---------------------------------------------------------------------

    public QuestStage stage() {
        return stage;
    }

    public void setStage(QuestStage stage) {
        this.stage = stage;
        this.questProgress = 0;
        this.questStep = 0;
    }

    public int questStep() {
        return questStep;
    }

    public void setQuestStep(int step) {
        this.questStep = step;
    }

    public int questProgress() {
        return questProgress;
    }

    public void setQuestProgress(int progress) {
        this.questProgress = progress;
    }

    public boolean flag(String flag) {
        return flags.contains(flag);
    }

    public void setFlag(String flag, boolean value) {
        if (value) flags.add(flag);
        else flags.remove(flag);
    }

    public boolean cursed() {
        return cursed;
    }

    public void setCursed(boolean cursed) {
        this.cursed = cursed;
    }

    // ---- Side quests -----------------------------------------------------------------

    public enum SideState { LOCKED, AVAILABLE, ACTIVE, DONE }

    public SideState sideState(SideQuest quest) {
        if (sideDone.contains(quest.id)) return SideState.DONE;
        if (sideProgress.containsKey(quest.id)) return SideState.ACTIVE;
        return stage.atLeast(quest.unlockedAt) ? SideState.AVAILABLE : SideState.LOCKED;
    }

    public int sideProgress(SideQuest quest) {
        return sideProgress.getOrDefault(quest.id, 0);
    }

    public void setSideProgress(SideQuest quest, int progress) {
        if (sideProgress.containsKey(quest.id)) sideProgress.put(quest.id, Math.max(0, progress));
    }

    public void acceptSide(SideQuest quest) {
        if (!sideDone.contains(quest.id)) sideProgress.putIfAbsent(quest.id, 0);
    }

    public void completeSide(SideQuest quest) {
        sideProgress.remove(quest.id);
        sideDone.add(quest.id);
        if (tracked.equals(quest.id)) tracked = "";
    }

    public Set<String> activeSideIds() {
        return sideProgress.keySet();
    }

    public String tracked() {
        return tracked;
    }

    public void setTracked(String id) {
        this.tracked = id == null ? "" : id;
    }

    // ---- Waypoints -------------------------------------------------------------------

    public boolean knowsWaypoint(String id) {
        return waypoints.contains(id);
    }

    /** @return true if newly discovered */
    public boolean discoverWaypoint(String id) {
        return waypoints.add(id);
    }

    public Set<String> waypoints() {
        return waypoints;
    }

    public String respawnWaypoint() {
        return respawnWaypoint;
    }

    public void setRespawnWaypoint(String id) {
        this.respawnWaypoint = id == null ? "" : id;
    }

    public long fastTravelReadyAt() {
        return fastTravelReadyAt;
    }

    public void setFastTravelReadyAt(long gameTime) {
        this.fastTravelReadyAt = gameTime;
    }

    // ---- Login / logout ------------------------------------------------------------

    public void setReturnPoint(String dim, double x, double y, double z) {
        this.hasReturn = true;
        this.returnDim = dim;
        this.returnX = x;
        this.returnY = y;
        this.returnZ = z;
    }

    public void setFrontierPos(long pos) {
        this.hasFrontierPos = true;
        this.frontierPos = pos;
    }

    public void setMoveInput(byte strafe, byte forward) {
        this.moveStrafe = strafe;
        this.moveForward = forward;
    }

    public byte moveStrafe() {
        return moveStrafe;
    }

    public byte moveForward() {
        return moveForward;
    }

    public boolean hasFrontierPos() {
        return hasFrontierPos;
    }

    /**
     * Saves from before the designed map remember places in the old Frontier (where you logged
     * out, your waypoints). Forget them once, so the avatar starts over in Firstia.
     * @return true if anything was reset
     */
    public boolean migrateToDesignedMap() {
        if (flag("designed_map")) return false;
        setFlag("designed_map", true);
        boolean had = hasFrontierPos || !waypoints.isEmpty();
        hasFrontierPos = false;
        respawnWaypoint = "";
        waypoints.clear();
        return had;
    }

    public long frontierPos() {
        return frontierPos;
    }

    public boolean hasReturn() {
        return hasReturn;
    }

    public String returnDim() {
        return returnDim;
    }

    public double returnX() {
        return returnX;
    }

    public double returnY() {
        return returnY;
    }

    public double returnZ() {
        return returnZ;
    }

    // ---- Serialization -------------------------------------------------------------

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("level", level);
        tag.putInt("exp", exp);
        tag.putInt("statPoints", statPoints);
        tag.putIntArray("stats", stats.clone());
        tag.putFloat("stamina", stamina);
        ListTag skills = new ListTag();
        for (Skill s : unlocked) skills.add(StringTag.valueOf(s.id));
        tag.put("skills", skills);
        ListTag slots = new ListTag();
        for (Skill s : equipped) slots.add(StringTag.valueOf(s == null ? "" : s.id));
        tag.put("equipped", slots);
        CompoundTag cds = new CompoundTag();
        cooldowns.forEach((k, v) -> cds.putInt(k.id, v));
        tag.put("cooldowns", cds);
        CompoundTag useTag = new CompoundTag();
        uses.forEach((k, v) -> useTag.putInt(k.id, v));
        tag.put("uses", useTag);
        tag.putString("stageName", stage.name());
        tag.putInt("questProgress", questProgress);
        tag.putInt("questStep", questStep);
        CompoundTag side = new CompoundTag();
        sideProgress.forEach(side::putInt);
        tag.put("sideProgress", side);
        ListTag done = new ListTag();
        for (String d : sideDone) done.add(StringTag.valueOf(d));
        tag.put("sideDone", done);
        tag.putString("tracked", tracked);
        ListTag wps = new ListTag();
        for (String w : waypoints) wps.add(StringTag.valueOf(w));
        tag.put("waypoints", wps);
        tag.putString("respawnWaypoint", respawnWaypoint);
        tag.putLong("fastTravelReadyAt", fastTravelReadyAt);
        ListTag flagList = new ListTag();
        for (String f : flags) flagList.add(StringTag.valueOf(f));
        tag.put("flags", flagList);
        tag.putBoolean("cursed", cursed);
        tag.putBoolean("hasReturn", hasReturn);
        tag.putString("returnDim", returnDim);
        tag.putDouble("returnX", returnX);
        tag.putDouble("returnY", returnY);
        tag.putDouble("returnZ", returnZ);
        tag.putBoolean("hasFrontierPos", hasFrontierPos);
        tag.putLong("frontierPos", frontierPos);
        return tag;
    }

    public void load(CompoundTag tag) {
        level = Mth.clamp(tag.getInt("level"), 1, MAX_LEVEL);
        exp = tag.getInt("exp");
        statPoints = tag.getInt("statPoints");
        int[] saved = tag.getIntArray("stats");
        for (int i = 0; i < stats.length; i++) stats[i] = i < saved.length ? saved[i] : 0;
        stamina = tag.getFloat("stamina");
        unlocked.clear();
        for (Tag t : tag.getList("skills", Tag.TAG_STRING)) {
            Skill s = Skill.byId(t.getAsString());
            if (s != null) unlocked.add(s);
        }
        if (unlocked.isEmpty()) unlocked.add(Skill.TAP_STEP);
        ListTag slots = tag.getList("equipped", Tag.TAG_STRING);
        for (int i = 0; i < SKILL_SLOTS; i++) {
            equipped[i] = i < slots.size() ? Skill.byId(slots.getString(i)) : null;
        }
        cooldowns.clear();
        CompoundTag cds = tag.getCompound("cooldowns");
        for (String key : cds.getAllKeys()) {
            Skill s = Skill.byId(key);
            if (s != null) cooldowns.put(s, cds.getInt(key));
        }
        uses.clear();
        CompoundTag useTag = tag.getCompound("uses");
        for (String key : useTag.getAllKeys()) {
            Skill s = Skill.byId(key);
            if (s != null) uses.put(s, useTag.getInt(key));
        }
        QuestStage named = tag.contains("stageName") ? QuestStage.byName(tag.getString("stageName")) : null;
        stage = named != null ? named : QuestStage.fromLegacyOrdinal(tag.getInt("stage"));
        questProgress = tag.getInt("questProgress");
        questStep = tag.getInt("questStep");
        sideProgress.clear();
        CompoundTag side = tag.getCompound("sideProgress");
        for (String key : side.getAllKeys()) {
            if (SideQuest.byId(key) != null) sideProgress.put(key, side.getInt(key));
        }
        sideDone.clear();
        for (Tag t : tag.getList("sideDone", Tag.TAG_STRING)) sideDone.add(t.getAsString());
        tracked = tag.getString("tracked");
        waypoints.clear();
        for (Tag t : tag.getList("waypoints", Tag.TAG_STRING)) waypoints.add(t.getAsString());
        respawnWaypoint = tag.getString("respawnWaypoint");
        fastTravelReadyAt = tag.getLong("fastTravelReadyAt");
        flags.clear();
        for (Tag t : tag.getList("flags", Tag.TAG_STRING)) flags.add(t.getAsString());
        cursed = tag.getBoolean("cursed");
        hasReturn = tag.getBoolean("hasReturn");
        returnDim = tag.getString("returnDim");
        returnX = tag.getDouble("returnX");
        returnY = tag.getDouble("returnY");
        returnZ = tag.getDouble("returnZ");
        hasFrontierPos = tag.getBoolean("hasFrontierPos");
        frontierPos = tag.getLong("frontierPos");
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        return save();
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
        load(nbt);
    }

    /** Cheap change detector so the server only syncs when something moved. */
    public int syncHash() {
        int h = level;
        h = 31 * h + exp;
        h = 31 * h + statPoints;
        for (int s : stats) h = 31 * h + s;
        h = 31 * h + (int) stamina;
        h = 31 * h + unlocked.hashCode();
        for (Skill s : equipped) h = 31 * h + (s == null ? 0 : s.ordinal() + 1);
        for (Map.Entry<Skill, Integer> e : cooldowns.entrySet()) h = 31 * h + e.getKey().ordinal() * 7 + (e.getValue() + 9) / 10;
        h = 31 * h + stage.ordinal();
        h = 31 * h + questProgress;
        h = 31 * h + questStep;
        h = 31 * h + flags.hashCode();
        h = 31 * h + (cursed ? 1 : 0);
        h = 31 * h + sideProgress.hashCode();
        h = 31 * h + sideDone.hashCode();
        h = 31 * h + tracked.hashCode();
        h = 31 * h + waypoints.hashCode();
        h = 31 * h + respawnWaypoint.hashCode();
        h = 31 * h + Long.hashCode(fastTravelReadyAt);
        return h;
    }
}
