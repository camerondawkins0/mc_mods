package com.slfrontier.entity;

/** A Frontier monster with a displayed level and a fixed experience reward. */
public interface LeveledMob {
    int frontierLevel();

    int frontierExp();
}
