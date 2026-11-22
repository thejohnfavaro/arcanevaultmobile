package com.arcanevault.mobile;

import org.junit.Test;
import static org.junit.Assert.*;
import org.json.JSONArray;
import org.json.JSONObject;

/** Golden Windows fingerprint and lossless mobile review contract tests. */
public final class AiReviewMetadataTest {
    private JSONObject deck()throws Exception {
        return new JSONObject()
            .put("id","deck_native_test")
            .put("name","Borg Queen")
            .put("format","Commander")
            .put("commander","The Borg Queen")
            .put("folder","EDH")
            .put("unknownFuture",new JSONObject().put("keep","untouched"))
            .put("revisions",new JSONArray().put(new JSONObject().put("label","old")))
            .put("entries",new JSONArray()
                .put(new JSONObject().put("name","Sol Ring").put("qty",2))
                .put(new JSONObject().put("name","Esper Sentinel").put("qty",1)));
    }

    @Test public void windowsAndAndroidFingerprintMatchExactly()throws Exception{
        JSONObject input=deck();
        assertEquals("1F0A97DD9BEE5A3B005383F8C74BBE782367A7BAF6B326824B7C6872204CE065",
            AiReviewMetadata.fingerprint(input));
        JSONObject reordered=new JSONObject(input.toString());
        reordered.put("entries",new JSONArray()
            .put(new JSONObject().put("name","Esper Sentinel").put("qty",1))
            .put(new JSONObject().put("name","Sol Ring").put("qty",2)));
        assertEquals(AiReviewMetadata.fingerprint(input),
            AiReviewMetadata.fingerprint(reordered));
        reordered.put("format","Arena Brawl");
        assertNotEquals(AiReviewMetadata.fingerprint(input),
            AiReviewMetadata.fingerprint(reordered));
        assertEquals("Sol Ring",AiReviewMetadata.canonical("Sol Ring (CMM) 397"));
    }

    @Test public void reviewRoundTripKeepsUnknownFieldsAndHistory()throws Exception{
        JSONObject source=deck();
        String unchanged=source.toString();
        JSONObject saved=AiReviewMetadata.attach(source,
            "## Health\n[[card:Sol Ring]] draws no cards.",
            "gpt-test","health");
        assertEquals(unchanged,source.toString());
        assertTrue(AiReviewMetadata.reviewOnlyChange(source,saved));
        assertFalse(AiReviewMetadata.stale(saved));
        assertTrue(AiReviewMetadata.label(saved).contains("Saved"));
        assertEquals(1,saved.getJSONArray("revisions").length());
        assertEquals("untouched",saved.getJSONObject("unknownFuture").getString("keep"));
        assertEquals("## Health\n[[card:Sol Ring]] draws no cards.",
            new JSONObject(saved.toString()).getJSONObject("aiReview").getString("markdown"));
        JSONObject updated=NativeDeckDraft.revised(saved,"Renamed deck",
            "2 Sol Ring\n1 Esper Sentinel");
        assertFalse(AiReviewMetadata.stale(updated));
        JSONObject changed=NativeDeckDraft.revised(saved,"Renamed deck",
            "3 Sol Ring\n1 Esper Sentinel");
        assertTrue(AiReviewMetadata.stale(changed));
        assertTrue(AiReviewMetadata.label(changed).contains("Outdated"));
        assertTrue(changed.has("aiReview"));
        assertFalse(AiReviewMetadata.reviewOnlyChange(saved,changed));
    }

    @Test public void cannotSaveReviewWithDeckEditsOrInvalidData()throws Exception{
        JSONObject source=deck();
        JSONObject correct=AiReviewMetadata.attach(source,
            "valid", "gpt-test", "synergy");
        JSONObject modified=new JSONObject(correct.toString());
        modified.put("commander","Different Commander");
        assertFalse(AiReviewMetadata.reviewOnlyChange(source,modified));
        JSONObject invalid=new JSONObject(correct.toString());
        invalid.getJSONObject("aiReview").put("deckFingerprint","bad");
        assertFalse(AiReviewMetadata.reviewOnlyChange(source,invalid));
        try{AiReviewMetadata.attach(source,"","model","health");
            fail("Missing review accepted");}catch(IllegalArgumentException expected){}
        try{AiReviewMetadata.attach(source,"valid","model","unknown");
            fail("Unknown focus accepted");}catch(IllegalArgumentException expected){}
    }
}
