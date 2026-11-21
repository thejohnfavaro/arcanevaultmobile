package com.arcanevault.mobile;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class AiReviewCardMentionsTest {
    @Test public void extractsDeckAndProposedCardsWithoutTrustingAi()throws Exception{
        JSONArray deck=new JSONArray().put(new JSONObject()
            .put("name","Sol Ring (CMM) 396").put("qty",1));
        String response="## Improvements\n" +
            "**Esper Sentinel** should support **Sol Ring**.\n" +
            "| Cut | Add | Why |\n| --- | --- | --- |\n" +
            "| Arcane Signet | Smothering Tithe | More ramp |";
        List<String> found=AiReviewCardMentions.candidates(response,deck,"The Borg Queen");
        assertTrue(found.contains("Sol Ring"));
        assertTrue(found.contains("Esper Sentinel"));
        assertTrue(found.contains("Arcane Signet"));
        assertTrue(found.contains("Smothering Tithe"));
        assertTrue(found.contains("The Borg Queen"));
        assertFalse(found.contains("Why"));
        assertFalse(found.contains("Cut"));
        assertEquals("Smothering Tithe",
            AiReviewCardMentions.longestFirst(java.util.Arrays.asList(
                "Tithe","Smothering Tithe")).get(0));
    }
    @Test public void prefersFullFrontFaceOverShortRealCard()throws Exception{
        String response="Replace **Fell the Profane** with a different removal spell.";
        List<String> found=AiReviewCardMentions.candidates(response,new JSONArray(),"");
        assertTrue(found.contains("Fell the Profane"));
        assertEquals("Fell the Profane",
            AiReviewCardMentions.longestFirst(java.util.Arrays.asList(
                "Fell","Fell the Profane")).get(0));
    }

}
