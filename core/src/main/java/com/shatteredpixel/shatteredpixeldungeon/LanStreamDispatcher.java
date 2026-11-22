package com.shatteredpixel.shatteredpixeldungeon;

import com.shatteredpixel.shatteredpixeldungeon.actor.mobs.MobSyncPacket;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class LanStreamDispatcher {

    private static final int PACKET_MAGIC = 0x50444E54; // "PDNT" frame sync marker

    private final Socket socket;
    private final LinkedBlockingQueue<NetPacket> outboundQueue = new LinkedBlockingQueue<>(1000);
    private final LinkedBlockingQueue<NetPacket> inboundQueue  = new LinkedBlockingQueue<>(1000);

    private volatile boolean running = false;
    private Thread senderThread;
    private Thread receiverThread;

    // Pass your ALREADY ESTABLISHED TCP socket here
    public LanStreamDispatcher(Socket existingSocket) {
        this.socket = existingSocket;
    }

    public void start() throws IOException {
        socket.setTcpNoDelay(true); // Send small mob packets immediately over Wi-Fi
        running = true;

        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        DataInputStream in   = new DataInputStream(new BufferedInputStream(socket.getInputStream()));

        // SINGLE SENDER THREAD: Continuously drains outboundQueue -> TCP Socket
        senderThread = new Thread(() -> {
            try {
                while (running && !Thread.currentThread().isInterrupted()) {
                    NetPacket pkt = outboundQueue.poll(500, TimeUnit.MILLISECONDS);
                    if (pkt != null) {
                        writeFrame(out, pkt);

                        // Batch-flush any additional queued packets immediately
                        while ((pkt = outboundQueue.poll()) != null) {
                            writeFrame(out, pkt);
                        }
                        out.flush();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                if (running) {
                    System.err.println("TCP Send Error: " + e.getMessage());
                    stop();
                }
            }
        }, "PD-UnifiedSender");

        // SINGLE RECEIVER THREAD: Continuously reads TCP Socket -> inboundQueue
        receiverThread = new Thread(() -> {
            try {
                while (running && !Thread.currentThread().isInterrupted()) {
                    int magic = in.readInt();
                    if (magic != PACKET_MAGIC) {
                        throw new IOException("Stream desynchronized! Bad magic header.");
                    }

                    byte type = in.readByte();
                    NetPacket received = null;

                    switch (type) {
                      /*  case NetPacket.TYPE_LEVEL_SNAPSHOT:
                            received = LevelSnapshotPacket.readFrom(in);
                            break;*/
                        case NetPacket.TYPE_MOB_SYNC:
                            received = MobSyncPacket.readFrom(in);
                            break;
                        default:
                            throw new IOException("Unknown packet type: " + type);
                    }

                    if (received != null) {
                        inboundQueue.offer(received);
                    }
                }
            } catch (IOException e) {
                if (running) {
                    System.err.println("TCP Receive Error: " + e.getMessage());
                    stop();
                }
            }
        }, "PD-UnifiedReceiver");

        senderThread.start();
        receiverThread.start();
    }

    private void writeFrame(DataOutputStream out, NetPacket pkt) throws IOException {
        out.writeInt(PACKET_MAGIC);
        out.writeByte(pkt.getType());
        pkt.writeTo(out);
    }

    // Call from ANY game thread to send Level Snapshots, Mob updates, etc.
    public void send(NetPacket packet) {
        if (!running) return;
        if (!outboundQueue.offer(packet)) {
            // Avoid dropping Level Snapshots if queue is full; only drop stale mob updates
            if (packet.getType() == NetPacket.TYPE_MOB_SYNC) {
                outboundQueue.poll();
                outboundQueue.offer(packet);
            }
        }
    }

    // Call inside GameScene.update() on the main LibGDX thread
    public NetPacket pollReceived() {
        return inboundQueue.poll();
    }

    public void stop() {
        running = false;
        if (senderThread != null) senderThread.interrupt();
        if (receiverThread != null) receiverThread.interrupt();
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }
                          }
