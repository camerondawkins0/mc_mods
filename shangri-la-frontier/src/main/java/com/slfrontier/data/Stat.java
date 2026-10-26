package com.slfrontier.data;

/**
 * Avatar stats, mirroring the status screen of Shangri-La Frontier.
 * Points are earned on level-up and spent on the Status tab.
 */
public enum Stat {
    VIT("vit", "Vitality: +1 max health per point"),
    STR("str", "Strength: +0.25 attack damage per point"),
    AGI("agi", "Agility: +0.4% movement speed per point"),
    DEX("dex", "Dexterity: +1% attack speed per point"),
    TEC("tec", "Technique: -1% skill cooldowns per point, longer parry window"),
    LUC("luc", "Luck: +0.5% critical chance per point"),
    STA("sta", "Stamina: +3 max stamina per point");

    public final String key;
    public final String help;

    Stat(String key, String help) {
        this.key = key;
        this.help = help;
    }

    public String translationKey() {
        return "stat.slfrontier." + key;
    }

    public static Stat byIndex(int i) {
        Stat[] values = values();
        return i >= 0 && i < values.length ? values[i] : null;
    }
}
