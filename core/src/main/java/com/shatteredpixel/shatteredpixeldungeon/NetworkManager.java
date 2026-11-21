package com.shatteredpixel.shatteredpixeldungeon;

public class NetworkManager {

    // 'volatile' ensures the main game thread sees the new dispatcher immediately
    private static volatile LanStreamDispatcher dispatcher;

    // Called by your background connection thread once the Socket connects
    public static void setDispatcher(LanStreamDispatcher activeDispatcher) {
        if (dispatcher != null) {
            dispatcher.stop(); // Clean up any old connection first
        }
        dispatcher = activeDispatcher;
    }

    // Called anywhere in Shattered Pixel Dungeon (GameScene, Mob, Level)
    public static LanStreamDispatcher getDispatcher() {
        return dispatcher;
    }

    public static boolean isConnected() {
        return dispatcher != null;
    }

    // Call when exiting to the main menu or closing the game
    public static void disconnect() {
        if (dispatcher != null) {
            dispatcher.stop();
            dispatcher = null;
        }
    }
}
