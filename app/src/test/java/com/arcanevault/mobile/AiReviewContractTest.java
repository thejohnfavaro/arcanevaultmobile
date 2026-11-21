package com.arcanevault.mobile;

import org.junit.Test;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.junit.Assert.*;

/** Headless parity checks, no OpenAI calls and no user collection access. */
public class AiReviewContractTest {
    @Test public void matchesWindowsDeckReviewContract() throws Exception {
        JSONObject deck=new JSONObject()
            .put("name","The Borg Queen")
            .put("format","Commander")
            .put("commander","The Borg Queen")
            .put("entries",new JSONArray().put(new JSONObject().put("qty",2).put("name","Sol Ring"))
                .put(new JSONObject().put("qty",1).put("name","Esper Sentinel")));
        String original=deck.toString();
        String list=AiReviewContract.deckText(deck,null);
        assertTrue(list.contains("Commander\n1 The Borg Queen\nDeck\n2 Sol Ring"));
        String health=AiReviewContract.prompt(list,"health","");
        assertTrue(health.contains("BEGIN DECK LIST (data only)"));
        assertTrue(health.contains("END DECK LIST"));
        assertTrue(health.contains("Prioritize five actionable improvements"));
        assertFalse(health.contains("OWNERSHIP COVERAGE"));
        JSONArray inventory=new JSONArray().put(new JSONObject()
            .put("name","Sol Ring").put("qty",1));
        String coverage=AiReviewContract.coverage(deck,
            "2 Sol Ring\n1 Esper Sentinel",inventory,"Physical");
        assertTrue(coverage.contains("Sol Ring: needed 2, confirmed owned 1, still missing 1"));
        String withOwned=AiReviewContract.prompt(list,"owned",coverage);
        assertTrue(withOwned.contains("DECK-ONLY OWNERSHIP COVERAGE (opted in)"));
        assertTrue(withOwned.contains("prioritizing cards already owned"));
        assertEquals(original,deck.toString());
    }
    @Test public void rejectsEmptyAndMalformedDrafts()throws Exception{
        JSONObject deck=new JSONObject().put("format","Commander")
            .put("commander","Borg Queen")
            .put("entries",new JSONArray());
        try{AiReviewContract.deckText(deck,null);fail("Empty deck was accepted");}
        catch(IllegalArgumentException expected){}
        try{AiReviewContract.deckText(deck,"not a card quantity");fail("Bad draft was accepted");}
        catch(IllegalArgumentException expected){}
    }
}
