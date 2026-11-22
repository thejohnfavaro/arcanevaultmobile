package com.arcanevault.mobile;

import org.junit.Test;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;
import static org.junit.Assert.*;

public class NativeMatchAnalyticsSnapshotTest {
    private static JSONObject event(String time,String deck,String format,String result)throws Exception {
        return new JSONObject().put("playedUtc",time)
            .put("deckName",deck).put("format",format).put("result",result);
    }
    private static JSONObject peer(JSONArray events)throws Exception {
        return new JSONObject().put("format","arcane-vault-peer-sync").put("schema",1)
            .put("matchAnalytics",new JSONObject().put("schema",1).put("records",events));
    }
    @Test public void readOnlyImportAndFilteredStats()throws Exception {
        JSONArray entries=new JSONArray();
        entries.put(event("2026-11-17T11:00:00Z","Borg","Brawl","win"));
        entries.put(event("2026-11-16T11:00:00Z","Borg","Brawl","loss"));
        entries.put(event("2026-11-15T11:00:00Z","Borg","Historic","win"));
        entries.put(event("2026-11-14T11:00:00Z","Other","Brawl","loss"));
        entries.put(event("2026-08-01T11:00:00Z","Borg","Brawl","loss"));
        JSONObject source=NativeMatchAnalyticsSnapshot.fromPeer(peer(entries));
        assertEquals(5,source.getJSONArray("records").length());
        Instant now=Instant.parse("2026-11-17T12:00:00Z");
        NativeMatchAnalyticsSnapshot.Report borg=
            NativeMatchAnalyticsSnapshot.calculate(source,"Borg","Brawl",30,now);
        assertEquals(1,borg.wins);
        assertEquals(1,borg.losses);
        assertEquals(50,borg.percent());
        assertEquals(2,borg.newest.size());
        assertEquals(1,borg.bestWins);
        assertEquals(1,borg.worstLosses);
        assertEquals(1,borg.weeks.size());
        NativeMatchAnalyticsSnapshot.Report all=
            NativeMatchAnalyticsSnapshot.calculate(source,"","",0,now);
        assertEquals(2,all.wins);
        assertEquals(3,all.losses);
    }
    @Test public void opponentColorsAreSeparatedAndUnknownIsNotColorless()throws Exception {
        JSONArray entries=new JSONArray();
        entries.put(event("2026-11-17T11:00:00Z","Etali","Brawl","loss")
            .put("opponentColors","UB"));
        entries.put(event("2026-11-16T11:00:00Z","Etali","Brawl","win")
            .put("opponentColors","BU"));
        entries.put(event("2026-11-15T11:00:00Z","Etali","Brawl","loss")
            .put("opponentColors","C"));
        entries.put(event("2026-11-14T11:00:00Z","Etali","Brawl","win"));
        entries.put(event("2026-11-13T11:00:00Z","Other","Brawl","win")
            .put("opponentColors","UB"));
        JSONObject source=NativeMatchAnalyticsSnapshot.fromPeer(peer(entries));
        var groups=NativeMatchAnalyticsSnapshot.byOpponentColors(source,"Etali",
            "Brawl",30,Instant.parse("2026-11-18T12:00:00Z"));
        assertEquals(3,groups.size());
        assertEquals("UB",groups.get(0).identity);
        assertEquals(1,groups.get(0).wins);
        assertEquals(1,groups.get(0).losses);
        assertEquals("C",groups.get(1).identity);
        assertEquals(0,groups.get(1).wins);
        assertEquals(1,groups.get(1).losses);
        assertEquals("Unknown",groups.get(2).identity);
        assertEquals(1,groups.get(2).wins);
    }
    @Test public void rejectUnverifiedRowsAndOtherFormats()throws Exception {
        JSONArray entries=new JSONArray().put(event("2026-11-17T11:00:00Z","Borg","","unknown"));
        try{NativeMatchAnalyticsSnapshot.fromPeer(peer(entries));fail("Unknown not confirmed");}
        catch(IllegalArgumentException expected){}
        JSONObject notPeer=peer(new JSONArray());
        notPeer.put("format","not-arcane-vault");
        try{NativeMatchAnalyticsSnapshot.fromPeer(notPeer);fail("Wrong peer format");}
        catch(IllegalArgumentException expected){}
    }
}
