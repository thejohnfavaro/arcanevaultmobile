package com.arcanevault.mobile;

import static org.junit.Assert.*;
import org.junit.Test;

public final class TabletopSessionTest {
    @Test public void fourPlayerCommanderDefaultAndIsolatedCounters(){
        TabletopSession game=new TabletopSession(4,40);
        assertEquals(4,game.players);
        for(int life:game.life)assertEquals(40,life);
        game.changeLife(0,-3);
        game.changePoison(0,5);
        game.changeCommanderDamage(0,2,7);
        assertEquals(30,game.life[0]);
        assertEquals(5,game.poison[0]);
        assertEquals(7,game.damage[0][2]);
        assertEquals(0,game.damage[0][1]);
        assertEquals(40,game.life[1]);
        assertEquals(40,game.life[2]);
    }

    @Test public void undoRestoresExactPreviousState(){
        TabletopSession game=new TabletopSession(4,40);
        game.changeCommanderDamage(0,1,21);
        assertEquals(19,game.life[0]);
        assertEquals(21,game.damage[0][1]);
        assertTrue(game.undo());
        assertEquals(40,game.life[0]);
        assertEquals(0,game.damage[0][1]);
        assertFalse(game.canUndo());
    }

    @Test public void sessionRoundTripRestoresHistoryAndStyles(){
        TabletopSession game=new TabletopSession(3,40);
        game.rename(2,"Guest");
        game.nextColor(0);
        game.changeLife(1,5);
        game.changePoison(1,1);
        TabletopSession restored=TabletopSession.decode(game.encode().toString());
        assertEquals(3,restored.players);
        assertEquals("Guest",restored.names[2]);
        assertEquals(45,restored.life[1]);
        assertEquals(1,restored.poison[1]);
        assertEquals(1,restored.colors[0]);
        assertTrue(restored.undo());
        assertEquals(0,restored.poison[1]);
        assertEquals(45,restored.life[1]);
    }

    @Test public void guardsCounterAndSourceBounds(){
        TabletopSession game=new TabletopSession(2,40);
        try{game.changeCommanderDamage(0,0,1);fail("Self damage accepted");}
        catch(IllegalArgumentException expected){}
        try{game.changePoison(0,-1);fail("Negative poison accepted");}
        catch(IllegalArgumentException expected){}
        try{new TabletopSession(5,40);fail("Five player game accepted");}
        catch(IllegalArgumentException expected){}
        assertFalse(game.canUndo());
    }
}
