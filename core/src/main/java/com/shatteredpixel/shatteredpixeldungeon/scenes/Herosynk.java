package com.shatteredpixel.shatteredpixeldungeon.scenes;

import com.shatteredpixel.shatteredpixeldungeon.NetPacket;


import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class Herosynk implements Netpackets
  {
    public void herodata();
    @Override
    public byte getType(){
        return TYPE_MOV_SYNC;
    }
    @Override
    public void writeTo(DataOutputStream out) throws IOException
    {
    }
  }
