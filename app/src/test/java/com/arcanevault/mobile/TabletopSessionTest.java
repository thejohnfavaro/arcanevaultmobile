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

    @Test public void colorSelectionIsUndoableAndRemainsIsolated(){
        TabletopSession game=new TabletopSession(4,40);
        game.setColor(0,5);
        assertEquals(5,game.colors[0]);
        assertEquals(1,game.colors[1]);
        assertTrue(game.undo());
        assertEquals(0,game.colors[0]);
    }

    @Test public void commanderDamageReversesWithoutTouchingOtherSources(){
        TabletopSession game=new TabletopSession(4,40);
        game.changeCommanderDamage(2,0,5);
        game.changeCommanderDamage(2,1,8);
        game.changeCommanderDamage(2,0,-3);
        assertEquals(2,game.damage[2][0]);
        assertEquals(8,game.damage[2][1]);
        assertEquals(30,game.life[2]);
        assertEquals(40,game.life[0]);
    }

    @Test public void turnsTrackRoundsFromChosenStartingPlayerAndUndo(){
        TabletopSession game=new TabletopSession(4,40);
        game.setStartingPlayer(2);
        assertEquals(2,game.activePlayer);
        assertEquals(2,game.firstPlayer);
        assertEquals(1,game.round);
        game.nextTurn();
        assertEquals(3,game.activePlayer);
        game.nextTurn();
        assertEquals(0,game.activePlayer);
        game.nextTurn();
        assertEquals(1,game.activePlayer);
        game.nextTurn();
        assertEquals(2,game.activePlayer);
        assertEquals(2,game.round);
        assertTrue(game.undo());
        assertEquals(1,game.activePlayer);
        assertEquals(1,game.round);
        assertEquals(40,game.life[2]);
        assertTrue(game.recentEvents(10).get(0).contains("begins their turn"));
    }

    @Test public void counterHistoryIsBoundedAndUndoRestoresEvents(){
        TabletopSession game=new TabletopSession(2,40);
        game.changeLife(0,-5);
        game.changePoison(1,2);
        game.changeCommanderDamage(0,1,3);
        assertEquals(3,game.recentEvents(30).size());
        assertTrue(game.recentEvents(1).get(0).contains("commander damage"));
        TabletopSession restored=TabletopSession.decode(game.encode().toString());
        assertEquals(3,restored.recentEvents(30).size());
        assertEquals(32,restored.life[0]);
        assertTrue(restored.undo());
        assertEquals(35,restored.life[0]);
        assertEquals(2,restored.recentEvents(30).size());
        for(int i=0;i<110;i++)restored.nextTurn();
        assertEquals(TabletopSession.MAX_EVENTS,restored.recentEvents(100).size());
    }

    @Test public void olderSavedSessionsWithoutTurnFieldsStillRestore() throws Exception {
        TabletopSession game=new TabletopSession(3,40);
        game.changeLife(2,-8);
        org.json.JSONObject legacy=game.snapshot();
        legacy.remove("activePlayer");
        legacy.remove("firstPlayer");
        legacy.remove("round");
        legacy.remove("events");
        org.json.JSONObject encoded=new org.json.JSONObject()
            .put("session",legacy).put("undo",new org.json.JSONArray());
        TabletopSession restored=TabletopSession.decode(encoded.toString());
        assertEquals(32,restored.life[2]);
        assertEquals(0,restored.activePlayer);
        assertEquals(0,restored.firstPlayer);
        assertEquals(1,restored.round);
        assertTrue(restored.recentEvents(20).isEmpty());
        restored.nextTurn();
        assertEquals(1,restored.activePlayer);
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
