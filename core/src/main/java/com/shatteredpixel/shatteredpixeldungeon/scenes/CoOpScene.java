/*
 * Step 3C: transfer and display the same serialized dungeon level on both phones.
 * The host remains authoritative; turn/movement synchronization is a later step.
 */
package com.shatteredpixel.shatteredpixeldungeon.scenes;

import com.badlogic.gdx.Gdx;
import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndOptions;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndTextInput;
import com.watabou.utils.Bundle;

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
    private static final int SOCKET_TIMEOUT_MS = 20000;

    private static volatile ServerSocket hostServer;
    private static volatile Socket activeSocket;
    private static volatile boolean cancelled;
    private WndOptions activeOptions;

    @Override
    public void create() {
        super.create();
        showMenu();
    }

    private void showMenu() {
        showOptions(new WndOptions(
                "Co-op Multiplayer",
                "Co-op level-display test: host sends the complete dungeon floor. Both phones restore and display that same floor, with the host and joining player placed at separate starting cells. Movement and actions are not synchronized in this build. Both phones must use the same Wi-Fi network or hotspot.",
                "Host LAN Game", "Join LAN Game", "Back to Title") {
            @Override protected void onSelect(int index) {
                if (index == 0) startHost();
                else if (index == 1) askForHostAddress();
                else {
                    stopNetworking();
                    ShatteredPixelDungeon.switchScene(TitleScene.class);
                }
            }
        });
    }

    private void startHost() {
        stopNetworking();
        cancelled = false;
        final CoOpLevelSnapshot.SerializedLevel snapshot;
        try {
            showMessage("Preparing Dungeon", "Serializing the complete dungeon floor with the game's Bundle system…", "Please wait", null);
            snapshot = CoOpLevelSnapshot.createSerializedLevel();
            if (snapshot.data == null || snapshot.data.length == 0) throw new IllegalStateException("The serialized dungeon bundle is empty.");
        } catch (final Exception e) {
            showMessage("Dungeon Serialization Failed", readableError(e)
                    + "\n\nNo co-op session was started. Your normal single-player mode has not been changed.",
                    "Back to Co-op", new Runnable() {
                        @Override public void run() { ShatteredPixelDungeon.switchScene(CoOpScene.class); }
                    });
            return;
        }

        showMessage("Starting Host", "Complete dungeon bundle prepared (" + snapshot.data.length
                + " bytes compressed). Starting the local Wi-Fi host…", "Cancel", new Runnable() {
            @Override public void run() {
                stopNetworking();
                ShatteredPixelDungeon.switchScene(CoOpScene.class);
            }
        });

        Thread thread = new Thread(new Runnable() {
            @Override public void run() {
                ServerSocket server = null;
                Socket socket = null;
                try {
                    server = new ServerSocket(PORT);
                    hostServer = server;
                    if (cancelled) return;
                    final String address = findLocalIPv4();
                    postStatus("Waiting for Player", "Connect the second phone to this same Wi-Fi/hotspot, then choose Join LAN Game and enter:\n\n"
                            + address + "\nPort: " + PORT + "\n\nA complete compressed dungeon bundle is ready (" + snapshot.data.length + " bytes).", "Cancel", new Runnable() {
                        @Override public void run() {
                            stopNetworking();
                            ShatteredPixelDungeon.switchScene(CoOpScene.class);
                        }
                    });

                    socket = server.accept();
                    activeSocket = socket;
                    socket.setSoTimeout(SOCKET_TIMEOUT_MS);
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    out.writeInt(snapshot.data.length);
                    out.write(snapshot.data);
                    out.flush();
                    String reply = in.readUTF();
                    String expected = "BUNDLE_RECEIVED|" + snapshot.floor + "|" + snapshot.width + "|" + snapshot.height
                            + "|" + snapshot.terrainCells + "|" + snapshot.data.length;
                    if (expected.equals(reply)) {
                        postStatus("Shared Dungeon Ready", "Success! Both phones exchanged the complete dungeon data.\n\n"
                                + "Floor: " + snapshot.floor + "\nDimensions: " + snapshot.width + " × " + snapshot.height
                                + "\nTerrain cells: " + snapshot.terrainCells + "\nCompressed bundle: " + snapshot.data.length
                                + " bytes\nHost start cell: " + snapshot.hostPosition + "\nJoining start cell: " + snapshot.guestPosition
                                + "\n\nTap Enter Shared Dungeon to display the floor on the host phone. Both character objects are now registered, but movement/actions are not synchronized yet.", "Enter Shared Dungeon", new Runnable() {
                            @Override public void run() {
                                stopNetworking();
                                com.shatteredpixel.shatteredpixeldungeon.Dungeon.createCoOpComrade(snapshot.guestPosition);
                                ShatteredPixelDungeon.switchScene(GameScene.class);
                            }
                        });
                    } else {
                        postStatus("Bundle Validation Failed", "The joining phone connected, but its confirmation did not match the sent bundle.\nReceived: "
                                + (reply == null ? "no reply" : reply), "Back to Co-op", new Runnable() {
                            @Override public void run() { stopNetworking(); ShatteredPixelDungeon.switchScene(CoOpScene.class); }
                        });
                    }
                } catch (final Exception e) {
                    if (!cancelled) postStatus("Host Connection Failed", readableError(e)
                            + "\n\nCheck that both phones are on the same Wi-Fi/hotspot and try again.", "Back to Co-op", new Runnable() {
                        @Override public void run() { stopNetworking(); ShatteredPixelDungeon.switchScene(CoOpScene.class); }
                    });
                } finally {
                    closeSocket(socket);
                    if (activeSocket == socket) activeSocket = null;
                    closeServer(server);
                    if (hostServer == server) hostServer = null;
                }
            }
        }, "CoOp-LAN-Host");
        thread.setDaemon(true);
        thread.start();
    }

    private void askForHostAddress() {
        addToFront(new WndTextInput("Join LAN Game", "Enter the host phone's local IP address. Both phones must be on the same Wi-Fi/hotspot.", "192.168.43.1", 64, false, "Connect", "Cancel") {
            @Override public void onSelect(boolean positive, String text) {
                if (positive) {
                    final String host = text == null ? "" : text.trim();
                    if (host.length() == 0) {
                        showMessage("Host Address Needed", "Enter the IP address shown on the host phone.", "OK", new Runnable() {
                            @Override public void run() { ShatteredPixelDungeon.switchScene(CoOpScene.class); }
                        });
                    } else startJoin(host);
                } else ShatteredPixelDungeon.switchScene(CoOpScene.class);
            }
        });
    }

    private void startJoin(final String host) {
        stopNetworking();
        cancelled = false;
        showMessage("Connecting", "Connecting to " + host + ":" + PORT + " and waiting for the complete dungeon bundle…", "Cancel", new Runnable() {
            @Override public void run() {
                cancelled = true;
                closeSocket(activeSocket);
                ShatteredPixelDungeon.switchScene(CoOpScene.class);
            }
        });

        Thread thread = new Thread(new Runnable() {
            @Override public void run() {
                Socket socket = null;
                try {
                    socket = new Socket();
                    activeSocket = socket;
                    socket.connect(new java.net.InetSocketAddress(host, PORT), CONNECT_TIMEOUT_MS);
                    socket.setSoTimeout(SOCKET_TIMEOUT_MS);
                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    int length = in.readInt();
                    if (length <= 0 || length > 16 * 1024 * 1024) {
                        throw new IllegalStateException("The host sent an invalid bundle size (" + length + " bytes). Maximum allowed is 16 MB.");
                    }
                    byte[] payload = new byte[length];
                    in.readFully(payload);
                    Bundle received = Bundle.read(new ByteArrayInputStream(payload));
                    Bundle levelBundle = received.getBundle("coop_level");
                    int width = levelBundle.getInt("width");
                    int height = levelBundle.getInt("height");
                    int[] map = levelBundle.getIntArray("map");
                    final int floor = received.getInt("coop_floor");
                    final int hostPosition = received.getInt("coop_host_position");
                    final int guestPosition = received.getInt("coop_guest_position");
                    if (width < 1 || height < 1 || map == null || map.length != width * height
                            || hostPosition < 0 || guestPosition < 0 || hostPosition >= map.length || guestPosition >= map.length) {
                        throw new IllegalStateException("The serialized Level Bundle has invalid dimensions, terrain, or starting positions.");
                    }
                    String[] required = new String[] {"heaps", "mobs", "plants", "traps", "blobs", "transitions", "customTiles", "customTerrain", "customWalls"};
                    StringBuilder missing = new StringBuilder();
                    for (String key : required) {
                        if (!levelBundle.contains(key)) {
                            if (missing.length() > 0) missing.append(", ");
                            missing.append(key);
                        }
                    }
                    if (missing.length() > 0) throw new IllegalStateException("The full Level Bundle is missing fields: " + missing);
                    final int finalWidth = width;
                    final int finalHeight = height;
                    final int finalCells = map.length;
                    final int finalBytes = payload.length;
                    out.writeUTF("BUNDLE_RECEIVED|" + floor + "|" + width + "|" + height + "|" + map.length + "|" + payload.length);
                    out.flush();
                    // Level restoration and Dungeon global-state changes must occur on
                    // the libGDX application thread, not on the socket worker thread.
                    if (Gdx.app == null) throw new IllegalStateException("The game application is not ready to restore the shared level.");
                    Gdx.app.postRunnable(new Runnable() {
                        @Override public void run() {
                            if (cancelled || !(ShatteredPixelDungeon.scene() instanceof CoOpScene)) return;
                            try {
                                CoOpLevelSnapshot.installReceivedLevel(payload);
                                showMessage("Shared Dungeon Ready", "Success! The joining phone restored the host's complete dungeon level.\n\n"
                                        + "Floor: " + floor + "\nDimensions: " + finalWidth + " × " + finalHeight
                                        + "\nTerrain cells: " + finalCells + "\nCompressed bundle: " + finalBytes
                                        + " bytes\nHost start cell: " + hostPosition + "\nYour start cell: " + guestPosition
                                        + "\n\nTap Enter Shared Dungeon to display the same floor. Both character objects are now registered, but movement/actions are not synchronized yet.", "Enter Shared Dungeon", new Runnable() {
                                    @Override public void run() {
                                        stopNetworking();
                                        com.shatteredpixel.shatteredpixeldungeon.Dungeon.createCoOpComrade(hostPosition);
                                        ShatteredPixelDungeon.switchScene(GameScene.class);
                                    }
                                });
                            } catch (final Exception restoreError) {
                                showMessage("Level Restore Failed", readableError(restoreError)
                                        + "\n\nThe bundle arrived, but the game could not restore it. Send the build log or screenshot so we can fix the restore stage.", "Back to Co-op", new Runnable() {
                                    @Override public void run() { stopNetworking(); ShatteredPixelDungeon.switchScene(CoOpScene.class); }
                                });
                            }
                        }
                    });
                } catch (final Exception e) {
                    if (!cancelled) postStatus("Join Failed", readableError(e)
                            + "\n\nCheck the host IP, Wi-Fi/hotspot connection, and try again.", "Back to Co-op", new Runnable() {
                        @Override public void run() { stopNetworking(); ShatteredPixelDungeon.switchScene(CoOpScene.class); }
                    });
                } finally {
                    closeSocket(socket);
                    if (activeSocket == socket) activeSocket = null;
                }
            }
        }, "CoOp-LAN-Join");
        thread.setDaemon(true);
        thread.start();
    }

    private void showOptions(WndOptions options) {
        if (activeOptions != null) activeOptions.hide();
        activeOptions = options;
        addToFront(options);
    }

    private void showMessage(String title, String message, String button, final Runnable action) {
        showOptions(new WndOptions(title, message, button) {
            @Override protected void onSelect(int index) { if (action != null) action.run(); }
        });
    }

    private void postStatus(final String title, final String message, final String button, final Runnable action) {
        if (Gdx.app == null) return;
        Gdx.app.postRunnable(new Runnable() {
            @Override public void run() {
                if (!cancelled && ShatteredPixelDungeon.scene() instanceof CoOpScene) {
                    showMessage(title, message, button, action);
                }
            }
        });
    }

    private static String findLocalIPv4() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback()) continue;
                Enumeration<InetAddress> addresses = network.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress() && !address.isLinkLocalAddress()) {
                        String value = address.getHostAddress();
                        if (value.startsWith("192.168.") || value.startsWith("10.") || value.startsWith("172.")) return value;
                    }
                }
            }
        } catch (SocketException ignored) { }
        return "192.168.43.1";
    }

    private static String readableError(Exception e) {
        if (e instanceof java.net.ConnectException) return "Could not connect. The host may not be ready, the IP may be incorrect, or the hotspot may block device-to-device traffic.";
        if (e instanceof java.net.SocketTimeoutException) return "Connection timed out. Check the host IP and that the host is waiting for a player.";
        if (e instanceof java.net.BindException) return "Could not start the host port. Close any previous co-op session and try again.";
        String message = e.getMessage();
        return message == null || message.length() == 0 ? "An error occurred." : message;
    }

    private static void closeSocket(Socket socket) {
        if (socket != null) try { socket.close(); } catch (Exception ignored) { }
    }

    private static void closeServer(ServerSocket server) {
        if (server != null) try { server.close(); } catch (Exception ignored) { }
    }

    private static void stopNetworking() {
        cancelled = true;
        closeSocket(activeSocket);
        activeSocket = null;
        closeServer(hostServer);
        hostServer = null;
    }

    @Override protected void onBackPressed() {
        stopNetworking();
        ShatteredPixelDungeon.switchScene(TitleScene.class);
    }
            }
                                   
