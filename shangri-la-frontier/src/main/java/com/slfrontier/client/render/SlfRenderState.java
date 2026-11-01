package com.slfrontier.client.render;

import com.slfrontier.entity.SlfAnimated;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** What an {@link SlfMobRenderer} reads off a Frontier creature each frame for its model and layers. */
public class SlfRenderState extends LivingEntityRenderState {
    /** Milliseconds into each animation slot, already held back by hit-stops; -1 while a slot is stopped. */
    public final long[] animTimes = new long[SlfAnimated.MAX_ANIMS];
    /** Strength of the white hit-flash, 0..1. */
    public float flash;
    /** A per-creature switch read by its model hook (Wezaemon: disarmed). */
    public boolean flag;
}
