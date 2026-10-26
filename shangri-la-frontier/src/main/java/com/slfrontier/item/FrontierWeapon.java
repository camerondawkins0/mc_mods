package com.slfrontier.item;

/** Shared information for Frontier weapons shown in tooltips and used in combat. */
public interface FrontierWeapon {
    enum Kind {
        TWIN_BLADES, KATANA, GREATSWORD
    }

    Kind kind();

    /** Extra critical chance while held in the main hand. */
    default float critBonus() {
        return 0f;
    }
}
