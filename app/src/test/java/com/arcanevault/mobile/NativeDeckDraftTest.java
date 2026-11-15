package com.arcanevault.mobile;

import static org.junit.Assert.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class NativeDeckDraftTest {
    @Test public void mergesDuplicatesWithoutChangingCardName() throws Exception {
        JSONArray entries=NativeDeckDraft.parseEntries("2 Sol Ring\n1 Arcane Signet\n1 sol ring");
        assertEquals(2,entries.length());
        assertEquals("Sol Ring",entries.getJSONObject(0).getString("name"));
        assertEquals(3,entries.getJSONObject(0).getInt("qty"));
    }

    @Test public void refusesMalformedOrEmptyDecks() throws Exception {
        try { NativeDeckDraft.parseEntries("Sol Ring"); fail("Missing quantity accepted"); }
        catch(IllegalArgumentException expected) { }
        try { NativeDeckDraft.parseEntries("0 Sol Ring"); fail("Zero accepted"); }
        catch(IllegalArgumentException expected) { }
        JSONObject prior=new JSONObject().put("id","deck-1").put("commander","Atraxa")
            .put("name","Sample").put("entries",new JSONArray());
        try { NativeDeckDraft.revised(prior,"Sample"," "); fail("Empty deck accepted"); }
        catch(IllegalArgumentException expected) { }
    }

    @Test public void preservesCommanderAndOtherDeckMetadata() throws Exception {
        JSONObject prior=new JSONObject().put("id","deck-1").put("commander","Atraxa")
            .put("name","Old name").put("folder","My favorites").put("revisions",new JSONArray())
            .put("entries",new JSONArray().put(new JSONObject().put("name","Island").put("qty",2)));
        JSONObject next=NativeDeckDraft.revised(prior,"New name","4 Island\n1 Sol Ring");
        assertEquals("Atraxa",next.getString("commander"));
        assertEquals("My favorites",next.getString("folder"));
        assertEquals("New name",next.getString("name"));
        assertEquals(2,next.getJSONArray("entries").length());
        assertEquals("Old name",prior.getString("name"));
    }
}
