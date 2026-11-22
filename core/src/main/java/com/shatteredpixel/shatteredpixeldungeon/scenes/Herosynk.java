package com.shatteredpixel.shatteredpixeldungeon.scenes;

import com.shatteredpixel.shatteredpixeldungeon.NetPacket;


import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class Herosynk implements NetPacket
  {
    
    byte[] k1;
    public void herodata(byte[] payload)
    {
     // out.writeByte(FRAME_STATE);
      // out.writeInt(payload.length);
      if(payload!=null)
      {
      //  k1=payload;
      }
        //  out.flush();
    }
    @Override
    public byte getType(){
        return TYPE_HERO_SYNC;
    }
    @Override
    public void writeTo(DataOutputStream out) throws IOException
    {
  //    out.write(k1);
    }
  }
