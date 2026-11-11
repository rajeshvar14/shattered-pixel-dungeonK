/*
 * Co-op level snapshot serialization and installation.
 */
package com.shatteredpixel.shatteredpixeldungeon.scenes;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.watabou.utils.Bundle;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;

public final class CoOpLevelSnapshot {

    public static final class SerializedMetadata {
    public final int floor;
    public final int branch;
    public final int width;
    public final int height;
    public final int hostPosition;
    public final int guestPosition;
    public final int terrainCells;

    private SerializedMetadata(int floor, int branch, int width, int height,
                               int hostPosition, int guestPosition,
                               int terrainCells) {
        this.floor = floor;
        this.branch = branch;
        this.width = width;
        this.height = height;
        this.hostPosition = hostPosition;
        this.guestPosition = guestPosition;
        this.terrainCells = terrainCells;
    }
}

private static int findGuestStart(Level level, int hostPosition) {
    for (int offset : com.watabou.utils.PathFinder.NEIGHBOURS8) {
        int cell = hostPosition + offset;
        if (cell >= 0 && cell < level.length()
                && level.passable[cell]
                && !level.solid[cell]
                && !level.invalidHeroPos(cell)
                && cell != level.exit()) {
            return cell;
        }
    }
    return -1;
}
    
public static SerializedMetadata readMetadata(byte[] payload) throws Exception {
    if (payload == null || payload.length == 0) {
        throw new IllegalArgumentException("Empty level bundle.");
    }

    Bundle bundle = Bundle.read(new ByteArrayInputStream(payload));
    Level level = (Level) bundle.get("coop_level");
    if (level == null || level.map == null) {
        throw new IllegalStateException("Snapshot has no valid level.");
    }

    int hostPosition = bundle.getInt("coop_host_position");
    int guestPosition = bundle.getInt("coop_guest_position");

    if (hostPosition < 0 || guestPosition < 0
            || hostPosition >= level.length()
            || guestPosition >= level.length()) {
        throw new IllegalStateException("Snapshot positions are out of range.");
    }

    return new SerializedMetadata(
            bundle.getInt("coop_floor"),
            bundle.getInt("coop_branch"),
            level.width(),
            level.height(),
            hostPosition,
            guestPosition,
            level.map.length);
}
    private CoOpLevelSnapshot() {
    }

    public static final class SerializedLevel {
        public final byte[] data;
        public final int floor;
        public final int branch;
        public final int width;
        public final int height;
        public final int hostPosition;
        public final int guestPosition;
        public final int terrainCells;

        private SerializedLevel(byte[] data, int floor, int branch,
                                int width, int height, int hostPosition,
                                int guestPosition, int terrainCells) {
            this.data = data;
            this.floor = floor;
            this.branch = branch;
            this.width = width;
            this.height = height;
            this.hostPosition = hostPosition;
            this.guestPosition = guestPosition;
            this.terrainCells = terrainCells;
        }
    }

    /**
     * Reuses an active floor, or generates and activates the first floor if no
     * run is active. Call from the game/application thread.
     */
    public static SerializedLevel createSerializedLevel() {
        if (Dungeon.level != null && Dungeon.level.map != null) {
            return serialize(Dungeon.level);
        }
        if (Dungeon.hero != null) {
            throw new IllegalStateException(
                    "A hero is active but no dungeon floor is loaded.");
        }

        if (GamesInProgress.selectedClass == null) {
            GamesInProgress.selectedClass = HeroClass.WARRIOR;
        }

        Dungeon.initSeed();
        Dungeon.init();

        Level level = Dungeon.newLevel();
        if (level == null || level.map == null
                || level.width() < 1 || level.height() < 1) {
            throw new IllegalStateException("Could not generate a valid dungeon level.");
        }

        int hostPosition = level.entrance();
        if (!isPassable(level, hostPosition)) {
            hostPosition = findPassable(level, -1);
        }
        if (hostPosition < 0) {
            throw new IllegalStateException("No valid host starting cell was found.");
        }

        Dungeon.switchLevel(level, hostPosition);
        return serialize(level);
    }

    /** Serializes the full Level bundle and chooses distinct starting cells. */
    public static SerializedLevel serialize(Level level) {
        if (level == null || level.map == null) {
            throw new IllegalArgumentException("Cannot serialize an empty level.");
        }

        int hostPosition = Dungeon.hero != null ? Dungeon.hero.pos : level.entrance();
        if (!isPassable(level, hostPosition)) {
            hostPosition = findPassable(level, -1);
        }

        int guestPosition = -1;
        if (Dungeon.comrade != null && Dungeon.comrade.pos != hostPosition
                && isPassable(level, Dungeon.comrade.pos)) {
            guestPosition = Dungeon.comrade.pos;
        }
        if (guestPosition < 0) {
         //   guestPosition = findPassable(level, hostPosition);
              guestPosition = findGuestStart(level, hostPosition);
        }
        if (hostPosition < 0 || guestPosition < 0) {
            throw new IllegalStateException("Could not find two valid starting cells.");
        }

        Bundle bundle = new Bundle();
        bundle.put("coop_level", level);
        bundle.put("coop_floor", Dungeon.depth);
        bundle.put("coop_branch", Dungeon.branch);
        bundle.put("coop_seed", Dungeon.seed);
        bundle.put("coop_class", Dungeon.hero == null
                ? HeroClass.WARRIOR : Dungeon.hero.heroClass);
        bundle.put("coop_host_position", hostPosition);
        bundle.put("coop_guest_position", guestPosition);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!Bundle.write(bundle, output, true)) {
            throw new IllegalStateException("Could not serialize the dungeon level.");
        }

        // Keep the host's Comrade at the selected guest cell on this floor.
        if (Dungeon.comrade != null) {
            Dungeon.setCoOpComradePosition(guestPosition);
        }

        return new SerializedLevel(output.toByteArray(), Dungeon.depth,
                Dungeon.branch, level.width(), level.height(), hostPosition,
                guestPosition, level.map.length);
    }

    /**
     * Installs a received snapshot. Use initializeRun=true only for the first
     * snapshot. Later snapshots preserve the joining player's Hero and run state.
     */
    public static void installReceivedLevel(byte[] payload, boolean initializeRun)
            throws Exception {
installReceivedLevel(payload, initializeRun, null);
            }
public static void installReceivedLevel(byte[] payload, boolean initializeRun,
                                           HeroClass localHeroClass)
           throws Exception {                
        if (payload == null || payload.length == 0) {
            throw new IllegalArgumentException("The received dungeon bundle is empty.");
        }

        Bundle bundle = Bundle.read(new ByteArrayInputStream(payload));
        int floor = bundle.getInt("coop_floor");
        int branch = bundle.getInt("coop_branch");
        int hostPosition = bundle.getInt("coop_host_position");
        int guestPosition = bundle.getInt("coop_guest_position");
        long seed = bundle.getLong("coop_seed");
        HeroClass heroClass = bundle.getEnum("coop_class", HeroClass.class);

        if (floor < 1) {
            throw new IllegalStateException("Invalid floor number in snapshot.");
        }
        if (heroClass == null) heroClass = HeroClass.WARRIOR;

        Level level = (Level) bundle.get("coop_level");
        if (level == null || level.map == null
                || level.width() < 1 || level.height() < 1
                || hostPosition < 0 || guestPosition < 0
                || hostPosition >= level.length()
                || guestPosition >= level.length()) {
            throw new IllegalStateException("Invalid level or starting positions.");
        }

        if (!isPassable(level, hostPosition)) {
            hostPosition = findPassable(level, -1);
        }
        if (!isPassable(level, guestPosition)
                || guestPosition == hostPosition) {
          //  guestPosition = findPassable(level, hostPosition);
            guestPosition = findGuestStart(level, hostPosition);
        }
        if (hostPosition < 0 || guestPosition < 0) {
            throw new IllegalStateException("No valid player starting cells were found.");
        }

        if (initializeRun) {
            GamesInProgress.selectedClass = localHeroClass == null ? heroClass : localHeroClass;
            Dungeon.daily = false;
            Dungeon.seed = seed;
            Dungeon.init();
        } else if (Dungeon.hero == null) {
            throw new IllegalStateException(
                    "Cannot update a floor before the joining run is initialized.");
        }

        Dungeon.seed = seed;
        Dungeon.depth = floor;
        Dungeon.branch = branch;
       if (!GameScene.waitForActorsBeforeLevelInstall()) {
          throw new IllegalStateException(
                 "Could not stop actor processing before installing the co-op floor.");
        }
       Actor.clear();
         Dungeon.switchLevel(level, guestPosition);
        Dungeon.switchLevel(level, guestPosition);
        Dungeon.setCoOpComradePosition(hostPosition);
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
