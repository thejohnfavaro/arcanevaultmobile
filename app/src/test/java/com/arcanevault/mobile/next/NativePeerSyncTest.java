package com.arcanevault.mobile.next;

import static org.junit.Assert.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class NativePeerSyncTest {
    private JSONObject peer(String device,int copies)throws Exception {
        JSONObject data=new JSONObject().put("schema",2)
            .put("cards",new JSONArray().put(new JSONObject()
                .put("name","Lightning Bolt").put("qty",copies)))
            .put("physicalCards",new JSONArray().put(new JSONObject()
                .put("name","Sol Ring").put("qty",2).put("set","CMM")
                .put("number","397").put("finish","nonfoil")
                .put("scryfallId","11111111-1111-4111-8111-111111111111")))
            .put("deckLibrary",new JSONArray().put(new JSONObject()
                .put("id","deck_1").put("name","Commander Sample")
                .put("commander","Sample Commander")
                .put("entries",new JSONArray())))
            .put("preferredArtwork",new JSONObject()
                .put("sol ring",new JSONObject().put("image",
                    "https://cards.scryfall.io/test.jpg")));
        return new JSONObject().put("format",NativePeerSync.FORMAT)
            .put("schema",NativePeerSync.SCHEMA)
            .put("deviceId",device)
            .put("revision","a".repeat(64)).put("data",data);
    }
    @Test public void identicalPeerIsNoop()throws Exception{
        JSONObject local=peer("00000000-0000-4000-8000-000000000701",4);
        JSONObject other=peer("00000000-0000-4000-8000-000000000702",4);
        JSONObject result=NativePeerSync.preview(local,other);
        assertEquals(0,result.getJSONArray("changes").length());
        assertEquals(4,result.getInt("equal"));
        assertFalse(result.getBoolean("safeToAutoApply"));
    }
    @Test public void printingAndArenaDifferencesNeverApply()throws Exception{
        JSONObject local=peer("00000000-0000-4000-8000-000000000701",4);
        JSONObject other=peer("00000000-0000-4000-8000-000000000702",5);
        other.getJSONObject("data").getJSONArray("physicalCards")
            .put(new JSONObject().put("name","Counterspell").put("qty",1)
                .put("set","2XM").put("number","47")
                .put("scryfallId","22222222-2222-4222-8222-222222222222"));
        String before=local.toString();
        JSONObject preview=NativePeerSync.preview(local,other);
        assertEquals(2,preview.getJSONArray("changes").length());
        assertEquals(1,preview.getInt("conflicts"));
        assertEquals(before,local.toString());
    }
    @Test public void serializerPropertyOrderDoesNotCreateConflicts()throws Exception {
        JSONObject local=peer("00000000-0000-4000-8000-000000000701",4);
        JSONObject remote=peer("00000000-0000-4000-8000-000000000702",4);
        JSONArray ordered=new JSONArray().put(new JSONObject()
            .put("qty",4).put("name","Lightning Bolt"));
        remote.getJSONObject("data").put("cards",ordered);
        remote.getJSONObject("data").put("preferredArtwork",
            new JSONObject().put("sol ring",new JSONObject()
                .put("image","https://cards.scryfall.io/test.jpg")));
        JSONObject comparison=NativePeerSync.preview(local,remote);
        assertEquals(0,comparison.getJSONArray("changes").length());
    }

    @Test public void malformedAndDuplicateDecksRejected()throws Exception{
        JSONObject local=peer("00000000-0000-4000-8000-000000000701",4);
        JSONObject peer=peer("00000000-0000-4000-8000-000000000702",4);
        peer.put("format","other");
        try{NativePeerSync.preview(local,peer);fail("should reject format");}
        catch(IllegalArgumentException expected){}
        JSONObject duplicated=peer("00000000-0000-4000-8000-000000000702",4);
        duplicated.getJSONObject("data").getJSONArray("deckLibrary")
            .put(new JSONObject().put("id","deck_1").put("name","Conflict"));
        try{NativePeerSync.preview(local,duplicated);fail("should reject duplicate deck IDs");}
        catch(IllegalArgumentException expected){}
    }
}
