/*
 * Local Wi-Fi co-op connection and shared-data test.
 * This stage verifies socket connectivity and exchange of a small test snapshot.
 * It does not yet load or synchronize the real dungeon.
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
    private static final int TEST_LEVEL = 1;
    private static final int TEST_MAP_WIDTH = 32;
    private static final int TEST_MAP_HEIGHT = 32;

    private static volatile ServerSocket hostServer;
    private static volatile Socket activeSocket;
    private static volatile boolean cancelled;

    // Only keep one options/status window active. Replacing status windows
    // prevents older modal windows from remaining stacked behind newer ones.
    private WndOptions activeOptions;

    @Override
    public void create() {
        super.create();
        showMenu();
    }

    private void showMenu() {
        showOptions(new WndOptions(
                "Co-op Multiplayer",
                "Connect two phones over the same Wi-Fi network or hotspot. This test will exchange sample dungeon data; real dungeon play comes later.",
                "Host LAN Game",
                "Join LAN Game",
                "Back to Title"
        ) {
            @Override
            protected void onSelect(int index) {
                if (index == 0) {
                    startHost();
                } else if (index == 1) {
                    askForHostAddress();
                } else {
                    stopNetworking();
                    ShatteredPixelDungeon.switchScene(TitleScene.class);
                }
            }
        });
    }

    private void startHost() {
        stopNetworking();
        cancelled = false;
        showMessage("Starting Host", "Starting the local Wi-Fi host…", "Cancel", new Runnable() {
            @Override public void run() {
                stopNetworking();
                ShatteredPixelDungeon.switchScene(CoOpScene.class);
            }
        });

        Thread thread = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    ServerSocket server = new ServerSocket(PORT);
                    hostServer = server;
                    if (cancelled) {
                        server.close();
                        return;
                    }
                    final String address = findLocalIPv4();
                    postStatus("Waiting for Player", "Connect the second phone to this same Wi-Fi/hotspot, then choose Join LAN Game and enter:\n\n"
                            + address + "\nPort: " + PORT + "\n\nWaiting for a player to join…", "Cancel", new Runnable() {
                        @Override public void run() {
                            stopNetworking();
                            ShatteredPixelDungeon.switchScene(CoOpScene.class);
                        }
                    });

                    Socket socket = server.accept();
                    activeSocket = socket;
                    socket.setSoTimeout(10000);
                    postStatus("Connection Established", "A player connected. Exchanging test dungeon data…", "Back to Co-op", new Runnable() {
                        @Override public void run() {
                            stopNetworking();
                            ShatteredPixelDungeon.switchScene(CoOpScene.class);
                        }
                    });

                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    out.println(testSnapshot(0));
                    String request = in.readLine();
                    if ("UPDATE_REQUEST".equals(request)) {
                        out.println(testSnapshot(1));
                        postStatus("Shared Data Confirmed", "Connection established!\n\nBoth phones exchanged the test dungeon snapshot:\nDungeon level: "
                                + TEST_LEVEL + "\nMap size: " + TEST_MAP_WIDTH + " × " + TEST_MAP_HEIGHT
                                + "\nShared test counter: 1\n\nThis is sample data, not the actual playable dungeon yet.", "Back to Co-op", new Runnable() {
                            @Override public void run() {
                                stopNetworking();
                                ShatteredPixelDungeon.switchScene(CoOpScene.class);
                            }
                        });
                    } else {
                        postStatus("Data Test Failed", "The phone connected, but the test-data reply was not received correctly.", "Back to Co-op", new Runnable() {
                            @Override public void run() {
                                stopNetworking();
                                ShatteredPixelDungeon.switchScene(CoOpScene.class);
                            }
                        });
                    }
                } catch (final Exception e) {
                    if (!cancelled) {
                        postStatus("Host Connection Failed", readableError(e) + "\n\nCheck that both phones are on the same Wi-Fi/hotspot and try again.", "Back to Co-op", new Runnable() {
                            @Override public void run() {
                                stopNetworking();
                                ShatteredPixelDungeon.switchScene(CoOpScene.class);
                            }
                        });
                    }
                } finally {
                    closeSocket(activeSocket);
                    activeSocket = null;
                    closeServer(hostServer);
                    hostServer = null;
                }
            }
        }, "CoOp-LAN-Host");
        thread.setDaemon(true);
        thread.start();
    }

    private void askForHostAddress() {
        addToFront(new WndTextInput("Join LAN Game", "Enter the host phone's local IP address. Both phones must be on the same Wi-Fi/hotspot.", "192.168.43.1", 64, false, "Connect", "Cancel") {
            @Override
            public void onSelect(boolean positive, String text) {
                if (positive) {
                    final String host = text == null ? "" : text.trim();
                    if (host.length() == 0) {
                        showMessage("Host Address Needed", "Enter the IP address shown on the host phone.", "OK", new Runnable() {
                            @Override public void run() { ShatteredPixelDungeon.switchScene(CoOpScene.class); }
                        });
                    } else {
                        startJoin(host);
                    }
                } else {
                    ShatteredPixelDungeon.switchScene(CoOpScene.class);
                }
            }
        });
    }

    private void startJoin(final String host) {
        stopNetworking();
        cancelled = false;
        showMessage("Connecting", "Connecting to " + host + ":" + PORT + "…", "Cancel", new Runnable() {
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
                    socket.setSoTimeout(10000);
                    final BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                    final PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    String first = in.readLine();
                    if (!isSnapshot(first)) {
                        throw new IllegalStateException("The host did not send the expected test dungeon data.");
                    }
                    postStatus("Connection Established", "Connected to the host. Initial test dungeon data received. Checking an update…", "Cancel", new Runnable() {
                        @Override public void run() {
                            cancelled = true;
                            closeSocket(activeSocket);
                            ShatteredPixelDungeon.switchScene(CoOpScene.class);
                        }
                    });
                    out.println("UPDATE_REQUEST");
                    final String updated = in.readLine();
                    if (!isSnapshot(updated) || !updated.endsWith("|1")) {
                        throw new IllegalStateException("The host did not return the updated test data.");
                    }
                    postStatus("Shared Data Confirmed", "Connection established!\n\nReceived matching test dungeon data from the host:\nDungeon level: "
                            + TEST_LEVEL + "\nMap size: " + TEST_MAP_WIDTH + " × " + TEST_MAP_HEIGHT
                            + "\nShared test counter: 1\n\nThis is sample data, not the actual playable dungeon yet.", "Back to Co-op", new Runnable() {
                        @Override public void run() {
                            stopNetworking();
                            ShatteredPixelDungeon.switchScene(CoOpScene.class);
                        }
                    });
                } catch (final Exception e) {
                    if (!cancelled) {
                        postStatus("Join Failed", readableError(e) + "\n\nCheck the host IP, Wi-Fi/hotspot connection, and try again.", "Back to Co-op", new Runnable() {
                            @Override public void run() {
                                stopNetworking();
                                ShatteredPixelDungeon.switchScene(CoOpScene.class);
                            }
                        });
                    }
                } finally {
                    closeSocket(socket);
                    if (activeSocket == socket) activeSocket = null;
                }
            }
        }, "CoOp-LAN-Join");
        thread.setDaemon(true);
        thread.start();
    }

    private static String testSnapshot(int counter) {
        return "DUNGEON_TEST|" + TEST_LEVEL + "|" + TEST_MAP_WIDTH + "|" + TEST_MAP_HEIGHT + "|" + counter;
    }

    private static boolean isSnapshot(String line) {
        if (line == null) return false;
        String[] parts = line.split("\\|");
        return parts.length == 5 && "DUNGEON_TEST".equals(parts[0])
                && String.valueOf(TEST_LEVEL).equals(parts[1])
                && String.valueOf(TEST_MAP_WIDTH).equals(parts[2])
                && String.valueOf(TEST_MAP_HEIGHT).equals(parts[3]);
    }

    private void showOptions(WndOptions options) {
        if (activeOptions != null) {
            activeOptions.hide();
        }
        activeOptions = options;
        addToFront(options);
    }

    private void showMessage(String title, String message, String button, final Runnable action) {
        showOptions(new WndOptions(title, message, button) {
            @Override protected void onSelect(int index) {
                if (action != null) action.run();
            }
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
        } catch (SocketException ignored) {
            // Fall back to the common Android hotspot address below.
        }
        return "192.168.43.1";
    }

    private static String readableError(Exception e) {
        if (e instanceof java.net.ConnectException) return "Could not connect. The host may not be ready, the IP may be incorrect, or the hotspot may block device-to-device traffic.";
        if (e instanceof java.net.SocketTimeoutException) return "Connection timed out. Check the host IP and that the host is waiting for a player.";
        if (e instanceof java.net.BindException) return "Could not start the host port. Close any previous co-op session and try again.";
        String message = e.getMessage();
        return message == null || message.length() == 0 ? "A network error occurred." : "Network error: " + message;
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

    @Override
    protected void onBackPressed() {
        stopNetworking();
        ShatteredPixelDungeon.switchScene(TitleScene.class);
    }
                        }
                        
