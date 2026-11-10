package com.shatteredpixel.shatteredpixeldungeon.scenes;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;

import java.io.IOException;

/** Keeps the co-op run in a save slot separate from normal player slots. */
public final class CoOpRunState {

    // Normal save slots are 1..GamesInProgress.MAX_SLOTS.
    public static final int SAVE_SLOT = GamesInProgress.MAX_SLOTS + 1;

    private CoOpRunState() {
    }

    /** Save the currently active run before switching the save destination. */
    public static void selectCoOpSlotPreservingCurrentRun() throws IOException {
        if (Dungeon.hero != null
                && Dungeon.level != null
                && GamesInProgress.curSlot > 0
                && GamesInProgress.curSlot != SAVE_SLOT) {
            Dungeon.saveAll();
        }

        GamesInProgress.curSlot = SAVE_SLOT;
    }

    /** Start a clean co-op run at floor 1, without reusing the single-player floor. */
    public static void startFresh() throws IOException {
        selectCoOpSlotPreservingCurrentRun();

        if (GamesInProgress.selectedClass == null) {
            GamesInProgress.selectedClass =
                    Dungeon.hero != null ? Dungeon.hero.heroClass : HeroClass.WARRIOR;
        }

        Dungeon.daily = false;
        Dungeon.dailyReplay = false;
        Dungeon.initSeed();
        Dungeon.init();

        Level level = Dungeon.newLevel();
        Dungeon.switchLevel(level, level.entrance());
    }

    public static boolean hasSavedRun() {
        return GamesInProgress.gameExists(SAVE_SLOT);
    }

    /** Restore the saved local co-op run. */
    public static void restore() throws IOException {
        GamesInProgress.curSlot = SAVE_SLOT;
        Dungeon.loadGame(SAVE_SLOT);

        Level level = Dungeon.loadLevel(SAVE_SLOT);
        Dungeon.switchLevel(level, Dungeon.hero.pos);
    }
}
