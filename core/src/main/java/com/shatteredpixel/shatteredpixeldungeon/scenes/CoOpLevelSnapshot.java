/*
 * Co-op development helper.
 * Shares terrain from the active dungeon if one is already loaded, without
 * resetting or clearing that active game. If no game is active, it generates
 * a temporary test floor using the game's normal generator.
 *
 * The legacy terrain snapshot method remains for compatibility; the current
 * Co-op test uses createSerializedLevel() to transfer the complete Level Bundle.
 */
package com.shatteredpixel.shatteredpixeldungeon.scenes;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.watabou.utils.Bundle;

import java.io.ByteArrayOutputStream;

public final class CoOpLevelSnapshot {

    private CoOpLevelSnapshot() {}

    /** Complete compressed Level bundle and basic session metadata. */
    public static final class SerializedLevel {
        public final byte[] data;
        public final int floor;
        public final int width;
        public final int height;
        public final int hostPosition;
        public final int guestPosition;
        public final int terrainCells;

        private SerializedLevel(byte[] data, int floor, int width, int height,
                                int hostPosition, int guestPosition, int terrainCells) {
            this.data = data;
            this.floor = floor;
            this.width = width;
            this.height = height;
            this.hostPosition = hostPosition;
            this.guestPosition = guestPosition;
            this.terrainCells = terrainCells;
        }
    }

    /**
     * Serializes the complete current Level using the game's own Bundle format.
     * If no run is active, creates a real run and keeps it active so the host can
     * display the same floor after the joining phone has received it. Existing
     * active runs are reused without calling Dungeon.init().
     */
    public static SerializedLevel createSerializedLevel() {
        if (Dungeon.level != null && Dungeon.level.map != null) {
            return serialize(Dungeon.level, Dungeon.depth);
        }
        if (Dungeon.hero != null) {
            throw new IllegalStateException("A hero is active but no dungeon floor is loaded. Finish loading the floor or return to the title screen before starting co-op.");
        }

        if (GamesInProgress.selectedClass == null) GamesInProgress.selectedClass = HeroClass.WARRIOR;
        Dungeon.initSeed();
        Dungeon.init();
        Level level = Dungeon.newLevel();
        if (level == null || level.map == null || level.width() < 1 || level.height() < 1) {
            throw new IllegalStateException("The game did not generate a valid dungeon map.");
        }
        int hostPosition = level.entrance();
        if (!isPassable(level, hostPosition)) hostPosition = findPassable(level, -1);
        if (hostPosition < 0) throw new IllegalStateException("Could not find a valid starting cell on the generated floor.");
        // Activate the generated floor and position the host hero on it. This is
        // intentionally retained so the host can enter GameScene after transfer.
        Dungeon.switchLevel(level, hostPosition);
        return serialize(level, Dungeon.depth);
    }

    private static SerializedLevel serialize(Level level, int floor) {
        int hostPosition = Dungeon.hero != null ? Dungeon.hero.pos : level.entrance();
        if (!isPassable(level, hostPosition)) hostPosition = findPassable(level, -1);
        int guestPosition = findPassable(level, hostPosition);
        if (hostPosition < 0 || guestPosition < 0) {
            throw new IllegalStateException("Could not find two valid starting cells on the dungeon floor.");
        }

        Bundle bundle = new Bundle();
        bundle.put("coop_level", level);
        bundle.put("coop_floor", floor);
        bundle.put("coop_branch", Dungeon.branch);
        bundle.put("coop_seed", Dungeon.seed);
        bundle.put("coop_class", GamesInProgress.selectedClass == null ? HeroClass.WARRIOR : GamesInProgress.selectedClass);
        bundle.put("coop_host_position", hostPosition);
        bundle.put("coop_guest_position", guestPosition);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!Bundle.write(bundle, output, true)) {
            throw new IllegalStateException("The game could not serialize the complete dungeon floor.");
        }
        return new SerializedLevel(output.toByteArray(), floor, level.width(), level.height(),
                hostPosition, guestPosition, level.map.length);
    }

    /**
     * Installs the received shared level on the joining phone. This intentionally
     * starts a fresh local run using the host's seed/class/depth, then restores the
     * serialized Level and places the joining hero at the assigned guest position.
     * It must be called on the libGDX application thread.
     */
    public static void installReceivedLevel(byte[] payload) throws Exception {
        if (payload == null || payload.length == 0) {
            throw new IllegalArgumentException("The received dungeon bundle is empty.");
        }
        Bundle bundle = Bundle.read(new java.io.ByteArrayInputStream(payload));
        int floor = bundle.getInt("coop_floor");
        int branch = bundle.getInt("coop_branch");
        int guestPosition = bundle.getInt("coop_guest_position");
        long seed = bundle.getLong("coop_seed");
        HeroClass heroClass = bundle.getEnum("coop_class", HeroClass.class);
        if (floor < 1 || guestPosition < 0) {
            throw new IllegalStateException("The received dungeon metadata is invalid.");
        }
        if (heroClass == null) heroClass = HeroClass.WARRIOR;

        // The user requested that this test display the transferred floor even if
        // it replaces a single-player run. Initialize cleanly before restoring it.
        GamesInProgress.selectedClass = heroClass;
        Dungeon.daily = false;
        Dungeon.seed = seed;
        Dungeon.init();
        Dungeon.depth = floor;
        Dungeon.branch = branch;

        Level level = (Level) bundle.get("coop_level");
        if (level == null || level.map == null || guestPosition >= level.length()
                || level.width() < 1 || level.height() < 1) {
            throw new IllegalStateException("The serialized dungeon level could not be restored.");
        }
        if (!isPassable(level, guestPosition)) {
            guestPosition = findPassable(level, -1);
        }
        if (guestPosition < 0) {
            throw new IllegalStateException("No valid starting cell was found for the joining player.");
        }
        Dungeon.switchLevel(level, guestPosition);
    }

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
            
