package com.voxelgame.sim;

/**
 * Types of damage that can be applied to the player.
 * Used by Player.damage() to identify the cause and by future
 * systems (mobs, environment) to apply appropriate effects.
 */
public enum DamageSource {

    /** Damage from falling more than 3 blocks. */
    FALL,

    /** Damage from a hostile mob (Phase 3). */
    MOB,

    /** Damage from falling into the void (below Y = -64). */
    VOID,

    /** Generic/unspecified damage. */
    GENERIC,

    /** Damage from drowning (no oxygen underwater). */
    DROWNING,

    /** Damage from TNT explosion. */
    EXPLOSION,

    /** Damage from contact with lava. */
    LAVA,

    /** Damage from arrow projectile. */
    ARROW
}
