/*
 * Co-op second player character.
 * This class deliberately reuses Hero mechanics while maintaining its own
 * instance state (position, HP, belongings, buffs, and action queue).
 */
package com.shatteredpixel.shatteredpixeldungeon.actors.hero;

public class Comrade extends Hero {

    /** Most recently selected target cell reported by the remote player. */
    public volatile int targetCell = -1;

    public Comrade() {
        super();
    }
}
