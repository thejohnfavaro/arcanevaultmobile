package com.arcanevault.mobile;

import static org.junit.Assert.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class NativeArenaCardNameTest {
    @Test public void removesOnlyCompleteArenaPrintingSuffixes() {
        assertEquals("Primary Unimatrix Adjunct",
            NativeArenaCardName.identity("Primary Unimatrix Adjunct (TRK) 30"));
        assertEquals("A.I.M. Synthoids",
            NativeArenaCardName.identity("A.I.M. Synthoids (MSH) 17"));
        assertEquals("Island",NativeArenaCardName.identity(" Island (TMT) 316 "));
        assertEquals("Card",NativeArenaCardName.identity("Card (ABC) 123a"));
        assertEquals("Name (Alternate)",NativeArenaCardName.identity("Name (Alternate)"));
        assertEquals("Card (X) 12",NativeArenaCardName.identity("Card (X) 12"));
        assertEquals("Card (TRK) edition",NativeArenaCardName.identity("Card (TRK) edition"));
        assertEquals("card",NativeArenaCardName.key(" Card (TRK) 99 "));
    }

    @Test public void matchesAnnotatedDeckCopiesToUnannotatedArenaInventory()
            throws Exception {
        JSONObject deck=new JSONObject().put("format","Brawl")
            .put("commander","The Borg Queen (TRC) 1")
            .put("entries",new JSONArray()
                .put(new JSONObject().put("name","Island (TMT) 316").put("qty",8))
                .put(new JSONObject().put("name","A.I.M. Synthoids (MSH) 17").put("qty",1)));
        JSONArray arena=new JSONArray()
            .put(new JSONObject().put("name","Island").put("qty",8))
            .put(new JSONObject().put("name","The Borg Queen").put("qty",1))
            .put(new JSONObject().put("name","A.I.M. Synthoids").put("qty",1));
        NativeDeckFit.Result fit=NativeDeckFit.check(deck,new JSONArray(),arena);
        assertEquals(10,fit.required);
        assertEquals(10,fit.covered);
        assertEquals(0,fit.missingCopies());
        assertTrue(fit.arena);
        assertEquals("Island (TMT) 316",deck.getJSONArray("entries")
            .getJSONObject(0).getString("name"));
    }

    @Test public void doesNotMixArenaAndPhysicalInventory() throws Exception {
        JSONObject deck=new JSONObject().put("format","Historic Brawl")
            .put("entries",new JSONArray()
                .put(new JSONObject().put("name","Sol Ring (CMM) 390").put("qty",1)));
        JSONArray paper=new JSONArray()
            .put(new JSONObject().put("name","Sol Ring").put("qty",1));
        NativeDeckFit.Result fit=NativeDeckFit.check(deck,paper,new JSONArray());
        assertEquals(1,fit.required);
        assertEquals(0,fit.covered);
    }
}
