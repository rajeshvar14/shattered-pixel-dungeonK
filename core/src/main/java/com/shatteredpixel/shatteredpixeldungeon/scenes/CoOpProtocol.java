/*
 * Copyright (C) 2026
 * Co-op networking protocol foundation for Shattered Pixel Dungeon.
 * This class intentionally has no game-state side effects. It is a small,
 * versioned wire format to be integrated into the co-op scene in later steps.
 */
package com.shatteredpixel.shatteredpixeldungeon.scenes;

import java.util.StringTokenizer;

/**
 * Encodes/decodes the small line-based messages used by the co-op prototype.
 * Messages are UTF-8 text lines, so they can be sent with BufferedReader and
 * PrintWriter. Do not send arbitrary player-entered text through this format.
 */
public final class CoOpProtocol {

    public static final int PROTOCOL_VERSION = 1;

    public static final String TYPE_HELLO = "HELLO";
    public static final String TYPE_LEVEL = "LEVEL";
    public static final String TYPE_MOVE = "MOVE";
    public static final String TYPE_ACK = "ACK";
    public static final String TYPE_ERROR = "ERROR";

    private CoOpProtocol() {
    }

    /**
     * Initial handshake. The host remains authoritative for the session.
     */
    public static String hello() {
        return TYPE_HELLO + "|" + PROTOCOL_VERSION;
    }

    public static boolean isHello(String line) {
        String[] fields = split(line);
        return fields != null && fields.length == 2
                && TYPE_HELLO.equals(fields[0])
                && String.valueOf(PROTOCOL_VERSION).equals(fields[1]);
    }

    /**
     * Encodes a map snapshot. Map cells are the game's integer terrain codes.
     * The map must be a rectangular row-major array of width * height cells.
     */
    public static String levelSnapshot(int level, int width, int height,
                                       int hostPosition, int guestPosition,
                                       int[] map) {
        if (level < 1) throw new IllegalArgumentException("level must be positive");
        if (width < 1 || height < 1) throw new IllegalArgumentException("invalid map dimensions");
        if ((long) width * (long) height != (map == null ? -1 : map.length)) {
            throw new IllegalArgumentException("map length does not match dimensions");
        }
        if (!validPosition(hostPosition, map.length) || !validPosition(guestPosition, map.length)) {
            throw new IllegalArgumentException("hero position is outside the map");
        }

        StringBuilder out = new StringBuilder(32 + map.length * 4);
        out.append(TYPE_LEVEL).append('|').append(PROTOCOL_VERSION).append('|')
                .append(level).append('|').append(width).append('|').append(height).append('|')
                .append(hostPosition).append('|').append(guestPosition).append('|');
        for (int i = 0; i < map.length; i++) {
            if (i != 0) out.append(',');
            out.append(map[i]);
        }
        return out.toString();
    }

    public static LevelSnapshot parseLevelSnapshot(String line) {
        String[] fields = split(line);
        if (fields == null || fields.length != 8 || !TYPE_LEVEL.equals(fields[0])) return null;
        try {
            if (Integer.parseInt(fields[1]) != PROTOCOL_VERSION) return null;
            int level = Integer.parseInt(fields[2]);
            int width = Integer.parseInt(fields[3]);
            int height = Integer.parseInt(fields[4]);
            int hostPosition = Integer.parseInt(fields[5]);
            int guestPosition = Integer.parseInt(fields[6]);
            if (level < 1 || width < 1 || height < 1 || (long) width * height > 100000L) return null;

            StringTokenizer cells = new StringTokenizer(fields[7], ",");
            int count = width * height;
            if (cells.countTokens() != count) return null;
            int[] map = new int[count];
            for (int i = 0; i < count; i++) map[i] = Integer.parseInt(cells.nextToken());
            if (!validPosition(hostPosition, count) || !validPosition(guestPosition, count)) return null;
            return new LevelSnapshot(level, width, height, hostPosition, guestPosition, map);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** A requested one-cell movement; the host validates passability. */
    public static String move(int sequence, int from, int to) {
        if (sequence < 1 || from < 0 || to < 0) throw new IllegalArgumentException("invalid move");
        return TYPE_MOVE + "|" + PROTOCOL_VERSION + "|" + sequence + "|" + from + "|" + to;
    }

    public static MoveRequest parseMove(String line) {
        String[] fields = split(line);
        if (fields == null || fields.length != 5 || !TYPE_MOVE.equals(fields[0])) return null;
        try {
            if (Integer.parseInt(fields[1]) != PROTOCOL_VERSION) return null;
            int sequence = Integer.parseInt(fields[2]);
            int from = Integer.parseInt(fields[3]);
            int to = Integer.parseInt(fields[4]);
            if (sequence < 1 || from < 0 || to < 0) return null;
            return new MoveRequest(sequence, from, to);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static String ack(int sequence, int acceptedPosition) {
        if (sequence < 1 || acceptedPosition < 0) throw new IllegalArgumentException("invalid acknowledgement");
        return TYPE_ACK + "|" + PROTOCOL_VERSION + "|" + sequence + "|" + acceptedPosition;
    }

    public static String error(String shortCode) {
        if (shortCode == null || !shortCode.matches("[A-Z0-9_]{1,32}")) {
            throw new IllegalArgumentException("error code must be short uppercase text");
        }
        return TYPE_ERROR + "|" + PROTOCOL_VERSION + "|" + shortCode;
    }

    private static String[] split(String line) {
        if (line == null || line.length() > 1_000_000) return null;
        return line.split("\\|", -1);
    }

    private static boolean validPosition(int position, int mapLength) {
        return position >= 0 && position < mapLength;
    }

    public static final class LevelSnapshot {
        public final int level;
        public final int width;
        public final int height;
        public final int hostPosition;
        public final int guestPosition;
        public final int[] map;

        private LevelSnapshot(int level, int width, int height,
                              int hostPosition, int guestPosition, int[] map) {
            this.level = level;
            this.width = width;
            this.height = height;
            this.hostPosition = hostPosition;
            this.guestPosition = guestPosition;
            this.map = map;
        }
    }

    public static final class MoveRequest {
        public final int sequence;
        public final int from;
        public final int to;

        private MoveRequest(int sequence, int from, int to) {
            this.sequence = sequence;
            this.from = from;
            this.to = to;
        }
    }
    }
            
