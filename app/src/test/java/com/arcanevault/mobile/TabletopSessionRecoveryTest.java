package com.arcanevault.mobile;

import org.junit.Test;
import static org.junit.Assert.*;
import org.json.JSONObject;

/** Crash and corruption recovery never touch the collection or deck library. */
public final class TabletopSessionRecoveryTest {
    @Test public void checkpointRestoresLifePoisonDamageTurnsUndoAndColor(){
        TabletopSession game=new TabletopSession(4,40);
        game.changeLife(0,-6);
        game.changePoison(1,3);
        game.changeCommanderDamage(2,0,7);
        game.setColor(3,6);
        game.nextTurn();
        String checkpoint=TabletopSessionRecovery.checkpoint(game.encode().toString());
        TabletopSession recovered=TabletopSessionRecovery.restore(checkpoint);
        assertEquals(34,recovered.life[0]);
        assertEquals(3,recovered.poison[1]);
        assertEquals(33,recovered.life[2]);
        assertEquals(7,recovered.damage[2][0]);
        assertEquals(6,recovered.colors[3]);
        assertEquals(1,recovered.activePlayer);
        assertTrue(recovered.canUndo());
        assertTrue(recovered.undo());
        assertEquals(0,recovered.activePlayer);
        assertEquals(40,recovered.life[1]);
    }

    @Test public void previousGenerationSurvivesBadActiveSession() {
        TabletopSession original=new TabletopSession(3,40);
        original.changeCommanderDamage(1,2,13);
        String previous=TabletopSessionRecovery.checkpoint(original.encode().toString());
        TabletopSession current=TabletopSession.decode(original.encode().toString());
        current.changePoison(0,4);
        assertEquals(4,current.poison[0]);
        TabletopSession rescued=TabletopSessionRecovery.restore(previous);
        assertEquals(0,rescued.poison[0]);
        assertEquals(13,rescued.damage[1][2]);
        assertEquals(27,rescued.life[1]);
    }

    @Test public void rejectsDamagedSnapshotChecksumAndSchema()throws Exception{
        TabletopSession game=new TabletopSession(2,40);
        game.changeLife(1,-8);
        JSONObject encoded=new JSONObject(TabletopSessionRecovery.checkpoint(
            game.encode().toString()));
        encoded.put("sha256","0".repeat(64));
        try{TabletopSessionRecovery.restore(encoded.toString());
            fail("Corrupt SHA allowed");}
        catch(IllegalArgumentException expected){}
        encoded.put("schema",99);
        try{TabletopSessionRecovery.restore(encoded.toString());
            fail("Future schema accepted");}
        catch(IllegalArgumentException expected){}
        encoded.put("schema",1).put("sha256","0".repeat(64))
            .put("session","{}");
        try{TabletopSessionRecovery.restore(encoded.toString());
            fail("Damaged session accepted");}
        catch(IllegalArgumentException expected){}
    }
}
