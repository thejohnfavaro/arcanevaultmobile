package com.arcanevault.mobile.next;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public final class NativePeerDeltaManifestTest {
    private JSONObject snapshot(int bolt,String extra)throws Exception {
        JSONArray cards=new JSONArray().put(
            new JSONObject().put("name","Lightning Bolt").put("qty",bolt));
        if(extra!=null)cards.put(new JSONObject().put("name",extra).put("qty",1));
        JSONObject data=new JSONObject().put("schema",2).put("cards",cards)
            .put("physicalCards",new JSONArray()).put("deckLibrary",new JSONArray())
            .put("preferredArtwork",new JSONObject());
        return new JSONObject().put("revision","aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
            .put("data",data);
    }
    @Test public void unchangedSnapshotHasNoDelta()throws Exception {
        JSONObject input=snapshot(3,null);
        assertEquals(0,NativePeerDeltaManifest.compare(input,
            new JSONObject(input.toString())).getInt("changedEntries"));
    }
    @Test public void editedQuantityAndNewCardAreExplicit()throws Exception {
        JSONObject base=snapshot(2,null),current=snapshot(4,"Artificial Anomaly");
        JSONObject result=NativePeerDeltaManifest.compare(base,current);
        assertEquals(2,result.getInt("changedEntries"));
        JSONArray rows=result.getJSONObject("groups").getJSONArray("cards");
        assertEquals("added",rows.getJSONObject(0).getString("state"));
        assertEquals("artificial anomaly",rows.getJSONObject(0).getString("id"));
        assertEquals("modified",rows.getJSONObject(1).getString("state"));
        assertEquals(0,result.getJSONObject("groups").getJSONArray("deckLibrary").length());
        assertEquals(2,base.getJSONObject("data").getJSONArray("cards")
            .getJSONObject(0).getInt("qty"));
    }
    @Test public void safeDescriptionRemainsReadOnlyAndFallsBackOnBadRecords()
            throws Exception {
        JSONObject base=snapshot(2,null),now=snapshot(4,"Artificial Anomaly");
        String summary=NativePeerDeltaManifest.describeSafe(base,now);
        assertTrue(summary.contains("2 changed record(s)"));
        assertTrue(summary.contains("Arena 2"));
        assertTrue(summary.contains("Decks 0"));
        JSONObject malformed=new JSONObject(now.toString());
        malformed.getJSONObject("data").getJSONArray("cards")
            .put(new JSONObject().put("name","Lightning Bolt").put("qty",999));
        assertTrue(NativePeerDeltaManifest.describeSafe(base,malformed)
            .contains("Full-snapshot verification remains in effect"));
        assertEquals(2,base.getJSONObject("data").getJSONArray("cards")
            .getJSONObject(0).getInt("qty"));
    }

    @Test public void missingRecordIsNotAnAutomaticDeletion()throws Exception {
        JSONObject old=snapshot(2,"Artificial Anomaly"),now=snapshot(2,null);
        JSONArray delta=NativePeerDeltaManifest.compare(old,now)
            .getJSONObject("groups").getJSONArray("cards");
        assertEquals("missing",delta.getJSONObject(0).getString("state"));
        assertFalse(delta.getJSONObject(0).has("deleteAuthorized"));
    }
}
