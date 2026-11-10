package com.shatteredpixel.shatteredpixeldungeon.scenes;

import com.badlogic.gdx.Gdx;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndOptions;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndTextInput;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;

public class CoOpScene extends PixelScene {

    private static final int PORT = 47832;
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int MAX_BUNDLE_BYTES = 16 * 1024 * 1024;

    private static final int FRAME_STATE = 1;
    private static final int FRAME_LEVEL = 2;

    private static volatile ServerSocket hostServer;
    private static volatile Socket activeSocket;
    private static volatile DataInputStream sessionIn;
    private static volatile DataOutputStream sessionOut;
    private static volatile boolean sessionActive;
    private static volatile boolean hostRole;
    private static volatile int sendSequence;
    private static volatile int receiveSequence;
    private static volatile int localTargetCell = -1;

    private volatile boolean cancelled;
    private WndOptions activeOptions;
private static volatile boolean pausedForResume;
    @Override
    public void create() {
        super.create();
        showMenu();
    }

    private void showMenu() {
        showOptions(new WndOptions(
                "Co-op Multiplayer",
                "Shares the host's dungeon floor and continuously exchanges both players' positions and selected target cells. Target cells are stored only; they are not executed. Both phones must use the same Wi-Fi network or hotspot.",
                "New Host Game","Resume Host Game" "Join LAN Game", "Back to Title") );
            @Override
            protected void onSelect(int index) {
                if (index == 0) startHost(false);
                else if (index == 1) starthost(true);
                else if (index == 2) askForHostAddress();
                else {
                    stopNetworking();
                    ShatteredPixelDungeon.switchScene(TitleScene.class);
                }
            }
        
    }

    private void startHost(boolean resumeSavedRun) {
        stopNetworking();
        cancelled = false;

        final CoOpLevelSnapshot.SerializedLevel snapshot;
        try {
        
               if (resumeSavedRun) {
               if (!CoOpRunState.hasSavedRun()) {
                   throw new IllegalStateException("No saved co-op run was found.");
               }
               CoOpRunState.restore();
               } else {
                CoOpRunState.startFresh();
            }
            snapshot = CoOpLevelSnapshot.serialize(Dungeon.level);
        } catch (Exception e) {
            showMessage("Dungeon Setup Failed", readableError(e), "Back", null);
            return;
        }

        showMessage("Starting Host",
                "Waiting for a joining phone. Initial level bundle: "
                        + snapshot.data.length + " bytes.",
                "Cancel", new Runnable() {
                    @Override
                    public void run() {
                        stopNetworking();
                        ShatteredPixelDungeon.switchScene(CoOpScene.class);
                    }
                });

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                ServerSocket server = null;
                Socket socket = null;
                boolean keepSession = false;
                try {
                    server = new ServerSocket(PORT);
                    hostServer = server;

                    postStatus("Waiting for Player",
                            "Connect the other phone to the same Wi-Fi/hotspot.\n\n"
                                    + "Host IP: " + findLocalIPv4()
                                    + "\nPort: " + PORT,
                            "Cancel", new Runnable() {
                                @Override
                                public void run() {
                                    stopNetworking();
                                    ShatteredPixelDungeon.switchScene(CoOpScene.class);
                                }
                            });

                    socket = server.accept();
                    if (cancelled) return;

                    socket.setSoTimeout(0);
                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());

                    out.writeInt(snapshot.data.length);
                    out.write(snapshot.data);
                    out.flush();

                    String reply = in.readUTF();
                    String expected = "BUNDLE_RECEIVED|" + snapshot.floor
                            + "|" + snapshot.branch + "|" + snapshot.width
                            + "|" + snapshot.height + "|" + snapshot.terrainCells
                            + "|" + snapshot.data.length;
                    if (!expected.equals(reply)) {
                        throw new IllegalStateException(
                                "The joining phone did not confirm the initial level bundle.");
                    }

                    beginSession(socket, in, out, true);
                    keepSession = true;

                    final int guestPosition = snapshot.guestPosition;
                    postStatus("Shared Dungeon Ready",
                            "Initial floor transferred. Tap Enter Shared Dungeon "
                                    + "to begin the co-op display.",
                            "Enter Shared Dungeon", new Runnable() {
                                @Override
                                public void run() {
                                    Dungeon.setCoOpComradePosition(guestPosition);
                                    ShatteredPixelDungeon.switchScene(GameScene.class);
                                }
                            });

                    readHostFrames(in);
                } catch (final Exception e) {
                    if (!cancelled && !pausedForResume) {
                        postStatus("Host Connection Failed", readableError(e),
                                "Back", new Runnable() {
                                    @Override
                                    public void run() {
                                        stopNetworking();
                                        ShatteredPixelDungeon.switchScene(CoOpScene.class);
                                    }
                                });
                    }
                } finally {
                    closeServer(server);
                    if (hostServer == server) hostServer = null;
                    if (!keepSession) closeSocket(socket);
                }
            }
        }, "CoOp-LAN-Host");

        thread.setDaemon(true);
        thread.start();
    }

    private void askForHostAddress() {
        addToFront(new WndTextInput(
                "Join LAN Game",
                "Enter the host phone's local IP address. Both phones must be on the same Wi-Fi/hotspot.",
                "192.168.43.1", 64, false, "Connect", "Cancel") {
            @Override
            public void onSelect(boolean positive, String text) {
                if (positive) {
                    final String host = text == null ? "" : text.trim();
                    if (host.length() == 0) {
                        showMessage("Host Address Needed",
                                "Enter the IP shown on the host phone.", "OK", null);
                    } else {
                        startJoin(host);
                    }
                }
            }
        });
    }

    private void startJoin(final String host) {
        stopNetworking();
        cancelled = false;

        showMessage("Connecting",
                "Connecting to " + host + ":" + PORT + "…",
                "Cancel", new Runnable() {
                    @Override
                    public void run() {
                        stopNetworking();
                        ShatteredPixelDungeon.switchScene(CoOpScene.class);
                    }
                });

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                Socket socket = null;
                boolean keepSession = false;
                try {
                    socket = new Socket();
                    activeSocket = socket;
                    socket.connect(new java.net.InetSocketAddress(host, PORT),
                            CONNECT_TIMEOUT_MS);
                    socket.setSoTimeout(0);

                    final DataInputStream in =
                            new DataInputStream(socket.getInputStream());
                    final DataOutputStream out =
                            new DataOutputStream(socket.getOutputStream());

                    int length = in.readInt();
                    if (length <= 0 || length > MAX_BUNDLE_BYTES) {
                        throw new IllegalStateException(
                                "Host sent an invalid bundle size: " + length);
                    }
                    final byte[] payload = new byte[length];
                    in.readFully(payload);

                    final CoOpLevelSnapshot.SerializedMetadata metadata =
                            CoOpLevelSnapshot.readMetadata(payload);

                    out.writeUTF("BUNDLE_RECEIVED|" + metadata.floor
                            + "|" + metadata.branch + "|" + metadata.width
                            + "|" + metadata.height + "|" + metadata.terrainCells
                            + "|" + payload.length);
                    out.flush();

                    beginSession(socket, in, out, false);
                    keepSession = true;

                    if (Gdx.app == null) {
                        throw new IllegalStateException(
                                "Game application is not ready to install the level.");
                    }
                    Gdx.app.postRunnable(new Runnable() {
                        @Override
                        public void run() {

                             if (!sessionActive) return;
                             try {
                                if (CoOpRunState.hasSavedRun()) {
                                     CoOpRunState.restore();
                                     CoOpLevelSnapshot.installReceivedLevel(payload, false);
                                 } else {
                                    CoOpRunState.selectCoOpSlotPreservingCurrentRun();
                                    CoOpLevelSnapshot.installReceivedLevel(payload, true);
                                }
                                
                                showMessage("Shared Dungeon Ready",
                                        "Floor " + metadata.floor + " received. "
                                                + "Tap Enter Shared Dungeon.",
                                        "Enter Shared Dungeon", new Runnable() {
                                            @Override
                                            public void run() {
                                                Dungeon.setCoOpComradePosition(
                                                        metadata.hostPosition);
                                                ShatteredPixelDungeon.switchScene(
                                                        GameScene.class);
                                            }
                                        });
                            } catch (Exception e) {
                                showMessage("Level Restore Failed",
                                        readableError(e), "Back", new Runnable() {
                                            @Override
                                            public void run() {
                                                stopNetworking();
                                                ShatteredPixelDungeon.switchScene(
                                                        CoOpScene.class);
                                            }
                                        });
                            }
                        }
                    });

                    readJoinFrames(in);
                } catch (final Exception e) {
                    if (!cancelled && !pausedForResume) {
                        postStatus("Join Failed", readableError(e), "Back",
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        stopNetworking();
                                        ShatteredPixelDungeon.switchScene(
                                                CoOpScene.class);
                                    }
                                });
                    }
                } finally {
                    if (!keepSession) closeSocket(socket);
                }
            }
        }, "CoOp-LAN-Join");

        thread.setDaemon(true);
        thread.start();
    }

    private static synchronized void beginSession(Socket socket,
                                                  DataInputStream in,
                                                  DataOutputStream out,
                                                  boolean isHost) {
        activeSocket = socket;
        pausedForResume = false;
        sessionIn = in;
        sessionOut = out;
        hostRole = isHost;
        sessionActive = true;
        sendSequence = 0;
        receiveSequence = 0;
        localTargetCell = -1;
    }

    /**
     * Called periodically by GameScene with the local player's latest state.
     * The remote side stores this state on its Comrade; it does not execute it.
     */
    public static void publishLocalState(int position, int targetCell) {
        if (!sessionActive || Dungeon.level == null
                || position < 0 || position >= Dungeon.level.length()) {
            return;
        }

        DataOutputStream out = sessionOut;
        if (out == null) return;

        try {
            synchronized (out) {
                out.writeByte(FRAME_STATE);
                out.writeInt(++sendSequence);
                out.writeInt(Dungeon.depth);
                out.writeInt(Dungeon.branch);
                out.writeInt(position);
                out.writeInt(targetCell);
                out.flush();
            }
        } catch (Exception e) {
            endSession();
        }
    }

    /** Records the most recently selected cell for periodic transmission. */
    public static void noteLocalTargetCell(int cell) {
        localTargetCell = cell;
    }

    public static int localTargetCell() {
        return localTargetCell;
    }

    /**
     * Called after the host switches floors. Snapshot serialization and socket
     * writes are queued onto the application thread.
     */
    public static void onHostLevelChanged() {
        if (!sessionActive || !hostRole || Gdx.app == null) return;

        Gdx.app.postRunnable(new Runnable() {
            @Override
            public void run() {
                if (!sessionActive || !hostRole || Dungeon.level == null) return;
                try {
                    CoOpLevelSnapshot.SerializedLevel snapshot =
                            CoOpLevelSnapshot.serialize(Dungeon.level);
                    sendLevelSnapshot(snapshot.data);
                } catch (Exception e) {
                    endSession();
                }
            }
        });
    }

    private static void sendLevelSnapshot(byte[] payload) throws Exception {
        DataOutputStream out = sessionOut;
        if (out == null || payload == null || payload.length == 0
                || payload.length > MAX_BUNDLE_BYTES) {
            throw new IllegalStateException("Invalid level snapshot.");
        }

        synchronized (out) {
            out.writeByte(FRAME_LEVEL);
            out.writeInt(payload.length);
            out.write(payload);
            out.flush();
        }
    }

    private void readHostFrames(DataInputStream in) throws Exception {
        while (sessionActive && !cancelled) {
            int type = in.readUnsignedByte();
            if (type == FRAME_STATE) {
                receiveState(in);
            } else {
                throw new IllegalStateException("Unexpected frame from joining phone.");
            }
        }
    }

    private void readJoinFrames(DataInputStream in) throws Exception {
        while (sessionActive && !cancelled) {
            int type = in.readUnsignedByte();
            if (type == FRAME_STATE) {
                receiveState(in);
            } else if (type == FRAME_LEVEL) {
                receiveLevel(in);
            } else {
                throw new IllegalStateException("Unknown co-op frame type.");
            }
        }
    }

    private void receiveState(DataInputStream in) throws Exception {
        final int sequence = in.readInt();
        final int floor = in.readInt();
        final int branch = in.readInt();
        final int position = in.readInt();
        final int targetCell = in.readInt();

        if (sequence <= receiveSequence) return;
        receiveSequence = sequence;

        if (Gdx.app == null) return;
        Gdx.app.postRunnable(new Runnable() {
            @Override
            public void run() {
                if (!sessionActive || Dungeon.level == null
                        || floor != Dungeon.depth || branch != Dungeon.branch
                        || position < 0 || position >= Dungeon.level.length()
                        || targetCell < -1
                        || targetCell >= Dungeon.level.length()) {
                    return;
                }

                if (Dungeon.comrade == null) {
                    Dungeon.createCoOpComrade(position);
                } else {
                    Dungeon.comrade.pos = position;
                    Dungeon.comrade.targetCell = targetCell;
                    GameScene.syncComradeSprite();
                }
            }
        });
    }

    private void receiveLevel(DataInputStream in) throws Exception {
        int length = in.readInt();
        if (length <= 0 || length > MAX_BUNDLE_BYTES) {
            throw new IllegalStateException("Invalid level update size: " + length);
        }

        final byte[] payload = new byte[length];
        in.readFully(payload);

        final CoOpLevelSnapshot.SerializedMetadata metadata =
                CoOpLevelSnapshot.readMetadata(payload);

        if (Gdx.app == null) return;
        Gdx.app.postRunnable(new Runnable() {
            @Override
            public void run() {
                if (!sessionActive || hostRole) return;
                try {
                    CoOpLevelSnapshot.installReceivedLevel(payload, false);
                    // Recreate the scene so its map and sprites reflect the new floor.
                    ShatteredPixelDungeon.switchScene(GameScene.class);
                } catch (Exception e) {
                    endSession();
                }
            }
        });
    }

    private void showOptions(WndOptions options) {
        if (activeOptions != null) activeOptions.hide();
        activeOptions = options;
        addToFront(options);
    }

    private void showMessage(String title, String message,
                             String button, final Runnable action) {
        showOptions(new WndOptions(title, message, button) {
            @Override
            protected void onSelect(int index) {
                if (action != null) action.run();
            }
        });
    }

    private void postStatus(final String title, final String message,
                            final String button, final Runnable action) {
        if (Gdx.app == null) return;
        Gdx.app.postRunnable(new Runnable() {
            @Override
            public void run() {
                if (!cancelled && ShatteredPixelDungeon.scene() instanceof CoOpScene) {
                    showMessage(title, message, button, action);
                }
            }
        });
    }

    private static String findLocalIPv4() {
        try {
            Enumeration<NetworkInterface> interfaces =
                    NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback()) continue;

                Enumeration<InetAddress> addresses = network.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address
                            && !address.isLoopbackAddress()
                            && !address.isLinkLocalAddress()) {
                        String value = address.getHostAddress();
                        if (value.startsWith("192.168.")
                                || value.startsWith("10.")
                                || value.startsWith("172.")) {
                            return value;
                        }
                    }
                }
            }
        } catch (SocketException ignored) {
        }
        return "192.168.43.1";
    }

    private static String readableError(Exception e) {
        if (e instanceof java.net.ConnectException) {
            return "Could not connect. Check the host IP and Wi-Fi/hotspot.";
        }
        if (e instanceof java.net.SocketTimeoutException) {
            return "Connection timed out.";
        }
        if (e instanceof java.net.BindException) {
            return "Could not open the host port. Close any old co-op session and retry.";
        }
        String message = e.getMessage();
        return message == null || message.length() == 0
                ? "A network or level-transfer error occurred." : message;
    }

    public static boolean isSessionActive() {
    return sessionActive;
}

public static void pauseForResume() {
    pausedForResume = true;
    closeServer(hostServer);
    hostServer = null;
    endSession();
}
    private static void endSession() {
        sessionActive = false;
        closeSocket(activeSocket);
        activeSocket = null;
        sessionIn = null;
        sessionOut = null;
    }

    private static void closeSocket(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static void closeServer(ServerSocket server) {
        if (server != null) {
            try {
                server.close();
            } catch (Exception ignored) {
            }
        }
    }

    private void stopNetworking() {
        cancelled = true;
        endSession();
        closeServer(hostServer);
        hostServer = null;
    }

    @Override
    protected void onBackPressed() {
        stopNetworking();
        ShatteredPixelDungeon.switchScene(TitleScene.class);
    }
}
