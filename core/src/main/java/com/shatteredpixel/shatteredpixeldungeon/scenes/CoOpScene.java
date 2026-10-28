/*
 * Step 2: real dungeon terrain transfer over local Wi-Fi.
 * The host shares the current real floor when one is loaded, or generates a test floor when no run is active, and sends its terrain map; the joining
 * phone validates the received dimensions, cells, and checksum. This does not
 * yet display a playable dungeon or synchronize movement/turns.
 */
package com.shatteredpixel.shatteredpixeldungeon.scenes;

import com.badlogic.gdx.Gdx;
import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndOptions;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndTextInput;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
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
                "Co-op development test: the host shares terrain from its currently loaded floor, or generates a test floor if no game is active. Both phones must use the same Wi-Fi network or hotspot.",
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
        final String snapshot;
        final CoOpProtocol.LevelSnapshot parsed;
        try {
            showMessage("Preparing Dungeon", "Preparing a real dungeon floor for the map-sharing test…", "Please wait", null);
            snapshot = CoOpLevelSnapshot.createHostLevelSnapshot();
            parsed = CoOpProtocol.parseLevelSnapshot(snapshot);
            if (parsed == null) throw new IllegalStateException("The generated map could not be encoded correctly.");
        } catch (final Exception e) {
            showMessage("Dungeon Generation Failed", readableError(e)
                    + "\n\nNo co-op session was started. Your normal single-player mode has not been changed.",
                    "Back to Co-op", new Runnable() {
                        @Override public void run() { ShatteredPixelDungeon.switchScene(CoOpScene.class); }
                    });
            return;
        }

        showMessage("Starting Host", "Dungeon terrain prepared (" + parsed.width + " × " + parsed.height
                + "). Starting the local Wi-Fi host…", "Cancel", new Runnable() {
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
                            + address + "\nPort: " + PORT + "\n\nA real dungeon map is ready. Waiting for the other phone…", "Cancel", new Runnable() {
                        @Override public void run() {
                            stopNetworking();
                            ShatteredPixelDungeon.switchScene(CoOpScene.class);
                        }
                    });

                    socket = server.accept();
                    activeSocket = socket;
                    socket.setSoTimeout(SOCKET_TIMEOUT_MS);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    out.println(snapshot);
                    String reply = in.readLine();
                    String expected = "MAP_RECEIVED|" + parsed.width + "|" + parsed.height + "|"
                            + parsed.map.length + "|" + checksum(parsed.map);
                    if (expected.equals(reply)) {
                        postStatus("Real Map Shared", "Success! The joining phone received and validated the host's REAL generated dungeon terrain.\n\n"
                                + "Dungeon floor: " + parsed.level + "\nMap dimensions: " + parsed.width + " × " + parsed.height
                                + "\nTerrain cells: " + parsed.map.length + "\nMap checksum: " + checksum(parsed.map)
                                + "\n\nThis confirms map transfer only; playable shared movement comes next.", "Back to Co-op", new Runnable() {
                            @Override public void run() {
                                stopNetworking();
                                ShatteredPixelDungeon.switchScene(CoOpScene.class);
                            }
                        });
                    } else {
                        postStatus("Map Validation Failed", "The joining phone connected, but its confirmation did not match the sent map.\nReceived: "
                                + (reply == null ? "no reply" : reply), "Back to Co-op", new Runnable() {
                            @Override public void run() {
                                stopNetworking();
                                ShatteredPixelDungeon.switchScene(CoOpScene.class);
                            }
                        });
                    }
                } catch (final Exception e) {
                    if (!cancelled) postStatus("Host Connection Failed", readableError(e)
                            + "\n\nCheck that both phones are on the same Wi-Fi/hotspot and try again.", "Back to Co-op", new Runnable() {
                        @Override public void run() {
                            stopNetworking();
                            ShatteredPixelDungeon.switchScene(CoOpScene.class);
                        }
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
        showMessage("Connecting", "Connecting to " + host + ":" + PORT + " and waiting for the real dungeon map…", "Cancel", new Runnable() {
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
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    String line = in.readLine();
                    final CoOpProtocol.LevelSnapshot map = CoOpProtocol.parseLevelSnapshot(line);
                    if (map == null) throw new IllegalStateException("The host sent invalid or incomplete terrain-map data.");
                    final long sum = checksum(map.map);
                    out.println("MAP_RECEIVED|" + map.width + "|" + map.height + "|" + map.map.length + "|" + sum);
                    postStatus("Real Map Received", "The host's generated dungeon terrain was received and validated.\n\n"
                            + "Dungeon floor: " + map.level + "\nMap dimensions: " + map.width + " × " + map.height
                            + "\nTerrain cells: " + map.map.length + "\nMap checksum: " + sum
                            + "\n\nThis test validates map transfer; it does not yet display a playable dungeon.", "Back to Co-op", new Runnable() {
                        @Override public void run() {
                            stopNetworking();
                            ShatteredPixelDungeon.switchScene(CoOpScene.class);
                        }
                    });
                } catch (final Exception e) {
                    if (!cancelled) postStatus("Join Failed", readableError(e)
                            + "\n\nCheck the host IP, Wi-Fi/hotspot connection, and try again.", "Back to Co-op", new Runnable() {
                        @Override public void run() {
                            stopNetworking();
                            ShatteredPixelDungeon.switchScene(CoOpScene.class);
                        }
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

    private static long checksum(int[] map) {
        long value = 1;
        for (int cell : map) value = (value * 31 + cell) & 0x7fffffffL;
        return value;
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
                        
