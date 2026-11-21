package com.shatteredpixel.shatteredpixeldungeon;
import java.io.DataOutputStream;
import java.io.IOException;

public interface NetPacket {
    byte TYPE_LEVEL_SNAPSHOT = 1;
    byte TYPE_MOB_SYNC       = 2;
    // Easy to expand later:
    // byte TYPE_HERO_MOVE   = 3;

    byte getType();
    void writeTo(DataOutputStream out) throws IOException;
}
