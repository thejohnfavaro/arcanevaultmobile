package com.arcanevault.mobile;

import org.json.JSONArray;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class AiReviewCardMentionsTest {
    @Test public void extractsOnlyExplicitCardMarkers(){
        String response="Brawl and draw are ordinary words. " +
            "[[card:Fell the Profane]] replaces [[card:Fell]]. " +
            "The [[card:Smothering Tithe]] offers value.";
        List<String> found=AiReviewCardMentions.candidates(
            response,new JSONArray(),"The Borg Queen");
        assertEquals(3,found.size());
        assertTrue(found.contains("Fell the Profane"));
        assertTrue(found.contains("Fell"));
        assertTrue(found.contains("Smothering Tithe"));
        assertFalse(found.contains("Brawl"));
        assertFalse(found.contains("draw"));
        assertFalse(found.contains("The Borg Queen"));
        assertEquals("Fell the Profane",
            AiReviewCardMentions.longestFirst(found).get(0));
    }

    @Test public void ignoresUnmarkedCardNames(){
        List<String> found=AiReviewCardMentions.candidates(
            "Fell the Profane is a card. Brawl is the format.",
            new JSONArray(),"Fell");
        assertTrue(found.isEmpty());
    }
}
