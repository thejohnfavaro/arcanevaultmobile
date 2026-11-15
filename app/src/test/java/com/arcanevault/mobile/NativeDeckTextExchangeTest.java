package com.arcanevault.mobile;

import org.junit.Test;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.junit.Assert.*;

public final class NativeDeckTextExchangeTest {
    @Test public void acceptsArenaAnnotationsAndPreservesCommander()throws Exception{
        NativeDeckTextExchange.Parsed parsed=NativeDeckTextExchange.parse(
            "Commander\n1 Atraxa, Praetors' Voice (2XM) 183\n\nDeck\n2 Sol Ring (CMM) 396\n1 Arcane Signet",
            "Atraxa, Praetors' Voice");
        assertEquals(3,parsed.copies);
        assertTrue(parsed.removedPrintingAnnotations);
        assertEquals("2 Sol Ring\n1 Arcane Signet",parsed.entries);
    }
    @Test public void rejectsCommanderMismatch()throws Exception{
        try {
            NativeDeckTextExchange.parse("Commander\n1 Korvold\nDeck\n1 Swamp","Atraxa");
            fail("Mismatched commander silently imported");
        }catch(IllegalArgumentException expected){
            assertTrue(expected.getMessage().contains("commander"));
        }
    }
    @Test public void refusesSideboardAndMalformedContent()throws Exception{
        try{
            NativeDeckTextExchange.parse("Deck\n1 Island\nSideboard\n1 Mountain","Atraxa");
            fail("Sideboard silently lost");
        }catch(IllegalArgumentException expected){ }
        try{
            NativeDeckTextExchange.parse("Deck\nIsland","Atraxa");
            fail("Malformed deck accepted");
        }catch(IllegalArgumentException expected){ }
    }
    @Test public void exportsNamesWithoutInventingPrintings()throws Exception{
        JSONObject deck=new JSONObject()
            .put("commander","Atraxa")
            .put("entries",new JSONArray()
                .put(new JSONObject().put("name","Sol Ring").put("qty",1)));
        assertEquals("Commander\n1 Atraxa\n\nDeck\n1 Sol Ring\n",
            NativeDeckTextExchange.export(deck,true));
        assertEquals("Commander\n1 Atraxa\n\nMainboard\n1 Sol Ring\n",
            NativeDeckTextExchange.export(deck,false));
    }
}
