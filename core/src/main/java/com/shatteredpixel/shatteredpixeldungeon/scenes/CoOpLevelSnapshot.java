/*
 * Co-op development helper.
 * Shares terrain from the active dungeon if one is already loaded, without
 * resetting or clearing that active game. If no game is active, it generates
 * a temporary test floor using the game's normal generator.
 *
 * This transfers terrain only; complete level-state serialization is a later
 * step. Do not use this as the final multiplayer save/load implementation.
 */
package com.shatteredpixel.shatteredpixeldungeon.scenes;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;

public final class CoOpLevelSnapshot {

    private CoOpLevelSnapshot() {}

    /**
     * Creates a terrain snapshot without overwriting an already-loaded level.
     * If a real level is active, that level is shared. If not, a temporary
     * test level is generated. Existing active hero/level references are never
     * cleared by this method.
     */
    public static String createHostLevelSnapshot() {
        // IMPORTANT: scene changes do not necessarily clear Dungeon static state.
        // If a floor is already loaded, snapshot it directly and do not call
        // Dungeon.init(), Dungeon.newLevel(), or clear any game references.
        if (Dungeon.level != null && Dungeon.level.map != null) {
            Level current = Dungeon.level;
            int hostPosition = Dungeon.hero != null ? Dungeon.hero.pos : current.entrance();
            if (!isPassable(current, hostPosition)) {
                hostPosition = findPassable(current, -1);
            }
            int guestPosition = findPassable(current, hostPosition);
            if (hostPosition < 0 || guestPosition < 0) {
                throw new IllegalStateException("Could not find two valid starting cells on the current dungeon floor.");
            }
            return CoOpProtocol.levelSnapshot(Dungeon.depth, current.width(), current.height(),
                    hostPosition, guestPosition, current.map.clone());
        }

        // A hero without a loaded level is an inconsistent/transitioning game
        // state. Refuse to initialize over it, rather than damaging the run.
        if (Dungeon.hero != null) {
            throw new IllegalStateException(
                    "A hero is active but no dungeon floor is loaded. Finish loading the floor or return to the title screen before starting the co-op test.");
        }

        // No active game is loaded, so a temporary test floor can be generated.
        try {
            if (GamesInProgress.selectedClass == null) {
                GamesInProgress.selectedClass = HeroClass.WARRIOR;
            }
            Dungeon.initSeed();
            Dungeon.init();
            Level level = Dungeon.newLevel();
            if (level == null || level.map == null || level.width() < 1 || level.height() < 1) {
                throw new IllegalStateException("The game did not generate a valid dungeon map.");
            }

            int hostPosition = level.entrance();
            if (!isPassable(level, hostPosition)) hostPosition = findPassable(level, -1);
            int guestPosition = findPassable(level, hostPosition);
            if (hostPosition < 0 || guestPosition < 0) {
                throw new IllegalStateException("Could not find two valid starting cells on the generated map.");
            }

            return CoOpProtocol.levelSnapshot(Dungeon.depth, level.width(), level.height(),
                    hostPosition, guestPosition, level.map.clone());
        } finally {
            // This cleanup is only reached on the no-active-game path above.
            // It must never run when an existing game was detected.
            Dungeon.hero = null;
            Dungeon.level = null;
        }
    }

    private static int findPassable(Level level, int except) {
        for (int i = 0; i < level.length(); i++) {
            if (i != except && isPassable(level, i)) return i;
        }
        return -1;
    }

    private static boolean isPassable(Level level, int cell) {
        return cell >= 0 && cell < level.length()
                && level.passable != null && level.passable[cell];
    }
}
