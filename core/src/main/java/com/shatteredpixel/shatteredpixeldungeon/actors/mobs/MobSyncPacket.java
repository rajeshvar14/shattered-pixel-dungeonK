package com.shatteredpixel.shatteredpixeldungeon.actors.mobs; // (match your exact subfolder)

import com.shatteredpixel.shatteredpixeldungeon.NetPacket;


import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;


public class MobSyncPacket implements NetPacket{
    // Action constants
    public static final byte ACTION_NONE   = 0;
    public static final byte ACTION_MOVE   = 1;
    public static final byte ACTION_ATTACK = 2;
    public static final byte ACTION_ALERT  = 3;
    public static final byte ACTION_DIE    = 4;

    // State constants
    public static final byte STATE_SLEEPING  = 0;
    public static final byte STATE_WANDERING = 1;
    public static final byte STATE_HUNTING   = 2;
    public static final byte STATE_FLEEING   = 3;

    public final int mobId;
    public final int pos;
    public final int hp;
    public final int maxHp;
    public final String state;
    public final byte action;
    public final int targetPos; // Used if action is MOVE or ATTACK
//  public Mob.AiState k4= new Sleeping();
    public MobSyncPacket(int mobId, int pos, int hp) {
        this.mobId = mobId;
        this.pos = pos;
        this.hp = hp;
      //this.maxHp = maxHp;
    //    this.state = sstate;
       // this.action = action;
        //this.targetPos = targetPos;
    }
@Override
    public byte getType(){
        return TYPE_MOB_SYNC;
    }
    // Serialize directly to TCP stream
    @Override
    public void writeTo(DataOutputStream out) throws IOException {
        out.writeInt(mobId);
        out.writeInt(pos);
        out.writeInt(hp);
       // out.writeInt(maxHp);
 //       out.writeString(state);
      //  out.writeByte(action);
      //  out.writeInt(targetPos);
    }

    // Deserialize directly from TCP stream
    public static MobSyncPacket readFrom(DataInputStream in) throws IOException {
        int mobId     = in.readInt();
        int pos       = in.readInt();
        int hp        = in.readInt();
     //   int maxHp     = in.readInt();
     //   String state = in.readString();
/*if (state.equals( SLEEPING.TAG )) {
    this.state = SLEEPING;
} else if (state.equals( WANDERING.TAG )) {
    this.state = WANDERING;
} else if (state.equals( HUNTING.TAG )) {
    this.state = HUNTING;
} else if (state.equals( FLEEING.TAG )) {
    this.state = FLEEING;
} else if (state.equals( PASSIVE.TAG )) {
    this.state = PASSIVE;
}*/
        
     //  byte state    = in.readByte();
     /*   byte action   = in.readByte();
        int targetPos = in.readInt();*/
        return new MobSyncPacket(mobId, pos, hp );
    }
  }
