/*
 * Co-op level snapshot adapter.
 *
 * Captures the currently generated single-player level's terrain map into the
 * versioned CoOpProtocol format. This is a preparation step: it does not start
 * a dungeon, mutate the level, or wire networking into CoOpScene.
 */
package com.shatteredpixel.shatteredpixeldungeon.scenes;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;

public final class CoOpLevelSnapshot {

    private CoOpLevelSnapshot() {
    }

    /**
     * Encodes the currently loaded level's terrain map.
     *
     * @param guestPosition a valid cell index reserved for the joining hero;
     *                      caller must choose a suitable free cell.
     * @return a CoOpProtocol LEVEL message
     */
    public static String captureCurrentLevel(int guestPosition) {
        Level level = Dungeon.level;
        if (level == null || Dungeon.hero == null) {
            throw new IllegalStateException("A dungeon level and hero must be loaded first.");
        }

        int width = level.width();
        int height = level.height();
        int[] map = level.map.clone();
        int hostPosition = Dungeon.hero.pos;

        return CoOpProtocol.levelSnapshot(
                Dungeon.depth,
                width,
                height,
                hostPosition,
                guestPosition,
                map
        );
    }

    /**
     * Returns a defensive copy of the terrain map for later client-side use.
     * The protocol decoder validates dimensions and cell positions.
     */
    public static int[] copyCurrentTerrainMap() {
        Level level = Dungeon.level;
        if (level == null) {
            throw new IllegalStateException("A dungeon level must be loaded first.");
        }
        return level.map.clone();
    }
  }
