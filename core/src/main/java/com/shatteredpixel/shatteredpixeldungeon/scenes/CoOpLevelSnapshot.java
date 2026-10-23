/*
 * Step 2 co-op test helper: generate a fresh real level and encode its terrain.
 * This is a transfer test only; it does not switch the game into GameScene.
 */
package com.shatteredpixel.shatteredpixeldungeon.scenes;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;

public final class CoOpLevelSnapshot {

    private CoOpLevelSnapshot() {}

    /**
     * Generates a real floor using the game's normal level generator and returns
     * its actual terrain array as a CoOpProtocol LEVEL line. The temporary run
     * is not saved and no gameplay scene is opened.
     */
    public static String generateFreshLevelSnapshot() {
        // This test is launched from the title/co-op menu, before a run is active.
        // Refuse to overwrite a live in-memory game, preserving single-player state.
        if (Dungeon.hero != null || Dungeon.level != null) {
            throw new IllegalStateException(
                    "A game is already active in memory. Return to the title screen and try co-op again.");
        }

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
            if (!isPassable(level, hostPosition)) {
                hostPosition = findPassable(level, -1);
            }
            int guestPosition = findPassable(level, hostPosition);
            if (hostPosition < 0 || guestPosition < 0) {
                throw new IllegalStateException("Could not find two valid starting cells on the generated map.");
            }

            return CoOpProtocol.levelSnapshot(
                    Dungeon.depth, level.width(), level.height(),
                    hostPosition, guestPosition, level.map.clone());
        } finally {
            // Do not leave the temporary test hero/level in memory. A later
            // normal single-player start will initialize its own run as usual.
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
